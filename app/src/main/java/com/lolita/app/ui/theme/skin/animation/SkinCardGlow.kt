package com.lolita.app.ui.theme.skin.animation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.Pink400
import com.lolita.app.ui.theme.SkinType
import kotlin.math.sin

@Composable
fun Modifier.skinCardGlow(): Modifier {
    val skin = LolitaSkin.current
    if (!skin.animations.ambientAnimation.cardGlowEffect) return this

    val infiniteTransition = rememberInfiniteTransition(label = "cardGlow")
    val glowProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cardGlowProgress"
    )

    return this.then(SkinCardGlowModifier(skin.skinType, glowProgress))
}

private data class SkinCardGlowModifier(
    val skinType: SkinType,
    val progress: Float
) : androidx.compose.ui.draw.DrawModifier {
    override fun ContentDrawScope.draw() {
        drawContent()
        when (skinType) {
            SkinType.DEFAULT -> {
                val alpha = (sin(progress * 2f * Math.PI.toFloat()) * 0.5f + 0.5f) * 0.15f
                drawRoundRect(
                    Pink400.copy(alpha = alpha),
                    style = Stroke(width = 1.5f)
                )
            }
            SkinType.GOTHIC -> {
                val alpha = (sin(progress * 2f * Math.PI.toFloat()) * 0.5f + 0.5f) * 0.12f
                drawRoundRect(
                    Color.Black.copy(alpha = alpha),
                    style = Stroke(width = 2f)
                )
            }
            SkinType.CHINESE -> {
                val alpha = (sin(progress * 2f * Math.PI.toFloat()) * 0.5f + 0.5f) * 0.1f
                drawRoundRect(
                    Color(0xFF2C2C2C).copy(alpha = alpha),
                    style = Stroke(width = 1f)
                )
            }
            SkinType.CLASSIC -> {
                val sweepX = size.width * progress
                drawLine(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Color(0xFFD4AF37).copy(alpha = 0.3f),
                            Color.Transparent
                        ),
                        startX = sweepX - 40f,
                        endX = sweepX + 40f
                    ),
                    start = Offset(sweepX, 0f),
                    end = Offset(sweepX, size.height),
                    strokeWidth = 2f
                )
            }
        }
    }
}
