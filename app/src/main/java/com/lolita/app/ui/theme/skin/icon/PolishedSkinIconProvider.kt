package com.lolita.app.ui.theme.skin.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** All providers share the same optical grid and complete icon coverage. */
internal class PolishedSkinIconProvider(style: SkinIconStyle) : SkinIconProvider {
    override val navigation: NavigationIcons = PolishedNavigationIcons(style)
    override val action: ActionIcons = PolishedActionIcons(style)
    override val content: ContentIcons = PolishedContentIcons(style)
    override val arrow: ArrowIcons = PolishedArrowIcons(style)
    override val status: StatusIcons = PolishedStatusIcons(style)
}

private class PolishedNavigationIcons(private val style: SkinIconStyle) : NavigationIcons {
    @Composable override fun Home(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.Home, style, modifier, tint)
    @Composable override fun Wishlist(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.Wishlist, style, modifier, tint)
    @Composable override fun Outfit(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.Outfit, style, modifier, tint)
    @Composable override fun Stats(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.Stats, style, modifier, tint)
    @Composable override fun Settings(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.Settings, style, modifier, tint)
}

private class PolishedActionIcons(private val style: SkinIconStyle) : ActionIcons {
    @Composable override fun Add(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.Add, style, modifier, tint)
    @Composable override fun Delete(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.Delete, style, modifier, tint)
    @Composable override fun Edit(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.Edit, style, modifier, tint)
    @Composable override fun Search(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.Search, style, modifier, tint)
    @Composable override fun Sort(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.Sort, style, modifier, tint)
    @Composable override fun Save(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.Save, style, modifier, tint)
    @Composable override fun Close(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.Close, style, modifier, tint)
    @Composable override fun Share(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.Share, style, modifier, tint)
    @Composable override fun FilterList(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.FilterList, style, modifier, tint)
    @Composable override fun MoreVert(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.MoreVert, style, modifier, tint)
    @Composable override fun ContentCopy(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.ContentCopy, style, modifier, tint)
    @Composable override fun Refresh(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.Refresh, style, modifier, tint)
    @Composable override fun ViewAgenda(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.ViewAgenda, style, modifier, tint)
    @Composable override fun GridView(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.GridView, style, modifier, tint)
    @Composable override fun Apps(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.Apps, style, modifier, tint)
    @Composable override fun Gallery(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.Gallery, style, modifier, tint)
}

private class PolishedContentIcons(private val style: SkinIconStyle) : ContentIcons {
    @Composable override fun Laboratory(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.Laboratory, style, modifier, tint)
    @Composable override fun Star(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.Star, style, modifier, tint)
    @Composable override fun StarBorder(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.StarBorder, style, modifier, tint)
    @Composable override fun Image(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.Image, style, modifier, tint)
    @Composable override fun Camera(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.Camera, style, modifier, tint)
    @Composable override fun AddPhoto(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.AddPhoto, style, modifier, tint)
    @Composable override fun Link(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.Link, style, modifier, tint)
    @Composable override fun LinkOff(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.LinkOff, style, modifier, tint)
    @Composable override fun Palette(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.Palette, style, modifier, tint)
    @Composable override fun FileOpen(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.FileOpen, style, modifier, tint)
    @Composable override fun CalendarMonth(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.CalendarMonth, style, modifier, tint)
    @Composable override fun Notifications(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.Notifications, style, modifier, tint)
    @Composable override fun AttachMoney(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.AttachMoney, style, modifier, tint)
    @Composable override fun Category(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.Category, style, modifier, tint)
    @Composable override fun Location(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.Location, style, modifier, tint)
}

private class PolishedArrowIcons(private val style: SkinIconStyle) : ArrowIcons {
    @Composable override fun ArrowBack(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.ArrowBack, style, modifier, tint)
    @Composable override fun ArrowForward(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.ArrowForward, style, modifier, tint)
    @Composable override fun KeyboardArrowLeft(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.KeyboardArrowLeft, style, modifier, tint)
    @Composable override fun KeyboardArrowRight(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.KeyboardArrowRight, style, modifier, tint)
    @Composable override fun ExpandMore(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.ExpandMore, style, modifier, tint)
    @Composable override fun ExpandLess(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.ExpandLess, style, modifier, tint)
    @Composable override fun ArrowDropDown(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.ArrowDropDown, style, modifier, tint)
    @Composable override fun SwapVert(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.SwapVert, style, modifier, tint)
    @Composable override fun OpenInNew(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.OpenInNew, style, modifier, tint)
}

private class PolishedStatusIcons(private val style: SkinIconStyle) : StatusIcons {
    @Composable override fun CheckCircle(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.CheckCircle, style, modifier, tint)
    @Composable override fun Warning(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.Warning, style, modifier, tint)
    @Composable override fun Error(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.Error, style, modifier, tint)
    @Composable override fun Info(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.Info, style, modifier, tint)
    @Composable override fun Visibility(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.Visibility, style, modifier, tint)
    @Composable override fun VisibilityOff(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.VisibilityOff, style, modifier, tint)
    @Composable override fun Help(modifier: Modifier, tint: Color) =
        PolishedSkinIcon(IconKey.Help, style, modifier, tint)
}
