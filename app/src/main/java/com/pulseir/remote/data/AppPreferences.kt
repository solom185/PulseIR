package com.pulseir.remote.data

import android.content.Context
import com.pulseir.remote.ir.HapticLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { DARK, LIGHT, SYSTEM }
enum class AppLanguage(val tag: String) { SYSTEM(""), ARABIC("ar"), ENGLISH("en") }

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val hapticLevel: HapticLevel = HapticLevel.MEDIUM,
    val scanSpeedMs: Long = 450L,
    val useVolumeKeys: Boolean = true,
    val keepScreenOn: Boolean = false,
    val showOnLockScreen: Boolean = true,
)

class AppPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("pulseir_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    private fun read(): UserSettings = UserSettings(
        themeMode = ThemeMode.entries.getOrElse(prefs.getInt("theme_mode", ThemeMode.DARK.ordinal)) { ThemeMode.DARK },
        dynamicColor = prefs.getBoolean("dynamic_color", false),
        language = AppLanguage.entries.getOrElse(prefs.getInt("language", AppLanguage.SYSTEM.ordinal)) { AppLanguage.SYSTEM },
        hapticLevel = HapticLevel.fromInt(prefs.getInt("haptic_level", HapticLevel.MEDIUM.ordinal)),
        scanSpeedMs = prefs.getLong("scan_speed_ms", 450L),
        useVolumeKeys = prefs.getBoolean("use_volume_keys", true),
        keepScreenOn = prefs.getBoolean("keep_screen_on", false),
        showOnLockScreen = prefs.getBoolean("show_on_lock_screen", true),
    )

    fun setShowOnLockScreen(enabled: Boolean) {
        prefs.edit().putBoolean("show_on_lock_screen", enabled).apply()
        _settings.value = _settings.value.copy(showOnLockScreen = enabled)
    }

    fun setUseVolumeKeys(enabled: Boolean) {
        prefs.edit().putBoolean("use_volume_keys", enabled).apply()
        _settings.value = _settings.value.copy(useVolumeKeys = enabled)
    }

    fun setKeepScreenOn(enabled: Boolean) {
        prefs.edit().putBoolean("keep_screen_on", enabled).apply()
        _settings.value = _settings.value.copy(keepScreenOn = enabled)
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putInt("theme_mode", mode.ordinal).apply()
        _settings.value = _settings.value.copy(themeMode = mode)
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean("dynamic_color", enabled).apply()
        _settings.value = _settings.value.copy(dynamicColor = enabled)
    }

    fun setLanguage(lang: AppLanguage) {
        prefs.edit().putInt("language", lang.ordinal).apply()
        _settings.value = _settings.value.copy(language = lang)
    }

    fun setHapticLevel(level: HapticLevel) {
        prefs.edit().putInt("haptic_level", level.ordinal).apply()
        _settings.value = _settings.value.copy(hapticLevel = level)
    }

    fun setScanSpeedMs(ms: Long) {
        prefs.edit().putLong("scan_speed_ms", ms).apply()
        _settings.value = _settings.value.copy(scanSpeedMs = ms)
    }

    // Widget mapping: appWidgetId -> deviceId
    fun setWidgetDevice(widgetId: Int, deviceId: String) {
        prefs.edit().putString("widget_device_$widgetId", deviceId).apply()
    }

    fun getWidgetDevice(widgetId: Int): String? = prefs.getString("widget_device_$widgetId", null)

    fun removeWidgetDevice(widgetId: Int) {
        prefs.edit().remove("widget_device_$widgetId").apply()
    }

    // Lock-screen pinned notification remote persistence
    fun setPinnedNotificationDeviceId(deviceId: String?) {
        val editor = prefs.edit()
        if (deviceId == null) {
            editor.remove("pinned_notif_device_id")
        } else {
            editor.putString("pinned_notif_device_id", deviceId)
        }
        editor.apply()
    }

    fun getPinnedNotificationDeviceId(): String? = prefs.getString("pinned_notif_device_id", null)
}
