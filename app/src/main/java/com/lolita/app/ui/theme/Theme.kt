package com.lolita.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Pink400,
    onPrimary = White,
    primaryContainer = Pink100,
    onPrimaryContainer = Gray800,

    secondary = Lavender,
    onSecondary = Gray800,
    secondaryContainer = Cream,
    onSecondaryContainer = Gray800,

    tertiary = Pink300,
    onTertiary = White,

    background = Pink30,
    onBackground = Gray800,

    surface = White,
    onSurface = Gray800,
    surfaceVariant = Pink50,

    error = Color(0xFFD32F2F),
    onError = White,

    outline = Gray400,
    outlineVariant = Pink200
)

private val DarkColors = darkColorScheme(
    primary = Pink400,
    onPrimary = White,
    primaryContainer = Pink600,
    onPrimaryContainer = Pink100,

    secondary = Lavender,
    onSecondary = Gray800,
    secondaryContainer = Gray800,

    tertiary = Pink300,
    onTertiary = White,

    background = Gray900,
    onBackground = Gray100,

    surface = Gray800,
    onSurface = Gray100,

    error = Color(0xFFCF6679),
    onError = Black
)

@Composable
fun LolitaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
