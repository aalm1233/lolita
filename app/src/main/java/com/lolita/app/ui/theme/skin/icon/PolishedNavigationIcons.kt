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
    navPath(style, tint) {
        when (style) {
            SkinIconStyle.SWEET -> {
                // Low, generous dollhouse roof and a softly rounded front door.
                moveTo(3.2f, 10f); quadraticTo(5f, 6f, 12f, 3.5f)
                quadraticTo(19f, 6f, 20.8f, 10f); close()
                moveTo(5f, 10f); lineTo(5f, 18.8f); quadraticTo(5f, 20.5f, 7f, 20.5f)
                lineTo(17f, 20.5f); quadraticTo(19f, 20.5f, 19f, 18.8f); lineTo(19f, 10f)
                moveTo(9.5f, 20.5f); lineTo(9.5f, 16f)
                cubicTo(9.5f, 12.5f, 14.5f, 12.5f, 14.5f, 16f); lineTo(14.5f, 20.5f)
            }
            SkinIconStyle.GOTHIC -> {
                // A tall pointed arch, with shoulders cut into the outer silhouette.
                moveTo(4.5f, 20.5f); lineTo(4.5f, 11f); lineTo(7f, 11f)
                lineTo(7f, 8f); lineTo(12f, 3.6f); lineTo(17f, 8f)
                lineTo(17f, 11f); lineTo(19.5f, 11f); lineTo(19.5f, 20.5f); close()
                moveTo(9f, 20.5f); lineTo(9f, 14.5f); lineTo(12f, 10.5f)
                lineTo(15f, 14.5f); lineTo(15f, 20.5f)
            }
            SkinIconStyle.CHINESE -> {
                moveTo(3.2f, 7.5f); quadraticTo(5f, 11f, 12f, 3.5f)
                quadraticTo(19f, 11f, 20.8f, 7.5f); lineTo(19f, 11f)
                lineTo(5f, 11f); close()
                moveTo(6.5f, 11f); lineTo(6.5f, 20f)
                moveTo(17.5f, 11f); lineTo(17.5f, 20f)
                moveTo(4.5f, 20f); lineTo(19.5f, 20f)
                moveTo(10f, 20f); lineTo(10f, 14.5f); lineTo(14f, 14.5f); lineTo(14f, 20f)
            }
            SkinIconStyle.CLASSIC -> {
                // Pediment and two upright columns, a rectilinear symmetrical manor.
                moveTo(3.5f, 8.5f); lineTo(12f, 3.5f); lineTo(20.5f, 8.5f); close()
                moveTo(5.5f, 11.5f); lineTo(5.5f, 20.5f); lineTo(18.5f, 20.5f); lineTo(18.5f, 11.5f)
                moveTo(9.5f, 20.5f); lineTo(9.5f, 13f); lineTo(14.5f, 13f); lineTo(14.5f, 20.5f)
            }
            SkinIconStyle.NAVY -> {
                moveTo(7f, 20.5f); lineTo(9f, 10f); lineTo(15f, 10f); lineTo(17f, 20.5f); close()
                moveTo(8f, 10f); lineTo(8f, 6.5f); lineTo(16f, 6.5f); lineTo(16f, 10f)
                moveTo(6.5f, 6.5f); lineTo(12f, 3.5f); lineTo(17.5f, 6.5f)
                moveTo(8.2f, 14f); lineTo(15.8f, 14f)
                moveTo(5f, 20.5f); lineTo(19f, 20.5f)
            }
            SkinIconStyle.COUNTRY -> {
                // Broad overhanging thatch, compact cottage, offset chimney.
                moveTo(3.2f, 11.5f); quadraticTo(5f, 7.5f, 10.5f, 4.5f)
                quadraticTo(16f, 5f, 20.8f, 11.5f); quadraticTo(12f, 13f, 3.2f, 11.5f); close()
                moveTo(15.5f, 5.5f); lineTo(15.5f, 3.5f); lineTo(18f, 3.5f); lineTo(18f, 8f)
                moveTo(5.5f, 12f); lineTo(6f, 20f); lineTo(18f, 20f); lineTo(18.5f, 12f)
                moveTo(10f, 20f); lineTo(10f, 15f); lineTo(14f, 15f); lineTo(14f, 20f)
            }
            SkinIconStyle.VICTORIAN -> {
                // Curved central gable with low side wings, distinct from a pediment.
                moveTo(3.5f, 20.5f); lineTo(3.5f, 12f); lineTo(7f, 12f); lineTo(7f, 9f)
                cubicTo(7f, 6f, 10f, 5.5f, 12f, 3.5f)
                cubicTo(14f, 5.5f, 17f, 6f, 17f, 9f)
                lineTo(17f, 12f); lineTo(20.5f, 12f); lineTo(20.5f, 20.5f); close()
                moveTo(9.5f, 20.5f); lineTo(9.5f, 15f)
                cubicTo(9.5f, 11.5f, 14.5f, 11.5f, 14.5f, 15f); lineTo(14.5f, 20.5f)
            }
        }
    }
}

