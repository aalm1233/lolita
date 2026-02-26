package com.lolita.app.ui.theme.skin.icon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

// ── Shared helpers ──────────────────────────────────────────────

private fun navyStroke(s: Float) = Stroke(
    width = s * 0.07f, cap = StrokeCap.Round, join = StrokeJoin.Round
)

private fun thinNavy(s: Float) = Stroke(
    width = s * 0.035f, cap = StrokeCap.Round, join = StrokeJoin.Round
)

private fun DrawScope.drawAnchor(c: Offset, r: Float, color: Color) {
    val st = Stroke(width = r * 0.22f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawCircle(color, r * 0.25f, Offset(c.x, c.y - r * 0.65f), style = st)
    drawLine(color, Offset(c.x, c.y - r * 0.4f), Offset(c.x, c.y + r * 0.7f), st.width)
    drawLine(color, Offset(c.x - r * 0.4f, c.y - r * 0.1f), Offset(c.x + r * 0.4f, c.y - r * 0.1f), st.width)
    val fluke = Path().apply {
        moveTo(c.x - r * 0.55f, c.y + r * 0.3f)
        quadraticBezierTo(c.x - r * 0.5f, c.y + r * 0.8f, c.x, c.y + r * 0.7f)
        quadraticBezierTo(c.x + r * 0.5f, c.y + r * 0.8f, c.x + r * 0.55f, c.y + r * 0.3f)
    }
    drawPath(fluke, color, style = st)
}

private fun DrawScope.drawWave(startX: Float, y: Float, width: Float, color: Color) {
    val st = Stroke(width = width * 0.06f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val seg = width / 4f
    val amp = width * 0.06f
    val p = Path().apply {
        moveTo(startX, y)
        cubicTo(startX + seg * 0.5f, y - amp, startX + seg * 0.5f, y - amp, startX + seg, y)
        cubicTo(startX + seg * 1.5f, y + amp, startX + seg * 1.5f, y + amp, startX + seg * 2f, y)
        cubicTo(startX + seg * 2.5f, y - amp, startX + seg * 2.5f, y - amp, startX + seg * 3f, y)
        cubicTo(startX + seg * 3.5f, y + amp, startX + seg * 3.5f, y + amp, startX + seg * 4f, y)
    }
    drawPath(p, color, style = st)
}

private fun DrawScope.drawRope(start: Offset, end: Offset, color: Color) {
    val st = Stroke(width = (end - start).getDistance() * 0.06f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val mx = (start.x + end.x) / 2f
    val my = (start.y + end.y) / 2f
    val dx = end.x - start.x; val dy = end.y - start.y
    val cx = mx - dy * 0.15f; val cy = my + dx * 0.15f
    val p = Path().apply {
        moveTo(start.x, start.y)
        quadraticBezierTo(cx, cy, end.x, end.y)
    }
    drawPath(p, color, style = st)
}

// ── Navigation Icons ────────────────────────────────────────────

private class NavyNavigationIcons : BaseNavigationIcons() {
    @Composable override fun Home(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Lighthouse tower
            val tower = Path().apply {
                moveTo(s * 0.38f, s * 0.88f); lineTo(s * 0.42f, s * 0.3f)
                lineTo(s * 0.58f, s * 0.3f); lineTo(s * 0.62f, s * 0.88f); close()
            }
            drawPath(tower, tint, style = st)
            // Lamp housing
            drawRoundRect(tint, Offset(s * 0.36f, s * 0.2f), Size(s * 0.28f, s * 0.12f),
                cornerRadius = CornerRadius(s * 0.02f), style = st)
            // Roof cap
            val roof = Path().apply {
                moveTo(s * 0.5f, s * 0.1f); lineTo(s * 0.34f, s * 0.2f); lineTo(s * 0.66f, s * 0.2f); close()
            }
            drawPath(roof, tint, style = st)
            // Light beams
            drawLine(tint.copy(alpha = 0.5f), Offset(s * 0.36f, s * 0.24f), Offset(s * 0.15f, s * 0.18f),
                strokeWidth = s * 0.03f, cap = StrokeCap.Round)
            drawLine(tint.copy(alpha = 0.5f), Offset(s * 0.64f, s * 0.24f), Offset(s * 0.85f, s * 0.18f),
                strokeWidth = s * 0.03f, cap = StrokeCap.Round)
            // Horizontal stripes on tower
            drawLine(tint, Offset(s * 0.40f, s * 0.5f), Offset(s * 0.60f, s * 0.5f), strokeWidth = s * 0.03f)
            drawLine(tint, Offset(s * 0.39f, s * 0.65f), Offset(s * 0.61f, s * 0.65f), strokeWidth = s * 0.03f)
        }
    }
    @Composable override fun Wishlist(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Heart-shaped ring at top of anchor
            val heart = Path().apply {
                moveTo(s * 0.5f, s * 0.28f)
                cubicTo(s * 0.35f, s * 0.18f, s * 0.3f, s * 0.05f, s * 0.5f, s * 0.14f)
                cubicTo(s * 0.7f, s * 0.05f, s * 0.65f, s * 0.18f, s * 0.5f, s * 0.28f)
            }
            drawPath(heart, tint, style = st)
            // Anchor shaft
            drawLine(tint, Offset(s * 0.5f, s * 0.28f), Offset(s * 0.5f, s * 0.82f),
                strokeWidth = s * 0.07f, cap = StrokeCap.Round)
            // Cross bar
            drawLine(tint, Offset(s * 0.3f, s * 0.48f), Offset(s * 0.7f, s * 0.48f),
                strokeWidth = s * 0.06f, cap = StrokeCap.Round)
            // Flukes
            val fluke = Path().apply {
                moveTo(s * 0.22f, s * 0.62f)
                quadraticBezierTo(s * 0.25f, s * 0.88f, s * 0.5f, s * 0.82f)
                quadraticBezierTo(s * 0.75f, s * 0.88f, s * 0.78f, s * 0.62f)
            }
            drawPath(fluke, tint, style = st)
        }
    }
    @Composable override fun Outfit(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Sailor collar — V-shaped with ribbon
            val collar = Path().apply {
                moveTo(s * 0.2f, s * 0.12f); lineTo(s * 0.5f, s * 0.62f); lineTo(s * 0.8f, s * 0.12f)
            }
            drawPath(collar, tint, style = st)
            // Collar outline top
            drawLine(tint, Offset(s * 0.2f, s * 0.12f), Offset(s * 0.8f, s * 0.12f),
                strokeWidth = s * 0.05f, cap = StrokeCap.Round)
            // Stripes on collar
            drawLine(tint.copy(alpha = 0.4f), Offset(s * 0.28f, s * 0.2f), Offset(s * 0.72f, s * 0.2f),
                strokeWidth = s * 0.025f, cap = StrokeCap.Round)
            drawLine(tint.copy(alpha = 0.4f), Offset(s * 0.33f, s * 0.28f), Offset(s * 0.67f, s * 0.28f),
                strokeWidth = s * 0.025f, cap = StrokeCap.Round)
            // Ribbon bow at bottom
            val bowL = Path().apply {
                moveTo(s * 0.5f, s * 0.62f)
                cubicTo(s * 0.35f, s * 0.65f, s * 0.3f, s * 0.78f, s * 0.38f, s * 0.82f)
                quadraticBezierTo(s * 0.45f, s * 0.84f, s * 0.5f, s * 0.72f)
            }
            drawPath(bowL, tint, style = thinNavy(s))
            val bowR = Path().apply {
                moveTo(s * 0.5f, s * 0.62f)
                cubicTo(s * 0.65f, s * 0.65f, s * 0.7f, s * 0.78f, s * 0.62f, s * 0.82f)
                quadraticBezierTo(s * 0.55f, s * 0.84f, s * 0.5f, s * 0.72f)
            }
            drawPath(bowR, tint, style = thinNavy(s))
            // Ribbon tails
            drawLine(tint, Offset(s * 0.38f, s * 0.82f), Offset(s * 0.35f, s * 0.92f),
                strokeWidth = s * 0.03f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.62f, s * 0.82f), Offset(s * 0.65f, s * 0.92f),
                strokeWidth = s * 0.03f, cap = StrokeCap.Round)
        }
    }
    @Composable override fun Stats(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            val cx = s * 0.5f; val cy = s * 0.5f; val r = s * 0.36f
            // Helm outer ring
            drawCircle(tint, r, Offset(cx, cy), style = st)
            drawCircle(tint, r * 0.35f, Offset(cx, cy), style = st)
            // 8 spokes
            for (i in 0 until 8) {
                val angle = Math.toRadians((i * 45.0))
                val ix = cx + r * 0.35f * cos(angle).toFloat()
                val iy = cy + r * 0.35f * sin(angle).toFloat()
                val ox = cx + r * cos(angle).toFloat()
                val oy = cy + r * sin(angle).toFloat()
                drawLine(tint, Offset(ix, iy), Offset(ox, oy),
                    strokeWidth = s * 0.04f, cap = StrokeCap.Round)
            }
            // Handle pegs on outer ring
            for (i in 0 until 8) {
                val angle = Math.toRadians((i * 45.0))
                val px = cx + (r + s * 0.06f) * cos(angle).toFloat()
                val py = cy + (r + s * 0.06f) * sin(angle).toFloat()
                drawCircle(tint, s * 0.03f, Offset(px, py))
            }
        }
    }
    @Composable override fun Settings(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            val cx = s * 0.5f; val cy = s * 0.5f; val r = s * 0.36f
            // Compass circle
            drawCircle(tint, r, Offset(cx, cy), style = st)
            drawCircle(tint, r * 0.15f, Offset(cx, cy), style = thinNavy(s))
            // N/S/E/W diamond points
            val pointLen = r * 0.85f; val pointW = s * 0.08f
            // North
            val north = Path().apply {
                moveTo(cx, cy - pointLen); lineTo(cx - pointW, cy); lineTo(cx, cy - pointLen * 0.3f)
                lineTo(cx + pointW, cy); close()
            }
            drawPath(north, tint, style = Fill)
            // South
            val south = Path().apply {
                moveTo(cx, cy + pointLen); lineTo(cx - pointW, cy); lineTo(cx, cy + pointLen * 0.3f)
                lineTo(cx + pointW, cy); close()
            }
            drawPath(south, tint.copy(alpha = 0.5f), style = Fill)
            // East
            val east = Path().apply {
                moveTo(cx + pointLen, cy); lineTo(cx, cy - pointW); lineTo(cx + pointLen * 0.3f, cy)
                lineTo(cx, cy + pointW); close()
            }
            drawPath(east, tint.copy(alpha = 0.5f), style = Fill)
            // West
            val west = Path().apply {
                moveTo(cx - pointLen, cy); lineTo(cx, cy - pointW); lineTo(cx - pointLen * 0.3f, cy)
                lineTo(cx, cy + pointW); close()
            }
            drawPath(west, tint.copy(alpha = 0.5f), style = Fill)
        }
    }
}

