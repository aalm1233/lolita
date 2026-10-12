package com.lolita.app.ui.theme.skin.icon

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
        SkinIconStyle.GOTHIC -> 3.8f
        SkinIconStyle.CHINESE -> 4.3f
        SkinIconStyle.CLASSIC -> 4.6f
        SkinIconStyle.NAVY -> 5.6f
        SkinIconStyle.COUNTRY -> 4.9f
        SkinIconStyle.VICTORIAN -> 3.9f
    }
    val points = (0 until 10).map { i ->
        val angle = -Math.PI / 2 + i * Math.PI / 5
        val outer = when (style) {
            SkinIconStyle.GOTHIC -> if (i == 0) 9f else 8f
            SkinIconStyle.COUNTRY -> if (i == 2 || i == 6) 8.1f else 9f
            SkinIconStyle.CHINESE -> if (i == 0) 8f else 9f
            else -> 8.8f
        }
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
        } else if (style == SkinIconStyle.VICTORIAN) {
            // Jewel-like tapered rays, curved inward instead of straight star edges.
            moveTo(points.first().x, points.first().y)
            for (i in 1..10) {
                val p = points[i % 10]
                val previous = points[(i - 1) % 10]
                val mid = (p + previous) * 0.5f
                val control = Offset(12f, 12f) + (mid - Offset(12f, 12f)) * 0.8f
                quadraticTo(control.x, control.y, p.x, p.y)
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
    val frame = when (style) {
        SkinIconStyle.NAVY -> Path().apply {
            moveTo(12f, 3.5f); cubicTo(0.7f, 3.5f, 0.7f, 20.5f, 12f, 20.5f)
            cubicTo(23.3f, 20.5f, 23.3f, 3.5f, 12f, 3.5f); close()
        }
        SkinIconStyle.CLASSIC -> Path().apply {
            moveTo(4f, 3.5f); lineTo(20f, 3.5f); lineTo(20f, 21f); lineTo(4f, 21f); close()
            moveTo(4f, 18f); lineTo(20f, 18f)
        }
        SkinIconStyle.CHINESE -> Path().apply {
            moveTo(6f, 4.5f); lineTo(6f, 20f); lineTo(18f, 20f); lineTo(18f, 4.5f)
            moveTo(3.5f, 4.5f); lineTo(20.5f, 4.5f)
            moveTo(3.5f, 20f); lineTo(20.5f, 20f)
        }
        else -> contentObjectFrame(style, 3.2f, top, 20.8f, 20.5f)
    }
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
            SkinIconStyle.CLASSIC -> { moveTo(6f, 15.5f); lineTo(10f, 11.8f); lineTo(13.4f, 15f); lineTo(15.6f, 12.8f); lineTo(18f, 15.5f) }
            SkinIconStyle.NAVY -> { quadraticTo(8f, 15.3f, 10f, 17.1f); quadraticTo(12f, 19f, 14f, 17.1f); quadraticTo(16f, 15.3f, 18f, 17.7f) }
            SkinIconStyle.VICTORIAN -> { lineTo(9f, 15f); quadraticTo(10.8f, 12f, 12.2f, 14.4f); lineTo(15f, 12f); lineTo(18f, 17.7f) }
        }
    }
    drawPath(mountains, tint, style = style.stroke)
    if (!add) drawCircle(tint, 1.1f, Offset(if (style == SkinIconStyle.GOTHIC) 12f else 8f, if (style == SkinIconStyle.NAVY) 10f else 8.4f))
}

