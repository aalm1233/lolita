package com.lolita.app.ui.theme.skin.animation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

private val SkyBlue = Color(0xFF4A90D9)

class NavyAnimationProvider : SkinAnimationProvider {

    override val skinTransition = SkinTransitionSpec(
        durationMs = 380,
        overlay = { progress ->
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val maxRadius = size.maxDimension * 0.8f
                for (i in 0..2) {
                    val rippleProgress = (progress - i * 0.15f).coerceIn(0f, 1f)
                    val radius = maxRadius * rippleProgress
                    val alpha = (1f - rippleProgress) * 0.1f
                    drawCircle(
                        SkyBlue.copy(alpha = alpha),
                        radius = radius,
                        center = Offset(cx, cy),
                        style = Stroke(width = 3f)
                    )
                }
            }
        }
    )

    override val tabSwitchAnimation = TabSwitchAnimationSpec(
        indicatorAnimation = tween(
            durationMillis = 380,
            easing = CubicBezierEasing(0.3f, 0f, 0.2f, 1f)
        ),
        selectedEffect = { _ -> Modifier },
        particleEffect = null
    )

    override val cardAnimation = CardAnimationSpec(
        enterTransition = fadeIn(tween(350)) +
            slideInVertically(
                tween(380, easing = CubicBezierEasing(0.3f, 0f, 0.2f, 1f))
            ) { it / 4 },
        exitTransition = fadeOut(tween(250)) +
            slideOutVertically(tween(250)) { -it / 6 },
        staggerDelayMs = 65,
        enterDurationMs = 380
    )

    override val interactionFeedback = InteractionFeedbackSpec(
        pressScale = 0.94f,
        rippleColor = SkyBlue,
        rippleAlpha = 0.16f,
        customRipple = { center, progress ->
            Canvas(Modifier.fillMaxSize()) {
                for (i in 0..1) {
                    val p = (progress - i * 0.2f).coerceIn(0f, 1f)
                    val radius = size.minDimension * 0.3f * p
                    val alpha = (1f - p) * 0.25f
                    drawCircle(
                        SkyBlue.copy(alpha = alpha),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 1.5f)
                    )
                }
            }
        }
    )

    override val clickFeedback = SkinClickFeedbackSpec(
        pressScale = 0.94f,
        scaleAnimationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        rippleColor = SkyBlue,
        rippleDuration = 400,
        rippleStyle = RippleStyle.SOFT,
        hasParticles = true,
        particleCount = 5
    )

    override val navigation = SkinNavigationSpec(
        enterTransition = navyEnterTransition(),
        exitTransition = navyExitTransition(),
        popEnterTransition = navyPopEnterTransition(),
        popExitTransition = navyPopExitTransition(),
        hasOverlayEffect = true,
        overlayDuration = 400
    )

    override val listAnimation = SkinListAnimationSpec(
        appearDirection = AppearDirection.FROM_BOTTOM,
        appearOffsetPx = 40f,
        staggerDelayMs = 65,
        animationSpec = tween(350, easing = CubicBezierEasing(0.3f, 0f, 0.2f, 1f)),
        flingFrictionMultiplier = 1.1f
    )

    override val ambientAnimation = SkinAmbientAnimationSpec(
        backgroundEnabled = true,
        backgroundParticleCount = 22,
        backgroundCycleDurationRange = 8000..15000,
        backgroundAlphaRange = 0.08f..0.25f,
        topBarDecorationAnimated = true,
        cardGlowEffect = true
    )
}
