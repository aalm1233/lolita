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

private fun classicStroke(s: Float) = Stroke(
    width = s * 0.065f, cap = StrokeCap.Round, join = StrokeJoin.Round
)

private fun thinClassic(s: Float) = Stroke(
    width = s * 0.035f, cap = StrokeCap.Round, join = StrokeJoin.Round
)

private fun DrawScope.drawSpade(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - r)
        cubicTo(c.x + r * 0.8f, c.y - r * 0.6f, c.x + r * 0.9f, c.y + r * 0.2f, c.x, c.y + r * 0.4f)
        cubicTo(c.x - r * 0.9f, c.y + r * 0.2f, c.x - r * 0.8f, c.y - r * 0.6f, c.x, c.y - r)
        moveTo(c.x, c.y + r * 0.4f)
        lineTo(c.x, c.y + r * 0.8f)
        moveTo(c.x - r * 0.25f, c.y + r * 0.8f)
        lineTo(c.x + r * 0.25f, c.y + r * 0.8f)
    }
    drawPath(p, color, style = Fill)
}

private fun DrawScope.drawCrown(c: Offset, w: Float, h: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x - w, c.y + h * 0.3f)
        lineTo(c.x - w, c.y - h * 0.2f)
        lineTo(c.x - w * 0.5f, c.y + h * 0.1f)
        lineTo(c.x, c.y - h * 0.5f)
        lineTo(c.x + w * 0.5f, c.y + h * 0.1f)
        lineTo(c.x + w, c.y - h * 0.2f)
        lineTo(c.x + w, c.y + h * 0.3f)
        close()
    }
    drawPath(p, color, style = Fill)
}

private fun DrawScope.drawScrollwork(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x - r, c.y)
        cubicTo(c.x - r, c.y - r * 0.8f, c.x, c.y - r * 0.5f, c.x, c.y)
        cubicTo(c.x, c.y + r * 0.5f, c.x + r, c.y + r * 0.8f, c.x + r, c.y)
    }
    drawPath(p, color, style = thinClassic(r * 3))
}

// ── Navigation Icons ────────────────────────────────────────────

private class ClassicNavigationIcons : BaseNavigationIcons() {
    @Composable override fun Home(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = classicStroke(s)
            // Victorian manor with arch door
            val house = Path().apply {
                moveTo(s * 0.5f, s * 0.1f)
                lineTo(s * 0.12f, s * 0.45f); lineTo(s * 0.12f, s * 0.85f)
                quadraticBezierTo(s * 0.12f, s * 0.9f, s * 0.18f, s * 0.9f)
                lineTo(s * 0.82f, s * 0.9f)
                quadraticBezierTo(s * 0.88f, s * 0.9f, s * 0.88f, s * 0.85f)
                lineTo(s * 0.88f, s * 0.45f); close()
            }
            drawPath(house, tint, style = st)
            // Arch door
            drawArc(tint, 180f, 180f, false, Offset(s * 0.38f, s * 0.52f),
                Size(s * 0.24f, s * 0.24f), style = st)
            drawLine(tint, Offset(s * 0.38f, s * 0.64f), Offset(s * 0.38f, s * 0.9f),
                strokeWidth = s * 0.05f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.62f, s * 0.64f), Offset(s * 0.62f, s * 0.9f),
                strokeWidth = s * 0.05f, cap = StrokeCap.Round)
            drawScrollwork(Offset(s * 0.5f, s * 0.1f), s * 0.06f, tint.copy(alpha = 0.5f))
        }
    }
    @Composable override fun Wishlist(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = classicStroke(s)
            // Victorian ornate heart
            val heart = Path().apply {
                moveTo(s * 0.5f, s * 0.82f)
                cubicTo(s * 0.12f, s * 0.55f, s * 0.12f, s * 0.2f, s * 0.5f, s * 0.32f)
                cubicTo(s * 0.88f, s * 0.2f, s * 0.88f, s * 0.55f, s * 0.5f, s * 0.82f)
            }
            drawPath(heart, tint, style = st)
            drawCrown(Offset(s * 0.5f, s * 0.2f), s * 0.12f, s * 0.1f, tint.copy(alpha = 0.6f))
        }
    }
    @Composable override fun Outfit(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = classicStroke(s)
            // Rococo dress silhouette
            val dress = Path().apply {
                moveTo(s * 0.4f, s * 0.1f); lineTo(s * 0.6f, s * 0.1f)
                lineTo(s * 0.62f, s * 0.3f)
                cubicTo(s * 0.65f, s * 0.4f, s * 0.82f, s * 0.6f, s * 0.85f, s * 0.88f)
                lineTo(s * 0.15f, s * 0.88f)
                cubicTo(s * 0.18f, s * 0.6f, s * 0.35f, s * 0.4f, s * 0.38f, s * 0.3f)
                close()
            }
            drawPath(dress, tint, style = st)
            // Lace edge scallops
            for (i in 0..5) {
                val x = s * (0.2f + i * 0.12f)
                drawArc(tint, 0f, 180f, false, Offset(x - s * 0.04f, s * 0.84f),
                    Size(s * 0.08f, s * 0.06f), style = thinClassic(s))
            }
        }
    }
    @Composable override fun Stats(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = classicStroke(s)
            // Pocket watch
            drawCircle(tint, s * 0.32f, Offset(s * 0.5f, s * 0.52f), style = st)
            drawCircle(tint, s * 0.28f, Offset(s * 0.5f, s * 0.52f), style = thinClassic(s))
            // Watch stem
            drawLine(tint, Offset(s * 0.5f, s * 0.2f), Offset(s * 0.5f, s * 0.12f),
                strokeWidth = s * 0.04f, cap = StrokeCap.Round)
            drawCircle(tint, s * 0.03f, Offset(s * 0.5f, s * 0.1f))
            // Hands
            drawLine(tint, Offset(s * 0.5f, s * 0.52f), Offset(s * 0.5f, s * 0.35f),
                strokeWidth = s * 0.04f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.5f, s * 0.52f), Offset(s * 0.62f, s * 0.48f),
                strokeWidth = s * 0.03f, cap = StrokeCap.Round)
        }
    }
    @Composable override fun Settings(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = classicStroke(s)
            val c = Offset(s * 0.5f, s * 0.5f)
            // Precision gear
            for (i in 0 until 8) {
                rotate(45f * i, c) {
                    drawRoundRect(tint, Offset(c.x - s * 0.04f, c.y - s * 0.38f),
                        Size(s * 0.08f, s * 0.14f),
                        cornerRadius = CornerRadius(s * 0.02f), style = Fill)
                }
            }
            drawCircle(tint, s * 0.22f, c, style = st)
            drawCircle(tint, s * 0.08f, c, style = st)
            drawCrown(Offset(c.x, c.y - s * 0.01f), s * 0.06f, s * 0.05f, tint)
        }
    }
}

