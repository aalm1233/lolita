# Design: Search/Catalog/Wishlist Cleanup

Date: 2026-05-12

## Change 1: Fix gallery mode search/filter staleness bug

**Root cause:** `ItemListViewModel` has 9 methods that update `filteredItems` and `itemCardDataList` but neglect `galleryCardDataList`:
`search()`, `filterByBrand()`, `filterBySeason()`, `filterByStyle()`, `filterByColor()`,
`filterByGroup()`, `filterByStatuses()`, `togglePendingBalanceOnly()`, `setSortOption()`.

When the user is in GALLERY view mode, searching or applying a filter updates the
underlying `filteredItems` and the list/grid `itemCardDataList`, but `galleryCardDataList`
still holds the pre-filter data. The staggered grid rendering reads from
`galleryCardDataList`, so it displays stale results.

**Fix:** In each of the 9 methods, after computing `sorted`, also recompute
`galleryCardDataList`:

```kotlin
galleryCardDataList = if (_uiState.value.viewMode == ViewMode.GALLERY) {
    buildGalleryCardDataList(sorted, ...)
} else emptyList()
```

CatalogViewModel uses a centralized `refreshFilteredEntries()` that correctly updates
`galleryCardDataList`, so it does NOT have this bug. And it will be deleted in Change 2
regardless.

## Change 2: Remove Catalog, merge wishlist into homepage

**Goal:** Cancel the Catalog (图鉴) feature entirely. Repurpose the wishlist (愿望单) as a
"心愿" (Wishes) tab on the homepage, removing the dedicated bottom nav tab.

**Detailed changes:**

### 2a. Delete Catalog subsystem
- Delete `CatalogEntry.kt` entity
- Delete `CatalogEntryDao.kt`
- Delete `CatalogRepository.kt`
- Delete `CatalogViewModel.kt` (3 ViewModels: list/detail/edit)
- Delete `CatalogListContent.kt`
- Delete `CatalogDetailScreen.kt`
- Delete `CatalogEditScreen.kt`
- Remove `catalogRepository` from `AppModule.kt`

### 2b. Delete wishlist standalone screen
- Delete `WishlistScreen.kt` (content moves into ItemListScreen's pager)

### 2c. Modify ItemListScreen
- 4th pager tab changes from "图鉴" to "心愿"
- Tab content embeds wishlist via a shared `WishlistContent` composable extracted from the
  current `WishlistScreen.kt` (keep the ViewModel logic, move the UI)
- Wishlist tab still creates items with `defaultStatus = "WISHED"` — the `defaultStatus`
  parameter on `ItemEdit` route stays
- Remove catalog-specific UI branches (search routing split for page==3, catalog filter
  panel, catalog view mode toggle)

### 2d. Navigation changes
- `Screen.kt`: Remove `CatalogDetail`, `CatalogEdit`, `Wishlist`, `SkinIconGallery` routes
- `Screen.kt`: Remove `prefillCatalogEntryId` from `ItemEdit` route args; `defaultStatus` stays (still used by wishlist tab)
- `LolitaNavHost.kt`: Remove catalog detail/edit composable registrations
- `LolitaNavHost.kt`: Remove wishlist composable registration
- `LolitaNavHost.kt`: Remove bottom nav item for wishlist (5 tabs → 4 tabs)
- Bottom nav tabs become: 首页, 穿搭, 统计, 个人

### 2e. Database migration
- Increment `LolitaDatabase` version (18 → 19)
- Add migration to DROP TABLE `catalog_entries`
- Remove `CatalogEntry` from `@Database(entities = [...])`

### 2f. Cleanup
- `ItemEditScreen` / `ItemEditViewModel`: Remove `prefillCatalogEntryId` parameter and catalog-entry prefill logic
- `ItemDao.kt` / `ItemRepository.kt`: Remove any catalog-related queries (e.g. findByLinkedItemId)
- `Item.kt` / `ItemStatus.WISHED`: Stays — wishlist items are still Items with WISHED status
- `AppModule.kt`: Remove `CatalogRepository` lazy singleton

## Change 3: Remove skin icon gallery debug feature

**Goal:** Remove the debug tool used during skin icon system development.

**Changes:**
- Delete `SkinIconGalleryScreen.kt` (entire file ~290 lines)
- `ThemeSelectScreen.kt`: Remove the ElevatedCard with "查看当前皮肤图标总览" button (lines 86-117) and the `onNavigateToIconGallery` parameter
- `Screen.kt`: Remove `SkinIconGallery` route
- `LolitaNavHost.kt`: Remove import and composable registration

## Version bump

`2.34.6` → `2.35.0` (minor: feature removal + bug fix)
