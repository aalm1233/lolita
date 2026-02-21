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

// ── Shared helpers ──────────────────────────────────────────────

private fun gothicStroke(s: Float) = Stroke(
    width = s * 0.06f, cap = StrokeCap.Butt, join = StrokeJoin.Miter
)

private fun DrawScope.drawGothicCross(c: Offset, arm: Float, color: Color) {
    val w = arm * 0.28f
    drawRect(color, Offset(c.x - w, c.y - arm), Size(w * 2, arm * 2))
    drawRect(color, Offset(c.x - arm * 0.6f, c.y - arm * 0.3f - w),
        Size(arm * 1.2f, w * 2))
}

private fun DrawScope.drawGothicThorn(start: Offset, end: Offset, len: Float, color: Color) {
    val dx = end.x - start.x; val dy = end.y - start.y
    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
    if (dist < 1f) return
    val nx = -dy / dist; val ny = dx / dist
    val count = (dist / (len * 2.5f)).toInt().coerceIn(2, 6)
    for (i in 1 until count) {
        val t = i.toFloat() / count
        val px = start.x + dx * t; val py = start.y + dy * t
        val side = if (i % 2 == 0) 1f else -1f
        val tip = Offset(px + nx * len * side, py + ny * len * side)
        drawLine(color, Offset(px, py), tip, strokeWidth = len * 0.3f)
    }
}

private fun DrawScope.drawGothicBatWing(anchor: Offset, span: Float, color: Color, flipX: Boolean = false) {
    val dir = if (flipX) -1f else 1f
    val p = Path().apply {
        moveTo(anchor.x, anchor.y)
        cubicTo(anchor.x + span * 0.3f * dir, anchor.y - span * 0.6f,
            anchor.x + span * 0.7f * dir, anchor.y - span * 0.4f,
            anchor.x + span * dir, anchor.y - span * 0.1f)
        lineTo(anchor.x + span * 0.7f * dir, anchor.y + span * 0.1f)
        cubicTo(anchor.x + span * 0.5f * dir, anchor.y - span * 0.15f,
            anchor.x + span * 0.2f * dir, anchor.y + span * 0.05f,
            anchor.x, anchor.y)
    }
    drawPath(p, color, style = Fill)
}

private fun DrawScope.drawGothicArch(cx: Float, top: Float, bottom: Float, w: Float, color: Color, st: Stroke) {
    val p = Path().apply {
        moveTo(cx - w, bottom)
        lineTo(cx - w, top + w)
        quadraticBezierTo(cx, top - w * 0.5f, cx + w, top + w)
        lineTo(cx + w, bottom)
    }
    drawPath(p, color, style = st)
}

// ── Navigation Icons ────────────────────────────────────────────

private class GothicNavigationIcons : BaseNavigationIcons() {
    @Composable override fun Home(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = gothicStroke(s)
            val p = Path().apply {
                moveTo(s * 0.5f, s * 0.08f)
                lineTo(s * 0.12f, s * 0.5f); lineTo(s * 0.12f, s * 0.9f)
                lineTo(s * 0.88f, s * 0.9f); lineTo(s * 0.88f, s * 0.5f); close()
            }
            drawPath(p, tint, style = st)
            drawGothicCross(Offset(s * 0.5f, s * 0.08f), s * 0.06f, tint)
            drawGothicArch(s * 0.5f, s * 0.55f, s * 0.9f, s * 0.12f, tint, st)
        }
    }
    @Composable override fun Wishlist(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = gothicStroke(s)
            val heart = Path().apply {
                moveTo(s * 0.5f, s * 0.82f)
                cubicTo(s * 0.1f, s * 0.55f, s * 0.1f, s * 0.2f, s * 0.5f, s * 0.32f)
                cubicTo(s * 0.9f, s * 0.2f, s * 0.9f, s * 0.55f, s * 0.5f, s * 0.82f)
            }
            drawPath(heart, tint, style = st)
            drawGothicThorn(Offset(s * 0.15f, s * 0.35f), Offset(s * 0.5f, s * 0.82f), s * 0.05f, tint)
            drawGothicThorn(Offset(s * 0.85f, s * 0.35f), Offset(s * 0.5f, s * 0.82f), s * 0.05f, tint)
        }
    }
    @Composable override fun Outfit(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = gothicStroke(s)
            // Coffin-shaped wardrobe
            val coffin = Path().apply {
                moveTo(s * 0.35f, s * 0.08f); lineTo(s * 0.65f, s * 0.08f)
                lineTo(s * 0.72f, s * 0.3f); lineTo(s * 0.72f, s * 0.78f)
                lineTo(s * 0.6f, s * 0.92f); lineTo(s * 0.4f, s * 0.92f)
                lineTo(s * 0.28f, s * 0.78f); lineTo(s * 0.28f, s * 0.3f); close()
            }
            drawPath(coffin, tint, style = st)
            drawLine(tint, Offset(s * 0.5f, s * 0.3f), Offset(s * 0.5f, s * 0.78f),
                strokeWidth = s * 0.04f)
            drawGothicCross(Offset(s * 0.5f, s * 0.5f), s * 0.08f, tint)
        }
    }
    @Composable override fun Stats(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = gothicStroke(s)
            // Bar chart with cross tops
            val bars = listOf(0.2f to 0.6f, 0.4f to 0.35f, 0.6f to 0.5f, 0.8f to 0.25f)
            bars.forEach { (x, top) ->
                drawRect(tint, Offset(s * (x - 0.06f), s * top),
                    Size(s * 0.12f, s * (0.88f - top)), style = st)
                drawGothicCross(Offset(s * x, s * (top - 0.04f)), s * 0.03f, tint)
            }
            drawLine(tint, Offset(s * 0.1f, s * 0.88f), Offset(s * 0.9f, s * 0.88f),
                strokeWidth = s * 0.04f)
        }
    }
    @Composable override fun Settings(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = gothicStroke(s)
            val c = Offset(s * 0.5f, s * 0.5f)
            // Angular gear
            for (i in 0 until 8) {
                rotate(45f * i, c) {
                    drawRect(tint, Offset(c.x - s * 0.04f, c.y - s * 0.38f),
                        Size(s * 0.08f, s * 0.15f))
                }
            }
            drawCircle(tint, s * 0.2f, c, style = st)
            drawGothicBatWing(Offset(c.x - s * 0.08f, c.y), s * 0.18f, tint, flipX = true)
            drawGothicBatWing(Offset(c.x + s * 0.08f, c.y), s * 0.18f, tint)
        }
    }
}

