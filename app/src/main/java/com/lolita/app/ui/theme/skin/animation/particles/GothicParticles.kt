package com.lolita.app.ui.theme.skin.animation.particles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.lolita.app.ui.theme.skin.animation.AmbientParticle
import kotlin.math.sin
import kotlin.random.Random

class GothicSmokeParticle : AmbientParticle() {
    override var x = 0f
    override var y = 0f
    override var alpha = 0f
    private var baseAlpha = 0f
    private var breathPhase = 0f
    private var breathSpeed = 0f
    private var driftSpeed = 0f
    private var radius = 0f

    override fun reset(width: Float, height: Float) {
        x = Random.nextFloat() * width
        y = height * 0.3f + Random.nextFloat() * height * 0.5f
        radius = 120f + Random.nextFloat() * 120f
        baseAlpha = 0.08f + Random.nextFloat() * 0.12f
        alpha = baseAlpha
        breathPhase = Random.nextFloat() * 6.28f
        breathSpeed = 0.0003f + Random.nextFloat() * 0.0004f
        driftSpeed = 0.02f + Random.nextFloat() * 0.03f
        if (Random.nextBoolean()) driftSpeed = -driftSpeed
    }

    override fun update(deltaMs: Long, width: Float, height: Float) {
        x += driftSpeed * deltaMs
        breathPhase += breathSpeed * deltaMs
        alpha = baseAlpha + sin(breathPhase) * baseAlpha * 0.5f
        if (x > width + radius) x = -radius
        if (x < -radius) x = width + radius
    }

    override fun DrawScope.draw() {
        drawCircle(
            Brush.radialGradient(
                listOf(
                    Color.Black.copy(alpha = alpha),
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
