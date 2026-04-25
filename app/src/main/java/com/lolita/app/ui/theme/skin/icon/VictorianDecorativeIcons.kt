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

private val VictorianIvory = Color(0xFFF5E6D3)

internal enum class VictorianActionKind {
    Add, Delete, Edit, Search, Sort, Save, Close, Share,
    FilterList, MoreVert, ContentCopy, Refresh, ViewAgenda, GridView, Apps, Gallery
}

internal enum class VictorianContentKind {
    Star, StarBorder, Image, Camera, AddPhoto, Link, LinkOff,
    Palette, FileOpen, CalendarMonth, Notifications, AttachMoney, Category, Location
}

internal enum class VictorianArrowKind {
    ArrowBack, ArrowForward, KeyboardArrowLeft, KeyboardArrowRight,
    ExpandMore, ExpandLess, ArrowDropDown, SwapVert, OpenInNew
}

internal enum class VictorianStatusKind {
    CheckCircle, Warning, Error, Info, Visibility, VisibilityOff, Help
}

@Composable
internal fun VictorianNavIcon(
    key: IconKey,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    VictorianDecorCanvas(modifier) { s ->
        when (key) {
            IconKey.Home -> drawVictorianHomeCharm(s, tint)
            IconKey.Wishlist -> drawVictorianWishCharm(s, tint)
            IconKey.Outfit -> drawVictorianOutfitCharm(s, tint)
            IconKey.Stats -> drawVictorianStatsCharm(s, tint)
            IconKey.Settings -> drawVictorianSettingsCharm(s, tint)
            else -> {}
        }
    }
}

@Composable
internal fun VictorianActionDecorativeIcon(
    kind: VictorianActionKind,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    VictorianDecorCanvas(modifier) { s ->
        when (kind) {
            VictorianActionKind.Add -> {
                drawVictorianShield(Offset(s * 0.5f, s * 0.56f), s * 0.56f, s * 0.68f, tint)
                drawVictorianScrollwork(Offset(s * 0.22f, s * 0.2f), s * 0.14f, tint)
                drawVictorianScrollwork(Offset(s * 0.78f, s * 0.2f), s * 0.14f, tint)
                drawVictorianPlusGlyph(Offset(s * 0.5f, s * 0.58f), s * 0.24f, VictorianIvory)
            }
            VictorianActionKind.Delete -> {
                drawVictorianShield(Offset(s * 0.5f, s * 0.56f), s * 0.56f, s * 0.68f, tint)
                drawVictorianCrossGlyph(Offset(s * 0.5f, s * 0.58f), s * 0.22f, VictorianIvory)
            }
            VictorianActionKind.Edit -> {
                drawVictorianShield(Offset(s * 0.5f, s * 0.56f), s * 0.56f, s * 0.68f, tint)
                drawVictorianQuillGlyph(Offset(s * 0.52f, s * 0.58f), s * 0.26f, VictorianIvory)
            }
            VictorianActionKind.Search -> {
                drawVictorianShield(Offset(s * 0.5f, s * 0.56f), s * 0.56f, s * 0.68f, tint)
                drawCircle(VictorianIvory, s * 0.1f, Offset(s * 0.44f, s * 0.5f))
                drawLine(VictorianIvory, Offset(s * 0.52f, s * 0.58f), Offset(s * 0.62f, s * 0.68f), s * 0.05f, StrokeCap.Round)
            }
            VictorianActionKind.Sort -> {
                drawVictorianShield(Offset(s * 0.5f, s * 0.56f), s * 0.56f, s * 0.68f, tint)
                drawRoundRect(VictorianIvory, Offset(s * 0.26f, s * 0.4f), Size(s * 0.48f, s * 0.06f), CornerRadius(s * 0.03f, s * 0.03f))
                drawRoundRect(VictorianIvory, Offset(s * 0.32f, s * 0.52f), Size(s * 0.36f, s * 0.06f), CornerRadius(s * 0.03f, s * 0.03f))
                drawRoundRect(VictorianIvory, Offset(s * 0.38f, s * 0.64f), Size(s * 0.24f, s * 0.06f), CornerRadius(s * 0.03f, s * 0.03f))
            }
            VictorianActionKind.Save -> {
                drawVictorianShield(Offset(s * 0.5f, s * 0.56f), s * 0.56f, s * 0.68f, tint)
                drawVictorianScrollwork(Offset(s * 0.5f, s * 0.16f), s * 0.12f, tint)
                drawVictorianCheckGlyph(Offset(s * 0.5f, s * 0.58f), s * 0.22f, VictorianIvory)
            }
            VictorianActionKind.Close -> {
                drawVictorianShield(Offset(s * 0.5f, s * 0.56f), s * 0.56f, s * 0.68f, tint)
                drawVictorianScrollwork(Offset(s * 0.22f, s * 0.82f), s * 0.12f, tint)
                drawVictorianScrollwork(Offset(s * 0.78f, s * 0.82f), s * 0.12f, tint)
                drawVictorianCrossGlyph(Offset(s * 0.5f, s * 0.58f), s * 0.22f, VictorianIvory)
            }
            VictorianActionKind.Share -> {
                drawVictorianEnvelope(Offset(s * 0.5f, s * 0.56f), s * 0.66f, s * 0.48f, tint)
                drawLine(VictorianIvory, Offset(s * 0.56f, s * 0.42f), Offset(s * 0.7f, s * 0.32f), s * 0.05f, StrokeCap.Round)
                drawLine(VictorianIvory, Offset(s * 0.6f, s * 0.32f), Offset(s * 0.7f, s * 0.32f), s * 0.05f, StrokeCap.Round)
                drawLine(VictorianIvory, Offset(s * 0.7f, s * 0.32f), Offset(s * 0.7f, s * 0.42f), s * 0.05f, StrokeCap.Round)
            }
            VictorianActionKind.FilterList -> {
                drawVictorianShield(Offset(s * 0.5f, s * 0.56f), s * 0.56f, s * 0.68f, tint)
                val funnel = Path().apply {
                    moveTo(s * 0.28f, s * 0.38f)
                    lineTo(s * 0.72f, s * 0.38f)
                    lineTo(s * 0.56f, s * 0.56f)
                    lineTo(s * 0.56f, s * 0.72f)
                    lineTo(s * 0.44f, s * 0.78f)
                    lineTo(s * 0.44f, s * 0.56f)
                    close()
                }
                drawPath(funnel, VictorianIvory)
            }
            VictorianActionKind.MoreVert -> {
                drawVictorianShield(Offset(s * 0.5f, s * 0.56f), s * 0.56f, s * 0.68f, tint)
                drawCircle(VictorianIvory, s * 0.05f, Offset(s * 0.5f, s * 0.4f))
                drawCircle(VictorianIvory, s * 0.05f, Offset(s * 0.5f, s * 0.54f))
                drawCircle(VictorianIvory, s * 0.05f, Offset(s * 0.5f, s * 0.68f))
            }
            VictorianActionKind.ContentCopy -> {
                drawVictorianStackedCards(Offset(s * 0.5f, s * 0.58f), s * 0.54f, s * 0.6f, tint)
            }
            VictorianActionKind.Refresh -> {
                drawVictorianWreath(Offset(s * 0.5f, s * 0.54f), s * 0.28f, tint)
            }
            VictorianActionKind.ViewAgenda -> {
                drawVictorianShield(Offset(s * 0.5f, s * 0.56f), s * 0.56f, s * 0.68f, tint)
                drawRoundRect(VictorianIvory, Offset(s * 0.26f, s * 0.38f), Size(s * 0.48f, s * 0.08f), CornerRadius(s * 0.04f, s * 0.04f))
                drawRoundRect(VictorianIvory, Offset(s * 0.26f, s * 0.52f), Size(s * 0.48f, s * 0.08f), CornerRadius(s * 0.04f, s * 0.04f))
                drawRoundRect(VictorianIvory, Offset(s * 0.26f, s * 0.66f), Size(s * 0.48f, s * 0.08f), CornerRadius(s * 0.04f, s * 0.04f))
            }
            VictorianActionKind.GridView -> {
                drawVictorianCabinet(Offset(s * 0.5f, s * 0.58f), s * 0.62f, s * 0.56f, tint, 2)
            }
            VictorianActionKind.Apps -> {
                drawVictorianCabinet(Offset(s * 0.5f, s * 0.58f), s * 0.66f, s * 0.6f, tint, 3)
            }
            VictorianActionKind.Gallery -> {
                drawVictorianFrame(Offset(s * 0.5f, s * 0.56f), s * 0.68f, s * 0.52f, tint)
                drawCircle(tint.copy(alpha = 0.22f), s * 0.06f, Offset(s * 0.36f, s * 0.48f))
                val hill = Path().apply {
                    moveTo(s * 0.28f, s * 0.62f)
                    lineTo(s * 0.42f, s * 0.48f)
                    lineTo(s * 0.56f, s * 0.58f)
                    lineTo(s * 0.72f, s * 0.44f)
                    lineTo(s * 0.72f, s * 0.62f)
                    close()
                }
                drawPath(hill, tint.copy(alpha = 0.22f))
                drawVictorianScrollwork(Offset(s * 0.5f, s * 0.18f), s * 0.12f, tint)
            }
        }
    }
}