private fun DrawScope.drawSkinHeart(style: SkinIconStyle, tint: Color) {
    // Each heart changes its own mass and contour, with no emblem inside it.
    navPath(style, tint) {
        when (style) {
            SkinIconStyle.SWEET -> {
                moveTo(12f, 20f); cubicTo(9f, 18.5f, 3.2f, 14f, 3.2f, 8.5f)
                cubicTo(3.2f, 3.5f, 9.5f, 3.2f, 12f, 7.5f)
                cubicTo(14.5f, 3.2f, 20.8f, 3.5f, 20.8f, 8.5f)
                cubicTo(20.8f, 14f, 15f, 18.5f, 12f, 20f); close()
            }
            SkinIconStyle.GOTHIC -> {
                moveTo(12f, 20.3f); lineTo(4f, 12f); lineTo(4f, 7f)
                lineTo(7.5f, 4f); lineTo(12f, 8f); lineTo(16.5f, 4f)
                lineTo(20f, 7f); lineTo(20f, 12f); close()
            }
            SkinIconStyle.CHINESE -> {
                // Folded shoulders and an open, long lower taper.
                moveTo(12f, 20.5f); quadraticTo(9f, 16f, 4f, 11f)
                lineTo(4f, 6f); lineTo(8.5f, 4.5f); lineTo(12f, 8.5f)
                lineTo(15.5f, 4.5f); lineTo(20f, 6f); lineTo(20f, 11f)
                quadraticTo(15f, 16f, 12f, 20.5f); close()
            }
            SkinIconStyle.CLASSIC -> {
                // Small restrained lobes above straight, symmetrical diagonals.
                moveTo(12f, 20f); lineTo(4.8f, 11f)
                cubicTo(1.5f, 6f, 7f, 1.8f, 12f, 6.8f)
                cubicTo(17f, 1.8f, 22.5f, 6f, 19.2f, 11f); close()
            }
            SkinIconStyle.NAVY -> {
                // Wide squared deck-like lobes and a short V-shaped keel.
                moveTo(12f, 19f); lineTo(4f, 12f); lineTo(4f, 7.5f)
                quadraticTo(4f, 5f, 6.5f, 5f); lineTo(9f, 5f); lineTo(12f, 8f)
                lineTo(15f, 5f); lineTo(17.5f, 5f); quadraticTo(20f, 5f, 20f, 7.5f)
                lineTo(20f, 12f); close()
            }
            SkinIconStyle.COUNTRY -> {
                // An asymmetric, hand-bent heart: one broad lobe and a curling point.
                moveTo(10.5f, 20f); cubicTo(6f, 16.5f, 3.2f, 12.5f, 3.5f, 8.5f)
                cubicTo(3.8f, 4f, 9f, 3.5f, 11.5f, 8f)
                cubicTo(14.5f, 2.5f, 21.5f, 5f, 20f, 10.5f)
                cubicTo(19f, 15f, 13.5f, 15.5f, 10.5f, 20f); close()
            }
            SkinIconStyle.VICTORIAN -> {
                // Tall lobes draw inward into a long ogee point.
                moveTo(12f, 20.5f); cubicTo(10f, 16.5f, 4.5f, 15f, 4.5f, 8f)
                cubicTo(4.5f, 2f, 10.5f, 2.5f, 12f, 8f)
                cubicTo(13.5f, 2.5f, 19.5f, 2f, 19.5f, 8f)
                cubicTo(19.5f, 15f, 14f, 16.5f, 12f, 20.5f); close()
            }
        }
    }
}

