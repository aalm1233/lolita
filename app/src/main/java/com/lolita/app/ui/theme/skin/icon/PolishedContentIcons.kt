package com.lolita.app.ui.theme.skin.icon

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlin.math.cos
import kotlin.math.sin

/** Object silhouettes carry the family identity at the same optical weight as navigation. */
internal fun DrawScope.drawPolishedContentIcon(key: IconKey, style: SkinIconStyle, tint: Color) {
    when (key) {
        IconKey.Star, IconKey.StarBorder -> drawPath(
            contentStar(style), tint, style = if (key == IconKey.Star) Fill else style.stroke
        )
        IconKey.Image, IconKey.AddPhoto -> drawContentPicture(style, tint, key == IconKey.AddPhoto)
        IconKey.Camera -> drawContentCamera(style, tint)
        IconKey.Link, IconKey.LinkOff -> drawContentChain(style, tint, key == IconKey.LinkOff)
        IconKey.Palette -> drawContentPalette(style, tint)
        IconKey.FileOpen -> drawContentFolder(style, tint)
        IconKey.CalendarMonth -> drawContentCalendar(style, tint)
        IconKey.Notifications -> drawContentBell(style, tint)
        IconKey.AttachMoney -> drawContentMoney(style, tint)
        IconKey.Category -> drawContentCategory(style, tint)
        IconKey.Location -> drawContentLocation(style, tint)
        else -> error("Not a content icon: $key")
    }
}

private fun contentStar(style: SkinIconStyle): Path {
    val inner = when (style) {
        SkinIconStyle.SWEET -> 5.1f
        SkinIconStyle.GOTHIC -> 3.5f
        SkinIconStyle.CHINESE -> 4.3f
        SkinIconStyle.CLASSIC -> 4.6f
        SkinIconStyle.NAVY -> 5.6f
        SkinIconStyle.COUNTRY -> 4.9f
        SkinIconStyle.VICTORIAN -> 3.9f
    }
    val points = (0 until 10).map { i ->
        val angle = -Math.PI / 2 + i * Math.PI / 5
        val outer = if (style == SkinIconStyle.GOTHIC) 7.6f else 8.8f
        val r = if (i % 2 == 0) outer else inner
        val squash = when (style) {
            SkinIconStyle.NAVY -> 0.87f
            SkinIconStyle.VICTORIAN -> 1.05f
            else -> 1f
        }
        val horizontal = if (style == SkinIconStyle.VICTORIAN) 0.82f else 1f
        Offset(12f + cos(angle).toFloat() * r * horizontal, 12f + sin(angle).toFloat() * r * squash)
    }
    return Path().apply {
        // Soft families use a real rounded contour, rather than a different stroke join only.
        if (style == SkinIconStyle.SWEET || style == SkinIconStyle.COUNTRY) {
            val soft = if (style == SkinIconStyle.SWEET) 0.28f else 0.13f
            fun between(a: Offset, b: Offset, fraction: Float) = a + (b - a) * fraction
            val first = between(points.last(), points.first(), 1f - soft)
            moveTo(first.x, first.y)
            points.forEachIndexed { i, p ->
                val next = points[(i + 1) % 10]
                val after = between(p, next, soft)
                quadraticTo(p.x, p.y, after.x, after.y)
                val beforeNext = between(p, next, 1f - soft)
                lineTo(beforeNext.x, beforeNext.y)
            }
        } else if (style == SkinIconStyle.NAVY || style == SkinIconStyle.CHINESE) {
            // Broad sailor-star tips versus small, straight seal-like cuts.
            val cut = if (style == SkinIconStyle.NAVY) 0.25f else 0.1f
            points.forEachIndexed { i, p ->
                if (i % 2 == 0) {
                    val previous = points[(i + 9) % 10]
                    val next = points[(i + 1) % 10]
                    val before = p + (previous - p) * cut
                    val after = p + (next - p) * cut
                    if (i == 0) moveTo(before.x, before.y) else lineTo(before.x, before.y)
                    lineTo(after.x, after.y)
                } else lineTo(p.x, p.y)
            }
        } else {
            points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        }
        close()
    }
}

