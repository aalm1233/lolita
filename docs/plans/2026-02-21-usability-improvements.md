# Usability Improvements Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add sorting to item/coordinate lists, search to wishlist/coordinate/outfit lists, and unsaved-changes confirmation to all edit screens.

**Architecture:** Three independent features. Sorting and search are ViewModel-level filtering with minimal UI additions. Unsaved changes uses a shared composable with `BackHandler` that each edit screen integrates.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, StateFlow, BackHandler

---

### Task 1: Create SortOption enum and sort menu composable

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/screen/common/SortOption.kt`

**Step 1: Create the SortOption enum and reusable SortMenuButton composable**

```kotlin
package com.lolita.app.ui.screen.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

enum class SortOption(val label: String) {
    DEFAULT("默认排序"),
    DATE_DESC("日期 — 最新优先"),
    DATE_ASC("日期 — 最早优先"),
    PRICE_DESC("价格 — 最贵优先"),
    PRICE_ASC("价格 — 最便宜优先")
}

@Composable
fun SortMenuButton(
    currentSort: SortOption,
    showPriceOptions: Boolean,
    onSortSelected: (SortOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }, modifier = modifier) {
        Icon(
            Icons.AutoMirrored.Filled.Sort,
            contentDescription = "排序",
            tint = if (currentSort != SortOption.DEFAULT)
                MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        SortOption.entries.forEach { option ->
            if (option == SortOption.PRICE_DESC || option == SortOption.PRICE_ASC) {
                if (!showPriceOptions) return@forEach
            }
            DropdownMenuItem(
                text = { Text(option.label) },
                onClick = { onSortSelected(option); expanded = false },
                trailingIcon = if (currentSort == option) {
                    { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                } else null
            )
        }
    }
}
```

**Step 2: Build to verify**

Run: `./gradlew.bat assembleDebug`

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/common/SortOption.kt
git commit -m "feat: add SortOption enum and SortMenuButton composable"
```

---

### Task 2: Add sorting to ItemListViewModel

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt`

**Step 1: Add sortOption to ItemListUiState**

In `ItemListUiState` data class (line 35), add field:

```kotlin
data class ItemListUiState(
    // ... existing fields ...
    val sortOption: SortOption = SortOption.DEFAULT,
    // ... rest of fields ...
)
```

Add import at top of file:

```kotlin
import com.lolita.app.ui.screen.common.SortOption
```

**Step 2: Add sort method and apply sorting after filtering**

Add method to `ItemListViewModel` (after `setColumns` around line 321):

```kotlin
fun setSortOption(option: SortOption) {
    _uiState.update { it.copy(sortOption = option) }
    val state = _uiState.value
    val filtered = applyFilters(
        state.items, state.filterStatus, state.searchQuery, state.filterGroup, state.categoryGroups,
        state.filterSeason, state.filterStyle, state.filterColor, state.filterBrandId
    )
    val sorted = applySorting(filtered, option, state.itemPrices)
    _uiState.update { it.copy(filteredItems = sorted) }
    updateTotalPrice(sorted)
}

private fun applySorting(items: List<Item>, sort: SortOption, prices: Map<Long, Double>): List<Item> {
    return when (sort) {
        SortOption.DEFAULT -> items
        SortOption.DATE_DESC -> items.sortedByDescending { it.updatedAt }
        SortOption.DATE_ASC -> items.sortedBy { it.updatedAt }
        SortOption.PRICE_DESC -> items.sortedByDescending { prices[it.id] ?: 0.0 }
        SortOption.PRICE_ASC -> items.sortedBy { prices[it.id] ?: 0.0 }
    }
}
```

**Step 3: Apply sorting in all existing filter/search paths**

In every place that updates `filteredItems` (the `loadItems` collect block, `filterByStatus`, `filterByGroup`, `filterBySeason`, `filterByStyle`, `filterByColor`, `filterByBrand`, `search`), wrap the result with `applySorting`. For example, in `loadItems` collect block (around line 155):

Change:
```kotlin
filteredItems = filtered,
```
To:
```kotlin
filteredItems = applySorting(filtered, _uiState.value.sortOption, data.priceMap),
```

Apply the same pattern to all `filterBy*` and `search` methods — after computing `filtered`, wrap with:
```kotlin
val sorted = applySorting(filtered, _uiState.value.sortOption, _uiState.value.itemPrices)
```
Then use `sorted` instead of `filtered` for `filteredItems` and `updateTotalPrice`.

**Step 4: Build to verify**

Run: `./gradlew.bat assembleDebug`

**Step 5: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt
git commit -m "feat: add sorting logic to ItemListViewModel"
```

---

### Task 3: Add sort button UI to ItemListScreen

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt`

**Step 1: Add import**

```kotlin
import com.lolita.app.ui.screen.common.SortMenuButton
```

**Step 2: Add sort button in the search bar Row**

In the `Row` that contains the search bar, filter button, and column toggle (around line 154-224), add the `SortMenuButton` between the filter button and the column toggle button. After the `Box` containing the filter `IconButton` (ends around line 201) and before the column toggle `IconButton` (line 202):

```kotlin
SortMenuButton(
    currentSort = uiState.sortOption,
    showPriceOptions = uiState.showTotalPrice,
    onSortSelected = { viewModel.setSortOption(it) }
)
```

**Step 3: Build to verify**

Run: `./gradlew.bat assembleDebug`

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt
git commit -m "feat: add sort menu button to item list screen"
```

---

### Task 4: Add sorting to CoordinateListViewModel

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateViewModel.kt`

**Step 1: Add sortOption to CoordinateListUiState**

```kotlin
import com.lolita.app.ui.screen.common.SortOption
```

Add field to `CoordinateListUiState` (line 22):

```kotlin
data class CoordinateListUiState(
    // ... existing fields ...
    val sortOption: SortOption = SortOption.DEFAULT,
    // ... rest ...
)
```

**Step 2: Add sort method to CoordinateListViewModel**

After `setColumns` (line 124), add:

```kotlin
fun setSortOption(option: SortOption) {
    val state = _uiState.value
    val sorted = applySorting(state.coordinates, option, state.priceByCoordinate)
    _uiState.value = state.copy(sortOption = option, coordinates = sorted)
}

private fun applySorting(
    coordinates: List<Coordinate>,
    sort: SortOption,
    prices: Map<Long, Double>
): List<Coordinate> {
    return when (sort) {
        SortOption.DEFAULT -> coordinates
        SortOption.DATE_DESC -> coordinates.sortedByDescending { it.updatedAt }
        SortOption.DATE_ASC -> coordinates.sortedBy { it.updatedAt }
        SortOption.PRICE_DESC -> coordinates.sortedByDescending { prices[it.id] ?: 0.0 }
        SortOption.PRICE_ASC -> coordinates.sortedBy { prices[it.id] ?: 0.0 }
    }
}
```

**Step 3: Apply sorting in loadCoordinates collect block**

In `loadCoordinates()` (around line 93), after building the `CoordinateListUiState`, apply sorting before emitting. Change the collect block to apply sorting:

```kotlin
}.collect { state ->
    _uiState.value = state.copy(
        coordinates = applySorting(state.coordinates, _uiState.value.sortOption, state.priceByCoordinate),
        sortOption = _uiState.value.sortOption
    )
}
```

**Step 4: Build to verify**

Run: `./gradlew.bat assembleDebug`

**Step 5: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateViewModel.kt
git commit -m "feat: add sorting logic to CoordinateListViewModel"
```

