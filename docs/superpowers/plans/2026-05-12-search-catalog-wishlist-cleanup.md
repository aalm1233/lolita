# Search/Catalog/Wishlist Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix gallery mode search/filter staleness bug, remove Catalog subsystem, merge wishlist into homepage as 4th pager tab, remove skin icon gallery debug page.

**Architecture:** ItemListViewModel's 9 filter/search/sort methods gain `galleryCardDataList` recomputation. The Catalog subsystem (entity/DAO/repository/ViewModel/3 screens) is deleted entirely. The wishlist becomes the 4th tab in ItemListScreen's HorizontalPager, sharing the existing `WishlistViewModel`. Bottom nav drops from 5 to 4 tabs. Database migrates to v19 dropping `catalog_entries` and `remote_catalog_entries` tables.

**Tech Stack:** Kotlin, Jetpack Compose, Room, MVVM

---

### Task 1: Fix gallery mode search/filter staleness in ItemListViewModel

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt` (9 methods)

- [ ] **Step 1: Fix `filterByStatuses` (line ~348)**

Replace the `_uiState.update` block at line 348:
```kotlin
// Before:
_uiState.update { it.copy(filterStatuses = statuses, filteredItems = sorted, itemCardDataList = buildItemCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice)) }

// After:
_uiState.update {
    it.copy(
        filterStatuses = statuses,
        filteredItems = sorted,
        itemCardDataList = buildItemCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice),
        galleryCardDataList = if (it.viewMode == ViewMode.GALLERY) {
            buildGalleryCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice)
        } else emptyList()
    )
}
```

- [ ] **Step 2: Fix `togglePendingBalanceOnly` (line ~361)**

Same pattern — add `galleryCardDataList` to the `_uiState.update` block:
```kotlin
_uiState.update {
    it.copy(
        filterPendingBalanceOnly = newValue,
        filteredItems = sorted,
        itemCardDataList = buildItemCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice),
        galleryCardDataList = if (it.viewMode == ViewMode.GALLERY) {
            buildGalleryCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice)
        } else emptyList()
    )
}
```

- [ ] **Step 3: Fix `filterByGroup` (line ~373)**

Same pattern — add `galleryCardDataList`:
```kotlin
_uiState.update {
    it.copy(
        filterGroup = group,
        filteredItems = sorted,
        itemCardDataList = buildItemCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice),
        galleryCardDataList = if (it.viewMode == ViewMode.GALLERY) {
            buildGalleryCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice)
        } else emptyList()
    )
}
```

- [ ] **Step 4: Fix `filterBySeason` (line ~385)**

Same pattern — add `galleryCardDataList`:
```kotlin
_uiState.update {
    it.copy(
        filterSeason = season,
        filteredItems = sorted,
        itemCardDataList = buildItemCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice),
        galleryCardDataList = if (it.viewMode == ViewMode.GALLERY) {
            buildGalleryCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice)
        } else emptyList()
    )
}
```

- [ ] **Step 5: Fix `filterByStyle` (line ~397)**

Same pattern — add `galleryCardDataList`:
```kotlin
_uiState.update {
    it.copy(
        filterStyle = style,
        filteredItems = sorted,
        itemCardDataList = buildItemCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice),
        galleryCardDataList = if (it.viewMode == ViewMode.GALLERY) {
            buildGalleryCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice)
        } else emptyList()
    )
}
```

- [ ] **Step 6: Fix `filterByColor` (line ~409)**

Same pattern — add `galleryCardDataList`:
```kotlin
_uiState.update {
    it.copy(
        filterColor = color,
        filteredItems = sorted,
        itemCardDataList = buildItemCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice),
        galleryCardDataList = if (it.viewMode == ViewMode.GALLERY) {
            buildGalleryCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice)
        } else emptyList()
    )
}
```

- [ ] **Step 7: Fix `filterByBrand` (line ~419)**

Same pattern — add `galleryCardDataList`:
```kotlin
_uiState.update {
    it.copy(
        filterBrandId = brandId,
        filteredItems = sorted,
        itemCardDataList = buildItemCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice),
        galleryCardDataList = if (it.viewMode == ViewMode.GALLERY) {
            buildGalleryCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice)
        } else emptyList()
    )
}
```

- [ ] **Step 8: Fix `search` (line ~437)**

Same pattern — add `galleryCardDataList`:
```kotlin
_uiState.update {
    it.copy(
        filteredItems = sorted,
        itemCardDataList = buildItemCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice),
        galleryCardDataList = if (it.viewMode == ViewMode.GALLERY) {
            buildGalleryCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice)
        } else emptyList()
    )
}
```

- [ ] **Step 9: Fix `setSortOption` (line ~515)**

Same pattern — add `galleryCardDataList`:
```kotlin
_uiState.update {
    it.copy(
        filteredItems = sorted,
        itemCardDataList = buildItemCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice),
        galleryCardDataList = if (it.viewMode == ViewMode.GALLERY) {
            buildGalleryCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice)
        } else emptyList()
    )
}
```

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt
git commit -m "fix: update galleryCardDataList on search/filter/sort in ItemListViewModel"
```

