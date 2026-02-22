package com.lolita.app.ui.screen.item

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.ItemPriority
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.local.entity.PriceType
import com.lolita.app.ui.screen.common.GradientTopAppBar
import kotlinx.coroutines.launch
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon

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
    onNavigateToRecommendation: (Long) -> Unit = {},
    viewModel: ItemEditViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf<String?>(null) }

    // Load item data — reload when screen is resumed (e.g. after editing)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(itemId, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            viewModel.loadItem(itemId)
        }
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
                        showDeleteDialog = false
                        coroutineScope.launch {
                            viewModel.deleteItem()
                                .onSuccess { onBack() }
                                .onFailure { showError = it.message }
                        }
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
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    val hasImage = uiState.item?.imageUrl != null

    Scaffold(
        topBar = {
            if (!hasImage) {
                GradientTopAppBar(
                    title = { Text("服饰详情") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            SkinIcon(IconKey.ArrowBack)
                        }
                    },
                    actions = {
                        IconButton(onClick = { onEdit(itemId) }) {
                            SkinIcon(IconKey.Edit)
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            SkinIcon(IconKey.Delete, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            }
        },
        contentWindowInsets = if (hasImage) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = if (hasImage) 0.dp else padding.calculateTopPadding(),
                                bottom = padding.calculateBottomPadding()
                            )
                            .verticalScroll(rememberScrollState())
                    ) {
                    // Image section
                    if (item.imageUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(item.imageUrl)
                                .crossfade(300)
                                .build(),
                            contentDescription = item.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(380.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(88.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                                )
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = item.name.firstOrNull()?.toString() ?: "?",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "暂无图片",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
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

                        HorizontalDivider(color = MaterialTheme.colorScheme.primaryContainer, thickness = 1.dp)

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

                        // Color, Season, Style
                        item.color?.let { color ->
                            if (color.isNotEmpty()) DetailRow(label = "颜色", value = color)
                        }
                        item.season?.let { season ->
                            if (season.isNotEmpty()) DetailRow(label = "季节", value = season.replace(",", "、"))
                        }
                        item.style?.let { style ->
                            if (style.isNotEmpty()) DetailRow(label = "风格", value = style)
                        }
                        item.source?.let { source ->
                            if (source.isNotEmpty()) DetailRow(label = "来源", value = source)
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.primaryContainer, thickness = 1.dp)

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

                        // Size info
                        if (!item.size.isNullOrEmpty() || item.sizeChartImageUrl != null) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.primaryContainer, thickness = 1.dp)

                            Column {
                                Text(
                                    text = "尺码信息",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (!item.size.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    DetailRow(label = "尺码", value = item.size!!)
                                }
                                if (item.sizeChartImageUrl != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(item.sizeChartImageUrl)
                                            .crossfade(300)
                                            .build(),
                                        contentDescription = "尺码表",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(MaterialTheme.shapes.medium),
                                        contentScale = ContentScale.FillWidth
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.primaryContainer, thickness = 1.dp)

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
                                OutlinedButton(
                                    onClick = onNavigateToPriceManage,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("管理价格", style = MaterialTheme.typography.labelMedium)
                                    Spacer(Modifier.width(4.dp))
                                    SkinIcon(IconKey.ArrowForward, modifier = Modifier.size(16.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (uiState.pricesWithPayments.isEmpty()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            "暂无价格信息，点击图标添加",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                        val dateFormat = remember { java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.getDefault()) }
                                uiState.pricesWithPayments.forEach { priceWithPayments ->
                                    val price = priceWithPayments.price
                                    val payments = priceWithPayments.payments
                                    val paidAmount = payments.filter { it.isPaid }.sumOf { it.amount }

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    when (price.type) {
                                                        PriceType.FULL -> "全价"
                                                        PriceType.DEPOSIT_BALANCE -> "定金+尾款"
                                                    },
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    "¥${String.format("%.2f", price.totalPrice)}",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            if (price.type == PriceType.DEPOSIT_BALANCE) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        "定金 ¥${String.format("%.2f", price.deposit ?: 0.0)}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        "尾款 ¥${String.format("%.2f", price.balance ?: 0.0)}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            if (payments.isNotEmpty()) {
                                                val unpaidAmount = payments.filter { !it.isPaid }.sumOf { it.amount }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        "已付 ¥${String.format("%.2f", paidAmount)}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    if (unpaidAmount > 0) {
                                                        Text(
                                                            "待付 ¥${String.format("%.2f", unpaidAmount)}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                }
                                            }

                                            price.purchaseDate?.let { date ->
                                                Text(
                                                    "购买日期: ${dateFormat.format(java.util.Date(date))}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }

                        // 推荐搭配按钮
                        if (item.status == ItemStatus.OWNED) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                OutlinedButton(
                                    onClick = { onNavigateToRecommendation(item.id) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                ) {
                                    SkinIcon(IconKey.Star, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("推荐搭配")
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.primaryContainer, thickness = 1.dp)
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.primaryContainer, thickness = 1.dp)

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

                    // Floating top bar overlay for immersive image
                    if (hasImage) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.5f),
                                            Color.Transparent
                                        ),
                                        startY = 0f,
                                        endY = 160f
                                    )
                                )
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = onBack) {
                                    SkinIcon(IconKey.ArrowBack, tint = Color.White)
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(onClick = { onEdit(itemId) }) {
                                    SkinIcon(IconKey.Edit, tint = Color.White)
                                }
                                IconButton(onClick = { showDeleteDialog = true }) {
                                    SkinIcon(IconKey.Delete, tint = Color.White.copy(alpha = 0.8f))
                                }
                            }
                        }
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
            ItemStatus.OWNED -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
            ItemStatus.WISHED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            ItemStatus.PENDING_BALANCE -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
        },
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = when (status) {
                ItemStatus.OWNED -> "已拥有"
                ItemStatus.WISHED -> "愿望单"
                ItemStatus.PENDING_BALANCE -> "待补尾款"
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
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(6.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            ) {}
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
