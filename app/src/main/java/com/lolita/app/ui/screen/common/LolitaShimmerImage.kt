package com.lolita.app.ui.screen.common

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.lolita.app.ui.theme.LolitaSkin
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.animation.circular.CircularRevealPlugin
import com.skydoves.landscapist.coil.CoilImage
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.placeholder.shimmer.ShimmerPlugin
import com.skydoves.landscapist.placeholder.shimmer.Shimmer

/**
 * Skin-aware image loading composable that wraps Landscapist CoilImage.
 *
 * Features:
 * - ShimmerPlugin with skin-aware colors during image loading
 * - CircularRevealPlugin for smooth appearance animation
 * - Initial letter fallback on image load failure (matches existing pattern)
 * - Consistent error/empty state handling across all screens
 *
 * @param model Image data: String URL, File, Uri, or @DrawableRes Int
 * @param contentDescription Accessibility description for the image
 * @param modifier Modifier for sizing and clipping
 * @param contentScale How the image should be scaled/cropped
 * @param alignment How the image should be aligned within its bounds
 * @param placeholderInitial Letter to display on failure (uses first character)
 * @param shimmerEnabled Whether to show shimmer during loading
 * @param circularRevealEnabled Whether to animate image appearance with circular reveal
 */
@Composable
fun LolitaShimmerImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    placeholderInitial: String? = null,
    shimmerEnabled: Boolean = true,
    circularRevealEnabled: Boolean = true,
) {
    val skin = LolitaSkin.current
    val isDark = isSystemInDarkTheme()
    val containerColor = if (isDark) skin.cardContainerColorDark else skin.cardContainerColor
    val accentColor = if (isDark) skin.accentColorDark else skin.accentColor

    val shimmerBaseColor = containerColor
    val shimmerHighlightColor = accentColor.copy(alpha = 0.3f)

    val component = rememberImageComponent {
        if (shimmerEnabled) {
            +ShimmerPlugin(
                shimmer = Shimmer.Flash(
                    baseColor = shimmerBaseColor,
                    highlightColor = shimmerHighlightColor,
                    duration = 600,
                    tilt = 15f,
                    dropOff = 0.65f,
                    intensity = 0f,
                )
            )
        }
        if (circularRevealEnabled) {
            +CircularRevealPlugin(duration = 350)
        }
    }

    CoilImage(
        imageModel = { model },
        modifier = modifier,
        imageOptions = ImageOptions(
            contentScale = contentScale,
            alignment = alignment,
            contentDescription = contentDescription,
        ),
        component = component,
        failure = {
            val initial = placeholderInitial ?: "?"
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    )
}