---

### Task 5: Add sort button UI to CoordinateListScreen

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateListScreen.kt`

**Step 1: Add import**

```kotlin
import com.lolita.app.ui.screen.common.SortMenuButton
```

**Step 2: Add sort button in CoordinateListScreen top bar actions**

In the `GradientTopAppBar` actions block (line 59-74), add `SortMenuButton` before the column toggle `IconButton`:

```kotlin
actions = {
    SortMenuButton(
        currentSort = uiState.sortOption,
        showPriceOptions = uiState.showPrice,
        onSortSelected = { viewModel.setSortOption(it) }
    )
    IconButton(onClick = {
        val next = when (uiState.columnsPerRow) { 1 -> 2; 2 -> 3; else -> 1 }
        viewModel.setColumns(next)
    }) {
        // ... existing column toggle icon ...
    }
}
```

**Step 3: Also add sort button to the ItemListScreen's coordinate tab**

In `ItemListScreen.kt`, the column toggle button (line 202-223) already handles coordinate tab. Add sort support for the coordinate tab too. In the search bar `Row`, update the `SortMenuButton` to handle both tabs:

```kotlin
SortMenuButton(
    currentSort = if (pagerState.currentPage == 2) coordinateUiState.sortOption else uiState.sortOption,
    showPriceOptions = if (pagerState.currentPage == 2) coordinateUiState.showPrice else uiState.showTotalPrice,
    onSortSelected = {
        if (pagerState.currentPage == 2) coordinateViewModel.setSortOption(it)
        else viewModel.setSortOption(it)
    }
)
```

**Step 4: Build to verify**

Run: `./gradlew.bat assembleDebug`

**Step 5: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateListScreen.kt app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt
git commit -m "feat: add sort menu button to coordinate list and item list coordinate tab"
```

