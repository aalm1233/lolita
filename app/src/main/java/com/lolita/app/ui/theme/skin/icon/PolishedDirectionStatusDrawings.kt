package com.lolita.app.ui.theme.skin.icon

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform

/** Direction marks use the same construction when mirrored, including their hand-drawn details. */
internal fun DrawScope.drawPolishedDirectionStatusIcon(key: IconKey, style: SkinIconStyle, tint: Color) {
    when (key) {
        IconKey.ArrowBack -> directionArrow(style, tint)
        IconKey.ArrowForward -> withTransform({ scale(-1f, 1f, Offset(12f, 12f)) }) { directionArrow(style, tint) }
        IconKey.KeyboardArrowLeft -> directionChevron(style, tint)
        IconKey.KeyboardArrowRight -> withTransform({ scale(-1f, 1f, Offset(12f, 12f)) }) { directionChevron(style, tint) }
        IconKey.ExpandMore -> withTransform({ rotate(-90f, Offset(12f, 12f)) }) { directionChevron(style, tint) }
        IconKey.ExpandLess -> withTransform({ rotate(-90f, Offset(12f, 12f)); scale(-1f, 1f, Offset(12f, 12f)) }) { directionChevron(style, tint) }
        IconKey.ArrowDropDown -> directionDrop(style, tint)
        IconKey.SwapVert -> {
            directionSwapArrow(style, tint)
            withTransform({ rotate(180f, Offset(12f, 12f)) }) { directionSwapArrow(style, tint) }
        }
        IconKey.OpenInNew -> directionOpen(style, tint)
        IconKey.Visibility, IconKey.VisibilityOff -> {
            if (key == IconKey.Visibility) statusEye(style, tint)
            else {
                // A 3.5-unit diagonal corridor leaves actual transparent clearance around the slash.
                val gap = Path().apply { moveTo(0f, -2.5f); lineTo(26.5f, 24f); lineTo(24f, 26.5f); lineTo(-2.5f, 0f); close() }
                clipPath(gap, ClipOp.Difference) { statusEye(style, tint) }
                dsPath(style, tint) { moveTo(4f, 4f); lineTo(20f, 20f) }
            }
        }
        IconKey.Warning -> statusWarning(style, tint)
        IconKey.CheckCircle, IconKey.Error, IconKey.Info, IconKey.Help -> {
            statusFrame(key, style, tint)
            statusPunctuation(key, style, tint)
        }
        else -> error("Not a direction or status icon: $key")
    }
}

private fun DrawScope.dsPath(style: SkinIconStyle, tint: Color, block: Path.() -> Unit) =
    drawPath(Path().apply(block), tint, style = style.stroke)

private fun DrawScope.dsFill(tint: Color, block: Path.() -> Unit) = drawPath(Path().apply(block), tint)

/** Long left arrow: soft ribbon / lance / brush / drafting / signal / twig / quill. */
private fun DrawScope.directionArrow(style: SkinIconStyle, tint: Color) {
    when (style) {
        SkinIconStyle.SWEET -> dsPath(style, tint) {
            moveTo(10f, 5.5f); cubicTo(9f, 8.5f, 6f, 10.5f, 3.5f, 12f)
            cubicTo(6f, 13.5f, 9f, 15.5f, 10f, 18.5f)
            moveTo(4.5f, 12f); cubicTo(10f, 10f, 15f, 14f, 20f, 12f)
        }
        SkinIconStyle.GOTHIC -> {
            dsFill(tint) { moveTo(3f, 12f); lineTo(11f, 5f); lineTo(9f, 12f); lineTo(11f, 19f); close() }
            dsPath(style, tint) { moveTo(9f, 12f); lineTo(20f, 12f); moveTo(19f, 10f); lineTo(19f, 14f) }
        }
        SkinIconStyle.CHINESE -> {
            dsFill(tint) { moveTo(3f, 12.5f); quadraticTo(7f, 9f, 11f, 5f); lineTo(10f, 9f); lineTo(6f, 12.3f); quadraticTo(8f, 15f, 11f, 18.5f); lineTo(7.7f, 17f); close() }
            dsFill(tint) { moveTo(5f, 12f); quadraticTo(13f, 10.8f, 20.5f, 10.5f); lineTo(18.7f, 13.1f); quadraticTo(12f, 12.1f, 5f, 13.5f); close() }
        }
        SkinIconStyle.CLASSIC -> dsPath(style, tint) {
            moveTo(10f, 6f); lineTo(4f, 12f); lineTo(10f, 18f)
            moveTo(4f, 12f); lineTo(19.5f, 12f); moveTo(19.5f, 8.5f); lineTo(19.5f, 15.5f)
        }
        SkinIconStyle.NAVY -> dsPath(style, tint) {
            moveTo(3.5f, 12f); lineTo(10f, 6f); lineTo(10f, 10f); lineTo(20f, 10f)
            lineTo(20f, 14f); lineTo(10f, 14f); lineTo(10f, 18f); close()
        }
        SkinIconStyle.COUNTRY -> dsPath(style, tint) {
            moveTo(11f, 5.5f); quadraticTo(6f, 7f, 4f, 12f); quadraticTo(8f, 13f, 10.5f, 18f)
            moveTo(4f, 12f); quadraticTo(13f, 11f, 20f, 13f)
            moveTo(14.5f, 12f); quadraticTo(17f, 8.5f, 20f, 9.5f); quadraticTo(18f, 12.5f, 14.5f, 12f)
        }
        SkinIconStyle.VICTORIAN -> dsPath(style, tint) {
            moveTo(10.5f, 5f); cubicTo(12f, 9f, 5f, 8.5f, 3.5f, 12f)
            cubicTo(5f, 15.5f, 12f, 15f, 10.5f, 19f)
            moveTo(4f, 12f); lineTo(14f, 12f); cubicTo(16f, 12f, 16f, 9f, 18f, 9f)
            cubicTo(22f, 9f, 20f, 15f, 17.5f, 14f)
        }
    }
}