/** Picture/calendar frames: seven different proportions and load-bearing structures. */
private fun contentObjectFrame(style: SkinIconStyle, l: Float, t: Float, r: Float, b: Float): Path {
    val w = r - l
    val h = b - t
    return Path().apply {
        fun m(x: Float, y: Float) = moveTo(l + x * w, t + y * h)
        fun ln(x: Float, y: Float) = lineTo(l + x * w, t + y * h)
        fun q(cx: Float, cy: Float, x: Float, y: Float) = quadraticTo(l + cx * w, t + cy * h, l + x * w, t + y * h)
        when (style) {
            SkinIconStyle.SWEET -> {
                m(0.24f, 0f); q(0f, 0f, 0f, 0.28f); ln(0f, 0.72f)
                q(0f, 1f, 0.24f, 1f); ln(0.76f, 1f); q(1f, 1f, 1f, 0.72f)
                ln(1f, 0.28f); q(1f, 0f, 0.76f, 0f)
            }
            SkinIconStyle.GOTHIC -> {
                m(0f, 0.34f); ln(0.5f, 0f); ln(1f, 0.34f); ln(1f, 0.86f)
                ln(0.84f, 1f); ln(0.16f, 1f); ln(0f, 0.86f)
            }
            SkinIconStyle.CHINESE -> {
                m(0f, 0.05f); q(0.2f, 0.2f, 0.5f, 0.12f); q(0.8f, 0.2f, 1f, 0.05f)
                ln(0.88f, 0.33f); ln(0.88f, 1f); ln(0.12f, 1f); ln(0.12f, 0.33f)
            }
            SkinIconStyle.CLASSIC -> {
                m(0.1f, 0f); ln(0.9f, 0f); ln(0.9f, 0.85f); ln(1f, 1f)
                ln(0f, 1f); ln(0.1f, 0.85f)
            }
            SkinIconStyle.NAVY -> {
                m(0.2f, 0f); ln(0.8f, 0f); q(1f, 0f, 1f, 0.35f)
                ln(1f, 0.65f); q(1f, 1f, 0.8f, 1f); ln(0.2f, 1f)
                q(0f, 1f, 0f, 0.65f); ln(0f, 0.35f); q(0f, 0f, 0.2f, 0f)
            }
            SkinIconStyle.COUNTRY -> {
                m(0f, 0f); ln(1f, 0f); ln(0.94f, 0.18f); ln(0.88f, 0.18f)
                ln(0.88f, 0.82f); ln(1f, 1f); ln(0f, 1f); ln(0.12f, 0.82f)
                ln(0.12f, 0.18f); ln(0.06f, 0.18f)
            }
            SkinIconStyle.VICTORIAN -> {
                m(0.08f, 0.42f); q(0.08f, 0.08f, 0.5f, 0f); q(0.92f, 0.08f, 0.92f, 0.42f)
                ln(0.92f, 0.82f); q(1f, 0.92f, 0.85f, 1f); ln(0.15f, 1f)
                q(0f, 0.92f, 0.08f, 0.82f)
            }
        }
        close()
    }
}

private fun DrawScope.contentLine(style: SkinIconStyle, tint: Color, x1: Float, y1: Float, x2: Float, y2: Float) {
    drawLine(tint, Offset(x1, y1), Offset(x2, y2), style.stroke.width, cap = style.stroke.cap)
}

private fun DrawScope.drawContentPicture(style: SkinIconStyle, tint: Color, add: Boolean) {
    val top = if (style == SkinIconStyle.NAVY) 6f else 3.5f
    val frame = contentObjectFrame(style, 3.2f, top, 20.8f, 20.5f)
    if (add) {
        // A genuinely open upper-right contour keeps the plus independent of the picture.
        // Clip only the frame; landscape and functional mark retain their full 1.7-unit stroke.
        clipRect(left = 13.4f, bottom = 12f, clipOp = ClipOp.Difference) {
            drawPath(frame, tint, style = style.stroke)
        }
        contentLine(style, tint, 15f, 6.2f, 20.5f, 6.2f)
        contentLine(style, tint, 17.75f, 3.45f, 17.75f, 8.95f)
    } else drawPath(frame, tint, style = style.stroke)
    val mountains = Path().apply {
        moveTo(6f, 17.7f)
        when (style) {
            SkinIconStyle.SWEET -> { quadraticTo(8.4f, 12f, 10.8f, 15.4f); quadraticTo(14.2f, 10.8f, 18f, 17.7f) }
            SkinIconStyle.COUNTRY -> { cubicTo(8f, 14.4f, 10f, 13.5f, 12f, 16.3f); quadraticTo(15.2f, 13.2f, 18f, 17.7f) }
            SkinIconStyle.GOTHIC -> { lineTo(10.4f, 11.8f); lineTo(13.3f, 16f); lineTo(15.5f, 13.6f); lineTo(18f, 17.7f) }
            SkinIconStyle.CHINESE -> { lineTo(9f, 14.1f); lineTo(10.8f, 15.1f); lineTo(14.1f, 11.7f); lineTo(18f, 17.7f) }
            SkinIconStyle.CLASSIC -> { lineTo(10f, 12.8f); lineTo(13.4f, 16.6f); lineTo(15.6f, 14.3f); lineTo(18f, 17.7f) }
            SkinIconStyle.NAVY -> { quadraticTo(8f, 15.3f, 10f, 17.1f); quadraticTo(12f, 19f, 14f, 17.1f); quadraticTo(16f, 15.3f, 18f, 17.7f) }
            SkinIconStyle.VICTORIAN -> { lineTo(9f, 15f); quadraticTo(10.8f, 12f, 12.2f, 14.4f); lineTo(15f, 12f); lineTo(18f, 17.7f) }
        }
    }
    drawPath(mountains, tint, style = style.stroke)
    if (!add) drawCircle(tint, 1.1f, Offset(if (style == SkinIconStyle.GOTHIC) 12f else 8f, if (style == SkinIconStyle.NAVY) 10f else 8.4f))
}

