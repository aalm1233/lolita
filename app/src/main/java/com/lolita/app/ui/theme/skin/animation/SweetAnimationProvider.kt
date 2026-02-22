package com.lolita.app.ui.theme.skin.animation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import com.lolita.app.ui.theme.Pink400
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class SweetAnimationProvider : SkinAnimationProvider {

    override val skinTransition = SkinTransitionSpec(
        durationMs = 350,
        overlay = { progress ->
            Canvas(Modifier.fillMaxSize()) {
                val count = 12
                for (i in 0 until count) {
                    val angle = (2 * PI * i / count).toFloat()
                    val dist = size.minDimension * 0.5f * progress
                    val cx = center.x + cos(angle) * dist
                    val cy = center.y + sin(angle) * dist
                    val alpha = (1f - progress).coerceIn(0f, 1f)
                    val r = size.minDimension * 0.02f * (1f - progress * 0.5f)
                    drawSweetHeartParticle(Offset(cx, cy), r, Pink400.copy(alpha = alpha))
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
        enterTransition = fadeIn(tween(300, easing = FastOutSlowInEasing)) +
            scaleIn(
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                initialScale = 0.85f
            ),
        exitTransition = fadeOut(tween(200)) +
            scaleOut(tween(200), targetScale = 0.9f),
        staggerDelayMs = 60,
        enterDurationMs = 350
    )

    override val interactionFeedback = InteractionFeedbackSpec(
        pressScale = 0.92f,
        rippleColor = Pink400,
        rippleAlpha = 0.18f,
        customRipple = { center, progress ->
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension * 0.3f * progress
                val alpha = (1f - progress).coerceIn(0f, 0.4f)
                drawCircle(Pink400.copy(alpha = alpha), radius = radius, center = center)
            }
        }
    )

    override val clickFeedback = SkinClickFeedbackSpec(
        pressScale = 0.88f,
        scaleAnimationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        rippleColor = Pink400,
        rippleDuration = 500,
        rippleStyle = RippleStyle.SOFT,
        hasParticles = true,
        particleCount = 4
    )

    override val navigation = SkinNavigationSpec(
        enterTransition = sweetEnterTransition(),
        exitTransition = sweetExitTransition(),
        popEnterTransition = sweetPopEnterTransition(),
        popExitTransition = sweetPopExitTransition(),
        hasOverlayEffect = true,
        overlayDuration = 350
    )

    override val listAnimation = SkinListAnimationSpec(
        appearDirection = AppearDirection.FROM_BOTTOM,
        appearOffsetPx = 80f,
        staggerDelayMs = 60,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        flingFrictionMultiplier = 0.7f
    )

    override val ambientAnimation = SkinAmbientAnimationSpec(
        backgroundEnabled = true,
        backgroundParticleCount = 20,
        backgroundCycleDurationRange = 8000..15000,
        backgroundAlphaRange = 0.15f..0.4f,
        topBarDecorationAnimated = true,
        cardGlowEffect = true
    )
}

private fun DrawScope.drawSweetHeartParticle(center: Offset, radius: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y + radius * 0.3f)
        cubicTo(center.x - radius, center.y - radius * 0.5f,
            center.x - radius * 0.5f, center.y - radius,
            center.x, center.y - radius * 0.3f)
        cubicTo(center.x + radius * 0.5f, center.y - radius,
            center.x + radius, center.y - radius * 0.5f,
            center.x, center.y + radius * 0.3f)
    }
    drawPath(path, color, style = Fill)
}