---

### Task 2: Remove skin icon gallery debug feature

**Files:**
- Delete: `app/src/main/java/com/lolita/app/ui/screen/settings/SkinIconGalleryScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/settings/ThemeSelectScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/Screen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt`

- [ ] **Step 1: Delete SkinIconGalleryScreen.kt**

```bash
git rm app/src/main/java/com/lolita/app/ui/screen/settings/SkinIconGalleryScreen.kt
```

- [ ] **Step 2: Remove debug card and parameter from ThemeSelectScreen**

In `ThemeSelectScreen.kt`:
- Remove the `onNavigateToIconGallery` parameter (currently on the function signature line)
- Remove the `ElevatedCard` block (lines 86-117) that contains the "查看当前皮肤图标总览" button
- Remove the `IconButton` import if it was only used by that card (check — `IconButton` from `androidx.compose.material3` may be used elsewhere in the file; keep it if so)

- [ ] **Step 3: Remove `SkinIconGallery` route from Screen.kt**

Delete lines 139-141:
```kotlin
data object SkinIconGallery : Screen {
    override val route = "skin_icon_gallery"
}
```

- [ ] **Step 4: Remove gallery registration from LolitaNavHost.kt**

- Delete import on line 81: `import com.lolita.app.ui.screen.settings.SkinIconGalleryScreen`
- In ThemeSelect composable registration (line ~648-654), remove `onNavigateToIconGallery` parameter:
```kotlin
// Before:
ThemeSelectScreen(
    onBack = { navController.popBackStack() },
    onNavigateToIconGallery = { navController.navigate(Screen.SkinIconGallery.route) }
)
// After:
ThemeSelectScreen(
    onBack = { navController.popBackStack() }
)
```
- Delete the SkinIconGallery composable block (lines 656-659)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/settings/ThemeSelectScreen.kt app/src/main/java/com/lolita/app/ui/navigation/Screen.kt app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt
git commit -m "chore: remove skin icon gallery debug feature"
```

---

### Task 3: Delete Catalog subsystem files

**Files:**
- Delete: `app/src/main/java/com/lolita/app/data/local/entity/CatalogEntry.kt`
- Delete: `app/src/main/java/com/lolita/app/data/local/dao/CatalogEntryDao.kt`
- Delete: `app/src/main/java/com/lolita/app/data/repository/CatalogRepository.kt`
- Delete: `app/src/main/java/com/lolita/app/ui/screen/catalog/CatalogViewModel.kt`
- Delete: `app/src/main/java/com/lolita/app/ui/screen/catalog/CatalogListContent.kt`
- Delete: `app/src/main/java/com/lolita/app/ui/screen/catalog/CatalogDetailScreen.kt`
- Delete: `app/src/main/java/com/lolita/app/ui/screen/catalog/CatalogEditScreen.kt`

- [ ] **Step 1: Delete all catalog files**

```bash
git rm app/src/main/java/com/lolita/app/data/local/entity/CatalogEntry.kt
git rm app/src/main/java/com/lolita/app/data/local/dao/CatalogEntryDao.kt
git rm app/src/main/java/com/lolita/app/data/repository/CatalogRepository.kt
git rm app/src/main/java/com/lolita/app/ui/screen/catalog/CatalogViewModel.kt
git rm app/src/main/java/com/lolita/app/ui/screen/catalog/CatalogListContent.kt
git rm app/src/main/java/com/lolita/app/ui/screen/catalog/CatalogDetailScreen.kt
git rm app/src/main/java/com/lolita/app/ui/screen/catalog/CatalogEditScreen.kt
```

- [ ] **Step 2: Verify catalog directory is empty and remove it**

```bash
rmdir app/src/main/java/com/lolita/app/ui/screen/catalog
```

- [ ] **Step 3: Commit**

```bash
git commit -m "chore: delete catalog subsystem (entity, DAO, repository, ViewModels, screens)"
```

---

### Task 4: Remove catalog references from AppModule and repositories

**Files:**
- Modify: `app/src/main/java/com/lolita/app/di/AppModule.kt`
- Modify: `app/src/main/java/com/lolita/app/data/repository/BrandRepository.kt`
- Modify: `app/src/main/java/com/lolita/app/data/repository/CategoryRepository.kt`
- Modify: `app/src/main/java/com/lolita/app/data/repository/StyleRepository.kt`
- Modify: `app/src/main/java/com/lolita/app/data/repository/SeasonRepository.kt`
- Modify: `app/src/main/java/com/lolita/app/data/repository/SourceRepository.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt` (ItemEditViewModel catalogRepository field)

- [ ] **Step 1: Remove catalogRepository from AppModule**

In `AppModule.kt`:
- Remove `private val _catalogRepository by lazy { CatalogRepository(database.catalogEntryDao()) }`
- Remove `fun catalogRepository() = _catalogRepository`
- Remove `import com.lolita.app.data.repository.CatalogRepository`

- [ ] **Step 2: Remove catalogEntryDao parameter from BrandRepository**

In `BrandRepository.kt`, remove `catalogEntryDao` from the constructor and any methods that use it for cascade-updating catalog entries when brands are renamed/deleted. The constructor changes from:
```kotlin
class BrandRepository(
    private val brandDao: BrandDao,
    private val itemDao: ItemDao,
    private val catalogEntryDao: CatalogEntryDao,
    private val db: LolitaDatabase
)
```
to:
```kotlin
class BrandRepository(
    private val brandDao: BrandDao,
    private val itemDao: ItemDao,
    private val db: LolitaDatabase
)
```
Also remove the `import com.lolita.app.data.local.dao.CatalogEntryDao` if no longer used.

- [ ] **Step 3: Remove catalogEntryDao parameter from CategoryRepository**

Same as Step 2 for `CategoryRepository.kt` — remove `catalogEntryDao` parameter and import.

- [ ] **Step 4: Remove catalogEntryDao parameter from StyleRepository**

Same pattern for `StyleRepository.kt`.

- [ ] **Step 5: Remove catalogEntryDao parameter from SeasonRepository**

Same pattern for `SeasonRepository.kt`.

- [ ] **Step 6: Remove catalogEntryDao parameter from SourceRepository**

Same pattern for `SourceRepository.kt`.

- [ ] **Step 7: Remove catalogRepository from ItemEditViewModel**

In `ItemViewModel.kt`, line ~579:
```kotlin
// Remove this line from constructor parameters:
private val catalogRepository: CatalogRepository = com.lolita.app.di.AppModule.catalogRepository(),
```
Also remove `import com.lolita.app.data.repository.CatalogRepository`.

Also remove all usage of `catalogRepository` and `prefillCatalogEntryId` in the ViewModel body:
- `pendingCatalogLinkId` field
- `catalogPrefillImagePaths` field
- The `prefillCatalogEntryId` parameter from `loadItem()` function
- The catalog prefill logic inside `loadItem()` that calls `catalogRepository.getCatalogEntryById()`
- The `pendingCatalogLinkId?.let { catalogRepository.linkToItem(it, savedId) }` call in save logic

- [ ] **Step 8: Update AppModule constructor calls**

In `AppModule.kt`, update all repository constructor calls to remove the `database.catalogEntryDao()` argument:
```kotlin
// BrandRepository - remove catalogEntryDao argument
private val _brandRepository by lazy {
    BrandRepository(database.brandDao(), database.itemDao(), database)
}
// CategoryRepository - remove catalogEntryDao argument
private val _categoryRepository by lazy {
    CategoryRepository(database.categoryDao(), database.itemDao(), database)
}
// StyleRepository - remove catalogEntryDao argument
// SeasonRepository - remove catalogEntryDao argument
// SourceRepository - remove catalogEntryDao argument
```

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/lolita/app/di/AppModule.kt app/src/main/java/com/lolita/app/data/repository/BrandRepository.kt app/src/main/java/com/lolita/app/data/repository/CategoryRepository.kt app/src/main/java/com/lolita/app/data/repository/StyleRepository.kt app/src/main/java/com/lolita/app/data/repository/SeasonRepository.kt app/src/main/java/com/lolita/app/data/repository/SourceRepository.kt app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt
git commit -m "chore: remove catalog references from AppModule and repositories"
```

