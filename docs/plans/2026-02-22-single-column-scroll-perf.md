# 单列列表滑动性能优化 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Optimize the single-column LazyColumn scroll performance in ItemListScreen to eliminate frame drops during fast scrolling.

**Architecture:** Introduce a `LocalIsListScrolling` CompositionLocal to decouple scroll state from item recomposition. Pause background animations during scroll. Bundle item display data into an `@Immutable` class to help Compose skip recomposition. Lazy-load DropdownMenus and constrain AsyncImage decode size.

**Tech Stack:** Kotlin, Jetpack Compose, Coil 2.7.0, Room

---

### Task 1: Create LocalIsListScrolling CompositionLocal

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/LocalIsListScrolling.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt:150-161`

**Step 1: Create LocalIsListScrolling definition**

Create `app/src/main/java/com/lolita/app/ui/theme/skin/animation/LocalIsListScrolling.kt`:

```kotlin
package com.lolita.app.ui.theme.skin.animation

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf

val LocalIsListScrolling = staticCompositionLocalOf<MutableState<Boolean>> {
    mutableStateOf(false)
}
```

**Step 2: Provide LocalIsListScrolling in LolitaNavHost**

In `LolitaNavHost.kt`, around line 150-161, wrap the `Box` containing `SkinBackgroundAnimation` and `NavHost` with a `CompositionLocalProvider`:

```kotlin
// Add imports:
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import com.lolita.app.ui.theme.skin.animation.LocalIsListScrolling

