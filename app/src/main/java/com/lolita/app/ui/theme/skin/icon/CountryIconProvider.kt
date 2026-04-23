package com.lolita.app.ui.theme.skin.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private class CountryNavigationIcons : BaseNavigationIcons() {
    @Composable
    override fun Home(modifier: Modifier, tint: Color) {
        CountryBottomNavIcon(IconKey.Home, modifier, tint)
    }

    @Composable
    override fun Wishlist(modifier: Modifier, tint: Color) {
        CountryBottomNavIcon(IconKey.Wishlist, modifier, tint)
    }

    @Composable
    override fun Outfit(modifier: Modifier, tint: Color) {
        CountryBottomNavIcon(IconKey.Outfit, modifier, tint)
    }

    @Composable
    override fun Stats(modifier: Modifier, tint: Color) {
        CountryBottomNavIcon(IconKey.Stats, modifier, tint)
    }

    @Composable
    override fun Settings(modifier: Modifier, tint: Color) {
        CountryBottomNavIcon(IconKey.Settings, modifier, tint)
    }
}

private class CountryActionIcons : BaseActionIcons() {
    @Composable override fun Add(modifier: Modifier, tint: Color) = CountryActionDecorativeIcon(CountryActionKind.Add, modifier, tint)
    @Composable override fun Delete(modifier: Modifier, tint: Color) = CountryActionDecorativeIcon(CountryActionKind.Delete, modifier, tint)
    @Composable override fun Edit(modifier: Modifier, tint: Color) = CountryActionDecorativeIcon(CountryActionKind.Edit, modifier, tint)
    @Composable override fun Search(modifier: Modifier, tint: Color) = CountryActionDecorativeIcon(CountryActionKind.Search, modifier, tint)
    @Composable override fun Sort(modifier: Modifier, tint: Color) = CountryActionDecorativeIcon(CountryActionKind.Sort, modifier, tint)
    @Composable override fun Save(modifier: Modifier, tint: Color) = CountryActionDecorativeIcon(CountryActionKind.Save, modifier, tint)
    @Composable override fun Close(modifier: Modifier, tint: Color) = CountryActionDecorativeIcon(CountryActionKind.Close, modifier, tint)
    @Composable override fun Share(modifier: Modifier, tint: Color) = CountryActionDecorativeIcon(CountryActionKind.Share, modifier, tint)
    @Composable override fun FilterList(modifier: Modifier, tint: Color) = CountryActionDecorativeIcon(CountryActionKind.FilterList, modifier, tint)
    @Composable override fun MoreVert(modifier: Modifier, tint: Color) = CountryActionDecorativeIcon(CountryActionKind.MoreVert, modifier, tint)
    @Composable override fun ContentCopy(modifier: Modifier, tint: Color) = CountryActionDecorativeIcon(CountryActionKind.ContentCopy, modifier, tint)
    @Composable override fun Refresh(modifier: Modifier, tint: Color) = CountryActionDecorativeIcon(CountryActionKind.Refresh, modifier, tint)
    @Composable override fun ViewAgenda(modifier: Modifier, tint: Color) = CountryActionDecorativeIcon(CountryActionKind.ViewAgenda, modifier, tint)
    @Composable override fun GridView(modifier: Modifier, tint: Color) = CountryActionDecorativeIcon(CountryActionKind.GridView, modifier, tint)
    @Composable override fun Apps(modifier: Modifier, tint: Color) = CountryActionDecorativeIcon(CountryActionKind.Apps, modifier, tint)
    @Composable override fun Gallery(modifier: Modifier, tint: Color) = CountryActionDecorativeIcon(CountryActionKind.Gallery, modifier, tint)
}

