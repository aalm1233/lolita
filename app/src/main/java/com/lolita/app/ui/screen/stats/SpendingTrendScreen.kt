package com.lolita.app.ui.screen.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.lolita.app.ui.theme.skin.component.skinClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.MonthlySpending
import com.lolita.app.data.repository.PriceRepository
import com.lolita.app.di.AppModule
import com.lolita.app.domain.usecase.OrderAccounting
import com.lolita.app.ui.component.chart.LineChart
import com.lolita.app.ui.component.chart.LineChartData
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class MonthDetail(
    val month: String,
    val amount: Double,
    val isCurrentMonth: Boolean
)

data class SpendingTrendUiState(
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val monthlyData: List<LineChartData> = emptyList(),
    val yearTotal: Double = 0.0,
    val undatedSpending: Double = 0.0,
    val selectedMonthIndex: Int = -1,
    val monthlyDetails: List<MonthDetail> = emptyList(),
    val showTotalPrice: Boolean = false,
    val isLoading: Boolean = true
)

class SpendingTrendViewModel(
    private val priceRepository: PriceRepository = AppModule.priceRepository(),
    private val showTotalPrice: Flow<Boolean> = AppModule.appPreferences().showTotalPrice
) : ViewModel() {
    private data class Selection(val year: Int, val monthIndex: Int = -1)
    private val selection = MutableStateFlow(Selection(Calendar.getInstance().get(Calendar.YEAR)))
    private val _uiState = MutableStateFlow(SpendingTrendUiState())
    val uiState: StateFlow<SpendingTrendUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                priceRepository.getMonthlySpending(),
                priceRepository.getUndatedSpending(),
                showTotalPrice,
                selection
            ) { monthlyList, undated, showPrice, selected ->
                buildState(monthlyList, undated, showPrice, selected.year, selected.monthIndex)
            }.collect { state ->
                if (state.selectedYear == selection.value.year) _uiState.value = state
            }
        }
    }

    private fun buildState(
        monthlyList: List<MonthlySpending>,
        undatedSpending: Double,
        showPrice: Boolean,
        year: Int,
        monthIndex: Int
    ): SpendingTrendUiState {
        val yearPrefix = "$year-"
        val filtered = monthlyList.filter { it.yearMonth.startsWith(yearPrefix) }
            .associate { it.yearMonth to it.totalSpending }
        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR)
        val currentMonth = now.get(Calendar.MONTH) + 1
        val amounts = (1..12).map { month ->
            filtered["$year-${month.toString().padStart(2, '0')}"] ?: 0.0
        }
        return SpendingTrendUiState(
            selectedYear = year,
            monthlyData = amounts.mapIndexed { index, amount -> LineChartData("${index + 1}月", amount) },
            yearTotal = OrderAccounting.sum(amounts),
            undatedSpending = undatedSpending,
            selectedMonthIndex = monthIndex,
            monthlyDetails = amounts.mapIndexed { index, amount ->
                MonthDetail("${index + 1}月", amount, year == currentYear && index + 1 == currentMonth)
            },
            showTotalPrice = showPrice,
            isLoading = false
        )
    }

    fun previousYear() {
        selection.update { it.copy(year = it.year - 1, monthIndex = -1) }
    }

    fun nextYear() {
        selection.update { it.copy(year = it.year + 1, monthIndex = -1) }
    }

    fun selectMonth(index: Int) {
        selection.update { it.copy(monthIndex = if (it.monthIndex == index) -1 else index) }
    }
}

@Composable
fun SpendingTrendContent(
    viewModel: SpendingTrendViewModel = viewModel(),
    modifier: Modifier = Modifier,
    onNavigateToFilteredList: (filterType: String, filterValue: String, title: String) -> Unit = { _, _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()

    if (!uiState.showTotalPrice) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "开启价格显示以查看消费趋势",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Year selector + year total
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.previousYear() }) {
                SkinIcon(
                    key = IconKey.KeyboardArrowLeft,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "${uiState.selectedYear}年",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { viewModel.nextYear() }) {
                SkinIcon(
                    key = IconKey.KeyboardArrowRight,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "¥${String.format("%.2f", uiState.yearTotal)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (uiState.undatedSpending > 0) {
            Text(
                text = "另有 ¥${String.format("%.2f", uiState.undatedSpending)} 实付日期待补，未计入月度趋势",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Line chart
        LineChart(
            data = uiState.monthlyData,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            selectedIndex = uiState.selectedMonthIndex,
            onPointClick = { viewModel.selectMonth(it) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Monthly details list
        uiState.monthlyDetails.forEachIndexed { index, detail ->
            val yearMonth = "${uiState.selectedYear}-${(index + 1).toString().padStart(2, '0')}"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .skinClickable {
                        if (detail.amount > 0) {
                            onNavigateToFilteredList("month", yearMonth, "${uiState.selectedYear}年${detail.month}")
                        }
                    }
                    .then(
                        if (detail.isCurrentMonth) {
                            Modifier.background(
                                MaterialTheme.colorScheme.background,
                                RoundedCornerShape(8.dp)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = detail.month,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (detail.isCurrentMonth) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = "¥${String.format("%.2f", detail.amount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (detail.amount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