private fun DrawScope.drawContentCamera(style: SkinIconStyle, tint: Color) {
    val body = Path().apply {
        when (style) {
            SkinIconStyle.SWEET -> {
                moveTo(6f, 7f); quadraticTo(3.2f, 7f, 3.2f, 10f); lineTo(3.2f, 17f)
                quadraticTo(3.2f, 20f, 6.2f, 20f); lineTo(17.8f, 20f); quadraticTo(20.8f, 20f, 20.8f, 17f)
                lineTo(20.8f, 10f); quadraticTo(20.8f, 7f, 18f, 7f); lineTo(16.2f, 7f)
                quadraticTo(15.7f, 3.7f, 12f, 3.7f); quadraticTo(8.3f, 3.7f, 7.8f, 7f)
            }
            SkinIconStyle.GOTHIC -> {
                moveTo(3.2f, 9f); lineTo(5.2f, 7f); lineTo(8f, 7f); lineTo(12f, 3.4f)
                lineTo(16f, 7f); lineTo(18.8f, 7f); lineTo(20.8f, 9f); lineTo(20.8f, 17.8f)
                lineTo(18.6f, 20f); lineTo(5.4f, 20f); lineTo(3.2f, 17.8f)
            }
            SkinIconStyle.CHINESE -> {
                moveTo(3.2f, 6.5f); quadraticTo(7f, 8f, 8f, 6.5f); lineTo(9f, 4.3f)
                lineTo(15f, 4.3f); lineTo(16f, 6.5f); quadraticTo(17f, 8f, 20.8f, 6.5f)
                lineTo(19.3f, 10f); lineTo(19.3f, 20f); lineTo(4.7f, 20f); lineTo(4.7f, 10f)
            }
            SkinIconStyle.CLASSIC -> {
                moveTo(3.5f, 7.7f); lineTo(8f, 7.7f); lineTo(8f, 4f); lineTo(16f, 4f)
                lineTo(16f, 7.7f); lineTo(20.5f, 7.7f); lineTo(20.5f, 19.8f); lineTo(3.5f, 19.8f)
            }
            SkinIconStyle.NAVY -> {
                moveTo(3.2f, 9f); lineTo(7f, 9f); lineTo(8.3f, 6f); lineTo(15.7f, 6f)
                lineTo(17f, 9f); lineTo(20.8f, 9f); lineTo(20.8f, 17.7f)
                quadraticTo(20.8f, 19.5f, 19f, 19.5f); lineTo(5f, 19.5f); quadraticTo(3.2f, 19.5f, 3.2f, 17.7f)
            }
            SkinIconStyle.COUNTRY -> {
                moveTo(3.2f, 6.8f); lineTo(8f, 6.8f); lineTo(8.5f, 4.5f); lineTo(15.5f, 4.5f)
                lineTo(16f, 6.8f); lineTo(20.8f, 6.8f); lineTo(20f, 9.2f); lineTo(19.5f, 19.7f)
                lineTo(4.5f, 19.7f); lineTo(4f, 9.2f)
            }
            SkinIconStyle.VICTORIAN -> {
                moveTo(4f, 10f); quadraticTo(4f, 7f, 7.5f, 7f); quadraticTo(8f, 3.3f, 12f, 3.3f)
                quadraticTo(16f, 3.3f, 16.5f, 7f); quadraticTo(20f, 7f, 20f, 10f)
                lineTo(20f, 17.2f); quadraticTo(21f, 20f, 17f, 20f); lineTo(7f, 20f)
                quadraticTo(3f, 20f, 4f, 17.2f)
            }
        }
        close()
    }
    drawPath(body, tint, style = style.stroke)
    if (style == SkinIconStyle.GOTHIC || style == SkinIconStyle.CHINESE) {
        drawPath(contentObjectFrame(style, 8f, 9.1f, 16f, 17.8f), tint, style = style.stroke)
    } else drawCircle(tint, if (style == SkinIconStyle.NAVY) 3.3f else 3.9f, Offset(12f, 13.5f), style = style.stroke)
}