// ── Action Icons ────────────────────────────────────────────────

private class GothicActionIcons : BaseActionIcons() {
    @Composable override fun Add(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val cx = s * 0.5f; val cy = s * 0.5f
            val ironCross = Path().apply {
                moveTo(cx - s * 0.06f, cy - s * 0.08f)
                lineTo(cx - s * 0.10f, cy - s * 0.38f)
                lineTo(cx + s * 0.10f, cy - s * 0.38f)
                lineTo(cx + s * 0.06f, cy - s * 0.08f)
                moveTo(cx + s * 0.08f, cy - s * 0.06f)
                lineTo(cx + s * 0.38f, cy - s * 0.10f)
                lineTo(cx + s * 0.38f, cy + s * 0.10f)
                lineTo(cx + s * 0.08f, cy + s * 0.06f)
                moveTo(cx + s * 0.06f, cy + s * 0.08f)
                lineTo(cx + s * 0.10f, cy + s * 0.38f)
                lineTo(cx - s * 0.10f, cy + s * 0.38f)
                lineTo(cx - s * 0.06f, cy + s * 0.08f)
                moveTo(cx - s * 0.08f, cy + s * 0.06f)
                lineTo(cx - s * 0.38f, cy + s * 0.10f)
                lineTo(cx - s * 0.38f, cy - s * 0.10f)
                lineTo(cx - s * 0.08f, cy - s * 0.06f)
            }
            drawPath(ironCross, tint, style = Fill)
            drawRect(tint, Offset(cx - s * 0.08f, cy - s * 0.08f), Size(s * 0.16f, s * 0.16f))
        }
    }
    @Composable override fun Delete(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = gothicStroke(s)
            val body = Path().apply {
                moveTo(s * 0.25f, s * 0.3f); lineTo(s * 0.3f, s * 0.88f)
                lineTo(s * 0.7f, s * 0.88f); lineTo(s * 0.75f, s * 0.3f)
            }
            drawPath(body, tint, style = st)
            drawLine(tint, Offset(s * 0.2f, s * 0.28f), Offset(s * 0.8f, s * 0.28f), strokeWidth = s * 0.05f)
            drawGothicCross(Offset(s * 0.5f, s * 0.2f), s * 0.06f, tint)
            drawLine(tint, Offset(s * 0.42f, s * 0.42f), Offset(s * 0.42f, s * 0.75f), strokeWidth = s * 0.04f)
            drawLine(tint, Offset(s * 0.58f, s * 0.42f), Offset(s * 0.58f, s * 0.75f), strokeWidth = s * 0.04f)
        }
    }
    @Composable override fun Edit(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = gothicStroke(s)
            // Quill pen
            val quill = Path().apply {
                moveTo(s * 0.78f, s * 0.1f); lineTo(s * 0.88f, s * 0.22f)
                lineTo(s * 0.32f, s * 0.78f); lineTo(s * 0.12f, s * 0.88f)
                lineTo(s * 0.22f, s * 0.68f); close()
            }
            drawPath(quill, tint, style = st)
            // Ink bottle
            drawRect(tint, Offset(s * 0.7f, s * 0.72f), Size(s * 0.18f, s * 0.18f), style = st)
            drawLine(tint, Offset(s * 0.75f, s * 0.72f), Offset(s * 0.82f, s * 0.65f), strokeWidth = s * 0.04f)
        }
    }
    @Composable override fun Search(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = gothicStroke(s)
            // Monocle shape
            drawCircle(tint, s * 0.22f, Offset(s * 0.42f, s * 0.42f), style = st)
            drawLine(tint, Offset(s * 0.58f, s * 0.58f), Offset(s * 0.85f, s * 0.85f), strokeWidth = s * 0.06f)
            // Chain from monocle
            drawLine(tint, Offset(s * 0.42f, s * 0.64f), Offset(s * 0.35f, s * 0.82f),
                strokeWidth = s * 0.03f, cap = StrokeCap.Round)
        }
    }
    @Composable override fun Sort(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            data class Bar(val y: Float, val endX: Float)
            val bars = listOf(
                Bar(s * 0.28f, s * 0.82f),
                Bar(s * 0.52f, s * 0.64f),
                Bar(s * 0.76f, s * 0.46f)
            )
            val startX = s * 0.16f
            bars.forEach { bar ->
                drawLine(tint, Offset(startX, bar.y), Offset(bar.endX, bar.y), strokeWidth = s * 0.05f)
                val spire = Path().apply {
                    moveTo(bar.endX - s * 0.04f, bar.y)
                    lineTo(bar.endX, bar.y - s * 0.10f)
                    lineTo(bar.endX + s * 0.04f, bar.y)
                }
                drawPath(spire, tint, style = Fill)
            }
            val wing = Path().apply {
                moveTo(startX, bars[0].y)
                lineTo(startX - s * 0.06f, bars[0].y - s * 0.08f)
                cubicTo(startX - s * 0.02f, bars[0].y - s * 0.04f,
                    startX - s * 0.02f, bars[0].y - s * 0.02f, startX, bars[0].y)
            }
            drawPath(wing, tint, style = Fill)
        }
    }
    @Composable override fun Save(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = gothicStroke(s)
            val check = Path().apply {
                moveTo(s * 0.18f, s * 0.5f); lineTo(s * 0.42f, s * 0.75f); lineTo(s * 0.82f, s * 0.25f)
            }
            drawPath(check, tint, style = st)
            drawGothicCross(Offset(s * 0.82f, s * 0.2f), s * 0.06f, tint.copy(alpha = 0.5f))
        }
    }
    @Composable override fun Close(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            drawLine(tint, Offset(s * 0.2f, s * 0.2f), Offset(s * 0.8f, s * 0.8f), strokeWidth = s * 0.06f)
            drawLine(tint, Offset(s * 0.8f, s * 0.2f), Offset(s * 0.2f, s * 0.8f), strokeWidth = s * 0.06f)
            // Thorn at each tip
            listOf(Offset(s*0.2f,s*0.2f), Offset(s*0.8f,s*0.2f), Offset(s*0.2f,s*0.8f), Offset(s*0.8f,s*0.8f)).forEach {
                drawCircle(tint, s * 0.025f, it)
            }
        }
    }
    @Composable override fun Share(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = gothicStroke(s)
            // Spider web pattern
            val c = Offset(s * 0.5f, s * 0.5f)
            val pts = listOf(
                Offset(s * 0.5f, s * 0.15f), Offset(s * 0.8f, s * 0.35f), Offset(s * 0.8f, s * 0.7f),
                Offset(s * 0.5f, s * 0.85f), Offset(s * 0.2f, s * 0.7f), Offset(s * 0.2f, s * 0.35f)
            )
            pts.forEach { drawLine(tint, c, it, strokeWidth = s * 0.03f) }
            // Outer ring
            val ring = Path().apply { moveTo(pts[0].x, pts[0].y); for (i in 1..5) lineTo(pts[i].x, pts[i].y); close() }
            drawPath(ring, tint, style = st)
        }
    }
    @Composable override fun FilterList(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val cx = s * 0.5f
            data class Bar(val y: Float, val halfW: Float)
            val bars = listOf(
                Bar(s * 0.22f, s * 0.38f),
                Bar(s * 0.50f, s * 0.24f),
                Bar(s * 0.78f, s * 0.10f)
            )
            bars.forEach { bar ->
                drawLine(tint, Offset(cx - bar.halfW, bar.y), Offset(cx + bar.halfW, bar.y),
                    strokeWidth = s * 0.05f)
                drawLine(tint, Offset(cx - bar.halfW, bar.y),
                    Offset(cx - bar.halfW - s * 0.04f, bar.y - s * 0.04f), strokeWidth = s * 0.03f)
                drawLine(tint, Offset(cx - bar.halfW, bar.y),
                    Offset(cx - bar.halfW - s * 0.04f, bar.y + s * 0.04f), strokeWidth = s * 0.03f)
                drawLine(tint, Offset(cx + bar.halfW, bar.y),
                    Offset(cx + bar.halfW + s * 0.04f, bar.y - s * 0.04f), strokeWidth = s * 0.03f)
                drawLine(tint, Offset(cx + bar.halfW, bar.y),
                    Offset(cx + bar.halfW + s * 0.04f, bar.y + s * 0.04f), strokeWidth = s * 0.03f)
            }
            for (i in 0 until bars.size - 1) {
                val x1 = cx - bars[i].halfW; val x2 = cx - bars[i + 1].halfW
                drawLine(tint, Offset(x1, bars[i].y), Offset(x2, bars[i + 1].y), strokeWidth = s * 0.03f)
                val x3 = cx + bars[i].halfW; val x4 = cx + bars[i + 1].halfW
                drawLine(tint, Offset(x3, bars[i].y), Offset(x4, bars[i + 1].y), strokeWidth = s * 0.03f)
            }
        }
    }
    @Composable override fun MoreVert(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            // Three diamonds
            listOf(0.22f, 0.5f, 0.78f).forEach { y ->
                val d = Path().apply {
                    moveTo(s * 0.5f, s * (y - 0.06f))
                    lineTo(s * 0.56f, s * y)
                    lineTo(s * 0.5f, s * (y + 0.06f))
                    lineTo(s * 0.44f, s * y); close()
                }
                drawPath(d, tint, style = Fill)
            }
        }
    }
    @Composable override fun ContentCopy(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = gothicStroke(s)
            drawRect(tint, Offset(s * 0.28f, s * 0.28f), Size(s * 0.55f, s * 0.62f), style = st)
            drawRect(tint, Offset(s * 0.15f, s * 0.1f), Size(s * 0.55f, s * 0.62f), style = st)
            drawGothicBatWing(Offset(s * 0.7f, s * 0.15f), s * 0.12f, tint.copy(alpha = 0.5f))
        }
    }
    @Composable override fun Refresh(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = gothicStroke(s)
            drawArc(tint, 30f, 280f, false, Offset(s * 0.15f, s * 0.15f),
                Size(s * 0.7f, s * 0.7f), style = st)
            val arrow = Path().apply {
                moveTo(s * 0.62f, s * 0.12f); lineTo(s * 0.78f, s * 0.25f); lineTo(s * 0.55f, s * 0.28f); close()
            }
            drawPath(arrow, tint, style = Fill)
            drawGothicThorn(Offset(s * 0.3f, s * 0.15f), Offset(s * 0.7f, s * 0.15f), s * 0.04f, tint.copy(alpha = 0.4f))
        }
    }
    @Composable override fun ViewAgenda(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val stroke = Stroke(s * 0.05f, join = StrokeJoin.Miter)
            drawRect(tint, Offset(s * 0.14f, s * 0.10f), Size(s * 0.72f, s * 0.32f), style = stroke)
            drawLine(tint, Offset(s * 0.50f, s * 0.16f), Offset(s * 0.50f, s * 0.34f), strokeWidth = s * 0.025f)
            drawLine(tint, Offset(s * 0.38f, s * 0.24f), Offset(s * 0.62f, s * 0.24f), strokeWidth = s * 0.025f)
            drawRect(tint, Offset(s * 0.14f, s * 0.58f), Size(s * 0.72f, s * 0.32f), style = stroke)
            drawLine(tint, Offset(s * 0.50f, s * 0.64f), Offset(s * 0.50f, s * 0.82f), strokeWidth = s * 0.025f)
            drawLine(tint, Offset(s * 0.38f, s * 0.72f), Offset(s * 0.62f, s * 0.72f), strokeWidth = s * 0.025f)
        }
    }
    @Composable override fun GridView(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val positions = listOf(
                Offset(s * 0.30f, s * 0.30f), Offset(s * 0.70f, s * 0.30f),
                Offset(s * 0.30f, s * 0.70f), Offset(s * 0.70f, s * 0.70f)
            )
            val r = s * 0.12f
            positions.forEach { c ->
                val diamond = Path().apply {
                    moveTo(c.x, c.y - r); lineTo(c.x + r, c.y)
                    lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close()
                }
                drawPath(diamond, tint, style = Stroke(s * 0.045f, join = StrokeJoin.Miter))
            }
            drawLine(tint, Offset(s * 0.30f, s * 0.42f), Offset(s * 0.30f, s * 0.58f), strokeWidth = s * 0.025f)
            drawLine(tint, Offset(s * 0.70f, s * 0.42f), Offset(s * 0.70f, s * 0.58f), strokeWidth = s * 0.025f)
            drawLine(tint, Offset(s * 0.42f, s * 0.30f), Offset(s * 0.58f, s * 0.30f), strokeWidth = s * 0.025f)
            drawLine(tint, Offset(s * 0.42f, s * 0.70f), Offset(s * 0.58f, s * 0.70f), strokeWidth = s * 0.025f)
        }
    }
    @Composable override fun Apps(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val gap = s / 4f
            val r = s * 0.06f
            for (row in 0..2) {
                for (col in 0..2) {
                    val cx = gap + col * gap; val cy = gap + row * gap
                    val diamond = Path().apply {
                        moveTo(cx, cy - r); lineTo(cx + r, cy)
                        lineTo(cx, cy + r); lineTo(cx - r, cy); close()
                    }
                    drawPath(diamond, tint, style = Fill)
                }
            }
        }
    }
}

