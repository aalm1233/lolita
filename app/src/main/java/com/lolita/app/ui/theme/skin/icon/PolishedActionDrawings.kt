package com.lolita.app.ui.theme.skin.icon

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform

/** Action silhouettes: soft cloth, cut iron, brushwork, stationery, fittings, wood and silver. */
internal fun DrawScope.drawPolishedActionIcon(key: IconKey, style: SkinIconStyle, tint: Color) {
    when (key) {
        IconKey.Add, IconKey.Close -> {
            val a = actionRibbon(style, 4.5f, 12f, 19.5f, 12f)
            val b = actionRibbon(style, 12f, 4.5f, 12f, 19.5f)
            withTransform({ if (key == IconKey.Close) rotate(45f, Offset(12f, 12f)) }) {
                drawPath(Path.combine(PathOperation.Union, a, b), tint)
            }
        }
        IconKey.Save -> actionSave(style, tint)
        IconKey.Delete -> actionDelete(style, tint)
        IconKey.Edit -> actionEdit(style, tint)
        IconKey.Search -> actionSearch(style, tint)
        IconKey.Sort, IconKey.FilterList -> actionBars(style, tint, key == IconKey.FilterList)
        IconKey.Share -> actionShare(style, tint)
        IconKey.MoreVert -> for (y in listOf(5.5f, 12f, 18.5f)) {
            drawPath(actionTile(style, 10.25f, y - 1.75f, 3.5f, 3.5f), tint)
        }
        IconKey.ContentCopy -> actionCopy(style, tint)
        IconKey.Refresh -> actionRefresh(style, tint)
        IconKey.ViewAgenda, IconKey.GridView, IconKey.Apps -> actionLayout(style, tint, key)
        IconKey.Gallery -> actionGallery(style, tint)
        else -> error("Not an action icon: $key")
    }
}

private fun DrawScope.actionPath(style: SkinIconStyle, tint: Color, block: Path.() -> Unit) =
    drawPath(Path().apply(block), tint, style = style.stroke)

/** A broad, filled pen mark. Shape varies along its entire length, not just at endpoints. */
private fun actionRibbon(style: SkinIconStyle, x1: Float, y1: Float, x2: Float, y2: Float): Path {
    val dx = x2 - x1
    val dy = y2 - y1
    val length = kotlin.math.sqrt(dx * dx + dy * dy)
    val ux = dx / length
    val uy = dy / length
    fun Path.point(u: Float, v: Float, start: Boolean = false) {
        val x = x1 + ux * u - uy * v
        val y = y1 + uy * u + ux * v
        if (start) moveTo(x, y) else lineTo(x, y)
    }
    fun Path.curve(u: Float, v: Float, u2: Float, v2: Float) = quadraticTo(
        x1 + ux * u - uy * v, y1 + uy * u + ux * v,
        x1 + ux * u2 - uy * v2, y1 + uy * u2 + ux * v2
    )
    return Path().apply {
        when (style) {
            SkinIconStyle.SWEET -> {
                point(1f, -1.15f, true); curve(length * .5f, -1.8f, length - 1f, -1.15f)
                curve(length + 1f, 0f, length - 1f, 1.15f)
                curve(length * .5f, 1.8f, 1f, 1.15f); curve(-1f, 0f, 1f, -1.15f)
            }
            SkinIconStyle.GOTHIC -> {
                point(0f, 0f, true); point(2f, -1.3f); point(length - 2f, -.8f)
                point(length, -1.7f); point(length - .6f, 1.3f); point(2f, .8f)
            }
            SkinIconStyle.CHINESE -> {
                point(0f, -.2f, true); point(1f, -1.7f); curve(length * .45f, -.3f, length, -1.2f)
                point(length - 1.2f, 1.3f); curve(length * .5f, 1.9f, 0f, -.2f)
            }
            SkinIconStyle.CLASSIC -> {
                point(0f, -1.3f, true); point(1.8f, -1.3f); point(1.8f, -.75f)
                point(length - 1.8f, -.75f); point(length - 1.8f, -1.3f); point(length, -1.3f)
                point(length, 1.3f); point(length - 1.8f, 1.3f); point(length - 1.8f, .75f)
                point(1.8f, .75f); point(1.8f, 1.3f); point(0f, 1.3f)
            }
            SkinIconStyle.NAVY -> {
                point(0f, -.9f, true); point(length - 2.5f, -.9f); point(length - 2.5f, -1.5f)
                point(length, -1.5f); point(length, 1.5f); point(length - 2.5f, 1.5f)
                point(length - 2.5f, .9f); point(0f, .9f)
            }
            SkinIconStyle.COUNTRY -> {
                point(0f, -.8f, true); curve(length * .35f, -2.2f, length, -.6f)
                point(length - .3f, 1.4f); curve(length * .55f, .2f, .6f, 1.5f)
            }
            SkinIconStyle.VICTORIAN -> {
                point(0f, 0f, true); curve(1f, -2f, 3f, -1f)
                curve(length * .5f, -.1f, length - 3f, -1f); curve(length - 1f, -2f, length, 0f)
                curve(length - 1f, 2f, length - 3f, 1f)
                curve(length * .5f, .1f, 3f, 1f); curve(1f, 2f, 0f, 0f)
            }
        }
        close()
    }
}