---

### Task 6: Add search to WishlistScreen

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/WishlistScreen.kt`

**Step 1: Refactor WishlistViewModel to support search**

Replace the simple `WishlistViewModel` (lines 38-43) with one that supports search filtering:

```kotlin
class WishlistViewModel(
    private val itemRepository: ItemRepository = com.lolita.app.di.AppModule.itemRepository()
) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    private val _allItems = itemRepository.getWishlistByPriority()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val items: StateFlow<List<Item>> = combine(_allItems, _searchQuery) { items, query ->
        if (query.isBlank()) items
        else items.filter { it.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun search(query: String) { _searchQuery.value = query }
}
```

Add imports at top of file:

```kotlin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
```

**Step 2: Add search bar UI to WishlistScreen**

In `WishlistScreen` composable, add a search bar between the `Scaffold` content padding and the list. Inside the `Scaffold` content lambda (after `padding`), wrap the existing content in a `Column` and add the search bar at the top:

```kotlin
) { padding ->
    val searchQuery by viewModel.searchQuery.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.search(it) },
            placeholder = { Text("搜索愿望单") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                { IconButton(onClick = { viewModel.search("") }) {
                    Icon(Icons.Default.Close, contentDescription = "清除")
                } }
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )

        if (items.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Favorite,
                title = if (searchQuery.isNotEmpty()) "未找到匹配的服饰" else "愿望单为空",
                subtitle = if (searchQuery.isNotEmpty()) "试试其他关键词" else "添加心仪的服饰到愿望单",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    WishlistItemCard(
                        item = item,
                        onClick = { onNavigateToDetail(item.id) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}
```

Add imports:

```kotlin
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.IconButton
```

**Step 3: Build to verify**

Run: `./gradlew.bat assembleDebug`

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/WishlistScreen.kt
git commit -m "feat: add search to wishlist screen"
```

---

### Task 7: Add search to CoordinateListViewModel and CoordinateListScreen

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateViewModel.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateListScreen.kt`

**Step 1: Add search state to CoordinateListUiState and ViewModel**

Add field to `CoordinateListUiState`:

```kotlin
val searchQuery: String = "",
```

Add to `CoordinateListViewModel`:

```kotlin
private var searchJob: Job? = null

fun search(query: String) {
    _uiState.value = _uiState.value.copy(searchQuery = query)
    searchJob?.cancel()
    searchJob = viewModelScope.launch {
        kotlinx.coroutines.delay(300)
        applySearch(query)
    }
}

private fun applySearch(query: String) {
    // Search is applied reactively — the collect block in loadCoordinates
    // already runs. We just need to trigger a re-filter.
    // Since coordinates come from Flow, we store allCoordinates separately.
}
```

Actually, a cleaner approach: store the full list and filter in the collect block. Modify `loadCoordinates()` to store all coordinates and apply search:

Add a private field:

```kotlin
private var allCoordinates: List<Coordinate> = emptyList()
```

In the `collect` block of `loadCoordinates`, save all coordinates and apply search:

```kotlin
}.collect { state ->
    allCoordinates = state.coordinates
    val query = _uiState.value.searchQuery
    val filtered = if (query.isBlank()) state.coordinates
        else state.coordinates.filter { it.name.contains(query, ignoreCase = true) }
    val sorted = applySorting(filtered, _uiState.value.sortOption, state.priceByCoordinate)
    _uiState.value = state.copy(
        coordinates = sorted,
        sortOption = _uiState.value.sortOption,
        searchQuery = _uiState.value.searchQuery
    )
}
```

Update `search` method to filter from `allCoordinates`:

```kotlin
fun search(query: String) {
    val filtered = if (query.isBlank()) allCoordinates
        else allCoordinates.filter { it.name.contains(query, ignoreCase = true) }
    val sorted = applySorting(filtered, _uiState.value.sortOption, _uiState.value.priceByCoordinate)
    _uiState.value = _uiState.value.copy(searchQuery = query, coordinates = sorted)
}
```

Add import: `import kotlinx.coroutines.Job`

**Step 2: Add search bar to CoordinateListScreen**

In `CoordinateListScreen`, add a search bar in the top bar actions area. Better approach: add it between the top bar and the list content. Modify the `Scaffold` content to include a search bar:

```kotlin
) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        // Search bar
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.search(it) },
            placeholder = { Text("搜索套装") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (uiState.searchQuery.isNotEmpty()) {
                { IconButton(onClick = { viewModel.search("") }) {
                    Icon(Icons.Default.Close, contentDescription = "清除")
                } }
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )

        CoordinateListContent(
            onNavigateToDetail = onNavigateToDetail,
            onNavigateToEdit = onNavigateToEdit,
            viewModel = viewModel
        )
    }
}
```

Add imports:

```kotlin
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Column
```

**Step 3: Build to verify**

Run: `./gradlew.bat assembleDebug`

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateViewModel.kt app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateListScreen.kt
git commit -m "feat: add search to coordinate list screen"
```

