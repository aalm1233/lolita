package com.lolita.app.ui.theme.skin.animation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
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

interface SkinAnimationProvider {
    val skinTransition: SkinTransitionSpec
    val tabSwitchAnimation: TabSwitchAnimationSpec
    val cardAnimation: CardAnimationSpec
    val interactionFeedback: InteractionFeedbackSpec
}
