package com.lolita.app.ui.screen.calendar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.PaymentWithItemInfo
import com.lolita.app.data.local.entity.PriceType
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.PaymentRepository
import com.lolita.app.data.repository.PriceRepository
import com.lolita.app.di.AppModule

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon

// --- ViewModel ---

data class MonthStats(
    val month: Int, // 0-based
    val paidTotal: Double = 0.0,
    val paidCount: Int = 0,
    val unpaidTotal: Double = 0.0,
    val unpaidCount: Int = 0,
    val overdueAmount: Double = 0.0
)

data class PaymentCalendarUiState(
    val currentYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val selectedMonth: Int? = null, // 0-based, null = no month selected
    val yearPayments: List<PaymentWithItemInfo> = emptyList(),
    val monthStatsMap: Map<Int, MonthStats> = emptyMap(),
    val yearPaidTotal: Double = 0.0,
    val yearPaidCount: Int = 0,
    val yearUnpaidTotal: Double = 0.0,
    val yearUnpaidCount: Int = 0,
    val yearOverdueAmount: Double = 0.0,
    val isLoading: Boolean = true
)

class PaymentCalendarViewModel(
    private val priceRepository: PriceRepository = AppModule.priceRepository(),
    private val paymentRepository: PaymentRepository = AppModule.paymentRepository(),
    private val itemRepository: ItemRepository = AppModule.itemRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentCalendarUiState())
    val uiState: StateFlow<PaymentCalendarUiState> = _uiState.asStateFlow()

    private var loadDataJob: Job? = null

    init {
        loadData()
    }

    private fun loadData() {
        loadDataJob?.cancel()
        val state = _uiState.value
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, state.currentYear)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val yearStart = cal.timeInMillis
        cal.add(Calendar.YEAR, 1)
        val yearEnd = cal.timeInMillis - 1
        val now = System.currentTimeMillis()

        loadDataJob = viewModelScope.launch {
            priceRepository.getPaymentsWithItemInfoByDateRange(yearStart, yearEnd)
                .collect { payments ->
                    val monthStatsMap = buildMonthStatsMap(payments, state.currentYear, now)
                    val paid = payments.filter { it.isPaid }
                    val unpaid = payments.filter { !it.isPaid }
                    _uiState.value = _uiState.value.copy(
                        yearPayments = payments,
                        monthStatsMap = monthStatsMap,
                        yearPaidTotal = paid.sumOf { it.amount },
                        yearPaidCount = paid.size,
                        yearUnpaidTotal = unpaid.sumOf { it.amount },
                        yearUnpaidCount = unpaid.size,
                        yearOverdueAmount = unpaid.filter { it.dueDate < now }.sumOf { it.amount },
                        isLoading = false
                    )
                }
        }
    }

    fun previousYear() {
        _uiState.value = _uiState.value.copy(
            currentYear = _uiState.value.currentYear - 1,
            selectedMonth = null,
            isLoading = true
        )
        loadData()
    }

    fun nextYear() {
        _uiState.value = _uiState.value.copy(
            currentYear = _uiState.value.currentYear + 1,
            selectedMonth = null,
            isLoading = true
        )
        loadData()
    }

    fun selectMonth(month: Int) {
        val current = _uiState.value.selectedMonth
        _uiState.value = _uiState.value.copy(
            selectedMonth = if (current == month) null else month
        )
    }

    fun markAsPaid(payment: PaymentWithItemInfo) {
        viewModelScope.launch {
            val price = priceRepository.getPriceById(payment.priceId)
            val item = price?.let { itemRepository.getItemById(it.itemId) }
            val itemName = item?.name ?: "服饰"

            val fullPayment = paymentRepository.getPaymentById(payment.paymentId)
            if (fullPayment != null && !fullPayment.isPaid) {
                paymentRepository.updatePayment(
                    fullPayment.copy(isPaid = true, paidDate = System.currentTimeMillis()),
                    itemName
                )
            }
        }
    }

    private fun buildMonthStatsMap(
        payments: List<PaymentWithItemInfo>,
        year: Int,
        now: Long
    ): Map<Int, MonthStats> {
        val cal = Calendar.getInstance()
        val monthPayments = mutableMapOf<Int, MutableList<PaymentWithItemInfo>>()
        payments.forEach { p ->
            cal.timeInMillis = p.dueDate
            if (cal.get(Calendar.YEAR) == year) {
                val month = cal.get(Calendar.MONTH)
                monthPayments.getOrPut(month) { mutableListOf() }.add(p)
            }
        }
        return monthPayments.mapValues { (month, list) ->
            val paid = list.filter { it.isPaid }
            val unpaid = list.filter { !it.isPaid }
            MonthStats(
                month = month,
                paidTotal = paid.sumOf { it.amount },
                paidCount = paid.size,
                unpaidTotal = unpaid.sumOf { it.amount },
                unpaidCount = unpaid.size,
                overdueAmount = unpaid.filter { it.dueDate < now }.sumOf { it.amount }
            )
        }
    }
}

