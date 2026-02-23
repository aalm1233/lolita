package com.lolita.app.ui.screen.item

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lolita.app.data.local.entity.Item
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import com.lolita.app.ui.theme.skin.animation.skinItemAppear
import com.lolita.app.ui.theme.skin.animation.rememberSkinFlingBehavior
import com.lolita.app.ui.theme.skin.component.SkinClickableBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationDetailScreen(
    locationId: Long,
    onBack: () -> Unit,
    onItemClick: (Long) -> Unit,
    viewModel: LocationDetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showItemPicker by remember { mutableStateOf(false) }

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
                flingBehavior = rememberSkinFlingBehavior()
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${uiState.items.size} 件服饰",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (!uiState.isUnassigned) {
                            SkinClickableBox(
                                onClick = {
                                    viewModel.loadAllItemsForPicker()
                                    showItemPicker = true
                                }
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        SkinIcon(IconKey.Add, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        Text(
                                            "添加服饰",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Item list
                itemsIndexed(uiState.items, key = { _, item -> item.id }) { index, item ->
                    SkinClickableBox(
                        onClick = { onItemClick(item.id) },
                        modifier = Modifier.skinItemAppear(index)
                    ) {
                        LocationItemCard(
                            item = item,
                            brandName = uiState.brandNames[item.brandId] ?: "",
                            categoryName = uiState.categoryNames[item.categoryId] ?: ""
                        )
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

            if (showItemPicker) {
                ModalBottomSheet(
                    onDismissRequest = { showItemPicker = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ) {
                    LocationItemPickerContent(
                        allItems = uiState.allItems,
                        selectedItemIds = uiState.pickerSelectedItemIds,
                        locationNames = uiState.locationNames,
                        currentLocationId = locationId,
                        searchQuery = uiState.pickerSearchQuery,
                        onSearchQueryChange = { viewModel.updatePickerSearchQuery(it) },
                        onToggleItem = { viewModel.togglePickerItemSelection(it) },
                        onConfirm = {
                            viewModel.confirmPickerSelection(locationId) {
                                showItemPicker = false
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationItemPickerContent(
    allItems: List<Item>,
    selectedItemIds: Set<Long>,
    locationNames: Map<Long, String>,
    currentLocationId: Long,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleItem: (Long) -> Unit,
    onConfirm: () -> Unit
) {
    val filteredItems = remember(allItems, searchQuery) {
        if (searchQuery.isBlank()) allItems
        else allItems.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("选择服饰", style = MaterialTheme.typography.titleMedium)
            SkinClickableBox(onClick = onConfirm) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "确认 (${selectedItemIds.size})",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // Search
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("搜索服饰...") },
            leadingIcon = { SkinIcon(IconKey.Search, modifier = Modifier.size(20.dp)) },
            singleLine = true
        )

        // Item list
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .heightIn(max = 400.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (filteredItems.isEmpty()) {
                item {
                    Text(
                        if (searchQuery.isBlank()) "暂无服饰" else "未找到匹配的服饰",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(filteredItems, key = { it.id }) { item ->
                    val isSelected = item.id in selectedItemIds
                    val belongsToOther = item.locationId != null
                        && item.locationId != currentLocationId
                    val otherLocName = if (belongsToOther) {
                        locationNames[item.locationId]
                    } else null

                    LocationPickerItemRow(
                        item = item,
                        isSelected = isSelected,
                        otherLocationName = otherLocName,
                        onToggle = { onToggleItem(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationPickerItemRow(
    item: Item,
    isSelected: Boolean,
    otherLocationName: String?,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surface,
        onClick = onToggle
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary
                )
            )

            if (item.imageUrl != null) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.name,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                )
                            ),
                            RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.name.firstOrNull()?.toString() ?: "?",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (otherLocationName != null) {
                    Text(
                        "已属于位置「$otherLocationName」",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
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
