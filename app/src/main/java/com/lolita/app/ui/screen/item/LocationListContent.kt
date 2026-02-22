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
import com.lolita.app.ui.theme.skin.animation.SkinClickable
import com.lolita.app.ui.theme.skin.animation.SkinItemAppear
import com.lolita.app.ui.theme.skin.animation.SkinFlingBehavior

@Composable
fun LocationListContent(
    locations: List<Location>,
    locationItemCounts: Map<Long, Int>,
    unassignedItemCount: Int,
    onLocationClick: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        flingBehavior = SkinFlingBehavior()
    ) {
        itemsIndexed(locations, key = { _, loc -> loc.id }) { index, location ->
            SkinItemAppear(index = index) {
                SkinClickable(onClick = { onLocationClick(location.id) }) {
                    LocationCardItem(
                        name = location.name,
                        description = location.description,
                        imageUrl = location.imageUrl,
                        itemCount = locationItemCounts[location.id] ?: 0
                    )
                }
            }
        }
        if (unassignedItemCount > 0) {
            item(key = "unassigned") {
                SkinItemAppear(index = locations.size) {
                    SkinClickable(onClick = { onLocationClick(-1L) }) {
                        LocationCardItem(
                            name = "未分配",
                            description = "未设置位置的服饰",
                            imageUrl = null,
                            itemCount = unassignedItemCount,
                            isUnassigned = true
                        )
                    }
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
    isUnassigned: Boolean = false
) {
    LolitaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp))
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
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${itemCount} 件服饰",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            SkinIcon(IconKey.KeyboardArrowRight, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
