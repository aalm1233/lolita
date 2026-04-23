package com.lolita.app.ui.theme.skin.icon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

private val CountryDecorCream = Color(0xFFF8EEDB)

internal enum class CountryActionKind {
    Add, Delete, Edit, Search, Sort, Save, Close, Share,
    FilterList, MoreVert, ContentCopy, Refresh, ViewAgenda, GridView, Apps, Gallery
}

internal enum class CountryContentKind {
    Star, StarBorder, Image, Camera, AddPhoto, Link, LinkOff,
    Palette, FileOpen, CalendarMonth, Notifications, AttachMoney, Category, Location
}

internal enum class CountryArrowKind {
    ArrowBack, ArrowForward, KeyboardArrowLeft, KeyboardArrowRight,
    ExpandMore, ExpandLess, ArrowDropDown, SwapVert, OpenInNew
}

internal enum class CountryStatusKind {
    CheckCircle, Warning, Error, Info, Visibility, VisibilityOff, Help
}

@Composable
internal fun CountryActionDecorativeIcon(
    kind: CountryActionKind,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    CountryDecorCanvas(modifier) { s ->
        when (kind) {
            CountryActionKind.Add -> {
                drawPlaque(Offset(s * 0.5f, s * 0.62f), s * 0.66f, s * 0.5f, tint)
                drawCountryBow(Offset(s * 0.5f, s * 0.22f), s * 0.2f, tint)
                drawPlusGlyph(Offset(s * 0.5f, s * 0.62f), s * 0.28f, CountryDecorCream)
            }
            CountryActionKind.Delete -> {
                drawTag(Offset(s * 0.5f, s * 0.58f), s * 0.56f, s * 0.68f, tint)
                drawCrossGlyph(Offset(s * 0.5f, s * 0.6f), s * 0.26f, CountryDecorCream)
            }
            CountryActionKind.Edit -> {
                drawTag(Offset(s * 0.5f, s * 0.58f), s * 0.58f, s * 0.7f, tint)
                drawQuillGlyph(Offset(s * 0.52f, s * 0.58f), s * 0.3f, CountryDecorCream)
            }
            CountryActionKind.Search -> {
                drawSearchHoop(Offset(s * 0.48f, s * 0.48f), s * 0.22f, tint)
                drawCountryBow(Offset(s * 0.38f, s * 0.18f), s * 0.14f, tint)
            }
            CountryActionKind.Sort -> {
                drawVerticalRibbons(Offset(s * 0.5f, s * 0.52f), s * 0.58f, tint)
            }
            CountryActionKind.Save -> {
                drawRosetteBadge(Offset(s * 0.5f, s * 0.46f), s * 0.24f, tint, true)
                drawCheckGlyph(Offset(s * 0.5f, s * 0.46f), s * 0.18f, CountryDecorCream)
            }
            CountryActionKind.Close -> {
                drawPlaque(Offset(s * 0.5f, s * 0.58f), s * 0.58f, s * 0.44f, tint)
                drawCountryBow(Offset(s * 0.5f, s * 0.24f), s * 0.18f, tint)
                drawCrossGlyph(Offset(s * 0.5f, s * 0.58f), s * 0.24f, CountryDecorCream)
            }
            CountryActionKind.Share -> {
                drawPostcard(Offset(s * 0.48f, s * 0.56f), s * 0.66f, s * 0.48f, tint)
                drawRibbonArrowGlyph(Offset(s * 0.66f, s * 0.46f), s * 0.18f, CountryDecorCream, 1, false)
            }
            CountryActionKind.FilterList -> {
                drawFilterPennants(Offset(s * 0.5f, s * 0.56f), s * 0.64f, s * 0.56f, tint)
            }
            CountryActionKind.MoreVert -> {
                drawTag(Offset(s * 0.5f, s * 0.56f), s * 0.42f, s * 0.7f, tint)
                drawCircle(CountryDecorCream, s * 0.055f, Offset(s * 0.5f, s * 0.42f))
                drawCircle(CountryDecorCream, s * 0.055f, Offset(s * 0.5f, s * 0.58f))
                drawCircle(CountryDecorCream, s * 0.055f, Offset(s * 0.5f, s * 0.74f))
            }
            CountryActionKind.ContentCopy -> {
                drawStackedCards(Offset(s * 0.5f, s * 0.58f), s * 0.54f, s * 0.6f, tint)
            }
            CountryActionKind.Refresh -> {
                drawRefreshWreath(Offset(s * 0.5f, s * 0.54f), s * 0.28f, tint)
            }
            CountryActionKind.ViewAgenda -> {
                drawPlaque(Offset(s * 0.5f, s * 0.58f), s * 0.66f, s * 0.56f, tint)
                drawRoundRect(CountryDecorCream, Offset(s * 0.26f, s * 0.42f), Size(s * 0.48f, s * 0.08f), CornerRadius(s * 0.04f, s * 0.04f))
                drawRoundRect(CountryDecorCream, Offset(s * 0.26f, s * 0.56f), Size(s * 0.48f, s * 0.08f), CornerRadius(s * 0.04f, s * 0.04f))
                drawRoundRect(CountryDecorCream, Offset(s * 0.26f, s * 0.7f), Size(s * 0.48f, s * 0.08f), CornerRadius(s * 0.04f, s * 0.04f))
            }
            CountryActionKind.GridView -> {
                drawQuiltBoard(Offset(s * 0.5f, s * 0.58f), s * 0.62f, s * 0.56f, tint, 2)
            }
            CountryActionKind.Apps -> {
                drawQuiltBoard(Offset(s * 0.5f, s * 0.58f), s * 0.66f, s * 0.6f, tint, 3)
            }
            CountryActionKind.Gallery -> {
                drawPostcard(Offset(s * 0.5f, s * 0.58f), s * 0.68f, s * 0.52f, tint)
                drawCountryBow(Offset(s * 0.5f, s * 0.2f), s * 0.16f, tint)
            }
        }
    }
}
@Composable
internal fun CountryContentDecorativeIcon(
    kind: CountryContentKind,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    CountryDecorCanvas(modifier) { s ->
        when (kind) {
            CountryContentKind.Star -> drawRosetteBadge(Offset(s * 0.5f, s * 0.5f), s * 0.24f, tint, false)
            CountryContentKind.StarBorder -> drawHollowRosette(Offset(s * 0.5f, s * 0.5f), s * 0.24f, tint)
            CountryContentKind.Image -> drawPostcard(Offset(s * 0.5f, s * 0.58f), s * 0.68f, s * 0.52f, tint)
            CountryContentKind.Camera -> drawCameraCharm(Offset(s * 0.5f, s * 0.56f), s * 0.66f, s * 0.46f, tint)
            CountryContentKind.AddPhoto -> {
                drawPostcard(Offset(s * 0.46f, s * 0.58f), s * 0.62f, s * 0.48f, tint)
                drawPlaque(Offset(s * 0.76f, s * 0.32f), s * 0.22f, s * 0.22f, tint)
                drawPlusGlyph(Offset(s * 0.76f, s * 0.32f), s * 0.12f, CountryDecorCream)
            }
            CountryContentKind.Link -> drawChainLinks(Offset(s * 0.5f, s * 0.56f), s * 0.66f, s * 0.34f, tint, false)
            CountryContentKind.LinkOff -> drawChainLinks(Offset(s * 0.5f, s * 0.56f), s * 0.66f, s * 0.34f, tint, true)
            CountryContentKind.Palette -> drawSwatchFan(Offset(s * 0.52f, s * 0.62f), s * 0.68f, s * 0.5f, tint)
            CountryContentKind.FileOpen -> drawOpenLetter(Offset(s * 0.5f, s * 0.58f), s * 0.68f, s * 0.52f, tint)
            CountryContentKind.CalendarMonth -> drawCalendarCharm(Offset(s * 0.5f, s * 0.58f), s * 0.66f, s * 0.56f, tint)
            CountryContentKind.Notifications -> drawBellCharm(Offset(s * 0.5f, s * 0.56f), s * 0.56f, s * 0.62f, tint)
            CountryContentKind.AttachMoney -> drawCoinPurse(Offset(s * 0.5f, s * 0.58f), s * 0.62f, s * 0.52f, tint)
            CountryContentKind.Category -> drawCabinetCharm(Offset(s * 0.5f, s * 0.58f), s * 0.68f, s * 0.56f, tint)
            CountryContentKind.Location -> drawSignpostCharm(Offset(s * 0.5f, s * 0.58f), s * 0.62f, s * 0.62f, tint)
        }
    }
}