private fun DrawScope.actionSave(style: SkinIconStyle, tint: Color) {
    // All seven remain a check, with different sweep, knee and pen pressure.
    if (style == SkinIconStyle.SWEET || style == SkinIconStyle.COUNTRY || style == SkinIconStyle.VICTORIAN) {
        actionPath(style, tint) {
            when (style) {
                SkinIconStyle.SWEET -> { moveTo(4.5f, 11.5f); quadraticTo(7f, 12f, 10f, 17.5f); quadraticTo(15f, 9f, 20f, 6.5f) }
                SkinIconStyle.COUNTRY -> { moveTo(4.5f, 13f); quadraticTo(7f, 12f, 9f, 17f); cubicTo(12f, 12f, 15f, 8f, 20f, 5.5f) }
                else -> { moveTo(4f, 12f); cubicTo(7f, 9f, 8f, 15f, 9.5f, 18f); cubicTo(12f, 12f, 16f, 8f, 20f, 6f) }
            }
        }
    } else {
        val kneeX = if (style == SkinIconStyle.NAVY) 9f else 10f
        val kneeY = if (style == SkinIconStyle.CHINESE) 18f else 17f
        drawPath(Path.combine(PathOperation.Union,
            actionRibbon(style, 4.5f, 12f, kneeX, kneeY),
            actionRibbon(style, kneeX, kneeY, 20f, 6f)), tint)
    }
}

