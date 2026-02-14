package com.lolita.app.ui.screen.outfit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Checkroom
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lolita.app.data.local.entity.Item
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.theme.Pink400
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitLogDetailScreen(
    logId: Long,
    onBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    onNavigateToItem: (Long) -> Unit,
    viewModel: OutfitLogDetailViewModel = viewModel(
        factory = OutfitLogDetailViewModelFactory(logId)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    var removeConfirmItem by remember { mutableStateOf<Item?>(null) }

    if (removeConfirmItem != null) {
        AlertDialog(
            onDismissRequest = { removeConfirmItem = null },
            title = { Text("确认移除") },
            text = { Text("确定要移除关联服饰 \"${removeConfirmItem?.name}\" 吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        removeConfirmItem?.let { viewModel.removeItem(it.id) }
                        removeConfirmItem = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("移除") }
            },
            dismissButton = {
                TextButton(onClick = { removeConfirmItem = null }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            GradientTopAppBar(
                title = { Text("穿搭详情") },
                compact = true,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigateToEdit(logId) }) {
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
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val log = uiState.log ?: return@Scaffold
            val dateFormat = SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.CHINA)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Date header
                item {
                    Text(
                        text = dateFormat.format(Date(log.date)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Pink400,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                // Note section
                if (log.note.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Text(
                                text = log.note,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                // Photos section
                if (log.imageUrls.isNotEmpty()) {
                    item {
                        Text(
                            text = "照片 (${log.imageUrls.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    if (log.imageUrls.size == 1) {
                        item {
                            AsyncImage(
                                model = log.imageUrls.first(),
                                contentDescription = "穿搭照片",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.FillWidth
                            )
                        }
                    } else {
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(log.imageUrls) { imageUrl ->
                                    AsyncImage(
                                        model = imageUrl,
                                        contentDescription = "穿搭照片",
                                        modifier = Modifier
                                            .width(220.dp)
                                            .aspectRatio(0.75f)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                }

                // Associated items section
                if (uiState.items.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Checkroom,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Pink400
                            )
                            Text(
                                text = "关联服饰 (${uiState.items.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    items(uiState.items, key = { it.id }) { item ->
                        DetailItemCard(
                            item = item,
                            onClick = { onNavigateToItem(item.id) },
                            onRemove = { removeConfirmItem = item },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailItemCard(
    item: Item,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (item.imageUrl != null) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.name,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Checkroom,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = "移除", modifier = Modifier.size(18.dp))
            }
        }
    }
}

class OutfitLogDetailViewModelFactory(private val logId: Long) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return OutfitLogDetailViewModel(
            outfitLogRepository = com.lolita.app.di.AppModule.outfitLogRepository(),
            logId = logId
        ) as T
    }
}
