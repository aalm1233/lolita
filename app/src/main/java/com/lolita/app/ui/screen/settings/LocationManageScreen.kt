package com.lolita.app.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.Location
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.theme.LocalLolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.animation.rememberSkinFlingBehavior
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationManageScreen(onBack: () -> Unit, viewModel: LocationManageViewModel = viewModel(),
    onLocationClick: (Long) -> Unit = {}) {
    val state by viewModel.uiState.collectAsState()
    val skin = LocalLolitaSkin.current
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    ObserveSkinScroll(listState)
    BackHandler(state.isSaving) { }
    LaunchedEffect(state.errorMessage) { state.errorMessage?.let { snackbar.showSnackbar(it); viewModel.clearError() } }
    Scaffold(containerColor = Color.Transparent, snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        GradientTopAppBar(title = { Text("房间与收纳") }, navigationIcon = {
            IconButton(onClick = onBack, enabled = !state.isSaving) { SkinIcon(IconKey.ArrowBack) }
        })
    }) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(skin.spacingLarge), verticalArrangement = Arrangement.spacedBy(skin.cardGap),
            flingBehavior = rememberSkinFlingBehavior()) {
            item { LolitaSection(title = "实际存放位置") { row {
                Text("房间下可添加柜架、收纳盒，再添加层或格。数量按物品记录计算，包含挂载小物。")
                Button(onClick = viewModel::showAddDialog, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) { Text("新增房间") }
                OutlinedButton(onClick = viewModel::showTemplates, enabled = !state.isSaving && state.locations.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()) { Text("添加常用收纳") }
            } } }
            if (state.isLoading) items(3) { LolitaCard(Modifier.fillMaxWidth()) { ShimmerLine(widthFraction = 0.8f) } }
            if (!state.isLoading && state.locations.isEmpty()) item { SkinEmptyState(iconKey = IconKey.Location, title = "暂无房间，先按实际布局添加") }
            items(state.locations, key = { it.id }) { room ->
                RoomCard(room, state.locationItemCounts[room.id] ?: 0, state.nodes.count { it.locationId == room.id }, state.isSaving,
                    { onLocationClick(room.id) }, { viewModel.showEditDialog(room) }, { viewModel.showDeleteConfirm(room) })
            }
        }
    }
    if (state.showAddDialog) StorageEditDialog("新增房间", busy = state.isSaving,
        onDismiss = viewModel::hideAddDialog, onConfirm = viewModel::addLocation)
    state.editingLocation?.let { room -> StorageEditDialog("编辑房间", room.name, room.description, room.imageUrl, state.isSaving,
        onDismiss = viewModel::hideEditDialog, onConfirm = { name, description, image -> viewModel.updateLocation(room, name, description, image) }) }
    state.showDeleteConfirm?.let { room ->
        val nodeCount = state.nodes.count { it.locationId == room.id }
        val occupied = nodeCount > 0 || state.deleteItemCount > 0
        var clearContents by remember(room.id) { mutableStateOf(false) }
        AlertDialog(onDismissRequest = viewModel::hideDeleteConfirm, title = { Text("删除房间「${room.name}」？") }, text = {
            Column { Text("包含 $nodeCount 个收纳位置、${state.deleteItemCount} 条记录。物品资料会保留。")
                if (occupied) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(clearContents, { clearContents = it }, enabled = !state.isSaving)
                    Text("全部移至未设置位置，并删除房间及其收纳位置")
                }
            }
        }, confirmButton = { TextButton(onClick = { viewModel.deleteLocation(room, clearContents) },
            enabled = !state.isSaving && (!occupied || clearContents)) { Text("确认删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = viewModel::hideDeleteConfirm, enabled = !state.isSaving) { Text("取消") } })
    }
    if (state.showTemplates) StorageTemplateDialog(state.locations, state.templates, null, state.isSaving,
        viewModel::hideTemplates, viewModel::adoptTemplates)
}

@Composable
private fun RoomCard(room: Location, count: Int, nodeCount: Int, busy: Boolean,
    onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val skin = LocalLolitaSkin.current
    LolitaCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
            TextButton(onClick = onOpen, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                room.imageUrl?.let { LolitaShimmerImage(model = it, contentDescription = null,
                    modifier = Modifier.size(60.dp).clip(RoundedCornerShape(skin.cornerRadiusSmall)), contentScale = ContentScale.Crop) }
                Column(Modifier.weight(1f).padding(start = skin.spacingMedium)) {
                    Text(room.name, style = MaterialTheme.typography.titleMedium)
                    if (room.description.isNotBlank()) Text(room.description, style = MaterialTheme.typography.bodySmall)
                    Text("$nodeCount 个收纳位置 · $count 条记录", style = MaterialTheme.typography.labelMedium)
                }
                SkinIcon(IconKey.KeyboardArrowRight)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEdit, enabled = !busy) { Text("编辑房间") }
                TextButton(onClick = onDelete, enabled = !busy) { Text("删除", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
