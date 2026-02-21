package com.lolita.app.ui.theme.skin.icon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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

// ── Shared helpers ──────────────────────────────────────────────

private fun DrawScope.sweetStroke(s: Float) = Stroke(
    width = s * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round
)

private fun DrawScope.drawSweetHeart(center: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(center.x, center.y + r * 0.35f)
        cubicTo(center.x - r, center.y - r * 0.4f,
            center.x - r * 0.5f, center.y - r,
            center.x, center.y - r * 0.25f)
        cubicTo(center.x + r * 0.5f, center.y - r,
            center.x + r, center.y - r * 0.4f,
            center.x, center.y + r * 0.35f)
    }
    drawPath(p, color, style = Fill)
}

private fun DrawScope.drawSweetBow(center: Offset, w: Float, color: Color) {
    val p = Path().apply {
        // left loop
        moveTo(center.x, center.y)
        cubicTo(center.x - w * 0.6f, center.y - w * 0.5f,
            center.x - w * 0.8f, center.y + w * 0.3f,
            center.x, center.y)
        // right loop
        cubicTo(center.x + w * 0.8f, center.y + w * 0.3f,
            center.x + w * 0.6f, center.y - w * 0.5f,
            center.x, center.y)
    }
    drawPath(p, color, style = Fill)
}

private fun DrawScope.drawSweetPetal(center: Offset, r: Float, angle: Float, color: Color) {
    rotate(angle, center) {
        val p = Path().apply {
            moveTo(center.x, center.y - r)
            quadraticBezierTo(center.x + r * 0.5f, center.y - r * 0.3f, center.x, center.y)
            quadraticBezierTo(center.x - r * 0.5f, center.y - r * 0.3f, center.x, center.y - r)
        }
        drawPath(p, color.copy(alpha = 0.5f), style = Fill)
    }
}

private fun DrawScope.drawSweetFlower(center: Offset, r: Float, color: Color, count: Int = 5) {
    for (i in 0 until count) {
        drawSweetPetal(center, r, 360f / count * i, color)
    }
    drawCircle(color, r * 0.2f, center)
}

// ── Navigation Icons ────────────────────────────────────────────

private class SweetNavigationIcons : BaseNavigationIcons() {
    @Composable override fun Home(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = sweetStroke(s)
            val p = Path().apply {
                moveTo(s * 0.5f, s * 0.12f)
                lineTo(s * 0.13f, s * 0.48f)
                lineTo(s * 0.13f, s * 0.8f)
                quadraticBezierTo(s * 0.13f, s * 0.88f, s * 0.22f, s * 0.88f)
                lineTo(s * 0.78f, s * 0.88f)
                quadraticBezierTo(s * 0.87f, s * 0.88f, s * 0.87f, s * 0.8f)
                lineTo(s * 0.87f, s * 0.48f)
                close()
            }
            drawPath(p, tint, style = st)
            drawSweetHeart(Offset(s * 0.5f, s * 0.62f), s * 0.11f, tint)
            drawSweetBow(Offset(s * 0.5f, s * 0.12f), s * 0.12f, tint.copy(alpha = 0.6f))
        }
    }
    @Composable override fun Wishlist(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            drawSweetHeart(Offset(s * 0.5f, s * 0.48f), s * 0.32f, tint)
            drawSweetHeart(Offset(s * 0.5f, s * 0.42f), s * 0.22f, tint.copy(alpha = 0.5f))
            drawSweetBow(Offset(s * 0.5f, s * 0.2f), s * 0.14f, tint.copy(alpha = 0.7f))
        }
    }
    @Composable override fun Outfit(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = sweetStroke(s)
            // Hanger
            val hanger = Path().apply {
                moveTo(s * 0.5f, s * 0.1f)
                lineTo(s * 0.5f, s * 0.22f)
                lineTo(s * 0.15f, s * 0.45f)
                moveTo(s * 0.5f, s * 0.22f)
                lineTo(s * 0.85f, s * 0.45f)
            }
            drawPath(hanger, tint, style = st)
            // Dress body
            val dress = Path().apply {
                moveTo(s * 0.25f, s * 0.45f)
                quadraticBezierTo(s * 0.15f, s * 0.75f, s * 0.2f, s * 0.88f)
                lineTo(s * 0.8f, s * 0.88f)
                quadraticBezierTo(s * 0.85f, s * 0.75f, s * 0.75f, s * 0.45f)
            }
            drawPath(dress, tint, style = st)
            drawSweetBow(Offset(s * 0.5f, s * 0.45f), s * 0.1f, tint.copy(alpha = 0.6f))
        }
    }
    @Composable override fun Stats(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = sweetStroke(s)
            // Pie chart
            drawCircle(tint, s * 0.3f, Offset(s * 0.48f, s * 0.52f), style = st)
            drawLine(tint, Offset(s * 0.48f, s * 0.52f), Offset(s * 0.48f, s * 0.22f),
                strokeWidth = s * 0.06f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.48f, s * 0.52f), Offset(s * 0.72f, s * 0.38f),
                strokeWidth = s * 0.06f, cap = StrokeCap.Round)
            drawSweetFlower(Offset(s * 0.82f, s * 0.18f), s * 0.08f, tint)
        }
    }
    @Composable override fun Settings(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = sweetStroke(s)
            val c = Offset(s * 0.5f, s * 0.5f)
            // Flower-shaped gear
            for (i in 0 until 6) {
                drawSweetPetal(c, s * 0.3f, 60f * i, tint)
            }
            drawCircle(tint, s * 0.13f, c, style = st)
            drawSweetHeart(c, s * 0.08f, tint)
        }
    }
}

