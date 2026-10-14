package com.lolita.app.ui.screen.`import`

import androidx.compose.ui.graphics.Color
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import com.lolita.app.ui.theme.skin.component.skinClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.platform.LocalFocusManager
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.screen.common.CardVariant
import com.lolita.app.ui.screen.common.SectionHeader
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lolita.app.ui.screen.common.LolitaShimmerImage
import com.lolita.app.data.local.entity.Brand
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.CategoryGroup
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.screen.common.LolitaSection
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImportDetailContent(
    uiState: TaobaoImportUiState,
    viewModel: TaobaoImportViewModel,
    onBack: () -> Unit,
    onPickLocalImage: (index: Int) -> Unit = { _ -> },
    onExecuteImport: () -> Unit = {},
    onNavigateToGuide: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val skin = LolitaSkin.current
    val focusManager = LocalFocusManager.current
    val imeVisible = WindowInsets.isImeVisible
    val showDecoration = !imeVisible && LocalDensity.current.fontScale <= 1.4f
    val cardProgress = remember { Animatable(1f) }
    var navigationDirection by remember { mutableIntStateOf(1) }
    var showBackConfirmDialog by remember { mutableStateOf(false) }

    var showImportConfirm by remember { mutableStateOf(false) }
    var singleMode by rememberSaveable { mutableStateOf(true) }
    var currentIndex by rememberSaveable { mutableIntStateOf(0) }
    var paymentFilter by rememberSaveable { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    ObserveSkinScroll(listState)
    val visibleIndices = uiState.importItems.indices.filter { index ->
        val item = uiState.importItems[index]
        when (paymentFilter) {
            1 -> item.paymentRole == null
            2 -> item.paymentRole == PaymentRole.DEPOSIT
            3 -> item.paymentRole == PaymentRole.BALANCE
            4 -> item.paymentRole != null && item.pairedWith == null
            else -> true
        }
    }
    val activeIndex = currentIndex.coerceIn(0, (uiState.importItems.size - 1).coerceAtLeast(0))
    var previousIndex by remember { mutableIntStateOf(activeIndex) }
    LaunchedEffect(singleMode, activeIndex, paymentFilter) { listState.scrollToItem(0) }
    LaunchedEffect(activeIndex) {
        if (previousIndex != activeIndex) {
            navigationDirection = if (activeIndex > previousIndex) 1 else -1
            previousIndex = activeIndex
            cardProgress.snapTo(0f)
            cardProgress.animateTo(1f, skin.spatialSpring)
        }
    }

    BackHandler { showBackConfirmDialog = true }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GradientTopAppBar(
                title = { Text("完善导入数据 (${uiState.importItems.size}件)") },
                navigationIcon = {
                    IconButton(onClick = { showBackConfirmDialog = true }) {
                        SkinIcon(IconKey.ArrowBack)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToGuide) {
                        SkinIcon(IconKey.Help)
                    }
                }
            )
        },
        bottomBar = {
            ImportActionDock(floating = singleMode) {
                    if (singleMode && uiState.importItems.isNotEmpty()) {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            if (maxWidth < 360.dp && LocalDensity.current.fontScale > 1.3f) {
                                Column {
                                    Text("${activeIndex + 1} / ${uiState.importItems.size}", Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                                    Row(Modifier.fillMaxWidth()) {
                                        TextButton(onClick = { focusManager.clearFocus(); currentIndex = activeIndex - 1 }, enabled = activeIndex > 0, modifier = Modifier.weight(1f)) { Text("上一件") }
                                        TextButton(onClick = { focusManager.clearFocus(); currentIndex = activeIndex + 1 }, enabled = activeIndex < uiState.importItems.lastIndex, modifier = Modifier.weight(1f)) { Text("下一件") }
                                    }
                                }
                            } else {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { focusManager.clearFocus(); currentIndex = activeIndex - 1 }, enabled = activeIndex > 0) { Text("上一件") }
                                    Text("${activeIndex + 1} / ${uiState.importItems.size}")
                                    TextButton(onClick = { focusManager.clearFocus(); currentIndex = activeIndex + 1 }, enabled = activeIndex < uiState.importItems.lastIndex) { Text("下一件") }
                                }
                            }
                        }
                    }
                    val validCount = uiState.importItems.indices.count { isImportItemReady(it, uiState.importItems) }
                    Text("${validCount}/${uiState.importItems.size} 条资料已完善",
                        style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = { showImportConfirm = true },
                        enabled = validCount > 0,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text("核对并导入") }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).testTag("importDetailList"),
            contentPadding = PaddingValues(skin.spacingLarge),
            verticalArrangement = Arrangement.spacedBy(skin.spacingLarge)
        ) {
            item {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = singleMode, onClick = { singleMode = true }, label = { Text("逐件填写") })
                        FilterChip(selected = !singleMode, onClick = { singleMode = false }, label = { Text("批量编辑") })
                    }
                    Text(if (singleMode) "先确认付款类型，再核对资料与账目。" else "先确认付款类型，再核对资料和金额。可先跳过，修改会保留；最后统一导入已完善的商品。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!singleMode) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("全部", "全款", "定金", "尾款", "待配对").forEachIndexed { filter, label ->
                            val count = uiState.importItems.count { candidate -> when (filter) {
                                1 -> candidate.paymentRole == null
                                2 -> candidate.paymentRole == PaymentRole.DEPOSIT
                                3 -> candidate.paymentRole == PaymentRole.BALANCE
                                4 -> candidate.paymentRole != null && candidate.pairedWith == null
                                else -> true
                            } }
                            FilterChip(selected = paymentFilter == filter, onClick = { paymentFilter = filter }, label = { Text("$label $count") })
                        }
                    }
                }
            }
            val shownOrderIds = (if (singleMode) listOfNotNull(uiState.importItems.getOrNull(activeIndex)?.originalItem?.orderId)
                else visibleIndices.map { uiState.importItems[it].originalItem.orderId }.distinct())
            if (!singleMode) importOrderSummaries(uiState, shownOrderIds)
            if (singleMode && uiState.importItems.isNotEmpty()) item(key = "single-progress") {
                ImportSingleProgress(activeIndex, uiState.importItems.size, uiState.importItems.getOrNull(activeIndex + 1))
            }
            if (!singleMode && visibleIndices.isEmpty()) item { Text("此分类暂无商品，可切换其他分类。") }
            if (singleMode && uiState.importItems.isNotEmpty()) item(key = "single-editor") {
                ImportStackCard(uiState.importItems.lastIndex - activeIndex, showDecoration, { cardProgress.value }, navigationDirection) {
                    key(activeIndex) {
                        ImportCardEditor(activeIndex, uiState, viewModel, onPickLocalImage, floating = true)
                    }
                }
            } else {
                items(visibleIndices, key = { it }) { index ->
                    ImportCardEditor(index, uiState, viewModel, onPickLocalImage, floating = false)
                }
            }
            if (singleMode) importOrderSummaries(uiState, shownOrderIds)
        }
    }

    if (showImportConfirm) {
        val ready = uiState.importItems.indices.count { isImportItemReady(it, uiState.importItems) }
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text("确认导入") },
            text = { Text("本次将处理 $ready 条已完善的商品资料，定金与尾款配对后合并为同一件服饰。${if (ready < uiState.importItems.size) "另有 ${uiState.importItems.size - ready} 条未完善，本次不会导入。" else ""}") },
            confirmButton = { TextButton(onClick = { showImportConfirm = false; onExecuteImport() }) { Text("确认导入") } },
            dismissButton = { TextButton(onClick = { showImportConfirm = false }) { Text("继续核对") } }
        )
    }

    if (showBackConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBackConfirmDialog = false },
            title = { Text("确认返回") },
            text = { Text("已编辑的导入数据尚未保存，返回后将丢失所有修改。") },
            confirmButton = {
                TextButton(onClick = {
                    showBackConfirmDialog = false
                    onBack()
                }) { Text("返回") }
            },
            dismissButton = {
                TextButton(onClick = { showBackConfirmDialog = false }) { Text("继续编辑") }
            }
        )
    }
}

