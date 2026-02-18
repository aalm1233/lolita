# Coordinate List 3 Fixes Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix coordinate column toggle not working, add item thumbnails to coordinate cards, and add swipe-to-delete for both item and coordinate lists in 1-column mode.

**Architecture:** Incremental changes to existing Compose UI. Extract a shared `SwipeToDeleteContainer` composable. Wire `CoordinateListViewModel` into `ItemListScreen` for column toggle. Render existing `itemImages` data in card components.

**Tech Stack:** Kotlin, Jetpack Compose, Material3 (SwipeToDismissBox), Coil

---

### Task 1: 修复套装列表列数切换 — ItemListScreen 接入 CoordinateListViewModel

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt`

**Step 1: 在 ItemListScreen 中创建 CoordinateListViewModel 实例**

在 `ItemListScreen` 函数体内，`val uiState by viewModel.uiState.collectAsState()` 之后增加：

```kotlin
val coordinateViewModel: CoordinateListViewModel = viewModel()
val coordinateUiState by coordinateViewModel.uiState.collectAsState()
```

需要增加 import：
```kotlin
import com.lolita.app.ui.screen.coordinate.CoordinateListViewModel
```

**Step 2: 列数切换按钮根据当前 tab 分发**

将 `IconButton` 的 onClick（约第 198-204 行）改为：

```kotlin
IconButton(
    onClick = {
        if (pagerState.currentPage == 2) {
            val next = when (coordinateUiState.columnsPerRow) { 1 -> 2; 2 -> 3; else -> 1 }
            coordinateViewModel.setColumns(next)
        } else {
            val next = when (uiState.columnsPerRow) { 1 -> 2; 2 -> 3; else -> 1 }
            viewModel.setColumns(next)
        }
    }
)
```

**Step 3: 切换图标根据当前 tab 读取对应 columnsPerRow**

将图标部分（约第 206-214 行）改为：

```kotlin
{
    val currentColumns = if (pagerState.currentPage == 2) coordinateUiState.columnsPerRow else uiState.columnsPerRow
    Icon(
        imageVector = when (currentColumns) {
            1 -> Icons.Default.ViewAgenda
            2 -> Icons.Default.GridView
            else -> Icons.Default.Apps
        },
        contentDescription = "切换列数",
        tint = Pink400
    )
}
```

**Step 4: 将 coordinateViewModel 传给 CoordinateListContent**

将第 347-350 行改为：

```kotlin
CoordinateListContent(
    onNavigateToDetail = onNavigateToCoordinateDetail,
    onNavigateToEdit = onNavigateToCoordinateEdit,
    viewModel = coordinateViewModel
)
```

**Step 5: Build 验证**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add -A && git commit -m "fix: coordinate column toggle now works by routing to CoordinateListViewModel"
```

---