---

### Task 5: Update navigation — Screen.kt and LolitaNavHost.kt

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/Screen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt`

- [ ] **Step 1: Remove catalog routes from Screen.kt**

Delete `CatalogDetail` (lines 31-34), `CatalogEdit` (lines 36-39), and `Wishlist` (lines 61-63) from `Screen.kt`.

- [ ] **Step 2: Remove `prefillCatalogEntryId` from ItemEdit route in Screen.kt**

Change the `ItemEdit` route:
```kotlin
data object ItemEdit : Screen {
    override val route = "item_edit/{itemId}?defaultStatus={defaultStatus}"
    fun createRoute(
        itemId: Long? = null,
        defaultStatus: String? = null
    ): String {
        val id = itemId ?: 0L
        val queryParams = buildList {
            if (defaultStatus != null) add("defaultStatus=$defaultStatus")
        }.joinToString("&")
        return if (queryParams.isNotEmpty()) "item_edit/$id?$queryParams" else "item_edit/$id"
    }
}
```

- [ ] **Step 3: Remove Wishlist bottom nav item**

In `LolitaNavHost.kt`, remove the wishlist entry from `BottomNavItems.items` (lines 103-107). The list becomes 4 items: 首页, 穿搭, 统计, 个人.

- [ ] **Step 4: Remove catalog-related imports from LolitaNavHost.kt**

Delete these import lines:
```kotlin
import com.lolita.app.ui.screen.catalog.CatalogDetailScreen
import com.lolita.app.ui.screen.catalog.CatalogEditScreen
import com.lolita.app.ui.screen.item.WishlistScreen
```

- [ ] **Step 5: Remove `prefillCatalogEntryId` from ItemEdit navArguments**

Lines ~348: Remove the `navArgument("prefillCatalogEntryId")` line. Lines ~353: Remove `val prefillCatalogEntryId = ...`. Lines ~357: Remove `prefillCatalogEntryId = if (prefillCatalogEntryId == 0L) null else prefillCatalogEntryId` parameter from `ItemEditScreen()` call.

- [ ] **Step 6: Remove Catalog Detail/Edit and Wishlist composable registrations**

Delete:
- Catalog Detail composable block (lines 363-399)
- Catalog Edit composable block (lines 401-412)
- Wishlist composable block (lines 481-493)

- [ ] **Step 7: Update ItemListScreen call site to remove catalog callbacks**

In the `composable(Screen.ItemList.route)` block, remove `onNavigateToCatalogDetail` and `onNavigateToCatalogAdd` parameters and their navigation lambdas.

- [ ] **Step 8: Update ItemEditScreen call site**

Remove `prefillCatalogEntryId` parameter from `ItemEditScreen()` call.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/navigation/Screen.kt app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt
git commit -m "chore: remove catalog/wishlist routes, update bottom nav to 4 tabs"
```

