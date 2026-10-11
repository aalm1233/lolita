package com.lolita.app.ui.screen.price

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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
    LaunchedEffect(priceId, paymentId) { viewModel.loadPayment(priceId, paymentId) }
    UnsavedChangesHandler(viewModel.hasUnsavedChanges, onBack)
    Scaffold(topBar = { GradientTopAppBar(title = { Text(if (paymentId == null) "记录实际付款" else "编辑实际付款") },
        navigationIcon = { IconButton(onClick = onBack) { SkinIcon(IconKey.ArrowBack) } }, actions = {
            IconButton(enabled = viewModel.isValid() && !state.isSaving, onClick = { scope.launch {
                if (paymentId == null) viewModel.save(priceId).onSuccess { onSaveSuccess() }
                else viewModel.update(paymentId).onSuccess { onSaveSuccess() }
            } }) { if (state.isSaving) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) else SkinIcon(IconKey.Save) }
        }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(skin.spacingLarge),
            verticalArrangement = Arrangement.spacedBy(skin.cardGap)) {
            if (state.isLoading) PricingSkeleton() else {
                LolitaSection("实际付款") { row {
                    Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                        PricingValueRow(if (paymentId == null) "当前可付余额" else "此笔可调整金额上限", formatPriceAmount(state.remainingAmount.coerceAtLeast(0.0)))
                        PriceMoneyField(state.amount, viewModel::updateAmount, "本次实付金额", !state.isSaving)
                        PricingDateField("实际付款日期", state.paidDate, viewModel::updatePaidDate, !state.isSaving, clearable = false)
                        Text("保存表示这笔钱已实际付出。付清订单后，服饰及共用价格的小物自动变为已拥有。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } }
                LolitaSection("关联付款阶段（可选）") { row {
                    Column(verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                        FilterChip(selected = state.stageId == null, onClick = { viewModel.updateStageId(null) }, label = { Text("不指定阶段") }, enabled = !state.isSaving)
                        state.stages.forEach { stage ->
                            FilterChip(selected = state.stageId == stage.id, onClick = { viewModel.updateStageId(stage.id) },
                                label = { Text("${stage.name} · ${formatPriceAmount(stage.amount)}") }, enabled = !state.isSaving)
                        }
                        Text("同一阶段可以分多次付款，每次实付分别保留。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } }
            }
        }
    }
    state.error?.let { error -> AlertDialog(onDismissRequest = viewModel::clearError, title = { Text("提示") }, text = { Text(error) },
        confirmButton = { TextButton(onClick = viewModel::clearError) { Text("确定") } }) }
}
