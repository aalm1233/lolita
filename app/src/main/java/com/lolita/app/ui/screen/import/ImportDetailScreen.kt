package com.lolita.app.ui.screen.`import`

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lolita.app.data.local.entity.Brand
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.CategoryGroup
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.theme.Pink400

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportDetailContent(
    uiState: TaobaoImportUiState,
    viewModel: TaobaoImportViewModel,
    onBack: () -> Unit,
    onPickLocalImage: (index: Int) -> Unit = { _ -> },
    onExecuteImport: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GradientTopAppBar(
                title = { Text("完善导入数据 (${uiState.importItems.size}件)") },
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
                    val validCount = uiState.importItems.count { it.brandId > 0 && it.categoryId > 0 }
                    Text("${validCount}/${uiState.importItems.size} 件已完善",
                        style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = onExecuteImport,
                        enabled = validCount > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = Pink400)
                    ) { Text("导入") }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(uiState.importItems.size) { index ->
                ImportItemCard(
                    item = uiState.importItems[index],
                    index = index,
                    allItems = uiState.importItems,
                    brands = uiState.brands,
                    categories = uiState.categories,
                    onUpdate = { update -> viewModel.updateImportItem(index, update) },
                    onPickLocalImage = { onPickLocalImage(index) },
                    onManualPair = { targetIdx -> viewModel.manualPair(index, targetIdx) },
                    onUnpair = { viewModel.unpair(index) },
                    onAddCategory = { name, group -> viewModel.addCategory(name, group) },
                    onSetPaymentRole = { role -> viewModel.setPaymentRole(index, role) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportItemCard(
    item: ImportItemState,
    index: Int,
    allItems: List<ImportItemState>,
    brands: List<Brand>,
    categories: List<Category>,
    onUpdate: ((ImportItemState) -> ImportItemState) -> Unit,
    onPickLocalImage: () -> Unit,
    onManualPair: (Int) -> Unit,
    onUnpair: () -> Unit,
    onAddCategory: (String, CategoryGroup) -> Unit = { _, _ -> },
    onSetPaymentRole: (PaymentRole?) -> Unit = {}
) {
    val isValid = item.brandId > 0 && item.categoryId > 0
    var priceText by remember(item.price) {
        mutableStateOf(if (item.price > 0) String.format("%.2f", item.price) else "")
    }
    var showPairDialog by remember { mutableStateOf(false) }

    LolitaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 序号 + 状态标签
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("#${index + 1}", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = Pink400)
                    // 定金/尾款标签
                    if (item.paymentRole != null) {
                        val isPaired = item.pairedWith != null
                        val roleText = if (item.paymentRole == PaymentRole.DEPOSIT) "定金" else "尾款"
                        val labelColor = if (isPaired) Pink400 else MaterialTheme.colorScheme.error
                        val labelBg = if (isPaired) Pink400.copy(alpha = 0.1f)
                            else MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                        Surface(color = labelBg, shape = MaterialTheme.shapes.small) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(roleText, style = MaterialTheme.typography.labelSmall, color = labelColor)
                                if (isPaired) {
                                    Text("-> #${item.pairedWith!! + 1}",
                                        style = MaterialTheme.typography.labelSmall, color = labelColor)
                                } else {
                                    Text("(未匹配)", style = MaterialTheme.typography.labelSmall, color = labelColor)
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // 配对操作按钮
                    if (item.paymentRole != null) {
                        if (item.pairedWith != null) {
                            IconButton(onClick = onUnpair, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.LinkOff, "取消配对",
                                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        } else {
                            IconButton(onClick = { showPairDialog = true }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Link, "手动匹配",
                                    tint = Pink400, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    if (isValid) {
                        Surface(color = Pink400.copy(alpha = 0.1f), shape = MaterialTheme.shapes.small) {
                            Text("已完善", Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall, color = Pink400)
                        }
                    }
                }
            }

            // 角色选择：普通/定金/尾款
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("角色:", style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.CenterVertically))
                FilterChip(
                    selected = item.paymentRole == null,
                    onClick = { onSetPaymentRole(null) },
                    label = { Text("普通") }
                )
                FilterChip(
                    selected = item.paymentRole == PaymentRole.DEPOSIT,
                    onClick = { onSetPaymentRole(PaymentRole.DEPOSIT) },
                    label = { Text("定金") }
                )
                FilterChip(
                    selected = item.paymentRole == PaymentRole.BALANCE,
                    onClick = { onSetPaymentRole(PaymentRole.BALANCE) },
                    label = { Text("尾款") }
                )
            }

            // 型号款式原始值（只读）
            if (item.styleSpec.isNotBlank()) {
                Text(item.styleSpec,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            val isPairedBalance = item.paymentRole == PaymentRole.BALANCE && item.pairedWith != null

            if (isPairedBalance) {
                // 尾款已关联：只显示价格和日期
                Text("数据将从定金项 #${item.pairedWith!! + 1} 继承",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { v ->
                            priceText = v
                            onUpdate { it.copy(price = v.toDoubleOrNull() ?: 0.0) }
                        },
                        label = { Text("尾款价格") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        prefix = { Text("¥") }
                    )
                    OutlinedTextField(
                        value = item.purchaseDate,
                        onValueChange = { v -> onUpdate { it.copy(purchaseDate = v) } },
                        label = { Text("购买日期") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        readOnly = true
                    )
                }
            } else {
                // 普通商品或定金：显示完整字段
                // 商品名称
                OutlinedTextField(
                    value = item.name,
                    onValueChange = { v -> onUpdate { it.copy(name = v) } },
                    label = { Text("商品名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 2
                )
                // 品牌选择（搜索对话框）
                ImportBrandSelector(
                    selectedBrandId = item.brandId,
                    brands = brands,
                    onBrandSelected = { id -> onUpdate { it.copy(brandId = id) } }
                )

                // 分类选择（下拉）
                ImportCategorySelector(
                    selectedCategoryId = item.categoryId,
                    categories = categories,
                    onCategorySelected = { id -> onUpdate { it.copy(categoryId = id) } },
                    onAddCategory = onAddCategory
                )
                // 颜色 + 尺码 (一行两个)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = item.color,
                        onValueChange = { v -> onUpdate { it.copy(color = v) } },
                        label = { Text("颜色") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = item.size,
                        onValueChange = { v -> onUpdate { it.copy(size = v) } },
                        label = { Text("尺码") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                // 价格 + 购买日期 (一行两个)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { v ->
                            priceText = v
                            onUpdate { it.copy(price = v.toDoubleOrNull() ?: 0.0) }
                        },
                        label = { Text(if (item.paymentRole == PaymentRole.DEPOSIT) "定金价格" else "价格") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        prefix = { Text("¥") }
                    )
                    OutlinedTextField(
                        value = item.purchaseDate,
                        onValueChange = { v -> onUpdate { it.copy(purchaseDate = v) } },
                        label = { Text("购买日期") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        readOnly = true
                    )
                }

                // 定金已关联时显示尾款价格（只读）
                if (item.paymentRole == PaymentRole.DEPOSIT && item.pairedWith != null) {
                    val balanceItem = allItems.getOrNull(item.pairedWith!!)
                    if (balanceItem != null) {
                        OutlinedTextField(
                            value = if (balanceItem.price > 0) String.format("%.2f", balanceItem.price) else "",
                            onValueChange = {},
                            label = { Text("关联尾款价格 (#${item.pairedWith!! + 1})") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            prefix = { Text("¥") },
                            readOnly = true,
                            enabled = false,
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                // 图片
                Text("商品图片", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.imageUrl != null) {
                    Box(Modifier.fillMaxWidth()) {
                        val context = LocalContext.current
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(item.imageUrl).crossfade(true).build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(160.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { onUpdate { it.copy(imageUrl = null) } },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(Icons.Default.Close, "移除图片",
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onPickLocalImage,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("选择图片")
                    }
                }
            } // end else (非尾款关联)
        }
    }

    // 手动匹配对话框
    if (showPairDialog) {
        val targetRole = if (item.paymentRole == PaymentRole.DEPOSIT) PaymentRole.BALANCE else PaymentRole.DEPOSIT
        val candidates = allItems.mapIndexedNotNull { idx, candidate ->
            if (idx != index && candidate.paymentRole == targetRole && candidate.pairedWith == null)
                idx to candidate else null
        }
        AlertDialog(
            onDismissRequest = { showPairDialog = false },
            title = { Text("选择配对的${if (targetRole == PaymentRole.BALANCE) "尾款" else "定金"}") },
            text = {
                if (candidates.isEmpty()) {
                    Text("没有可配对的${if (targetRole == PaymentRole.BALANCE) "尾款" else "定金"}项",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.heightIn(max = 350.dp)) {
                        items(candidates) { (idx, candidate) ->
                            Column(
                                Modifier.fillMaxWidth()
                                    .clickable { onManualPair(idx); showPairDialog = false }
                                    .padding(vertical = 10.dp, horizontal = 4.dp)
                            ) {
                                Text("#${idx + 1} ${candidate.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                                if (candidate.styleSpec.isNotBlank()) {
                                    Text(candidate.styleSpec,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text("¥${String.format("%.2f", candidate.price)}  ${candidate.originalItem.shopName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPairDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun ImportBrandSelector(
    selectedBrandId: Long,
    brands: List<Brand>,
    onBrandSelected: (Long) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val selectedName = brands.find { it.id == selectedBrandId }?.name ?: ""
    val isError = selectedBrandId == 0L

    Box(Modifier.fillMaxWidth().clickable { showDialog = true }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("品牌 *") },
            trailingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
            isError = isError,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = if (isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.outline,
                disabledLabelColor = if (isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }

    if (showDialog) {
        var searchQuery by remember { mutableStateOf("") }
        val filtered = remember(searchQuery, brands) {
            if (searchQuery.isBlank()) brands
            else brands.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("选择品牌") },
            text = {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("搜索品牌...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 350.dp)) {
                        items(filtered, key = { it.id }) { brand ->
                            Text(
                                brand.name,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { onBrandSelected(brand.id); showDialog = false }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (brand.id == selectedBrandId) Pink400
                                    else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDialog = false }) { Text("取消") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportCategorySelector(
    selectedCategoryId: Long,
    categories: List<Category>,
    onCategorySelected: (Long) -> Unit,
    onAddCategory: (String, CategoryGroup) -> Unit = { _, _ -> }
) {
    var expanded by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    val isError = selectedCategoryId == 0L

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = categories.find { it.id == selectedCategoryId }?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("类型 *") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            isError = isError
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = { onCategorySelected(category.id); expanded = false }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("+ 新增类型", color = Pink400, fontWeight = FontWeight.Medium) },
                onClick = { expanded = false; showAddDialog = true }
            )
        }
    }

    if (showAddDialog) {
        var newName by remember { mutableStateOf("") }
        var selectedGroup by remember { mutableStateOf(CategoryGroup.CLOTHING) }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("新增类型") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("类型名称") },
                        placeholder = { Text("如: OP、SK、头饰...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text("分组", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedGroup == CategoryGroup.CLOTHING,
                            onClick = { selectedGroup = CategoryGroup.CLOTHING },
                            label = { Text("服装") }
                        )
                        FilterChip(
                            selected = selectedGroup == CategoryGroup.ACCESSORY,
                            onClick = { selectedGroup = CategoryGroup.ACCESSORY },
                            label = { Text("配饰") }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onAddCategory(newName.trim(), selectedGroup)
                            showAddDialog = false
                        }
                    },
                    enabled = newName.isNotBlank()
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("取消") }
            }
        )
    }
}