/** Short left chevron: full arm geometry changes, not just endpoints. */
private fun DrawScope.directionChevron(style: SkinIconStyle, tint: Color) {
    when (style) {
        SkinIconStyle.SWEET -> dsPath(style, tint) {
            moveTo(16f, 5.5f); cubicTo(15f, 9.5f, 12f, 10.5f, 8f, 12f)
            cubicTo(12f, 13.5f, 15f, 14.5f, 16f, 18.5f)
        }
        SkinIconStyle.GOTHIC -> dsFill(tint) { moveTo(16f, 4.5f); lineTo(7f, 12f); lineTo(16f, 19.5f); lineTo(13.5f, 12f); close() }
        SkinIconStyle.CHINESE -> dsFill(tint) {
            moveTo(16.5f, 5f); quadraticTo(13f, 9f, 8.5f, 12f); quadraticTo(11f, 15f, 16f, 19f)
            lineTo(12f, 17.6f); lineTo(6.5f, 12.5f); lineTo(14f, 5.5f); close()
        }
        SkinIconStyle.CLASSIC -> dsPath(style, tint) {
            moveTo(16.5f, 6f); lineTo(13.5f, 6f); lineTo(7.5f, 12f); lineTo(13.5f, 18f); lineTo(16.5f, 18f)
        }
        SkinIconStyle.NAVY -> dsPath(style, tint) {
            moveTo(16f, 5f); lineTo(8f, 12f); lineTo(16f, 19f); lineTo(16f, 14.5f)
            lineTo(13f, 12f); lineTo(16f, 9.5f); close()
        }
        SkinIconStyle.COUNTRY -> dsPath(style, tint) {
            // A single bent-twig stroke keeps the short control readable at 18dp.
            moveTo(15.5f, 5.5f); quadraticTo(10f, 7.2f, 8f, 12f)
            quadraticTo(12f, 13.8f, 15.5f, 18.5f)
        }
        SkinIconStyle.VICTORIAN -> dsPath(style, tint) {
            moveTo(16f, 5f); cubicTo(18f, 10f, 9f, 8.5f, 7.5f, 12f)
            cubicTo(9f, 15.5f, 18f, 14f, 16f, 19f)
        }
    }
}

private fun DrawScope.directionDrop(style: SkinIconStyle, tint: Color) = dsFill(tint) {
    when (style) {
        SkinIconStyle.SWEET -> { moveTo(5f, 8.5f); quadraticTo(12f, 6.5f, 19f, 8.5f); quadraticTo(16f, 15f, 12f, 17f); quadraticTo(8f, 15f, 5f, 8.5f) }
        SkinIconStyle.GOTHIC -> { moveTo(5f, 7f); lineTo(12f, 9f); lineTo(19f, 7f); lineTo(12f, 18f) }
        SkinIconStyle.CHINESE -> { moveTo(4.5f, 7f); quadraticTo(12f, 10f, 19.5f, 7f); lineTo(12f, 18f); lineTo(10.3f, 13f) }
        SkinIconStyle.CLASSIC -> { moveTo(6f, 8f); lineTo(18f, 8f); lineTo(12f, 16f) }
        SkinIconStyle.NAVY -> { moveTo(5f, 7f); lineTo(19f, 7f); lineTo(19f, 10f); lineTo(12f, 17f); lineTo(5f, 10f) }
        SkinIconStyle.COUNTRY -> { moveTo(5f, 8f); quadraticTo(14f, 6f, 19f, 9f); quadraticTo(15f, 16f, 11f, 17f); quadraticTo(10f, 12f, 5f, 8f) }
        SkinIconStyle.VICTORIAN -> { moveTo(5f, 7.5f); quadraticTo(12f, 12f, 19f, 7.5f); cubicTo(18f, 13f, 13f, 13f, 12f, 18f); cubicTo(11f, 13f, 6f, 13f, 5f, 7.5f) }
    }
    close()
}

