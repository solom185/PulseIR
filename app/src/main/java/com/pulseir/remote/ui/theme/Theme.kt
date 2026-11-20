package com.pulseir.remote.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pulseir.remote.data.ThemeMode

object PulseColors {
    val Crimson = Color(0xFFFF3B5C)
    val CrimsonDark = Color(0xFFE11D48)
    val Cyan = Color(0xFF00D4FF)
    val CyanDark = Color(0xFF0284C7)
    val Emerald = Color(0xFF10B981)
    val Amber = Color(0xFFFFB020)
    val Violet = Color(0xFF8B5CF6)

    val ObsidianBg = Color(0xFF0B0E14)
    val ObsidianSurface = Color(0xFF141923)
    val ObsidianSurfaceHigh = Color(0xFF1E2636)
    val ObsidianOutline = Color(0xFF2B3548)

    val LightBg = Color(0xFFF3F6FA)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceHigh = Color(0xFFE9EFF6)
    val LightOutline = Color(0xFFCBD5E1)
}

private val ObsidianDarkScheme = darkColorScheme(
    primary = PulseColors.Crimson,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A1020),
    onPrimaryContainer = Color(0xFFFFD9DF),
    secondary = PulseColors.Cyan,
    onSecondary = Color(0xFF001F2A),
    secondaryContainer = Color(0xFF0A3344),
    onSecondaryContainer = Color(0xFFB8F0FF),
    tertiary = PulseColors.Emerald,
    onTertiary = Color(0xFF002116),
    tertiaryContainer = Color(0xFF073B2B),
    onTertiaryContainer = Color(0xFFA7F3D0),
    background = PulseColors.ObsidianBg,
    onBackground = Color(0xFFF1F5F9),
    surface = PulseColors.ObsidianSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = PulseColors.ObsidianSurfaceHigh,
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainerLowest = Color(0xFF080A0F),
    surfaceContainerLow = Color(0xFF10151E),
    surfaceContainer = PulseColors.ObsidianSurface,
    surfaceContainerHigh = PulseColors.ObsidianSurfaceHigh,
    surfaceContainerHighest = Color(0xFF263044),
    outline = PulseColors.ObsidianOutline,
    outlineVariant = Color(0xFF1F2838),
    error = Color(0xFFFF5A5F),
)

private val CrispLightScheme = lightColorScheme(
    primary = PulseColors.CrimsonDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE4E9),
    onPrimaryContainer = Color(0xFF6B0A22),
    secondary = PulseColors.CyanDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF074060),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF064E3B),
    background = PulseColors.LightBg,
    onBackground = Color(0xFF0F172A),
    surface = PulseColors.LightSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = PulseColors.LightSurfaceHigh,
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = PulseColors.LightSurface,
    surfaceContainerHigh = PulseColors.LightSurfaceHigh,
    surfaceContainerHighest = Color(0xFFDCE5F0),
    outline = PulseColors.LightOutline,
    outlineVariant = Color(0xFFE2E8F0),
)

val PulseTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)

@Composable
fun PulseIRTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> ObsidianDarkScheme
        else -> CrispLightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PulseTypography,
        content = content,
    )
}
