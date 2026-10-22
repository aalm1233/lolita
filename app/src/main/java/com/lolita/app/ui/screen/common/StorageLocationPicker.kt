package com.lolita.app.ui.screen.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lolita.app.data.local.entity.Location
import com.lolita.app.data.local.entity.StorageNode
import com.lolita.app.domain.model.StorageTree
import com.lolita.app.ui.theme.LocalLolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon

/** A searchable room/cabinet/compartment picker shared with the item editor. */
@Composable
fun StorageLocationPicker(
    selectedLocationId: Long?,
    selectedNodeId: Long?,
    locations: List<Location>,
    nodes: List<StorageNode>,
    onLocationSelected: (Long?, Long?) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pair<Long?, Long?>?>(null) }
    LaunchedEffect(enabled) { if (!enabled) { showPicker = false; pending = null } }
    OutlinedButton(onClick = { showPicker = true }, enabled = enabled, modifier = modifier.fillMaxWidth()) {
        SkinIcon(IconKey.Location, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(LocalLolitaSkin.current.spacingMedium))
        Column(Modifier.weight(1f)) {
            Text("存放位置（可选）", style = MaterialTheme.typography.labelSmall)
            Text(StorageTree.pathFor(selectedLocationId, selectedNodeId, locations, nodes))
        }
        SkinIcon(IconKey.KeyboardArrowRight)
    }
    if (showPicker) {
        StorageDestinationDialog(locations, nodes, onDismiss = { showPicker = false }, onSelect = { room, node ->
            if (selectedNodeId != null && room != selectedLocationId) pending = room to node
            else {
                onLocationSelected(room, node)
                showPicker = false
            }
        })
    }
    pending?.let { target ->
        AlertDialog(onDismissRequest = { pending = null },
            title = { Text("更换房间并清除原层格？") },
            text = { Text("原柜架和层格属于其他房间。本次将使用新选择的位置。") },
            confirmButton = { TextButton(onClick = {
                onLocationSelected(target.first, target.second)
                pending = null
                showPicker = false
            }) { Text("清除并更换") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("取消") } })
    }
}

@Composable
fun StorageDestinationDialog(
    locations: List<Location>,
    nodes: List<StorageNode>,
    onDismiss: () -> Unit,
    onSelect: (Long?, Long?) -> Unit,
    excludedNodeIds: Set<Long> = emptySet(),
    allowUnassigned: Boolean = true,
    title: String = "选择存放位置"
) {
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    ObserveSkinScroll(listState)
    val choices = remember(locations, nodes, excludedNodeIds) {
        locations.flatMap { room ->
            listOf(Triple(room.id, null as Long?, room.name)) +
                nodes.filter { it.locationId == room.id && it.id !in excludedNodeIds }
                    .map { Triple(room.id, it.id as Long?, StorageTree.pathFor(room.id, it.id, locations, nodes)) }
                    .sortedBy { it.third }
        }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(LocalLolitaSkin.current.spacingMedium)) {
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("搜索位置路径") },
                leadingIcon = { SkinIcon(IconKey.Search) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                if (allowUnassigned && (query.isBlank() || "未设置位置".contains(query))) {
                    item { TextButton(onClick = { onSelect(null, null) }, modifier = Modifier.fillMaxWidth()) { Text("未设置位置") } }
                }
                val filtered = choices.filter { it.third.contains(query.trim(), ignoreCase = true) }
                items(filtered, key = { "${it.first}:${it.second}" }) { choice ->
                    TextButton(onClick = { onSelect(choice.first, choice.second) }, modifier = Modifier.fillMaxWidth()) {
                        Text(choice.third, modifier = Modifier.fillMaxWidth())
                    }
                }
                if (filtered.isEmpty()) item { Text("没有匹配的位置", modifier = Modifier.padding(vertical = 12.dp)) }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("返回") } })
}
