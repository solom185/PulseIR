package com.pulseir.remote.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pulseir.remote.BuildConfig
import com.pulseir.remote.R
import com.pulseir.remote.data.AppLanguage
import com.pulseir.remote.data.AppPreferences
import com.pulseir.remote.data.CatalogRepository
import com.pulseir.remote.data.ThemeMode
import com.pulseir.remote.data.UserRepository
import com.pulseir.remote.ir.HapticLevel
import com.pulseir.remote.ir.IrTransmitter
import com.pulseir.remote.ui.components.IrLedStatusPill
import com.pulseir.remote.ui.theme.PulseColors

@Composable
fun SettingsScreen(
    preferences: AppPreferences,
    catalogRepository: CatalogRepository,
    userRepository: UserRepository,
    irTransmitter: IrTransmitter,
    onLanguageChanged: (AppLanguage) -> Unit,
) {
    val context = LocalContext.current
    val settings by preferences.settings.collectAsState()
    val lastPulse by irTransmitter.lastPulse.collectAsState()
    var stats by remember { mutableStateOf(0 to 0) }

    LaunchedEffect(Unit) {
        stats = catalogRepository.getCatalogStats()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(userRepository.exportBackupJson().toByteArray(Charsets.UTF_8))
                }
                true
            }.getOrDefault(false)
            if (ok) {
                Toast.makeText(context, context.getString(R.string.backup_exported), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            val ok = runCatching {
                val txt = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return@runCatching false
                userRepository.importBackupJson(txt)
            }.getOrDefault(false)
            if (ok) {
                Toast.makeText(context, context.getString(R.string.backup_restored), Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineMedium)
                IrLedStatusPill(hasIrEmitter = irTransmitter.hasIrEmitter, lastPulse = lastPulse)
            }
        }

        // Appearance & Language
        item {
            SettingsCard(title = stringResource(R.string.settings_appearance), icon = Icons.Rounded.Palette) {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = settings.themeMode == ThemeMode.DARK,
                        onClick = { preferences.setThemeMode(ThemeMode.DARK) },
                        label = { Text(stringResource(R.string.theme_dark)) },
                    )
                    FilterChip(
                        selected = settings.themeMode == ThemeMode.LIGHT,
                        onClick = { preferences.setThemeMode(ThemeMode.LIGHT) },
                        label = { Text(stringResource(R.string.theme_light)) },
                    )
                    FilterChip(
                        selected = settings.themeMode == ThemeMode.SYSTEM,
                        onClick = { preferences.setThemeMode(ThemeMode.SYSTEM) },
                        label = { Text(stringResource(R.string.theme_system)) },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_dynamic_color), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = settings.dynamicColor, onCheckedChange = { preferences.setDynamicColor(it) })
                }

                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = settings.language == AppLanguage.SYSTEM,
                        onClick = {
                            preferences.setLanguage(AppLanguage.SYSTEM)
                            onLanguageChanged(AppLanguage.SYSTEM)
                        },
                        label = { Text(stringResource(R.string.lang_system)) },
                    )
                    FilterChip(
                        selected = settings.language == AppLanguage.ARABIC,
                        onClick = {
                            preferences.setLanguage(AppLanguage.ARABIC)
                            onLanguageChanged(AppLanguage.ARABIC)
                        },
                        label = { Text(stringResource(R.string.lang_arabic)) },
                    )
                    FilterChip(
                        selected = settings.language == AppLanguage.ENGLISH,
                        onClick = {
                            preferences.setLanguage(AppLanguage.ENGLISH)
                            onLanguageChanged(AppLanguage.ENGLISH)
                        },
                        label = { Text(stringResource(R.string.lang_english)) },
                    )
                }
            }
        }

        // Remote Controls & Comfort (Lock Screen + Hardware Volume Keys + Keep Screen On)
        item {
            SettingsCard(title = stringResource(R.string.settings_controls), icon = Icons.Rounded.SettingsRemote) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(stringResource(R.string.settings_lock_screen), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.settings_lock_screen_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = settings.showOnLockScreen, onCheckedChange = { preferences.setShowOnLockScreen(it) })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(stringResource(R.string.settings_volume_keys), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.settings_volume_keys_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = settings.useVolumeKeys, onCheckedChange = { preferences.setUseVolumeKeys(it) })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(stringResource(R.string.settings_keep_screen_on), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.settings_keep_screen_on_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = settings.keepScreenOn, onCheckedChange = { preferences.setKeepScreenOn(it) })
                }
            }
        }

        // Haptic feedback (vibrates ONLY without firing IR!)
        item {
            SettingsCard(title = stringResource(R.string.settings_haptics), icon = Icons.Rounded.Vibration) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val levels = listOf(
                        HapticLevel.OFF to stringResource(R.string.haptics_off),
                        HapticLevel.LIGHT to stringResource(R.string.haptics_light),
                        HapticLevel.MEDIUM to stringResource(R.string.haptics_medium),
                        HapticLevel.STRONG to stringResource(R.string.haptics_strong),
                    )
                    for ((lvl, label) in levels) {
                        FilterChip(
                            selected = settings.hapticLevel == lvl,
                            onClick = {
                                preferences.setHapticLevel(lvl)
                                irTransmitter.hapticLevel = lvl
                                irTransmitter.testHaptic()
                            },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }

        // Hardware IR Diagnostics
        item {
            SettingsCard(title = stringResource(R.string.settings_hardware), icon = Icons.Rounded.Sensors) {
                val freqText = remember {
                    val ranges = irTransmitter.carrierFrequencyRanges
                    if (ranges.isEmpty()) "—" else ranges.joinToString(", ") { "${it.first / 1000}–${it.last / 1000} kHz" }
                }
                Text(
                    text = if (irTransmitter.hasIrEmitter) stringResource(R.string.ir_ready) else stringResource(R.string.ir_demo_mode),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (irTransmitter.hasIrEmitter) PulseColors.Emerald else PulseColors.Amber,
                )
                Text(
                    text = "${stringResource(R.string.supported_frequencies)}: $freqText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { irTransmitter.sendDiagnosticPulse() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Crimson),
                ) {
                    Icon(Icons.Rounded.Bolt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.send_test_pulse))
                }
                Text(
                    text = stringResource(R.string.test_pulse_tip),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Offline Backup & Restore
        item {
            SettingsCard(title = stringResource(R.string.settings_backup), icon = Icons.Rounded.Download) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { exportLauncher.launch("pulseir_backup.json") },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.export_backup), maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Rounded.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.import_backup), maxLines = 1)
                    }
                }
            }
        }

        // About & Privacy
        item {
            SettingsCard(title = stringResource(R.string.settings_about), icon = Icons.Rounded.Info) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Rounded.WifiOff, contentDescription = null, tint = PulseColors.Emerald, modifier = Modifier.size(18.dp))
                        Text(
                            text = stringResource(R.string.zero_internet_badge),
                            style = MaterialTheme.typography.labelLarge,
                            color = PulseColors.Emerald,
                        )
                    }
                    Text(
                        text = "${stringResource(R.string.app_name)} v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(R.string.about_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (stats.first > 0) {
                    Text(
                        text = stringResource(R.string.catalog_stats, stats.first, stats.second),
                        style = MaterialTheme.typography.labelLarge,
                        color = PulseColors.Cyan,
                    )
                }
            }
        }

        // Author signature footer
        item {
            Text(
                text = stringResource(R.string.made_by_signature),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = PulseColors.Crimson, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}