@Composable
private fun ImportCardEditor(index: Int, state: TaobaoImportUiState, viewModel: TaobaoImportViewModel,
    onPickLocalImage: (Int) -> Unit, floating: Boolean) {
    ImportItemCard(
        item = state.importItems[index], index = index, allItems = state.importItems,
        brands = state.brands, categories = state.categories, floating = floating,
        onUpdate = { update -> viewModel.updateImportItem(index, update) },
        onPickLocalImage = { onPickLocalImage(index) },
        onManualPair = { target -> viewModel.manualPair(index, target) },
        onUnpair = { viewModel.unpair(index) },
        onAddCategory = viewModel::addCategory,
        onSetPaymentRole = { role -> viewModel.setPaymentRole(index, role) }
    )
}

private fun LazyListScope.importOrderSummaries(uiState: TaobaoImportUiState, shownOrderIds: List<String>) {
    val rowsByOrder = uiState.importItems.groupBy { it.originalItem.orderId }
    shownOrderIds.forEach { orderId ->
        val rows = rowsByOrder[orderId].orEmpty()
        val original = uiState.orders.firstOrNull { it.orderId == orderId }
        if (original != null) item(key = "source-order-$orderId") {
            LolitaSection("源订单 $orderId") { row {
                Text("源实付 ¥${original.totalPaid} · 源邮费 ¥${original.shipping}")
                Text("本次选择 ${rows.size}/${original.items.sumOf { it.quantity }} 件，分摊邮费 ¥${rows.filter { it.shipping.isFinite() }.sumOf { it.shipping }}，待核对实付 ¥${rows.filter { it.paidAmount.isFinite() }.sumOf { it.paidAmount }}。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
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
    onSetPaymentRole: (PaymentRole?) -> Unit = {},
    floating: Boolean = false
) {
    val isValid = isImportItemReady(index, allItems)
    val priceText = item.priceInput ?: item.price.takeIf { it.isFinite() }?.toString().orEmpty()
    val shippingText = item.shippingInput ?: item.shipping.takeIf { it.isFinite() }?.toString().orEmpty()
    val paidText = item.paidInput ?: item.paidAmount.takeIf { it.isFinite() }?.toString().orEmpty()
    var showPairDialog by remember { mutableStateOf(false) }
    val skin = LolitaSkin.current
    val identityMissing = item.name.isBlank() || item.brandId == 0L || item.categoryId == 0L
    var identityExpanded by rememberSaveable(index) { mutableStateOf(identityMissing) }
    LaunchedEffect(identityMissing) { if (identityMissing) identityExpanded = true }

    LolitaCard(modifier = Modifier.fillMaxWidth(), variant = if (floating) CardVariant.FEATURED else CardVariant.DEFAULT,
        contentPadding = PaddingValues(skin.cardInnerPadding)) {
        Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
            // 序号 + 状态标签
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("#${index + 1}", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    // 定金/尾款标签
                    if (item.paymentRole != null) {
                        val isPaired = item.pairedWith != null
                        val roleText = if (item.paymentRole == PaymentRole.DEPOSIT) "定金" else "尾款"
                        val labelColor = if (isPaired) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        val labelBg = if (isPaired) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
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
                    if (!floating && item.paymentRole != null) {
                        if (item.pairedWith != null) {
                            IconButton(onClick = onUnpair, modifier = Modifier.size(32.dp)) {
                                SkinIcon(IconKey.LinkOff, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            IconButton(onClick = { showPairDialog = true }, modifier = Modifier.size(32.dp)) {
                                SkinIcon(IconKey.Link, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    if (isValid) {
                        Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), shape = MaterialTheme.shapes.small) {
                            Text("已完善", Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            Text(item.name, style = if (floating) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)
            Text("源订单 ${item.originalItem.orderId} · 第 ${item.sourceUnitIndex + 1}/${item.originalItem.quantity} 件", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("原商品金额列：¥${item.originalItem.price}；原数量：${item.originalItem.quantity}。下方金额均为本件分配，允许核对后修改。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (item.name != item.originalItem.name) Text("原商品：${item.originalItem.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("付款类型 · 识别有误可直接修改", style = MaterialTheme.typography.labelMedium)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                FilterChip(
                    selected = item.paymentRole == null,
                    onClick = { onSetPaymentRole(null) },
                    label = { Text("全款") }
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

            item.pairedWith?.let { pairedIndex ->
                allItems.getOrNull(pairedIndex)?.let { paired ->
                    Text("已配对：#${pairedIndex + 1} ${paired.originalItem.name}", style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = { showPairDialog = true }) { Text("更换配对") }
                        TextButton(onClick = onUnpair) { Text("解除配对") }
                    }
                }
            }
            if (item.pairedWith == null) {
                if (item.paymentRole != null) TextButton(onClick = { showPairDialog = true }) { Text("选择${if (item.paymentRole == PaymentRole.DEPOSIT) "尾款" else "定金"}配对") }
            }

            // 型号款式原始值（只读）
            if (item.styleSpec.isNotBlank()) {
                Text(item.styleSpec,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            val isPairedBalance = item.paymentRole == PaymentRole.BALANCE && item.pairedWith != null
            if (floating) SectionHeader("金额与付款")

            if (isPairedBalance) {
                // 尾款已关联：只显示价格和日期
                Text("数据将从定金项 #${item.pairedWith!! + 1} 继承",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = priceText,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        onValueChange = { v ->
                            onUpdate { it.copy(priceInput = v, price = v.toDoubleOrNull() ?: Double.NaN) }
                        },
                        label = { Text(if (floating) "本件尾款金额" else "尾款商品金额（本件）") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        prefix = { Text("¥") }
                    )
                    OutlinedTextField(
                        value = item.purchaseDate,
                        onValueChange = { v -> onUpdate { it.copy(purchaseDate = v) } },
                        label = { Text(if (floating) "实际付款日期" else "实际付款日期（核对）") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            } else {
                // 普通商品或定金：显示完整字段
                if (!floating) ImportIdentityFields(item, brands, categories, onUpdate, onAddCategory, onPickLocalImage, showImage = false)
                // 价格 + 付款日期 (一行两个)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = priceText,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        onValueChange = { v ->
                            onUpdate { it.copy(priceInput = v, price = v.toDoubleOrNull() ?: Double.NaN) }
                        },
                        label = { Text(if (floating) { if (item.paymentRole == PaymentRole.DEPOSIT) "本件定金金额" else "本件商品金额" }
                            else if (item.paymentRole == PaymentRole.DEPOSIT) "定金商品金额（本件）" else "商品金额（本件优惠后）") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        prefix = { Text("¥") }
                    )
                    OutlinedTextField(
                        value = item.purchaseDate,
                        onValueChange = { v -> onUpdate { it.copy(purchaseDate = v) } },
                        label = { Text(if (floating) "实际付款日期" else "实际付款日期（核对）") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
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

                // 未匹配定金：手动填写尾款信息
                if (item.paymentRole == PaymentRole.DEPOSIT && item.pairedWith == null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        "尾款信息（手动填写）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val balanceText = item.balanceInput ?: item.manualBalance?.let { String.format("%.2f", it) }.orEmpty()
                    var showBalanceDatePicker by remember { mutableStateOf(false) }
                    val balanceDatePickerState = rememberDatePickerState(
                        initialSelectedDateMillis = item.balanceDueDate
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = balanceText,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            onValueChange = { v ->
                                onUpdate { it.copy(balanceInput = v, manualBalance = v.toDoubleOrNull()) }
                            },
                            label = { Text("尾款金额") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            prefix = { Text("¥") }
                        )
                        Box(modifier = Modifier.weight(1f).skinClickable { showBalanceDatePicker = true }) {
                            OutlinedTextField(
                                value = item.balanceDueDate?.let {
                                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                        .format(java.util.Date(it))
                                } ?: "",
                                onValueChange = {},
                                label = { Text(if (floating) "尾款应付日期" else "尾款应付日期（可待定）") },
                                placeholder = { Text("日期待定") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                readOnly = true,
                                enabled = false,
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }

                    if (showBalanceDatePicker) {
                        DatePickerDialog(
                            onDismissRequest = { showBalanceDatePicker = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    balanceDatePickerState.selectedDateMillis?.let { millis ->
                                        onUpdate { it.copy(balanceDueDate = millis) }
                                    }
                                    showBalanceDatePicker = false
                                }) { Text("确定") }
                            },
                            dismissButton = {
                                TextButton(onClick = { onUpdate { it.copy(balanceDueDate = null) }; showBalanceDatePicker = false }) { Text("日期待定") }
                            }
                        ) {
                            DatePicker(state = balanceDatePickerState)
                        }
                    }
                }

                if (!floating) ImportImageFields(item, onUpdate, onPickLocalImage)
            } // end else (非尾款关联)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = shippingText, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), onValueChange = { value ->
                    onUpdate { it.copy(shippingInput = value, shipping = value.toDoubleOrNull() ?: Double.NaN) }
                }, label = { Text("本件分摊邮费") }, prefix = { Text("¥") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = paidText, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), onValueChange = { value ->
                    onUpdate { it.copy(paidInput = value, paidAmount = value.toDoubleOrNull() ?: Double.NaN) }
                }, label = { Text("本件实际已付") }, prefix = { Text("¥") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            if (floating && !isPairedBalance) {
                SectionHeader("服饰资料", action = {
                    TextButton(onClick = { identityExpanded = !identityExpanded }) { Text(if (identityExpanded) "收起资料" else "展开资料") }
                })
                if (identityExpanded) ImportIdentityFields(item, brands, categories, onUpdate, onAddCategory, onPickLocalImage)
            }
            Text("邮费包含在应付总额中，实付可小于应付；尾款日期可以待定。默认日期来自订单提交时间，请改为实际付款日期。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = item.financialConfirmed, onCheckedChange = { checked -> onUpdate { it.copy(financialConfirmed = checked) } })
                Text("已核对本件金额、邮费、实付和日期", style = MaterialTheme.typography.bodyMedium)
            }
            if (item.paymentRole == PaymentRole.BALANCE && item.pairedWith == null) Text("未配对尾款缺少完整订单价格，请配对定金；确为全款商品时改为全款。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }

    // 手动匹配对话框
    if (showPairDialog) {
        val targetRole = if (item.paymentRole == PaymentRole.DEPOSIT) PaymentRole.BALANCE else PaymentRole.DEPOSIT
        val candidates = allItems.mapIndexedNotNull { idx, candidate ->
            if (idx != index && candidate.paymentRole == targetRole && (candidate.pairedWith == null || candidate.pairedWith == index))
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
                                    .skinClickable { onManualPair(idx); showPairDialog = false }
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
private fun ImportIdentityFields(
    item: ImportItemState,
    brands: List<Brand>,
    categories: List<Category>,
    onUpdate: ((ImportItemState) -> ImportItemState) -> Unit,
    onAddCategory: (String, CategoryGroup) -> Unit,
    onPickLocalImage: () -> Unit,
    showImage: Boolean = true
) {
    val skin = LolitaSkin.current
    Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
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

        if (showImage) ImportImageFields(item, onUpdate, onPickLocalImage)
    }
}

@Composable
private fun ImportImageFields(
    item: ImportItemState,
    onUpdate: ((ImportItemState) -> ImportItemState) -> Unit,
    onPickLocalImage: () -> Unit
) {
    val skin = LolitaSkin.current
        // 图片
        Text("商品图片", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (item.imageUrl != null) {
            Box(Modifier.fillMaxWidth()) {
                LolitaShimmerImage(
                    model = item.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                        .clip(RoundedCornerShape(skin.cornerRadiusMedium)),
                    contentScale = ContentScale.Crop,
                    circularRevealEnabled = false
                )
                IconButton(
                    onClick = { onUpdate { it.copy(imageUrl = null) } },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    SkinIcon(IconKey.Close, tint = MaterialTheme.colorScheme.error)
                }
            }
        } else {
            OutlinedButton(
                onClick = onPickLocalImage,
                modifier = Modifier.fillMaxWidth()
            ) {
                SkinIcon(IconKey.AddPhoto, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("选择图片")
            }
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

    Box(Modifier.fillMaxWidth().skinClickable { showDialog = true }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("品牌 *") },
            trailingIcon = { SkinIcon(IconKey.Search) },
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
                        leadingIcon = { SkinIcon(IconKey.Search) },
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
                                    .skinClickable { onBrandSelected(brand.id); showDialog = false }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (brand.id == selectedBrandId) MaterialTheme.colorScheme.primary
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
                text = { Text("+ 新增类型", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium) },
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