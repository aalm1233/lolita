package com.lolita.app.ui.screen.calendar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
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

data class PaymentCalendarUiState(
    val currentYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val currentMonth: Int = Calendar.getInstance().get(Calendar.MONTH), // 0-based
    val selectedDay: Int? = null,
    val monthPayments: List<PaymentWithItemInfo> = emptyList(),
    val monthUnpaidTotal: Double = 0.0,
    val totalUnpaidAmount: Double = 0.0,
    val overdueAmount: Double = 0.0,
    val monthPaidTotal: Double = 0.0,
    val monthPaidCount: Int = 0,
    val monthUnpaidCount: Int = 0,
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
            set(Calendar.MONTH, state.currentMonth)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val monthStart = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        val monthEnd = cal.timeInMillis - 1
        val now = System.currentTimeMillis()

        loadDataJob = viewModelScope.launch {
            combine(
                paymentRepository.getPaymentsWithItemInfoByDateRange(monthStart, monthEnd),
                paymentRepository.getMonthUnpaidTotal(monthStart, monthEnd),
                paymentRepository.getTotalUnpaidAmount(),
                paymentRepository.getOverdueAmount(now)
            ) { payments, monthUnpaid, totalUnpaid, overdue ->
                val paidPayments = payments.filter { it.isPaid }
                val unpaidPayments = payments.filter { !it.isPaid }
                _uiState.value.copy(
                    monthPayments = payments,
                    monthUnpaidTotal = monthUnpaid,
                    totalUnpaidAmount = totalUnpaid,
                    overdueAmount = overdue,
                    monthPaidTotal = paidPayments.sumOf { it.amount },
                    monthPaidCount = paidPayments.size,
                    monthUnpaidCount = unpaidPayments.size,
                    isLoading = false
                )
            }.collect { _uiState.value = it }
        }
    }

    fun previousMonth() {
        val state = _uiState.value
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, state.currentYear)
            set(Calendar.MONTH, state.currentMonth)
        }
        cal.add(Calendar.MONTH, -1)
        _uiState.value = state.copy(
            currentYear = cal.get(Calendar.YEAR),
            currentMonth = cal.get(Calendar.MONTH),
            selectedDay = null,
            isLoading = true
        )
        loadData()
    }

    fun nextMonth() {
        val state = _uiState.value
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, state.currentYear)
            set(Calendar.MONTH, state.currentMonth)
        }
        cal.add(Calendar.MONTH, 1)
        _uiState.value = state.copy(
            currentYear = cal.get(Calendar.YEAR),
            currentMonth = cal.get(Calendar.MONTH),
            selectedDay = null,
            isLoading = true
        )
        loadData()
    }

    fun selectDay(day: Int) {
        _uiState.value = _uiState.value.copy(selectedDay = day)
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
            StatsRow(
                monthPaid = uiState.monthPaidTotal,
                monthPaidCount = uiState.monthPaidCount,
                monthUnpaid = uiState.monthUnpaidTotal,
                monthUnpaidCount = uiState.monthUnpaidCount,
                overdue = uiState.overdueAmount
            )
        }
        item {
            MonthHeader(
                year = uiState.currentYear,
                month = uiState.currentMonth,
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
private fun StatsRow(
    monthPaid: Double,
    monthPaidCount: Int,
    monthUnpaid: Double,
    monthUnpaidCount: Int,
    overdue: Double
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MiniStatCard("当月已付", monthPaid, Color(0xFF4CAF50), Modifier.weight(1f), subtitle = "${monthPaidCount}笔")
        MiniStatCard("当月待付", monthUnpaid, MaterialTheme.colorScheme.primary, Modifier.weight(1f), subtitle = "${monthUnpaidCount}笔")
        MiniStatCard("已逾期", overdue, Color(0xFFD32F2F), Modifier.weight(1f))
    }
}

@Composable
private fun MiniStatCard(label: String, amount: Double, color: Color, modifier: Modifier, subtitle: String? = null) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            Spacer(Modifier.height(4.dp))
            Text(
                "¥${String.format("%.0f", amount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = color.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun MonthHeader(year: Int, month: Int, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            SkinIcon(IconKey.KeyboardArrowLeft)
        }
        Text(
            "${year}年${month + 1}月",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onNext) {
            SkinIcon(IconKey.KeyboardArrowRight)
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

    // Build day→status map
    val dayStatusMap = buildDayStatusMap(payments, year, month, now, sevenDaysLater)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
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
            Spacer(Modifier.height(8.dp))

            // Day cells
            var dayCounter = 1
            val rows = ((firstDayOfWeek + daysInMonth + 6) / 7)
            repeat(rows) { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    repeat(7) { col ->
                        val cellIndex = row * 7 + col
                        if (cellIndex < firstDayOfWeek || dayCounter > daysInMonth) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            val day = dayCounter
                            val status = dayStatusMap[day]
                            val isSelected = day == selectedDay
                            DayCell(
                                day = day,
                                status = status,
                                isSelected = isSelected,
                                modifier = Modifier.weight(1f),
                                onClick = { onDayClick(day) }
                            )
                            dayCounter++
                        }
                    }
                }
                if (row < rows - 1) Spacer(Modifier.height(4.dp))
            }
        }
    }
}

private enum class DayStatus { OVERDUE, UPCOMING, UNPAID, ALL_PAID }

@Composable
private fun DayCell(
    day: Int,
    status: DayStatus?,
    isSelected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val dotColor = when (status) {
        DayStatus.OVERDUE -> Color(0xFFD32F2F)
        DayStatus.UPCOMING -> Color(0xFFFF9800)
        DayStatus.UNPAID -> MaterialTheme.colorScheme.primary
        DayStatus.ALL_PAID -> Color(0xFF4CAF50)
        null -> Color.Transparent
    }
    val bgColor by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
        animationSpec = tween(200), label = "dayBg"
    )

    Column(
        modifier = modifier
            .clip(CircleShape)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            day.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
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

private fun buildDayStatusMap(
    payments: List<PaymentWithItemInfo>,
    year: Int,
    month: Int,
    now: Long,
    sevenDaysLater: Long
): Map<Int, DayStatus> {
    val cal = Calendar.getInstance()
    val dayPayments = mutableMapOf<Int, MutableList<PaymentWithItemInfo>>()
    payments.forEach { p ->
        cal.timeInMillis = p.dueDate
        if (cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month) {
            val day = cal.get(Calendar.DAY_OF_MONTH)
            dayPayments.getOrPut(day) { mutableListOf() }.add(p)
        }
    }
    return dayPayments.mapValues { (_, list) ->
        val allPaid = list.all { it.isPaid }
        if (allPaid) {
            DayStatus.ALL_PAID
        } else {
            val hasOverdue = list.any { !it.isPaid && it.dueDate < now }
            val hasUpcoming = list.any { !it.isPaid && it.dueDate in now..sevenDaysLater }
            when {
                hasOverdue -> DayStatus.OVERDUE
                hasUpcoming -> DayStatus.UPCOMING
                else -> DayStatus.UNPAID
            }
        }
    }
}

private fun getPaymentsForDay(
    payments: List<PaymentWithItemInfo>,
    year: Int,
    month: Int,
    day: Int
): List<PaymentWithItemInfo> {
    val cal = Calendar.getInstance()
    return payments.filter { p ->
        cal.timeInMillis = p.dueDate
        cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month && cal.get(Calendar.DAY_OF_MONTH) == day
    }
}