@Composable
internal fun VictorianContentDecorativeIcon(
    kind: VictorianContentKind,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    VictorianDecorCanvas(modifier) { s ->
        when (kind) {
            VictorianContentKind.Star -> {
                drawVictorianFrame(Offset(s * 0.5f, s * 0.56f), s * 0.68f, s * 0.52f, tint)
                drawVictorianStarGlyph(Offset(s * 0.5f, s * 0.56f), s * 0.28f, VictorianIvory)
            }
            VictorianContentKind.StarBorder -> {
                drawVictorianFrame(Offset(s * 0.5f, s * 0.56f), s * 0.68f, s * 0.52f, tint)
                drawVictorianHollowStarGlyph(Offset(s * 0.5f, s * 0.56f), s * 0.28f, VictorianIvory)
            }
            VictorianContentKind.Image -> {
                drawVictorianFrame(Offset(s * 0.5f, s * 0.56f), s * 0.68f, s * 0.52f, tint)
                drawCircle(tint.copy(alpha = 0.22f), s * 0.06f, Offset(s * 0.36f, s * 0.48f))
                val hill = Path().apply {
                    moveTo(s * 0.28f, s * 0.62f)
                    lineTo(s * 0.42f, s * 0.48f)
                    lineTo(s * 0.56f, s * 0.58f)
                    lineTo(s * 0.72f, s * 0.44f)
                    lineTo(s * 0.72f, s * 0.62f)
                    close()
                }
                drawPath(hill, tint.copy(alpha = 0.22f))
            }
            VictorianContentKind.Camera -> drawVictorianCamera(Offset(s * 0.5f, s * 0.56f), s * 0.66f, s * 0.46f, tint)
            VictorianContentKind.AddPhoto -> {
                drawVictorianCamera(Offset(s * 0.46f, s * 0.56f), s * 0.6f, s * 0.42f, tint)
                drawVictorianShield(Offset(s * 0.76f, s * 0.3f), s * 0.2f, s * 0.2f, tint)
                drawVictorianPlusGlyph(Offset(s * 0.76f, s * 0.3f), s * 0.12f, VictorianIvory)
            }
            VictorianContentKind.Link -> drawVictorianChain(Offset(s * 0.5f, s * 0.56f), s * 0.66f, s * 0.34f, tint, false)
            VictorianContentKind.LinkOff -> drawVictorianChain(Offset(s * 0.5f, s * 0.56f), s * 0.66f, s * 0.34f, tint, true)
            VictorianContentKind.Palette -> drawVictorianPalette(Offset(s * 0.52f, s * 0.62f), s * 0.68f, s * 0.5f, tint)
            VictorianContentKind.FileOpen -> {
                drawVictorianEnvelope(Offset(s * 0.5f, s * 0.58f), s * 0.68f, s * 0.52f, tint)
                drawLine(VictorianIvory, Offset(s * 0.5f, s * 0.42f), Offset(s * 0.5f, s * 0.28f), s * 0.05f, StrokeCap.Round)
                drawLine(VictorianIvory, Offset(s * 0.42f, s * 0.36f), Offset(s * 0.5f, s * 0.28f), s * 0.05f, StrokeCap.Round)
                drawLine(VictorianIvory, Offset(s * 0.58f, s * 0.36f), Offset(s * 0.5f, s * 0.28f), s * 0.05f, StrokeCap.Round)
            }
            VictorianContentKind.CalendarMonth -> drawVictorianCalendar(Offset(s * 0.5f, s * 0.58f), s * 0.66f, s * 0.56f, tint)
            VictorianContentKind.Notifications -> drawVictorianBell(Offset(s * 0.5f, s * 0.56f), s * 0.56f, s * 0.62f, tint)
            VictorianContentKind.AttachMoney -> drawVictorianPurse(Offset(s * 0.5f, s * 0.58f), s * 0.62f, s * 0.52f, tint)
            VictorianContentKind.Category -> drawVictorianCabinet(Offset(s * 0.5f, s * 0.58f), s * 0.68f, s * 0.56f, tint, 2)
            VictorianContentKind.Location -> drawVictorianSignpost(Offset(s * 0.5f, s * 0.58f), s * 0.62f, s * 0.62f, tint)
        }
    }
}

