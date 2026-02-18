package com.lolita.app.ui.screen.`import`

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.model.TaobaoOrder
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.theme.Pink400

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaobaoImportScreen(
    onBack: () -> Unit,
    onNavigateToDetail: (selectedItemsJson: String) -> Unit,
    viewModel: TaobaoImportViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // 本地图片选择器
    var pendingImageIndex by remember { mutableIntStateOf(-1) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> viewModel.onLocalImageSelected(pendingImageIndex, uri) }

    when (uiState.currentStep) {
        ImportStep.SELECT -> OrderSelectContent(
            uiState = uiState,
            viewModel = viewModel,
            onBack = onBack
        )
        ImportStep.PREPARE -> ImportPrepareContent(
            uiState = uiState,
            viewModel = viewModel,
            onBack = { viewModel.goBackToSelectFromPrepare() }
        )
        ImportStep.DETAIL -> ImportDetailContent(
            uiState = uiState,
            viewModel = viewModel,
            onBack = { viewModel.goBackToSelect() },
            onPickLocalImage = { index ->
                pendingImageIndex = index
                imagePickerLauncher.launch(arrayOf("image/*"))
            },
            onExecuteImport = { viewModel.executeImport() }
        )
        ImportStep.IMPORTING -> ImportingContent()
        ImportStep.RESULT -> ImportResultContent(
            result = uiState.importResult,
            onDone = onBack
        )
    }
}

// ==================== Step 1: 订单选择 ====================

@Composable
private fun OrderSelectContent(
    uiState: TaobaoImportUiState,
    viewModel: TaobaoImportViewModel,
    onBack: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> viewModel.onFileSelected(uri) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            GradientTopAppBar(
                title = { Text("淘宝订单导入") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (uiState.fileLoaded && uiState.selectedItems.isNotEmpty()) {
                Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "已选 ${uiState.selectedItems.size} 件商品",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = { viewModel.proceedToPrepare() },
                            colors = ButtonDefaults.buttonColors(containerColor = Pink400)
                        ) { Text("下一步") }
                    }
                }
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Pink400)
            }
        } else if (!uiState.fileLoaded) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.FileOpen, null, Modifier.size(64.dp), tint = Pink400.copy(alpha = 0.6f))
                Spacer(Modifier.height(16.dp))
                Text("选择淘宝订单导出的 Excel 文件",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        filePickerLauncher.launch(arrayOf(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        ))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Pink400)
                ) { Text("选择文件") }
            }
        } else {
            OrderListBody(
                orders = uiState.orders,
                selectedItems = uiState.selectedItems,
                onToggleItem = viewModel::toggleItem,
                onSelectAll = viewModel::selectAll,
                onDeselectAll = viewModel::deselectAll,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun OrderListBody(
    orders: List<TaobaoOrder>,
    selectedItems: Set<String>,
    onToggleItem: (String, Int) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalItems = orders.sumOf { it.items.size }
    val allSelected = selectedItems.size == totalItems

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("共 ${orders.size} 个订单，${totalItems} 件商品",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { if (allSelected) onDeselectAll() else onSelectAll() }) {
                    Text(if (allSelected) "取消全选" else "全选", color = Pink400)
                }
            }
        }
        orders.forEach { order ->
            item(key = order.orderId) {
                OrderCard(order, selectedItems, onToggleItem)
            }
        }
    }
}

