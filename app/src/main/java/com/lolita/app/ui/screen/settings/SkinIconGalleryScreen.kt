package com.lolita.app.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.SkinType
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon

private data class IconGallerySection(
    val title: String,
    val icons: List<IconKey>
)

@Composable
fun SkinIconGalleryScreen(
    onBack: () -> Unit
) {
    val skin = LolitaSkin.current
    val sections = listOf(
        IconGallerySection(
            title = "\u5bfc\u822a\u56fe\u6807",
            icons = listOf(
                IconKey.Home,
                IconKey.Wishlist,
                IconKey.Outfit,
                IconKey.Stats,
                IconKey.Settings
            )
        ),
        IconGallerySection(
            title = "\u64cd\u4f5c\u56fe\u6807",
            icons = listOf(
                IconKey.Add,
                IconKey.Delete,
                IconKey.Edit,
                IconKey.Search,
                IconKey.Sort,
                IconKey.Save,
                IconKey.Close,
                IconKey.Share,
                IconKey.FilterList,
                IconKey.MoreVert,
                IconKey.ContentCopy,
                IconKey.Refresh,
                IconKey.ViewAgenda,
                IconKey.GridView,
                IconKey.Apps,
                IconKey.Gallery
            )
        ),
        IconGallerySection(
            title = "\u5185\u5bb9\u56fe\u6807",
            icons = listOf(
                IconKey.Star,
                IconKey.StarBorder,
                IconKey.Image,
                IconKey.Camera,
                IconKey.AddPhoto,
                IconKey.Link,
                IconKey.LinkOff,
                IconKey.Palette,
                IconKey.FileOpen,
                IconKey.CalendarMonth,
                IconKey.Notifications,
                IconKey.AttachMoney,
                IconKey.Category,
                IconKey.Location
            )
        ),
        IconGallerySection(
            title = "\u65b9\u5411\u56fe\u6807",
            icons = listOf(
                IconKey.ArrowBack,
                IconKey.ArrowForward,
                IconKey.KeyboardArrowLeft,
                IconKey.KeyboardArrowRight,
                IconKey.ExpandMore,
                IconKey.ExpandLess,
                IconKey.ArrowDropDown,
                IconKey.SwapVert,
                IconKey.OpenInNew
            )
        ),
        IconGallerySection(
            title = "\u72b6\u6001\u56fe\u6807",
            icons = listOf(
                IconKey.CheckCircle,
                IconKey.Warning,
                IconKey.Error,
                IconKey.Info,
                IconKey.Visibility,
                IconKey.VisibilityOff,
                IconKey.Help
            )
        )
    )

    Scaffold(
        topBar = {
            GradientTopAppBar(
                title = { Text("\u76ae\u80a4\u56fe\u6807\u603b\u89c8") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        SkinIcon(IconKey.ArrowBack)
                    }
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 88.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "\u5f53\u524d\u76ae\u80a4\uff1a" +
                                skin.skinType.displayName() +
                                " (" + skin.skinType.name + ")",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "\u8fd9\u91cc\u76f4\u63a5\u6e32\u67d3\u5f53\u524d\u4e3b\u9898\u4e0b\u5168\u90e8 51 \u4e2a\u53ef\u81ea\u5b9a\u4e49\u56fe\u6807\uff0c\u7528\u6765\u786e\u8ba4\u56fe\u6807\u662f\u5426\u771f\u7684\u5207\u5230\u4e86\u65b0\u76ae\u80a4\u3002",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            sections.forEach { section ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }
                items(section.icons, key = { it.name }) { iconKey ->
                    IconGalleryCell(iconKey = iconKey)
                }
            }
        }
    }
}

