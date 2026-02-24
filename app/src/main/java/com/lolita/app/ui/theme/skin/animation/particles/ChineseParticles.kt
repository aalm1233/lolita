package com.lolita.app.ui.theme.skin.animation.particles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.lolita.app.ui.theme.skin.animation.AmbientParticle
import kotlin.math.sin
import kotlin.random.Random

class ChineseCloudParticle : AmbientParticle() {
    override var x = 0f
    override var y = 0f
    override var alpha = 0f
    private var baseAlpha = 0f
    private var breathPhase = 0f
    private var breathSpeed = 0f
    private var driftSpeed = 0f
    private var scaleX = 1f
    private var scaleY = 1f
    private var cloudWidth = 0f
    private var cloudHeight = 0f

    override fun reset(width: Float, height: Float) {
        x = Random.nextFloat() * width
        y = height * 0.1f + Random.nextFloat() * height * 0.4f
        cloudWidth = 160f + Random.nextFloat() * 120f
        cloudHeight = 60f + Random.nextFloat() * 40f
        baseAlpha = 0.05f + Random.nextFloat() * 0.1f
        alpha = baseAlpha
        breathPhase = Random.nextFloat() * 6.28f
        breathSpeed = 0.0002f + Random.nextFloat() * 0.0003f
        driftSpeed = 0.01f + Random.nextFloat() * 0.02f
        if (Random.nextBoolean()) driftSpeed = -driftSpeed
    }

    override fun update(deltaMs: Long, width: Float, height: Float) {
        x += driftSpeed * deltaMs
        breathPhase += breathSpeed * deltaMs
        alpha = baseAlpha + sin(breathPhase) * baseAlpha * 0.4f
        scaleX = 1f + sin(breathPhase * 0.7f) * 0.05f
        scaleY = 1f + sin(breathPhase * 0.5f) * 0.03f
        if (x > width + cloudWidth) x = -cloudWidth
        if (x < -cloudWidth) x = width + cloudWidth
    }

    override fun DrawScope.draw() {
        val inkColor = Color(0xFF2C2C2C).copy(alpha = alpha)
        val w = cloudWidth * scaleX
        val h = cloudHeight * scaleY
        val path = Path().apply {
            moveTo(x - w * 0.5f, y)
            cubicTo(x - w * 0.4f, y - h, x - w * 0.1f, y - h * 1.2f, x, y - h * 0.8f)
            cubicTo(x + w * 0.1f, y - h * 1.3f, x + w * 0.35f, y - h * 0.9f, x + w * 0.5f, y)
            cubicTo(x + w * 0.3f, y + h * 0.3f, x - w * 0.3f, y + h * 0.3f, x - w * 0.5f, y)
            close()
        }
        drawPath(path, inkColor)
    }
}