/** Narrow companion arrow preserves full stroke width instead of squeezing the long arrow. */
private fun DrawScope.directionSwapArrow(style: SkinIconStyle, tint: Color) {
    when (style) {
        SkinIconStyle.SWEET -> dsPath(style, tint) {
            moveTo(4f, 9f); quadraticTo(7f, 7f, 8f, 4f); quadraticTo(9f, 7f, 12f, 9f)
            moveTo(8f, 4.5f); cubicTo(6f, 10f, 10f, 14f, 8f, 20f)
        }
        SkinIconStyle.GOTHIC -> {
            dsFill(tint) { moveTo(8f, 3f); lineTo(12f, 10f); lineTo(8f, 8f); lineTo(4f, 10f); close() }
            dsPath(style, tint) { moveTo(8f, 8f); lineTo(8f, 20f); moveTo(6.5f, 19f); lineTo(9.5f, 19f) }
        }
        SkinIconStyle.CHINESE -> {
            dsFill(tint) { moveTo(8f, 3f); lineTo(12f, 10f); lineTo(8f, 6.5f); lineTo(4f, 9f); lineTo(4.8f, 6.5f); close() }
            dsFill(tint) { moveTo(7.5f, 5f); lineTo(9.2f, 6f); lineTo(8.5f, 20f); lineTo(6.5f, 18f); close() }
        }
        SkinIconStyle.CLASSIC -> dsPath(style, tint) {
            moveTo(4f, 8f); lineTo(8f, 4f); lineTo(12f, 8f); moveTo(8f, 4f); lineTo(8f, 20f)
            moveTo(5.5f, 20f); lineTo(10.5f, 20f)
        }
        SkinIconStyle.NAVY -> dsFill(tint) {
            // A solid signal head with a 1.7-unit stem, rather than two cramped outline walls.
            moveTo(8f, 3.5f); lineTo(11.4f, 8.8f); lineTo(8.85f, 8.8f)
            lineTo(8.85f, 20f); lineTo(7.15f, 20f); lineTo(7.15f, 8.8f)
            lineTo(4.6f, 8.8f); close()
        }
        SkinIconStyle.COUNTRY -> dsPath(style, tint) {
            moveTo(4f, 9f); quadraticTo(4.5f, 5f, 8f, 4f); quadraticTo(9f, 7f, 12f, 8.5f)
            moveTo(8f, 4f); quadraticTo(6.5f, 11f, 8f, 20f)
            moveTo(7.5f, 14f); quadraticTo(3f, 14f, 4f, 18f); quadraticTo(7f, 17f, 7.5f, 14f)
        }
        SkinIconStyle.VICTORIAN -> dsPath(style, tint) {
            moveTo(4f, 8.7f); cubicTo(6.5f, 8.7f, 5.8f, 5.8f, 7f, 3.8f)
            cubicTo(8.2f, 5.8f, 7.5f, 8.7f, 10.5f, 8.7f)
            // The narrow silver stem ends in one outward sweep, leaving the center open.
            moveTo(7f, 4.5f); lineTo(7f, 17f); quadraticTo(7f, 20f, 5f, 20f)
        }
    }
}