// ── Action Icons ────────────────────────────────────────────────

private class NavyActionIcons : BaseActionIcons() {
    @Composable override fun Add(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            val cx = s * 0.5f; val cy = s * 0.5f; val r = s * 0.38f
            // Life preserver ring
            drawCircle(tint, r, Offset(cx, cy), style = st)
            drawCircle(tint, r * 0.6f, Offset(cx, cy), style = st)
            // Plus in center
            drawLine(tint, Offset(cx, cy - r * 0.35f), Offset(cx, cy + r * 0.35f),
                strokeWidth = s * 0.07f, cap = StrokeCap.Round)
            drawLine(tint, Offset(cx - r * 0.35f, cy), Offset(cx + r * 0.35f, cy),
                strokeWidth = s * 0.07f, cap = StrokeCap.Round)
        }
    }
    @Composable override fun Delete(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Anchor
            drawCircle(tint, s * 0.06f, Offset(s * 0.5f, s * 0.14f), style = st)
            drawLine(tint, Offset(s * 0.5f, s * 0.2f), Offset(s * 0.5f, s * 0.78f),
                strokeWidth = s * 0.06f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.32f, s * 0.42f), Offset(s * 0.68f, s * 0.42f),
                strokeWidth = s * 0.05f, cap = StrokeCap.Round)
            val fluke = Path().apply {
                moveTo(s * 0.25f, s * 0.58f)
                quadraticBezierTo(s * 0.28f, s * 0.82f, s * 0.5f, s * 0.78f)
                quadraticBezierTo(s * 0.72f, s * 0.82f, s * 0.75f, s * 0.58f)
            }
            drawPath(fluke, tint, style = st)
            // X through it
            drawLine(tint, Offset(s * 0.2f, s * 0.2f), Offset(s * 0.8f, s * 0.85f),
                strokeWidth = s * 0.06f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.8f, s * 0.2f), Offset(s * 0.2f, s * 0.85f),
                strokeWidth = s * 0.06f, cap = StrokeCap.Round)
        }
    }
    @Composable override fun Edit(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Quill feather pen
            val quill = Path().apply {
                moveTo(s * 0.18f, s * 0.88f)
                lineTo(s * 0.25f, s * 0.72f)
                cubicTo(s * 0.35f, s * 0.55f, s * 0.55f, s * 0.3f, s * 0.82f, s * 0.1f)
                cubicTo(s * 0.75f, s * 0.2f, s * 0.6f, s * 0.35f, s * 0.45f, s * 0.55f)
                cubicTo(s * 0.35f, s * 0.65f, s * 0.28f, s * 0.75f, s * 0.22f, s * 0.82f)
                close()
            }
            drawPath(quill, tint, style = st)
            // Feather spine
            drawLine(tint, Offset(s * 0.22f, s * 0.82f), Offset(s * 0.78f, s * 0.14f),
                strokeWidth = s * 0.03f, cap = StrokeCap.Round)
            // Feather barbs
            for (i in 1..4) {
                val t = i * 0.2f
                val bx = s * (0.22f + t * 0.56f); val by = s * (0.82f - t * 0.68f)
                drawLine(tint.copy(alpha = 0.4f), Offset(bx, by),
                    Offset(bx + s * 0.08f, by - s * 0.06f),
                    strokeWidth = s * 0.02f, cap = StrokeCap.Round)
            }
        }
    }
    @Composable override fun Search(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Telescope / spyglass
            // Main barrel
            drawRoundRect(tint, Offset(s * 0.15f, s * 0.4f), Size(s * 0.5f, s * 0.2f),
                cornerRadius = CornerRadius(s * 0.04f), style = st)
            // Eyepiece (smaller)
            drawRoundRect(tint, Offset(s * 0.08f, s * 0.43f), Size(s * 0.12f, s * 0.14f),
                cornerRadius = CornerRadius(s * 0.03f), style = st)
            // Front lens (wider)
            drawRoundRect(tint, Offset(s * 0.6f, s * 0.36f), Size(s * 0.15f, s * 0.28f),
                cornerRadius = CornerRadius(s * 0.04f), style = st)
            // Lens cap ring
            drawLine(tint, Offset(s * 0.75f, s * 0.38f), Offset(s * 0.75f, s * 0.62f),
                strokeWidth = s * 0.04f, cap = StrokeCap.Round)
            // Light rays from lens
            drawLine(tint.copy(alpha = 0.4f), Offset(s * 0.78f, s * 0.42f), Offset(s * 0.9f, s * 0.35f),
                strokeWidth = s * 0.025f, cap = StrokeCap.Round)
            drawLine(tint.copy(alpha = 0.4f), Offset(s * 0.78f, s * 0.5f), Offset(s * 0.92f, s * 0.5f),
                strokeWidth = s * 0.025f, cap = StrokeCap.Round)
            drawLine(tint.copy(alpha = 0.4f), Offset(s * 0.78f, s * 0.58f), Offset(s * 0.9f, s * 0.65f),
                strokeWidth = s * 0.025f, cap = StrokeCap.Round)
        }
    }
    @Composable override fun Sort(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Three wave lines of different heights
            val ys = listOf(s * 0.25f, s * 0.5f, s * 0.75f)
            val widths = listOf(s * 0.7f, s * 0.5f, s * 0.3f)
            val amps = listOf(s * 0.06f, s * 0.05f, s * 0.04f)
            for (i in 0..2) {
                val startX = s * 0.15f; val y = ys[i]; val w = widths[i]; val amp = amps[i]
                val seg = w / 2f
                val p = Path().apply {
                    moveTo(startX, y)
                    cubicTo(startX + seg * 0.5f, y - amp, startX + seg * 0.5f, y - amp, startX + seg, y)
                    cubicTo(startX + seg * 1.5f, y + amp, startX + seg * 1.5f, y + amp, startX + seg * 2f, y)
                }
                drawPath(p, tint, style = st)
            }
        }
    }
    @Composable override fun Save(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Anchor dropping downward
            drawCircle(tint, s * 0.06f, Offset(s * 0.5f, s * 0.1f), style = st)
            drawLine(tint, Offset(s * 0.5f, s * 0.16f), Offset(s * 0.5f, s * 0.68f),
                strokeWidth = s * 0.06f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.32f, s * 0.35f), Offset(s * 0.68f, s * 0.35f),
                strokeWidth = s * 0.05f, cap = StrokeCap.Round)
            // Down arrow at bottom
            val arrow = Path().apply {
                moveTo(s * 0.35f, s * 0.62f); lineTo(s * 0.5f, s * 0.82f); lineTo(s * 0.65f, s * 0.62f)
            }
            drawPath(arrow, tint, style = st)
            // Small flukes
            val fluke = Path().apply {
                moveTo(s * 0.3f, s * 0.52f)
                quadraticBezierTo(s * 0.32f, s * 0.65f, s * 0.5f, s * 0.62f)
                quadraticBezierTo(s * 0.68f, s * 0.65f, s * 0.7f, s * 0.52f)
            }
            drawPath(fluke, tint.copy(alpha = 0.4f), style = thinNavy(s))
        }
    }
    @Composable override fun Close(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Rope tied in X shape
            val p1 = Path().apply {
                moveTo(s * 0.2f, s * 0.2f)
                cubicTo(s * 0.35f, s * 0.35f, s * 0.45f, s * 0.45f, s * 0.5f, s * 0.5f)
                cubicTo(s * 0.55f, s * 0.55f, s * 0.65f, s * 0.65f, s * 0.8f, s * 0.8f)
            }
            drawPath(p1, tint, style = st)
            val p2 = Path().apply {
                moveTo(s * 0.8f, s * 0.2f)
                cubicTo(s * 0.65f, s * 0.35f, s * 0.55f, s * 0.45f, s * 0.5f, s * 0.5f)
                cubicTo(s * 0.45f, s * 0.55f, s * 0.35f, s * 0.65f, s * 0.2f, s * 0.8f)
            }
            drawPath(p2, tint, style = st)
            // Knot in center
            drawCircle(tint, s * 0.06f, Offset(s * 0.5f, s * 0.5f))
        }
    }
    @Composable override fun Share(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Signal flag (triangular pennant)
            val flag = Path().apply {
                moveTo(s * 0.25f, s * 0.12f); lineTo(s * 0.8f, s * 0.35f)
                lineTo(s * 0.25f, s * 0.58f); close()
            }
            drawPath(flag, tint, style = st)
            // Flagpole
            drawLine(tint, Offset(s * 0.25f, s * 0.08f), Offset(s * 0.25f, s * 0.92f),
                strokeWidth = s * 0.05f, cap = StrokeCap.Round)
            // Stripe on flag
            drawLine(tint.copy(alpha = 0.4f), Offset(s * 0.3f, s * 0.35f), Offset(s * 0.65f, s * 0.35f),
                strokeWidth = s * 0.025f, cap = StrokeCap.Round)
        }
    }
    @Composable override fun FilterList(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = thinNavy(s)
            // Fishing net pattern (crossed lines)
            val gap = s * 0.18f
            for (i in 0..4) {
                val x = s * 0.1f + i * gap
                drawLine(tint, Offset(x, s * 0.1f), Offset(x, s * 0.9f), strokeWidth = s * 0.03f)
            }
            for (i in 0..4) {
                val y = s * 0.1f + i * gap
                drawLine(tint, Offset(s * 0.1f, y), Offset(s * 0.9f, y), strokeWidth = s * 0.03f)
            }
            // Knots at intersections (corners only)
            drawCircle(tint, s * 0.025f, Offset(s * 0.1f, s * 0.1f))
            drawCircle(tint, s * 0.025f, Offset(s * 0.82f, s * 0.1f))
            drawCircle(tint, s * 0.025f, Offset(s * 0.1f, s * 0.82f))
            drawCircle(tint, s * 0.025f, Offset(s * 0.82f, s * 0.82f))
        }
    }
    @Composable override fun MoreVert(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Three rope knots vertically
            val ys = listOf(s * 0.2f, s * 0.5f, s * 0.8f)
            for (y in ys) {
                drawCircle(tint, s * 0.07f, Offset(s * 0.5f, y), style = st)
                drawCircle(tint, s * 0.03f, Offset(s * 0.5f, y))
            }
        }
    }
    @Composable override fun ContentCopy(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Two overlapping signal flags
            val flag1 = Path().apply {
                moveTo(s * 0.15f, s * 0.1f); lineTo(s * 0.6f, s * 0.28f)
                lineTo(s * 0.15f, s * 0.46f); close()
            }
            drawPath(flag1, tint, style = st)
            val flag2 = Path().apply {
                moveTo(s * 0.35f, s * 0.42f); lineTo(s * 0.8f, s * 0.6f)
                lineTo(s * 0.35f, s * 0.78f); close()
            }
            drawPath(flag2, tint, style = st)
            // Poles
            drawLine(tint, Offset(s * 0.15f, s * 0.06f), Offset(s * 0.15f, s * 0.52f),
                strokeWidth = s * 0.04f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.35f, s * 0.38f), Offset(s * 0.35f, s * 0.88f),
                strokeWidth = s * 0.04f, cap = StrokeCap.Round)
        }
    }
    @Composable override fun Refresh(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Compass needle with circular arrow
            drawArc(tint, 40f, 280f, false, Offset(s * 0.15f, s * 0.15f),
                Size(s * 0.7f, s * 0.7f), style = st)
            // Arrow head
            val arrow = Path().apply {
                moveTo(s * 0.62f, s * 0.12f); lineTo(s * 0.78f, s * 0.24f); lineTo(s * 0.56f, s * 0.28f)
            }
            drawPath(arrow, tint, style = Fill)
            // Compass needle in center
            val needleN = Path().apply {
                moveTo(s * 0.5f, s * 0.35f); lineTo(s * 0.46f, s * 0.5f); lineTo(s * 0.54f, s * 0.5f); close()
            }
            drawPath(needleN, tint, style = Fill)
            val needleS = Path().apply {
                moveTo(s * 0.5f, s * 0.65f); lineTo(s * 0.46f, s * 0.5f); lineTo(s * 0.54f, s * 0.5f); close()
            }
            drawPath(needleS, tint.copy(alpha = 0.4f), style = Fill)
        }
    }
    @Composable override fun ViewAgenda(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Two stacked porthole-style cards
            drawRoundRect(tint, Offset(s * 0.12f, s * 0.08f), Size(s * 0.76f, s * 0.35f),
                cornerRadius = CornerRadius(s * 0.04f), style = st)
            drawCircle(tint, s * 0.08f, Offset(s * 0.3f, s * 0.255f), style = thinNavy(s))
            drawLine(tint.copy(alpha = 0.4f), Offset(s * 0.45f, s * 0.2f), Offset(s * 0.78f, s * 0.2f),
                strokeWidth = s * 0.025f)
            drawLine(tint.copy(alpha = 0.4f), Offset(s * 0.45f, s * 0.31f), Offset(s * 0.72f, s * 0.31f),
                strokeWidth = s * 0.025f)
            drawRoundRect(tint, Offset(s * 0.12f, s * 0.57f), Size(s * 0.76f, s * 0.35f),
                cornerRadius = CornerRadius(s * 0.04f), style = st)
            drawCircle(tint, s * 0.08f, Offset(s * 0.3f, s * 0.745f), style = thinNavy(s))
            drawLine(tint.copy(alpha = 0.4f), Offset(s * 0.45f, s * 0.69f), Offset(s * 0.78f, s * 0.69f),
                strokeWidth = s * 0.025f)
            drawLine(tint.copy(alpha = 0.4f), Offset(s * 0.45f, s * 0.8f), Offset(s * 0.72f, s * 0.8f),
                strokeWidth = s * 0.025f)
        }
    }
    @Composable override fun GridView(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            val gap = s * 0.08f; val cell = (s - gap * 3) / 2
            for (row in 0..1) {
                for (col in 0..1) {
                    val x = gap + col * (cell + gap)
                    val y = gap + row * (cell + gap)
                    // Porthole style cells
                    drawRoundRect(tint, Offset(x, y), Size(cell, cell),
                        cornerRadius = CornerRadius(cell * 0.5f), style = st)
                    // Cross dividers
                    drawLine(tint.copy(alpha = 0.3f), Offset(x + cell * 0.2f, y + cell * 0.5f),
                        Offset(x + cell * 0.8f, y + cell * 0.5f), strokeWidth = s * 0.02f)
                    drawLine(tint.copy(alpha = 0.3f), Offset(x + cell * 0.5f, y + cell * 0.2f),
                        Offset(x + cell * 0.5f, y + cell * 0.8f), strokeWidth = s * 0.02f)
                }
            }
        }
    }
    @Composable override fun Apps(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val gap = s / 4f
            // 3x3 grid of small anchors (simplified as dots with tiny lines)
            for (row in 0..2) {
                for (col in 0..2) {
                    val cx = gap + col * gap; val cy = gap + row * gap
                    drawCircle(tint, s * 0.04f, Offset(cx, cy))
                    drawLine(tint, Offset(cx, cy + s * 0.01f), Offset(cx, cy + s * 0.06f),
                        strokeWidth = s * 0.02f, cap = StrokeCap.Round)
                }
            }
        }
    }
}