---

### Task 8: Add search to OutfitLogListViewModel and OutfitLogListScreen

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/outfit/OutfitLogViewModel.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/outfit/OutfitLogListScreen.kt`

**Step 1: Add search state to OutfitLogListUiState and ViewModel**

Add field to `OutfitLogListUiState` (line 21):

```kotlin
data class OutfitLogListUiState(
    val logs: List<OutfitLogListItem> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true
)
```

In `OutfitLogListViewModel`, add a private field and search method:

```kotlin
private var allLogs: List<OutfitLogListItem> = emptyList()

fun search(query: String) {
    val filtered = if (query.isBlank()) allLogs
        else allLogs.filter { it.previewNote.contains(query, ignoreCase = true) }
    _uiState.value = _uiState.value.copy(searchQuery = query, logs = filtered)
}
```

In the `loadOutfitLogs()` collect block (around line 85), save all logs and apply search:

```kotlin
.collect { listItems ->
    allLogs = listItems
    val query = _uiState.value.searchQuery
    val filtered = if (query.isBlank()) listItems
        else listItems.filter { it.previewNote.contains(query, ignoreCase = true) }
    _uiState.value = _uiState.value.copy(
        logs = filtered,
        searchQuery = query,
        isLoading = false
    )
}
```

**Step 2: Add search bar to OutfitLogListScreen**

In `OutfitLogListScreen`, add a search bar between the top bar and the `LazyColumn`. Wrap the `LazyColumn` in a `Column` and add the search bar:

Inside the `Scaffold` content (after `padding`), change from directly using `LazyColumn` to:

```kotlin
) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        // Search bar
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.search(it) },
            placeholder = { Text("搜索穿搭日记") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (uiState.searchQuery.isNotEmpty()) {
                { IconButton(onClick = { viewModel.search("") }) {
                    Icon(Icons.Default.Close, contentDescription = "清除")
                } }
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ... existing loading/empty/items content unchanged ...
        }
    }
}
```

Update the empty state text to be search-aware:

```kotlin
} else if (uiState.logs.isEmpty()) {
    item {
        EmptyState(
            icon = Icons.Default.Create,
            title = if (uiState.searchQuery.isNotEmpty()) "未找到匹配的日记" else "还没有穿搭日记",
            subtitle = if (uiState.searchQuery.isNotEmpty()) "试试其他关键词" else "记录每天的穿搭"
        )
    }
}
```

Add imports:

```kotlin
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Column
```

**Step 3: Build to verify**

Run: `./gradlew.bat assembleDebug`

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/outfit/OutfitLogViewModel.kt app/src/main/java/com/lolita/app/ui/screen/outfit/OutfitLogListScreen.kt
git commit -m "feat: add search to outfit log list screen"
```

