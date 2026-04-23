package com.lolita.app.ui.screen.catalog

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lolita.app.data.local.entity.CatalogEntry
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.ui.screen.common.BrandLogo
import com.lolita.app.ui.screen.common.SkinEmptyState
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.screen.common.ViewMode
import com.lolita.app.ui.screen.common.findColorHex
import com.lolita.app.ui.theme.skin.icon.IconKey

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CatalogListContent(
    uiState: CatalogListUiState,
    onNavigateToDetail: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (uiState.isLoading) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        } else if (uiState.filteredEntries.isEmpty()) {
            SkinEmptyState(
                iconKey = IconKey.Home,
                title = "暂无图鉴",
                subtitle = "点击右下角添加第一条图鉴记录",
                modifier = Modifier.fillMaxSize()
            )
        } else if (uiState.viewMode == ViewMode.GALLERY) {
            val galleryItems = uiState.galleryCardDataList
            if (galleryItems.isEmpty()) {
                SkinEmptyState(
                    iconKey = IconKey.Home,
                    title = "暂无带图图鉴",
                    subtitle = "切换到列表或网格模式查看更多记录",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    verticalItemSpacing = 8.dp,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    staggeredItems(galleryItems, key = { it.entry.id }) { data ->
                        CatalogGalleryCard(
                            data = data,
                            onClick = { onNavigateToDetail(data.entry.id) }
                        )
                    }
                }
            }
        } else if (uiState.columnsPerRow == 1) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.cardDataList, key = { it.entry.id }) { data ->
                    CatalogListCard(
                        data = data,
                        onClick = { onNavigateToDetail(data.entry.id) }
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(uiState.columnsPerRow),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                gridItems(uiState.cardDataList, key = { it.entry.id }) { data ->
                    CatalogGridCard(
                        data = data,
                        onClick = { onNavigateToDetail(data.entry.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CatalogFilterPanel(
    uiState: CatalogListUiState,
    onBrandSelected: (Long?) -> Unit,
    onCategorySelected: (Long?) -> Unit,
    onStyleSelected: (String?) -> Unit,
    onSeasonSelected: (String?) -> Unit,
    onColorSelected: (String?) -> Unit
) {
    val usedBrandIds = uiState.entries.mapNotNull { it.brandId }.toSet()
    val brandOptions = uiState.brandNames
        .filter { (id, _) -> id in usedBrandIds }
        .entries
        .sortedBy { it.value }

    val usedCategoryIds = uiState.entries.mapNotNull { it.categoryId }.toSet()
    val categoryOptions = uiState.categoryNames
        .filter { (id, _) -> id in usedCategoryIds }
        .entries
        .sortedBy { it.value }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (brandOptions.isNotEmpty()) {
                CatalogFilterOptionRow(
                    label = "品牌",
                    options = brandOptions.map { it.value },
                    selectedValue = uiState.filterBrandId?.let(uiState.brandNames::get),
                    onSelected = { name ->
                        onBrandSelected(brandOptions.firstOrNull { it.value == name }?.key)
                    },
                    onClear = { onBrandSelected(null) },
                    searchable = true
                )
            }
            if (categoryOptions.isNotEmpty()) {
                CatalogFilterOptionRow(
                    label = "分类",
                    options = categoryOptions.map { it.value },
                    selectedValue = uiState.filterCategoryId?.let(uiState.categoryNames::get),
                    onSelected = { name ->
                        onCategorySelected(categoryOptions.firstOrNull { it.value == name }?.key)
                    },
                    onClear = { onCategorySelected(null) },
                    searchable = true
                )
            }
            if (uiState.styleOptions.isNotEmpty()) {
                CatalogFilterOptionRow(
                    label = "风格",
                    options = uiState.styleOptions,
                    selectedValue = uiState.filterStyle,
                    onSelected = onStyleSelected,
                    onClear = { onStyleSelected(null) }
                )
            }
            if (uiState.seasonOptions.isNotEmpty()) {
                CatalogFilterOptionRow(
                    label = "季节",
                    options = uiState.seasonOptions,
                    selectedValue = uiState.filterSeason,
                    onSelected = onSeasonSelected,
                    onClear = { onSeasonSelected(null) }
                )
            }
            if (uiState.colorOptions.isNotEmpty()) {
                CatalogFilterOptionRow(
                    label = "颜色",
                    options = uiState.colorOptions,
                    selectedValue = uiState.filterColor,
                    onSelected = onColorSelected,
                    onClear = { onColorSelected(null) }
                )
            }
        }
    }
}

@Composable
private fun CatalogFilterOptionRow(
    label: String,
    options: List<String>,
    selectedValue: String?,
    onSelected: (String) -> Unit,
    onClear: () -> Unit,
    searchable: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp)
        )
        Box(modifier = Modifier.weight(1f)) {
            FilterChip(
                selected = selectedValue != null,
                onClick = {
                    searchQuery = ""
                    expanded = true
                },
                label = {
                    Text(
                        text = selectedValue ?: "全部",
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                trailingIcon = if (selectedValue != null) {
                    {
                        Text(
                            text = "清除",
                            fontSize = 10.sp,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                } else {
                    null
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.height(30.dp)
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 280.dp)
            ) {
                if (searchable) {
                    androidx.compose.material3.OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("搜索", fontSize = 13.sp) },
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .width(200.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
                DropdownMenuItem(
                    text = { Text("全部") },
                    onClick = {
                        onClear()
                        expanded = false
                    }
                )
                val filtered = if (searchQuery.isBlank()) options else options.filter { it.contains(searchQuery, ignoreCase = true) }
                filtered.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                option,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        onClick = {
                            onSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogListCard(
    data: CatalogCardData,
    onClick: () -> Unit
) {
    val entry = data.entry
    LolitaCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CatalogThumbnail(
                imagePath = entry.imageUrls.firstOrNull(),
                title = entry.name,
                modifier = Modifier
                    .width(96.dp)
                    .aspectRatio(0.78f)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!entry.seriesName.isNullOrBlank()) {
                    Text(
                        text = entry.seriesName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                CatalogMetaRow(data = data)
                CatalogTagFlow(entry = entry, linkedItemStatus = data.linkedItemStatus, isRemote = data.isRemote)
                if (entry.description.isNotBlank()) {
                    Text(
                        text = entry.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogGridCard(
    data: CatalogCardData,
    onClick: () -> Unit
) {
    val entry = data.entry
    LolitaCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            CatalogThumbnail(
                imagePath = entry.imageUrls.firstOrNull(),
                title = entry.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.78f)
            )
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!entry.seriesName.isNullOrBlank()) {
                    Text(
                        text = entry.seriesName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                data.brandName?.let {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        BrandLogo(logoUrl = data.brandLogoUrl, brandName = it, size = 14.dp)
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (data.linkedItemStatus != null) {
                    CatalogLinkedBadge(status = data.linkedItemStatus)
                }
                if (data.isRemote) {
                    CatalogSharedBadge()
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CatalogGalleryCard(
    data: CatalogCardData,
    onClick: () -> Unit
) {
    val entry = data.entry
    val context = LocalContext.current
    val density = LocalDensity.current
    val imageSizePx = with(density) { 300.dp.roundToPx() }

    LolitaCard(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onClick)
        ) {
            if (entry.imageUrls.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(resolveCatalogImageModel(entry.imageUrls.first()))
                        .size(imageSizePx)
                        .crossfade(true)
                        .build(),
                    contentDescription = entry.name,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.78f)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = entry.name.firstOrNull()?.toString() ?: "?",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                if (data.isRemote) {
                    CatalogSharedBadge()
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Column {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subline = data.brandName ?: entry.seriesName
                    if (!subline.isNullOrBlank()) {
                        Text(
                            text = subline,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogThumbnail(
    imagePath: String?,
    title: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val imageSizePx = with(density) { 220.dp.roundToPx() }

    if (imagePath != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(resolveCatalogImageModel(imagePath))
                .size(imageSizePx)
                .crossfade(true)
                .build(),
            contentDescription = title,
            modifier = modifier.clip(RoundedCornerShape(14.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title.firstOrNull()?.toString() ?: "?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatalogMetaRow(data: CatalogCardData) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        data.brandName?.let {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BrandLogo(logoUrl = data.brandLogoUrl, brandName = it, size = 14.dp)
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
        data.categoryName?.let {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = it,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatalogTagFlow(
    entry: CatalogEntry,
    linkedItemStatus: ItemStatus?,
    isRemote: Boolean
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (isRemote) {
            CatalogSharedBadge()
        }
        if (!entry.style.isNullOrBlank()) {
            CatalogTagChip(text = entry.style)
        }
        if (!entry.season.isNullOrBlank()) {
            CatalogTagChip(text = entry.season)
        }
        if (!entry.size.isNullOrBlank()) {
            CatalogTagChip(text = entry.size)
        }
        entry.colors.take(3).forEach { colorName ->
            val hex = findColorHex(colorName)
            val chipColor = hex?.let(::Color) ?: Color.Gray
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(chipColor)
                    )
                    Text(
                        text = colorName,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        if (linkedItemStatus != null) {
            CatalogLinkedBadge(status = linkedItemStatus)
        }
    }
}

@Composable
private fun CatalogSharedBadge() {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = "Shared",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CatalogTagChip(text: String?) {
    if (text.isNullOrBlank()) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun CatalogLinkedBadge(status: ItemStatus) {
    val (label, color) = when (status) {
        ItemStatus.OWNED -> "已拥有" to MaterialTheme.colorScheme.tertiary
        ItemStatus.WISHED -> "愿望单" to MaterialTheme.colorScheme.primary
        ItemStatus.PENDING_BALANCE -> "待补尾款" to MaterialTheme.colorScheme.secondary
    }

    Surface(
        color = color.copy(alpha = 0.18f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun resolveCatalogImageModel(imagePath: String): Any {
    return if (imagePath.startsWith("http://") || imagePath.startsWith("https://")) {
        imagePath
    } else {
        java.io.File(imagePath)
    }
}