private fun DrawScope.actionDelete(style: SkinIconStyle, tint: Color) {
    actionPath(style, tint) {
        when (style) {
            SkinIconStyle.SWEET -> {
                moveTo(6f, 9f); lineTo(6.8f, 17f); cubicTo(7f, 22f, 17f, 22f, 17.2f, 17f); lineTo(18f, 9f)
                moveTo(4f, 7f); quadraticTo(12f, 5f, 20f, 7f)
                moveTo(9f, 5.8f); cubicTo(9f, 2.5f, 15f, 2.5f, 15f, 5.8f)
                moveTo(10f, 11f); lineTo(10.5f, 17f); moveTo(14f, 11f); lineTo(13.5f, 17f)
            }
            SkinIconStyle.GOTHIC -> {
                moveTo(6f, 9f); lineTo(7f, 18f); lineTo(12f, 21f); lineTo(17f, 18f); lineTo(18f, 9f)
                moveTo(4f, 7f); lineTo(7f, 5.5f); lineTo(17f, 5.5f); lineTo(20f, 7f)
                moveTo(9f, 5.5f); lineTo(12f, 3f); lineTo(15f, 5.5f)
                moveTo(10f, 10f); lineTo(10f, 17f); moveTo(14f, 10f); lineTo(14f, 17f)
            }
            SkinIconStyle.CHINESE -> {
                moveTo(6f, 9f); lineTo(6f, 20f); lineTo(18f, 20f); lineTo(18f, 9f)
                moveTo(3.5f, 6f); quadraticTo(5f, 8f, 8f, 7f); lineTo(16f, 7f); quadraticTo(19f, 8f, 20.5f, 6f)
                moveTo(9f, 7f); lineTo(9f, 3.5f); lineTo(15f, 3.5f); lineTo(15f, 7f)
                moveTo(10f, 11f); lineTo(10f, 17f); moveTo(14f, 11f); lineTo(14f, 17f)
            }
            SkinIconStyle.CLASSIC -> {
                moveTo(6f, 8f); lineTo(7f, 20f); lineTo(17f, 20f); lineTo(18f, 8f)
                moveTo(4f, 6f); lineTo(20f, 6f); moveTo(9f, 6f); lineTo(9f, 3.5f); lineTo(15f, 3.5f); lineTo(15f, 6f)
                moveTo(10f, 10f); lineTo(10f, 17f); moveTo(14f, 10f); lineTo(14f, 17f)
            }
            SkinIconStyle.NAVY -> {
                moveTo(5f, 9f); lineTo(5f, 17f); quadraticTo(5f, 20f, 8f, 20f); lineTo(16f, 20f); quadraticTo(19f, 20f, 19f, 17f); lineTo(19f, 9f)
                moveTo(4f, 6.5f); lineTo(20f, 6.5f); moveTo(9f, 6.5f); lineTo(9f, 3.5f); lineTo(15f, 3.5f); lineTo(15f, 6.5f)
                moveTo(8f, 13f); lineTo(16f, 13f); moveTo(8f, 16.5f); lineTo(16f, 16.5f)
            }
            SkinIconStyle.COUNTRY -> {
                moveTo(5f, 9f); lineTo(7f, 19f); quadraticTo(12f, 21f, 17f, 19f); lineTo(19f, 9f)
                moveTo(4f, 7f); quadraticTo(12f, 5.5f, 20f, 7f)
                moveTo(9f, 5.8f); quadraticTo(12f, 1.5f, 15f, 5.8f)
                moveTo(9f, 10f); lineTo(10f, 17f); moveTo(15f, 10f); lineTo(14f, 17f)
            }
            SkinIconStyle.VICTORIAN -> {
                moveTo(5.5f, 9f); cubicTo(8f, 12f, 5f, 18f, 9f, 20f); lineTo(15f, 20f); cubicTo(19f, 18f, 16f, 12f, 18.5f, 9f)
                moveTo(4f, 7.5f); cubicTo(7f, 7.5f, 7f, 4.5f, 12f, 4.5f); cubicTo(17f, 4.5f, 17f, 7.5f, 20f, 7.5f)
                moveTo(12f, 3f); lineTo(12f, 4.5f); moveTo(10f, 11f); quadraticTo(12f, 14f, 10f, 17f); moveTo(14f, 11f); quadraticTo(12f, 14f, 14f, 17f)
            }
        }
    }
}

