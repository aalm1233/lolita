package com.lolita.app.ui.screen.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.data.repository.PriceRepository
import com.lolita.app.di.AppModule
import com.lolita.app.ui.component.chart.ChartPalette
import com.lolita.app.ui.component.chart.chartPalette
import com.lolita.app.ui.component.chart.DonutChart
import com.lolita.app.ui.component.chart.PieChartData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class SpendingDimension(val label: String) {
    BRAND("品牌"), CATEGORY("分类"), STYLE("风格"), SEASON("季节")
}

data class SpendingRankItem(
    val name: String,
    val amount: Double,
    val percentage: Double
)

data class SpendingDistributionUiState(
    val dimension: SpendingDimension = SpendingDimension.BRAND,
    val chartData: List<PieChartData> = emptyList(),
    val rankingList: List<SpendingRankItem> = emptyList(),
    val totalSpending: Double = 0.0,
    val selectedIndex: Int = -1,
    val showTotalPrice: Boolean = false,
    val isLoading: Boolean = true
)

class SpendingDistributionViewModel(
    private val priceRepository: PriceRepository = AppModule.priceRepository(),
    private val appPreferences: AppPreferences = AppModule.appPreferences()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpendingDistributionUiState())
    val uiState: StateFlow<SpendingDistributionUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val dimension = _uiState.value.dimension
            val spendingFlow = when (dimension) {
                SpendingDimension.BRAND -> priceRepository.getSpendingByBrand()
                    .map { list -> list.map { it.name to it.totalSpending } }
                SpendingDimension.CATEGORY -> priceRepository.getSpendingByCategory()
                    .map { list -> list.map { it.name to it.totalSpending } }
                SpendingDimension.STYLE -> priceRepository.getSpendingByStyle()
                    .map { list -> list.map { it.style to it.totalSpending } }
                SpendingDimension.SEASON -> priceRepository.getSpendingBySeasonRaw()
                    .map { list ->
                        val seasonMap = mutableMapOf<String, Double>()
                        list.forEach { item ->
                            item.style.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                .forEach { season ->
                                    seasonMap[season] = (seasonMap[season] ?: 0.0) + item.totalSpending
                                }
                        }
                        seasonMap.entries.sortedByDescending { it.value }.map { it.key to it.value }
                    }
            }

            spendingFlow.combine(appPreferences.showTotalPrice) { items, showPrice ->
                buildState(dimension, items, showPrice)
            }.collect { _uiState.value = it }
        }
    }

    private fun buildState(
        dimension: SpendingDimension,
        items: List<Pair<String, Double>>,
        showPrice: Boolean
    ): SpendingDistributionUiState {
        val total = items.sumOf { it.second }
        val top10 = items.take(10)
        val rest = items.drop(10)
        val grouped = if (rest.isNotEmpty()) {
            top10 + ("其他" to rest.sumOf { it.second })
        } else {
            top10
        }
        val chartData = grouped.mapIndexed { index, (label, value) ->
            PieChartData(
                label = label,
                value = value,
                color = ChartPalette[index % ChartPalette.size]
            )
        }
        val rankingList = grouped.map { (name, amount) ->
            SpendingRankItem(
                name = name,
                amount = amount,
                percentage = if (total > 0) amount / total * 100.0 else 0.0
            )
        }
        return SpendingDistributionUiState(
            dimension = dimension,
            chartData = chartData,
            rankingList = rankingList,
            totalSpending = total,
            selectedIndex = -1,
            showTotalPrice = showPrice,
            isLoading = false
        )
    }

    fun switchDimension(dimension: SpendingDimension) {
        _uiState.value = _uiState.value.copy(dimension = dimension, isLoading = true)
        loadData()
    }

    fun selectSlice(index: Int) {
        _uiState.value = _uiState.value.copy(
            selectedIndex = if (_uiState.value.selectedIndex == index) -1 else index
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpendingDistributionContent(
    viewModel: SpendingDistributionViewModel = viewModel(),
    modifier: Modifier = Modifier,
    onNavigateToFilteredList: (filterType: String, filterValue: String, title: String) -> Unit = { _, _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SpendingDimension.entries.forEach { dimension ->
                FilterChip(
                    selected = uiState.dimension == dimension,
                    onClick = { viewModel.switchDimension(dimension) },
                    label = { Text(dimension.label) }
                )
            }
        }

        if (!uiState.showTotalPrice) {
            Text(
                text = "开启价格显示以查看消费分析",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
            )
        } else if (uiState.chartData.isEmpty() && !uiState.isLoading) {
            Text(
                text = "暂无数据",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
            )
        } else if (uiState.showTotalPrice && uiState.chartData.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                DonutChart(
                    data = uiState.chartData,
                    modifier = Modifier.size(200.dp),
                    centerText = "¥${String.format("%.0f", uiState.totalSpending)}",
                    selectedIndex = uiState.selectedIndex,
                    onSliceClick = { viewModel.selectSlice(it) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val filterType = when (uiState.dimension) {
                SpendingDimension.BRAND -> "brand"
                SpendingDimension.CATEGORY -> "category"
                SpendingDimension.STYLE -> "style"
                SpendingDimension.SEASON -> "season"
            }
            val dimensionLabel = uiState.dimension.label

            uiState.rankingList.forEachIndexed { index, item ->
                val palette = chartPalette()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            if (item.name != "其他") {
                                onNavigateToFilteredList(filterType, item.name, "$dimensionLabel: ${item.name}")
                            }
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                if (index < uiState.chartData.size) uiState.chartData[index].color
                                else palette[index % palette.size]
                            )
                    )
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "¥${String.format("%.0f", item.amount)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${String.format("%.1f", item.percentage)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}