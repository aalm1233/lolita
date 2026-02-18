package com.lolita.app.ui.screen.coordinate

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lolita.app.data.local.entity.Coordinate
import com.lolita.app.ui.screen.common.EmptyState
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.theme.Pink300
import com.lolita.app.ui.theme.Pink400

@Composable
fun CoordinateListScreen(
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToAdd: () -> Unit = {},
    onNavigateToEdit: (Long) -> Unit = {},
    viewModel: CoordinateListViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            GradientTopAppBar(
                title = { Text("套装管理") },
                compact = true,
                actions = {
                    IconButton(onClick = {
                        val next = when (uiState.columnsPerRow) { 1 -> 2; 2 -> 3; else -> 1 }
                        viewModel.setColumns(next)
                    }) {
                        Icon(
                            imageVector = when (uiState.columnsPerRow) {
                                1 -> Icons.Default.ViewAgenda
                                2 -> Icons.Default.GridView
                                else -> Icons.Default.Apps
                            },
                            contentDescription = "切换列数",
                            tint = Pink400
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAdd,
                containerColor = Pink400,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加套装", tint = Color.White)
            }
        }
    ) { padding ->
        CoordinateListContent(
            onNavigateToDetail = onNavigateToDetail,
            onNavigateToEdit = onNavigateToEdit,
            viewModel = viewModel,
            modifier = Modifier.padding(padding)
        )
    }
}

@Composable
fun CoordinateListContent(
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToEdit: (Long) -> Unit = {},
    viewModel: CoordinateListViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var coordinateToDelete by remember { mutableStateOf<Coordinate?>(null) }

    coordinateToDelete?.let { coord ->
        AlertDialog(
            onDismissRequest = { coordinateToDelete = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除套装「${coord.name}」吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCoordinate(coord)
                        coordinateToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { coordinateToDelete = null }) { Text("取消") }
            }
        )
    }
    if (uiState.isLoading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (uiState.coordinates.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            EmptyState(
                icon = Icons.Default.Star,
                title = "暂无套装",
                subtitle = "点击 + 创建第一个套装"
            )
        }
    } else if (uiState.columnsPerRow == 1) {
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(uiState.coordinates, key = { it.id }) { coordinate ->
                CoordinateCard(
                    coordinate = coordinate,
                    itemCount = uiState.itemCounts[coordinate.id] ?: 0,
                    itemImages = uiState.itemImagesByCoordinate[coordinate.id] ?: emptyList(),
                    totalPrice = uiState.priceByCoordinate[coordinate.id] ?: 0.0,
                    onClick = { onNavigateToDetail(coordinate.id) },
                    onEdit = { onNavigateToEdit(coordinate.id) },
                    onDelete = { coordinateToDelete = coordinate },
                    modifier = Modifier.animateItem()
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(uiState.columnsPerRow),
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(uiState.coordinates, key = { it.id }) { coordinate ->
                CoordinateGridCard(
                    coordinate = coordinate,
                    itemCount = uiState.itemCounts[coordinate.id] ?: 0,
                    itemImages = uiState.itemImagesByCoordinate[coordinate.id] ?: emptyList(),
                    totalPrice = uiState.priceByCoordinate[coordinate.id] ?: 0.0,
                    onClick = { onNavigateToDetail(coordinate.id) },
                    onEdit = { onNavigateToEdit(coordinate.id) },
                    onDelete = { coordinateToDelete = coordinate }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CoordinateCard(
    coordinate: Coordinate,
    itemCount: Int,
    itemImages: List<String?>,
    totalPrice: Double,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    LolitaCard(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { showMenu = true })
        ) {
            Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (coordinate.imageUrl != null) {
                    AsyncImage(
                        model = coordinate.imageUrl,
                        contentDescription = coordinate.name,
                        modifier = Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(80.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.linearGradient(listOf(Pink300.copy(alpha = 0.5f), Pink400.copy(alpha = 0.3f))),
                                RoundedCornerShape(12.dp)
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Star, null, Modifier.size(28.dp), tint = Pink400)
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            coordinate.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (totalPrice > 0) {
                            Text(
                                "¥%.0f".format(totalPrice),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Pink400
                            )
                        }
                    }
// __CARD_PART2__
                    if (coordinate.description.isNotEmpty()) {
                        Text(
                            coordinate.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (itemCount > 0) {
                            Surface(color = Pink400.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                                Text(
                                    "${itemCount} 件",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Pink400,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("编辑") }, onClick = { showMenu = false; onEdit() })
                DropdownMenuItem(text = { Text("删除") }, onClick = { showMenu = false; onDelete() })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CoordinateGridCard(
    coordinate: Coordinate,
    itemCount: Int,
    itemImages: List<String?>,
    totalPrice: Double,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    LolitaCard(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { showMenu = true })
        ) {
            Column {
                Box {
                    if (coordinate.imageUrl != null) {
                        AsyncImage(
                            model = coordinate.imageUrl,
                            contentDescription = coordinate.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.8f)
                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.8f)
                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                .background(
                                    Brush.linearGradient(listOf(Pink300.copy(alpha = 0.5f), Pink400.copy(alpha = 0.3f)))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Star, null, Modifier.size(36.dp), tint = Pink400)
                        }
                    }
// __GRID_OVERLAY__
                    // 价格标签（右上角）
                    if (totalPrice > 0) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                            color = Color.Black.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "¥%.0f".format(totalPrice),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    // 件数标签（左上角）
                    if (itemCount > 0) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                            color = Pink400.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${itemCount}件",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
                // 信息区域
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = coordinate.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (coordinate.description.isNotEmpty()) {
                        Text(
                            text = coordinate.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("编辑") }, onClick = { showMenu = false; onEdit() })
                DropdownMenuItem(text = { Text("删除") }, onClick = { showMenu = false; onDelete() })
            }
        }
    }
}

private fun formatCoordinateDate(timestamp: Long): String {
    val dateFormat = java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.getDefault())
    return dateFormat.format(java.util.Date(timestamp))
}