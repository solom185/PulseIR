package com.pulseir.remote.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.pulseir.remote.PulseIRApplication
import com.pulseir.remote.R
import com.pulseir.remote.data.AppLanguage
import com.pulseir.remote.ui.components.TopIrBeamGlow
import com.pulseir.remote.ui.screens.CatalogBrowserScreen
import com.pulseir.remote.ui.screens.CustomRemoteScreen
import com.pulseir.remote.ui.screens.HomeScreen
import com.pulseir.remote.ui.screens.LabAndMacrosScreen
import com.pulseir.remote.ui.screens.RemoteControllerScreen
import com.pulseir.remote.ui.screens.SettingsScreen
import com.pulseir.remote.ui.screens.SmartScanScreen

private sealed interface OverlayRoute {
    data object Catalog : OverlayRoute
    data class Remote(val deviceId: String) : OverlayRoute
    data class CustomRemote(val customRemoteId: String) : OverlayRoute
}

private val OverlayRouteSaver = Saver<OverlayRoute?, String>(
    save = { route ->
        when (route) {
            null -> ""
            is OverlayRoute.Catalog -> "catalog"
            is OverlayRoute.Remote -> "remote:${route.deviceId}"
            is OverlayRoute.CustomRemote -> "custom:${route.customRemoteId}"
        }
    },
    restore = { raw ->
        when {
            raw.isEmpty() -> null
            raw == "catalog" -> OverlayRoute.Catalog
            raw.startsWith("remote:") -> OverlayRoute.Remote(raw.removePrefix("remote:"))
            raw.startsWith("custom:") -> OverlayRoute.CustomRemote(raw.removePrefix("custom:"))
            else -> null
        }
    },
)

@Composable
fun PulseIRApp(
    app: PulseIRApplication,
    externalOpenDeviceId: String?,
    onConsumeExternalOpen: () -> Unit,
    onLanguageChanged: (AppLanguage) -> Unit,
    onRegisterVolumeHandler: (((isUp: Boolean) -> Boolean)?) -> Unit,
    onLockScreenOverlayChanged: (Boolean) -> Unit,
) {
    val settings by app.preferences.settings.collectAsState()
    val lastPulse by app.irTransmitter.lastPulse.collectAsState()

    var bottomTab by rememberSaveable { mutableIntStateOf(0) }
    var labInitialTab by rememberSaveable { mutableIntStateOf(0) }
    var overlay by rememberSaveable(stateSaver = OverlayRouteSaver) { mutableStateOf<OverlayRoute?>(null) }

    // Open target device directly when launched from Lock Screen Notification or Quick Settings Tile
    LaunchedEffect(externalOpenDeviceId) {
        if (!externalOpenDeviceId.isNullOrBlank()) {
            overlay = OverlayRoute.Remote(externalOpenDeviceId)
            onConsumeExternalOpen()
        }
    }

    // Automatically show active Remote / Custom Remote OVER the Lock Screen when enabled!
    val isRemoteOpen = overlay is OverlayRoute.Remote || overlay is OverlayRoute.CustomRemote
    LaunchedEffect(isRemoteOpen, settings.showOnLockScreen) {
        onLockScreenOverlayChanged(isRemoteOpen && settings.showOnLockScreen)
    }

    BackHandler(enabled = overlay != null || bottomTab != 0) {
        if (overlay != null) {
            overlay = null
        } else {
            bottomTab = 0
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val route = overlay) {
            is OverlayRoute.Catalog -> {
                CatalogBrowserScreen(
                    catalogRepository = app.catalogRepository,
                    userRepository = app.userRepository,
                    irTransmitter = app.irTransmitter,
                    onBack = { overlay = null },
                    onDeviceSaved = { deviceId -> overlay = OverlayRoute.Remote(deviceId) },
                )
            }
            is OverlayRoute.Remote -> {
                RemoteControllerScreen(
                    deviceId = route.deviceId,
                    userRepository = app.userRepository,
                    irTransmitter = app.irTransmitter,
                    keepScreenOn = settings.keepScreenOn,
                    useVolumeKeys = settings.useVolumeKeys,
                    onRegisterVolumeHandler = onRegisterVolumeHandler,
                    onSwitchDevice = { nextId -> overlay = OverlayRoute.Remote(nextId) },
                    onBack = { overlay = null },
                )
            }
            is OverlayRoute.CustomRemote -> {
                CustomRemoteScreen(
                    customRemoteId = route.customRemoteId,
                    userRepository = app.userRepository,
                    irTransmitter = app.irTransmitter,
                    onBack = { overlay = null },
                )
            }
            null -> {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            NavigationBarItem(
                                selected = bottomTab == 0,
                                onClick = { bottomTab = 0 },
                                icon = { Icon(Icons.Rounded.SettingsRemote, contentDescription = null) },
                                label = { Text(stringResource(R.string.nav_remotes), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                            NavigationBarItem(
                                selected = bottomTab == 1,
                                onClick = { bottomTab = 1 },
                                icon = { Icon(Icons.Rounded.Radar, contentDescription = null) },
                                label = { Text(stringResource(R.string.nav_scan), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                            NavigationBarItem(
                                selected = bottomTab == 2,
                                onClick = {
                                    labInitialTab = 0
                                    bottomTab = 2
                                },
                                icon = { Icon(Icons.Rounded.Bolt, contentDescription = null) },
                                label = { Text(stringResource(R.string.nav_lab), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                            NavigationBarItem(
                                selected = bottomTab == 3,
                                onClick = { bottomTab = 3 },
                                icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                                label = { Text(stringResource(R.string.nav_settings), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    },
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        when (bottomTab) {
                            0 -> HomeScreen(
                                catalogRepository = app.catalogRepository,
                                userRepository = app.userRepository,
                                irTransmitter = app.irTransmitter,
                                onOpenDevice = { overlay = OverlayRoute.Remote(it) },
                                onOpenCustomRemote = { overlay = OverlayRoute.CustomRemote(it) },
                                onOpenCatalog = { overlay = OverlayRoute.Catalog },
                                onOpenSmartScan = { bottomTab = 1 },
                                onOpenImportTab = {
                                    labInitialTab = 3
                                    bottomTab = 2
                                },
                            )
                            1 -> SmartScanScreen(
                                preferences = app.preferences,
                                catalogRepository = app.catalogRepository,
                                userRepository = app.userRepository,
                                irTransmitter = app.irTransmitter,
                                onDeviceSaved = { deviceId ->
                                    bottomTab = 0
                                    overlay = OverlayRoute.Remote(deviceId)
                                },
                            )
                            2 -> LabAndMacrosScreen(
                                initialTab = labInitialTab,
                                userRepository = app.userRepository,
                                irTransmitter = app.irTransmitter,
                                onDeviceImported = { deviceId ->
                                    bottomTab = 0
                                    overlay = OverlayRoute.Remote(deviceId)
                                },
                            )
                            else -> SettingsScreen(
                                preferences = app.preferences,
                                catalogRepository = app.catalogRepository,
                                userRepository = app.userRepository,
                                irTransmitter = app.irTransmitter,
                                onLanguageChanged = onLanguageChanged,
                            )
                        }
                    }
                }
            }
        }

        // Ambient IR emission glow at top bezel toward the phone's physical IR LED
        TopIrBeamGlow(
            lastPulse = lastPulse,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}