// ── Content Icons ───────────────────────────────────────────────

private class NavyContentIcons : BaseContentIcons() {
    @Composable override fun Star(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            // Starfish — five organic curved arms
            val cx = s * 0.5f; val cy = s * 0.5f
            val p = Path().apply {
                for (i in 0 until 5) {
                    val angle = Math.toRadians(-90.0 + i * 72.0)
                    val nextAngle = Math.toRadians(-90.0 + (i + 1) * 72.0)
                    val midAngle = Math.toRadians(-90.0 + i * 72.0 + 36.0)
                    val outerR = s * 0.4f; val innerR = s * 0.18f
                    val ox = cx + outerR * cos(angle).toFloat()
                    val oy = cy + outerR * sin(angle).toFloat()
                    val ix = cx + innerR * cos(midAngle).toFloat()
                    val iy = cy + innerR * sin(midAngle).toFloat()
                    if (i == 0) moveTo(ox, oy)
                    quadraticBezierTo(
                        cx + innerR * 0.6f * cos(angle + 0.3).toFloat(),
                        cy + innerR * 0.6f * sin(angle + 0.3).toFloat(),
                        ix, iy
                    )
                    val nx = cx + outerR * cos(nextAngle).toFloat()
                    val ny = cy + outerR * sin(nextAngle).toFloat()
                    quadraticBezierTo(
                        cx + innerR * 0.6f * cos(nextAngle - 0.3).toFloat(),
                        cy + innerR * 0.6f * sin(nextAngle - 0.3).toFloat(),
                        nx, ny
                    )
                }
                close()
            }
            drawPath(p, tint, style = Fill)
        }
    }
    @Composable override fun StarBorder(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            val cx = s * 0.5f; val cy = s * 0.5f
            val p = Path().apply {
                for (i in 0 until 5) {
                    val angle = Math.toRadians(-90.0 + i * 72.0)
                    val nextAngle = Math.toRadians(-90.0 + (i + 1) * 72.0)
                    val midAngle = Math.toRadians(-90.0 + i * 72.0 + 36.0)
                    val outerR = s * 0.4f; val innerR = s * 0.18f
                    val ox = cx + outerR * cos(angle).toFloat()
                    val oy = cy + outerR * sin(angle).toFloat()
                    val ix = cx + innerR * cos(midAngle).toFloat()
                    val iy = cy + innerR * sin(midAngle).toFloat()
                    if (i == 0) moveTo(ox, oy)
                    quadraticBezierTo(
                        cx + innerR * 0.6f * cos(angle + 0.3).toFloat(),
                        cy + innerR * 0.6f * sin(angle + 0.3).toFloat(),
                        ix, iy
                    )
                    val nx = cx + outerR * cos(nextAngle).toFloat()
                    val ny = cy + outerR * sin(nextAngle).toFloat()
                    quadraticBezierTo(
                        cx + innerR * 0.6f * cos(nextAngle - 0.3).toFloat(),
                        cy + innerR * 0.6f * sin(nextAngle - 0.3).toFloat(),
                        nx, ny
                    )
                }
                close()
            }
            drawPath(p, tint, style = st)
        }
    }
    @Composable override fun Image(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            val cx = s * 0.5f; val cy = s * 0.5f; val r = s * 0.35f
            // Porthole — round window
            drawCircle(tint, r, Offset(cx, cy), style = st)
            drawCircle(tint, r * 0.85f, Offset(cx, cy), style = thinNavy(s))
            // Cross dividers
            drawLine(tint, Offset(cx - r * 0.7f, cy), Offset(cx + r * 0.7f, cy),
                strokeWidth = s * 0.04f, cap = StrokeCap.Round)
            drawLine(tint, Offset(cx, cy - r * 0.7f), Offset(cx, cy + r * 0.7f),
                strokeWidth = s * 0.04f, cap = StrokeCap.Round)
            // Bolts at corners
            val boltR = s * 0.025f
            drawCircle(tint, boltR, Offset(cx - r * 0.78f, cy - r * 0.78f))
            drawCircle(tint, boltR, Offset(cx + r * 0.78f, cy - r * 0.78f))
            drawCircle(tint, boltR, Offset(cx - r * 0.78f, cy + r * 0.78f))
            drawCircle(tint, boltR, Offset(cx + r * 0.78f, cy + r * 0.78f))
        }
    }
    @Composable override fun Camera(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            val cx = s * 0.5f; val cy = s * 0.5f; val r = s * 0.35f
            // Porthole with lens
            drawCircle(tint, r, Offset(cx, cy), style = st)
            drawCircle(tint, r * 0.85f, Offset(cx, cy), style = thinNavy(s))
            // Camera lens in center
            drawCircle(tint, s * 0.12f, Offset(cx, cy), style = st)
            drawCircle(tint, s * 0.05f, Offset(cx, cy))
            // Bolts
            for (i in 0 until 4) {
                val angle = Math.toRadians(45.0 + i * 90.0)
                val bx = cx + r * 0.85f * cos(angle).toFloat()
                val by = cy + r * 0.85f * sin(angle).toFloat()
                drawCircle(tint, s * 0.02f, Offset(bx, by))
            }
        }
    }
    @Composable override fun AddPhoto(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Porthole (smaller, offset left)
            drawCircle(tint, s * 0.26f, Offset(s * 0.38f, s * 0.5f), style = st)
            drawCircle(tint, s * 0.21f, Offset(s * 0.38f, s * 0.5f), style = thinNavy(s))
            // Plus sign at top-right
            drawLine(tint, Offset(s * 0.78f, s * 0.2f), Offset(s * 0.78f, s * 0.5f),
                strokeWidth = s * 0.07f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.63f, s * 0.35f), Offset(s * 0.93f, s * 0.35f),
                strokeWidth = s * 0.07f, cap = StrokeCap.Round)
        }
    }
    @Composable override fun Link(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Chain link — two interlocked ovals
            drawRoundRect(tint, Offset(s * 0.08f, s * 0.3f), Size(s * 0.4f, s * 0.4f),
                cornerRadius = CornerRadius(s * 0.15f), style = st)
            drawRoundRect(tint, Offset(s * 0.52f, s * 0.3f), Size(s * 0.4f, s * 0.4f),
                cornerRadius = CornerRadius(s * 0.15f), style = st)
            // Connecting overlap
            drawLine(tint, Offset(s * 0.44f, s * 0.5f), Offset(s * 0.56f, s * 0.5f),
                strokeWidth = s * 0.06f, cap = StrokeCap.Round)
        }
    }
    @Composable override fun LinkOff(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Broken chain — two separated ovals
            drawRoundRect(tint, Offset(s * 0.05f, s * 0.3f), Size(s * 0.38f, s * 0.4f),
                cornerRadius = CornerRadius(s * 0.15f), style = st)
            drawRoundRect(tint, Offset(s * 0.57f, s * 0.3f), Size(s * 0.38f, s * 0.4f),
                cornerRadius = CornerRadius(s * 0.15f), style = st)
            // Slash through
            drawLine(tint, Offset(s * 0.2f, s * 0.82f), Offset(s * 0.8f, s * 0.18f),
                strokeWidth = s * 0.06f, cap = StrokeCap.Round)
        }
    }
    @Composable override fun Palette(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Scallop shell shape
            val shell = Path().apply {
                moveTo(s * 0.5f, s * 0.88f)
                cubicTo(s * 0.1f, s * 0.85f, s * 0.05f, s * 0.4f, s * 0.5f, s * 0.12f)
                cubicTo(s * 0.95f, s * 0.4f, s * 0.9f, s * 0.85f, s * 0.5f, s * 0.88f)
            }
            drawPath(shell, tint, style = st)
            // Shell ridges radiating from bottom center
            for (i in -2..2) {
                val angle = Math.toRadians(-90.0 + i * 18.0)
                val ex = s * 0.5f + s * 0.32f * cos(angle).toFloat()
                val ey = s * 0.5f + s * 0.32f * sin(angle).toFloat()
                drawLine(tint.copy(alpha = 0.4f), Offset(s * 0.5f, s * 0.82f), Offset(ex, ey),
                    strokeWidth = s * 0.025f, cap = StrokeCap.Round)
            }
        }
    }
    @Composable override fun FileOpen(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Rolled scroll / map
            val scroll = Path().apply {
                moveTo(s * 0.2f, s * 0.15f); lineTo(s * 0.8f, s * 0.15f)
                lineTo(s * 0.8f, s * 0.78f); lineTo(s * 0.2f, s * 0.78f); close()
            }
            drawPath(scroll, tint, style = st)
            // Top roll curl
            val topCurl = Path().apply {
                moveTo(s * 0.18f, s * 0.15f)
                cubicTo(s * 0.18f, s * 0.08f, s * 0.28f, s * 0.08f, s * 0.28f, s * 0.15f)
            }
            drawPath(topCurl, tint, style = st)
            val topCurlR = Path().apply {
                moveTo(s * 0.72f, s * 0.15f)
                cubicTo(s * 0.72f, s * 0.08f, s * 0.82f, s * 0.08f, s * 0.82f, s * 0.15f)
            }
            drawPath(topCurlR, tint, style = st)
            // Bottom roll curl
            val botCurl = Path().apply {
                moveTo(s * 0.18f, s * 0.78f)
                cubicTo(s * 0.18f, s * 0.86f, s * 0.28f, s * 0.86f, s * 0.28f, s * 0.78f)
            }
            drawPath(botCurl, tint, style = st)
            val botCurlR = Path().apply {
                moveTo(s * 0.72f, s * 0.78f)
                cubicTo(s * 0.72f, s * 0.86f, s * 0.82f, s * 0.86f, s * 0.82f, s * 0.78f)
            }
            drawPath(botCurlR, tint, style = st)
            // Map lines
            drawLine(tint.copy(alpha = 0.3f), Offset(s * 0.3f, s * 0.35f), Offset(s * 0.7f, s * 0.35f),
                strokeWidth = s * 0.02f)
            drawLine(tint.copy(alpha = 0.3f), Offset(s * 0.3f, s * 0.5f), Offset(s * 0.65f, s * 0.5f),
                strokeWidth = s * 0.02f)
        }
    }
    @Composable override fun CalendarMonth(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Ship's log book
            drawRoundRect(tint, Offset(s * 0.15f, s * 0.12f), Size(s * 0.7f, s * 0.76f),
                cornerRadius = CornerRadius(s * 0.04f), style = st)
            // Spine
            drawLine(tint, Offset(s * 0.15f, s * 0.12f), Offset(s * 0.15f, s * 0.88f),
                strokeWidth = s * 0.06f, cap = StrokeCap.Round)
            // Lines on page
            for (i in 0..4) {
                val y = s * (0.28f + i * 0.12f)
                drawLine(tint.copy(alpha = 0.3f), Offset(s * 0.28f, y), Offset(s * 0.75f, y),
                    strokeWidth = s * 0.02f)
            }
            // Small anchor emblem on cover
            drawAnchor(Offset(s * 0.52f, s * 0.2f), s * 0.06f, tint.copy(alpha = 0.5f))
        }
    }
    @Composable override fun Notifications(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Ship's bell
            val bell = Path().apply {
                moveTo(s * 0.3f, s * 0.65f)
                cubicTo(s * 0.3f, s * 0.35f, s * 0.38f, s * 0.2f, s * 0.5f, s * 0.2f)
                cubicTo(s * 0.62f, s * 0.2f, s * 0.7f, s * 0.35f, s * 0.7f, s * 0.65f)
                lineTo(s * 0.78f, s * 0.72f); lineTo(s * 0.22f, s * 0.72f); close()
            }
            drawPath(bell, tint, style = st)
            // Mounting bracket at top
            drawLine(tint, Offset(s * 0.5f, s * 0.1f), Offset(s * 0.5f, s * 0.2f),
                strokeWidth = s * 0.05f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.38f, s * 0.1f), Offset(s * 0.62f, s * 0.1f),
                strokeWidth = s * 0.05f, cap = StrokeCap.Round)
            // Clapper
            drawCircle(tint, s * 0.04f, Offset(s * 0.5f, s * 0.72f))
            drawLine(tint, Offset(s * 0.5f, s * 0.72f), Offset(s * 0.5f, s * 0.82f),
                strokeWidth = s * 0.03f, cap = StrokeCap.Round)
            drawCircle(tint, s * 0.03f, Offset(s * 0.5f, s * 0.85f))
            // Sound waves
            drawArc(tint.copy(alpha = 0.3f), 220f, 100f, false,
                Offset(s * 0.12f, s * 0.5f), Size(s * 0.16f, s * 0.3f), style = thinNavy(s))
            drawArc(tint.copy(alpha = 0.3f), 220f, 100f, false,
                Offset(s * 0.72f, s * 0.5f), Size(s * 0.16f, s * 0.3f), style = thinNavy(s))
        }
    }
    @Composable override fun AttachMoney(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Gold coin
            drawCircle(tint, s * 0.36f, Offset(s * 0.5f, s * 0.5f), style = st)
            drawCircle(tint, s * 0.3f, Offset(s * 0.5f, s * 0.5f), style = thinNavy(s))
            // Anchor emblem inside
            drawAnchor(Offset(s * 0.5f, s * 0.5f), s * 0.18f, tint)
        }
    }
    @Composable override fun Category(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            val cx = s * 0.5f; val cy = s * 0.5f; val r = s * 0.36f
            // Compass quadrants — circle divided into 4
            drawCircle(tint, r, Offset(cx, cy), style = st)
            drawLine(tint, Offset(cx - r, cy), Offset(cx + r, cy),
                strokeWidth = s * 0.05f, cap = StrokeCap.Round)
            drawLine(tint, Offset(cx, cy - r), Offset(cx, cy + r),
                strokeWidth = s * 0.05f, cap = StrokeCap.Round)
            // Small dots in each quadrant
            drawCircle(tint, s * 0.03f, Offset(cx - r * 0.5f, cy - r * 0.5f))
            drawCircle(tint, s * 0.03f, Offset(cx + r * 0.5f, cy - r * 0.5f))
            drawCircle(tint, s * 0.03f, Offset(cx - r * 0.5f, cy + r * 0.5f))
            drawCircle(tint, s * 0.03f, Offset(cx + r * 0.5f, cy + r * 0.5f))
        }
    }
    @Composable override fun Location(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Lighthouse with beacon rays
            val tower = Path().apply {
                moveTo(s * 0.38f, s * 0.9f); lineTo(s * 0.42f, s * 0.35f)
                lineTo(s * 0.58f, s * 0.35f); lineTo(s * 0.62f, s * 0.9f); close()
            }
            drawPath(tower, tint, style = st)
            // Lamp housing
            drawRoundRect(tint, Offset(s * 0.38f, s * 0.22f), Size(s * 0.24f, s * 0.14f),
                cornerRadius = CornerRadius(s * 0.02f), style = st)
            // Roof
            val roof = Path().apply {
                moveTo(s * 0.5f, s * 0.1f); lineTo(s * 0.35f, s * 0.22f); lineTo(s * 0.65f, s * 0.22f); close()
            }
            drawPath(roof, tint, style = st)
            // Beacon rays
            drawLine(tint.copy(alpha = 0.5f), Offset(s * 0.38f, s * 0.26f), Offset(s * 0.12f, s * 0.15f),
                strokeWidth = s * 0.03f, cap = StrokeCap.Round)
            drawLine(tint.copy(alpha = 0.5f), Offset(s * 0.62f, s * 0.26f), Offset(s * 0.88f, s * 0.15f),
                strokeWidth = s * 0.03f, cap = StrokeCap.Round)
            drawLine(tint.copy(alpha = 0.35f), Offset(s * 0.38f, s * 0.29f), Offset(s * 0.15f, s * 0.32f),
                strokeWidth = s * 0.025f, cap = StrokeCap.Round)
            drawLine(tint.copy(alpha = 0.35f), Offset(s * 0.62f, s * 0.29f), Offset(s * 0.85f, s * 0.32f),
                strokeWidth = s * 0.025f, cap = StrokeCap.Round)
            // Stripes
            drawLine(tint, Offset(s * 0.41f, s * 0.55f), Offset(s * 0.59f, s * 0.55f), strokeWidth = s * 0.03f)
            drawLine(tint, Offset(s * 0.40f, s * 0.7f), Offset(s * 0.60f, s * 0.7f), strokeWidth = s * 0.03f)
        }
    }
}

