package com.lolita.app.ui.theme.skin.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.Pink400
import com.lolita.app.ui.theme.SkinType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class OverlayParticle(
    val startX: Float, val startY: Float,
    val endX: Float, val endY: Float,
    val size: Float, val rotationStart: Float, val rotationEnd: Float
)

@Composable
fun SkinNavigationOverlay(
    isTransitioning: Boolean,
    skinType: SkinType,
    modifier: Modifier = Modifier
) {
    val skin = LolitaSkin.current
    val spec = skin.animations.navigation
    if (!spec.hasOverlayEffect) return

    val progress = remember { Animatable(0f) }
    var particles by remember { mutableStateOf(emptyList<OverlayParticle>()) }

    LaunchedEffect(isTransitioning) {
        if (isTransitioning) {
            particles = List(6) { createOverlayParticle(skinType) }
            progress.snapTo(0f)
            progress.animateTo(1f, tween(spec.overlayDuration))
            particles = emptyList()
        }
    }

    if (particles.isEmpty()) return

    val p = progress.value

    Canvas(modifier.fillMaxSize()) {
        particles.forEach { particle ->
            val x = particle.startX + (particle.endX - particle.startX) * p
            val y = particle.startY + (particle.endY - particle.startY) * p
            val alpha = if (p < 0.5f) p * 2f else (1f - p) * 2f
            when (skinType) {
                SkinType.DEFAULT -> drawPetalOverlay(x * size.width, y * size.height, particle.size, alpha)
                SkinType.GOTHIC -> drawShardOverlay(x * size.width, y * size.height, particle.size, alpha)
                SkinType.CHINESE -> drawInkSplashOverlay(x * size.width, y * size.height, particle.size, alpha)
                SkinType.CLASSIC -> drawGoldSweepOverlay(p, alpha)
                SkinType.NAVY -> drawWaveRippleOverlay(x * size.width, y * size.height, particle.size, alpha)
            }
        }
    }
}

private fun createOverlayParticle(skinType: SkinType): OverlayParticle {
    return when (skinType) {
        SkinType.DEFAULT -> OverlayParticle(
            startX = Random.nextFloat(), startY = -0.1f,
            endX = Random.nextFloat(), endY = 1.1f,
            size = 8f + Random.nextFloat() * 12f,
            rotationStart = Random.nextFloat() * 360f,
            rotationEnd = Random.nextFloat() * 360f
        )
        SkinType.GOTHIC -> OverlayParticle(
            startX = 0.5f, startY = 0.5f,
            endX = Random.nextFloat(), endY = Random.nextFloat(),
            size = 5f + Random.nextFloat() * 8f,
            rotationStart = 0f, rotationEnd = Random.nextFloat() * 180f
        )
        SkinType.CHINESE -> OverlayParticle(
            startX = if (Random.nextBoolean()) -0.1f else 1.1f,
            startY = Random.nextFloat(),
            endX = 0.3f + Random.nextFloat() * 0.4f,
            endY = Random.nextFloat(),
            size = 6f + Random.nextFloat() * 10f,
            rotationStart = 0f, rotationEnd = 0f
        )
        SkinType.CLASSIC -> OverlayParticle(
            startX = 0f, startY = 0f, endX = 1f, endY = 0f,
            size = 0f, rotationStart = 0f, rotationEnd = 0f
        )
        SkinType.NAVY -> OverlayParticle(
            startX = Random.nextFloat(), startY = 1.1f,
            endX = Random.nextFloat(), endY = -0.1f,
            size = 5f + Random.nextFloat() * 8f,
            rotationStart = 0f, rotationEnd = 0f
        )
    }
}

private fun DrawScope.drawPetalOverlay(x: Float, y: Float, s: Float, alpha: Float) {
    val path = Path().apply {
        moveTo(x, y + s * 0.3f)
        cubicTo(x - s, y - s * 0.5f, x - s * 0.5f, y - s, x, y - s * 0.3f)
        cubicTo(x + s * 0.5f, y - s, x + s, y - s * 0.5f, x, y + s * 0.3f)
    }
    drawPath(path, Pink400.copy(alpha = alpha * 0.4f))
}

private fun DrawScope.drawShardOverlay(x: Float, y: Float, s: Float, alpha: Float) {
    val path = Path().apply {
        moveTo(x, y - s)
        lineTo(x + s * 0.6f, y + s * 0.5f)
        lineTo(x - s * 0.6f, y + s * 0.5f)
        close()
    }
    drawPath(path, Color(0xFF4A0E4E).copy(alpha = alpha * 0.3f))
}

private fun DrawScope.drawInkSplashOverlay(x: Float, y: Float, s: Float, alpha: Float) {
    drawCircle(
        Color(0xFF2C2C2C).copy(alpha = alpha * 0.25f),
        radius = s,
        center = Offset(x, y)
    )
}

private fun DrawScope.drawGoldSweepOverlay(progress: Float, alpha: Float) {
    val sweepX = size.width * progress
    drawRect(
        Brush.horizontalGradient(
            listOf(Color.Transparent, Color(0xFFD4AF37).copy(alpha = alpha * 0.2f), Color.Transparent),
            startX = sweepX - size.width * 0.15f,
            endX = sweepX + size.width * 0.15f
        )
    )
}

private fun DrawScope.drawWaveRippleOverlay(x: Float, y: Float, s: Float, alpha: Float) {
    val skyBlue = Color(0xFF4A90D9)
    drawCircle(
        skyBlue.copy(alpha = alpha * 0.2f),
        radius = s * 1.5f,
        center = Offset(x, y)
    )
    drawCircle(
        skyBlue.copy(alpha = alpha * 0.3f),
        radius = s,
        center = Offset(x, y),
        style = Stroke(width = 1.5f)
    )
}
