package com.lolita.app.ui.screen.outfit

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.Item
import com.lolita.app.ui.screen.common.CardVariant
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.screen.common.LolitaShimmerImage
import com.lolita.app.ui.screen.common.ShimmerLine
import com.lolita.app.ui.screen.common.ShimmerRect
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.component.skinClickable
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickOutfitLogScreen(
    onNavigateBack: () -> Unit,
    viewModel: QuickOutfitLogViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val skin = LolitaSkin.current
    val accent = if (isSystemInDarkTheme()) skin.accentColorDark else skin.accentColor
    val dateFormat = remember { SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINESE) }
    val interactionEnabled = !uiState.isSaving && !uiState.isLoading && !uiState.loadFailed
    val visibleItems = uiState.visibleItems
    val gridState = rememberLazyGridState()

    LaunchedEffect(uiState.searchQuery, uiState.selectedCategoryId) {
        gridState.scrollToItem(0)
    }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) onNavigateBack()
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            GradientTopAppBar(
                title = { Text(if (uiState.existingLogId != null) "编辑今日穿搭" else "记录今日穿搭") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, enabled = !uiState.isSaving) {
                        SkinIcon(IconKey.ArrowBack)
                    }
                },
                compact = true
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding()
                .padding(horizontal = skin.spacingLarge),
            verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(skin.spacingMedium),
                verticalArrangement = Arrangement.spacedBy(skin.spacingMedium),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = skin.spacingSmall)
            ) {
                item(key = "selection-controls", span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(skin.spacingSmall)
                        ) {
                            SkinIcon(IconKey.CalendarMonth, modifier = Modifier.size(16.dp), tint = accent)
                            Text(
                                text = dateFormat.format(Date()),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = { viewModel.toggleShowNote() },
                                enabled = interactionEnabled,
                                contentPadding = PaddingValues(horizontal = skin.spacingSmall)
                            ) {
                                Text(if (uiState.showNote) "收起备注" else "备注", style = MaterialTheme.typography.labelMedium)
                                SkinIcon(
                                    if (uiState.showNote) IconKey.ExpandLess else IconKey.ExpandMore,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        if (uiState.showNote) {
                            OutlinedTextField(
                                value = uiState.note,
                                onValueChange = viewModel::updateNote,
                                enabled = interactionEnabled,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("今天的穿搭心得…") },
                                shape = RoundedCornerShape(skin.cornerRadiusMedium),
                                maxLines = 3
                            )
                        }
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = viewModel::updateSearchQuery,
                            enabled = interactionEnabled,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("搜索物品名称或品牌", style = MaterialTheme.typography.bodyMedium) },
                            leadingIcon = { SkinIcon(IconKey.Search, modifier = Modifier.size(20.dp)) },
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.updateSearchQuery("") },
                                        enabled = interactionEnabled,
                                        modifier = Modifier.semantics { contentDescription = "清空搜索" }
                                    ) { SkinIcon(IconKey.Close, modifier = Modifier.size(18.dp)) }
                                }
                            },
                            shape = RoundedCornerShape(skin.cornerRadiusMedium),
                            singleLine = true
                        )
                        if (uiState.availableCategories.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                                item {
                                    FilterChip(
                                        selected = uiState.selectedCategoryId == null,
                                        onClick = { viewModel.selectCategory(null) },
                                        enabled = interactionEnabled,
                                        label = { Text("全部") },
                                        shape = RoundedCornerShape(skin.cornerRadiusSmall)
                                    )
                                }
                                rowItems(uiState.availableCategories, key = { it.id }) { category ->
                                    FilterChip(
                                        selected = uiState.selectedCategoryId == category.id,
                                        onClick = { viewModel.selectCategory(category.id) },
                                        enabled = interactionEnabled,
                                        label = { Text(category.name) },
                                        shape = RoundedCornerShape(skin.cornerRadiusSmall)
                                    )
                                }
                            }
                        }
                        if (uiState.selectedItemIds.isNotEmpty()) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "已选 ${uiState.selectedItemIds.size} 件",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = accent,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("点按移除", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                                rowItems(uiState.selectedItems, key = { it.id }) { item ->
                                    SelectedQuickItem(item, interactionEnabled, onRemove = { viewModel.toggleItem(item.id) })
                                }
                            }
                        }
                        uiState.error?.let { error ->
                            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                when {
                    uiState.isLoading -> {
                        items(9, key = { "loading-$it" }) { QuickItemSkeleton() }
                    }
                    uiState.items.isEmpty() || visibleItems.isEmpty() -> {
                        item(key = "empty-items", span = { GridItemSpan(maxLineSpan) }) {
                            Column(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                SkinIcon(
                                    if (uiState.items.isEmpty()) IconKey.Outfit else IconKey.Search,
                                    modifier = Modifier.size(40.dp), tint = accent
                                )
                                Spacer(Modifier.height(skin.spacingMedium))
                                Text(
                                    if (uiState.items.isEmpty()) "还没有可选的服饰" else "没有符合条件的物品",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Spacer(Modifier.height(skin.spacingSmall))
                                Text(
                                    if (uiState.items.isEmpty()) "已拥有的服饰会显示在这里" else "试试其他名称、品牌或分类",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (uiState.searchQuery.isNotEmpty() || uiState.selectedCategoryId != null) {
                                    TextButton(onClick = viewModel::clearFilters, enabled = interactionEnabled) {
                                        Text("清除筛选")
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        items(visibleItems, key = { it.id }) { item ->
                            QuickItemCard(
                                item = item,
                                brandName = uiState.brandNames[item.brandId].orEmpty(),
                                isSelected = item.id in uiState.selectedItemIds,
                                enabled = interactionEnabled,
                                onClick = { viewModel.toggleItem(item.id) }
                            )
                        }
                    }
                }
            }
            Button(
                onClick = viewModel::save,
                enabled = interactionEnabled && uiState.selectedItemIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(skin.cornerRadiusMedium)
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    SkinIcon(IconKey.Save, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(skin.spacingMedium))
                Text(
                    if (uiState.isSaving) "保存中…" else "保存今日穿搭（${uiState.selectedItemIds.size} 件）",
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Spacer(Modifier.height(skin.spacingMedium))
        }
    }
}

@Composable
private fun QuickItemCard(
    item: Item,
    brandName: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val skin = LolitaSkin.current
    val accent = if (isSystemInDarkTheme()) skin.accentColorDark else skin.accentColor
    val shape = RoundedCornerShape(skin.cornerRadiusSmall)
    LolitaCard(
        variant = CardVariant.GALLERY,
        shape = shape,
        modifier = Modifier.fillMaxWidth().clip(shape)
            .border(if (isSelected) 2.dp else 1.dp,
                if (isSelected) accent else MaterialTheme.colorScheme.outlineVariant, shape)
            .semantics(mergeDescendants = true) {
                selected = isSelected
                stateDescription = if (isSelected) "已选择" else "未选择"
            }
            .skinClickable(enabled = enabled, onClick = onClick)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                QuickItemImage(item, Modifier.fillMaxSize())
                if (isSelected) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(skin.spacingSmall).size(24.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        SkinIcon(IconKey.CheckCircle, modifier = Modifier.padding(4.dp))
                    }
                }
            }
            Column(modifier = Modifier.padding(skin.spacingMedium),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = brandName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SelectedQuickItem(item: Item, enabled: Boolean, onRemove: () -> Unit) {
    val skin = LolitaSkin.current
    Column(
        modifier = Modifier.width(64.dp)
            .semantics(mergeDescendants = true) { contentDescription = "移除已选服饰：${item.name}" }
            .skinClickable(enabled = enabled, onClick = onRemove),
        verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(skin.cornerRadiusSmall))) {
            QuickItemImage(item, Modifier.fillMaxSize())
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(20.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) { SkinIcon(IconKey.Close, modifier = Modifier.padding(4.dp)) }
        }
        Text(item.name, style = MaterialTheme.typography.labelSmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun QuickItemImage(item: Item, modifier: Modifier) {
    val image = item.imageUrls.firstOrNull { it.isNotBlank() }
    if (image != null) {
        LolitaShimmerImage(
            model = File(image),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
            placeholderInitial = item.name.firstOrNull()?.toString(),
            circularRevealEnabled = false
        )
    } else {
        Surface(modifier = modifier, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center) {
                SkinIcon(IconKey.Outfit, modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun QuickItemSkeleton() {
    val skin = LolitaSkin.current
    Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
        ShimmerRect(width = 96.dp, height = 96.dp,
            shape = RoundedCornerShape(skin.cornerRadiusSmall), modifier = Modifier.fillMaxWidth())
        ShimmerLine(widthFraction = 0.85f, height = 14.dp)
        ShimmerLine(widthFraction = 0.55f, height = 12.dp)
    }
}
