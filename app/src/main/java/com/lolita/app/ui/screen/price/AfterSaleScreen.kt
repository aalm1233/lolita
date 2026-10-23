package com.lolita.app.ui.screen.price

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.*
import com.lolita.app.domain.usecase.OrderAccounting
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.animation.rememberSkinFlingBehavior
import com.lolita.app.ui.theme.skin.icon.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AfterSaleEditorScreen(mode: AfterSaleEditorMode, id: Long, onBack: () -> Unit, onSaved: () -> Unit,
    viewModel: AfterSaleEditorViewModel = viewModel(factory = AfterSaleEditorFactory)) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(mode, id) { viewModel.load(mode, id) }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    AfterSaleEditorBody(state, onBack, viewModel::amount, viewModel::note, viewModel::date, viewModel::kind,
        viewModel::confirm, viewModel::save, viewModel::removeReference, viewModel::unknown)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AfterSaleEditorBody(state: AfterSaleEditorState, onBack: () -> Unit, onAmount: (String) -> Unit, onNote: (String) -> Unit,
    onDate: (Long?) -> Unit, onKind: (RefundKind) -> Unit, onConfirm: (Boolean) -> Unit, onSave: () -> Unit, onRemove: () -> Unit, onUnknown: (Boolean) -> Unit = {}) {
    val skin = LolitaSkin.current
    val scroll = rememberScrollState(); ObserveSkinScroll(scroll)
    val back = UnsavedChangesHandler(state.dirty, onBack, blocked = state.saving)
    var remove by remember { mutableStateOf(false) }
    val editable = !state.loading && !state.failed && !state.saving && !state.saved
    val title = when (state.mode) {
        AfterSaleEditorMode.REFERENCE -> "仅记参考价"
        AfterSaleEditorMode.REFUND -> "记录退款到账"
        AfterSaleEditorMode.CANCEL -> if (state.snapshot?.cancellation == null) "取消购买订单" else "调整约定退款"
    }
    val saveLabel = when (state.mode) {
        AfterSaleEditorMode.REFERENCE -> "保存参考价"
        AfterSaleEditorMode.REFUND -> "保存退款到账"
        AfterSaleEditorMode.CANCEL -> if (state.snapshot?.cancellation == null) "确认取消订单" else "保存约定退款"
    }
    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
        GradientTopAppBar(title = { Text(title) }, navigationIcon = {
            IconButton(onClick = back, enabled = !state.saving, modifier = Modifier.testTag("afterSaleBack")) { SkinIcon(IconKey.ArrowBack) }
        })
    }, bottomBar = { EditorSaveBar(saveLabel, state.saving, state.valid, onSave) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).verticalScroll(scroll)
            .padding(skin.spacingLarge).testTag("afterSaleEditor"), verticalArrangement = Arrangement.spacedBy(skin.cardGap)) {
            if (state.loading) PricingSkeleton() else {
                Text(state.itemName, style = MaterialTheme.typography.titleSmall)
                EditingMoneyNotice()
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("afterSaleError")) }
                if (!state.failed) {
                    val data = state.snapshot
                    if (state.mode == AfterSaleEditorMode.REFERENCE) {
                        LolitaSection("参考价格") { row { Text("只记录参考金额，保留当前拥有状态，不建立订单、不计入待付或付款提醒。") } }
                    } else if (state.mode == AfterSaleEditorMode.REFUND && data != null) {
                        LolitaSection("真实资金退回") { row {
                            OrderSummaryAmount("可记录上限", formatPriceAmount(data.net))
                            Text("仅记录已到账金额，不向商家发起退款。原付款和应付计划保留；如停止购买，请使用取消订单。", style = MaterialTheme.typography.bodySmall)
                        } }
                    } else if (data != null) {
                        LolitaSection(if (data.cancellation == null) "停止这次购买" else "核对约定退款") { row {
                            if (data.cancellation == null) {
                                Text("请先确认双方不再付款。取消后停止剩余待付和提醒，物品及共价小物回到心愿；原照片、位置引用、订单和实付保留。")
                                PricingValueRow("停止跟踪的待付", formatPriceAmount(OrderAccounting.remaining(data.price.totalPrice, data.paid)))
                            } else Text("只修改约定退款，不改变物品状态或生成到账记录。")
                            PricingValueRow("原实付", formatPriceAmount(data.paid))
                            PricingValueRow("已退回", formatPriceAmount(data.refunded))
                        } }
                    }
                    LolitaSection(if (state.mode == AfterSaleEditorMode.REFERENCE) "金额与来源" else "本次记录") { row {
                        if (state.mode == AfterSaleEditorMode.REFUND) {
                            RefundKind.entries.forEach { kind -> FilterChip(selected = state.kind == kind, onClick = { onKind(kind) },
                                enabled = editable, label = { Text(if (kind == RefundKind.REFUND) "退款" else "退差价") }) }
                        }
                        if (state.mode == AfterSaleEditorMode.CANCEL) Row(Modifier.fillMaxWidth().toggleable(state.expectedUnknown, enabled = editable, role = Role.Checkbox, onValueChange = onUnknown), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(state.expectedUnknown, null, enabled = editable); Text("约定退款金额待确认", modifier = Modifier.weight(1f))
                        }
                        if (!state.expectedUnknown || state.mode != AfterSaleEditorMode.CANCEL) PriceMoneyField(state.amount, onAmount, when (state.mode) {
                            AfterSaleEditorMode.REFERENCE -> "参考金额（含已知邮费）"
                            AfterSaleEditorMode.REFUND -> "实际到账金额"
                            AfterSaleEditorMode.CANCEL -> "约定退款总额（含已到账）"
                        }, editable, error = state.amountError.takeIf { state.amount.isNotBlank() })
                        if (state.mode == AfterSaleEditorMode.REFUND) {
                            PricingDateField("实际到账日期", state.date, onDate, editable, clearable = false)
                            if (state.date > System.currentTimeMillis()) Text("到账日期不能在未来", color = MaterialTheme.colorScheme.error)
                        }
                        if (state.mode == AfterSaleEditorMode.CANCEL) Text("若不退定金，请填已到账总额（未退过可填0）；未退回部分保留为净支出。约定不表示已经到账。", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(state.note, onNote, label = { Text(if (state.mode == AfterSaleEditorMode.REFERENCE) "来源与备注（可选）" else "备注（可选）") },
                            enabled = editable, modifier = Modifier.fillMaxWidth().testTag("afterSaleNote"), minLines = 2,
                            supportingText = { Text("${state.note.length}/500") })
                        if (state.mode == AfterSaleEditorMode.CANCEL) Row(Modifier.fillMaxWidth().toggleable(state.confirmed,
                            enabled = editable, role = Role.Checkbox, onValueChange = onConfirm), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(state.confirmed, null, enabled = editable)
                            Text(if (data?.cancellation == null) "已确认取消整笔订单并让物品回到心愿" else "已核对约定退款总额", modifier = Modifier.weight(1f))
                        }
                    } }
                    val amount = if (state.expectedUnknown && state.mode == AfterSaleEditorMode.CANCEL) null else parseMoney(state.amount, state.mode != AfterSaleEditorMode.REFUND)
                    if (amount != null && state.amountError == null && data != null) LolitaSection("保存后") { row {
                        if (state.mode == AfterSaleEditorMode.REFUND) {
                            PricingValueRow("累计已退回", formatPriceAmount(OrderAccounting.sum(listOf(data.refunded, amount))))
                            PricingValueRow("净支出", formatPriceAmount((OrderAccounting.cents(data.net) - OrderAccounting.cents(amount)) / 100.0))
                        } else if (state.mode == AfterSaleEditorMode.CANCEL) PricingValueRow("待退款", formatPriceAmount(OrderAccounting.remaining(amount, data.refunded)))
                    } }
                    if (state.mode == AfterSaleEditorMode.CANCEL && state.expectedUnknown) Text("取消后待退款金额保持未知；真实到账仍可逐笔记录。", style = MaterialTheme.typography.bodySmall)
                    if (state.mode == AfterSaleEditorMode.REFERENCE && state.reference != null) TextButton(onClick = { remove = true }, enabled = editable, modifier = Modifier.fillMaxWidth()) { Text("移除参考价") }
                    Spacer(Modifier.height(skin.spacingMedium))
                }
            }
        }
    }
    if (remove) AlertDialog(onDismissRequest = { remove = false }, title = { Text("移除参考价") }, text = { Text("仅移除这项参考金额，现有订单、实付和拥有状态保留。") },
        confirmButton = { TextButton(onClick = { remove = false; onRemove() }, enabled = !state.saving) { Text("确认移除") } },
        dismissButton = { TextButton(onClick = { remove = false }) { Text("保留") } })
}