// ── Action Icons ────────────────────────────────────────────────

private class SweetActionIcons : BaseActionIcons() {
    @Composable override fun Add(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val c = Offset(s * 0.5f, s * 0.5f)
            drawLine(tint, Offset(s * 0.5f, s * 0.2f), Offset(s * 0.5f, s * 0.8f),
                strokeWidth = s * 0.09f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.2f, s * 0.5f), Offset(s * 0.8f, s * 0.5f),
                strokeWidth = s * 0.09f, cap = StrokeCap.Round)
            drawSweetFlower(Offset(s * 0.5f, s * 0.2f), s * 0.06f, tint.copy(alpha = 0.5f), 4)
        }
    }
    @Composable override fun Delete(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = sweetStroke(s)
            // Trash can body
            val body = Path().apply {
                moveTo(s * 0.25f, s * 0.32f)
                lineTo(s * 0.3f, s * 0.85f)
                quadraticBezierTo(s * 0.3f, s * 0.9f, s * 0.35f, s * 0.9f)
                lineTo(s * 0.65f, s * 0.9f)
                quadraticBezierTo(s * 0.7f, s * 0.9f, s * 0.7f, s * 0.85f)
                lineTo(s * 0.75f, s * 0.32f)
            }
            drawPath(body, tint, style = st)
            // Lid
            drawLine(tint, Offset(s * 0.2f, s * 0.3f), Offset(s * 0.8f, s * 0.3f),
                strokeWidth = s * 0.07f, cap = StrokeCap.Round)
            drawSweetHeart(Offset(s * 0.5f, s * 0.22f), s * 0.07f, tint)
        }
    }
    @Composable override fun Edit(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = sweetStroke(s)
            val pen = Path().apply {
                moveTo(s * 0.72f, s * 0.15f)
                lineTo(s * 0.85f, s * 0.28f)
                lineTo(s * 0.35f, s * 0.78f)
                lineTo(s * 0.15f, s * 0.85f)
                lineTo(s * 0.22f, s * 0.65f)
                close()
            }
            drawPath(pen, tint, style = st)
            drawSweetFlower(Offset(s * 0.8f, s * 0.12f), s * 0.05f, tint.copy(alpha = 0.5f), 4)
        }
    }
    @Composable override fun Search(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = sweetStroke(s)
            drawCircle(tint, s * 0.25f, Offset(s * 0.42f, s * 0.42f), style = st)
            drawLine(tint, Offset(s * 0.6f, s * 0.6f), Offset(s * 0.82f, s * 0.82f),
                strokeWidth = s * 0.08f, cap = StrokeCap.Round)
            drawSweetHeart(Offset(s * 0.42f, s * 0.42f), s * 0.09f, tint.copy(alpha = 0.5f))
        }
    }
    @Composable override fun Sort(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            drawLine(tint, Offset(s * 0.2f, s * 0.28f), Offset(s * 0.8f, s * 0.28f),
                strokeWidth = s * 0.07f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.2f, s * 0.5f), Offset(s * 0.65f, s * 0.5f),
                strokeWidth = s * 0.07f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.2f, s * 0.72f), Offset(s * 0.5f, s * 0.72f),
                strokeWidth = s * 0.07f, cap = StrokeCap.Round)
            drawSweetHeart(Offset(s * 0.85f, s * 0.28f), s * 0.05f, tint.copy(alpha = 0.5f))
        }
    }
    @Composable override fun Save(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = sweetStroke(s)
            val check = Path().apply {
                moveTo(s * 0.2f, s * 0.5f)
                lineTo(s * 0.42f, s * 0.72f)
                lineTo(s * 0.8f, s * 0.28f)
            }
            drawPath(check, tint, style = st)
            drawSweetPetal(Offset(s * 0.8f, s * 0.28f), s * 0.08f, 0f, tint)
        }
    }
    @Composable override fun Close(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            drawLine(tint, Offset(s * 0.22f, s * 0.22f), Offset(s * 0.78f, s * 0.78f),
                strokeWidth = s * 0.08f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.78f, s * 0.22f), Offset(s * 0.22f, s * 0.78f),
                strokeWidth = s * 0.08f, cap = StrokeCap.Round)
            drawSweetPetal(Offset(s * 0.78f, s * 0.22f), s * 0.06f, 45f, tint.copy(alpha = 0.4f))
            drawSweetPetal(Offset(s * 0.22f, s * 0.22f), s * 0.06f, -45f, tint.copy(alpha = 0.4f))
        }
    }
    @Composable override fun Share(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = sweetStroke(s)
            val top = Offset(s * 0.7f, s * 0.22f)
            val mid = Offset(s * 0.3f, s * 0.5f)
            val bot = Offset(s * 0.7f, s * 0.78f)
            drawLine(tint, top, mid, strokeWidth = s * 0.06f, cap = StrokeCap.Round)
            drawLine(tint, mid, bot, strokeWidth = s * 0.06f, cap = StrokeCap.Round)
            drawSweetHeart(top, s * 0.07f, tint)
            drawSweetHeart(mid, s * 0.07f, tint)
            drawSweetHeart(bot, s * 0.07f, tint)
        }
    }
    @Composable override fun FilterList(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            drawLine(tint, Offset(s * 0.12f, s * 0.25f), Offset(s * 0.88f, s * 0.25f),
                strokeWidth = s * 0.07f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.25f, s * 0.5f), Offset(s * 0.75f, s * 0.5f),
                strokeWidth = s * 0.07f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.38f, s * 0.75f), Offset(s * 0.62f, s * 0.75f),
                strokeWidth = s * 0.07f, cap = StrokeCap.Round)
            drawSweetBow(Offset(s * 0.5f, s * 0.75f), s * 0.08f, tint.copy(alpha = 0.5f))
        }
    }
    @Composable override fun MoreVert(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            drawSweetHeart(Offset(s * 0.5f, s * 0.22f), s * 0.07f, tint)
            drawSweetHeart(Offset(s * 0.5f, s * 0.5f), s * 0.07f, tint)
            drawSweetHeart(Offset(s * 0.5f, s * 0.78f), s * 0.07f, tint)
        }
    }
    @Composable override fun ContentCopy(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = sweetStroke(s)
            drawRoundRect(tint, Offset(s * 0.28f, s * 0.28f), Size(s * 0.52f, s * 0.62f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.08f), style = st)
            drawRoundRect(tint, Offset(s * 0.18f, s * 0.12f), Size(s * 0.52f, s * 0.62f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.08f), style = st)
            drawSweetHeart(Offset(s * 0.7f, s * 0.18f), s * 0.05f, tint.copy(alpha = 0.5f))
        }
    }
    @Composable override fun Refresh(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = sweetStroke(s)
            drawArc(tint, 30f, 280f, false,
                topLeft = Offset(s * 0.15f, s * 0.15f),
                size = Size(s * 0.7f, s * 0.7f), style = st)
            // Arrow tip
            val arrow = Path().apply {
                moveTo(s * 0.65f, s * 0.15f)
                lineTo(s * 0.78f, s * 0.28f)
                lineTo(s * 0.55f, s * 0.28f)
            }
            drawPath(arrow, tint, style = Fill)
            drawSweetFlower(Offset(s * 0.5f, s * 0.5f), s * 0.06f, tint.copy(alpha = 0.4f), 4)
        }
    }
}

