package com.pulseir.remote.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Eject
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulseir.remote.R
import com.pulseir.remote.data.CustomIrCode
import com.pulseir.remote.data.UserRepository
import com.pulseir.remote.ir.AcFanSpeed
import com.pulseir.remote.ir.AcMode
import com.pulseir.remote.ir.AcProtocolEngine
import com.pulseir.remote.ir.AcState
import com.pulseir.remote.ir.ButtonRole
import com.pulseir.remote.ir.IrButton
import com.pulseir.remote.ir.IrTransmitter
import com.pulseir.remote.ir.SmartAcProtocol
import com.pulseir.remote.lockscreen.RemoteNotificationController
import com.pulseir.remote.ui.components.CircularDPad
import com.pulseir.remote.ui.components.IrLedStatusPill
import com.pulseir.remote.ui.components.KeepScreenOnEffect
import com.pulseir.remote.ui.components.RockerControl
import com.pulseir.remote.ui.components.SignalInspectorSheet
import com.pulseir.remote.ui.components.TactileRemoteButton
import com.pulseir.remote.ui.components.WaveformCanvas
import com.pulseir.remote.ui.components.categoryAccentColor
import com.pulseir.remote.ui.components.categoryIcon
import com.pulseir.remote.ui.theme.PulseColors
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteControllerScreen(
    deviceId: String,
    userRepository: UserRepository,
    irTransmitter: IrTransmitter,
    keepScreenOn: Boolean,
    useVolumeKeys: Boolean,
    onRegisterVolumeHandler: (((isUp: Boolean) -> Boolean)?) -> Unit,
    onSwitchDevice: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store by userRepository.state.collectAsState()
    val device = remember(store.devices, deviceId) { store.devices.firstOrNull { it.id == deviceId } }
    val lastPulse by irTransmitter.lastPulse.collectAsState()
    val pinnedDeviceId by RemoteNotificationController.pinnedDeviceId.collectAsState()
    val isPinned = device != null && pinnedDeviceId == device.id

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && device != null) {
            RemoteNotificationController.pinRemote(context, device)
            Toast.makeText(context, context.getString(R.string.pinned_lockscreen_toast), Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleLockScreenPin() {
        val d = device ?: return
        if (isPinned) {
            RemoteNotificationController.dismiss(context)
            Toast.makeText(context, context.getString(R.string.unpinned_lockscreen_toast), Toast.LENGTH_SHORT).show()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            RemoteNotificationController.pinRemote(context, d)
            Toast.makeText(context, context.getString(R.string.pinned_lockscreen_toast), Toast.LENGTH_SHORT).show()
        }
    }

    var buttons by remember { mutableStateOf<List<IrButton>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var switcherExpanded by remember { mutableStateOf(false) }
    var inspectedButton by remember { mutableStateOf<IrButton?>(null) }

    KeepScreenOnEffect(enabled = keepScreenOn)

    LaunchedEffect(device?.id) {
        if (device != null) {
            userRepository.markDeviceUsed(device.id)
            loading = true
            buttons = userRepository.getDeviceButtons(device)
            loading = false
        }
    }

    val byRole = remember(buttons) { buttons.associateBy { it.role } }

    fun fire(btn: IrButton?) {
        if (btn != null) irTransmitter.transmit(btn.signal, btn.name)
    }

    // Hook phone's physical side volume keys to TV/Audio Volume Up/Down!
    DisposableEffect(useVolumeKeys, byRole) {
        val volUp = byRole[ButtonRole.VOL_UP]
        val volDown = byRole[ButtonRole.VOL_DOWN]
        if (useVolumeKeys && (volUp != null || volDown != null)) {
            onRegisterVolumeHandler { isUp ->
                val target = if (isUp) volUp else volDown
                if (target != null) {
                    irTransmitter.transmit(target.signal, target.name)
                    true
                } else {
                    false
                }
            }
        } else {
            onRegisterVolumeHandler(null)
        }
        onDispose { onRegisterVolumeHandler(null) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(enabled = store.devices.size > 1) { switcherExpanded = true },
                        ) {
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = device?.name ?: "",
                                        style = MaterialTheme.typography.titleLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (store.devices.size > 1) {
                                        Icon(
                                            Icons.Rounded.KeyboardArrowDown,
                                            contentDescription = stringResource(R.string.switch_device),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                                if (device != null) {
                                    Text(
                                        text = "${device.brand} · ${device.model}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        DropdownMenu(
                            expanded = switcherExpanded,
                            onDismissRequest = { switcherExpanded = false },
                        ) {
                            for (d in store.devices) {
                                DropdownMenuItem(
                                    text = { Text(d.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingIcon = {
                                        Icon(
                                            categoryIcon(d.categoryId),
                                            contentDescription = null,
                                            tint = categoryAccentColor(d.categoryId),
                                        )
                                    },
                                    onClick = {
                                        switcherExpanded = false
                                        if (d.id != deviceId) onSwitchDevice(d.id)
                                    },
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (device != null) {
                        IconButton(onClick = ::toggleLockScreenPin) {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = stringResource(R.string.pin_lockscreen),
                                tint = if (isPinned) PulseColors.Cyan else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IrLedStatusPill(
                        hasIrEmitter = irTransmitter.hasIrEmitter,
                        lastPulse = lastPulse,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
            )
        },
    ) { padding ->
        if (loading || device == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PulseColors.Crimson)
            }
            return@Scaffold
        }

        // Check if this device uses our Stateful Smart AC Engine!
        val smartAcProto = SmartAcProtocol.fromId(device.smartAcProtocolId)
        if (smartAcProto != null) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                StatefulSmartAcBody(
                    protocol = smartAcProto,
                    irTransmitter = irTransmitter,
                )
            }
            return@Scaffold
        }

        val isAc = device.categoryId == "ac"
        val isLed = device.categoryId == "led"
        val isCamera = device.categoryId == "camera"

        val hasNumpad = remember(byRole) {
            (0..9).any { byRole.containsKey(ButtonRole.valueOf("NUM_$it")) }
        }
        val hasMedia = remember(byRole) {
            listOf(ButtonRole.PLAY, ButtonRole.PAUSE, ButtonRole.STOP, ButtonRole.REW, ButtonRole.FF, ButtonRole.PREV, ButtonRole.NEXT)
                .any { byRole.containsKey(it) }
        }

        val tabs = remember(isAc, isLed, isCamera, hasNumpad, hasMedia) {
            buildList {
                add("main")
                if (!isAc && !isLed && !isCamera && hasNumpad) add("numpad")
                if (!isAc && !isLed && !isCamera && hasMedia) add("media")
                add("all")
            }
        }
        var selectedTab by remember(deviceId) { mutableIntStateOf(0) }
        val activeTabKey = tabs.getOrElse(selectedTab) { "main" }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SecondaryTabRow(
                selectedTabIndex = selectedTab.coerceIn(0, tabs.lastIndex),
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                tabs.forEachIndexed { idx, key ->
                    val label = when (key) {
                        "main" -> stringResource(R.string.tab_main)
                        "numpad" -> stringResource(R.string.tab_numpad)
                        "media" -> stringResource(R.string.tab_media)
                        else -> "${stringResource(R.string.tab_all)} (${buttons.size})"
                    }
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }

            when (activeTabKey) {
                "main" -> {
                    when {
                        isAc -> AcRemoteBody(buttons = buttons, byRole = byRole, onFire = ::fire, onInspect = { inspectedButton = it })
                        isLed -> LedRemoteBody(buttons = buttons, byRole = byRole, onFire = ::fire, onInspect = { inspectedButton = it })
                        isCamera -> CameraRemoteBody(buttons = buttons, byRole = byRole, onFire = ::fire, onInspect = { inspectedButton = it })
                        else -> StandardErgonomicRemoteBody(buttons = buttons, byRole = byRole, onFire = ::fire, onInspect = { inspectedButton = it })
                    }
                }
                "numpad" -> NumpadRemoteBody(byRole = byRole, onFire = ::fire, onInspect = { inspectedButton = it })
                "media" -> MediaRemoteBody(byRole = byRole, onFire = ::fire, onInspect = { inspectedButton = it })
                else -> AllButtonsGrid(buttons = buttons, onFire = ::fire, onInspect = { inspectedButton = it })
            }
        }
    }

    inspectedButton?.let { btn ->
        SignalInspectorSheet(
            button = btn,
            onTest = { irTransmitter.transmit(btn.signal, btn.name) },
            onSaveToCustom = {
                userRepository.saveCustomCode(
                    CustomIrCode(
                        id = UUID.randomUUID().toString(),
                        name = "${device?.name ?: ""} ${btn.name}".trim(),
                        signal = btn.signal,
                    ),
                )
            },
            onDismiss = { inspectedButton = null },
        )
    }
}

/**
 * Full Computational Stateful Smart AC Thermostat UI (Coolix / Gree / LG / Toshiba).
 */
@Composable
private fun StatefulSmartAcBody(
    protocol: SmartAcProtocol,
    irTransmitter: IrTransmitter,
) {
    var acState by remember { mutableStateOf(AcState(powerOn = true, mode = AcMode.COOL, tempC = 24, fanSpeed = AcFanSpeed.AUTO, swing = false)) }
    val lastFrame = remember(protocol, acState) { AcProtocolEngine.encode(protocol, acState) }

    fun send(next: AcState, label: String, swingOnly: Boolean = false) {
        acState = next
        val frame = AcProtocolEngine.encode(protocol, next, swingToggleOnly = swingOnly)
        irTransmitter.transmit(frame, label)
    }

    val modeAccent = when {
        !acState.powerOn -> MaterialTheme.colorScheme.outline
        acState.mode == AcMode.HEAT -> PulseColors.Amber
        acState.mode == AcMode.DRY -> PulseColors.Violet
        acState.mode == AcMode.FAN -> PulseColors.Emerald
        else -> PulseColors.Cyan
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Digital Thermostat LCD
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.5.dp, modeAccent.copy(alpha = 0.6f)),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (acState.powerOn) acState.mode.name else "OFF",
                            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                            color = modeAccent,
                        )
                        Text(
                            text = "${stringResource(R.string.ac_speed)}: ${acState.fanSpeed.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            TactileRemoteButton(
                                onPress = {
                                    val next = acState.copy(powerOn = true, tempC = (acState.tempC - 1).coerceIn(16, 30))
                                    send(next, "${next.mode} ${next.tempC}°C")
                                },
                                icon = Icons.Rounded.Remove,
                                shape = CircleShape,
                                height = 58.dp,
                                modifier = Modifier.size(58.dp),
                            )
                            Text(
                                text = if (acState.powerOn) "${acState.tempC}°C" else "--",
                                fontSize = 52.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (acState.powerOn) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TactileRemoteButton(
                                onPress = {
                                    val next = acState.copy(powerOn = true, tempC = (acState.tempC + 1).coerceIn(16, 30))
                                    send(next, "${next.mode} ${next.tempC}°C")
                                },
                                icon = Icons.Rounded.Add,
                                shape = CircleShape,
                                height = 58.dp,
                                modifier = Modifier.size(58.dp),
                            )
                        }
                    }

                    WaveformCanvas(
                        patternUs = lastFrame.patternUs.toIntArray(),
                        modifier = Modifier.height(50.dp),
                    )
                }
            }
        }

        // Power ON / OFF
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TactileRemoteButton(
                    onPress = { send(acState.copy(powerOn = true), "AC ON ${acState.tempC}°C") },
                    icon = Icons.Rounded.PowerSettingsNew,
                    label = stringResource(R.string.power_on),
                    containerColor = PulseColors.Emerald,
                    contentColor = Color.White,
                    height = 58.dp,
                    modifier = Modifier.weight(1f),
                )
                TactileRemoteButton(
                    onPress = { send(acState.copy(powerOn = false), "AC OFF") },
                    icon = Icons.Rounded.PowerSettingsNew,
                    label = stringResource(R.string.power_off),
                    containerColor = PulseColors.Crimson,
                    contentColor = Color.White,
                    height = 58.dp,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Mode row
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val modes = listOf(
                    Triple(AcMode.COOL, stringResource(R.string.ac_cool), Icons.Rounded.AcUnit),
                    Triple(AcMode.HEAT, stringResource(R.string.ac_heat), Icons.Rounded.WbSunny),
                    Triple(AcMode.DRY, stringResource(R.string.ac_dry), Icons.Rounded.WaterDrop),
                    Triple(AcMode.FAN, stringResource(R.string.ac_fan), Icons.Rounded.Air),
                    Triple(AcMode.AUTO, stringResource(R.string.ac_auto), Icons.Rounded.AutoMode),
                )
                for ((mode, label, icon) in modes) {
                    val selected = acState.powerOn && acState.mode == mode
                    TactileRemoteButton(
                        onPress = { send(acState.copy(powerOn = true, mode = mode), "${mode.name} ${acState.tempC}°C") },
                        icon = icon,
                        label = label,
                        containerColor = if (selected) modeAccent.copy(alpha = 0.24f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (selected) modeAccent else MaterialTheme.colorScheme.onSurface,
                        height = 60.dp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Fan Speed selector
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.ac_speed), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val fans = listOf(
                        AcFanSpeed.AUTO to stringResource(R.string.ac_fan_auto),
                        AcFanSpeed.LOW to stringResource(R.string.ac_fan_low),
                        AcFanSpeed.MEDIUM to stringResource(R.string.ac_fan_med),
                        AcFanSpeed.HIGH to stringResource(R.string.ac_fan_high),
                    )
                    for ((speed, label) in fans) {
                        FilterChip(
                            selected = acState.fanSpeed == speed,
                            onClick = { send(acState.copy(powerOn = true, fanSpeed = speed), "Fan ${speed.name}") },
                            label = { Text(label, maxLines = 1) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // Swing Toggle
        item {
            TactileRemoteButton(
                onPress = {
                    val next = acState.copy(swing = !acState.swing)
                    val useSwingOnly = protocol == SmartAcProtocol.COOLIX || protocol == SmartAcProtocol.LG
                    send(next, "Swing ${if (next.swing) "ON" else "OFF"}", swingOnly = useSwingOnly)
                },
                icon = Icons.Rounded.SwapVert,
                label = "${stringResource(R.string.ac_swing)} (${if (acState.swing) "ON" else "OFF"})",
                containerColor = if (acState.swing) PulseColors.Cyan.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (acState.swing) PulseColors.Cyan else MaterialTheme.colorScheme.onSurface,
                height = 54.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StandardErgonomicRemoteBody(
    buttons: List<IrButton>,
    byRole: Map<ButtonRole, IrButton>,
    onFire: (IrButton?) -> Unit,
    onInspect: (IrButton) -> Unit,
) {
    val powerBtn = byRole[ButtonRole.POWER] ?: byRole[ButtonRole.POWER_ON]
    val inputBtn = byRole[ButtonRole.INPUT]
    val muteBtn = byRole[ButtonRole.MUTE]

    val standardCount = listOf(
        ButtonRole.POWER, ButtonRole.VOL_UP, ButtonRole.CH_UP, ButtonRole.UP, ButtonRole.OK, ButtonRole.MUTE,
    ).count { byRole.containsKey(it) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top row: Power / Input / Mute
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TactileRemoteButton(
                    onPress = { onFire(powerBtn) },
                    onLongPress = powerBtn?.let { b -> { onInspect(b) } },
                    icon = Icons.Rounded.PowerSettingsNew,
                    label = stringResource(R.string.power),
                    containerColor = PulseColors.Crimson,
                    contentColor = Color.White,
                    enabled = powerBtn != null,
                    shape = RoundedCornerShape(18.dp),
                    height = 60.dp,
                    modifier = Modifier.weight(1f),
                )
                TactileRemoteButton(
                    onPress = { onFire(inputBtn) },
                    onLongPress = inputBtn?.let { b -> { onInspect(b) } },
                    icon = Icons.AutoMirrored.Rounded.Input,
                    label = stringResource(R.string.input),
                    enabled = inputBtn != null,
                    shape = RoundedCornerShape(18.dp),
                    height = 60.dp,
                    modifier = Modifier.weight(1f),
                )
                TactileRemoteButton(
                    onPress = { onFire(muteBtn) },
                    onLongPress = muteBtn?.let { b -> { onInspect(b) } },
                    icon = Icons.AutoMirrored.Rounded.VolumeOff,
                    label = stringResource(R.string.mute),
                    enabled = muteBtn != null,
                    shape = RoundedCornerShape(18.dp),
                    height = 60.dp,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (byRole.containsKey(ButtonRole.POWER_ON) && byRole.containsKey(ButtonRole.POWER_OFF)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TactileRemoteButton(
                        onPress = { onFire(byRole[ButtonRole.POWER_ON]) },
                        onLongPress = byRole[ButtonRole.POWER_ON]?.let { b -> { onInspect(b) } },
                        label = stringResource(R.string.power_on),
                        containerColor = PulseColors.Emerald.copy(alpha = 0.22f),
                        contentColor = PulseColors.Emerald,
                        modifier = Modifier.weight(1f),
                        height = 46.dp,
                    )
                    TactileRemoteButton(
                        onPress = { onFire(byRole[ButtonRole.POWER_OFF]) },
                        onLongPress = byRole[ButtonRole.POWER_OFF]?.let { b -> { onInspect(b) } },
                        label = stringResource(R.string.power_off),
                        containerColor = PulseColors.Crimson.copy(alpha = 0.22f),
                        contentColor = PulseColors.Crimson,
                        modifier = Modifier.weight(1f),
                        height = 46.dp,
                    )
                }
            }
        }

        // Physical LTR spatial layout for VOL — D-Pad — CH so hardware muscle memory never mirrors!
        if (standardCount >= 2) {
            item {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RockerControl(
                            title = stringResource(R.string.vol),
                            onUp = byRole[ButtonRole.VOL_UP]?.let { b -> { onFire(b) } },
                            onDown = byRole[ButtonRole.VOL_DOWN]?.let { b -> { onFire(b) } },
                        )
                        CircularDPad(
                            onUp = byRole[ButtonRole.UP]?.let { b -> { onFire(b) } },
                            onDown = byRole[ButtonRole.DOWN]?.let { b -> { onFire(b) } },
                            onLeft = byRole[ButtonRole.LEFT]?.let { b -> { onFire(b) } },
                            onRight = byRole[ButtonRole.RIGHT]?.let { b -> { onFire(b) } },
                            onOk = byRole[ButtonRole.OK]?.let { b -> { onFire(b) } },
                        )
                        RockerControl(
                            title = stringResource(R.string.ch),
                            onUp = byRole[ButtonRole.CH_UP]?.let { b -> { onFire(b) } },
                            onDown = byRole[ButtonRole.CH_DOWN]?.let { b -> { onFire(b) } },
                        )
                    }
                }
            }

            item {
                val homeOrGuide = byRole[ButtonRole.HOME] ?: byRole[ButtonRole.GUIDE]
                val exitOrInfo = byRole[ButtonRole.EXIT] ?: byRole[ButtonRole.INFO]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TactileRemoteButton(
                        onPress = { onFire(byRole[ButtonRole.BACK]) },
                        onLongPress = byRole[ButtonRole.BACK]?.let { b -> { onInspect(b) } },
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        label = stringResource(R.string.back),
                        enabled = byRole.containsKey(ButtonRole.BACK),
                        modifier = Modifier.weight(1f),
                    )
                    TactileRemoteButton(
                        onPress = { onFire(homeOrGuide) },
                        onLongPress = homeOrGuide?.let { b -> { onInspect(b) } },
                        icon = Icons.Rounded.Home,
                        label = stringResource(R.string.home),
                        enabled = homeOrGuide != null,
                        modifier = Modifier.weight(1f),
                    )
                    TactileRemoteButton(
                        onPress = { onFire(byRole[ButtonRole.MENU]) },
                        onLongPress = byRole[ButtonRole.MENU]?.let { b -> { onInspect(b) } },
                        icon = Icons.Rounded.Menu,
                        label = stringResource(R.string.menu),
                        enabled = byRole.containsKey(ButtonRole.MENU),
                        modifier = Modifier.weight(1f),
                    )
                    TactileRemoteButton(
                        onPress = { onFire(exitOrInfo) },
                        onLongPress = exitOrInfo?.let { b -> { onInspect(b) } },
                        icon = if (byRole.containsKey(ButtonRole.EXIT)) Icons.Rounded.Close else Icons.Rounded.Info,
                        label = if (byRole.containsKey(ButtonRole.EXIT)) stringResource(R.string.exit) else stringResource(R.string.info),
                        enabled = exitOrInfo != null,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            val colorRoles = listOf(
                ButtonRole.COLOR_RED to Color(0xFFEF4444),
                ButtonRole.COLOR_GREEN to Color(0xFF22C55E),
                ButtonRole.COLOR_YELLOW to Color(0xFFEAB308),
                ButtonRole.COLOR_BLUE to Color(0xFF3B82F6),
            )
            if (colorRoles.any { byRole.containsKey(it.first) }) {
                item {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            for ((role, color) in colorRoles) {
                                TactileRemoteButton(
                                    onPress = { onFire(byRole[role]) },
                                    onLongPress = byRole[role]?.let { b -> { onInspect(b) } },
                                    label = "",
                                    containerColor = color.copy(alpha = 0.85f),
                                    contentColor = Color.White,
                                    enabled = byRole.containsKey(role),
                                    shape = RoundedCornerShape(999.dp),
                                    height = 34.dp,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    buttons.take(24).chunked(2).forEach { rowBtns ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (b in rowBtns) {
                                TactileRemoteButton(
                                    onPress = { onFire(b) },
                                    onLongPress = { onInspect(b) },
                                    label = b.name,
                                    modifier = Modifier.weight(1f),
                                    height = 56.dp,
                                )
                            }
                            if (rowBtns.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AcRemoteBody(
    buttons: List<IrButton>,
    byRole: Map<ButtonRole, IrButton>,
    onFire: (IrButton?) -> Unit,
    onInspect: (IrButton) -> Unit,
) {
    val presetRegex = remember { Regex("""(?i)(cool|heat|dry|fan|auto)[_\s-]*(\d{2})""") }
    val presets = remember(buttons) {
        buttons.mapNotNull { b ->
            val m = presetRegex.find(b.name)
            if (m != null) Triple(m.groupValues[1].lowercase(), m.groupValues[2].toInt(), b) else null
        }
    }
    var selectedMode by remember { mutableStateOf("cool") }
    var targetTemp by remember { mutableIntStateOf(24) }

    fun findBestPreset(mode: String, temp: Int): IrButton? {
        return presets.firstOrNull { it.first == mode && it.second == temp }?.third
            ?: presets.filter { it.first == mode }.minByOrNull { kotlin.math.abs(it.second - temp) }?.third
    }

    val powerOn = byRole[ButtonRole.POWER_ON] ?: byRole[ButtonRole.POWER]
    val powerOff = byRole[ButtonRole.POWER_OFF] ?: buttons.firstOrNull { it.name.equals("off", true) } ?: byRole[ButtonRole.POWER]
    val tempUpBtn = byRole[ButtonRole.TEMP_UP]
    val tempDownBtn = byRole[ButtonRole.TEMP_DOWN]

    val modeAccent = when (selectedMode) {
        "heat" -> PulseColors.Amber
        "dry" -> PulseColors.Violet
        "fan" -> PulseColors.Emerald
        else -> PulseColors.Cyan
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.5.dp, modeAccent.copy(alpha = 0.55f)),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = selectedMode.uppercase(),
                        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                        color = modeAccent,
                    )
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            TactileRemoteButton(
                                onPress = {
                                    targetTemp = (targetTemp - 1).coerceIn(16, 30)
                                    onFire(tempDownBtn ?: findBestPreset(selectedMode, targetTemp))
                                },
                                icon = Icons.Rounded.Remove,
                                enabled = tempDownBtn != null || presets.isNotEmpty(),
                                shape = CircleShape,
                                height = 56.dp,
                                modifier = Modifier.size(56.dp),
                            )
                            Text(
                                text = "${targetTemp}°C",
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            TactileRemoteButton(
                                onPress = {
                                    targetTemp = (targetTemp + 1).coerceIn(16, 30)
                                    onFire(tempUpBtn ?: findBestPreset(selectedMode, targetTemp))
                                },
                                icon = Icons.Rounded.Add,
                                enabled = tempUpBtn != null || presets.isNotEmpty(),
                                shape = CircleShape,
                                height = 56.dp,
                                modifier = Modifier.size(56.dp),
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TactileRemoteButton(
                    onPress = { onFire(powerOn ?: findBestPreset(selectedMode, targetTemp)) },
                    onLongPress = (powerOn ?: findBestPreset(selectedMode, targetTemp))?.let { b -> { onInspect(b) } },
                    icon = Icons.Rounded.PowerSettingsNew,
                    label = stringResource(R.string.power_on),
                    containerColor = PulseColors.Emerald,
                    contentColor = Color.White,
                    enabled = powerOn != null || presets.isNotEmpty(),
                    height = 58.dp,
                    modifier = Modifier.weight(1f),
                )
                TactileRemoteButton(
                    onPress = { onFire(powerOff) },
                    onLongPress = powerOff?.let { b -> { onInspect(b) } },
                    icon = Icons.Rounded.PowerSettingsNew,
                    label = stringResource(R.string.power_off),
                    containerColor = PulseColors.Crimson,
                    contentColor = Color.White,
                    enabled = powerOff != null,
                    height = 58.dp,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val modes = listOf(
                    Triple("cool", stringResource(R.string.ac_cool), Icons.Rounded.AcUnit to (byRole[ButtonRole.AC_COOL] ?: findBestPreset("cool", targetTemp))),
                    Triple("heat", stringResource(R.string.ac_heat), Icons.Rounded.WbSunny to (byRole[ButtonRole.AC_HEAT] ?: findBestPreset("heat", targetTemp))),
                    Triple("dry", stringResource(R.string.ac_dry), Icons.Rounded.WaterDrop to (byRole[ButtonRole.AC_DRY] ?: findBestPreset("dry", targetTemp))),
                    Triple("auto", stringResource(R.string.ac_auto), Icons.Rounded.AutoMode to (byRole[ButtonRole.AC_AUTO] ?: byRole[ButtonRole.MODE])),
                )
                for ((modeKey, label, pair) in modes) {
                    val (icon, btn) = pair
                    TactileRemoteButton(
                        onPress = {
                            selectedMode = modeKey
                            onFire(btn)
                        },
                        onLongPress = btn?.let { b -> { onInspect(b) } },
                        icon = icon,
                        label = label,
                        containerColor = if (selectedMode == modeKey) modeAccent.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (selectedMode == modeKey) modeAccent else MaterialTheme.colorScheme.onSurface,
                        enabled = btn != null,
                        height = 60.dp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TactileRemoteButton(
                    onPress = { onFire(byRole[ButtonRole.FAN_SPEED]) },
                    onLongPress = byRole[ButtonRole.FAN_SPEED]?.let { b -> { onInspect(b) } },
                    icon = Icons.Rounded.Air,
                    label = stringResource(R.string.ac_speed),
                    enabled = byRole.containsKey(ButtonRole.FAN_SPEED),
                    modifier = Modifier.weight(1f),
                )
                TactileRemoteButton(
                    onPress = { onFire(byRole[ButtonRole.SWING]) },
                    onLongPress = byRole[ButtonRole.SWING]?.let { b -> { onInspect(b) } },
                    icon = Icons.Rounded.SwapVert,
                    label = stringResource(R.string.ac_swing),
                    enabled = byRole.containsKey(ButtonRole.SWING),
                    modifier = Modifier.weight(1f),
                )
                TactileRemoteButton(
                    onPress = { onFire(byRole[ButtonRole.TIMER]) },
                    onLongPress = byRole[ButtonRole.TIMER]?.let { b -> { onInspect(b) } },
                    icon = Icons.Rounded.Timer,
                    label = stringResource(R.string.ac_timer),
                    enabled = byRole.containsKey(ButtonRole.TIMER),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                buttons.take(12).chunked(3).forEach { rowBtns ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (b in rowBtns) {
                            TactileRemoteButton(
                                onPress = { onFire(b) },
                                onLongPress = { onInspect(b) },
                                label = b.name,
                                height = 46.dp,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - rowBtns.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LedRemoteBody(
    buttons: List<IrButton>,
    byRole: Map<ButtonRole, IrButton>,
    onFire: (IrButton?) -> Unit,
    onInspect: (IrButton) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TactileRemoteButton(
                    onPress = { onFire(byRole[ButtonRole.BRIGHT_UP]) },
                    icon = Icons.Rounded.BrightnessHigh,
                    enabled = byRole.containsKey(ButtonRole.BRIGHT_UP),
                    repeatable = true,
                    modifier = Modifier.weight(1f),
                )
                TactileRemoteButton(
                    onPress = { onFire(byRole[ButtonRole.BRIGHT_DOWN]) },
                    icon = Icons.Rounded.BrightnessLow,
                    enabled = byRole.containsKey(ButtonRole.BRIGHT_DOWN),
                    repeatable = true,
                    modifier = Modifier.weight(1f),
                )
                TactileRemoteButton(
                    onPress = { onFire(byRole[ButtonRole.POWER_OFF] ?: byRole[ButtonRole.POWER]) },
                    label = stringResource(R.string.power_off),
                    containerColor = Color(0xFF334155),
                    contentColor = Color.White,
                    enabled = byRole.containsKey(ButtonRole.POWER_OFF) || byRole.containsKey(ButtonRole.POWER),
                    modifier = Modifier.weight(1f),
                )
                TactileRemoteButton(
                    onPress = { onFire(byRole[ButtonRole.POWER_ON] ?: byRole[ButtonRole.POWER]) },
                    label = stringResource(R.string.power_on),
                    containerColor = PulseColors.Crimson,
                    contentColor = Color.White,
                    enabled = byRole.containsKey(ButtonRole.POWER_ON) || byRole.containsKey(ButtonRole.POWER),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val swatches = listOf(
                        Triple(ButtonRole.COLOR_RED, "R", Color(0xFFEF4444)),
                        Triple(ButtonRole.COLOR_GREEN, "G", Color(0xFF22C55E)),
                        Triple(ButtonRole.COLOR_BLUE, "B", Color(0xFF3B82F6)),
                        Triple(ButtonRole.COLOR_WHITE, "W", Color(0xFFF8FAFC)),
                    )
                    for ((role, text, bg) in swatches) {
                        TactileRemoteButton(
                            onPress = { onFire(byRole[role]) },
                            onLongPress = byRole[role]?.let { b -> { onInspect(b) } },
                            label = text,
                            containerColor = bg,
                            contentColor = if (role == ButtonRole.COLOR_WHITE) Color.Black else Color.White,
                            enabled = byRole.containsKey(role),
                            shape = CircleShape,
                            height = 64.dp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                buttons.chunked(3).forEach { rowBtns ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (b in rowBtns) {
                            TactileRemoteButton(
                                onPress = { onFire(b) },
                                onLongPress = { onInspect(b) },
                                label = b.name,
                                height = 50.dp,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - rowBtns.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraRemoteBody(
    buttons: List<IrButton>,
    byRole: Map<ButtonRole, IrButton>,
    onFire: (IrButton?) -> Unit,
    onInspect: (IrButton) -> Unit,
) {
    val shutter = byRole[ButtonRole.SHUTTER] ?: buttons.firstOrNull()
    val shutter2s = byRole[ButtonRole.SHUTTER_2S] ?: buttons.getOrNull(1)
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TactileRemoteButton(
            onPress = { onFire(shutter) },
            onLongPress = shutter?.let { b -> { onInspect(b) } },
            icon = Icons.Rounded.CameraAlt,
            label = shutter?.name ?: "SHUTTER",
            containerColor = PulseColors.Crimson,
            contentColor = Color.White,
            enabled = shutter != null,
            shape = CircleShape,
            height = 148.dp,
            modifier = Modifier.size(148.dp),
        )
        if (shutter2s != null) {
            Spacer(Modifier.height(24.dp))
            TactileRemoteButton(
                onPress = { onFire(shutter2s) },
                onLongPress = { onInspect(shutter2s) },
                icon = Icons.Rounded.Timer,
                label = shutter2s.name,
                height = 56.dp,
                modifier = Modifier.fillMaxWidth(0.7f),
            )
        }
    }
}

@Composable
private fun NumpadRemoteBody(
    byRole: Map<ButtonRole, IrButton>,
    onFire: (IrButton?) -> Unit,
    onInspect: (IrButton) -> Unit,
) {
    val rows = listOf(
        listOf(ButtonRole.NUM_1 to "1", ButtonRole.NUM_2 to "2", ButtonRole.NUM_3 to "3"),
        listOf(ButtonRole.NUM_4 to "4", ButtonRole.NUM_5 to "5", ButtonRole.NUM_6 to "6"),
        listOf(ButtonRole.NUM_7 to "7", ButtonRole.NUM_8 to "8", ButtonRole.NUM_9 to "9"),
        listOf(ButtonRole.GUIDE to stringResource(R.string.guide), ButtonRole.NUM_0 to "0", ButtonRole.LAST_CH to "LAST"),
    )
    // Strict physical LTR layout so 1-2-3 NEVER flips to 3-2-1 in Arabic!
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
            for (row in rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    for ((role, text) in row) {
                        TactileRemoteButton(
                            onPress = { onFire(byRole[role]) },
                            onLongPress = byRole[role]?.let { b -> { onInspect(b) } },
                            label = text,
                            enabled = byRole.containsKey(role),
                            height = 66.dp,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaRemoteBody(
    byRole: Map<ButtonRole, IrButton>,
    onFire: (IrButton?) -> Unit,
    onInspect: (IrButton) -> Unit,
) {
    val rows = listOf(
        listOf(
            Triple(ButtonRole.PREV, Icons.Rounded.SkipPrevious, "Prev"),
            Triple(ButtonRole.PLAY, Icons.Rounded.PlayArrow, "Play"),
            Triple(ButtonRole.NEXT, Icons.Rounded.SkipNext, "Next"),
        ),
        listOf(
            Triple(ButtonRole.REW, Icons.Rounded.FastRewind, "Rew"),
            Triple(ButtonRole.PAUSE, Icons.Rounded.Pause, "Pause"),
            Triple(ButtonRole.FF, Icons.Rounded.FastForward, "FF"),
        ),
        listOf(
            Triple(ButtonRole.REC, Icons.Rounded.FiberManualRecord, "Rec"),
            Triple(ButtonRole.STOP, Icons.Rounded.Stop, "Stop"),
            Triple(ButtonRole.EJECT, Icons.Rounded.Eject, "Eject"),
        ),
    )
    // Strict physical LTR layout so Prev / Play / Next & Rew / Pause / FF stay physically correct!
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
            for (row in rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    for ((role, icon, label) in row) {
                        TactileRemoteButton(
                            onPress = { onFire(byRole[role]) },
                            onLongPress = byRole[role]?.let { b -> { onInspect(b) } },
                            icon = icon,
                            label = label,
                            contentColor = if (role == ButtonRole.REC) PulseColors.Crimson else MaterialTheme.colorScheme.onSurface,
                            enabled = byRole.containsKey(role),
                            height = 70.dp,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AllButtonsGrid(
    buttons: List<IrButton>,
    onFire: (IrButton?) -> Unit,
    onInspect: (IrButton) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(buttons, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) buttons else buttons.filter { it.name.lowercase().contains(q) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.search)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filtered, key = { "${it.id}_${it.name}" }) { btn ->
                TactileRemoteButton(
                    onPress = { onFire(btn) },
                    onLongPress = { onInspect(btn) },
                    label = btn.name,
                    height = 56.dp,
                    shape = RoundedCornerShape(14.dp),
                )
            }
        }
    }
}