---

### Task 9: Create UnsavedChangesHandler composable

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/screen/common/UnsavedChangesHandler.kt`

**Step 1: Create the composable**

```kotlin
package com.lolita.app.ui.screen.common

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*

@Composable
fun UnsavedChangesHandler(
    hasUnsavedChanges: Boolean,
    onConfirmLeave: () -> Unit
): () -> Unit {
    var showDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = hasUnsavedChanges) {
        showDialog = true
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("未保存的修改") },
            text = { Text("有未保存的修改，确定要离开吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    onConfirmLeave()
                }) { Text("放弃") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("继续编辑") }
            }
        )
    }

    // Return a callback for the top bar back button
    return {
        if (hasUnsavedChanges) showDialog = true
        else onConfirmLeave()
    }
}
```

**Step 2: Build to verify**

Run: `./gradlew.bat assembleDebug`

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/common/UnsavedChangesHandler.kt
git commit -m "feat: add UnsavedChangesHandler composable"
```

---

### Task 10: Add unsaved changes detection to ItemEditScreen

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemEditScreen.kt`

**Step 1: Add hasUnsavedChanges to ItemEditViewModel**

In `ItemEditViewModel` (around line 341), add a private field to track initial state and a computed property:

```kotlin
private var initialState: ItemEditUiState? = null
```

At the end of `loadItem()` (after setting `isLoading = false` in both the existing-item and new-item branches), capture the initial state:

```kotlin
// After the final _uiState.update in loadItem:
initialState = _uiState.value
```

Add a method:

```kotlin
fun hasUnsavedChanges(): Boolean {
    val initial = initialState ?: return false
    val current = _uiState.value
    return current.name != initial.name ||
        current.description != initial.description ||
        current.brandId != initial.brandId ||
        current.categoryId != initial.categoryId ||
        current.coordinateId != initial.coordinateId ||
        current.status != initial.status ||
        current.priority != initial.priority ||
        current.imageUrl != initial.imageUrl ||
        current.color != initial.color ||
        current.seasons != initial.seasons ||
        current.style != initial.style ||
        current.size != initial.size ||
        current.sizeChartImageUrl != initial.sizeChartImageUrl
}
```

**Step 2: Integrate UnsavedChangesHandler in ItemEditScreen**

In `ItemEditScreen.kt`, add import:

```kotlin
import com.lolita.app.ui.screen.common.UnsavedChangesHandler
```

Inside the `ItemEditScreen` composable, before the `Scaffold`, add:

```kotlin
val handleBack = UnsavedChangesHandler(
    hasUnsavedChanges = viewModel.hasUnsavedChanges(),
    onConfirmLeave = onBack
)
```

Then replace the top bar back button's `onClick = onBack` with `onClick = handleBack`. In the `GradientTopAppBar` `navigationIcon` (around line 116-119), change:

```kotlin
navigationIcon = {
    IconButton(onClick = handleBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
    }
}
```

**Step 3: Build to verify**

Run: `./gradlew.bat assembleDebug`

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt app/src/main/java/com/lolita/app/ui/screen/item/ItemEditScreen.kt
git commit -m "feat: add unsaved changes detection to item edit screen"
```

---

