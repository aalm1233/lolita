package com.lolita.app.ui.theme.skin.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.SkinType

@Composable
fun SkinTransitionOverlay(
    currentSkin: SkinType,
    onTransitionComplete: () -> Unit = {}
) {
    val skin = LolitaSkin.current
    val spec = skin.animations.skinTransition
    var isAnimating by remember { mutableStateOf(false) }
    var previousSkin by remember { mutableStateOf(currentSkin) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(currentSkin) {
        if (currentSkin != previousSkin) {
            isAnimating = true
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = spec.durationMs)
            )
            isAnimating = false
            previousSkin = currentSkin
            onTransitionComplete()
        }
    }

    if (isAnimating) {
        Box(Modifier.fillMaxSize()) {
            spec.overlay(progress.value)
        }
    }
}