private fun DrawScope.drawContentCamera(style: SkinIconStyle, tint: Color) {
    contentPath(style, tint) {
        when (style) {
            SkinIconStyle.SWEET -> { // Instant camera: large soft body and visible print slot.
                moveTo(7f, 4f); lineTo(17f, 4f); quadraticTo(20f, 4f, 20f, 8f)
                lineTo(21f, 18f); quadraticTo(21f, 21f, 18f, 21f); lineTo(6f, 21f)
                quadraticTo(3f, 21f, 3f, 18f); lineTo(4f, 8f); quadraticTo(4f, 4f, 7f, 4f); close()
                moveTo(8f, 17.5f); lineTo(16f, 17.5f)
            }
            SkinIconStyle.GOTHIC -> { // Cut-shoulder body and diamond aperture.
                moveTo(3.5f, 9f); lineTo(7f, 9f); lineTo(10f, 4f); lineTo(14f, 4f)
                lineTo(17f, 9f); lineTo(20.5f, 9f); lineTo(20.5f, 18f)
                lineTo(18f, 20f); lineTo(6f, 20f); lineTo(3.5f, 18f); close()
                moveTo(12f, 9f); lineTo(16f, 13f); lineTo(12f, 17f); lineTo(8f, 13f); close()
            }
            SkinIconStyle.CHINESE -> { // Folding box camera, squared lens board and slanted bellows.
                moveTo(3.5f, 7f); lineTo(8f, 7f); lineTo(10f, 10f); lineTo(19f, 8f)
                lineTo(20.5f, 19f); lineTo(10f, 19f); lineTo(8f, 16f); lineTo(3.5f, 16f); close()
                moveTo(10f, 10f); lineTo(10f, 19f)
                moveTo(5f, 7f); lineTo(5f, 4f); lineTo(8f, 4f)
            }
            SkinIconStyle.CLASSIC -> { // Twin-lens reflex, vertically stacked working lenses.
                moveTo(6f, 20.5f); lineTo(6f, 5.5f); lineTo(8f, 3.5f); lineTo(16f, 3.5f)
                lineTo(18f, 5.5f); lineTo(18f, 20.5f); close()
            }
            SkinIconStyle.NAVY -> { // Waterproof camera with side grips and porthole lens.
                moveTo(3f, 8f); lineTo(8f, 8f); lineTo(9f, 5f); lineTo(15f, 5f)
                lineTo(16f, 8f); lineTo(21f, 8f); lineTo(21f, 19f); lineTo(3f, 19f); close()
                moveTo(6f, 11f); lineTo(6f, 16f); moveTo(18f, 11f); lineTo(18f, 16f)
            }
            SkinIconStyle.COUNTRY -> { // Wooden box camera on a folding stand.
                moveTo(4f, 5f); lineTo(20f, 5f); lineTo(19f, 16f); lineTo(5f, 16f); close()
                moveTo(12f, 16f); lineTo(7f, 21f); moveTo(12f, 16f); lineTo(17f, 21f)
            }
            SkinIconStyle.VICTORIAN -> { // Bellows camera: small rear standard, large front lens.
                moveTo(3.5f, 7f); lineTo(6f, 7f); lineTo(6f, 17f); lineTo(3.5f, 17f); close()
                moveTo(6f, 9f); lineTo(10f, 6f); lineTo(15f, 9f)
                moveTo(6f, 15f); lineTo(10f, 18f); lineTo(15f, 15f)
                moveTo(10f, 6f); lineTo(10f, 18f)
                moveTo(12f, 19f); lineTo(9f, 21f); moveTo(12f, 19f); lineTo(15f, 21f)
            }
        }
    }
    when (style) {
        SkinIconStyle.SWEET -> drawCircle(tint, 3.6f, Offset(12f, 10f), style = style.stroke)
        SkinIconStyle.GOTHIC -> Unit
        SkinIconStyle.CHINESE -> drawCircle(tint, 2.3f, Offset(15f, 14f), style = style.stroke)
        SkinIconStyle.CLASSIC -> {
            drawCircle(tint, 2.2f, Offset(12f, 8f), style = style.stroke)
            drawCircle(tint, 3.2f, Offset(12f, 15.5f), style = style.stroke)
        }
        SkinIconStyle.NAVY -> drawCircle(tint, 3.6f, Offset(12f, 13.5f), style = style.stroke)
        SkinIconStyle.COUNTRY -> drawCircle(tint, 3.2f, Offset(12f, 10.5f), style = style.stroke)
        SkinIconStyle.VICTORIAN -> drawOval(tint, Offset(14f, 7f), Size(6.5f, 11f), style = style.stroke)
    }
}