---

### Task 6: Convert ItemListScreen page 3 from Catalog to Wishlist

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt`

- [ ] **Step 1: Remove catalog imports**

Remove:
```kotlin
import com.lolita.app.ui.screen.catalog.CatalogFilterPanel
import com.lolita.app.ui.screen.catalog.CatalogListContent
import com.lolita.app.ui.screen.catalog.CatalogListViewModel
```

- [ ] **Step 2: Remove catalog callbacks from function signature**

Remove from `ItemListScreen` parameters:
```kotlin
onNavigateToCatalogDetail: (Long) -> Unit = {},
onNavigateToCatalogAdd: () -> Unit = {},
```

- [ ] **Step 3: Remove catalogViewModel from the composable**

Delete lines 109-110:
```kotlin
val catalogViewModel: CatalogListViewModel = viewModel()
val catalogUiState by catalogViewModel.uiState.collectAsState()
```

- [ ] **Step 4: Update the search bar routing**

In `SearchModeBar` (lines 254-265), remove the `if (pagerState.currentPage == 3)` branches:
```kotlin
SearchModeBar(
    query = uiState.searchQuery,
    onQueryChange = { viewModel.search(it) },
    onCancel = {
        viewModel.search("")
        isSearchMode = false
    },
    focusRequester = searchFocusRequester,
    placeholder = "搜索服饰"
)
```

- [ ] **Step 5: Update the NormalModeBar tab-aware logic**

In `NormalModeBar` (lines 268-349):
- `onFilterClick`: Remove `|| pagerState.currentPage == 3` (line 277-278), keep only `pagerState.currentPage == 1`
- `activeFilterCount`: Remove catalog count logic (lines 131-138), use `itemActiveFilterCount` directly
- `currentSort`: Remove catalog case (line 283-284), remove `case 3 -> catalogUiState.sortOption`
- `onSortSelected`: Remove catalog case (line 293-295), remove `3 -> catalogViewModel.setSortOption(it)`
- `onViewModeToggle`: Remove catalog case (lines 303-312), remove the `else if (pagerState.currentPage == 3)` block
- `viewModeIcon`: Remove catalog case (lines 338-341), remove `3 -> when (catalogUiState.viewMode)...`

- [ ] **Step 6: Remove CatalogFilterPanel**

Delete lines 367-376 (the `AnimatedVisibility(visible = showFilterPanel && pagerState.currentPage == 3)` block).

- [ ] **Step 7: Update pager page 3 content**

Replace lines 640-646:
```kotlin
// Before:
3 -> {
    CatalogListContent(
        uiState = catalogUiState,
        onNavigateToDetail = onNavigateToCatalogDetail,
        modifier = Modifier.fillMaxSize()
    )
}