@Composable
internal fun VictorianArrowDecorativeIcon(
    kind: VictorianArrowKind,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    VictorianDecorCanvas(modifier) { s ->
        when (kind) {
            VictorianArrowKind.ArrowBack -> drawFeatherArrow(Offset(s * 0.5f, s * 0.54f), s * 0.72f, tint, -1, false)
            VictorianArrowKind.ArrowForward -> drawFeatherArrow(Offset(s * 0.5f, s * 0.54f), s * 0.72f, tint, 1, false)
            VictorianArrowKind.KeyboardArrowLeft -> drawFeatherArrow(Offset(s * 0.5f, s * 0.54f), s * 0.38f, tint, -1, false)
            VictorianArrowKind.KeyboardArrowRight -> drawFeatherArrow(Offset(s * 0.5f, s * 0.54f), s * 0.38f, tint, 1, false)
            VictorianArrowKind.ExpandMore -> {
                drawVictorianScrollwork(Offset(s * 0.22f, s * 0.14f), s * 0.12f, tint)
                drawVictorianScrollwork(Offset(s * 0.78f, s * 0.14f), s * 0.12f, tint)
                drawFeatherArrow(Offset(s * 0.5f, s * 0.58f), s * 0.32f, tint, 1, true)
            }
            VictorianArrowKind.ExpandLess -> {
                drawVictorianScrollwork(Offset(s * 0.22f, s * 0.82f), s * 0.12f, tint)
                drawVictorianScrollwork(Offset(s * 0.78f, s * 0.82f), s * 0.12f, tint)
                drawFeatherArrow(Offset(s * 0.5f, s * 0.44f), s * 0.32f, tint, -1, true)
            }
            VictorianArrowKind.ArrowDropDown -> drawFeatherArrow(Offset(s * 0.5f, s * 0.54f), s * 0.32f, tint, 1, true)
            VictorianArrowKind.SwapVert -> {
                drawFeatherArrow(Offset(s * 0.5f, s * 0.34f), s * 0.26f, tint, -1, true)
                drawFeatherArrow(Offset(s * 0.5f, s * 0.68f), s * 0.26f, tint, 1, true)
            }
            VictorianArrowKind.OpenInNew -> {
                drawVictorianFrame(Offset(s * 0.46f, s * 0.56f), s * 0.58f, s * 0.52f, tint)
                drawLine(VictorianIvory, Offset(s * 0.48f, s * 0.48f), Offset(s * 0.68f, s * 0.28f), s * 0.05f, StrokeCap.Round)
                drawLine(VictorianIvory, Offset(s * 0.54f, s * 0.28f), Offset(s * 0.68f, s * 0.28f), s * 0.05f, StrokeCap.Round)
                drawLine(VictorianIvory, Offset(s * 0.68f, s * 0.28f), Offset(s * 0.68f, s * 0.42f), s * 0.05f, StrokeCap.Round)
            }
        }
    }
}

