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
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin

private val CountrySage = Color(0xFF7C9A69)
private val CountryBerry = Color(0xFFC56759)
private val CountryButter = Color(0xFFEACB87)

class CountryAnimationProvider : SkinAnimationProvider {

    override val skinTransition = SkinTransitionSpec(
        durationMs = 420,
        overlay = { progress ->
            Canvas(Modifier.fillMaxSize()) {
                val baseRadius = size.minDimension * 0.05f
                repeat(7) { index ->
                    val angle = (index / 7f) * 360f
                    val dist = size.minDimension * (0.12f + progress * 0.32f)
                    val center = Offset(
                        x = this.center.x + cos(Math.toRadians(angle.toDouble())).toFloat() * dist,
                        y = this.center.y + sin(Math.toRadians(angle.toDouble())).toFloat() * dist
                    )
                    val alpha = (1f - progress).coerceIn(0f, 1f) * 0.55f
                    rotate(angle, center) {
                        drawCircle(
                            color = CountrySage.copy(alpha = alpha * 0.35f),
                            radius = baseRadius * (1.8f - progress * 0.6f),
                            center = center
                        )
                    }
                    drawCountryBloom(center, baseRadius * (1.1f - progress * 0.2f), alpha)
                }
            }
        }
    )

    override val tabSwitchAnimation = TabSwitchAnimationSpec(
        indicatorAnimation = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        selectedEffect = { _ -> Modifier },
        particleEffect = null
    )

    override val cardAnimation = CardAnimationSpec(
        enterTransition = fadeIn(tween(320, easing = CubicBezierEasing(0.2f, 0f, 0.15f, 1f))) +
            slideInVertically(
                animationSpec = tween(360, easing = CubicBezierEasing(0.2f, 0f, 0.15f, 1f))
            ) { it / 5 },
        exitTransition = fadeOut(tween(220)) +
            slideOutVertically(tween(220)) { it / 8 },
        staggerDelayMs = 48,
        enterDurationMs = 360
    )

    override val interactionFeedback = InteractionFeedbackSpec(
        pressScale = 0.95f,
        rippleColor = CountryBerry,
        rippleAlpha = 0.2f,
        customRipple = { center, progress ->
            Canvas(Modifier.fillMaxSize()) {
                val outer = size.minDimension * 0.32f * progress
                val inner = size.minDimension * 0.18f * progress
                drawCircle(
                    color = CountrySage.copy(alpha = (1f - progress) * 0.18f),
                    radius = outer,
                    center = center
                )
                drawCircle(
                    color = CountryButter.copy(alpha = (1f - progress) * 0.26f),
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
        rippleColor = CountrySage,
        rippleDuration = 450,
        rippleStyle = RippleStyle.SOFT,
        hasParticles = true,
        particleCount = 6
    )

    override val navigation = SkinNavigationSpec(
        enterTransition = fadeIn(tween(320)) + slideInVertically(tween(380)) { it / 6 },
        exitTransition = fadeOut(tween(220)) + slideOutVertically(tween(220)) { -it / 10 },
        popEnterTransition = fadeIn(tween(320)) + slideInVertically(tween(380)) { -it / 6 },
        popExitTransition = fadeOut(tween(220)) + slideOutVertically(tween(220)) { it / 10 },
        hasOverlayEffect = true,
        overlayDuration = 420
    )

    override val listAnimation = SkinListAnimationSpec(
        appearDirection = AppearDirection.FROM_BOTTOM,
        appearOffsetPx = 52f,
        staggerDelayMs = 52,
        animationSpec = tween(340, easing = CubicBezierEasing(0.2f, 0f, 0.15f, 1f)),
        flingFrictionMultiplier = 0.96f
    )

    override val ambientAnimation = SkinAmbientAnimationSpec(
        backgroundEnabled = true,
        backgroundParticleCount = 24,
        backgroundCycleDurationRange = 9000..16000,
        backgroundAlphaRange = 0.08f..0.24f,
        topBarDecorationAnimated = true,
        cardGlowEffect = true
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCountryBloom(
    center: Offset,
    radius: Float,
    alpha: Float
) {
    repeat(6) { index ->
        val angle = index * 60f
        rotate(angle, center) {
            drawOval(
                color = CountryButter.copy(alpha = alpha),
                topLeft = Offset(center.x - radius * 0.28f, center.y - radius * 1.1f),
                size = androidx.compose.ui.geometry.Size(radius * 0.56f, radius * 1.05f)
            )
        }
    }
    drawCircle(
        color = CountryBerry.copy(alpha = alpha),
        radius = radius * 0.28f,
        center = center
    )
}
