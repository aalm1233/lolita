package com.lolita.app.ui.theme.skin.icon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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
            val s = size.minDimension
            val cx = s * 0.5f; val cy = s * 0.5f
            val petalR = s * 0.12f
            listOf(
                Offset(cx, cy - s * 0.28f),
                Offset(cx + s * 0.28f, cy),
                Offset(cx, cy + s * 0.28f),
                Offset(cx - s * 0.28f, cy)
            ).forEach { center ->
                val petal = Path().apply {
                    moveTo(cx, cy)
                    cubicTo(center.x - petalR * 0.8f, center.y - petalR * 0.8f,
                        center.x - petalR, center.y + petalR * 0.3f, center.x, center.y)
                    cubicTo(center.x + petalR, center.y + petalR * 0.3f,
                        center.x + petalR * 0.8f, center.y - petalR * 0.8f, cx, cy)
                }
                drawPath(petal, tint, style = Fill)
            }
            drawCircle(tint, radius = s * 0.06f, center = Offset(cx, cy))
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
            data class Bar(val y: Float, val endX: Float)
            val bars = listOf(
                Bar(s * 0.25f, s * 0.80f),
                Bar(s * 0.50f, s * 0.62f),
                Bar(s * 0.75f, s * 0.44f)
            )
            val startX = s * 0.18f
            bars.forEach { bar ->
                drawLine(tint, Offset(startX, bar.y), Offset(bar.endX - s * 0.04f, bar.y),
                    strokeWidth = s * 0.06f, cap = StrokeCap.Round)
                drawCircle(tint, radius = s * 0.055f, center = Offset(bar.endX, bar.y))
            }
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
            val cx = s * 0.5f
            data class Tier(val y: Float, val halfW: Float, val thick: Float)
            val tiers = listOf(
                Tier(s * 0.30f, s * 0.38f, s * 0.08f),
                Tier(s * 0.52f, s * 0.26f, s * 0.07f),
                Tier(s * 0.74f, s * 0.14f, s * 0.06f)
            )
            tiers.forEach { tier ->
                val wave = Path().apply {
                    moveTo(cx - tier.halfW, tier.y)
                    val segments = 4
                    val segW = tier.halfW * 2 / segments
                    for (i in 0 until segments) {
                        val x0 = cx - tier.halfW + segW * i
                        val x1 = x0 + segW
                        val cpY = if (i % 2 == 0) tier.y - s * 0.03f else tier.y + s * 0.03f
                        quadraticBezierTo(x0 + segW / 2, cpY, x1, tier.y)
                    }
                }
                drawPath(wave, tint, style = Stroke(tier.thick, cap = StrokeCap.Round))
            }
            drawCircle(tint, radius = s * 0.04f, center = Offset(cx, s * 0.18f))
            val stem = Path().apply {
                moveTo(cx, s * 0.18f)
                cubicTo(cx + s * 0.03f, s * 0.14f, cx + s * 0.05f, s * 0.12f, cx + s * 0.04f, s * 0.10f)
            }
            drawPath(stem, tint, style = Stroke(s * 0.025f, cap = StrokeCap.Round))
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
    @Composable override fun ViewAgenda(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val r = s * 0.06f
            drawRoundRect(tint, topLeft = Offset(s * 0.15f, s * 0.12f),
                size = Size(s * 0.70f, s * 0.30f),
                cornerRadius = CornerRadius(r, r), style = Stroke(s * 0.06f, cap = StrokeCap.Round))
            drawRoundRect(tint, topLeft = Offset(s * 0.15f, s * 0.58f),
                size = Size(s * 0.70f, s * 0.30f),
                cornerRadius = CornerRadius(r, r), style = Stroke(s * 0.06f, cap = StrokeCap.Round))
            drawSweetHeart(Offset(s * 0.78f, s * 0.18f), s * 0.04f, tint.copy(alpha = 0.5f))
            drawSweetHeart(Offset(s * 0.78f, s * 0.64f), s * 0.04f, tint.copy(alpha = 0.5f))
        }
    }
    @Composable override fun GridView(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val gap = s * 0.08f
            val cellSize = (s - gap * 3) / 2
            val r = cellSize * 0.35f
            for (row in 0..1) {
                for (col in 0..1) {
                    val x = gap + col * (cellSize + gap)
                    val y = gap + row * (cellSize + gap)
                    drawRoundRect(tint, topLeft = Offset(x, y),
                        size = Size(cellSize, cellSize),
                        cornerRadius = CornerRadius(r, r),
                        style = Stroke(s * 0.055f, cap = StrokeCap.Round))
                }
            }
        }
    }
    @Composable override fun Apps(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val gap = s / 4f
            val sizes = listOf(0.065f, 0.055f, 0.06f, 0.055f, 0.07f, 0.055f, 0.06f, 0.055f, 0.065f)
            var idx = 0
            for (row in 0..2) {
                for (col in 0..2) {
                    drawCircle(tint, radius = s * sizes[idx],
                        center = Offset(gap + col * gap, gap + row * gap))
                    idx++
                }
            }
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
            val ribbon = Path().apply {
                moveTo(s * 0.75f, s * 0.22f)
                cubicTo(s * 0.55f, s * 0.28f, s * 0.40f, s * 0.40f, s * 0.22f, s * 0.50f)
                cubicTo(s * 0.40f, s * 0.60f, s * 0.55f, s * 0.72f, s * 0.75f, s * 0.78f)
            }
            drawPath(ribbon, tint, style = sweetStroke(s))
            drawCircle(tint, radius = s * 0.04f, center = Offset(s * 0.22f, s * 0.50f))
            val bowTop = Path().apply {
                moveTo(s * 0.75f, s * 0.22f)
                cubicTo(s * 0.82f, s * 0.15f, s * 0.88f, s * 0.18f, s * 0.85f, s * 0.26f)
            }
            drawPath(bowTop, tint.copy(alpha = 0.6f), style = Stroke(s * 0.05f, cap = StrokeCap.Round))
            val bowBot = Path().apply {
                moveTo(s * 0.75f, s * 0.78f)
                cubicTo(s * 0.82f, s * 0.85f, s * 0.88f, s * 0.82f, s * 0.85f, s * 0.74f)
            }
            drawPath(bowBot, tint.copy(alpha = 0.6f), style = Stroke(s * 0.05f, cap = StrokeCap.Round))
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
