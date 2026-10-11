package com.lolita.app.ui.theme.skin.icon

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.sin

internal fun DrawScope.drawPolishedNavigationIcon(key: IconKey, style: SkinIconStyle, tint: Color) {
    when (key) {
        IconKey.Home -> drawSkinHome(style, tint)
        IconKey.Wishlist -> drawSkinHeart(style, tint)
        IconKey.Outfit -> drawSkinDress(style, tint)
        IconKey.Stats -> drawSkinChart(style, tint)
        IconKey.Settings -> drawSkinGear(style, tint)
        else -> error("Not a navigation icon: $key")
    }
}

private fun DrawScope.navPath(style: SkinIconStyle, tint: Color, block: Path.() -> Unit) {
    drawPath(Path().apply(block), tint, style = style.stroke)
}

private fun DrawScope.navLine(style: SkinIconStyle, tint: Color, x1: Float, y1: Float, x2: Float, y2: Float) {
    drawLine(tint, Offset(x1, y1), Offset(x2, y2), style.stroke.width, style.stroke.cap)
}

private fun DrawScope.drawSkinHome(style: SkinIconStyle, tint: Color) {
    if (style == SkinIconStyle.NAVY) {
        // A broad, open lighthouse silhouette; no fine stripes or light rays.
        navPath(style, tint) {
            moveTo(7f, 21f); lineTo(8.5f, 10f); lineTo(15.5f, 10f); lineTo(17f, 21f); close()
            moveTo(7.5f, 10f); lineTo(7.5f, 6.5f); lineTo(16.5f, 6.5f); lineTo(16.5f, 10f)
            moveTo(6f, 6.5f); lineTo(12f, 3f); lineTo(18f, 6.5f)
        }
        navLine(style, tint, 6f, 21f, 18f, 21f)
        navLine(style, tint, 12f, 15.5f, 12f, 21f)
        return
    }
    if (style == SkinIconStyle.CHINESE) {
        navPath(style, tint) {
            moveTo(3f, 9f); quadraticTo(7.5f, 9f, 12f, 3.5f)
            quadraticTo(16.5f, 9f, 21f, 9f)
            moveTo(5f, 10.5f); lineTo(19f, 10.5f)
            moveTo(6f, 11f); lineTo(6f, 20f); lineTo(18f, 20f); lineTo(18f, 11f)
            moveTo(10f, 20f); lineTo(10f, 14.5f); lineTo(14f, 14.5f); lineTo(14f, 20f)
        }
        return
    }
    navPath(style, tint) {
        moveTo(3f, 10.5f); lineTo(12f, 3f); lineTo(21f, 10.5f)
        moveTo(5f, 10f); lineTo(5f, 19.5f)
        if (style == SkinIconStyle.SWEET || style == SkinIconStyle.COUNTRY) {
            quadraticTo(5f, 21f, 6.5f, 21f)
            lineTo(17.5f, 21f); quadraticTo(19f, 21f, 19f, 19.5f)
        } else {
            lineTo(5f, 21f); lineTo(19f, 21f)
        }
        lineTo(19f, 10f)
        moveTo(9.5f, 21f); lineTo(9.5f, 16f)
        when (style) {
            SkinIconStyle.GOTHIC -> { lineTo(12f, 13f); lineTo(14.5f, 16f) }
            SkinIconStyle.CLASSIC, SkinIconStyle.VICTORIAN ->
                cubicTo(9.5f, 12.5f, 14.5f, 12.5f, 14.5f, 16f)
            else -> { lineTo(9.5f, 14f); lineTo(14.5f, 14f) }
        }
        lineTo(14.5f, 21f)
    }
    if (style == SkinIconStyle.VICTORIAN) {
        navPath(style, tint) { moveTo(16f, 5.5f); lineTo(16f, 3.5f); lineTo(18.5f, 3.5f); lineTo(18.5f, 8f) }
    }
}

private fun DrawScope.drawSkinHeart(style: SkinIconStyle, tint: Color) {
    navPath(style, tint) {
        moveTo(12f, 20.5f)
        if (style == SkinIconStyle.GOTHIC) {
            lineTo(3.5f, 12f); lineTo(3.5f, 7f); lineTo(7.5f, 4f)
            lineTo(12f, 7.5f); lineTo(16.5f, 4f); lineTo(20.5f, 7f)
            lineTo(20.5f, 12f); close()
        } else {
            cubicTo(9.5f, 18.5f, 3f, 13.5f, 3f, 8.5f)
            cubicTo(3f, 3.5f, 9f, 2.5f, 12f, 7f)
            cubicTo(15f, 2.5f, 21f, 3.5f, 21f, 8.5f)
            cubicTo(21f, 13.5f, 14.5f, 18.5f, 12f, 20.5f)
            close()
        }
    }
    when (style) {
        SkinIconStyle.NAVY -> navPath(style, tint) {
            moveTo(7.5f, 12f); quadraticTo(9.75f, 10.5f, 12f, 12f)
            quadraticTo(14.25f, 13.5f, 16.5f, 12f)
        }
        SkinIconStyle.COUNTRY -> navPath(style, tint) {
            moveTo(10f, 14f); quadraticTo(8.5f, 10f, 14f, 10f)
            quadraticTo(14f, 14f, 10f, 14f); close()
        }
        SkinIconStyle.VICTORIAN -> navPath(style, tint) {
            moveTo(12f, 10f); lineTo(14f, 12.5f); lineTo(12f, 15f); lineTo(10f, 12.5f); close()
        }
        else -> Unit
    }
}

