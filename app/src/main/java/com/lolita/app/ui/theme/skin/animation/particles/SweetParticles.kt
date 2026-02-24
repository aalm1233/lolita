package com.lolita.app.ui.theme.skin.animation.particles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import com.lolita.app.ui.theme.Pink400
import com.lolita.app.ui.theme.skin.animation.AmbientParticle
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class SweetBubbleParticle : AmbientParticle() {
    override var x = 0f
    override var y = 0f
    override var alpha = 0f
    private var radius = 0f
    private var speed = 0f
    private var wobblePhase = 0f
    private var wobbleSpeed = 0f

    override fun reset(width: Float, height: Float) {
        x = Random.nextFloat() * width
        y = height + Random.nextFloat() * 100f
        radius = 8f + Random.nextFloat() * 16f
        speed = 0.3f + Random.nextFloat() * 0.5f
        alpha = 0.1f + Random.nextFloat() * 0.15f
        wobblePhase = Random.nextFloat() * 2f * PI.toFloat()
        wobbleSpeed = 0.001f + Random.nextFloat() * 0.002f
    }

    override fun update(deltaMs: Long, width: Float, height: Float) {
        y -= speed * deltaMs
        wobblePhase += wobbleSpeed * deltaMs
        x += sin(wobblePhase) * 0.3f
        if (y < -radius * 2) reset(width, height)
    }

    override fun DrawScope.draw() {
        drawCircle(
            Pink400.copy(alpha = alpha),
            radius = radius,
            center = Offset(x, y)
        )
    }
}
class SweetPetalParticle : AmbientParticle() {
    override var x = 0f
    override var y = 0f
    override var alpha = 0f
    private var petalSize = 0f
    private var speed = 0f
    private var wobblePhase = 0f
    private var wobbleSpeed = 0f
    private var rotation = 0f
    private var rotationSpeed = 0f

    override fun reset(width: Float, height: Float) {
        x = Random.nextFloat() * width
        y = -Random.nextFloat() * 50f
        petalSize = 12f + Random.nextFloat() * 16f
        speed = 0.15f + Random.nextFloat() * 0.25f
        alpha = 0.12f + Random.nextFloat() * 0.15f
        wobblePhase = Random.nextFloat() * 2f * PI.toFloat()
        wobbleSpeed = 0.0008f + Random.nextFloat() * 0.0015f
        rotation = Random.nextFloat() * 360f
        rotationSpeed = 0.02f + Random.nextFloat() * 0.04f
    }

    override fun update(deltaMs: Long, width: Float, height: Float) {
        y += speed * deltaMs
        wobblePhase += wobbleSpeed * deltaMs
        x += sin(wobblePhase) * 0.5f
        rotation += rotationSpeed * deltaMs
        if (y > height + petalSize * 2) reset(width, height)
    }

    override fun DrawScope.draw() {
        val center = Offset(x, y)
        val r = petalSize
        val path = Path().apply {
            moveTo(center.x, center.y + r * 0.3f)
            cubicTo(center.x - r, center.y - r * 0.5f,
                center.x - r * 0.5f, center.y - r,
                center.x, center.y - r * 0.3f)
            cubicTo(center.x + r * 0.5f, center.y - r,
                center.x + r, center.y - r * 0.5f,
                center.x, center.y + r * 0.3f)
        }
        drawPath(path, Pink400.copy(alpha = alpha), style = Fill)
    }
}