// ── Arrow Icons ─────────────────────────────────────────────────

private class NavyArrowIcons : BaseArrowIcons() {
    @Composable override fun ArrowBack(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Wave-styled left arrow
            val wave = Path().apply {
                moveTo(s * 0.82f, s * 0.5f)
                cubicTo(s * 0.7f, s * 0.42f, s * 0.6f, s * 0.58f, s * 0.45f, s * 0.5f)
                cubicTo(s * 0.38f, s * 0.46f, s * 0.32f, s * 0.5f, s * 0.25f, s * 0.5f)
            }
            drawPath(wave, tint, style = st)
            // Arrowhead
            val head = Path().apply {
                moveTo(s * 0.42f, s * 0.28f); lineTo(s * 0.2f, s * 0.5f); lineTo(s * 0.42f, s * 0.72f)
            }
            drawPath(head, tint, style = st)
        }
    }
    @Composable override fun ArrowForward(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Wave-styled right arrow
            val wave = Path().apply {
                moveTo(s * 0.18f, s * 0.5f)
                cubicTo(s * 0.3f, s * 0.42f, s * 0.4f, s * 0.58f, s * 0.55f, s * 0.5f)
                cubicTo(s * 0.62f, s * 0.46f, s * 0.68f, s * 0.5f, s * 0.75f, s * 0.5f)
            }
            drawPath(wave, tint, style = st)
            val head = Path().apply {
                moveTo(s * 0.58f, s * 0.28f); lineTo(s * 0.8f, s * 0.5f); lineTo(s * 0.58f, s * 0.72f)
            }
            drawPath(head, tint, style = st)
        }
    }
    @Composable override fun KeyboardArrowLeft(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Small wave chevron left
            val chev = Path().apply {
                moveTo(s * 0.62f, s * 0.22f)
                cubicTo(s * 0.5f, s * 0.35f, s * 0.38f, s * 0.42f, s * 0.32f, s * 0.5f)
                cubicTo(s * 0.38f, s * 0.58f, s * 0.5f, s * 0.65f, s * 0.62f, s * 0.78f)
            }
            drawPath(chev, tint, style = st)
        }
    }
    @Composable override fun KeyboardArrowRight(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            val chev = Path().apply {
                moveTo(s * 0.38f, s * 0.22f)
                cubicTo(s * 0.5f, s * 0.35f, s * 0.62f, s * 0.42f, s * 0.68f, s * 0.5f)
                cubicTo(s * 0.62f, s * 0.58f, s * 0.5f, s * 0.65f, s * 0.38f, s * 0.78f)
            }
            drawPath(chev, tint, style = st)
        }
    }
    @Composable override fun ExpandMore(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Wave chevron down
            val chev = Path().apply {
                moveTo(s * 0.2f, s * 0.35f)
                cubicTo(s * 0.32f, s * 0.45f, s * 0.42f, s * 0.58f, s * 0.5f, s * 0.65f)
                cubicTo(s * 0.58f, s * 0.58f, s * 0.68f, s * 0.45f, s * 0.8f, s * 0.35f)
            }
            drawPath(chev, tint, style = st)
        }
    }
    @Composable override fun ExpandLess(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Wave chevron up
            val chev = Path().apply {
                moveTo(s * 0.2f, s * 0.65f)
                cubicTo(s * 0.32f, s * 0.55f, s * 0.42f, s * 0.42f, s * 0.5f, s * 0.35f)
                cubicTo(s * 0.58f, s * 0.42f, s * 0.68f, s * 0.55f, s * 0.8f, s * 0.65f)
            }
            drawPath(chev, tint, style = st)
        }
    }
    @Composable override fun ArrowDropDown(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            // Small anchor pointing down
            drawCircle(tint, s * 0.05f, Offset(s * 0.5f, s * 0.2f))
            drawLine(tint, Offset(s * 0.5f, s * 0.25f), Offset(s * 0.5f, s * 0.6f),
                strokeWidth = s * 0.05f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.38f, s * 0.38f), Offset(s * 0.62f, s * 0.38f),
                strokeWidth = s * 0.04f, cap = StrokeCap.Round)
            // Down-pointing flukes
            val fluke = Path().apply {
                moveTo(s * 0.32f, s * 0.52f)
                quadraticBezierTo(s * 0.35f, s * 0.72f, s * 0.5f, s * 0.78f)
                quadraticBezierTo(s * 0.65f, s * 0.72f, s * 0.68f, s * 0.52f)
            }
            drawPath(fluke, tint, style = navyStroke(s))
        }
    }
    @Composable override fun SwapVert(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Tidal arrows — up wave arrow
            val up = Path().apply {
                moveTo(s * 0.3f, s * 0.75f)
                cubicTo(s * 0.3f, s * 0.6f, s * 0.3f, s * 0.45f, s * 0.3f, s * 0.3f)
            }
            drawPath(up, tint, style = st)
            val upHead = Path().apply {
                moveTo(s * 0.18f, s * 0.42f); lineTo(s * 0.3f, s * 0.22f); lineTo(s * 0.42f, s * 0.42f)
            }
            drawPath(upHead, tint, style = st)
            // Down wave arrow
            val dn = Path().apply {
                moveTo(s * 0.7f, s * 0.25f)
                cubicTo(s * 0.7f, s * 0.4f, s * 0.7f, s * 0.55f, s * 0.7f, s * 0.7f)
            }
            drawPath(dn, tint, style = st)
            val dnHead = Path().apply {
                moveTo(s * 0.58f, s * 0.58f); lineTo(s * 0.7f, s * 0.78f); lineTo(s * 0.82f, s * 0.58f)
            }
            drawPath(dnHead, tint, style = st)
            // Small wave between
            val wave = Path().apply {
                moveTo(s * 0.4f, s * 0.5f)
                cubicTo(s * 0.45f, s * 0.45f, s * 0.55f, s * 0.55f, s * 0.6f, s * 0.5f)
            }
            drawPath(wave, tint.copy(alpha = 0.4f), style = thinNavy(s))
        }
    }
    @Composable override fun OpenInNew(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Telescope extending outward
            // Base section
            drawRoundRect(tint, Offset(s * 0.08f, s * 0.42f), Size(s * 0.3f, s * 0.16f),
                cornerRadius = CornerRadius(s * 0.03f), style = st)
            // Middle section
            drawRoundRect(tint, Offset(s * 0.32f, s * 0.39f), Size(s * 0.25f, s * 0.22f),
                cornerRadius = CornerRadius(s * 0.03f), style = st)
            // Front section extending out
            drawRoundRect(tint, Offset(s * 0.52f, s * 0.36f), Size(s * 0.2f, s * 0.28f),
                cornerRadius = CornerRadius(s * 0.04f), style = st)
            // Arrow indicating outward
            drawLine(tint, Offset(s * 0.72f, s * 0.5f), Offset(s * 0.9f, s * 0.5f),
                strokeWidth = s * 0.05f, cap = StrokeCap.Round)
            val arr = Path().apply {
                moveTo(s * 0.82f, s * 0.38f); lineTo(s * 0.92f, s * 0.5f); lineTo(s * 0.82f, s * 0.62f)
            }
            drawPath(arr, tint, style = st)
        }
    }
}

