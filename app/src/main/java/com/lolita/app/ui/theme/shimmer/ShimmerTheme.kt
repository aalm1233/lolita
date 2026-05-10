package com.lolita.app.ui.theme.shimmer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.unit.dp
import com.lolita.app.ui.theme.LolitaSkin
import com.valentinilk.shimmer.ShimmerTheme
import com.valentinilk.shimmer.defaultShimmerTheme
import com.valentinilk.shimmer.shimmerSpec

/**
 * Creates a skin-aware [ShimmerTheme] derived from the current [LolitaSkinConfig].
 * Each skin maps to a distinct shimmer color pair that reflects its visual identity.
 */
@Composable
fun skinShimmerTheme(): ShimmerTheme {
    val skin = LolitaSkin.current
    val isDark = isSystemInDarkTheme()

    val baseColor = if (isDark) skin.cardContainerColorDark else skin.cardContainerColor
    val highlightColor = if (isDark) skin.accentColorDark else skin.accentColor

    return defaultShimmerTheme.copy(
        animationSpec = infiniteRepeatable(
            animation = shimmerSpec(durationMillis = 800, easing = LinearEasing, delayMillis = 1200),
            repeatMode = RepeatMode.Restart,
        ),
        blendMode = BlendMode.DstIn,
        rotation = 15f,
        shaderColors = listOf(
            baseColor,
            highlightColor.copy(alpha = 0.4f),
            baseColor,
        ),
        shaderColorStops = listOf(0.0f, 0.5f, 1.0f),
        shimmerWidth = 400.dp,
    )
}