// ── Content Icons ───────────────────────────────────────────────

private class GothicContentIcons : BaseContentIcons() {
    @Composable override fun Star(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            // Pentagram star
            val p = Path().apply {
                moveTo(s * 0.5f, s * 0.08f)
                lineTo(s * 0.62f, s * 0.38f); lineTo(s * 0.95f, s * 0.38f)
                lineTo(s * 0.68f, s * 0.58f); lineTo(s * 0.8f, s * 0.92f)
                lineTo(s * 0.5f, s * 0.72f); lineTo(s * 0.2f, s * 0.92f)
                lineTo(s * 0.32f, s * 0.58f); lineTo(s * 0.05f, s * 0.38f)
                lineTo(s * 0.38f, s * 0.38f); close()
            }
            drawPath(p, tint, style = Fill)
        }
    }
    @Composable override fun StarBorder(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = gothicStroke(s)
            val p = Path().apply {
                moveTo(s * 0.5f, s * 0.08f)
                lineTo(s * 0.62f, s * 0.38f); lineTo(s * 0.95f, s * 0.38f)
                lineTo(s * 0.68f, s * 0.58f); lineTo(s * 0.8f, s * 0.92f)
                lineTo(s * 0.5f, s * 0.72f); lineTo(s * 0.2f, s * 0.92f)
                lineTo(s * 0.32f, s * 0.58f); lineTo(s * 0.05f, s * 0.38f)
                lineTo(s * 0.38f, s * 0.38f); close()
            }
            drawPath(p, tint, style = st)
        }
    }
    @Composable override fun Image(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = gothicStroke(s)
            drawRect(tint, Offset(s * 0.1f, s * 0.15f), Size(s * 0.8f, s * 0.7f), style = st)
            val mt = Path().apply {
                moveTo(s * 0.15f, s * 0.72f); lineTo(s * 0.35f, s * 0.45f)
                lineTo(s * 0.5f, s * 0.58f); lineTo(s * 0.7f, s * 0.35f); lineTo(s * 0.88f, s * 0.72f)
            }
            drawPath(mt, tint, style = st)
            drawGothicCross(Offset(s * 0.28f, s * 0.32f), s * 0.05f, tint)
        }
    }
    @Composable override fun Camera(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = gothicStroke(s)
            drawRect(tint, Offset(s * 0.08f, s * 0.28f), Size(s * 0.84f, s * 0.55f), style = st)
            val bump = Path().apply {
                moveTo(s * 0.35f, s * 0.28f); lineTo(s * 0.4f, s * 0.15f)
                lineTo(s * 0.6f, s * 0.15f); lineTo(s * 0.65f, s * 0.28f)
            }
            drawPath(bump, tint, style = st)
            drawCircle(tint, s * 0.14f, Offset(s * 0.5f, s * 0.55f), style = st)
            drawGothicBatWing(Offset(s * 0.36f, s * 0.55f), s * 0.12f, tint.copy(alpha = 0.4f), flipX = true)
            drawGothicBatWing(Offset(s * 0.64f, s * 0.55f), s * 0.12f, tint.copy(alpha = 0.4f))
        }
    }
    @Composable override fun AddPhoto(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = gothicStroke(s)
            drawRect(tint, Offset(s * 0.05f, s * 0.2f), Size(s * 0.62f, s * 0.6f), style = st)
            drawLine(tint, Offset(s * 0.8f, s * 0.32f), Offset(s * 0.8f, s * 0.68f), strokeWidth = s * 0.05f)
            drawLine(tint, Offset(s * 0.62f, s * 0.5f), Offset(s * 0.95f, s * 0.5f), strokeWidth = s * 0.05f)
        }
    }
    @Composable override fun Link(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = gothicStroke(s)
            // Angular chain links
            val link1 = Path().apply {
                moveTo(s * 0.15f, s * 0.35f); lineTo(s * 0.15f, s * 0.65f)
                lineTo(s * 0.45f, s * 0.65f); lineTo(s * 0.45f, s * 0.35f); close()
            }
            val link2 = Path().apply {
                moveTo(s * 0.55f, s * 0.35f); lineTo(s * 0.55f, s * 0.65f)
                lineTo(s * 0.85f, s * 0.65f); lineTo(s * 0.85f, s * 0.35f); close()
            }
            drawPath(link1, tint, style = st); drawPath(link2, tint, style = st)
            drawLine(tint, Offset(s * 0.42f, s * 0.5f), Offset(s * 0.58f, s * 0.5f), strokeWidth = s * 0.05f)
        }
    }
    @Composable override fun LinkOff(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = gothicStroke(s)
            val link1 = Path().apply {
                moveTo(s * 0.1f, s * 0.35f); lineTo(s * 0.1f, s * 0.65f)
                lineTo(s * 0.4f, s * 0.65f); lineTo(s * 0.4f, s * 0.35f); close()
            }
            val link2 = Path().apply {
                moveTo(s * 0.6f, s * 0.35f); lineTo(s * 0.6f, s * 0.65f)
                lineTo(s * 0.9f, s * 0.65f); lineTo(s * 0.9f, s * 0.35f); close()
            }
            drawPath(link1, tint, style = st); drawPath(link2, tint, style = st)
            drawLine(tint, Offset(s * 0.2f, s * 0.82f), Offset(s * 0.8f, s * 0.18f), strokeWidth = s * 0.05f)
        }
    }
    @Composable override fun Palette(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = gothicStroke(s)
            val pal = Path().apply {
                moveTo(s * 0.5f, s * 0.1f)
                cubicTo(s * 0.15f, s * 0.1f, s * 0.05f, s * 0.5f, s * 0.25f, s * 0.8f)
                cubicTo(s * 0.35f, s * 0.92f, s * 0.55f, s * 0.85f, s * 0.5f, s * 0.7f)
                cubicTo(s * 0.45f, s * 0.55f, s * 0.65f, s * 0.5f, s * 0.7f, s * 0.6f)
                cubicTo(s * 0.85f, s * 0.75f, s * 0.95f, s * 0.4f, s * 0.5f, s * 0.1f)
            }
            drawPath(pal, tint, style = st)
            drawGothicCross(Offset(s * 0.32f, s * 0.38f), s * 0.04f, tint)
            drawGothicCross(Offset(s * 0.48f, s * 0.28f), s * 0.04f, tint)
            drawGothicCross(Offset(s * 0.62f, s * 0.35f), s * 0.04f, tint)
        }
    }
    @Composable override fun FileOpen(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = gothicStroke(s)
            // Tome / book
            val book = Path().apply {
                moveTo(s * 0.15f, s * 0.12f); lineTo(s * 0.15f, s * 0.88f)
                lineTo(s * 0.85f, s * 0.88f); lineTo(s * 0.85f, s * 0.12f); close()
            }
            drawPath(book, tint, style = st)
            drawLine(tint, Offset(s * 0.15f, s * 0.8f), Offset(s * 0.85f, s * 0.8f), strokeWidth = s * 0.04f)
            drawGothicCross(Offset(s * 0.5f, s * 0.45f), s * 0.1f, tint)
        }
    }
    @Composable override fun CalendarMonth(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = gothicStroke(s)
            drawRect(tint, Offset(s * 0.12f, s * 0.2f), Size(s * 0.76f, s * 0.68f), style = st)
            drawLine(tint, Offset(s * 0.12f, s * 0.38f), Offset(s * 0.88f, s * 0.38f), strokeWidth = s * 0.04f)
            drawLine(tint, Offset(s * 0.35f, s * 0.12f), Offset(s * 0.35f, s * 0.28f), strokeWidth = s * 0.05f)
            drawLine(tint, Offset(s * 0.65f, s * 0.12f), Offset(s * 0.65f, s * 0.28f), strokeWidth = s * 0.05f)
            // Crescent moon
            val moon = Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(s * 0.4f, s * 0.48f, s * 0.6f, s * 0.78f))
            }
            drawPath(moon, tint, style = st)
        }
    }
    @Composable override fun Notifications(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = gothicStroke(s)
            val bell = Path().apply {
                moveTo(s * 0.25f, s * 0.62f)
                cubicTo(s * 0.25f, s * 0.3f, s * 0.35f, s * 0.15f, s * 0.5f, s * 0.15f)
                cubicTo(s * 0.65f, s * 0.15f, s * 0.75f, s * 0.3f, s * 0.75f, s * 0.62f)
                lineTo(s * 0.82f, s * 0.72f); lineTo(s * 0.18f, s * 0.72f); close()
            }
            drawPath(bell, tint, style = st)
            drawLine(tint, Offset(s * 0.5f, s * 0.08f), Offset(s * 0.5f, s * 0.15f), strokeWidth = s * 0.04f)
            drawArc(tint, 0f, 180f, false, Offset(s * 0.4f, s * 0.75f), Size(s * 0.2f, s * 0.15f), style = st)
            drawGothicBatWing(Offset(s * 0.5f, s * 0.08f), s * 0.1f, tint.copy(alpha = 0.5f))
            drawGothicBatWing(Offset(s * 0.5f, s * 0.08f), s * 0.1f, tint.copy(alpha = 0.5f), flipX = true)
        }
    }
    @Composable override fun AttachMoney(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = gothicStroke(s)
            drawLine(tint, Offset(s * 0.5f, s * 0.08f), Offset(s * 0.5f, s * 0.92f), strokeWidth = s * 0.05f)
            val dollar = Path().apply {
                moveTo(s * 0.68f, s * 0.28f)
                cubicTo(s * 0.65f, s * 0.18f, s * 0.35f, s * 0.18f, s * 0.32f, s * 0.33f)
                cubicTo(s * 0.3f, s * 0.46f, s * 0.7f, s * 0.54f, s * 0.68f, s * 0.67f)
                cubicTo(s * 0.65f, s * 0.82f, s * 0.35f, s * 0.82f, s * 0.32f, s * 0.72f)
            }
            drawPath(dollar, tint, style = st)
        }
    }
    @Composable override fun Category(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = gothicStroke(s)
            val gap = s * 0.06f; val cell = (s - gap * 3) / 2
            val positions = listOf(
                Offset(gap, gap), Offset(gap * 2 + cell, gap),
                Offset(gap, gap * 2 + cell), Offset(gap * 2 + cell, gap * 2 + cell)
            )
            positions.forEach { pos ->
                drawRect(tint, pos, Size(cell, cell), style = st)
            }
            // Diamond in each cell
            positions.forEach { pos ->
                val cx = pos.x + cell / 2; val cy = pos.y + cell / 2; val r = cell * 0.2f
                val d = Path().apply {
                    moveTo(cx, cy - r); lineTo(cx + r, cy); lineTo(cx, cy + r); lineTo(cx - r, cy); close()
                }
                drawPath(d, tint, style = Fill)
            }
        }
    }
}

