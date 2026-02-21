package com.lolita.app.ui.theme.skin.animation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val WineColor = Color(0xFF722F37)
private val GoldAccent = Color(0xFFD4AF37)

class ClassicAnimationProvider : SkinAnimationProvider {

    override val skinTransition = SkinTransitionSpec(
        durationMs = 400,
        overlay = { progress ->
            Canvas(Modifier.fillMaxSize()) {
                // Page turn: golden light sweeping left to right
                val sweepX = size.width * progress
                drawRect(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            GoldAccent.copy(alpha = 0.3f * (1f - progress)),
                            GoldAccent.copy(alpha = 0.15f * (1f - progress)),
                            Color.Transparent
                        ),
                        startX = sweepX - size.width * 0.3f,
                        endX = sweepX + size.width * 0.1f
                    )
                )
            }
        }
    )

    override val tabSwitchAnimation = TabSwitchAnimationSpec(
        indicatorAnimation = tween(
            durationMillis = 380,
            easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
        ),
        selectedEffect = { _ -> Modifier },
        particleEffect = null
    )

    override val cardAnimation = CardAnimationSpec(
        enterTransition = fadeIn(tween(350, easing = EaseInOutCubic)) +
            slideInVertically(
                tween(400, easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f))
            ) { it / 4 },
        exitTransition = fadeOut(tween(250)) +
            slideOutVertically(tween(250)) { -it / 6 },
        staggerDelayMs = 55,
        enterDurationMs = 400
    )

    override val interactionFeedback = InteractionFeedbackSpec(
        pressScale = 0.96f,
        rippleColor = WineColor,
        rippleAlpha = 0.14f,
        customRipple = { center, progress ->
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension * 0.28f * progress
                val alpha = (1f - progress) * 0.3f
                // Gold ring expanding
                drawCircle(
                    GoldAccent.copy(alpha = alpha),
                    radius = radius,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                )
                // Inner subtle fill
                drawCircle(
                    WineColor.copy(alpha = alpha * 0.3f),
                    radius = radius * 0.8f,
                    center = center
                )
            }
        }
    )
}
