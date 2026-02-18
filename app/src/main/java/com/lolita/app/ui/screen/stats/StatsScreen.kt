package com.lolita.app.ui.screen.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextAlign
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
fun StatsContent(viewModel: StatsViewModel = viewModel(), modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "已拥有",
                targetValue = uiState.ownedCount,
                icon = Icons.Default.Home,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "愿望单",
                targetValue = uiState.wishedCount,
                icon = Icons.Default.Favorite,
                color = Color(0xFFFF6B6B),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "套装",
                targetValue = uiState.coordinateCount,
                icon = Icons.Default.Star,
                color = Color(0xFFFFD93D),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "穿搭记录",
                targetValue = uiState.outfitLogCount,
                icon = Icons.Default.DateRange,
                color = Color(0xFF6BCF7F),
                modifier = Modifier.weight(1f)
            )
        }
        if (uiState.showTotalPrice) {
            SpendingCard(
                title = "总消费",
                targetValue = uiState.totalSpending,
                icon = Icons.Default.ShoppingCart,
                color = Color(0xFFE91E8C),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Average price card
        if (uiState.showTotalPrice && uiState.ownedCount > 0) {
            SpendingCard(
                title = "单品均价",
                targetValue = uiState.averagePrice,
                icon = Icons.Default.ShoppingCart,
                color = Color(0xFFFF91A4),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Most expensive item
        if (uiState.showTotalPrice && uiState.mostExpensiveItem != null) {
            val item = uiState.mostExpensiveItem!!
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFF1493).copy(alpha = 0.1f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (item.imageUrl != null) {
                        AsyncImage(
                            model = item.imageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "最贵单品",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = item.itemName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "¥${String.format("%.2f", item.totalSpending)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF1493)
                    )
                }
            }
        }
        // Brand Top 5
        if (uiState.topBrands.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "品牌 Top 5",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    val maxCount = uiState.topBrands.maxOfOrNull { it.itemCount } ?: 1
                    uiState.topBrands.forEach { brand ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = brand.brandName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.width(80.dp),
                                maxLines = 1
                            )
                            LinearProgressIndicator(
                                progress = { brand.itemCount.toFloat() / maxCount },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            )
                            Text(
                                text = "${brand.itemCount}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(32.dp),
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
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    val animatedValue by animateIntAsState(
        targetValue = targetValue,
        animationSpec = tween(durationMillis = 800),
        label = "statCount"
    )

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = animatedValue.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun SpendingCard(
    title: String,
    targetValue: Double,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    val animatedValue by animateFloatAsState(
        targetValue = targetValue.toFloat(),
        animationSpec = tween(durationMillis = 800),
        label = "spendingAmount"
    )

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "¥${String.format("%.2f", animatedValue)}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
