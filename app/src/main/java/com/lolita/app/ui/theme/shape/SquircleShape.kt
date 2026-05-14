package com.lolita.app.ui.theme.shape

import android.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * Continuous-curvature rounded rectangle (squircle).
 *
 * Uses cubic Bezier curves with control-point coefficient 0.63
 * (vs 0.552 for standard circular arcs). The higher coefficient
 * causes the curve to begin earlier from the straight edge,
 * producing a fan-like flare at each corner — the signature
 * iOS 26 Liquid Glass corner treatment.
 */
class SquircleShape(private val radius: Dp) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val r = with(density) { radius.toPx() }.coerceAtMost(size.minDimension() / 2f)
        if (r <= 0f) return Outline.Rectangle(size.toRect())

        val c = r * 0.63f // control point distance — 0.63 = squircle, 0.552 = standard

        val path = Path().apply {
            // Top-left corner
            moveTo(0f, r)
            cubicTo(0f, r - c, r - c, 0f, r, 0f)

            // Top edge → top-right corner
            lineTo(size.width - r, 0f)
            cubicTo(
                size.width - r + c, 0f,
                size.width, r - c,
                size.width, r
            )

            // Right edge → bottom-right corner
            lineTo(size.width, size.height - r)
            cubicTo(
                size.width, size.height - r + c,
                size.width - r + c, size.height,
                size.width - r, size.height
            )

            // Bottom edge → bottom-left corner
            lineTo(r, size.height)
            cubicTo(
                r - c, size.height,
                0f, size.height - r + c,
                0f, size.height - r
            )

            close()
        }
        return Outline.Generic(path)
    }
}
