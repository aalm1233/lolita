package com.lolita.app.ui.theme.skin.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class RippleInstance(
    val center: Offset,
    val progress: Animatable<Float, *> = Animatable(0f)
)

@Composable
fun SkinRippleEffect(
    interactionSource: MutableInteractionSource,
    spec: SkinClickFeedbackSpec,
    modifier: Modifier = Modifier
) {
    val ripples = remember { mutableStateListOf<RippleInstance>() }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) {
                val ripple = RippleInstance(center = interaction.pressPosition)
                ripples.add(ripple)
                launch {
                    ripple.progress.animateTo(1f, tween(spec.rippleDuration))
                    ripples.remove(ripple)
                }
            }
        }
    }
    Canvas(modifier) {
        ripples.forEach { ripple ->
            val progress = ripple.progress.value
            val alpha = (1f - progress).coerceIn(0f, 1f) * 0.5f
            when (spec.rippleStyle) {
                RippleStyle.SOFT -> drawSoftRipple(ripple.center, progress, spec.rippleColor, alpha)
                RippleStyle.SHARP -> drawSharpRipple(ripple.center, progress, spec.rippleColor, alpha)
                RippleStyle.INK -> drawInkRipple(ripple.center, progress, spec.rippleColor, alpha)
                RippleStyle.GLOW -> drawGlowRipple(ripple.center, progress, spec.rippleColor, alpha)
            }
        }
    }
}

private fun DrawScope.drawSoftRipple(center: Offset, progress: Float, color: Color, alpha: Float) {
    val radius = size.minDimension * 0.4f * progress
    drawCircle(
        Brush.radialGradient(
            listOf(color.copy(alpha = alpha), Color.Transparent),
            center = center,
            radius = radius.coerceAtLeast(1f)
        ),
        radius = radius,
        center = center
    )
}

private fun DrawScope.drawSharpRipple(center: Offset, progress: Float, color: Color, alpha: Float) {
    val radius = size.minDimension * 0.35f * progress
    drawCircle(color.copy(alpha = alpha), radius = radius, center = center)
    val jaggedCount = 8
    for (i in 0 until jaggedCount) {
        val angle = (2 * PI * i / jaggedCount).toFloat()
        val innerR = radius * 0.9f
        val outerR = radius * 1.1f
        drawLine(
            color.copy(alpha = alpha * 0.8f),
            start = Offset(center.x + cos(angle) * innerR, center.y + sin(angle) * innerR),
            end = Offset(center.x + cos(angle) * outerR, center.y + sin(angle) * outerR),
            strokeWidth = 1.5f
        )
    }
}

private fun DrawScope.drawInkRipple(center: Offset, progress: Float, color: Color, alpha: Float) {
    val baseRadius = size.minDimension * 0.35f * progress
    val path = Path().apply {
        val points = 12
        for (i in 0..points) {
            val angle = (2 * PI * i / points).toFloat()
            val wobble = 1f + sin(angle * 3f + progress * 5f) * 0.15f
            val r = baseRadius * wobble
            val px = center.x + cos(angle) * r
            val py = center.y + sin(angle) * r
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
    drawPath(path, color.copy(alpha = alpha))
}

private fun DrawScope.drawGlowRipple(center: Offset, progress: Float, color: Color, alpha: Float) {
    val radius = size.minDimension * 0.3f * progress
    // Outer glow
    drawCircle(
        Brush.radialGradient(
            listOf(color.copy(alpha = alpha * 0.3f), Color.Transparent),
            center = center,
            radius = (radius * 1.6f).coerceAtLeast(1f)
        ),
        radius = radius * 1.6f,
        center = center
    )
    // Inner ring
    drawCircle(
        color.copy(alpha = alpha),
        radius = radius,
        center = center,
        style = Stroke(width = 2.5f)
    )
}