private fun DrawScope.directionOpen(style: SkinIconStyle, tint: Color) {
    dsPath(style, tint) {
        when (style) {
            SkinIconStyle.SWEET -> { moveTo(10f, 4f); cubicTo(4f, 3f, 3.5f, 6f, 3.5f, 12f); cubicTo(3.5f, 19f, 5f, 20.5f, 12f, 20.5f); cubicTo(18f, 20.5f, 21f, 20f, 20f, 14f) }
            SkinIconStyle.GOTHIC -> { moveTo(10f, 4f); lineTo(6f, 4f); lineTo(3.5f, 7f); lineTo(3.5f, 18f); lineTo(6f, 20.5f); lineTo(17f, 20.5f); lineTo(20f, 17.5f); lineTo(20f, 14f) }
            SkinIconStyle.CHINESE -> { moveTo(10f, 4f); lineTo(5f, 4f); lineTo(5f, 19f); lineTo(20f, 19f); lineTo(20f, 14f); moveTo(3f, 7f); lineTo(5f, 7f); moveTo(17f, 19f); lineTo(17f, 21f) }
            SkinIconStyle.CLASSIC -> { moveTo(10f, 4f); lineTo(4f, 4f); lineTo(4f, 20f); lineTo(20f, 20f); lineTo(20f, 14f) }
            SkinIconStyle.NAVY -> { moveTo(10f, 3.8f); cubicTo(0f, 5f, 1f, 20f, 11f, 20f); cubicTo(17f, 20f, 20f, 19f, 20f, 14f) }
            SkinIconStyle.COUNTRY -> { moveTo(10f, 4.5f); lineTo(4f, 5.5f); lineTo(5f, 19.5f); quadraticTo(12f, 21f, 19.5f, 19f); lineTo(20f, 14f) }
            SkinIconStyle.VICTORIAN -> { moveTo(10f, 4f); cubicTo(3f, 3f, 3f, 9f, 4f, 14f); quadraticTo(5f, 20f, 12f, 21f); quadraticTo(19f, 20f, 20f, 14f) }
        }
    }
    // A dedicated diagonal uses the full logical stroke and stays clear of both open frame ends.
    // Family identity remains in the shaft/head construction, without shrinking a long-arrow curl.
    when (style) {
        SkinIconStyle.SWEET -> dsPath(style, tint) {
            moveTo(14.3f, 4.6f); cubicTo(16.2f, 3.8f, 18.5f, 4f, 20f, 4f)
            quadraticTo(20.5f, 6.5f, 19.8f, 9.5f)
            moveTo(10.5f, 13.5f); cubicTo(14f, 12f, 16f, 7f, 20f, 4f)
        }
        SkinIconStyle.GOTHIC -> dsPath(style, tint) {
            moveTo(14.5f, 5.5f); lineTo(20.5f, 3.5f); lineTo(18.5f, 9.5f)
            moveTo(10.5f, 13.5f); lineTo(20.5f, 3.5f)
        }
        SkinIconStyle.CHINESE -> dsFill(tint) {
            moveTo(10.5f, 13.8f); quadraticTo(14.5f, 7.6f, 20.5f, 3.5f)
            lineTo(19.7f, 9.5f); lineTo(18.4f, 8.7f); lineTo(18.8f, 6.5f)
            quadraticTo(14.6f, 10.8f, 11.5f, 14.2f); close()
            moveTo(14.2f, 4.5f); lineTo(20.5f, 3.5f); lineTo(18.8f, 6.5f)
            lineTo(14.2f, 5.9f); close()
        }
        SkinIconStyle.CLASSIC -> dsPath(style, tint) {
            moveTo(14f, 4f); lineTo(20f, 4f); lineTo(20f, 10f)
            moveTo(10f, 14f); lineTo(20f, 4f)
        }
        SkinIconStyle.NAVY -> dsFill(tint) {
            moveTo(10.1f, 12.9f); lineTo(17.9f, 5.1f); lineTo(14.1f, 5.1f)
            lineTo(14.1f, 3.4f); lineTo(20.6f, 3.4f); lineTo(20.6f, 9.9f)
            lineTo(18.9f, 9.9f); lineTo(18.9f, 6.1f); lineTo(11.3f, 14.1f); close()
        }
        SkinIconStyle.COUNTRY -> dsPath(style, tint) {
            moveTo(14.5f, 5f); quadraticTo(17f, 3.6f, 20.5f, 3.9f)
            quadraticTo(20.2f, 7f, 19.5f, 9.8f)
            moveTo(10.3f, 13.8f); quadraticTo(13.2f, 8f, 20.5f, 3.9f)
        }
        SkinIconStyle.VICTORIAN -> dsPath(style, tint) {
            moveTo(14.7f, 4.5f); cubicTo(17f, 5f, 17.5f, 2.8f, 20.5f, 3.5f)
            cubicTo(21.2f, 6.4f, 18.7f, 7.1f, 19.5f, 9.8f)
            moveTo(10.5f, 13.8f); cubicTo(10.8f, 9.8f, 18.2f, 7.8f, 20.5f, 3.5f)
        }
    }
}