private fun DrawScope.drawSkinDress(style: SkinIconStyle, tint: Color) {
    val puff = style == SkinIconStyle.SWEET || style == SkinIconStyle.COUNTRY
    navPath(style, tint) {
        moveTo(8.5f, 3.5f)
        if (style == SkinIconStyle.CHINESE) {
            lineTo(6.5f, 3.5f); lineTo(3f, 9f); lineTo(6.5f, 11f)
            lineTo(7.5f, 9.5f); lineTo(5f, 20.5f); lineTo(19f, 20.5f)
            lineTo(16.5f, 9.5f); lineTo(17.5f, 11f); lineTo(21f, 9f)
            lineTo(17.5f, 3.5f); lineTo(15.5f, 3.5f); lineTo(12f, 7f); close()
        } else {
            quadraticTo(12f, 6.5f, 15.5f, 3.5f)
            if (puff) {
                quadraticTo(19f, 3.5f, 19f, 7f); lineTo(16f, 9f)
            } else {
                lineTo(18.5f, 7f); lineTo(16f, 8.5f)
            }
            lineTo(14.5f, 11f)
            if (style == SkinIconStyle.GOTHIC) {
                lineTo(20f, 20.5f); lineTo(4f, 20.5f)
            } else {
                quadraticTo(17f, 14.5f, 20f, 20.5f)
                quadraticTo(12f, 22f, 4f, 20.5f)
                quadraticTo(7f, 14.5f, 9.5f, 11f)
            }
            lineTo(9.5f, 11f); lineTo(8f, 8.5f)
            if (puff) {
                lineTo(5f, 7f); quadraticTo(5f, 3.5f, 8.5f, 3.5f)
            } else { lineTo(5.5f, 7f); close() }
        }
    }
    navLine(style, tint, 9.5f, 11f, 14.5f, 11f)
    when (style) {
        SkinIconStyle.CHINESE -> navPath(style, tint) {
            moveTo(8.5f, 3.5f); lineTo(14.5f, 11f)
            moveTo(15.5f, 3.5f); lineTo(10.5f, 10f)
        }
        SkinIconStyle.NAVY -> navPath(style, tint) {
            moveTo(8f, 5f); lineTo(12f, 9f); lineTo(16f, 5f)
        }
        SkinIconStyle.COUNTRY -> navPath(style, tint) {
            moveTo(10f, 14f); lineTo(10f, 16.5f)
            quadraticTo(12f, 18f, 14f, 16.5f); lineTo(14f, 14f); close()
        }
        SkinIconStyle.CLASSIC, SkinIconStyle.VICTORIAN -> navPath(style, tint) {
            moveTo(10f, 14f); quadraticTo(9f, 17f, 8f, 19f)
            moveTo(14f, 14f); quadraticTo(15f, 17f, 16f, 19f)
        }
        else -> Unit
    }
}

private fun DrawScope.drawSkinChart(style: SkinIconStyle, tint: Color) {
    val corner = when (style) {
        SkinIconStyle.GOTHIC, SkinIconStyle.CHINESE -> 0f
        SkinIconStyle.SWEET, SkinIconStyle.COUNTRY -> 1.3f
        else -> 0.5f
    }
    listOf(5f to 12f, 10.5f to 8f, 16f to 4f).forEach { (x, y) ->
        drawRoundRect(tint, Offset(x, y), Size(3f, 18f - y), CornerRadius(corner))
    }
    if (style == SkinIconStyle.NAVY) {
        navPath(style, tint) {
            moveTo(3.5f, 21f); quadraticTo(6.3f, 19.5f, 9.2f, 21f)
            quadraticTo(12f, 22.5f, 14.8f, 21f)
            quadraticTo(17.7f, 19.5f, 20.5f, 21f)
        }
    } else {
        navLine(style, tint, 3.5f, 21f, 20.5f, 21f)
    }
}

private fun DrawScope.drawSkinGear(style: SkinIconStyle, tint: Color) {
    if (style == SkinIconStyle.SWEET || style == SkinIconStyle.COUNTRY) {
        // A single scalloped contour keeps the flower character and a readable gear center.
        val petals = if (style == SkinIconStyle.SWEET) 6 else 8
        val step = 2.0 * Math.PI / petals
        navPath(style, tint) {
            for (i in 0 until petals) {
                val start = -Math.PI / 2 + i * step
                val end = start + step
                val middle = start + step / 2
                val sx = 12f + 7f * cos(start).toFloat()
                val sy = 12f + 7f * sin(start).toFloat()
                if (i == 0) moveTo(sx, sy)
                cubicTo(
                    12f + 10f * cos(start + step / 4).toFloat(),
                    12f + 10f * sin(start + step / 4).toFloat(),
                    12f + 10f * cos(middle + step / 4).toFloat(),
                    12f + 10f * sin(middle + step / 4).toFloat(),
                    12f + 7f * cos(end).toFloat(), 12f + 7f * sin(end).toFloat()
                )
            }
            close()
        }
    } else {
        navPath(style, tint) {
            repeat(8) { tooth ->
                listOf(0f to 7.2f, 0.15f to 9f, 0.55f to 9f, 0.7f to 7.2f).forEachIndexed { point, (fraction, r) ->
                    val a = -Math.PI / 2 + (tooth + fraction) * Math.PI / 4
                    val x = 12f + r * cos(a).toFloat()
                    val y = 12f + r * sin(a).toFloat()
                    if (tooth == 0 && point == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            close()
        }
    }
    drawCircle(tint, 3.2f, Offset(12f, 12f), style = style.stroke)
}