@Composable
internal fun CountryArrowDecorativeIcon(
    kind: CountryArrowKind,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    CountryDecorCanvas(modifier) { s ->
        when (kind) {
            CountryArrowKind.ArrowBack -> drawArrowSign(Offset(s * 0.5f, s * 0.58f), s * 0.72f, s * 0.42f, tint, -1)
            CountryArrowKind.ArrowForward -> drawArrowSign(Offset(s * 0.5f, s * 0.58f), s * 0.72f, s * 0.42f, tint, 1)
            CountryArrowKind.KeyboardArrowLeft -> drawRibbonArrowGlyph(Offset(s * 0.5f, s * 0.58f), s * 0.28f, tint, -1, false)
            CountryArrowKind.KeyboardArrowRight -> drawRibbonArrowGlyph(Offset(s * 0.5f, s * 0.58f), s * 0.28f, tint, 1, false)
            CountryArrowKind.ExpandMore -> {
                drawCountryBow(Offset(s * 0.5f, s * 0.24f), s * 0.16f, tint)
                drawRibbonArrowGlyph(Offset(s * 0.5f, s * 0.62f), s * 0.26f, tint, 1, true)
            }
            CountryArrowKind.ExpandLess -> {
                drawCountryBow(Offset(s * 0.5f, s * 0.76f), s * 0.16f, tint)
                drawRibbonArrowGlyph(Offset(s * 0.5f, s * 0.44f), s * 0.26f, tint, -1, true)
            }
            CountryArrowKind.ArrowDropDown -> drawRibbonArrowGlyph(Offset(s * 0.5f, s * 0.56f), s * 0.3f, tint, 1, true)
            CountryArrowKind.SwapVert -> drawVerticalRibbons(Offset(s * 0.5f, s * 0.52f), s * 0.6f, tint)
            CountryArrowKind.OpenInNew -> {
                drawTag(Offset(s * 0.46f, s * 0.58f), s * 0.54f, s * 0.66f, tint)
                drawOpenCornerArrow(Offset(s * 0.58f, s * 0.5f), s * 0.22f, CountryDecorCream)
            }
        }
    }
}