private fun DrawScope.drawSkinDress(style: SkinIconStyle, tint: Color) {
    navPath(style, tint) {
        when (style) {
            SkinIconStyle.SWEET -> {
                moveTo(8.5f, 3.5f); quadraticTo(12f, 6.5f, 15.5f, 3.5f)
                cubicTo(20f, 3.5f, 21f, 8f, 16.5f, 9f); lineTo(14.5f, 11f)
                cubicTo(18.5f, 13f, 20.5f, 16f, 20f, 19.5f)
                quadraticTo(12f, 22f, 4f, 19.5f)
                cubicTo(3.5f, 16f, 5.5f, 13f, 9.5f, 11f); lineTo(7.5f, 9f)
                cubicTo(3f, 8f, 4f, 3.5f, 8.5f, 3.5f); close()
                moveTo(9.5f, 11f); lineTo(14.5f, 11f)
            }
            SkinIconStyle.GOTHIC -> {
                moveTo(8f, 3.5f); lineTo(12f, 7f); lineTo(16f, 3.5f)
                lineTo(19.5f, 8f); lineTo(16f, 9.5f); lineTo(14.5f, 12f)
                lineTo(19.5f, 18.5f); lineTo(16.5f, 20f); lineTo(12f, 18.5f)
                lineTo(7.5f, 20f); lineTo(4.5f, 18.5f); lineTo(9.5f, 12f)
                lineTo(8f, 9.5f); lineTo(4.5f, 8f); close()
                moveTo(9.5f, 12f); lineTo(12f, 13.5f); lineTo(14.5f, 12f)
            }
            SkinIconStyle.CHINESE -> {
                moveTo(8f, 3.5f); lineTo(5.5f, 4.5f); lineTo(3.2f, 10f)
                lineTo(6.5f, 12f); lineTo(8f, 9.5f); lineTo(6f, 20.5f)
                lineTo(18f, 20.5f); lineTo(16f, 9.5f); lineTo(17.5f, 12f)
                lineTo(20.8f, 10f); lineTo(18.5f, 4.5f); lineTo(16f, 3.5f)
                lineTo(12f, 6.5f); close()
                moveTo(8f, 3.5f); lineTo(14.5f, 12f); lineTo(9.5f, 12f)
                moveTo(16f, 3.5f); lineTo(11.5f, 8f)
            }
            SkinIconStyle.CLASSIC -> {
                // Narrow shoulders and a continuous straight A-line, without puff sleeves.
                moveTo(8.5f, 3.5f); lineTo(10f, 6f); lineTo(14f, 6f); lineTo(15.5f, 3.5f)
                lineTo(17.5f, 6.5f); lineTo(15f, 9f); lineTo(14f, 11f)
                lineTo(18f, 20.5f); lineTo(6f, 20.5f); lineTo(10f, 11f)
                lineTo(9f, 9f); lineTo(6.5f, 6.5f); close()
                moveTo(10f, 11f); lineTo(14f, 11f)
            }
            SkinIconStyle.NAVY -> {
                moveTo(8f, 3.5f); lineTo(5f, 4.5f); lineTo(3.5f, 8.5f)
                lineTo(7f, 10f); lineTo(8f, 8.5f); lineTo(9f, 12f)
                lineTo(5f, 20f); lineTo(19f, 20f); lineTo(15f, 12f)
                lineTo(16f, 8.5f); lineTo(17f, 10f); lineTo(20.5f, 8.5f)
                lineTo(19f, 4.5f); lineTo(16f, 3.5f); lineTo(12f, 8.5f); close()
                moveTo(9f, 12f); lineTo(15f, 12f)
                moveTo(7f, 17f); lineTo(17f, 17f)
            }
            SkinIconStyle.COUNTRY -> {
                // A sleeveless work apron: broad bib, sloping sides and a curved hem.
                moveTo(9f, 3.5f); lineTo(15f, 3.5f); lineTo(15f, 8f)
                quadraticTo(15f, 11f, 18f, 11f); lineTo(19.5f, 19.5f)
                quadraticTo(12f, 21.5f, 4.5f, 19.5f); lineTo(6f, 11f)
                quadraticTo(9f, 11f, 9f, 8f); close()
                moveTo(9.5f, 13.5f); lineTo(14.5f, 13.5f); lineTo(14.5f, 15.5f)
                quadraticTo(12f, 18f, 9.5f, 15.5f); close()
            }
            SkinIconStyle.VICTORIAN -> {
                // High waist, narrow bodice, curved overskirt and broad layered lower skirt.
                moveTo(8.5f, 3.5f); quadraticTo(12f, 5.5f, 15.5f, 3.5f)
                lineTo(18f, 7f); lineTo(15f, 8.5f); lineTo(14f, 10f)
                cubicTo(15f, 14f, 18.5f, 16f, 20.5f, 20f)
                quadraticTo(12f, 21.5f, 3.5f, 20f)
                cubicTo(5.5f, 16f, 9f, 14f, 10f, 10f)
                lineTo(9f, 8.5f); lineTo(6f, 7f); close()
                moveTo(10f, 10f); lineTo(14f, 10f)
                moveTo(7f, 16f); quadraticTo(12f, 18.5f, 17f, 16f)
            }
        }
    }
}

