package com.pulseir.remote.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pulseir.remote.R
import com.pulseir.remote.data.CatalogRepository
import com.pulseir.remote.data.CustomRemote
import com.pulseir.remote.data.Macro
import com.pulseir.remote.data.MacroStep
import com.pulseir.remote.data.SavedDevice
import com.pulseir.remote.data.UserRepository
import com.pulseir.remote.ir.ButtonRole
import com.pulseir.remote.ir.IrTransmitter
import com.pulseir.remote.ui.components.ALL_ROOMS
import com.pulseir.remote.ui.components.IrLedStatusPill
import com.pulseir.remote.ui.components.categoryAccentColor
import com.pulseir.remote.ui.components.categoryIcon
import com.pulseir.remote.ui.components.roomDisplayName
import com.pulseir.remote.ui.theme.PulseColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    catalogRepository: CatalogRepository,
    userRepository: UserRepository,
    irTransmitter: IrTransmitter,
    onOpenDevice: (String) -> Unit,
    onOpenCustomRemote: (String) -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenSmartScan: () -> Unit,
    onOpenImportTab: () -> Unit,
) {
    val store by userRepository.state.collectAsState()
    val lastPulse by irTransmitter.lastPulse.collectAsState()
    val scope = rememberCoroutineScope()

    var selectedRoom by remember { mutableStateOf("all") }
    var showAddSheet by remember { mutableStateOf(false) }
    var showNewCustomDialog by remember { mutableStateOf(false) }
    var editingDevice by remember { mutableStateOf<SavedDevice?>(null) }
    var runningMacroId by remember { mutableStateOf<String?>(null) }
    var catalogStats by remember { mutableStateOf(6031 to 182000) }

    LaunchedEffect(Unit) {
        runCatching { catalogStats = catalogRepository.getCatalogStats() }
    }

    val isEmpty = store.devices.isEmpty() && store.customRemotes.isEmpty()

    val filteredDevices = remember(store.devices, selectedRoom) {
        if (selectedRoom == "all") store.devices else store.devices.filter { it.room == selectedRoom }
    }
    val lastUsedDevice = remember(store.devices) {
        if (store.devices.size >= 3) store.devices.maxByOrNull { it.lastUsedAt } else null
    }

    fun quickPowerDevice(device: SavedDevice) {
        scope.launch(Dispatchers.IO) {
            val buttons = userRepository.getDeviceButtons(device)
            val pwr = buttons.firstOrNull { it.role == ButtonRole.POWER }
                ?: buttons.firstOrNull { it.role == ButtonRole.POWER_ON }
                ?: buttons.firstOrNull { it.role == ButtonRole.POWER_OFF }
                ?: buttons.firstOrNull()
            if (pwr != null) {
                irTransmitter.transmit(pwr.signal, "${device.name}: ${pwr.name}")
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            // Hide FAB on empty hero so there is zero redundant UI clutter
            if (!isEmpty) {
                FloatingActionButton(
                    onClick = { showAddSheet = true },
                    containerColor = PulseColors.Crimson,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_device))
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Calm Minimal Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = PulseColors.Emerald.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, PulseColors.Emerald.copy(alpha = 0.35f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.WifiOff,
                                    contentDescription = null,
                                    tint = PulseColors.Emerald,
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    text = stringResource(R.string.offline_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PulseColors.Emerald,
                                )
                            }
                        }
                    }
                    IrLedStatusPill(
                        hasIrEmitter = irTransmitter.hasIrEmitter,
                        lastPulse = lastPulse,
                    )
                }
            }

            // Empty onboarding hero + calm offline spec card
            if (isEmpty) {
                item {
                    EmptyHomeHero(
                        onOpenCatalog = onOpenCatalog,
                        onOpenSmartScan = onOpenSmartScan,
                        onOpenImport = onOpenImportTab,
                    )
                }
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SpecMetric(value = "${catalogStats.first}+", label = stringResource(R.string.nav_remotes), accent = PulseColors.Crimson)
                            SpecMetric(value = "${catalogStats.second / 1000}K+", label = stringResource(R.string.spec_codes), accent = PulseColors.Cyan)
                            SpecMetric(value = "100%", label = stringResource(R.string.offline_badge), accent = PulseColors.Emerald)
                        }
                    }
                }
            } else {
                // Continue Using (Quick Resume banner when 3+ devices)
                if (lastUsedDevice != null) {
                    item {
                        val accent = categoryAccentColor(lastUsedDevice.categoryId)
                        Surface(
                            onClick = { onOpenDevice(lastUsedDevice.id) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.horizontalGradient(listOf(accent.copy(alpha = 0.14f), Color.Transparent)),
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.History, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.last_used_remote),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accent,
                                    )
                                    Text(
                                        text = lastUsedDevice.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Surface(
                                    onClick = { quickPowerDevice(lastUsedDevice) },
                                    shape = CircleShape,
                                    color = PulseColors.Crimson.copy(alpha = 0.16f),
                                    modifier = Modifier.size(38.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Rounded.PowerSettingsNew,
                                            contentDescription = stringResource(R.string.power),
                                            tint = PulseColors.Crimson,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Room Filter Chips
                if (store.devices.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = selectedRoom == "all",
                                onClick = { selectedRoom = "all" },
                                label = { Text(stringResource(R.string.all_rooms)) },
                            )
                            for (room in ALL_ROOMS) {
                                FilterChip(
                                    selected = selectedRoom == room,
                                    onClick = { selectedRoom = room },
                                    label = { Text(roomDisplayName(room)) },
                                )
                            }
                        }
                    }
                }

                // Quick Macros Strip
                if (store.macros.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.quick_macros),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            for (macro in store.macros) {
                                val isRunning = runningMacroId == macro.id
                                QuickMacroChip(
                                    macro = macro,
                                    isRunning = isRunning,
                                    onRun = {
                                        if (runningMacroId != null) return@QuickMacroChip
                                        scope.launch(Dispatchers.IO) {
                                            runningMacroId = macro.id
                                            try {
                                                for (step in macro.steps) {
                                                    when (step) {
                                                        is MacroStep.SendIr -> irTransmitter.transmitBlocking(step.signal, step.label, performHaptic = true)
                                                        is MacroStep.Delay -> delay(step.delayMs)
                                                    }
                                                }
                                            } finally {
                                                runningMacroId = null
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                // Saved Devices — Sleek 2-Column Hardware Tile Grid
                if (store.devices.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.my_devices),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    val deviceRows = filteredDevices.chunked(2)
                    items(deviceRows, key = { row -> row.joinToString("_") { it.id } }) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            for (device in row) {
                                SavedDeviceTile(
                                    device = device,
                                    onClick = { onOpenDevice(device.id) },
                                    onQuickPower = { quickPowerDevice(device) },
                                    onEdit = { editingDevice = device },
                                    onDelete = { userRepository.deleteDevice(device.id) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                // Custom Remotes Section
                if (store.customRemotes.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.my_custom_remotes),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    items(store.customRemotes, key = { it.id }) { custom ->
                        CustomRemoteCard(
                            remote = custom,
                            onClick = { onOpenCustomRemote(custom.id) },
                            onDelete = { userRepository.deleteCustomRemote(custom.id) },
                        )
                    }
                }
            }

            // Subtle author signature footer
            item {
                Text(
                    text = stringResource(R.string.made_by_signature),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 8.dp),
                )
            }
        }
    }

    // Add Action Bottom Sheet
    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.add_device),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                AddOptionTile(
                    icon = Icons.Rounded.Search,
                    accent = PulseColors.Crimson,
                    title = stringResource(R.string.browse_catalog),
                    subtitle = stringResource(R.string.catalog_subtitle_mega),
                    onClick = {
                        showAddSheet = false
                        onOpenCatalog()
                    },
                )
                AddOptionTile(
                    icon = Icons.Rounded.Radar,
                    accent = PulseColors.Cyan,
                    title = stringResource(R.string.start_smart_scan),
                    subtitle = stringResource(R.string.smart_scan_subtitle),
                    onClick = {
                        showAddSheet = false
                        onOpenSmartScan()
                    },
                )
                AddOptionTile(
                    icon = Icons.Rounded.SettingsRemote,
                    accent = PulseColors.Violet,
                    title = stringResource(R.string.new_custom_remote),
                    subtitle = stringResource(R.string.empty_custom_remote),
                    onClick = {
                        showAddSheet = false
                        showNewCustomDialog = true
                    },
                )
                AddOptionTile(
                    icon = Icons.Rounded.FileUpload,
                    accent = PulseColors.Emerald,
                    title = stringResource(R.string.import_ir_file),
                    subtitle = stringResource(R.string.import_ir_title),
                    onClick = {
                        showAddSheet = false
                        onOpenImportTab()
                    },
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showNewCustomDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewCustomDialog = false },
            title = { Text(stringResource(R.string.new_custom_remote)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.device_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(onClick = {
                    val created = userRepository.createCustomRemote(name.ifBlank { "My Custom Remote" })
                    showNewCustomDialog = false
                    onOpenCustomRemote(created.id)
                }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewCustomDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    editingDevice?.let { dev ->
        var newName by remember(dev.id) { mutableStateOf(dev.name) }
        var newRoom by remember(dev.id) { mutableStateOf(dev.room) }
        AlertDialog(
            onDismissRequest = { editingDevice = null },
            title = { Text(stringResource(R.string.rename)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.device_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for (room in ALL_ROOMS) {
                            FilterChip(
                                selected = newRoom == room,
                                onClick = { newRoom = room },
                                label = { Text(roomDisplayName(room)) },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    userRepository.renameDevice(dev.id, newName, newRoom)
                    editingDevice = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { editingDevice = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SpecMetric(value: String, label: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = accent)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyHomeHero(
    onOpenCatalog: () -> Unit,
    onOpenSmartScan: () -> Unit,
    onOpenImport: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .clip(CircleShape)
                    .background(PulseColors.Crimson.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.SettingsRemote,
                    contentDescription = null,
                    tint = PulseColors.Crimson,
                    modifier = Modifier.size(38.dp),
                )
            }
            Text(
                text = stringResource(R.string.empty_devices_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.empty_devices_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onOpenCatalog,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Crimson),
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.browse_catalog))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onOpenSmartScan,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Rounded.Radar, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.nav_scan), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onOpenImport,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.tab_import), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun SavedDeviceTile(
    device: SavedDevice,
    onClick: () -> Unit,
    onQuickPower: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = categoryAccentColor(device.categoryId)
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = categoryIcon(device.categoryId),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Surface(
                    onClick = onQuickPower,
                    shape = CircleShape,
                    color = PulseColors.Crimson.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, PulseColors.Crimson.copy(alpha = 0.45f)),
                    modifier = Modifier.size(38.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.PowerSettingsNew,
                            contentDescription = stringResource(R.string.power),
                            tint = PulseColors.Crimson,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${device.brand} · ${roomDisplayName(device.room)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(26.dp),
                        ) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rename)) },
                                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onEdit()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete)) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomRemoteCard(
    remote: CustomRemote,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val totalButtons = remote.tabs.sumOf { it.buttons.size }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, PulseColors.Violet.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(PulseColors.Violet.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.SettingsRemote, contentDescription = null, tint = PulseColors.Violet)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(remote.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = stringResource(R.string.buttons_count, totalButtons),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun QuickMacroChip(
    macro: Macro,
    isRunning: Boolean,
    onRun: () -> Unit,
) {
    val color = Color(macro.colorHex)
    Surface(
        onClick = onRun,
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = if (isRunning) 0.35f else 0.16f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (isRunning) Icons.Rounded.Bolt else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = macro.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun AddOptionTile(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
