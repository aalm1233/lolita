package com.lolita.app.ui.theme.skin.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

/** Seven original vessel silhouettes on the existing 24-unit optical grid. */
internal fun DrawScope.drawLaboratoryIcon(style: SkinIconStyle, tint: Color) {
    val vessel = Path().apply {
        when (style) {
            SkinIconStyle.SWEET -> {
                moveTo(9f, 3f)
                lineTo(15f, 3f)
                moveTo(10f, 3f)
                lineTo(10f, 8f)
                cubicTo(10f, 10f, 5f, 12f, 5f, 17f)
                cubicTo(5f, 21f, 8f, 22f, 12f, 22f)
                cubicTo(16f, 22f, 19f, 21f, 19f, 17f)
                cubicTo(19f, 12f, 14f, 10f, 14f, 8f)
                lineTo(14f, 3f)
                moveTo(6f, 16f)
                cubicTo(9f, 14f, 14f, 18f, 18f, 16f)
            }
            SkinIconStyle.GOTHIC -> {
                moveTo(9f, 3f)
                lineTo(15f, 3f)
                moveTo(10f, 3f)
                lineTo(10f, 9f)
                lineTo(4f, 18f)
                lineTo(7f, 22f)
                lineTo(17f, 22f)
                lineTo(20f, 18f)
                lineTo(14f, 9f)
                lineTo(14f, 3f)
                moveTo(7f, 16f)
                lineTo(17f, 16f)
                moveTo(10f, 19f)
                lineTo(14f, 19f)
            }
            SkinIconStyle.CHINESE -> {
                moveTo(9f, 3f)
                lineTo(15f, 3f)
                moveTo(10f, 3f)
                lineTo(10f, 7f)
                cubicTo(6f, 8f, 7f, 12f, 10f, 12f)
                cubicTo(3f, 15f, 5f, 22f, 12f, 22f)
                cubicTo(19f, 22f, 21f, 15f, 14f, 12f)
                cubicTo(17f, 12f, 18f, 8f, 14f, 7f)
                lineTo(14f, 3f)
                moveTo(7f, 17f)
                cubicTo(10f, 16f, 14f, 18f, 17f, 17f)
            }
            SkinIconStyle.CLASSIC -> {
                moveTo(9f, 3f)
                lineTo(15f, 3f)
                moveTo(10f, 3f)
                lineTo(10f, 11f)
                cubicTo(6f, 12f, 5f, 15f, 6f, 18f)
                cubicTo(7f, 21f, 9f, 22f, 12f, 22f)
                cubicTo(15f, 22f, 17f, 21f, 18f, 18f)
                cubicTo(19f, 15f, 18f, 12f, 14f, 11f)
                lineTo(14f, 3f)
                moveTo(6f, 16f)
                lineTo(18f, 16f)
            }
            SkinIconStyle.NAVY -> {
                moveTo(8f, 3f)
                lineTo(16f, 3f)
                moveTo(9f, 3f)
                lineTo(9f, 9f)
                lineTo(5f, 13f)
                lineTo(5f, 19f)
                lineTo(8f, 22f)
                lineTo(16f, 22f)
                lineTo(19f, 19f)
                lineTo(19f, 13f)
                lineTo(15f, 9f)
                lineTo(15f, 3f)
                moveTo(5f, 15f)
                cubicTo(8f, 13f, 11f, 17f, 14f, 15f)
                cubicTo(16f, 14f, 17f, 14f, 19f, 15f)
                moveTo(10f, 19f)
                lineTo(14f, 19f)
            }
            SkinIconStyle.COUNTRY -> {
                moveTo(8f, 4f)
                quadraticTo(12f, 2f, 16f, 4f)
                moveTo(9f, 4f)
                lineTo(9f, 9f)
                quadraticTo(4f, 10f, 4f, 16f)
                quadraticTo(4f, 22f, 12f, 22f)
                quadraticTo(20f, 22f, 20f, 16f)
                quadraticTo(20f, 10f, 15f, 9f)
                lineTo(15f, 4f)
                moveTo(4f, 16f)
                quadraticTo(8f, 13f, 12f, 16f)
                quadraticTo(16f, 19f, 20f, 16f)
            }
            SkinIconStyle.VICTORIAN -> {
                moveTo(8f, 3f)
                lineTo(16f, 3f)
                moveTo(10f, 3f)
                lineTo(10f, 10f)
                cubicTo(10f, 13f, 7f, 13f, 7f, 17f)
                cubicTo(7f, 20f, 9f, 21f, 12f, 21f)
                cubicTo(15f, 21f, 17f, 20f, 17f, 17f)
                cubicTo(17f, 13f, 14f, 13f, 14f, 10f)
                lineTo(14f, 3f)
                moveTo(9f, 22f)
                lineTo(15f, 22f)
                moveTo(8f, 16f)
                quadraticTo(12f, 18f, 16f, 16f)
            }
        }
    }
    drawPath(vessel, tint, style = style.stroke)
}