private fun DrawScope.drawSkinChart(style: SkinIconStyle, tint: Color) {
    when (style) {
        SkinIconStyle.SWEET -> {
            // Broad capsule bars, with round ends visible even at navigation size.
            listOf(4f to 12f, 10f to 8f, 16f to 4f).forEach { (x, y) ->
                drawRoundRect(tint, Offset(x, y), Size(4f, 16f - y), CornerRadius(2f))
            }
            navPath(style, tint) { moveTo(3.5f, 19f); quadraticTo(12f, 22f, 20.5f, 19f) }
        }
        SkinIconStyle.GOTHIC -> {
            // Three pointed columns; points are part of the data marks themselves.
            listOf(4f to 13f, 10f to 9f, 16f to 5f).forEach { (x, y) ->
                drawPath(Path().apply {
                    moveTo(x, 19f); lineTo(x, y); lineTo(x + 2f, y - 2f)
                    lineTo(x + 4f, y); lineTo(x + 4f, 19f); close()
                }, tint)
            }
        }
        SkinIconStyle.CHINESE -> navPath(style, tint) {
            // A stepped histogram, whose continuous top reads as ordered roof levels.
            moveTo(3.5f, 20f); lineTo(3.5f, 13f); lineTo(9f, 13f)
            lineTo(9f, 8.5f); lineTo(14.5f, 8.5f); lineTo(14.5f, 4f)
            lineTo(20f, 4f); lineTo(20f, 20f); close()
            moveTo(9f, 16f); lineTo(9f, 20f)
            moveTo(14.5f, 11.5f); lineTo(14.5f, 20f)
        }
        SkinIconStyle.CLASSIC -> {
            navPath(style, tint) { moveTo(3.5f, 4f); lineTo(3.5f, 20.5f); lineTo(20.5f, 20.5f) }
            listOf(7f to 13f, 12f to 9f, 17f to 4f).forEach { (x, y) ->
                drawRect(tint, Offset(x, y), Size(2.5f, 17.5f - y))
            }
        }
        SkinIconStyle.NAVY -> {
            // A porthole pie chart, not bars with a decorative wave below them.
            drawCircle(tint, 8f, Offset(12f, 12f), style = style.stroke)
            navPath(style, tint) { moveTo(12f, 4f); lineTo(12f, 12f); lineTo(20f, 12f) }
            navLine(style, tint, 12f, 12f, 6.3f, 17.7f)
        }
        SkinIconStyle.COUNTRY -> {
            // Organic rising trend, broad open area and a simple baseline.
            navPath(style, tint) {
                moveTo(3.5f, 17f); cubicTo(7f, 17f, 6f, 10f, 10f, 12f)
                cubicTo(14f, 14f, 14.5f, 5f, 20f, 4f)
                moveTo(3.5f, 20.5f); lineTo(20f, 20.5f); lineTo(20f, 8.5f)
            }
            drawCircle(tint, 1.4f, Offset(10f, 12f))
        }
        SkinIconStyle.VICTORIAN -> {
            // Outlined arch-headed columns; generous voids keep them readable.
            listOf(3.5f to 13f, 9.5f to 9f, 15.5f to 5f).forEach { (x, y) ->
                navPath(style, tint) {
                    moveTo(x, 20f); lineTo(x, y)
                    cubicTo(x, y - 3f, x + 4f, y - 3f, x + 4f, y)
                    lineTo(x + 4f, 20f); close()
                }
            }
        }
    }
}