@Composable
private fun OrderCard(
    order: TaobaoOrder,
    selectedItems: Set<String>,
    onToggleItem: (String, Int) -> Unit
) {
    val isClosed = order.orderStatus == "交易关闭"
    LolitaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Column {
                    Text(order.shopName, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isClosed) Color.Gray else MaterialTheme.colorScheme.onSurface)
                    Text(order.orderTime, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (isClosed) {
                        Surface(color = Color.Gray.copy(alpha = 0.1f), shape = MaterialTheme.shapes.small) {
                            Text("交易关闭", Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    }
                    if (order.totalPaid > 0) {
                        Text("¥${String.format("%.2f", order.totalPaid)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (isClosed) Color.Gray else Pink400)
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            order.items.forEachIndexed { index, item ->
                val isSelected = "${order.orderId}:$index" in selectedItems
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(isSelected, { onToggleItem(order.orderId, index) },
                        colors = CheckboxDefaults.colors(checkedColor = Pink400))
                    Column(Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            color = if (isClosed) Color.Gray else MaterialTheme.colorScheme.onSurface)
                        if (item.styleSpec.isNotBlank()) {
                            Text(item.styleSpec, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (item.price > 0) {
                        Text("¥${String.format("%.2f", item.price)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isClosed) Color.Gray else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ==================== Step 2: 预处理 — 创建缺失数据 ====================

@Composable
private fun ImportPrepareContent(
    uiState: TaobaoImportUiState,
    viewModel: TaobaoImportViewModel,
    onBack: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val missingBrands = uiState.missingItems.filter { it.type == MissingDataType.BRAND }
    val missingCategories = uiState.missingItems.filter { it.type == MissingDataType.CATEGORY }
    val checkedCount = uiState.missingItems.count { it.checked }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GradientTopAppBar(
                title = { Text("数据准备") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (checkedCount > 0) "将创建 $checkedCount 项" else "跳过创建",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { viewModel.confirmPrepare() },
                        enabled = !uiState.isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = Pink400)
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(if (checkedCount > 0) "创建并继续" else "跳过")
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "以下品牌和类型在数据库中不存在，勾选后将自动创建。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.toggleAllMissingItems(true) }) {
                        Text("全选", color = Pink400)
                    }
                    TextButton(onClick = { viewModel.toggleAllMissingItems(false) }) {
                        Text("全不选", color = Pink400)
                    }
                }
            }

            if (missingBrands.isNotEmpty()) {
                item {
                    Text(
                        "品牌 (${missingBrands.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                val brandStartIdx = uiState.missingItems.indexOfFirst { it.type == MissingDataType.BRAND }
                itemsIndexed(missingBrands) { localIdx, item ->
                    MissingDataRow(
                        item = item,
                        onToggle = { viewModel.toggleMissingItem(brandStartIdx + localIdx) }
                    )
                }
            }

            if (missingCategories.isNotEmpty()) {
                item {
                    Text(
                        "类型 (${missingCategories.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                val catStartIdx = uiState.missingItems.indexOfFirst { it.type == MissingDataType.CATEGORY }
                itemsIndexed(missingCategories) { localIdx, item ->
                    MissingDataRow(
                        item = item,
                        onToggle = { viewModel.toggleMissingItem(catStartIdx + localIdx) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MissingDataRow(
    item: MissingDataItem,
    onToggle: () -> Unit
) {
    LolitaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.checked,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = Pink400)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyMedium)
                if (item.type == MissingDataType.CATEGORY && item.extra.isNotBlank()) {
                    Text(
                        if (item.extra == "ACCESSORY") "配饰" else "服装",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Surface(
                color = if (item.type == MissingDataType.BRAND)
                    Pink400.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    if (item.type == MissingDataType.BRAND) "品牌" else "类型",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.type == MissingDataType.BRAND)
                        Pink400 else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ==================== Step 4: 导入中 ====================

@Composable
private fun ImportingContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Pink400)
            Spacer(Modifier.height(16.dp))
            Text("正在导入...", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

// ==================== Step 5: 导入结果 ====================

@Composable
private fun ImportResultContent(
    result: ImportResult?,
    onDone: () -> Unit
) {
    Scaffold(
        topBar = {
            GradientTopAppBar(
                title = { Text("导入完成") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.CheckCircle, null,
                modifier = Modifier.size(72.dp),
                tint = Pink400
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "成功导入 ${result?.importedCount ?: 0} 件服饰",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            if ((result?.mergedCount ?: 0) > 0) {
                Text(
                    "其中 ${result?.mergedCount} 件为定金+尾款合并",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if ((result?.skippedCount ?: 0) > 0) {
                Text(
                    "跳过 ${result?.skippedCount} 件未完善的商品",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onDone,
                colors = ButtonDefaults.buttonColors(containerColor = Pink400)
            ) { Text("完成") }
        }
    }
}