private fun DrawScope.drawContentChain(style: SkinIconStyle, tint: Color, off: Boolean) {
    // Ring profiles are constructed in a diagonal local coordinate system.
    fun ring(upper: Boolean): Path {
        val p = Path()
        fun point(u: Float, v: Float): Offset {
            val d = 0.70710677f
            return Offset(12f + (u - v) * d, 12f + (u + v) * d)
        }
        fun m(u: Float, v: Float) { val a = point(u, v); p.moveTo(a.x, a.y) }
        fun l(u: Float, v: Float) { val a = point(u, v); p.lineTo(a.x, a.y) }
        fun q(u: Float, v: Float, x: Float, y: Float) { val a = point(u, v); val b = point(x, y); p.quadraticTo(a.x, a.y, b.x, b.y) }
        val sign = if (upper) -1f else 1f
        val near = if (off) 2.8f else 1.5f
        val far = 8.7f
        val width = when (style) { SkinIconStyle.SWEET -> 3.2f; SkinIconStyle.NAVY -> 2.3f; SkinIconStyle.VICTORIAN -> 2.4f; else -> 2.8f }
        m(-width, sign * near)
        when (style) {
            SkinIconStyle.GOTHIC -> { l(-width, sign * 6.6f); l(0f, sign * far); l(width, sign * 6.6f) }
            SkinIconStyle.CHINESE -> { l(-width, sign * 7.3f); l(-1.4f, sign * far); l(width, sign * far); l(width, sign * 6.2f) }
            SkinIconStyle.CLASSIC -> { l(-width, sign * far); l(width, sign * far) }
            SkinIconStyle.COUNTRY -> { q(-width - 0.8f, sign * far, 0f, sign * far); q(width + 0.8f, sign * far, width, sign * 5.6f) }
            SkinIconStyle.VICTORIAN -> { q(-width, sign * 7.6f, 0f, sign * far); q(width, sign * 7.6f, width, sign * 5.6f) }
            else -> { l(-width, sign * 5.6f); q(-width, sign * far, 0f, sign * far); q(width, sign * far, width, sign * 5.6f) }
        }
        l(width, sign * near)
        return p
    }
    drawPath(ring(true), tint, style = style.stroke)
    drawPath(ring(false), tint, style = style.stroke)
    if (off) {
        contentLine(style, tint, 8.5f, 15.5f, 10.1f, 13.9f)
        contentLine(style, tint, 13.9f, 10.1f, 15.5f, 8.5f)
        contentLine(style, tint, 4f, 4f, 20f, 20f)
    } else contentLine(style, tint, 8.8f, 15.2f, 15.2f, 8.8f)
}