@Composable
internal fun CountryStatusDecorativeIcon(
    kind: CountryStatusKind,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    CountryDecorCanvas(modifier) { s ->
        when (kind) {
            CountryStatusKind.CheckCircle -> {
                drawRosetteBadge(Offset(s * 0.5f, s * 0.48f), s * 0.24f, tint, true)
                drawCheckGlyph(Offset(s * 0.5f, s * 0.48f), s * 0.18f, CountryDecorCream)
            }
            CountryStatusKind.Warning -> {
                drawPennant(Offset(s * 0.5f, s * 0.56f), s * 0.52f, s * 0.7f, tint)
                drawExclamationGlyph(Offset(s * 0.5f, s * 0.52f), s * 0.2f, CountryDecorCream)
            }
            CountryStatusKind.Error -> {
                drawPlaque(Offset(s * 0.5f, s * 0.58f), s * 0.6f, s * 0.46f, tint)
                drawCrossGlyph(Offset(s * 0.5f, s * 0.58f), s * 0.24f, CountryDecorCream)
            }
            CountryStatusKind.Info -> {
                drawPlaque(Offset(s * 0.5f, s * 0.58f), s * 0.6f, s * 0.46f, tint)
                drawInfoGlyph(Offset(s * 0.5f, s * 0.58f), s * 0.22f, CountryDecorCream)
            }
            CountryStatusKind.Visibility -> drawCurtainCharm(Offset(s * 0.5f, s * 0.56f), s * 0.7f, s * 0.56f, tint, false)
            CountryStatusKind.VisibilityOff -> drawCurtainCharm(Offset(s * 0.5f, s * 0.56f), s * 0.7f, s * 0.56f, tint, true)
            CountryStatusKind.Help -> {
                drawTag(Offset(s * 0.5f, s * 0.58f), s * 0.56f, s * 0.68f, tint)
                drawQuestionGlyph(Offset(s * 0.5f, s * 0.56f), s * 0.24f, CountryDecorCream)
            }
        }
    }
}

@Composable
private fun CountryDecorCanvas(
    modifier: Modifier,
    drawBlock: DrawScope.(Float) -> Unit
) {
    Canvas(Modifier.size(24.dp).then(modifier)) {
        drawBlock(size.minDimension)
    }
}

private fun DrawScope.drawCountryBow(
    center: Offset,
    scale: Float,
    tint: Color,
    knotColor: Color = CountryDecorCream
) {
    val leftLoop = Path().apply {
        moveTo(center.x - scale * 0.1f, center.y)
        cubicTo(
            center.x - scale * 0.42f, center.y - scale * 0.32f,
            center.x - scale * 0.5f, center.y + scale * 0.02f,
            center.x - scale * 0.2f, center.y + scale * 0.18f
        )
        close()
    }
    val rightLoop = Path().apply {
        moveTo(center.x + scale * 0.1f, center.y)
        cubicTo(
            center.x + scale * 0.42f, center.y - scale * 0.32f,
            center.x + scale * 0.5f, center.y + scale * 0.02f,
            center.x + scale * 0.2f, center.y + scale * 0.18f
        )
        close()
    }
    val leftTail = Path().apply {
        moveTo(center.x - scale * 0.08f, center.y + scale * 0.1f)
        lineTo(center.x - scale * 0.22f, center.y + scale * 0.4f)
        lineTo(center.x - scale * 0.02f, center.y + scale * 0.28f)
        close()
    }
    val rightTail = Path().apply {
        moveTo(center.x + scale * 0.08f, center.y + scale * 0.1f)
        lineTo(center.x + scale * 0.22f, center.y + scale * 0.4f)
        lineTo(center.x + scale * 0.02f, center.y + scale * 0.28f)
        close()
    }
    drawPath(leftLoop, tint)
    drawPath(rightLoop, tint)
    drawPath(leftTail, tint)
    drawPath(rightTail, tint)
    drawRoundRect(
        color = knotColor,
        topLeft = Offset(center.x - scale * 0.09f, center.y - scale * 0.05f),
        size = Size(scale * 0.18f, scale * 0.16f),
        cornerRadius = CornerRadius(scale * 0.04f, scale * 0.04f)
    )
}
private fun DrawScope.drawPlaque(center: Offset, width: Float, height: Float, tint: Color) {
    val left = center.x - width / 2f
    val top = center.y - height / 2f
    val right = center.x + width / 2f
    val bottom = center.y + height / 2f
    val plaque = Path().apply {
        moveTo(left + width * 0.12f, top)
        lineTo(right - width * 0.12f, top)
        quadraticTo(right, top, right, top + height * 0.18f)
        lineTo(right, bottom - height * 0.18f)
        quadraticTo(right, bottom, right - width * 0.12f, bottom)
        lineTo(left + width * 0.12f, bottom)
        quadraticTo(left, bottom, left, bottom - height * 0.18f)
        lineTo(left, top + height * 0.18f)
        quadraticTo(left, top, left + width * 0.12f, top)
        close()
    }
    drawPath(plaque, tint)
}

private fun DrawScope.drawTag(center: Offset, width: Float, height: Float, tint: Color) {
    val left = center.x - width / 2f
    val top = center.y - height / 2f
    val right = center.x + width / 2f
    val bottom = center.y + height / 2f
    val tag = Path().apply {
        moveTo(left + width * 0.18f, top)
        lineTo(right, top)
        lineTo(right, bottom)
        lineTo(left, bottom)
        lineTo(left, top + height * 0.2f)
        close()
    }
    drawPath(tag, tint)
    drawCircle(CountryDecorCream, width * 0.06f, Offset(left + width * 0.16f, top + height * 0.15f))
}