@Composable
fun AfterSaleScreen(priceId: Long, onBack: () -> Unit, onEdit: (AfterSaleEditorMode) -> Unit, onLedger: () -> Unit,
    viewModel: AfterSaleViewModel = viewModel(factory = AfterSaleFactory(priceId))) {
    val state by viewModel.state.collectAsState()
    AfterSaleBody(state, onBack, onEdit, onLedger, viewModel::voidRefund)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AfterSaleBody(state: AfterSaleUiState, onBack: () -> Unit, onEdit: (AfterSaleEditorMode) -> Unit, onLedger: () -> Unit,
    onVoid: (Long, String) -> Unit) {
    val skin = LolitaSkin.current
    val money = pricingMoneyVisible()
    val scroll = rememberLazyListState(); ObserveSkinScroll(scroll)
    var pendingVoid by remember { mutableStateOf<Pair<Long, String>?>(null) }
    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
        GradientTopAppBar(title = { Text("退款与取消") }, navigationIcon = { IconButton(onClick = onBack, enabled = !state.working) { SkinIcon(IconKey.ArrowBack) } })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).testTag("afterSaleList"), state = scroll,
            contentPadding = PaddingValues(skin.spacingLarge), verticalArrangement = Arrangement.spacedBy(skin.cardGap), flingBehavior = rememberSkinFlingBehavior()) {
            if (state.loading) item { PricingSkeleton() }
            state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            val data = state.snapshot
            if (!state.loading && data == null) item { Text("订单资料暂不可用，请返回核对。") }
            if (data != null) {
                item { Text(data.item.name, style = MaterialTheme.typography.titleSmall); if (!money) PrivateMoneyActionNotice() }
                item { LolitaSection(if (data.cancellation != null) "已取消订单" else "订单资金") { row {
                    if (data.cancellation != null) Text("已停止待付跟踪。取消时物品与共价小物移回心愿，之后的物品状态以最新记录为准。", style = MaterialTheme.typography.bodySmall)
                    if (money) {
                        if (data.cancellation != null) OrderSummaryAmount("待退款", data.pendingRefund?.let(::formatPriceAmount) ?: "金额待确认")
                        PricingValueRow("原实付", formatPriceAmount(data.paid))
                        PricingValueRow("已退回", formatPriceAmount(data.refunded))
                        PricingValueRow("净支出", formatPriceAmount(data.net))
                    }
                    if (data.net > 0) Button(onClick = { onEdit(AfterSaleEditorMode.REFUND) }, enabled = !state.working, modifier = Modifier.fillMaxWidth()) { Text("记录退款到账") }
                    TextButton(onClick = onLedger, modifier = Modifier.fillMaxWidth()) { Text("查看原付款记录") }
                } } }
                if (data.refunds.isEmpty()) item { Text("尚无退款到账记录；约定退款请在真实到账后登记。", style = MaterialTheme.typography.bodyMedium) }
                items(data.refunds, key = { it.id }) { refund ->
                    LolitaSection(if (refund.kind == RefundKind.PRICE_DIFFERENCE) "退差价到账" else "退款到账") { row {
                        if (refund.voidedAt != null) Text("已撤销误记 · ${formatPriceDate(refund.voidedAt)}")
                        if (money) PricingValueRow("到账金额", formatPriceAmount(refund.amount))
                        Text("实际到账：${formatPriceDate(refund.receivedAt)}", style = MaterialTheme.typography.bodySmall)
                        // Notes may themselves contain amounts; hide them with financial privacy.
                        if (money && refund.note.isNotBlank()) Text(refund.note)
                        if (refund.voidedAt == null) TextButton(enabled = !state.working, onClick = { pendingVoid = refund.id to data.fingerprint }) { Text("更正误记（撤销此条）") }
                    } }
                }
                if (data.cancellation != null || (data.isCurrent && data.binding?.purchaseRecordStatus == PurchaseRecordStatus.TRACKED)) item {
                    LolitaSection(if (data.cancellation != null) "取消记录" else "停止购买") { row {
                        if (data.cancellation != null) {
                            Text("取消日期：${formatPriceDate(data.cancellation.cancelledAt)}")
                            if (money) PricingValueRow("约定退款总额", data.cancellation.expectedRefundTotal?.let(::formatPriceAmount) ?: "金额待确认")
                            if (money && data.cancellation.note.isNotBlank()) Text(data.cancellation.note)
                        }
                        TextButton(onClick = { onEdit(AfterSaleEditorMode.CANCEL) }, enabled = !state.working, modifier = Modifier.fillMaxWidth()) {
                            Text(if (data.cancellation != null) "调整约定退款总额" else "取消购买订单")
                        }
                    } }
                }
            }
        }
    }
    pendingVoid?.let { pending -> AlertDialog(onDismissRequest = { if (!state.working) pendingVoid = null }, title = { Text("撤销退款误记") },
        text = { Text("仅用于这笔到账记错的情况。原记录保留并标记已撤销，净支出和待退款会重新计算；这不表示向商家付款。") },
        confirmButton = { TextButton(enabled = !state.working, onClick = { onVoid(pending.first, pending.second); pendingVoid = null }) { Text("确认纠正") } },
        dismissButton = { TextButton(onClick = { pendingVoid = null }) { Text("保留记录") } }) }
}