// ── Content Icons ───────────────────────────────────────────────

private class SweetContentIcons : BaseContentIcons() {
    @Composable override fun Star(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val c = Offset(s * 0.5f, s * 0.5f)
            val p = Path().apply {
                moveTo(s * 0.5f, s * 0.1f)
                lineTo(s * 0.62f, s * 0.38f); lineTo(s * 0.92f, s * 0.38f)
                lineTo(s * 0.68f, s * 0.58f); lineTo(s * 0.78f, s * 0.88f)
                lineTo(s * 0.5f, s * 0.7f); lineTo(s * 0.22f, s * 0.88f)
                lineTo(s * 0.32f, s * 0.58f); lineTo(s * 0.08f, s * 0.38f)
                lineTo(s * 0.38f, s * 0.38f); close()
            }
            drawPath(p, tint, style = Fill)
            drawCircle(tint.copy(alpha = 0.4f), s * 0.03f, Offset(s * 0.82f, s * 0.18f))
            drawCircle(tint.copy(alpha = 0.3f), s * 0.02f, Offset(s * 0.88f, s * 0.25f))
        }
    }
    @Composable override fun StarBorder(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = sweetStroke(s)
            val p = Path().apply {
                moveTo(s * 0.5f, s * 0.1f)
                lineTo(s * 0.62f, s * 0.38f); lineTo(s * 0.92f, s * 0.38f)
                lineTo(s * 0.68f, s * 0.58f); lineTo(s * 0.78f, s * 0.88f)
                lineTo(s * 0.5f, s * 0.7f); lineTo(s * 0.22f, s * 0.88f)
                lineTo(s * 0.32f, s * 0.58f); lineTo(s * 0.08f, s * 0.38f)
                lineTo(s * 0.38f, s * 0.38f); close()
            }
            drawPath(p, tint, style = st)
        }
    }
    @Composable override fun Image(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = sweetStroke(s)
            drawRoundRect(tint, Offset(s * 0.12f, s * 0.18f), Size(s * 0.76f, s * 0.64f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.1f), style = st)
            // Mountain with heart
            val mt = Path().apply {
                moveTo(s * 0.2f, s * 0.7f)
                lineTo(s * 0.4f, s * 0.45f); lineTo(s * 0.55f, s * 0.58f)
                lineTo(s * 0.7f, s * 0.38f); lineTo(s * 0.85f, s * 0.7f)
            }
            drawPath(mt, tint, style = st)
            drawSweetHeart(Offset(s * 0.3f, s * 0.35f), s * 0.06f, tint)
        }
    }
    @Composable override fun Camera(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = sweetStroke(s)
            drawRoundRect(tint, Offset(s * 0.1f, s * 0.28f), Size(s * 0.8f, s * 0.55f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.08f), style = st)
            // Lens bump
            val bump = Path().apply {
                moveTo(s * 0.35f, s * 0.28f); lineTo(s * 0.4f, s * 0.18f)
                lineTo(s * 0.6f, s * 0.18f); lineTo(s * 0.65f, s * 0.28f)
            }
            drawPath(bump, tint, style = st)
            drawCircle(tint, s * 0.14f, Offset(s * 0.5f, s * 0.55f), style = st)
            drawSweetHeart(Offset(s * 0.5f, s * 0.55f), s * 0.06f, tint)
        }
    }
    @Composable override fun AddPhoto(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = sweetStroke(s)
            drawRoundRect(tint, Offset(s * 0.05f, s * 0.2f), Size(s * 0.65f, s * 0.6f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.08f), style = st)
            drawLine(tint, Offset(s * 0.78f, s * 0.35f), Offset(s * 0.78f, s * 0.65f),
                strokeWidth = s * 0.07f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.63f, s * 0.5f), Offset(s * 0.93f, s * 0.5f),
                strokeWidth = s * 0.07f, cap = StrokeCap.Round)
            drawSweetBow(Offset(s * 0.78f, s * 0.35f), s * 0.06f, tint.copy(alpha = 0.5f))
        }
    }
    @Composable override fun Link(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = sweetStroke(s)
            drawArc(tint, 150f, 180f, false, Offset(s * 0.15f, s * 0.25f),
                Size(s * 0.35f, s * 0.5f), style = st)
            drawArc(tint, -30f, 180f, false, Offset(s * 0.5f, s * 0.25f),
                Size(s * 0.35f, s * 0.5f), style = st)
            drawLine(tint, Offset(s * 0.4f, s * 0.5f), Offset(s * 0.6f, s * 0.5f),
                strokeWidth = s * 0.06f, cap = StrokeCap.Round)
            drawSweetHeart(Offset(s * 0.5f, s * 0.5f), s * 0.05f, tint.copy(alpha = 0.4f))
        }
    }
    @Composable override fun LinkOff(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = sweetStroke(s)
            drawArc(tint, 150f, 180f, false, Offset(s * 0.1f, s * 0.25f),
                Size(s * 0.35f, s * 0.5f), style = st)
            drawArc(tint, -30f, 180f, false, Offset(s * 0.55f, s * 0.25f),
                Size(s * 0.35f, s * 0.5f), style = st)
            drawLine(tint, Offset(s * 0.2f, s * 0.8f), Offset(s * 0.8f, s * 0.2f),
                strokeWidth = s * 0.06f, cap = StrokeCap.Round)
        }
    }
    @Composable override fun Palette(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = sweetStroke(s)
            val pal = Path().apply {
                moveTo(s * 0.5f, s * 0.1f)
                cubicTo(s * 0.15f, s * 0.1f, s * 0.05f, s * 0.5f, s * 0.25f, s * 0.8f)
                cubicTo(s * 0.35f, s * 0.92f, s * 0.55f, s * 0.85f, s * 0.5f, s * 0.7f)
                cubicTo(s * 0.45f, s * 0.55f, s * 0.65f, s * 0.5f, s * 0.7f, s * 0.6f)
                cubicTo(s * 0.85f, s * 0.75f, s * 0.95f, s * 0.4f, s * 0.5f, s * 0.1f)
            }
            drawPath(pal, tint, style = st)
            drawSweetHeart(Offset(s * 0.3f, s * 0.4f), s * 0.04f, tint)
            drawSweetHeart(Offset(s * 0.45f, s * 0.3f), s * 0.04f, tint.copy(alpha = 0.7f))
            drawSweetHeart(Offset(s * 0.6f, s * 0.35f), s * 0.04f, tint.copy(alpha = 0.5f))
        }
    }
    @Composable override fun FileOpen(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = sweetStroke(s)
            val folder = Path().apply {
                moveTo(s * 0.1f, s * 0.25f)
                lineTo(s * 0.1f, s * 0.8f)
                quadraticBezierTo(s * 0.1f, s * 0.85f, s * 0.15f, s * 0.85f)
                lineTo(s * 0.85f, s * 0.85f)
                quadraticBezierTo(s * 0.9f, s * 0.85f, s * 0.9f, s * 0.8f)
                lineTo(s * 0.9f, s * 0.38f)
                lineTo(s * 0.5f, s * 0.38f)
                lineTo(s * 0.42f, s * 0.25f)
                close()
            }
            drawPath(folder, tint, style = st)
            drawSweetBow(Offset(s * 0.46f, s * 0.38f), s * 0.08f, tint.copy(alpha = 0.5f))
        }
    }
    @Composable override fun CalendarMonth(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = sweetStroke(s)
            drawRoundRect(tint, Offset(s * 0.12f, s * 0.2f), Size(s * 0.76f, s * 0.68f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.08f), style = st)
            drawLine(tint, Offset(s * 0.12f, s * 0.38f), Offset(s * 0.88f, s * 0.38f),
                strokeWidth = s * 0.06f, cap = StrokeCap.Round)
            // Rings
            drawLine(tint, Offset(s * 0.35f, s * 0.12f), Offset(s * 0.35f, s * 0.28f),
                strokeWidth = s * 0.06f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.65f, s * 0.12f), Offset(s * 0.65f, s * 0.28f),
                strokeWidth = s * 0.06f, cap = StrokeCap.Round)
            drawSweetHeart(Offset(s * 0.5f, s * 0.6f), s * 0.1f, tint)
        }
    }
    @Composable override fun Notifications(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = sweetStroke(s)
            val bell = Path().apply {
                moveTo(s * 0.5f, s * 0.1f)
                lineTo(s * 0.5f, s * 0.18f)
                moveTo(s * 0.25f, s * 0.6f)
                cubicTo(s * 0.25f, s * 0.3f, s * 0.35f, s * 0.18f, s * 0.5f, s * 0.18f)
                cubicTo(s * 0.65f, s * 0.18f, s * 0.75f, s * 0.3f, s * 0.75f, s * 0.6f)
                lineTo(s * 0.82f, s * 0.72f)
                lineTo(s * 0.18f, s * 0.72f)
                close()
            }
            drawPath(bell, tint, style = st)
            drawArc(tint, 0f, 180f, false, Offset(s * 0.4f, s * 0.75f),
                Size(s * 0.2f, s * 0.15f), style = st)
            drawSweetBow(Offset(s * 0.5f, s * 0.18f), s * 0.08f, tint.copy(alpha = 0.6f))
        }
    }
    @Composable override fun AttachMoney(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = sweetStroke(s)
            drawLine(tint, Offset(s * 0.5f, s * 0.1f), Offset(s * 0.5f, s * 0.9f),
                strokeWidth = s * 0.06f, cap = StrokeCap.Round)
            val dollar = Path().apply {
                moveTo(s * 0.68f, s * 0.3f)
                cubicTo(s * 0.65f, s * 0.2f, s * 0.35f, s * 0.2f, s * 0.32f, s * 0.35f)
                cubicTo(s * 0.3f, s * 0.48f, s * 0.7f, s * 0.52f, s * 0.68f, s * 0.65f)
                cubicTo(s * 0.65f, s * 0.8f, s * 0.35f, s * 0.8f, s * 0.32f, s * 0.7f)
            }
            drawPath(dollar, tint, style = st)
            drawSweetPetal(Offset(s * 0.75f, s * 0.2f), s * 0.06f, 30f, tint)
        }
    }
    @Composable override fun Category(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = sweetStroke(s)
            val gap = s * 0.06f; val cell = (s - gap * 3) / 2
            val positions = listOf(
                Offset(gap, gap), Offset(gap * 2 + cell, gap),
                Offset(gap, gap * 2 + cell), Offset(gap * 2 + cell, gap * 2 + cell)
            )
            positions.forEach { pos ->
                drawRoundRect(tint, pos, Size(cell, cell),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.06f), style = st)
            }
            positions.forEach { pos ->
                drawSweetHeart(Offset(pos.x + cell / 2, pos.y + cell / 2), cell * 0.18f,
                    tint.copy(alpha = 0.5f))
            }
        }
    }
}