private fun DrawScope.drawRosetteBadge(center: Offset, radius: Float, tint: Color, ribbons: Boolean) {
    repeat(8) { index ->
        rotate(index * 45f, center) {
            drawCircle(tint, radius * 0.28f, Offset(center.x, center.y - radius * 0.52f))
        }
    }
    if (ribbons) {
        val leftRibbon = Path().apply {
            moveTo(center.x - radius * 0.18f, center.y + radius * 0.44f)
            lineTo(center.x - radius * 0.42f, center.y + radius * 1.08f)
            lineTo(center.x - radius * 0.06f, center.y + radius * 0.78f)
            close()
        }
        val rightRibbon = Path().apply {
            moveTo(center.x + radius * 0.18f, center.y + radius * 0.44f)
            lineTo(center.x + radius * 0.42f, center.y + radius * 1.08f)
            lineTo(center.x + radius * 0.06f, center.y + radius * 0.78f)
            close()
        }
        drawPath(leftRibbon, tint)
        drawPath(rightRibbon, tint)
    }
    drawCircle(tint, radius * 0.6f, center)
    drawCircle(CountryDecorCream, radius * 0.32f, center)
}

private fun DrawScope.drawHollowRosette(center: Offset, radius: Float, tint: Color) {
    repeat(8) { index ->
        rotate(index * 45f, center) {
            drawCircle(tint, radius * 0.26f, Offset(center.x, center.y - radius * 0.5f))
        }
    }
    drawCircle(tint, radius * 0.54f, center)
    drawCircle(CountryDecorCream, radius * 0.3f, center)
    drawCircle(tint.copy(alpha = 0.2f), radius * 0.14f, center)
}

private fun DrawScope.drawSearchHoop(center: Offset, radius: Float, tint: Color) {
    drawCircle(tint, radius, center)
    drawCircle(CountryDecorCream, radius * 0.56f, center)
    rotate(42f, Offset(center.x + radius * 0.5f, center.y + radius * 0.54f)) {
        drawRoundRect(
            color = tint,
            topLeft = Offset(center.x + radius * 0.36f, center.y + radius * 0.38f),
            size = Size(radius * 0.34f, radius * 0.14f),
            cornerRadius = CornerRadius(radius * 0.07f, radius * 0.07f)
        )
    }
}

private fun DrawScope.drawVerticalRibbons(center: Offset, height: Float, tint: Color) {
    drawRibbonArrowGlyph(Offset(center.x, center.y - height * 0.2f), height * 0.28f, tint, -1, true)
    drawRibbonArrowGlyph(Offset(center.x, center.y + height * 0.2f), height * 0.28f, tint, 1, true)
}

private fun DrawScope.drawPostcard(center: Offset, width: Float, height: Float, tint: Color) {
    drawRoundRect(
        color = tint,
        topLeft = Offset(center.x - width / 2f, center.y - height / 2f),
        size = Size(width, height),
        cornerRadius = CornerRadius(width * 0.12f, width * 0.12f)
    )
    drawRoundRect(
        color = CountryDecorCream,
        topLeft = Offset(center.x - width * 0.34f, center.y - height * 0.28f),
        size = Size(width * 0.68f, height * 0.56f),
        cornerRadius = CornerRadius(width * 0.08f, width * 0.08f)
    )
    val hill = Path().apply {
        moveTo(center.x - width * 0.28f, center.y + height * 0.18f)
        lineTo(center.x - width * 0.08f, center.y - height * 0.02f)
        lineTo(center.x + width * 0.08f, center.y + height * 0.1f)
        lineTo(center.x + width * 0.22f, center.y - height * 0.08f)
        lineTo(center.x + width * 0.3f, center.y + height * 0.18f)
        close()
    }
    drawPath(hill, tint.copy(alpha = 0.22f))
    drawCircle(tint.copy(alpha = 0.22f), width * 0.08f, Offset(center.x + width * 0.18f, center.y - height * 0.12f))
}

private fun DrawScope.drawStackedCards(center: Offset, width: Float, height: Float, tint: Color) {
    rotate(-9f, center) {
        drawRoundRect(
            color = tint.copy(alpha = 0.5f),
            topLeft = Offset(center.x - width * 0.44f, center.y - height * 0.32f),
            size = Size(width * 0.7f, height * 0.5f),
            cornerRadius = CornerRadius(width * 0.08f, width * 0.08f)
        )
    }
    rotate(9f, center) {
        drawRoundRect(
            color = tint.copy(alpha = 0.8f),
            topLeft = Offset(center.x - width * 0.12f, center.y - height * 0.28f),
            size = Size(width * 0.7f, height * 0.5f),
            cornerRadius = CornerRadius(width * 0.08f, width * 0.08f)
        )
    }
    drawRoundRect(
        color = tint,
        topLeft = Offset(center.x - width * 0.38f, center.y - height * 0.2f),
        size = Size(width * 0.76f, height * 0.54f),
        cornerRadius = CornerRadius(width * 0.08f, width * 0.08f)
    )
    drawRoundRect(
        color = CountryDecorCream,
        topLeft = Offset(center.x - width * 0.26f, center.y - height * 0.08f),
        size = Size(width * 0.52f, height * 0.08f),
        cornerRadius = CornerRadius(width * 0.04f, width * 0.04f)
    )
}
private fun DrawScope.drawRefreshWreath(center: Offset, radius: Float, tint: Color) {
    drawCircle(tint, radius, center)
    drawCircle(CountryDecorCream, radius * 0.58f, center)
    drawArc(
        color = tint.copy(alpha = 0.22f),
        startAngle = 220f,
        sweepAngle = 240f,
        useCenter = false,
        topLeft = Offset(center.x - radius * 0.74f, center.y - radius * 0.74f),
        size = Size(radius * 1.48f, radius * 1.48f),
        style = Stroke(width = radius * 0.28f, cap = StrokeCap.Round)
    )
    val arrow = Path().apply {
        moveTo(center.x + radius * 0.12f, center.y - radius * 0.78f)
        lineTo(center.x + radius * 0.54f, center.y - radius * 0.66f)
        lineTo(center.x + radius * 0.26f, center.y - radius * 0.38f)
        close()
    }
    drawPath(arrow, tint)
}

