package com.lolita.app.ui.component.chart

import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lolita.app.ui.theme.Gray800
import kotlin.math.atan2
import kotlin.math.sqrt

data class PieChartData(
    val label: String,
    val value: Double,
    val color: Color
)

@Composable
fun DonutChart(
    data: List<PieChartData>,
    modifier: Modifier = Modifier,
    centerText: String = "",
    selectedIndex: Int = -1,
    onSliceClick: (Int) -> Unit = {}
) {
    if (data.isEmpty()) return

    val total = data.sumOf { it.value }
    if (total == 0.0) return

    var animationPlayed by remember { mutableStateOf(false) }
    val animationProgress by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "donutAnimation"
    )
    LaunchedEffect(Unit) {
        animationPlayed = true
    }

    val density = LocalDensity.current
    val normalStrokeWidth = with(density) { 50.dp.toPx() }
    val selectedStrokeWidth = with(density) { 60.dp.toPx() }
    val centerTextSize = with(density) { 14.sp.toPx() }

    val sweepAngles = data.map { (it.value / total * 360.0).toFloat() }

    val centerPaint = remember(centerText) {
        Paint().apply {
            textAlign = Paint.Align.CENTER
            textSize = centerTextSize
            color = Gray800.toArgb()
            isAntiAlias = true
        }
    }

    Canvas(
        modifier = modifier
            .size(200.dp)
            .pointerInput(data, total) {
                detectTapGestures { offset ->
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val dx = offset.x - centerX
                    val dy = offset.y - centerY
                    val distance = sqrt(dx * dx + dy * dy)
                    val outerRadius = size.width.coerceAtMost(size.height) / 2f
                    val innerRadius = outerRadius - normalStrokeWidth
                    if (distance < innerRadius || distance > outerRadius) return@detectTapGestures

                    var angle = Math.toDegrees(
                        atan2(dy.toDouble(), dx.toDouble())
                    ).toFloat()
                    angle = (angle + 360f + 90f) % 360f

                    var cumulative = 0f
                    for (i in sweepAngles.indices) {
                        cumulative += sweepAngles[i]
                        if (angle <= cumulative) {
                            onSliceClick(i)
                            break
                        }
                    }
                }
            }
    ) {
        val maxStroke = selectedStrokeWidth
        val diameter = size.minDimension - maxStroke
        val topLeft = Offset(
            (size.width - diameter) / 2f,
            (size.height - diameter) / 2f
        )
        val arcSize = Size(diameter, diameter)

        var startAngle = -90f
        for (i in data.indices) {
            val sweep = sweepAngles[i] * animationProgress
            val isSelected = i == selectedIndex
            val strokeWidth = if (isSelected) selectedStrokeWidth else normalStrokeWidth
            drawArc(
                color = data[i].color,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth)
            )
            startAngle += sweep
        }

        if (centerText.isNotEmpty()) {
            drawContext.canvas.nativeCanvas.drawText(
                centerText,
                size.width / 2f,
                size.height / 2f + centerTextSize / 3f,
                centerPaint
            )
        }
    }
}