### Task 11: Add unsaved changes detection to PriceEditScreen

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/price/PriceViewModel.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/price/PriceEditScreen.kt`

**Step 1: Add hasUnsavedChanges to PriceEditViewModel**

In `PriceEditViewModel` (around line 90), add:

```kotlin
private var initialState: PriceEditUiState? = null
```

At the end of `loadPrice()` (after updating state, around line 122), and also for new price case (when priceId is null, the default state is the initial):

In `loadPrice`:
```kotlin
fun loadPrice(priceId: Long?) {
    if (priceId == null) {
        initialState = _uiState.value
        return
    }
    // ... existing load logic ...
    // After _uiState.update:
    initialState = _uiState.value
}
```

Add method:

```kotlin
fun hasUnsavedChanges(): Boolean {
    val initial = initialState ?: return false
    val current = _uiState.value
    return current.priceType != initial.priceType ||
        current.totalPrice != initial.totalPrice ||
        current.deposit != initial.deposit ||
        current.balance != initial.balance ||
        current.purchaseDate != initial.purchaseDate
}
```

**Step 2: Integrate in PriceEditScreen**

Add import:

```kotlin
import com.lolita.app.ui.screen.common.UnsavedChangesHandler
```

Add before `Scaffold`:

```kotlin
val handleBack = UnsavedChangesHandler(
    hasUnsavedChanges = viewModel.hasUnsavedChanges(),
    onConfirmLeave = onBack
)
```

Replace the back button `onClick = onBack` with `onClick = handleBack` in the `GradientTopAppBar` `navigationIcon`.

**Step 3: Build to verify**

Run: `./gradlew.bat assembleDebug`

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/price/PriceViewModel.kt app/src/main/java/com/lolita/app/ui/screen/price/PriceEditScreen.kt
git commit -m "feat: add unsaved changes detection to price edit screen"
```

---

### Task 12: Add unsaved changes detection to PaymentEditScreen

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/price/PriceViewModel.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/price/PaymentEditScreen.kt`

**Step 1: Add hasUnsavedChanges to PaymentEditViewModel**

In `PaymentEditViewModel` (around line 394), add:

```kotlin
private var initialState: PaymentEditUiState? = null
```

In `loadPayment()`, capture initial state after setting values. For new payment (paymentId == null), after setting default dueDate:

```kotlin
fun loadPayment(paymentId: Long?) {
    if (paymentId == null) {
        _uiState.value = _uiState.value.copy(dueDate = System.currentTimeMillis())
        initialState = _uiState.value
        return
    }
    // ... existing load logic ...
    // After _uiState.value = PaymentEditUiState(...):
    initialState = _uiState.value
}
```

Note: The existing code sets state inside a `viewModelScope.launch` block. Add `initialState = _uiState.value` after the `payment?.let` block completes its state update.

Add method:

```kotlin
fun hasUnsavedChanges(): Boolean {
    val initial = initialState ?: return false
    val current = _uiState.value
    return current.amount != initial.amount ||
        current.dueDate != initial.dueDate ||
        current.reminderSet != initial.reminderSet ||
        current.customReminderDays != initial.customReminderDays
}
```

**Step 2: Integrate in PaymentEditScreen**

Add import:

```kotlin
import com.lolita.app.ui.screen.common.UnsavedChangesHandler
```

Add before `Scaffold`:

```kotlin
val handleBack = UnsavedChangesHandler(
    hasUnsavedChanges = viewModel.hasUnsavedChanges(),
    onConfirmLeave = onBack
)
```

Replace the back button `onClick = onBack` with `onClick = handleBack` in the `GradientTopAppBar` `navigationIcon` (around line 140-144).

**Step 3: Build to verify**

Run: `./gradlew.bat assembleDebug`

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/price/PriceViewModel.kt app/src/main/java/com/lolita/app/ui/screen/price/PaymentEditScreen.kt
git commit -m "feat: add unsaved changes detection to payment edit screen"
```

---

