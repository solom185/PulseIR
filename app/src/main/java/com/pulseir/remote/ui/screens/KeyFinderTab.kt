package com.pulseir.remote.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulseir.remote.R
import com.pulseir.remote.data.UserRepository
import com.pulseir.remote.ir.IrButton
import com.pulseir.remote.ir.IrCodeParser
import com.pulseir.remote.ir.IrProtocol
import com.pulseir.remote.ir.IrSignal
import com.pulseir.remote.ir.IrTransmitter
import com.pulseir.remote.ui.components.ALL_ROOMS
import com.pulseir.remote.ui.components.KeepScreenOnEffect
import com.pulseir.remote.ui.components.roomDisplayName
import com.pulseir.remote.ui.theme.PulseColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val QUICK_KEY_LABELS = listOf(
    "Power", "Mute", "Vol+", "Vol-", "Ch+", "Ch-",
    "Up", "Down", "Left", "Right", "OK",
    "Menu", "Back", "Exit", "Info", "Input", "Guide",
    "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
)

private val SWEEPABLE_PROTOCOLS = listOf(
    IrProtocol.NEC,
    IrProtocol.NECext,
    IrProtocol.Samsung32,
    IrProtocol.RC5,
    IrProtocol.RC6,
    IrProtocol.SIRC,
)

