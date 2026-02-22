# 服饰列表滑动性能优化 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将服饰列表滑动卡顿从 3s+ 优化到流畅滚动（<1s 响应）

**Architecture:** 通过滑动时禁用 cardGlow 动画、消除 O(n²) indexOf 查找、限制出场动画范围来减少滑动时的重组和重绘开销

**Tech Stack:** Kotlin, Jetpack Compose, LazyListState/LazyGridState

---

### Task 1: SkinCardGlow — 增加 isScrolling 参数

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinCardGlow.kt:29-46`

**Step 1: 修改 skinCardGlow 函数签名和逻辑**

```kotlin
@Composable
fun Modifier.skinCardGlow(isScrolling: Boolean = false): Modifier {
    val skin = LolitaSkin.current
    if (!skin.animations.ambientAnimation.cardGlowEffect) return this
    if (isScrolling) return this

    val infiniteTransition = rememberInfiniteTransition(label = "cardGlow")
    val glowProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cardGlowProgress"
    )

    return this.then(SkinCardGlowModifier(skin.skinType, glowProgress))
}
```

关键点：`isScrolling = true` 时提前返回 `this`，跳过 `rememberInfiniteTransition` 的创建和绘制。

**Step 2: 构建验证**

Run: `cd D:\java\lolita && .\gradlew.bat assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinCardGlow.kt
git commit -m "perf: add isScrolling param to skinCardGlow to skip animation during scroll"
```

---

### Task 2: LolitaCard — 透传 isScrolling 参数

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/common/LolitaCard.kt:10-36`

**Step 1: 增加 isScrolling 参数并透传给 skinCardGlow**

```kotlin
@Composable
fun LolitaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    isScrolling: Boolean = false,
    content: @Composable () -> Unit
) {
    val cardShape = LolitaSkin.current.cardShape
    val glowModifier = modifier.skinCardGlow(isScrolling = isScrolling)
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = glowModifier,
            shape = cardShape,
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            content()
        }
    } else {
        Card(
            modifier = glowModifier,
            shape = cardShape,
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            content()
        }
    }
}
```

默认 `isScrolling = false`，不影响其他 22 个调用方。只有 ItemListScreen 传入 `true`。

**Step 2: 构建验证**

Run: `cd D:\java\lolita && .\gradlew.bat assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/common/LolitaCard.kt
git commit -m "perf: add isScrolling param to LolitaCard for scroll optimization"
```

---

### Task 3: SkinItemAppear — 首屏阈值限制

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinItemAppear.kt:12-45`

**Step 1: 增加首屏阈值判断，超过阈值直接跳过动画**

```kotlin
private const val APPEAR_ANIMATION_THRESHOLD = 10

@Composable
fun Modifier.skinItemAppear(index: Int): Modifier {
    if (index >= APPEAR_ANIMATION_THRESHOLD) {
        return this
    }

    val skin = LolitaSkin.current
    val spec = skin.animations.listAnimation
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay((index * spec.staggerDelayMs).toLong())
        animProgress.animateTo(1f, animationSpec = spec.animationSpec)
    }

    val progress = animProgress.value

    val offsetX = when (spec.appearDirection) {
        AppearDirection.FROM_LEFT -> spec.appearOffsetPx * (1f - progress)
        else -> 0f
    }
    val offsetY = when (spec.appearDirection) {
        AppearDirection.FROM_BOTTOM -> spec.appearOffsetPx * (1f - progress)
        else -> 0f
    }
    val scale = when (spec.appearDirection) {
        AppearDirection.FADE_SCALE -> 0.9f + 0.1f * progress
        else -> 1f
    }

    return this.graphicsLayer {
        translationX = offsetX
        translationY = offsetY
        scaleX = scale
        scaleY = scale
        alpha = progress
    }
}
```

关键点：`index >= 10` 时直接返回 `this`，不创建 `Animatable` 和 `LaunchedEffect`。首屏约 4-5 个 item 可见，阈值 10 留有余量。

**Step 2: 构建验证**

Run: `cd D:\java\lolita && .\gradlew.bat assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinItemAppear.kt
git commit -m "perf: skip appear animation for items beyond first screen threshold"
```

---

### Task 4: ItemListScreen — itemsIndexed + isScrollInProgress 传递

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt:330-384`