private fun DrawScope.actionEdit(style: SkinIconStyle, tint: Color) {
    actionPath(style, tint) {
        when (style) {
            SkinIconStyle.SWEET -> {
                moveTo(4f, 20f); lineTo(5f, 14f); lineTo(15f, 4f); cubicTo(18f, 1f, 23f, 6f, 20f, 9f); lineTo(10f, 19f); close()
                moveTo(5f, 14f); quadraticTo(9f, 15f, 10f, 19f); moveTo(13f, 6f); lineTo(18f, 11f)
            }
            SkinIconStyle.GOTHIC -> {
                moveTo(3.5f, 20.5f); lineTo(6f, 12f); lineTo(15f, 3.5f); lineTo(20.5f, 3.5f); lineTo(20.5f, 9f); lineTo(12f, 18f); close()
                moveTo(6f, 12f); lineTo(12f, 18f); moveTo(9f, 15f); lineTo(18f, 6f)
            }
            SkinIconStyle.CHINESE -> {
                moveTo(4f, 20f); quadraticTo(4f, 13f, 10f, 12f); lineTo(18f, 3f); lineTo(21f, 6f); lineTo(12f, 14f); quadraticTo(11f, 20f, 4f, 20f)
                moveTo(10f, 12f); lineTo(12f, 14f); moveTo(4f, 20f); quadraticTo(8f, 19f, 8f, 16f)
            }
            SkinIconStyle.CLASSIC -> {
                moveTo(4f, 20f); lineTo(5f, 14f); lineTo(16f, 3f); lineTo(21f, 8f); lineTo(10f, 19f); close()
                moveTo(5f, 14f); lineTo(10f, 19f); moveTo(14f, 5f); lineTo(19f, 10f); moveTo(8f, 15f); lineTo(16f, 7f)
            }
            SkinIconStyle.NAVY -> {
                moveTo(4f, 20f); lineTo(4f, 16f); lineTo(15f, 5f); lineTo(18f, 3f); lineTo(21f, 6f); lineTo(19f, 9f); lineTo(8f, 20f); close()
                moveTo(7f, 13f); lineTo(11f, 17f); moveTo(13f, 7f); lineTo(17f, 11f)
            }
            SkinIconStyle.COUNTRY -> {
                moveTo(4f, 20f); lineTo(6f, 13f); quadraticTo(10f, 5f, 19f, 3f); quadraticTo(21f, 10f, 12f, 16f); close()
                moveTo(4f, 20f); quadraticTo(9f, 12f, 17f, 6f); moveTo(11f, 11f); lineTo(11f, 7f)
            }
            SkinIconStyle.VICTORIAN -> {
                moveTo(3.5f, 20.5f); cubicTo(10f, 17f, 6f, 7f, 20.5f, 3.5f); cubicTo(19f, 12f, 16f, 17f, 9f, 17f)
                moveTo(3.5f, 20.5f); quadraticTo(11f, 12f, 18f, 6f); moveTo(11f, 12f); lineTo(15.5f, 12f)
            }
        }
    }
}

private fun DrawScope.actionSearch(style: SkinIconStyle, tint: Color) {
    actionPath(style, tint) {
        when (style) {
            SkinIconStyle.SWEET -> { moveTo(16f, 10f); cubicTo(16f, 18f, 3.5f, 18f, 3.5f, 10f); cubicTo(3.5f, 2f, 16f, 2f, 16f, 10f); close() }
            SkinIconStyle.GOTHIC -> { moveTo(10f, 3f); lineTo(16f, 7f); lineTo(16f, 13f); lineTo(10f, 17f); lineTo(4f, 13f); lineTo(4f, 7f); close() }
            SkinIconStyle.CHINESE -> { moveTo(4f, 4f); lineTo(16f, 4f); lineTo(16f, 14f); lineTo(13f, 17f); lineTo(4f, 17f); close() }
            SkinIconStyle.CLASSIC -> { moveTo(16f, 10f); cubicTo(16f, 18f, 4f, 18f, 4f, 10f); cubicTo(4f, 2f, 16f, 2f, 16f, 10f); close() }
            SkinIconStyle.NAVY -> { moveTo(8f, 3.5f); lineTo(12f, 3.5f); quadraticTo(17f, 3.5f, 17f, 10f); quadraticTo(17f, 16.5f, 12f, 16.5f); lineTo(8f, 16.5f); quadraticTo(3f, 16.5f, 3f, 10f); quadraticTo(3f, 3.5f, 8f, 3.5f); close() }
            SkinIconStyle.COUNTRY -> { moveTo(16.5f, 9f); cubicTo(18f, 16f, 6f, 19f, 4f, 12f); cubicTo(1f, 5f, 14f, 1f, 16.5f, 9f); close() }
            SkinIconStyle.VICTORIAN -> { moveTo(16f, 9f); cubicTo(16f, 19f, 4f, 19f, 4f, 9f); cubicTo(4f, 1.5f, 16f, 1.5f, 16f, 9f); close() }
        }
    }
    drawPath(actionRibbon(style, 15.5f, 15.5f, 20.5f, 20.5f), tint)
}

