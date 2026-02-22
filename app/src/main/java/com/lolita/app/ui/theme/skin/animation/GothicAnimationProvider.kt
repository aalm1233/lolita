package com.lolita.app.ui.theme.skin.animation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
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

private val GothicPurple = Color(0xFF4A0E4E)
private val BloodRed = Color(0xFF8B0000)

class GothicAnimationProvider : SkinAnimationProvider {

    override val skinTransition = SkinTransitionSpec(
        durationMs = 600,
        overlay = { progress ->
            Canvas(Modifier.fillMaxSize()) {
                // Dark overlay fading
                drawRect(Color.Black.copy(alpha = 0.7f * (1f - progress)))
                // Crack lines radiating from center
                val crackCount = 8
                for (i in 0 until crackCount) {
                    val angle = (2 * PI * i / crackCount).toFloat()
                    val len = size.width * 0.4f * progress
                    drawLine(
                        BloodRed.copy(alpha = progress * 0.6f),
                        center,
                        Offset(center.x + cos(angle) * len, center.y + sin(angle) * len),
                        strokeWidth = 2f
                    )
                }
            }
        }
    )

    override val tabSwitchAnimation = TabSwitchAnimationSpec(
        indicatorAnimation = tween(
            durationMillis = 500,
            easing = CubicBezierEasing(0.2f, 0f, 0.1f, 1f)
        ),
        selectedEffect = { _ -> Modifier },
        particleEffect = null
    )

    override val cardAnimation = CardAnimationSpec(
        enterTransition = fadeIn(tween(500, easing = LinearOutSlowInEasing)) +
            slideInVertically(
                tween(600, easing = CubicBezierEasing(0.2f, 0f, 0.1f, 1f))
            ) { it / 2 },
        exitTransition = fadeOut(tween(400)) +
            slideOutVertically(tween(400)) { it / 3 },
        staggerDelayMs = 80,
        enterDurationMs = 600
    )

    override val interactionFeedback = InteractionFeedbackSpec(
        pressScale = 0.97f,
        rippleColor = GothicPurple,
        rippleAlpha = 0.25f,
        customRipple = { center, progress ->
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension * 0.35f * progress
                val alpha = (1f - progress) * 0.5f
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color.Black.copy(alpha = alpha), Color.Transparent),
                        center = center, radius = radius.coerceAtLeast(1f)
                    ),
                    radius = radius, center = center
                )
            }
        }
    )

    override val clickFeedback = SkinClickFeedbackSpec(
        pressScale = 0.95f,
        scaleAnimationSpec = tween(100, easing = LinearEasing),
        rippleColor = GothicPurple,
        rippleDuration = 350,
        rippleStyle = RippleStyle.SHARP,
        hasParticles = true,
        particleCount = 5
    )

    override val navigation = SkinNavigationSpec(
        enterTransition = gothicEnterTransition(),
        exitTransition = gothicExitTransition(),
        popEnterTransition = gothicPopEnterTransition(),
        popExitTransition = gothicPopExitTransition(),
        hasOverlayEffect = true,
        overlayDuration = 400
    )

    override val listAnimation = SkinListAnimationSpec(
        appearDirection = AppearDirection.FADE_SCALE,
        appearOffsetPx = 40f,
        staggerDelayMs = 30,
        animationSpec = tween(250, easing = LinearOutSlowInEasing),
        flingFrictionMultiplier = 1.5f
    )

    override val ambientAnimation = SkinAmbientAnimationSpec(
        backgroundEnabled = true,
        backgroundParticleCount = 18,
        backgroundCycleDurationRange = 12000..20000,
        backgroundAlphaRange = 0.12f..0.3f,
        topBarDecorationAnimated = true,
        cardGlowEffect = false
    )
}