private fun DrawScope.statusEye(style: SkinIconStyle, tint: Color) {
    dsPath(style, tint) {
        when (style) {
            SkinIconStyle.SWEET -> { moveTo(3f, 12f); cubicTo(5f, 4f, 19f, 4f, 21f, 12f); cubicTo(17f, 20f, 7f, 20f, 3f, 12f); close() }
            SkinIconStyle.GOTHIC -> { moveTo(3f, 12f); lineTo(8f, 7f); lineTo(12f, 5f); lineTo(16f, 7f); lineTo(21f, 12f); lineTo(16f, 16f); lineTo(12f, 19f); lineTo(8f, 16f); close() }
            SkinIconStyle.CHINESE -> { moveTo(3f, 13f); quadraticTo(10f, 5f, 17f, 8f); lineTo(21f, 6f); lineTo(20f, 12f); quadraticTo(12f, 20f, 3f, 13f); close() }
            SkinIconStyle.CLASSIC -> { moveTo(3f, 12f); quadraticTo(12f, 1.5f, 21f, 12f); quadraticTo(12f, 22.5f, 3f, 12f); close() }
            SkinIconStyle.NAVY -> { moveTo(3f, 10f); lineTo(8f, 6f); lineTo(16f, 6f); lineTo(21f, 10f); lineTo(21f, 14f); lineTo(16f, 18f); lineTo(8f, 18f); lineTo(3f, 14f); close() }
            SkinIconStyle.COUNTRY -> { moveTo(3f, 13.5f); cubicTo(8f, 4f, 16f, 3f, 21f, 10.5f); cubicTo(17f, 20f, 8f, 20f, 3f, 13.5f); close() }
            SkinIconStyle.VICTORIAN -> { moveTo(3f, 12f); cubicTo(7f, 12f, 6f, 5f, 12f, 5f); cubicTo(18f, 5f, 17f, 12f, 21f, 12f); cubicTo(17f, 12f, 18f, 19f, 12f, 19f); cubicTo(6f, 19f, 7f, 12f, 3f, 12f); close() }
        }
    }
    when (style) {
        SkinIconStyle.GOTHIC -> dsPath(style, tint) { moveTo(12f, 8.5f); lineTo(14.5f, 12f); lineTo(12f, 15.5f); lineTo(9.5f, 12f); close() }
        SkinIconStyle.CHINESE -> drawOval(tint, Offset(10f, 9f), Size(4f, 6f))
        SkinIconStyle.VICTORIAN -> drawOval(tint, Offset(9.7f, 8.5f), Size(4.6f, 7f), style = style.stroke)
        SkinIconStyle.NAVY -> drawCircle(tint, 3.4f, Offset(12f, 12f), style = style.stroke)
        SkinIconStyle.COUNTRY -> dsPath(style, tint) { moveTo(12.5f, 8.5f); cubicTo(17f, 11f, 13f, 17.5f, 10f, 14f); cubicTo(8f, 12f, 9f, 9f, 12.5f, 8.5f); close() }
        else -> drawCircle(tint, 2.8f, Offset(12f, 12f), style = style.stroke)
    }
}