private fun DrawScope.drawSkinGear(style: SkinIconStyle, tint: Color) {
    when (style) {
        SkinIconStyle.SWEET -> drawPetalGear(style, tint, 6, 6.4f, 10f, 1f, 1f)
        SkinIconStyle.COUNTRY -> navPath(style, tint) {
            // Four broad rounded wooden cogs, with deep open spaces between them.
            moveTo(9f, 7f); lineTo(9f, 5.5f); cubicTo(9f, 2.5f, 15f, 2.5f, 15f, 5.5f)
            lineTo(15f, 7f); quadraticTo(15f, 9f, 17f, 9f); lineTo(18.5f, 9f)
            cubicTo(21.5f, 9f, 21.5f, 15f, 18.5f, 15f)
            lineTo(17f, 15f); quadraticTo(15f, 15f, 15f, 17f); lineTo(15f, 18.5f)
            cubicTo(15f, 21.5f, 9f, 21.5f, 9f, 18.5f)
            lineTo(9f, 17f); quadraticTo(9f, 15f, 7f, 15f); lineTo(5.5f, 15f)
            cubicTo(2.5f, 15f, 2.5f, 9f, 5.5f, 9f)
            lineTo(7f, 9f); quadraticTo(9f, 9f, 9f, 7f); close()
        }
        SkinIconStyle.VICTORIAN -> drawPetalGear(style, tint, 8, 6.4f, 9.7f, 0.82f, 1f)
        SkinIconStyle.GOTHIC -> {
            navPath(style, tint) {
                repeat(8) { tooth ->
                    val angle = -Math.PI / 2 + tooth * Math.PI / 4
                    val valley = navPolar(angle - Math.PI / 8, 6.4f)
                    val tip = navPolar(angle, 8.5f)
                    if (tooth == 0) moveTo(valley.x, valley.y) else lineTo(valley.x, valley.y)
                    lineTo(tip.x, tip.y)
                }
                close()
            }
        }
        SkinIconStyle.CHINESE -> navPath(style, tint) {
            // Four broad, square teeth and stepped corners.
            moveTo(9f, 3.5f); lineTo(15f, 3.5f); lineTo(15f, 7f)
            lineTo(17f, 7f); lineTo(17f, 9f); lineTo(20.5f, 9f)
            lineTo(20.5f, 15f); lineTo(17f, 15f); lineTo(17f, 17f)
            lineTo(15f, 17f); lineTo(15f, 20.5f); lineTo(9f, 20.5f)
            lineTo(9f, 17f); lineTo(7f, 17f); lineTo(7f, 15f)
            lineTo(3.5f, 15f); lineTo(3.5f, 9f); lineTo(7f, 9f)
            lineTo(7f, 7f); lineTo(9f, 7f); close()
        }
        SkinIconStyle.CLASSIC -> navPath(style, tint) {
            repeat(8) { tooth ->
                listOf(0f to 6.6f, 0.15f to 8.5f, 0.6f to 8.5f, 0.75f to 6.6f).forEachIndexed { point, (fraction, radius) ->
                    val p = navPolar(-Math.PI / 2 + (tooth + fraction) * Math.PI / 4, radius)
                    if (tooth == 0 && point == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
            }
            close()
        }
        SkinIconStyle.NAVY -> {
            // An open mechanical wheel, with stout exposed radial teeth.
            drawCircle(tint, 6.1f, Offset(12f, 12f), style = style.stroke)
            repeat(8) { tooth ->
                val a = tooth * Math.PI / 4
                val start = navPolar(a, 6.1f)
                val end = navPolar(a, 8.5f)
                navLine(style, tint, start.x, start.y, end.x, end.y)
            }
        }
    }
    when (style) {
        SkinIconStyle.CHINESE -> navPath(style, tint) {
            moveTo(9f, 9f); lineTo(15f, 9f); lineTo(15f, 15f); lineTo(9f, 15f); close()
        }
        SkinIconStyle.VICTORIAN -> drawOval(tint, Offset(9.5f, 8.5f), Size(5f, 7f), style = style.stroke)
        else -> drawCircle(tint, 3f, Offset(12f, 12f), style = style.stroke)
    }
}

private fun navPolar(angle: Double, radius: Float): Offset =
    Offset(12f + radius * cos(angle).toFloat(), 12f + radius * sin(angle).toFloat())

private fun DrawScope.drawPetalGear(
    style: SkinIconStyle,
    tint: Color,
    petals: Int,
    inner: Float,
    outer: Float,
    scaleX: Float,
    scaleY: Float
) {
    val step = 2.0 * Math.PI / petals
    fun point(angle: Double, radius: Float) = Offset(
        12f + radius * cos(angle).toFloat() * scaleX,
        12f + radius * sin(angle).toFloat() * scaleY
    )
    navPath(style, tint) {
        repeat(petals) { i ->
            val angle = -Math.PI / 2 + i * step
            val start = point(angle, inner)
            val c1 = point(angle + step / 4, outer)
            val c2 = point(angle + step * 3 / 4, outer)
            val end = point(angle + step, inner)
            if (i == 0) moveTo(start.x, start.y)
            cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)
        }
        close()
    }
}
