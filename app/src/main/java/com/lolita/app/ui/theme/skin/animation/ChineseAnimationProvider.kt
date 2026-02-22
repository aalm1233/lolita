package com.lolita.app.ui.theme.skin.animation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private val Vermillion = Color(0xFFE34234)
private val InkBlack = Color(0xFF1A1A2E)

class ChineseAnimationProvider : SkinAnimationProvider {

    override val skinTransition = SkinTransitionSpec(
        durationMs = 450,
        overlay = { progress ->
            Canvas(Modifier.fillMaxSize()) {
                val maxR = hypot(size.width, size.height) / 2f
                val radius = maxR * progress
                drawCircle(
                    Brush.radialGradient(
                        listOf(
                            Color.Transparent,
                            InkBlack.copy(alpha = 0.6f * (1f - progress)),
                            InkBlack.copy(alpha = 0.3f * (1f - progress)),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius.coerceAtLeast(1f)
                    ),
                    radius = radius, center = center
                )
                // Ink splash dots at wavefront
                val dotCount = 10
                for (i in 0 until dotCount) {
                    val angle = (2 * PI * i / dotCount).toFloat()
                    val dx = cos(angle) * (radius + radius * 0.05f)
                    val dy = sin(angle) * (radius + radius * 0.05f)
                    drawCircle(
                        InkBlack.copy(alpha = 0.4f * (1f - progress)),
                        radius = size.minDimension * 0.008f,
                        center = Offset(center.x + dx, center.y + dy)
                    )
                }
            }
        }
    )

    override val tabSwitchAnimation = TabSwitchAnimationSpec(
        indicatorAnimation = tween(
            durationMillis = 400,
            easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
        ),
        selectedEffect = { _ -> Modifier },
        particleEffect = null
    )

    override val cardAnimation = CardAnimationSpec(
        enterTransition = fadeIn(tween(400, easing = LinearOutSlowInEasing)) +
            slideInHorizontally(
                tween(450, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f))
            ) { it / 3 },
        exitTransition = fadeOut(tween(300)) +
            slideOutHorizontally(tween(300)) { -it / 4 },
        staggerDelayMs = 70,
        enterDurationMs = 450
    )

    override val interactionFeedback = InteractionFeedbackSpec(
        pressScale = 0.95f,
        rippleColor = Vermillion,
        rippleAlpha = 0.15f,
        customRipple = { center, progress ->
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension * 0.3f * progress
                val alpha = (1f - progress) * 0.35f
                drawCircle(
                    Brush.radialGradient(
                        listOf(
                            InkBlack.copy(alpha = alpha),
                            InkBlack.copy(alpha = alpha * 0.3f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius.coerceAtLeast(1f)
                    ),
                    radius = radius, center = center
                )
            }
        }
    )

    override val clickFeedback = SkinClickFeedbackSpec(
        pressScale = 0.92f,
        scaleAnimationSpec = tween(250, easing = FastOutSlowInEasing),
        rippleColor = Color(0xFF2C2C2C),
        rippleDuration = 600,
        rippleStyle = RippleStyle.INK,
        hasParticles = true,
        particleCount = 3
    )

    override val navigation = SkinNavigationSpec(
        enterTransition = chineseEnterTransition(),
        exitTransition = chineseExitTransition(),
        popEnterTransition = chinesePopEnterTransition(),
        popExitTransition = chinesePopExitTransition(),
        hasOverlayEffect = true,
        overlayDuration = 400
    )

    override val listAnimation = SkinListAnimationSpec(
        appearDirection = AppearDirection.FROM_LEFT,
        appearOffsetPx = 100f,
        staggerDelayMs = 80,
        animationSpec = tween(400, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
        flingFrictionMultiplier = 1.0f
    )

    override val ambientAnimation = SkinAmbientAnimationSpec(
        backgroundEnabled = true,
        backgroundParticleCount = 10,
        backgroundCycleDurationRange = 15000..25000,
        backgroundAlphaRange = 0.1f..0.25f,
        topBarDecorationAnimated = true,
        cardGlowEffect = true
    )
}