// After:
3 -> {
    WishlistContent(
        onNavigateToDetail = onNavigateToDetail,
        onNavigateToEdit = onNavigateToEdit
    )
}
```

- [ ] **Step 8: Add WishlistContent composable to the file**

At the bottom of `ItemListScreen.kt` (before the last closing brace or in a suitable location), add a `WishlistContent` composable that wraps the wishlist UI from the old `WishlistScreen`. This composable uses `WishlistViewModel`, renders wishlist item cards, and handles delete confirmation dialogs. It does NOT include its own `Scaffold` or `GradientTopAppBar` (those are handled by ItemListScreen's outer structure).

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WishlistContent(
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToEdit: (Long?) -> Unit,
    viewModel: WishlistViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val deleteError by viewModel.errorMessage.collectAsState()
    var itemToDelete by remember { mutableStateOf<Item?>(null) }

    deleteError?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("提示") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) { Text("确定") }
            }
        )
    }

    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("确认删除") },
            text = { Text("确定要从愿望单删除 \"${itemToDelete?.name}\" 吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        itemToDelete?.let { viewModel.deleteItem(it) }
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    SkinIcon(IconKey.Delete, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("取消") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.allItems.isNotEmpty()) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.search(it) },
                placeholder = { Text("搜索心愿") },
                leadingIcon = { SkinIcon(IconKey.Search) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )
        }

        if (uiState.isLoading) {
            val shimmer = rememberShimmer(ShimmerBounds.Window)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(5) {
                    WishlistItemCardSkeleton(modifier = Modifier.shimmer(shimmer))
                }
            }
        } else if (uiState.allItems.isEmpty()) {
            SkinEmptyState(
                iconKey = IconKey.Wishlist,
                title = "心愿单为空",
                subtitle = "点击右下角 + 添加心仪的服饰",
                modifier = Modifier.fillMaxSize()
            )
        } else if (uiState.filteredItems.isEmpty()) {
            SkinEmptyState(
                iconKey = IconKey.Search,
                title = "无搜索结果",
                subtitle = "试试其他关键词",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val flingBehavior = rememberSkinFlingBehavior()
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                flingBehavior = flingBehavior
            ) {
                items(uiState.filteredItems, key = { it.id }) { item ->
                    val index = uiState.filteredItems.indexOf(item)
                    SwipeToDeleteContainer(
                        onDelete = { itemToDelete = item }
                    ) {
                        WishlistItemCard(
                            item = item,
                            onClick = { onNavigateToDetail(item.id) },
                            modifier = Modifier
                                .skinItemAppear(index)
                                .animateItem()
                        )
                    }
                }
            }
        }
    }
}
```

