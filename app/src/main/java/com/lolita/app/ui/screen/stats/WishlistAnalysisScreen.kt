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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.ItemPriority
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.PriceRepository
import com.lolita.app.di.AppModule
import com.lolita.app.ui.component.chart.DonutChart
import com.lolita.app.ui.component.chart.PieChartData
import com.lolita.app.ui.component.chart.StatsProgressBar
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.screen.common.ShimmerRect
import com.lolita.app.ui.screen.common.ShimmerLine
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import com.valentinilk.shimmer.ShimmerBounds
import com.valentinilk.shimmer.rememberShimmer
import com.valentinilk.shimmer.shimmer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class PriorityDetail(
    val priorityLabel: String,
    val itemCount: Int,
    val budget: Double
)

data class WishlistAnalysisUiState(
    val totalBudget: Double = 0.0,
    val ownedCount: Int = 0,
    val wishedCount: Int = 0,
    val fulfillmentRate: Double = 0.0,
    val priorityChartData: List<PieChartData> = emptyList(),
    val priorityDetails: List<PriorityDetail> = emptyList(),
    val selectedPriorityIndex: Int = -1,
    val showTotalPrice: Boolean = false,
    val isLoading: Boolean = true
)

class WishlistAnalysisViewModel(
    private val priceRepository: PriceRepository = AppModule.priceRepository(),
    private val itemRepository: ItemRepository = AppModule.itemRepository(),
    private val appPreferences: AppPreferences = AppModule.appPreferences()
) : ViewModel() {

    private val _uiState = MutableStateFlow(WishlistAnalysisUiState())
    val uiState: StateFlow<WishlistAnalysisUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                priceRepository.getWishlistTotalBudget(),
                itemRepository.getOwnedCount(),
                itemRepository.getWishedCount(),
                priceRepository.getWishlistByPriorityStats()
            ) { totalBudget, ownedCount, wishedCount, priorityStats ->
                val total = ownedCount + wishedCount
                val fulfillmentRate = if (total > 0) ownedCount.toDouble() / total else 0.0

                val chartData = priorityStats.map { stat ->
                    PieChartData(
                        label = mapPriorityLabel(stat.priority),
                        value = stat.itemCount.toDouble(),
                        color = Color.Unspecified
                    )
                }
                val details = priorityStats.map { stat ->
                    PriorityDetail(
                        priorityLabel = mapPriorityLabel(stat.priority),
                        itemCount = stat.itemCount,
                        budget = stat.totalBudget
                    )
                }
                WishlistAnalysisUiState(
                    totalBudget = totalBudget,
                    ownedCount = ownedCount,
                    wishedCount = wishedCount,
                    fulfillmentRate = fulfillmentRate,
                    priorityChartData = chartData,
                    priorityDetails = details,
                    isLoading = false
                )
            }.combine(appPreferences.showTotalPrice) { state, showPrice ->
                state.copy(showTotalPrice = showPrice)
            }.collect { _uiState.value = it }
        }
    }

    fun selectPriority(index: Int) {
        _uiState.value = _uiState.value.copy(
            selectedPriorityIndex = if (_uiState.value.selectedPriorityIndex == index) -1 else index
        )
    }

    private fun mapPriorityLabel(priority: String): String = when (priority) {
        "HIGH" -> "高优先级"
        "MEDIUM" -> "中优先级"
        "LOW" -> "低优先级"
        else -> priority
    }

}

@Composable
fun WishlistAnalysisContent(
    viewModel: WishlistAnalysisViewModel = viewModel(),
    modifier: Modifier = Modifier,
    onNavigateToFilteredList: (filterType: String, filterValue: String, title: String) -> Unit = { _, _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isLoading) {
        val shimmer = rememberShimmer(ShimmerBounds.Window)
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Budget card skeleton
            ShimmerRect(
                width = 400.dp,
                height = 80.dp,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().shimmer(shimmer)
            )
            // Progress bar skeleton
            ShimmerRect(
                width = 400.dp,
                height = 24.dp,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().shimmer(shimmer)
            )
            // Chart skeleton
            ShimmerRect(
                width = 180.dp,
                height = 200.dp,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().shimmer(shimmer)
            )
            // Priority detail rows skeleton
            repeat(3) {
                ShimmerLine(widthFraction = 0.8f, height = 16.dp, modifier = Modifier.shimmer(shimmer))
            }
        }
        return
    }

    if (uiState.wishedCount == 0 && uiState.ownedCount == 0) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "暂无数据",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val budgetAccent = MaterialTheme.colorScheme.primary
        // Budget card
        LolitaCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SkinIcon(
                    key = IconKey.AttachMoney,
                    tint = budgetAccent,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = "愿望单预算",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (uiState.showTotalPrice) {
                        Text(
                            text = "¥${String.format("%.2f", uiState.totalBudget)}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = budgetAccent
                        )
                    } else {
                        Text(
                            text = "¥***",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = budgetAccent
                        )
                    }
                }
            }
        }

        // Fulfillment progress bar
        StatsProgressBar(
            current = uiState.ownedCount.toDouble(),
            total = (uiState.ownedCount + uiState.wishedCount).toDouble(),
            label = "已实现 (${uiState.ownedCount}/${uiState.ownedCount + uiState.wishedCount})",
            modifier = Modifier.fillMaxWidth()
        )

        // Priority distribution donut chart
        if (uiState.priorityChartData.isNotEmpty()) {
            val chartData = uiState.priorityChartData.map { data ->
                val priority = when (data.label) {
                    "高优先级" -> ItemPriority.HIGH
                    "中优先级" -> ItemPriority.MEDIUM
                    "低优先级" -> ItemPriority.LOW
                    else -> ItemPriority.LOW
                }
                data.copy(color = mapPriorityColor(priority))
            }
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                DonutChart(
                    data = chartData,
                    modifier = Modifier.size(180.dp),
                    centerText = "${uiState.wishedCount}件",
                    selectedIndex = uiState.selectedPriorityIndex,
                    onSliceClick = { viewModel.selectPriority(it) }
                )
            }

            // Priority details list
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                uiState.priorityDetails.forEachIndexed { index, detail ->
                    val priorityValue = when (detail.priorityLabel) {
                        "高优先级" -> "HIGH"
                        "中优先级" -> "MEDIUM"
                        "低优先级" -> "LOW"
                        else -> ""
                    }
                    val priorityEnum = when (detail.priorityLabel) {
                        "高优先级" -> ItemPriority.HIGH
                        "中优先级" -> ItemPriority.MEDIUM
                        "低优先级" -> ItemPriority.LOW
                        else -> ItemPriority.LOW
                    }
                    PriorityDetailRow(
                        detail = detail,
                        color = mapPriorityColor(priorityEnum),
                        showBudget = uiState.showTotalPrice,
                        onClick = { onNavigateToFilteredList("priority", priorityValue, detail.priorityLabel) }
                    )
                }
            }
        }
    }
}

@Composable
private fun mapPriorityColor(priority: ItemPriority): Color {
    return when (priority) {
        ItemPriority.HIGH -> MaterialTheme.colorScheme.primary
        ItemPriority.MEDIUM -> MaterialTheme.colorScheme.tertiary
        ItemPriority.LOW -> MaterialTheme.colorScheme.secondary
    }
}

@Composable
private fun PriorityDetailRow(
    detail: PriorityDetail,
    color: Color,
    showBudget: Boolean,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .skinClickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = detail.priorityLabel,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "${detail.itemCount}件",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (showBudget) {
                Text(
                    text = "¥${String.format("%.2f", detail.budget)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
    }
}