@Composable
internal fun VictorianStatusDecorativeIcon(
    kind: VictorianStatusKind,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    VictorianDecorCanvas(modifier) { s ->
        when (kind) {
            VictorianStatusKind.CheckCircle -> {
                drawVictorianSeal(Offset(s * 0.5f, s * 0.5f), s * 0.36f, tint)
                drawVictorianCheckGlyph(Offset(s * 0.5f, s * 0.5f), s * 0.18f, VictorianIvory)
            }
            VictorianStatusKind.Warning -> {
                drawVictorianSeal(Offset(s * 0.5f, s * 0.5f), s * 0.36f, tint)
                drawVictorianExclamationGlyph(Offset(s * 0.5f, s * 0.5f), s * 0.18f, VictorianIvory)
            }
            VictorianStatusKind.Error -> {
                drawVictorianSeal(Offset(s * 0.5f, s * 0.5f), s * 0.36f, tint)
                drawVictorianCrossGlyph(Offset(s * 0.5f, s * 0.5f), s * 0.16f, VictorianIvory)
            }
            VictorianStatusKind.Info -> {
                drawVictorianSeal(Offset(s * 0.5f, s * 0.5f), s * 0.36f, tint)
                drawVictorianInfoGlyph(Offset(s * 0.5f, s * 0.5f), s * 0.18f, VictorianIvory)
            }
            VictorianStatusKind.Visibility -> drawVictorianCurtain(Offset(s * 0.5f, s * 0.56f), s * 0.7f, s * 0.56f, tint, false)
            VictorianStatusKind.VisibilityOff -> drawVictorianCurtain(Offset(s * 0.5f, s * 0.56f), s * 0.7f, s * 0.56f, tint, true)
            VictorianStatusKind.Help -> {
                drawVictorianSeal(Offset(s * 0.5f, s * 0.5f), s * 0.36f, tint)
                drawVictorianQuestionGlyph(Offset(s * 0.5f, s * 0.5f), s * 0.18f, VictorianIvory)
            }
        }
    }
}

@Composable
private fun VictorianDecorCanvas(
    modifier: Modifier,
    drawBlock: DrawScope.(Float) -> Unit
) {
    Canvas(Modifier.size(24.dp).then(modifier)) {
        drawBlock(size.minDimension)
    }
}

private fun DrawScope.drawVictorianScrollwork(center: Offset, scale: Float, tint: Color) {
    val scroll = Path().apply {
        moveTo(center.x - scale * 0.5f, center.y + scale * 0.2f)
        cubicTo(
            center.x - scale * 0.52f, center.y - scale * 0.3f,
            center.x + scale * 0.52f, center.y - scale * 0.3f,
            center.x + scale * 0.5f, center.y + scale * 0.2f
        )
        cubicTo(
            center.x + scale * 0.42f, center.y + scale * 0.48f,
            center.x - scale * 0.42f, center.y + scale * 0.48f,
            center.x - scale * 0.5f, center.y + scale * 0.2f
        )
        close()
    }
    drawPath(scroll, tint)
    drawCircle(VictorianIvory, scale * 0.12f, Offset(center.x, center.y))
    drawCircle(tint, scale * 0.06f, Offset(center.x - scale * 0.22f, center.y + scale * 0.1f))
    drawCircle(tint, scale * 0.06f, Offset(center.x + scale * 0.22f, center.y + scale * 0.1f))
}

private fun DrawScope.drawVictorianShield(center: Offset, width: Float, height: Float, tint: Color) {
    val shield = Path().apply {
        moveTo(center.x, center.y - height / 2f)
        quadraticTo(center.x + width / 2f, center.y - height / 2f, center.x + width / 2f, center.y - height * 0.18f)
        lineTo(center.x + width / 2f, center.y + height * 0.1f)
        quadraticTo(center.x + width / 2f, center.y + height * 0.42f, center.x, center.y + height / 2f)
        quadraticTo(center.x - width / 2f, center.y + height * 0.42f, center.x - width / 2f, center.y + height * 0.1f)
        lineTo(center.x - width / 2f, center.y - height * 0.18f)
        quadraticTo(center.x - width / 2f, center.y - height / 2f, center.x, center.y - height / 2f)
        close()
    }
    drawPath(shield, tint)
    drawPath(shield, tint.copy(alpha = 0.12f))
    drawLine(
        VictorianIvory.copy(alpha = 0.3f),
        Offset(center.x - width * 0.1f, center.y - height * 0.32f),
        Offset(center.x + width * 0.1f, center.y - height * 0.32f),
        width * 0.03f,
        StrokeCap.Round
    )
}

private fun DrawScope.drawVictorianFrame(center: Offset, width: Float, height: Float, tint: Color) {
    drawRoundRect(
        color = tint,
        topLeft = Offset(center.x - width / 2f, center.y - height / 2f),
        size = Size(width, height),
        cornerRadius = CornerRadius(width * 0.06f, width * 0.06f)
    )
    drawRoundRect(
        color = tint,
        topLeft = Offset(center.x - width / 2f - width * 0.04f, center.y - height / 2f - height * 0.04f),
        size = Size(width + width * 0.08f, height + height * 0.08f),
        cornerRadius = CornerRadius(width * 0.1f, width * 0.1f),
        style = Stroke(width = width * 0.04f)
    )
    val rosetteR = width * 0.06f
    drawCircle(tint, rosetteR, Offset(center.x - width * 0.38f, center.y - height * 0.38f))
    drawCircle(VictorianIvory, rosetteR * 0.5f, Offset(center.x - width * 0.38f, center.y - height * 0.38f))
    drawCircle(tint, rosetteR, Offset(center.x + width * 0.38f, center.y - height * 0.38f))
    drawCircle(VictorianIvory, rosetteR * 0.5f, Offset(center.x + width * 0.38f, center.y - height * 0.38f))
    drawCircle(tint, rosetteR, Offset(center.x - width * 0.38f, center.y + height * 0.38f))
    drawCircle(VictorianIvory, rosetteR * 0.5f, Offset(center.x - width * 0.38f, center.y + height * 0.38f))
    drawCircle(tint, rosetteR, Offset(center.x + width * 0.38f, center.y + height * 0.38f))
    drawCircle(VictorianIvory, rosetteR * 0.5f, Offset(center.x + width * 0.38f, center.y + height * 0.38f))
}

private fun DrawScope.drawVictorianSeal(center: Offset, radius: Float, tint: Color) {
    drawCircle(tint, radius, center)
    drawCircle(tint, radius * 1.06f, center, style = Stroke(width = radius * 0.08f))
    val dots = 16
    repeat(dots) { i ->
        val angle = i * 360f / dots
        rotate(angle, center) {
            drawCircle(tint, radius * 0.04f, Offset(center.x, center.y - radius * 0.92f))
        }
    }
    drawCircle(VictorianIvory, radius * 0.58f, center)
    drawCircle(tint.copy(alpha = 0.18f), radius * 0.48f, center)
}

