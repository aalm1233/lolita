package com.lolita.app.ui.theme.skin.animation.particles

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import com.lolita.app.ui.theme.skin.animation.AmbientParticle
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

class ClassicDiamondParticle : AmbientParticle() {
    override var x = 0f
    override var y = 0f
    override var alpha = 0f
    private var baseAlpha = 0f
    private var diamondSize = 0f
    private var driftSpeedX = 0f
    private var driftSpeedY = 0f
    private var sparklePhase = 0f
    private var sparkleSpeed = 0f

    override fun reset(width: Float, height: Float) {
        x = Random.nextFloat() * width
        y = Random.nextFloat() * height
        diamondSize = 6f + Random.nextFloat() * 6f
        baseAlpha = 0.15f + Random.nextFloat() * 0.2f
        alpha = baseAlpha
        sparklePhase = Random.nextFloat() * 6.28f
        sparkleSpeed = 0.004f + Random.nextFloat() * 0.005f
        driftSpeedX = 0.005f + Random.nextFloat() * 0.01f
        driftSpeedY = 0.005f + Random.nextFloat() * 0.01f
        if (Random.nextBoolean()) driftSpeedX = -driftSpeedX
        if (Random.nextBoolean()) driftSpeedY = -driftSpeedY
    }

    override fun update(deltaMs: Long, width: Float, height: Float) {
        x += driftSpeedX * deltaMs
        y += driftSpeedY * deltaMs
        sparklePhase += sparkleSpeed * deltaMs
        alpha = baseAlpha * max(0f, sin(sparklePhase))

        if (x > width + diamondSize) x = -diamondSize
        if (x < -diamondSize) x = width + diamondSize
        if (y > height + diamondSize) y = -diamondSize
        if (y < -diamondSize) y = height + diamondSize
    }

    override fun DrawScope.draw() {
        val gold = Color(0xFFD4AF37)
        val path = Path().apply {
            moveTo(x, y - diamondSize)
            lineTo(x + diamondSize * 0.6f, y)
            lineTo(x, y + diamondSize)
            lineTo(x - diamondSize * 0.6f, y)
            close()
        }
        drawPath(path, gold.copy(alpha = alpha), style = Fill)
    }
}
