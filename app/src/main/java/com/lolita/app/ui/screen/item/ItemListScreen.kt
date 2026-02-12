package com.lolita.app.ui.screen.item

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.ui.screen.common.EmptyState
import com.lolita.app.ui.theme.Pink300
import com.lolita.app.ui.theme.Pink400

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ItemListScreen(
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToEdit: (Long?) -> Unit,
    onNavigateToWishlist: () -> Unit,
    viewModel: ItemListViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var itemToDelete by remember { mutableStateOf<Item?>(null) }

    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除「${itemToDelete!!.name}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteItem(itemToDelete!!)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的服饰") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Pink300,
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToEdit(null) },
                containerColor = Pink400,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加服饰", tint = Color.White)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            FilterChipsRow(
                selectedFilter = uiState.filterStatus,
                onFilterSelected = { viewModel.filterByStatus(it) },
                onNavigateToWishlist = onNavigateToWishlist
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Pink400)
                }
            } else if (uiState.filteredItems.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Home,
                    title = "暂无服饰",
                    subtitle = "点击右下角 + 添加新服饰",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(
                        items = uiState.filteredItems,
                        key = { it.id }
                    ) { item ->
                        ItemCard(
                            item = item,
                            onClick = { onNavigateToDetail(item.id) },
                            onEdit = { onNavigateToEdit(item.id) },
                            onDelete = { itemToDelete = item },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipsRow(
    selectedFilter: ItemStatus?,
    onFilterSelected: (ItemStatus?) -> Unit,
    onNavigateToWishlist: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedFilter == null,
            onClick = { onFilterSelected(null) },
            label = { Text("全部") },
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = selectedFilter == ItemStatus.OWNED,
            onClick = { onFilterSelected(ItemStatus.OWNED) },
            label = { Text("已拥有") },
            leadingIcon = if (selectedFilter == ItemStatus.OWNED) {
                { Icon(Icons.Default.Face, null, modifier = Modifier.size(16.dp)) }
            } else null,
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = selectedFilter == ItemStatus.WISHED,
            onClick = { onFilterSelected(ItemStatus.WISHED) },
            label = { Text("愿望单") },
            leadingIcon = if (selectedFilter == ItemStatus.WISHED) {
                { Icon(Icons.Default.Favorite, null, modifier = Modifier.size(16.dp)) }
            } else null,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onNavigateToWishlist,
            modifier = Modifier
                .size(48.dp)
                .background(Pink300.copy(alpha = 0.2f), CircleShape)
        ) {
            Icon(Icons.Default.Star, contentDescription = "优先级愿望单", tint = Pink400)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemCard(
    item: Item,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑", tint = Pink400)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("编辑") },
                            onClick = { showMenu = false; onEdit() },
                            leadingIcon = { Icon(Icons.Default.Edit, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            onClick = { showMenu = false; onDelete() },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                            },
                            colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (item.description.isNotEmpty()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Surface(
                color = when (item.status) {
                    ItemStatus.OWNED -> Pink300.copy(alpha = 0.3f)
                    ItemStatus.WISHED -> Pink400.copy(alpha = 0.3f)
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = when (item.status) {
                        ItemStatus.OWNED -> "已拥有"
                        ItemStatus.WISHED -> "愿望单"
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
