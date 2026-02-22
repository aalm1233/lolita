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

private fun brushStroke(s: Float) = Stroke(
    width = s * 0.07f, cap = StrokeCap.Round, join = StrokeJoin.Round
)

private fun thinBrush(s: Float) = Stroke(
    width = s * 0.04f, cap = StrokeCap.Round, join = StrokeJoin.Round
)

private fun DrawScope.drawChineseCloud(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x - r, c.y)
        cubicTo(c.x - r, c.y - r * 0.8f, c.x - r * 0.3f, c.y - r * 1.1f, c.x, c.y - r * 0.6f)
        cubicTo(c.x + r * 0.3f, c.y - r * 1.1f, c.x + r, c.y - r * 0.8f, c.x + r, c.y)
        cubicTo(c.x + r * 0.6f, c.y + r * 0.3f, c.x - r * 0.6f, c.y + r * 0.3f, c.x - r, c.y)
    }
    drawPath(p, color, style = Fill)
}

private fun DrawScope.drawPlumBlossom(c: Offset, r: Float, color: Color) {
    for (i in 0 until 5) {
        rotate(72f * i, c) {
            val petal = Path().apply {
                moveTo(c.x, c.y - r)
                quadraticBezierTo(c.x + r * 0.45f, c.y - r * 0.35f, c.x, c.y)
                quadraticBezierTo(c.x - r * 0.45f, c.y - r * 0.35f, c.x, c.y - r)
            }
            drawPath(petal, color, style = Fill)
        }
    }
    drawCircle(color.copy(alpha = 0.8f), r * 0.15f, c)
}

private fun DrawScope.drawInkSplash(c: Offset, r: Float, color: Color) {
    drawCircle(color.copy(alpha = 0.3f), r, c)
    drawCircle(color.copy(alpha = 0.5f), r * 0.5f, c)
    drawCircle(color.copy(alpha = 0.15f), r * 0.3f, Offset(c.x + r * 0.6f, c.y - r * 0.4f))
}

private fun DrawScope.drawSealStamp(c: Offset, r: Float, color: Color) {
    drawRect(color.copy(alpha = 0.7f), Offset(c.x - r, c.y - r), Size(r * 2, r * 2),
        style = Stroke(width = r * 0.2f, cap = StrokeCap.Butt))
}

// ── Navigation Icons ────────────────────────────────────────────