/** Frames retain status conventions: circular approval, stop shapes, information cards and query bubbles. */
private fun DrawScope.statusFrame(key: IconKey, style: SkinIconStyle, tint: Color) {
    dsPath(style, tint) {
        when (style) {
            SkinIconStyle.SWEET -> when (key) {
                IconKey.CheckCircle -> { moveTo(7f, 5f); cubicTo(7f, 2.5f, 17f, 2.5f, 17f, 5f); cubicTo(21.5f, 5f, 22f, 17f, 18f, 17f); cubicTo(18f, 22f, 6f, 22f, 6f, 17f); cubicTo(2f, 17f, 2.5f, 5f, 7f, 5f); close() }
                IconKey.Error -> { moveTo(8f, 3.5f); lineTo(16f, 3.5f); quadraticTo(21f, 3.5f, 21f, 9f); lineTo(21f, 15f); quadraticTo(21f, 20.5f, 16f, 20.5f); lineTo(8f, 20.5f); quadraticTo(3f, 20.5f, 3f, 15f); lineTo(3f, 9f); quadraticTo(3f, 3.5f, 8f, 3.5f); close() }
                IconKey.Info -> { moveTo(12f, 3f); cubicTo(17f, 3f, 20f, 6f, 20f, 12f); cubicTo(20f, 18f, 17f, 21f, 12f, 21f); cubicTo(7f, 21f, 4f, 18f, 4f, 12f); cubicTo(4f, 6f, 7f, 3f, 12f, 3f); close() }
                else -> { moveTo(6f, 18f); cubicTo(-1f, 12f, 4f, 3.5f, 12f, 3.5f); cubicTo(24f, 3.5f, 24f, 19f, 12f, 19f); lineTo(6f, 21f); close() }
            }
            SkinIconStyle.GOTHIC -> when (key) {
                IconKey.CheckCircle -> { moveTo(12f, 3f); lineTo(18f, 5.5f); lineTo(21f, 12f); lineTo(18f, 18.5f); lineTo(12f, 21f); lineTo(6f, 18.5f); lineTo(3f, 12f); lineTo(6f, 5.5f); close() }
                IconKey.Error -> { moveTo(8f, 3.5f); lineTo(16f, 3.5f); lineTo(20.5f, 8f); lineTo(20.5f, 16f); lineTo(16f, 20.5f); lineTo(8f, 20.5f); lineTo(3.5f, 16f); lineTo(3.5f, 8f); close() }
                IconKey.Info -> { moveTo(4.5f, 20.5f); lineTo(4.5f, 10f); quadraticTo(4.5f, 6f, 12f, 3f); quadraticTo(19.5f, 6f, 19.5f, 10f); lineTo(19.5f, 20.5f); close() }
                else -> { moveTo(4f, 8f); lineTo(8f, 3.5f); lineTo(16f, 3.5f); lineTo(20f, 8f); lineTo(20f, 16f); lineTo(16f, 19f); lineTo(8f, 19f); lineTo(4f, 21f); close() }
            }
            SkinIconStyle.CHINESE -> when (key) {
                IconKey.CheckCircle -> { moveTo(8f, 3.5f); quadraticTo(4f, 3.5f, 4f, 8f); lineTo(4f, 17f); lineTo(7f, 20f); lineTo(17f, 20f); quadraticTo(20f, 20f, 20f, 16f); lineTo(20f, 7f); lineTo(17f, 3.5f); close() }
                IconKey.Error -> { moveTo(4f, 4f); lineTo(20f, 4f); lineTo(20f, 20f); lineTo(4f, 20f); close() }
                IconKey.Info -> { moveTo(4f, 5f); lineTo(18f, 5f); lineTo(18f, 19f); lineTo(4f, 19f); close(); moveTo(4f, 3f); lineTo(4f, 21f); moveTo(18f, 5f); lineTo(21f, 5f); lineTo(21f, 9f) }
                else -> { moveTo(4f, 4f); lineTo(20f, 4f); lineTo(20f, 18f); lineTo(10f, 18f); lineTo(6f, 21f); lineTo(6f, 18f); lineTo(4f, 18f); close() }
            }
            SkinIconStyle.CLASSIC -> when (key) {
                IconKey.CheckCircle -> { moveTo(12f, 3f); cubicTo(24f, 3f, 24f, 21f, 12f, 21f); cubicTo(0f, 21f, 0f, 3f, 12f, 3f); close() }
                IconKey.Error -> { moveTo(12f, 3f); lineTo(21f, 12f); lineTo(12f, 21f); lineTo(3f, 12f); close() }
                IconKey.Info -> { moveTo(5f, 4f); lineTo(19f, 4f); lineTo(19f, 20f); lineTo(5f, 20f); close() }
                else -> { moveTo(12f, 3f); cubicTo(22.5f, 3f, 22.5f, 21f, 12f, 21f); cubicTo(1.5f, 21f, 1.5f, 3f, 12f, 3f); close() }
            }
            SkinIconStyle.NAVY -> when (key) {
                IconKey.CheckCircle -> { moveTo(8f, 3.5f); lineTo(16f, 3.5f); cubicTo(23f, 3.5f, 23f, 20.5f, 16f, 20.5f); lineTo(8f, 20.5f); cubicTo(1f, 20.5f, 1f, 3.5f, 8f, 3.5f); close() }
                IconKey.Error -> { moveTo(7f, 3.5f); lineTo(17f, 3.5f); lineTo(21f, 12f); lineTo(17f, 20.5f); lineTo(7f, 20.5f); lineTo(3f, 12f); close() }
                IconKey.Info -> { moveTo(12f, 3f); cubicTo(24f, 3f, 24f, 21f, 12f, 21f); cubicTo(0f, 21f, 0f, 3f, 12f, 3f); close(); moveTo(3f, 12f); lineTo(5f, 12f); moveTo(19f, 12f); lineTo(21f, 12f) }
                else -> { moveTo(7f, 4f); lineTo(17f, 4f); quadraticTo(21f, 4f, 21f, 8f); lineTo(21f, 16f); quadraticTo(21f, 20f, 17f, 20f); lineTo(7f, 20f); quadraticTo(3f, 20f, 3f, 16f); lineTo(3f, 8f); quadraticTo(3f, 4f, 7f, 4f); close() }
            }
            SkinIconStyle.COUNTRY -> when (key) {
                IconKey.CheckCircle -> { moveTo(4f, 16f); cubicTo(1f, 6f, 11f, 2f, 20f, 4f); cubicTo(23f, 14f, 16f, 23f, 8f, 20f); quadraticTo(5f, 19f, 4f, 16f); close() }
                IconKey.Error -> { moveTo(5f, 5f); quadraticTo(12f, 2f, 19f, 5f); lineTo(21f, 17f); quadraticTo(12f, 22f, 3f, 18f); close() }
                IconKey.Info -> { moveTo(6f, 4f); quadraticTo(12f, 2f, 18f, 4f); lineTo(20f, 20f); quadraticTo(12f, 18f, 4f, 20f); close() }
                else -> { moveTo(5f, 17f); cubicTo(-1f, 8f, 9f, 1f, 17f, 5f); cubicTo(25f, 8f, 20f, 20f, 11f, 19f); lineTo(5f, 21f); close() }
            }
            SkinIconStyle.VICTORIAN -> when (key) {
                IconKey.CheckCircle -> { moveTo(12f, 3f); cubicTo(18f, 3f, 21f, 8f, 20f, 13f); cubicTo(19f, 18f, 16f, 19f, 12f, 21f); cubicTo(8f, 19f, 5f, 18f, 4f, 13f); cubicTo(3f, 8f, 6f, 3f, 12f, 3f); close() }
                IconKey.Error -> { moveTo(12f, 3f); quadraticTo(16f, 6f, 20f, 7f); lineTo(20f, 17f); quadraticTo(16f, 18f, 12f, 21f); quadraticTo(8f, 18f, 4f, 17f); lineTo(4f, 7f); quadraticTo(8f, 6f, 12f, 3f); close() }
                IconKey.Info -> { moveTo(5f, 20f); lineTo(5f, 9f); cubicTo(5f, 1f, 19f, 1f, 19f, 9f); lineTo(19f, 20f); quadraticTo(12f, 18f, 5f, 20f); close() }
                else -> { moveTo(12f, 3f); cubicTo(19f, 3f, 20f, 8f, 19f, 13f); quadraticTo(18f, 19f, 12f, 21f); quadraticTo(6f, 19f, 5f, 13f); cubicTo(4f, 8f, 5f, 3f, 12f, 3f); close() }
            }
        }
    }
}