/**
 * Software Key Discovery / Protocol Command Sweeper (0x00..0xFF).
 * Allows users on IR-TX-only phones (like Xiaomi Redmi Note 15 5G) to discover and save
 * every button of any obscure/old receiver, TV, fan, or LED controller with zero extra hardware!
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyFinderTabContent(
    userRepository: UserRepository,
    irTransmitter: IrTransmitter,
    onRemoteSaved: (String) -> Unit,
) {
    val store by userRepository.state.collectAsState()
    val scope = rememberCoroutineScope()

    var protocol by remember { mutableStateOf(IrProtocol.NEC) }
    var protoDropdown by remember { mutableStateOf(false) }
    var addrHex by remember { mutableStateOf("00") }
    var cmdIndex by remember { mutableIntStateOf(0) }
    var isSweeping by remember { mutableStateOf(false) }
    var captured by remember { mutableStateOf<List<IrButton>>(emptyList()) }
    var showCaptureDialog by remember { mutableStateOf(false) }
    var showSaveRemoteDialog by remember { mutableStateOf(false) }
    var showDeviceFillDialog by remember { mutableStateOf(false) }

    KeepScreenOnEffect(enabled = isSweeping)

    val parsedAddr = remember(addrHex) { IrCodeParser.parseHexAddressOrCommand(addrHex) ?: 0 }
    val maxCmd = remember(protocol) {
        when (protocol) {
            IrProtocol.RC5 -> 63
            IrProtocol.SIRC -> 127
            else -> 255
        }
    }

    fun buildSignal(cmd: Int): IrSignal.Parsed {
        val finalCmd = if (protocol == IrProtocol.NECext) {
            // Standard NECext often uses cmd + ~cmd in high byte
            (cmd and 0xFF) or (((cmd.inv()) and 0xFF) shl 8)
        } else {
            cmd
        }
        return IrSignal.Parsed(protocol, parsedAddr, finalCmd)
    }

    LaunchedEffect(isSweeping, protocol, parsedAddr) {
        if (!isSweeping) return@LaunchedEffect
        while (isSweeping && cmdIndex <= maxCmd) {
            val sig = buildSignal(cmdIndex)
            irTransmitter.transmit(sig, "0x%02X".format(cmdIndex), performHaptic = true)
            delay(650L)
            if (!isSweeping) break
            if (cmdIndex < maxCmd) cmdIndex++ else isSweeping = false
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
                border = BorderStroke(1.dp, PulseColors.Cyan.copy(alpha = 0.4f)),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = PulseColors.Cyan)
                        Text(stringResource(R.string.discover_title), style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = stringResource(R.string.discover_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (store.devices.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { showDeviceFillDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(stringResource(R.string.fill_from_device))
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ExposedDropdownMenuBox(
                            expanded = protoDropdown,
                            onExpandedChange = { protoDropdown = it },
                            modifier = Modifier.weight(1.3f),
                        ) {
                            OutlinedTextField(
                                value = protocol.wireName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.protocol_label)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = protoDropdown) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            )
                            ExposedDropdownMenu(expanded = protoDropdown, onDismissRequest = { protoDropdown = false }) {
                                for (p in SWEEPABLE_PROTOCOLS) {
                                    DropdownMenuItem(
                                        text = { Text(p.wireName) },
                                        onClick = {
                                            protocol = p
                                            cmdIndex = 0
                                            protoDropdown = false
                                        },
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = addrHex,
                            onValueChange = { addrHex = it },
                            label = { Text(stringResource(R.string.addr_hex_short)) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // Current Command Readout + Progress
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "CMD 0x%02X  (${cmdIndex} / $maxCmd)".format(cmdIndex),
                                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                color = PulseColors.Cyan,
                            )
                            LinearProgressIndicator(
                                progress = { (cmdIndex + 1).toFloat() / (maxCmd + 1).toFloat() },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)),
                                color = PulseColors.Cyan,
                            )
                        }
                    }

                    // Prev / Send / Next
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                isSweeping = false
                                if (cmdIndex > 0) cmdIndex--
                                irTransmitter.transmit(buildSignal(cmdIndex), "0x%02X".format(cmdIndex))
                            },
                            enabled = cmdIndex > 0,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = null) }

                        OutlinedButton(
                            onClick = { irTransmitter.transmit(buildSignal(cmdIndex), "0x%02X".format(cmdIndex)) },
                            modifier = Modifier.weight(1.4f).height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.test))
                        }

                        OutlinedButton(
                            onClick = {
                                isSweeping = false
                                if (cmdIndex < maxCmd) cmdIndex++
                                irTransmitter.transmit(buildSignal(cmdIndex), "0x%02X".format(cmdIndex))
                            },
                            enabled = cmdIndex < maxCmd,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) }
                    }

                    // Sweep + Capture Key!
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { isSweeping = !isSweeping },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isSweeping) PulseColors.Amber else PulseColors.Crimson),
                        ) {
                            Icon(if (isSweeping) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (isSweeping) stringResource(R.string.pause_scan) else stringResource(R.string.start_scan), maxLines = 1)
                        }
                        Button(
                            onClick = {
                                isSweeping = false
                                showCaptureDialog = true
                            },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Emerald),
                        ) {
                            Icon(Icons.Rounded.BookmarkAdd, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.capture_key), maxLines = 1)
                        }
                    }
                }
            }
        }

        if (captured.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.captured_keys_count, captured.size), style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = { showSaveRemoteDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Emerald),
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.save_discovered_remote))
                    }
                }
            }

            itemsIndexed(captured, key = { idx, b -> "${idx}_${b.name}" }) { idx, btn ->
                Surface(
                    onClick = { irTransmitter.transmit(btn.signal, btn.name) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(btn.name, style = MaterialTheme.typography.titleMedium)
                            Text(btn.signal.shortBadge(), style = MaterialTheme.typography.labelSmall, color = PulseColors.Cyan)
                        }
                        IconButton(onClick = { captured = captured.toMutableList().apply { removeAt(idx) } }) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                }
            }
        }
    }

    // Capture Key Label Dialog
    if (showCaptureDialog) {
        var keyName by remember { mutableStateOf("Power") }
        AlertDialog(
            onDismissRequest = { showCaptureDialog = false },
            title = { Text("${stringResource(R.string.capture_key)} (0x%02X)".format(cmdIndex)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = keyName,
                        onValueChange = { keyName = it },
                        label = { Text(stringResource(R.string.button_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for (preset in QUICK_KEY_LABELS) {
                            FilterChip(
                                selected = keyName == preset,
                                onClick = { keyName = preset },
                                label = { Text(preset) },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val sig = buildSignal(cmdIndex)
                    val clean = keyName.trim().ifEmpty { "Key 0x%02X".format(cmdIndex) }
                    captured = captured + IrButton(
                        id = (captured.size + 1).toLong(),
                        name = clean,
                        role = IrCodeParser.inferRole(clean),
                        signal = sig,
                    )
                    showCaptureDialog = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showCaptureDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    // Save Captured Buttons as Full Remote Dialog
    if (showSaveRemoteDialog) {
        var remoteName by remember { mutableStateOf("") }
        var selectedCat by remember { mutableStateOf("box") }
        var selectedRoom by remember { mutableStateOf("living") }
        AlertDialog(
            onDismissRequest = { showSaveRemoteDialog = false },
            title = { Text(stringResource(R.string.save_discovered_remote)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = remoteName,
                        onValueChange = { remoteName = it },
                        label = { Text(stringResource(R.string.device_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for (c in listOf("box", "tv", "fan", "led", "av", "ac", "misc")) {
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
                }
            },
            confirmButton = {
                Button(onClick = {
                    val saved = userRepository.addDevice(
                        name = remoteName.ifBlank { "Discovered ${protocol.wireName}" },
                        room = selectedRoom,
                        categoryId = selectedCat,
                        brand = protocol.wireName,
                        model = "Addr $addrHex",
                        catalogRemoteId = null,
                        customButtons = captured,
                    )
                    showSaveRemoteDialog = false
                    onRemoteSaved(saved.id)
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveRemoteDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    // Fill Protocol + Address from any Saved Device
    if (showDeviceFillDialog) {
        AlertDialog(
            onDismissRequest = { showDeviceFillDialog = false },
            title = { Text(stringResource(R.string.fill_from_device)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (d in store.devices.take(8)) {
                        Surface(
                            onClick = {
                                scope.launch {
                                    val btns = userRepository.getDeviceButtons(d)
                                    val firstParsed = btns.mapNotNull { it.signal as? IrSignal.Parsed }.firstOrNull()
                                    if (firstParsed != null && firstParsed.protocol in SWEEPABLE_PROTOCOLS) {
                                        protocol = firstParsed.protocol
                                        addrHex = IrSignal.formatLittleEndianHex(firstParsed.address)
                                        cmdIndex = 0
                                    }
                                    showDeviceFillDialog = false
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(d.name, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDeviceFillDialog = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}
