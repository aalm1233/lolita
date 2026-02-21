package com.lolita.app.ui.theme.skin.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.lolita.app.ui.theme.LolitaSkin
import kotlinx.coroutines.delay

@Composable
fun Modifier.skinItemAppear(index: Int): Modifier {
    val skin = LolitaSkin.current
    val spec = skin.animations.listAnimation
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay((index * spec.staggerDelayMs).toLong())
        animProgress.animateTo(1f, animationSpec = spec.animationSpec)
    }

    val progress = animProgress.value

    val offsetX = when (spec.appearDirection) {
        AppearDirection.FROM_LEFT -> spec.appearOffsetPx * (1f - progress)
        else -> 0f
    }
    val offsetY = when (spec.appearDirection) {
        AppearDirection.FROM_BOTTOM -> spec.appearOffsetPx * (1f - progress)
        else -> 0f
    }
    val scale = when (spec.appearDirection) {
        AppearDirection.FADE_SCALE -> 0.9f + 0.1f * progress
        else -> 1f
    }

    return this.graphicsLayer {
        translationX = offsetX
        translationY = offsetY
        scaleX = scale
        scaleY = scale
        alpha = progress
    }
}
