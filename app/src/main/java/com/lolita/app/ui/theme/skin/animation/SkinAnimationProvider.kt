package com.lolita.app.ui.theme.skin.animation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

data class SkinTransitionSpec(
    val durationMs: Int,
    val overlay: @Composable (progress: Float) -> Unit
)

data class TabSwitchAnimationSpec(
    val indicatorAnimation: AnimationSpec<Float>,
    val selectedEffect: @Composable (selected: Boolean) -> Modifier,
    val particleEffect: (@Composable (Offset) -> Unit)?
)

data class CardAnimationSpec(
    val enterTransition: EnterTransition,
    val exitTransition: ExitTransition,
    val staggerDelayMs: Int,
    val enterDurationMs: Int
)

data class InteractionFeedbackSpec(
    val pressScale: Float,
    val rippleColor: Color,
    val rippleAlpha: Float,
    val customRipple: (@Composable (Offset, Float) -> Unit)?
)

enum class RippleStyle { SOFT, SHARP, INK, GLOW }
enum class AppearDirection { FROM_BOTTOM, FROM_LEFT, FADE_SCALE }

data class SkinClickFeedbackSpec(
    val pressScale: Float,
    val scaleAnimationSpec: AnimationSpec<Float>,
    val rippleColor: Color,
    val rippleDuration: Int,
    val rippleStyle: RippleStyle,
    val hasParticles: Boolean,
    val particleCount: Int
)

data class SkinNavigationSpec(
    val enterTransition: EnterTransition,
    val exitTransition: ExitTransition,
    val popEnterTransition: EnterTransition,
    val popExitTransition: ExitTransition,
    val hasOverlayEffect: Boolean,
    val overlayDuration: Int
)

data class SkinListAnimationSpec(
    val appearDirection: AppearDirection,
    val appearOffsetPx: Float,
    val staggerDelayMs: Int,
    val animationSpec: AnimationSpec<Float>,
    val flingFrictionMultiplier: Float
)

data class SkinAmbientAnimationSpec(
    val backgroundEnabled: Boolean,
    val backgroundParticleCount: Int,
    val backgroundCycleDurationRange: IntRange,
    val backgroundAlphaRange: ClosedFloatingPointRange<Float>,
    val topBarDecorationAnimated: Boolean,
    val cardGlowEffect: Boolean
)

interface SkinAnimationProvider {
    val skinTransition: SkinTransitionSpec
    val tabSwitchAnimation: TabSwitchAnimationSpec
    val cardAnimation: CardAnimationSpec
    val interactionFeedback: InteractionFeedbackSpec

    val clickFeedback: SkinClickFeedbackSpec
        get() = SkinClickFeedbackSpec(
            pressScale = interactionFeedback.pressScale,
            scaleAnimationSpec = spring(),
            rippleColor = interactionFeedback.rippleColor,
            rippleDuration = 400,
            rippleStyle = RippleStyle.SOFT,
            hasParticles = false,
            particleCount = 0
        )

    val navigation: SkinNavigationSpec
        get() = SkinNavigationSpec(
            enterTransition = fadeIn() + slideInHorizontally { it / 4 },
            exitTransition = fadeOut() + slideOutHorizontally { -it / 4 },
            popEnterTransition = fadeIn() + slideInHorizontally { -it / 4 },
            popExitTransition = fadeOut() + slideOutHorizontally { it / 4 },
            hasOverlayEffect = false,
            overlayDuration = 0
        )

    val listAnimation: SkinListAnimationSpec
        get() = SkinListAnimationSpec(
            appearDirection = AppearDirection.FROM_BOTTOM,
            appearOffsetPx = 60f,
            staggerDelayMs = 50,
            animationSpec = tween(300),
            flingFrictionMultiplier = 1.0f
        )

    val ambientAnimation: SkinAmbientAnimationSpec
        get() = SkinAmbientAnimationSpec(
            backgroundEnabled = false,
            backgroundParticleCount = 0,
            backgroundCycleDurationRange = 10000..15000,
            backgroundAlphaRange = 0.1f..0.2f,
            topBarDecorationAnimated = false,
            cardGlowEffect = false
        )
}