private fun DrawScope.drawQuiltBoard(center: Offset, width: Float, height: Float, tint: Color, cells: Int) {
    drawRoundRect(
        color = tint,
        topLeft = Offset(center.x - width / 2f, center.y - height / 2f),
        size = Size(width, height),
        cornerRadius = CornerRadius(width * 0.12f, width * 0.12f)
    )
    val gap = width * 0.06f
    val cellSize = if (cells == 2) width * 0.22f else width * 0.15f
    val total = cells * cellSize + (cells - 1) * gap
    val startX = center.x - total / 2f
    val startY = center.y - total / 2f
    repeat(cells) { row ->
        repeat(cells) { col ->
            drawRoundRect(
                color = CountryDecorCream,
                topLeft = Offset(startX + col * (cellSize + gap), startY + row * (cellSize + gap)),
                size = Size(cellSize, cellSize),
                cornerRadius = CornerRadius(cellSize * 0.26f, cellSize * 0.26f)
            )
        }
    }
}

private fun DrawScope.drawCameraCharm(center: Offset, width: Float, height: Float, tint: Color) {
    drawRoundRect(
        color = tint,
        topLeft = Offset(center.x - width / 2f, center.y - height / 2f),
        size = Size(width, height),
        cornerRadius = CornerRadius(width * 0.12f, width * 0.12f)
    )
    drawRoundRect(
        color = tint,
        topLeft = Offset(center.x - width * 0.26f, center.y - height * 0.72f),
        size = Size(width * 0.22f, height * 0.18f),
        cornerRadius = CornerRadius(width * 0.06f, width * 0.06f)
    )
    drawCircle(CountryDecorCream, width * 0.18f, Offset(center.x, center.y))
    drawCircle(tint.copy(alpha = 0.22f), width * 0.08f, Offset(center.x, center.y))
    drawCountryBow(Offset(center.x + width * 0.2f, center.y - height * 0.44f), width * 0.18f, tint)
}

private fun DrawScope.drawChainLinks(center: Offset, width: Float, height: Float, tint: Color, broken: Boolean) {
    drawOval(
        color = tint,
        topLeft = Offset(center.x - width * 0.34f, center.y - height / 2f),
        size = Size(width * 0.36f, height)
    )
    drawOval(
        color = CountryDecorCream,
        topLeft = Offset(center.x - width * 0.26f, center.y - height * 0.32f),
        size = Size(width * 0.2f, height * 0.64f)
    )
    drawOval(
        color = tint,
        topLeft = Offset(center.x - width * 0.02f, center.y - height / 2f),
        size = Size(width * 0.36f, height)
    )
    drawOval(
        color = CountryDecorCream,
        topLeft = Offset(center.x + width * 0.06f, center.y - height * 0.32f),
        size = Size(width * 0.2f, height * 0.64f)
    )
    if (broken) {
        drawCrossGlyph(Offset(center.x, center.y), width * 0.18f, tint)
    }
}

private fun DrawScope.drawSwatchFan(center: Offset, width: Float, height: Float, tint: Color) {
    val angles = listOf(-18f, 0f, 18f)
    angles.forEachIndexed { index, angle ->
        rotate(angle, center) {
            drawRoundRect(
                color = if (index == 1) tint else tint.copy(alpha = 0.74f),
                topLeft = Offset(center.x - width * 0.16f, center.y - height * 0.34f),
                size = Size(width * 0.28f, height * 0.62f),
                cornerRadius = CornerRadius(width * 0.06f, width * 0.06f)
            )
            drawRoundRect(
                color = CountryDecorCream,
                topLeft = Offset(center.x - width * 0.08f, center.y - height * 0.26f),
                size = Size(width * 0.08f, height * 0.46f),
                cornerRadius = CornerRadius(width * 0.03f, width * 0.03f)
            )
        }
    }
    drawCircle(tint, width * 0.07f, Offset(center.x, center.y + height * 0.22f))
}

private fun DrawScope.drawOpenLetter(center: Offset, width: Float, height: Float, tint: Color) {
    val envelope = Path().apply {
        moveTo(center.x - width / 2f, center.y - height * 0.08f)
        lineTo(center.x, center.y - height / 2f)
        lineTo(center.x + width / 2f, center.y - height * 0.08f)
        lineTo(center.x + width / 2f, center.y + height / 2f)
        lineTo(center.x - width / 2f, center.y + height / 2f)
        close()
    }
    drawPath(envelope, tint)
    val letter = Path().apply {
        moveTo(center.x - width * 0.26f, center.y - height * 0.02f)
        lineTo(center.x, center.y - height * 0.32f)
        lineTo(center.x + width * 0.26f, center.y - height * 0.02f)
        lineTo(center.x + width * 0.22f, center.y + height * 0.24f)
        lineTo(center.x - width * 0.22f, center.y + height * 0.24f)
        close()
    }
    drawPath(letter, CountryDecorCream)
    drawRoundRect(
        color = CountryDecorCream,
        topLeft = Offset(center.x - width * 0.18f, center.y + height * 0.04f),
        size = Size(width * 0.36f, height * 0.06f),
        cornerRadius = CornerRadius(width * 0.03f, width * 0.03f)
    )
}

