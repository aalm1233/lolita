package com.lolita.app.ui.screen.stats

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.data.repository.CoordinateRepository
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.OutfitLogRepository
import com.lolita.app.data.repository.PriceRepository
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.screen.item.PurchaseReviewShortcut
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.rememberSkinFlingBehavior
import com.lolita.app.ui.theme.skin.component.skinClickable
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
    val allRecordedSpending: Double = 0.0,
    val incompleteCurrentOrderCount: Int = 0,
    val showTotalPrice: Boolean = false,
    val isLoading: Boolean = true,
    val averagePrice: Double = 0.0,
    val mostExpensiveItem: ItemWithSpending? = null,
    val topBrands: List<BrandItemCount> = emptyList(),
    val purchaseReviews: List<ItemPurchaseReview> = emptyList()
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
                    averagePrice = 0.0,
                    isLoading = false
                )
            }.combine(priceRepository.getAllRecordedSpending()) { state, allRecorded ->
                state.copy(allRecordedSpending = allRecorded)
            }.combine(priceRepository.getIncompleteCurrentOrderCount()) { state, incompleteCount ->
                state.copy(incompleteCurrentOrderCount = incompleteCount)
            }.combine(itemRepository.getPurchaseReviews()) { state, reviews ->
                state.copy(purchaseReviews = reviews)
            }.combine(priceRepository.getAverageOwnedPrice()) { state, average ->
                state.copy(averagePrice = average)
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
    val skin = LolitaSkin.current
    if (uiState.isLoading) {
        StatsOverviewSkeleton(modifier)
        return
    }

    Column(
        modifier = modifier.fillMaxSize()
            .verticalScroll(rememberScrollState(), flingBehavior = rememberSkinFlingBehavior())
            .padding(skin.spacingLarge),
        verticalArrangement = Arrangement.spacedBy(skin.cardGap)
    ) {
        PurchaseReviewShortcut(
            reviewCount = uiState.purchaseReviews.size,
            onClick = { onNavigateToFilteredList("status_review", "", "待核对购买资料") },
            modifier = Modifier.fillMaxWidth()
        )
        LolitaSection("衣橱概况") {
            row { WardrobeCounts(uiState, onNavigateToFilteredList) }
        }
        if (uiState.showTotalPrice) {
            SpendingOverview(uiState)
            uiState.mostExpensiveItem?.let { item ->
                MostExpensiveOrder(item, onClick = { onNavigateToItemDetail(item.itemId) })
            }
        }
        if (uiState.topBrands.isNotEmpty()) {
            OwnedBrands(uiState.topBrands, onNavigateToFilteredList)
        }
    }
}

private data class OverviewCount(val title: String, val value: Int, val onClick: (() -> Unit)? = null)