private fun DrawScope.drawContentPalette(style: SkinIconStyle, tint: Color) {
    val palette = Path().apply {
        when (style) {
            SkinIconStyle.SWEET -> {
                moveTo(12f, 3.5f); cubicTo(7f, 2.2f, 3.3f, 6.8f, 3.3f, 11f)
                cubicTo(3.3f, 17f, 8f, 21f, 12.5f, 20.5f); quadraticTo(16f, 20f, 13.5f, 17f)
                quadraticTo(13f, 14.5f, 17.5f, 15.5f); cubicTo(23f, 15.5f, 21f, 5f, 12f, 3.5f)
            }
            SkinIconStyle.GOTHIC -> {
                moveTo(12f, 3.3f); lineTo(5f, 6f); lineTo(3.3f, 13f); lineTo(7f, 20.5f)
                lineTo(14.5f, 20.5f); lineTo(12.5f, 15.5f); lineTo(19f, 15.5f); lineTo(20.7f, 9f)
            }
            SkinIconStyle.CHINESE -> {
                moveTo(4f, 5f); quadraticTo(12f, 2f, 20.5f, 4.5f); lineTo(19f, 15f)
                lineTo(14f, 15f); lineTo(14f, 20f); lineTo(5f, 20f); lineTo(3.5f, 8f)
            }
            SkinIconStyle.CLASSIC -> {
                moveTo(7f, 3.5f); lineTo(17f, 3.5f); quadraticTo(20.5f, 3.5f, 20.5f, 7f)
                lineTo(20.5f, 15f); lineTo(14.5f, 15f); lineTo(14.5f, 20.5f); lineTo(7f, 20.5f)
                quadraticTo(3.5f, 20.5f, 3.5f, 17f); lineTo(3.5f, 7f); quadraticTo(3.5f, 3.5f, 7f, 3.5f)
            }
            SkinIconStyle.NAVY -> {
                moveTo(8f, 5f); lineTo(17f, 5f); quadraticTo(20.7f, 5f, 20.7f, 9f)
                lineTo(20.7f, 15.5f); lineTo(15.5f, 15.5f); quadraticTo(11f, 15.5f, 14f, 19f)
                lineTo(8f, 19f); quadraticTo(3.3f, 19f, 3.3f, 12f); quadraticTo(3.3f, 5f, 8f, 5f)
            }
            SkinIconStyle.COUNTRY -> {
                moveTo(7f, 5f); cubicTo(2f, 9f, 3f, 16f, 7f, 19f); quadraticTo(10f, 21f, 14f, 20f)
                quadraticTo(17f, 19f, 14f, 16.5f); quadraticTo(14f, 14f, 18f, 14.8f)
                cubicTo(23f, 13f, 19f, 3f, 14f, 3.5f); quadraticTo(10f, 3.5f, 7f, 5f)
            }
            SkinIconStyle.VICTORIAN -> {
                moveTo(12f, 3.2f); cubicTo(6f, 3.2f, 4f, 7f, 4f, 12f)
                cubicTo(4f, 18f, 8f, 21f, 12.5f, 20.5f); quadraticTo(15f, 20f, 13f, 17.5f)
                quadraticTo(12f, 15f, 15f, 14.5f); quadraticTo(21f, 16f, 20f, 10f)
                cubicTo(20f, 6f, 17f, 3.2f, 12f, 3.2f)
            }
        }
        close()
    }
    drawPath(palette, tint, style = style.stroke)
    val spots = when (style) {
        SkinIconStyle.NAVY -> listOf(Offset(7f, 10f), Offset(11.5f, 8.5f), Offset(16f, 10f))
        SkinIconStyle.CHINESE, SkinIconStyle.CLASSIC -> listOf(Offset(7.5f, 8f), Offset(12f, 7f), Offset(16.5f, 8f), Offset(7.5f, 13f))
        else -> listOf(Offset(7f, 11f), Offset(9f, 7f), Offset(14f, 6.8f), Offset(17f, 10f))
    }
    spots.forEach { drawCircle(tint, 1f, it) }
}