// ── Arrow Icons ─────────────────────────────────────────────────

private class SweetArrowIcons : BaseArrowIcons() {
    @Composable override fun ArrowBack(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val arr = Path().apply {
                moveTo(s * 0.65f, s * 0.2f)
                lineTo(s * 0.3f, s * 0.5f)
                lineTo(s * 0.65f, s * 0.8f)
            }
            drawPath(arr, tint, style = sweetStroke(s))
            drawSweetPetal(Offset(s * 0.65f, s * 0.2f), s * 0.05f, -30f, tint.copy(alpha = 0.4f))
        }
    }
    @Composable override fun ArrowForward(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val arr = Path().apply {
                moveTo(s * 0.35f, s * 0.2f)
                lineTo(s * 0.7f, s * 0.5f)
                lineTo(s * 0.35f, s * 0.8f)
            }
            drawPath(arr, tint, style = sweetStroke(s))
            drawSweetPetal(Offset(s * 0.35f, s * 0.8f), s * 0.05f, 30f, tint.copy(alpha = 0.4f))
        }
    }
    @Composable override fun KeyboardArrowLeft(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val arr = Path().apply {
                moveTo(s * 0.62f, s * 0.22f); lineTo(s * 0.32f, s * 0.5f); lineTo(s * 0.62f, s * 0.78f)
            }
            drawPath(arr, tint, style = sweetStroke(s))
        }
    }
    @Composable override fun KeyboardArrowRight(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val arr = Path().apply {
                moveTo(s * 0.38f, s * 0.22f); lineTo(s * 0.68f, s * 0.5f); lineTo(s * 0.38f, s * 0.78f)
            }
            drawPath(arr, tint, style = sweetStroke(s))
        }
    }
    @Composable override fun ExpandMore(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val arr = Path().apply {
                moveTo(s * 0.22f, s * 0.35f); lineTo(s * 0.5f, s * 0.65f); lineTo(s * 0.78f, s * 0.35f)
            }
            drawPath(arr, tint, style = sweetStroke(s))
            drawSweetPetal(Offset(s * 0.5f, s * 0.65f), s * 0.05f, 180f, tint.copy(alpha = 0.4f))
        }
    }
    @Composable override fun ExpandLess(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val arr = Path().apply {
                moveTo(s * 0.22f, s * 0.65f); lineTo(s * 0.5f, s * 0.35f); lineTo(s * 0.78f, s * 0.65f)
            }
            drawPath(arr, tint, style = sweetStroke(s))
            drawSweetPetal(Offset(s * 0.5f, s * 0.35f), s * 0.05f, 0f, tint.copy(alpha = 0.4f))
        }
    }
    @Composable override fun ArrowDropDown(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val tri = Path().apply {
                moveTo(s * 0.25f, s * 0.35f); lineTo(s * 0.5f, s * 0.7f); lineTo(s * 0.75f, s * 0.35f); close()
            }
            drawPath(tri, tint, style = Fill)
        }
    }
    @Composable override fun SwapVert(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = sweetStroke(s)
            // Up arrow
            val up = Path().apply {
                moveTo(s * 0.35f, s * 0.75f); lineTo(s * 0.35f, s * 0.3f)
                moveTo(s * 0.2f, s * 0.42f); lineTo(s * 0.35f, s * 0.25f); lineTo(s * 0.5f, s * 0.42f)
            }
            drawPath(up, tint, style = st)
            // Down arrow
            val dn = Path().apply {
                moveTo(s * 0.65f, s * 0.25f); lineTo(s * 0.65f, s * 0.7f)
                moveTo(s * 0.5f, s * 0.58f); lineTo(s * 0.65f, s * 0.75f); lineTo(s * 0.8f, s * 0.58f)
            }
            drawPath(dn, tint, style = st)
            drawSweetHeart(Offset(s * 0.5f, s * 0.5f), s * 0.05f, tint.copy(alpha = 0.4f))
        }
    }
    @Composable override fun OpenInNew(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = sweetStroke(s)
            val box = Path().apply {
                moveTo(s * 0.55f, s * 0.15f); lineTo(s * 0.2f, s * 0.15f)
                lineTo(s * 0.2f, s * 0.85f); lineTo(s * 0.85f, s * 0.85f)
                lineTo(s * 0.85f, s * 0.5f)
            }
            drawPath(box, tint, style = st)
            drawLine(tint, Offset(s * 0.5f, s * 0.5f), Offset(s * 0.85f, s * 0.15f),
                strokeWidth = s * 0.07f, cap = StrokeCap.Round)
            drawSweetPetal(Offset(s * 0.85f, s * 0.15f), s * 0.06f, -45f, tint.copy(alpha = 0.5f))
        }
    }
}

