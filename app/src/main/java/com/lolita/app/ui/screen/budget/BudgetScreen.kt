package com.lolita.app.ui.screen.budget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.animation.rememberSkinFlingBehavior
import java.math.BigDecimal
import java.time.*
import java.time.format.DateTimeFormatter

fun budgetMoney(cents: Long): String = "¥" + BigDecimal.valueOf(cents, 2).toPlainString()

@Composable
fun BudgetContent(viewModel: BudgetViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    BudgetBody(state, viewModel::changeMonth, viewModel::resetEditor, viewModel::save, viewModel::remove, viewModel::retry)
}

@Composable
fun BudgetBody(state: BudgetUiState, onMonth: (Long) -> Unit, onEditor: () -> Unit,
    onSave: (String) -> Unit, onRemove: () -> Unit, onRetry: () -> Unit = {}) {
    val skin = LolitaSkin.current
    val scroll = rememberLazyListState()
    ObserveSkinScroll(scroll)
    var editing by rememberSaveable(state.month.toString()) { mutableStateOf(false) }
    var input by rememberSaveable(state.month.toString()) { mutableStateOf("") }
    LaunchedEffect(state.saved) { if (state.saved) editing = false }
    LaunchedEffect(state.showMoney) { if (!state.showMoney) editing = false }
    LazyColumn(state = scroll, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(skin.spacingLarge),
        verticalArrangement = Arrangement.spacedBy(skin.cardGap), flingBehavior = rememberSkinFlingBehavior()) {
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onMonth(-1) }, enabled = !state.saving) { Text("上一月") }
            Text("${state.month.year}年${state.month.monthValue}月", modifier = Modifier.padding(top = skin.spacingMedium), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { onMonth(1) }, enabled = !state.saving) { Text("下一月") }
        } }
        if (state.loading) {
            items(3) { LolitaCard { Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                ShimmerLine(widthFraction = 0.5f)
                ShimmerLine(widthFraction = 0.8f)
            } } }
        } else if (!state.showMoney) {
            item { LolitaSection("金额已隐藏") { row { Text("请在个人页开启「显示金额」后查看和管理预算。") } } }
        } else {
            state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error)
                if (state.summary == null) TextButton(onClick = onRetry) { Text("重新加载预算") }
            } }
            item { LolitaSection(title = "月度预算", action = {
                TextButton(onClick = {
                    input = state.limitCents?.let { BigDecimal.valueOf(it, 2).toPlainString() }.orEmpty()
                    onEditor(); editing = true
                }, enabled = !state.saving && state.summary != null) { Text(if (state.limitCents == null) "设置" else "修改") }
            }) { row {
                Text(state.limitCents?.let(::budgetMoney) ?: "尚未设置预算", style = MaterialTheme.typography.headlineSmall)
                Text("每月单独设置，可设为 0；取消预算后保留付款记录。", style = MaterialTheme.typography.bodySmall)
            } } }
            state.summary?.let { summary ->
                item { LolitaSection("本月付款压力") {
                    row { BudgetAmount("本月已录实付", summary.paidCents) }
                    row { BudgetAmount("本月剩余待付", summary.pendingCents) }
                    row { BudgetAmount("此前剩余待付", summary.earlierPendingCents) }
                    row {
                        val remaining = summary.projectedRemainingCents
                        Text(if (remaining == null) "设置预算后可查看预计余额" else if (remaining < 0) "预计超支 ${budgetMoney(-remaining)}" else "预计余额 ${budgetMoney(remaining)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (remaining != null && remaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        Text("预计余额 = 预算 − 本月实付 − 本月待付 − 此前待付。实付按实际付款日期归月，待付只计算当前跟踪订单的剩余阶段。", style = MaterialTheme.typography.bodySmall)
                    }
                } }
                if (summary.undatedPendingCents > 0 || summary.undatedPaidCents != 0L) item {
                    LolitaSection("未定日期的金额") {
                        row { BudgetAmount("未定日期待付", summary.undatedPendingCents) }
                        row { BudgetAmount("缺少付款日期的实付", summary.undatedPaidCents) }
                        row { Text("以上金额未计入任何月份或预计余额；请补齐日期后再安排预算。", style = MaterialTheme.typography.bodySmall) }
                    }
                }
                item { SectionHeader("付款明细") }
                if (summary.entries.isEmpty()) item { Text("本月暂无付款记录或待付阶段。") }
                items(summary.entries, key = { "${it.isPaid}-${it.id}" }) { entry ->
                    LolitaCard(variant = CardVariant.COMPACT) {
                        Column(verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                            Text(entry.itemName, style = MaterialTheme.typography.titleSmall)
                            Text("${if (entry.isPaid) "已付" else "待付"} · ${entry.date?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ISO_DATE) }.orEmpty()}")
                            Text(budgetMoney(com.lolita.app.domain.usecase.OrderAccounting.cents(entry.amount)))
                        }
                    }
                }
            }
        }
    }
    if (editing && state.showMoney) AlertDialog(onDismissRequest = { if (!state.saving) editing = false },
        title = { Text("设置 ${state.month.monthValue} 月预算") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("预算金额（元）") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = !state.saving)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.limitCents != null) TextButton(onClick = onRemove, enabled = !state.saving) { Text("取消本月预算") }
            }
        }, confirmButton = { TextButton(onClick = { onSave(input) }, enabled = !state.saving && input.isNotBlank()) { Text(if (state.saving) "保存中…" else "保存") } },
        dismissButton = { TextButton(onClick = { editing = false }, enabled = !state.saving) { Text("返回") } })
}

@Composable
private fun BudgetAmount(label: String, cents: Long) {
    Column(Modifier.fillMaxWidth().padding(vertical = LolitaSkin.current.spacingSmall)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(budgetMoney(cents), style = MaterialTheme.typography.titleLarge)
    }
}
