package com.lolita.app.ui.theme.skin.animation.particles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.lolita.app.ui.theme.skin.animation.AmbientParticle
import kotlin.math.sin
import kotlin.random.Random

private val DaisyPetal = Color(0xFFF8E9C1)
private val DaisyCenter = Color(0xFFC56759)
private val LeafGreen = Color(0xFF7C9A69)

class CountryDaisyParticle : AmbientParticle() {
    override var x = 0f
    override var y = 0f
    override var alpha = 0f
    private var baseAlpha = 0f
    private var radius = 0f
    private var driftX = 0f
    private var driftY = 0f
    private var swayPhase = 0f
    private var swaySpeed = 0f
    private var rotation = 0f
    private var rotationSpeed = 0f

    override fun reset(width: Float, height: Float) {
        x = Random.nextFloat() * width
        y = Random.nextFloat() * height
        radius = 8f + Random.nextFloat() * 8f
        baseAlpha = 0.08f + Random.nextFloat() * 0.1f
        alpha = baseAlpha
        driftX = (-0.004f + Random.nextFloat() * 0.008f)
        driftY = 0.004f + Random.nextFloat() * 0.008f
        swayPhase = Random.nextFloat() * 6.28f
        swaySpeed = 0.0005f + Random.nextFloat() * 0.0007f
        rotation = Random.nextFloat() * 360f
        rotationSpeed = -0.01f + Random.nextFloat() * 0.02f
    }

    override fun update(deltaMs: Long, width: Float, height: Float) {
        swayPhase += swaySpeed * deltaMs
        x += (driftX + sin(swayPhase) * 0.01f) * deltaMs
        y += driftY * deltaMs
        rotation += rotationSpeed * deltaMs
        alpha = baseAlpha + sin(swayPhase * 0.8f) * baseAlpha * 0.35f

        if (y > height + radius * 2f) reset(width, 0f)
        if (x > width + radius * 2f) x = -radius * 2f
        if (x < -radius * 2f) x = width + radius * 2f
    }

    override fun DrawScope.draw() {
        rotate(rotation, Offset(x, y)) {
            repeat(6) { index ->
                rotate(index * 60f, Offset(x, y)) {
                    drawOval(
                        color = DaisyPetal.copy(alpha = alpha),
                        topLeft = Offset(x - radius * 0.3f, y - radius * 1.2f),
                        size = Size(radius * 0.6f, radius * 1.1f)
                    )
                }
            }
            drawCircle(DaisyCenter.copy(alpha = alpha), radius * 0.26f, Offset(x, y))
        }
    }
}

class CountryLeafParticle : AmbientParticle() {
    override var x = 0f
    override var y = 0f
    override var alpha = 0f
    private var baseAlpha = 0f
    private var leafSize = 0f
    private var driftX = 0f
    private var driftY = 0f
    private var swayPhase = 0f
    private var swaySpeed = 0f
    private var rotation = 0f
    private var rotationAmplitude = 0f

    override fun reset(width: Float, height: Float) {
        x = Random.nextFloat() * width
        y = Random.nextFloat() * height
        leafSize = 10f + Random.nextFloat() * 12f
        baseAlpha = 0.06f + Random.nextFloat() * 0.08f
        alpha = baseAlpha
        driftX = (-0.006f + Random.nextFloat() * 0.012f)
        driftY = 0.003f + Random.nextFloat() * 0.006f
        swayPhase = Random.nextFloat() * 6.28f
        swaySpeed = 0.0004f + Random.nextFloat() * 0.0005f
        rotation = Random.nextFloat() * 360f
        rotationAmplitude = 10f + Random.nextFloat() * 15f
    }

    override fun update(deltaMs: Long, width: Float, height: Float) {
        swayPhase += swaySpeed * deltaMs
        x += (driftX + sin(swayPhase) * 0.006f) * deltaMs
        y += driftY * deltaMs
        alpha = baseAlpha + sin(swayPhase) * baseAlpha * 0.45f

        if (y > height + leafSize * 2f) reset(width, 0f)
        if (x > width + leafSize * 2f) x = -leafSize * 2f
        if (x < -leafSize * 2f) x = width + leafSize * 2f
    }

    override fun DrawScope.draw() {
        rotate(rotation + sin(swayPhase) * rotationAmplitude, Offset(x, y)) {
            val path = Path().apply {
                moveTo(x, y - leafSize)
                quadraticBezierTo(x + leafSize * 0.8f, y - leafSize * 0.15f, x, y + leafSize)
                quadraticBezierTo(x - leafSize * 0.8f, y - leafSize * 0.15f, x, y - leafSize)
                close()
            }
            drawPath(path, LeafGreen.copy(alpha = alpha))
            drawPath(
                path = Path().apply {
                    moveTo(x, y - leafSize * 0.82f)
                    lineTo(x, y + leafSize * 0.82f)
                },
                color = Color.White.copy(alpha = alpha * 0.35f),
                style = Stroke(width = 1.2f)
            )
        }
    }
}
