package com.lolita.app.ui.screen.outfit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import com.lolita.app.ui.theme.skin.animation.skinItemAppear
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.animation.rememberSkinFlingBehavior
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lolita.app.ui.screen.common.LolitaShimmerImage
import com.lolita.app.ui.screen.common.heroSharedElement
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.screen.common.ShimmerLine
import com.lolita.app.ui.screen.common.ShimmerRect
import com.lolita.app.ui.screen.common.SkinEmptyState
import com.lolita.app.ui.screen.common.SwipeToDeleteContainer
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import com.valentinilk.shimmer.ShimmerBounds
import com.valentinilk.shimmer.rememberShimmer
import com.valentinilk.shimmer.shimmer

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OutfitLogListScreen(
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToEdit: (Long?) -> Unit,
    viewModel: OutfitLogListViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var logToDelete by remember { mutableStateOf<OutfitLogListItem?>(null) }

    if (logToDelete != null) {
        AlertDialog(
            onDismissRequest = { logToDelete = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除这条穿搭日记吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteOutfitLog(logToDelete!!.id)
                        logToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    SkinIcon(IconKey.Delete, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { logToDelete = null }) { Text("取消") }
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            GradientTopAppBar(title = { Text("穿搭日记") }, compact = true)
        },
        floatingActionButton = {
            if (uiState.isLoading || uiState.allLogs.isNotEmpty()) {
            FloatingActionButton(
                onClick = { onNavigateToEdit(null) },
                containerColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(16.dp)
            ) {
                SkinIcon(IconKey.Add, tint = Color.White)
            }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.allLogs.isNotEmpty()) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.search(it) },
                    placeholder = { Text("搜索穿搭日记") },
                    leadingIcon = { SkinIcon(IconKey.Search) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
            val flingBehavior = rememberSkinFlingBehavior()
            val listState = rememberLazyListState()
            ObserveSkinScroll(listState)
            if (uiState.isLoading) {
                val shimmer = rememberShimmer(ShimmerBounds.Window)
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    flingBehavior = flingBehavior
                ) {
                    items(4) {
                        OutfitLogListItemCardSkeleton(modifier = Modifier.shimmer(shimmer))
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    flingBehavior = flingBehavior
                ) {
                    if (uiState.allLogs.isEmpty()) {
                        item {
                            SkinEmptyState(
                                iconKey = IconKey.Edit,
                                title = "还没有穿搭日记",
                                subtitle = "把今天喜欢的搭配留下来",
                                actionLabel = "记录一次穿搭",
                                onAction = { onNavigateToEdit(null) }
                            )
                        }
                    } else if (uiState.logs.isEmpty()) {
                        item {
                            SkinEmptyState(
                                iconKey = IconKey.Search,
                                title = "无搜索结果",
                                subtitle = "试试其他关键词",
                                actionLabel = "清除搜索",
                                onAction = { viewModel.search("") }
                            )
                        }
                    } else {
                        itemsIndexed(uiState.logs, key = { _, log -> log.id }) { index, log ->
                            SwipeToDeleteContainer(
                                onDelete = { logToDelete = log }
                            ) {
                                OutfitLogListItemCard(
                                    log = log,
                                    onClick = { onNavigateToDetail(log.id) },
                                    onEdit = { onNavigateToEdit(log.id) },
                                    onDelete = { logToDelete = log },
                                    modifier = Modifier
                                        .skinItemAppear(index)
                                    .animateItem()
                            )
                        }
                    }
                 }
             }
         }
     }
 }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OutfitLogListItemCard(
    log: OutfitLogListItem,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    LolitaCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                )
        ) {
            // Image header or date header
            if (log.firstImageUrl != null) {
                Box {
                    LolitaShimmerImage(
                        model = log.firstImageUrl,
                        contentDescription = "穿搭照片",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .heroSharedElement("outfitLogImage-${log.id}")
                            .clip(RoundedCornerShape(topStart = LolitaSkin.current.cornerRadiusMedium, topEnd = LolitaSkin.current.cornerRadiusMedium)),
                        contentScale = ContentScale.Crop,
                        placeholderInitial = "穿"
                    )
                    // Date badge overlay
                    Surface(
                        shape = RoundedCornerShape(bottomEnd = 12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            text = log.dateString,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            } else {
                // No image — colored date header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            RoundedCornerShape(topStart = LolitaSkin.current.cornerRadiusMedium, topEnd = LolitaSkin.current.cornerRadiusMedium)
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = log.dateString,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Content area
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (log.previewNote.isNotEmpty()) {
                    Text(
                        text = log.previewNote,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Stats row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (log.imageCount > 0) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SkinIcon(
                                key = IconKey.Camera,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${log.imageCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (log.itemCount > 0) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SkinIcon(IconKey.Save, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${log.itemCount} 件",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Long-press dropdown menu
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("编辑") },
                onClick = { showMenu = false; onEdit() },
                leadingIcon = { SkinIcon(IconKey.Edit) }
            )
            DropdownMenuItem(
                text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                onClick = { showMenu = false; onDelete() },
                leadingIcon = {
                    SkinIcon(IconKey.Delete, tint = MaterialTheme.colorScheme.error)
                }
            )
        }
    }
}

@Composable
private fun OutfitLogListItemCardSkeleton(modifier: Modifier = Modifier) {
    LolitaCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        Column {
            ShimmerRect(
                width = 200.dp,
                height = 220.dp,
                shape = RoundedCornerShape(topStart = LolitaSkin.current.cornerRadiusMedium, topEnd = LolitaSkin.current.cornerRadiusMedium),
                modifier = Modifier.fillMaxWidth()
            )
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ShimmerLine(widthFraction = 0.7f, height = 16.dp)
                ShimmerLine(widthFraction = 0.5f, height = 14.dp)
            }
        }
    }
}
