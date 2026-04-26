package com.lolita.app.ui.screen.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.data.repository.CoordinateRepository
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.OutfitLogRepository
import com.lolita.app.data.repository.PriceRepository

import com.lolita.app.data.local.entity.BrandItemCount
import com.lolita.app.data.local.entity.ItemWithSpending
import coil.compose.AsyncImage
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.screen.common.SectionHeader
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class StatsUiState(
    val ownedCount: Int = 0,
    val wishedCount: Int = 0,
    val coordinateCount: Int = 0,
    val outfitLogCount: Int = 0,
    val totalSpending: Double = 0.0,
    val showTotalPrice: Boolean = false,
    val isLoading: Boolean = true,
    val averagePrice: Double = 0.0,
    val mostExpensiveItem: ItemWithSpending? = null,
    val topBrands: List<BrandItemCount> = emptyList()
)

class StatsViewModel(
    private val itemRepository: ItemRepository = com.lolita.app.di.AppModule.itemRepository(),
    private val coordinateRepository: CoordinateRepository = com.lolita.app.di.AppModule.coordinateRepository(),
    private val outfitLogRepository: OutfitLogRepository = com.lolita.app.di.AppModule.outfitLogRepository(),
    private val priceRepository: PriceRepository = com.lolita.app.di.AppModule.priceRepository(),
    private val appPreferences: AppPreferences = com.lolita.app.di.AppModule.appPreferences()
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            combine(
                itemRepository.getItemsByStatus(ItemStatus.OWNED),
                itemRepository.getItemsByStatus(ItemStatus.WISHED),
                coordinateRepository.getCoordinateCount(),
                outfitLogRepository.getAllOutfitLogs(),
                priceRepository.getTotalSpending()
            ) { owned, wished, coordCount, logs, spending ->
                StatsUiState(
                    ownedCount = owned.size,
                    wishedCount = wished.size,
                    coordinateCount = coordCount,
                    outfitLogCount = logs.size,
                    totalSpending = spending,
                    averagePrice = if (owned.isNotEmpty()) spending / owned.size else 0.0,
                    isLoading = false
                )
            }.combine(appPreferences.showTotalPrice) { state, showPrice ->
                state.copy(showTotalPrice = showPrice)
            }.combine(priceRepository.getMostExpensiveItem()) { state, item ->
                state.copy(mostExpensiveItem = item)
            }.combine(itemRepository.getTopBrandsByCount()) { state, brands ->
                state.copy(topBrands = brands)
            }.collect { _uiState.value = it }
        }
    }
}

@Composable
fun StatsContent(
    viewModel: StatsViewModel = viewModel(),
    modifier: Modifier = Modifier,
    onNavigateToFilteredList: (filterType: String, filterValue: String, title: String) -> Unit = { _, _, _ -> },
    onNavigateToItemDetail: (Long) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val iconTint = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "已拥有",
                targetValue = uiState.ownedCount,
                iconKey = IconKey.Home,
                iconTint = iconTint,
                modifier = Modifier.weight(1f),
                onClick = { onNavigateToFilteredList("status_owned", "", "已拥有") }
            )
            StatCard(
                title = "愿望单",
                targetValue = uiState.wishedCount,
                iconKey = IconKey.Wishlist,
                iconTint = iconTint,
                modifier = Modifier.weight(1f),
                onClick = { onNavigateToFilteredList("status_wished", "", "愿望单") }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "套装",
                targetValue = uiState.coordinateCount,
                iconKey = IconKey.Star,
                iconTint = iconTint,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "穿搭记录",
                targetValue = uiState.outfitLogCount,
                iconKey = IconKey.CalendarMonth,
                iconTint = iconTint,
                modifier = Modifier.weight(1f)
            )
        }

        if (uiState.showTotalPrice) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
            SectionHeader(title = "消费概览")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SpendingCard(
                    title = "总消费",
                    targetValue = uiState.totalSpending,
                    iconKey = IconKey.AttachMoney,
                    iconTint = iconTint,
                    modifier = Modifier.weight(1f)
                )
                if (uiState.ownedCount > 0) {
                    SpendingCard(
                        title = "单品均价",
                        targetValue = uiState.averagePrice,
                        iconKey = IconKey.AttachMoney,
                        iconTint = iconTint,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (uiState.showTotalPrice && uiState.mostExpensiveItem != null) {
            val item = uiState.mostExpensiveItem!!
            LolitaCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigateToItemDetail(item.itemId) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (item.imageUrls.isNotEmpty()) {
                        AsyncImage(
                            model = item.imageUrls.first(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "最贵单品",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.itemName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = "¥${String.format("%.2f", item.totalSpending)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (uiState.topBrands.isNotEmpty()) {
            LolitaCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    SectionHeader(title = "品牌 Top 5")
                    Spacer(modifier = Modifier.height(8.dp))
                    val maxCount = uiState.topBrands.maxOfOrNull { it.itemCount } ?: 1
                    uiState.topBrands.forEachIndexed { index, brand ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onNavigateToFilteredList("brand", brand.brandName, "品牌: ${brand.brandName}") }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(20.dp)
                            )
                            Text(
                                text = brand.brandName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.width(72.dp),
                                maxLines = 1
                            )
                            LinearProgressIndicator(
                                progress = { brand.itemCount.toFloat() / maxCount },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                            Text(
                                text = "${brand.itemCount}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.width(28.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    targetValue: Int,
    iconKey: IconKey,
    iconTint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val animatedValue by animateIntAsState(
        targetValue = targetValue,
        animationSpec = tween(durationMillis = 800),
        label = "statCount"
    )

    LolitaCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
            SkinIcon(
                key = iconKey,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = animatedValue.toString(),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SpendingCard(
    title: String,
    targetValue: Double,
    iconKey: IconKey,
    iconTint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val animatedValue by animateFloatAsState(
        targetValue = targetValue.toFloat(),
        animationSpec = tween(durationMillis = 800),
        label = "spendingAmount"
    )

    LolitaCard(modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
            SkinIcon(
                key = iconKey,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "¥${String.format("%.2f", animatedValue)}",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