@Composable
private fun WardrobeCounts(
    state: StatsUiState,
    onNavigate: (String, String, String) -> Unit
) {
    val skin = LolitaSkin.current
    val counts = listOf(
        OverviewCount("已拥有", state.ownedCount) { onNavigate("status_owned", "", "已拥有") },
        OverviewCount("愿望单", state.wishedCount) { onNavigate("status_wished", "", "愿望单") },
        OverviewCount("套装", state.coordinateCount),
        OverviewCount("穿搭记录", state.outfitLogCount)
    )
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 280.dp && fontScale <= 1.3f) 4 else 2
        Column(verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
            counts.chunked(columns).forEach { group ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                    group.forEach { count ->
                        Column(
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(skin.cornerRadiusSmall))
                                .then(if (count.onClick != null) Modifier.skinClickable(onClick = count.onClick) else Modifier)
                                .semantics(mergeDescendants = true) {}
                                .padding(vertical = skin.spacingMedium)
                                .heightIn(min = 48.dp),
                            verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)
                        ) {
                            OverviewNumber("%,d".format(count.value), MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.fillMaxWidth())
                            Text(count.title, style = MaterialTheme.typography.bodySmall,
                                color = if (count.onClick != null) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpendingOverview(state: StatsUiState) {
    val skin = LolitaSkin.current
    LolitaSection("消费概览") {
        row {
            Column(Modifier.fillMaxWidth().padding(vertical = skin.spacingMedium),
                verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                Text("当前订单实付", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OverviewNumber(overviewMoney(state.totalSpending), MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary)
                Text("当前绑定订单的已录实付", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        row {
            SpendingDetail("全部已录实付", state.allRecordedSpending, "包含归档订单，只累计已保存的真实付款")
        }
        if (state.ownedCount > 0) row {
            SpendingDetail("订单参考均价", state.averagePrice, "已拥有服饰的当前订单总价均值，不代表实付均价")
        }
        if (state.purchaseReviews.isNotEmpty() && state.incompleteCurrentOrderCount > 0) row {
            Column(Modifier.padding(vertical = skin.spacingMedium),
                verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                Text("付款资料待补 · ${state.incompleteCurrentOrderCount} 笔当前订单",
                    style = MaterialTheme.typography.labelLarge)
                Text("清单按服饰与小物计件，此处按订单计笔；缺失金额为未知，不据此认定欠款。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SpendingDetail(title: String, amount: Double, description: String) {
    val skin = LolitaSkin.current
    val labelStyle = MaterialTheme.typography.bodyMedium
    val amountStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    val text = overviewMoney(amount)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    Column(Modifier.fillMaxWidth().padding(vertical = skin.spacingMedium),
        verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val requiredWidth = measurer.measure(title, labelStyle, softWrap = false).size.width +
                measurer.measure(text, amountStyle, softWrap = false).size.width +
                with(density) { skin.spacingLarge.roundToPx() }
            if (requiredWidth <= with(density) { maxWidth.roundToPx() }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(title, style = labelStyle)
                    Text(text, style = amountStyle)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                    Text(title, style = labelStyle)
                    OverviewNumber(text, amountStyle)
                }
            }
        }
        Text(description, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun overviewMoney(value: Double): String = "¥%,.2f".format(value)

/** Fit within the available width while retaining the exact value and large-font wrapping. */
@Composable
private fun OverviewNumber(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val numberStyle = style.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
    BoxWithConstraints(modifier) {
        val width = with(density) { maxWidth.roundToPx() }
        var fontSize = numberStyle.fontSize
        while (fontSize.value > 18f &&
            measurer.measure(text, numberStyle.copy(fontSize = fontSize), softWrap = false).size.width > width) {
            fontSize = (fontSize.value - 2f).coerceAtLeast(18f).sp
        }
        Text(text, style = numberStyle.copy(fontSize = fontSize), color = color,
            modifier = Modifier.fillMaxWidth(), softWrap = true)
    }
}

@Composable
private fun MostExpensiveOrder(item: ItemWithSpending, onClick: () -> Unit) {
    val skin = LolitaSkin.current
    LolitaCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
            SectionHeader("当前订单实付最高")
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(skin.cornerRadiusSmall))
                    .background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    if (item.imageUrls.isNotEmpty()) {
                        LolitaShimmerImage(model = item.imageUrls.first(), contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                            placeholderInitial = item.itemName.firstOrNull()?.toString())
                    } else SkinIcon(IconKey.Outfit, modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Text(item.itemName, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                SkinIcon(IconKey.ArrowForward, modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OverviewNumber(overviewMoney(item.totalSpending), MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun OwnedBrands(brands: List<BrandItemCount>, onNavigate: (String, String, String) -> Unit) {
    val skin = LolitaSkin.current
    val maxCount = (brands.maxOfOrNull { it.itemCount } ?: 1).coerceAtLeast(1)
    LolitaSection("已拥有品牌 · Top 5") {
        brands.forEachIndexed { index, brand ->
            row {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(skin.cornerRadiusSmall))
                    .skinClickable { onNavigate("brand", brand.brandName, "品牌: ${brand.brandName}") }
                    .semantics(mergeDescendants = true) {}
                    .padding(vertical = skin.spacingMedium).heightIn(min = 48.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = skin.spacingSmall))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                        Row(verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                            Text(brand.brandName, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f))
                            Text("${brand.itemCount} 件", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LinearProgressIndicator(progress = { brand.itemCount.toFloat() / maxCount },
                            modifier = Modifier.fillMaxWidth().height(skin.spacingSmall)
                                .clip(RoundedCornerShape(skin.cornerRadiusSmall)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsOverviewSkeleton(modifier: Modifier = Modifier) {
    val skin = LolitaSkin.current
    val fontScale = LocalDensity.current.fontScale
    Column(modifier.fillMaxSize().padding(skin.spacingLarge),
        verticalArrangement = Arrangement.spacedBy(skin.cardGap)) {
        LolitaCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(skin.spacingLarge)) {
                ShimmerLine(0.35f)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val columns = if (maxWidth >= 280.dp && fontScale <= 1.3f) 4 else 2
                    Column(verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                        repeat(4 / columns) {
                            Row(horizontalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                                repeat(columns) {
                                    Column(Modifier.weight(1f).padding(vertical = skin.spacingMedium),
                                        verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                                        ShimmerLine(0.6f, height = 28.dp)
                                        ShimmerLine(0.9f, height = 12.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        LolitaCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(skin.spacingLarge)) {
                ShimmerLine(0.35f)
                ShimmerLine(0.7f, height = 36.dp)
                repeat(2) { ShimmerLine(0.9f) }
            }
        }
    }
}
