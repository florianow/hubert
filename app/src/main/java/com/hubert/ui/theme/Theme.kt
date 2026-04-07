package com.hubert.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AccentPurple,
    secondary = FrenchBlue,
    tertiary = GermanGold,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = LightBackground,
    onSecondary = LightBackground,
    onTertiary = DarkBackground,
    onBackground = LightBackground,
    onSurface = LightBackground,
)

private val LightColorScheme = lightColorScheme(
    primary = AccentPurple,
    secondary = FrenchBlue,
    tertiary = GermanGold,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = LightBackground,
    onSecondary = LightBackground,
    onTertiary = DarkBackground,
    onBackground = DarkBackground,
    onSurface = DarkBackground,
)

@Composable
fun HubertTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