// ── Arrow Icons ─────────────────────────────────────────────────

private class GothicArrowIcons : BaseArrowIcons() {
    @Composable override fun ArrowBack(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val blade = Path().apply {
                moveTo(s * 0.18f, s * 0.50f)
                lineTo(s * 0.42f, s * 0.35f)
                lineTo(s * 0.78f, s * 0.40f)
                lineTo(s * 0.78f, s * 0.60f)
                lineTo(s * 0.42f, s * 0.65f)
                close()
            }
            drawPath(blade, tint, style = Stroke(s * 0.05f, join = StrokeJoin.Miter))
            drawLine(tint, Offset(s * 0.55f, s * 0.38f), Offset(s * 0.52f, s * 0.30f), strokeWidth = s * 0.03f)
            drawLine(tint, Offset(s * 0.65f, s * 0.39f), Offset(s * 0.62f, s * 0.31f), strokeWidth = s * 0.03f)
            drawLine(tint, Offset(s * 0.55f, s * 0.62f), Offset(s * 0.52f, s * 0.70f), strokeWidth = s * 0.03f)
            drawLine(tint, Offset(s * 0.65f, s * 0.61f), Offset(s * 0.62f, s * 0.69f), strokeWidth = s * 0.03f)
        }
    }
    @Composable override fun ArrowForward(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val arr = Path().apply {
                moveTo(s * 0.3f, s * 0.18f); lineTo(s * 0.75f, s * 0.5f); lineTo(s * 0.3f, s * 0.82f)
            }
            drawPath(arr, tint, style = gothicStroke(s))
        }
    }
    @Composable override fun KeyboardArrowLeft(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val arr = Path().apply {
                moveTo(s * 0.65f, s * 0.2f); lineTo(s * 0.3f, s * 0.5f); lineTo(s * 0.65f, s * 0.8f)
            }
            drawPath(arr, tint, style = gothicStroke(s))
        }
    }
    @Composable override fun KeyboardArrowRight(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val arr = Path().apply {
                moveTo(s * 0.35f, s * 0.2f); lineTo(s * 0.7f, s * 0.5f); lineTo(s * 0.35f, s * 0.8f)
            }
            drawPath(arr, tint, style = gothicStroke(s))
        }
    }
    @Composable override fun ExpandMore(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val arr = Path().apply {
                moveTo(s * 0.2f, s * 0.32f); lineTo(s * 0.5f, s * 0.68f); lineTo(s * 0.8f, s * 0.32f)
            }
            drawPath(arr, tint, style = gothicStroke(s))
        }
    }
    @Composable override fun ExpandLess(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val arr = Path().apply {
                moveTo(s * 0.2f, s * 0.68f); lineTo(s * 0.5f, s * 0.32f); lineTo(s * 0.8f, s * 0.68f)
            }
            drawPath(arr, tint, style = gothicStroke(s))
        }
    }
    @Composable override fun ArrowDropDown(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            // Diamond-shaped drop
            val d = Path().apply {
                moveTo(s * 0.5f, s * 0.72f); lineTo(s * 0.25f, s * 0.35f)
                lineTo(s * 0.5f, s * 0.42f); lineTo(s * 0.75f, s * 0.35f); close()
            }
            drawPath(d, tint, style = Fill)
        }
    }
    @Composable override fun SwapVert(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = gothicStroke(s)
            val up = Path().apply {
                moveTo(s * 0.35f, s * 0.78f); lineTo(s * 0.35f, s * 0.28f)
                moveTo(s * 0.2f, s * 0.42f); lineTo(s * 0.35f, s * 0.22f); lineTo(s * 0.5f, s * 0.42f)
            }
            drawPath(up, tint, style = st)
            val dn = Path().apply {
                moveTo(s * 0.65f, s * 0.22f); lineTo(s * 0.65f, s * 0.72f)
                moveTo(s * 0.5f, s * 0.58f); lineTo(s * 0.65f, s * 0.78f); lineTo(s * 0.8f, s * 0.58f)
            }
            drawPath(dn, tint, style = st)
            drawGothicCross(Offset(s * 0.5f, s * 0.5f), s * 0.04f, tint.copy(alpha = 0.4f))
        }
    }
    @Composable override fun OpenInNew(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = gothicStroke(s)
            val box = Path().apply {
                moveTo(s * 0.55f, s * 0.15f); lineTo(s * 0.15f, s * 0.15f)
                lineTo(s * 0.15f, s * 0.85f); lineTo(s * 0.85f, s * 0.85f)
                lineTo(s * 0.85f, s * 0.45f)
            }
            drawPath(box, tint, style = st)
            drawLine(tint, Offset(s * 0.48f, s * 0.52f), Offset(s * 0.85f, s * 0.15f), strokeWidth = s * 0.05f)
            // Dagger tip
            val tip = Path().apply {
                moveTo(s * 0.85f, s * 0.15f); lineTo(s * 0.72f, s * 0.15f)
                moveTo(s * 0.85f, s * 0.15f); lineTo(s * 0.85f, s * 0.28f)
            }
            drawPath(tip, tint, style = st)
        }
    }
}