private class ChineseNavigationIcons : BaseNavigationIcons() {
    @Composable override fun Home(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = brushStroke(s)
            // Chinese pavilion with curved eaves
            val roof = Path().apply {
                moveTo(s * 0.5f, s * 0.08f)
                cubicTo(s * 0.3f, s * 0.15f, s * 0.1f, s * 0.32f, s * 0.05f, s * 0.38f)
                moveTo(s * 0.5f, s * 0.08f)
                cubicTo(s * 0.7f, s * 0.15f, s * 0.9f, s * 0.32f, s * 0.95f, s * 0.38f)
            }
            drawPath(roof, tint, style = st)
            // Pillars
            drawLine(tint, Offset(s * 0.28f, s * 0.38f), Offset(s * 0.28f, s * 0.88f),
                strokeWidth = s * 0.05f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.72f, s * 0.38f), Offset(s * 0.72f, s * 0.88f),
                strokeWidth = s * 0.05f, cap = StrokeCap.Round)
            // Base
            drawLine(tint, Offset(s * 0.18f, s * 0.88f), Offset(s * 0.82f, s * 0.88f),
                strokeWidth = s * 0.06f, cap = StrokeCap.Round)
            drawChineseCloud(Offset(s * 0.82f, s * 0.2f), s * 0.08f, tint.copy(alpha = 0.4f))
        }
    }
    @Composable override fun Wishlist(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = brushStroke(s)
            // Ink-brush heart
            val heart = Path().apply {
                moveTo(s * 0.5f, s * 0.82f)
                cubicTo(s * 0.12f, s * 0.55f, s * 0.12f, s * 0.2f, s * 0.5f, s * 0.32f)
                cubicTo(s * 0.88f, s * 0.2f, s * 0.88f, s * 0.55f, s * 0.5f, s * 0.82f)
            }
            drawPath(heart, tint, style = st)
            drawPlumBlossom(Offset(s * 0.5f, s * 0.22f), s * 0.08f, tint.copy(alpha = 0.6f))
        }
    }
    @Composable override fun Outfit(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = brushStroke(s)
            // Hanfu cross-collar silhouette
            val hanfu = Path().apply {
                moveTo(s * 0.35f, s * 0.1f); lineTo(s * 0.25f, s * 0.1f)
                lineTo(s * 0.15f, s * 0.35f); lineTo(s * 0.2f, s * 0.88f)
                lineTo(s * 0.8f, s * 0.88f); lineTo(s * 0.85f, s * 0.35f)
                lineTo(s * 0.75f, s * 0.1f); lineTo(s * 0.65f, s * 0.1f)
                // Cross collar
                lineTo(s * 0.5f, s * 0.35f); lineTo(s * 0.35f, s * 0.1f)
            }
            drawPath(hanfu, tint, style = st)
            // Tassel
            drawLine(tint, Offset(s * 0.5f, s * 0.35f), Offset(s * 0.5f, s * 0.55f),
                strokeWidth = s * 0.03f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.45f, s * 0.55f), Offset(s * 0.55f, s * 0.55f),
                strokeWidth = s * 0.03f, cap = StrokeCap.Round)
        }
    }
    @Composable override fun Stats(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = brushStroke(s)
            // Scroll with chart
            drawRoundRect(tint, Offset(s * 0.12f, s * 0.15f), Size(s * 0.76f, s * 0.7f),
                cornerRadius = CornerRadius(s * 0.04f), style = st)
            // Scroll rolls
            drawCircle(tint, s * 0.06f, Offset(s * 0.12f, s * 0.15f), style = st)
            drawCircle(tint, s * 0.06f, Offset(s * 0.88f, s * 0.15f), style = st)
            // Ink bars
            val bars = listOf(0.28f to 0.55f, 0.42f to 0.35f, 0.56f to 0.48f, 0.7f to 0.3f)
            bars.forEach { (x, top) ->
                drawLine(tint, Offset(s * x, s * 0.75f), Offset(s * x, s * top),
                    strokeWidth = s * 0.06f, cap = StrokeCap.Round)
            }
        }
    }
    @Composable override fun Settings(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = brushStroke(s)
            val c = Offset(s * 0.5f, s * 0.5f)
            // Bagua / Taiji inspired
            drawCircle(tint, s * 0.35f, c, style = st)
            // Yin-yang S curve
            val curve = Path().apply {
                moveTo(s * 0.5f, s * 0.15f)
                cubicTo(s * 0.7f, s * 0.3f, s * 0.3f, s * 0.7f, s * 0.5f, s * 0.85f)
            }
            drawPath(curve, tint, style = st)
            drawCircle(tint, s * 0.05f, Offset(s * 0.5f, s * 0.32f))
            drawCircle(tint, s * 0.05f, Offset(s * 0.5f, s * 0.68f), style = st)
            drawChineseCloud(Offset(s * 0.85f, s * 0.15f), s * 0.07f, tint.copy(alpha = 0.4f))
        }
    }
}

// ── Action Icons ────────────────────────────────────────────────