private fun DrawScope.drawCalendarCharm(center: Offset, width: Float, height: Float, tint: Color) {
    drawPlaque(center, width, height, tint)
    drawCountryBow(Offset(center.x, center.y - height * 0.54f), width * 0.2f, tint)
    drawRoundRect(
        color = CountryDecorCream,
        topLeft = Offset(center.x - width * 0.28f, center.y - height * 0.12f),
        size = Size(width * 0.56f, height * 0.36f),
        cornerRadius = CornerRadius(width * 0.06f, width * 0.06f)
    )
    repeat(3) { index ->
        drawRoundRect(
            color = tint.copy(alpha = 0.22f),
            topLeft = Offset(center.x - width * 0.2f + index * width * 0.15f, center.y + height * 0.02f),
            size = Size(width * 0.08f, height * 0.12f),
            cornerRadius = CornerRadius(width * 0.02f, width * 0.02f)
        )
    }
}

private fun DrawScope.drawBellCharm(center: Offset, width: Float, height: Float, tint: Color) {
    val bell = Path().apply {
        moveTo(center.x - width * 0.28f, center.y - height * 0.02f)
        quadraticTo(center.x - width * 0.22f, center.y - height * 0.34f, center.x, center.y - height * 0.34f)
        quadraticTo(center.x + width * 0.22f, center.y - height * 0.34f, center.x + width * 0.28f, center.y - height * 0.02f)
        lineTo(center.x + width * 0.34f, center.y + height * 0.22f)
        lineTo(center.x - width * 0.34f, center.y + height * 0.22f)
        close()
    }
    drawPath(bell, tint)
    drawRoundRect(
        color = tint,
        topLeft = Offset(center.x - width * 0.08f, center.y - height * 0.48f),
        size = Size(width * 0.16f, height * 0.12f),
        cornerRadius = CornerRadius(width * 0.04f, width * 0.04f)
    )
    drawCircle(CountryDecorCream, width * 0.08f, Offset(center.x, center.y + height * 0.12f))
    drawCountryBow(Offset(center.x, center.y - height * 0.54f), width * 0.18f, tint)
}
private fun DrawScope.drawCoinPurse(center: Offset, width: Float, height: Float, tint: Color) {
    val purse = Path().apply {
        moveTo(center.x - width * 0.3f, center.y - height * 0.08f)
        quadraticTo(center.x - width * 0.24f, center.y + height * 0.3f, center.x, center.y + height * 0.34f)
        quadraticTo(center.x + width * 0.24f, center.y + height * 0.3f, center.x + width * 0.3f, center.y - height * 0.08f)
        lineTo(center.x + width * 0.18f, center.y - height * 0.28f)
        lineTo(center.x - width * 0.18f, center.y - height * 0.28f)
        close()
    }
    drawPath(purse, tint)
    drawCircle(tint, width * 0.08f, Offset(center.x - width * 0.1f, center.y - height * 0.34f))
    drawCircle(tint, width * 0.08f, Offset(center.x + width * 0.1f, center.y - height * 0.34f))
    drawCircle(CountryDecorCream, width * 0.07f, Offset(center.x, center.y + height * 0.06f))
}

private fun DrawScope.drawCabinetCharm(center: Offset, width: Float, height: Float, tint: Color) {
    drawRoundRect(
        color = tint,
        topLeft = Offset(center.x - width / 2f, center.y - height / 2f),
        size = Size(width, height),
        cornerRadius = CornerRadius(width * 0.1f, width * 0.1f)
    )
    repeat(2) { row ->
        repeat(2) { col ->
            val left = center.x - width * 0.34f + col * width * 0.34f
            val top = center.y - height * 0.28f + row * height * 0.28f
            drawRoundRect(
                color = CountryDecorCream,
                topLeft = Offset(left, top),
                size = Size(width * 0.24f, height * 0.18f),
                cornerRadius = CornerRadius(width * 0.04f, width * 0.04f)
            )
            drawCircle(tint.copy(alpha = 0.26f), width * 0.024f, Offset(left + width * 0.12f, top + height * 0.09f))
        }
    }
}

private fun DrawScope.drawSignpostCharm(center: Offset, width: Float, height: Float, tint: Color) {
    drawRoundRect(
        color = tint,
        topLeft = Offset(center.x - width * 0.05f, center.y - height * 0.34f),
        size = Size(width * 0.1f, height * 0.7f),
        cornerRadius = CornerRadius(width * 0.04f, width * 0.04f)
    )
    val leftBoard = Path().apply {
        moveTo(center.x - width * 0.46f, center.y - height * 0.12f)
        lineTo(center.x - width * 0.08f, center.y - height * 0.12f)
        lineTo(center.x - width * 0.08f, center.y + height * 0.06f)
        lineTo(center.x - width * 0.46f, center.y + height * 0.06f)
        lineTo(center.x - width * 0.58f, center.y - height * 0.03f)
        close()
    }
    val rightBoard = Path().apply {
        moveTo(center.x + width * 0.08f, center.y - height * 0.32f)
        lineTo(center.x + width * 0.46f, center.y - height * 0.32f)
        lineTo(center.x + width * 0.58f, center.y - height * 0.23f)
        lineTo(center.x + width * 0.46f, center.y - height * 0.14f)
        lineTo(center.x + width * 0.08f, center.y - height * 0.14f)
        close()
    }
    drawPath(leftBoard, tint)
    drawPath(rightBoard, tint)
    drawCircle(CountryDecorCream, width * 0.04f, Offset(center.x - width * 0.34f, center.y - height * 0.03f))
    drawCircle(CountryDecorCream, width * 0.04f, Offset(center.x + width * 0.32f, center.y - height * 0.23f))
}

