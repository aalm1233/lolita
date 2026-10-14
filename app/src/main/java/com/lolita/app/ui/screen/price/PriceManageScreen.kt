package com.lolita.app.ui.screen.price

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.Price
import com.lolita.app.data.local.entity.PriceType
import com.lolita.app.data.local.entity.PurchaseRecordStatus
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.LolitaSection
import com.lolita.app.ui.screen.common.SkinEmptyState
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.skinItemAppear
import com.lolita.app.ui.theme.skin.animation.rememberSkinFlingBehavior
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceManageScreen(
    itemId: Long, onBack: () -> Unit, onNavigateToPriceEdit: (Long?) -> Unit,
    onNavigateToPaymentManage: (Long) -> Unit,
    onNavigateToParentPriceManage: (Long) -> Unit = {},
    onNavigateToItemEdit: (Long) -> Unit = {},
    viewModel: PriceManageViewModel = viewModel(factory = PriceManageViewModelFactory(itemId))
) {
    val state by viewModel.uiState.collectAsState()
    val skin = LolitaSkin.current
    var orderToBind by remember { mutableStateOf<Price?>(null) }
    var confirmArchive by remember { mutableStateOf(false) }
    var purchaseConfirmation by remember { mutableStateOf<PurchaseRecordStatus?>(null) }
    Scaffold(containerColor = Color.Transparent, topBar = { GradientTopAppBar(title = { Text("价格与付款") },
        navigationIcon = { IconButton(onClick = onBack) { SkinIcon(IconKey.ArrowBack) } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = skin.spacingLarge).testTag("priceManageList"),
            contentPadding = PaddingValues(vertical = skin.spacingLarge), verticalArrangement = Arrangement.spacedBy(skin.cardGap),
            flingBehavior = rememberSkinFlingBehavior()) {
            if (state.isLoading) item { PricingSkeleton() }
            else if (state.loadFailed) item {
                LolitaSection("价格暂时无法读取") { row {
                    Text("请返回后重试，现有价格与付款记录保留。")
                } }
            } else {
                if (state.includedInParentPrice) item {
                    LolitaSection("共用主件价格") { row {
                        Text("已包含在${state.parentName ?: "主件"}价格中，小物不重复记账。", style = MaterialTheme.typography.bodyMedium)
                        Text("付款状态随主件同步。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.needsPurchaseReview) OutlinedButton(
                            enabled = !state.isUpdating && state.parentItemId != null,
                            onClick = { state.parentItemId?.let(onNavigateToParentPriceManage) },
                            modifier = Modifier.fillMaxWidth().testTag("reviewParentPriceEntry")
                        ) { Text("前往主件核对") }
                    } }
                }
                val order = state.order
                if (order != null) item {
                    var detailsExpanded by rememberSaveable(order.price.id, order.purchaseRecordStatus) { mutableStateOf(false) }
                    LolitaSection(if (state.includedInParentPrice) "主件订单" else "当前订单", action = if (state.includedInParentPrice) null else {
                        { IconButton(enabled = !state.isUpdating && order.purchaseRecordStatus == PurchaseRecordStatus.TRACKED,
                            onClick = { onNavigateToPriceEdit(order.price.id) }) { SkinIcon(IconKey.Edit) } }
                    }) {
                        row {
                            OrderStatusText(order.purchaseRecordStatus, order.remainingAmount,
                                if (order.price.type == PriceType.DEPOSIT_BALANCE) "待补尾款" else "待付款")
                            OrderSummaryAmount(if (order.purchaseRecordStatus == PurchaseRecordStatus.TRACKED) "应付总额" else "订单总价", formatPriceAmount(order.price.totalPrice))
                            if (order.purchaseRecordStatus == PurchaseRecordStatus.TRACKED) {
                                PricingValueRow("实际已付", formatPriceAmount(order.paidAmount), MaterialTheme.colorScheme.primary)
                                PricingValueRow("未付余额", formatPriceAmount(order.remainingAmount.coerceAtLeast(0.0)), if (order.remainingAmount > 0.005) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                            } else {
                                PricingValueRow("已录实付", if (order.payments.isEmpty()) "付款记录未补全" else formatPriceAmount(order.paidAmount))
                                PricingValueRow("未付余额", "未知")
                                Text("价格与付款阶段只读，购买资料仍待核对。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (order.paidAmount > order.price.totalPrice + 0.005) Text("历史实付超过订单总额，请核对原始付款。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            if (order.purchaseRecordStatus == PurchaseRecordStatus.TRACKED && order.payments.any { it.isPaid && it.paidDate == null }) Text(
                                "实付日期待补：账本金额已核对，仍有真实付款日期未知。补齐前仍留在待核对购买资料清单。",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (state.includedInParentPrice || order.purchaseRecordStatus == PurchaseRecordStatus.TRACKED) {
                                Button(enabled = !state.isUpdating, onClick = { onNavigateToPaymentManage(order.price.id) }, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (state.includedInParentPrice) "查看主件付款" else "付款阶段与实付记录")
                                }
                            } else {
                                TextButton(enabled = !state.isUpdating, onClick = { onNavigateToPaymentManage(order.price.id) }, modifier = Modifier.fillMaxWidth()) { Text("查看付款阶段与实付记录") }
                            }
                            TextButton(onClick = { detailsExpanded = !detailsExpanded }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (detailsExpanded) "收起价格明细" else "展开价格明细")
                                Spacer(Modifier.width(skin.spacingSmall))
                                SkinIcon(if (detailsExpanded) IconKey.ExpandLess else IconKey.ExpandMore)
                            }
                            if (detailsExpanded) {
                                PricingValueRow("付款形式", order.price.type.priceLabel())
                                PricingValueRow("商品金额", formatPriceAmount(order.price.totalPrice - order.price.shipping))
                                PricingValueRow("邮费", formatPriceAmount(order.price.shipping))
                            }
                        }
                        if (!state.includedInParentPrice) row {
                            if (order.purchaseRecordStatus == PurchaseRecordStatus.TRACKED && order.remainingAmount > 0.005) {
                                TextButton(enabled = !state.isUpdating, onClick = { purchaseConfirmation = PurchaseRecordStatus.HISTORICAL_OWNED }, modifier = Modifier.fillMaxWidth()) {
                                    Text("历史已购，付款资料未补齐")
                                }
                            }
                            TextButton(enabled = !state.isUpdating, onClick = { confirmArchive = true }, modifier = Modifier.fillMaxWidth()) { Text("保留历史并解除当前订单") }
                        }
                    }
                }
                if (order != null && order.purchaseRecordStatus != PurchaseRecordStatus.TRACKED) item {
                    PurchaseRecordNotice(
                        status = order.purchaseRecordStatus,
                        isWorking = state.isUpdating,
                        onConfirmHistorical = if (state.includedInParentPrice) null else {
                            { purchaseConfirmation = PurchaseRecordStatus.HISTORICAL_OWNED }
                        },
                        onConfirmTracked = if (state.includedInParentPrice) null else {
                            { purchaseConfirmation = PurchaseRecordStatus.TRACKED }
                        },
                        onSupplementPayment = if (state.includedInParentPrice || order.purchaseRecordStatus != PurchaseRecordStatus.HISTORICAL_OWNED) null else {
                            { onNavigateToPaymentManage(order.price.id) }
                        }
                    )
                } else if (order == null && !state.includedInParentPrice && state.historicalOrders.isEmpty()) item {
                    LolitaSection("尚未设置价格") { row {
                        SkinEmptyState(iconKey = IconKey.AttachMoney, title = "暂无订单价格", subtitle = "每件独立计价的服饰只有一笔当前订单")
                        Button(onClick = { onNavigateToPriceEdit(null) }, modifier = Modifier.fillMaxWidth()) { Text("设置价格") }
                        if (state.needsPurchaseReview) {
                            Text("没有价格时可先核对服饰状态，价格和真实付款可稍后补全。",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(
                                enabled = !state.isUpdating,
                                onClick = { onNavigateToItemEdit(itemId) },
                                modifier = Modifier.fillMaxWidth().testTag("reviewUnpricedItemEntry")
                            ) { Text("核对服饰状态") }
                        }
                    } }
                } else if (order == null && state.includedInParentPrice) item {
                    LolitaSection("主件价格待录入") { row { Text("请先在主件详情设置价格。无价格不会被当作零元付清。") } }
                }
                if (state.historicalOrders.isNotEmpty()) {
                    item { LolitaSection(if (state.includedInParentPrice) "小物原有价格归档" else "历史订单") { row {
                        Text(if (state.includedInParentPrice) "共用主件价格前的独立账目完整保留，可查看原始记录。" else if (order == null && state.needsPurchaseReview) "存在旧价格，请选择一笔作为当前订单后核对购买资料。" else "以下旧价格完整保留，不计入当前订单。")
                        if (!state.includedInParentPrice && order == null && state.needsPurchaseReview) Text("选择不会相加、合并或删除原有付款。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } } }
                    itemsIndexed(state.historicalOrders, key = { _, value -> value.price.id }) { index, historical ->
                        val paid = historical.payments.filter { it.isPaid }.sumOf { it.amount }
                        LolitaSection("历史订单 #${historical.price.id}", modifier = Modifier.skinItemAppear(index)) {
                            row {
                                PricingValueRow("订单总价", formatPriceAmount(historical.price.totalPrice))
                                PricingValueRow("已录实付", if (historical.payments.none { it.isPaid }) "付款记录未补全" else formatPriceAmount(paid))
                                PricingValueRow("付款记录", "${historical.payments.size} 笔")
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    TextButton(onClick = { onNavigateToPaymentManage(historical.price.id) }) { Text("查看原记录") }
                                    if (!state.includedInParentPrice) OutlinedButton(enabled = !state.isUpdating, onClick = { orderToBind = historical.price }) { Text("设为当前订单") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmArchive) AlertDialog(onDismissRequest = { confirmArchive = false }, title = { Text("归档当前订单") },
        text = { Text("完整保留价格、阶段和实付记录，解除当前订单绑定。此后可将小物切换为共用主件价格，或重新选定历史订单。归档后从当前订单实付中移除，仍计入全部已录实付。") },
        confirmButton = { TextButton(enabled = !state.isUpdating, onClick = { viewModel.archiveCurrentOrder(); confirmArchive = false }) { Text("保留并归档") } },
        dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text("取消") } })
    orderToBind?.let { price -> AlertDialog(onDismissRequest = { orderToBind = null }, title = { Text("确认当前订单") },
        text = { Text("选择总价 ${formatPriceAmount(price.totalPrice)} 的历史订单作为本服饰唯一当前价格。其他价格、阶段和付款记录完整保留。购买状态或付款资料不完整时仍需核对，不会自动补造实付金额或日期。") },
        confirmButton = { TextButton(enabled = !state.isUpdating, onClick = { viewModel.bindHistoricalOrder(price.id); orderToBind = null }) { Text("确认选择") } },
        dismissButton = { TextButton(onClick = { orderToBind = null }) { Text("取消") } }) }
    purchaseConfirmation?.let { status ->
        val historical = status == PurchaseRecordStatus.HISTORICAL_OWNED
        val order = state.order
        var paymentRecordsVerified by remember(status, order) { mutableStateOf(false) }
        AlertDialog(
            modifier = Modifier.testTag("purchaseReviewDialog"),
            onDismissRequest = { purchaseConfirmation = null },
            title = { Text(if (historical) "确认历史已购" else "核对账本并启用跟踪") },
            text = {
                if (historical) Text("此次仅确认这件服饰已拥有，历史付款资料仍待补齐，确认后仍留在待核对购买资料清单。订单价格、所有原始阶段和付款记录保留；不会生成实付金额或付款日期，未付余额仍为未知。")
                else Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                    Text("请先核对真实付款记录是否已全部录入。启用后将按以下余额计算付款状态和提醒，不会自动补录缺失付款。")
                    if (order != null) {
                        PricingValueRow("订单总价", formatPriceAmount(order.price.totalPrice))
                        PricingValueRow("已录实付", formatPriceAmount(order.paidAmount))
                        PricingValueRow("核对后待付余额", formatPriceAmount(order.remainingAmount.coerceAtLeast(0.0)))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().testTag("reviewPaymentRecordsVerified").toggleable(
                            value = paymentRecordsVerified,
                            enabled = !state.isUpdating && order != null,
                            role = Role.Checkbox,
                            onValueChange = { paymentRecordsVerified = it }
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = paymentRecordsVerified, onCheckedChange = null, enabled = !state.isUpdating && order != null)
                        Spacer(Modifier.width(skin.spacingSmall))
                        Text("真实付款记录已全部录入，余额与实际待付一致", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("资料未补齐时请取消，余额继续保持未知。已购服饰可先确认历史已购，再补录能够确认的真实付款。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (order?.payments?.any { it.isPaid && it.paidDate == null } == true) Text(
                        "实付日期尚未补齐，启用跟踪后仍留在待核对购买资料清单。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(modifier = Modifier.testTag(if (historical) "reviewConfirmHistorical" else "reviewConfirmTracked"), enabled = !state.isUpdating && order != null && (historical || paymentRecordsVerified), onClick = {
                if (historical) viewModel.confirmHistoricalPurchase() else viewModel.confirmTrackedOrder(paymentRecordsVerified)
                purchaseConfirmation = null
            }) { Text(if (historical) "确认历史已购" else "确认并启用跟踪") } },
            dismissButton = { TextButton(modifier = Modifier.testTag("reviewCancel"), onClick = { purchaseConfirmation = null }) { Text("取消") } }
        )
    }
    state.error?.let { error -> AlertDialog(onDismissRequest = viewModel::clearError, title = { Text("提示") }, text = { Text(error) },
        confirmButton = { TextButton(onClick = viewModel::clearError) { Text("确定") } }) }
}

@Composable
internal fun PurchaseRecordNotice(
    status: PurchaseRecordStatus,
    isWorking: Boolean = false,
    onConfirmHistorical: (() -> Unit)? = null,
    onConfirmTracked: (() -> Unit)? = null,
    onReturnToReview: (() -> Unit)? = null,
    onSupplementPayment: (() -> Unit)? = null
) {
    if (status == PurchaseRecordStatus.TRACKED) return
    val historical = status == PurchaseRecordStatus.HISTORICAL_OWNED
    val skin = LolitaSkin.current
    var explanationExpanded by rememberSaveable(status) { mutableStateOf(false) }
    LolitaSection(if (historical) "历史已购" else "历史付款资料待核对") {
        row {
            Column(verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                Text(if (historical) "已确认拥有，未付余额未知。原付款阶段只读；可补录或更正真实实付，资料补齐前仍留在核对清单。"
                    else "未付余额未知。价格、阶段和实付暂只读，原记录保留；缺少资料不代表仍欠款。",
                    style = MaterialTheme.typography.bodyMedium)
                if (historical && onSupplementPayment != null) Button(
                    onClick = onSupplementPayment, enabled = !isWorking,
                    modifier = Modifier.fillMaxWidth().testTag("reviewSupplementPaymentEntry")
                ) { Text("补录真实付款") }
                if (!historical && onConfirmHistorical != null) OutlinedButton(
                    onClick = onConfirmHistorical, enabled = !isWorking, modifier = Modifier.fillMaxWidth().testTag("reviewHistoricalEntry")
                ) { Text(if (isWorking) "处理中…" else "确认历史已购") }
                if (onConfirmTracked != null) TextButton(
                    onClick = onConfirmTracked, enabled = !isWorking, modifier = Modifier.fillMaxWidth().testTag("reviewTrackedEntry")
                ) { Text("核对账本并启用跟踪") }
                if (onReturnToReview != null) TextButton(
                    onClick = onReturnToReview, enabled = !isWorking, modifier = Modifier.fillMaxWidth()
                ) { Text("返回核对购买资料") }
                TextButton(onClick = { explanationExpanded = !explanationExpanded }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (explanationExpanded) "收起核对说明" else "查看核对说明")
                    Spacer(Modifier.width(skin.spacingSmall))
                    SkinIcon(if (explanationExpanded) IconKey.ExpandLess else IconKey.ExpandMore)
                }
                if (explanationExpanded) Text(if (historical) "订单价格和原始记录完整保留。付款资料补齐后，可核对账本并启用后续付款跟踪。补录不改变历史已购证据，也不会自动启用跟踪。"
                    else "确认历史已购不会补造付款金额或日期；真实付款记录完整且余额核对一致后，才启用付款跟踪。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun OrderStatusText(status: PurchaseRecordStatus, remainingAmount: Double, awaitingPaymentLabel: String = "付款跟踪中") {
    Text(
        text = when (status) {
            PurchaseRecordStatus.TRACKED -> if (remainingAmount > 0.005) awaitingPaymentLabel else "已付清"
            PurchaseRecordStatus.HISTORICAL_OWNED -> "历史已购，付款资料待核对"
            PurchaseRecordStatus.NEEDS_REVIEW -> "购买资料待核对"
        },
        modifier = Modifier.padding(top = LolitaSkin.current.spacingMedium),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
internal fun OrderSummaryAmount(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(Modifier.fillMaxWidth().padding(vertical = LolitaSkin.current.spacingMedium)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = color)
    }
}

internal fun PriceType.priceLabel(): String = when (this) {
    PriceType.FULL -> "全款"
    PriceType.DEPOSIT_BALANCE -> "定金尾款"
    PriceType.MULTI_STAGE -> "多段付款"
}

class PriceManageViewModelFactory(private val itemId: Long) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = PriceManageViewModel(itemId = itemId) as T
}
