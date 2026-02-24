package com.lolita.app.ui.theme.skin.animation.particles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import com.lolita.app.ui.theme.Pink400
import com.lolita.app.ui.theme.skin.animation.AmbientParticle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class SweetStarParticle : AmbientParticle() {
    override var x = 0f
    override var y = 0f
    override var alpha = 0f
    private var baseAlpha = 0f
    private var starSize = 0f
    private var driftSpeedX = 0f
    private var driftSpeedY = 0f
    private var breathPhase = 0f
    private var breathSpeed = 0f

    override fun reset(width: Float, height: Float) {
        x = Random.nextFloat() * width
        y = Random.nextFloat() * height
        starSize = 16f + Random.nextFloat() * 14f
        baseAlpha = 0.15f + Random.nextFloat() * 0.2f
        alpha = baseAlpha
        breathPhase = Random.nextFloat() * 6.28f
        breathSpeed = 0.003f + Random.nextFloat() * 0.004f
        driftSpeedX = 0.005f + Random.nextFloat() * 0.01f
        driftSpeedY = 0.005f + Random.nextFloat() * 0.01f
        if (Random.nextBoolean()) driftSpeedX = -driftSpeedX
        if (Random.nextBoolean()) driftSpeedY = -driftSpeedY
    }

    override fun update(deltaMs: Long, width: Float, height: Float) {
        x += driftSpeedX * deltaMs
        y += driftSpeedY * deltaMs
        breathPhase += breathSpeed * deltaMs
        alpha = baseAlpha + sin(breathPhase * 3f) * baseAlpha * 0.8f

        if (x > width + starSize) x = -starSize
        if (x < -starSize) x = width + starSize
        if (y > height + starSize) y = -starSize
        if (y < -starSize) y = height + starSize
    }

    override fun DrawScope.draw() {
        val outerRadius = starSize
        val innerRadius = starSize * 0.4f
        val path = Path().apply {
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) outerRadius else innerRadius
                val angle = (i * PI / 5f - PI / 2f).toFloat()
                val px = x + cos(angle) * r
                val py = y + sin(angle) * r
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
            close()
        }
        drawPath(path, Pink400.copy(alpha = alpha), style = Fill)
    }
}
