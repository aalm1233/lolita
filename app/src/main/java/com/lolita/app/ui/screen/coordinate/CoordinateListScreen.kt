package com.lolita.app.ui.screen.coordinate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.font.FontWeight
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
    viewModel: CoordinateListViewModel = viewModel()
) {
    Scaffold(
        topBar = {
            GradientTopAppBar(title = { Text("套装管理") }, compact = true)
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
            viewModel = viewModel,
            modifier = Modifier.padding(padding)
        )
    }
}

@Composable
fun CoordinateListContent(
    onNavigateToDetail: (Long) -> Unit,
    viewModel: CoordinateListViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (uiState.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (uiState.coordinates.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Star,
                    title = "暂无套装",
                    subtitle = "点击 + 创建第一个套装"
                )
            }
        } else {
            items(uiState.coordinates, key = { it.id }) { coordinate ->
                CoordinateCard(
                    coordinate = coordinate,
                    itemCount = uiState.itemCounts[coordinate.id] ?: 0,
                    itemImages = uiState.itemImagesByCoordinate[coordinate.id] ?: emptyList(),
                    onClick = { onNavigateToDetail(coordinate.id) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

@Composable
private fun CoordinateCard(
    coordinate: Coordinate,
    itemCount: Int,
    itemImages: List<String?>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LolitaCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    coordinate.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (itemCount > 0) {
                    Surface(
                        color = Pink400.copy(alpha = 0.15f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "${itemCount} 件",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Pink400,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            if (coordinate.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = coordinate.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Item thumbnail preview row
            if (itemImages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy((-8).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemImages.take(4).forEach { imageUrl ->
                        if (imageUrl != null) {
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(36.dp)
                                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Surface(
                                modifier = Modifier
                                    .size(36.dp)
                                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                shape = CircleShape,
                                color = Color.Transparent
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Pink300.copy(alpha = 0.5f), Pink400.copy(alpha = 0.3f))
                                            ),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = Pink400
                                    )
                                }
                            }
                        }
                    }
                    if (itemCount > 4) {
                        Surface(
                            modifier = Modifier
                                .size(36.dp)
                                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                            shape = CircleShape,
                            color = Pink400.copy(alpha = 0.15f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "+${itemCount - 4}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Pink400,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            } else if (itemCount == 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "点击添加服饰",
                    style = MaterialTheme.typography.bodySmall,
                    color = Pink400.copy(alpha = 0.6f)
                )
            }

            // Creation time
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = formatCoordinateDate(coordinate.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

private val coordinateDateFormat = java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.getDefault())

private fun formatCoordinateDate(timestamp: Long): String {
    return coordinateDateFormat.format(java.util.Date(timestamp))
}