private fun DrawScope.actionBars(style: SkinIconStyle, tint: Color, centered: Boolean) {
    for ((index, length) in listOf(16f, 10f, 4f).withIndex()) {
        val start = if (centered) 12f - length / 2 else 4f
        drawPath(actionRibbon(style, start, 6f + index * 6f, start + length, 6f + index * 6f), tint)
    }
}

/** Shape is the tile/node itself, never a border surrounding a second miniature symbol. */
private fun actionTile(style: SkinIconStyle, x: Float, y: Float, w: Float, h: Float) = Path().apply {
    when (style) {
        SkinIconStyle.SWEET -> { moveTo(x + w / 2, y); cubicTo(x + w * 1.17f, y, x + w * 1.17f, y + h, x + w / 2, y + h); cubicTo(x - w * .17f, y + h, x - w * .17f, y, x + w / 2, y) }
        SkinIconStyle.GOTHIC -> { moveTo(x + w / 2, y); lineTo(x + w, y + h * .3f); lineTo(x + w * .85f, y + h); lineTo(x + w * .15f, y + h); lineTo(x, y + h * .3f) }
        SkinIconStyle.CHINESE -> { moveTo(x, y); lineTo(x + w, y + h * .12f); lineTo(x + w * .9f, y + h); lineTo(x + w * .1f, y + h * .9f) }
        SkinIconStyle.CLASSIC -> { moveTo(x, y); lineTo(x + w, y); lineTo(x + w, y + h); lineTo(x, y + h) }
        SkinIconStyle.NAVY -> { moveTo(x + w * .25f, y); lineTo(x + w * .75f, y); quadraticTo(x + w, y, x + w, y + h * .3f); lineTo(x + w, y + h * .7f); quadraticTo(x + w, y + h, x + w * .75f, y + h); lineTo(x + w * .25f, y + h); quadraticTo(x, y + h, x, y + h * .7f); lineTo(x, y + h * .3f); quadraticTo(x, y, x + w * .25f, y) }
        SkinIconStyle.COUNTRY -> { moveTo(x, y + h * .8f); quadraticTo(x, y, x + w, y); quadraticTo(x + w, y + h, x, y + h * .8f) }
        SkinIconStyle.VICTORIAN -> { moveTo(x, y + h * .4f); cubicTo(x, y - h * .13f, x + w, y - h * .13f, x + w, y + h * .4f); quadraticTo(x + w, y + h * .8f, x + w / 2, y + h); quadraticTo(x, y + h * .8f, x, y + h * .4f) }
    }
    close()
}

private fun DrawScope.actionShare(style: SkinIconStyle, tint: Color) {
    // Connections terminate outside the open nodes; tint is never painted over itself there.
    actionPath(style, tint) {
        when (style) {
            SkinIconStyle.SWEET -> { moveTo(8.5f, 10.5f); quadraticTo(12f, 6f, 15.5f, 6f); moveTo(8.5f, 13.5f); quadraticTo(12f, 18f, 15.5f, 18f) }
            SkinIconStyle.GOTHIC -> { moveTo(8.5f, 10f); lineTo(12f, 7f); lineTo(15f, 6f); moveTo(8.5f, 14f); lineTo(12f, 17f); lineTo(15f, 18f) }
            SkinIconStyle.CHINESE -> { moveTo(9f, 11f); lineTo(12f, 11f); lineTo(12f, 6f); lineTo(15f, 6f); moveTo(9f, 13f); lineTo(12f, 13f); lineTo(12f, 18f); lineTo(15f, 18f) }
            SkinIconStyle.CLASSIC -> { moveTo(8.5f, 10.5f); lineTo(15.5f, 6.5f); moveTo(8.5f, 13.5f); lineTo(15.5f, 17.5f) }
            SkinIconStyle.NAVY -> { moveTo(9f, 12f); lineTo(12f, 12f); lineTo(12f, 6f); lineTo(15f, 6f); moveTo(12f, 12f); lineTo(12f, 18f); lineTo(15f, 18f) }
            SkinIconStyle.COUNTRY -> { moveTo(8f, 11f); cubicTo(12f, 11f, 10f, 6f, 15f, 6f); moveTo(8f, 13f); cubicTo(13f, 13f, 11f, 18f, 15f, 18f) }
            SkinIconStyle.VICTORIAN -> { moveTo(8.5f, 10f); cubicTo(8f, 6f, 12f, 5f, 15.5f, 6f); moveTo(8.5f, 14f); cubicTo(8f, 18f, 12f, 19f, 15.5f, 18f) }
        }
    }
    for ((x, y) in listOf(3.5f to 9.5f, 15.5f to 3.5f, 15.5f to 15.5f)) {
        drawPath(actionTile(style, x, y, 5f, 5f), tint, style = style.stroke)
    }
}

