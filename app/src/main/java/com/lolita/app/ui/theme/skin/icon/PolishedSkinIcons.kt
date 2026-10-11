package com.lolita.app.ui.theme.skin.icon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp

/** Shared optical grid. Skin personality belongs in the silhouette, not tiny badges. */
internal enum class SkinIconStyle {
    SWEET, GOTHIC, CHINESE, CLASSIC, NAVY, COUNTRY, VICTORIAN;

    val stroke: Stroke
        get() = Stroke(
            width = 1.7f,
            cap = if (this == GOTHIC) StrokeCap.Square else StrokeCap.Round,
            join = if (this == GOTHIC) StrokeJoin.Miter else StrokeJoin.Round
        )
}

/** Draw in logical 24×24 units; caller sizing wins and rectangular slots stay centered. */
@Composable
internal fun SkinIconCanvas(modifier: Modifier, draw: DrawScope.() -> Unit) {
    Canvas(modifier.size(24.dp)) {
        val side = size.minDimension
        if (side > 0f) {
            withTransform({
                translate((size.width - side) / 2f, (size.height - side) / 2f)
                scale(side / 24f, side / 24f, Offset.Zero)
            }, draw)
        }
    }
}

@Composable
internal fun PolishedSkinIcon(
    key: IconKey,
    style: SkinIconStyle,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    SkinIconCanvas(modifier) {
        when (key) {
            IconKey.Home, IconKey.Wishlist, IconKey.Outfit, IconKey.Stats, IconKey.Settings ->
                drawPolishedNavigationIcon(key, style, tint)
            IconKey.Star, IconKey.StarBorder, IconKey.Image, IconKey.Camera, IconKey.AddPhoto,
            IconKey.Link, IconKey.LinkOff, IconKey.Palette, IconKey.FileOpen,
            IconKey.CalendarMonth, IconKey.Notifications, IconKey.AttachMoney,
            IconKey.Category, IconKey.Location -> drawPolishedContentIcon(key, style, tint)
            IconKey.Add, IconKey.Delete, IconKey.Edit, IconKey.Search, IconKey.Sort,
            IconKey.Save, IconKey.Close, IconKey.Share, IconKey.FilterList, IconKey.MoreVert,
            IconKey.ContentCopy, IconKey.Refresh, IconKey.ViewAgenda, IconKey.GridView,
            IconKey.Apps, IconKey.Gallery, IconKey.ArrowBack, IconKey.ArrowForward,
            IconKey.KeyboardArrowLeft, IconKey.KeyboardArrowRight, IconKey.ExpandMore,
            IconKey.ExpandLess, IconKey.ArrowDropDown, IconKey.SwapVert, IconKey.OpenInNew,
            IconKey.CheckCircle, IconKey.Warning, IconKey.Error, IconKey.Info,
            IconKey.Visibility, IconKey.VisibilityOff, IconKey.Help ->
                drawPolishedControlIcon(key, style, tint)
        }
    }
}
