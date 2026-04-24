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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill

private val VictorianBurgundy = Color(0xFF7B1E3A)
private val VictorianGold = Color(0xFFB8860B)
private val VictorianCream = Color(0xFFF5E6D3)

class VictorianAnimationProvider : SkinAnimationProvider {

    override val skinTransition = SkinTransitionSpec(
        durationMs = 450,
        overlay = { progress ->
            Canvas(Modifier.fillMaxSize()) {
                val curtainWidth = size.width * 0.5f * (1f - progress)
                val alpha = (1f - progress).coerceIn(0f, 1f)
                drawRect(
                    color = VictorianBurgundy.copy(alpha = alpha * 0.7f),
                    topLeft = Offset.Zero,
                    size = Size(curtainWidth, size.height)
                )
                drawRect(
                    color = VictorianBurgundy.copy(alpha = alpha * 0.7f),
                    topLeft = Offset(size.width - curtainWidth, 0f),
                    size = Size(curtainWidth, size.height)
                )
                drawRect(
                    color = VictorianGold.copy(alpha = alpha * 0.3f),
                    topLeft = Offset(curtainWidth - 2f, 0f),
                    size = Size(4f, size.height)
                )
                drawRect(
                    color = VictorianGold.copy(alpha = alpha * 0.3f),
                    topLeft = Offset(size.width - curtainWidth - 2f, 0f),
                    size = Size(4f, size.height)
                )
            }
        }
    )

    override val tabSwitchAnimation = TabSwitchAnimationSpec(
        indicatorAnimation = spring(
            dampingRatio = 0.75f,
            stiffness = Spring.StiffnessLow
        ),
        selectedEffect = { _ -> Modifier },
        particleEffect = null
    )

    override val cardAnimation = CardAnimationSpec(
        enterTransition = fadeIn(tween(340, easing = CubicBezierEasing(0.2f, 0f, 0.15f, 1f))) +
            slideInVertically(
                animationSpec = tween(380, easing = CubicBezierEasing(0.2f, 0f, 0.15f, 1f))
            ) { it / 5 },
        exitTransition = fadeOut(tween(240)) +
            slideOutVertically(tween(240)) { it / 8 },
        staggerDelayMs = 40,
        enterDurationMs = 380
    )

    override val interactionFeedback = InteractionFeedbackSpec(
        pressScale = 0.95f,
        rippleColor = VictorianGold,
        rippleAlpha = 0.2f,
        customRipple = { center, progress ->
            Canvas(Modifier.fillMaxSize()) {
                val outer = size.minDimension * 0.35f * progress
                val inner = size.minDimension * 0.2f * progress
                drawCircle(
                    color = VictorianGold.copy(alpha = (1f - progress) * 0.22f),
                    radius = outer,
                    center = center
                )
                drawCircle(
                    color = VictorianCream.copy(alpha = (1f - progress) * 0.28f),
                    radius = inner,
                    center = center,
                    style = Fill
                )
            }
        }
    )

    override val clickFeedback = SkinClickFeedbackSpec(
        pressScale = 0.92f,
        scaleAnimationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        rippleColor = VictorianBurgundy,
        rippleDuration = 480,
        rippleStyle = RippleStyle.GLOW,
        hasParticles = true,
        particleCount = 6
    )

    override val navigation = SkinNavigationSpec(
        enterTransition = fadeIn(tween(340)) + slideInVertically(tween(400)) { it / 6 },
        exitTransition = fadeOut(tween(240)) + slideOutVertically(tween(240)) { -it / 10 },
        popEnterTransition = fadeIn(tween(340)) + slideInVertically(tween(400)) { -it / 6 },
        popExitTransition = fadeOut(tween(240)) + slideOutVertically(tween(240)) { it / 10 },
        hasOverlayEffect = true,
        overlayDuration = 440
    )

    override val listAnimation = SkinListAnimationSpec(
        appearDirection = AppearDirection.FROM_BOTTOM,
        appearOffsetPx = 52f,
        staggerDelayMs = 40,
        animationSpec = tween(350, easing = CubicBezierEasing(0.2f, 0f, 0.15f, 1f)),
        flingFrictionMultiplier = 0.96f
    )

    override val ambientAnimation = SkinAmbientAnimationSpec(
        backgroundEnabled = true,
        backgroundParticleCount = 26,
        backgroundCycleDurationRange = 9000..16000,
        backgroundAlphaRange = 0.08f..0.24f,
        topBarDecorationAnimated = true,
        cardGlowEffect = true
    )
}
