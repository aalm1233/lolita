package com.lolita.app.ui.screen.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lolita.app.ui.theme.shape.SquircleShape
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

@Composable
fun GlassSurface(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    shape: Shape = SquircleShape(16.dp),
    blurRadius: Dp = 12.dp,
    glassAlpha: Float = 0.65f,
    highlightAlpha: Float = 0.15f,
    elevation: Dp = 2.dp,
    hazeState: HazeState? = null,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    content: @Composable () -> Unit
) {

    // Base glass color — semi-transparent white
    val glassColor = Color.White.copy(alpha = glassAlpha)

    // Specular highlight: bright top edge fading to transparent bottom
    val highlightBrush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = highlightAlpha),
            Color.White.copy(alpha = highlightAlpha * 0.25f),
            Color.White.copy(alpha = 0f)
        )
    )

    // Clip to shape first so glass fill doesn't bleed outside the squircle
    // Shadow + blur + background modifier chain
    val glassModifier = if (hazeState != null) {
        modifier
            .clip(shape)
            .shadow(elevation, shape, clip = false)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = glassColor,
                    tint = HazeTint(glassColor),
                    blurRadius = blurRadius
                )
            )
    } else {
        modifier
            .clip(shape)
            .shadow(elevation, shape, clip = false)
            .background(glassColor, shape)
    }

    // Highlight border over the glass
    val highlightedModifier = glassModifier
        .border(1.dp, highlightBrush, shape)

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = highlightedModifier,
            shape = shape,
            color = Color.Transparent
        ) {
            Box(modifier = Modifier.padding(contentPadding)) {
                content()
            }
        }
    } else {
        Surface(
            modifier = highlightedModifier,
            shape = shape,
            color = Color.Transparent
        ) {
            Box(modifier = Modifier.padding(contentPadding)) {
                content()
            }
        }
    }
}
