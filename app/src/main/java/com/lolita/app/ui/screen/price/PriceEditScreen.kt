package com.lolita.app.ui.screen.price

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.PriceType
import com.lolita.app.data.local.entity.PurchaseRecordStatus
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.LolitaSection
import com.lolita.app.ui.screen.common.UnsavedChangesHandler
import com.lolita.app.ui.screen.common.EditorSaveBar
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PriceEditScreen(itemId: Long, priceId: Long?, onBack: () -> Unit, onSaveSuccess: () -> Unit, viewModel: PriceEditViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val skin = LolitaSkin.current
    val tracked = state.purchaseRecordStatus == PurchaseRecordStatus.TRACKED
    val canEdit = tracked && !state.isSaving && !state.isLoading && !state.loadFailed
    var confirmTemplate by remember { mutableStateOf(false) }
    LaunchedEffect(priceId, itemId) { viewModel.loadPrice(priceId); if (priceId == null) viewModel.loadReferenceQuote(itemId) }
    val requestBack = UnsavedChangesHandler(hasUnsavedChanges = viewModel.hasUnsavedChanges, onBack = onBack)
    val saveWithReminderPermissions = rememberStageReminderSave {
        scope.launch {
            if (priceId == null) viewModel.save(itemId).onSuccess { onSaveSuccess() }
            else viewModel.update(priceId).onSuccess { onSaveSuccess() }
        }
    }

    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
        GradientTopAppBar(title = { Text(if (priceId == null) "设置价格" else "编辑价格与计划") },
            navigationIcon = { IconButton(onClick = requestBack, enabled = !state.isSaving) { SkinIcon(IconKey.ArrowBack) } })
    }, bottomBar = {
        EditorSaveBar("保存价格与计划", state.isSaving, canEdit && viewModel.isValid()) {
            saveWithReminderPermissions(state.stages.any { it.reminderSet && it.dueDate != null })
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).verticalScroll(rememberScrollState()).padding(skin.spacingLarge),
            verticalArrangement = Arrangement.spacedBy(skin.cardGap)) {
            if (state.isLoading) PricingSkeleton() else if (state.loadFailed) {
                LolitaSection("价格暂时无法读取") { row { Text("请返回后重试，现有价格与付款记录保留。") } }
            } else {
                if (!tracked) PurchaseRecordNotice(state.purchaseRecordStatus, isWorking = state.isSaving, onReturnToReview = requestBack)
                if (priceId == null && state.referenceQuote != null) {
                    val quote = state.referenceQuote!!
                    LolitaSection("淘宝参考报价") { row {
                        Column(verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                            Text("¥${quote.amount} · ${quote.specification}")
                            Text("请核对款式、定金/尾款与优惠。点击采用仅填入商品金额，确认保存后建立订单；实付另行记录。",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(onClick = viewModel::useReferenceQuote,
                                enabled = canEdit && state.totalPrice.isBlank()) { Text("使用参考报价 ¥${quote.amount}") }
                        }
                    } }
                }
                LolitaSection("订单价格") {
                    row { Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                        PriceMoneyField(state.totalPrice, viewModel::updateTotalPrice, "商品金额", canEdit)
                        PriceMoneyField(state.shipping, viewModel::updateShipping, "邮费", canEdit)
                        PricingValueRow(if (tracked) "应付总额（含邮费）" else "订单总价（含邮费）", state.payableTotal?.let(::formatPriceAmount) ?: "请填写金额")
                        if (priceId != null) PricingValueRow(if (tracked) "历史实际已付" else "已录实付", if (!tracked && !state.hasRecordedPayments) "付款记录未补全" else formatPriceAmount(state.paidAmount))
                        Text(if (tracked) "修改金额或应付日期会更新余额，已付款记录保留。" else "历史价格和付款阶段只读。请回价格页核对完整账本并启用付款跟踪后，再修改计划。",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } }
                }
                LolitaSection(if (tracked) "付款计划" else "原付款计划（只读）") {
                    row { Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                            listOf(PriceType.FULL to "全款", PriceType.DEPOSIT_BALANCE to "定金尾款", PriceType.MULTI_STAGE to "多段").forEach { (type, label) ->
                                FilterChip(selected = state.priceType == type, onClick = { viewModel.updatePriceType(type) }, label = { Text(label) }, enabled = canEdit)
                            }
                        }
                        TextButton(onClick = { confirmTemplate = true }, enabled = canEdit) { Text("重新拆分计划") }
                        if (tracked) Text(state.calculationHint ?: "金额可自动补算，手动输入优先。",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (tracked) "阶段只是应付总额的拆分。实付请在付款页单独记录。" else "原阶段金额和日期保留，不据此认定欠款或设置提醒。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } }
                }
                state.stages.forEachIndexed { index, stage ->
                    var expanded by rememberSaveable(stage.id, index) { mutableStateOf(stage.amount.isBlank()) }
                    LolitaSection("付款阶段 ${index + 1}", action = {
                        IconButton(onClick = { viewModel.removeStage(index) }, enabled = canEdit) { SkinIcon(IconKey.Delete, tint = MaterialTheme.colorScheme.error) }
                    }) { row {
                        PricingValueRow(stage.name.ifBlank { "未命名阶段" }, parseMoney(stage.amount)?.let(::formatPriceAmount) ?: "金额待填写")
                        Text("应付日期：${formatPriceDate(stage.dueDate)}", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起阶段 ${index + 1}" else "编辑阶段 ${index + 1}") }
                        if (expanded) StageDraftFields(stage, { viewModel.updateStage(index, it) }, canEdit)
                    } }
                }
                OutlinedButton(onClick = viewModel::addStage, enabled = canEdit, modifier = Modifier.fillMaxWidth()) { SkinIcon(IconKey.Add); Spacer(Modifier.width(skin.spacingSmall)); Text("增加付款阶段") }
                val sum = state.stages.sumOf { parseMoney(it.amount, true) ?: 0.0 }
                Text(if (!tracked) "原阶段合计 ${formatPriceAmount(sum)}，未付余额为未知。" else if (state.stages.isEmpty()) "尚未安排付款阶段，未付余额仍按应付总额计算。" else "阶段合计 ${formatPriceAmount(sum)}，应与含邮费总额一致。",
                    style = MaterialTheme.typography.bodySmall, color = if (!tracked || viewModel.isValid()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(skin.spacingLarge))
            }
        }
    }
    if (confirmTemplate) AlertDialog(onDismissRequest = { confirmTemplate = false }, title = { Text("重设付款计划") },
        text = { Text("将替换当前付款阶段，保留实际已付款记录。新阶段的应付日期默认为待定。") },
        confirmButton = { TextButton(enabled = canEdit, onClick = { viewModel.applyTemplate(); confirmTemplate = false }) { Text("使用模板") } },
        dismissButton = { TextButton(onClick = { confirmTemplate = false }) { Text("取消") } })
    state.error?.let { error -> AlertDialog(onDismissRequest = viewModel::clearError, title = { Text("保存失败") }, text = { Text(error) },
        confirmButton = { TextButton(onClick = viewModel::clearError) { Text("确定") } }) }
}