private fun DrawScope.drawArrowSign(center: Offset, width: Float, height: Float, tint: Color, direction: Int) {
    val left = center.x - width / 2f
    val right = center.x + width / 2f
    val top = center.y - height / 2f
    val bottom = center.y + height / 2f
    val board = Path().apply {
        if (direction < 0) {
            moveTo(left, center.y)
            lineTo(left + width * 0.2f, top)
            lineTo(right, top)
            lineTo(right, bottom)
            lineTo(left + width * 0.2f, bottom)
        } else {
            moveTo(right, center.y)
            lineTo(right - width * 0.2f, top)
            lineTo(left, top)
            lineTo(left, bottom)
            lineTo(right - width * 0.2f, bottom)
        }
        close()
    }
    drawPath(board, tint)
    drawRibbonArrowGlyph(center, height * 0.32f, CountryDecorCream, direction, false)
}

private fun DrawScope.drawCurtainCharm(center: Offset, width: Float, height: Float, tint: Color, closed: Boolean) {
    drawRoundRect(
        color = tint,
        topLeft = Offset(center.x - width / 2f, center.y - height / 2f),
        size = Size(width, height * 0.22f),
        cornerRadius = CornerRadius(width * 0.08f, width * 0.08f)
    )
    drawCountryBow(Offset(center.x, center.y - height * 0.42f), width * 0.18f, tint)
    if (closed) {
        drawRoundRect(
            color = tint,
            topLeft = Offset(center.x - width * 0.34f, center.y - height * 0.18f),
            size = Size(width * 0.68f, height * 0.56f),
            cornerRadius = CornerRadius(width * 0.08f, width * 0.08f)
        )
        drawRoundRect(
            color = CountryDecorCream,
            topLeft = Offset(center.x - width * 0.03f, center.y - height * 0.16f),
            size = Size(width * 0.06f, height * 0.52f),
            cornerRadius = CornerRadius(width * 0.03f, width * 0.03f)
        )
    } else {
        drawRoundRect(
            color = tint,
            topLeft = Offset(center.x - width * 0.34f, center.y - height * 0.18f),
            size = Size(width * 0.22f, height * 0.56f),
            cornerRadius = CornerRadius(width * 0.08f, width * 0.08f)
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(center.x + width * 0.12f, center.y - height * 0.18f),
            size = Size(width * 0.22f, height * 0.56f),
            cornerRadius = CornerRadius(width * 0.08f, width * 0.08f)
        )
        drawRoundRect(
            color = CountryDecorCream,
            topLeft = Offset(center.x - width * 0.14f, center.y - height * 0.08f),
            size = Size(width * 0.28f, height * 0.34f),
            cornerRadius = CornerRadius(width * 0.06f, width * 0.06f)
        )
    }
}

private fun DrawScope.drawPennant(center: Offset, width: Float, height: Float, tint: Color) {
    val pennant = Path().apply {
        moveTo(center.x - width / 2f, center.y - height / 2f)
        lineTo(center.x + width / 2f, center.y - height / 2f)
        lineTo(center.x + width / 2f, center.y + height * 0.2f)
        lineTo(center.x, center.y + height / 2f)
        lineTo(center.x - width / 2f, center.y + height * 0.2f)
        close()
    }
    drawPath(pennant, tint)
    drawCountryBow(Offset(center.x, center.y - height * 0.54f), width * 0.18f, tint)
}

private fun DrawScope.drawFilterPennants(center: Offset, width: Float, height: Float, tint: Color) {
    repeat(3) { index ->
        val itemWidth = width * (0.74f - index * 0.14f)
        val top = center.y - height * 0.34f + index * height * 0.22f
        val pennant = Path().apply {
            moveTo(center.x - itemWidth / 2f, top)
            lineTo(center.x + itemWidth / 2f, top)
            lineTo(center.x + itemWidth / 2f, top + height * 0.12f)
            lineTo(center.x, top + height * 0.22f)
            lineTo(center.x - itemWidth / 2f, top + height * 0.12f)
            close()
        }
        drawPath(pennant, tint.copy(alpha = 1f - index * 0.16f))
    }
}
private fun DrawScope.drawRibbonArrowGlyph(
    center: Offset,
    scale: Float,
    color: Color,
    direction: Int,
    vertical: Boolean
) {
    if (vertical) {
        val path = Path().apply {
            if (direction > 0) {
                moveTo(center.x - scale * 0.18f, center.y - scale * 0.34f)
                lineTo(center.x + scale * 0.18f, center.y - scale * 0.34f)
                lineTo(center.x + scale * 0.18f, center.y)
                lineTo(center.x + scale * 0.32f, center.y)
                lineTo(center.x, center.y + scale * 0.34f)
                lineTo(center.x - scale * 0.32f, center.y)
                lineTo(center.x - scale * 0.18f, center.y)
            } else {
                moveTo(center.x - scale * 0.18f, center.y + scale * 0.34f)
                lineTo(center.x + scale * 0.18f, center.y + scale * 0.34f)
                lineTo(center.x + scale * 0.18f, center.y)
                lineTo(center.x + scale * 0.32f, center.y)
                lineTo(center.x, center.y - scale * 0.34f)
                lineTo(center.x - scale * 0.32f, center.y)
                lineTo(center.x - scale * 0.18f, center.y)
            }
            close()
        }
        drawPath(path, color)
    } else {
        val path = Path().apply {
            if (direction < 0) {
                moveTo(center.x + scale * 0.34f, center.y - scale * 0.18f)
                lineTo(center.x + scale * 0.34f, center.y + scale * 0.18f)
                lineTo(center.x, center.y + scale * 0.18f)
                lineTo(center.x, center.y + scale * 0.32f)
                lineTo(center.x - scale * 0.34f, center.y)
                lineTo(center.x, center.y - scale * 0.32f)
                lineTo(center.x, center.y - scale * 0.18f)
            } else {
                moveTo(center.x - scale * 0.34f, center.y - scale * 0.18f)
                lineTo(center.x - scale * 0.34f, center.y + scale * 0.18f)
                lineTo(center.x, center.y + scale * 0.18f)
                lineTo(center.x, center.y + scale * 0.32f)
                lineTo(center.x + scale * 0.34f, center.y)
                lineTo(center.x, center.y - scale * 0.32f)
                lineTo(center.x, center.y - scale * 0.18f)
            }
            close()
        }
        drawPath(path, color)
    }
}