// ── Action Icons ────────────────────────────────────────────────

private class ClassicActionIcons : BaseActionIcons() {
    @Composable override fun Add(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val cx = s * 0.5f; val cy = s * 0.5f
            val vArm = Path().apply {
                moveTo(cx - s * 0.04f, cy - s * 0.06f)
                lineTo(cx - s * 0.08f, cy - s * 0.34f)
                cubicTo(cx - s * 0.14f, cy - s * 0.38f, cx - s * 0.12f, cy - s * 0.44f, cx, cy - s * 0.40f)
                cubicTo(cx + s * 0.12f, cy - s * 0.44f, cx + s * 0.14f, cy - s * 0.38f, cx + s * 0.08f, cy - s * 0.34f)
                lineTo(cx + s * 0.04f, cy - s * 0.06f)
            }
            drawPath(vArm, tint, style = Fill)
            val bArm = Path().apply {
                moveTo(cx - s * 0.04f, cy + s * 0.06f)
                lineTo(cx - s * 0.08f, cy + s * 0.34f)
                cubicTo(cx - s * 0.14f, cy + s * 0.38f, cx - s * 0.12f, cy + s * 0.44f, cx, cy + s * 0.40f)
                cubicTo(cx + s * 0.12f, cy + s * 0.44f, cx + s * 0.14f, cy + s * 0.38f, cx + s * 0.08f, cy + s * 0.34f)
                lineTo(cx + s * 0.04f, cy + s * 0.06f)
            }
            drawPath(bArm, tint, style = Fill)
            val rArm = Path().apply {
                moveTo(cx + s * 0.06f, cy - s * 0.04f)
                lineTo(cx + s * 0.34f, cy - s * 0.08f)
                cubicTo(cx + s * 0.38f, cy - s * 0.14f, cx + s * 0.44f, cy - s * 0.12f, cx + s * 0.40f, cy)
                cubicTo(cx + s * 0.44f, cy + s * 0.12f, cx + s * 0.38f, cy + s * 0.14f, cx + s * 0.34f, cy + s * 0.08f)
                lineTo(cx + s * 0.06f, cy + s * 0.04f)
            }
            drawPath(rArm, tint, style = Fill)
            val lArm = Path().apply {
                moveTo(cx - s * 0.06f, cy - s * 0.04f)
                lineTo(cx - s * 0.34f, cy - s * 0.08f)
                cubicTo(cx - s * 0.38f, cy - s * 0.14f, cx - s * 0.44f, cy - s * 0.12f, cx - s * 0.40f, cy)
                cubicTo(cx - s * 0.44f, cy + s * 0.12f, cx - s * 0.38f, cy + s * 0.14f, cx - s * 0.34f, cy + s * 0.08f)
                lineTo(cx - s * 0.06f, cy + s * 0.04f)
            }
            drawPath(lArm, tint, style = Fill)
            drawCircle(tint, s * 0.06f, Offset(cx, cy))
        }
    }
    @Composable override fun Delete(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = classicStroke(s)
            val body = Path().apply {
                moveTo(s * 0.25f, s * 0.32f)
                lineTo(s * 0.28f, s * 0.82f)
                quadraticBezierTo(s * 0.28f, s * 0.9f, s * 0.35f, s * 0.9f)
                lineTo(s * 0.65f, s * 0.9f)
                quadraticBezierTo(s * 0.72f, s * 0.9f, s * 0.72f, s * 0.82f)
                lineTo(s * 0.75f, s * 0.32f)
            }
            drawPath(body, tint, style = st)
            drawLine(tint, Offset(s * 0.2f, s * 0.3f), Offset(s * 0.8f, s * 0.3f),
                strokeWidth = s * 0.06f, cap = StrokeCap.Round)
            // Ornate lid knob
            drawRoundRect(tint, Offset(s * 0.42f, s * 0.18f), Size(s * 0.16f, s * 0.1f),
                cornerRadius = CornerRadius(s * 0.03f), style = st)
            // Relief lines
            drawLine(tint, Offset(s * 0.42f, s * 0.42f), Offset(s * 0.42f, s * 0.78f),
                strokeWidth = s * 0.03f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.58f, s * 0.42f), Offset(s * 0.58f, s * 0.78f),
                strokeWidth = s * 0.03f, cap = StrokeCap.Round)
        }
    }
    @Composable override fun Edit(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = classicStroke(s)
            // Fountain pen
            val pen = Path().apply {
                moveTo(s * 0.72f, s * 0.12f); lineTo(s * 0.85f, s * 0.25f)
                lineTo(s * 0.32f, s * 0.78f); lineTo(s * 0.12f, s * 0.88f)
                lineTo(s * 0.22f, s * 0.68f); close()
            }
            drawPath(pen, tint, style = st)
            // Ink bottle
            drawRoundRect(tint, Offset(s * 0.68f, s * 0.7f), Size(s * 0.2f, s * 0.2f),
                cornerRadius = CornerRadius(s * 0.03f), style = st)
            drawScrollwork(Offset(s * 0.78f, s * 0.12f), s * 0.04f, tint.copy(alpha = 0.4f))
        }
    }
    @Composable override fun Search(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = classicStroke(s)
            drawCircle(tint, s * 0.23f, Offset(s * 0.42f, s * 0.42f), style = st)
            drawCircle(tint, s * 0.18f, Offset(s * 0.42f, s * 0.42f), style = thinClassic(s))
            drawLine(tint, Offset(s * 0.6f, s * 0.6f), Offset(s * 0.82f, s * 0.82f),
                strokeWidth = s * 0.07f, cap = StrokeCap.Round)
            // Ornate handle
            drawScrollwork(Offset(s * 0.82f, s * 0.82f), s * 0.04f, tint.copy(alpha = 0.4f))
        }
    }
    @Composable override fun Sort(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            data class Bar(val y: Float, val endX: Float)
            val bars = listOf(
                Bar(s * 0.25f, s * 0.82f),
                Bar(s * 0.50f, s * 0.64f),
                Bar(s * 0.75f, s * 0.46f)
            )
            val colX = s * 0.18f
            drawLine(tint, Offset(colX, s * 0.15f), Offset(colX, s * 0.85f),
                strokeWidth = s * 0.05f, cap = StrokeCap.Round)
            drawLine(tint, Offset(colX - s * 0.06f, s * 0.85f), Offset(colX + s * 0.06f, s * 0.85f),
                strokeWidth = s * 0.04f, cap = StrokeCap.Round)
            drawLine(tint, Offset(colX - s * 0.06f, s * 0.15f), Offset(colX + s * 0.06f, s * 0.15f),
                strokeWidth = s * 0.04f, cap = StrokeCap.Round)
            bars.forEach { bar ->
                drawLine(tint, Offset(colX, bar.y), Offset(bar.endX, bar.y),
                    strokeWidth = s * 0.055f, cap = StrokeCap.Round)
                drawCircle(tint, s * 0.04f, Offset(bar.endX, bar.y))
            }
        }
    }
    @Composable override fun Save(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = classicStroke(s)
            val check = Path().apply {
                moveTo(s * 0.18f, s * 0.5f); lineTo(s * 0.42f, s * 0.72f); lineTo(s * 0.82f, s * 0.28f)
            }
            drawPath(check, tint, style = st)
            // Gold frame accent
            drawRoundRect(tint.copy(alpha = 0.3f), Offset(s * 0.1f, s * 0.15f),
                Size(s * 0.8f, s * 0.7f), cornerRadius = CornerRadius(s * 0.06f), style = thinClassic(s))
        }
    }
    @Composable override fun Close(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            drawLine(tint, Offset(s * 0.22f, s * 0.22f), Offset(s * 0.78f, s * 0.78f),
                strokeWidth = s * 0.065f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.78f, s * 0.22f), Offset(s * 0.22f, s * 0.78f),
                strokeWidth = s * 0.065f, cap = StrokeCap.Round)
            drawScrollwork(Offset(s * 0.78f, s * 0.22f), s * 0.04f, tint.copy(alpha = 0.4f))
            drawScrollwork(Offset(s * 0.22f, s * 0.22f), s * 0.04f, tint.copy(alpha = 0.4f))
        }
    }
    @Composable override fun Share(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = classicStroke(s)
            // Wax-sealed envelope
            val env = Path().apply {
                moveTo(s * 0.1f, s * 0.25f); lineTo(s * 0.9f, s * 0.25f)
                lineTo(s * 0.9f, s * 0.78f); lineTo(s * 0.1f, s * 0.78f); close()
            }
            drawPath(env, tint, style = st)
            // Flap
            val flap = Path().apply {
                moveTo(s * 0.1f, s * 0.25f); lineTo(s * 0.5f, s * 0.52f); lineTo(s * 0.9f, s * 0.25f)
            }
            drawPath(flap, tint, style = st)
            // Wax seal
            drawCircle(tint, s * 0.08f, Offset(s * 0.5f, s * 0.58f))
            drawSpade(Offset(s * 0.5f, s * 0.57f), s * 0.04f, tint.copy(alpha = 0.6f))
        }
    }
    @Composable override fun FilterList(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val cx = s * 0.5f
            data class Bar(val y: Float, val halfW: Float)
            val bars = listOf(
                Bar(s * 0.24f, s * 0.36f),
                Bar(s * 0.50f, s * 0.24f),
                Bar(s * 0.76f, s * 0.12f)
            )
            drawLine(tint, Offset(cx, s * 0.18f), Offset(cx, s * 0.82f),
                strokeWidth = s * 0.04f, cap = StrokeCap.Round)
            bars.forEach { bar ->
                drawLine(tint, Offset(cx - bar.halfW, bar.y), Offset(cx + bar.halfW, bar.y),
                    strokeWidth = s * 0.055f, cap = StrokeCap.Round)
                drawCircle(tint, s * 0.035f, Offset(cx - bar.halfW, bar.y))
                drawCircle(tint, s * 0.035f, Offset(cx + bar.halfW, bar.y))
                drawScrollwork(Offset(cx - bar.halfW - s * 0.01f, bar.y), s * 0.025f, tint.copy(alpha = 0.4f))
                drawScrollwork(Offset(cx + bar.halfW + s * 0.01f, bar.y), s * 0.025f, tint.copy(alpha = 0.4f))
            }
        }
    }
    @Composable override fun MoreVert(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            // Three small spades
            drawSpade(Offset(s * 0.5f, s * 0.2f), s * 0.06f, tint)
            drawSpade(Offset(s * 0.5f, s * 0.5f), s * 0.06f, tint)
            drawSpade(Offset(s * 0.5f, s * 0.8f), s * 0.06f, tint)
        }
    }
    @Composable override fun ContentCopy(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = classicStroke(s)
            drawRoundRect(tint, Offset(s * 0.28f, s * 0.28f), Size(s * 0.55f, s * 0.62f),
                cornerRadius = CornerRadius(s * 0.06f), style = st)
            drawRoundRect(tint, Offset(s * 0.15f, s * 0.1f), Size(s * 0.55f, s * 0.62f),
                cornerRadius = CornerRadius(s * 0.06f), style = st)
            // Wax seal corner
            drawCircle(tint, s * 0.04f, Offset(s * 0.72f, s * 0.18f))
        }
    }
    @Composable override fun Refresh(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = classicStroke(s)
            drawArc(tint, 35f, 275f, false, Offset(s * 0.15f, s * 0.15f),
                Size(s * 0.7f, s * 0.7f), style = st)
            // Scrollwork arrow
            val arrow = Path().apply {
                moveTo(s * 0.63f, s * 0.12f); lineTo(s * 0.78f, s * 0.26f); lineTo(s * 0.55f, s * 0.28f); close()
            }
            drawPath(arrow, tint, style = Fill)
            drawScrollwork(Offset(s * 0.5f, s * 0.5f), s * 0.05f, tint.copy(alpha = 0.3f))
        }
    }
    @Composable override fun ViewAgenda(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val stroke = classicStroke(s)
            drawRoundRect(tint, Offset(s * 0.14f, s * 0.10f), Size(s * 0.72f, s * 0.32f),
                cornerRadius = CornerRadius(s * 0.02f), style = stroke)
            drawLine(tint.copy(alpha = 0.3f), Offset(s * 0.22f, s * 0.20f), Offset(s * 0.78f, s * 0.20f),
                strokeWidth = s * 0.02f)
            drawLine(tint.copy(alpha = 0.3f), Offset(s * 0.22f, s * 0.30f), Offset(s * 0.78f, s * 0.30f),
                strokeWidth = s * 0.02f)
            drawRoundRect(tint, Offset(s * 0.14f, s * 0.58f), Size(s * 0.72f, s * 0.32f),
                cornerRadius = CornerRadius(s * 0.02f), style = stroke)
            drawLine(tint.copy(alpha = 0.3f), Offset(s * 0.22f, s * 0.68f), Offset(s * 0.78f, s * 0.68f),
                strokeWidth = s * 0.02f)
            drawLine(tint.copy(alpha = 0.3f), Offset(s * 0.22f, s * 0.78f), Offset(s * 0.78f, s * 0.78f),
                strokeWidth = s * 0.02f)
        }
    }
    @Composable override fun GridView(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val gap = s * 0.08f
            val cellSize = (s - gap * 3) / 2
            val stroke = Stroke(s * 0.05f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            for (row in 0..1) {
                for (col in 0..1) {
                    val x = gap + col * (cellSize + gap)
                    val y = gap + row * (cellSize + gap)
                    drawRoundRect(tint, Offset(x, y), Size(cellSize, cellSize),
                        cornerRadius = CornerRadius(s * 0.02f), style = stroke)
                    drawScrollwork(Offset(x + cellSize * 0.5f, y + cellSize * 0.5f),
                        s * 0.03f, tint.copy(alpha = 0.35f))
                }
            }
        }
    }
    @Composable override fun Apps(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val gap = s / 4f
            val innerR = s * 0.045f
            val outerR = s * 0.065f
            for (row in 0..2) {
                for (col in 0..2) {
                    val cx = gap + col * gap; val cy = gap + row * gap
                    drawCircle(tint.copy(alpha = 0.35f), outerR, Offset(cx, cy),
                        style = Stroke(s * 0.02f))
                    drawCircle(tint, innerR, Offset(cx, cy), style = Fill)
                }
            }
        }
    }
    @Composable override fun Gallery(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val stroke = Stroke(s * 0.05f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val gap = s * 0.08f
            val colW = (s - gap * 3) / 2
            val r = CornerRadius(colW * 0.25f)
            drawRoundRect(tint, Offset(gap, gap), Size(colW, s * 0.5f), r, stroke)
            drawRoundRect(tint, Offset(gap, gap + s * 0.5f + gap), Size(colW, s * 0.28f), r, stroke)
            drawRoundRect(tint, Offset(gap * 2 + colW, gap), Size(colW, s * 0.28f), r, stroke)
            drawRoundRect(tint, Offset(gap * 2 + colW, gap + s * 0.28f + gap), Size(colW, s * 0.5f), r, stroke)
        }
    }
}