private fun DrawScope.drawContentFolder(style: SkinIconStyle, tint: Color) {
    val back = Path().apply {
        moveTo(3.5f, 18.7f)
        when (style) {
            SkinIconStyle.SWEET -> { lineTo(3.5f, 7f); quadraticTo(3.5f, 4.2f, 6f, 4.2f); lineTo(9f, 4.2f); quadraticTo(11f, 4.2f, 12f, 7f) }
            SkinIconStyle.GOTHIC -> { lineTo(3.5f, 7f); lineTo(6f, 4.2f); lineTo(9f, 4.2f); lineTo(12f, 7f) }
            SkinIconStyle.CHINESE -> { lineTo(4.5f, 7f); lineTo(3.5f, 4.5f); lineTo(9f, 5.3f); lineTo(12f, 7f) }
            SkinIconStyle.CLASSIC -> { lineTo(3.5f, 4.5f); lineTo(10f, 4.5f); lineTo(10f, 7f) }
            SkinIconStyle.NAVY -> { lineTo(3.5f, 6f); lineTo(10f, 6f); lineTo(12f, 8f) }
            SkinIconStyle.COUNTRY -> { lineTo(4.3f, 4.5f); lineTo(9f, 4.5f); lineTo(11.5f, 7f) }
            SkinIconStyle.VICTORIAN -> { lineTo(4.5f, 8f); quadraticTo(4.5f, 3.2f, 9f, 4.3f); quadraticTo(11f, 4.3f, 12f, 7f) }
        }
        lineTo(19f, if (style == SkinIconStyle.NAVY) 8f else 7f)
        lineTo(20.5f, 9.5f)
    }
    drawPath(back, tint, style = style.stroke)
    val front = Path().apply {
        when (style) {
            SkinIconStyle.SWEET -> { moveTo(3.5f, 19.5f); lineTo(5.5f, 12f); quadraticTo(6f, 10.5f, 8f, 10.5f); lineTo(20.5f, 10.5f); lineTo(18.7f, 18f); quadraticTo(18f, 19.5f, 16f, 19.5f) }
            SkinIconStyle.GOTHIC -> { moveTo(3.5f, 19.5f); lineTo(6f, 10.5f); lineTo(18.5f, 10.5f); lineTo(20.5f, 12.5f); lineTo(18f, 19.5f) }
            SkinIconStyle.CHINESE -> { moveTo(3.5f, 19.5f); lineTo(5.5f, 12f); lineTo(4.8f, 10.5f); quadraticTo(12f, 12f, 20.5f, 10.5f); lineTo(18.5f, 19.5f) }
            SkinIconStyle.CLASSIC -> { moveTo(3.5f, 19.5f); lineTo(6.5f, 10.5f); lineTo(20.5f, 10.5f); lineTo(17.5f, 19.5f) }
            SkinIconStyle.NAVY -> { moveTo(3.5f, 19f); lineTo(5.7f, 11.5f); lineTo(20.5f, 11.5f); lineTo(18.3f, 19f) }
            SkinIconStyle.COUNTRY -> { moveTo(3.5f, 19.5f); lineTo(5f, 10.5f); lineTo(20.5f, 10.5f); lineTo(19f, 19.5f) }
            SkinIconStyle.VICTORIAN -> { moveTo(3.5f, 19.5f); lineTo(6f, 12f); quadraticTo(13f, 8.7f, 20.5f, 11.2f); lineTo(18.3f, 18f); quadraticTo(18f, 19.5f, 16f, 19.5f) }
        }
        close()
    }
    drawPath(front, tint, style = style.stroke)
}

private fun DrawScope.drawContentCalendar(style: SkinIconStyle, tint: Color) {
    val top = if (style == SkinIconStyle.NAVY) 6f else 4.5f
    drawPath(contentObjectFrame(style, 3.5f, top, 20.5f, 20.5f), tint, style = style.stroke)
    val binding = if (style == SkinIconStyle.GOTHIC || style == SkinIconStyle.VICTORIAN) 8.4f else 6.6f
    for (x in listOf(8.1f, 15.9f)) contentLine(style, tint, x, 3.3f, x, binding)
    contentLine(style, tint, 6f, 10.4f, 18f, 10.4f)
    for (x in listOf(8f, 12f, 16f)) {
        if (style == SkinIconStyle.CHINESE || style == SkinIconStyle.CLASSIC) {
            contentLine(style, tint, x - 0.5f, 14f, x + 0.5f, 14f)
            contentLine(style, tint, x - 0.5f, 17.5f, x + 0.5f, 17.5f)
        } else {
            drawCircle(tint, 0.8f, Offset(x, 14f)); drawCircle(tint, 0.8f, Offset(x, 17.5f))
        }
    }
}

