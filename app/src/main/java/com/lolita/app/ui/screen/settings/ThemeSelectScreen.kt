package com.lolita.app.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.lolita.app.ui.theme.SkinType
import com.lolita.app.ui.theme.getSkinConfig
import kotlinx.coroutines.launch
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon

@Composable
fun ThemeSelectScreen(
    onBack: () -> Unit,
    appPreferences: AppPreferences = AppModule.appPreferences()
) {
    val currentSkin by appPreferences.skinType.collectAsState(initial = SkinType.DEFAULT)
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            GradientTopAppBar(
                title = { Text("选择皮肤") },
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
            // Gradient preview bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Brush.horizontalGradient(skin.gradientColors)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${skin.topBarDecoration}  ${skin.name}  ${skin.topBarDecoration}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = skin.fontFamily
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Font preview
            Text(
                text = "Lolita Fashion",
                fontFamily = skin.fontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = skin.lightColorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Decoration symbol
            Text(
                text = skin.topBarDecoration,
                fontSize = 20.sp,
                color = skin.lightColorScheme.primary.copy(alpha = 0.6f)
            )

            if (isSelected) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "当前使用",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
