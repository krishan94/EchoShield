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

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryTeal,
    onPrimary = Color.Black,
    primaryContainer = PrimaryTealDark,
    onPrimaryContainer = Color.White,
    secondary = AccentBlue,
    onSecondary = Color.White,
    secondaryContainer = AccentBlueDark,
    onSecondaryContainer = Color.White,
    tertiary = CyberPurple,
    onTertiary = Color.White,
    background = DarkBackground,
    onBackground = TextPrimaryDark,
    surface = DarkCard,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkCardElevated,
    onSurfaceVariant = TextSecondaryDark,
    outline = DarkSurfaceStroke,
    outlineVariant = Color(0xFF223659),
    error = DangerRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryTealDark,
    onPrimary = Color.White,
    primaryContainer = PrimaryTeal,
    onPrimaryContainer = Color.Black,
    secondary = AccentBlue,
    onSecondary = Color.White,
    secondaryContainer = AccentBlueDark,
    onSecondaryContainer = Color.White,
    tertiary = CyberPurple,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = TextPrimaryLight,
    surface = LightCard,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightCardElevated,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightSurfaceStroke,
    outlineVariant = Color(0xFFCBD5E1),
    error = DangerRed,
    onError = Color.White
)

@Composable
fun EchoShieldTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
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

