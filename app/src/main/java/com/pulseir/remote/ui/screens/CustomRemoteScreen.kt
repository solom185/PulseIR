package com.pulseir.remote.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pulseir.remote.R
import com.pulseir.remote.data.CustomAction
import com.pulseir.remote.data.CustomRemoteButton
import com.pulseir.remote.data.CustomRemoteTab
import com.pulseir.remote.data.MacroStep
import com.pulseir.remote.data.SavedDevice
import com.pulseir.remote.data.UserRepository
import com.pulseir.remote.ir.IrButton
import com.pulseir.remote.ir.IrTransmitter
import com.pulseir.remote.ui.components.IrLedStatusPill
import com.pulseir.remote.ui.components.TactileRemoteButton
import com.pulseir.remote.ui.theme.PulseColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

private val BUTTON_PALETTE = listOf(
    0xFF1E2636L,
    0xFFFF3B5CL,
    0xFF00B4D8L,
    0xFF10B981L,
    0xFFF59E0BL,
    0xFF8B5CF6L,
    0xFFEC4899L,
    0xFF3B82F6L,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomRemoteScreen(
    customRemoteId: String,
    userRepository: UserRepository,
    irTransmitter: IrTransmitter,
    onBack: () -> Unit,
) {
    val store by userRepository.state.collectAsState()
    val remote = remember(store.customRemotes, customRemoteId) {
        store.customRemotes.firstOrNull { it.id == customRemoteId }
    }
    val lastPulse by irTransmitter.lastPulse.collectAsState()
    val scope = rememberCoroutineScope()

    var editMode by remember { mutableStateOf(false) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showAddButtonSheet by remember { mutableStateOf(false) }
    var showAddTabDialog by remember { mutableStateOf(false) }
    var editingButton by remember { mutableStateOf<CustomRemoteButton?>(null) }
    var renamingTab by remember { mutableStateOf<CustomRemoteTab?>(null) }

    if (remote == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val safeTabIdx = selectedTabIndex.coerceIn(0, (remote.tabs.size - 1).coerceAtLeast(0))
    val activeTab = remote.tabs.getOrNull(safeTabIdx)

    fun updateActiveTabButtons(transform: (List<CustomRemoteButton>) -> List<CustomRemoteButton>) {
        if (activeTab == null) return
        val updatedTabs = remote.tabs.mapIndexed { idx, t ->
            if (idx == safeTabIdx) t.copy(buttons = transform(t.buttons)) else t
        }
        userRepository.updateCustomRemote(remote.copy(tabs = updatedTabs))
    }

    fun executeAction(btn: CustomRemoteButton) {
        when (val a = btn.action) {
            is CustomAction.DirectSignal -> irTransmitter.transmit(a.signal, btn.label)
            is CustomAction.MacroRef -> {
                val macro = userRepository.getMacro(a.macroId) ?: return
                scope.launch(Dispatchers.IO) {
                    for (step in macro.steps) {
                        when (step) {
                            is MacroStep.SendIr -> irTransmitter.transmitBlocking(step.signal, step.label, performHaptic = true)
                            is MacroStep.Delay -> delay(step.delayMs)
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(remote.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IrLedStatusPill(hasIrEmitter = irTransmitter.hasIrEmitter, lastPulse = lastPulse)
                    Spacer(Modifier.width(6.dp))
                    FilterChip(
                        selected = editMode,
                        onClick = { editMode = !editMode },
                        label = { Text(if (editMode) stringResource(R.string.done_editing) else stringResource(R.string.edit_mode)) },
                        leadingIcon = {
                            Icon(
                                if (editMode) Icons.Rounded.Check else Icons.Rounded.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                },
            )
        },
        floatingActionButton = {
            if (editMode) {
                FloatingActionButton(
                    onClick = { showAddButtonSheet = true },
                    containerColor = PulseColors.Crimson,
                    contentColor = Color.White,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_button))
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Tab strip with Edit/Delete tab actions when in Edit Mode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                remote.tabs.forEachIndexed { idx, tab ->
                    FilterChip(
                        selected = idx == safeTabIdx,
                        onClick = {
                            if (editMode && idx == safeTabIdx) {
                                renamingTab = tab
                            } else {
                                selectedTabIndex = idx
                            }
                        },
                        trailingIcon = if (editMode && idx == safeTabIdx) {
                            { Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.rename_tab), modifier = Modifier.size(14.dp)) }
                        } else null,
                        label = { Text(tab.title) },
                    )
                }
                if (editMode) {
                    FilterChip(
                        selected = false,
                        onClick = { showAddTabDialog = true },
                        leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(stringResource(R.string.add_tab)) },
                    )
                }
            }

            if (activeTab == null || activeTab.buttons.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = stringResource(R.string.empty_custom_remote),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!editMode) {
                            Button(onClick = { editMode = true }) {
                                Text(stringResource(R.string.edit_mode))
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(
                        items = activeTab.buttons,
                        key = { _, b -> b.id },
                        span = { _, b -> GridItemSpan(if (b.isWide) 2 else 1) },
                    ) { idx, btn ->
                        val bg = Color(btn.colorHex)
                        val isMacro = btn.action is CustomAction.MacroRef
                        if (!editMode) {
                            TactileRemoteButton(
                                onPress = { executeAction(btn) },
                                label = btn.label,
                                icon = if (isMacro) Icons.Rounded.Bolt else null,
                                containerColor = bg,
                                contentColor = Color.White,
                                height = 66.dp,
                                shape = RoundedCornerShape(18.dp),
                            )
                        } else {
                            Surface(
                                onClick = { editingButton = btn },
                                shape = RoundedCornerShape(18.dp),
                                color = bg.copy(alpha = 0.85f),
                                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.35f)),
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = btn.label,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Row {
                                            IconButton(
                                                onClick = {
                                                    if (idx > 0) {
                                                        updateActiveTabButtons { list ->
                                                            list.toMutableList().apply {
                                                                val item = removeAt(idx)
                                                                add(idx - 1, item)
                                                            }
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.size(30.dp),
                                            ) {
                                                Icon(Icons.Rounded.ArrowUpward, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(
                                                onClick = {
                                                    if (idx + 1 < activeTab.buttons.size) {
                                                        updateActiveTabButtons { list ->
                                                            list.toMutableList().apply {
                                                                val item = removeAt(idx)
                                                                add(idx + 1, item)
                                                            }
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.size(30.dp),
                                            ) {
                                                Icon(Icons.Rounded.ArrowDownward, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(
                                                onClick = { editingButton = btn },
                                                modifier = Modifier.size(30.dp),
                                            ) {
                                                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.edit_button), tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                updateActiveTabButtons { list -> list.filterNot { it.id == btn.id } }
                                            },
                                            modifier = Modifier.size(30.dp),
                                        ) {
                                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete), tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Existing Button Dialog
    editingButton?.let { btn ->
        var label by remember(btn.id) { mutableStateOf(btn.label) }
        var colorHex by remember(btn.id) { mutableLongStateOf(btn.colorHex) }
        var isWide by remember(btn.id) { mutableStateOf(btn.isWide) }
        AlertDialog(
            onDismissRequest = { editingButton = null },
            title = { Text(stringResource(R.string.edit_button)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text(stringResource(R.string.button_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (hex in BUTTON_PALETTE) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(hex))
                                    .border(
                                        width = if (colorHex == hex) 3.dp else 1.dp,
                                        color = if (colorHex == hex) Color.White else Color.White.copy(alpha = 0.25f),
                                        shape = CircleShape,
                                    )
                                    .clickable { colorHex = hex },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.wide_button))
                        Switch(checked = isWide, onCheckedChange = { isWide = it })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    updateActiveTabButtons { list ->
                        list.map {
                            if (it.id == btn.id) it.copy(label = label.trim().ifEmpty { it.label }, colorHex = colorHex, isWide = isWide) else it
                        }
                    }
                    editingButton = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { editingButton = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    // Rename / Delete Tab Dialog
    renamingTab?.let { tab ->
        var title by remember(tab.id) { mutableStateOf(tab.title) }
        AlertDialog(
            onDismissRequest = { renamingTab = null },
            title = { Text(stringResource(R.string.rename_tab)) },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(onClick = {
                    val updatedTabs = remote.tabs.map {
                        if (it.id == tab.id) it.copy(title = title.trim().ifEmpty { it.title }) else it
                    }
                    userRepository.updateCustomRemote(remote.copy(tabs = updatedTabs))
                    renamingTab = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                Row {
                    if (remote.tabs.size > 1) {
                        TextButton(onClick = {
                            val updatedTabs = remote.tabs.filterNot { it.id == tab.id }
                            userRepository.updateCustomRemote(remote.copy(tabs = updatedTabs))
                            selectedTabIndex = 0
                            renamingTab = null
                        }) { Text(stringResource(R.string.delete_tab), color = PulseColors.Crimson) }
                    }
                    TextButton(onClick = { renamingTab = null }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }

    // Add Tab Dialog
    if (showAddTabDialog) {
        var tabName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddTabDialog = false },
            title = { Text(stringResource(R.string.add_tab)) },
            text = {
                OutlinedTextField(
                    value = tabName,
                    onValueChange = { tabName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(onClick = {
                    val title = tabName.trim().ifEmpty { "Tab ${remote.tabs.size + 1}" }
                    val newTabs = remote.tabs + CustomRemoteTab(id = UUID.randomUUID().toString(), title = title)
                    userRepository.updateCustomRemote(remote.copy(tabs = newTabs))
                    selectedTabIndex = newTabs.lastIndex
                    showAddTabDialog = false
                }) { Text(stringResource(R.string.add)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddTabDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showAddButtonSheet) {
        AddCustomButtonSheet(
            userRepository = userRepository,
            onDismiss = { showAddButtonSheet = false },
            onAddButton = { newBtn ->
                updateActiveTabButtons { it + newBtn }
                showAddButtonSheet = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCustomButtonSheet(
    userRepository: UserRepository,
    onDismiss: () -> Unit,
    onAddButton: (CustomRemoteButton) -> Unit,
) {
    val store by userRepository.state.collectAsState()
    var sourceTab by remember { mutableIntStateOf(0) }
    var selectedDevice by remember { mutableStateOf<SavedDevice?>(store.devices.firstOrNull()) }
    var deviceButtons by remember { mutableStateOf<List<IrButton>>(emptyList()) }

    var pendingAction by remember { mutableStateOf<CustomAction?>(null) }
    var label by remember { mutableStateOf("") }
    var colorHex by remember { mutableLongStateOf(BUTTON_PALETTE[0]) }
    var isWide by remember { mutableStateOf(false) }

    LaunchedEffect(selectedDevice?.id) {
        deviceButtons = selectedDevice?.let { userRepository.getDeviceButtons(it) } ?: emptyList()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.add_button), style = MaterialTheme.typography.titleLarge)

            if (pendingAction == null) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = sourceTab == 0, onClick = { sourceTab = 0 }, label = { Text(stringResource(R.string.from_saved_device)) })
                    FilterChip(selected = sourceTab == 1, onClick = { sourceTab = 1 }, label = { Text(stringResource(R.string.from_macro)) })
                    FilterChip(selected = sourceTab == 2, onClick = { sourceTab = 2 }, label = { Text(stringResource(R.string.from_custom_ir)) })
                }

                when (sourceTab) {
                    0 -> {
                        if (store.devices.isEmpty()) {
                            Text(stringResource(R.string.save_device_first), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                for (d in store.devices) {
                                    FilterChip(
                                        selected = selectedDevice?.id == d.id,
                                        onClick = { selectedDevice = d },
                                        label = { Text(d.name) },
                                    )
                                }
                            }
                            LazyColumn(
                                modifier = Modifier.height(260.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                items(deviceButtons, key = { "${it.id}_${it.name}" }) { btn ->
                                    Surface(
                                        onClick = {
                                            pendingAction = CustomAction.DirectSignal(selectedDevice?.name ?: "", btn.signal)
                                            label = btn.name
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(btn.name, style = MaterialTheme.typography.titleMedium)
                                            Text(btn.signal.shortBadge(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        LazyColumn(
                            modifier = Modifier.height(260.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(store.macros, key = { it.id }) { m ->
                                Surface(
                                    onClick = {
                                        pendingAction = CustomAction.MacroRef(m.id)
                                        label = m.name
                                        colorHex = m.colorHex
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(12.dp)) {
                                        Text(m.name, style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.height(260.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(store.customCodes, key = { it.id }) { c ->
                                Surface(
                                    onClick = {
                                        pendingAction = CustomAction.DirectSignal("Custom", c.signal)
                                        label = c.name
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(c.name, style = MaterialTheme.typography.titleMedium)
                                        Text(c.signal.shortBadge(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.button_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.button_color), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (hex in BUTTON_PALETTE) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .border(
                                    width = if (colorHex == hex) 3.dp else 1.dp,
                                    color = if (colorHex == hex) Color.White else Color.White.copy(alpha = 0.25f),
                                    shape = CircleShape,
                                )
                                .clickable { colorHex = hex },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.wide_button))
                    Switch(checked = isWide, onCheckedChange = { isWide = it })
                }
                Button(
                    onClick = {
                        onAddButton(
                            CustomRemoteButton(
                                id = UUID.randomUUID().toString(),
                                label = label.trim().ifEmpty { "Button" },
                                colorHex = colorHex,
                                isWide = isWide,
                                action = pendingAction!!,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(stringResource(R.string.save))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
