package com.lolita.app.ui.component.chart

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lolita.app.ui.theme.Gray200
import com.lolita.app.ui.theme.Gray800
import kotlin.math.abs
import kotlin.math.ceil

data class LineChartData(
    val label: String,
    val value: Double
)

@Composable
fun LineChart(
    data: List<LineChartData>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFFFF69B4),
    selectedIndex: Int = -1,
    onPointClick: (Int) -> Unit = {}
) {
    if (data.isEmpty()) return
    val maxValue = data.maxOf { it.value }
    if (maxValue == 0.0) return

    val density = LocalDensity.current
    val leftPadding = with(density) { 40.dp.toPx() }
    val bottomPadding = with(density) { 24.dp.toPx() }
    val pointRadius = with(density) { 4.dp.toPx() }
    val selectedPointRadius = with(density) { 6.dp.toPx() }
    val lineStrokeWidth = with(density) { 2.dp.toPx() }
    val axisLabelSize = with(density) { 10.sp.toPx() }
    val tooltipTextSize = with(density) { 11.sp.toPx() }
    val gridLineColor = Gray200

    // Calculate Y-axis ticks: 4-5 nice tick marks
    val tickCount = 5
    val rawStep = maxValue / (tickCount - 1)
    val magnitude = Math.pow(10.0, Math.floor(Math.log10(rawStep)))
    val niceStep = when {
        rawStep / magnitude <= 1.5 -> magnitude
        rawStep / magnitude <= 3.0 -> 2.0 * magnitude
        rawStep / magnitude <= 7.0 -> 5.0 * magnitude
        else -> 10.0 * magnitude
    }
    val niceMax = ceil(maxValue / niceStep) * niceStep
    val actualTickCount = (niceMax / niceStep).toInt() + 1
    val ticks = (0 until actualTickCount).map { it * niceStep }

    val axisLabelPaint = remember {
        Paint().apply {
            textSize = axisLabelSize
            color = Gray800.toArgb()
            isAntiAlias = true
        }
    }
    val tooltipPaint = remember {
        Paint().apply {
            textSize = tooltipTextSize
            color = android.graphics.Color.WHITE
            isAntiAlias = true
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(data) {
                detectTapGestures { tapOffset ->
                    val chartWidth = size.width - leftPadding
                    val chartHeight = size.height - bottomPadding
                    if (data.size < 2) {
                        if (data.size == 1) onPointClick(0)
                        return@detectTapGestures
                    }
                    val spacing = chartWidth / (data.size - 1)
                    var nearestIdx = -1
                    var nearestDist = Float.MAX_VALUE
                    for (i in data.indices) {
                        val px = leftPadding + i * spacing
                        val py = chartHeight - (data[i].value / niceMax * chartHeight).toFloat()
                        val dist = abs(tapOffset.x - px) + abs(tapOffset.y - py)
                        if (dist < nearestDist) {
                            nearestDist = dist
                            nearestIdx = i
                        }
                    }
                    if (nearestIdx >= 0 && nearestDist < 100f) {
                        onPointClick(nearestIdx)
                    }
                }
            }
    ) {
        val chartWidth = size.width - leftPadding
        val chartHeight = size.height - bottomPadding

        // Draw Y-axis grid lines and labels
        for (tick in ticks) {
            val y = chartHeight - (tick / niceMax * chartHeight).toFloat()
            drawLine(
                color = gridLineColor,
                start = Offset(leftPadding, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
            val label = if (tick == tick.toLong().toDouble()) {
                tick.toLong().toString()
            } else {
                String.format("%.1f", tick)
            }
            axisLabelPaint.textAlign = Paint.Align.RIGHT
            drawContext.canvas.nativeCanvas.drawText(
                label,
                leftPadding - 8f,
                y + axisLabelSize / 3f,
                axisLabelPaint
            )
        }

        // Calculate point positions
        val points = if (data.size == 1) {
            listOf(Offset(leftPadding + chartWidth / 2f,
                chartHeight - (data[0].value / niceMax * chartHeight).toFloat()))
        } else {
            val spacing = chartWidth / (data.size - 1)
            data.mapIndexed { i, d ->
                Offset(
                    leftPadding + i * spacing,
                    chartHeight - (d.value / niceMax * chartHeight).toFloat()
                )
            }
        }

        // Draw X-axis labels
        axisLabelPaint.textAlign = Paint.Align.CENTER
        for (i in data.indices) {
            drawContext.canvas.nativeCanvas.drawText(
                data[i].label,
                points[i].x,
                size.height - 4f,
                axisLabelPaint
            )
        }

        // Gradient fill below the line
        if (points.size >= 2) {
            val gradientPath = Path().apply {
                moveTo(points.first().x, chartHeight)
                for (pt in points) {
                    lineTo(pt.x, pt.y)
                }
                lineTo(points.last().x, chartHeight)
                close()
            }
            drawPath(
                path = gradientPath,
                brush = Brush.verticalGradient(
                    listOf(lineColor.copy(alpha = 0.3f), Color.Transparent),
                    startY = 0f,
                    endY = chartHeight
                )
            )
        }

        // Draw lines between points
        for (i in 0 until points.size - 1) {
            drawLine(
                color = lineColor,
                start = points[i],
                end = points[i + 1],
                strokeWidth = lineStrokeWidth
            )
        }

        // Draw data points
        for (i in points.indices) {
            val radius = if (i == selectedIndex) selectedPointRadius else pointRadius
            drawCircle(
                color = lineColor,
                radius = radius,
                center = points[i]
            )
        }

        // Draw tooltip for selected point
        if (selectedIndex in data.indices) {
            val pt = points[selectedIndex]
            val valueText = if (data[selectedIndex].value == data[selectedIndex].value.toLong().toDouble()) {
                data[selectedIndex].value.toLong().toString()
            } else {
                String.format("%.1f", data[selectedIndex].value)
            }
            val textWidth = tooltipPaint.measureText(valueText)
            val tooltipW = textWidth + 16f
            val tooltipH = tooltipTextSize + 12f
            val tooltipX = (pt.x - tooltipW / 2f)
                .coerceIn(0f, size.width - tooltipW)
            val tooltipY = pt.y - selectedPointRadius - tooltipH - 4f

            drawRoundRect(
                color = Gray800,
                topLeft = Offset(tooltipX, tooltipY),
                size = Size(tooltipW, tooltipH),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
            drawContext.canvas.nativeCanvas.drawText(
                valueText,
                tooltipX + tooltipW / 2f,
                tooltipY + tooltipH - 6f,
                tooltipPaint.apply { textAlign = Paint.Align.CENTER }
            )
        }
    }
}
