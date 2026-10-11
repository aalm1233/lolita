package com.lolita.app.ui.theme.skin.icon

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** Clear, shared control silhouettes; the skin supplies the line finish and corner treatment. */
internal fun DrawScope.drawPolishedControlIcon(key: IconKey, style: SkinIconStyle, tint: Color) {
    val stroke = style.stroke
    val corner = when (style) {
        SkinIconStyle.SWEET, SkinIconStyle.COUNTRY -> 1.8f
        SkinIconStyle.GOTHIC -> 0.35f
        SkinIconStyle.CHINESE, SkinIconStyle.NAVY -> 0.8f
        SkinIconStyle.CLASSIC, SkinIconStyle.VICTORIAN -> 1.2f
    }
    when (key) {
        IconKey.Add -> {
            controlLine(tint, stroke, 5f, 12f, 19f, 12f)
            controlLine(tint, stroke, 12f, 5f, 12f, 19f)
        }
        IconKey.Delete -> {
            controlLine(tint, stroke, 4.5f, 6.5f, 19.5f, 6.5f)
            controlPath(tint, stroke) {
                moveTo(9f, 6.5f)
                lineTo(9f, 3.8f)
                lineTo(15f, 3.8f)
                lineTo(15f, 6.5f)
            }
            controlPath(tint, stroke) {
                moveTo(6f, 9f)
                lineTo(6.8f, 18.6f)
                quadraticTo(6.9f, 20f, 8.4f, 20f)
                lineTo(15.6f, 20f)
                quadraticTo(17.1f, 20f, 17.2f, 18.6f)
                lineTo(18f, 9f)
            }
            controlLine(tint, stroke, 10f, 10.5f, 10.3f, 16.5f)
            controlLine(tint, stroke, 14f, 10.5f, 13.7f, 16.5f)
        }
        IconKey.Edit -> {
            controlPath(tint, stroke) {
                moveTo(4.2f, 19.8f)
                lineTo(5.4f, 15.3f)
                lineTo(16.1f, 4.6f)
                quadraticTo(17f, 3.7f, 18f, 4.6f)
                lineTo(19.4f, 6f)
                quadraticTo(20.3f, 7f, 19.4f, 7.9f)
                lineTo(8.7f, 18.6f)
                close()
                moveTo(14.2f, 6.5f)
                lineTo(17.5f, 9.8f)
                moveTo(5.4f, 15.3f)
                lineTo(8.7f, 18.6f)
            }
        }
        IconKey.Search -> {
            drawCircle(tint, 6.2f, Offset(10.3f, 10.3f), style = stroke)
            controlLine(tint, stroke, 14.8f, 14.8f, 20f, 20f)
        }
        IconKey.Sort -> {
            controlLine(tint, stroke, 4f, 6f, 20f, 6f)
            controlLine(tint, stroke, 4f, 12f, 15f, 12f)
            controlLine(tint, stroke, 4f, 18f, 10f, 18f)
        }
        IconKey.Save -> controlCheck(tint, stroke, 5f, 12f, 10f, 17f, 19f, 7f)
        IconKey.Close -> {
            controlLine(tint, stroke, 6f, 6f, 18f, 18f)
            controlLine(tint, stroke, 18f, 6f, 6f, 18f)
        }
        IconKey.Share -> {
            controlLine(tint, stroke, 8.3f, 10.7f, 15.7f, 6.3f)
            controlLine(tint, stroke, 8.3f, 13.3f, 15.7f, 17.7f)
            drawCircle(tint, 2.6f, Offset(6f, 12f), style = stroke)
            drawCircle(tint, 2.6f, Offset(18f, 5f), style = stroke)
            drawCircle(tint, 2.6f, Offset(18f, 19f), style = stroke)
        }
        IconKey.FilterList -> {
            controlLine(tint, stroke, 4f, 6f, 20f, 6f)
            controlLine(tint, stroke, 7.5f, 12f, 16.5f, 12f)
            controlLine(tint, stroke, 10.5f, 18f, 13.5f, 18f)
        }
        IconKey.MoreVert -> {
            for (y in listOf(5.5f, 12f, 18.5f)) {
                drawCircle(tint, 1.4f, Offset(12f, y))
            }
        }
        IconKey.ContentCopy -> {
            controlRect(tint, stroke, 8f, 8f, 12f, 12f, corner)
            controlPath(tint, stroke) {
                moveTo(5.5f, 16f)
                lineTo(4f, 16f)
                lineTo(4f, 4f + corner)
                quadraticTo(4f, 4f, 4f + corner, 4f)
                lineTo(16f, 4f)
                lineTo(16f, 5.5f)
            }
        }
        IconKey.Refresh -> {
            controlPath(tint, stroke) {
                moveTo(19.5f, 9f)
                cubicTo(18.3f, 5.7f, 15.7f, 4f, 12f, 4f)
                cubicTo(7.6f, 4f, 4f, 7.6f, 4f, 12f)
                cubicTo(4f, 16.4f, 7.6f, 20f, 12f, 20f)
                cubicTo(15f, 20f, 17.7f, 18.4f, 19f, 16f)
                moveTo(20f, 4.3f)
                lineTo(19.5f, 9f)
                lineTo(14.8f, 8.5f)
            }
        }
        IconKey.ViewAgenda -> {
            controlRect(tint, stroke, 4f, 4.5f, 16f, 5.8f, corner)
            controlRect(tint, stroke, 4f, 13.7f, 16f, 5.8f, corner)
        }
        IconKey.GridView -> {
            for (x in listOf(4f, 14f)) {
                for (y in listOf(4f, 14f)) {
                    controlRect(tint, stroke, x, y, 6f, 6f, corner)
                }
            }
        }
        IconKey.Apps -> {
            for (x in listOf(3.5f, 10.2f, 16.9f)) {
                for (y in listOf(3.5f, 10.2f, 16.9f)) {
                    drawRoundRect(
                        tint, Offset(x, y), Size(3.6f, 3.6f),
                        CornerRadius(corner.coerceAtMost(1f))
                    )
                }
            }
        }
        IconKey.Gallery -> {
            controlPath(tint, stroke) {
                moveTo(7f, 4f)
                lineTo(20f - corner, 4f)
                quadraticTo(20f, 4f, 20f, 4f + corner)
                lineTo(20f, 16f)
            }
            controlRect(tint, stroke, 3f, 7f, 14f, 13f, corner)
            drawCircle(tint, 1.2f, Offset(7f, 11f))
            controlPath(tint, stroke) {
                moveTo(3.5f, 17f)
                lineTo(7.5f, 13.5f)
                lineTo(10.5f, 16f)
                lineTo(13f, 13f)
                lineTo(16.5f, 17f)
            }
        }
        IconKey.ArrowBack -> {
            controlLine(tint, stroke, 4f, 12f, 20f, 12f)
            controlPath(tint, stroke) {
                moveTo(10f, 6f)
                lineTo(4f, 12f)
                lineTo(10f, 18f)
            }
        }
        IconKey.ArrowForward -> {
            controlLine(tint, stroke, 4f, 12f, 20f, 12f)
            controlPath(tint, stroke) {
                moveTo(14f, 6f)
                lineTo(20f, 12f)
                lineTo(14f, 18f)
            }
        }
        IconKey.KeyboardArrowLeft -> controlPath(tint, stroke) {
            moveTo(15f, 5.5f)
            lineTo(8.5f, 12f)
            lineTo(15f, 18.5f)
        }
        IconKey.KeyboardArrowRight -> controlPath(tint, stroke) {
            moveTo(9f, 5.5f)
            lineTo(15.5f, 12f)
            lineTo(9f, 18.5f)
        }
        IconKey.ExpandMore -> controlPath(tint, stroke) {
            moveTo(5.5f, 8.5f)
            lineTo(12f, 15f)
            lineTo(18.5f, 8.5f)
        }
        IconKey.ExpandLess -> controlPath(tint, stroke) {
            moveTo(5.5f, 15.5f)
            lineTo(12f, 9f)
            lineTo(18.5f, 15.5f)
        }
        IconKey.ArrowDropDown -> drawPath(Path().apply {
            moveTo(7.5f, 9.5f)
            lineTo(16.5f, 9.5f)
            lineTo(12f, 14.5f)
            close()
        }, tint)
        IconKey.SwapVert -> {
            controlLine(tint, stroke, 8f, 4f, 8f, 18.5f)
            controlPath(tint, stroke) {
                moveTo(4.5f, 7.5f)
                lineTo(8f, 4f)
                lineTo(11.5f, 7.5f)
            }
            controlLine(tint, stroke, 16f, 5.5f, 16f, 20f)
            controlPath(tint, stroke) {
                moveTo(12.5f, 16.5f)
                lineTo(16f, 20f)
                lineTo(19.5f, 16.5f)
            }
        }
        IconKey.OpenInNew -> {
            controlPath(tint, stroke) {
                moveTo(11.5f, 4f)
                lineTo(4f + corner, 4f)
                quadraticTo(4f, 4f, 4f, 4f + corner)
                lineTo(4f, 20f - corner)
                quadraticTo(4f, 20f, 4f + corner, 20f)
                lineTo(20f - corner, 20f)
                quadraticTo(20f, 20f, 20f, 20f - corner)
                lineTo(20f, 12.5f)
                moveTo(14f, 4f)
                lineTo(20f, 4f)
                lineTo(20f, 10f)
                moveTo(10f, 14f)
                lineTo(20f, 4f)
            }
        }
        IconKey.CheckCircle -> {
            controlStatusCircle(tint, stroke)
            controlCheck(tint, stroke, 7.5f, 12f, 10.5f, 15.2f, 16.5f, 9.3f)
        }
        IconKey.Warning -> {
            controlPath(tint, stroke) {
                moveTo(12f, 3.5f)
                lineTo(21f, 20f)
                lineTo(3f, 20f)
                close()
            }
            controlLine(tint, stroke, 12f, 9f, 12f, 13.5f)
            drawCircle(tint, 0.95f, Offset(12f, 17f))
        }
        IconKey.Error -> {
            controlStatusCircle(tint, stroke)
            controlLine(tint, stroke, 12f, 7.3f, 12f, 12.7f)
            drawCircle(tint, 0.95f, Offset(12f, 16.3f))
        }
        IconKey.Info -> {
            controlStatusCircle(tint, stroke)
            drawCircle(tint, 0.95f, Offset(12f, 7.6f))
            controlLine(tint, stroke, 12f, 11f, 12f, 17f)
        }
        IconKey.Visibility -> {
            controlPath(tint, stroke) {
                moveTo(2.8f, 12f)
                cubicTo(7f, 4.8f, 17f, 4.8f, 21.2f, 12f)
                cubicTo(17f, 19.2f, 7f, 19.2f, 2.8f, 12f)
                close()
            }
            drawCircle(tint, 3.1f, Offset(12f, 12f), style = stroke)
        }
        IconKey.VisibilityOff -> {
            // Break the eye and pupil where the slash passes instead of painting an opaque mask.
            controlPath(tint, stroke) {
                moveTo(8.7f, 7.2f)
                cubicTo(13.1f, 5.5f, 18.2f, 7.1f, 21.2f, 12f)
                quadraticTo(20.2f, 13.8f, 18.3f, 15.3f)
                moveTo(15.3f, 16.8f)
                cubicTo(10.9f, 18.5f, 5.8f, 16.9f, 2.8f, 12f)
                quadraticTo(3.8f, 10.2f, 5.7f, 8.7f)
                moveTo(11.3f, 9f)
                cubicTo(13.6f, 8.4f, 15.6f, 10.4f, 15f, 12.7f)
                moveTo(12.7f, 15f)
                cubicTo(10.4f, 15.6f, 8.4f, 13.6f, 9f, 11.3f)
            }
            controlLine(tint, stroke, 4f, 4f, 20f, 20f)
        }
        IconKey.Help -> {
            controlStatusCircle(tint, stroke)
            controlPath(tint, stroke) {
                moveTo(9.4f, 8.4f)
                cubicTo(9.5f, 6.7f, 11f, 6.2f, 12.3f, 6.5f)
                cubicTo(15.4f, 6.9f, 15.6f, 10.1f, 13.2f, 11.6f)
                quadraticTo(12f, 12.3f, 12f, 13.5f)
            }
            drawCircle(tint, 0.95f, Offset(12f, 17f))
        }
        else -> error("Not a control icon: $key")
    }
}

private inline fun DrawScope.controlPath(color: Color, stroke: Stroke, path: Path.() -> Unit) {
    drawPath(Path().apply(path), color, style = stroke)
}

private fun DrawScope.controlLine(
    color: Color, stroke: Stroke, x1: Float, y1: Float, x2: Float, y2: Float
) {
    drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = stroke.width, cap = stroke.cap)
}

private fun DrawScope.controlRect(
    color: Color, stroke: Stroke, x: Float, y: Float, width: Float, height: Float, corner: Float
) {
    drawRoundRect(color, Offset(x, y), Size(width, height), CornerRadius(corner), style = stroke)
}

private fun DrawScope.controlCheck(
    color: Color, stroke: Stroke,
    x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float
) = controlPath(color, stroke) {
    moveTo(x1, y1)
    lineTo(x2, y2)
    lineTo(x3, y3)
}

private fun DrawScope.controlStatusCircle(color: Color, stroke: Stroke) {
    drawCircle(color, 8.7f, Offset(12f, 12f), style = stroke)
}
