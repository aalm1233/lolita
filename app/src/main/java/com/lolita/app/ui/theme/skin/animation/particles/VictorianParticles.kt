package com.lolita.app.ui.theme.skin.animation.particles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.lolita.app.ui.theme.skin.animation.AmbientParticle
import kotlin.math.sin
import kotlin.random.Random

private val RoseRed = Color(0xFF9C254D)
private val DustGold = Color(0xFFD4A843)

class VictorianRosePetal : AmbientParticle() {
    override var x = 0f
    override var y = 0f
    override var alpha = 0f
    private var baseAlpha = 0f
    private var petalWidth = 0f
    private var petalHeight = 0f
    private var driftX = 0f
    private var driftY = 0f
    private var swayPhase = 0f
    private var swaySpeed = 0f
    private var rotation = 0f
    private var rotationSpeed = 0f

    override fun reset(width: Float, height: Float) {
        x = Random.nextFloat() * width
        y = -20f - Random.nextFloat() * 40f
        petalWidth = 5f + Random.nextFloat() * 6f
        petalHeight = 8f + Random.nextFloat() * 8f
        baseAlpha = 0.06f + Random.nextFloat() * 0.1f
        alpha = baseAlpha
        driftX = (-0.005f + Random.nextFloat() * 0.01f)
        driftY = 0.006f + Random.nextFloat() * 0.008f
        swayPhase = Random.nextFloat() * 6.28f
        swaySpeed = 0.0004f + Random.nextFloat() * 0.0006f
        rotation = Random.nextFloat() * 360f
        rotationSpeed = -0.008f + Random.nextFloat() * 0.016f
    }

    override fun update(deltaMs: Long, width: Float, height: Float) {
        swayPhase += swaySpeed * deltaMs
        x += (driftX + sin(swayPhase) * 0.008f) * deltaMs
        y += driftY * deltaMs
        rotation += rotationSpeed * deltaMs
        alpha = baseAlpha + sin(swayPhase * 0.8f) * baseAlpha * 0.35f
        if (y > height + petalHeight * 2f) reset(width, height)
        if (x > width + petalWidth * 2f) x = -petalWidth * 2f
        if (x < -petalWidth * 2f) x = width + petalWidth * 2f
    }

    override fun DrawScope.draw() {
        rotate(rotation, Offset(x, y)) {
            val petal = Path().apply {
                moveTo(x, y - petalHeight * 0.5f)
                quadraticBezierTo(x + petalWidth * 0.8f, y - petalHeight * 0.15f, x, y + petalHeight * 0.5f)
                quadraticBezierTo(x - petalWidth * 0.8f, y - petalHeight * 0.15f, x, y - petalHeight * 0.5f)
                close()
            }
            drawPath(petal, RoseRed.copy(alpha = alpha))
            drawLine(
                color = Color.White.copy(alpha = alpha * 0.3f),
                start = Offset(x, y - petalHeight * 0.4f),
                end = Offset(x, y + petalHeight * 0.4f),
                strokeWidth = 1f
            )
        }
    }
}

class VictorianGoldDust : AmbientParticle() {
    override var x = 0f
    override var y = 0f
    override var alpha = 0f
    private var baseAlpha = 0f
    private var radius = 0f
    private var driftX = 0f
    private var driftY = 0f
    private var swayPhase = 0f
    private var swaySpeed = 0f
    private var twinklePhase = 0f
    private var twinkleSpeed = 0f

    override fun reset(width: Float, height: Float) {
        x = Random.nextFloat() * width
        y = Random.nextFloat() * height
        radius = 1.5f + Random.nextFloat() * 2.5f
        baseAlpha = 0.05f + Random.nextFloat() * 0.12f
        alpha = baseAlpha
        driftX = (-0.002f + Random.nextFloat() * 0.004f)
        driftY = -(0.002f + Random.nextFloat() * 0.005f)
        swayPhase = Random.nextFloat() * 6.28f
        swaySpeed = 0.0006f + Random.nextFloat() * 0.0008f
        twinklePhase = Random.nextFloat() * 6.28f
        twinkleSpeed = 0.001f + Random.nextFloat() * 0.002f
    }

    override fun update(deltaMs: Long, width: Float, height: Float) {
        swayPhase += swaySpeed * deltaMs
        twinklePhase += twinkleSpeed * deltaMs
        x += (driftX + sin(swayPhase) * 0.004f) * deltaMs
        y += driftY * deltaMs
        alpha = baseAlpha * (0.5f + 0.5f * sin(twinklePhase))
        if (y < -radius * 2f) reset(width, height)
        if (x > width + radius * 2f) x = -radius * 2f
        if (x < -radius * 2f) x = width + radius * 2f
    }

    override fun DrawScope.draw() {
        drawCircle(DustGold.copy(alpha = alpha), radius, Offset(x, y))
        drawCircle(Color.White.copy(alpha = alpha * 0.4f), radius * 0.5f, Offset(x, y))
    }
}
