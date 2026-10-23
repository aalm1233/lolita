package com.lolita.app.ui.screen.`import`

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lolita.app.data.local.entity.Brand
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.CategoryGroup
import com.lolita.app.data.local.entity.Location
import com.lolita.app.data.local.entity.StorageNode
import com.lolita.app.domain.model.CategorySemantic
import com.lolita.app.domain.model.OrderImportSource
import com.lolita.app.domain.model.WardrobeFamily
import com.lolita.app.domain.time.localDateToPickerMillis
import com.lolita.app.domain.time.pickerMillisToLocalDate
import com.lolita.app.ui.screen.common.CardVariant
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.screen.common.LolitaSection
import com.lolita.app.ui.screen.common.LolitaShimmerImage
import com.lolita.app.ui.screen.common.SectionHeader
import com.lolita.app.ui.screen.common.SizeSelector
import com.lolita.app.ui.screen.common.StorageLocationPicker
import com.lolita.app.ui.screen.item.CategorySemanticPicker
import com.lolita.app.ui.screen.item.WardrobeCategorySelector
import com.lolita.app.ui.screen.item.WardrobeSpecificationEditor
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.component.skinClickable
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
    onNavigateToGuide: () -> Unit = {},
    onViewOriginal: (Long) -> Unit = {},
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
    val displayIndices = remember(uiState.importItems) { importDisplayOrder(uiState.importItems) }
    var currentSourceIndex by
        rememberSaveable(uiState.sessionId) { mutableIntStateOf(uiState.currentItemIndex) }
    var batchDialog by remember { mutableStateOf(false) }
    var reasonFilter by rememberSaveable { mutableStateOf("全部") }
    var paymentFilter by rememberSaveable { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    ObserveSkinScroll(listState)
    val visibleIndices =
        displayIndices.filter { index ->
            val item = uiState.importItems[index]
            (reasonFilter == "全部" ||
                importProblems(index, uiState.importItems).any { it == reasonFilter } ||
                item.pairedWith?.let { partner ->
                    importProblems(partner, uiState.importItems).any { it == reasonFilter }
                } == true) &&
                when (paymentFilter) {
                    1 -> item.paymentRole == null
                    2 -> item.paymentRole == PaymentRole.DEPOSIT
                    3 -> item.paymentRole == PaymentRole.BALANCE
                    4 -> item.paymentRole != null && item.pairedWith == null
                    else -> true
                }
        }
    val activeIndex =
        currentSourceIndex.takeIf { it in uiState.importItems.indices }
            ?: displayIndices.firstOrNull()
            ?: 0
    val activePosition = displayIndices.indexOf(activeIndex).coerceAtLeast(0)
    val nextSourceIndex = displayIndices.getOrNull(activePosition + 1)
    fun navigate(direction: Int) {
        val target = displayIndices.getOrNull(activePosition + direction) ?: return
        focusManager.clearFocus()
        navigationDirection = direction
        currentSourceIndex = target
    }
    var previousIndex by remember { mutableIntStateOf(activeIndex) }
    LaunchedEffect(singleMode, activeIndex, paymentFilter) { listState.scrollToItem(0) }
    LaunchedEffect(activeIndex) {
        viewModel.setCurrentItemIndex(activeIndex)
        if (previousIndex != activeIndex) {
            previousIndex = activeIndex
            cardProgress.snapTo(0f)
            cardProgress.animateTo(1f, skin.spatialSpring)
        }
    }

    BackHandler { onBack() }

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
                title = { Text("核对导入资料") },
                navigationIcon = { IconButton(onClick = onBack) { SkinIcon(IconKey.ArrowBack) } },
                actions = { IconButton(onClick = onNavigateToGuide) { SkinIcon(IconKey.Help) } },
            )
        },
        bottomBar = {
            val count = importNewItemCount(uiState.importItems)
            com.lolita.app.ui.screen.common.EditorSaveBar(
                label = if (singleMode) "核对本件并继续" else "查看可导入的 $count 件",
                saving = false,
                enabled = if (singleMode) uiState.importItems.isNotEmpty() else count > 0,
                onSave = {
                    if (!singleMode) onExecuteImport()
                    else if (isImportItemReady(activeIndex, uiState.importItems)) {
                        val target =
                            displayIndices.firstOrNull {
                                it > activeIndex &&
                                    uiState.importItems[it].committedItemId == null &&
                                    !isImportItemReady(it, uiState.importItems)
                            }
                                ?: displayIndices.firstOrNull {
                                    uiState.importItems[it].committedItemId == null &&
                                        !isImportItemReady(it, uiState.importItems)
                                }
                        if (target != null) currentSourceIndex = target else onExecuteImport()
                    } else {
                        viewModel.reportImportError(
                            importProblems(activeIndex, uiState.importItems).joinToString("、")
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier =
                Modifier.fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .testTag("importDetailList"),
            contentPadding = PaddingValues(skin.spacingLarge),
            verticalArrangement = Arrangement.spacedBy(skin.spacingLarge),
        ) {
            item {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = singleMode,
                            onClick = { singleMode = true },
                            label = { Text("逐件填写") },
                        )
                        FilterChip(
                            selected = !singleMode,
                            onClick = { singleMode = false },
                            label = { Text("列表") },
                        )
                    }
                    Text(
                        if (singleMode) "先确认付款类型，再核对资料与账目。"
                        else "先确认付款类型，再核对资料和金额。可先跳过，修改会保留；最后统一导入已就绪商品。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!singleMode)
                        TextButton(onClick = { batchDialog = true }) { Text("选择资料，批量设品牌 / 类型") }
                    Text(
                        "${uiState.importItems.indices.count { isImportItemReady(it,uiState.importItems) }} 条可导入 · ${uiState.importItems.count { it.committedItemId==null }} 条未完成",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                        TextButton(onClick = { navigate(-1) }, enabled = activePosition > 0) {
                            Text("上一件")
                        }
                        TextButton(
                            onClick = { navigate(1) },
                            enabled = activePosition < displayIndices.lastIndex,
                        ) {
                            Text("下一件")
                        }
                        TextButton(
                            onClick = {
                                viewModel.nextUnresolved()?.let { currentSourceIndex = it }
                            }
                        ) {
                            Text("下一条待处理")
                        }
                        TextButton(
                            onClick = onExecuteImport,
                            enabled = importNewItemCount(uiState.importItems) > 0,
                        ) {
                            Text("查看可导入的 ${importNewItemCount(uiState.importItems)} 件")
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                        (listOf("全部") +
                                uiState.importItems.indices
                                    .flatMap { importProblems(it, uiState.importItems) }
                                    .filterNot { it == "已导入" }
                                    .distinct())
                            .forEach { reason ->
                                FilterChip(
                                    reasonFilter == reason,
                                    {
                                        reasonFilter = reason
                                        singleMode = false
                                    },
                                    label = { Text(reason) },
                                )
                            }
                    }
                    if (!singleMode)
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf("全部", "全款", "定金", "尾款", "待配对").forEachIndexed { filter, label ->
                                val count =
                                    uiState.importItems.count { candidate ->
                                        when (filter) {
                                            1 -> candidate.paymentRole == null
                                            2 -> candidate.paymentRole == PaymentRole.DEPOSIT
                                            3 -> candidate.paymentRole == PaymentRole.BALANCE
                                            4 ->
                                                candidate.paymentRole != null &&
                                                    candidate.pairedWith == null
                                            else -> true
                                        }
                                    }
                                FilterChip(
                                    selected = paymentFilter == filter,
                                    onClick = { paymentFilter = filter },
                                    label = { Text("$label $count") },
                                )
                            }
                        }
                }
            }
            val shownOrderIds =
                (if (singleMode)
                    listOfNotNull(uiState.importItems.getOrNull(activeIndex)?.originalItem?.orderId)
                else visibleIndices.map { uiState.importItems[it].originalItem.orderId }.distinct())
            if (!singleMode) importOrderSummaries(uiState, shownOrderIds)
            if (singleMode && uiState.importItems.isNotEmpty())
                item(key = "single-progress") {
                    ImportSingleProgress(
                        activePosition,
                        uiState.importItems.size,
                        nextSourceIndex?.let { uiState.importItems[it] },
                        nextSourceIndex,
                    )
                }
            if (!singleMode && visibleIndices.isEmpty()) item { Text("此分类暂无商品，可切换其他分类。") }
            if (singleMode && uiState.importItems.isNotEmpty())
                item(key = "single-editor") {
                    ImportStackCard(
                        displayIndices.lastIndex - activePosition,
                        showDecoration,
                        { cardProgress.value },
                        navigationDirection,
                    ) {
                        key(activeIndex) {
                            ImportCardEditor(
                                activeIndex,
                                uiState,
                                viewModel,
                                onPickLocalImage,
                                floating = true,
                                onViewOriginal = onViewOriginal,
                            )
                        }
                    }
                }
            else {
                items(visibleIndices, key = { it }) { index ->
                    ImportCardEditor(
                        index,
                        uiState,
                        viewModel,
                        onPickLocalImage,
                        floating = false,
                        onViewOriginal = onViewOriginal,
                    )
                }
            }
            if (singleMode) importOrderSummaries(uiState, shownOrderIds)
        }
    }

    if (batchDialog) ImportBatchAttributesDialog(uiState, viewModel) { batchDialog = false }

    if (showImportConfirm) {
        val ready = uiState.importItems.indices.count { isImportItemReady(it, uiState.importItems) }
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text("确认导入") },
            text = {
                Text(
                    "本次将处理 $ready 条可导入商品资料，定金与尾款配对后合并为同一件服饰。${if (ready < uiState.importItems.size) "另有 ${uiState.importItems.size - ready} 条尚未就绪，本次不会导入。" else ""}"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportConfirm = false
                        onExecuteImport()
                    }
                ) {
                    Text("确认导入")
                }
            },
            dismissButton = { TextButton(onClick = { showImportConfirm = false }) { Text("继续核对") } },
        )
    }

    if (showBackConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBackConfirmDialog = false },
            title = { Text("放弃未保存的修改？") },
            text = { Text("返回后，本次未导入的修改将丢失。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBackConfirmDialog = false
                        onBack()
                    }
                ) {
                    Text("放弃并返回")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackConfirmDialog = false }) { Text("继续编辑") }
            },
        )
    }
}