### Task 2: 套装卡片展示关联服饰小圆形缩略图

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateListScreen.kt`

**Step 1: CoordinateCard（1列模式）增加缩略图行**

在 `CoordinateCard` 的件数标签 Row（第 265 行）中，在件数 Surface 之后、Row 闭合之前，增加服饰缩略图：

```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
    if (itemCount > 0) {
        Surface(color = Pink400.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
            Text(
                "${itemCount} 件",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Pink400,
                fontWeight = FontWeight.Medium
            )
        }
    }
    // 服饰缩略图
    if (itemImages.isNotEmpty()) {
        Spacer(Modifier.width(4.dp))
        Row {
            itemImages.take(4).filterNotNull().forEachIndexed { index, imageUrl ->
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .offset(x = (-6 * index).dp)
                        .border(1.dp, Color.White, CircleShape)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}
```

需要增加 import（如果尚未导入）：
```kotlin
import androidx.compose.foundation.border
import androidx.compose.ui.unit.IntOffset
```

注意：`border` 和 `CircleShape` 已在 import 中。`offset` 需要用 `Modifier.offset(x = dp)` 形式。

**Step 2: CoordinateGridCard（2/3列模式）增加缩略图行**

在 `CoordinateGridCard` 的信息区域 Column（第 369-389 行）中，在 description 之后增加：

```kotlin
// 服饰缩略图
if (itemImages.isNotEmpty()) {
    Row(modifier = Modifier.padding(top = 2.dp)) {
        itemImages.take(4).filterNotNull().forEachIndexed { index, imageUrl ->
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .offset(x = (-4 * index).dp)
                    .border(1.dp, Color.White, CircleShape)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
    }
}
```

**Step 3: Build 验证**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add -A && git commit -m "feat: show item thumbnails as overlapping circles in coordinate cards"
```

---

### Task 3: 提取公共 SwipeToDeleteContainer 组件

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/screen/common/SwipeToDeleteContainer.kt`

**Step 1: 创建 SwipeToDeleteContainer**

```kotlin
package com.lolita.app.ui.screen.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteContainer(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                false // Don't actually dismiss; let the dialog handle it
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart)
                    Color(0xFFFF5252) else Color.Transparent,
                label = "swipe-bg"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = Color.White
                )
            }
        }
    ) {
        content()
    }
}
```

**Step 2: Build 验证**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add -A && git commit -m "feat: extract reusable SwipeToDeleteContainer component"
```

---

### Task 4: 套装列表1列模式增加左滑删除

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateListScreen.kt`

**Step 1: 在 LazyColumn 的 items 中包裹 SwipeToDeleteContainer**

在 `CoordinateListContent` 的1列模式 LazyColumn（第 145-162 行），将 items 块改为：

```kotlin
items(uiState.coordinates, key = { it.id }) { coordinate ->
    SwipeToDeleteContainer(
        onDelete = { coordinateToDelete = coordinate }
    ) {
        CoordinateCard(
            coordinate = coordinate,
            itemCount = uiState.itemCounts[coordinate.id] ?: 0,
            itemImages = uiState.itemImagesByCoordinate[coordinate.id] ?: emptyList(),
            totalPrice = uiState.priceByCoordinate[coordinate.id] ?: 0.0,
            onClick = { onNavigateToDetail(coordinate.id) },
            onEdit = { onNavigateToEdit(coordinate.id) },
            onDelete = { coordinateToDelete = coordinate },
            modifier = Modifier.animateItem()
        )
    }
}
```

增加 import：
```kotlin
import com.lolita.app.ui.screen.common.SwipeToDeleteContainer
```

**Step 2: Build 验证**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add -A && git commit -m "feat: add swipe-to-delete for coordinate list in 1-column mode"
```

---

### Task 5: 服饰列表1列模式增加左滑删除

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt`

**Step 1: 在 LazyColumn 的 items 中包裹 SwipeToDeleteContainer**

在 `ItemListScreen` 的1列模式 LazyColumn（第 299-319 行），将 items 块改为：

```kotlin
items(
    items = uiState.filteredItems,
    key = { it.id }
) { item ->
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
            modifier = Modifier.animateItem()
        )
    }
}
```

增加 import：
```kotlin
import com.lolita.app.ui.screen.common.SwipeToDeleteContainer
```

**Step 2: Build 验证**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add -A && git commit -m "feat: add swipe-to-delete for item list in 1-column mode"
```

---

### Task 6: 最终集成验证

**Step 1: Clean build**

Run: `./gradlew.bat clean assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 2: 手动验证清单**

1. 套装列数切换：切到套装 tab → 点击切换按钮 → 1/2/3列正确切换
2. 切回服饰 tab → 切换按钮控制服饰列数（互不干扰）
3. 套装卡片小图：1列和2/3列模式下都能看到关联服饰的小圆形缩略图
4. 左滑删除（套装）：1列模式左滑 → 红色背景 → 弹出确认对话框
5. 左滑删除（服饰）：1列模式左滑 → 红色背景 → 弹出确认对话框
6. 2/3列模式下左滑不生效，长按菜单正常