private fun DrawScope.drawVictorianRibbon(center: Offset, width: Float, height: Float, tint: Color) {
    val ribbon = Path().apply {
        moveTo(center.x - width / 2f, center.y - height / 2f)
        lineTo(center.x + width / 2f, center.y - height / 2f)
        lineTo(center.x + width / 2f, center.y + height / 2f)
        lineTo(center.x + width * 0.3f, center.y + height * 0.2f)
        lineTo(center.x, center.y + height / 2f)
        lineTo(center.x - width * 0.3f, center.y + height * 0.2f)
        lineTo(center.x - width / 2f, center.y + height / 2f)
        close()
    }
    drawPath(ribbon, tint)
    drawRoundRect(
        color = VictorianIvory.copy(alpha = 0.3f),
        topLeft = Offset(center.x - width * 0.38f, center.y - height * 0.32f),
        size = Size(width * 0.76f, height * 0.12f),
        cornerRadius = CornerRadius(height * 0.06f, height * 0.06f)
    )
}

private fun DrawScope.drawFeatherArrow(center: Offset, size: Float, tint: Color, direction: Int, vertical: Boolean) {
    if (vertical) {
        val shaftLen = size * 0.4f
        val headLen = size * 0.3f
        val dy = if (direction > 0) 1 else -1
        val tipY = center.y + dy * (shaftLen / 2f + headLen)
        val baseY = center.y - dy * shaftLen / 2f
        drawLine(tint, Offset(center.x, baseY), Offset(center.x, tipY), size * 0.08f, StrokeCap.Round)
        val head = Path().apply {
            moveTo(center.x, tipY)
            lineTo(center.x - size * 0.16f, tipY - dy * size * 0.2f)
            lineTo(center.x + size * 0.16f, tipY - dy * size * 0.2f)
            close()
        }
        drawPath(head, tint)
        drawLine(VictorianIvory, Offset(center.x - size * 0.12f, baseY - dy * size * 0.04f), Offset(center.x - size * 0.04f, baseY + dy * size * 0.06f), size * 0.03f, StrokeCap.Round)
        drawLine(VictorianIvory, Offset(center.x + size * 0.12f, baseY - dy * size * 0.04f), Offset(center.x + size * 0.04f, baseY + dy * size * 0.06f), size * 0.03f, StrokeCap.Round)
    } else {
        val shaftLen = size * 0.4f
        val headLen = size * 0.3f
        val dx = if (direction > 0) 1 else -1
        val tipX = center.x + dx * (shaftLen / 2f + headLen)
        val baseX = center.x - dx * shaftLen / 2f
        drawLine(tint, Offset(baseX, center.y), Offset(tipX, center.y), size * 0.08f, StrokeCap.Round)
        val head = Path().apply {
            moveTo(tipX, center.y)
            lineTo(tipX - dx * size * 0.2f, center.y - size * 0.16f)
            lineTo(tipX - dx * size * 0.2f, center.y + size * 0.16f)
            close()
        }
        drawPath(head, tint)
        drawLine(VictorianIvory, Offset(baseX - dx * size * 0.04f, center.y - size * 0.12f), Offset(baseX + dx * size * 0.06f, center.y - size * 0.04f), size * 0.03f, StrokeCap.Round)
        drawLine(VictorianIvory, Offset(baseX - dx * size * 0.04f, center.y + size * 0.12f), Offset(baseX + dx * size * 0.06f, center.y + size * 0.04f), size * 0.03f, StrokeCap.Round)
    }
}

