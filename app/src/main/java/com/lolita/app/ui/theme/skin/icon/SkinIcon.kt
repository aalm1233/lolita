package com.lolita.app.ui.theme.skin.icon

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.lolita.app.ui.theme.LolitaSkin

@Composable
fun SkinIcon(
    key: IconKey,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    val icons = LolitaSkin.current.icons
    when (key) {
        // Navigation
        IconKey.Home -> icons.navigation.Home(modifier, tint)
        IconKey.Wishlist -> icons.navigation.Wishlist(modifier, tint)
        IconKey.Outfit -> icons.navigation.Outfit(modifier, tint)
        IconKey.Stats -> icons.navigation.Stats(modifier, tint)
        IconKey.Settings -> icons.navigation.Settings(modifier, tint)
        // Action
        IconKey.Add -> icons.action.Add(modifier, tint)
        IconKey.Delete -> icons.action.Delete(modifier, tint)
        IconKey.Edit -> icons.action.Edit(modifier, tint)
        IconKey.Search -> icons.action.Search(modifier, tint)
        IconKey.Sort -> icons.action.Sort(modifier, tint)
        IconKey.Save -> icons.action.Save(modifier, tint)
        IconKey.Close -> icons.action.Close(modifier, tint)
        IconKey.Share -> icons.action.Share(modifier, tint)
        IconKey.FilterList -> icons.action.FilterList(modifier, tint)
        IconKey.MoreVert -> icons.action.MoreVert(modifier, tint)
        IconKey.ContentCopy -> icons.action.ContentCopy(modifier, tint)
        IconKey.Refresh -> icons.action.Refresh(modifier, tint)
        IconKey.ViewAgenda -> icons.action.ViewAgenda(modifier, tint)
        IconKey.GridView -> icons.action.GridView(modifier, tint)
        IconKey.Apps -> icons.action.Apps(modifier, tint)
        // Content
        IconKey.Star -> icons.content.Star(modifier, tint)
        IconKey.StarBorder -> icons.content.StarBorder(modifier, tint)
        IconKey.Image -> icons.content.Image(modifier, tint)
        IconKey.Camera -> icons.content.Camera(modifier, tint)
        IconKey.AddPhoto -> icons.content.AddPhoto(modifier, tint)
        IconKey.Link -> icons.content.Link(modifier, tint)
        IconKey.LinkOff -> icons.content.LinkOff(modifier, tint)
        IconKey.Palette -> icons.content.Palette(modifier, tint)
        IconKey.FileOpen -> icons.content.FileOpen(modifier, tint)
        IconKey.CalendarMonth -> icons.content.CalendarMonth(modifier, tint)
        IconKey.Notifications -> icons.content.Notifications(modifier, tint)
        IconKey.AttachMoney -> icons.content.AttachMoney(modifier, tint)
        IconKey.Category -> icons.content.Category(modifier, tint)
        IconKey.Location -> icons.content.Location(modifier, tint)
        // Arrow
        IconKey.ArrowBack -> icons.arrow.ArrowBack(modifier, tint)
        IconKey.ArrowForward -> icons.arrow.ArrowForward(modifier, tint)
        IconKey.KeyboardArrowLeft -> icons.arrow.KeyboardArrowLeft(modifier, tint)
        IconKey.KeyboardArrowRight -> icons.arrow.KeyboardArrowRight(modifier, tint)
        IconKey.ExpandMore -> icons.arrow.ExpandMore(modifier, tint)
        IconKey.ExpandLess -> icons.arrow.ExpandLess(modifier, tint)
        IconKey.ArrowDropDown -> icons.arrow.ArrowDropDown(modifier, tint)
        IconKey.SwapVert -> icons.arrow.SwapVert(modifier, tint)
        IconKey.OpenInNew -> icons.arrow.OpenInNew(modifier, tint)
        // Status
        IconKey.CheckCircle -> icons.status.CheckCircle(modifier, tint)
        IconKey.Warning -> icons.status.Warning(modifier, tint)
        IconKey.Error -> icons.status.Error(modifier, tint)
        IconKey.Info -> icons.status.Info(modifier, tint)
        IconKey.Visibility -> icons.status.Visibility(modifier, tint)
        IconKey.VisibilityOff -> icons.status.VisibilityOff(modifier, tint)
        IconKey.Help -> icons.status.Help(modifier, tint)
    }
}
