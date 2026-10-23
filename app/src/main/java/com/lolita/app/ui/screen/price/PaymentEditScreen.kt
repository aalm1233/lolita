package com.lolita.app.ui.screen.price

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.PurchaseRecordStatus
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentEditScreen(priceId: Long, paymentId: Long?, onBack: () -> Unit, onSaveSuccess: () -> Unit,
    viewModel: PaymentEditViewModel = viewModel(factory = PaymentEditorFactory), suggestedStageId: Long? = null) {
    val state by viewModel.uiState.collectAsState()
    val skin = LolitaSkin.current
    val canEdit = !state.isLoading && !state.loadFailed && !state.isSaving && !state.isSaved && state.purchaseRecordStatus != PurchaseRecordStatus.NEEDS_REVIEW
    val historical = state.purchaseRecordStatus == PurchaseRecordStatus.HISTORICAL_OWNED
    LaunchedEffect(priceId, paymentId) { viewModel.loadPayment(priceId, paymentId, suggestedStageId) }
    LaunchedEffect(state.isSaved) { if (state.isSaved) onSaveSuccess() }
    val requestBack = UnsavedChangesHandler(viewModel.hasUnsavedChanges, onBack, blocked = state.isSaving)
    val scroll = rememberScrollState()
    ObserveSkinScroll(scroll)
    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
        GradientTopAppBar(title = { Text(if (paymentId != null) "编辑实际付款" else if (historical) "补录真实付款" else "记录实际付款") },
            navigationIcon = { IconButton(onClick = requestBack, enabled = !state.isSaving) { SkinIcon(IconKey.ArrowBack) } })
    }, bottomBar = {
        EditorSaveBar("保存付款记录", state.isSaving, canEdit && viewModel.isValid()) {
            viewModel.viewModelScope.launch {
                if (paymentId == null) viewModel.save(priceId) else viewModel.update(paymentId)
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).verticalScroll(scroll)
            .padding(skin.spacingLarge).testTag("paymentEditor"), verticalArrangement = Arrangement.spacedBy(skin.cardGap)) {
            if (state.isLoading) PricingSkeleton() else if (state.loadFailed) {
                LolitaSection("付款资料暂时无法读取") { row { Text("请返回核对订单后重试，现有价格与付款记录保留。") } }
            } else {
                Text(state.itemName, style = MaterialTheme.typography.titleSmall)
                EditingMoneyNotice()
                if (historical) Text("已确认拥有 · 未付余额未知；补录后保留历史已购状态。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.purchaseRecordStatus == PurchaseRecordStatus.NEEDS_REVIEW)
                    PurchaseRecordNotice(state.purchaseRecordStatus, onReturnToReview = requestBack)
                LolitaSection("实际付款") { row {
                    Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                        PriceMoneyField(state.amount, viewModel::updateAmount, "本次实付金额", canEdit,
                            error = state.amount.takeIf { it.isNotBlank() }?.let { paymentAmountError(state) })
                        PricingValueRow(if (historical) "可补录金额上限" else if (paymentId == null) "当前可付余额" else "此笔可调整金额上限",
                            if (state.purchaseRecordStatus == PurchaseRecordStatus.NEEDS_REVIEW) "未知" else formatPriceAmount(state.remainingAmount.coerceAtLeast(0.0)))
                        if (paymentId == null && state.suggestedAmount > 0.005) TextButton(
                            onClick = viewModel::fillUnrecordedAmount, enabled = canEdit, modifier = Modifier.fillMaxWidth()) {
                            Text(if (historical) "填入参考金额" else "填入${state.stages.firstOrNull { it.id == state.stageId }?.name ?: "剩余"}金额 ${formatPriceAmount(state.suggestedAmount)}")
                        }
                        if (historical) Text("参考金额用于核对，不代表仍欠款。", style = MaterialTheme.typography.bodySmall)
                        if (state.canRecordUnknownDate) Row(Modifier.fillMaxWidth().toggleable(
                            value = state.isPaidDateUnknown, enabled = canEdit, role = Role.Checkbox,
                            onValueChange = viewModel::updatePaidDateUnknown), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(state.isPaidDateUnknown, onCheckedChange = null, enabled = canEdit)
                            Spacer(Modifier.width(skin.spacingSmall)); Text("付款日期记不清")
                        }
                        if (state.canRecordUnknownDate && state.isPaidDateUnknown) {
                            PricingValueRow("实际付款日期", "日期未知")
                            Text("这笔实付计入消费总额，不归入任何年度或月份。", style = MaterialTheme.typography.bodySmall)
                        } else {
                            PricingDateField("实际付款日期", state.paidDate, viewModel::updatePaidDate, canEdit, clearable = false)
                            Text(if (historical) "请选择真实日期；记不清时请明确勾选。" else "默认今天；补记以前的付款请修改日期。",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (state.amount.isNotBlank() && paymentAmountError(state) == null && paymentValidation(state) != null)
                            Text(paymentValidation(state)!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                } }
                if (viewModel.isValid()) LolitaSection("保存后") { row {
                    val amount = parseMoney(state.amount) ?: 0.0
                    val paid = moneySum(state.recordedPaidAmount - state.originalAmount, amount)
                    val remaining = com.lolita.app.domain.usecase.OrderAccounting.remaining(state.orderTotal, paid).coerceAtLeast(0.0)
                    PricingValueRow(if (historical) "累计已录实付" else "累计已付", formatPriceAmount(paid))
                    PricingValueRow("剩余待付", if (historical) "未知" else formatPriceAmount(remaining))
                    Text(if (historical) "下一步：返回订单核对资料是否补齐。" else if (remaining <= 0.005)
                        "订单将付清，物品及共价小物变为已拥有。" else "仍为待付款，可继续分次记录。", style = MaterialTheme.typography.bodySmall)
                } }
                LolitaSection("关联与参考") { row {
                    ExpandableInfo("付款阶段：${state.stages.firstOrNull { it.id == state.stageId }?.name ?: "未指定"}") {
                        FilterChip(selected = state.stageId == null, onClick = { viewModel.updateStageId(null) }, label = { Text("不指定阶段") }, enabled = canEdit)
                        state.stages.forEach { stage ->
                            FilterChip(selected = state.stageId == stage.id, onClick = { viewModel.updateStageId(stage.id) },
                                label = { Text("${stage.name} · ${formatPriceAmount(stage.amount)}") }, enabled = canEdit)
                        }
                        Text("同一阶段可分多次记录；未指定阶段按计划顺序抵扣。", style = MaterialTheme.typography.bodySmall)
                    }
                    ExpandableInfo("购买信息参考") {
                        state.purchaseSource?.let { Text("购买来源：$it") }
                        PricingValueRow("订单总价", formatPriceAmount(state.orderTotal))
                        PricingValueRow("已录实付", formatPriceAmount(state.recordedPaidAmount))
                        state.purchaseDescription?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        Text("订单价格仅供核对，保存只记录本次真实实付。", style = MaterialTheme.typography.bodySmall)
                    }
                } }
                Spacer(Modifier.height(skin.spacingMedium))
            }
        }
    }
    state.error?.let { error -> AlertDialog(onDismissRequest = viewModel::clearError, title = { Text("付款操作未完成") }, text = { Text(error) },
        confirmButton = { TextButton(onClick = viewModel::clearError) { Text("确定") } }) }
}