private fun DrawScope.drawContentBell(style: SkinIconStyle, tint: Color) {
    val bell = Path().apply {
        when (style) {
            SkinIconStyle.SWEET -> {
                moveTo(3.5f, 17.2f); quadraticTo(6f, 16f, 5.5f, 11f); cubicTo(4.5f, 3f, 19.5f, 3f, 18.5f, 11f)
                quadraticTo(18f, 16f, 20.5f, 17.2f); quadraticTo(12f, 20f, 3.5f, 17.2f)
            }
            SkinIconStyle.GOTHIC -> {
                moveTo(3.5f, 17.8f); lineTo(6.3f, 14.5f); lineTo(6.3f, 8.2f); lineTo(12f, 4.2f)
                lineTo(17.7f, 8.2f); lineTo(17.7f, 14.5f); lineTo(20.5f, 17.8f)
            }
            SkinIconStyle.CHINESE -> {
                moveTo(3.5f, 16.5f); quadraticTo(6f, 18.5f, 7f, 14f); lineTo(7f, 8f); lineTo(9f, 5f)
                lineTo(15f, 5f); lineTo(17f, 8f); lineTo(17f, 14f); quadraticTo(18f, 18.5f, 20.5f, 16.5f)
                lineTo(19f, 19f); lineTo(5f, 19f)
            }
            SkinIconStyle.CLASSIC -> {
                moveTo(4f, 18f); lineTo(6.5f, 15f); lineTo(6.5f, 9f); quadraticTo(6.5f, 4.5f, 12f, 4.5f)
                quadraticTo(17.5f, 4.5f, 17.5f, 9f); lineTo(17.5f, 15f); lineTo(20f, 18f)
            }
            SkinIconStyle.NAVY -> {
                moveTo(3.5f, 17.5f); lineTo(6.5f, 15f); lineTo(8f, 6f); lineTo(16f, 6f)
                lineTo(17.5f, 15f); lineTo(20.5f, 17.5f)
            }
            SkinIconStyle.COUNTRY -> {
                moveTo(3.5f, 17.8f); quadraticTo(6f, 14.5f, 7f, 8f); quadraticTo(12f, 3f, 17f, 8f)
                quadraticTo(18f, 14.5f, 20.5f, 17.8f); quadraticTo(12f, 19f, 3.5f, 17.8f)
            }
            SkinIconStyle.VICTORIAN -> {
                moveTo(4f, 18f); quadraticTo(7.5f, 17f, 7.5f, 13f); lineTo(7.5f, 8f)
                quadraticTo(7.5f, 4.5f, 12f, 4.5f); quadraticTo(16.5f, 4.5f, 16.5f, 8f)
                lineTo(16.5f, 13f); quadraticTo(16.5f, 17f, 20f, 18f); quadraticTo(12f, 19.8f, 4f, 18f)
            }
        }
        close()
    }
    drawPath(bell, tint, style = style.stroke)
    contentLine(style, tint, 12f, 3.2f, 12f, if (style == SkinIconStyle.NAVY) 6f else 4.5f)
    contentLine(style, tint, 10.7f, 20.7f, 13.3f, 20.7f)
}

private fun DrawScope.drawContentMoney(style: SkinIconStyle, tint: Color) {
    val coin = Path().apply {
        when (style) {
            SkinIconStyle.SWEET -> { moveTo(12f, 3.2f); cubicTo(0.3f, 3.2f, 0.3f, 20.8f, 12f, 20.8f); cubicTo(23.7f, 20.8f, 23.7f, 3.2f, 12f, 3.2f) }
            SkinIconStyle.GOTHIC -> { moveTo(12f, 3.2f); lineTo(18.3f, 5.7f); lineTo(20.8f, 12f); lineTo(18.3f, 18.3f); lineTo(12f, 20.8f); lineTo(5.7f, 18.3f); lineTo(3.2f, 12f); lineTo(5.7f, 5.7f) }
            SkinIconStyle.CHINESE -> { moveTo(5f, 3.5f); lineTo(19f, 3.5f); lineTo(20.5f, 5f); lineTo(20.5f, 19f); lineTo(19f, 20.5f); lineTo(5f, 20.5f); lineTo(3.5f, 19f); lineTo(3.5f, 5f) }
            SkinIconStyle.CLASSIC -> { moveTo(7f, 3.5f); lineTo(17f, 3.5f); quadraticTo(20.5f, 3.5f, 20.5f, 7f); lineTo(20.5f, 17f); quadraticTo(20.5f, 20.5f, 17f, 20.5f); lineTo(7f, 20.5f); quadraticTo(3.5f, 20.5f, 3.5f, 17f); lineTo(3.5f, 7f); quadraticTo(3.5f, 3.5f, 7f, 3.5f) }
            SkinIconStyle.NAVY -> { moveTo(8f, 5f); lineTo(16f, 5f); cubicTo(22.5f, 5f, 22.5f, 19f, 16f, 19f); lineTo(8f, 19f); cubicTo(1.5f, 19f, 1.5f, 5f, 8f, 5f) }
            SkinIconStyle.COUNTRY -> { moveTo(12f, 3.5f); cubicTo(6f, 2.5f, 3f, 7f, 3.5f, 13f); cubicTo(4f, 19f, 9f, 22f, 15f, 20f); cubicTo(22f, 18f, 23f, 5f, 12f, 3.5f) }
            SkinIconStyle.VICTORIAN -> { moveTo(12f, 3.2f); cubicTo(6f, 3.2f, 4.5f, 7f, 4.5f, 12f); cubicTo(4.5f, 17f, 6f, 20.8f, 12f, 20.8f); cubicTo(18f, 20.8f, 19.5f, 17f, 19.5f, 12f); cubicTo(19.5f, 7f, 18f, 3.2f, 12f, 3.2f) }
        }
        close()
    }
    drawPath(coin, tint, style = style.stroke)
    val y = if (style == SkinIconStyle.NAVY) 8f else 7f
    val yuan = Path().apply { moveTo(8.5f, y); lineTo(12f, 11.5f); lineTo(15.5f, y); moveTo(12f, 11.5f); lineTo(12f, 17.4f) }
    drawPath(yuan, tint, style = style.stroke)
    contentLine(style, tint, 8.5f, 11.8f, 15.5f, 11.8f)
    contentLine(style, tint, 8.5f, 14.8f, 15.5f, 14.8f)
}