private fun DrawScope.actionCopy(style: SkinIconStyle, tint: Color) {
    actionPath(style, tint) {
        when (style) {
            SkinIconStyle.SWEET -> { moveTo(5f, 16f); quadraticTo(3.5f, 16f, 3.5f, 14f); lineTo(3.5f, 6f); quadraticTo(3.5f, 3.5f, 6f, 3.5f); lineTo(14f, 3.5f); quadraticTo(16f, 3.5f, 16f, 5f) }
            SkinIconStyle.GOTHIC -> { moveTo(5f, 16f); lineTo(3.5f, 14f); lineTo(3.5f, 6f); lineTo(6f, 3.5f); lineTo(14f, 3.5f); lineTo(16f, 5f) }
            SkinIconStyle.CHINESE -> { moveTo(4f, 16f); lineTo(4f, 4f); lineTo(15.5f, 4f); moveTo(3f, 4f); lineTo(6f, 4f) }
            SkinIconStyle.CLASSIC -> { moveTo(5f, 16f); lineTo(3.5f, 16f); lineTo(3.5f, 3.5f); lineTo(16f, 3.5f); lineTo(16f, 5f) }
            SkinIconStyle.NAVY -> { moveTo(5f, 16f); quadraticTo(3f, 16f, 3f, 13f); lineTo(3f, 6f); quadraticTo(3f, 3f, 6f, 3f); lineTo(13f, 3f); quadraticTo(16f, 3f, 16f, 5f) }
            SkinIconStyle.COUNTRY -> { moveTo(5f, 16f); lineTo(3.5f, 5f); quadraticTo(10f, 2f, 16f, 4f) }
            SkinIconStyle.VICTORIAN -> { moveTo(5f, 16f); lineTo(4f, 8f); cubicTo(3f, 2f, 14f, 1.5f, 16f, 5f) }
        }
    }
    drawPath(actionSheet(style, 8f, 8f, 12f, 12f), tint, style = style.stroke)
}