@Composable
private fun ImportCardEditor(
    index: Int,
    state: TaobaoImportUiState,
    viewModel: TaobaoImportViewModel,
    onPickLocalImage: (Int) -> Unit,
    floating: Boolean,
    onViewOriginal: (Long) -> Unit = {},
) {
    val row = state.importItems[index]
    if (row.committedItemId != null) {
        LolitaSection("这条资料已导入") {
            row {
                Text(row.name)
                TextButton(onClick = { onViewOriginal(row.committedItemId) }) { Text("查看原物品") }
            }
        }
        return
    }
    var linkDialog by remember(index) { mutableStateOf(false) }
    if (linkDialog) ImportLinkExistingDialog(index, row, viewModel) { linkDialog = false }
    Column(verticalArrangement = Arrangement.spacedBy(LolitaSkin.current.spacingMedium)) {
        val problems = importProblems(index, state.importItems)
        if (problems.isNotEmpty()) ImportNotice("还需处理：${problems.joinToString("、")}", true)
        if (row.sourceProblem != null) {
            TextButton(
                onClick = {
                    viewModel.confirmSourceIdentity(index, row.sourceProblem.contains("已删除"))
                }
            ) {
                Text(if (row.sourceProblem.contains("已删除")) "确认重新登记已删除物品" else "已核对原商品与件号，确认本条身份")
            }
        }
        val oldSources = viewModel.savedSourceOrders(row.originalItem.orderId)
        if (oldSources.isNotEmpty()) {
            val showMoney by
                com.lolita.app.di.AppModule.appPreferences()
                    .showTotalPrice
                    .collectAsState(initial = false)
            LolitaSection("已保存的来源证据") {
                oldSources.forEach { old ->
                    row {
                        Text("${old.sourceFileName} · ${old.orderStatus}")
                        Text(
                            "源实付：${displayImportAmount(OrderImportSource.amount(old.rawPaid,old.totalPaid),showMoney)}；运费：${displayImportAmount(OrderImportSource.amount(old.rawShipping,old.shipping),showMoney)}"
                        )
                        Text(old.items.joinToString("；") { "${it.styleSpec} × ${it.quantity}" })
                    }
                }
            }
        }
        TextButton(onClick = { linkDialog = true }) { Text("以前已登记？关联原物品") }
        Text(
            "原商品行 ${row.sourceLineIndex+1} · 第 ${row.sourceUnitIndex+1}/${row.originalItem.quantity} 件",
            style = MaterialTheme.typography.bodySmall,
        )
        ImportItemCard(
            item = state.importItems[index],
            index = index,
            allItems = state.importItems,
            brands = state.brands,
            categories = state.categories,
            floating = floating,
            locations = state.locations,
            storageNodes = state.storageNodes,
            onUpdate = { update -> viewModel.updateImportItem(index, update) },
            onPickLocalImage = { onPickLocalImage(index) },
            onManualPair = { target -> viewModel.manualPair(index, target) },
            onUnpair = { viewModel.unpair(index) },
            onAddCategory = viewModel::addCategory,
            onSetPaymentRole = { role -> viewModel.setPaymentRole(index, role) },
        )
    }
}

