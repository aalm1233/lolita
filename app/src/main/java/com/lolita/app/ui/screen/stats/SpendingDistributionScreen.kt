package com.lolita.app.ui.screen.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.lolita.app.ui.theme.skin.component.skinClickable
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
import com.lolita.app.data.repository.PriceRepository
import com.lolita.app.di.AppModule
import com.lolita.app.domain.usecase.OrderAccounting
import com.lolita.app.domain.usecase.SpendingBreakdown
import com.lolita.app.domain.usecase.SpendingBucket
import com.lolita.app.ui.component.chart.ChartPalette
import com.lolita.app.ui.component.chart.chartPalette
import com.lolita.app.ui.component.chart.DonutChart
import com.lolita.app.ui.component.chart.PieChartData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class SpendingDimension(val label: String) {
    BRAND("品牌"), CATEGORY("分类"), STYLE("风格"), SEASON("季节")
}

data class SpendingRankItem(
    val name: String,
    val amount: Double,
    val percentage: Double,
    val filterValue: String? = name
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

@OptIn(ExperimentalCoroutinesApi::class)
class SpendingDistributionViewModel(
    private val priceRepository: PriceRepository = AppModule.priceRepository(),
    private val showTotalPrice: Flow<Boolean> = AppModule.appPreferences().showTotalPrice
) : ViewModel() {
    private val selectedDimension = MutableStateFlow(SpendingDimension.BRAND)
    private val selectedSliceIndex = MutableStateFlow(-1)
    private val _uiState = MutableStateFlow(SpendingDistributionUiState())
    val uiState: StateFlow<SpendingDistributionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            selectedDimension.flatMapLatest { dimension ->
                combine(spendingByDimension(dimension), showTotalPrice, selectedSliceIndex) { items, showPrice, index ->
                    buildState(dimension, items, showPrice, index)
                }
            }.collect { state ->
                if (state.dimension == selectedDimension.value) _uiState.value = state
            }
        }
    }

    private fun spendingByDimension(dimension: SpendingDimension): Flow<List<SpendingBucket>> = when (dimension) {
        SpendingDimension.BRAND -> priceRepository.getSpendingByBrand().map { rows ->
            SpendingBreakdown.singleLabel(rows.map { it.name to it.totalSpending }, "未设置品牌")
        }
        SpendingDimension.CATEGORY -> priceRepository.getSpendingByCategory().map { rows ->
            SpendingBreakdown.singleLabel(rows.map { it.name to it.totalSpending }, "未设置分类")
        }
        SpendingDimension.STYLE -> priceRepository.getSpendingByStyle().map { rows ->
            SpendingBreakdown.singleLabel(rows.map { it.style to it.totalSpending }, "未设置风格")
        }
        SpendingDimension.SEASON -> priceRepository.getSpendingBySeasonRaw().map { rows ->
            SpendingBreakdown.seasons(rows.map { it.style to it.totalSpending })
        }
    }

    private fun buildState(
        dimension: SpendingDimension,
        items: List<SpendingBucket>,
        showPrice: Boolean,
        selectedIndex: Int
    ): SpendingDistributionUiState {
        val total = OrderAccounting.sum(items.map { it.amount })
        val top10 = items.take(10)
        val rest = items.drop(10)
        val grouped = if (rest.isNotEmpty()) {
            top10 + SpendingBucket("其他", OrderAccounting.sum(rest.map { it.amount }), null)
        } else top10
        return SpendingDistributionUiState(
            dimension = dimension,
            chartData = grouped.mapIndexed { index, item ->
                PieChartData(item.name, item.amount, ChartPalette[index % ChartPalette.size])
            },
            rankingList = grouped.map { item ->
                SpendingRankItem(item.name, item.amount, if (total > 0) item.amount / total * 100.0 else 0.0, item.filterValue)
            },
            totalSpending = total,
            selectedIndex = selectedIndex.takeIf { it in grouped.indices } ?: -1,
            showTotalPrice = showPrice,
            isLoading = false
        )
    }

    fun switchDimension(dimension: SpendingDimension) {
        if (selectedDimension.value == dimension) return
        selectedDimension.value = dimension
        _uiState.value = SpendingDistributionUiState(dimension = dimension, showTotalPrice = _uiState.value.showTotalPrice)
        selectedSliceIndex.value = -1
    }

    fun selectSlice(index: Int) {
        selectedSliceIndex.value = if (selectedSliceIndex.value == index) -1 else index
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
                text = "暂无实际付款",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
            )
        } else if (uiState.showTotalPrice && uiState.chartData.isNotEmpty()) {
            if (uiState.dimension == SpendingDimension.SEASON) {
                Text(
                    text = "多季节服饰的消费按季节均分，合计不重复",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                DonutChart(
                    data = uiState.chartData,
                    modifier = Modifier.size(200.dp),
                    centerText = "¥${String.format("%.2f", uiState.totalSpending)}",
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
                        .skinClickable {
                            if (item.filterValue != null) {
                                onNavigateToFilteredList(filterType, item.filterValue, "$dimensionLabel: ${item.name}")
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
                        text = "¥${String.format("%.2f", item.amount)}",
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