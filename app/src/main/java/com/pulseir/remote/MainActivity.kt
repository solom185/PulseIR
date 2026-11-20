package com.pulseir.remote

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.pulseir.remote.data.AppLanguage
import com.pulseir.remote.data.AppPreferences
import com.pulseir.remote.ui.PulseIRApp
import com.pulseir.remote.ui.theme.PulseIRTheme
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var activeVolumeKeyHandler: ((isUp: Boolean) -> Boolean)? = null
    private val externalOpenDevice = MutableStateFlow<String?>(null)

    override fun attachBaseContext(newBase: Context) {
        val prefs = AppPreferences(newBase)
        val lang = prefs.settings.value.language
        super.attachBaseContext(wrapLocaleContext(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        val app = application as PulseIRApplication

        setContent {
            val settings by app.preferences.settings.collectAsState()
            val pendingDevice by externalOpenDevice.collectAsState()

            PulseIRTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
            ) {
                PulseIRApp(
                    app = app,
                    externalOpenDeviceId = pendingDevice,
                    onConsumeExternalOpen = { externalOpenDevice.value = null },
                    onLanguageChanged = { recreate() },
                    onRegisterVolumeHandler = { handler -> activeVolumeKeyHandler = handler },
                    onLockScreenOverlayChanged = { show -> applyShowOnLockScreen(show) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val devId = intent?.getStringExtra(EXTRA_OPEN_DEVICE_ID)
        if (!devId.isNullOrBlank()) {
            externalOpenDevice.value = devId
        }
    }

    private fun applyShowOnLockScreen(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(enabled)
        } else {
            @Suppress("DEPRECATION")
            if (enabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handler = activeVolumeKeyHandler
        if (handler != null && (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                val handled = handler(event.keyCode == KeyEvent.KEYCODE_VOLUME_UP)
                if (handled) return true
            } else if (event.action == KeyEvent.ACTION_UP) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        const val EXTRA_OPEN_DEVICE_ID = "extra_open_device_id"

        fun wrapLocaleContext(base: Context, language: AppLanguage): Context {
            if (language == AppLanguage.SYSTEM) return base
            val locale = Locale.forLanguageTag(language.tag)
            Locale.setDefault(locale)
            val config = Configuration(base.resources.configuration)
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            return base.createConfigurationContext(config)
        }
    }
}
