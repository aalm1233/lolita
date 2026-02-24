package com.lolita.app.ui.screen.item

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lolita.app.data.local.entity.Location
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import com.lolita.app.ui.theme.skin.animation.skinItemAppear
import com.lolita.app.ui.theme.skin.animation.rememberSkinFlingBehavior
import com.lolita.app.ui.theme.skin.component.SkinClickableBox

@Composable
fun LocationListContent(
    locations: List<Location>,
    locationItemCounts: Map<Long, Int>,
    locationItemImages: Map<Long, List<String>>,
    unassignedItemCount: Int,
    onLocationClick: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        flingBehavior = rememberSkinFlingBehavior()
    ) {
        itemsIndexed(locations, key = { _, loc -> loc.id }) { index, location ->
            SkinClickableBox(
                onClick = { onLocationClick(location.id) },
                modifier = Modifier.skinItemAppear(index)
            ) {
                LocationCardItem(
                    name = location.name,
                    description = location.description,
                    imageUrl = location.imageUrl,
                    itemCount = locationItemCounts[location.id] ?: 0,
                    itemImages = locationItemImages[location.id] ?: emptyList()
                )
            }
        }
        if (unassignedItemCount > 0) {
            item(key = "unassigned") {
                SkinClickableBox(
                    onClick = { onLocationClick(-1L) },
                    modifier = Modifier.skinItemAppear(locations.size)
                ) {
                    LocationCardItem(
                        name = "未分配",
                        description = "未设置位置的服饰",
                        imageUrl = null,
                        itemCount = unassignedItemCount,
                        itemImages = emptyList(),
                        isUnassigned = true
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationCardItem(
    name: String,
    description: String,
    imageUrl: String?,
    itemCount: Int,
    itemImages: List<String> = emptyList(),
    isUnassigned: Boolean = false
) {
    LolitaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Location image - 56dp
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        SkinIcon(
                            if (isUnassigned) IconKey.Info else IconKey.Location,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                if (description.isNotBlank()) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
                // Item count badge
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "${itemCount} 件服饰",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                // Item thumbnail preview row
                Spacer(Modifier.height(6.dp))
                if (itemImages.isNotEmpty()) {
                    Row {
                        itemImages.forEachIndexed { index, url ->
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(36.dp)
                                    .offset(x = (-(index * 6)).dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                } else {
                    Text(
                        "暂无服饰",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
            SkinIcon(IconKey.KeyboardArrowRight, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
