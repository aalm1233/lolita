package com.lolita.app.ui.screen.price

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.Payment
import com.lolita.app.data.local.entity.PaymentStage
import com.lolita.app.data.local.entity.PurchaseRecordStatus
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.CardVariant
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.screen.common.LolitaSection
import com.lolita.app.ui.screen.common.SectionHeader
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.skinItemAppear
import com.lolita.app.ui.theme.skin.animation.rememberSkinFlingBehavior
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentManageScreen(priceId: Long, onBack: () -> Unit, onNavigateToPaymentEdit: (Long?) -> Unit,
    viewModel: PaymentManageViewModel = viewModel(factory = PaymentManageViewModelFactory(priceId))) {
    val state by viewModel.uiState.collectAsState()
    val skin = LolitaSkin.current
    val scope = rememberCoroutineScope()
    var editingStage by remember { mutableStateOf<StageDraft?>(null) }
    var stageToDelete by remember { mutableStateOf<PaymentStage?>(null) }
    var paymentToDelete by remember { mutableStateOf<Payment?>(null) }
    var savingStage by remember { mutableStateOf(false) }
    var explanationExpanded by rememberSaveable(priceId, state.purchaseRecordStatus) { mutableStateOf(false) }
    val tracked = state.purchaseRecordStatus == PurchaseRecordStatus.TRACKED
    val canEditStages = state.exists && state.isCurrent && tracked && !state.isLoading && !state.isWorking
    val canEditPayments = state.exists && state.isCurrent && state.purchaseRecordStatus != PurchaseRecordStatus.NEEDS_REVIEW && !state.isLoading && !state.isWorking
    val saveWithReminderPermissions = rememberStageReminderSave {
        if (canEditStages) editingStage?.let { stage -> scope.launch {
            savingStage = true
            viewModel.saveStage(stage).onSuccess { editingStage = null }
            savingStage = false
        } }
    }

    Scaffold(containerColor = Color.Transparent, topBar = { GradientTopAppBar(title = { Text("付款阶段与实付") },
        navigationIcon = { IconButton(onClick = onBack) { SkinIcon(IconKey.ArrowBack) } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = skin.spacingLarge).testTag("paymentManageList"),
            contentPadding = PaddingValues(vertical = skin.spacingLarge),
            flingBehavior = rememberSkinFlingBehavior()) {
            if (state.isLoading) item { PricingSkeleton() }
            else if (!state.exists) item { LolitaSection(if (state.errorMessage == null) "订单不存在" else "订单暂时无法读取") { row {
                Text(if (state.errorMessage == null) "这笔订单已不存在，请返回。" else "请返回后重试，现有价格与付款记录保留。")
            } } }
            else {
                item { LolitaSection(if (tracked) "订单余额" else "已录付款资料") {
                    row {
                        OrderStatusText(state.purchaseRecordStatus, state.unpaidAmount)
                        if (!state.isCurrent) Text("历史订单，原始记录只读", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OrderSummaryAmount(if (tracked) "应付总额" else "订单总价", formatPriceAmount(state.totalPrice))
                        if (tracked) {
                            PricingValueRow("实际已付", formatPriceAmount(state.paidAmount), MaterialTheme.colorScheme.primary)
                            PricingValueRow("未付余额", formatPriceAmount(state.unpaidAmount.coerceAtLeast(0.0)), if (state.unpaidAmount > 0.005) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        } else {
                            PricingValueRow("已录实付", if (state.payments.isEmpty()) "付款记录未补全" else formatPriceAmount(state.paidAmount))
                            PricingValueRow("未付余额", "未知")
                            Text(if (state.purchaseRecordStatus == PurchaseRecordStatus.HISTORICAL_OWNED) "原付款阶段只读，可补录或更正真实实付。" else "付款阶段与实付记录只读，请先核对购买资料。",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (state.paidAmount > state.totalPrice + 0.005) Text("原始记录存在超付，请核对。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { onNavigateToPaymentEdit(null) }, enabled = canEditPayments && state.unpaidAmount > 0.005, modifier = Modifier.fillMaxWidth()) {
                            SkinIcon(IconKey.Add); Spacer(Modifier.width(skin.spacingSmall)); Text(if (state.purchaseRecordStatus == PurchaseRecordStatus.HISTORICAL_OWNED) "补录真实付款" else "记录实际付款")
                        }
                        if (!state.isCurrent) Text("要调整账目，请先在价格页将此订单设为当前订单。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { explanationExpanded = !explanationExpanded }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (explanationExpanded) "收起记账说明" else "查看记账说明")
                            Spacer(Modifier.width(skin.spacingSmall))
                            SkinIcon(if (explanationExpanded) IconKey.ExpandLess else IconKey.ExpandMore)
                        }
                        if (explanationExpanded) Text(if (tracked) "付款阶段用于安排应付；删除阶段不会减少订单余额。未指定阶段的实付按计划顺序抵扣。"
                            else "原付款阶段只读保留，不据此认定欠款或设置提醒。已录实付按原计划顺序展示抵扣，不代表资料已补全。",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } }
                if (!tracked) item {
                    Spacer(Modifier.height(skin.cardGap))
                    PurchaseRecordNotice(state.purchaseRecordStatus, isWorking = state.isWorking, onReturnToReview = onBack)
                }
                item {
                    Spacer(Modifier.height(skin.cardGap))
                    PaymentRecordGroupPart(isFirst = true, isLast = state.stages.isEmpty()) {
                        SectionHeader(if (tracked) "付款计划" else "原付款计划", action = {
                            TextButton(enabled = canEditStages, onClick = {
                                val planned = state.stages.sumOf { it.amount }
                                editingStage = StageDraft(name = "第${state.stages.size + 1}段", amount = (state.totalPrice - planned).takeIf { it > 0.005 }?.let(::moneyText) ?: "")
                            }) { SkinIcon(IconKey.Add); Text("新增阶段") }
                        })
                        if (state.stages.isEmpty()) Text(if (tracked) "尚未安排阶段，可直接记录实付，或新增阶段。" else "未保留付款阶段，资料缺失不代表仍未付款。")
                        else PricingValueRow(if (tracked) "已安排应付" else "原阶段金额", formatPriceAmount(state.stages.sumOf { it.amount }))
                        if (tracked && state.stages.sumOf { it.amount } + 0.005 < state.totalPrice) Text("部分金额尚未安排阶段，仍计入未付余额。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.stages.sumOf { it.amount } > state.totalPrice + 0.005) Text("历史计划金额超过订单总额，请核对原始记录。", color = MaterialTheme.colorScheme.error)
                    }
                }
                val stagePaidAmounts = com.lolita.app.domain.usecase.StageAccounting.effectivePaid(state.stages, state.payments)
                // Stage and payment IDs come from different tables and can have the same value.
                itemsIndexed(state.stages, key = { _, value -> "stage-${value.id}" }) { index, stage ->
                    val paid = stagePaidAmounts[stage.id] ?: 0.0
                    PaymentRecordGroupPart(isLast = index == state.stages.lastIndex,
                        modifier = Modifier.skinItemAppear(index).testTag("paymentStage-${stage.id}")) {
                        PaymentRecordHeading(stage.name, formatPriceAmount(stage.amount))
                        Text("应付日期：${formatPriceDate(stage.dueDate)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${if (tracked) "阶段已付" else "已录抵扣"}：${if (!tracked && state.payments.isEmpty()) "付款记录未补全" else formatPriceAmount(paid)}", style = MaterialTheme.typography.bodySmall)
                                if (!state.isCurrent || !tracked) Text("付款阶段只读", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(enabled = canEditStages, modifier = Modifier.semantics { contentDescription = "编辑付款阶段：${stage.name}" }, onClick = { editingStage = StageDraft(stage.id, stage.name, moneyText(stage.amount), stage.dueDate, stage.reminderSet, stage.customReminderDays?.toString() ?: "1", stage.calendarEventId) }) { SkinIcon(IconKey.Edit) }
                            IconButton(enabled = canEditStages, modifier = Modifier.semantics { contentDescription = "删除付款阶段：${stage.name}" }, onClick = { stageToDelete = stage }) { SkinIcon(IconKey.Delete, tint = MaterialTheme.colorScheme.error) }
                        }
                        if (tracked && state.isCurrent && state.unpaidAmount > 0.005 && stage.reminderSet && stage.dueDate != null && paid + 0.005 < stage.amount) Text("提前 ${stage.customReminderDays ?: 1} 天提醒", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                item {
                    Spacer(Modifier.height(skin.cardGap))
                    PaymentRecordGroupPart(isFirst = true, isLast = state.payments.isEmpty()) {
                        SectionHeader("实际付款记录")
                        Text(if (state.payments.isEmpty()) {
                            if (tracked) "暂无实付。应付日期不代表已付款。" else "付款记录未补全，不能据此认定实付为零或仍欠全款。"
                        } else "共 ${state.payments.size} 笔已录实付；按实际付款日期记账。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                itemsIndexed(state.payments, key = { _, value -> "payment-${value.id}" }) { index, payment ->
                    val stage = state.stages.firstOrNull { it.id == payment.stageId }
                    PaymentRecordGroupPart(isLast = index == state.payments.lastIndex,
                        modifier = Modifier.skinItemAppear(index).testTag("actualPayment-${payment.id}")) {
                        PaymentRecordHeading("实付", formatPriceAmount(payment.amount))
                        Text("实付日期：${payment.paidDate?.let { formatPriceDate(it) } ?: "历史日期缺失，待核对"}", style = MaterialTheme.typography.bodySmall,
                            color = if (payment.paidDate == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("付款阶段：${stage?.name ?: "未指定阶段"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (!state.isCurrent || state.purchaseRecordStatus == PurchaseRecordStatus.NEEDS_REVIEW) Text("实付记录只读", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(enabled = canEditPayments, modifier = Modifier.semantics { contentDescription = "编辑实际付款：${formatPriceAmount(payment.amount)}" }, onClick = { onNavigateToPaymentEdit(payment.id) }) { SkinIcon(IconKey.Edit) }
                            IconButton(enabled = canEditPayments, modifier = Modifier.semantics { contentDescription = "撤销实际付款：${formatPriceAmount(payment.amount)}" }, onClick = { paymentToDelete = payment }) { SkinIcon(IconKey.Delete, tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                if (state.legacyUnpaidPayments.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(skin.cardGap))
                        PaymentRecordGroupPart(isFirst = true, isLast = false) {
                            SectionHeader("保留的旧未付款记录")
                            Text("这些是升级前的原始计划，不计入实付，也不决定当前余额。请核对付款阶段。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    itemsIndexed(state.legacyUnpaidPayments, key = { _, value -> "legacy-payment-${value.id}" }) { index, old ->
                        PaymentRecordGroupPart(isLast = index == state.legacyUnpaidPayments.lastIndex) {
                            PaymentRecordHeading("旧记录 #${old.id}", formatPriceAmount(old.amount))
                            Text("原应付日期：${formatPriceDate(old.dueDate)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("原始计划只读", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    editingStage?.let { stage -> AlertDialog(onDismissRequest = { if (!savingStage) editingStage = null },
        title = { Text(if (stage.id == 0L) "新增付款阶段" else "编辑付款阶段") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { StageDraftFields(stage, { editingStage = it }, canEditStages && !savingStage) } },
        confirmButton = { TextButton(enabled = canEditStages && !savingStage && stage.name.isNotBlank() && parseMoney(stage.amount) != null, onClick = {
            saveWithReminderPermissions(stage.reminderSet && stage.dueDate != null)
        }) { Text(if (savingStage) "保存中" else "保存") } },
        dismissButton = { TextButton(enabled = !savingStage, onClick = { editingStage = null }) { Text("取消") } }) }
    stageToDelete?.let { stage -> AlertDialog(onDismissRequest = { stageToDelete = null }, title = { Text("删除付款阶段") },
        text = { Text("删除“${stage.name}”的应付安排与提醒，保留所有实际付款。订单总额和未付余额不会因此减少。") },
        confirmButton = { TextButton(enabled = canEditStages, onClick = { viewModel.deleteStage(stage); stageToDelete = null }) { Text("删除阶段", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { stageToDelete = null }) { Text("取消") } }) }
    paymentToDelete?.let { payment -> AlertDialog(onDismissRequest = { paymentToDelete = null }, title = { Text("撤销实际付款") },
        text = { Text(if (state.purchaseRecordStatus == PurchaseRecordStatus.HISTORICAL_OWNED)
            "撤销已录实付 ${formatPriceAmount(payment.amount)}，保留历史已购状态和其余原记录。未付余额仍为未知，请只撤销确认有误的记录。"
            else "撤销实付 ${formatPriceAmount(payment.amount)} 后会重新计算余额。若不再付清，服饰及共用价格的小物将恢复待付款状态。") },
        confirmButton = { TextButton(enabled = canEditPayments, onClick = { viewModel.deletePayment(payment); paymentToDelete = null }) { Text("确认撤销", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { paymentToDelete = null }) { Text("取消") } }) }
    state.errorMessage?.let { error -> AlertDialog(onDismissRequest = viewModel::clearError, title = { Text("提示") }, text = { Text(error) },
        confirmButton = { TextButton(onClick = viewModel::clearError) { Text("确定") } }) }
}

/** Keep every record as a lazy item while drawing it as one part of a compact group. */
@Composable
private fun PaymentRecordGroupPart(
    isFirst: Boolean = false,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val skin = LolitaSkin.current
    val isDark = isSystemInDarkTheme()
    val compactBorder = if (isDark) skin.compactCardBorderStrokeDark ?: skin.compactCardBorderStroke else skin.compactCardBorderStroke
    val hasVisibleBorder = compactBorder != null && compactBorder.width > 0.dp
    LolitaCard(
        modifier = modifier.fillMaxWidth(),
        variant = CardVariant.COMPACT,
        shape = RoundedCornerShape(
            topStart = if (isFirst) skin.cornerRadiusMedium else 0.dp,
            topEnd = if (isFirst) skin.cornerRadiusMedium else 0.dp,
            bottomStart = if (isLast) skin.cornerRadiusMedium else 0.dp,
            bottomEnd = if (isLast) skin.cornerRadiusMedium else 0.dp
        ),
        contentPadding = PaddingValues(horizontal = skin.cardInnerPadding, vertical = skin.spacingMedium)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
            if (!isFirst && !hasVisibleBorder) HorizontalDivider(
                color = if (isDark) skin.sectionDividerColorDark else skin.sectionDividerColor,
                thickness = skin.sectionDividerHeight
            )
            content()
        }
    }
}

@Composable
private fun PaymentRecordHeading(label: String, amount: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(amount, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

class PaymentManageViewModelFactory(private val priceId: Long) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = PaymentManageViewModel(priceId = priceId) as T
}
