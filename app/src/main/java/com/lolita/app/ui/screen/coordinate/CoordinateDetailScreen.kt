package com.lolita.app.ui.screen.coordinate

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoordinateDetailScreen(
    coordinateId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: CoordinateDetailViewModel = viewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    var itemToRemove by remember { mutableStateOf<Item?>(null) }

    if (itemToRemove != null) {
        AlertDialog(
            onDismissRequest = { itemToRemove = null },
            title = { Text("确认移除") },
            text = { Text("确定要从套装中移除「${itemToRemove!!.name}」吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            viewModel.removeItemFromCoordinate(itemToRemove!!)
                        }
                        itemToRemove = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("移除") }
            },
            dismissButton = {
                TextButton(onClick = { itemToRemove = null }) { Text("取消") }
            }
        )
    }

    LaunchedEffect(coordinateId) {
        viewModel.loadCoordinate(coordinateId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.coordinate?.name ?: "套装详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(coordinateId) }) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.coordinate == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text("套装不存在")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    CoordinateInfoCard(
                        coordinate = uiState.coordinate!!,
                        itemCount = uiState.items.size
                    )
                }

                item {
                    Text(
                        "包含服饰 (${uiState.items.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (uiState.items.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "暂无服饰",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(uiState.items) { item ->
                        CoordinateItemCard(
                            item = item,
                            onRemove = { itemToRemove = item }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CoordinateInfoCard(
    coordinate: com.lolita.app.data.local.entity.Coordinate,
    itemCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                coordinate.name,
                style = MaterialTheme.typography.headlineSmall
            )

            if (coordinate.description.isNotEmpty()) {
                Text(
                    coordinate.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "服饰: $itemCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CoordinateItemCard(
    item: Item,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium
                )
                if (item.description.isNotEmpty()) {
                    Text(
                        item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusBadge(item.status)
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "移除")
            }
        }
    }
}

@Composable
private fun StatusBadge(status: ItemStatus) {
    Surface(
        color = when (status) {
            ItemStatus.OWNED -> MaterialTheme.colorScheme.primaryContainer
            ItemStatus.WISHED -> MaterialTheme.colorScheme.secondaryContainer
        },
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = when (status) {
                ItemStatus.OWNED -> "已拥有"
                ItemStatus.WISHED -> "愿望单"
            },
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = when (status) {
                ItemStatus.OWNED -> MaterialTheme.colorScheme.onPrimaryContainer
                ItemStatus.WISHED -> MaterialTheme.colorScheme.onSecondaryContainer
            }
        )
    }
}