private fun DrawScope.statusPunctuation(key: IconKey, style: SkinIconStyle, tint: Color) {
    if (key == IconKey.CheckCircle) {
        dsPath(style, tint) {
            when (style) {
                SkinIconStyle.SWEET -> { moveTo(7f, 12f); quadraticTo(9f, 13f, 10.5f, 16f); quadraticTo(13f, 10f, 17f, 8f) }
                SkinIconStyle.GOTHIC -> { moveTo(7f, 11.5f); lineTo(10.5f, 15.5f); lineTo(17f, 8f) }
                SkinIconStyle.CHINESE -> { moveTo(7f, 12f); lineTo(10f, 15.5f); quadraticTo(12f, 12f, 17f, 8f) }
                SkinIconStyle.CLASSIC -> { moveTo(7f, 12f); lineTo(10.5f, 15.5f); lineTo(17f, 9f) }
                SkinIconStyle.NAVY -> { moveTo(7f, 11f); lineTo(7f, 13f); lineTo(10f, 16f); lineTo(17f, 8f) }
                SkinIconStyle.COUNTRY -> { moveTo(7f, 12f); quadraticTo(8f, 15f, 10f, 16f); cubicTo(11f, 12f, 14f, 11f, 17f, 8f) }
                SkinIconStyle.VICTORIAN -> { moveTo(7f, 11.5f); cubicTo(9f, 10f, 10f, 15f, 10.5f, 16f); quadraticTo(13f, 11f, 17f, 8f) }
            }
        }
        return
    }
    if (key == IconKey.Error) {
        statusExclamation(style, tint, 7f, 13f, 16.7f)
        return
    }
    if (key == IconKey.Info) {
        statusDot(style, tint, 12f, 7.5f)
        dsPath(style, tint) {
            when (style) {
                SkinIconStyle.SWEET -> { moveTo(10.5f, 11f); quadraticTo(12.5f, 10f, 12f, 13f); lineTo(12f, 16f); quadraticTo(12f, 18f, 14f, 16f) }
                SkinIconStyle.GOTHIC -> { moveTo(10f, 10.5f); lineTo(12f, 10.5f); lineTo(12f, 17f); lineTo(14f, 17f) }
                SkinIconStyle.CHINESE -> { moveTo(10f, 11f); lineTo(13f, 10.5f); lineTo(11.5f, 17f); lineTo(14.5f, 16f) }
                SkinIconStyle.CLASSIC -> { moveTo(10f, 11f); lineTo(12f, 11f); lineTo(12f, 17f); moveTo(9.5f, 17f); lineTo(14.5f, 17f) }
                SkinIconStyle.NAVY -> { moveTo(10f, 11f); lineTo(14f, 11f); moveTo(12f, 11f); lineTo(12f, 17f); moveTo(10f, 17f); lineTo(14f, 17f) }
                SkinIconStyle.COUNTRY -> { moveTo(10.5f, 11f); quadraticTo(14f, 10f, 12f, 14f); quadraticTo(10f, 18f, 14f, 17f) }
                SkinIconStyle.VICTORIAN -> { moveTo(9.5f, 11.5f); quadraticTo(14f, 9f, 12f, 14f); cubicTo(10f, 19f, 14f, 17f, 14.5f, 15.5f) }
            }
        }
        return
    }
    dsPath(style, tint) {
        when (style) {
            SkinIconStyle.SWEET -> { moveTo(9f, 8.5f); cubicTo(8f, 5f, 16f, 5f, 15f, 9f); quadraticTo(15f, 10.5f, 12f, 12f); lineTo(12f, 13.5f) }
            SkinIconStyle.GOTHIC -> { moveTo(9f, 8.5f); lineTo(9f, 7f); lineTo(12f, 5.8f); lineTo(15f, 7f); lineTo(15f, 10f); lineTo(12f, 12f); lineTo(12f, 13.5f) }
            SkinIconStyle.CHINESE -> { moveTo(8.5f, 8f); lineTo(9f, 6.5f); lineTo(15f, 6.5f); lineTo(15f, 10f); lineTo(12f, 12f); lineTo(11.5f, 13.5f) }
            SkinIconStyle.CLASSIC -> { moveTo(9f, 8f); cubicTo(9f, 4.5f, 15.5f, 5f, 15f, 9f); quadraticTo(15f, 11f, 12f, 12f); lineTo(12f, 13.5f) }
            SkinIconStyle.NAVY -> { moveTo(9f, 8.5f); lineTo(9f, 7.5f); quadraticTo(9f, 6f, 12f, 6f); quadraticTo(15f, 6f, 15f, 8f); lineTo(15f, 9.5f); lineTo(12f, 12f); lineTo(12f, 13.5f) }
            SkinIconStyle.COUNTRY -> { moveTo(8.5f, 8.5f); cubicTo(9f, 4.5f, 16f, 6f, 14.5f, 10f); quadraticTo(11f, 11f, 11.5f, 13.5f) }
            SkinIconStyle.VICTORIAN -> { moveTo(8.8f, 8.5f); cubicTo(7.5f, 5f, 16f, 4.5f, 15f, 9f); cubicTo(14.5f, 11f, 11f, 10.5f, 12f, 13.5f) }
        }
    }
    statusDot(style, tint, 12f, 17f)
}