private class ChineseActionIcons : BaseActionIcons() {
    @Composable override fun Add(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val cx = s * 0.5f; val cy = s * 0.5f
            val thick = s * 0.11f
            val vStroke = Path().apply {
                moveTo(cx - thick * 0.5f, s * 0.14f)
                lineTo(cx + thick * 0.5f, s * 0.14f)
                lineTo(cx + thick * 0.45f, s * 0.86f)
                lineTo(cx - thick * 0.45f, s * 0.86f)
                close()
            }
            drawPath(vStroke, tint, style = Fill)
            val hStroke = Path().apply {
                moveTo(s * 0.14f, cy - thick * 0.5f)
                lineTo(s * 0.86f, cy - thick * 0.5f)
                lineTo(s * 0.86f, cy + thick * 0.45f)
                lineTo(s * 0.14f, cy + thick * 0.45f)
                close()
            }
            drawPath(hStroke, tint, style = Fill)
            drawInkSplash(Offset(s * 0.82f, s * 0.18f), s * 0.035f, tint)
        }
    }
    @Composable override fun Delete(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = brushStroke(s)
            // Incense burner / furnace
            val body = Path().apply {
                moveTo(s * 0.25f, s * 0.35f)
                lineTo(s * 0.22f, s * 0.78f)
                quadraticBezierTo(s * 0.22f, s * 0.88f, s * 0.32f, s * 0.88f)
                lineTo(s * 0.68f, s * 0.88f)
                quadraticBezierTo(s * 0.78f, s * 0.88f, s * 0.78f, s * 0.78f)
                lineTo(s * 0.75f, s * 0.35f)
            }
            drawPath(body, tint, style = st)
            drawLine(tint, Offset(s * 0.2f, s * 0.33f), Offset(s * 0.8f, s * 0.33f),
                strokeWidth = s * 0.06f, cap = StrokeCap.Round)
            // Smoke wisps
            val smoke = Path().apply {
                moveTo(s * 0.5f, s * 0.3f)
                cubicTo(s * 0.45f, s * 0.2f, s * 0.55f, s * 0.15f, s * 0.5f, s * 0.08f)
            }
            drawPath(smoke, tint.copy(alpha = 0.4f), style = thinBrush(s))
        }
    }
    @Composable override fun Edit(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = brushStroke(s)
            // Calligraphy brush
            val brush = Path().apply {
                moveTo(s * 0.7f, s * 0.12f); lineTo(s * 0.82f, s * 0.25f)
                lineTo(s * 0.3f, s * 0.78f); lineTo(s * 0.12f, s * 0.88f)
                lineTo(s * 0.22f, s * 0.68f); close()
            }
            drawPath(brush, tint, style = st)
            // Inkstone
            drawOval(tint, Offset(s * 0.62f, s * 0.72f), Size(s * 0.28f, s * 0.18f), style = st)
        }
    }
    @Composable override fun Search(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = brushStroke(s)
            // Lantern-shaped magnifier
            drawCircle(tint, s * 0.22f, Offset(s * 0.42f, s * 0.42f), style = st)
            drawLine(tint, Offset(s * 0.58f, s * 0.58f), Offset(s * 0.82f, s * 0.82f),
                strokeWidth = s * 0.08f, cap = StrokeCap.Round)
            // Tassel on handle
            drawLine(tint, Offset(s * 0.82f, s * 0.82f), Offset(s * 0.85f, s * 0.92f),
                strokeWidth = s * 0.03f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.82f, s * 0.82f), Offset(s * 0.78f, s * 0.92f),
                strokeWidth = s * 0.03f, cap = StrokeCap.Round)
        }
    }
    @Composable override fun Sort(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            data class Mountain(val y: Float, val startX: Float, val endX: Float, val alpha: Float, val thick: Float)
            val mountains = listOf(
                Mountain(s * 0.28f, s * 0.10f, s * 0.90f, 0.4f, s * 0.06f),
                Mountain(s * 0.52f, s * 0.15f, s * 0.70f, 0.7f, s * 0.07f),
                Mountain(s * 0.76f, s * 0.20f, s * 0.50f, 1.0f, s * 0.08f)
            )
            mountains.forEach { m ->
                val w = m.endX - m.startX
                val peak = Path().apply {
                    moveTo(m.startX, m.y)
                    cubicTo(m.startX + w * 0.25f, m.y - s * 0.06f,
                        m.startX + w * 0.40f, m.y - s * 0.08f,
                        m.startX + w * 0.50f, m.y - s * 0.03f)
                    cubicTo(m.startX + w * 0.65f, m.y + s * 0.02f,
                        m.startX + w * 0.80f, m.y - s * 0.05f,
                        m.endX, m.y)
                }
                drawPath(peak, tint.copy(alpha = m.alpha),
                    style = Stroke(m.thick, cap = StrokeCap.Round))
            }
        }
    }
    @Composable override fun Save(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = brushStroke(s)
            // Brush stroke checkmark
            val check = Path().apply {
                moveTo(s * 0.18f, s * 0.5f); lineTo(s * 0.42f, s * 0.75f); lineTo(s * 0.82f, s * 0.25f)
            }
            drawPath(check, tint, style = st)
            drawSealStamp(Offset(s * 0.78f, s * 0.78f), s * 0.08f, tint)
        }
    }
    @Composable override fun Close(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            // Brush stroke X
            drawLine(tint, Offset(s * 0.2f, s * 0.2f), Offset(s * 0.8f, s * 0.8f),
                strokeWidth = s * 0.08f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.8f, s * 0.2f), Offset(s * 0.2f, s * 0.8f),
                strokeWidth = s * 0.08f, cap = StrokeCap.Round)
            drawInkSplash(Offset(s * 0.5f, s * 0.5f), s * 0.06f, tint)
        }
    }
    @Composable override fun Share(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = brushStroke(s)
            // Flying dove / messenger bird silhouette
            val bird = Path().apply {
                moveTo(s * 0.15f, s * 0.5f)
                cubicTo(s * 0.3f, s * 0.25f, s * 0.5f, s * 0.2f, s * 0.7f, s * 0.3f)
                cubicTo(s * 0.6f, s * 0.35f, s * 0.55f, s * 0.4f, s * 0.7f, s * 0.3f)
                cubicTo(s * 0.8f, s * 0.25f, s * 0.88f, s * 0.3f, s * 0.85f, s * 0.4f)
            }
            drawPath(bird, tint, style = st)
            // Wing
            val wing = Path().apply {
                moveTo(s * 0.45f, s * 0.35f)
                cubicTo(s * 0.35f, s * 0.15f, s * 0.55f, s * 0.1f, s * 0.65f, s * 0.25f)
            }
            drawPath(wing, tint, style = thinBrush(s))
            drawChineseCloud(Offset(s * 0.3f, s * 0.72f), s * 0.1f, tint.copy(alpha = 0.3f))
        }
    }
    @Composable override fun FilterList(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val pivotX = s * 0.50f; val pivotY = s * 0.88f
            data class Rib(val radius: Float, val thick: Float, val sweep: Float)
            val ribs = listOf(
                Rib(s * 0.62f, s * 0.07f, 100f),
                Rib(s * 0.44f, s * 0.06f, 80f),
                Rib(s * 0.26f, s * 0.05f, 60f)
            )
            ribs.forEach { rib ->
                val rect = Rect(
                    pivotX - rib.radius, pivotY - rib.radius,
                    pivotX + rib.radius, pivotY + rib.radius
                )
                drawArc(tint, startAngle = -90f - rib.sweep / 2, sweepAngle = rib.sweep,
                    useCenter = false, topLeft = rect.topLeft, size = rect.size,
                    style = Stroke(rib.thick, cap = StrokeCap.Round))
            }
            drawCircle(tint, radius = s * 0.04f, center = Offset(pivotX, pivotY))
        }
    }
    @Composable override fun MoreVert(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            // Three ink dots
            drawInkSplash(Offset(s * 0.5f, s * 0.2f), s * 0.06f, tint)
            drawInkSplash(Offset(s * 0.5f, s * 0.5f), s * 0.06f, tint)
            drawInkSplash(Offset(s * 0.5f, s * 0.8f), s * 0.06f, tint)
        }
    }
    @Composable override fun ContentCopy(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = brushStroke(s)
            // Two rice paper sheets
            drawRoundRect(tint, Offset(s * 0.25f, s * 0.25f), Size(s * 0.55f, s * 0.65f),
                cornerRadius = CornerRadius(s * 0.03f), style = st)
            drawRoundRect(tint, Offset(s * 0.15f, s * 0.1f), Size(s * 0.55f, s * 0.65f),
                cornerRadius = CornerRadius(s * 0.03f), style = st)
            drawSealStamp(Offset(s * 0.7f, s * 0.18f), s * 0.06f, tint.copy(alpha = 0.5f))
        }
    }
    @Composable override fun Refresh(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = brushStroke(s)
            // Taiji rotation
            drawArc(tint, 40f, 270f, false, Offset(s * 0.15f, s * 0.15f),
                Size(s * 0.7f, s * 0.7f), style = st)
            val arrow = Path().apply {
                moveTo(s * 0.62f, s * 0.12f); lineTo(s * 0.78f, s * 0.25f); lineTo(s * 0.55f, s * 0.28f); close()
            }
            drawPath(arrow, tint, style = Fill)
            drawChineseCloud(Offset(s * 0.5f, s * 0.5f), s * 0.08f, tint.copy(alpha = 0.3f))
        }
    }
    @Composable override fun ViewAgenda(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val stroke = brushStroke(s)
            drawRoundRect(tint, Offset(s * 0.20f, s * 0.12f), Size(s * 0.60f, s * 0.30f),
                cornerRadius = CornerRadius(s * 0.03f), style = stroke)
            drawCircle(tint, s * 0.045f, Offset(s * 0.18f, s * 0.27f))
            drawCircle(tint, s * 0.045f, Offset(s * 0.82f, s * 0.27f))
            drawRoundRect(tint, Offset(s * 0.20f, s * 0.58f), Size(s * 0.60f, s * 0.30f),
                cornerRadius = CornerRadius(s * 0.03f), style = stroke)
            drawCircle(tint, s * 0.045f, Offset(s * 0.18f, s * 0.73f))
            drawCircle(tint, s * 0.045f, Offset(s * 0.82f, s * 0.73f))
        }
    }
    @Composable override fun GridView(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val pad = s * 0.12f
            val inner = s - pad * 2
            val mid = pad + inner / 2
            drawRect(tint, Offset(pad, pad), Size(inner, inner),
                style = Stroke(s * 0.06f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawLine(tint, Offset(mid, pad), Offset(mid, pad + inner),
                strokeWidth = s * 0.07f, cap = StrokeCap.Round)
            drawLine(tint, Offset(pad, mid), Offset(pad + inner, mid),
                strokeWidth = s * 0.07f, cap = StrokeCap.Round)
        }
    }
    @Composable override fun Apps(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val gap = s / 4f
            val r = s * 0.055f
            for (row in 0..2) {
                for (col in 0..2) {
                    val cx = gap + col * gap; val cy = gap + row * gap
                    drawRoundRect(tint,
                        Offset(cx - r, cy - r), Size(r * 2, r * 2),
                        cornerRadius = CornerRadius(r * 0.3f),
                        style = Fill)
                }
            }
        }
    }
}

