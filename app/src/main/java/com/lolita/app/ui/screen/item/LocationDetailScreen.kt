package com.lolita.app.ui.screen.item

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lolita.app.data.local.entity.Item
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import com.lolita.app.ui.theme.skin.animation.SkinClickable
import com.lolita.app.ui.theme.skin.animation.SkinItemAppear
import com.lolita.app.ui.theme.skin.animation.SkinFlingBehavior

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationDetailScreen(
    locationId: Long,
    onBack: () -> Unit,
    onItemClick: (Long) -> Unit,
    viewModel: LocationDetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(locationId) {
        viewModel.loadLocation(locationId)
    }

    val title = when {
        uiState.isUnassigned -> "未分配"
        uiState.location != null -> uiState.location!!.name
        else -> "位置详情"
    }

    Scaffold(
        topBar = {
            GradientTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        SkinIcon(IconKey.ArrowBack)
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                flingBehavior = SkinFlingBehavior()
            ) {
                // Location header (image + description)
                if (!uiState.isUnassigned && uiState.location != null) {
                    val loc = uiState.location!!
                    if (loc.imageUrl != null) {
                        item {
                            AsyncImage(
                                model = loc.imageUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    if (loc.description.isNotBlank()) {
                        item {
                            Text(
                                loc.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    item {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            thickness = 1.dp
                        )
                    }
                }

                // Item count header
                item {
                    Text(
                        "${uiState.items.size} 件服饰",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Item list
                itemsIndexed(uiState.items, key = { _, item -> item.id }) { index, item ->
                    SkinItemAppear(index = index) {
                        SkinClickable(onClick = { onItemClick(item.id) }) {
                            LocationItemCard(
                                item = item,
                                brandName = uiState.brandNames[item.brandId] ?: "",
                                categoryName = uiState.categoryNames[item.categoryId] ?: ""
                            )
                        }
                    }
                }

                if (uiState.items.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "暂无服饰",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationItemCard(
    item: Item,
    brandName: String,
    categoryName: String
) {
    LolitaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.imageUrl != null) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        SkinIcon(IconKey.Image, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (brandName.isNotBlank()) {
                    Text(brandName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (categoryName.isNotBlank()) {
                    Text(categoryName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            SkinIcon(IconKey.KeyboardArrowRight, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