// Around line 150, replace:
//     Box {
//         SkinBackgroundAnimation(...)
//         NavHost(...)
//     }
// With:
    val isListScrolling = remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalIsListScrolling provides isListScrolling) {
        Box {
            SkinBackgroundAnimation(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
            NavHost(...)  // keep existing NavHost unchanged
        }
    }
```

**Step 3: Build**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/LocalIsListScrolling.kt app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt
git commit -m "perf: add LocalIsListScrolling CompositionLocal for scroll state sharing"
```

---

### Task 2: Pause SkinBackgroundAnimation during scroll

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinBackgroundAnimation.kt:34-66`

**Step 1: Read LocalIsListScrolling and skip frame updates when scrolling**

In `SkinBackgroundAnimation.kt`, modify the composable function:

```kotlin
// Add import:
import androidx.compose.runtime.getValue

@Composable
fun SkinBackgroundAnimation(
    modifier: Modifier = Modifier
) {
    val skin = LolitaSkin.current
    val spec = skin.animations.ambientAnimation

    if (!spec.backgroundEnabled) return

    val isScrolling by LocalIsListScrolling.current

    val particles = remember(skin.skinType) {
        createParticles(skin.skinType, spec.backgroundParticleCount)
    }

    val frameTime = remember { mutableLongStateOf(0L) }

    LaunchedEffect(skin.skinType) {
        particles.forEach { it.reset(1080f, 1920f) }
        while (isActive) {
            delay(16L)
            if (!isScrolling) {
                frameTime.longValue = System.nanoTime() / 1_000_000L
            }
        }
    }

    Canvas(modifier.fillMaxSize()) {
        @Suppress("UNUSED_VARIABLE")
        val currentFrame = frameTime.longValue
        if (!isScrolling) {
            particles.forEach { particle ->
                particle.update(16L, size.width, size.height)
                with(particle) { draw() }
            }
        }
    }
}
```

Key change: when `isScrolling` is true, `frameTime` stops updating (no Canvas invalidation) and particles stop updating/drawing. The Canvas still exists but doesn't redraw, freeing GPU for the list.

Note: `isScrolling` is read via delegate (`by`) so it's a snapshot state read. Inside `LaunchedEffect`, the read of `isScrolling` happens each loop iteration because it's a state read in a coroutine — this works correctly with Compose snapshot system.

**Step 2: Build**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinBackgroundAnimation.kt
git commit -m "perf: pause SkinBackgroundAnimation during list scroll"
```

---

### Task 3: Modify SkinCardGlow to use CompositionLocal

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinCardGlow.kt:29-33`

**Step 1: Replace isScrolling parameter with CompositionLocal read**

```kotlin
// Add import:
import androidx.compose.runtime.getValue

@Composable
fun Modifier.skinCardGlow(): Modifier {
    val skin = LolitaSkin.current
    if (!skin.animations.ambientAnimation.cardGlowEffect) return this
    val isScrolling by LocalIsListScrolling.current
    if (isScrolling) return this

    // ... rest unchanged
}
```

Remove the `isScrolling: Boolean = false` parameter entirely.

**Step 2: Build**

Run: `./gradlew.bat assembleDebug`
Expected: FAIL — `LolitaCard.kt` still passes `isScrolling` param. This is expected, fixed in Task 4.

---

### Task 4: Modify LolitaCard to remove isScrolling parameter

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/common/LolitaCard.kt:10-18`

**Step 1: Remove isScrolling parameter and update skinCardGlow call**

```kotlin
@Composable
fun LolitaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val cardShape = LolitaSkin.current.cardShape
    val glowModifier = modifier.skinCardGlow()
    // ... rest unchanged (if/else Card blocks stay the same)
}
```

**Step 2: Build**

Run: `./gradlew.bat assembleDebug`
Expected: FAIL — `ItemListScreen.kt` still passes `isScrolling` to `LolitaCard`. This is expected, fixed in Task 6.

**Step 3: Commit Tasks 3+4 together**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinCardGlow.kt app/src/main/java/com/lolita/app/ui/screen/common/LolitaCard.kt
git commit -m "perf: SkinCardGlow reads LocalIsListScrolling directly, remove isScrolling param chain"
```

---

### Task 5: Create @Immutable ItemCardData and update ViewModel

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt:36-62`

**Step 1: Add ItemCardData class**

Add above `ItemListUiState` (around line 36):

```kotlin
import androidx.compose.runtime.Immutable

@Immutable
data class ItemCardData(
    val item: Item,
    val brandName: String?,
    val categoryName: String?,
    val itemPrice: Double?,
    val showPrice: Boolean
)
```

**Step 2: Add itemCardDataList to ItemListUiState**

Add a new field to `ItemListUiState`:

```kotlin
data class ItemListUiState(
    // ... existing fields unchanged ...
    val itemCardDataList: List<ItemCardData> = emptyList()
)
```

**Step 3: Build ItemCardData list in ViewModel**

Add a private helper method to `ItemListViewModel`:

```kotlin
private fun buildItemCardDataList(
    items: List<Item>,
    brandNames: Map<Long, String>,
    categoryNames: Map<Long, String>,
    itemPrices: Map<Long, Double>,
    showPrice: Boolean
): List<ItemCardData> {
    return items.map { item ->
        ItemCardData(
            item = item,
            brandName = brandNames[item.brandId],
            categoryName = categoryNames[item.categoryId],
            itemPrice = itemPrices[item.id],
            showPrice = showPrice
        )
    }
}
```

**Step 4: Update all _uiState.update calls that change filteredItems**

Every place that updates `filteredItems` must also rebuild `itemCardDataList`. There are multiple call sites in the ViewModel:

In `loadItems()` (line ~158), after setting `filteredItems = sorted`, add:
```kotlin
itemCardDataList = buildItemCardDataList(sorted, data.brandMap, data.categoryMap, data.priceMap, it.showTotalPrice)
```

In `filterByStatus()`, `filterByGroup()`, `filterBySeason()`, `filterByStyle()`, `filterByColor()`, `filterByBrand()`, `search()`, `setSortOption()` — each has a pattern like:
```kotlin
_uiState.update { it.copy(filterXxx = xxx, filteredItems = sorted) }
```
Change each to also include:
```kotlin
_uiState.update { it.copy(
    filterXxx = xxx,
    filteredItems = sorted,
    itemCardDataList = buildItemCardDataList(sorted, it.brandNames, it.categoryNames, it.itemPrices, it.showTotalPrice)
) }
```

Also in `loadPreferences()` where `showTotalPrice` changes (line ~131):
```kotlin
_uiState.update {
    val newShow = show
    it.copy(
        showTotalPrice = newShow,
        itemCardDataList = buildItemCardDataList(it.filteredItems, it.brandNames, it.categoryNames, it.itemPrices, newShow)
    )
}
```

**Step 5: Build**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL (ItemListScreen not yet changed, so old code still compiles)

**Step 6: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt
git commit -m "perf: add @Immutable ItemCardData to reduce recomposition in item list"
```

---

### Task 6: Update ItemListScreen — all remaining changes

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt`

This task covers 4 changes in ItemListScreen:
- (A) Provide LocalIsListScrolling from scroll state
- (B) Remove isScrolling param from ItemCard/ItemGridCard
- (C) Use ItemCardData in LazyColumn
- (D) Lazy DropdownMenu + AsyncImage size constraint

**Step 1: Add imports**

```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil.request.ImageRequest
import com.lolita.app.ui.theme.skin.animation.LocalIsListScrolling
```

**Step 2: Update LazyColumn section (lines ~333-365)**

Replace the single-column branch:

```kotlin
} else if (uiState.columnsPerRow == 1) {
    val flingBehavior = rememberSkinFlingBehavior()
    val listState = rememberLazyListState()
    val scrollingState = LocalIsListScrolling.current
    LaunchedEffect(listState.isScrollInProgress) {
        scrollingState.value = listState.isScrollInProgress
    }
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        flingBehavior = flingBehavior
    ) {
        itemsIndexed(
            items = uiState.itemCardDataList,
            key = { _, data -> data.item.id }
        ) { index, data ->
            SwipeToDeleteContainer(
                onDelete = { itemToDelete = data.item }
            ) {
                ItemCard(
                    data = data,
                    onClick = { onNavigateToDetail(data.item.id) },
                    onEdit = { onNavigateToEdit(data.item.id) },
                    onDelete = { itemToDelete = data.item },
                    modifier = Modifier
                        .skinItemAppear(index)
                        .animateItem()
                )
            }
        }
    }
```

**Step 3: Update LazyVerticalGrid section (lines ~367-392)**

```kotlin
} else {
    val gridState = rememberLazyGridState()
    val scrollingState = LocalIsListScrolling.current
    LaunchedEffect(gridState.isScrollInProgress) {
        scrollingState.value = gridState.isScrollInProgress
    }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(uiState.columnsPerRow),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(
            items = uiState.itemCardDataList,
            key = { it.item.id }
        ) { data ->
            ItemGridCard(
                data = data,
                onClick = { onNavigateToDetail(data.item.id) },
                onEdit = { onNavigateToEdit(data.item.id) },
                onDelete = { itemToDelete = data.item }
            )
        }
    }
}
```

**Step 4: Update ItemCard signature and body (lines ~446-638)**

Change ItemCard to accept `ItemCardData`:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemCard(
    data: ItemCardData,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val item = data.item
    val brandName = data.brandName
    val categoryName = data.categoryName
    val itemPrice = data.itemPrice
    val showPrice = data.showPrice
    var showMenu by remember { mutableStateOf(false) }

    LolitaCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        // ... rest of the Row content stays the same,
        // just references local vals above instead of params
```

Inside ItemCard, for the AsyncImage (around line 472), add size constraint:

```kotlin
val context = LocalContext.current
val density = LocalDensity.current
val imageSizePx = with(density) { 80.dp.roundToPx() }

if (item.imageUrl != null) {
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(java.io.File(item.imageUrl))
            .size(imageSizePx)
            .crossfade(true)
            .build(),
        contentDescription = item.name,
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Crop
    )
}
```

For the DropdownMenu, make it lazy (around line 532):

```kotlin
Box {
    IconButton(onClick = { showMenu = true }) {
        SkinIcon(IconKey.Edit, tint = MaterialTheme.colorScheme.primary)
    }
    if (showMenu) {
        DropdownMenu(
            expanded = true,
            onDismissRequest = { showMenu = false }
        ) {
            // ... menu items unchanged
        }
    }
}
```

**Step 5: Update ItemGridCard similarly (lines ~640+)**

Change signature to accept `ItemCardData`, remove `isScrolling` param:

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemGridCard(
    data: ItemCardData,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val item = data.item
    val brandName = data.brandName
    val categoryName = data.categoryName
    val itemPrice = data.itemPrice
    val showPrice = data.showPrice
    var showMenu by remember { mutableStateOf(false) }

    LolitaCard(
        modifier = modifier.fillMaxWidth()
    ) {
        // ... rest unchanged, just uses local vals
```

Also apply AsyncImage size constraint in ItemGridCard (the grid uses `aspectRatio(0.8f)` so use a reasonable size like 200dp):

```kotlin
val context = LocalContext.current
val density = LocalDensity.current
val imageSizePx = with(density) { 200.dp.roundToPx() }

AsyncImage(
    model = ImageRequest.Builder(context)
        .data(java.io.File(item.imageUrl!!))
        .size(imageSizePx)
        .crossfade(true)
        .build(),
    // ... rest unchanged
)
```

And make DropdownMenu lazy in ItemGridCard too (same `if (showMenu)` pattern).

**Step 6: Build**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 7: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt
git commit -m "perf: optimize ItemListScreen scroll — CompositionLocal, ItemCardData, lazy menu, image sizing"
```

---

### Task 7: Version bump + release build

**Files:**
- Modify: `app/build.gradle.kts` — bump versionCode and versionName

**Step 1: Bump version**

This is a performance optimization (patch-level change). Bump:
- `versionCode` from current value +1
- `versionName` to next patch (e.g. `2.1.1` → `2.1.2`)

Check current values first, then increment accordingly.

**Step 2: Release build**

Run: `./gradlew.bat assembleRelease`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "chore: bump version for scroll performance optimization"
```

---

## Summary of all changes

| File | Change |
|------|--------|
| `LocalIsListScrolling.kt` (new) | CompositionLocal definition |
| `LolitaNavHost.kt` | Provide `LocalIsListScrolling` at top level |
| `SkinBackgroundAnimation.kt` | Pause particle updates during scroll |
| `SkinCardGlow.kt` | Read `LocalIsListScrolling` instead of param |
| `LolitaCard.kt` | Remove `isScrolling` param |
| `ItemViewModel.kt` | Add `@Immutable ItemCardData`, build list in ViewModel |
| `ItemListScreen.kt` | Use `ItemCardData`, provide scroll state, lazy DropdownMenu, AsyncImage size |
| `build.gradle.kts` | Version bump |