// ── Status Icons ────────────────────────────────────────────────

private class NavyStatusIcons : BaseStatusIcons() {
    @Composable override fun CheckCircle(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            val cx = s * 0.5f; val cy = s * 0.5f; val r = s * 0.36f
            // Life preserver ring
            drawCircle(tint, r, Offset(cx, cy), style = st)
            drawCircle(tint, r * 0.6f, Offset(cx, cy), style = st)
            // Cross straps on ring
            for (i in 0 until 4) {
                val angle = Math.toRadians(i * 90.0 + 45.0)
                val ix = cx + r * 0.6f * cos(angle).toFloat()
                val iy = cy + r * 0.6f * sin(angle).toFloat()
                val ox = cx + r * cos(angle).toFloat()
                val oy = cy + r * sin(angle).toFloat()
                drawLine(tint, Offset(ix, iy), Offset(ox, oy),
                    strokeWidth = s * 0.04f, cap = StrokeCap.Round)
            }
            // Checkmark inside
            val check = Path().apply {
                moveTo(cx - r * 0.3f, cy); lineTo(cx - r * 0.05f, cy + r * 0.25f)
                lineTo(cx + r * 0.3f, cy - r * 0.2f)
            }
            drawPath(check, tint, style = st)
        }
    }
    @Composable override fun Warning(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Lighthouse with warning triangle beam
            val tower = Path().apply {
                moveTo(s * 0.42f, s * 0.9f); lineTo(s * 0.45f, s * 0.4f)
                lineTo(s * 0.55f, s * 0.4f); lineTo(s * 0.58f, s * 0.9f); close()
            }
            drawPath(tower, tint, style = st)
            // Lamp
            drawRoundRect(tint, Offset(s * 0.42f, s * 0.28f), Size(s * 0.16f, s * 0.13f),
                cornerRadius = CornerRadius(s * 0.02f), style = st)
            // Roof
            val roof = Path().apply {
                moveTo(s * 0.5f, s * 0.18f); lineTo(s * 0.4f, s * 0.28f); lineTo(s * 0.6f, s * 0.28f); close()
            }
            drawPath(roof, tint, style = st)
            // Warning triangle beam left
            val beamL = Path().apply {
                moveTo(s * 0.42f, s * 0.3f); lineTo(s * 0.08f, s * 0.12f); lineTo(s * 0.08f, s * 0.38f); close()
            }
            drawPath(beamL, tint.copy(alpha = 0.35f), style = thinNavy(s))
            // Warning triangle beam right
            val beamR = Path().apply {
                moveTo(s * 0.58f, s * 0.3f); lineTo(s * 0.92f, s * 0.12f); lineTo(s * 0.92f, s * 0.38f); close()
            }
            drawPath(beamR, tint.copy(alpha = 0.35f), style = thinNavy(s))
        }
    }
    @Composable override fun Error(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Storm warning flag — square flag with X
            val flag = Path().apply {
                moveTo(s * 0.3f, s * 0.12f); lineTo(s * 0.85f, s * 0.12f)
                lineTo(s * 0.85f, s * 0.58f); lineTo(s * 0.3f, s * 0.58f); close()
            }
            drawPath(flag, tint, style = st)
            // X on flag
            drawLine(tint, Offset(s * 0.38f, s * 0.2f), Offset(s * 0.77f, s * 0.5f),
                strokeWidth = s * 0.06f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.77f, s * 0.2f), Offset(s * 0.38f, s * 0.5f),
                strokeWidth = s * 0.06f, cap = StrokeCap.Round)
            // Flagpole
            drawLine(tint, Offset(s * 0.3f, s * 0.08f), Offset(s * 0.3f, s * 0.92f),
                strokeWidth = s * 0.05f, cap = StrokeCap.Round)
        }
    }
    @Composable override fun Info(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Message in bottle
            // Bottle body
            val bottle = Path().apply {
                moveTo(s * 0.3f, s * 0.42f)
                cubicTo(s * 0.3f, s * 0.82f, s * 0.3f, s * 0.88f, s * 0.38f, s * 0.88f)
                lineTo(s * 0.62f, s * 0.88f)
                cubicTo(s * 0.7f, s * 0.88f, s * 0.7f, s * 0.82f, s * 0.7f, s * 0.42f)
                close()
            }
            drawPath(bottle, tint, style = st)
            // Bottle neck
            drawRoundRect(tint, Offset(s * 0.42f, s * 0.18f), Size(s * 0.16f, s * 0.24f),
                cornerRadius = CornerRadius(s * 0.04f), style = st)
            // Cork
            drawRoundRect(tint, Offset(s * 0.44f, s * 0.1f), Size(s * 0.12f, s * 0.1f),
                cornerRadius = CornerRadius(s * 0.03f), style = Fill)
            drawRoundRect(tint, Offset(s * 0.44f, s * 0.1f), Size(s * 0.12f, s * 0.1f),
                cornerRadius = CornerRadius(s * 0.03f), style = thinNavy(s))
            // 'i' inside bottle
            drawCircle(tint, s * 0.03f, Offset(s * 0.5f, s * 0.55f))
            drawLine(tint, Offset(s * 0.5f, s * 0.62f), Offset(s * 0.5f, s * 0.78f),
                strokeWidth = s * 0.05f, cap = StrokeCap.Round)
        }
    }
    @Composable override fun Visibility(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Telescope open / extended
            // Eyepiece
            drawRoundRect(tint, Offset(s * 0.06f, s * 0.42f), Size(s * 0.14f, s * 0.16f),
                cornerRadius = CornerRadius(s * 0.03f), style = st)
            // Middle barrel
            drawRoundRect(tint, Offset(s * 0.18f, s * 0.39f), Size(s * 0.28f, s * 0.22f),
                cornerRadius = CornerRadius(s * 0.04f), style = st)
            // Front barrel (wider)
            drawRoundRect(tint, Offset(s * 0.44f, s * 0.35f), Size(s * 0.22f, s * 0.3f),
                cornerRadius = CornerRadius(s * 0.05f), style = st)
            // Lens
            drawCircle(tint, s * 0.1f, Offset(s * 0.72f, s * 0.5f), style = st)
            drawCircle(tint, s * 0.04f, Offset(s * 0.72f, s * 0.5f))
            // Light glint
            drawLine(tint.copy(alpha = 0.4f), Offset(s * 0.82f, s * 0.4f), Offset(s * 0.9f, s * 0.32f),
                strokeWidth = s * 0.025f, cap = StrokeCap.Round)
        }
    }
    @Composable override fun VisibilityOff(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Telescope with line through it
            // Eyepiece
            drawRoundRect(tint, Offset(s * 0.06f, s * 0.42f), Size(s * 0.14f, s * 0.16f),
                cornerRadius = CornerRadius(s * 0.03f), style = st)
            // Middle barrel
            drawRoundRect(tint, Offset(s * 0.18f, s * 0.39f), Size(s * 0.28f, s * 0.22f),
                cornerRadius = CornerRadius(s * 0.04f), style = st)
            // Front barrel
            drawRoundRect(tint, Offset(s * 0.44f, s * 0.35f), Size(s * 0.22f, s * 0.3f),
                cornerRadius = CornerRadius(s * 0.05f), style = st)
            // Lens
            drawCircle(tint, s * 0.1f, Offset(s * 0.72f, s * 0.5f), style = st)
            // Diagonal strike-through
            drawLine(tint, Offset(s * 0.12f, s * 0.85f), Offset(s * 0.88f, s * 0.15f),
                strokeWidth = s * 0.07f, cap = StrokeCap.Round)
        }
    }

    @Composable override fun Help(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = navyStroke(s)
            // Life preserver ring
            drawCircle(tint, s * 0.38f, Offset(s * 0.5f, s * 0.5f), style = st)
            drawCircle(tint, s * 0.28f, Offset(s * 0.5f, s * 0.5f), style = thinNavy(s))
            // Flowing question mark
            val q = Path().apply {
                moveTo(s * 0.38f, s * 0.35f)
                cubicTo(s * 0.38f, s * 0.2f, s * 0.62f, s * 0.2f, s * 0.62f, s * 0.38f)
                cubicTo(s * 0.62f, s * 0.48f, s * 0.5f, s * 0.48f, s * 0.5f, s * 0.58f)
            }
            drawPath(q, tint, style = st)
            // Wave dot
            drawCircle(tint, s * 0.04f, Offset(s * 0.5f, s * 0.7f))
            drawCircle(tint.copy(alpha = 0.3f), s * 0.06f, Offset(s * 0.5f, s * 0.7f))
        }
    }
}

// ── Provider ────────────────────────────────────────────────────

class NavyIconProvider : BaseSkinIconProvider() {
    override val navigation: NavigationIcons = NavyNavigationIcons()
    override val action: ActionIcons = NavyActionIcons()
    override val content: ContentIcons = NavyContentIcons()
    override val arrow: ArrowIcons = NavyArrowIcons()
    override val status: StatusIcons = NavyStatusIcons()
}
