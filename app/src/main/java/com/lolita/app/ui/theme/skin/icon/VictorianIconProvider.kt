package com.lolita.app.ui.theme.skin.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private class VictorianNavigationIcons : BaseNavigationIcons() {
    @Composable
    override fun Home(modifier: Modifier, tint: Color) {
        VictorianNavIcon(IconKey.Home, modifier, tint)
    }

    @Composable
    override fun Wishlist(modifier: Modifier, tint: Color) {
        VictorianNavIcon(IconKey.Wishlist, modifier, tint)
    }

    @Composable
    override fun Outfit(modifier: Modifier, tint: Color) {
        VictorianNavIcon(IconKey.Outfit, modifier, tint)
    }

    @Composable
    override fun Stats(modifier: Modifier, tint: Color) {
        VictorianNavIcon(IconKey.Stats, modifier, tint)
    }

    @Composable
    override fun Settings(modifier: Modifier, tint: Color) {
        VictorianNavIcon(IconKey.Settings, modifier, tint)
    }
}

private class VictorianActionIcons : BaseActionIcons() {
    @Composable override fun Add(modifier: Modifier, tint: Color) = VictorianActionDecorativeIcon(VictorianActionKind.Add, modifier, tint)
    @Composable override fun Delete(modifier: Modifier, tint: Color) = VictorianActionDecorativeIcon(VictorianActionKind.Delete, modifier, tint)
    @Composable override fun Edit(modifier: Modifier, tint: Color) = VictorianActionDecorativeIcon(VictorianActionKind.Edit, modifier, tint)
    @Composable override fun Search(modifier: Modifier, tint: Color) = VictorianActionDecorativeIcon(VictorianActionKind.Search, modifier, tint)
    @Composable override fun Sort(modifier: Modifier, tint: Color) = VictorianActionDecorativeIcon(VictorianActionKind.Sort, modifier, tint)
    @Composable override fun Save(modifier: Modifier, tint: Color) = VictorianActionDecorativeIcon(VictorianActionKind.Save, modifier, tint)
    @Composable override fun Close(modifier: Modifier, tint: Color) = VictorianActionDecorativeIcon(VictorianActionKind.Close, modifier, tint)
    @Composable override fun Share(modifier: Modifier, tint: Color) = VictorianActionDecorativeIcon(VictorianActionKind.Share, modifier, tint)
    @Composable override fun FilterList(modifier: Modifier, tint: Color) = VictorianActionDecorativeIcon(VictorianActionKind.FilterList, modifier, tint)
    @Composable override fun MoreVert(modifier: Modifier, tint: Color) = VictorianActionDecorativeIcon(VictorianActionKind.MoreVert, modifier, tint)
    @Composable override fun ContentCopy(modifier: Modifier, tint: Color) = VictorianActionDecorativeIcon(VictorianActionKind.ContentCopy, modifier, tint)
    @Composable override fun Refresh(modifier: Modifier, tint: Color) = VictorianActionDecorativeIcon(VictorianActionKind.Refresh, modifier, tint)
    @Composable override fun ViewAgenda(modifier: Modifier, tint: Color) = VictorianActionDecorativeIcon(VictorianActionKind.ViewAgenda, modifier, tint)
    @Composable override fun GridView(modifier: Modifier, tint: Color) = VictorianActionDecorativeIcon(VictorianActionKind.GridView, modifier, tint)
    @Composable override fun Apps(modifier: Modifier, tint: Color) = VictorianActionDecorativeIcon(VictorianActionKind.Apps, modifier, tint)
    @Composable override fun Gallery(modifier: Modifier, tint: Color) = VictorianActionDecorativeIcon(VictorianActionKind.Gallery, modifier, tint)
}