### Task 13: Add unsaved changes detection to CoordinateEditScreen

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateViewModel.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateEditScreen.kt`

**Step 1: Add hasUnsavedChanges to CoordinateEditViewModel**

In `CoordinateEditViewModel` (around line 189), add:

```kotlin
private var initialState: CoordinateEditUiState? = null
```

For new coordinate (coordinateId == null in `loadCoordinate`), capture initial state after `loadAllItems` has populated. The simplest approach: capture after `loadCoordinate` is called. In `loadCoordinate()`:

```kotlin
fun loadCoordinate(coordinateId: Long?) {
    if (coordinateId == null) {
        initialState = _uiState.value
        return
    }

    viewModelScope.launch {
        // ... existing load logic ...
        // After _uiState.value = _uiState.value.copy(selectedItemIds = itemIds):
        initialState = _uiState.value
    }
}
```

Add method:

```kotlin
fun hasUnsavedChanges(): Boolean {
    val initial = initialState ?: return false
    val current = _uiState.value
    return current.name != initial.name ||
        current.description != initial.description ||
        current.imageUrl != initial.imageUrl ||
        current.selectedItemIds != initial.selectedItemIds
}
```

**Step 2: Integrate in CoordinateEditScreen**

Add import:

```kotlin
import com.lolita.app.ui.screen.common.UnsavedChangesHandler
```

Add before `Scaffold`:

```kotlin
val handleBack = UnsavedChangesHandler(
    hasUnsavedChanges = viewModel.hasUnsavedChanges(),
    onConfirmLeave = onBack
)
```

Replace the back button `onClick = onBack` with `onClick = handleBack` in the `GradientTopAppBar` `navigationIcon` (around line 86-90).

**Step 3: Build to verify**

Run: `./gradlew.bat assembleDebug`

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateViewModel.kt app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateEditScreen.kt
git commit -m "feat: add unsaved changes detection to coordinate edit screen"
```

---

### Task 14: Add unsaved changes detection to OutfitLogEditScreen

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/outfit/OutfitLogViewModel.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/outfit/OutfitLogEditScreen.kt`

**Step 1: Add hasUnsavedChanges to OutfitLogEditViewModel**

In `OutfitLogEditViewModel` (around line 149), add:

```kotlin
private var initialState: OutfitLogEditUiState? = null
```

In `loadOutfitLog()`, capture initial state. For new log (logId == null), after setting default date:

```kotlin
fun loadOutfitLog(logId: Long?) {
    editingLogId = logId
    if (logId == null) {
        _uiState.value = _uiState.value.copy(date = System.currentTimeMillis())
        initialState = _uiState.value
        return
    }

    viewModelScope.launch {
        // ... existing load logic ...
        // After _uiState.value = _uiState.value.copy(date = ..., note = ..., imageUrls = ..., selectedItemIds = ...):
        initialState = _uiState.value
    }
}
```

Add method:

```kotlin
fun hasUnsavedChanges(): Boolean {
    val initial = initialState ?: return false
    val current = _uiState.value
    return current.note != initial.note ||
        current.imageUrls != initial.imageUrls ||
        current.selectedItemIds != initial.selectedItemIds
}
```

Note: We intentionally exclude `date` from comparison for new logs since it's auto-set to today.

**Step 2: Integrate in OutfitLogEditScreen**

Add import:

```kotlin
import com.lolita.app.ui.screen.common.UnsavedChangesHandler
```

Add before `Scaffold`:

```kotlin
val handleBack = UnsavedChangesHandler(
    hasUnsavedChanges = viewModel.hasUnsavedChanges(),
    onConfirmLeave = onBack
)
```

Replace the back button `onClick = onBack` with `onClick = handleBack` in the `GradientTopAppBar` `navigationIcon` (around line 91-95).

**Step 3: Build to verify**

Run: `./gradlew.bat assembleDebug`

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/outfit/OutfitLogViewModel.kt app/src/main/java/com/lolita/app/ui/screen/outfit/OutfitLogEditScreen.kt
git commit -m "feat: add unsaved changes detection to outfit log edit screen"
```

---

### Task 15: Final build verification

**Step 1: Clean build**

Run: `./gradlew.bat clean assembleDebug`

Expected: BUILD SUCCESSFUL

**Step 2: Commit any remaining changes**

If any files were missed, stage and commit them.

**Step 3: Final commit with all features**

If all individual commits are done, no action needed. Otherwise:

```bash
git add -A
git commit -m "feat: usability improvements — sorting, search, unsaved changes handler"
```