private fun SkinType.displayName(): String = when (this) {
    SkinType.DEFAULT -> "\u751c\u7f8e\u5c11\u5973"
    SkinType.GOTHIC -> "\u54e5\u7279\u6697\u9ed1"
    SkinType.CHINESE -> "\u4e2d\u534e\u98ce\u97f5"
    SkinType.CLASSIC -> "\u7ecf\u5178\u4f18\u96c5"
    SkinType.NAVY -> "\u6e05\u98ce\u6c34\u624b"
    SkinType.COUNTRY -> "\u7267\u6b4c\u7530\u56ed"
}

@Composable
private fun IconGalleryCell(iconKey: IconKey) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = MaterialTheme.shapes.small
                    ),
                contentAlignment = Alignment.Center
            ) {
                SkinIcon(
                    key = iconKey,
                    modifier = Modifier.size(26.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = iconKey.displayName(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun IconKey.displayName(): String = when (this) {
    IconKey.Home -> "\u9996\u9875"
    IconKey.Wishlist -> "\u613f\u671b"
    IconKey.Outfit -> "\u7a7f\u642d"
    IconKey.Stats -> "\u7edf\u8ba1"
    IconKey.Settings -> "\u8bbe\u7f6e"
    IconKey.Add -> "\u65b0\u589e"
    IconKey.Delete -> "\u5220\u9664"
    IconKey.Edit -> "\u7f16\u8f91"
    IconKey.Search -> "\u641c\u7d22"
    IconKey.Sort -> "\u6392\u5e8f"
    IconKey.Save -> "\u4fdd\u5b58"
    IconKey.Close -> "\u5173\u95ed"
    IconKey.Share -> "\u5206\u4eab"
    IconKey.FilterList -> "\u7b5b\u9009"
    IconKey.MoreVert -> "\u66f4\u591a"
    IconKey.ContentCopy -> "\u590d\u5236"
    IconKey.Refresh -> "\u5237\u65b0"
    IconKey.ViewAgenda -> "\u5217\u8868"
    IconKey.GridView -> "\u7f51\u683c"
    IconKey.Apps -> "\u5e94\u7528"
    IconKey.Gallery -> "\u76f8\u518c"
    IconKey.Star -> "\u6536\u85cf"
    IconKey.StarBorder -> "\u7a7a\u5fc3\u661f"
    IconKey.Image -> "\u56fe\u7247"
    IconKey.Camera -> "\u76f8\u673a"
    IconKey.AddPhoto -> "\u52a0\u56fe"
    IconKey.Link -> "\u94fe\u63a5"
    IconKey.LinkOff -> "\u65ad\u94fe"
    IconKey.Palette -> "\u8c03\u8272\u76d8"
    IconKey.FileOpen -> "\u6253\u5f00"
    IconKey.CalendarMonth -> "\u65e5\u5386"
    IconKey.Notifications -> "\u63d0\u9192"
    IconKey.AttachMoney -> "\u91d1\u989d"
    IconKey.Category -> "\u5206\u7c7b"
    IconKey.Location -> "\u4f4d\u7f6e"
    IconKey.ArrowBack -> "\u8fd4\u56de"
    IconKey.ArrowForward -> "\u524d\u8fdb"
    IconKey.KeyboardArrowLeft -> "\u5de6\u7bad\u5934"
    IconKey.KeyboardArrowRight -> "\u53f3\u7bad\u5934"
    IconKey.ExpandMore -> "\u5c55\u5f00"
    IconKey.ExpandLess -> "\u6536\u8d77"
    IconKey.ArrowDropDown -> "\u4e0b\u62c9"
    IconKey.SwapVert -> "\u5207\u6362"
    IconKey.OpenInNew -> "\u8df3\u8f6c"
    IconKey.CheckCircle -> "\u6210\u529f"
    IconKey.Warning -> "\u8b66\u544a"
    IconKey.Error -> "\u9519\u8bef"
    IconKey.Info -> "\u4fe1\u606f"
    IconKey.Visibility -> "\u53ef\u89c1"
    IconKey.VisibilityOff -> "\u9690\u85cf"
    IconKey.Help -> "\u5e2e\u52a9"
}