private fun DrawScope.drawVictorianPlusGlyph(center: Offset, size: Float, color: Color) {
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

private fun DrawScope.drawVictorianCrossGlyph(center: Offset, size: Float, color: Color) {
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

private fun DrawScope.drawVictorianCheckGlyph(center: Offset, size: Float, color: Color) {
    drawLine(
        color,
        Offset(center.x - size * 0.28f, center.y + size * 0.02f),
        Offset(center.x - size * 0.06f, center.y + size * 0.24f),
        size * 0.14f,
        StrokeCap.Round
    )
    drawLine(
        color,
        Offset(center.x - size * 0.06f, center.y + size * 0.24f),
        Offset(center.x + size * 0.28f, center.y - size * 0.18f),
        size * 0.14f,
        StrokeCap.Round
    )
}

private fun DrawScope.drawVictorianExclamationGlyph(center: Offset, size: Float, color: Color) {
    drawRoundRect(
        color,
        Offset(center.x - size * 0.08f, center.y - size * 0.34f),
        Size(size * 0.16f, size * 0.48f),
        CornerRadius(size * 0.08f, size * 0.08f)
    )
    drawCircle(color, size * 0.08f, Offset(center.x, center.y + size * 0.24f))
}

private fun DrawScope.drawVictorianInfoGlyph(center: Offset, size: Float, color: Color) {
    drawCircle(color, size * 0.08f, Offset(center.x, center.y - size * 0.22f))
    drawRoundRect(
        color,
        Offset(center.x - size * 0.08f, center.y - size * 0.02f),
        Size(size * 0.16f, size * 0.42f),
        CornerRadius(size * 0.08f, size * 0.08f)
    )
}

private fun DrawScope.drawVictorianQuestionGlyph(center: Offset, size: Float, color: Color) {
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

private fun DrawScope.drawVictorianQuillGlyph(center: Offset, size: Float, color: Color) {
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

private fun DrawScope.drawVictorianStarGlyph(center: Offset, size: Float, color: Color) {
    val star = Path().apply {
        val outerR = size * 0.5f
        val innerR = size * 0.2f
        for (i in 0 until 5) {
            val outerAngle = Math.toRadians(-90.0 + i * 72.0)
            val innerAngle = Math.toRadians(-90.0 + i * 72.0 + 36.0)
            lineTo(center.x + outerR * Math.cos(outerAngle).toFloat(), center.y + outerR * Math.sin(outerAngle).toFloat())
            lineTo(center.x + innerR * Math.cos(innerAngle).toFloat(), center.y + innerR * Math.sin(innerAngle).toFloat())
        }
        close()
    }
    drawPath(star, color)
}

private fun DrawScope.drawVictorianHollowStarGlyph(center: Offset, size: Float, color: Color) {
    val star = Path().apply {
        val outerR = size * 0.5f
        val innerR = size * 0.2f
        for (i in 0 until 5) {
            val outerAngle = Math.toRadians(-90.0 + i * 72.0)
            val innerAngle = Math.toRadians(-90.0 + i * 72.0 + 36.0)
            lineTo(center.x + outerR * Math.cos(outerAngle).toFloat(), center.y + outerR * Math.sin(outerAngle).toFloat())
            lineTo(center.x + innerR * Math.cos(innerAngle).toFloat(), center.y + innerR * Math.sin(innerAngle).toFloat())
        }
        close()
    }
    drawPath(star, color, style = Stroke(width = size * 0.08f, cap = StrokeCap.Round))
}

private fun DrawScope.drawVictorianEnvelope(center: Offset, width: Float, height: Float, tint: Color) {
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
    drawPath(letter, VictorianIvory)
    drawRoundRect(
        color = VictorianIvory,
        topLeft = Offset(center.x - width * 0.18f, center.y + height * 0.04f),
        size = Size(width * 0.36f, height * 0.06f),
        cornerRadius = CornerRadius(width * 0.03f, width * 0.03f)
    )
    drawVictorianScrollwork(Offset(center.x, center.y - height * 0.46f), width * 0.12f, tint)
}

private fun DrawScope.drawVictorianCalendar(center: Offset, width: Float, height: Float, tint: Color) {
    drawVictorianShield(center, width, height, tint)
    drawVictorianScrollwork(Offset(center.x, center.y - height * 0.54f), width * 0.16f, tint)
    drawRoundRect(
        color = VictorianIvory,
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

private fun DrawScope.drawVictorianBell(center: Offset, width: Float, height: Float, tint: Color) {
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
    drawCircle(VictorianIvory, width * 0.08f, Offset(center.x, center.y + height * 0.12f))
    drawVictorianScrollwork(Offset(center.x, center.y - height * 0.54f), width * 0.14f, tint)
}

private fun DrawScope.drawVictorianPurse(center: Offset, width: Float, height: Float, tint: Color) {
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
    drawCircle(VictorianIvory, width * 0.07f, Offset(center.x, center.y + height * 0.06f))
    drawVictorianScrollwork(Offset(center.x - width * 0.3f, center.y - height * 0.18f), width * 0.08f, tint)
    drawVictorianScrollwork(Offset(center.x + width * 0.3f, center.y - height * 0.18f), width * 0.08f, tint)
}

private fun DrawScope.drawVictorianCabinet(center: Offset, width: Float, height: Float, tint: Color, cells: Int) {
    drawVictorianShield(center, width, height, tint)
    val gap = width * 0.06f
    val cellSize = if (cells == 2) width * 0.22f else width * 0.15f
    val total = cells * cellSize + (cells - 1) * gap
    val startX = center.x - total / 2f
    val startY = center.y - total / 2f
    repeat(cells) { row ->
        repeat(cells) { col ->
            drawRoundRect(
                color = VictorianIvory,
                topLeft = Offset(startX + col * (cellSize + gap), startY + row * (cellSize + gap)),
                size = Size(cellSize, cellSize),
                cornerRadius = CornerRadius(cellSize * 0.26f, cellSize * 0.26f)
            )
            drawCircle(tint.copy(alpha = 0.26f), cellSize * 0.09f, Offset(startX + col * (cellSize + gap) + cellSize * 0.5f, startY + row * (cellSize + gap) + cellSize * 0.5f))
        }
    }
}

private fun DrawScope.drawVictorianSignpost(center: Offset, width: Float, height: Float, tint: Color) {
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
    drawVictorianScrollwork(Offset(center.x - width * 0.34f, center.y - height * 0.03f), width * 0.08f, tint)
    drawVictorianScrollwork(Offset(center.x + width * 0.32f, center.y - height * 0.23f), width * 0.08f, tint)
}

private fun DrawScope.drawVictorianCamera(center: Offset, width: Float, height: Float, tint: Color) {
    drawVictorianShield(center, width, height, tint)
    drawRoundRect(
        color = tint,
        topLeft = Offset(center.x - width * 0.26f, center.y - height * 0.72f),
        size = Size(width * 0.22f, height * 0.18f),
        cornerRadius = CornerRadius(width * 0.06f, width * 0.06f)
    )
    drawCircle(VictorianIvory, width * 0.18f, Offset(center.x, center.y))
    drawCircle(tint.copy(alpha = 0.22f), width * 0.08f, Offset(center.x, center.y))
    drawVictorianScrollwork(Offset(center.x + width * 0.24f, center.y - height * 0.42f), width * 0.1f, tint)
}

private fun DrawScope.drawVictorianChain(center: Offset, width: Float, height: Float, tint: Color, broken: Boolean) {
    drawOval(
        color = tint,
        topLeft = Offset(center.x - width * 0.34f, center.y - height / 2f),
        size = Size(width * 0.36f, height)
    )
    drawOval(
        color = VictorianIvory,
        topLeft = Offset(center.x - width * 0.26f, center.y - height * 0.32f),
        size = Size(width * 0.2f, height * 0.64f)
    )
    drawOval(
        color = tint,
        topLeft = Offset(center.x - width * 0.02f, center.y - height / 2f),
        size = Size(width * 0.36f, height)
    )
    drawOval(
        color = VictorianIvory,
        topLeft = Offset(center.x + width * 0.06f, center.y - height * 0.32f),
        size = Size(width * 0.2f, height * 0.64f)
    )
    if (broken) {
        drawVictorianCrossGlyph(Offset(center.x, center.y), width * 0.18f, tint)
    }
}

private fun DrawScope.drawVictorianPalette(center: Offset, width: Float, height: Float, tint: Color) {
    drawVictorianFrame(center, width, height, tint)
    val angles = listOf(-18f, 0f, 18f)
    angles.forEachIndexed { index, angle ->
        rotate(angle, center) {
            drawRoundRect(
                color = if (index == 1) VictorianIvory else VictorianIvory.copy(alpha = 0.6f),
                topLeft = Offset(center.x - width * 0.16f, center.y - height * 0.34f),
                size = Size(width * 0.28f, height * 0.62f),
                cornerRadius = CornerRadius(width * 0.06f, width * 0.06f)
            )
        }
    }
    drawCircle(tint, width * 0.07f, Offset(center.x, center.y + height * 0.22f))
}

private fun DrawScope.drawVictorianStackedCards(center: Offset, width: Float, height: Float, tint: Color) {
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
        color = VictorianIvory,
        topLeft = Offset(center.x - width * 0.26f, center.y - height * 0.08f),
        size = Size(width * 0.52f, height * 0.08f),
        cornerRadius = CornerRadius(width * 0.04f, width * 0.04f)
    )
    drawVictorianScrollwork(Offset(center.x - width * 0.34f, center.y - height * 0.12f), width * 0.06f, tint)
}

private fun DrawScope.drawVictorianWreath(center: Offset, radius: Float, tint: Color) {
    drawCircle(tint, radius, center)
    drawCircle(VictorianIvory, radius * 0.58f, center)
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
    drawVictorianScrollwork(Offset(center.x, center.y - radius * 0.52f), radius * 0.14f, tint)
}

private fun DrawScope.drawVictorianCurtain(center: Offset, width: Float, height: Float, tint: Color, closed: Boolean) {
    drawVictorianRibbon(Offset(center.x, center.y - height * 0.28f), width * 0.8f, height * 0.2f, tint)
    if (closed) {
        drawRoundRect(
            color = tint,
            topLeft = Offset(center.x - width * 0.34f, center.y - height * 0.18f),
            size = Size(width * 0.68f, height * 0.56f),
            cornerRadius = CornerRadius(width * 0.08f, width * 0.08f)
        )
        drawRoundRect(
            color = VictorianIvory,
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
            color = VictorianIvory,
            topLeft = Offset(center.x - width * 0.14f, center.y - height * 0.08f),
            size = Size(width * 0.28f, height * 0.34f),
            cornerRadius = CornerRadius(width * 0.06f, width * 0.06f)
        )
    }
}

private fun DrawScope.drawVictorianHomeCharm(s: Float, tint: Color) {
    drawVictorianRibbon(Offset(s * 0.5f, s * 0.14f), s * 0.56f, s * 0.18f, tint)
    val manor = Path().apply {
        moveTo(s * 0.14f, s * 0.42f)
        lineTo(s * 0.14f, s * 0.82f)
        quadraticTo(s * 0.14f, s * 0.9f, s * 0.24f, s * 0.88f)
        lineTo(s * 0.76f, s * 0.88f)
        quadraticTo(s * 0.86f, s * 0.9f, s * 0.86f, s * 0.82f)
        lineTo(s * 0.86f, s * 0.42f)
        close()
    }
    drawPath(manor, tint)
    val roof = Path().apply {
        moveTo(s * 0.08f, s * 0.44f)
        lineTo(s * 0.5f, s * 0.22f)
        lineTo(s * 0.92f, s * 0.44f)
        close()
    }
    drawPath(roof, tint)
    drawRoundRect(
        color = VictorianIvory,
        topLeft = Offset(s * 0.26f, s * 0.66f),
        size = Size(s * 0.48f, s * 0.1f),
        cornerRadius = CornerRadius(s * 0.05f, s * 0.05f)
    )
    drawRoundRect(
        color = VictorianIvory,
        topLeft = Offset(s * 0.42f, s * 0.44f),
        size = Size(s * 0.16f, s * 0.22f),
        cornerRadius = CornerRadius(s * 0.04f, s * 0.04f)
    )
}

private fun DrawScope.drawVictorianWishCharm(s: Float, tint: Color) {
    drawVictorianRibbon(Offset(s * 0.5f, s * 0.14f), s * 0.56f, s * 0.18f, tint)
    val heart = Path().apply {
        moveTo(s * 0.5f, s * 0.82f)
        cubicTo(s * 0.14f, s * 0.58f, s * 0.14f, s * 0.32f, s * 0.34f, s * 0.32f)
        cubicTo(s * 0.44f, s * 0.32f, s * 0.5f, s * 0.4f, s * 0.5f, s * 0.48f)
        cubicTo(s * 0.5f, s * 0.4f, s * 0.56f, s * 0.32f, s * 0.66f, s * 0.32f)
        cubicTo(s * 0.86f, s * 0.32f, s * 0.86f, s * 0.58f, s * 0.5f, s * 0.82f)
        close()
    }
    drawPath(heart, tint)
    drawCircle(VictorianIvory, s * 0.06f, Offset(s * 0.5f, s * 0.56f))
    drawVictorianScrollwork(Offset(s * 0.3f, s * 0.36f), s * 0.08f, tint)
    drawVictorianScrollwork(Offset(s * 0.7f, s * 0.36f), s * 0.08f, tint)
}

private fun DrawScope.drawVictorianOutfitCharm(s: Float, tint: Color) {
    drawVictorianRibbon(Offset(s * 0.5f, s * 0.12f), s * 0.56f, s * 0.18f, tint)
    val corset = Path().apply {
        moveTo(s * 0.32f, s * 0.24f)
        quadraticTo(s * 0.5f, s * 0.18f, s * 0.68f, s * 0.24f)
        lineTo(s * 0.72f, s * 0.4f)
        lineTo(s * 0.76f, s * 0.5f)
        lineTo(s * 0.72f, s * 0.6f)
        lineTo(s * 0.68f, s * 0.72f)
        quadraticTo(s * 0.5f, s * 0.78f, s * 0.32f, s * 0.72f)
        lineTo(s * 0.28f, s * 0.6f)
        lineTo(s * 0.24f, s * 0.5f)
        lineTo(s * 0.28f, s * 0.4f)
        close()
    }
    drawPath(corset, tint)
    val skirt = Path().apply {
        moveTo(s * 0.28f, s * 0.68f)
        lineTo(s * 0.72f, s * 0.68f)
        lineTo(s * 0.82f, s * 0.92f)
        lineTo(s * 0.18f, s * 0.92f)
        close()
    }
    drawPath(skirt, tint)
    drawLine(VictorianIvory, Offset(s * 0.5f, s * 0.32f), Offset(s * 0.5f, s * 0.68f), s * 0.03f, StrokeCap.Round)
    drawVictorianScrollwork(Offset(s * 0.5f, s * 0.88f), s * 0.1f, tint)
}

private fun DrawScope.drawVictorianStatsCharm(s: Float, tint: Color) {
    drawVictorianRibbon(Offset(s * 0.5f, s * 0.12f), s * 0.56f, s * 0.18f, tint)
    val center = Offset(s * 0.5f, s * 0.54f)
    repeat(8) { index ->
        rotate(index * 45f, center) {
            drawCircle(tint, s * 0.09f, Offset(center.x, center.y - s * 0.18f))
        }
    }
    val leftRibbon = Path().apply {
        moveTo(s * 0.4f, s * 0.72f)
        lineTo(s * 0.27f, s * 0.96f)
        lineTo(s * 0.47f, s * 0.82f)
        close()
    }
    val rightRibbon = Path().apply {
        moveTo(s * 0.6f, s * 0.72f)
        lineTo(s * 0.73f, s * 0.96f)
        lineTo(s * 0.53f, s * 0.82f)
        close()
    }
    drawPath(leftRibbon, tint)
    drawPath(rightRibbon, tint)
    drawCircle(tint, s * 0.18f, center)
    drawCircle(VictorianIvory, s * 0.1f, center)
    drawRoundRect(
        color = tint.copy(alpha = 0.24f),
        topLeft = Offset(s * 0.41f, s * 0.52f),
        size = Size(s * 0.04f, s * 0.12f),
        cornerRadius = CornerRadius(s * 0.02f, s * 0.02f)
    )
    drawRoundRect(
        color = tint.copy(alpha = 0.24f),
        topLeft = Offset(s * 0.48f, s * 0.48f),
        size = Size(s * 0.04f, s * 0.16f),
        cornerRadius = CornerRadius(s * 0.02f, s * 0.02f)
    )
    drawRoundRect(
        color = tint.copy(alpha = 0.24f),
        topLeft = Offset(s * 0.55f, s * 0.44f),
        size = Size(s * 0.04f, s * 0.2f),
        cornerRadius = CornerRadius(s * 0.02f, s * 0.02f)
    )
}

private fun DrawScope.drawVictorianSettingsCharm(s: Float, tint: Color) {
    drawVictorianRibbon(Offset(s * 0.5f, s * 0.12f), s * 0.56f, s * 0.18f, tint)
    val gear = Path().apply {
        val cx = s * 0.5f
        val cy = s * 0.56f
        val outerR = s * 0.3f
        val innerR = s * 0.18f
        val teeth = 8
        for (i in 0 until teeth) {
            val angle1 = Math.toRadians(-90.0 + i * 360.0 / teeth)
            val angle2 = Math.toRadians(-90.0 + (i + 0.35) * 360.0 / teeth)
            val angle3 = Math.toRadians(-90.0 + (i + 0.5) * 360.0 / teeth)
            val angle4 = Math.toRadians(-90.0 + (i + 0.85) * 360.0 / teeth)
            lineTo(cx + outerR * Math.cos(angle2).toFloat(), cy + outerR * Math.sin(angle2).toFloat())
            lineTo(cx + outerR * Math.cos(angle4).toFloat(), cy + outerR * Math.sin(angle4).toFloat())
            val angle5 = Math.toRadians(-90.0 + (i + 1.0) * 360.0 / teeth)
            lineTo(cx + innerR * Math.cos(angle5).toFloat(), cy + innerR * Math.sin(angle5).toFloat())
            val angle6 = Math.toRadians(-90.0 + (i + 0.5) * 360.0 / teeth)
            lineTo(cx + innerR * Math.cos(angle6).toFloat(), cy + innerR * Math.sin(angle6).toFloat())
        }
        close()
    }
    drawPath(gear, tint)
    drawCircle(VictorianIvory, s * 0.08f, Offset(s * 0.5f, s * 0.56f))
    drawVictorianScrollwork(Offset(s * 0.22f, s * 0.32f), s * 0.06f, tint)
    drawVictorianScrollwork(Offset(s * 0.78f, s * 0.32f), s * 0.06f, tint)
    drawVictorianScrollwork(Offset(s * 0.22f, s * 0.8f), s * 0.06f, tint)
    drawVictorianScrollwork(Offset(s * 0.78f, s * 0.8f), s * 0.06f, tint)
}
