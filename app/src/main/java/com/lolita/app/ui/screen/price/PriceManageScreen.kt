package com.lolita.app.ui.screen.price

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    viewModel: PriceManageViewModel = viewModel(factory = PriceManageViewModelFactory(itemId))
) {
    val state by viewModel.uiState.collectAsState()
    val skin = LolitaSkin.current
    var orderToBind by remember { mutableStateOf<Price?>(null) }
    var confirmArchive by remember { mutableStateOf(false) }
    var purchaseConfirmation by remember { mutableStateOf<PurchaseRecordStatus?>(null) }
    Scaffold(topBar = { GradientTopAppBar(title = { Text("价格与付款") },
        navigationIcon = { IconButton(onClick = onBack) { SkinIcon(IconKey.ArrowBack) } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = skin.spacingLarge),
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
                    } }
                }
                val order = state.order
                if (order != null && order.purchaseRecordStatus != PurchaseRecordStatus.TRACKED) item {
                    PurchaseRecordNotice(
                        status = order.purchaseRecordStatus,
                        isWorking = state.isUpdating,
                        onConfirmHistorical = if (state.includedInParentPrice) null else {
                            { purchaseConfirmation = PurchaseRecordStatus.HISTORICAL_OWNED }
                        },
                        onConfirmTracked = if (state.includedInParentPrice) null else {
                            { purchaseConfirmation = PurchaseRecordStatus.TRACKED }
                        }
                    )
                }
                if (order != null) item {
                    LolitaSection(if (state.includedInParentPrice) "主件订单" else "当前订单", action = if (state.includedInParentPrice) null else {
                        { IconButton(enabled = !state.isUpdating && order.purchaseRecordStatus == PurchaseRecordStatus.TRACKED,
                            onClick = { onNavigateToPriceEdit(order.price.id) }) { SkinIcon(IconKey.Edit) } }
                    }) {
                        row {
                            PricingValueRow("付款形式", order.price.type.priceLabel())
                            PricingValueRow("商品金额", formatPriceAmount(order.price.totalPrice - order.price.shipping))
                            PricingValueRow("邮费", formatPriceAmount(order.price.shipping))
                            PricingValueRow(if (order.purchaseRecordStatus == PurchaseRecordStatus.TRACKED) "应付总额" else "订单总价", formatPriceAmount(order.price.totalPrice))
                        }
                        row {
                            if (order.purchaseRecordStatus == PurchaseRecordStatus.TRACKED) {
                                PricingValueRow("实际已付", formatPriceAmount(order.paidAmount), MaterialTheme.colorScheme.primary)
                                PricingValueRow("未付余额", formatPriceAmount(order.remainingAmount.coerceAtLeast(0.0)), if (order.remainingAmount > 0.005) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                            } else {
                                PricingValueRow("已录实付", if (order.payments.isEmpty()) "付款记录未补全" else formatPriceAmount(order.paidAmount))
                                PricingValueRow("未付余额", "未知")
                            }
                            if (order.paidAmount > order.price.totalPrice + 0.005) Text("历史实付超过订单总额，请核对原始付款。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            Button(enabled = !state.isUpdating, onClick = { onNavigateToPaymentManage(order.price.id) }, modifier = Modifier.fillMaxWidth()) { Text(if (state.includedInParentPrice) "查看主件付款" else "付款阶段与实付记录") }
                            if (!state.includedInParentPrice && order.purchaseRecordStatus == PurchaseRecordStatus.TRACKED && order.payments.isEmpty()) {
                                TextButton(enabled = !state.isUpdating, onClick = { purchaseConfirmation = PurchaseRecordStatus.HISTORICAL_OWNED }, modifier = Modifier.fillMaxWidth()) {
                                    Text("历史已购，付款资料缺失")
                                }
                            }
                            if (!state.includedInParentPrice) TextButton(enabled = !state.isUpdating, onClick = { confirmArchive = true }, modifier = Modifier.fillMaxWidth()) { Text("保留历史并解除当前订单") }
                        }
                    }
                } else if (!state.includedInParentPrice && state.historicalOrders.isEmpty()) item {
                    LolitaSection("尚未设置价格") { row {
                        SkinEmptyState(iconKey = IconKey.AttachMoney, title = "暂无订单价格", subtitle = "每件独立计价的服饰只有一笔当前订单")
                        Button(onClick = { onNavigateToPriceEdit(null) }, modifier = Modifier.fillMaxWidth()) { Text("设置价格") }
                    } }
                } else if (state.includedInParentPrice) item {
                    LolitaSection("主件价格待录入") { row { Text("请先在主件详情设置价格。无价格不会被当作零元付清。") } }
                }
                if (state.historicalOrders.isNotEmpty()) {
                    item { LolitaSection(if (state.includedInParentPrice) "小物原有价格归档" else "历史价格待核对") { row {
                        Text(if (state.includedInParentPrice) "共用主件价格前的独立账目完整保留，可查看原始记录。" else if (order == null) "存在旧价格，请选择一笔作为当前订单。" else "以下旧价格完整保留，不计入当前订单。")
                        Text("选择不会相加、合并或删除原有付款。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        text = { Text("完整保留价格、阶段和实付记录，解除当前订单绑定。此后可将小物切换为共用主件价格，或重新选定历史订单。归档的订单不再计入当前消费统计。") },
        confirmButton = { TextButton(enabled = !state.isUpdating, onClick = { viewModel.archiveCurrentOrder(); confirmArchive = false }) { Text("保留并归档") } },
        dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text("取消") } })
    orderToBind?.let { price -> AlertDialog(onDismissRequest = { orderToBind = null }, title = { Text("确认当前订单") },
        text = { Text("选择总价 ${formatPriceAmount(price.totalPrice)} 的历史订单作为本服饰唯一当前价格。其他价格、阶段和付款记录完整保留。购买状态或付款资料不完整时仍需核对，不会自动补造实付金额或日期。") },
        confirmButton = { TextButton(enabled = !state.isUpdating, onClick = { viewModel.bindHistoricalOrder(price.id); orderToBind = null }) { Text("确认选择") } },
        dismissButton = { TextButton(onClick = { orderToBind = null }) { Text("取消") } }) }
    purchaseConfirmation?.let { status ->
        val historical = status == PurchaseRecordStatus.HISTORICAL_OWNED
        AlertDialog(
            onDismissRequest = { purchaseConfirmation = null },
            title = { Text(if (historical) "确认历史已购" else "确认仍待付款") },
            text = { Text(if (historical)
                "确认这件服饰已购入，但历史付款资料没有补全。订单价格、所有原始阶段和付款记录保留；不会生成实付金额或付款日期，未付余额仍为未知。"
                else "确认这笔订单确实仍需付款后，系统会按订单总价减去已录实付显示未付余额，并恢复状态和提醒计算。原价格和付款记录保留，不会生成实付金额或日期。若已购完成而只是缺付款资料，请取消。") },
            confirmButton = { TextButton(enabled = !state.isUpdating, onClick = {
                if (historical) viewModel.confirmHistoricalPurchase() else viewModel.confirmTrackedOrder()
                purchaseConfirmation = null
            }) { Text(if (historical) "确认历史已购" else "确认仍待付款") } },
            dismissButton = { TextButton(onClick = { purchaseConfirmation = null }) { Text("取消") } }
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
    onReturnToReview: (() -> Unit)? = null
) {
    if (status == PurchaseRecordStatus.TRACKED) return
    val historical = status == PurchaseRecordStatus.HISTORICAL_OWNED
    LolitaSection(if (historical) "历史已购" else "历史付款资料待核对") {
        row {
            Column(verticalArrangement = Arrangement.spacedBy(LolitaSkin.current.spacingMedium)) {
                Text(if (historical) "付款记录未补全。订单价格和原始记录保留，未付余额为未知。"
                    else "原价格和记录保留。核对前暂不修改付款阶段或实付记录，也不根据缺失资料认定欠款。",
                    style = MaterialTheme.typography.bodyMedium)
                Text(if (historical) "历史付款阶段只读，可补录或更正能够确认的真实实付。需要跟踪后续付款时，请先确认仍待付款。"
                    else "确认历史已购不会补造付款金额或日期；确认仍待付款后，才按正常订单跟踪余额和提醒。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!historical && onConfirmHistorical != null) Button(
                    onClick = onConfirmHistorical, enabled = !isWorking, modifier = Modifier.fillMaxWidth()
                ) { Text(if (isWorking) "处理中…" else "确认历史已购") }
                if (onConfirmTracked != null) OutlinedButton(
                    onClick = onConfirmTracked, enabled = !isWorking, modifier = Modifier.fillMaxWidth()
                ) { Text("确认仍待付款") }
                if (onReturnToReview != null) TextButton(
                    onClick = onReturnToReview, enabled = !isWorking, modifier = Modifier.fillMaxWidth()
                ) { Text("返回核对购买状态") }
            }
        }
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
