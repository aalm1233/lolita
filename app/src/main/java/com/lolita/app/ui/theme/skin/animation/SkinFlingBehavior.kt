package com.lolita.app.ui.theme.skin.animation

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.lolita.app.ui.theme.LolitaSkin
import kotlin.math.abs

@Composable
fun rememberSkinFlingBehavior(): FlingBehavior {
    val skin = LolitaSkin.current
    val friction = skin.animations.listAnimation.flingFrictionMultiplier
    val defaultFling = ScrollableDefaults.flingBehavior()

    return if (friction == 1.0f) {
        defaultFling
    } else {
        remember(friction) {
            SkinFlingBehavior(frictionMultiplier = friction)
        }
    }
}

private class SkinFlingBehavior(
    private val frictionMultiplier: Float
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        if (abs(initialVelocity) < 0.5f) return initialVelocity

        var lastValue = 0f
        var remainingVelocity = initialVelocity
        val animationState = AnimationState(
            initialValue = 0f,
            initialVelocity = initialVelocity
        )

        animationState.animateDecay(exponentialDecay(frictionMultiplier = frictionMultiplier)) {
            // AnimationState already contains this frame's value before this callback.
            val delta = value - lastValue
            lastValue = value
            val consumed = scrollBy(delta)
            remainingVelocity = velocity
            if (abs(delta - consumed) > 0.5f) {
                cancelAnimation()
            }
        }

        return remainingVelocity
    }
}
