package com.lolita.app.ui.theme.skin.animation.particles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.lolita.app.ui.theme.skin.animation.AmbientParticle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ChinesePlumBlossomParticle : AmbientParticle() {
    override var x = 0f
    override var y = 0f
    override var alpha = 0f
    private var petalSize = 0f
    private var fallSpeed = 0f
    private var wobblePhase = 0f
    private var wobbleSpeed = 0f
    private var rotation = 0f
    private var rotationSpeed = 0f

    override fun reset(width: Float, height: Float) {
        x = Random.nextFloat() * width
        y = -Random.nextFloat() * 80f
        petalSize = 20f + Random.nextFloat() * 16f
        fallSpeed = 0.1f + Random.nextFloat() * 0.2f
        alpha = 0.12f + Random.nextFloat() * 0.15f
        wobblePhase = Random.nextFloat() * 6.28f
        wobbleSpeed = 0.0006f + Random.nextFloat() * 0.001f
        rotation = Random.nextFloat() * 360f
        rotationSpeed = 0.015f + Random.nextFloat() * 0.03f
    }

    override fun update(deltaMs: Long, width: Float, height: Float) {
        y += fallSpeed * deltaMs
        wobblePhase += wobbleSpeed * deltaMs
        x += sin(wobblePhase) * 0.4f
        rotation += rotationSpeed * deltaMs
        if (y > height + petalSize * 2) reset(width, height)
    }

    override fun DrawScope.draw() {
        val petalColor = Color(0xFFC41E3A)
        val centerColor = Color(0xFFDAA520)
        val center = Offset(x, y)
        val dist = petalSize * 0.5f
        val petalRadius = petalSize * 0.18f

        for (i in 0 until 5) {
            val angle = (i * 2.0 * PI / 5.0 + rotation * PI / 180.0).toFloat()
            val px = x + cos(angle) * dist
            val py = y + sin(angle) * dist
            drawCircle(
                petalColor.copy(alpha = alpha),
                radius = petalRadius,
                center = Offset(px, py)
            )
        }
        drawCircle(
            centerColor.copy(alpha = alpha),
            radius = 4f,
            center = center
        )
    }
}
