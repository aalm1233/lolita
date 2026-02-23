package com.lolita.app.ui.theme.skin.animation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.SkinType
import com.lolita.app.ui.theme.skin.animation.particles.ChineseCloudParticle
import com.lolita.app.ui.theme.skin.animation.particles.ChinesePlumBlossomParticle
import com.lolita.app.ui.theme.skin.animation.particles.ClassicDiamondParticle
import com.lolita.app.ui.theme.skin.animation.particles.ClassicSparkleParticle
import com.lolita.app.ui.theme.skin.animation.particles.GothicEmberParticle
import com.lolita.app.ui.theme.skin.animation.particles.GothicSmokeParticle
import com.lolita.app.ui.theme.skin.animation.particles.SweetBubbleParticle
import com.lolita.app.ui.theme.skin.animation.particles.SweetPetalParticle
import com.lolita.app.ui.theme.skin.animation.particles.SweetStarParticle
import com.lolita.app.ui.theme.skin.animation.particles.NavyAnchorParticle
import com.lolita.app.ui.theme.skin.animation.particles.NavyBubbleParticle
import com.lolita.app.ui.theme.skin.animation.particles.NavyRopeKnotParticle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

abstract class AmbientParticle {
    abstract var x: Float
    abstract var y: Float
    abstract var alpha: Float
    abstract fun update(deltaMs: Long, width: Float, height: Float)
    abstract fun DrawScope.draw()
    abstract fun reset(width: Float, height: Float)
}

@Composable
fun SkinBackgroundAnimation(
    modifier: Modifier = Modifier
) {
    val skin = LolitaSkin.current
    val spec = skin.animations.ambientAnimation

    if (!spec.backgroundEnabled) return

    val isScrolling by LocalIsListScrolling.current

    val particles = remember(skin.skinType) {
        createParticles(skin.skinType, spec.backgroundParticleCount)
    }

    val frameTime = remember { mutableLongStateOf(0L) }

    LaunchedEffect(skin.skinType, isScrolling) {
        // Initialize particles with a dummy size, they'll reset on first draw
        particles.forEach { it.reset(1080f, 1920f) }
        if (!isScrolling) {
            while (isActive) {
                delay(16L) // ~60fps
                frameTime.longValue = System.nanoTime() / 1_000_000L
            }
        }
    }

    Canvas(modifier.fillMaxSize()) {
        // Read frameTime to trigger recomposition
        @Suppress("UNUSED_VARIABLE")
        val currentFrame = frameTime.longValue
        if (!isScrolling) {
            particles.forEach { particle ->
                particle.update(16L, size.width, size.height)
                with(particle) { draw() }
            }
        }
    }
}

private fun createParticles(skinType: SkinType, count: Int): List<AmbientParticle> {
    return when (skinType) {
        SkinType.DEFAULT -> {
            val bubbles = List((count * 0.4f).toInt().coerceAtLeast(1)) { SweetBubbleParticle() }
            val petals = List((count * 0.3f).toInt().coerceAtLeast(1)) { SweetPetalParticle() }
            val stars = List((count * 0.3f).toInt().coerceAtLeast(1)) { SweetStarParticle() }
            bubbles + petals + stars
        }
        SkinType.GOTHIC -> {
            val smoke = List((count * 0.5f).toInt().coerceAtLeast(1)) { GothicSmokeParticle() }
            val embers = List((count * 0.5f).toInt().coerceAtLeast(1)) { GothicEmberParticle() }
            smoke + embers
        }
        SkinType.CHINESE -> {
            val clouds = List((count * 0.4f).toInt().coerceAtLeast(1)) { ChineseCloudParticle() }
            val blossoms = List((count * 0.6f).toInt().coerceAtLeast(1)) { ChinesePlumBlossomParticle() }
            clouds + blossoms
        }
        SkinType.CLASSIC -> {
            val sparkles = List((count * 0.5f).toInt().coerceAtLeast(1)) { ClassicSparkleParticle() }
            val diamonds = List((count * 0.5f).toInt().coerceAtLeast(1)) { ClassicDiamondParticle() }
            sparkles + diamonds
        }
        SkinType.NAVY -> {
            val anchors = List((count * 0.4f).toInt().coerceAtLeast(1)) { NavyAnchorParticle() }
            val knots = List((count * 0.3f).toInt().coerceAtLeast(1)) { NavyRopeKnotParticle() }
            val bubbles = List((count * 0.3f).toInt().coerceAtLeast(1)) { NavyBubbleParticle() }
            anchors + knots + bubbles
        }
    }
}