// ── Content Icons ───────────────────────────────────────────────

private class ClassicContentIcons : BaseContentIcons() {
    @Composable override fun Star(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val p = Path().apply {
                moveTo(s * 0.5f, s * 0.08f)
                lineTo(s * 0.62f, s * 0.38f); lineTo(s * 0.95f, s * 0.38f)
                lineTo(s * 0.68f, s * 0.58f); lineTo(s * 0.8f, s * 0.92f)
                lineTo(s * 0.5f, s * 0.72f); lineTo(s * 0.2f, s * 0.92f)
                lineTo(s * 0.32f, s * 0.58f); lineTo(s * 0.05f, s * 0.38f)
                lineTo(s * 0.38f, s * 0.38f); close()
            }
            drawPath(p, tint, style = Fill)
            drawCrown(Offset(s * 0.5f, s * 0.06f), s * 0.08f, s * 0.06f, tint.copy(alpha = 0.5f))
        }
    }
    @Composable override fun StarBorder(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = classicStroke(s)
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
            val s = size.minDimension; val st = classicStroke(s)
            // Oval picture frame
            drawOval(tint, Offset(s * 0.12f, s * 0.1f), Size(s * 0.76f, s * 0.8f), style = st)
            drawOval(tint, Offset(s * 0.18f, s * 0.16f), Size(s * 0.64f, s * 0.68f), style = thinClassic(s))
            // Landscape
            val mt = Path().apply {
                moveTo(s * 0.22f, s * 0.65f); lineTo(s * 0.4f, s * 0.42f)
                lineTo(s * 0.55f, s * 0.55f); lineTo(s * 0.72f, s * 0.38f); lineTo(s * 0.82f, s * 0.65f)
            }
            drawPath(mt, tint, style = thinClassic(s))
        }
    }
    @Composable override fun Camera(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = classicStroke(s)
            // Vintage camera
            drawRoundRect(tint, Offset(s * 0.08f, s * 0.28f), Size(s * 0.84f, s * 0.55f),
                cornerRadius = CornerRadius(s * 0.06f), style = st)
            val bump = Path().apply {
                moveTo(s * 0.35f, s * 0.28f); lineTo(s * 0.4f, s * 0.16f)
                lineTo(s * 0.6f, s * 0.16f); lineTo(s * 0.65f, s * 0.28f)
            }
            drawPath(bump, tint, style = st)
            drawCircle(tint, s * 0.14f, Offset(s * 0.5f, s * 0.55f), style = st)
            drawCircle(tint, s * 0.09f, Offset(s * 0.5f, s * 0.55f), style = thinClassic(s))
        }
    }
    @Composable override fun AddPhoto(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = classicStroke(s)
            drawRoundRect(tint, Offset(s * 0.05f, s * 0.18f), Size(s * 0.62f, s * 0.64f),
                cornerRadius = CornerRadius(s * 0.06f), style = st)
            drawLine(tint, Offset(s * 0.8f, s * 0.32f), Offset(s * 0.8f, s * 0.68f),
                strokeWidth = s * 0.065f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.62f, s * 0.5f), Offset(s * 0.95f, s * 0.5f),
                strokeWidth = s * 0.065f, cap = StrokeCap.Round)
            drawScrollwork(Offset(s * 0.8f, s * 0.32f), s * 0.03f, tint.copy(alpha = 0.4f))
        }
    }
    @Composable override fun Link(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = classicStroke(s)
            // Elegant chain links
            drawRoundRect(tint, Offset(s * 0.1f, s * 0.32f), Size(s * 0.35f, s * 0.36f),
                cornerRadius = CornerRadius(s * 0.12f), style = st)
            drawRoundRect(tint, Offset(s * 0.55f, s * 0.32f), Size(s * 0.35f, s * 0.36f),
                cornerRadius = CornerRadius(s * 0.12f), style = st)
            drawLine(tint, Offset(s * 0.42f, s * 0.5f), Offset(s * 0.58f, s * 0.5f),
                strokeWidth = s * 0.05f, cap = StrokeCap.Round)
        }
    }
    @Composable override fun LinkOff(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = classicStroke(s)
            drawRoundRect(tint, Offset(s * 0.05f, s * 0.32f), Size(s * 0.35f, s * 0.36f),
                cornerRadius = CornerRadius(s * 0.12f), style = st)
            drawRoundRect(tint, Offset(s * 0.6f, s * 0.32f), Size(s * 0.35f, s * 0.36f),
                cornerRadius = CornerRadius(s * 0.12f), style = st)
            drawLine(tint, Offset(s * 0.18f, s * 0.82f), Offset(s * 0.82f, s * 0.18f),
                strokeWidth = s * 0.06f, cap = StrokeCap.Round)
        }
    }
    @Composable override fun Palette(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = classicStroke(s)
            val pal = Path().apply {
                moveTo(s * 0.5f, s * 0.1f)
                cubicTo(s * 0.15f, s * 0.1f, s * 0.05f, s * 0.5f, s * 0.25f, s * 0.8f)
                cubicTo(s * 0.35f, s * 0.92f, s * 0.55f, s * 0.85f, s * 0.5f, s * 0.7f)
                cubicTo(s * 0.45f, s * 0.55f, s * 0.65f, s * 0.5f, s * 0.7f, s * 0.6f)
                cubicTo(s * 0.85f, s * 0.75f, s * 0.95f, s * 0.4f, s * 0.5f, s * 0.1f)
            }
            drawPath(pal, tint, style = st)
            // Gold-edged color holes
            drawCircle(tint, s * 0.04f, Offset(s * 0.3f, s * 0.38f))
            drawCircle(tint, s * 0.04f, Offset(s * 0.45f, s * 0.28f))
            drawCircle(tint, s * 0.04f, Offset(s * 0.62f, s * 0.33f))
            drawCircle(tint, s * 0.04f, Offset(s * 0.35f, s * 0.55f))
        }
    }
    @Composable override fun FileOpen(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = classicStroke(s)
            val folder = Path().apply {
                moveTo(s * 0.1f, s * 0.22f); lineTo(s * 0.1f, s * 0.82f)
                quadraticBezierTo(s * 0.1f, s * 0.88f, s * 0.16f, s * 0.88f)
                lineTo(s * 0.84f, s * 0.88f)
                quadraticBezierTo(s * 0.9f, s * 0.88f, s * 0.9f, s * 0.82f)
                lineTo(s * 0.9f, s * 0.38f); lineTo(s * 0.52f, s * 0.38f)
                lineTo(s * 0.44f, s * 0.22f); close()
            }
            drawPath(folder, tint, style = st)
            // Wax seal
            drawCircle(tint, s * 0.04f, Offset(s * 0.48f, s * 0.38f))
        }
    }
    @Composable override fun CalendarMonth(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = classicStroke(s)
            drawRoundRect(tint, Offset(s * 0.12f, s * 0.2f), Size(s * 0.76f, s * 0.68f),
                cornerRadius = CornerRadius(s * 0.06f), style = st)
            drawLine(tint, Offset(s * 0.12f, s * 0.38f), Offset(s * 0.88f, s * 0.38f),
                strokeWidth = s * 0.05f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.35f, s * 0.12f), Offset(s * 0.35f, s * 0.28f),
                strokeWidth = s * 0.05f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.65f, s * 0.12f), Offset(s * 0.65f, s * 0.28f),
                strokeWidth = s * 0.05f, cap = StrokeCap.Round)
            // Gold border inner
            drawRoundRect(tint.copy(alpha = 0.3f), Offset(s * 0.18f, s * 0.42f),
                Size(s * 0.64f, s * 0.4f), cornerRadius = CornerRadius(s * 0.03f), style = thinClassic(s))
        }
    }
    @Composable override fun Notifications(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = classicStroke(s)
            val bell = Path().apply {
                moveTo(s * 0.25f, s * 0.62f)
                cubicTo(s * 0.25f, s * 0.3f, s * 0.35f, s * 0.15f, s * 0.5f, s * 0.15f)
                cubicTo(s * 0.65f, s * 0.15f, s * 0.75f, s * 0.3f, s * 0.75f, s * 0.62f)
                lineTo(s * 0.82f, s * 0.72f); lineTo(s * 0.18f, s * 0.72f); close()
            }
            drawPath(bell, tint, style = st)
            drawLine(tint, Offset(s * 0.5f, s * 0.08f), Offset(s * 0.5f, s * 0.15f),
                strokeWidth = s * 0.04f, cap = StrokeCap.Round)
            drawArc(tint, 0f, 180f, false, Offset(s * 0.4f, s * 0.75f),
                Size(s * 0.2f, s * 0.15f), style = st)
            drawCrown(Offset(s * 0.5f, s * 0.08f), s * 0.08f, s * 0.06f, tint.copy(alpha = 0.5f))
        }
    }
    @Composable override fun AttachMoney(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = classicStroke(s)
            drawLine(tint, Offset(s * 0.5f, s * 0.08f), Offset(s * 0.5f, s * 0.92f),
                strokeWidth = s * 0.05f, cap = StrokeCap.Round)
            val dollar = Path().apply {
                moveTo(s * 0.68f, s * 0.28f)
                cubicTo(s * 0.65f, s * 0.18f, s * 0.35f, s * 0.18f, s * 0.32f, s * 0.33f)
                cubicTo(s * 0.3f, s * 0.46f, s * 0.7f, s * 0.54f, s * 0.68f, s * 0.67f)
                cubicTo(s * 0.65f, s * 0.82f, s * 0.35f, s * 0.82f, s * 0.32f, s * 0.72f)
            }
            drawPath(dollar, tint, style = st)
            drawScrollwork(Offset(s * 0.72f, s * 0.2f), s * 0.04f, tint.copy(alpha = 0.4f))
        }
    }
    @Composable override fun Category(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = classicStroke(s)
            val gap = s * 0.06f; val cell = (s - gap * 3) / 2
            val positions = listOf(
                Offset(gap, gap), Offset(gap * 2 + cell, gap),
                Offset(gap, gap * 2 + cell), Offset(gap * 2 + cell, gap * 2 + cell)
            )
            positions.forEach { pos ->
                drawRoundRect(tint, pos, Size(cell, cell),
                    cornerRadius = CornerRadius(s * 0.05f), style = st)
            }
            // Ornament in each cell
            positions.forEach { pos ->
                drawScrollwork(Offset(pos.x + cell / 2, pos.y + cell / 2),
                    cell * 0.15f, tint.copy(alpha = 0.4f))
            }
        }
    }
    @Composable override fun Location(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = classicStroke(s)
            // Wardrobe body
            drawRoundRect(tint, Offset(s * 0.2f, s * 0.2f), Size(s * 0.6f, s * 0.65f),
                CornerRadius(s * 0.03f), style = st)
            // Top ornament arch
            val arch = Path().apply {
                moveTo(s * 0.3f, s * 0.2f)
                quadraticBezierTo(s * 0.5f, s * 0.08f, s * 0.7f, s * 0.2f)
            }
            drawPath(arch, tint, style = st)
            // Center divider
            drawLine(tint, Offset(s * 0.5f, s * 0.25f), Offset(s * 0.5f, s * 0.8f), st.width)
            // Door knobs
            drawCircle(tint, s * 0.025f, Offset(s * 0.44f, s * 0.52f))
            drawCircle(tint, s * 0.025f, Offset(s * 0.56f, s * 0.52f))
            // Feet
            drawLine(tint, Offset(s * 0.22f, s * 0.85f), Offset(s * 0.22f, s * 0.9f), st.width)
            drawLine(tint, Offset(s * 0.78f, s * 0.85f), Offset(s * 0.78f, s * 0.9f), st.width)
        }
    }
}

