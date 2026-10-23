package com.lolita.app.ui.screen.`import`

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lolita.app.data.local.entity.*
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.*
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.screen.item.CategorySemanticPicker
import com.lolita.app.ui.screen.item.itemStatusLabel
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.numericReading
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.icon.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun OrderImportExperience(
    state: TaobaoImportUiState,
    vm: TaobaoImportViewModel,
    onBack: () -> Unit,
    onDetail: (Long) -> Unit,
    onProduct: () -> Unit,
    onGuide: () -> Unit,
    onSupplement: (Long) -> Unit,
    preferences: com.lolita.app.data.preferences.AppPreferences = AppModule.appPreferences(),
) {
    val skin = LolitaSkin.current
    val list = rememberLazyListState()
    ObserveSkinScroll(list)
    val showMoney by preferences.showTotalPrice.collectAsState(initial = false)
    val snack = remember { SnackbarHostState() }
    var reloadDraft by remember { mutableStateOf(false) }
    var removeFile by remember { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf<String?>(null) }
    var filterDialog by remember { mutableStateOf(false) }
    var moveDialog by remember { mutableStateOf(false) }
    var selectedMove by rememberSaveable { mutableStateOf<List<Long>>(emptyList()) }
    var moveInitialized by rememberSaveable { mutableStateOf(false) }
    val eligible = state.resultItems.filter { it.status.supportsStorage }
    LaunchedEffect(state.currentStep, state.resultItems) {
        if (state.currentStep == ImportStep.STORAGE && !moveInitialized) {
            selectedMove = eligible.map { it.id }
            moveInitialized = true
        }
    }
    LaunchedEffect(state.currentStep) { list.scrollToItem(0) }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snack.showSnackbar(it)
            vm.clearError()
        }
    }
    val picker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments(),
            vm::onFileSelected,
        )
    fun pick() =
        picker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
    fun back() {
        when (state.currentStep) {
            ImportStep.ENTRY -> vm.saveAndLeave(onBack)
            ImportStep.ADDED,
            ImportStep.STORAGE -> vm.setStep(ImportStep.RESULT)
            ImportStep.REVIEW -> vm.setStep(ImportStep.DETAIL)
            else -> vm.setStep(ImportStep.ENTRY)
        }
    }
    BackHandler { back() }
    val stepTitle =
        when (state.currentStep) {
            ImportStep.ENTRY -> "淘宝订单导入"
            ImportStep.FILES -> "检查订单文件"
            ImportStep.SELECT -> "选择订单"
            ImportStep.CONFLICT -> "来源比较"
            ImportStep.PREPARE -> "品牌与类型"
            ImportStep.REVIEW -> "确认导入"
            ImportStep.RESULT -> "导入结果"
            ImportStep.ADDED -> "本次新增"
            ImportStep.STORAGE -> "安排收纳"
            else -> "导入资料"
        }
    val readyCount = importNewItemCount(state.importItems)
    val remaining = state.importItems.count { it.committedItemId == null }
    val primary =
        when (state.currentStep) {
            ImportStep.ENTRY -> "选择订单文件"
            ImportStep.FILES -> "使用已读文件继续"
            ImportStep.SELECT -> "确认选择，继续"
            ImportStep.CONFLICT -> if (state.conflicts.isEmpty()) "进入品牌与类型" else "请先选择来源版本"
            ImportStep.PREPARE -> "进入逐件核对"
            ImportStep.REVIEW -> "确认导入 $readyCount 件"
            ImportStep.RESULT -> "完成，稍后继续"
            ImportStep.ADDED -> "为已拥有物品安排收纳"
            ImportStep.STORAGE -> "移入所选位置"
            else -> "继续"
        }
    val enabled =
        !state.isLoading &&
            !state.isRestoring &&
            when (state.currentStep) {
                ImportStep.FILES -> state.fileLoaded
                ImportStep.SELECT -> state.selectedItems.isNotEmpty()
                ImportStep.CONFLICT -> state.conflicts.isEmpty()
                ImportStep.REVIEW -> readyCount > 0
                ImportStep.STORAGE -> selectedMove.isNotEmpty()
                else -> true
            }
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            GradientTopAppBar(
                title = { Text(stepTitle) },
                navigationIcon = { IconButton(onClick = ::back) { SkinIcon(IconKey.ArrowBack) } },
                actions = { IconButton(onClick = onGuide) { SkinIcon(IconKey.Help) } },
            )
        },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            EditorSaveBar(primary, state.isLoading, enabled) {
                when (state.currentStep) {
                    ImportStep.ENTRY -> {
                        vm.newSession(::pick)
                    }
                    ImportStep.FILES -> vm.useReadFiles()
                    ImportStep.SELECT -> vm.proceedToPrepare()
                    ImportStep.CONFLICT -> vm.proceedToPrepare()
                    ImportStep.PREPARE -> vm.confirmPrepare()
                    ImportStep.REVIEW -> vm.executeImport()
                    ImportStep.RESULT -> vm.saveAndLeave(onBack)
                    ImportStep.ADDED -> {
                        moveInitialized = false
                        vm.setStep(ImportStep.STORAGE)
                    }
                    ImportStep.STORAGE -> moveDialog = true
                    else -> Unit
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = list,
            modifier =
                Modifier.fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .testTag("orderImportExperience"),
            contentPadding = PaddingValues(skin.spacingLarge),
            verticalArrangement = Arrangement.spacedBy(skin.spacingLarge),
        ) {
            if (state.isRestoring) {
                items(3) {
                    LolitaCard {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ShimmerLine(widthFraction = .7f)
                            ShimmerLine(widthFraction = 1f)
                            ShimmerRect(width = 240.dp, height = 48.dp)
                        }
                    }
                }
            } else {
                state.draftError?.let {
                    item {
                        ImportNotice(it, true)
                        TextButton(onClick = { vm.saveAndLeave {} }) { Text("重试保存草稿") }
                        TextButton(onClick = { reloadDraft = true }) { Text("重新读取已保存草稿") }
                    }
                }
                if (state.sessionId != null)
                    item {
                        Text(
                            if (state.draftSaving) "正在保存草稿…"
                            else if (state.draftError != null) "草稿尚未保存" else "本机草稿 · 修改自动保存",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                when (state.currentStep) {
                    ImportStep.ENTRY -> {
                        item { ImportIntro("让订单，走进衣橱", "导入购买资料，再慢慢补齐照片与收纳。") }
                        items(state.sessions, key = { it.id }) { session ->
                            LolitaCard {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)
                                ) {
                                    SectionHeader("继续上次导入")
                                    Text("${session.remaining} 条未完成资料 · 已新增 ${session.added} 件")
                                    Text(
                                        SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA)
                                            .format(Date(session.updatedAt)),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    OutlinedButton(
                                        onClick = { vm.restoreSession(session.id) },
                                        Modifier.fillMaxWidth(),
                                    ) {
                                        Text(if (session.remaining > 0) "继续核对" else "查看导入结果")
                                    }
                                    TextButton(onClick = { discard = session.id }) {
                                        Text("删除未完成草稿")
                                    }
                                }
                            }
                        }
                        item {
                            LolitaSection("从订单文件开始") {
                                row { Text("在本机读取订单，不上传文件或获取账号。") }
                                row { TextButton(onClick = onGuide) { Text("查看获取订单文件指南") } }
                            }
                        }
                        item {
                            OutlinedButton(onClick = onProduct, Modifier.fillMaxWidth()) {
                                Text("商品链接导入")
                            }
                        }
                    }
                    ImportStep.FILES -> {
                        item { ImportIntro("检查订单文件", "逐个处理读取问题，已经读取的内容继续保留。") }
                        items(state.files, key = { it.id }) { file ->
                            LolitaCard {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)
                                ) {
                                    Text(file.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        if (file.state == "READY") "已读取" else "读取失败",
                                        color =
                                            if (file.state == "READY")
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.error,
                                    )
                                    file.problem?.let {
                                        Text(it, color = MaterialTheme.colorScheme.error)
                                    }
                                    if (file.state == "FAILED")
                                        TextButton(onClick = ::pick) { Text("重新选择此文件") }
                                    TextButton(onClick = { removeFile = file.id }) { Text("移除此文件") }
                                }
                            }
                        }
                        item {
                            OutlinedButton(onClick = ::pick, Modifier.fillMaxWidth()) {
                                Text("追加文件")
                            }
                        }
                        if (state.conflicts.isNotEmpty())
                            item {
                                ImportNotice("${state.conflicts.size} 笔来源资料不同，选择订单后需要比较。", true)
                            }
                        if (state.files.any { it.state == "FAILED" })
                            item { ImportNotice("继续时仅使用已读取文件。读取失败的文件不会计入本次资料。", true) }
                        if (state.files.isEmpty())
                            item {
                                Text("选择淘宝导出的 .xlsx 文件，可多选。")
                                TextButton(onClick = ::pick) { Text("选择文件") }
                            }
                    }
                    ImportStep.SELECT -> {
                        item {
                            ImportIntro(
                                "选出这次要登记的订单",
                                "${state.fileCount} 个文件 · ${state.orders.size} 笔订单",
                            )
                        }
                        item {
                            OutlinedTextField(
                                state.query,
                                vm::setQuery,
                                Modifier.fillMaxWidth(),
                                label = { Text("搜索商品、店铺或订单号") },
                                singleLine = true,
                                leadingIcon = { SkinIcon(IconKey.Search) },
                            )
                        }
                        item {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(skin.spacingMedium)
                            ) {
                                listOf("全部", "交易成功", "交易关闭", "只看已选", "日期未知").forEach { label ->
                                    FilterChip(
                                        state.selectionFilter == label,
                                        { vm.setSelectionFilter(label) },
                                        label = { Text(label) },
                                    )
                                }
                                FilterChip(
                                    false,
                                    { filterDialog = true },
                                    label = { Text("店铺 / 时间") },
                                )
                            }
                        }
                        val visible = visibleOrderLines(state)
                        val visibleKeys = visible.map { "${it.first.orderId}:${it.second}" }.toSet()
                        val hidden = (state.selectedItems - visibleKeys).size
                        val units =
                            state.orders.sumOf { order ->
                                order.items
                                    .mapIndexed { i, row ->
                                        if ("${order.orderId}:$i" in state.selectedItems)
                                            row.quantity
                                        else 0
                                    }
                                    .sum()
                            }
                        item {
                            Column {
                                Text("已选 ${state.selectedItems.size} 条商品记录 · 展开 $units 条资料")
                                if (hidden > 0)
                                    Text(
                                        "另有 $hidden 条已选被筛选隐藏",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                FlowRow {
                                    TextButton(onClick = { vm.selectVisible(true) }) {
                                        Text("全选当前结果")
                                    }
                                    TextButton(onClick = { vm.selectVisible(false) }) {
                                        Text("清除当前结果")
                                    }
                                    TextButton(onClick = vm::deselectAll) { Text("清空全部选择") }
                                }
                            }
                        }
                        if (visible.isEmpty()) item { ImportNotice("没有匹配记录，已经选择的资料仍然保留。") }
                        items(visible, key = { "${it.first.orderId}:${it.second}" }) {
                            (order, index) ->
                            val row = order.items[index]
                            val checked = "${order.orderId}:$index" in state.selectedItems
                            LolitaCard {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)
                                ) {
                                    Row(verticalAlignment = Alignment.Top) {
                                        Checkbox(checked, { vm.toggleItem(order.orderId, index) })
                                        Text(
                                            row.name,
                                            Modifier.weight(1f),
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                    }
                                    Text(
                                        order.shopName,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        "${row.styleSpec} · 数量 ${row.quantity}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        "${order.orderTime} · ${order.orderStatus}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                    Text("订单实付（含运费）", style = MaterialTheme.typography.bodySmall)
                                    ImportAmount(
                                        OrderImportSource.amount(order.rawPaid, order.totalPaid),
                                        showMoney,
                                    )
                                }
                            }
                        }
                    }
                    ImportStep.CONFLICT -> {
                        item { ImportIntro("同一订单，资料有变化", "选择来源只更新未提交草稿，已经保存的账本需要在原订单核对。") }
                        items(state.conflicts, key = { it.orderId }) { conflict ->
                            LolitaSection("订单 ${conflict.orderId}") {
                                conflict.versions.forEachIndexed { index, order ->
                                    row {
                                        Column(
                                            verticalArrangement =
                                                Arrangement.spacedBy(skin.spacingSmall)
                                        ) {
                                            Text(
                                                order.sourceFileName.ifBlank { "来源 ${index+1}" },
                                                style = MaterialTheme.typography.titleSmall,
                                            )
                                            Text(
                                                "${order.orderStatus} · ${order.items.sumOf { it.quantity }} 条资料"
                                            )
                                            order.items.forEach {
                                                Text(
                                                    "${it.name} · ${it.styleSpec} × ${it.quantity}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                                Text(
                                                    "商品金额：${displayImportAmount(OrderImportSource.amount(it.rawPrice,it.price),showMoney)}；付款日期：${it.actualPaidDate ?: "未知"}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                            Text(
                                                "实付：${displayImportAmount(OrderImportSource.amount(order.rawPaid,order.totalPaid),showMoney)}"
                                            )
                                            Text(
                                                "运费：${displayImportAmount(OrderImportSource.amount(order.rawShipping,order.shipping),showMoney)}"
                                            )
                                            OutlinedButton(
                                                onClick = {
                                                    vm.resolveConflict(conflict.orderId, index)
                                                },
                                                Modifier.fillMaxWidth(),
                                            ) {
                                                Text("使用此版本核对")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (state.conflicts.isEmpty()) item { ImportNotice("来源差异已处理，可以继续。") }
                    }
                    ImportStep.PREPARE -> {
                        item { ImportIntro("确认品牌与类型", "店铺不等于品牌，可以使用已有品牌、新建或保持未设置。") }
                        items(
                            state.orders
                                .filter { order ->
                                    order.items.indices.any {
                                        "${order.orderId}:$it" in state.selectedItems
                                    }
                                }
                                .map { it.shopName }
                                .distinct(),
                            key = { "shop:$it" },
                        ) { shop ->
                            LolitaCard {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)
                                ) {
                                    SectionHeader(shop)
                                    Text(
                                        "未核验的名称不会自动匹配相似品牌。",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    ImportBrandPicker(
                                        state.brands,
                                        state.brandMappings[shop]
                                            ?: state.brands.firstOrNull { it.isUnsetBrand }?.id
                                            ?: 0L,
                                    ) {
                                        vm.setBrandMapping(shop, it)
                                    }
                                    val candidateIndex =
                                        state.missingItems.indexOfFirst {
                                            it.type == MissingDataType.BRAND && it.name == shop
                                        }
                                    if (candidateIndex >= 0)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                state.missingItems[candidateIndex].checked,
                                                { vm.toggleMissingItem(candidateIndex) },
                                            )
                                            Text("新建品牌：$shop", Modifier.weight(1f))
                                        }
                                }
                            }
                        }
                        items(
                            state.missingItems.mapIndexedNotNull { index, row ->
                                if (row.type == MissingDataType.CATEGORY) index to row else null
                            },
                            key = { "category:${it.first}" },
                        ) { (index, row) ->
                            LolitaCard {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)
                                ) {
                                    Row {
                                        Checkbox(row.checked, { vm.toggleMissingItem(index) })
                                        Text("新增类型：${row.name}", Modifier.weight(1f))
                                    }
                                    CategorySemanticPicker(row.semantic) {
                                        vm.confirmMissingSemantic(index, it)
                                    }
                                }
                            }
                        }
                    }
                    ImportStep.REVIEW -> {
                        item { ImportIntro("确认这次写入的内容", "核对完成的资料才会进入衣橱。") }
                        val ready =
                            state.importItems.indices
                                .filter { isImportItemReady(it, state.importItems) }
                                .map { state.importItems[it] }
                        item {
                            LolitaSection("本次范围") {
                                row { Text("本次使用 ${ready.size} 条资料，新增 $readyCount 件物品") }
                                row {
                                    Text(
                                        "商品金额：${displayImportAmount(importSum(ready.map { it.price }),showMoney)}"
                                    )
                                }
                                row {
                                    Text(
                                        "运费：${displayImportAmount(importSum(ready.map { it.shipping }),showMoney)}"
                                    )
                                }
                                row {
                                    Text("本次记录实付")
                                    ImportAmount(importSum(ready.map { it.paidAmount }), showMoney)
                                }
                            }
                        }
                        item {
                            ImportNotice("另有 ${remaining-ready.size} 条留在草稿。保存失败时本批全部回滚，草稿和图片保留。")
                        }
                        val reasons =
                            state.importItems.indices
                                .filter {
                                    state.importItems[it].committedItemId == null &&
                                        !isImportItemReady(it, state.importItems)
                                }
                                .flatMap { importProblems(it, state.importItems) }
                                .groupingBy { it }
                                .eachCount()
                        if (reasons.isNotEmpty())
                            item {
                                LolitaSection("剩余待处理") {
                                    reasons.forEach { (reason, count) ->
                                        row { Text("$reason · $count 条") }
                                    }
                                    row {
                                        Text("原因可能重叠。", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                    }
                    ImportStep.RESULT -> {
                        val result = state.importResult
                        item {
                            ImportIntro(
                                if ((result?.linkedCount ?: 0) > 0)
                                    "已关联 ${result?.linkedCount} 件原物品，没有新增"
                                else "${result?.importedCount ?: 0} 件物品已加入衣橱",
                                "来源与付款证据已保留。",
                            )
                        }
                        item {
                            LolitaCard {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)
                                ) {
                                    OutlinedButton(
                                        onClick = vm::showAdded,
                                        Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            if (
                                                state.resultItems.isEmpty() &&
                                                    state.linkedResultItems.isNotEmpty()
                                            )
                                                "查看已关联物品"
                                            else "查看本次新增"
                                        )
                                    }
                                    Text("还剩 $remaining 条未完成资料")
                                    if (remaining > 0)
                                        TextButton(onClick = vm::resumeRemaining) {
                                            Text("继续核对剩余资料")
                                        }
                                }
                            }
                        }
                        if (state.resultItems.any { it.status.supportsStorage })
                            item {
                                TextButton(
                                    onClick = {
                                        moveInitialized = false
                                        vm.setStep(ImportStep.STORAGE)
                                    }
                                ) {
                                    Text("为已拥有物品安排收纳")
                                }
                            }
                        item {
                            Text(
                                "未完成草稿可稍后继续，离开不会自动导入剩余资料。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    ImportStep.ADDED -> {
                        item { ImportIntro("本次会话的物品", "新增与原物品关联分开列出；已删除的新增项保留记录。") }
                        items(state.resultItems, key = { it.id }) { item ->
                            LolitaCard {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)
                                ) {
                                    item.imageUrls.firstOrNull()?.let {
                                        LolitaShimmerImage(
                                            it,
                                            item.name,
                                            Modifier.fillMaxWidth().heightIn(max = 180.dp),
                                            contentScale = ContentScale.Fit,
                                        )
                                    }
                                    Text(item.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${item.size.orEmpty()} · ${itemStatusLabel(item.status)}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    TextButton(onClick = { onDetail(item.id) }) { Text("查看物品") }
                                    TextButton(onClick = { onSupplement(item.id) }) {
                                        Text("补全本件商品资料")
                                    }
                                }
                            }
                        }
                        items(state.deletedResultItemIds, key = { "deleted:$it" }) { id ->
                            ImportNotice("本次新增物品 #$id 已删除，历史来源记录保留。")
                        }
                        if (state.linkedResultItems.isNotEmpty())
                            item { SectionHeader("已关联原物品 · ${state.linkedResultItems.size} 件") }
                        items(state.linkedResultItems, key = { "linked:${it.id}" }) { item ->
                            LolitaCard {
                                Column {
                                    Text(item.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "本次未新增，保留原账本与资料。",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    TextButton(onClick = { onDetail(item.id) }) { Text("查看原物品") }
                                    TextButton(onClick = { onSupplement(item.id) }) {
                                        Text("补全本件商品资料")
                                    }
                                }
                            }
                        }
                        if (
                            state.resultItems.isEmpty() &&
                                state.deletedResultItemIds.isEmpty() &&
                                state.linkedResultItems.isEmpty()
                        )
                            item { ImportNotice("本会话还没有新增物品，可返回继续核对。") }
                    }
                    ImportStep.STORAGE -> {
                        item { ImportIntro("为已拥有物品安排收纳", "仅选择本次新增且当前已拥有的物品。") }
                        items(state.resultItems, key = { it.id }) { item ->
                            LolitaCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        item.id in selectedMove,
                                        { checked ->
                                            selectedMove =
                                                if (checked) selectedMove + item.id
                                                else selectedMove - item.id
                                        },
                                        enabled = item.status.supportsStorage,
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(item.name, style = MaterialTheme.typography.titleSmall)
                                        if (!item.status.supportsStorage)
                                            Text(
                                                "当前拥有状态不支持收纳",
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                    }
                                }
                            }
                        }
                        item {
                            Text(
                                "保存前重新检查状态与修改时间。变化项会阻止本次移动，供你重新核对。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
    if (reloadDraft)
        AlertDialog(
            onDismissRequest = { reloadDraft = false },
            title = { Text("重新读取已保存草稿？") },
            text = { Text("本页尚未保存的修改将被已保存版本替换。确认前请保留需要的输入；已保存物品与账本不变。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        reloadDraft = false
                        state.sessionId?.let(vm::restoreSession)
                    }
                ) {
                    Text("重新读取")
                }
            },
            dismissButton = { TextButton(onClick = { reloadDraft = false }) { Text("保留当前输入") } },
        )
    removeFile?.let { id ->
        val affected = vm.affectedByFileRemoval(id)
        AlertDialog(
            onDismissRequest = { removeFile = null },
            title = { Text("移除此文件？") },
            text = {
                Column {
                    Text("将移除本文件独有的 ${affected.size} 条未提交资料和核对输入。已保存物品、账本与其他文件资料保留。")
                    affected.take(5).forEach {
                        Text(it.name, style = MaterialTheme.typography.bodySmall)
                    }
                    if (affected.size > 5) Text("等 ${affected.size} 条资料")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.removeFile(id)
                        removeFile = null
                    }
                ) {
                    Text("移除文件及未提交资料")
                }
            },
            dismissButton = { TextButton(onClick = { removeFile = null }) { Text("保留") } },
        )
    }
    discard?.let { id ->
        AlertDialog(
            onDismissRequest = { discard = null },
            title = { Text("删除未完成草稿？") },
            text = { Text("将删除未提交输入和未引用图片。已保存物品、付款与永久来源记录保留。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.discardSession(id)
                        discard = null
                    }
                ) {
                    Text("删除草稿")
                }
            },
            dismissButton = { TextButton(onClick = { discard = null }) { Text("继续保留") } },
        )
    }
    if (filterDialog) {
        var shop by remember { mutableStateOf(state.shopFilter) }
        var from by remember { mutableStateOf(state.dateFrom) }
        var to by remember { mutableStateOf(state.dateTo) }
        val validDates =
            listOf(from, to).all {
                it.isBlank() ||
                    (it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && parseImportDate(it) != null)
            } && (from.isBlank() || to.isBlank() || from <= to)
        AlertDialog(
            onDismissRequest = { filterDialog = false },
            title = { Text("筛选订单") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                    OutlinedTextField(shop, { shop = it }, label = { Text("店铺名称") })
                    OutlinedTextField(from, { from = it }, label = { Text("下单日期从 YYYY-MM-DD") })
                    OutlinedTextField(to, { to = it }, label = { Text("至 YYYY-MM-DD") })
                    if (!validDates)
                        Text("请输入有效日期，结束日期不能早于开始日期", color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.setSelectionBounds(shop, from.trim(), to.trim())
                        filterDialog = false
                    },
                    enabled = validDates,
                ) {
                    Text("应用筛选")
                }
            },
            dismissButton = { TextButton(onClick = { filterDialog = false }) { Text("取消") } },
        )
    }
    if (moveDialog)
        StorageDestinationDialog(
            state.locations,
            state.storageNodes,
            { moveDialog = false },
            { room, node ->
                if (room != null)
                    vm.moveImported(
                        selectedMove.toSet(),
                        room,
                        node,
                        state.resultItems.associate { it.id to it.updatedAt },
                    ) {
                        moveDialog = false
                        vm.setStep(ImportStep.ADDED)
                    }
            },
            allowUnassigned = false,
            title = "将 ${selectedMove.size} 件移入位置",
        )
}

@Composable
internal fun ImportIntro(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(LolitaSkin.current.spacingMedium)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun ImportNotice(message: String, error: Boolean = false) {
    LolitaCard(variant = CardVariant.COMPACT) {
        Text(
            message,
            color =
                if (error) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

internal fun displayImportAmount(value: Double?, show: Boolean): String =
    if (!show) "金额已隐藏" else value?.let { "¥${String.format(Locale.CHINA,"%.2f",it)}" } ?: "金额待核对"

@Composable
internal fun ImportAmount(value: Double?, show: Boolean) {
    Text(
        displayImportAmount(value, show),
        Modifier.horizontalScroll(rememberScrollState()),
        style = MaterialTheme.typography.titleLarge.numericReading,
    )
}

@Composable
internal fun ImportBrandPicker(brands: List<Brand>, selected: Long, onSelect: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    OutlinedButton(onClick = { open = true }, Modifier.fillMaxWidth()) {
        Text("品牌：${brands.firstOrNull { it.id==selected }?.name ?: "未设置"}")
    }
    if (open) {
        val list = rememberLazyListState()
        ObserveSkinScroll(list)
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("选择品牌") },
            text = {
                Column {
                    OutlinedTextField(
                        query,
                        { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("搜索已有品牌") },
                        singleLine = true,
                    )
                    LazyColumn(state = list, modifier = Modifier.heightIn(max = 360.dp)) {
                        items(brands.filter { it.name.contains(query, true) }, key = { it.id }) {
                            brand ->
                            TextButton(
                                onClick = {
                                    onSelect(brand.id)
                                    open = false
                                },
                                Modifier.fillMaxWidth(),
                            ) {
                                Text(brand.name)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("取消") } },
        )
    }
}

@Composable
internal fun ImportLinkExistingDialog(
    index: Int,
    row: ImportItemState,
    vm: TaobaoImportViewModel,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf(row.name) }
    var choices by remember { mutableStateOf<List<Item>>(emptyList()) }
    var target by remember { mutableStateOf<Item?>(null) }
    val list = rememberLazyListState()
    ObserveSkinScroll(list)
    LaunchedEffect(query) { choices = vm.findExistingItems(query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关联已有物品") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LolitaSkin.current.spacingMedium)) {
                Text("只补充来源关联，不新建物品，也不改变原价格、付款或拥有状态。请确认原商品与件号对应。")
                OutlinedTextField(
                    query,
                    { query = it },
                    label = { Text("搜索原物品") },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(state = list, modifier = Modifier.heightIn(max = 280.dp)) {
                    items(choices, key = { it.id }) { item ->
                        TextButton(
                            onClick = { target = item },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "${if(target?.id==item.id)"✓ " else ""}${item.name} · ${item.size.orEmpty()}"
                            )
                        }
                    }
                }
                target?.let { Text("将关联：${it.name}（ID ${it.id}）") }
            }
        },
        confirmButton = {
            TextButton(
                enabled = target != null,
                onClick = {
                    target?.let { vm.linkExisting(index, it.id) }
                    onDismiss()
                },
            ) {
                Text("确认关联原物品")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun ImportBatchAttributesDialog(
    state: TaobaoImportUiState,
    vm: TaobaoImportViewModel,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var query by remember { mutableStateOf("") }
    var brandId by remember { mutableStateOf<Long?>(null) }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    val list = rememberLazyListState()
    ObserveSkinScroll(list)
    val candidates =
        state.importItems.indices.filter { state.importItems[it].committedItemId == null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("批量设置属性") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LolitaSkin.current.spacingMedium)) {
                Text("已选 ${selected.size} 条资料，包含搜索隐藏的选择。财务字段仍需逐件核对。")
                OutlinedTextField(
                    query,
                    { query = it },
                    label = { Text("搜索待导入资料") },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(state = list, modifier = Modifier.heightIn(max = 200.dp)) {
                    items(
                        candidates.filter { state.importItems[it].name.contains(query, true) },
                        key = { it },
                    ) { index ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                index in selected,
                                { checked ->
                                    selected = if (checked) selected + index else selected - index
                                },
                            )
                            Text(
                                "${state.importItems[index].name} · 第 ${state.importItems[index].sourceUnitIndex+1} 件\n" +
                                    "品牌：${state.brands.firstOrNull { it.id==state.importItems[index].brandId }?.name ?: "未设置"} → ${state.brands.firstOrNull { it.id==brandId }?.name ?: "不修改"}\n" +
                                    "类型：${state.categories.firstOrNull { it.id==state.importItems[index].categoryId }?.name ?: "未设置"} → ${state.categories.firstOrNull { it.id==categoryId }?.name ?: "不修改"}",
                                Modifier.weight(1f),
                            )
                        }
                    }
                }
                ImportBrandPicker(state.brands, brandId ?: 0L) { brandId = it }
                com.lolita.app.ui.screen.item.WardrobeCategorySelector(
                    categoryId ?: 0L,
                    state.categories,
                    { categoryId = it },
                )
                Text(
                    "本次影响 ${selected.size} 条；品牌 ${brandId?.let { id->state.brands.firstOrNull { it.id==id }?.name } ?: "不修改"}，类型 ${categoryId?.let { id->state.categories.firstOrNull { it.id==id }?.name } ?: "不修改"}。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.isNotEmpty() && (brandId != null || categoryId != null),
                onClick = {
                    vm.applyBatchAttributes(selected, brandId, categoryId)
                    onDismiss()
                },
            ) {
                Text("应用到所选资料")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
