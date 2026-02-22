package com.lolita.app.ui.theme.skin.animation.particles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.lolita.app.ui.theme.skin.animation.AmbientParticle
import kotlin.math.sin
import kotlin.random.Random

class GothicEmberParticle : AmbientParticle() {
    override var x = 0f
    override var y = 0f
    override var alpha = 0f
    private var baseAlpha = 0f
    private var size = 0f
    private var riseSpeed = 0f
    private var wobblePhase = 0f
    private var wobbleSpeed = 0f
    private var tailLength = 0f

    override fun reset(width: Float, height: Float) {
        x = Random.nextFloat() * width
        y = height + Random.nextFloat() * 100f
        size = 3f + Random.nextFloat() * 5f
        baseAlpha = 0.2f + Random.nextFloat() * 0.2f
        alpha = baseAlpha
        riseSpeed = 0.2f + Random.nextFloat() * 0.4f
        wobblePhase = Random.nextFloat() * 6.28f
        wobbleSpeed = 0.001f + Random.nextFloat() * 0.002f
        tailLength = 15f + Random.nextFloat() * 10f
    }

    override fun update(deltaMs: Long, width: Float, height: Float) {
        y -= riseSpeed * deltaMs
        wobblePhase += wobbleSpeed * deltaMs
        x += sin(wobblePhase) * 0.3f
        alpha = baseAlpha * (y / height).coerceIn(0f, 1f)
        if (y < -tailLength) reset(width, height)
    }

    override fun DrawScope.draw() {
        val emberColor = Color(0xFF8B0000)
        drawCircle(
            emberColor.copy(alpha = alpha),
            radius = size,
            center = Offset(x, y)
        )
        drawLine(
            emberColor.copy(alpha = alpha * 0.5f),
            start = Offset(x, y),
            end = Offset(x, y + tailLength),
            strokeWidth = size * 0.6f
        )
    }
}