private fun DrawScope.actionRefresh(style: SkinIconStyle, tint: Color) {
    actionPath(style, tint) {
        when (style) {
            SkinIconStyle.SWEET -> { moveTo(19.5f, 9f); cubicTo(18f, 2f, 6f, 2f, 4f, 10f); cubicTo(1f, 19f, 14f, 24f, 19f, 16f) }
            SkinIconStyle.GOTHIC -> { moveTo(19.5f, 9f); lineTo(15f, 4f); lineTo(8f, 4f); lineTo(4f, 9f); lineTo(4f, 15f); lineTo(9f, 20f); lineTo(15f, 20f); lineTo(19f, 16f) }
            SkinIconStyle.CHINESE -> { moveTo(19.5f, 9f); lineTo(19.5f, 5f); lineTo(5f, 5f); lineTo(5f, 19f); lineTo(17f, 19f); lineTo(19f, 16f) }
            SkinIconStyle.CLASSIC -> { moveTo(19.5f, 9f); cubicTo(17f, 2f, 7f, 2f, 4f, 9f); cubicTo(0f, 19f, 15f, 24f, 19f, 16f) }
            SkinIconStyle.NAVY -> { moveTo(19.5f, 9f); quadraticTo(19.5f, 5f, 15f, 5f); lineTo(8f, 5f); cubicTo(1f, 5f, 1f, 19f, 8f, 19f); lineTo(15f, 19f); quadraticTo(19f, 19f, 19f, 16f) }
            SkinIconStyle.COUNTRY -> { moveTo(19.5f, 9f); cubicTo(17f, 3f, 8f, 2f, 4.5f, 8f); cubicTo(1f, 14f, 7f, 22f, 13f, 20f); quadraticTo(17f, 19f, 19f, 16f) }
            SkinIconStyle.VICTORIAN -> { moveTo(19.5f, 9f); cubicTo(19f, 3f, 11f, 1f, 7f, 6f); cubicTo(1f, 13f, 5f, 21f, 12f, 20f); quadraticTo(18f, 20f, 19f, 16f) }
        }
    }
    val head = Path.combine(PathOperation.Union,
        actionRibbon(style, 19.8f, 4.5f, 19.5f, 9f),
        actionRibbon(style, 15f, 8.5f, 19.5f, 9f))
    drawPath(head, tint)
}

private fun DrawScope.actionLayout(style: SkinIconStyle, tint: Color, key: IconKey) {
    when (key) {
        IconKey.ViewAgenda -> {
            val x = if (style == SkinIconStyle.NAVY) 3.5f else 4f
            val width = 24f - 2 * x
            for (y in listOf(4f, 14f)) drawPath(actionTile(style, x, y, width, 6f), tint, style = style.stroke)
        }
        IconKey.GridView -> for (x in listOf(4f, 14f)) for (y in listOf(4f, 14f)) {
            drawPath(actionTile(style, x, y, 6f, 6f), tint, style = style.stroke)
        }
        IconKey.Apps -> for (column in 0..2) for (row in 0..2) {
            // Filled modules preserve their silhouettes at 18dp without tiny hollow interiors.
            val gap = if (style == SkinIconStyle.CLASSIC) 6.5f else 6.7f
            val width = if (style == SkinIconStyle.NAVY) 4.1f else 3.7f
            drawPath(actionTile(style, 3.3f + column * gap, 3.3f + row * gap, width, 3.7f), tint)
        }
        else -> Unit
    }
}

