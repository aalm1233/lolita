package com.lolita.app.ui.screen.price

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.Payment
import com.lolita.app.data.local.entity.PaymentStage
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.LolitaSection
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
    val saveWithReminderPermissions = rememberStageReminderSave {
        editingStage?.let { stage -> scope.launch {
            savingStage = true
            viewModel.saveStage(stage).onSuccess { editingStage = null }
            savingStage = false
        } }
    }

    Scaffold(topBar = { GradientTopAppBar(title = { Text("付款阶段与实付") },
        navigationIcon = { IconButton(onClick = onBack) { SkinIcon(IconKey.ArrowBack) } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = skin.spacingLarge),
            contentPadding = PaddingValues(vertical = skin.spacingLarge), verticalArrangement = Arrangement.spacedBy(skin.cardGap),
            flingBehavior = rememberSkinFlingBehavior()) {
            if (state.isLoading) item { PricingSkeleton() }
            else if (!state.exists) item { LolitaSection("订单不存在") { row { Text("这笔订单已不存在，请返回。") } } }
            else {
                if (!state.isCurrent) item { LolitaSection("历史订单") { row {
                    Text("原始记录只读保留。要调整账目，请先在价格页将此订单设为当前订单。", style = MaterialTheme.typography.bodyMedium)
                } } }
                item { LolitaSection("订单余额") {
                    row {
                        PricingValueRow("应付总额", formatPriceAmount(state.totalPrice))
                        PricingValueRow("实际已付", formatPriceAmount(state.paidAmount), MaterialTheme.colorScheme.primary)
                        PricingValueRow("未付余额", formatPriceAmount(state.unpaidAmount.coerceAtLeast(0.0)), if (state.unpaidAmount > 0.005) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        if (state.paidAmount > state.totalPrice + 0.005) Text("原始记录存在超付，请核对。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { onNavigateToPaymentEdit(null) }, enabled = state.isCurrent && state.unpaidAmount > 0.005, modifier = Modifier.fillMaxWidth()) {
                            SkinIcon(IconKey.Add); Spacer(Modifier.width(skin.spacingSmall)); Text("记录实际付款")
                        }
                    }
                    row { Text("付款阶段用于安排应付；删除阶段不会减少订单余额。未指定阶段的实付按计划顺序抵扣。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } }
                item { LolitaSection("付款计划", action = {
                    TextButton(enabled = state.isCurrent, onClick = {
                        val planned = state.stages.sumOf { it.amount }
                        editingStage = StageDraft(name = "第${state.stages.size + 1}段", amount = (state.totalPrice - planned).takeIf { it > 0.005 }?.let(::moneyText) ?: "")
                    }) { SkinIcon(IconKey.Add); Text("新增阶段") }
                }) { row {
                    if (state.stages.isEmpty()) Text("尚未安排阶段，可直接记录实付，或新增阶段。")
                    else PricingValueRow("已安排应付", formatPriceAmount(state.stages.sumOf { it.amount }))
                    if (state.stages.sumOf { it.amount } + 0.005 < state.totalPrice) Text("部分金额尚未安排阶段，仍计入未付余额。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.stages.sumOf { it.amount } > state.totalPrice + 0.005) Text("历史计划金额超过订单总额，请核对原始记录；余额以实际订单和实付为准。", color = MaterialTheme.colorScheme.error)
                } } }
                val stagePaidAmounts = com.lolita.app.domain.usecase.StageAccounting.effectivePaid(state.stages, state.payments)
                itemsIndexed(state.stages, key = { _, value -> value.id }) { index, stage ->
                    val paid = stagePaidAmounts[stage.id] ?: 0.0
                    LolitaSection(stage.name, modifier = Modifier.skinItemAppear(index), action = {
                        Row {
                            IconButton(enabled = state.isCurrent, onClick = { editingStage = StageDraft(stage.id, stage.name, moneyText(stage.amount), stage.dueDate, stage.reminderSet, stage.customReminderDays?.toString() ?: "1", stage.calendarEventId) }) { SkinIcon(IconKey.Edit) }
                            IconButton(enabled = state.isCurrent, onClick = { stageToDelete = stage }) { SkinIcon(IconKey.Delete, tint = MaterialTheme.colorScheme.error) }
                        }
                    }) { row {
                        PricingValueRow("阶段应付", formatPriceAmount(stage.amount))
                        PricingValueRow("阶段已付", formatPriceAmount(paid))
                        PricingValueRow("应付日期", formatPriceDate(stage.dueDate))
                        if (state.unpaidAmount > 0.005 && stage.reminderSet && stage.dueDate != null && paid + 0.005 < stage.amount) Text("提前 ${stage.customReminderDays ?: 1} 天提醒", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    } }
                }
                item { LolitaSection("实际付款记录") { row {
                    Text(if (state.payments.isEmpty()) "暂无实付。应付日期不代表已付款。" else "共 ${state.payments.size} 笔；按实际付款日期记账。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } } }
                if (state.legacyUnpaidPayments.isNotEmpty()) item {
                    LolitaSection("保留的旧未付款记录") { row {
                        Text("这些是升级前的原始计划，不计入实付，也不决定当前余额。请核对付款阶段。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        state.legacyUnpaidPayments.forEach { old ->
                            PricingValueRow("旧记录 #${old.id}", formatPriceAmount(old.amount))
                            Text("原应付日期：${formatPriceDate(old.dueDate)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } }
                }
                itemsIndexed(state.payments, key = { _, value -> value.id }) { index, payment ->
                    val stage = state.stages.firstOrNull { it.id == payment.stageId }
                    LolitaSection("实付 ${formatPriceAmount(payment.amount)}", modifier = Modifier.skinItemAppear(index), action = {
                        Row {
                            IconButton(enabled = state.isCurrent, onClick = { onNavigateToPaymentEdit(payment.id) }) { SkinIcon(IconKey.Edit) }
                            IconButton(enabled = state.isCurrent, onClick = { paymentToDelete = payment }) { SkinIcon(IconKey.Delete, tint = MaterialTheme.colorScheme.error) }
                        }
                    }) { row {
                        PricingValueRow("实付日期", payment.paidDate?.let { formatPriceDate(it) } ?: "历史日期缺失，待核对")
                        PricingValueRow("付款阶段", stage?.name ?: "未指定阶段")
                    } }
                }
            }
        }
    }
    editingStage?.let { stage -> AlertDialog(onDismissRequest = { if (!savingStage) editingStage = null },
        title = { Text(if (stage.id == 0L) "新增付款阶段" else "编辑付款阶段") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { StageDraftFields(stage, { editingStage = it }, !savingStage) } },
        confirmButton = { TextButton(enabled = !savingStage && stage.name.isNotBlank() && parseMoney(stage.amount) != null, onClick = {
            saveWithReminderPermissions(stage.reminderSet && stage.dueDate != null)
        }) { Text(if (savingStage) "保存中" else "保存") } },
        dismissButton = { TextButton(enabled = !savingStage, onClick = { editingStage = null }) { Text("取消") } }) }
    stageToDelete?.let { stage -> AlertDialog(onDismissRequest = { stageToDelete = null }, title = { Text("删除付款阶段") },
        text = { Text("删除“${stage.name}”的应付安排与提醒，保留所有实际付款。订单总额和未付余额不会因此减少。") },
        confirmButton = { TextButton(onClick = { viewModel.deleteStage(stage); stageToDelete = null }) { Text("删除阶段", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { stageToDelete = null }) { Text("取消") } }) }
    paymentToDelete?.let { payment -> AlertDialog(onDismissRequest = { paymentToDelete = null }, title = { Text("撤销实际付款") },
        text = { Text("撤销实付 ${formatPriceAmount(payment.amount)} 后会重新计算余额。若不再付清，服饰及共用价格的小物将恢复未收货状态。") },
        confirmButton = { TextButton(onClick = { viewModel.deletePayment(payment); paymentToDelete = null }) { Text("确认撤销", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { paymentToDelete = null }) { Text("取消") } }) }
    state.errorMessage?.let { error -> AlertDialog(onDismissRequest = viewModel::clearError, title = { Text("提示") }, text = { Text(error) },
        confirmButton = { TextButton(onClick = viewModel::clearError) { Text("确定") } }) }
}

class PaymentManageViewModelFactory(private val priceId: Long) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = PaymentManageViewModel(priceId = priceId) as T
}
