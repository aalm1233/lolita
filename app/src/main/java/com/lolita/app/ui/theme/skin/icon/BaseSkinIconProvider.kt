package com.lolita.app.ui.theme.skin.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

open class BaseNavigationIcons : NavigationIcons {
    @Composable override fun Home(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.Home, null, modifier, tint)
    @Composable override fun Wishlist(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.Favorite, null, modifier, tint)
    @Composable override fun Outfit(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.DateRange, null, modifier, tint)
    @Composable override fun Stats(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.Info, null, modifier, tint)
    @Composable override fun Settings(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.Settings, null, modifier, tint)
}

open class BaseActionIcons : ActionIcons {
    @Composable override fun Add(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.Add, null, modifier, tint)
    @Composable override fun Delete(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.Delete, null, modifier, tint)
    @Composable override fun Edit(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.Edit, null, modifier, tint)
    @Composable override fun Search(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.Search, null, modifier, tint)
    @Composable override fun Sort(modifier: Modifier, tint: Color) =
        Icon(Icons.AutoMirrored.Filled.Sort, null, modifier, tint)
    @Composable override fun Save(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.Check, null, modifier, tint)
    @Composable override fun Close(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.Close, null, modifier, tint)
    @Composable override fun Share(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.Share, null, modifier, tint)
    @Composable override fun FilterList(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.FilterList, null, modifier, tint)
    @Composable override fun MoreVert(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.MoreVert, null, modifier, tint)
    @Composable override fun ContentCopy(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.ContentCopy, null, modifier, tint)
    @Composable override fun Refresh(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.Refresh, null, modifier, tint)
}

open class BaseContentIcons : ContentIcons {
    @Composable override fun Star(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.Star, null, modifier, tint)
    @Composable override fun StarBorder(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.StarBorder, null, modifier, tint)
    @Composable override fun Image(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.Image, null, modifier, tint)
    @Composable override fun Camera(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.Camera, null, modifier, tint)
    @Composable override fun AddPhoto(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.AddPhotoAlternate, null, modifier, tint)
    @Composable override fun Link(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.Link, null, modifier, tint)
    @Composable override fun LinkOff(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.LinkOff, null, modifier, tint)
    @Composable override fun Palette(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.Palette, null, modifier, tint)
    @Composable override fun FileOpen(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.FileOpen, null, modifier, tint)
    @Composable override fun CalendarMonth(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.CalendarMonth, null, modifier, tint)
    @Composable override fun Notifications(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.Notifications, null, modifier, tint)
    @Composable override fun AttachMoney(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.AttachMoney, null, modifier, tint)
    @Composable override fun Category(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.Category, null, modifier, tint)
}

open class BaseArrowIcons : ArrowIcons {
    @Composable override fun ArrowBack(modifier: Modifier, tint: Color) =
        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier, tint)
    @Composable override fun ArrowForward(modifier: Modifier, tint: Color) =
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier, tint)
    @Composable override fun KeyboardArrowLeft(modifier: Modifier, tint: Color) =
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, modifier, tint)
    @Composable override fun KeyboardArrowRight(modifier: Modifier, tint: Color) =
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier, tint)
    @Composable override fun ExpandMore(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.ExpandMore, null, modifier, tint)
    @Composable override fun ExpandLess(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.ExpandLess, null, modifier, tint)
    @Composable override fun ArrowDropDown(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.ArrowDropDown, null, modifier, tint)
    @Composable override fun SwapVert(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.SwapVert, null, modifier, tint)
    @Composable override fun OpenInNew(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.OpenInNew, null, modifier, tint)
}

open class BaseStatusIcons : StatusIcons {
    @Composable override fun CheckCircle(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.CheckCircle, null, modifier, tint)
    @Composable override fun Warning(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.Warning, null, modifier, tint)
    @Composable override fun Error(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.Error, null, modifier, tint)
    @Composable override fun Info(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.Info, null, modifier, tint)
    @Composable override fun Visibility(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.Visibility, null, modifier, tint)
    @Composable override fun VisibilityOff(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.VisibilityOff, null, modifier, tint)
}

open class BaseSkinIconProvider : SkinIconProvider {
    override val navigation: NavigationIcons = BaseNavigationIcons()
    override val action: ActionIcons = BaseActionIcons()
    override val content: ContentIcons = BaseContentIcons()
    override val arrow: ArrowIcons = BaseArrowIcons()
    override val status: StatusIcons = BaseStatusIcons()
}
