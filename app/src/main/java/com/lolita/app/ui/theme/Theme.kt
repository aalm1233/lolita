package com.lolita.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.lolita.app.ui.theme.shimmer.skinShimmerTheme
import com.valentinilk.shimmer.LocalShimmerTheme

@Composable
fun LolitaTheme(
    skinType: SkinType = SkinType.DEFAULT,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val skin = getSkinConfig(skinType)
    val colors = if (darkTheme) skin.darkColorScheme else skin.lightColorScheme
    val shimmerTheme = skinShimmerTheme()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalLolitaSkin provides skin,
        LocalShimmerTheme provides shimmerTheme,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = skin.typography,
            content = content
        )
    }
}