// ── Arrow Icons ─────────────────────────────────────────────────

private class ClassicArrowIcons : BaseArrowIcons() {
    @Composable override fun ArrowBack(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val shaft = Path().apply {
                moveTo(s * 0.75f, s * 0.50f)
                lineTo(s * 0.30f, s * 0.50f)
            }
            drawPath(shaft, tint, style = classicStroke(s))
            val chevron = Path().apply {
                moveTo(s * 0.48f, s * 0.28f)
                lineTo(s * 0.26f, s * 0.50f)
                lineTo(s * 0.48f, s * 0.72f)
            }
            drawPath(chevron, tint, style = classicStroke(s))
            val voluteTop = Path().apply {
                moveTo(s * 0.75f, s * 0.50f)
                cubicTo(s * 0.80f, s * 0.42f, s * 0.86f, s * 0.36f, s * 0.82f, s * 0.30f)
                cubicTo(s * 0.78f, s * 0.26f, s * 0.74f, s * 0.30f, s * 0.76f, s * 0.34f)
            }
            drawPath(voluteTop, tint.copy(alpha = 0.6f), style = thinClassic(s))
            val voluteBot = Path().apply {
                moveTo(s * 0.75f, s * 0.50f)
                cubicTo(s * 0.80f, s * 0.58f, s * 0.86f, s * 0.64f, s * 0.82f, s * 0.70f)
                cubicTo(s * 0.78f, s * 0.74f, s * 0.74f, s * 0.70f, s * 0.76f, s * 0.66f)
            }
            drawPath(voluteBot, tint.copy(alpha = 0.6f), style = thinClassic(s))
            val spiral = Path().apply {
                moveTo(s * 0.26f, s * 0.50f)
                cubicTo(s * 0.22f, s * 0.47f, s * 0.19f, s * 0.50f, s * 0.22f, s * 0.53f)
            }
            drawPath(spiral, tint.copy(alpha = 0.5f), style = thinClassic(s))
        }
    }
    @Composable override fun ArrowForward(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val arr = Path().apply {
                moveTo(s * 0.32f, s * 0.18f); lineTo(s * 0.72f, s * 0.5f); lineTo(s * 0.32f, s * 0.82f)
            }
            drawPath(arr, tint, style = classicStroke(s))
            drawScrollwork(Offset(s * 0.32f, s * 0.82f), s * 0.04f, tint.copy(alpha = 0.4f))
        }
    }
    @Composable override fun KeyboardArrowLeft(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val arr = Path().apply {
                moveTo(s * 0.65f, s * 0.2f); lineTo(s * 0.3f, s * 0.5f); lineTo(s * 0.65f, s * 0.8f)
            }
            drawPath(arr, tint, style = classicStroke(s))
        }
    }
    @Composable override fun KeyboardArrowRight(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val arr = Path().apply {
                moveTo(s * 0.35f, s * 0.2f); lineTo(s * 0.7f, s * 0.5f); lineTo(s * 0.35f, s * 0.8f)
            }
            drawPath(arr, tint, style = classicStroke(s))
        }
    }
    @Composable override fun ExpandMore(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val arr = Path().apply {
                moveTo(s * 0.2f, s * 0.32f); lineTo(s * 0.5f, s * 0.68f); lineTo(s * 0.8f, s * 0.32f)
            }
            drawPath(arr, tint, style = classicStroke(s))
            drawScrollwork(Offset(s * 0.5f, s * 0.68f), s * 0.04f, tint.copy(alpha = 0.4f))
        }
    }
    @Composable override fun ExpandLess(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val arr = Path().apply {
                moveTo(s * 0.2f, s * 0.68f); lineTo(s * 0.5f, s * 0.32f); lineTo(s * 0.8f, s * 0.68f)
            }
            drawPath(arr, tint, style = classicStroke(s))
            drawScrollwork(Offset(s * 0.5f, s * 0.32f), s * 0.04f, tint.copy(alpha = 0.4f))
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
            val s = size.minDimension; val st = classicStroke(s)
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
            drawScrollwork(Offset(s * 0.5f, s * 0.5f), s * 0.04f, tint.copy(alpha = 0.3f))
        }
    }
    @Composable override fun OpenInNew(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = classicStroke(s)
            val box = Path().apply {
                moveTo(s * 0.55f, s * 0.15f); lineTo(s * 0.15f, s * 0.15f)
                lineTo(s * 0.15f, s * 0.85f); lineTo(s * 0.85f, s * 0.85f)
                lineTo(s * 0.85f, s * 0.48f)
            }
            drawPath(box, tint, style = st)
            drawLine(tint, Offset(s * 0.48f, s * 0.52f), Offset(s * 0.85f, s * 0.15f),
                strokeWidth = s * 0.06f, cap = StrokeCap.Round)
            drawScrollwork(Offset(s * 0.85f, s * 0.15f), s * 0.04f, tint.copy(alpha = 0.5f))
        }
    }
}