private fun LazyListScope.importOrderSummaries(
    uiState: TaobaoImportUiState,
    shownOrderIds: List<String>,
) {
    val rowsByOrder = uiState.importItems.groupBy { it.originalItem.orderId }
    shownOrderIds.forEach { orderId ->
        val rows = rowsByOrder[orderId].orEmpty()
        val original = uiState.orders.firstOrNull { it.orderId == orderId }
        if (original != null)
            item(key = "source-order-$orderId") {
                LolitaSection("源订单 $orderId") {
                    row {
                        val showMoney by
                            com.lolita.app.di.AppModule.appPreferences()
                                .showTotalPrice
                                .collectAsState(initial = false)
                        Text(
                            "${original.orderStatus} · 源实付 ${displayImportAmount(OrderImportSource.amount(original.rawPaid,original.totalPaid),showMoney)} · 源邮费 ${displayImportAmount(OrderImportSource.amount(original.rawShipping,original.shipping),showMoney)}"
                        )
                        if (
                            OrderImportSource.amount(original.rawPaid, original.totalPaid) ==
                                null ||
                                original.items.any {
                                    OrderImportSource.amount(it.rawPrice, it.price) == null
                                }
                        )
                            Text(
                                "来源金额未识别：商品金额 ${rows.firstOrNull()?.originalItem?.rawPrice.orEmpty()}；实付 ${original.rawPaid.orEmpty()}。请依据真实订单补填。",
                                color = MaterialTheme.colorScheme.error,
                            )
                        if (
                            original.orderStatus.contains("关闭") ||
                                original.orderStatus.contains("退款")
                        ) {
                            Text(
                                "该订单存在关闭或退款状态，请按真实付款及退款情况核对本件实付。源金额保留供核对，不会自动归零。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Text(
                            "本次选择 ${rows.size}/${original.items.sumOf { it.quantity }} 条资料。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
    onAddCategory: (String, CategoryGroup, CategorySemantic) -> Unit = { _, _, _ -> },
    onSetPaymentRole: (PaymentRole?) -> Unit = {},
    floating: Boolean = false,
    locations: List<Location> = emptyList(),
    storageNodes: List<StorageNode> = emptyList(),
) {
    val isValid = isImportItemReady(index, allItems)
    val priceText = item.priceInput ?: item.price.takeIf { it.isFinite() }?.toString().orEmpty()
    val shippingText =
        item.shippingInput ?: item.shipping.takeIf { it.isFinite() }?.toString().orEmpty()
    val paidText = item.paidInput ?: item.paidAmount.takeIf { it.isFinite() }?.toString().orEmpty()
    var showPairDialog by remember { mutableStateOf(false) }
    val skin = LolitaSkin.current
    val identityMissing = item.name.isBlank() || item.brandId == 0L || item.categoryId == 0L
    var identityExpanded by rememberSaveable(index) { mutableStateOf(identityMissing) }
    LaunchedEffect(identityMissing) { if (identityMissing) identityExpanded = true }

    LolitaCard(
        modifier = Modifier.fillMaxWidth(),
        variant = if (floating) CardVariant.FEATURED else CardVariant.DEFAULT,
        contentPadding = PaddingValues(skin.cardInnerPadding),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
            // 序号 + 状态标签
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "#${index + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    // 定金/尾款标签
                    if (item.paymentRole != null) {
                        val isPaired = item.pairedWith != null
                        val roleText = if (item.paymentRole == PaymentRole.DEPOSIT) "定金" else "尾款"
                        val labelColor =
                            if (isPaired) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        val labelBg =
                            if (isPaired) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            else MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                        Surface(color = labelBg, shape = MaterialTheme.shapes.small) {
                            Row(
                                Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    roleText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = labelColor,
                                )
                                if (isPaired) {
                                    Text(
                                        "-> #${item.pairedWith!! + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = labelColor,
                                    )
                                } else {
                                    Text(
                                        "(未匹配)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = labelColor,
                                    )
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
                                SkinIcon(
                                    IconKey.LinkOff,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { showPairDialog = true },
                                modifier = Modifier.size(32.dp),
                            ) {
                                SkinIcon(
                                    IconKey.Link,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    if (isValid) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                "可导入",
                                Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            Text(
                item.name,
                style =
                    if (floating) MaterialTheme.typography.titleLarge
                    else MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "源订单 ${item.originalItem.orderId} · 第 ${item.sourceUnitIndex + 1}/${item.originalItem.quantity} 件",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "源金额 ¥${item.originalItem.price} · 数量 ${item.originalItem.quantity}；下方为本件分摊金额。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (item.name != item.originalItem.name)
                com.lolita.app.ui.screen.common.ExpandableInfo("原商品名称") {
                    Text(item.originalItem.name, style = MaterialTheme.typography.bodySmall)
                }
            Text("付款类型", style = MaterialTheme.typography.labelMedium)
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(skin.spacingMedium),
            ) {
                FilterChip(
                    selected = item.paymentRole == null,
                    onClick = { onSetPaymentRole(null) },
                    label = { Text("全款") },
                )
                FilterChip(
                    selected = item.paymentRole == PaymentRole.DEPOSIT,
                    onClick = { onSetPaymentRole(PaymentRole.DEPOSIT) },
                    label = { Text("定金") },
                )
                FilterChip(
                    selected = item.paymentRole == PaymentRole.BALANCE,
                    onClick = { onSetPaymentRole(PaymentRole.BALANCE) },
                    label = { Text("尾款") },
                )
            }

            item.pairedWith?.let { pairedIndex ->
                allItems.getOrNull(pairedIndex)?.let { paired ->
                    Text(
                        "已配对：#${pairedIndex + 1} ${paired.originalItem.name}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row {
                        TextButton(onClick = { showPairDialog = true }) { Text("更换配对") }
                        TextButton(onClick = onUnpair) { Text("解除配对") }
                    }
                }
            }
            if (item.pairedWith == null) {
                if (item.paymentRole != null)
                    TextButton(onClick = { showPairDialog = true }) {
                        Text("选择${if (item.paymentRole == PaymentRole.DEPOSIT) "尾款" else "定金"}配对")
                    }
            }

            // 型号款式原始值（只读）
            if (item.styleSpec.isNotBlank()) {
                Text(
                    item.styleSpec,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val isPairedBalance = item.paymentRole == PaymentRole.BALANCE && item.pairedWith != null
            if (floating) SectionHeader("金额与付款")

            if (isPairedBalance) {
                // 尾款已关联：只显示价格和日期
                Text(
                    "数据将从定金项 #${item.pairedWith!! + 1} 继承",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = priceText,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        onValueChange = { v ->
                            onUpdate {
                                it.copy(priceInput = v, price = v.toDoubleOrNull() ?: Double.NaN)
                            }
                        },
                        label = { Text(if (floating) "本件尾款金额" else "尾款金额") },
                        modifier = Modifier.fillMaxWidth().then(importInputVisibility(priceText)),
                        singleLine = true,
                        prefix = { Text("¥") },
                    )
                    OutlinedTextField(
                        value = item.purchaseDate,
                        onValueChange = { v -> onUpdate { it.copy(purchaseDate = v) } },
                        label = { Text("实际付款日期") },
                        modifier =
                            Modifier.fillMaxWidth().then(importInputVisibility(item.purchaseDate)),
                        singleLine = true,
                    )
                }
            } else {
                // 普通商品或定金：显示完整字段
                if (!floating)
                    ImportIdentityFields(
                        item,
                        brands,
                        categories,
                        onUpdate,
                        onAddCategory,
                        onPickLocalImage,
                        showImage = false,
                        locations = locations,
                        storageNodes = storageNodes,
                        allowStorage = isImportStorageAvailable(index, allItems),
                    )
                // 价格 + 付款日期 (一行两个)
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = priceText,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        onValueChange = { v ->
                            onUpdate {
                                it.copy(priceInput = v, price = v.toDoubleOrNull() ?: Double.NaN)
                            }
                        },
                        label = {
                            Text(
                                if (floating) {
                                    if (item.paymentRole == PaymentRole.DEPOSIT) "本件定金金额"
                                    else "本件金额"
                                } else if (item.paymentRole == PaymentRole.DEPOSIT) "定金金额"
                                else "本件金额"
                            )
                        },
                        modifier = Modifier.fillMaxWidth().then(importInputVisibility(priceText)),
                        singleLine = true,
                        prefix = { Text("¥") },
                    )
                    OutlinedTextField(
                        value = item.purchaseDate,
                        onValueChange = { v -> onUpdate { it.copy(purchaseDate = v) } },
                        label = { Text("实际付款日期") },
                        modifier =
                            Modifier.fillMaxWidth().then(importInputVisibility(item.purchaseDate)),
                        singleLine = true,
                    )
                }

                // 定金已关联时显示尾款价格（只读）
                if (item.paymentRole == PaymentRole.DEPOSIT && item.pairedWith != null) {
                    val balanceItem = allItems.getOrNull(item.pairedWith!!)
                    if (balanceItem != null) {
                        OutlinedTextField(
                            value =
                                if (balanceItem.price > 0) String.format("%.2f", balanceItem.price)
                                else "",
                            onValueChange = {},
                            label = { Text("关联尾款价格 (#${item.pairedWith!! + 1})") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            prefix = { Text("¥") },
                            readOnly = true,
                            enabled = false,
                            colors =
                                OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                        )
                    }
                }

                // 未匹配定金：手动填写尾款信息
                if (item.paymentRole == PaymentRole.DEPOSIT && item.pairedWith == null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        "尾款信息（手动填写）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    val balanceText =
                        item.balanceInput
                            ?: item.manualBalance?.let { String.format("%.2f", it) }.orEmpty()
                    var showBalanceDatePicker by remember { mutableStateOf(false) }

                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = balanceText,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            onValueChange = { v ->
                                onUpdate {
                                    it.copy(balanceInput = v, manualBalance = v.toDoubleOrNull())
                                }
                            },
                            label = { Text("尾款金额") },
                            modifier =
                                Modifier.fillMaxWidth().then(importInputVisibility(balanceText)),
                            singleLine = true,
                            prefix = { Text("¥") },
                        )
                        Box(
                            modifier =
                                Modifier.fillMaxWidth().skinClickable {
                                    showBalanceDatePicker = true
                                }
                        ) {
                            OutlinedTextField(
                                value =
                                    item.balanceDueDate?.let {
                                        java.text
                                            .SimpleDateFormat(
                                                "yyyy-MM-dd",
                                                java.util.Locale.getDefault(),
                                            )
                                            .format(java.util.Date(it))
                                    } ?: "",
                                onValueChange = {},
                                label = { Text(if (floating) "尾款应付日期" else "尾款应付日期") },
                                placeholder = { Text("日期待定") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                readOnly = true,
                                enabled = false,
                                colors =
                                    OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                                        disabledLabelColor =
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                            )
                        }
                    }

                    if (showBalanceDatePicker) {
                        val balanceDatePickerState =
                            rememberDatePickerState(
                                initialSelectedDateMillis =
                                    item.balanceDueDate?.let { localDateToPickerMillis(it) }
                            )
                        DatePickerDialog(
                            onDismissRequest = { showBalanceDatePicker = false },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        balanceDatePickerState.selectedDateMillis?.let { millis ->
                                            onUpdate {
                                                it.copy(
                                                    balanceDueDate = pickerMillisToLocalDate(millis)
                                                )
                                            }
                                        }
                                        showBalanceDatePicker = false
                                    }
                                ) {
                                    Text("确定")
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        onUpdate { it.copy(balanceDueDate = null) }
                                        showBalanceDatePicker = false
                                    }
                                ) {
                                    Text("日期待定")
                                }
                            },
                        ) {
                            DatePicker(state = balanceDatePickerState)
                        }
                    }
                }

                if (!floating) ImportImageFields(item, onUpdate, onPickLocalImage)
            } // end else (非尾款关联)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = shippingText,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    onValueChange = { value ->
                        onUpdate {
                            it.copy(
                                shippingInput = value,
                                shipping = value.toDoubleOrNull() ?: Double.NaN,
                            )
                        }
                    },
                    label = { Text("本件分摊邮费") },
                    prefix = { Text("¥") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().then(importInputVisibility(shippingText)),
                )
                OutlinedTextField(
                    value = paidText,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    onValueChange = { value ->
                        onUpdate {
                            it.copy(
                                paidInput = value,
                                paidAmount = value.toDoubleOrNull() ?: Double.NaN,
                            )
                        }
                    },
                    label = { Text("本件实际已付") },
                    prefix = { Text("¥") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().then(importInputVisibility(paidText)),
                )
            }
            if (floating && !isPairedBalance) {
                SectionHeader(
                    "服饰资料",
                    action = {
                        TextButton(onClick = { identityExpanded = !identityExpanded }) {
                            Text(if (identityExpanded) "收起资料" else "展开资料")
                        }
                    },
                )
                if (identityExpanded)
                    ImportIdentityFields(
                        item,
                        brands,
                        categories,
                        onUpdate,
                        onAddCategory,
                        onPickLocalImage,
                        locations = locations,
                        storageNodes = storageNodes,
                        allowStorage = isImportStorageAvailable(index, allItems),
                    )
            }
            Text(
                "下单时间不代表付款时间。原下单时间：${item.originalItem.orderTime}。请填写真实付款日期；日期未知时本条保留草稿。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            var showPaidDatePicker by remember { mutableStateOf(false) }
            FlowRow {
                TextButton(onClick = { showPaidDatePicker = true }) { Text("选择实际付款日期") }
                TextButton(
                    onClick = {
                        onUpdate { it.copy(purchaseDate = "", financialConfirmed = false) }
                    }
                ) {
                    Text("日期记不清，暂留草稿")
                }
            }
            if (showPaidDatePicker) {
                val dateState =
                    rememberDatePickerState(
                        initialSelectedDateMillis =
                            parseImportDate(item.purchaseDate)?.let(::localDateToPickerMillis)
                    )
                DatePickerDialog(
                    onDismissRequest = { showPaidDatePicker = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                dateState.selectedDateMillis?.let { millis ->
                                    val date = pickerMillisToLocalDate(millis)
                                    onUpdate {
                                        it.copy(
                                            purchaseDate =
                                                java.text
                                                    .SimpleDateFormat(
                                                        "yyyy-MM-dd",
                                                        java.util.Locale.CHINA,
                                                    )
                                                    .format(java.util.Date(date))
                                        )
                                    }
                                }
                                showPaidDatePicker = false
                            }
                        ) {
                            Text("使用所选日期")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPaidDatePicker = false }) { Text("取消") }
                    },
                ) {
                    DatePicker(dateState)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = item.financialConfirmed,
                    onCheckedChange = { checked ->
                        onUpdate { it.copy(financialConfirmed = checked) }
                    },
                )
                Text("已核对本件金额、邮费、实付和日期", style = MaterialTheme.typography.bodyMedium)
            }
            if (item.paymentRole == PaymentRole.BALANCE && item.pairedWith == null)
                Text(
                    "未配对尾款缺少完整订单价格，请配对定金；确为全款商品时改为全款。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
        }
    }

    // 手动匹配对话框
    if (showPairDialog) {
        val targetRole =
            if (item.paymentRole == PaymentRole.DEPOSIT) PaymentRole.BALANCE
            else PaymentRole.DEPOSIT
        val candidates =
            allItems.mapIndexedNotNull { idx, candidate ->
                if (
                    idx != index &&
                        candidate.committedItemId == null &&
                        candidate.paymentRole == targetRole &&
                        (candidate.pairedWith == null || candidate.pairedWith == index)
                )
                    idx to candidate
                else null
            }
        AlertDialog(
            onDismissRequest = { showPairDialog = false },
            title = { Text("选择配对的${if (targetRole == PaymentRole.BALANCE) "尾款" else "定金"}") },
            text = {
                if (candidates.isEmpty()) {
                    Text(
                        "没有可配对的${if (targetRole == PaymentRole.BALANCE) "尾款" else "定金"}项",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 350.dp)) {
                        items(candidates) { (idx, candidate) ->
                            Column(
                                Modifier.fillMaxWidth()
                                    .skinClickable {
                                        onManualPair(idx)
                                        showPairDialog = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp)
                            ) {
                                Text(
                                    "#${idx + 1} ${candidate.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (candidate.styleSpec.isNotBlank()) {
                                    Text(
                                        candidate.styleSpec,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    "¥${String.format("%.2f", candidate.price)}  ${candidate.originalItem.shopName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPairDialog = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun ImportIdentityFields(
    item: ImportItemState,
    brands: List<Brand>,
    categories: List<Category>,
    onUpdate: ((ImportItemState) -> ImportItemState) -> Unit,
    onAddCategory: (String, CategoryGroup, CategorySemantic) -> Unit,
    onPickLocalImage: () -> Unit,
    showImage: Boolean = true,
    locations: List<Location> = emptyList(),
    storageNodes: List<StorageNode> = emptyList(),
    allowStorage: Boolean = false,
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
            maxLines = 2,
        )
        // 品牌选择（搜索对话框）
        ImportBrandSelector(
            selectedBrandId = item.brandId,
            brands = brands,
            onBrandSelected = { id -> onUpdate { it.copy(brandId = id) } },
        )

        // 分类选择（下拉）
        ImportCategorySelector(
            selectedCategoryId = item.categoryId,
            categories = categories,
            onCategorySelected = { id -> onUpdate { it.copy(categoryId = id) } },
            onAddCategory = onAddCategory,
        )
        OutlinedTextField(
            value = item.color,
            onValueChange = { v -> onUpdate { it.copy(color = v) } },
            label = { Text("颜色") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        SizeSelector(
            selectedSize = item.size.ifBlank { null },
            onSizeSelected = { size -> onUpdate { it.copy(size = size.orEmpty()) } },
            modifier = Modifier.fillMaxWidth(),
        )

        WardrobeSpecificationEditor(
            categories.firstOrNull { it.id == item.categoryId }?.categorySemantic
                ?: CategorySemantic.UNCONFIRMED,
            item.specifications,
            item.composition,
            true,
            { value -> onUpdate { it.copy(specifications = value) } },
            { value -> onUpdate { it.copy(composition = value) } },
        )
        if (allowStorage) {
            StorageLocationPicker(
                item.locationId,
                item.storageNodeId,
                locations,
                storageNodes,
                { room, node ->
                    onUpdate {
                        it.copy(locationId = room, storageNodeId = node, locationMappingNote = null)
                    }
                },
            )
            item.locationMappingNote?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (showImage) ImportImageFields(item, onUpdate, onPickLocalImage)
    }
}

@Composable
private fun ImportImageFields(
    item: ImportItemState,
    onUpdate: ((ImportItemState) -> ImportItemState) -> Unit,
    onPickLocalImage: () -> Unit,
) {
    val skin = LolitaSkin.current
    // 图片
    Text(
        "商品图片",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (item.imageUrl != null) {
        Box(Modifier.fillMaxWidth()) {
            LolitaShimmerImage(
                model = item.imageUrl,
                contentDescription = null,
                modifier =
                    Modifier.fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(skin.cornerRadiusMedium)),
                contentScale = ContentScale.Crop,
                circularRevealEnabled = false,
            )
            IconButton(
                onClick = { onUpdate { it.copy(imageUrl = null) } },
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                SkinIcon(IconKey.Close, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
    OutlinedButton(onClick = onPickLocalImage, modifier = Modifier.fillMaxWidth()) {
        SkinIcon(IconKey.AddPhoto, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(skin.spacingSmall))
        Text(if (item.imageUrl == null) "选择图片" else "替换图片")
    }
}

@Composable
private fun ImportBrandSelector(
    selectedBrandId: Long,
    brands: List<Brand>,
    onBrandSelected: (Long) -> Unit,
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
            colors =
                OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor =
                        if (isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.outline,
                    disabledLabelColor =
                        if (isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
        )
    }

    if (showDialog) {
        var searchQuery by remember { mutableStateOf("") }
        val filtered =
            remember(searchQuery, brands) {
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
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 350.dp)) {
                        items(filtered, key = { it.id }) { brand ->
                            Text(
                                brand.name,
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .skinClickable {
                                            onBrandSelected(brand.id)
                                            showDialog = false
                                        }
                                        .padding(vertical = 12.dp, horizontal = 4.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color =
                                    if (brand.id == selectedBrandId)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDialog = false }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportCategorySelector(
    selectedCategoryId: Long,
    categories: List<Category>,
    onCategorySelected: (Long) -> Unit,
    onAddCategory: (String, CategoryGroup, CategorySemantic) -> Unit = { _, _, _ -> },
) {
    var showAddDialog by remember { mutableStateOf(false) }
    WardrobeCategorySelector(
        selectedCategoryId,
        categories,
        onCategorySelected,
        isError = selectedCategoryId == 0L,
    )
    TextButton(onClick = { showAddDialog = true }) { Text("新增类型") }
    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var semantic by remember { mutableStateOf(CategorySemantic.UNCONFIRMED) }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("新增类型") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        name,
                        { name = it },
                        label = { Text("类型名称") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CategorySemanticPicker(semantic) { semantic = it }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && semantic != CategorySemantic.UNCONFIRMED,
                    onClick = {
                        onAddCategory(
                            name.trim(),
                            if (semantic.family == WardrobeFamily.CLOTHING) CategoryGroup.CLOTHING
                            else CategoryGroup.ACCESSORY,
                            semantic,
                        )
                        showAddDialog = false
                    },
                ) {
                    Text("确认创建")
                }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("取消") } },
        )
    }
}

/** Reposition the focused field after the IME and validation change the tall card's viewport. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun importInputVisibility(value: String): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, imeBottom, value) {
        if (focused && imeBottom > 0) {
            withFrameNanos {}
            requester.bringIntoView()
        }
    }
    return Modifier.bringIntoViewRequester(requester).onFocusChanged { focused = it.isFocused }
}
