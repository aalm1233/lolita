package com.lolita.app.ui.theme.skin.animation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

class DefaultAnimationProvider : SkinAnimationProvider {
    override val skinTransition = SkinTransitionSpec(
        durationMs = 300,
        overlay = { _ -> Box(Modifier) }
    )
    override val tabSwitchAnimation = TabSwitchAnimationSpec(
        indicatorAnimation = spring(stiffness = Spring.StiffnessMediumLow),
        selectedEffect = { _ -> Modifier },
        particleEffect = null
    )
    override val cardAnimation = CardAnimationSpec(
        enterTransition = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 },
        exitTransition = fadeOut(tween(200)),
        staggerDelayMs = 50,
        enterDurationMs = 300
    )
    override val interactionFeedback = InteractionFeedbackSpec(
        pressScale = 0.96f,
        rippleColor = Color.Black,
        rippleAlpha = 0.12f,
        customRipple = null
    )
}
