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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.Pink400
import com.lolita.app.ui.theme.SkinType
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@Composable
fun Modifier.skinCardGlow(isScrolling: Boolean = false): Modifier {
    val skin = LolitaSkin.current
    if (!skin.animations.ambientAnimation.cardGlowEffect) return this
    if (isScrolling) return this

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
                // Subtle base border
                drawRoundRect(
                    Pink400.copy(alpha = 0.06f),
                    style = Stroke(width = 1f)
                )
                // Calculate point along card perimeter (clockwise: top→right→bottom→left)
                val w = size.width
                val h = size.height
                val perimeter = 2f * (w + h)
                val dist = progress * perimeter
                val (px, py) = when {
                    dist < w -> Offset(dist, 0f)
                    dist < w + h -> Offset(w, dist - w)
                    dist < 2f * w + h -> Offset(w - (dist - w - h), h)
                    else -> Offset(0f, h - (dist - 2f * w - h))
                }
                // Radial glow at the traveling point
                drawCircle(
                    Brush.radialGradient(
                        listOf(Pink400.copy(alpha = 0.3f), Color.Transparent),
                        center = Offset(px, py),
                        radius = 25f
                    ),
                    radius = 25f,
                    center = Offset(px, py)
                )
                // Small heart shape at the point
                val hs = 8f
                val heartPath = Path().apply {
                    moveTo(px, py - hs * 0.3f)
                    cubicTo(px - hs, py - hs, px - hs, py - hs * 0.1f, px, py + hs * 0.5f)
                    moveTo(px, py - hs * 0.3f)
                    cubicTo(px + hs, py - hs, px + hs, py - hs * 0.1f, px, py + hs * 0.5f)
                    close()
                }
                drawPath(heartPath, Pink400.copy(alpha = 0.45f), style = Fill)
            }
            SkinType.GOTHIC -> {
                val w = size.width
                val h = size.height
                // Corner shadow pulse
                val pulseAlpha = 0.12f + (sin(progress * 2f * Math.PI.toFloat()) * 0.5f + 0.5f) * 0.16f
                val cornerRadius = min(w, h) * 0.35f
                val corners = listOf(
                    Offset(0f, 0f),
                    Offset(w, 0f),
                    Offset(w, h),
                    Offset(0f, h)
                )
                val gothicPurple = Color(0xFF4A0E4E)
                for (corner in corners) {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(gothicPurple.copy(alpha = pulseAlpha), Color.Transparent),
                            center = corner,
                            radius = cornerRadius
                        ),
                        radius = cornerRadius,
                        center = corner
                    )
                }
                // Blood red edge flicker (visible only half the cycle)
                val flickerAlpha = max(0f, sin(progress * 4f * Math.PI.toFloat())) * 0.2f
                if (flickerAlpha > 0f) {
                    drawRoundRect(
                        Color(0xFF8B0000).copy(alpha = flickerAlpha),
                        style = Stroke(width = 1.5f)
                    )
                }
            }
            SkinType.CHINESE -> {
                val w = size.width
                val h = size.height
                val inkColor = Color(0xFF2C2C2C)
                // Subtle base border
                drawRoundRect(
                    inkColor.copy(alpha = 0.04f),
                    style = Stroke(width = 0.8f)
                )
                // Ink wash spread from bottom-right
                val cx = w * 0.85f
                val cy = h * 0.85f
                val maxRadius = hypot(w.toDouble(), h.toDouble()).toFloat() * 0.45f
                val currentRadius = maxRadius * (0.3f + 0.15f * sin(progress * 2f * Math.PI.toFloat()))
                drawCircle(
                    Brush.radialGradient(
                        listOf(inkColor.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = currentRadius
                    ),
                    radius = currentRadius,
                    center = Offset(cx, cy)
                )
                // Small ink dot at center of spread
                drawCircle(
                    inkColor.copy(alpha = 0.35f),
                    radius = 3f,
                    center = Offset(cx, cy)
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