private fun DrawScope.actionGallery(style: SkinIconStyle, tint: Color) {
    actionPath(style, tint) {
        when (style) {
            SkinIconStyle.SWEET -> { moveTo(8f, 4f); lineTo(17f, 4f); quadraticTo(20f, 4f, 20f, 7f); lineTo(20f, 15f) }
            SkinIconStyle.GOTHIC -> { moveTo(8f, 4f); lineTo(17f, 4f); lineTo(20f, 7f); lineTo(20f, 15f) }
            SkinIconStyle.CHINESE -> { moveTo(8f, 4f); lineTo(20f, 4f); lineTo(20f, 15f); moveTo(18f, 4f); lineTo(21f, 4f) }
            SkinIconStyle.CLASSIC -> { moveTo(8f, 4f); lineTo(20f, 4f); lineTo(20f, 15f) }
            SkinIconStyle.NAVY -> { moveTo(8f, 4f); lineTo(17f, 4f); quadraticTo(20f, 4f, 20f, 8f); lineTo(20f, 15f) }
            SkinIconStyle.COUNTRY -> { moveTo(8f, 4.5f); quadraticTo(14f, 2.5f, 20f, 4f); lineTo(20.5f, 15f) }
            SkinIconStyle.VICTORIAN -> { moveTo(8f, 5f); cubicTo(8f, 1.5f, 20f, 1.5f, 20f, 7f); lineTo(20f, 15f) }
        }
    }
    drawPath(actionSheet(style, 3.5f, 7f, 13f, 13f), tint, style = style.stroke)
    drawCircle(tint, 1.1f, Offset(8f, 11f))
    actionPath(style, tint) {
        moveTo(5.5f, 17f)
        when (style) {
            SkinIconStyle.SWEET -> { quadraticTo(8f, 12f, 10.5f, 16f); quadraticTo(13f, 12f, 15f, 17f) }
            SkinIconStyle.GOTHIC -> { lineTo(8f, 13f); lineTo(11f, 17f); lineTo(13f, 14f); lineTo(15f, 17f) }
            SkinIconStyle.CHINESE -> { quadraticTo(7f, 16f, 8.5f, 13f); quadraticTo(9f, 15f, 11f, 16f); lineTo(13f, 14f); lineTo(15f, 17f) }
            SkinIconStyle.CLASSIC -> { lineTo(8.5f, 14f); lineTo(11f, 16.5f); lineTo(13f, 14.5f); lineTo(15f, 17f) }
            SkinIconStyle.NAVY -> { lineTo(8.5f, 14f); lineTo(11f, 17f); lineTo(14f, 17f) }
            SkinIconStyle.COUNTRY -> { quadraticTo(8f, 12f, 10.5f, 16f); quadraticTo(13f, 14f, 15f, 17f) }
            SkinIconStyle.VICTORIAN -> { cubicTo(7f, 16f, 7f, 12f, 9.5f, 14.5f); lineTo(11f, 16.5f); quadraticTo(13f, 13f, 15f, 17f) }
        }
    }
}

/** Paper/photo contours retain a usable rectangular image area, including the handmade skins. */
private fun actionSheet(style: SkinIconStyle, x: Float, y: Float, w: Float, h: Float): Path {
    if (style == SkinIconStyle.CLASSIC || style == SkinIconStyle.NAVY) {
        return actionTile(style, x, y, w, h)
    }
    return Path().apply {
        when (style) {
            SkinIconStyle.SWEET -> {
                moveTo(x + w * .22f, y); lineTo(x + w * .78f, y)
                quadraticTo(x + w, y, x + w, y + h * .22f)
                lineTo(x + w, y + h * .78f); quadraticTo(x + w, y + h, x + w * .78f, y + h)
                lineTo(x + w * .22f, y + h); quadraticTo(x, y + h, x, y + h * .78f)
                lineTo(x, y + h * .22f); quadraticTo(x, y, x + w * .22f, y); close()
            }
            SkinIconStyle.GOTHIC -> {
                moveTo(x, y); lineTo(x + w * .72f, y); lineTo(x + w, y + h * .28f)
                lineTo(x + w, y + h); lineTo(x + w * .15f, y + h)
                lineTo(x, y + h * .85f); close()
            }
            SkinIconStyle.CHINESE -> {
                moveTo(x, y + h * .12f); lineTo(x + w, y + h * .12f)
                lineTo(x + w, y + h); lineTo(x, y + h); close()
                moveTo(x, y); lineTo(x, y + h * .25f)
                moveTo(x + w, y); lineTo(x + w, y + h * .25f)
            }
            SkinIconStyle.COUNTRY -> {
                moveTo(x, y + h * .07f); quadraticTo(x + w * .5f, y - h * .07f, x + w, y)
                lineTo(x + w * .94f, y + h * .95f)
                quadraticTo(x + w * .45f, y + h * 1.05f, x + w * .04f, y + h); close()
            }
            SkinIconStyle.VICTORIAN -> {
                moveTo(x, y + h * .25f); cubicTo(x, y - h * .08f, x + w, y - h * .08f, x + w, y + h * .25f)
                lineTo(x + w, y + h * .87f); quadraticTo(x + w, y + h, x + w * .8f, y + h)
                lineTo(x + w * .2f, y + h); quadraticTo(x, y + h, x, y + h * .87f); close()
            }
            else -> Unit
        }
    }
}