Note: The `WishlistItemCard`, `WishlistItemCardSkeleton` composables already exist in `WishlistScreen.kt`. Since we're deleting that file, copy those composables into `ItemListScreen.kt` as private functions. The `WishlistViewModel` class also needs to stay — move it from `WishlistScreen.kt` into its own file or keep it in `ItemListScreen.kt`.

- [ ] **Step 9: Move WishlistViewModel and wishlist composables**

Since `WishlistScreen.kt` is being deleted but `WishlistViewModel`, `WishlistItemCard`, `WishlistItemCardSkeleton`, and `WishlistUiState` are still needed:

Option A (simpler, fewer files): Keep them in `ItemListScreen.kt` (already shown above).

Option B (cleaner): Extract to `app/src/main/java/com/lolita/app/ui/screen/item/WishlistContent.kt` containing `WishlistUiState`, `WishlistViewModel`, `WishlistContent`, `WishlistItemCard`, and `WishlistItemCardSkeleton`.

Choose Option B for cleanliness.

- [ ] **Step 10: Update pager tab labels**

In `NormalModeBar`, line 668, change:
```kotlin
val pageTabs = listOf("位置", "服饰", "套装", "图鉴")
```
to:
```kotlin
val pageTabs = listOf("位置", "服饰", "套装", "心愿")
```

- [ ] **Step 11: Update FAB action for page 3**

In the `FloatingActionButton` onClick (lines 198-203), change page 3 from `onNavigateToCatalogAdd()` to:
```kotlin
3 -> onNavigateToEdit(null)  // Add new wishlist item (same as page 1)
```

- [ ] **Step 12: Delete WishlistScreen.kt (the standalone screen)**

```bash
git rm app/src/main/java/com/lolita/app/ui/screen/item/WishlistScreen.kt
```

- [ ] **Step 13: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt app/src/main/java/com/lolita/app/ui/screen/item/WishlistContent.kt
git commit -m "feat: replace catalog tab with wishlist in homepage, remove wishlist bottom nav"
```

---

### Task 7: Clean up SharedLibrarySync catalog references

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/local/entity/SharedLibrarySyncEntities.kt`
- Modify: `app/src/main/java/com/lolita/app/data/local/dao/SharedLibrarySyncDao.kt`
- Modify: `app/src/main/java/com/lolita/app/data/repository/SharedLibrarySyncRepository.kt`
- Modify: `app/src/main/java/com/lolita/app/data/remote/SharedLibrarySyncApi.kt` (DTOs)

- [ ] **Step 1: Remove RemoteCatalogEntry entity**

In `SharedLibrarySyncEntities.kt`, delete the `RemoteCatalogEntry` data class and its table definition.

- [ ] **Step 2: Remove RemoteCatalogEntry from SharedLibrarySyncDao**

Delete any DAO methods that reference `RemoteCatalogEntry` (e.g., insert, query, delete for `remote_catalog_entries`).

- [ ] **Step 3: Remove catalog sync logic from SharedLibrarySyncRepository**

