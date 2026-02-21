package com.lolita.app.ui.theme.skin.animation.particles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.lolita.app.ui.theme.skin.animation.AmbientParticle
import kotlin.math.sin
import kotlin.random.Random

class ClassicSparkleParticle : AmbientParticle() {
    override var x = 0f
    override var y = 0f
    override var alpha = 0f
    private var baseAlpha = 0f
    private var breathPhase = 0f
    private var breathSpeed = 0f
    private var driftSpeedX = 0f
    private var driftSpeedY = 0f
    private var radius = 0f

    override fun reset(width: Float, height: Float) {
        x = Random.nextFloat() * width
        y = Random.nextFloat() * height
        radius = 10f + Random.nextFloat() * 30f
        baseAlpha = 0.1f + Random.nextFloat() * 0.15f
        alpha = baseAlpha
        breathPhase = Random.nextFloat() * 6.28f
        breathSpeed = 0.0004f + Random.nextFloat() * 0.0005f
        driftSpeedX = 0.005f + Random.nextFloat() * 0.01f
        driftSpeedY = 0.003f + Random.nextFloat() * 0.008f
        if (Random.nextBoolean()) driftSpeedX = -driftSpeedX
        if (Random.nextBoolean()) driftSpeedY = -driftSpeedY
    }

    override fun update(deltaMs: Long, width: Float, height: Float) {
        x += driftSpeedX * deltaMs
        y += driftSpeedY * deltaMs
        breathPhase += breathSpeed * deltaMs
        alpha = baseAlpha + sin(breathPhase) * baseAlpha * 0.6f

        if (x > width + radius) x = -radius
        if (x < -radius) x = width + radius
        if (y > height + radius) y = -radius
        if (y < -radius) y = height + radius
    }

    override fun DrawScope.draw() {
        val gold = Color(0xFFD4AF37)
        drawCircle(
            Brush.radialGradient(
                listOf(
                    gold.copy(alpha = alpha),
                    gold.copy(alpha = alpha * 0.3f),
                    Color.Transparent
                ),
                center = Offset(x, y),
                radius = radius.coerceAtLeast(1f)
            ),
            radius = radius,
            center = Offset(x, y)
        )
    }
}
