package com.echoshield.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.echoshield.core.theme.AppThemeMode

// --- MIDNIGHT TEAL DARK COLOR SCHEME ---
private val MidnightTealDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkBackground,
    primaryContainer = DarkSurface,
    onPrimaryContainer = DarkSecondary,
    secondary = DarkSecondary,
    onSecondary = DarkBackground,
    secondaryContainer = Color(0xFF225751),
    onSecondaryContainer = DarkPrimaryText,
    tertiary = DarkSecondary,
    onTertiary = DarkBackground,
    background = DarkBackground,
    onBackground = DarkPrimaryText,
    surface = DarkSurface,
    onSurface = DarkPrimaryText,
    surfaceVariant = DarkCardElevated,
    onSurfaceVariant = DarkSecondaryText,
    outline = DarkBorder,
    outlineVariant = Color(0xFF254743),
    error = DangerRed,
    onError = Color.White
)

// --- WARM IVORY LIGHT COLOR SCHEME ---
private val WarmIvoryLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9ECE8),
    onPrimaryContainer = LightPrimaryText,
    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3F3EF),
    onSecondaryContainer = LightPrimaryText,
    tertiary = LightSecondary,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightPrimaryText,
    surface = LightSurface,
    onSurface = LightPrimaryText,
    surfaceVariant = LightCardElevated,
    onSurfaceVariant = LightSecondaryText,
    outline = LightBorder,
    outlineVariant = Color(0xFFCDD6D0),
    error = DangerRed,
    onError = Color.White
)

@Composable
fun EchoShieldTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    darkTheme: Boolean = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    },
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MidnightTealDarkColorScheme else WarmIvoryLightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            window?.let {
                it.statusBarColor = colorScheme.background.toArgb()
                it.navigationBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(it, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = EchoTypography,
        shapes = EchoShapes,
        content = content
    )
}