Delete methods that sync catalog entries. Remove `catalogEntryDao` or `sharedLibrarySyncDao.catalogEntry*()` calls. If the entire SharedLibrarySyncRepository becomes empty or only syncs brands/categories, trim accordingly.

- [ ] **Step 4: Remove catalog DTOs from SharedLibrarySyncApi**

In `SharedLibrarySyncApi.kt`, remove `CatalogEntrySyncDto`, `catalogEntries` fields from `SnapshotPayloadDto` and `ChangesPayloadDto`, and `catalogEntryPublicId` from any remaining DTOs.

- [ ] **Step 5: Remove SharedLibrarySyncRepository from AppModule if empty**

If the sync repository is now empty, remove it from `AppModule.kt`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/
git commit -m "chore: remove catalog sync from SharedLibrarySync system"
```

---

### Task 8: Database migration — drop catalog tables

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/local/LolitaDatabase.kt`

- [ ] **Step 1: Remove CatalogEntry and RemoteCatalogEntry from @Database entities**

Remove lines 27 and 34 from the `@Database` annotation:
```kotlin
// Remove:
CatalogEntry::class,
// Remove:
RemoteCatalogEntry::class,
```

- [ ] **Step 2: Remove catalogEntryDao() abstract method**

Remove line 55:
```kotlin
abstract fun catalogEntryDao(): CatalogEntryDao
```

- [ ] **Step 3: Bump database version**

Change line 39:
```kotlin
version = 19,
```

- [ ] **Step 4: Add migration 18→19**

Add a new `Migration(18, 19)` object that drops the catalog tables:
```kotlin
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS catalog_entries")
        db.execSQL("DROP TABLE IF EXISTS remote_catalog_entries")
    }
}
```

- [ ] **Step 5: Add migration to the database builder**

In the `getInstance()` method, add `MIGRATION_18_19` to the list of migrations:
```kotlin
.addMigrations(
    MIGRATION_1_2, MIGRATION_2_3, ...,
    MIGRATION_18_19
)
```

- [ ] **Step 6: Remove unused imports**

Remove imports for `CatalogEntry`, `RemoteCatalogEntry`, and `CatalogEntryDao`.
Also remove the import for `SharedLibrarySyncEntities.RemoteCatalogEntry` if it's imported separately.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/local/LolitaDatabase.kt
git commit -m "chore: migrate database to v19, drop catalog tables"
```

---

### Task 9: Update BackupManager

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/file/BackupManager.kt`

- [ ] **Step 1: Remove catalog entry export/import logic**

Search for all `catalogEntry` and `CatalogEntry` references in `BackupManager.kt` and remove:
- Catalog entry export section (exporting catalog_entries table data)
- Catalog entry import section (parsing and inserting catalog entries from backup JSON)
- Any `catalogEntryDao` or `catalogRepository` references in the BackupManager constructor or methods

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/file/BackupManager.kt
git commit -m "chore: remove catalog from BackupManager export/import"
```

---

### Task 10: Version bump and release build

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Bump version**

```kotlin
versionCode = 59
versionName = "2.35.0"
```

- [ ] **Step 2: Commit**

```bash
git add app/build.gradle.kts
git commit -m "chore: bump version to 2.35.0 (59)"
```

- [ ] **Step 3: Build release APK**

```bash
./gradlew.bat clean assembleRelease
```

Expected: BUILD SUCCESSFUL. If build fails, fix compilation errors and re-run.

---

### Task 11: Final verification

- [ ] **Step 1: Verify app launches with migrated database**

Install the release APK on a device that had the previous version installed. Verify:
- App launches without crashing
- Bottom nav shows 4 tabs: 首页, 穿搭, 统计, 个人
- 首页 has 4 pager tabs: 位置, 服饰, 套装, 心愿
- 心愿 tab shows existing wishlist items
- Gallery mode search/filter works correctly (filter in gallery, verify results update)

- [ ] **Step 2: Verify no catalog references remain**

```bash
git grep -i "catalog" -- "*.kt" "*.java"
```

Only remaining matches should be benign (e.g., comments, or the word appearing in unrelated context). If any code references remain, fix them.
