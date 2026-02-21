package com.lolita.app.ui.theme.skin.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import com.lolita.app.ui.theme.Pink400
import com.lolita.app.ui.theme.SkinType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class ClickParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var alpha: Float,
    var scale: Float,
    var rotation: Float,
    var rotationSpeed: Float
)

@Composable
fun SkinClickParticles(
    trigger: State<Offset?>,
    spec: SkinClickFeedbackSpec,
    skinType: SkinType,
    modifier: Modifier = Modifier
) {
    if (!spec.hasParticles) return

    val particles = remember { mutableStateListOf<ClickParticle>() }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(trigger.value) {
        val pos = trigger.value ?: return@LaunchedEffect
        particles.clear()
        repeat(spec.particleCount) {
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            val speed = 2f + Random.nextFloat() * 3f
            particles.add(
                ClickParticle(
                    x = pos.x, y = pos.y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    alpha = 1f,
                    scale = 0.6f + Random.nextFloat() * 0.6f,
                    rotation = Random.nextFloat() * 360f,
                    rotationSpeed = (-2f + Random.nextFloat() * 4f)
                )
            )
        }
        progress.snapTo(0f)
        progress.animateTo(1f, tween(600))
        particles.clear()
    }

    if (particles.isEmpty()) return

    val currentProgress = progress.value

    Canvas(modifier.fillMaxSize()) {
        particles.forEach { p ->
            val alpha = (1f - currentProgress).coerceIn(0f, 1f)
            val drawX = p.x + p.vx * currentProgress * 20f
            val drawY = p.y + p.vy * currentProgress * 20f + currentProgress * currentProgress * 30f
            val drawScale = p.scale * (1f - currentProgress * 0.5f)
            val drawP = p.copy(x = drawX, y = drawY, alpha = alpha, scale = drawScale)
            if (alpha > 0.01f) {
                when (skinType) {
                    SkinType.DEFAULT -> drawHeartParticle(drawP)
                    SkinType.GOTHIC -> drawShardParticle(drawP)
                    SkinType.CHINESE -> drawInkDotParticle(drawP)
                    SkinType.CLASSIC -> drawSparkleParticle(drawP)
                }
            }
        }
    }
}

private fun DrawScope.drawHeartParticle(p: ClickParticle) {
    val r = 6f * p.scale
    val center = Offset(p.x, p.y)
    val path = Path().apply {
        moveTo(center.x, center.y + r * 0.3f)
        cubicTo(center.x - r, center.y - r * 0.5f,
            center.x - r * 0.5f, center.y - r,
            center.x, center.y - r * 0.3f)
        cubicTo(center.x + r * 0.5f, center.y - r,
            center.x + r, center.y - r * 0.5f,
            center.x, center.y + r * 0.3f)
    }
    drawPath(path, Pink400.copy(alpha = p.alpha), style = Fill)
}

private fun DrawScope.drawShardParticle(p: ClickParticle) {
    val s = 5f * p.scale
    val path = Path().apply {
        moveTo(p.x, p.y - s)
        lineTo(p.x + s * 0.6f, p.y + s * 0.5f)
        lineTo(p.x - s * 0.6f, p.y + s * 0.5f)
        close()
    }
    drawPath(path, Color(0xFF4A0E4E).copy(alpha = p.alpha), style = Fill)
}

private fun DrawScope.drawInkDotParticle(p: ClickParticle) {
    val r = (3f + 4f * p.scale)
    drawCircle(
        Color(0xFF2C2C2C).copy(alpha = p.alpha),
        radius = r,
        center = Offset(p.x, p.y)
    )
}

private fun DrawScope.drawSparkleParticle(p: ClickParticle) {
    val r = 4f * p.scale
    val gold = Color(0xFFD4AF37)
    drawCircle(gold.copy(alpha = p.alpha), radius = r, center = Offset(p.x, p.y))
    drawCircle(
        gold.copy(alpha = p.alpha * 0.3f),
        radius = r * 2f,
        center = Offset(p.x, p.y)
    )
}
