package com.lolita.app.ui.theme.skin.icon

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

interface NavigationIcons {
    @Composable fun Home(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun Wishlist(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun Outfit(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun Stats(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun Settings(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
}

interface ActionIcons {
    @Composable fun Add(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun Delete(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun Edit(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun Search(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun Sort(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun Save(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun Close(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun Share(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun FilterList(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun MoreVert(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun ContentCopy(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun Refresh(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun ViewAgenda(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun GridView(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun Apps(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
}

interface ContentIcons {
    @Composable fun Star(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun StarBorder(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun Image(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun Camera(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun AddPhoto(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun Link(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun LinkOff(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun Palette(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun FileOpen(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun CalendarMonth(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun Notifications(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun AttachMoney(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun Category(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun Location(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
}

interface ArrowIcons {
    @Composable fun ArrowBack(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun ArrowForward(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun KeyboardArrowLeft(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun KeyboardArrowRight(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun ExpandMore(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun ExpandLess(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun ArrowDropDown(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun SwapVert(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun OpenInNew(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
}

interface StatusIcons {
    @Composable fun CheckCircle(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun Warning(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun Error(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun Info(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun Visibility(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun VisibilityOff(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    @Composable fun Help(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
}

interface SkinIconProvider {
    val navigation: NavigationIcons
    val action: ActionIcons
    val content: ContentIcons
    val arrow: ArrowIcons
    val status: StatusIcons
}
