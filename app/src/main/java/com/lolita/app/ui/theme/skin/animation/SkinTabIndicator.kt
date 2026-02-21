package com.lolita.app.ui.theme.skin.animation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.TabPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.Pink400
import com.lolita.app.ui.theme.SkinType
import kotlin.math.sin

@Composable
fun SkinTabIndicator(
    tabPositions: List<TabPosition>,
    selectedTabIndex: Int,
    modifier: Modifier = Modifier
) {
    if (tabPositions.isEmpty()) return
    val skin = LolitaSkin.current
    val spec = skin.animations.tabSwitchAnimation

    val indicatorOffset by animateDpAsState(
        targetValue = tabPositions[selectedTabIndex].left,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "tabIndicatorOffset"
    )
    val indicatorWidth = tabPositions[selectedTabIndex].width

    Canvas(
        modifier
            .fillMaxWidth()
            .height(3.dp)
    ) {
        val left = indicatorOffset.toPx()
        val w = indicatorWidth.toPx()
        val h = size.height

        when (skin.skinType) {
            SkinType.DEFAULT -> {
                drawRoundRect(
                    Pink400,
                    topLeft = Offset(left + w * 0.1f, 0f),
                    size = Size(w * 0.8f, h),
                    cornerRadius = CornerRadius(h / 2f)
                )
            }
            SkinType.GOTHIC -> {
                drawRect(
                    Color(0xFF4A0E4E),
                    topLeft = Offset(left, 0f),
                    size = Size(w, h)
                )
                // Shadow trail
                drawRect(
                    Color(0xFF4A0E4E).copy(alpha = 0.3f),
                    topLeft = Offset(left - w * 0.1f, 0f),
                    size = Size(w * 0.1f, h)
                )
            }
            SkinType.CHINESE -> {
                val path = Path().apply {
                    moveTo(left, h)
                    val steps = 20
                    for (i in 0..steps) {
                        val px = left + w * i / steps
                        val py = h * 0.5f + sin(i.toFloat() * 0.8f) * h * 0.3f
                        lineTo(px, py)
                    }
                    lineTo(left + w, h)
                    close()
                }
                drawPath(path, Color(0xFF2C2C2C).copy(alpha = 0.8f))
            }
            SkinType.CLASSIC -> {
                val gold = Color(0xFFD4AF37)
                // Outer glow
                drawRoundRect(
                    gold.copy(alpha = 0.2f),
                    topLeft = Offset(left + w * 0.05f, -h * 0.3f),
                    size = Size(w * 0.9f, h * 1.6f),
                    cornerRadius = CornerRadius(h)
                )
                // Inner line
                drawRoundRect(
                    gold,
                    topLeft = Offset(left + w * 0.15f, h * 0.2f),
                    size = Size(w * 0.7f, h * 0.6f),
                    cornerRadius = CornerRadius(h / 2f)
                )
            }
        }
    }
}
