package com.lolita.app.ui.theme.skin.animation.particles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.lolita.app.ui.theme.skin.animation.AmbientParticle
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class NavyBubbleParticle : AmbientParticle() {
    override var x = 0f
    override var y = 0f
    override var alpha = 0f
    private var baseAlpha = 0f
    private var baseRadius = 0f
    private var radius = 0f
    private var riseSpeed = 0f
    private var wobblePhase = 0f
    private var wobbleSpeed = 0f
    private var breathPhase = 0f
    private var breathSpeed = 0f

    override fun reset(width: Float, height: Float) {
        x = Random.nextFloat() * width
        y = height + Random.nextFloat() * 100f
        baseRadius = 4f + Random.nextFloat() * 8f
        radius = baseRadius
        baseAlpha = 0.10f + Random.nextFloat() * 0.12f
        alpha = baseAlpha
        riseSpeed = 0.15f + Random.nextFloat() * 0.3f
        wobblePhase = Random.nextFloat() * 2f * PI.toFloat()
        wobbleSpeed = 0.001f + Random.nextFloat() * 0.002f
        breathPhase = Random.nextFloat() * 6.28f
        breathSpeed = 0.0008f + Random.nextFloat() * 0.001f
    }

    override fun update(deltaMs: Long, width: Float, height: Float) {
        y -= riseSpeed * deltaMs
        wobblePhase += wobbleSpeed * deltaMs
        x += sin(wobblePhase) * 0.3f
        breathPhase += breathSpeed * deltaMs
        alpha = baseAlpha + sin(breathPhase) * baseAlpha * 0.5f
        radius = baseRadius + sin(breathPhase) * baseRadius * 0.15f
        if (y < -radius * 2) reset(width, height)
    }

    override fun DrawScope.draw() {
        val skyBlue = Color(0xFF87CEEB)
        drawCircle(
            Brush.radialGradient(
                listOf(
                    skyBlue.copy(alpha = alpha),
                    skyBlue.copy(alpha = alpha * 0.3f),
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