private fun DrawScope.drawOpenCornerArrow(center: Offset, size: Float, color: Color) {
    drawLine(
        color,
        Offset(center.x - size * 0.32f, center.y + size * 0.32f),
        Offset(center.x + size * 0.22f, center.y - size * 0.22f),
        size * 0.16f,
        StrokeCap.Round
    )
    drawLine(
        color,
        Offset(center.x - size * 0.08f, center.y - size * 0.22f),
        Offset(center.x + size * 0.22f, center.y - size * 0.22f),
        size * 0.16f,
        StrokeCap.Round
    )
    drawLine(
        color,
        Offset(center.x + size * 0.22f, center.y - size * 0.22f),
        Offset(center.x + size * 0.22f, center.y + size * 0.08f),
        size * 0.16f,
        StrokeCap.Round
    )
}

private fun DrawScope.drawPlusGlyph(center: Offset, size: Float, color: Color) {
    drawRoundRect(
        color,
        Offset(center.x - size * 0.08f, center.y - size * 0.28f),
        Size(size * 0.16f, size * 0.56f),
        CornerRadius(size * 0.08f, size * 0.08f)
    )
    drawRoundRect(
        color,
        Offset(center.x - size * 0.28f, center.y - size * 0.08f),
        Size(size * 0.56f, size * 0.16f),
        CornerRadius(size * 0.08f, size * 0.08f)
    )
}

private fun DrawScope.drawCrossGlyph(center: Offset, size: Float, color: Color) {
    rotate(45f, center) {
        drawRoundRect(
            color,
            Offset(center.x - size * 0.08f, center.y - size * 0.28f),
            Size(size * 0.16f, size * 0.56f),
            CornerRadius(size * 0.08f, size * 0.08f)
        )
        drawRoundRect(
            color,
            Offset(center.x - size * 0.28f, center.y - size * 0.08f),
            Size(size * 0.56f, size * 0.16f),
            CornerRadius(size * 0.08f, size * 0.08f)
        )
    }
}

private fun DrawScope.drawCheckGlyph(center: Offset, size: Float, color: Color) {
    drawLine(
        color,
        Offset(center.x - size * 0.28f, center.y + size * 0.02f),
        Offset(center.x - size * 0.06f, center.y + size * 0.24f),
        size * 0.16f,
        StrokeCap.Round
    )
    drawLine(
        color,
        Offset(center.x - size * 0.06f, center.y + size * 0.24f),
        Offset(center.x + size * 0.28f, center.y - size * 0.18f),
        size * 0.16f,
        StrokeCap.Round
    )
}

private fun DrawScope.drawInfoGlyph(center: Offset, size: Float, color: Color) {
    drawCircle(color, size * 0.08f, Offset(center.x, center.y - size * 0.22f))
    drawRoundRect(
        color,
        Offset(center.x - size * 0.08f, center.y - size * 0.02f),
        Size(size * 0.16f, size * 0.42f),
        CornerRadius(size * 0.08f, size * 0.08f)
    )
}
private fun DrawScope.drawExclamationGlyph(center: Offset, size: Float, color: Color) {
    drawRoundRect(
        color,
        Offset(center.x - size * 0.08f, center.y - size * 0.34f),
        Size(size * 0.16f, size * 0.48f),
        CornerRadius(size * 0.08f, size * 0.08f)
    )
    drawCircle(color, size * 0.08f, Offset(center.x, center.y + size * 0.24f))
}

private fun DrawScope.drawQuestionGlyph(center: Offset, size: Float, color: Color) {
    drawArc(
        color = color,
        startAngle = 205f,
        sweepAngle = 220f,
        useCenter = false,
        topLeft = Offset(center.x - size * 0.28f, center.y - size * 0.34f),
        size = Size(size * 0.56f, size * 0.48f),
        style = Stroke(width = size * 0.14f, cap = StrokeCap.Round)
    )
    drawRoundRect(
        color,
        Offset(center.x - size * 0.07f, center.y - size * 0.02f),
        Size(size * 0.14f, size * 0.2f),
        CornerRadius(size * 0.07f, size * 0.07f)
    )
    drawCircle(color, size * 0.08f, Offset(center.x, center.y + size * 0.28f))
}

private fun DrawScope.drawQuillGlyph(center: Offset, size: Float, color: Color) {
    val feather = Path().apply {
        moveTo(center.x - size * 0.12f, center.y + size * 0.28f)
        quadraticTo(center.x - size * 0.34f, center.y - size * 0.02f, center.x, center.y - size * 0.32f)
        quadraticTo(center.x + size * 0.12f, center.y - size * 0.08f, center.x + size * 0.08f, center.y + size * 0.18f)
        close()
    }
    drawPath(feather, color)
    rotate(35f, center) {
        drawRoundRect(
            color,
            Offset(center.x - size * 0.04f, center.y + size * 0.04f),
            Size(size * 0.08f, size * 0.34f),
            CornerRadius(size * 0.04f, size * 0.04f)
        )
    }
}