// ── Status Icons ────────────────────────────────────────────────

private class SweetStatusIcons : BaseStatusIcons() {
    @Composable override fun CheckCircle(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = sweetStroke(s)
            // Flower wreath circle
            for (i in 0 until 8) {
                drawSweetPetal(Offset(s * 0.5f, s * 0.5f), s * 0.38f, 45f * i, tint.copy(alpha = 0.3f))
            }
            val check = Path().apply {
                moveTo(s * 0.3f, s * 0.5f); lineTo(s * 0.45f, s * 0.65f); lineTo(s * 0.72f, s * 0.35f)
            }
            drawPath(check, tint, style = st)
        }
    }
    @Composable override fun Warning(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = sweetStroke(s)
            val tri = Path().apply {
                moveTo(s * 0.5f, s * 0.12f)
                quadraticBezierTo(s * 0.48f, s * 0.15f, s * 0.1f, s * 0.85f)
                quadraticBezierTo(s * 0.1f, s * 0.9f, s * 0.15f, s * 0.9f)
                lineTo(s * 0.85f, s * 0.9f)
                quadraticBezierTo(s * 0.9f, s * 0.9f, s * 0.9f, s * 0.85f)
                quadraticBezierTo(s * 0.52f, s * 0.15f, s * 0.5f, s * 0.12f)
            }
            drawPath(tri, tint, style = st)
            drawLine(tint, Offset(s * 0.5f, s * 0.4f), Offset(s * 0.5f, s * 0.62f),
                strokeWidth = s * 0.07f, cap = StrokeCap.Round)
            drawSweetHeart(Offset(s * 0.5f, s * 0.75f), s * 0.05f, tint)
        }
    }
    @Composable override fun Error(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = sweetStroke(s)
            drawCircle(tint, s * 0.38f, Offset(s * 0.5f, s * 0.5f), style = st)
            drawLine(tint, Offset(s * 0.35f, s * 0.35f), Offset(s * 0.65f, s * 0.65f),
                strokeWidth = s * 0.07f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.65f, s * 0.35f), Offset(s * 0.35f, s * 0.65f),
                strokeWidth = s * 0.07f, cap = StrokeCap.Round)
            drawSweetPetal(Offset(s * 0.82f, s * 0.18f), s * 0.05f, -45f, tint.copy(alpha = 0.4f))
        }
    }
    @Composable override fun Info(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = sweetStroke(s)
            drawCircle(tint, s * 0.38f, Offset(s * 0.5f, s * 0.5f), style = st)
            drawSweetHeart(Offset(s * 0.5f, s * 0.33f), s * 0.06f, tint)
            drawLine(tint, Offset(s * 0.5f, s * 0.48f), Offset(s * 0.5f, s * 0.7f),
                strokeWidth = s * 0.07f, cap = StrokeCap.Round)
        }
    }
    @Composable override fun Visibility(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = sweetStroke(s)
            val eye = Path().apply {
                moveTo(s * 0.08f, s * 0.5f)
                cubicTo(s * 0.2f, s * 0.25f, s * 0.8f, s * 0.25f, s * 0.92f, s * 0.5f)
                cubicTo(s * 0.8f, s * 0.75f, s * 0.2f, s * 0.75f, s * 0.08f, s * 0.5f)
            }
            drawPath(eye, tint, style = st)
            drawSweetHeart(Offset(s * 0.5f, s * 0.5f), s * 0.1f, tint)
        }
    }
    @Composable override fun VisibilityOff(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = sweetStroke(s)
            val eye = Path().apply {
                moveTo(s * 0.08f, s * 0.5f)
                cubicTo(s * 0.2f, s * 0.25f, s * 0.8f, s * 0.25f, s * 0.92f, s * 0.5f)
                cubicTo(s * 0.8f, s * 0.75f, s * 0.2f, s * 0.75f, s * 0.08f, s * 0.5f)
            }
            drawPath(eye, tint, style = st)
            drawSweetHeart(Offset(s * 0.5f, s * 0.5f), s * 0.08f, tint.copy(alpha = 0.4f))
            drawLine(tint, Offset(s * 0.18f, s * 0.82f), Offset(s * 0.82f, s * 0.18f),
                strokeWidth = s * 0.07f, cap = StrokeCap.Round)
        }
    }
}

// ── Provider ────────────────────────────────────────────────────

class SweetIconProvider : BaseSkinIconProvider() {
    override val navigation: NavigationIcons = SweetNavigationIcons()
    override val action: ActionIcons = SweetActionIcons()
    override val content: ContentIcons = SweetContentIcons()
    override val arrow: ArrowIcons = SweetArrowIcons()
    override val status: StatusIcons = SweetStatusIcons()
}