// ── Content Icons ───────────────────────────────────────────────

private class ChineseContentIcons : BaseContentIcons() {
    @Composable override fun Star(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            // Plum blossom as star
            drawPlumBlossom(Offset(s * 0.5f, s * 0.5f), s * 0.32f, tint)
        }
    }
    @Composable override fun StarBorder(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = brushStroke(s)
            // Outline plum blossom
            for (i in 0 until 5) {
                rotate(72f * i, Offset(s * 0.5f, s * 0.5f)) {
                    val petal = Path().apply {
                        moveTo(s * 0.5f, s * 0.18f)
                        quadraticBezierTo(s * 0.64f, s * 0.38f, s * 0.5f, s * 0.5f)
                        quadraticBezierTo(s * 0.36f, s * 0.38f, s * 0.5f, s * 0.18f)
                    }
                    drawPath(petal, tint, style = thinBrush(s))
                }
            }
        }
    }
    @Composable override fun Image(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = brushStroke(s)
            // Scroll painting
            drawRoundRect(tint, Offset(s * 0.12f, s * 0.15f), Size(s * 0.76f, s * 0.7f),
                cornerRadius = CornerRadius(s * 0.03f), style = st)
            // Mountain landscape
            val mt = Path().apply {
                moveTo(s * 0.18f, s * 0.72f); lineTo(s * 0.35f, s * 0.42f)
                lineTo(s * 0.5f, s * 0.55f); lineTo(s * 0.7f, s * 0.35f); lineTo(s * 0.85f, s * 0.72f)
            }
            drawPath(mt, tint, style = thinBrush(s))
            drawChineseCloud(Offset(s * 0.3f, s * 0.3f), s * 0.06f, tint.copy(alpha = 0.4f))
        }
    }
    @Composable override fun Camera(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = brushStroke(s)
            // Dark box camera (暗箱)
            drawRoundRect(tint, Offset(s * 0.1f, s * 0.25f), Size(s * 0.8f, s * 0.55f),
                cornerRadius = CornerRadius(s * 0.04f), style = st)
            val bump = Path().apply {
                moveTo(s * 0.35f, s * 0.25f); lineTo(s * 0.4f, s * 0.15f)
                lineTo(s * 0.6f, s * 0.15f); lineTo(s * 0.65f, s * 0.25f)
            }
            drawPath(bump, tint, style = st)
            drawCircle(tint, s * 0.13f, Offset(s * 0.5f, s * 0.52f), style = st)
            drawChineseCloud(Offset(s * 0.5f, s * 0.52f), s * 0.06f, tint.copy(alpha = 0.4f))
        }
    }
    @Composable override fun AddPhoto(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = brushStroke(s)
            // Scroll + brush plus
            drawRoundRect(tint, Offset(s * 0.05f, s * 0.18f), Size(s * 0.62f, s * 0.64f),
                cornerRadius = CornerRadius(s * 0.03f), style = st)
            drawLine(tint, Offset(s * 0.8f, s * 0.32f), Offset(s * 0.8f, s * 0.68f),
                strokeWidth = s * 0.08f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.62f, s * 0.5f), Offset(s * 0.95f, s * 0.5f),
                strokeWidth = s * 0.08f, cap = StrokeCap.Round)
        }
    }
    @Composable override fun Link(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = brushStroke(s)
            // Jade pendant linked rings
            drawCircle(tint, s * 0.16f, Offset(s * 0.35f, s * 0.5f), style = st)
            drawCircle(tint, s * 0.16f, Offset(s * 0.65f, s * 0.5f), style = st)
            // Silk ribbon connecting
            val ribbon = Path().apply {
                moveTo(s * 0.35f, s * 0.34f)
                cubicTo(s * 0.45f, s * 0.42f, s * 0.55f, s * 0.42f, s * 0.65f, s * 0.34f)
            }
            drawPath(ribbon, tint.copy(alpha = 0.5f), style = thinBrush(s))
        }
    }
    @Composable override fun LinkOff(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = brushStroke(s)
            // Broken jade
            drawCircle(tint, s * 0.16f, Offset(s * 0.3f, s * 0.5f), style = st)
            drawCircle(tint, s * 0.16f, Offset(s * 0.7f, s * 0.5f), style = st)
            drawLine(tint, Offset(s * 0.18f, s * 0.82f), Offset(s * 0.82f, s * 0.18f),
                strokeWidth = s * 0.06f, cap = StrokeCap.Round)
            drawInkSplash(Offset(s * 0.5f, s * 0.5f), s * 0.05f, tint)
        }
    }
    @Composable override fun Palette(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = brushStroke(s)
            // Inkstone (砚台)
            drawOval(tint, Offset(s * 0.12f, s * 0.2f), Size(s * 0.76f, s * 0.6f), style = st)
            // Ink pool
            drawOval(tint.copy(alpha = 0.4f), Offset(s * 0.25f, s * 0.35f),
                Size(s * 0.5f, s * 0.3f), style = Fill)
            // Ink gradation dots
            drawCircle(tint.copy(alpha = 0.6f), s * 0.04f, Offset(s * 0.4f, s * 0.48f))
            drawCircle(tint.copy(alpha = 0.3f), s * 0.03f, Offset(s * 0.55f, s * 0.45f))
        }
    }
    @Composable override fun FileOpen(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = brushStroke(s)
            // Bamboo scroll (竹简) unrolling
            drawRoundRect(tint, Offset(s * 0.2f, s * 0.1f), Size(s * 0.6f, s * 0.8f),
                cornerRadius = CornerRadius(s * 0.03f), style = st)
            // Bamboo slats
            for (i in 0..4) {
                val x = s * (0.28f + i * 0.1f)
                drawLine(tint, Offset(x, s * 0.15f), Offset(x, s * 0.85f),
                    strokeWidth = s * 0.02f, cap = StrokeCap.Round)
            }
            // Scroll roll
            drawCircle(tint, s * 0.05f, Offset(s * 0.2f, s * 0.5f), style = st)
        }
    }
    @Composable override fun CalendarMonth(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = brushStroke(s)
            // Almanac (黄历)
            drawRoundRect(tint, Offset(s * 0.12f, s * 0.18f), Size(s * 0.76f, s * 0.7f),
                cornerRadius = CornerRadius(s * 0.03f), style = st)
            drawLine(tint, Offset(s * 0.12f, s * 0.38f), Offset(s * 0.88f, s * 0.38f),
                strokeWidth = s * 0.05f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.35f, s * 0.1f), Offset(s * 0.35f, s * 0.26f),
                strokeWidth = s * 0.05f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.65f, s * 0.1f), Offset(s * 0.65f, s * 0.26f),
                strokeWidth = s * 0.05f, cap = StrokeCap.Round)
            drawChineseCloud(Offset(s * 0.5f, s * 0.6f), s * 0.1f, tint.copy(alpha = 0.4f))
        }
    }
    @Composable override fun Notifications(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = brushStroke(s)
            // Chime bell (编钟)
            val bell = Path().apply {
                moveTo(s * 0.35f, s * 0.15f); lineTo(s * 0.65f, s * 0.15f)
                lineTo(s * 0.72f, s * 0.7f)
                quadraticBezierTo(s * 0.72f, s * 0.82f, s * 0.5f, s * 0.82f)
                quadraticBezierTo(s * 0.28f, s * 0.82f, s * 0.28f, s * 0.7f)
                close()
            }
            drawPath(bell, tint, style = st)
            // Tassel
            drawLine(tint, Offset(s * 0.5f, s * 0.82f), Offset(s * 0.5f, s * 0.92f),
                strokeWidth = s * 0.03f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.45f, s * 0.92f), Offset(s * 0.55f, s * 0.92f),
                strokeWidth = s * 0.03f, cap = StrokeCap.Round)
            // Horizontal bar
            drawLine(tint, Offset(s * 0.25f, s * 0.1f), Offset(s * 0.75f, s * 0.1f),
                strokeWidth = s * 0.05f, cap = StrokeCap.Round)
        }
    }
    @Composable override fun AttachMoney(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = brushStroke(s)
            // Copper coin (铜钱) / yuanbao
            drawCircle(tint, s * 0.32f, Offset(s * 0.5f, s * 0.5f), style = st)
            // Square hole in center
            drawRect(tint, Offset(s * 0.42f, s * 0.42f), Size(s * 0.16f, s * 0.16f), style = st)
        }
    }
    @Composable override fun Category(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = brushStroke(s)
            val gap = s * 0.06f; val cell = (s - gap * 3) / 2
            val positions = listOf(
                Offset(gap, gap), Offset(gap * 2 + cell, gap),
                Offset(gap, gap * 2 + cell), Offset(gap * 2 + cell, gap * 2 + cell)
            )
            positions.forEach { pos ->
                drawRoundRect(tint, pos, Size(cell, cell),
                    cornerRadius = CornerRadius(s * 0.03f), style = st)
            }
            // Plum blossom in each cell
            positions.forEach { pos ->
                drawPlumBlossom(Offset(pos.x + cell / 2, pos.y + cell / 2),
                    cell * 0.2f, tint.copy(alpha = 0.5f))
            }
        }
    }
    @Composable override fun Location(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = brushStroke(s)
            // Building body
            drawRect(tint, Offset(s * 0.25f, s * 0.45f), Size(s * 0.5f, s * 0.42f), style = st)
            // Curved eave roof
            val roof = Path().apply {
                moveTo(s * 0.1f, s * 0.48f)
                quadraticBezierTo(s * 0.3f, s * 0.35f, s * 0.5f, s * 0.2f)
                quadraticBezierTo(s * 0.7f, s * 0.35f, s * 0.9f, s * 0.48f)
            }
            drawPath(roof, tint, style = st)
            // Eave tips curving up
            drawLine(tint, Offset(s * 0.1f, s * 0.48f), Offset(s * 0.06f, s * 0.42f), st.width)
            drawLine(tint, Offset(s * 0.9f, s * 0.48f), Offset(s * 0.94f, s * 0.42f), st.width)
            // Door
            drawRect(tint, Offset(s * 0.42f, s * 0.65f), Size(s * 0.16f, s * 0.22f), style = st)
        }
    }
}