// --- UI ---

@Composable
fun PaymentCalendarContent(
    viewModel: PaymentCalendarViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            YearHeader(
                year = uiState.currentYear,
                yearPaidTotal = uiState.yearPaidTotal,
                yearPaidCount = uiState.yearPaidCount,
                yearUnpaidTotal = uiState.yearUnpaidTotal,
                yearUnpaidCount = uiState.yearUnpaidCount,
                yearOverdueAmount = uiState.yearOverdueAmount,
                onPrevious = viewModel::previousYear,
                onNext = viewModel::nextYear
            )
        }
        item {
            MonthCardGrid(
                monthStatsMap = uiState.monthStatsMap,
                selectedMonth = uiState.selectedMonth,
                currentYear = uiState.currentYear,
                onMonthClick = viewModel::selectMonth
            )
        }

        val selectedPayments = uiState.selectedMonth?.let { month ->
            getPaymentsForMonth(uiState.yearPayments, uiState.currentYear, month)
        } ?: emptyList()

        if (uiState.selectedMonth != null) {
            item {
                Text(
                    "${uiState.selectedMonth!! + 1}月 付款记录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (selectedPayments.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "当月无付款记录",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(selectedPayments, key = { it.paymentId }) { payment ->
                    PaymentInfoCard(
                        payment = payment,
                        onMarkPaid = if (!payment.isPaid) {{ viewModel.markAsPaid(payment) }} else null
                    )
                }
            }
        }
    }
}