private fun DrawScope.statusDot(style: SkinIconStyle, tint: Color, x: Float, y: Float) {
    when (style) {
        SkinIconStyle.GOTHIC -> dsFill(tint) { moveTo(x, y - 1.25f); lineTo(x + 1.2f, y); lineTo(x, y + 1.25f); lineTo(x - 1.2f, y); close() }
        SkinIconStyle.CHINESE, SkinIconStyle.NAVY -> drawRect(tint, Offset(x - 1f, y - 1f), Size(2f, 2f))
        SkinIconStyle.VICTORIAN -> drawOval(tint, Offset(x - .9f, y - 1.2f), Size(1.8f, 2.4f))
        else -> drawCircle(tint, 1f, Offset(x, y))
    }
}

private fun DrawScope.statusExclamation(style: SkinIconStyle, tint: Color, top: Float, bottom: Float, dot: Float) {
    when (style) {
        SkinIconStyle.CHINESE -> dsFill(tint) { moveTo(10.8f, top); lineTo(13.5f, top - .3f); lineTo(12.3f, bottom); lineTo(11.3f, bottom); close() }
        SkinIconStyle.GOTHIC -> dsFill(tint) { moveTo(10.5f, top); lineTo(13.5f, top); lineTo(12f, bottom + .5f); close() }
        SkinIconStyle.VICTORIAN -> dsFill(tint) { moveTo(12f, top - .5f); cubicTo(15f, top, 12.5f, bottom, 12f, bottom); cubicTo(11.5f, bottom, 9f, top, 12f, top - .5f); close() }
        else -> dsPath(style, tint) { moveTo(12f, top); if (style == SkinIconStyle.COUNTRY) quadraticTo(13f, (top + bottom) / 2, 12f, bottom) else lineTo(12f, bottom) }
    }
    statusDot(style, tint, 12f, dot)
}

private fun DrawScope.statusWarning(style: SkinIconStyle, tint: Color) {
    dsPath(style, tint) {
        when (style) {
            SkinIconStyle.SWEET -> { moveTo(10f, 5f); quadraticTo(12f, 1f, 14f, 5f); lineTo(20f, 17f); quadraticTo(22f, 20.5f, 18f, 20.5f); lineTo(6f, 20.5f); quadraticTo(2f, 20.5f, 4f, 17f); close() }
            SkinIconStyle.GOTHIC -> { moveTo(12f, 3f); lineTo(21f, 18f); lineTo(19f, 21f); lineTo(5f, 21f); lineTo(3f, 18f); close() }
            SkinIconStyle.CHINESE -> { moveTo(12f, 3.5f); lineTo(20f, 18.5f); lineTo(21f, 20.5f); quadraticTo(12f, 19f, 3f, 20.5f); lineTo(5f, 17f); close() }
            SkinIconStyle.CLASSIC -> { moveTo(12f, 3.5f); lineTo(21f, 20f); lineTo(3f, 20f); close() }
            SkinIconStyle.NAVY -> { moveTo(11f, 3.5f); lineTo(13f, 3.5f); lineTo(21f, 19f); lineTo(3f, 19f); close(); moveTo(6f, 21f); lineTo(18f, 21f) }
            SkinIconStyle.COUNTRY -> { moveTo(11f, 3.5f); quadraticTo(16f, 10f, 20f, 20f); quadraticTo(12f, 18f, 3.5f, 20f); quadraticTo(8f, 7f, 11f, 3.5f); close() }
            SkinIconStyle.VICTORIAN -> { moveTo(12f, 3f); cubicTo(14f, 9f, 18f, 11f, 20.5f, 20f); quadraticTo(12f, 18.5f, 3.5f, 20f); cubicTo(6f, 11f, 10f, 9f, 12f, 3f); close() }
        }
    }
    statusExclamation(style, tint, 9f, 13f, 16.5f)
}
