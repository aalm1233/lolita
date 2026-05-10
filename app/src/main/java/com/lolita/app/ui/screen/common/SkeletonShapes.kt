package com.lolita.app.ui.screen.common

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lolita.app.ui.theme.LolitaSkin
import com.valentinilk.shimmer.ShimmerBounds
import com.valentinilk.shimmer.rememberShimmer
import com.valentinilk.shimmer.shimmer

/**
 * Reusable building blocks for skeleton/shimmer placeholder layouts.
 * All shapes consume skin-aware colors from LolitaSkinConfig.
 */

/**
 * A rounded rectangle that shimmers, used for image/area placeholders.
 */
@Composable
fun ShimmerRect(
    width: Dp,
    height: Dp,
    shape: Shape = RoundedCornerShape(8.dp),
    modifier: Modifier = Modifier,
) {
    val skin = LolitaSkin.current
    val isDark = isSystemInDarkTheme()
    val baseColor = if (isDark) skin.cardContainerColorDark else skin.cardContainerColor
    val shimmerInstance = rememberShimmer(ShimmerBounds.View)

    Box(
        modifier = modifier
            .size(width, height)
            .clip(shape)
            .shimmer(shimmerInstance)
            .background(baseColor)
    )
}

/**
 * A circular shimmer shape, used for avatar/thumbnail placeholders.
 */
@Composable
fun ShimmerCircle(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val skin = LolitaSkin.current
    val isDark = isSystemInDarkTheme()
    val baseColor = if (isDark) skin.cardContainerColorDark else skin.cardContainerColor
    val shimmerInstance = rememberShimmer(ShimmerBounds.View)

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .shimmer(shimmerInstance)
            .background(baseColor)
    )
}

/**
 * A horizontal shimmer line, used for text placeholders.
 * @param widthFraction Fraction of parent width (0.0-1.0)
 */
@Composable
fun ShimmerLine(
    widthFraction: Float = 0.7f,
    height: Dp = 16.dp,
    shape: Shape = RoundedCornerShape(4.dp),
    modifier: Modifier = Modifier,
) {
    val skin = LolitaSkin.current
    val isDark = isSystemInDarkTheme()
    val baseColor = if (isDark) skin.cardContainerColorDark else skin.cardContainerColor
    val shimmerInstance = rememberShimmer(ShimmerBounds.View)

    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(shape)
            .shimmer(shimmerInstance)
            .background(baseColor)
    )
}