@Composable
private fun YearHeader(
    year: Int,
    yearPaidTotal: Double,
    yearPaidCount: Int,
    yearUnpaidTotal: Double,
    yearUnpaidCount: Int,
    yearOverdueAmount: Double,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious) {
                    SkinIcon(IconKey.KeyboardArrowLeft)
                }
                Text(
                    "${year}年",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onNext) {
                    SkinIcon(IconKey.KeyboardArrowRight)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatChip("已付", yearPaidTotal, yearPaidCount, Color(0xFF4CAF50))
                StatChip("待付", yearUnpaidTotal, yearUnpaidCount, MaterialTheme.colorScheme.primary)
                if (yearOverdueAmount > 0) {
                    StatChip("逾期", yearOverdueAmount, null, Color(0xFFD32F2F))
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, amount: Double, count: Int?, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        Spacer(Modifier.width(4.dp))
        Text(
            "¥${String.format("%.0f", amount)}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        if (count != null) {
            Text(
                "(${count})",
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun MonthCardGrid(
    monthStatsMap: Map<Int, MonthStats>,
    selectedMonth: Int?,
    currentYear: Int,
    onMonthClick: (Int) -> Unit
) {
    val todayCal = Calendar.getInstance()
    val isCurrentYear = todayCal.get(Calendar.YEAR) == currentYear
    val currentMonth = if (isCurrentYear) todayCal.get(Calendar.MONTH) else -1

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(4) { col ->
                    val month = row * 4 + col
                    MonthCard(
                        month = month,
                        stats = monthStatsMap[month],
                        isCurrentMonth = month == currentMonth,
                        isSelected = month == selectedMonth,
                        modifier = Modifier.weight(1f),
                        onClick = { onMonthClick(month) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthCard(
    month: Int,
    stats: MonthStats?,
    isCurrentMonth: Boolean,
    isSelected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val hasPayments = stats != null
    val hasOverdue = (stats?.overdueAmount ?: 0.0) > 0

    val bgColor by animateColorAsState(
        if (isSelected) primaryColor.copy(alpha = 0.15f)
        else MaterialTheme.colorScheme.surface,
        animationSpec = tween(200), label = "monthBg"
    )

    Card(
        modifier = modifier
            .heightIn(min = 100.dp)
            .then(
                if (isCurrentMonth) Modifier.border(
                    2.dp, primaryColor, MaterialTheme.shapes.medium
                ) else Modifier
            )
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                "${month + 1}月",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (hasPayments) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            if (stats != null) {
                if (stats.paidTotal > 0) {
                    Text(
                        "已付 ¥${String.format("%.0f", stats.paidTotal)}",
                        fontSize = 10.sp,
                        color = Color(0xFF4CAF50),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "(${stats.paidCount}笔)",
                        fontSize = 9.sp,
                        color = Color(0xFF4CAF50).copy(alpha = 0.7f)
                    )
                }
                if (stats.unpaidTotal > 0) {
                    val unpaidColor = if (hasOverdue) Color(0xFFD32F2F) else primaryColor
                    Text(
                        "待付 ¥${String.format("%.0f", stats.unpaidTotal)}",
                        fontSize = 10.sp,
                        color = unpaidColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "(${stats.unpaidCount}笔)",
                        fontSize = 9.sp,
                        color = unpaidColor.copy(alpha = 0.7f)
                    )
                }
                if (hasOverdue) {
                    Text(
                        "逾期 ¥${String.format("%.0f", stats.overdueAmount)}",
                        fontSize = 9.sp,
                        color = Color(0xFFD32F2F)
                    )
                }
            }
        }
    }
}

@Composable
private fun PaymentInfoCard(
    payment: PaymentWithItemInfo,
    onMarkPaid: (() -> Unit)? = null
) {
    val typeLabel = when (payment.priceType) {
        PriceType.DEPOSIT_BALANCE -> "定金尾款"
        PriceType.FULL -> "全款"
    }
    val now = System.currentTimeMillis()
    val isOverdue = !payment.isPaid && payment.dueDate < now
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (showConfirmDialog && onMarkPaid != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("确认付款") },
            text = {
                Text("确认将 ${payment.itemName} 的 ¥${String.format("%.2f", payment.amount)} 标记为已付款？")
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    onMarkPaid()
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("取消") }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isOverdue) CardDefaults.cardColors(
            containerColor = Color(0xFFD32F2F).copy(alpha = 0.06f)
        ) else CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    payment.itemName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = if (payment.isPaid) Color(0xFF4CAF50).copy(alpha = 0.1f)
                    else if (isOverdue) Color(0xFFD32F2F).copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        if (payment.isPaid) "已付清" else if (isOverdue) "已逾期" else "待付款",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (payment.isPaid) Color(0xFF4CAF50)
                        else if (isOverdue) Color(0xFFD32F2F)
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$typeLabel ¥${String.format("%.2f", payment.amount)}  应付: ${sdf.format(Date(payment.dueDate))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (onMarkPaid != null) {
                    TextButton(
                        onClick = { showConfirmDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("标记已付", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

private fun getPaymentsForMonth(
    payments: List<PaymentWithItemInfo>,
    year: Int,
    month: Int
): List<PaymentWithItemInfo> {
    val cal = Calendar.getInstance()
    return payments.filter { p ->
        cal.timeInMillis = p.dueDate
        cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month
    }
}
