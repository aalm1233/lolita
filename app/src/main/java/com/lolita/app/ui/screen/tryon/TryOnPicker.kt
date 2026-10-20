package com.lolita.app.ui.screen.tryon

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.domain.model.TryOnRole
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.component.skinClickable
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TryOnPicker(
    state: TryOnUiState,
    onSearch: (String) -> Unit,
    onCategory: (Long?) -> Unit,
    onToggle: (Long) -> Unit,
    onStatus: (ItemStatus?) -> Unit = {},
    onDismiss: () -> Unit
) {
    val skin = LolitaSkin.current
    val grid = rememberLazyGridState()
    val filters = rememberLazyListState()
    val chosen = rememberLazyListState()
    ObserveSkinScroll(grid)
    ObserveSkinScroll(filters)
    ObserveSkinScroll(chosen)
    LaunchedEffect(state.searchQuery, state.selectedCategoryId, state.statusFilter) { grid.scrollToItem(0) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = { GradientTopAppBar(title = { Text("选择试衣物品") }, navigationIcon = {
                IconButton(onClick = onDismiss) { SkinIcon(IconKey.ArrowBack, modifier = Modifier.semantics { contentDescription = "返回试衣" }) }
            }) },
            bottomBar = {
                Surface(tonalElevation = skin.cardElevation) {
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().imePadding()
                        .padding(skin.spacingLarge)) { Text("完成搭配选择（${state.selected.size}件）") }
                }
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { padding ->
            LazyVerticalGrid(
                columns = GridCells.Adaptive(108.dp), state = grid,
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).testTag("tryOnPicker"),
                contentPadding = PaddingValues(skin.spacingLarge),
                horizontalArrangement = Arrangement.spacedBy(skin.spacingMedium),
                verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)
            ) {
                item(key = "controls", span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                        Text("可选择已拥有与心愿物品，包含鞋履、袜装和挂载小物；筛选保留已选。搭配超过8件时，回到试衣页明确选择本次子集。", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(value = state.searchQuery, onValueChange = onSearch,
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text("搜索服饰名称") },
                            shape = RoundedCornerShape(skin.cornerRadiusMedium),
                            leadingIcon = { SkinIcon(IconKey.Search) })
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(skin.spacingSmall), verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                            listOf(null to "全部状态", ItemStatus.OWNED to "已拥有", ItemStatus.WISHED to "心愿").forEach { (status, label) ->
                                FilterChip(selected = state.statusFilter == status, onClick = { onStatus(status) },
                                    enabled = !state.busy, label = { Text(label) })
                            }
                        }
                        LazyRow(state = filters, horizontalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                            item { FilterChip(selected = state.selectedCategoryId == null, onClick = { onCategory(null) }, label = { Text("全部") }) }
                            items(state.availableCategories, key = { it.id }) { category ->
                                FilterChip(selected = state.selectedCategoryId == category.id,
                                    onClick = { onCategory(category.id) }, label = { Text(category.name) })
                            }
                        }
                        if (state.selected.isNotEmpty()) {
                            Text("已选 ${state.selected.size} 件 · 点按下方名称可移除", style = MaterialTheme.typography.labelMedium)
                            LazyRow(state = chosen, horizontalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                                items(state.selected, key = { it.itemId }) { row ->
                                    InputChip(selected = true, onClick = { onToggle(row.itemId) }, label = { Text(row.name) },
                                        trailingIcon = { SkinIcon(IconKey.Close, modifier = Modifier.size(16.dp)) })
                                }
                            }
                        }
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                if (state.isLoading) {
                    items(6, key = { "skeleton-$it" }) { ShimmerRect(width = 108.dp, height = 150.dp, modifier = Modifier.fillMaxWidth()) }
                } else if (state.visibleItems.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(if (state.loadFailed) "衣橱读取失败，请返回后重试" else if (state.items.isEmpty()) "还没有已拥有或心愿物品" else "没有匹配物品，请调整搜索、分类或状态",
                            modifier = Modifier.fillMaxWidth().padding(vertical = skin.spacingExtraLarge))
                    }
                } else gridItems(state.visibleItems, key = { it.id }) { item ->
                    val selected = state.selected.any { it.itemId == item.id }
                    LolitaCard(variant = CardVariant.GALLERY, contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.fillMaxWidth().semantics {
                            this.selected = selected
                            contentDescription = "${if (selected) "已选" else "选择"}服饰：${item.name}"
                        }.skinClickable(enabled = !state.busy, onClick = { onToggle(item.id) })) {
                        Column {
                            TryOnItemImage(item.imageUrls.firstOrNull().orEmpty(), item.name,
                                Modifier.fillMaxWidth().aspectRatio(0.8f))
                            Column(Modifier.padding(skin.spacingMedium), verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                                Text(item.name, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                                val status = if (item.status == ItemStatus.WISHED) "心愿" else "已拥有"
                                Text("$status · ${if (selected) "已选择" else if (item.imageUrls.none { it.isNotBlank() }) "无参考图" else "选择"}",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TryOnImagePicker(item: Item, selectedPath: String, onSelect: (String) -> Unit, onDismiss: () -> Unit, material: com.lolita.app.data.local.entity.ItemTryOnMaterial? = null) {
    val skin = LolitaSkin.current
    val list = rememberLazyListState()
    ObserveSkinScroll(list)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("选择一张参考图片") },
        text = {
            LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                material?.let { asset -> item("confirmed-material") {
                    LolitaCard(variant = CardVariant.COMPACT, modifier = Modifier.fillMaxWidth().skinClickable { onSelect(asset.imagePath); onDismiss() }) {
                        Column { TryOnItemImage(asset.imagePath, "已确认试衣素材", Modifier.fillMaxWidth().height(160.dp)); Text("已确认主视素材${if (selectedPath == asset.imagePath) " · 已选" else ""}") }
                    }
                } }
                itemsIndexed(item.imageUrls.filter { it.isNotBlank() }.distinct()) { imageIndex, path ->
                    val index = imageIndex + 1
                    LolitaCard(variant = CardVariant.COMPACT, modifier = Modifier.fillMaxWidth()
                        .semantics { selected = path == selectedPath }
                        .skinClickable(onClick = { onSelect(path); onDismiss() })) {
                        Column {
                            TryOnItemImage(path, "参考图片 $index", Modifier.fillMaxWidth().height(180.dp))
                            Text("参考图片 $index${if (path == selectedPath) " · 已选" else ""}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("返回") } })
}

@Composable
internal fun TryOnRolePicker(selectedRole: TryOnRole, onSelect: (TryOnRole) -> Unit, onDismiss: () -> Unit) {
    val list = rememberLazyListState()
    ObserveSkinScroll(list)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("核对穿戴位置") },
        text = {
            LazyColumn(state = list) {
                items(TryOnRole.entries) { role ->
                    Row(Modifier.fillMaxWidth().skinClickable(onClick = { onSelect(role); onDismiss() })
                        .padding(vertical = LolitaSkin.current.spacingSmall), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = role == selectedRole, onClick = null)
                        Text(role.label, Modifier.padding(start = LolitaSkin.current.spacingSmall))
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("返回") } })
}

@Composable
internal fun TryOnItemImage(path: String, description: String, modifier: Modifier = Modifier) {
    val skin = LolitaSkin.current
    if (path.isNotBlank()) LolitaShimmerImage(
        model = File(path), contentDescription = description,
        modifier = modifier.clip(RoundedCornerShape(skin.cornerRadiusSmall)),
        contentScale = ContentScale.Fit, circularRevealEnabled = false,
        placeholderInitial = description.firstOrNull()?.toString()
    ) else Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(skin.cornerRadiusSmall)) {
        Box(contentAlignment = Alignment.Center) { SkinIcon(IconKey.Image, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