private class VictorianContentIcons : BaseContentIcons() {
    @Composable override fun Star(modifier: Modifier, tint: Color) = VictorianContentDecorativeIcon(VictorianContentKind.Star, modifier, tint)
    @Composable override fun StarBorder(modifier: Modifier, tint: Color) = VictorianContentDecorativeIcon(VictorianContentKind.StarBorder, modifier, tint)
    @Composable override fun Image(modifier: Modifier, tint: Color) = VictorianContentDecorativeIcon(VictorianContentKind.Image, modifier, tint)
    @Composable override fun Camera(modifier: Modifier, tint: Color) = VictorianContentDecorativeIcon(VictorianContentKind.Camera, modifier, tint)
    @Composable override fun AddPhoto(modifier: Modifier, tint: Color) = VictorianContentDecorativeIcon(VictorianContentKind.AddPhoto, modifier, tint)
    @Composable override fun Link(modifier: Modifier, tint: Color) = VictorianContentDecorativeIcon(VictorianContentKind.Link, modifier, tint)
    @Composable override fun LinkOff(modifier: Modifier, tint: Color) = VictorianContentDecorativeIcon(VictorianContentKind.LinkOff, modifier, tint)
    @Composable override fun Palette(modifier: Modifier, tint: Color) = VictorianContentDecorativeIcon(VictorianContentKind.Palette, modifier, tint)
    @Composable override fun FileOpen(modifier: Modifier, tint: Color) = VictorianContentDecorativeIcon(VictorianContentKind.FileOpen, modifier, tint)
    @Composable override fun CalendarMonth(modifier: Modifier, tint: Color) = VictorianContentDecorativeIcon(VictorianContentKind.CalendarMonth, modifier, tint)
    @Composable override fun Notifications(modifier: Modifier, tint: Color) = VictorianContentDecorativeIcon(VictorianContentKind.Notifications, modifier, tint)
    @Composable override fun AttachMoney(modifier: Modifier, tint: Color) = VictorianContentDecorativeIcon(VictorianContentKind.AttachMoney, modifier, tint)
    @Composable override fun Category(modifier: Modifier, tint: Color) = VictorianContentDecorativeIcon(VictorianContentKind.Category, modifier, tint)
    @Composable override fun Location(modifier: Modifier, tint: Color) = VictorianContentDecorativeIcon(VictorianContentKind.Location, modifier, tint)
}

private class VictorianArrowIcons : BaseArrowIcons() {
    @Composable override fun ArrowBack(modifier: Modifier, tint: Color) = VictorianArrowDecorativeIcon(VictorianArrowKind.ArrowBack, modifier, tint)
    @Composable override fun ArrowForward(modifier: Modifier, tint: Color) = VictorianArrowDecorativeIcon(VictorianArrowKind.ArrowForward, modifier, tint)
    @Composable override fun KeyboardArrowLeft(modifier: Modifier, tint: Color) = VictorianArrowDecorativeIcon(VictorianArrowKind.KeyboardArrowLeft, modifier, tint)
    @Composable override fun KeyboardArrowRight(modifier: Modifier, tint: Color) = VictorianArrowDecorativeIcon(VictorianArrowKind.KeyboardArrowRight, modifier, tint)
    @Composable override fun ExpandMore(modifier: Modifier, tint: Color) = VictorianArrowDecorativeIcon(VictorianArrowKind.ExpandMore, modifier, tint)
    @Composable override fun ExpandLess(modifier: Modifier, tint: Color) = VictorianArrowDecorativeIcon(VictorianArrowKind.ExpandLess, modifier, tint)
    @Composable override fun ArrowDropDown(modifier: Modifier, tint: Color) = VictorianArrowDecorativeIcon(VictorianArrowKind.ArrowDropDown, modifier, tint)
    @Composable override fun SwapVert(modifier: Modifier, tint: Color) = VictorianArrowDecorativeIcon(VictorianArrowKind.SwapVert, modifier, tint)
    @Composable override fun OpenInNew(modifier: Modifier, tint: Color) = VictorianArrowDecorativeIcon(VictorianArrowKind.OpenInNew, modifier, tint)
}

private class VictorianStatusIcons : BaseStatusIcons() {
    @Composable override fun CheckCircle(modifier: Modifier, tint: Color) = VictorianStatusDecorativeIcon(VictorianStatusKind.CheckCircle, modifier, tint)
    @Composable override fun Warning(modifier: Modifier, tint: Color) = VictorianStatusDecorativeIcon(VictorianStatusKind.Warning, modifier, tint)
    @Composable override fun Error(modifier: Modifier, tint: Color) = VictorianStatusDecorativeIcon(VictorianStatusKind.Error, modifier, tint)
    @Composable override fun Info(modifier: Modifier, tint: Color) = VictorianStatusDecorativeIcon(VictorianStatusKind.Info, modifier, tint)
    @Composable override fun Visibility(modifier: Modifier, tint: Color) = VictorianStatusDecorativeIcon(VictorianStatusKind.Visibility, modifier, tint)
    @Composable override fun VisibilityOff(modifier: Modifier, tint: Color) = VictorianStatusDecorativeIcon(VictorianStatusKind.VisibilityOff, modifier, tint)
    @Composable override fun Help(modifier: Modifier, tint: Color) = VictorianStatusDecorativeIcon(VictorianStatusKind.Help, modifier, tint)
}

class VictorianIconProvider : BaseSkinIconProvider() {
    override val navigation: NavigationIcons = VictorianNavigationIcons()
    override val action: ActionIcons = VictorianActionIcons()
    override val content: ContentIcons = VictorianContentIcons()
    override val arrow: ArrowIcons = VictorianArrowIcons()
    override val status: StatusIcons = VictorianStatusIcons()
}
