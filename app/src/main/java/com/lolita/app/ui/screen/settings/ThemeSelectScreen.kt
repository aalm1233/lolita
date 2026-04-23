package com.lolita.app.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.di.AppModule
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.theme.LocalLolitaSkin
import com.lolita.app.ui.theme.SkinType
import com.lolita.app.ui.theme.getSkinConfig
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import kotlinx.coroutines.launch

@Composable
fun ThemeSelectScreen(
    onBack: () -> Unit,
    onNavigateToIconGallery: () -> Unit = {},
    appPreferences: AppPreferences = AppModule.appPreferences()
) {
    val currentSkin by appPreferences.skinType.collectAsState(initial = SkinType.DEFAULT)
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            GradientTopAppBar(
                title = { Text("\u9009\u62e9\u76ae\u80a4") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        SkinIcon(IconKey.ArrowBack)
                    }
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "\u5f53\u524d\u76ae\u80a4\uff1a" + currentSkin.displayName(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "\u5982\u679c\u4f60\u6000\u7591\u56fe\u6807\u6ca1\u5207\u6210\u529f\uff0c\u53ef\u4ee5\u76f4\u63a5\u6253\u5f00\u603b\u89c8\u9875\u770b\u5168\u90e8 51 \u4e2a\u81ea\u5b9a\u4e49\u56fe\u6807\u3002",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = onNavigateToIconGallery) {
                            SkinIcon(
                                IconKey.Visibility,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("\u67e5\u770b\u5f53\u524d\u76ae\u80a4\u56fe\u6807\u603b\u89c8")
                        }
                    }
                }
            }

            items(SkinType.entries.toList()) { skinType ->
                SkinPreviewCard(
                    skinType = skinType,
                    isSelected = skinType == currentSkin,
                    onClick = {
                        coroutineScope.launch { appPreferences.setSkinType(skinType) }
                    }
                )
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
private fun SkinPreviewCard(
    skinType: SkinType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val skin = getSkinConfig(skinType)
    val borderModifier = if (isSelected) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
    } else {
        Modifier
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(borderModifier)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Brush.horizontalGradient(skin.gradientColors)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${skin.topBarDecoration}  ${skinType.displayName()}  ${skin.topBarDecoration}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = skin.fontFamily
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Lolita Fashion",
                fontFamily = skin.fontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = skin.lightColorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            CompositionLocalProvider(LocalLolitaSkin provides skin) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SkinIcon(
                        IconKey.Home,
                        modifier = Modifier.size(18.dp),
                        tint = skin.lightColorScheme.primary
                    )
                    SkinIcon(
                        IconKey.Search,
                        modifier = Modifier.size(18.dp),
                        tint = skin.lightColorScheme.primary
                    )
                    SkinIcon(
                        IconKey.Settings,
                        modifier = Modifier.size(18.dp),
                        tint = skin.lightColorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = skin.topBarDecoration,
                fontSize = 20.sp,
                color = skin.lightColorScheme.primary.copy(alpha = 0.6f)
            )

            if (isSelected) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "\u5f53\u524d\u4f7f\u7528",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
