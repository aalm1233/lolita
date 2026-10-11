package com.lolita.app.ui.theme.skin.icon

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import kotlin.math.cos
import kotlin.math.sin

/** Content symbols use the same 24-unit canvas as navigation and actions. */
internal fun DrawScope.drawPolishedContentIcon(
    key: IconKey,
    style: SkinIconStyle,
    tint: Color
) {
    when (key) {
        IconKey.Star, IconKey.StarBorder -> {
            // Both states share one outline; the first point must start a new contour.
            val star = Path().apply {
                repeat(10) { index ->
                    val angle = -Math.PI / 2.0 + index * Math.PI / 5.0
                    val radius = if (index % 2 == 0) 9.5f else 4.35f
                    val x = 12f + cos(angle).toFloat() * radius
                    val y = 12f + sin(angle).toFloat() * radius
                    if (index == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(star, tint, style = if (key == IconKey.Star) Fill else style.stroke)
        }
        IconKey.Image -> {
            contentFrame(style, tint, 3f, 4f, 21f, 20f)
            drawCircle(tint, 1.4f, Offset(16.7f, 8.4f))
            val landscape = Path().apply {
                moveTo(4.6f, 17.8f)
                lineTo(9f, 12.6f)
                lineTo(12.8f, 16.5f)
                lineTo(15.7f, 13.6f)
                lineTo(19.4f, 17.8f)
            }
            drawPath(landscape, tint, style = style.stroke)
        }
        IconKey.Camera -> {
            val radius = contentCorner(style)
            val camera = Path().apply {
                moveTo(3f + radius, 7f)
                lineTo(7f, 7f)
                lineTo(8.6f, 4.2f)
                lineTo(15.4f, 4.2f)
                lineTo(17f, 7f)
                lineTo(21f - radius, 7f)
                quadraticTo(21f, 7f, 21f, 7f + radius)
                lineTo(21f, 20f - radius)
                quadraticTo(21f, 20f, 21f - radius, 20f)
                lineTo(3f + radius, 20f)
                quadraticTo(3f, 20f, 3f, 20f - radius)
                lineTo(3f, 7f + radius)
                quadraticTo(3f, 7f, 3f + radius, 7f)
                close()
            }
            drawPath(camera, tint, style = style.stroke)
            drawCircle(tint, 4f, Offset(12f, 13.4f), style = style.stroke)
        }
        IconKey.AddPhoto -> {
            // Leave a real opening for the plus instead of hiding the frame under a badge.
            val radius = contentCorner(style)
            val frame = Path().apply {
                moveTo(12.4f, 4.5f)
                lineTo(3f + radius, 4.5f)
                quadraticTo(3f, 4.5f, 3f, 4.5f + radius)
                lineTo(3f, 20f - radius)
                quadraticTo(3f, 20f, 3f + radius, 20f)
                lineTo(20f - radius, 20f)
                quadraticTo(20f, 20f, 20f, 20f - radius)
                lineTo(20f, 12f)
            }
            drawPath(frame, tint, style = style.stroke)
            val landscape = Path().apply {
                moveTo(4.6f, 17.7f)
                lineTo(9f, 12.6f)
                lineTo(12.8f, 16.5f)
                lineTo(15.7f, 13.6f)
                lineTo(18.4f, 17.1f)
            }
            drawPath(landscape, tint, style = style.stroke)
            drawCircle(tint, 1.2f, Offset(7.1f, 8.6f))
            contentLine(style, tint, 15f, 6f, 21f, 6f)
            contentLine(style, tint, 18f, 3f, 18f, 9f)
        }
        IconKey.Link -> {
            drawContentLinks(style, tint)
            contentLine(style, tint, 8.3f, 15.7f, 15.7f, 8.3f)
        }
        IconKey.LinkOff -> {
            val upper = Path().apply {
                moveTo(11.5f, 6.9f)
                lineTo(13.1f, 5.3f)
                cubicTo(15.2f, 3.2f, 17.9f, 3.2f, 19.4f, 4.7f)
                cubicTo(21f, 6.3f, 20.6f, 9.1f, 18.8f, 10.9f)
                lineTo(17.1f, 12.6f)
            }
            val lower = Path().apply {
                moveTo(6.9f, 11.5f)
                lineTo(5.2f, 13.1f)
                cubicTo(3.4f, 14.9f, 3f, 17.7f, 4.6f, 19.3f)
                cubicTo(6.1f, 20.8f, 8.8f, 20.8f, 10.9f, 18.7f)
                lineTo(12.6f, 17.1f)
            }
            drawPath(upper, tint, style = style.stroke)
            drawPath(lower, tint, style = style.stroke)
            contentLine(style, tint, 8.3f, 15.7f, 10.2f, 13.8f)
            contentLine(style, tint, 13.8f, 10.2f, 15.7f, 8.3f)
            contentLine(style, tint, 3.5f, 3.5f, 20.5f, 20.5f)
        }
        IconKey.Palette -> {
            val palette = Path().apply {
                moveTo(12f, 3f)
                cubicTo(6.4f, 3f, 3f, 6.6f, 3f, 11.3f)
                cubicTo(3f, 16.7f, 7f, 21f, 12.2f, 21f)
                cubicTo(14.2f, 21f, 15.2f, 19.6f, 14.2f, 18f)
                cubicTo(13.3f, 16.7f, 13.8f, 15.3f, 15.7f, 15.3f)
                lineTo(17.2f, 15.3f)
                cubicTo(19.8f, 15.3f, 21f, 13.8f, 21f, 11.5f)
                cubicTo(21f, 6.6f, 17f, 3f, 12f, 3f)
                close()
            }
            drawPath(palette, tint, style = style.stroke)
            drawCircle(tint, 1.2f, Offset(6.7f, 10.7f))
            drawCircle(tint, 1.2f, Offset(9.1f, 6.7f))
            drawCircle(tint, 1.2f, Offset(13.5f, 6.3f))
            drawCircle(tint, 1.2f, Offset(17.4f, 9f))
        }
        IconKey.FileOpen -> {
            val folderBack = Path().apply {
                moveTo(3f, 19f)
                lineTo(3f, 6.6f)
                quadraticTo(3f, 5f, 4.6f, 5f)
                lineTo(9f, 5f)
                lineTo(11.3f, 7.3f)
                lineTo(19.4f, 7.3f)
                quadraticTo(21f, 7.3f, 21f, 8.9f)
            }
            drawPath(folderBack, tint, style = style.stroke)
            val folderFront = Path().apply {
                moveTo(3f, 19f)
                lineTo(5.4f, 10.5f)
                lineTo(21.5f, 10.5f)
                lineTo(19.2f, 19f)
                close()
            }
            drawPath(folderFront, tint, style = style.stroke)
        }
        IconKey.CalendarMonth -> {
            contentFrame(style, tint, 3.5f, 5f, 20.5f, 21f)
            contentLine(style, tint, 7.1f, 3f, 7.1f, 7f)
            contentLine(style, tint, 16.9f, 3f, 16.9f, 7f)
            contentLine(style, tint, 3.5f, 9.2f, 20.5f, 9.2f)
            for (x in listOf(7.4f, 12f, 16.6f)) {
                drawCircle(tint, 0.9f, Offset(x, 13.4f))
                drawCircle(tint, 0.9f, Offset(x, 17.5f))
            }
        }
        IconKey.Notifications -> {
            val bell = Path().apply {
                moveTo(5.3f, 10f)
                cubicTo(5.3f, 6.4f, 7.9f, 4.5f, 12f, 4.5f)
                cubicTo(16.1f, 4.5f, 18.7f, 6.4f, 18.7f, 10f)
                lineTo(18.7f, 14f)
                lineTo(21f, 17.5f)
                lineTo(3f, 17.5f)
                lineTo(5.3f, 14f)
                close()
            }
            drawPath(bell, tint, style = style.stroke)
            contentLine(style, tint, 12f, 2.7f, 12f, 4.5f)
            val clapper = Path().apply {
                moveTo(9.5f, 20f)
                quadraticTo(12f, 22f, 14.5f, 20f)
            }
            drawPath(clapper, tint, style = style.stroke)
        }
        IconKey.AttachMoney -> {
            drawCircle(tint, 9.2f, Offset(12f, 12f), style = style.stroke)
            val yuan = Path().apply {
                moveTo(7.6f, 6.9f)
                lineTo(12f, 12.1f)
                lineTo(16.4f, 6.9f)
                moveTo(12f, 12.1f)
                lineTo(12f, 18.1f)
            }
            drawPath(yuan, tint, style = style.stroke)
            contentLine(style, tint, 8f, 12.1f, 16f, 12.1f)
            contentLine(style, tint, 8f, 15.2f, 16f, 15.2f)
        }
        IconKey.Category -> {
            val triangle = Path().apply {
                moveTo(12f, 3f)
                lineTo(7.8f, 9.4f)
                lineTo(16.2f, 9.4f)
                close()
            }
            drawPath(triangle, tint, style = style.stroke)
            contentFrame(style, tint, 3.5f, 13f, 10.5f, 20f)
            drawCircle(tint, 3.5f, Offset(17f, 16.5f), style = style.stroke)
        }
        IconKey.Location -> {
            val pin = Path().apply {
                moveTo(12f, 21f)
                cubicTo(10.3f, 18.9f, 4.7f, 14.3f, 4.7f, 10.2f)
                cubicTo(4.7f, 6f, 7.8f, 3f, 12f, 3f)
                cubicTo(16.2f, 3f, 19.3f, 6f, 19.3f, 10.2f)
                cubicTo(19.3f, 14.3f, 13.7f, 18.9f, 12f, 21f)
                close()
            }
            drawPath(pin, tint, style = style.stroke)
            drawCircle(tint, 2.7f, Offset(12f, 10.1f), style = style.stroke)
        }
        else -> error("Not a content icon: $key")
    }
}

private fun contentCorner(style: SkinIconStyle): Float = when (style) {
    SkinIconStyle.SWEET -> 2.2f
    SkinIconStyle.GOTHIC -> 0f
    SkinIconStyle.CHINESE -> 0.5f
    SkinIconStyle.CLASSIC -> 0.8f
    SkinIconStyle.NAVY -> 1.8f
    SkinIconStyle.COUNTRY -> 2.6f
    SkinIconStyle.VICTORIAN -> 1.2f
}

private fun DrawScope.contentFrame(
    style: SkinIconStyle,
    tint: Color,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float
) {
    if (style == SkinIconStyle.GOTHIC) {
        val cut = 1.4f
        val frame = Path().apply {
            moveTo(left + cut, top)
            lineTo(right - cut, top)
            lineTo(right, top + cut)
            lineTo(right, bottom - cut)
            lineTo(right - cut, bottom)
            lineTo(left + cut, bottom)
            lineTo(left, bottom - cut)
            lineTo(left, top + cut)
            close()
        }
        drawPath(frame, tint, style = style.stroke)
    } else {
        val radius = contentCorner(style)
        drawRoundRect(
            tint,
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            cornerRadius = CornerRadius(radius, radius),
            style = style.stroke
        )
    }
}

private fun DrawScope.contentLine(
    style: SkinIconStyle,
    tint: Color,
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float
) {
    drawLine(
        tint,
        Offset(startX, startY),
        Offset(endX, endY),
        strokeWidth = style.stroke.width,
        cap = style.stroke.cap
    )
}

private fun DrawScope.drawContentLinks(style: SkinIconStyle, tint: Color) {
    val upper = Path().apply {
        moveTo(10.3f, 8.1f)
        lineTo(13.1f, 5.3f)
        cubicTo(15.2f, 3.2f, 17.9f, 3.2f, 19.4f, 4.7f)
        cubicTo(21f, 6.3f, 20.6f, 9.1f, 18.8f, 10.9f)
        lineTo(16.3f, 13.4f)
    }
    val lower = Path().apply {
        moveTo(7.7f, 10.6f)
        lineTo(5.2f, 13.1f)
        cubicTo(3.4f, 14.9f, 3f, 17.7f, 4.6f, 19.3f)
        cubicTo(6.1f, 20.8f, 8.8f, 20.8f, 10.9f, 18.7f)
        lineTo(13.7f, 15.9f)
    }
    drawPath(upper, tint, style = style.stroke)
    drawPath(lower, tint, style = style.stroke)
}
