package com.lolita.app.ui.screen.price

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.PurchaseRecordStatus
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.LolitaSection
import com.lolita.app.ui.screen.common.UnsavedChangesHandler
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentEditScreen(priceId: Long, paymentId: Long?, onBack: () -> Unit, onSaveSuccess: () -> Unit, viewModel: PaymentEditViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val skin = LolitaSkin.current
    val canEdit = !state.isLoading && !state.loadFailed && !state.isSaving && state.purchaseRecordStatus != PurchaseRecordStatus.NEEDS_REVIEW
    val historical = state.purchaseRecordStatus == PurchaseRecordStatus.HISTORICAL_OWNED
    val canSave = canEdit && viewModel.isValid()
    val savePayment: () -> Unit = {
        scope.launch {
            if (paymentId == null) viewModel.save(priceId).onSuccess { onSaveSuccess() }
            else viewModel.update(paymentId).onSuccess { onSaveSuccess() }
        }
    }
    LaunchedEffect(priceId, paymentId) { viewModel.loadPayment(priceId, paymentId) }
    val requestBack = UnsavedChangesHandler(viewModel.hasUnsavedChanges, onBack)
    Scaffold(topBar = { GradientTopAppBar(title = { Text(if (paymentId != null) "编辑实际付款" else if (historical) "补录真实付款" else "记录实际付款") },
        navigationIcon = { IconButton(onClick = requestBack) { SkinIcon(IconKey.ArrowBack) } }, actions = {
            IconButton(enabled = canSave, onClick = savePayment) { if (state.isSaving) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) else SkinIcon(IconKey.Save) }
        }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(skin.spacingLarge),
            verticalArrangement = Arrangement.spacedBy(skin.cardGap)) {
            if (state.isLoading) PricingSkeleton() else if (state.loadFailed) {
                LolitaSection("付款资料暂时无法读取") { row { Text("请返回后重试，现有价格与付款记录保留。") } }
            } else {
                if (state.purchaseRecordStatus != PurchaseRecordStatus.TRACKED) PurchaseRecordNotice(state.purchaseRecordStatus, isWorking = state.isSaving, onReturnToReview = requestBack)
                LolitaSection("购买信息参考") {
                    row { Column(verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                        Text("服饰", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(state.itemName, style = MaterialTheme.typography.bodyLarge)
                    } }
                    state.purchaseSource?.takeIf { it.isNotBlank() }?.let { source ->
                        row { Column(verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                            Text("购买来源", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(source, style = MaterialTheme.typography.bodyMedium)
                        } }
                    }
                    row {
                        PricingValueRow("订单总价", formatPriceAmount(state.orderTotal))
                        PricingValueRow("已录实付", formatPriceAmount(state.recordedPaidAmount))
                    }
                    state.purchaseDescription?.takeIf { it.isNotBlank() }?.let { description ->
                        row { Column(verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                            Text("原订单信息", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(description, style = MaterialTheme.typography.bodyMedium)
                        } }
                    }
                    row { Text("订单价格仅供核对，请确认真实实付金额后保存付款记录。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                LolitaSection("实际付款") { row {
                    Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                        if (state.purchaseRecordStatus == PurchaseRecordStatus.NEEDS_REVIEW) PricingValueRow("未付余额", "未知")
                        else PricingValueRow(if (historical) "可补录金额上限" else if (paymentId == null) "当前可付余额" else "此笔可调整金额上限", formatPriceAmount(state.remainingAmount.coerceAtLeast(0.0)))
                        PriceMoneyField(state.amount, viewModel::updateAmount, "本次实付金额", canEdit)
                        if (historical && paymentId == null) TextButton(onClick = viewModel::fillUnrecordedAmount, enabled = canEdit && state.remainingAmount > 0.005) {
                            Text("填入参考金额")
                        }
                        if (historical && paymentId == null) Text("参考金额为订单总价减已录实付，请按实际付款情况调整。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.canRecordUnknownDate) Row(
                            modifier = Modifier.fillMaxWidth().toggleable(
                                value = state.isPaidDateUnknown,
                                enabled = canEdit,
                                role = Role.Checkbox,
                                onValueChange = viewModel::updatePaidDateUnknown
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = state.isPaidDateUnknown, onCheckedChange = null, enabled = canEdit)
                            Spacer(Modifier.width(skin.spacingSmall))
                            Text("付款日期记不清", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (state.canRecordUnknownDate && state.isPaidDateUnknown) {
                            PricingValueRow("实际付款日期", "日期未知")
                            Text("金额计入消费总额和消费分布，日期未知的记录不归入年度或月度趋势。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else PricingDateField("实际付款日期", state.paidDate, viewModel::updatePaidDate, canEdit, clearable = false)
                        Text(when (state.purchaseRecordStatus) {
                            PurchaseRecordStatus.HISTORICAL_OWNED -> "只补录能够确认的真实实付。记得日期时请选择真实日期，记不清时可标记日期未知。金额上限用于校验已录资料，未付余额仍为未知；保存保留历史已购状态。"
                            PurchaseRecordStatus.NEEDS_REVIEW -> "请先返回价格页核对购买状态，再补录或更正真实付款。"
                            PurchaseRecordStatus.TRACKED -> "保存表示这笔钱已实际付出。付清订单后，服饰及共用价格的小物自动变为已拥有。"
                        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } }
                LolitaSection("关联付款阶段（可选）") { row {
                    Column(verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                        FilterChip(selected = state.stageId == null, onClick = { viewModel.updateStageId(null) }, label = { Text("不指定阶段") }, enabled = canEdit)
                        state.stages.forEach { stage ->
                            FilterChip(selected = state.stageId == stage.id, onClick = { viewModel.updateStageId(stage.id) },
                                label = { Text("${stage.name} · ${formatPriceAmount(stage.amount)}") }, enabled = canEdit)
                        }
                        Text("同一阶段可以分多次付款，每次实付分别保留。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } }
                Button(onClick = savePayment, enabled = canSave, modifier = Modifier.fillMaxWidth()) {
                    if (state.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(skin.spacingSmall))
                    }
                    Text(if (state.isSaving) "保存中…" else "保存付款记录")
                }
                Spacer(Modifier.height(skin.spacingLarge))
            }
        }
    }
    state.error?.let { error -> AlertDialog(onDismissRequest = viewModel::clearError, title = { Text("提示") }, text = { Text(error) },
        confirmButton = { TextButton(onClick = viewModel::clearError) { Text("确定") } }) }
}