// ── Arrow Icons ─────────────────────────────────────────────────

private class ChineseArrowIcons : BaseArrowIcons() {
    @Composable override fun ArrowBack(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val stroke = Path().apply {
                moveTo(s * 0.78f, s * 0.20f)
                cubicTo(s * 0.60f, s * 0.30f, s * 0.40f, s * 0.42f, s * 0.20f, s * 0.52f)
            }
            drawPath(stroke, tint, style = Stroke(s * 0.10f, cap = StrokeCap.Round))
            val taper = Path().apply {
                moveTo(s * 0.50f, s * 0.38f)
                cubicTo(s * 0.38f, s * 0.44f, s * 0.28f, s * 0.48f, s * 0.18f, s * 0.52f)
            }
            drawPath(taper, tint, style = Stroke(s * 0.04f, cap = StrokeCap.Round))
            drawLine(tint.copy(alpha = 0.4f),
                Offset(s * 0.22f, s * 0.56f), Offset(s * 0.16f, s * 0.60f),
                strokeWidth = s * 0.025f, cap = StrokeCap.Round)
            drawLine(tint.copy(alpha = 0.3f),
                Offset(s * 0.26f, s * 0.58f), Offset(s * 0.20f, s * 0.63f),
                strokeWidth = s * 0.02f, cap = StrokeCap.Round)
            drawInkSplash(Offset(s * 0.78f, s * 0.20f), s * 0.04f, tint)
        }
    }
    @Composable override fun ArrowForward(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val arr = Path().apply {
                moveTo(s * 0.32f, s * 0.18f); lineTo(s * 0.72f, s * 0.5f); lineTo(s * 0.32f, s * 0.82f)
            }
            drawPath(arr, tint, style = brushStroke(s))
            drawInkSplash(Offset(s * 0.72f, s * 0.5f), s * 0.04f, tint)
        }
    }
    @Composable override fun KeyboardArrowLeft(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val arr = Path().apply {
                moveTo(s * 0.65f, s * 0.2f); lineTo(s * 0.3f, s * 0.5f); lineTo(s * 0.65f, s * 0.8f)
            }
            drawPath(arr, tint, style = brushStroke(s))
        }
    }
    @Composable override fun KeyboardArrowRight(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val arr = Path().apply {
                moveTo(s * 0.35f, s * 0.2f); lineTo(s * 0.7f, s * 0.5f); lineTo(s * 0.35f, s * 0.8f)
            }
            drawPath(arr, tint, style = brushStroke(s))
        }
    }
    @Composable override fun ExpandMore(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val arr = Path().apply {
                moveTo(s * 0.2f, s * 0.32f); lineTo(s * 0.5f, s * 0.68f); lineTo(s * 0.8f, s * 0.32f)
            }
            drawPath(arr, tint, style = brushStroke(s))
            drawInkSplash(Offset(s * 0.5f, s * 0.68f), s * 0.03f, tint)
        }
    }
    @Composable override fun ExpandLess(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val arr = Path().apply {
                moveTo(s * 0.2f, s * 0.68f); lineTo(s * 0.5f, s * 0.32f); lineTo(s * 0.8f, s * 0.68f)
            }
            drawPath(arr, tint, style = brushStroke(s))
            drawInkSplash(Offset(s * 0.5f, s * 0.32f), s * 0.03f, tint)
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
            val s = size.minDimension; val st = brushStroke(s)
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
            drawChineseCloud(Offset(s * 0.5f, s * 0.5f), s * 0.06f, tint.copy(alpha = 0.3f))
        }
    }
    @Composable override fun OpenInNew(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = brushStroke(s)
            // Bird flying out of frame
            val box = Path().apply {
                moveTo(s * 0.55f, s * 0.15f); lineTo(s * 0.15f, s * 0.15f)
                lineTo(s * 0.15f, s * 0.85f); lineTo(s * 0.85f, s * 0.85f)
                lineTo(s * 0.85f, s * 0.48f)
            }
            drawPath(box, tint, style = st)
            drawLine(tint, Offset(s * 0.48f, s * 0.52f), Offset(s * 0.85f, s * 0.15f),
                strokeWidth = s * 0.06f, cap = StrokeCap.Round)
            drawChineseCloud(Offset(s * 0.85f, s * 0.15f), s * 0.07f, tint.copy(alpha = 0.4f))
        }
    }
}