private fun DrawScope.drawContentChain(style: SkinIconStyle, tint: Color, off: Boolean) {
    // Ring profiles are constructed in a diagonal local coordinate system.
    fun ring(upper: Boolean): Path {
        val p = Path()
        fun point(u: Float, v: Float): Offset {
            val angle = when (style) {
                SkinIconStyle.CLASSIC -> 0.0
                SkinIconStyle.NAVY -> Math.PI / 2
                SkinIconStyle.COUNTRY -> Math.PI / 6
                SkinIconStyle.VICTORIAN -> Math.PI / 3
                else -> Math.PI / 4
            }
            return Offset(12f + u * cos(angle).toFloat() - v * sin(angle).toFloat(),
                12f + u * sin(angle).toFloat() + v * cos(angle).toFloat())
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
    val angle = when (style) {
        SkinIconStyle.CLASSIC -> 0.0
        SkinIconStyle.NAVY -> Math.PI / 2
        SkinIconStyle.COUNTRY -> Math.PI / 6
        SkinIconStyle.VICTORIAN -> Math.PI / 3
        else -> Math.PI / 4
    }
    val dx = sin(angle).toFloat() * 4.5f
    val dy = cos(angle).toFloat() * 4.5f
    if (off) {
        // No connector remains under the slash: background-independent broken chain.
        val sx = cos(angle).toFloat() * 7f
        val sy = sin(angle).toFloat() * 7f
        contentLine(style, tint, 12f - sx, 12f - sy, 12f + sx, 12f + sy)
    } else contentLine(style, tint, 12f - dx, 12f + dy, 12f + dx, 12f - dy)
}

/** Paths below describe the working object itself, not a generic enclosing badge. */
private fun DrawScope.contentPath(style: SkinIconStyle, tint: Color, block: Path.() -> Unit) {
    drawPath(Path().apply(block), tint, style = style.stroke)
}

private fun DrawScope.drawContentPalette(style: SkinIconStyle, tint: Color) {
    contentPath(style, tint) {
        when (style) {
            SkinIconStyle.SWEET -> { // Plump flower-shaped mixing palette, open thumb notch.
                moveTo(15f, 20f); cubicTo(10f, 23f, 2f, 17f, 4f, 12f)
                cubicTo(1f, 7f, 7f, 2f, 11f, 4f); cubicTo(16f, 1f, 22f, 7f, 20f, 12f)
                quadraticTo(19f, 15f, 15f, 14f); cubicTo(12f, 14f, 12f, 17f, 15f, 20f); close()
            }
            SkinIconStyle.GOTHIC -> { // Angular palette with a deep cut thumb notch.
                moveTo(12f, 3.5f); lineTo(20f, 8f); lineTo(19f, 14f); lineTo(13f, 13f)
                lineTo(11f, 16f); lineTo(15f, 20f); lineTo(6f, 19f); lineTo(3.5f, 11f); close()
            }
            SkinIconStyle.CHINESE -> { // Inkstone and separate brush: material changes the construction.
                moveTo(4f, 6f); quadraticTo(9f, 3f, 14f, 6f); lineTo(13f, 20f)
                lineTo(4f, 20f); close(); moveTo(5f, 12f); quadraticTo(8f, 10f, 12f, 12f)
                moveTo(20f, 3.5f); lineTo(17f, 13f); moveTo(17f, 13f)
                quadraticTo(20f, 18f, 15f, 20f); quadraticTo(13.5f, 16f, 17f, 13f); close()
            }
            SkinIconStyle.CLASSIC -> { // Fold-open paint box with three large pans.
                moveTo(4f, 11f); lineTo(4f, 4f); lineTo(20f, 4f); lineTo(20f, 11f)
                moveTo(3.5f, 11f); lineTo(20.5f, 11f); lineTo(19f, 20f); lineTo(5f, 20f); close()
                moveTo(9f, 14f); lineTo(9f, 17f); moveTo(15f, 14f); lineTo(15f, 17f)
            }
            SkinIconStyle.NAVY -> { // Broad boat-shaped palette with a rope-like thumb loop.
                moveTo(3.5f, 9f); lineTo(20.5f, 9f); quadraticTo(19f, 20f, 10f, 20f)
                quadraticTo(4f, 20f, 3.5f, 9f); close()
                moveTo(7f, 8.8f); cubicTo(5f, 2.5f, 13f, 2.5f, 12f, 8.8f)
            }
            SkinIconStyle.COUNTRY -> { // Leaf-shaped wooden palette; broad thumb opening.
                moveTo(4f, 18f); cubicTo(1f, 9f, 12f, 3f, 20f, 4f)
                cubicTo(22f, 14f, 17f, 21f, 11f, 20f); quadraticTo(14f, 16f, 10f, 15f)
                quadraticTo(6f, 15f, 7f, 20f); close()
            }
            SkinIconStyle.VICTORIAN -> { // Tall artist's palette with a long inward waist.
                moveTo(11f, 3.5f); cubicTo(3f, 4f, 2f, 17f, 8f, 20f)
                cubicTo(14f, 23f, 16f, 18f, 12f, 16f); cubicTo(9f, 13f, 18f, 15f, 20f, 11f)
                cubicTo(22f, 6f, 16f, 2f, 11f, 3.5f); close()
            }
        }
    }
    val spots = when (style) {
        SkinIconStyle.SWEET -> listOf(Offset(7f, 9f), Offset(12f, 7f), Offset(17f, 9f))
        SkinIconStyle.GOTHIC -> listOf(Offset(8f, 9f), Offset(13f, 7f), Offset(7f, 14f))
        SkinIconStyle.NAVY -> listOf(Offset(8f, 13f), Offset(13f, 13f), Offset(17f, 13f))
        SkinIconStyle.COUNTRY -> listOf(Offset(8f, 10f), Offset(13f, 7.5f), Offset(17f, 11f))
        SkinIconStyle.VICTORIAN -> listOf(Offset(8f, 9f), Offset(12f, 6.5f), Offset(17f, 9f))
        else -> emptyList()
    }
    spots.forEach { drawCircle(tint, 1.1f, it) }
}

private fun DrawScope.drawContentFolder(style: SkinIconStyle, tint: Color) {
    contentPath(style, tint) {
        when (style) {
            SkinIconStyle.SWEET -> { // Soft expanding pouch, generous rounded tab.
                moveTo(4f, 19f); lineTo(3.5f, 7f); quadraticTo(3.5f, 4f, 6.5f, 4f)
                quadraticTo(10f, 3f, 12f, 7f); lineTo(19f, 7f); lineTo(20f, 10f)
                moveTo(4f, 19f); quadraticTo(12f, 22f, 19f, 19f); lineTo(21f, 10f)
                quadraticTo(13f, 13f, 6f, 10f); close()
            }
            SkinIconStyle.GOTHIC -> { // Deep upright folio with an angular folded opening.
                moveTo(4f, 20f); lineTo(4f, 5f); lineTo(9f, 3.5f); lineTo(12f, 7f)
                lineTo(19f, 7f); lineTo(19f, 10f)
                moveTo(4f, 20f); lineTo(8f, 10f); lineTo(20.5f, 10f); lineTo(17f, 20f); close()
            }
            SkinIconStyle.CHINESE -> { // Thread-bound folder: open cover and exposed binding.
                moveTo(5f, 20f); lineTo(5f, 4f); lineTo(11f, 4f); lineTo(13f, 7f); lineTo(19f, 7f)
                moveTo(5f, 20f); lineTo(9f, 10f); lineTo(20.5f, 10f); lineTo(17.5f, 20f); close()
                moveTo(3.5f, 8f); lineTo(6.5f, 8f); moveTo(3.5f, 13f); lineTo(6.5f, 13f)
            }
            SkinIconStyle.CLASSIC -> { // Upright filing folder with a conspicuous index tab.
                moveTo(4f, 19.5f); lineTo(4f, 4f); lineTo(10f, 4f); lineTo(10f, 7f); lineTo(20f, 7f)
                moveTo(4f, 19.5f); lineTo(6f, 11f); lineTo(20.5f, 11f); lineTo(18.5f, 19.5f); close()
                moveTo(10f, 15f); lineTo(15f, 15f)
            }
            SkinIconStyle.NAVY -> { // Wide chart wallet with a turned-down triangular flap.
                moveTo(3.5f, 19f); lineTo(3.5f, 6f); lineTo(9f, 6f); lineTo(12f, 8f); lineTo(20.5f, 8f)
                lineTo(20.5f, 19f); close(); moveTo(3.5f, 10f); lineTo(12f, 14f); lineTo(20.5f, 10f)
            }
            SkinIconStyle.COUNTRY -> { // Sloping wooden file crate: low body and open handle.
                moveTo(4f, 10f); lineTo(4f, 5f); lineTo(10f, 5f); lineTo(12f, 8f); lineTo(19f, 8f)
                moveTo(3.5f, 11f); lineTo(20.5f, 11f); lineTo(18.5f, 20f); lineTo(5.5f, 20f); close()
                moveTo(9f, 15f); lineTo(15f, 15f)
            }
            SkinIconStyle.VICTORIAN -> { // Scalloped correspondence portfolio, curved opening.
                moveTo(4f, 19.5f); lineTo(4f, 8f); cubicTo(4f, 2f, 10f, 2.5f, 12f, 7f)
                lineTo(19f, 7f); lineTo(20f, 10f)
                moveTo(4f, 19.5f); lineTo(6f, 11f); cubicTo(10f, 13f, 16f, 8f, 20.5f, 11f)
                lineTo(18.5f, 19.5f); quadraticTo(12f, 22f, 4f, 19.5f); close()
            }
        }
    }
}

private fun DrawScope.drawContentCalendar(style: SkinIconStyle, tint: Color) {
    contentPath(style, tint) {
        when (style) {
            SkinIconStyle.SWEET -> { // Ring-bound rounded tear-off pad.
                moveTo(6f, 5f); lineTo(18f, 5f); quadraticTo(21f, 5f, 21f, 8f)
                lineTo(20f, 18f); quadraticTo(20f, 21f, 17f, 21f); lineTo(7f, 21f)
                quadraticTo(4f, 21f, 4f, 18f); lineTo(3f, 8f); quadraticTo(3f, 5f, 6f, 5f); close()
                moveTo(8f, 3f); lineTo(8f, 7f); moveTo(16f, 3f); lineTo(16f, 7f)
                moveTo(4f, 10f); lineTo(20f, 10f)
            }
            SkinIconStyle.GOTHIC -> { // Pointed hanging calendar with a broad date panel.
                moveTo(4f, 9f); lineTo(12f, 3.5f); lineTo(20f, 9f); lineTo(20f, 20f)
                lineTo(4f, 20f); close(); moveTo(4f, 10f); lineTo(20f, 10f)
            }
            SkinIconStyle.CHINESE -> { // Calendar scroll carried by two overhanging rollers.
                moveTo(6f, 5f); lineTo(6f, 20f); lineTo(18f, 20f); lineTo(18f, 5f)
                moveTo(3.5f, 5f); lineTo(20.5f, 5f); moveTo(3.5f, 20f); lineTo(20.5f, 20f)
                moveTo(9f, 9f); lineTo(15f, 9f)
            }
            SkinIconStyle.CLASSIC -> { // Desk calendar on a visible triangular stand.
                moveTo(4f, 5f); lineTo(17f, 5f); lineTo(19f, 20f); lineTo(3f, 20f); close()
                moveTo(17f, 5f); lineTo(21f, 18f); moveTo(7f, 3f); lineTo(7f, 7f)
                moveTo(14f, 3f); lineTo(14f, 7f); moveTo(4f, 10f); lineTo(17.5f, 10f)
            }
            SkinIconStyle.NAVY -> { // Wide logbook with a top bar and left spiral binding.
                moveTo(5f, 6f); lineTo(20f, 6f); lineTo(20f, 20f); lineTo(5f, 20f); close()
                moveTo(3f, 9f); lineTo(7f, 9f); moveTo(3f, 15f); lineTo(7f, 15f)
                moveTo(10f, 3.5f); lineTo(10f, 7.5f); moveTo(17f, 3.5f); lineTo(17f, 7.5f)
            }
            SkinIconStyle.COUNTRY -> { // Wooden peg board hanging from a central loop.
                moveTo(9f, 6f); quadraticTo(12f, 0.5f, 15f, 6f)
                moveTo(4f, 6f); lineTo(20f, 6f); lineTo(19f, 20f); lineTo(5f, 20f); close()
                moveTo(4.5f, 10f); lineTo(19.5f, 10f)
            }
            SkinIconStyle.VICTORIAN -> { // Arched perpetual calendar standing on two feet.
                moveTo(5f, 19f); lineTo(5f, 9f); cubicTo(5f, 1.5f, 19f, 1.5f, 19f, 9f)
                lineTo(19f, 19f); close(); moveTo(5f, 10f); lineTo(19f, 10f)
                moveTo(7f, 19f); lineTo(6f, 21f); moveTo(17f, 19f); lineTo(18f, 21f)
            }
        }
    }
    val xs = if (style == SkinIconStyle.NAVY) listOf(11f, 16f) else listOf(9f, 14.5f)
    for (x in xs) for (y in listOf(13.5f, 17f)) {
        contentLine(style, tint, x - 0.6f, y, x + 0.6f, y)
    }
}

private fun DrawScope.drawContentBell(style: SkinIconStyle, tint: Color) {
    contentPath(style, tint) {
        when (style) {
            SkinIconStyle.SWEET -> { // Plump bell with a single bowed skirt.
                moveTo(4f, 17f); quadraticTo(6f, 15f, 5.5f, 10f)
                cubicTo(5f, 3f, 19f, 3f, 18.5f, 10f); quadraticTo(18f, 15f, 20f, 17f)
                quadraticTo(12f, 21f, 4f, 17f); close()
            }
            SkinIconStyle.GOTHIC -> {
                moveTo(4f, 18f); lineTo(7f, 14f); lineTo(7f, 8f); lineTo(12f, 3.5f)
                lineTo(17f, 8f); lineTo(17f, 14f); lineTo(20f, 18f); close()
            }
            SkinIconStyle.CHINESE -> { // Wind bell with high curled lip and hanging chime.
                moveTo(4f, 14f); quadraticTo(6f, 17f, 7f, 11f); lineTo(8f, 6f)
                lineTo(16f, 6f); lineTo(17f, 11f); quadraticTo(18f, 17f, 20f, 14f)
                lineTo(18f, 17f); lineTo(6f, 17f); close()
                moveTo(12f, 17f); lineTo(12f, 21f); moveTo(12f, 3f); lineTo(12f, 6f)
            }
            SkinIconStyle.CLASSIC -> { // Counter bell with an actual base, no hanging shell.
                moveTo(5f, 17f); cubicTo(5f, 5f, 19f, 5f, 19f, 17f)
                moveTo(3.5f, 17f); lineTo(20.5f, 17f); lineTo(20.5f, 20f); lineTo(3.5f, 20f); close()
                moveTo(12f, 7.5f); lineTo(12f, 4f); moveTo(9.5f, 4f); lineTo(14.5f, 4f)
            }
            SkinIconStyle.NAVY -> { // Ship's bell, straight taper and exposed clapper.
                moveTo(4f, 17f); lineTo(7f, 14f); lineTo(9f, 6f); lineTo(15f, 6f)
                lineTo(17f, 14f); lineTo(20f, 17f); close()
                moveTo(10f, 6f); lineTo(10f, 3.5f); lineTo(14f, 3.5f); lineTo(14f, 6f)
                moveTo(12f, 17f); lineTo(12f, 21f)
            }
            SkinIconStyle.COUNTRY -> { // Handbell: wooden handle above a wide flared body.
                moveTo(10f, 8f); lineTo(10f, 3.5f); lineTo(14f, 3.5f); lineTo(14f, 8f)
                moveTo(4f, 18f); quadraticTo(7f, 13f, 8f, 8f); lineTo(16f, 8f)
                quadraticTo(17f, 13f, 20f, 18f); quadraticTo(12f, 21f, 4f, 18f); close()
            }
            SkinIconStyle.VICTORIAN -> { // Narrow-waisted service bell with an ogee skirt.
                moveTo(4f, 18f); cubicTo(10f, 17f, 5f, 8f, 9f, 6f)
                quadraticTo(12f, 3f, 15f, 6f); cubicTo(19f, 8f, 14f, 17f, 20f, 18f)
                quadraticTo(12f, 21f, 4f, 18f); close()
                moveTo(12f, 3f); lineTo(12f, 4.5f)
            }
        }
    }
    if (style == SkinIconStyle.SWEET || style == SkinIconStyle.GOTHIC || style == SkinIconStyle.COUNTRY || style == SkinIconStyle.VICTORIAN) {
        contentLine(style, tint, 11f, 21f, 13f, 21f)
    }
}

private fun DrawScope.drawContentMoney(style: SkinIconStyle, tint: Color) {
    // Currency remains the dominant mark; each material changes the supporting object.
    contentPath(style, tint) {
        when (style) {
            SkinIconStyle.SWEET -> { // Drawstring purse.
                moveTo(9f, 7f); lineTo(7f, 3.5f); quadraticTo(12f, 5f, 17f, 3.5f); lineTo(15f, 7f)
                cubicTo(23f, 14f, 21f, 21f, 12f, 21f); cubicTo(3f, 21f, 1f, 14f, 9f, 7f); close()
                moveTo(9f, 7f); lineTo(15f, 7f)
            }
            SkinIconStyle.GOTHIC -> { // Faceted minted token.
                moveTo(12f, 3f); lineTo(20f, 7f); lineTo(20f, 17f); lineTo(12f, 21f)
                lineTo(4f, 17f); lineTo(4f, 7f); close()
            }
            SkinIconStyle.CHINESE -> { // Ingot with curved wings and a weight-bearing bowl.
                moveTo(3f, 8f); quadraticTo(6f, 12f, 9f, 8f); quadraticTo(12f, 4f, 15f, 8f)
                quadraticTo(18f, 12f, 21f, 8f); lineTo(18f, 19f); quadraticTo(12f, 22f, 6f, 19f); close()
            }
            SkinIconStyle.CLASSIC -> { // Folded banknote: long, rectilinear proportions.
                moveTo(3f, 6f); lineTo(21f, 6f); lineTo(21f, 19f); lineTo(3f, 19f); close()
                moveTo(5f, 3.5f); lineTo(19f, 3.5f)
            }
            SkinIconStyle.NAVY -> { // Coin stack with a visible lower coin rim.
                moveTo(5f, 17f); cubicTo(0.5f, 10f, 6f, 3.5f, 12f, 3.5f)
                cubicTo(18f, 3.5f, 23.5f, 10f, 19f, 17f)
                quadraticTo(12f, 21f, 5f, 17f); close()
                moveTo(5f, 20f); quadraticTo(12f, 23f, 19f, 20f)
            }
            SkinIconStyle.COUNTRY -> { // Clasp purse with a squat handmade body.
                moveTo(6f, 8f); quadraticTo(12f, 6f, 18f, 8f); lineTo(20f, 18f)
                quadraticTo(12f, 22f, 4f, 18f); close()
                moveTo(8f, 7f); lineTo(11f, 4f); moveTo(16f, 7f); lineTo(13f, 4f)
            }
            SkinIconStyle.VICTORIAN -> { // Tall coin medallion, curving into a suspension crown.
                moveTo(9f, 5f); lineTo(9f, 3f); lineTo(15f, 3f); lineTo(15f, 5f)
                cubicTo(22f, 8f, 20f, 21f, 12f, 21f); cubicTo(4f, 21f, 2f, 8f, 9f, 5f); close()
            }
        }
    }
    val y = if (style == SkinIconStyle.SWEET || style == SkinIconStyle.CHINESE || style == SkinIconStyle.COUNTRY) 10f else 8f
    contentPath(style, tint) {
        moveTo(9f, y); lineTo(12f, y + 3f); lineTo(15f, y)
        moveTo(12f, y + 3f); lineTo(12f, 17.5f)
        moveTo(9f, y + 4f); lineTo(15f, y + 4f)
        moveTo(9f, y + 6f); lineTo(15f, y + 6f)
    }
}

private fun DrawScope.drawContentCategory(style: SkinIconStyle, tint: Color) {
    // Three large distinct samples, rather than three miniature copies of a frame.
    contentPath(style, tint) {
        when (style) {
            SkinIconStyle.SWEET -> {
                moveTo(9f, 9f); cubicTo(5f, 5f, 9f, 1f, 12f, 5f)
                cubicTo(15f, 1f, 19f, 5f, 15f, 9f); lineTo(12f, 11f); close()
                moveTo(4f, 14f); quadraticTo(7f, 11f, 10f, 14f); lineTo(10f, 20f); lineTo(4f, 20f); close()
            }
            SkinIconStyle.GOTHIC -> {
                moveTo(12f, 3f); lineTo(16f, 9f); lineTo(8f, 9f); close()
                moveTo(3.5f, 16f); lineTo(7f, 12f); lineTo(10.5f, 16f); lineTo(7f, 20.5f); close()
                moveTo(14f, 14f); lineTo(20f, 14f); lineTo(20f, 20f); lineTo(14f, 20f); close()
            }
            SkinIconStyle.CHINESE -> {
                moveTo(8f, 4f); lineTo(16f, 4f); lineTo(16f, 10f); lineTo(8f, 10f); close()
                moveTo(3.5f, 14f); lineTo(10f, 14f); lineTo(10f, 20f); lineTo(3.5f, 20f); close()
                moveTo(14f, 19f); lineTo(17f, 12f); lineTo(21f, 19f); close()
            }
            SkinIconStyle.CLASSIC -> {
                moveTo(8f, 10f); lineTo(12f, 3f); lineTo(16f, 10f); close()
                moveTo(4f, 14f); lineTo(10f, 14f); lineTo(10f, 20f); lineTo(4f, 20f); close()
            }
            SkinIconStyle.NAVY -> {
                moveTo(8f, 4f); lineTo(16f, 4f); lineTo(14f, 10f); lineTo(10f, 10f); close()
                moveTo(3.5f, 14f); lineTo(10.5f, 14f); lineTo(7f, 20f); close()
            }
            SkinIconStyle.COUNTRY -> {
                moveTo(8f, 10f); quadraticTo(6f, 3f, 16f, 3f); quadraticTo(18f, 11f, 8f, 10f); close()
                moveTo(3.5f, 14f); lineTo(10f, 13f); lineTo(10f, 20f); lineTo(4f, 20f); close()
            }
            SkinIconStyle.VICTORIAN -> {
                moveTo(8f, 9f); lineTo(8f, 7f); cubicTo(8f, 1.5f, 16f, 1.5f, 16f, 7f); lineTo(16f, 9f); close()
                moveTo(7f, 12.5f); lineTo(10.5f, 16.5f); lineTo(7f, 21f); lineTo(3.5f, 16.5f); close()
            }
        }
    }
    if (style !in listOf(SkinIconStyle.GOTHIC, SkinIconStyle.CHINESE)) {
        if (style == SkinIconStyle.VICTORIAN) drawOval(tint, Offset(14f, 13f), Size(6f, 8f), style = style.stroke)
        else drawCircle(tint, 3.2f, Offset(17f, 17f), style = style.stroke)
    }
}

private fun DrawScope.drawContentLocation(style: SkinIconStyle, tint: Color) {
    contentPath(style, tint) {
        when (style) {
            SkinIconStyle.SWEET -> { // Plush heart-headed pin.
                moveTo(12f, 21f); cubicTo(9f, 17f, 3.5f, 13f, 4f, 8f)
                cubicTo(4f, 3f, 9f, 2.5f, 12f, 6f); cubicTo(15f, 2.5f, 20f, 3f, 20f, 8f)
                cubicTo(20.5f, 13f, 15f, 17f, 12f, 21f); close()
            }
            SkinIconStyle.GOTHIC -> {
                moveTo(12f, 21f); lineTo(5f, 12f); lineTo(5f, 7f); lineTo(12f, 3f)
                lineTo(19f, 7f); lineTo(19f, 12f); close()
                moveTo(12f, 7f); lineTo(15f, 10f); lineTo(12f, 13f); lineTo(9f, 10f); close()
            }
            SkinIconStyle.CHINESE -> { // Map flag on a brush-like upright.
                moveTo(6f, 21f); lineTo(6f, 3f); moveTo(6f, 4f)
                quadraticTo(12f, 7f, 20f, 4f); lineTo(18f, 9f); lineTo(20f, 13f)
                quadraticTo(13f, 16f, 6f, 13f)
                moveTo(3f, 21f); lineTo(10f, 21f)
            }
            SkinIconStyle.CLASSIC -> { // Survey pin with a restrained circular head and needle.
                moveTo(9f, 16f); lineTo(12f, 21f); lineTo(15f, 16f)
            }
            SkinIconStyle.NAVY -> { // Compass pointer and open bearing ring.
                moveTo(15f, 3.5f); lineTo(17f, 14f); lineTo(7f, 20.5f); lineTo(7f, 10f); close()
                moveTo(7f, 10f); lineTo(17f, 14f)
            }
            SkinIconStyle.COUNTRY -> { // Wooden signpost: location as a real place marker.
                moveTo(11f, 3f); lineTo(11f, 5f); moveTo(11f, 12f); lineTo(11f, 21f)
                moveTo(4f, 5f); lineTo(17f, 5f); lineTo(21f, 8.5f); lineTo(17f, 12f)
                lineTo(4f, 12f); close(); moveTo(7f, 21f); lineTo(15f, 21f)
            }
            SkinIconStyle.VICTORIAN -> { // Long pear-shaped map pin with an oval jewel.
                moveTo(12f, 21f); cubicTo(10f, 16f, 5f, 14f, 5f, 9f)
                cubicTo(5f, 1f, 19f, 1f, 19f, 9f); cubicTo(19f, 14f, 14f, 16f, 12f, 21f); close()
            }
        }
    }
    when (style) {
        SkinIconStyle.SWEET -> drawCircle(tint, 2.4f, Offset(12f, 10.5f), style = style.stroke)
        SkinIconStyle.CLASSIC -> {
            drawCircle(tint, 6.2f, Offset(12f, 9.5f), style = style.stroke)
            drawCircle(tint, 2f, Offset(12f, 9.5f), style = style.stroke)
        }
        SkinIconStyle.VICTORIAN -> drawOval(tint, Offset(9.5f, 6f), Size(5f, 7f), style = style.stroke)
        else -> Unit
    }
}