private class CountryContentIcons : BaseContentIcons() {
    @Composable override fun Star(modifier: Modifier, tint: Color) = CountryContentDecorativeIcon(CountryContentKind.Star, modifier, tint)
    @Composable override fun StarBorder(modifier: Modifier, tint: Color) = CountryContentDecorativeIcon(CountryContentKind.StarBorder, modifier, tint)
    @Composable override fun Image(modifier: Modifier, tint: Color) = CountryContentDecorativeIcon(CountryContentKind.Image, modifier, tint)
    @Composable override fun Camera(modifier: Modifier, tint: Color) = CountryContentDecorativeIcon(CountryContentKind.Camera, modifier, tint)
    @Composable override fun AddPhoto(modifier: Modifier, tint: Color) = CountryContentDecorativeIcon(CountryContentKind.AddPhoto, modifier, tint)
    @Composable override fun Link(modifier: Modifier, tint: Color) = CountryContentDecorativeIcon(CountryContentKind.Link, modifier, tint)
    @Composable override fun LinkOff(modifier: Modifier, tint: Color) = CountryContentDecorativeIcon(CountryContentKind.LinkOff, modifier, tint)
    @Composable override fun Palette(modifier: Modifier, tint: Color) = CountryContentDecorativeIcon(CountryContentKind.Palette, modifier, tint)
    @Composable override fun FileOpen(modifier: Modifier, tint: Color) = CountryContentDecorativeIcon(CountryContentKind.FileOpen, modifier, tint)
    @Composable override fun CalendarMonth(modifier: Modifier, tint: Color) = CountryContentDecorativeIcon(CountryContentKind.CalendarMonth, modifier, tint)
    @Composable override fun Notifications(modifier: Modifier, tint: Color) = CountryContentDecorativeIcon(CountryContentKind.Notifications, modifier, tint)
    @Composable override fun AttachMoney(modifier: Modifier, tint: Color) = CountryContentDecorativeIcon(CountryContentKind.AttachMoney, modifier, tint)
    @Composable override fun Category(modifier: Modifier, tint: Color) = CountryContentDecorativeIcon(CountryContentKind.Category, modifier, tint)
    @Composable override fun Location(modifier: Modifier, tint: Color) = CountryContentDecorativeIcon(CountryContentKind.Location, modifier, tint)
}

private class CountryArrowIcons : BaseArrowIcons() {
    @Composable override fun ArrowBack(modifier: Modifier, tint: Color) = CountryArrowDecorativeIcon(CountryArrowKind.ArrowBack, modifier, tint)
    @Composable override fun ArrowForward(modifier: Modifier, tint: Color) = CountryArrowDecorativeIcon(CountryArrowKind.ArrowForward, modifier, tint)
    @Composable override fun KeyboardArrowLeft(modifier: Modifier, tint: Color) = CountryArrowDecorativeIcon(CountryArrowKind.KeyboardArrowLeft, modifier, tint)
    @Composable override fun KeyboardArrowRight(modifier: Modifier, tint: Color) = CountryArrowDecorativeIcon(CountryArrowKind.KeyboardArrowRight, modifier, tint)
    @Composable override fun ExpandMore(modifier: Modifier, tint: Color) = CountryArrowDecorativeIcon(CountryArrowKind.ExpandMore, modifier, tint)
    @Composable override fun ExpandLess(modifier: Modifier, tint: Color) = CountryArrowDecorativeIcon(CountryArrowKind.ExpandLess, modifier, tint)
    @Composable override fun ArrowDropDown(modifier: Modifier, tint: Color) = CountryArrowDecorativeIcon(CountryArrowKind.ArrowDropDown, modifier, tint)
    @Composable override fun SwapVert(modifier: Modifier, tint: Color) = CountryArrowDecorativeIcon(CountryArrowKind.SwapVert, modifier, tint)
    @Composable override fun OpenInNew(modifier: Modifier, tint: Color) = CountryArrowDecorativeIcon(CountryArrowKind.OpenInNew, modifier, tint)
}

private class CountryStatusIcons : BaseStatusIcons() {
    @Composable override fun CheckCircle(modifier: Modifier, tint: Color) = CountryStatusDecorativeIcon(CountryStatusKind.CheckCircle, modifier, tint)
    @Composable override fun Warning(modifier: Modifier, tint: Color) = CountryStatusDecorativeIcon(CountryStatusKind.Warning, modifier, tint)
    @Composable override fun Error(modifier: Modifier, tint: Color) = CountryStatusDecorativeIcon(CountryStatusKind.Error, modifier, tint)
    @Composable override fun Info(modifier: Modifier, tint: Color) = CountryStatusDecorativeIcon(CountryStatusKind.Info, modifier, tint)
    @Composable override fun Visibility(modifier: Modifier, tint: Color) = CountryStatusDecorativeIcon(CountryStatusKind.Visibility, modifier, tint)
    @Composable override fun VisibilityOff(modifier: Modifier, tint: Color) = CountryStatusDecorativeIcon(CountryStatusKind.VisibilityOff, modifier, tint)
    @Composable override fun Help(modifier: Modifier, tint: Color) = CountryStatusDecorativeIcon(CountryStatusKind.Help, modifier, tint)
}

class CountryIconProvider : BaseSkinIconProvider() {
    override val navigation: NavigationIcons = CountryNavigationIcons()
    override val action: ActionIcons = CountryActionIcons()
    override val content: ContentIcons = CountryContentIcons()
    override val arrow: ArrowIcons = CountryArrowIcons()
    override val status: StatusIcons = CountryStatusIcons()
}