// ── Status Icons ────────────────────────────────────────────────

private class ClassicStatusIcons : BaseStatusIcons() {
    @Composable override fun CheckCircle(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = classicStroke(s)
            // Gold-framed circle
            drawCircle(tint, s * 0.38f, Offset(s * 0.5f, s * 0.5f), style = st)
            drawCircle(tint, s * 0.33f, Offset(s * 0.5f, s * 0.5f), style = thinClassic(s))
            val check = Path().apply {
                moveTo(s * 0.3f, s * 0.5f); lineTo(s * 0.45f, s * 0.65f); lineTo(s * 0.72f, s * 0.35f)
            }
            drawPath(check, tint, style = st)
        }
    }
    @Composable override fun Warning(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = classicStroke(s)
            val tri = Path().apply {
                moveTo(s * 0.5f, s * 0.1f)
                quadraticBezierTo(s * 0.48f, s * 0.15f, s * 0.1f, s * 0.88f)
                quadraticBezierTo(s * 0.1f, s * 0.92f, s * 0.15f, s * 0.92f)
                lineTo(s * 0.85f, s * 0.92f)
                quadraticBezierTo(s * 0.9f, s * 0.92f, s * 0.9f, s * 0.88f)
                quadraticBezierTo(s * 0.52f, s * 0.15f, s * 0.5f, s * 0.1f)
            }
            drawPath(tri, tint, style = st)
            // Gold-edged exclamation
            drawLine(tint, Offset(s * 0.5f, s * 0.38f), Offset(s * 0.5f, s * 0.62f),
                strokeWidth = s * 0.07f, cap = StrokeCap.Round)
            drawCircle(tint, s * 0.04f, Offset(s * 0.5f, s * 0.76f))
        }
    }
    @Composable override fun Error(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = classicStroke(s)
            drawCircle(tint, s * 0.38f, Offset(s * 0.5f, s * 0.5f), style = st)
            drawCircle(tint, s * 0.33f, Offset(s * 0.5f, s * 0.5f), style = thinClassic(s))
            drawLine(tint, Offset(s * 0.35f, s * 0.35f), Offset(s * 0.65f, s * 0.65f),
                strokeWidth = s * 0.065f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.65f, s * 0.35f), Offset(s * 0.35f, s * 0.65f),
                strokeWidth = s * 0.065f, cap = StrokeCap.Round)
        }
    }
    @Composable override fun Info(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = classicStroke(s)
            drawCircle(tint, s * 0.38f, Offset(s * 0.5f, s * 0.5f), style = st)
            drawCircle(tint, s * 0.33f, Offset(s * 0.5f, s * 0.5f), style = thinClassic(s))
            // Serif "i"
            drawCircle(tint, s * 0.04f, Offset(s * 0.5f, s * 0.32f))
            drawLine(tint, Offset(s * 0.5f, s * 0.45f), Offset(s * 0.5f, s * 0.68f),
                strokeWidth = s * 0.065f, cap = StrokeCap.Round)
            // Serifs
            drawLine(tint, Offset(s * 0.43f, s * 0.45f), Offset(s * 0.57f, s * 0.45f),
                strokeWidth = s * 0.03f, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * 0.43f, s * 0.68f), Offset(s * 0.57f, s * 0.68f),
                strokeWidth = s * 0.03f, cap = StrokeCap.Round)
        }
    }
    @Composable override fun Visibility(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = classicStroke(s)
            val eye = Path().apply {
                moveTo(s * 0.05f, s * 0.5f)
                cubicTo(s * 0.2f, s * 0.22f, s * 0.8f, s * 0.22f, s * 0.95f, s * 0.5f)
                cubicTo(s * 0.8f, s * 0.78f, s * 0.2f, s * 0.78f, s * 0.05f, s * 0.5f)
            }
            drawPath(eye, tint, style = st)
            drawCircle(tint, s * 0.12f, Offset(s * 0.5f, s * 0.5f), style = st)
            drawCircle(tint, s * 0.05f, Offset(s * 0.5f, s * 0.5f))
            // Eyelash details
            for (i in 0..4) {
                val angle = -40f + i * 20f
                val rad = Math.toRadians(angle.toDouble())
                val ex = s * 0.5f + s * 0.32f * kotlin.math.cos(rad).toFloat()
                val ey = s * 0.5f - s * 0.32f * kotlin.math.sin(rad).toFloat()
                val ex2 = s * 0.5f + s * 0.38f * kotlin.math.cos(rad).toFloat()
                val ey2 = s * 0.5f - s * 0.38f * kotlin.math.sin(rad).toFloat()
                drawLine(tint, Offset(ex, ey), Offset(ex2, ey2),
                    strokeWidth = s * 0.02f, cap = StrokeCap.Round)
            }
        }
    }
    @Composable override fun VisibilityOff(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = classicStroke(s)
            val eye = Path().apply {
                moveTo(s * 0.05f, s * 0.5f)
                cubicTo(s * 0.2f, s * 0.22f, s * 0.8f, s * 0.22f, s * 0.95f, s * 0.5f)
                cubicTo(s * 0.8f, s * 0.78f, s * 0.2f, s * 0.78f, s * 0.05f, s * 0.5f)
            }
            drawPath(eye, tint, style = st)
            drawCircle(tint, s * 0.1f, Offset(s * 0.5f, s * 0.5f), style = st)
            drawLine(tint, Offset(s * 0.15f, s * 0.85f), Offset(s * 0.85f, s * 0.15f),
                strokeWidth = s * 0.065f, cap = StrokeCap.Round)
        }
    }

    @Composable override fun Help(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension; val st = classicStroke(s)
            // Double circle (classic style)
            drawCircle(tint, s * 0.38f, Offset(s * 0.5f, s * 0.5f), style = st)
            drawCircle(tint, s * 0.33f, Offset(s * 0.5f, s * 0.5f), style = thinClassic(s))
            // Elegant question mark with serifs
            val q = Path().apply {
                moveTo(s * 0.38f, s * 0.35f)
                cubicTo(s * 0.38f, s * 0.2f, s * 0.62f, s * 0.2f, s * 0.62f, s * 0.38f)
                cubicTo(s * 0.62f, s * 0.48f, s * 0.5f, s * 0.48f, s * 0.5f, s * 0.56f)
            }
            drawPath(q, tint, style = st)
            // Serif on question mark stem
            drawLine(tint, Offset(s * 0.45f, s * 0.56f), Offset(s * 0.55f, s * 0.56f),
                strokeWidth = s * 0.03f, cap = StrokeCap.Round)
            // Dot
            drawCircle(tint, s * 0.04f, Offset(s * 0.5f, s * 0.68f))
        }
    }
}

// ── Provider ────────────────────────────────────────────────────

class ClassicIconProvider : BaseSkinIconProvider() {
    override val navigation: NavigationIcons = ClassicNavigationIcons()
    override val action: ActionIcons = ClassicActionIcons()
    override val content: ContentIcons = ClassicContentIcons()
    override val arrow: ArrowIcons = ClassicArrowIcons()
    override val status: StatusIcons = ClassicStatusIcons()
}