// ── Status Icons ────────────────────────────────────────────────

private class GothicStatusIcons : BaseStatusIcons() {
    @Composable override fun CheckCircle(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = gothicStroke(s)
            // Pentagram circle
            drawCircle(tint, s * 0.38f, Offset(s * 0.5f, s * 0.5f), style = st)
            val check = Path().apply {
                moveTo(s * 0.28f, s * 0.5f); lineTo(s * 0.44f, s * 0.68f); lineTo(s * 0.72f, s * 0.32f)
            }
            drawPath(check, tint, style = st)
        }
    }
    @Composable override fun Warning(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = gothicStroke(s)
            val tri = Path().apply {
                moveTo(s * 0.5f, s * 0.1f); lineTo(s * 0.08f, s * 0.9f)
                lineTo(s * 0.92f, s * 0.9f); close()
            }
            drawPath(tri, tint, style = st)
            drawLine(tint, Offset(s * 0.5f, s * 0.38f), Offset(s * 0.5f, s * 0.62f), strokeWidth = s * 0.06f)
            drawGothicCross(Offset(s * 0.5f, s * 0.76f), s * 0.05f, tint)
        }
    }
    @Composable override fun Error(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = gothicStroke(s)
            drawCircle(tint, s * 0.38f, Offset(s * 0.5f, s * 0.5f), style = st)
            drawLine(tint, Offset(s * 0.33f, s * 0.33f), Offset(s * 0.67f, s * 0.67f), strokeWidth = s * 0.06f)
            drawLine(tint, Offset(s * 0.67f, s * 0.33f), Offset(s * 0.33f, s * 0.67f), strokeWidth = s * 0.06f)
            drawGothicThorn(Offset(s * 0.15f, s * 0.5f), Offset(s * 0.85f, s * 0.5f), s * 0.03f, tint.copy(alpha = 0.3f))
        }
    }
    @Composable override fun Info(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = gothicStroke(s)
            // Diamond shape instead of circle
            val diamond = Path().apply {
                moveTo(s * 0.5f, s * 0.08f); lineTo(s * 0.92f, s * 0.5f)
                lineTo(s * 0.5f, s * 0.92f); lineTo(s * 0.08f, s * 0.5f); close()
            }
            drawPath(diamond, tint, style = st)
            drawCircle(tint, s * 0.04f, Offset(s * 0.5f, s * 0.32f))
            drawLine(tint, Offset(s * 0.5f, s * 0.45f), Offset(s * 0.5f, s * 0.7f), strokeWidth = s * 0.06f)
        }
    }
    @Composable override fun Visibility(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = gothicStroke(s)
            val eye = Path().apply {
                moveTo(s * 0.05f, s * 0.5f)
                cubicTo(s * 0.2f, s * 0.22f, s * 0.8f, s * 0.22f, s * 0.95f, s * 0.5f)
                cubicTo(s * 0.8f, s * 0.78f, s * 0.2f, s * 0.78f, s * 0.05f, s * 0.5f)
            }
            drawPath(eye, tint, style = st)
            // Slit pupil
            drawLine(tint, Offset(s * 0.5f, s * 0.35f), Offset(s * 0.5f, s * 0.65f), strokeWidth = s * 0.05f)
            drawCircle(tint, s * 0.12f, Offset(s * 0.5f, s * 0.5f), style = st)
        }
    }
    @Composable override fun VisibilityOff(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = gothicStroke(s)
            val eye = Path().apply {
                moveTo(s * 0.05f, s * 0.5f)
                cubicTo(s * 0.2f, s * 0.22f, s * 0.8f, s * 0.22f, s * 0.95f, s * 0.5f)
                cubicTo(s * 0.8f, s * 0.78f, s * 0.2f, s * 0.78f, s * 0.05f, s * 0.5f)
            }
            drawPath(eye, tint, style = st)
            drawGothicCross(Offset(s * 0.5f, s * 0.5f), s * 0.06f, tint.copy(alpha = 0.5f))
            drawLine(tint, Offset(s * 0.15f, s * 0.85f), Offset(s * 0.85f, s * 0.15f), strokeWidth = s * 0.06f)
        }
    }
}

// ── Provider ────────────────────────────────────────────────────

class GothicIconProvider : BaseSkinIconProvider() {
    override val navigation: NavigationIcons = GothicNavigationIcons()
    override val action: ActionIcons = GothicActionIcons()
    override val content: ContentIcons = GothicContentIcons()
    override val arrow: ArrowIcons = GothicArrowIcons()
    override val status: StatusIcons = GothicStatusIcons()
}
