package com.lolita.app.ui.theme.skin.icon

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** Tools carry each skin's construction language; elementary symbols keep direct semantics. */
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
            controlStem(tint, style, 5f, 12f, 19f, 12f)
            controlStem(tint, style, 12f, 5f, 12f, 19f)
        }
        IconKey.Delete -> {
            controlToolBin(tint, style)
        }
        IconKey.Edit -> {
            controlPen(tint, style)
        }
        IconKey.Search -> {
            controlShape(tint, style, 3.8f, 3.8f, 12.7f, 12.7f)
            when (style) {
                SkinIconStyle.SWEET -> controlPath(tint, stroke) { moveTo(14.7f,14.7f); quadraticTo(18f,15f,20f,19.5f) }
                SkinIconStyle.GOTHIC -> controlPath(tint, stroke) { moveTo(14.8f,14.8f); lineTo(19.8f,18f); lineTo(18f,19.8f); close() }
                SkinIconStyle.CHINESE -> controlPath(tint, stroke) { moveTo(14.7f,14.7f); lineTo(19.5f,19.5f); lineTo(20f,17.4f) }
                SkinIconStyle.CLASSIC -> controlStem(tint, style,14.5f,14.5f,19.5f,19.5f)
                SkinIconStyle.NAVY -> controlPath(tint, stroke) { moveTo(14.7f,14.7f); lineTo(17f,15.4f); lineTo(20f,18.4f); lineTo(18.4f,20f); lineTo(15.4f,17f); close() }
                SkinIconStyle.COUNTRY -> controlPath(tint, stroke) { moveTo(14.6f,14.6f); cubicTo(16f,17.5f,19f,17f,19.6f,20f) }
                SkinIconStyle.VICTORIAN -> controlPath(tint, stroke) { moveTo(14.5f,14.5f); quadraticTo(15f,18f,18.7f,20f); quadraticTo(20f,20f,19.5f,18.6f); quadraticTo(18f,15f,14.5f,14.5f) }
            }
        }
        IconKey.Sort -> {
            controlStem(tint, style, 4f, 6f, 20f, 6f)
            controlStem(tint, style, 4f, 12f, 15f, 12f)
            controlStem(tint, style, 4f, 18f, 10f, 18f)
        }
        IconKey.Save -> controlCheck(tint, stroke, 5f, 12f, 10f, 17f, 19f, 7f)
        IconKey.Close -> {
            controlStem(tint, style, 6f, 6f, 18f, 18f)
            controlStem(tint, style, 18f, 6f, 6f, 18f)
        }
        IconKey.Share -> {
            controlStem(tint, style, 8.3f, 10.7f, 15.7f, 6.3f)
            controlStem(tint, style, 8.3f, 13.3f, 15.7f, 17.7f)
            drawCircle(tint, 2.6f, Offset(6f, 12f), style = stroke)
            drawCircle(tint, 2.6f, Offset(18f, 5f), style = stroke)
            drawCircle(tint, 2.6f, Offset(18f, 19f), style = stroke)
        }
        IconKey.FilterList -> {
            controlStem(tint, style, 4f, 6f, 20f, 6f)
            controlStem(tint, style, 7.5f, 12f, 16.5f, 12f)
            controlStem(tint, style, 10.5f, 18f, 13.5f, 18f)
        }
        IconKey.MoreVert -> {
            for (y in listOf(5.5f, 12f, 18.5f)) {
                when(style) {
                    SkinIconStyle.GOTHIC -> drawPath(Path().apply { moveTo(12f,y-1.6f); lineTo(13.5f,y); lineTo(12f,y+1.6f); lineTo(10.5f,y); close() },tint)
                    SkinIconStyle.CHINESE, SkinIconStyle.CLASSIC -> drawRect(tint,Offset(10.8f,y-1.2f),Size(2.4f,2.4f))
                    SkinIconStyle.NAVY -> drawRoundRect(tint,Offset(10.2f,y-1f),Size(3.6f,2f),CornerRadius(1f))
                    SkinIconStyle.VICTORIAN -> drawOval(tint,Offset(11f,y-1.6f),Size(2f,3.2f))
                    else -> drawCircle(tint,1.4f,Offset(12f,y))
                }
            }
        }
        IconKey.ContentCopy -> {
            controlShape(tint, style, 8f, 8f, 12f, 12f)
            controlPath(tint, stroke) { moveTo(5.3f,16f); lineTo(4f,16f); lineTo(4f,5f); lineTo(5f,4f); lineTo(16f,4f); lineTo(16f,5.5f) }
        }
        IconKey.Refresh -> {
            controlPath(tint, stroke) {
                when (style) {
                    SkinIconStyle.SWEET -> { moveTo(19.5f,9f); cubicTo(18f,2.8f,7f,2.8f,4.5f,9f); cubicTo(1.5f,17f,11f,23f,18.5f,16.7f) }
                    SkinIconStyle.GOTHIC -> { moveTo(19.5f,9f); lineTo(15f,4f); lineTo(8f,4f); lineTo(4f,9f); lineTo(4f,15f); lineTo(9f,20f); lineTo(15f,20f); lineTo(19f,16f) }
                    SkinIconStyle.CHINESE -> { moveTo(19.5f,9f); lineTo(19.5f,5f); lineTo(5f,5f); lineTo(5f,19f); lineTo(17f,19f); lineTo(19f,16f) }
                    SkinIconStyle.CLASSIC -> { moveTo(19.5f,9f); cubicTo(18f,5f,15f,4f,12f,4f); cubicTo(1f,4f,1f,20f,12f,20f); quadraticTo(17f,20f,19f,16f) }
                    SkinIconStyle.NAVY -> { moveTo(19.5f,9f); quadraticTo(19f,5f,15f,5f); lineTo(8f,5f); cubicTo(1.5f,5f,1.5f,19f,8f,19f); lineTo(15f,19f); quadraticTo(18f,19f,19.5f,16f) }
                    SkinIconStyle.COUNTRY -> { moveTo(19.5f,9f); cubicTo(17f,3f,9f,3f,5f,7f); cubicTo(1f,13f,6f,21f,12f,20f); quadraticTo(17f,20f,19f,16f) }
                    SkinIconStyle.VICTORIAN -> { moveTo(19.5f,9f); cubicTo(19f,5f,16f,3.5f,12f,3.5f); cubicTo(6f,3.5f,4.5f,8f,4.5f,13f); cubicTo(4.5f,22f,16f,22f,19f,16f) }
                }
                moveTo(20f,4.5f); lineTo(19.5f,9f); lineTo(15f,8.5f)
            }
        }
        IconKey.ViewAgenda -> {
            val horizontal = style == SkinIconStyle.NAVY
            val x = if (horizontal) 3.5f else 4f
            val width = if (horizontal) 17f else 16f
            controlShape(tint, style, x, 4f, width, 6f)
            controlShape(tint, style, x, 14f, width, 6f)
        }
        IconKey.GridView -> {
            for (x in listOf(4f,14f)) for (y in listOf(4f,14f)) controlShape(tint,style,x,y,6f,6f)
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
            controlPath(tint, stroke) { moveTo(8f,4f); lineTo(20f,4f); lineTo(20f,15f) }
            controlShape(tint,style,3.5f,7f,13f,13f)
            drawCircle(tint,1.1f,Offset(7f,11f))
            controlPath(tint,stroke) { moveTo(4.5f,17f); lineTo(8f,14f); lineTo(10.5f,16f); lineTo(13f,13.5f); lineTo(15.5f,17f) }
        }
        IconKey.ArrowBack -> {
            controlStem(tint, style, 4f, 12f, 20f, 12f)
            controlPath(tint, stroke) {
                moveTo(10f, 6f)
                lineTo(4f, 12f)
                lineTo(10f, 18f)
            }
        }
        IconKey.ArrowForward -> {
            controlStem(tint, style, 4f, 12f, 20f, 12f)
            controlPath(tint, stroke) {
                moveTo(14f, 6f)
                lineTo(20f, 12f)
                lineTo(14f, 18f)
            }
        }
        IconKey.KeyboardArrowLeft -> controlChevron(tint,style,15f,5.5f,8.5f,12f,15f,18.5f)
        IconKey.KeyboardArrowRight -> controlChevron(tint,style,9f,5.5f,15.5f,12f,9f,18.5f)
        IconKey.ExpandMore -> controlChevron(tint,style,5.5f,8.5f,12f,15f,18.5f,8.5f)
        IconKey.ExpandLess -> controlChevron(tint,style,5.5f,15.5f,12f,9f,18.5f,15.5f)
        IconKey.ArrowDropDown -> drawPath(Path().apply {
            moveTo(7.5f, 9.5f)
            lineTo(16.5f, 9.5f)
            lineTo(12f, 14.5f)
            close()
        }, tint)
        IconKey.SwapVert -> {
            controlStem(tint, style, 8f, 4f, 8f, 18.5f)
            controlPath(tint, stroke) {
                moveTo(4.5f, 7.5f)
                lineTo(8f, 4f)
                lineTo(11.5f, 7.5f)
            }
            controlStem(tint, style, 16f, 5.5f, 16f, 20f)
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
            controlShape(tint, style, 3.4f, 3.4f, 17.2f, 17.2f)
            controlCheck(tint, stroke, 7.5f, 12f, 10.5f, 15.2f, 16.5f, 9.3f)
        }
        IconKey.Warning -> {
            controlPath(tint, stroke) {
                moveTo(12f, 3.8f)
                when (style) {
                    SkinIconStyle.SWEET -> { quadraticTo(13f,3.8f,20.5f,18.5f); quadraticTo(21f,20f,19f,20f); lineTo(5f,20f); quadraticTo(3f,20f,3.5f,18.5f); quadraticTo(11f,3.8f,12f,3.8f) }
                    SkinIconStyle.GOTHIC -> { lineTo(20.5f,18f); lineTo(19.5f,20f); lineTo(4.5f,20f); lineTo(3.5f,18f); close() }
                    SkinIconStyle.NAVY -> { lineTo(20.5f,19f); lineTo(3.5f,19f); close() }
                    SkinIconStyle.COUNTRY -> { quadraticTo(15f,8f,20.5f,20f); quadraticTo(12f,18.8f,3.5f,20f); quadraticTo(8f,8f,12f,3.8f) }
                    SkinIconStyle.VICTORIAN -> { quadraticTo(17f,10f,20f,20f); quadraticTo(12f,18.5f,4f,20f); quadraticTo(7f,10f,12f,3.8f) }
                    else -> { lineTo(20.5f,20f); lineTo(3.5f,20f); close() }
                }
            }
            controlLine(tint, stroke, 12f, 9f, 12f, 13.5f)
            drawCircle(tint, 0.95f, Offset(12f, 17f))
        }
        IconKey.Error -> {
            controlShape(tint, style, 3.4f, 3.4f, 17.2f, 17.2f)
            controlLine(tint, stroke, 12f, 7.3f, 12f, 12.7f)
            drawCircle(tint, 0.95f, Offset(12f, 16.3f))
        }
        IconKey.Info -> {
            controlShape(tint, style, 3.4f, 3.4f, 17.2f, 17.2f)
            drawCircle(tint, 0.95f, Offset(12f, 7.6f))
            controlLine(tint, stroke, 12f, 11f, 12f, 17f)
        }
        IconKey.Visibility -> {
            controlPath(tint, stroke) {
                moveTo(2.8f, 12f)
                val rise = when(style) { SkinIconStyle.SWEET -> 3.8f; SkinIconStyle.GOTHIC -> 6f; SkinIconStyle.CHINESE -> 5f; SkinIconStyle.CLASSIC -> 5.8f; SkinIconStyle.NAVY -> 7f; SkinIconStyle.COUNTRY -> 4.7f; SkinIconStyle.VICTORIAN -> 3.2f }
                if (style == SkinIconStyle.GOTHIC || style == SkinIconStyle.CHINESE) {
                    lineTo(8f,rise+1f); lineTo(16f,rise+1f); lineTo(21.2f,12f); lineTo(16f,23f-rise); lineTo(8f,23f-rise); lineTo(2.8f,12f)
                } else {
                    cubicTo(7f,rise,17f,rise,21.2f,12f)
                    cubicTo(17f,24f-rise,7f,24f-rise,2.8f,12f)
                }
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
            controlShape(tint, style, 3.4f, 3.4f, 17.2f, 17.2f)
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


/** A semantic object's outline, not a decorative frame placed around a smaller icon. */
private fun DrawScope.controlShape(color: Color, style: SkinIconStyle, x: Float, y: Float, w: Float, h: Float) {
    controlPath(color, style.stroke) {
        when (style) {
            SkinIconStyle.SWEET -> { moveTo(x+w*.3f,y); lineTo(x+w*.7f,y); quadraticTo(x+w,y,x+w,y+h*.3f); lineTo(x+w,y+h*.7f); quadraticTo(x+w,y+h,x+w*.7f,y+h); lineTo(x+w*.3f,y+h); quadraticTo(x,y+h,x,y+h*.7f); lineTo(x,y+h*.3f); quadraticTo(x,y,x+w*.3f,y); close() }
            SkinIconStyle.GOTHIC -> { moveTo(x+w*.25f,y); lineTo(x+w*.75f,y); lineTo(x+w,y+h*.25f); lineTo(x+w,y+h*.75f); lineTo(x+w*.75f,y+h); lineTo(x+w*.25f,y+h); lineTo(x,y+h*.75f); lineTo(x,y+h*.25f); close() }
            SkinIconStyle.CHINESE -> { moveTo(x,y+h*.18f); lineTo(x+w*.18f,y+h*.18f); lineTo(x+w*.18f,y); lineTo(x+w,y); lineTo(x+w,y+h*.82f); lineTo(x+w*.82f,y+h*.82f); lineTo(x+w*.82f,y+h); lineTo(x,y+h); close() }
            SkinIconStyle.CLASSIC -> { moveTo(x,y); lineTo(x+w,y); lineTo(x+w,y+h); lineTo(x,y+h); close() }
            SkinIconStyle.NAVY -> { moveTo(x+w*.3f,y); lineTo(x+w*.7f,y); cubicTo(x+w*1.1f,y,x+w*1.1f,y+h,x+w*.7f,y+h); lineTo(x+w*.3f,y+h); cubicTo(x-w*.1f,y+h,x-w*.1f,y,x+w*.3f,y); close() }
            SkinIconStyle.COUNTRY -> { moveTo(x+w*.1f,y+h*.08f); quadraticTo(x+w*.5f,y-h*.12f,x+w*.9f,y+h*.08f); lineTo(x+w,y+h*.84f); quadraticTo(x+w*.5f,y+h*1.06f,x,y+h*.9f); close() }
            SkinIconStyle.VICTORIAN -> { moveTo(x,y+h*.35f); cubicTo(x,y-h*.1f,x+w,y-h*.1f,x+w,y+h*.35f); lineTo(x+w*.88f,y+h*.85f); quadraticTo(x+w*.5f,y+h*1.15f,x+w*.12f,y+h*.85f); close() }
        }
    }
}

private fun DrawScope.controlStem(color: Color, style: SkinIconStyle, x1: Float,y1: Float,x2: Float,y2: Float) {
    controlLine(color,style.stroke,x1,y1,x2,y2)
    val dx=x2-x1; val dy=y2-y1
    val length=kotlin.math.sqrt(dx*dx+dy*dy)
    if (length < 4f) return
    val nx=-dy/length; val ny=dx/length
    when(style) {
        SkinIconStyle.CHINESE -> controlLine(color,style.stroke,x2,y2,x2+nx*1.15f-dx/length*.5f,y2+ny*1.15f-dy/length*.5f)
        SkinIconStyle.CLASSIC -> controlLine(color,style.stroke,x1-nx*.65f,y1-ny*.65f,x1+nx*.65f,y1+ny*.65f)
        SkinIconStyle.VICTORIAN -> { drawCircle(color,1.05f,Offset(x1,y1)); drawCircle(color,1.05f,Offset(x2,y2)) }
        SkinIconStyle.COUNTRY -> controlLine(color,style.stroke,x1+dx*.38f,y1+dy*.38f,x1+dx*.38f+nx*1f,y1+dy*.38f+ny*1f)
        else -> Unit
    }
}

private fun DrawScope.controlToolBin(color: Color, style: SkinIconStyle) {
    val stroke=style.stroke
    if (style == SkinIconStyle.NAVY) controlShape(color,style,5f,9f,14f,11f)
    else controlShape(color,style,6f,8f,12f,12f)
    controlPath(color,stroke) {
        when(style) {
            SkinIconStyle.SWEET -> { moveTo(4.5f,7f); quadraticTo(12f,4.2f,19.5f,7f); moveTo(9f,5.5f); quadraticTo(12f,1.5f,15f,5.5f) }
            SkinIconStyle.GOTHIC -> { moveTo(4.5f,7f); lineTo(7f,5f); lineTo(17f,5f); lineTo(19.5f,7f); moveTo(10f,5f); lineTo(10f,3.5f); lineTo(14f,3.5f); lineTo(14f,5f) }
            SkinIconStyle.CHINESE -> { moveTo(4f,5.5f); lineTo(4f,7f); lineTo(20f,7f); lineTo(20f,5.5f); moveTo(9f,5f); lineTo(9f,3.5f); lineTo(15f,3.5f); lineTo(15f,5f) }
            SkinIconStyle.CLASSIC -> { moveTo(4.5f,6.5f); lineTo(19.5f,6.5f); moveTo(9f,6.5f); lineTo(9f,3.5f); lineTo(15f,3.5f); lineTo(15f,6.5f) }
            SkinIconStyle.NAVY -> { moveTo(4f,7f); lineTo(20f,7f); moveTo(5.5f,5f); lineTo(18.5f,5f); moveTo(10f,5f); lineTo(10f,3.5f); lineTo(14f,3.5f); lineTo(14f,5f) }
            SkinIconStyle.COUNTRY -> { moveTo(4.5f,6.5f); quadraticTo(12f,5f,19.5f,7f); moveTo(9f,5.5f); lineTo(10f,3.5f); lineTo(14f,3.7f); lineTo(15f,5.7f) }
            SkinIconStyle.VICTORIAN -> { moveTo(4.5f,7f); quadraticTo(5f,4.5f,12f,4.5f); quadraticTo(19f,4.5f,19.5f,7f); moveTo(10f,4.5f); quadraticTo(12f,1.8f,14f,4.5f) }
        }
    }
    controlLine(color,stroke,10f,11f,10.4f,17f)
    controlLine(color,stroke,14f,11f,13.6f,17f)
}

private fun DrawScope.controlPen(color: Color, style: SkinIconStyle) {
    val stroke=style.stroke
    // Chinese brush and Victorian quill have genuine writing tips, not ornamental badges.
    if (style==SkinIconStyle.CHINESE || style==SkinIconStyle.VICTORIAN || style==SkinIconStyle.COUNTRY) {
        controlPath(color,stroke) {
            moveTo(4f,20f); lineTo(7f,14f)
            when(style) {
                SkinIconStyle.CHINESE -> { lineTo(16f,4f); lineTo(19f,5f); lineTo(10f,16f); close(); moveTo(7f,14f); lineTo(10f,16f) }
                SkinIconStyle.VICTORIAN -> { cubicTo(9f,7f,14f,3f,20f,4f); cubicTo(19f,10f,15f,15f,9f,16f); lineTo(4f,20f); moveTo(7f,17f); lineTo(17f,7f) }
                else -> { quadraticTo(10f,9f,17f,4f); quadraticTo(20f,3.5f,19f,7f); quadraticTo(14f,13f,9f,16f); close(); moveTo(7f,14f); lineTo(9f,16f) }
            }
        }
    } else {
        controlPath(color,stroke) {
            moveTo(4f,20f); lineTo(5.3f,15f); lineTo(16f,4.3f)
            when(style) {
                SkinIconStyle.SWEET -> { quadraticTo(17.6f,2.8f,19.5f,5f); quadraticTo(21f,6.6f,19.5f,8f) }
                SkinIconStyle.GOTHIC -> { lineTo(18f,3.8f); lineTo(20f,6f); lineTo(19.5f,8f) }
                SkinIconStyle.NAVY -> { lineTo(18.5f,4.3f); lineTo(20f,5.8f); lineTo(20f,8f) }
                else -> { lineTo(19.5f,7.8f) }
            }
            lineTo(9f,18.8f); close(); moveTo(5.3f,15f); lineTo(9f,18.8f); moveTo(14f,6.3f); lineTo(17.8f,10f)
        }
    }
}

private fun DrawScope.controlChevron(color: Color, style: SkinIconStyle, x1: Float,y1: Float,x2: Float,y2: Float,x3: Float,y3: Float) {
    controlPath(color,style.stroke) {
        moveTo(x1,y1)
        when(style) {
            SkinIconStyle.SWEET -> { quadraticTo(x1+(x2-x1)*.25f,y1+(y2-y1)*.75f,x2,y2); quadraticTo(x3+(x2-x3)*.25f,y3+(y2-y3)*.75f,x3,y3) }
            SkinIconStyle.COUNTRY -> { quadraticTo((x1+x2)*.5f+.6f,(y1+y2)*.5f,x2,y2); quadraticTo((x2+x3)*.5f-.6f,(y2+y3)*.5f,x3,y3) }
            SkinIconStyle.VICTORIAN -> { quadraticTo(x1+(x2-x1)*.85f,y1+(y2-y1)*.15f,x2,y2); quadraticTo(x3+(x2-x3)*.85f,y3+(y2-y3)*.15f,x3,y3) }
            SkinIconStyle.NAVY -> { lineTo(x1+(x2-x1)*.82f,y1+(y2-y1)*.82f); lineTo(x2,y2); lineTo(x3,y3) }
            else -> { lineTo(x2,y2); lineTo(x3,y3) }
        }
    }
    if(style==SkinIconStyle.CHINESE) controlLine(color,style.stroke,x3,y3,x3+(x2-x3)*.13f,y3+(y2-y3)*.13f+1f)
}
