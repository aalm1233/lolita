package com.lolita.app.ui.screen.price

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.PriceType
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.LolitaSection
import com.lolita.app.ui.screen.common.UnsavedChangesHandler
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceEditScreen(itemId: Long, priceId: Long?, onBack: () -> Unit, onSaveSuccess: () -> Unit, viewModel: PriceEditViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val skin = LolitaSkin.current
    var confirmTemplate by remember { mutableStateOf(false) }
    LaunchedEffect(priceId) { viewModel.loadPrice(priceId) }
    UnsavedChangesHandler(hasUnsavedChanges = viewModel.hasUnsavedChanges, onBack = onBack)
    val saveWithReminderPermissions = rememberStageReminderSave {
        scope.launch {
            if (priceId == null) viewModel.save(itemId).onSuccess { onSaveSuccess() }
            else viewModel.update(priceId).onSuccess { onSaveSuccess() }
        }
    }

    Scaffold(topBar = {
        GradientTopAppBar(title = { Text(if (priceId == null) "设置价格" else "编辑价格与计划") },
            navigationIcon = { IconButton(onClick = onBack) { SkinIcon(IconKey.ArrowBack) } }, actions = {
                IconButton(enabled = viewModel.isValid() && !state.isSaving, onClick = {
                    saveWithReminderPermissions(state.stages.any { it.reminderSet && it.dueDate != null })
                }) { if (state.isSaving) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) else SkinIcon(IconKey.Save) }
            })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(skin.spacingLarge),
            verticalArrangement = Arrangement.spacedBy(skin.cardGap)) {
            if (state.isLoading) PricingSkeleton() else {
                LolitaSection("订单价格") {
                    row { Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                        PriceMoneyField(state.totalPrice, viewModel::updateTotalPrice, "商品金额", !state.isSaving)
                        PriceMoneyField(state.shipping, viewModel::updateShipping, "邮费", !state.isSaving)
                        PricingValueRow("应付总额（含邮费）", state.payableTotal?.let(::formatPriceAmount) ?: "请填写金额")
                        if (priceId != null) PricingValueRow("历史实际已付", formatPriceAmount(state.paidAmount))
                        Text("修改金额或应付日期会更新余额，已付款记录保留。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } }
                }
                LolitaSection("付款计划") {
                    row { Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                            listOf(PriceType.FULL to "全款", PriceType.DEPOSIT_BALANCE to "定金尾款", PriceType.MULTI_STAGE to "多段").forEach { (type, label) ->
                                FilterChip(selected = state.priceType == type, onClick = { viewModel.updatePriceType(type) }, label = { Text(label) }, enabled = !state.isSaving)
                            }
                        }
                        OutlinedButton(onClick = { confirmTemplate = true }, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) { Text("使用所选模板") }
                        Text("阶段只是应付总额的拆分。实付请在付款页单独记录。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } }
                }
                state.stages.forEachIndexed { index, stage ->
                    LolitaSection("付款阶段 ${index + 1}", action = {
                        IconButton(onClick = { viewModel.removeStage(index) }, enabled = !state.isSaving) { SkinIcon(IconKey.Delete, tint = MaterialTheme.colorScheme.error) }
                    }) { row { StageDraftFields(stage, { viewModel.updateStage(index, it) }, !state.isSaving) } }
                }
                OutlinedButton(onClick = viewModel::addStage, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) { SkinIcon(IconKey.Add); Spacer(Modifier.width(skin.spacingSmall)); Text("增加付款阶段") }
                val sum = state.stages.sumOf { parseMoney(it.amount, true) ?: 0.0 }
                Text(if (state.stages.isEmpty()) "尚未安排付款阶段，未付余额仍按应付总额计算。" else "阶段合计 ${formatPriceAmount(sum)}，应与含邮费总额一致。",
                    style = MaterialTheme.typography.bodySmall, color = if (viewModel.isValid()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(skin.spacingLarge))
            }
        }
    }
    if (confirmTemplate) AlertDialog(onDismissRequest = { confirmTemplate = false }, title = { Text("重设付款计划") },
        text = { Text("将替换当前付款阶段，保留实际已付款记录。新阶段的应付日期默认为待定。") },
        confirmButton = { TextButton(onClick = { viewModel.applyTemplate(); confirmTemplate = false }) { Text("使用模板") } },
        dismissButton = { TextButton(onClick = { confirmTemplate = false }) { Text("取消") } })
    state.error?.let { error -> AlertDialog(onDismissRequest = viewModel::clearError, title = { Text("保存失败") }, text = { Text(error) },
        confirmButton = { TextButton(onClick = viewModel::clearError) { Text("确定") } }) }
}
