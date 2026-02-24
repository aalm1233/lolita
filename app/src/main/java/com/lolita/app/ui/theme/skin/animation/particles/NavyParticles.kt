package com.lolita.app.ui.theme.skin.animation.particles

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.lolita.app.ui.theme.skin.animation.AmbientParticle
import kotlin.math.sin
import kotlin.random.Random

class NavyAnchorParticle : AmbientParticle() {
    override var x = 0f
    override var y = 0f
    override var alpha = 0f
    private var baseAlpha = 0f
    private var anchorSize = 0f
    private var breathPhase = 0f
    private var breathSpeed = 0f
    private var driftSpeedX = 0f
    private var driftSpeedY = 0f
    private var rotation = 0f
    private var rockPhase = 0f
    private var rockSpeed = 0f
    private var rockAmplitude = 0f

    override fun reset(width: Float, height: Float) {
        x = Random.nextFloat() * width
        y = Random.nextFloat() * height
        anchorSize = 16f + Random.nextFloat() * 12f
        baseAlpha = 0.08f + Random.nextFloat() * 0.12f
        alpha = baseAlpha
        breathPhase = Random.nextFloat() * 6.28f
        breathSpeed = 0.0005f + Random.nextFloat() * 0.0006f
        driftSpeedX = 0.005f + Random.nextFloat() * 0.008f
        driftSpeedY = 0.003f + Random.nextFloat() * 0.006f
        if (Random.nextBoolean()) driftSpeedX = -driftSpeedX
        if (Random.nextBoolean()) driftSpeedY = -driftSpeedY
        rockPhase = Random.nextFloat() * 6.28f
        rockSpeed = 0.001f + Random.nextFloat() * 0.0015f
        rockAmplitude = 8f + Random.nextFloat() * 7f
    }

    override fun update(deltaMs: Long, width: Float, height: Float) {
        x += driftSpeedX * deltaMs
        y += driftSpeedY * deltaMs
        breathPhase += breathSpeed * deltaMs
        alpha = baseAlpha + sin(breathPhase) * baseAlpha * 0.6f
        rockPhase += rockSpeed * deltaMs
        rotation = sin(rockPhase) * rockAmplitude

        if (x > width + anchorSize) x = -anchorSize
        if (x < -anchorSize) x = width + anchorSize
        if (y > height + anchorSize) y = -anchorSize
        if (y < -anchorSize) y = height + anchorSize
    }

    override fun DrawScope.draw() {
        val skyBlue = Color(0xFF4A90D9)
        val s = anchorSize
        rotate(rotation, pivot = androidx.compose.ui.geometry.Offset(x, y)) {
            val path = Path().apply {
                // Ring at top
                addOval(
                    androidx.compose.ui.geometry.Rect(
                        x - s * 0.15f, y - s,
                        x + s * 0.15f, y - s * 0.7f
                    )
                )
                // Shank (vertical line)
                moveTo(x, y - s * 0.7f)
                lineTo(x, y + s * 0.6f)
                // Crossbar
                moveTo(x - s * 0.4f, y - s * 0.35f)
                lineTo(x + s * 0.4f, y - s * 0.35f)
                // Curved arms at bottom
                moveTo(x - s * 0.5f, y + s * 0.2f)
                cubicTo(
                    x - s * 0.5f, y + s * 0.8f,
                    x - s * 0.1f, y + s * 0.9f,
                    x, y + s * 0.6f
                )
                moveTo(x + s * 0.5f, y + s * 0.2f)
                cubicTo(
                    x + s * 0.5f, y + s * 0.8f,
                    x + s * 0.1f, y + s * 0.9f,
                    x, y + s * 0.6f
                )
            }
            drawPath(path, skyBlue.copy(alpha = alpha), style = Stroke(width = 1.5f))
        }
    }
}

class NavyRopeKnotParticle : AmbientParticle() {
    override var x = 0f
    override var y = 0f
    override var alpha = 0f
    private var baseAlpha = 0f
    private var knotSize = 0f
    private var breathPhase = 0f
    private var breathSpeed = 0f
    private var driftSpeedX = 0f
    private var driftSpeedY = 0f
    private var rotation = 0f
    private var rotationSpeed = 0f

    override fun reset(width: Float, height: Float) {
        x = Random.nextFloat() * width
        y = Random.nextFloat() * height
        knotSize = 12f + Random.nextFloat() * 8f
        baseAlpha = 0.08f + Random.nextFloat() * 0.10f
        alpha = baseAlpha
        breathPhase = Random.nextFloat() * 6.28f
        breathSpeed = 0.0004f + Random.nextFloat() * 0.0005f
        driftSpeedX = 0.004f + Random.nextFloat() * 0.007f
        driftSpeedY = 0.003f + Random.nextFloat() * 0.006f
        if (Random.nextBoolean()) driftSpeedX = -driftSpeedX
        if (Random.nextBoolean()) driftSpeedY = -driftSpeedY
        rotation = Random.nextFloat() * 360f
        rotationSpeed = 0.008f + Random.nextFloat() * 0.012f
        if (Random.nextBoolean()) rotationSpeed = -rotationSpeed
    }

    override fun update(deltaMs: Long, width: Float, height: Float) {
        x += driftSpeedX * deltaMs
        y += driftSpeedY * deltaMs
        breathPhase += breathSpeed * deltaMs
        alpha = baseAlpha + sin(breathPhase) * baseAlpha * 0.5f
        rotation += rotationSpeed * deltaMs

        if (x > width + knotSize) x = -knotSize
        if (x < -knotSize) x = width + knotSize
        if (y > height + knotSize) y = -knotSize
        if (y < -knotSize) y = height + knotSize
    }

    override fun DrawScope.draw() {
        val gold = Color(0xFFDAA520)
        val s = knotSize
        rotate(rotation, pivot = androidx.compose.ui.geometry.Offset(x, y)) {
            val path = Path().apply {
                // First loop (top-left)
                moveTo(x, y)
                cubicTo(
                    x - s, y - s * 1.2f,
                    x - s * 1.2f, y + s * 0.5f,
                    x, y
                )
                // Second loop (bottom-right)
                cubicTo(
                    x + s, y + s * 1.2f,
                    x + s * 1.2f, y - s * 0.5f,
                    x, y
                )
            }
            drawPath(path, gold.copy(alpha = alpha), style = Stroke(width = 1.5f))
        }
    }
}
