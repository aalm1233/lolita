package com.lolita.app.ui.screen.item

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.StorageNode
import com.lolita.app.domain.model.StorageTree
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.theme.LocalLolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.animation.rememberSkinFlingBehavior
import com.lolita.app.ui.theme.skin.animation.skinItemAppear
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon

private data class NodeMoveTarget(val node: StorageNode, val roomId: Long, val parentId: Long?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationDetailScreen(locationId: Long, onBack: () -> Unit, onItemClick: (Long) -> Unit,
    viewModel: LocationDetailViewModel = viewModel(), onAddItem: (roomId: Long, nodeId: Long?) -> Unit = { _, _ -> }) {
    val state by viewModel.uiState.collectAsState()
    val skin = LocalLolitaSkin.current
    val listState = rememberLazyListState()
    ObserveSkinScroll(listState)
    val snackbar = remember { SnackbarHostState() }
    var addNode by remember { mutableStateOf(false) }
    var editNode by remember { mutableStateOf<StorageNode?>(null) }
    var deleteNode by remember { mutableStateOf<StorageNode?>(null) }
    var moveNode by remember { mutableStateOf<StorageNode?>(null) }
    var nodeMoveTarget by remember { mutableStateOf<NodeMoveTarget?>(null) }
    var showBatchDestination by remember { mutableStateOf(false) }
    var batchTarget by remember { mutableStateOf<Pair<Long?, Long?>?>(null) }
    var addItems by remember { mutableStateOf(false) }
    var addingItemIds by remember { mutableStateOf<Set<Long>?>(null) }
    val currentNode = state.nodes.find { it.id == state.currentNodeId }
    val canCreate = !state.isUnassigned && (currentNode == null || currentNode.parentNodeId == null)
    LaunchedEffect(locationId) { viewModel.loadLocation(locationId) }
    LaunchedEffect(state.currentNodeId) { listState.scrollToItem(0) }
    LaunchedEffect(state.errorMessage) { state.errorMessage?.let { snackbar.showSnackbar(it); viewModel.clearError() } }
    val goBack = { if (!state.isSaving && !viewModel.openParent()) onBack() }
    BackHandler(state.currentNodeId != null || state.isSaving) { goBack() }
    Scaffold(containerColor = Color.Transparent, snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        GradientTopAppBar(title = { Text(currentNode?.name ?: state.location?.name ?: if (state.isUnassigned) "未设置位置" else "位置详情") },
            navigationIcon = { IconButton(onClick = goBack, enabled = !state.isSaving) { SkinIcon(IconKey.ArrowBack) } })
    }, bottomBar = {
        if (state.selectedItemIds.isNotEmpty()) Surface(color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(skin.spacingLarge), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                TextButton(onClick = viewModel::clearSelection, enabled = !state.isSaving) { Text("取消选择") }
                Button(onClick = { showBatchDestination = true }, enabled = !state.isSaving, modifier = Modifier.weight(1f)) {
                    Text("移动 ${state.selectedItemIds.size} 条记录")
                }
            }
        }
    }) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).testTag("storage-room-list"),
            contentPadding = PaddingValues(skin.spacingLarge),
            verticalArrangement = Arrangement.spacedBy(skin.spacingMedium), flingBehavior = rememberSkinFlingBehavior()) {
            if (state.isLoading) {
                items(3) { LolitaCard(Modifier.fillMaxWidth()) { ShimmerLine(widthFraction = 0.8f) } }
            } else if (!state.isUnassigned && state.location == null) {
                item { SkinEmptyState(iconKey = IconKey.Location, title = "房间已删除") }
            } else {
                item { LolitaSection(title = "当前位置") { row {
                    Text(state.currentPath, style = MaterialTheme.typography.titleMedium)
                    Text("共 ${state.recordCount} 条记录（含挂载小物）", style = MaterialTheme.typography.bodySmall)
                    val image = currentNode?.imageUrl ?: if (currentNode == null) state.location?.imageUrl else null
                    image?.let {
                        ImageFrame { LolitaShimmerImage(model = it, contentDescription = "位置封面",
                            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).aspectRatio(1.8f)
                                .then(if (currentNode == null) Modifier.heroSharedElement("locationImage-$locationId") else Modifier)
                                .clip(RoundedCornerShape(skin.cornerRadiusMedium)), contentScale = ContentScale.Crop) }
                    }
                    val description = currentNode?.description ?: state.location?.description.orEmpty()
                    if (description.isNotBlank()) Text(description)
                    if (currentNode != null) NodeActions(currentNode, state.isSaving,
                        onEdit = { editNode = it }, onMove = { moveNode = it }, onDelete = { deleteNode = it })
                } } }
                item { OutlinedTextField(state.searchQuery, viewModel::updateSearch, modifier = Modifier.fillMaxWidth(),
                    label = { Text("搜索名称、品牌、分类或位置路径") }, leadingIcon = { SkinIcon(IconKey.Search) }, singleLine = true) }
                if (!state.isUnassigned) {
                    item { OutlinedButton(onClick = { onAddItem(locationId, currentNode?.id) }, enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth()) { Text("新增物品") } }
                    item { Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium), modifier = Modifier.fillMaxWidth()) {
                        if (canCreate) OutlinedButton(onClick = { addNode = true }, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                            Text(if (currentNode == null) "新增柜架 / 收纳盒" else "新增层 / 格")
                        }
                        OutlinedButton(onClick = { addItems = true }, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) { Text("移入物品") }
                    } }
                    if (currentNode == null) item { TextButton(onClick = viewModel::showTemplates, enabled = !state.isSaving) { Text("添加常用收纳") } }
                }
                if (state.childNodes.isNotEmpty()) {
                    item { SectionHeader(title = if (currentNode == null) "柜架与收纳盒" else "层与格") }
                    itemsIndexed(state.childNodes, key = { _, node -> "node-${node.id}" }) { index, node ->
                        LolitaCard(Modifier.fillMaxWidth().skinItemAppear(index)) {
                            Column(verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                                TextButton(onClick = { viewModel.openNode(node.id) }, modifier = Modifier.fillMaxWidth(), enabled = !state.isSaving) {
                                    node.imageUrl?.let { LolitaShimmerImage(model = it, contentDescription = null,
                                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(skin.cornerRadiusSmall)), contentScale = ContentScale.Crop) }
                                    Column(Modifier.weight(1f).padding(start = skin.spacingMedium)) {
                                        Text(node.name, style = MaterialTheme.typography.titleMedium)
                                        Text(StorageTree.pathFor(node.locationId, node.id, state.locations, state.nodes), style = MaterialTheme.typography.bodySmall)
                                        Text("${state.nodeItemCounts[node.id] ?: 0} 条记录", style = MaterialTheme.typography.labelMedium)
                                    }
                                    SkinIcon(IconKey.KeyboardArrowRight)
                                }
                                NodeActions(node, state.isSaving, { editNode = it }, { moveNode = it }, { deleteNode = it })
                            }
                        }
                    }
                }
                item { SectionHeader(title = if (currentNode == null && !state.isUnassigned && state.searchQuery.isBlank())
                    "房间内未细分位置 · ${state.items.size} 条记录" else "物品 · ${state.items.size} 条记录") }
                itemsIndexed(state.items, key = { _, item -> "item-${item.id}" }) { index, item ->
                    StorageItemRow(item, state, item.id in state.selectedItemIds,
                        onToggle = { viewModel.toggleSelection(item.id) }, onClick = { onItemClick(item.id) }, modifier = Modifier.skinItemAppear(index))
                }
                if (state.items.isEmpty()) item { Text(if (state.searchQuery.isBlank()) "此处暂无直接存放的物品" else "没有匹配的记录",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = skin.spacingLarge)) }
            }
        }
    }
    if (addNode) StorageEditDialog(title = if (currentNode == null) "新增柜架或收纳盒" else "新增层或格", busy = state.isSaving,
        onDismiss = { addNode = false }, onConfirm = { name, description, image -> viewModel.createNode(name, description, image) { addNode = false } })
    editNode?.let { node -> StorageEditDialog("编辑收纳位置", node.name, node.description, node.imageUrl, state.isSaving,
        onDismiss = { editNode = null }, onConfirm = { name, description, image -> viewModel.updateNode(node, name, description, image) { editNode = null } }) }
    deleteNode?.let { node ->
        val descendants = StorageTree.descendantIds(node.id, state.nodes)
        val count = state.allItems.count { it.storageNodeId in descendants }
        val occupied = descendants.size > 1 || count > 0
        var clearContents by remember(node.id) { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { if (!state.isSaving) deleteNode = null }, title = { Text("删除「${node.name}」？") }, text = {
            Column { Text("包含 ${descendants.size - 1} 个子位置、$count 条记录。物品资料会保留。")
                if (occupied) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(clearContents, { clearContents = it }, enabled = !state.isSaving)
                    Text("全部移至未设置位置，并删除这些收纳位置")
                }
            }
        }, confirmButton = { TextButton(onClick = { viewModel.deleteNode(node, clearContents) { deleteNode = null } },
            enabled = !state.isSaving && (!occupied || clearContents)) { Text("确认删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteNode = null }, enabled = !state.isSaving) { Text("取消") } })
    }
    moveNode?.let { node ->
        val descendants = StorageTree.descendantIds(node.id, state.nodes)
        val excluded = if (descendants.size > 1) state.nodes.map { it.id }.toSet() else descendants + state.nodes.filter { it.parentNodeId != null }.map { it.id }
        StorageDestinationDialog(state.locations, state.nodes, { moveNode = null }, { room, parent ->
            if (room != null) { nodeMoveTarget = NodeMoveTarget(node, room, parent); moveNode = null }
        }, excludedNodeIds = excluded, allowUnassigned = false, title = "移动收纳位置至")
    }
    nodeMoveTarget?.let { target ->
        val descendants = StorageTree.descendantIds(target.node.id, state.nodes)
        val count = state.allItems.count { it.storageNodeId in descendants }
        ConfirmStorageMove("将移动 ${descendants.size} 个收纳位置及 $count 条记录",
            StorageTree.pathFor(target.roomId, target.parentId, state.locations, state.nodes), state.isSaving,
            { nodeMoveTarget = null }) { viewModel.moveNode(target.node, target.roomId, target.parentId) { nodeMoveTarget = null } }
    }
    if (showBatchDestination) StorageDestinationDialog(state.locations, state.nodes, { showBatchDestination = false }, { room, node ->
        batchTarget = room to node; showBatchDestination = false
    })
    batchTarget?.let { target -> ConfirmStorageMove("将移动 ${state.selectedItemIds.size} 条记录",
        StorageTree.pathFor(target.first, target.second, state.locations, state.nodes), state.isSaving, { batchTarget = null }) {
        viewModel.moveSelected(target.first, target.second) { batchTarget = null }
    } }
    if (addItems) StorageItemPickerDialog(state, { addItems = false }, { addingItemIds = it; addItems = false })
    addingItemIds?.let { ids -> ConfirmStorageMove("将移入 ${ids.size} 条记录", state.currentPath, state.isSaving,
        { addingItemIds = null }) { viewModel.addItems(ids) { addingItemIds = null } } }
    if (state.showTemplates) StorageTemplateDialog(state.locations, state.templates, locationId, state.isSaving,
        viewModel::hideTemplates, viewModel::adoptTemplates)
}

@Composable
private fun NodeActions(node: StorageNode, busy: Boolean, onEdit: (StorageNode) -> Unit,
    onMove: (StorageNode) -> Unit, onDelete: (StorageNode) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        TextButton(onClick = { onEdit(node) }, enabled = !busy) { Text("编辑") }
        TextButton(onClick = { onMove(node) }, enabled = !busy) { Text("移动") }
        TextButton(onClick = { onDelete(node) }, enabled = !busy) { Text("删除", color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun StorageItemRow(item: Item, state: LocationDetailUiState, checked: Boolean, onToggle: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val skin = LocalLolitaSkin.current
    LolitaCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
            Checkbox(checked, { onToggle() }, enabled = !state.isSaving,
                modifier = Modifier.semantics { contentDescription = "选择${item.name}" })
            item.imageUrls.firstOrNull()?.let { LolitaShimmerImage(model = it, contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(skin.cornerRadiusSmall)), contentScale = ContentScale.Crop) }
            TextButton(onClick = onClick, modifier = Modifier.weight(1f), enabled = !state.isSaving) {
                Column(Modifier.fillMaxWidth()) {
                    Text(item.name, style = MaterialTheme.typography.titleSmall)
                    val details = listOfNotNull(state.brandNames[item.brandId], state.categoryNames[item.categoryId]).filter { it.isNotBlank() }
                    if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    Text(StorageTree.pathFor(item.locationId, item.storageNodeId, state.locations, state.nodes), style = MaterialTheme.typography.labelSmall)
                    if (item.parentItemId != null) Text("挂载小物", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ConfirmStorageMove(title: String, destination: String, busy: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(title) }, text = { Text("目标：$destination。确认后更新实际位置。") },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !busy) { Text(if (busy) "移动中…" else "确认移动") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } })
}

@Composable
private fun StorageItemPickerDialog(state: LocationDetailUiState, onDismiss: () -> Unit, onConfirm: (Set<Long>) -> Unit) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    val listState = rememberLazyListState()
    ObserveSkinScroll(listState)
    val filtered = state.allItems.filter { item -> query.isBlank() || listOf(item.name, state.brandNames[item.brandId].orEmpty(),
        StorageTree.pathFor(item.locationId, item.storageNodeId, state.locations, state.nodes)).any { it.contains(query.trim(), true) } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("选择移入物品") }, text = {
        Column {
            OutlinedTextField(query, { query = it }, label = { Text("搜索物品或路径") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            LazyColumn(state = listState, modifier = Modifier.heightIn(max = 340.dp)) {
                items(filtered, key = { it.id }) { item ->
                    Surface(onClick = { selected = if (item.id in selected) selected - item.id else selected + item.id }) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(item.id in selected, null)
                            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                Text(item.name)
                                Text(StorageTree.pathFor(item.locationId, item.storageNodeId, state.locations, state.nodes), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { onConfirm(selected) }, enabled = selected.isNotEmpty()) { Text("核对 ${selected.size} 条记录") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