**Step 1: LazyColumn 路径 — 添加 LazyListState + itemsIndexed + isScrolling 透传**

在 `else if (uiState.columnsPerRow == 1)` 分支内，改为：

```kotlin
} else if (uiState.columnsPerRow == 1) {
    val flingBehavior = rememberSkinFlingBehavior()
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        flingBehavior = flingBehavior
    ) {
        itemsIndexed(
            items = uiState.filteredItems,
            key = { _, item -> item.id }
        ) { index, item ->
            SwipeToDeleteContainer(
                onDelete = { itemToDelete = item }
            ) {
                ItemCard(
                    item = item,
                    brandName = uiState.brandNames[item.brandId],
                    categoryName = uiState.categoryNames[item.categoryId],
                    itemPrice = uiState.itemPrices[item.id],
                    showPrice = uiState.showTotalPrice,
                    onClick = { onNavigateToDetail(item.id) },
                    onEdit = { onNavigateToEdit(item.id) },
                    onDelete = { itemToDelete = item },
                    isScrolling = listState.isScrollInProgress,
                    modifier = Modifier
                        .skinItemAppear(index)
                        .animateItem()
                )
            }
        }
    }
```

改动点：
1. `rememberLazyListState()` 创建 state
2. `items` → `itemsIndexed`，消除 `indexOf()`
3. `listState.isScrollInProgress` 传给 `ItemCard`

**Step 2: ItemCard — 增加 isScrolling 参数并传给 LolitaCard**

```kotlin
private fun ItemCard(
    item: Item,
    brandName: String?,
    categoryName: String?,
    itemPrice: Double?,
    showPrice: Boolean = true,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    isScrolling: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    LolitaCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        isScrolling = isScrolling
    ) {
```

**Step 3: LazyVerticalGrid 路径 — 添加 LazyGridState + isScrolling 透传**

在 `else` (grid) 分支内，改为：

```kotlin
} else {
    val gridState = rememberLazyGridState()
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(uiState.columnsPerRow),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(
            items = uiState.filteredItems,
            key = { it.id }
        ) { item ->
            ItemGridCard(
                item = item,
                brandName = uiState.brandNames[item.brandId],
                categoryName = uiState.categoryNames[item.categoryId],
                itemPrice = uiState.itemPrices[item.id],
                showPrice = uiState.showTotalPrice,
                onClick = { onNavigateToDetail(item.id) },
                onEdit = { onNavigateToEdit(item.id) },
                onDelete = { itemToDelete = item },
                isScrolling = gridState.isScrollInProgress
            )
        }
    }
}
```

**Step 4: ItemGridCard — 增加 isScrolling 参数并传给 LolitaCard**

```kotlin
private fun ItemGridCard(
    ...
    isScrolling: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    LolitaCard(
        modifier = modifier.fillMaxWidth(),
        isScrolling = isScrolling
    ) {
```

**Step 5: 添加必要的 import**

在 ItemListScreen.kt 顶部确保有：
```kotlin
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
```

**Step 6: 构建验证**

Run: `cd D:\java\lolita && .\gradlew.bat assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 7: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt
git commit -m "perf: use itemsIndexed and pass isScrollInProgress to disable glow during scroll"
```

---

### Task 5: 版本号更新 + Release 构建

**Files:**
- Modify: `app/build.gradle.kts`

**Step 1: 更新版本号**

按照项目规范，bug fix 级别更新 patch 版本。将 `versionCode` +1，`versionName` 更新为 patch 版本。

**Step 2: Release 构建**

Run: `cd D:\java\lolita && .\gradlew.bat assembleRelease 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "chore: bump version for scroll performance optimization"
```
