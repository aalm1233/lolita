package com.lolita.app.ui.screen.calendar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.PaymentWithItemInfo
import com.lolita.app.data.local.entity.PriceType
import com.lolita.app.data.repository.PaymentRepository
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
    private val paymentRepository: PaymentRepository = AppModule.paymentRepository()
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
            paymentRepository.getPaymentsWithItemInfoByDateRange(yearStart, yearEnd)
                .collect { payments ->
                    val monthStatsMap = buildMonthStatsMap(payments, state.currentYear, now)
                    val yearPaid = payments.filter { it.isPaid }
                    val yearUnpaid = payments.filter { !it.isPaid }
                    _uiState.value = _uiState.value.copy(
                        yearPayments = payments,
                        monthStatsMap = monthStatsMap,
                        yearPaidTotal = yearPaid.sumOf { it.amount },
                        yearPaidCount = yearPaid.size,
                        yearUnpaidTotal = yearUnpaid.sumOf { it.amount },
                        yearUnpaidCount = yearUnpaid.size,
                        yearOverdueAmount = yearUnpaid.filter { it.dueDate < now }.sumOf { it.amount },
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

    fun markAsPaid(paymentId: Long, itemName: String = "") {
        viewModelScope.launch {
            val payment = paymentRepository.getPaymentById(paymentId) ?: return@launch
            paymentRepository.updatePayment(
                payment.copy(isPaid = true, paidDate = System.currentTimeMillis()),
                itemName = itemName
            )
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
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            MonthHeader(
                year = uiState.currentYear,
                month = uiState.currentMonth,
                monthPaidTotal = uiState.monthPaidTotal,
                monthPaidCount = uiState.monthPaidCount,
                monthUnpaidTotal = uiState.monthUnpaidTotal,
                monthUnpaidCount = uiState.monthUnpaidCount,
                overdueAmount = uiState.overdueAmount,
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth
            )
        }
        item {
            CalendarGrid(
                year = uiState.currentYear,
                month = uiState.currentMonth,
                selectedDay = uiState.selectedDay,
                payments = uiState.monthPayments,
                onDayClick = viewModel::selectDay
            )
        }
        val selectedPayments = uiState.selectedDay?.let { day ->
            getPaymentsForDay(uiState.monthPayments, uiState.currentYear, uiState.currentMonth, day)
        } ?: emptyList()

        if (uiState.selectedDay != null) {
            item {
                Text(
                    "${uiState.currentMonth + 1}月${uiState.selectedDay}日 付款记录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (selectedPayments.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "当日无付款记录",
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
                        onMarkPaid = {
                            coroutineScope.launch { viewModel.markAsPaid(payment.paymentId, payment.itemName) }
                        }
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
private fun CalendarGrid(
    year: Int,
    month: Int,
    selectedDay: Int?,
    payments: List<PaymentWithItemInfo>,
    onDayClick: (Int) -> Unit
) {
    val weekDays = listOf("日", "一", "二", "三", "四", "五", "六")
    val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sunday
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val now = System.currentTimeMillis()
    val sevenDaysLater = now + 7L * 24 * 60 * 60 * 1000

    val todayCal = Calendar.getInstance()
    val isCurrentMonth = todayCal.get(Calendar.YEAR) == year && todayCal.get(Calendar.MONTH) == month
    val today = if (isCurrentMonth) todayCal.get(Calendar.DAY_OF_MONTH) else -1

    val dayStatusMap = buildDayStatusMap(payments, year, month, now, sevenDaysLater)
    val dayAmountMap = buildDayAmountMap(payments, year, month)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Weekday headers
            Row(modifier = Modifier.fillMaxWidth()) {
                weekDays.forEach { day ->
                    Text(
                        day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            // Fixed 6 rows
            repeat(6) { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    repeat(7) { col ->
                        val cellIndex = row * 7 + col
                        val dayOfMonth = cellIndex - firstDayOfWeek + 1
                        val isInMonth = dayOfMonth in 1..daysInMonth

                        if (isInMonth) {
                            DayCell(
                                day = dayOfMonth,
                                isToday = dayOfMonth == today,
                                status = dayStatusMap[dayOfMonth],
                                amountInfo = dayAmountMap[dayOfMonth],
                                isSelected = dayOfMonth == selectedDay,
                                modifier = Modifier.weight(1f),
                                onClick = { onDayClick(dayOfMonth) }
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp)
                                    .padding(1.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        MaterialTheme.shapes.extraSmall
                                    )
                            )
                        }
                    }
                }
                if (row < 5) Spacer(Modifier.height(2.dp))
            }
        }
    }
}

private enum class DayStatus { OVERDUE, UPCOMING, UNPAID, ALL_PAID }

private data class DayAmountInfo(
    val paidTotal: Double = 0.0,
    val unpaidTotal: Double = 0.0,
    val paidCount: Int = 0,
    val unpaidCount: Int = 0
)

@Composable
private fun DayCell(
    day: Int,
    isToday: Boolean,
    status: DayStatus?,
    amountInfo: DayAmountInfo?,
    isSelected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val borderColor by animateColorAsState(
        if (isSelected) primaryColor else Color.Transparent,
        animationSpec = tween(200), label = "dayBorder"
    )

    Box(
        modifier = modifier
            .height(64.dp)
            .padding(1.dp)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = borderColor,
                shape = MaterialTheme.shapes.extraSmall
            )
            .background(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.shapes.extraSmall
            )
            .clickable(onClick = onClick)
            .padding(3.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isToday) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(primaryColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        day.toString(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            } else {
                Text(
                    day.toString(),
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (amountInfo != null) {
                var linesShown = 0
                if (amountInfo.paidTotal > 0 && linesShown < 2) {
                    Text(
                        "¥${String.format("%.0f", amountInfo.paidTotal)}",
                        fontSize = 9.sp,
                        color = Color(0xFF4CAF50),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 10.sp
                    )
                    linesShown++
                }
                if (amountInfo.unpaidTotal > 0 && linesShown < 2) {
                    Text(
                        "¥${String.format("%.0f", amountInfo.unpaidTotal)}",
                        fontSize = 9.sp,
                        color = when (status) {
                            DayStatus.OVERDUE -> Color(0xFFD32F2F)
                            else -> primaryColor
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 10.sp
                    )
                    linesShown++
                }

                val totalEntries = amountInfo.paidCount + amountInfo.unpaidCount
                if (totalEntries > 2) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        "+${totalEntries - 2}",
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}

@Composable
private fun PaymentInfoCard(payment: PaymentWithItemInfo, onMarkPaid: () -> Unit) {
    val typeLabel = when (payment.priceType) {
        PriceType.DEPOSIT_BALANCE -> "尾款"
        PriceType.FULL -> "全款"
    }
    val isOverdue = !payment.isPaid && payment.dueDate < System.currentTimeMillis()
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

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
                        if (payment.isPaid) "已付款" else if (isOverdue) "已逾期" else "待付款",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (payment.isPaid) Color(0xFF4CAF50)
                        else if (isOverdue) Color(0xFFD32F2F)
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "$typeLabel ¥${String.format("%.2f", payment.amount)}  应付: ${sdf.format(Date(payment.dueDate))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!payment.isPaid) {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onMarkPaid) {
                        SkinIcon(IconKey.Save, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("标记已付款")
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
