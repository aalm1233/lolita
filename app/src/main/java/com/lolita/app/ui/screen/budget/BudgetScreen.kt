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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.theme.numericReading
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.animation.rememberSkinFlingBehavior
import java.math.BigDecimal
import java.time.*
import java.time.format.DateTimeFormatter

fun budgetMoney(cents: Long): String = "¥" + BigDecimal.valueOf(cents, 2).toPlainString()

@Composable
fun BudgetContent(viewModel: BudgetViewModel = viewModel(), onNavigateToOrder: (Long) -> Unit = {}, onNavigateToLedger: (Long) -> Unit = {}) {
    val state by viewModel.state.collectAsState()
    BudgetBody(state, viewModel::changeMonth, viewModel::resetEditor, viewModel::save, viewModel::remove, viewModel::retry, onNavigateToOrder, onNavigateToLedger)
}

@Composable
fun BudgetBody(state: BudgetUiState, onMonth: (Long) -> Unit, onEditor: () -> Unit,
    onSave: (String) -> Unit, onRemove: () -> Unit, onRetry: () -> Unit = {}, onNavigateToOrder: (Long) -> Unit = {}, onNavigateToLedger: (Long) -> Unit = {}) {
    val skin = LolitaSkin.current
    val scroll = rememberLazyListState()
    ObserveSkinScroll(scroll)
    var editing by rememberSaveable(state.month.toString()) { mutableStateOf(false) }
    var input by rememberSaveable(state.month.toString()) { mutableStateOf("") }
    LaunchedEffect(state.saved) { if (state.saved) editing = false }
    LaunchedEffect(state.showMoney) { if (!state.showMoney) editing = false }
    LazyColumn(state = scroll, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(skin.spacingLarge),
        verticalArrangement = Arrangement.spacedBy(skin.cardGap), flingBehavior = rememberSkinFlingBehavior()) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = { onMonth(-1) }, enabled = !state.saving) {
                com.lolita.app.ui.theme.skin.icon.SkinIcon(com.lolita.app.ui.theme.skin.icon.IconKey.KeyboardArrowLeft,
                    Modifier.semantics { contentDescription = "上一月" })
            }
            Text("${state.month.year}年${state.month.monthValue}月", modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { onMonth(1) }, enabled = !state.saving) {
                com.lolita.app.ui.theme.skin.icon.SkinIcon(com.lolita.app.ui.theme.skin.icon.IconKey.KeyboardArrowRight,
                    Modifier.semantics { contentDescription = "下一月" })
            }
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
                Text(state.limitCents?.let(::budgetMoney) ?: "尚未设置预算", style = if (state.limitCents != null) MaterialTheme.typography.headlineSmall.numericReading else MaterialTheme.typography.headlineSmall)

            } } }
            state.summary?.let { summary ->
                item { LolitaSection("本月付款") {
                    row { BudgetAmount("本月已录实付", summary.entries.filter { it.isPaid && it.amount > 0 }.sumOf { com.lolita.app.domain.usecase.OrderAccounting.cents(it.amount) }) }
                    row { BudgetAmount("本月退款到账", -summary.entries.filter { it.isPaid && it.amount < 0 }.sumOf { com.lolita.app.domain.usecase.OrderAccounting.cents(it.amount) }) }
                    row { BudgetAmount("本月净支出", summary.paidCents) }
                    row { BudgetAmount("本月剩余待付", summary.pendingCents) }
                    row { BudgetAmount("此前剩余待付", summary.earlierPendingCents) }
                    row {
                        val remaining = summary.projectedRemainingCents
                        Text(if (remaining == null) "设置预算后可查看预计余额" else if (remaining < 0) "预计超支 ${budgetMoney(-remaining)}" else "预计余额 ${budgetMoney(remaining)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (remaining != null && remaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        com.lolita.app.ui.screen.common.ExpandableInfo("计算方式") { Text("预计余额 = 预算 − 本月净支出 − 本月待付 − 此前待付。净支出 = 实付 − 已到账退款（含归档或取消订单），各按实际发生日期归月；跨月退款可使本月净支出为负。待付只计算当前跟踪订单。", style = MaterialTheme.typography.bodySmall) }
                    }
                } }
                if (summary.undatedPendingCents > 0 || summary.undatedPaidCents != 0L) item {
                    LolitaSection("未定日期的金额") {
                        row { BudgetAmount("未定日期待付", summary.undatedPendingCents) }
                        row { BudgetAmount("缺少付款日期的实付", summary.undatedPaidCents) }
                        row { Text("以上金额未计入任何月份或预计余额；请补齐日期后再安排预算。", style = MaterialTheme.typography.bodySmall) }
                    }
                }
                items(summary.undatedEntries, key = { "undated-${it.isPaid}-${it.id}" }) { entry ->
                    LolitaCard(variant = CardVariant.COMPACT, onClick = { if (entry.isPaid) onNavigateToLedger(entry.priceId) else onNavigateToOrder(entry.itemId) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                            Text(entry.itemName, style = MaterialTheme.typography.titleSmall)
                            Text(if (entry.isPaid) "实付日期待补" else "应付日期待定", style = MaterialTheme.typography.bodyMedium)
                            Text(budgetMoney(com.lolita.app.domain.usecase.OrderAccounting.cents(entry.amount)))
                            Text("查看订单并核对日期", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                item { SectionHeader("付款明细") }
                if (summary.entries.isEmpty()) item { Text("本月暂无付款记录或待付阶段。") }
                items(summary.entries, key = { "${it.isPaid}-${it.id}" }) { entry ->
                    LolitaCard(variant = CardVariant.COMPACT, onClick = { if (entry.isPaid) onNavigateToLedger(entry.priceId) else onNavigateToOrder(entry.itemId) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                            Text(entry.itemName, style = MaterialTheme.typography.titleSmall)
                            Text("${if (entry.isPaid && entry.amount < 0) "退款到账" else if (entry.isPaid) "已付" else "待付"} · ${entry.date?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ISO_DATE) }.orEmpty()}")
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
                Text("每月单独设置，可设为 0；取消预算后保留付款记录。", style = MaterialTheme.typography.bodySmall)
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
        Text(budgetMoney(cents), style = MaterialTheme.typography.titleLarge.numericReading)
    }
}
