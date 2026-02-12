package com.lolita.app.ui.screen.item

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.ItemPriority
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.ui.theme.Pink300
import com.lolita.app.ui.theme.Pink400
import kotlinx.coroutines.launch

/**
 * Item Detail Screen - 服饰详情界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    itemId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onNavigateToPriceManage: () -> Unit = {},
    viewModel: ItemEditViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf<String?>(null) }

    // Load item data
    LaunchedEffect(itemId) {
        viewModel.loadItem(itemId)
    }

    // Show error dialog
    if (showError != null) {
        AlertDialog(
            onDismissRequest = { showError = null },
            title = { Text("提示") },
            text = { Text(showError ?: "") },
            confirmButton = {
                TextButton(onClick = { showError = null }) {
                    Text("确定")
                }
            }
        )
    }

    // Show delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除这件服饰吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            viewModel.deleteItem(
                                onSuccess = { onBack() },
                                onError = { showError = it }
                            )
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("服饰详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(itemId) }) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Pink400,
                    titleContentColor = androidx.compose.ui.graphics.Color.White,
                    navigationIconContentColor = androidx.compose.ui.graphics.Color.White,
                    actionIconContentColor = androidx.compose.ui.graphics.Color.White
                )
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Pink400)
            }
        } else {
            val item = uiState.item
            if (item == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("服饰不存在")
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Image section
                    if (item.imageUrl != null) {
                        AsyncImage(
                            model = item.imageUrl,
                            contentDescription = item.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Pink300.copy(alpha = 0.3f)
                                )
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "暂无图片",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Pink400
                                    )
                                }
                            }
                        }
                    }

                    // Content section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Name and status
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )

                                StatusBadge(item.status)
                            }

                            if (item.status == ItemStatus.WISHED) {
                                Spacer(modifier = Modifier.height(8.dp))
                                PriorityBadge(item.priority)
                            }
                        }

                        Divider()

                        // Brand and Category
                        DetailRow(
                            label = "品牌",
                            value = uiState.brands.find { it.id == item.brandId }?.name ?: "未知"
                        )

                        DetailRow(
                            label = "类型",
                            value = uiState.categories.find { it.id == item.categoryId }?.name ?: "未知"
                        )

                        // Coordinate (if any)
                        item.coordinateId?.let { coordinateId ->
                            DetailRow(
                                label = "所属套装",
                                value = uiState.coordinates.find { it.id == coordinateId }?.name ?: "未知"
                            )
                        }

                        Divider()

                        // Description
                        if (item.description.isNotEmpty()) {
                            Column {
                                Text(
                                    text = "描述",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = item.description,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }

                        Divider()

                        // Price Management Section
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "价格信息",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                IconButton(onClick = onNavigateToPriceManage) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = "管理价格",
                                        tint = Pink400
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = Pink300.copy(alpha = 0.2f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "点击图标管理此服饰的价格和付款信息",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Divider()

                        // Metadata
                        DetailRow(
                            label = "创建时间",
                            value = formatDate(item.createdAt)
                        )

                        DetailRow(
                            label = "更新时间",
                            value = formatDate(item.updatedAt)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Status Badge Component
 */
@Composable
private fun StatusBadge(status: ItemStatus) {
    Surface(
        color = when (status) {
            ItemStatus.OWNED -> Pink300.copy(alpha = 0.3f)
            ItemStatus.WISHED -> Pink400.copy(alpha = 0.3f)
        },
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = when (status) {
                ItemStatus.OWNED -> "已拥有"
                ItemStatus.WISHED -> "愿望单"
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Priority Badge Component
 */
@Composable
private fun PriorityBadge(priority: ItemPriority) {
    Surface(
        color = when (priority) {
            ItemPriority.HIGH -> androidx.compose.ui.graphics.Color(0xFFFF6B6B).copy(alpha = 0.3f)
            ItemPriority.MEDIUM -> androidx.compose.ui.graphics.Color(0xFFFFD93D).copy(alpha = 0.3f)
            ItemPriority.LOW -> androidx.compose.ui.graphics.Color(0xFF6BCF7F).copy(alpha = 0.3f)
        },
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = when (priority) {
                ItemPriority.HIGH -> "高优先级"
                ItemPriority.MEDIUM -> "中优先级"
                ItemPriority.LOW -> "低优先级"
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Detail Row Component
 */
@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Format timestamp to readable date
 */
private fun formatDate(timestamp: Long): String {
    val javaDate = java.util.Date(timestamp)
    val format = java.text.SimpleDateFormat("yyyy年MM月dd日 HH:mm", java.util.Locale.getDefault())
    return format.format(javaDate)
}
