# Fix Gallery Empty on First Load Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix the race condition where `galleryCardDataList` is empty when the app first opens in GALLERY view mode, because `loadPreferences()` reads the saved GALLERY mode before `loadItems()` has finished populating `itemCardDataList`.

**Architecture:** The fix is in `ItemListViewModel.loadItems()`. When it finishes building `itemCardDataList`, it should also rebuild `galleryCardDataList` if the current `viewMode` is already `GALLERY`. This ensures whichever coroutine finishes last wins correctly.

**Tech Stack:** Kotlin, Jetpack Compose, StateFlow, Room, DataStore

---

### Task 1: Fix the race condition in ItemListViewModel

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt:235-250`

**Step 1: Read the current `loadItems()` update block**

Open `ItemViewModel.kt` around line 228–253 and confirm the current `_uiState.update` call inside `collect { data -> ... }` does NOT rebuild `galleryCardDataList`.

**Step 2: Apply the fix**

In `loadItems()`, inside the `collect { data -> ... }` lambda, replace the existing `_uiState.update` call with:

```kotlin
val newItemCardDataList = buildItemCardDataList(
    sorted, data.brandMap, data.brandLogoMap, data.categoryMap, data.priceMap, it.showTotalPrice
)
_uiState.update {
    it.copy(
        items = data.items,
        filteredItems = sorted,
        brandNames = data.brandMap,
        brandLogoUrls = data.brandLogoMap,
        categoryNames = data.categoryMap,
        categoryGroups = data.groupMap,
        itemPrices = data.priceMap,
        seasonOptions = data.seasonOpts,
        styleOptions = data.styleOpts,
        colorOptions = data.colorOpts,
        isLoading = false,
        itemCardDataList = newItemCardDataList,
        galleryCardDataList = if (it.viewMode == ViewMode.GALLERY) {
            newItemCardDataList.filter { d -> d.item.imageUrls.isNotEmpty() }.shuffled()
        } else it.galleryCardDataList
    )
}
```

Key change: the new update checks `it.viewMode == ViewMode.GALLERY` and rebuilds `galleryCardDataList` from the freshly built `newItemCardDataList` if needed. If not in GALLERY mode, it preserves the existing value (which is `emptyList()` normally).

**Step 3: Verify the existing `loadPreferences()` collect block is unchanged**

Lines 195–206 handle the case when the user switches view mode at runtime or when preferences load first (before items). This block correctly uses `it.itemCardDataList` at that point. Since the fix ensures `loadItems()` also refreshes `galleryCardDataList`, whichever finishes last will have the correct data.

**Step 4: Build and verify**

```bash
./gradlew.bat assembleDebug
```

Expected: BUILD SUCCESSFUL with no compilation errors.

**Step 5: Bump version**

In `app/build.gradle.kts`, increment:
- `versionCode` from 23 to 24
- `versionName` from `"2.12"` to `"2.12.1"` (bug fix → patch bump)

**Step 6: Release build**

```bash
./gradlew.bat assembleRelease
```

Expected: BUILD SUCCESSFUL

**Step 7: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt
git add app/build.gradle.kts
git commit -m "fix: rebuild galleryCardDataList in loadItems to fix empty gallery on first launch"
```
