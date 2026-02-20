# Stats Drilldown & Item Click-Through Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make all stats data clickable to drill down into filtered item lists, and make all item displays navigate to item detail.

**Architecture:** New `FilteredItemListScreen` with its own ViewModel that queries items by filter type/value. Stats screens receive navigation callbacks. CoordinateDetailScreen gets item click support.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Navigation Compose, MVVM

---

### Task 1: Add DAO queries for filtered item lookup

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/local/dao/ItemDao.kt`
- Modify: `app/src/main/java/com/lolita/app/data/local/dao/PriceDao.kt`

**Step 1: Add new queries to ItemDao**

Add these queries after line 117 (before the closing `}`):

```kotlin
@Query("SELECT * FROM items WHERE style = :style OR style LIKE '%' || :style || '%' ORDER BY updated_at DESC")
fun getItemsByStyle(style: String): Flow<List<Item>>

@Query("SELECT * FROM items WHERE season = :season OR season LIKE '%' || :season || '%' ORDER BY updated_at DESC")
fun getItemsBySeason(season: String): Flow<List<Item>>

@Query("SELECT * FROM items WHERE status = 'WISHED' AND priority = :priority ORDER BY updated_at DESC")
fun getWishlistByPriorityFilter(priority: ItemPriority): Flow<List<Item>>

@Query("""
    SELECT i.* FROM items i
    INNER JOIN brands b ON i.brand_id = b.id
    WHERE b.name = :brandName
    ORDER BY i.updated_at DESC
""")
fun getItemsByBrandName(brandName: String): Flow<List<Item>>

@Query("""
    SELECT i.* FROM items i
    INNER JOIN categories c ON i.category_id = c.id
    WHERE c.name = :categoryName
    ORDER BY i.updated_at DESC
""")
fun getItemsByCategoryName(categoryName: String): Flow<List<Item>>
```

**Step 2: Add month-based item query to PriceDao**

Add after line 159 (before `deleteAllPrices`):

```kotlin
@Query("""
    SELECT DISTINCT i.* FROM items i
    INNER JOIN prices p ON p.item_id = i.id
    WHERE i.status = 'OWNED'
      AND p.purchase_date IS NOT NULL
      AND strftime('%Y-%m', p.purchase_date / 1000, 'unixepoch') = :yearMonth
    ORDER BY i.updated_at DESC
""")
fun getItemsByPurchaseMonth(yearMonth: String): Flow<List<Item>>
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/local/dao/ItemDao.kt app/src/main/java/com/lolita/app/data/local/dao/PriceDao.kt
git commit -m "feat: add DAO queries for filtered item lookup by style, season, priority, brand name, category name, and purchase month"
```

---

### Task 2: Add repository methods for filtered item lookup

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/repository/ItemRepository.kt`
- Modify: `app/src/main/java/com/lolita/app/data/repository/PriceRepository.kt`

**Step 1: Add methods to ItemRepository**

Add after `fun getWishedCount()` (line 41):

```kotlin
fun getItemsByBrandName(brandName: String) = itemDao.getItemsByBrandName(brandName)
fun getItemsByCategoryName(categoryName: String) = itemDao.getItemsByCategoryName(categoryName)
fun getItemsByStyle(style: String) = itemDao.getItemsByStyle(style)
fun getItemsBySeason(season: String) = itemDao.getItemsBySeason(season)
fun getWishlistByPriorityFilter(priority: ItemPriority) = itemDao.getWishlistByPriorityFilter(priority)
```

Add import for `ItemPriority` at top of file.

**Step 2: Add method to PriceRepository**

Add a public method that delegates to the DAO:

```kotlin
fun getItemsByPurchaseMonth(yearMonth: String) = priceDao.getItemsByPurchaseMonth(yearMonth)
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/repository/ItemRepository.kt app/src/main/java/com/lolita/app/data/repository/PriceRepository.kt
git commit -m "feat: add repository methods for filtered item lookup"
```

---

