package com.lolita.app.ui.theme.skin.animation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.SkinType
import com.lolita.app.ui.theme.skin.animation.particles.ChineseCloudParticle
import com.lolita.app.ui.theme.skin.animation.particles.ChinesePlumBlossomParticle
import com.lolita.app.ui.theme.skin.animation.particles.ClassicDiamondParticle
import com.lolita.app.ui.theme.skin.animation.particles.ClassicSparkleParticle
import com.lolita.app.ui.theme.skin.animation.particles.GothicEmberParticle
import com.lolita.app.ui.theme.skin.animation.particles.GothicSmokeParticle
import com.lolita.app.ui.theme.skin.animation.particles.CountryDaisyParticle
import com.lolita.app.ui.theme.skin.animation.particles.CountryLeafParticle
import com.lolita.app.ui.theme.skin.animation.particles.VictorianGoldDust
import com.lolita.app.ui.theme.skin.animation.particles.VictorianRosePetal
import com.lolita.app.ui.theme.skin.animation.particles.SweetBubbleParticle
import com.lolita.app.ui.theme.skin.animation.particles.SweetPetalParticle
import com.lolita.app.ui.theme.skin.animation.particles.SweetStarParticle
import com.lolita.app.ui.theme.skin.animation.particles.NavyAnchorParticle
import com.lolita.app.ui.theme.skin.animation.particles.NavyBubbleParticle
import com.lolita.app.ui.theme.skin.animation.particles.NavyRopeKnotParticle
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

    val particles = remember(skin.skinType, spec.backgroundParticleCount) {
        createParticles(skin.skinType, spec.backgroundParticleCount)
    }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val frameVersion = remember { mutableLongStateOf(0L) }

    LaunchedEffect(particles, canvasSize) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            particles.forEach { it.reset(canvasSize.width.toFloat(), canvasSize.height.toFloat()) }
            frameVersion.longValue++
        }
    }

    LaunchedEffect(particles, canvasSize, isScrolling) {
        if (isScrolling || canvasSize.width <= 0 || canvasSize.height <= 0) return@LaunchedEffect
        var previousFrameNanos = 0L
        while (isActive) {
            withFrameNanos { frameNanos ->
                if (previousFrameNanos == 0L) {
                    previousFrameNanos = frameNanos
                } else if (frameNanos - previousFrameNanos >= 16_000_000L) {
                    // Keep the existing ~60 Hz particle cadence on high-refresh displays.
                    val deltaMs = ((frameNanos - previousFrameNanos) / 1_000_000L).coerceIn(0L, 100L)
                    previousFrameNanos = frameNanos
                    particles.forEach { it.update(deltaMs, canvasSize.width.toFloat(), canvasSize.height.toFloat()) }
                    frameVersion.longValue++
                }
            }
        }
    }

    Canvas(modifier.fillMaxSize().onSizeChanged { canvasSize = it }) {
        // Observe frames in drawing; frozen particles remain visible while scrolling.
        @Suppress("UNUSED_VARIABLE")
        val currentFrame = frameVersion.longValue
        particles.forEach { particle -> with(particle) { draw() } }
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
        SkinType.COUNTRY -> {
            val daisies = List((count * 0.55f).toInt().coerceAtLeast(1)) { CountryDaisyParticle() }
            val leaves = List((count * 0.45f).toInt().coerceAtLeast(1)) { CountryLeafParticle() }
            daisies + leaves
        }
        SkinType.VICTORIAN -> {
            val petals = List((count * 0.55f).toInt().coerceAtLeast(1)) { VictorianRosePetal() }
            val dust = List((count * 0.45f).toInt().coerceAtLeast(1)) { VictorianGoldDust() }
            petals + dust
        }
    }
}
