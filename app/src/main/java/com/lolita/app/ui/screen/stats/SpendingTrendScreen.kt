package com.lolita.app.ui.screen.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.MonthlySpending
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.data.repository.PriceRepository
import com.lolita.app.di.AppModule
import com.lolita.app.ui.component.chart.LineChart
import com.lolita.app.ui.component.chart.LineChartData
import com.lolita.app.ui.theme.Pink30
import com.lolita.app.ui.theme.Pink400
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    val selectedMonthIndex: Int = -1,
    val monthlyDetails: List<MonthDetail> = emptyList(),
    val showTotalPrice: Boolean = false,
    val isLoading: Boolean = true
)

class SpendingTrendViewModel(
    private val priceRepository: PriceRepository = AppModule.priceRepository(),
    private val appPreferences: AppPreferences = AppModule.appPreferences()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpendingTrendUiState())
    val uiState: StateFlow<SpendingTrendUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            priceRepository.getMonthlySpending()
                .combine(appPreferences.showTotalPrice) { monthlyList, showPrice ->
                    buildState(monthlyList, showPrice)
                }
                .collect { _uiState.value = it }
        }
    }

    private fun buildState(
        monthlyList: List<MonthlySpending>,
        showPrice: Boolean
    ): SpendingTrendUiState {
        val year = _uiState.value.selectedYear
        val yearPrefix = "$year-"
        val filtered = monthlyList.filter { it.yearMonth.startsWith(yearPrefix) }
            .associate { it.yearMonth to it.totalSpending }

        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR)
        val currentMonth = now.get(Calendar.MONTH) + 1

        val chartData = (1..12).map { m ->
            val key = "$year-${String.format("%02d", m)}"
            LineChartData(
                label = "${m}月",
                value = filtered[key] ?: 0.0
            )
        }

        val yearTotal = chartData.sumOf { it.value }

        val details = (1..12).map { m ->
            val key = "$year-${String.format("%02d", m)}"
            MonthDetail(
                month = "${m}月",
                amount = filtered[key] ?: 0.0,
                isCurrentMonth = year == currentYear && m == currentMonth
            )
        }

        return SpendingTrendUiState(
            selectedYear = year,
            monthlyData = chartData,
            yearTotal = yearTotal,
            selectedMonthIndex = _uiState.value.selectedMonthIndex,
            monthlyDetails = details,
            showTotalPrice = showPrice,
            isLoading = false
        )
    }

    fun previousYear() {
        _uiState.value = _uiState.value.copy(
            selectedYear = _uiState.value.selectedYear - 1,
            selectedMonthIndex = -1
        )
        loadData()
    }

    fun nextYear() {
        _uiState.value = _uiState.value.copy(
            selectedYear = _uiState.value.selectedYear + 1,
            selectedMonthIndex = -1
        )
        loadData()
    }

    fun selectMonth(index: Int) {
        _uiState.value = _uiState.value.copy(
            selectedMonthIndex = if (_uiState.value.selectedMonthIndex == index) -1 else index
        )
    }
}

@Composable
fun SpendingTrendContent(
    viewModel: SpendingTrendViewModel = viewModel(),
    modifier: Modifier = Modifier
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
                Icon(
                    Icons.Default.KeyboardArrowLeft,
                    contentDescription = "上一年",
                    tint = Pink400
                )
            }
            Text(
                text = "${uiState.selectedYear}年",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { viewModel.nextYear() }) {
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = "下一年",
                    tint = Pink400
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "¥${String.format("%.2f", uiState.yearTotal)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Pink400
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (detail.isCurrentMonth) {
                            Modifier.background(
                                Pink30,
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
                    color = if (detail.amount > 0) Pink400 else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