### Task 3: Add FilteredItemList route to Screen.kt

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/Screen.kt`

**Step 1: Add the new route**

Add before the closing `}` of the sealed interface (or after `ThemeSelect`):

```kotlin
data object FilteredItemList : Screen {
    override val route = "filtered_item_list?filterType={filterType}&filterValue={filterValue}&title={title}"
    fun createRoute(filterType: String, filterValue: String, title: String): String {
        return "filtered_item_list?filterType=$filterType&filterValue=${android.net.Uri.encode(filterValue)}&title=${android.net.Uri.encode(title)}"
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/navigation/Screen.kt
git commit -m "feat: add FilteredItemList route definition"
```

---

### Task 4: Create FilteredItemListViewModel

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/screen/item/FilteredItemListViewModel.kt`

**Step 1: Create the ViewModel**

```kotlin
package com.lolita.app.ui.screen.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemPriority
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.PriceRepository
import com.lolita.app.di.AppModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FilteredItemListUiState(
    val items: List<Item> = emptyList(),
    val isLoading: Boolean = true
)

class FilteredItemListViewModel(
    private val filterType: String,
    private val filterValue: String,
    private val itemRepository: ItemRepository = AppModule.itemRepository(),
    private val priceRepository: PriceRepository = AppModule.priceRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(FilteredItemListUiState())
    val uiState: StateFlow<FilteredItemListUiState> = _uiState.asStateFlow()

    init {
        loadItems()
    }

    private fun loadItems() {
        viewModelScope.launch {
            val flow = when (filterType) {
                "status_owned" -> itemRepository.getItemsByStatus(ItemStatus.OWNED)
                "status_wished" -> itemRepository.getItemsByStatus(ItemStatus.WISHED)
                "brand" -> itemRepository.getItemsByBrandName(filterValue)
                "category" -> itemRepository.getItemsByCategoryName(filterValue)
                "style" -> itemRepository.getItemsByStyle(filterValue)
                "season" -> itemRepository.getItemsBySeason(filterValue)
                "month" -> priceRepository.getItemsByPurchaseMonth(filterValue)
                "priority" -> itemRepository.getWishlistByPriorityFilter(
                    ItemPriority.valueOf(filterValue)
                )
                else -> itemRepository.getAllItems()
            }
            flow.collect { items ->
                _uiState.value = FilteredItemListUiState(items = items, isLoading = false)
            }
        }
    }
}

class FilteredItemListViewModelFactory(
    private val filterType: String,
    private val filterValue: String
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return FilteredItemListViewModel(filterType, filterValue) as T
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/FilteredItemListViewModel.kt
git commit -m "feat: add FilteredItemListViewModel with filter-based item queries"
```

---

### Task 5: Create FilteredItemListScreen

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/screen/item/FilteredItemListScreen.kt`

**Step 1: Create the screen composable**

This screen shows a simple list of items with a back button and title. Each item is clickable to navigate to ItemDetailScreen. Uses a simplified item card (no edit/delete menu needed).

```kotlin
package com.lolita.app.ui.screen.item

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.LolitaCard

@Composable
fun FilteredItemListScreen(
    title: String,
    filterType: String,
    filterValue: String,
    onBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    viewModel: FilteredItemListViewModel = viewModel(
        factory = FilteredItemListViewModelFactory(filterType, filterValue)
    )
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            GradientTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else if (uiState.items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "暂无数据",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        "${uiState.items.size} 件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(uiState.items, key = { it.id }) { item ->
                    FilteredItemCard(
                        item = item,
                        onClick = { onNavigateToDetail(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilteredItemCard(item: Item, onClick: () -> Unit) {
    LolitaCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.imageUrl != null) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.name,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                )
                            ), RoundedCornerShape(10.dp)
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            item.name.firstOrNull()?.toString() ?: "?",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.description.isNotEmpty()) {
                    Text(
                        item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    color = when (item.status) {
                        ItemStatus.OWNED -> MaterialTheme.colorScheme.primaryContainer
                        ItemStatus.WISHED -> MaterialTheme.colorScheme.secondaryContainer
                    },
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = when (item.status) {
                            ItemStatus.OWNED -> "已拥有"
                            ItemStatus.WISHED -> "愿望单"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        color = when (item.status) {
                            ItemStatus.OWNED -> MaterialTheme.colorScheme.onPrimaryContainer
                            ItemStatus.WISHED -> MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    )
                }
            }
        }
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/FilteredItemListScreen.kt
git commit -m "feat: add FilteredItemListScreen with simplified item cards"
```

---

### Task 6: Register FilteredItemList route in LolitaNavHost

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt`

**Step 1: Add import**

Add at top with other imports:
```kotlin
import com.lolita.app.ui.screen.item.FilteredItemListScreen
```

**Step 2: Add composable route**

Add after the Quick Outfit Log route (after line 447, before the closing `}` of NavHost):

```kotlin
// Filtered Item List
composable(
    route = Screen.FilteredItemList.route,
    arguments = listOf(
        navArgument("filterType") { type = NavType.StringType; defaultValue = "" },
        navArgument("filterValue") { type = NavType.StringType; defaultValue = "" },
        navArgument("title") { type = NavType.StringType; defaultValue = "" }
    )
) { backStackEntry ->
    val filterType = backStackEntry.arguments?.getString("filterType") ?: ""
    val filterValue = backStackEntry.arguments?.getString("filterValue") ?: ""
    val title = backStackEntry.arguments?.getString("title") ?: ""
    FilteredItemListScreen(
        title = title,
        filterType = filterType,
        filterValue = filterValue,
        onBack = { navController.popBackStack() },
        onNavigateToDetail = { itemId ->
            navController.navigate(Screen.ItemDetail.createRoute(itemId))
        }
    )
}
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt
git commit -m "feat: register FilteredItemList route in navigation graph"
```

---

### Task 7: Make StatsPageScreen pass navigation callbacks

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/stats/StatsPageScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt`

**Step 1: Add navigation parameters to StatsPageScreen**

Change `StatsPageScreen()` signature to:

```kotlin
@Composable
fun StatsPageScreen(
    onNavigateToFilteredList: (filterType: String, filterValue: String, title: String) -> Unit = {},
    onNavigateToItemDetail: (Long) -> Unit = {}
)
```

Pass these callbacks to each content composable:

```kotlin
when (page) {
    0 -> StatsContent(
        onNavigateToFilteredList = onNavigateToFilteredList,
        onNavigateToItemDetail = onNavigateToItemDetail
    )
    1 -> SpendingDistributionContent(
        onNavigateToFilteredList = onNavigateToFilteredList
    )
    2 -> SpendingTrendContent(
        onNavigateToFilteredList = onNavigateToFilteredList
    )
    3 -> WishlistAnalysisContent(
        onNavigateToFilteredList = onNavigateToFilteredList
    )
    4 -> PaymentCalendarContent()
}
```

**Step 2: Wire up in LolitaNavHost**

Change the Stats composable (line 365-367) to:

```kotlin
composable(Screen.Stats.route) {
    StatsPageScreen(
        onNavigateToFilteredList = { filterType, filterValue, title ->
            navController.navigate(Screen.FilteredItemList.createRoute(filterType, filterValue, title))
        },
        onNavigateToItemDetail = { itemId ->
            navController.navigate(Screen.ItemDetail.createRoute(itemId))
        }
    )
}
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/stats/StatsPageScreen.kt app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt
git commit -m "feat: wire navigation callbacks through StatsPageScreen"
```

---

### Task 8: Make StatsContent (overview) clickable

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/stats/StatsScreen.kt`

**Step 1: Add navigation params to StatsContent**

Change signature to:
```kotlin
@Composable
fun StatsContent(
    viewModel: StatsViewModel = viewModel(),
    modifier: Modifier = Modifier,
    onNavigateToFilteredList: (filterType: String, filterValue: String, title: String) -> Unit = {},
    onNavigateToItemDetail: (Long) -> Unit = {}
)
```

**Step 2: Make StatCard clickable**

Add `onClick` parameter to `StatCard`:
```kotlin
@Composable
private fun StatCard(
    title: String,
    targetValue: Int,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
)
```

Wrap the `Card` with click: change `Card(modifier = modifier, ...)` to:
```kotlin
Card(
    modifier = modifier,
    onClick = { onClick?.invoke() },
    colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
)
```

Note: Use `Card(onClick = ...)` overload from Material3 which makes the card clickable.

**Step 3: Wire up the 4 stat cards**

For "已拥有" and "愿望单" cards, add onClick:
```kotlin
StatCard(
    title = "已拥有",
    targetValue = uiState.ownedCount,
    icon = Icons.Default.Home,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.weight(1f),
    onClick = { onNavigateToFilteredList("status_owned", "", "已拥有") }
)
StatCard(
    title = "愿望单",
    targetValue = uiState.wishedCount,
    icon = Icons.Default.Favorite,
    color = Color(0xFFFF6B6B),
    modifier = Modifier.weight(1f),
    onClick = { onNavigateToFilteredList("status_wished", "", "愿望单") }
)
```

For "套装" and "穿搭记录" — leave without onClick (these navigate to different tabs, not item lists).

**Step 4: Make "最贵单品" card clickable**

Change the most expensive item `Card` (line 182) to use `Card(onClick = ...)`:
```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    onClick = { onNavigateToItemDetail(item.itemId) },
    colors = CardDefaults.cardColors(containerColor = Color(0xFFFF1493).copy(alpha = 0.1f))
)
```

**Step 5: Make Brand Top 5 rows clickable**

Add `clickable` modifier and import to each brand row:
```kotlin
import androidx.compose.foundation.clickable

// In the brand forEach:
Row(
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(4.dp))
        .clickable { onNavigateToFilteredList("brand", brand.brandName, "品牌: ${brand.brandName}") }
        .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically
)
```

**Step 6: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/stats/StatsScreen.kt
git commit -m "feat: make stats overview cards and brand top 5 clickable"
```

---

### Task 9: Make SpendingDistributionContent ranking clickable

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/stats/SpendingDistributionScreen.kt`

**Step 1: Add navigation param**

Change signature to:
```kotlin
@Composable
fun SpendingDistributionContent(
    viewModel: SpendingDistributionViewModel = viewModel(),
    modifier: Modifier = Modifier,
    onNavigateToFilteredList: (filterType: String, filterValue: String, title: String) -> Unit = {}
)
```

**Step 2: Make ranking rows clickable**

Add `clickable` import and modify the ranking row (line 207):
```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip

// In the forEachIndexed:
val filterType = when (uiState.dimension) {
    SpendingDimension.BRAND -> "brand"
    SpendingDimension.CATEGORY -> "category"
    SpendingDimension.STYLE -> "style"
    SpendingDimension.SEASON -> "season"
}
val dimensionLabel = uiState.dimension.label

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
)
```

Skip click for "其他" (aggregated remainder) since it doesn't map to a single filter value.

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/stats/SpendingDistributionScreen.kt
git commit -m "feat: make spending distribution ranking rows clickable"
```

---

### Task 10: Make SpendingTrendContent month rows clickable

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/stats/SpendingTrendScreen.kt`

**Step 1: Add navigation param**

Change signature to:
```kotlin
@Composable
fun SpendingTrendContent(
    viewModel: SpendingTrendViewModel = viewModel(),
    modifier: Modifier = Modifier,
    onNavigateToFilteredList: (filterType: String, filterValue: String, title: String) -> Unit =
)
```

**Step 2: Make month rows clickable**

Add `clickable` import and modify the month detail row (line 213):
```kotlin
import androidx.compose.foundation.clickable

// In the forEachIndexed:
val yearMonth = "${uiState.selectedYear}-${String.format("%02d", index + 1)}"
Row(
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .clickable {
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
            } else Modifier
        )
        .padding(horizontal = 12.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
)
```

Only navigate when `amount > 0` (no point drilling into months with zero spending).

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/stats/SpendingTrendScreen.kt
git commit -m "feat: make spending trend month rows clickable"
```

---

### Task 11: Make WishlistAnalysisContent priority rows clickable

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/stats/WishlistAnalysisScreen.kt`

**Step 1: Add navigation param**

Change signature to:
```kotlin
@Composable
fun WishlistAnalysisContent(
    viewModel: WishlistAnalysisViewModel = viewModel(),
    modifier: Modifier = Modifier,
    onNavigateToFilteredList: (filterType: String, filterValue: String, title: String) -> Unit = {}
)
```

**Step 2: Make priority rows clickable**

Change the `PriorityDetailRow` composable to accept an `onClick`:
```kotlin
@Composable
private fun PriorityDetailRow(
    detail: PriorityDetail,
    color: Color,
    showBudget: Boolean,
    onClick: () -> Unit = {}
)
```

Add `clickable` to the outer Row:
```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(4.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 4.dp, vertical = 4.dp),
    ...
)
```

Wire up in the caller — map priority label back to enum value:
```kotlin
uiState.priorityDetails.forEachIndexed { index, detail ->
    val priorityValue = when (detail.priorityLabel) {
        "高优先级" -> "HIGH"
        "中优先级" -> "MEDIUM"
        "低优先级" -> "LOW"
        else -> ""
    }
    PriorityDetailRow(
        detail = detail,
        color = uiState.priorityChartData.getOrNull(index)?.color ?: Color(0xFFFFB6C1),
        showBudget = uiState.showTotalPrice,
        onClick = { onNavigateToFilteredList("priority", priorityValue, detail.priorityLabel) }
    )
}
```

Add imports:
```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/stats/WishlistAnalysisScreen.kt
git commit -m "feat: make wishlist analysis priority rows clickable"
```

---

### Task 12: Make CoordinateDetailScreen items clickable

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateDetailScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt`

**Step 1: Add onNavigateToItem param to CoordinateDetailScreen**

Change signature to:
```kotlin
@Composable
fun CoordinateDetailScreen(
    coordinateId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: () -> Unit = {},
    onNavigateToItem: (Long) -> Unit = {},
    viewModel: CoordinateDetailViewModel = viewModel()
)
```

**Step 2: Add onClick to CoordinateItemCard**

Change `CoordinateItemCard` signature:
```kotlin
@Composable
private fun CoordinateItemCard(
    item: Item,
    onClick: () -> Unit,
    onRemove: () -> Unit
)
```

Make the `LolitaCard` clickable:
```kotlin
LolitaCard(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth()
)
```

**Step 3: Wire up in the items list**

Change the items call (line 189-194):
```kotlin
items(uiState.items, key = { it.id }) { item ->
    CoordinateItemCard(
        item = item,
        onClick = { onNavigateToItem(item.id) },
        onRemove = { itemToRemove = item }
    )
}
```

**Step 4: Wire up in LolitaNavHost**

Change the CoordinateDetail composable (line 301-306) to pass `onNavigateToItem`:
```kotlin
CoordinateDetailScreen(
    coordinateId = coordinateId,
    onBack = { navController.popBackStack() },
    onEdit = { navController.navigate(Screen.CoordinateEdit.createRoute(it)) },
    onDelete = { navController.popBackStack() },
    onNavigateToItem = { itemId ->
        navController.navigate(Screen.ItemDetail.createRoute(itemId))
    }
)
```

**Step 5: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateDetailScreen.kt app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt
git commit -m "feat: make coordinate detail item cards clickable to navigate to item detail"
```

---

### Task 13: Build and verify

**Step 1: Run debug build**

```bash
./gradlew.bat assembleDebug
```

Expected: BUILD SUCCESSFUL

**Step 2: Fix any compilation errors**

If there are errors, fix them based on the error messages. Common issues:
- Missing imports (especially `clickable`, `clip`, `RoundedCornerShape`)
- Signature mismatches between composable calls

**Step 3: Final commit if fixes were needed**

```bash
git add -A
git commit -m "fix: resolve compilation issues in stats drilldown feature"
```