// ── Status Icons ────────────────────────────────────────────────

private class ChineseStatusIcons : BaseStatusIcons() {
    @Composable override fun CheckCircle(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = brushStroke(s)
            // Jade disc (玉璧)
            drawCircle(tint, s * 0.38f, Offset(s * 0.5f, s * 0.5f), style = st)
            val check = Path().apply {
                moveTo(s * 0.3f, s * 0.5f); lineTo(s * 0.45f, s * 0.65f); lineTo(s * 0.72f, s * 0.35f)
            }
            drawPath(check, tint, style = st)
        }
    }
    @Composable override fun Warning(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = brushStroke(s)
            // Token / tablet (令牌)
            val token = Path().apply {
                moveTo(s * 0.5f, s * 0.08f); lineTo(s * 0.15f, s * 0.5f)
                lineTo(s * 0.5f, s * 0.92f); lineTo(s * 0.85f, s * 0.5f); close()
            }
            drawPath(token, tint, style = st)
            // Brush stroke exclamation
            drawLine(tint, Offset(s * 0.5f, s * 0.3f), Offset(s * 0.5f, s * 0.58f),
                strokeWidth = s * 0.08f, cap = StrokeCap.Round)
            drawCircle(tint, s * 0.04f, Offset(s * 0.5f, s * 0.72f))
        }
    }
    @Composable override fun Error(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = brushStroke(s)
            // Broken jade disc
            drawCircle(tint, s * 0.38f, Offset(s * 0.5f, s * 0.5f), style = st)
            // Ink X
            drawLine(tint, Offset(s * 0.33f, s * 0.33f), Offset(s * 0.67f, s * 0.67f),
                strokeWidth = s * 0.07f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.67f, s * 0.33f), Offset(s * 0.33f, s * 0.67f),
                strokeWidth = s * 0.07f, cap = StrokeCap.Round)
            drawInkSplash(Offset(s * 0.5f, s * 0.5f), s * 0.05f, tint)
        }
    }
    @Composable override fun Info(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = brushStroke(s)
            // Jade disc with seal-style "i"
            drawCircle(tint, s * 0.38f, Offset(s * 0.5f, s * 0.5f), style = st)
            drawCircle(tint, s * 0.04f, Offset(s * 0.5f, s * 0.32f))
            drawLine(tint, Offset(s * 0.5f, s * 0.45f), Offset(s * 0.5f, s * 0.7f),
                strokeWidth = s * 0.07f, cap = StrokeCap.Round)
        }
    }
    @Composable override fun Visibility(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = brushStroke(s)
            // Phoenix eye (凤眼)
            val eye = Path().apply {
                moveTo(s * 0.05f, s * 0.5f)
                cubicTo(s * 0.2f, s * 0.22f, s * 0.8f, s * 0.22f, s * 0.95f, s * 0.5f)
                cubicTo(s * 0.8f, s * 0.78f, s * 0.2f, s * 0.78f, s * 0.05f, s * 0.5f)
            }
            drawPath(eye, tint, style = st)
            drawCircle(tint, s * 0.1f, Offset(s * 0.5f, s * 0.5f))
            drawChineseCloud(Offset(s * 0.5f, s * 0.5f), s * 0.05f, tint.copy(alpha = 0.3f))
        }
    }
    @Composable override fun VisibilityOff(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = brushStroke(s)
            // Closed phoenix eye
            val eye = Path().apply {
                moveTo(s * 0.05f, s * 0.5f)
                cubicTo(s * 0.2f, s * 0.22f, s * 0.8f, s * 0.22f, s * 0.95f, s * 0.5f)
                cubicTo(s * 0.8f, s * 0.78f, s * 0.2f, s * 0.78f, s * 0.05f, s * 0.5f)
            }
            drawPath(eye, tint, style = st)
            drawLine(tint, Offset(s * 0.15f, s * 0.85f), Offset(s * 0.85f, s * 0.15f),
                strokeWidth = s * 0.07f, cap = StrokeCap.Round)
            drawInkSplash(Offset(s * 0.5f, s * 0.5f), s * 0.05f, tint)
        }
    }
}

// ── Provider ────────────────────────────────────────────────────

class ChineseIconProvider : BaseSkinIconProvider() {
    override val navigation: NavigationIcons = ChineseNavigationIcons()
    override val action: ActionIcons = ChineseActionIcons()
    override val content: ContentIcons = ChineseContentIcons()
    override val arrow: ArrowIcons = ChineseArrowIcons()
    override val status: StatusIcons = ChineseStatusIcons()
}
