package com.lolita.app.ui.component.chart

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lolita.app.ui.theme.Gray200
import com.lolita.app.ui.theme.Gray600
import com.lolita.app.ui.theme.Gray800
import com.lolita.app.ui.theme.Pink400

@Composable
fun StatsProgressBar(
    current: Double,
    total: Double,
    label: String,
    modifier: Modifier = Modifier,
    barColor: Color = Pink400,
    backgroundColor: Color = Gray200
) {
    val fraction = if (total > 0) (current / total).coerceIn(0.0, 1.0) else 0.0
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.toFloat(),
        animationSpec = tween(1000),
        label = "progressAnimation"
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = Gray600
            )
            Text(
                text = "${(fraction * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = Gray800,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
        ) {
            val cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            // Background
            drawRoundRect(
                color = backgroundColor,
                size = size,
                cornerRadius = cornerRadius
            )
            // Fill
            drawRoundRect(
                color = barColor,
                size = Size(
                    width = size.width * animatedFraction,
                    height = size.height
                ),
                cornerRadius = cornerRadius
            )
        }
    }
}
