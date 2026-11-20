package com.pulseir.remote.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pulseir.remote.R
import com.pulseir.remote.data.CustomIrCode
import com.pulseir.remote.data.Macro
import com.pulseir.remote.data.MacroStep
import com.pulseir.remote.data.SavedDevice
import com.pulseir.remote.data.UserRepository
import com.pulseir.remote.ir.IrButton
import com.pulseir.remote.ir.IrCodeParser
import com.pulseir.remote.ir.IrProtocol
import com.pulseir.remote.ir.IrProtocolEncoder
import com.pulseir.remote.ir.IrSignal
import com.pulseir.remote.ir.IrTransmitter
import com.pulseir.remote.ui.components.ALL_ROOMS
import com.pulseir.remote.ui.components.IrLedStatusPill
import com.pulseir.remote.ui.components.WaveformCanvas
import com.pulseir.remote.ui.components.roomDisplayName
import com.pulseir.remote.ui.theme.PulseColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

private val MACRO_PALETTE = listOf(
    0xFF00B4D8L,
    0xFFFF3B5CL,
    0xFF10B981L,
    0xFFF59E0BL,
    0xFF8B5CF6L,
    0xFFEC4899L,
)

private val DELAY_PRESETS_MS = listOf(250L, 500L, 1000L, 2000L, 3000L, 5000L)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabAndMacrosScreen(
    initialTab: Int = 0,
    userRepository: UserRepository,
    irTransmitter: IrTransmitter,
    onDeviceImported: (String) -> Unit,
) {
    val lastPulse by irTransmitter.lastPulse.collectAsState()
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 3)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.nav_lab),
                style = MaterialTheme.typography.headlineMedium,
            )
            IrLedStatusPill(hasIrEmitter = irTransmitter.hasIrEmitter, lastPulse = lastPulse)
        }

        SecondaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(stringResource(R.string.tab_macros), maxLines = 1, overflow = TextOverflow.Ellipsis) })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(stringResource(R.string.tab_discover), maxLines = 1, overflow = TextOverflow.Ellipsis) })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text(stringResource(R.string.tab_custom_ir), maxLines = 1, overflow = TextOverflow.Ellipsis) })
            Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text(stringResource(R.string.tab_import), maxLines = 1, overflow = TextOverflow.Ellipsis) })
        }

        when (selectedTab) {
            0 -> MacrosTabContent(userRepository = userRepository, irTransmitter = irTransmitter)
            1 -> KeyFinderTabContent(userRepository = userRepository, irTransmitter = irTransmitter, onRemoteSaved = onDeviceImported)
            2 -> CustomIrLabTabContent(userRepository = userRepository, irTransmitter = irTransmitter)
            else -> ImportIrTabContent(userRepository = userRepository, irTransmitter = irTransmitter, onDeviceImported = onDeviceImported)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MacrosTabContent(
    userRepository: UserRepository,
    irTransmitter: IrTransmitter,
) {
    val context = LocalContext.current
    val store by userRepository.state.collectAsState()
    val scope = rememberCoroutineScope()
    var runningMacroId by remember { mutableStateOf<String?>(null) }
    var runningProgress by remember { mutableStateOf("") }
    var editingMacro by remember { mutableStateOf<Macro?>(null) }
    var showBuilder by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Button(
                onClick = {
                    editingMacro = null
                    showBuilder = true
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Crimson),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.new_macro))
            }
        }

        if (store.macros.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                ) {
                    Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.no_macros_yet), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.no_macros_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            items(store.macros, key = { it.id }) { macro ->
                val isRunning = runningMacroId == macro.id
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, Color(macro.colorHex).copy(alpha = 0.5f)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(macro.colorHex).copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Bolt, contentDescription = null, tint = Color(macro.colorHex))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(macro.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = if (isRunning) runningProgress else stringResource(R.string.steps_count, macro.steps.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isRunning) PulseColors.Cyan else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            onClick = {
                                if (runningMacroId != null) return@Button
                                scope.launch(Dispatchers.IO) {
                                    runningMacroId = macro.id
                                    macro.steps.forEachIndexed { i, step ->
                                        runningProgress = context.getString(R.string.running_step, i + 1, macro.steps.size)
                                        when (step) {
                                            is MacroStep.SendIr -> irTransmitter.transmitBlocking(step.signal, step.label, performHaptic = true)
                                            is MacroStep.Delay -> delay(step.delayMs)
                                        }
                                    }
                                    runningMacroId = null
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.run_macro))
                        }
                        IconButton(onClick = {
                            editingMacro = macro
                            showBuilder = true
                        }) {
                            Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.edit_macro))
                        }
                        IconButton(onClick = { userRepository.deleteMacro(macro.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                }
            }
        }
    }

    if (showBuilder) {
        MacroBuilderSheet(
            initial = editingMacro,
            userRepository = userRepository,
            onDismiss = { showBuilder = false },
            onSave = {
                userRepository.saveMacro(it)
                showBuilder = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MacroBuilderSheet(
    initial: Macro?,
    userRepository: UserRepository,
    onDismiss: () -> Unit,
    onSave: (Macro) -> Unit,
) {
    val store by userRepository.state.collectAsState()
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var colorHex by remember(initial?.id) { mutableLongStateOf(initial?.colorHex ?: MACRO_PALETTE[0]) }
    var steps by remember(initial?.id) { mutableStateOf(initial?.steps ?: emptyList()) }
    var pickingAction by remember { mutableStateOf(false) }
    var selectedDevice by remember { mutableStateOf<SavedDevice?>(store.devices.firstOrNull()) }
    var deviceButtons by remember { mutableStateOf<List<IrButton>>(emptyList()) }

    LaunchedEffect(selectedDevice?.id) {
        deviceButtons = selectedDevice?.let { userRepository.getDeviceButtons(it) } ?: emptyList()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (initial != null) stringResource(R.string.edit_macro) else stringResource(R.string.new_macro),
                style = MaterialTheme.typography.titleLarge,
            )

            if (!pickingAction) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.macro_name_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Color picker swatches
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (hex in MACRO_PALETTE) {
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

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { pickingAction = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.add_ir_step), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = {
                            steps = steps + MacroStep.Delay(id = UUID.randomUUID().toString(), delayMs = 1000L)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Rounded.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.add_delay_step), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                LazyColumn(
                    modifier = Modifier.height(220.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(steps, key = { _, s -> when (s) { is MacroStep.SendIr -> s.id; is MacroStep.Delay -> s.id } }) { idx, step ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = when (step) {
                                            is MacroStep.SendIr -> "${idx + 1}. ${step.deviceName}: ${step.label}"
                                            is MacroStep.Delay -> "${idx + 1}. ${stringResource(R.string.delay_ms, step.delayMs)}"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Row {
                                        IconButton(
                                            onClick = {
                                                if (idx > 0) {
                                                    steps = steps.toMutableList().apply {
                                                        val item = removeAt(idx)
                                                        add(idx - 1, item)
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(28.dp),
                                        ) { Icon(Icons.Rounded.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        IconButton(
                                            onClick = {
                                                if (idx + 1 < steps.size) {
                                                    steps = steps.toMutableList().apply {
                                                        val item = removeAt(idx)
                                                        add(idx + 1, item)
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(28.dp),
                                        ) { Icon(Icons.Rounded.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        IconButton(
                                            onClick = { steps = steps.toMutableList().apply { removeAt(idx) } },
                                            modifier = Modifier.size(28.dp),
                                        ) { Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    }
                                }
                                // Clean delay preset selector right under Delay step
                                if (step is MacroStep.Delay) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(top = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        for (ms in DELAY_PRESETS_MS) {
                                            val labelMs = if (ms >= 1000L) "${ms / 1000}s" else "${ms}ms"
                                            FilterChip(
                                                selected = step.delayMs == ms,
                                                onClick = {
                                                    steps = steps.toMutableList().apply {
                                                        set(idx, step.copy(delayMs = ms))
                                                    }
                                                },
                                                label = { Text(labelMs, style = MaterialTheme.typography.labelSmall) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        onSave(
                            Macro(
                                id = initial?.id ?: UUID.randomUUID().toString(),
                                name = name.trim().ifEmpty { "Macro" },
                                colorHex = colorHex,
                                steps = steps,
                            ),
                        )
                    },
                    enabled = steps.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(stringResource(R.string.save))
                }
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
                                steps = steps + MacroStep.SendIr(
                                    id = UUID.randomUUID().toString(),
                                    label = btn.name,
                                    deviceName = selectedDevice?.name ?: "",
                                    signal = btn.signal,
                                )
                                pickingAction = false
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
                    items(store.customCodes, key = { it.id }) { c ->
                        Surface(
                            onClick = {
                                steps = steps + MacroStep.SendIr(
                                    id = UUID.randomUUID().toString(),
                                    label = c.name,
                                    deviceName = "Custom IR",
                                    signal = c.signal,
                                )
                                pickingAction = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(c.name, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
                OutlinedButton(onClick = { pickingAction = false }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.back))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomIrLabTabContent(
    userRepository: UserRepository,
    irTransmitter: IrTransmitter,
) {
    val store by userRepository.state.collectAsState()
    var formatMode by remember { mutableIntStateOf(0) }
    var codeName by remember { mutableStateOf("") }

    var protocol by remember { mutableStateOf(IrProtocol.NEC) }
    var protoDropdown by remember { mutableStateOf(false) }
    var addrHex by remember { mutableStateOf("04") }
    var cmdHex by remember { mutableStateOf("08") }

    var prontoText by remember { mutableStateOf("") }

    var rawFreqText by remember { mutableStateOf("38000") }
    var rawPatternText by remember { mutableStateOf("9000 4500 560 560 560 1690 560") }
    var rawCycles by remember { mutableStateOf(false) }

    val parsedSignal: IrSignal? = remember(formatMode, protocol, addrHex, cmdHex, prontoText, rawFreqText, rawPatternText, rawCycles) {
        when (formatMode) {
            0 -> {
                val a = IrCodeParser.parseHexAddressOrCommand(addrHex)
                val c = IrCodeParser.parseHexAddressOrCommand(cmdHex)
                if (a != null && c != null) IrSignal.Parsed(protocol, a, c) else null
            }
            1 -> IrCodeParser.parseProntoHex(prontoText)
            else -> {
                val f = rawFreqText.trim().toIntOrNull() ?: 38000
                IrCodeParser.parseRawPattern(f, rawPatternText, valuesAreCycles = rawCycles)
            }
        }
    }
    val previewPattern = remember(parsedSignal) {
        parsedSignal?.let { IrProtocolEncoder.encode(it, toggleOverride = false).patternUs } ?: IntArray(0)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = null, tint = PulseColors.Cyan)
                        Text(stringResource(R.string.new_custom_code), style = MaterialTheme.typography.titleMedium)
                    }

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(selected = formatMode == 0, onClick = { formatMode = 0 }, label = { Text(stringResource(R.string.format_protocol)) })
                        FilterChip(selected = formatMode == 1, onClick = { formatMode = 1 }, label = { Text(stringResource(R.string.format_pronto)) })
                        FilterChip(selected = formatMode == 2, onClick = { formatMode = 2 }, label = { Text(stringResource(R.string.format_raw)) })
                    }

                    OutlinedTextField(
                        value = codeName,
                        onValueChange = { codeName = it },
                        label = { Text(stringResource(R.string.button_label)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    when (formatMode) {
                        0 -> {
                            ExposedDropdownMenuBox(
                                expanded = protoDropdown,
                                onExpandedChange = { protoDropdown = it },
                            ) {
                                OutlinedTextField(
                                    value = "${protocol.wireName} (${protocol.defaultFrequencyHz / 1000} kHz)",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(R.string.protocol_label)) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = protoDropdown) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                                )
                                ExposedDropdownMenu(expanded = protoDropdown, onDismissRequest = { protoDropdown = false }) {
                                    for (p in IrProtocol.entries) {
                                        DropdownMenuItem(
                                            text = { Text("${p.wireName} · ${p.defaultFrequencyHz / 1000} kHz") },
                                            onClick = {
                                                protocol = p
                                                protoDropdown = false
                                            },
                                        )
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = addrHex,
                                    onValueChange = { addrHex = it },
                                    label = { Text(stringResource(R.string.addr_hex_short)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = cmdHex,
                                    onValueChange = { cmdHex = it },
                                    label = { Text(stringResource(R.string.cmd_hex_short)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        1 -> {
                            OutlinedTextField(
                                value = prontoText,
                                onValueChange = { prontoText = it },
                                placeholder = { Text(stringResource(R.string.pronto_hint)) },
                                minLines = 3,
                                maxLines = 5,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        else -> {
                            OutlinedTextField(
                                value = rawFreqText,
                                onValueChange = { rawFreqText = it },
                                label = { Text(stringResource(R.string.raw_freq_label)) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = rawPatternText,
                                onValueChange = { rawPatternText = it },
                                label = { Text(stringResource(R.string.raw_pattern_label)) },
                                minLines = 2,
                                maxLines = 4,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stringResource(R.string.raw_in_cycles), style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = rawCycles, onCheckedChange = { rawCycles = it })
                            }
                        }
                    }

                    Text(
                        text = if (parsedSignal != null) {
                            "${stringResource(R.string.waveform_preview)} · ${parsedSignal.shortBadge()}"
                        } else {
                            stringResource(R.string.invalid_ir_code)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (parsedSignal != null) PulseColors.Cyan else PulseColors.Crimson,
                    )
                    WaveformCanvas(patternUs = previewPattern)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { parsedSignal?.let { irTransmitter.transmit(it, codeName.ifBlank { "Test" }) } },
                            enabled = parsedSignal != null,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(stringResource(R.string.test))
                        }
                        Button(
                            onClick = {
                                val sig = parsedSignal ?: return@Button
                                userRepository.saveCustomCode(
                                    CustomIrCode(
                                        id = UUID.randomUUID().toString(),
                                        name = codeName.trim().ifEmpty { sig.shortBadge() },
                                        signal = sig,
                                    ),
                                )
                                codeName = ""
                            },
                            enabled = parsedSignal != null,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Emerald),
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
        }

        if (store.customCodes.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.no_custom_ir_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
        } else {
            items(store.customCodes, key = { it.id }) { code ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(code.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = when (val s = code.signal) {
                                    is IrSignal.Parsed -> s.summary()
                                    is IrSignal.Raw -> s.summary()
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(
                            onClick = { irTransmitter.transmit(code.signal, code.name) },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(stringResource(R.string.test))
                        }
                        IconButton(onClick = { userRepository.deleteCustomCode(code.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportIrTabContent(
    userRepository: UserRepository,
    irTransmitter: IrTransmitter,
    onDeviceImported: (String) -> Unit,
) {
    val context = LocalContext.current
    var importedButtons by remember { mutableStateOf<List<IrButton>>(emptyList()) }
    var deviceName by remember { mutableStateOf("") }
    var selectedRoom by remember { mutableStateOf("living") }
    var selectedCat by remember { mutableStateOf("tv") }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        val fileName = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: "Imported Remote"

        if (text != null) {
            val parsed = IrCodeParser.parseFlipperIrFile(text)
            if (parsed.isNotEmpty()) {
                importedButtons = parsed
                deviceName = fileName.removeSuffix(".ir").replace("_", " ").trim()
                statusMsg = context.getString(R.string.import_success, deviceName, parsed.size)
            } else {
                importedButtons = emptyList()
                statusMsg = context.getString(R.string.import_failed)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, PulseColors.Emerald.copy(alpha = 0.45f)),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Rounded.FileUpload, contentDescription = null, tint = PulseColors.Emerald, modifier = Modifier.size(44.dp))
                    Text(stringResource(R.string.import_ir_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.import_ir_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { picker.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Emerald),
                    ) {
                        Icon(Icons.Rounded.FileUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.pick_ir_file))
                    }
                    if (statusMsg != null) {
                        Text(statusMsg!!, style = MaterialTheme.typography.labelLarge, color = PulseColors.Cyan)
                    }
                }
            }
        }

        if (importedButtons.isNotEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = deviceName,
                            onValueChange = { deviceName = it },
                            label = { Text(stringResource(R.string.device_name_label)) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            for (c in listOf("tv", "ac", "box", "fan", "led", "av", "projector", "misc")) {
                                FilterChip(selected = selectedCat == c, onClick = { selectedCat = c }, label = { Text(c.uppercase()) })
                            }
                        }
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            for (rm in ALL_ROOMS) {
                                FilterChip(selected = selectedRoom == rm, onClick = { selectedRoom = rm }, label = { Text(roomDisplayName(rm)) })
                            }
                        }
                        Button(
                            onClick = {
                                val saved = userRepository.addDevice(
                                    name = deviceName,
                                    room = selectedRoom,
                                    categoryId = selectedCat,
                                    brand = "Imported",
                                    model = ".ir File",
                                    catalogRemoteId = null,
                                    customButtons = importedButtons,
                                )
                                onDeviceImported(saved.id)
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(stringResource(R.string.save_this_remote))
                        }
                    }
                }
            }

            items(importedButtons.take(30), key = { "${it.id}_${it.name}" }) { btn ->
                Surface(
                    onClick = { irTransmitter.transmit(btn.signal, btn.name) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(btn.name, style = MaterialTheme.typography.titleMedium)
                        Text(btn.signal.shortBadge(), style = MaterialTheme.typography.labelSmall, color = PulseColors.Cyan)
                    }
                }
            }
        }
    }
}
