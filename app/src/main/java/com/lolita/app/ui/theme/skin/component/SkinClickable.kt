package com.lolita.app.ui.theme.skin.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerEventPass
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.SkinClickParticles
import com.lolita.app.ui.theme.skin.animation.SkinRippleEffect

@Composable
fun Modifier.skinClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val skin = LolitaSkin.current
    val clickFeedback = skin.animations.clickFeedback
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) clickFeedback.pressScale else 1f,
        animationSpec = clickFeedback.scaleAnimationSpec,
        label = "skinPressScale"
    )

    val clickPosition = remember { mutableStateOf<Offset?>(null) }

    return this
        .scale(scale)
        .pointerInput(enabled) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val down = event.changes.firstOrNull { it.pressed && !it.previousPressed }
                    if (down != null && enabled) {
                        clickPosition.value = down.position
                    }
                }
            }
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

@Composable
fun SkinClickableBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val skin = LolitaSkin.current
    val clickFeedback = skin.animations.clickFeedback
    val interactionSource = remember { MutableInteractionSource() }
    val clickPosition = remember { mutableStateOf<Offset?>(null) }

    Box(modifier = modifier.skinClickable(enabled, onClick)) {
        content()
        SkinRippleEffect(
            interactionSource = interactionSource,
            spec = clickFeedback,
            modifier = Modifier.matchParentSize()
        )
        SkinClickParticles(
            trigger = clickPosition,
            spec = clickFeedback,
            skinType = skin.skinType,
            modifier = Modifier.matchParentSize()
        )
    }
}