private fun DrawScope.drawContentCategory(style: SkinIconStyle, tint: Color) {
    // Three distinct objects keep the grouping meaning while using each family's object shapes.
    val top = contentObjectFrame(style, 8f, 3.2f, 16f, 10f)
    val left = contentObjectFrame(style, 3.3f, 13f, 10.2f, 20.5f)
    drawPath(top, tint, style = style.stroke)
    drawPath(left, tint, style = style.stroke)
    if (style == SkinIconStyle.GOTHIC || style == SkinIconStyle.CHINESE || style == SkinIconStyle.CLASSIC) {
        val diamond = Path().apply { moveTo(17f, 12.8f); lineTo(20.5f, 16.7f); lineTo(17f, 20.5f); lineTo(13.5f, 16.7f); close() }
        drawPath(diamond, tint, style = style.stroke)
    } else {
        drawCircle(tint, 3.4f, Offset(17f, 16.7f), style = style.stroke)
    }
}

private fun DrawScope.drawContentLocation(style: SkinIconStyle, tint: Color) {
    val pin = Path().apply {
        moveTo(12f, 20.7f)
        when (style) {
            SkinIconStyle.SWEET -> { cubicTo(9f, 18f, 4.3f, 14.7f, 4.3f, 10f); cubicTo(4.3f, 1f, 19.7f, 1f, 19.7f, 10f); cubicTo(19.7f, 14.7f, 15f, 18f, 12f, 20.7f) }
            SkinIconStyle.GOTHIC -> { lineTo(5.5f, 12.7f); lineTo(5.5f, 7.2f); lineTo(12f, 3.3f); lineTo(18.5f, 7.2f); lineTo(18.5f, 12.7f) }
            SkinIconStyle.CHINESE -> { lineTo(5.2f, 13.2f); lineTo(5.2f, 7.2f); lineTo(3.7f, 4f); quadraticTo(12f, 6.2f, 20.3f, 4f); lineTo(18.8f, 7.2f); lineTo(18.8f, 13.2f) }
            SkinIconStyle.CLASSIC -> { lineTo(6f, 14f); lineTo(6f, 3.8f); lineTo(18f, 3.8f); lineTo(18f, 14f) }
            SkinIconStyle.NAVY -> { lineTo(12f, 16.4f); cubicTo(1f, 16.4f, 1f, 3.8f, 12f, 3.8f); cubicTo(23f, 3.8f, 23f, 16.4f, 12f, 16.4f) }
            SkinIconStyle.COUNTRY -> { cubicTo(8f, 16.5f, 4.5f, 14f, 5f, 9f); cubicTo(5.5f, 2f, 18.5f, 2f, 19f, 9f); cubicTo(19.5f, 14f, 16f, 16.5f, 12f, 20.7f) }
            SkinIconStyle.VICTORIAN -> { cubicTo(10f, 17.3f, 6f, 15f, 6f, 10f); cubicTo(6f, 1f, 18f, 1f, 18f, 10f); cubicTo(18f, 15f, 14f, 17.3f, 12f, 20.7f) }
        }
        close()
    }
    drawPath(pin, tint, style = style.stroke)
    if (style == SkinIconStyle.GOTHIC || style == SkinIconStyle.CHINESE) {
        val center = Path().apply { moveTo(12f, 7.2f); lineTo(15f, 10.2f); lineTo(12f, 13.2f); lineTo(9f, 10.2f); close() }
        drawPath(center, tint, style = style.stroke)
    } else drawCircle(tint, 2.6f, Offset(12f, 9.8f), style = style.stroke)
}
