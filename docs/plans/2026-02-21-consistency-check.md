# 全功能一致性修复 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix all consistency issues across data integrity, UI components, ViewModel patterns, and dead code.

**Architecture:** Layered fix approach — data integrity first (user-facing bugs), then UI consistency, ViewModel pattern unification, and finally dead code cleanup. Each layer is independently verifiable.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Material3

---

### Task 1: BackupManager — Clean old images before import

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/file/BackupManager.kt:306-317`

**Step 1: Add image cleanup in clearAllTables()**

In `BackupManager.kt`, modify the `clearAllTables()` method to delete the images directory before clearing tables:

```kotlin
private suspend fun clearAllTables() {
    // Clean up orphaned image files before clearing database
    val imagesDir = File(context.filesDir, "images")
    if (imagesDir.exists()) {
        imagesDir.listFiles()?.forEach { it.delete() }
    }

    database.outfitLogDao().deleteAllOutfitItemCrossRefs()
    // ... rest unchanged
}
```

**Step 2: Build and verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/file/BackupManager.kt
git commit -m "fix: clean old images before backup import"
```

---

### Task 2: BackupManager — Rebuild calendar events after import

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/file/BackupManager.kt:229-254`

**Step 1: Add calendar event rebuild after payment import**

In `importFromJson()`, after the transaction block (line 232) and before the reminder rescheduling (line 241), add calendar event recreation:

```kotlin
// Recreate calendar events for unpaid payments with due dates
try {
    val unpaidPayments = database.paymentDao().getAllPaymentsList()
        .filter { !it.isPaid && it.dueDate > System.currentTimeMillis() }
    unpaidPayments.forEach { payment ->
        try {
            val eventId = CalendarEventHelper.insertEvent(
                context = context,
                title = "付款提醒",
                description = "付款金额: ¥${String.format("%.2f", payment.amount)}",
                startTimeMillis = payment.dueDate
            )
            if (eventId != null) {
                database.paymentDao().updateCalendarEventId(payment.id, eventId)
            }
        } catch (_: Exception) {}
    }
} catch (_: Exception) {}
```

Also add the import for `CalendarEventHelper` at the top of the file:
```kotlin
import com.lolita.app.data.notification.CalendarEventHelper
```

**Step 2: Build and verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/file/BackupManager.kt
git commit -m "fix: rebuild calendar events after backup import"
```

---

### Task 3: PriceRepository — Clean calendar events and reminders before cascade delete

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/repository/PriceRepository.kt:47`

**Step 1: Add constructor dependencies**

Add `PaymentReminderScheduler`, `CalendarEventHelper`, and `Context` to `PriceRepository`:

```kotlin
class PriceRepository(
    private val priceDao: PriceDao,
    private val paymentDao: PaymentDao,
    private val context: Context? = null
)
```

**Step 2: Replace deletePrice with cleanup logic**

Replace line 47:
```kotlin
suspend fun deletePrice(price: Price) {
    // Clean up calendar events and reminders before CASCADE delete
    val payments = paymentDao.getPaymentsByPriceList(price.id)
    if (context != null) {
        val scheduler = PaymentReminderScheduler(context)
        payments.forEach { payment ->
            payment.calendarEventId?.let { CalendarEventHelper.deleteEvent(context, it) }
            try { scheduler.cancelReminder(payment.id) } catch (_: Exception) {}
        }
    }
    priceDao.deletePrice(price)
}
```

Add imports at top:
```kotlin
import android.content.Context
import com.lolita.app.data.notification.CalendarEventHelper
import com.lolita.app.data.notification.PaymentReminderScheduler
```

**Step 3: Add getPaymentsByPriceList to PaymentDao**

In `PaymentDao.kt`, add:
```kotlin
@Query("SELECT * FROM payments WHERE price_id = :priceId")
suspend fun getPaymentsByPriceList(priceId: Long): List<Payment>
```

**Step 4: Update AppModule to pass context**

In `AppModule.kt`, update the `priceRepository` lazy init to pass context:
```kotlin
val priceRepository: () -> PriceRepository = {
    PriceRepository(database().priceDao(), database().paymentDao(), appContext)
}
```

**Step 5: Build and verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/repository/PriceRepository.kt
git add app/src/main/java/com/lolita/app/data/local/dao/PaymentDao.kt
git add app/src/main/java/com/lolita/app/di/AppModule.kt
git commit -m "fix: clean calendar events and reminders on price cascade delete"
```

---

### Task 4: CSV 导出补全缺失字段

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/file/BackupManager.kt:88-100`

**Step 1: 补全 Items CSV 列**

将 Items CSV header（line 90）和数据行（line 92）替换为：

```kotlin
// Items
sb.appendLine("\n=== ITEMS ===")
sb.appendLine("id,name,description,brand_id,category_id,coordinate_id,status,priority,image_url,color,season,style,size,size_chart_image_url,created_at,updated_at")
database.itemDao().getAllItemsList().forEach { i ->
    sb.appendLine("${i.id},${escapeCsv(i.name)},${escapeCsv(i.description)},${i.brandId},${i.categoryId},${i.coordinateId},${i.status},${i.priority},${escapeCsv(i.imageUrl)},${escapeCsv(i.color)},${escapeCsv(i.season)},${escapeCsv(i.style)},${escapeCsv(i.size)},${escapeCsv(i.sizeChartImageUrl)},${i.createdAt},${i.updatedAt}")
}
```

**Step 2: 补全 Coordinates CSV 列**

将 Coordinates CSV header（line 97）和数据行（line 99）替换为：

```kotlin
// Coordinates
sb.appendLine("\n=== COORDINATES ===")
sb.appendLine("id,name,description,image_url,created_at,updated_at")
database.coordinateDao().getAllCoordinatesList().forEach { c ->
    sb.appendLine("${c.id},${escapeCsv(c.name)},${escapeCsv(c.description)},${escapeCsv(c.imageUrl)},${c.createdAt},${c.updatedAt}")
}
```

**Step 3: Build and verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/file/BackupManager.kt
git commit -m "fix: add missing fields to CSV export (color, season, style, size, imageUrl)"
```

---

### Task 5: SwipeToDeleteContainer 硬编码颜色修复

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/common/SwipeToDeleteContainer.kt:45-47`

**Step 1: 替换硬编码颜色为主题色**

将 line 46-47 的 `Color(0xFFFF5252)` 替换为 `MaterialTheme.colorScheme.error`：

```kotlin
val color by animateColorAsState(
    targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart)
        MaterialTheme.colorScheme.error else Color.Transparent,
    label = "swipe-bg"
)
```

**Step 2: 添加 MaterialTheme import**

确认文件已有 `import androidx.compose.material3.*`（通配符导入已包含 MaterialTheme）。

**Step 3: Build and verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/common/SwipeToDeleteContainer.kt
git commit -m "fix: use theme error color in SwipeToDeleteContainer"
```

---

### Task 6: Card → LolitaCard 统一

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/price/PriceManageScreen.kt:140`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/price/PaymentManageScreen.kt:92,176,223`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/outfit/OutfitLogDetailScreen.kt:124,228`

**Step 1: PriceManageScreen — PriceCard**

In `PriceManageScreen.kt` line 140, replace `Card(` with `LolitaCard(`:

```kotlin
LolitaCard(
    modifier = Modifier.fillMaxWidth()
) {
```

Note: `LolitaCard` is already imported on line 29.

**Step 2: PaymentManageScreen — PaymentStatsCard, empty state card, PaymentCard**

In `PaymentManageScreen.kt`:

- Line 92: Replace the empty state `Card(` with `LolitaCard(`:
```kotlin
LolitaCard(
    modifier = Modifier.fillMaxWidth()
) {
```

- Line 176: Replace `PaymentStatsCard`'s `Card(` with `LolitaCard(`:
```kotlin
LolitaCard(
    modifier = Modifier.fillMaxWidth()
) {
```

- Line 223: Replace `PaymentCard`'s `Card(` with `LolitaCard(`:
```kotlin
LolitaCard(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth()
) {
```

Add import: `import com.lolita.app.ui.screen.common.LolitaCard`

**Step 3: OutfitLogDetailScreen — Note card, DetailItemCard**

In `OutfitLogDetailScreen.kt`:

- Line 124: Replace note section `Card(` with `LolitaCard(`:
```kotlin
LolitaCard(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
) {
```
Remove the `shape` and `colors` parameters (LolitaCard handles its own styling).

- Line 228: Replace `DetailItemCard`'s `Card(` with `LolitaCard(`:
```kotlin
LolitaCard(
    modifier = modifier.fillMaxWidth(),
) {
```
Remove the `shape` parameter.

Add import: `import com.lolita.app.ui.screen.common.LolitaCard`

**Step 4: Build and verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/price/PriceManageScreen.kt
git add app/src/main/java/com/lolita/app/ui/screen/price/PaymentManageScreen.kt
git add app/src/main/java/com/lolita/app/ui/screen/outfit/OutfitLogDetailScreen.kt
git commit -m "fix: replace Card with LolitaCard for UI consistency"
```

---

### Task 7: 空列表 → EmptyState 统一

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/FilteredItemListScreen.kt:60-70`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/RecommendationScreen.kt:58-66`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/price/PaymentManageScreen.kt:90-106`

**Step 1: FilteredItemListScreen**

Replace lines 60-70 (the `Text("暂无数据")` block) with:

```kotlin
} else if (uiState.items.isEmpty()) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center
    ) {
        EmptyState(
            icon = Icons.Default.Search,
            title = "暂无数据",
            subtitle = "没有找到匹配的服饰"
        )
    }
```

Add imports:
```kotlin
import com.lolita.app.ui.screen.common.EmptyState
import androidx.compose.material.icons.filled.Search
```

**Step 2: RecommendationScreen**

Replace lines 63-66 (the empty recommendations block) with:

```kotlin
uiState.recommendations.isEmpty() -> {
    Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
        EmptyState(
            icon = Icons.Default.Checkroom,
            title = "暂无推荐搭配",
            subtitle = "添加更多服饰以获取搭配推荐"
        )
    }
}
```

Add imports:
```kotlin
import com.lolita.app.ui.screen.common.EmptyState
import androidx.compose.material.icons.filled.Checkroom
```

**Step 3: PaymentManageScreen**

Replace lines 90-106 (the empty payment Card block) with:

```kotlin
if (uiState.payments.isEmpty()) {
    item {
        EmptyState(
            icon = Icons.Default.Payment,
            title = "暂无付款记录",
            subtitle = "点击 + 添加付款记录"
        )
    }
}
```

Add imports:
```kotlin
import com.lolita.app.ui.screen.common.EmptyState
import androidx.compose.material.icons.filled.Payment
```

**Step 4: Build and verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/FilteredItemListScreen.kt
git add app/src/main/java/com/lolita/app/ui/screen/item/RecommendationScreen.kt
git add app/src/main/java/com/lolita/app/ui/screen/price/PaymentManageScreen.kt
git commit -m "fix: use EmptyState component for empty list consistency"
```

---

### Task 8: 补充 SwipeToDeleteContainer 到缺失的列表屏幕

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/WishlistScreen.kt:164-169`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/outfit/OutfitLogListScreen.kt:139-147`

**Step 1: WishlistScreen — 添加滑动删除**

WishlistScreen 需要先在 ViewModel 中添加删除方法，然后在 Screen 中包裹 SwipeToDeleteContainer。

在 `WishlistScreen.kt` 的 `WishlistViewModel` 类中添加删除方法：

```kotlin
fun deleteItem(item: Item) {
    viewModelScope.launch {
        try {
            itemRepository.deleteItem(item)
        } catch (_: Exception) {}
    }
}
```

在 Screen composable 中添加删除确认状态和对话框（在 `val uiState` 之后）：

```kotlin
var itemToDelete by remember { mutableStateOf<Item?>(null) }

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
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("删除")
            }
        },
        dismissButton = {
            TextButton(onClick = { itemToDelete = null }) { Text("取消") }
        }
    )
}
```

将 items 循环（line 164-169）包裹 SwipeToDeleteContainer：

```kotlin
items(uiState.filteredItems, key = { it.id }) { item ->
    SwipeToDeleteContainer(
        onDelete = { itemToDelete = item }
    ) {
        WishlistItemCard(
            item = item,
            onClick = { onNavigateToDetail(item.id) },
            modifier = Modifier.animateItem()
        )
    }
}
```

添加 imports：
```kotlin
import com.lolita.app.ui.screen.common.SwipeToDeleteContainer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
```

**Step 2: OutfitLogListScreen — 添加滑动删除**

OutfitLogListScreen 已有删除确认对话框（logToDelete），只需包裹 SwipeToDeleteContainer。

将 items 循环（line 139-147）包裹：

```kotlin
items(uiState.logs, key = { it.id }) { log ->
    SwipeToDeleteContainer(
        onDelete = { logToDelete = log }
    ) {
        OutfitLogListItemCard(
            log = log,
            onClick = { onNavigateToDetail(log.id) },
            onEdit = { onNavigateToEdit(log.id) },
            onDelete = { logToDelete = log },
            modifier = Modifier.animateItem()
        )
    }
}
```

添加 import：
```kotlin
import com.lolita.app.ui.screen.common.SwipeToDeleteContainer
```

**Step 3: Build and verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/WishlistScreen.kt
git add app/src/main/java/com/lolita/app/ui/screen/outfit/OutfitLogListScreen.kt
git commit -m "feat: add SwipeToDeleteContainer to wishlist and outfit log list"
```

---

### Task 9: 补充加载状态到缺失的屏幕

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/WishlistScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/price/PriceManageScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/price/PaymentManageScreen.kt`

**Step 1: WishlistViewModel — 添加 isLoading 状态**

在 `WishlistScreen.kt` 的 `WishlistUiState` data class 中添加：

```kotlin
data class WishlistUiState(
    val allItems: List<Item> = emptyList(),
    val filteredItems: List<Item> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true
)
```

在 `WishlistViewModel.init` 的 collect 回调中设置 `isLoading = false`：

```kotlin
init {
    viewModelScope.launch {
        itemRepository.getWishlistByPriority().collect { items ->
            val query = _uiState.value.searchQuery
            _uiState.update {
                it.copy(
                    allItems = items,
                    filteredItems = applySearch(items, query),
                    isLoading = false
                )
            }
        }
    }
}
```

在 `WishlistScreen` composable 的 Column 内容开头（`if (uiState.allItems.isNotEmpty())` 之前）添加：

```kotlin
if (uiState.isLoading) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
    return@Column
}
```

**Step 2: PriceManageViewModel — 添加 isLoading 状态**

在 `PriceViewModel.kt` 的 `PriceManageUiState` 中添加 `isLoading: Boolean = true`。

在 `PriceManageViewModel.init` 的 collect 回调中设置 `isLoading = false`。

在 `PriceManageScreen.kt` 的 LazyColumn 之前添加加载判断：

```kotlin
if (uiState.isLoading) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
} else {
    LazyColumn(...) { ... }
}
```

**Step 3: PaymentManageViewModel — 添加 isLoading 状态**

同理，在 `PriceViewModel.kt` 的 `PaymentManageUiState` 中添加 `isLoading: Boolean = true`。

在 `PaymentManageViewModel.init` 的 collect 回调中设置 `isLoading = false`。

在 `PaymentManageScreen.kt` 的 LazyColumn 之前添加加载判断（同 Step 2 模式）。

**Step 4: Build and verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/WishlistScreen.kt
git add app/src/main/java/com/lolita/app/ui/screen/price/PriceManageScreen.kt
git add app/src/main/java/com/lolita/app/ui/screen/price/PaymentManageScreen.kt
git add app/src/main/java/com/lolita/app/ui/screen/price/PriceViewModel.kt
git commit -m "fix: add loading state to wishlist, price manage, and payment manage screens"
```

---

### Task 10: CoordinateEditScreen — 错误处理从 SnackbarHost 改为 AlertDialog

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateEditScreen.kt:55,69-76,88,100-111`

**Step 1: 移除 SnackbarHost 相关代码**

删除 line 55 的 `val snackbarHostState = remember { SnackbarHostState() }` 和 line 69-76 的 `var errorMessage` 及 `LaunchedEffect(errorMessage)` 块。

删除 Scaffold 中的 `snackbarHost = { SnackbarHost(snackbarHostState) }`（line 88）。

**Step 2: 添加 AlertDialog 错误处理**

在 `UnsavedChangesHandler` 之前添加：

```kotlin
var showError by remember { mutableStateOf<String?>(null) }

if (showError != null) {
    AlertDialog(
        onDismissRequest = { showError = null },
        title = { Text("保存失败") },
        text = { Text(showError ?: "") },
        confirmButton = {
            TextButton(onClick = { showError = null }) {
                Text("确定")
            }
        }
    )
}
```

**Step 3: 更新保存逻辑的错误回调**

将 line 108-109 的 `.onFailure { e -> errorMessage = e.message ?: "保存失败" }` 改为：

```kotlin
.onFailure { e ->
    showError = e.message ?: "保存失败"
}
```

**Step 4: Build and verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateEditScreen.kt
git commit -m "fix: unify CoordinateEditScreen error handling to AlertDialog"
```

---

### Task 11: ItemEditScreen — 统一保存逻辑为 Result 模式

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt` (ItemEditViewModel.saveItem)
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemEditScreen.kt:130-137`

**Step 1: 修改 ItemEditViewModel.saveItem 返回 Result**

在 `ItemViewModel.kt` 中，将 `saveItem(onSuccess, onError)` 回调模式改为 `suspend fun saveItem(): Result<Unit>`：

找到 `saveItem` 方法，将签名从：
```kotlin
suspend fun saveItem(onSuccess: () -> Unit, onError: (String) -> Unit)
```
改为：
```kotlin
suspend fun saveItem(): Result<Unit>
```

方法体内部：
- 将所有 `onError(msg)` 替换为 `return Result.failure(Exception(msg))`
- 将所有 `onSuccess()` 替换为 `return Result.success(Unit)`
- 移除 try-catch 中的 `onError` 调用，改为 `return Result.failure(e)`

同理修改 `deleteItem` 方法：
```kotlin
suspend fun deleteItem(): Result<Unit>
```

**Step 2: 更新 ItemEditScreen 保存调用**

将 line 130-137 的保存按钮 onClick 改为：

```kotlin
onClick = {
    hasAttemptedSave = true
    coroutineScope.launch {
        viewModel.saveItem()
            .onSuccess { onSaveSuccess() }
            .onFailure { showError = it.message }
    }
},
```

将 line 93-98 的删除按钮 onClick 改为：

```kotlin
onClick = {
    showDeleteConfirm = false
    coroutineScope.launch {
        viewModel.deleteItem()
            .onSuccess { onSaveSuccess() }
            .onFailure { showError = it.message }
    }
},
```

**Step 3: Build and verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt
git add app/src/main/java/com/lolita/app/ui/screen/item/ItemEditScreen.kt
git commit -m "refactor: unify ItemEditScreen save logic to Result pattern"
```

---

### Task 12: 统一验证方式 — ItemEditScreen 和 CoordinateEditScreen

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt` (ItemEditViewModel)
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemEditScreen.kt:139`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateEditScreen.kt:113`

**Step 1: 添加 isValid() 到 ItemEditViewModel**

在 `ItemViewModel.kt` 的 `ItemEditViewModel` 类中添加：

```kotlin
fun isValid(): Boolean {
    val state = _uiState.value
    return state.name.isNotBlank() && state.brandId != 0L && state.categoryId != 0L
}
```

**Step 2: ItemEditScreen 使用 viewModel.isValid()**

将 line 139 的 `enabled` 条件：
```kotlin
enabled = !uiState.isSaving && uiState.name.isNotBlank() && uiState.brandId != 0L && uiState.categoryId != 0L
```
替换为：
```kotlin
enabled = !uiState.isSaving && viewModel.isValid()
```

**Step 3: CoordinateEditViewModel 添加 isValid()**

在 `CoordinateViewModel.kt` 的 `CoordinateEditViewModel` 类中添加：

```kotlin
fun isValid(): Boolean {
    return _uiState.value.name.isNotBlank()
}
```

**Step 4: CoordinateEditScreen 使用 viewModel.isValid()**

将 line 113 的 `enabled` 条件：
```kotlin
enabled = uiState.name.isNotBlank() && !uiState.isSaving
```
替换为：
```kotlin
enabled = viewModel.isValid() && !uiState.isSaving
```

**Step 5: Build and verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt
git add app/src/main/java/com/lolita/app/ui/screen/item/ItemEditScreen.kt
git add app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateViewModel.kt
git add app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateEditScreen.kt
git commit -m "refactor: unify validation to viewModel.isValid() pattern"
```

---

### Task 13: 移除未使用的 DAO 方法

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/local/dao/ItemDao.kt:33-40`
- Modify: `app/src/main/java/com/lolita/app/data/local/dao/StyleDao.kt:20-21`
- Modify: `app/src/main/java/com/lolita/app/data/local/dao/SeasonDao.kt:20-21`

**Step 1: ItemDao — 移除 3 个未使用方法**

删除 `ItemDao.kt` 中以下 3 个方法（line 33-40）：

```kotlin
@Query("SELECT * FROM items WHERE coordinate_id = :coordinateId")
fun getItemsByCoordinate(coordinateId: Long): Flow<List<Item>>

@Query("SELECT * FROM items WHERE brand_id = :brandId")
fun getItemsByBrand(brandId: Long): Flow<List<Item>>

@Query("SELECT * FROM items WHERE category_id = :categoryId")
fun getItemsByCategory(categoryId: Long): Flow<List<Item>>
```

这些方法从未被任何 Repository 或 ViewModel 调用。实际使用的是 `getItemsByBrandName()` 和 `getItemsByCategoryName()`。

**Step 2: StyleDao — 移除 getPresetStyles()**

删除 `StyleDao.kt` 中 line 20-21：

```kotlin
@Query("SELECT * FROM styles WHERE is_preset = 1 ORDER BY name ASC")
fun getPresetStyles(): Flow<List<Style>>
```

**Step 3: SeasonDao — 移除 getPresetSeasons()**

删除 `SeasonDao.kt` 中 line 20-21：

```kotlin
@Query("SELECT * FROM seasons WHERE is_preset = 1 ORDER BY name ASC")
fun getPresetSeasons(): Flow<List<Season>>
```

**Step 4: Build and verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/local/dao/ItemDao.kt
git add app/src/main/java/com/lolita/app/data/local/dao/StyleDao.kt
git add app/src/main/java/com/lolita/app/data/local/dao/SeasonDao.kt
git commit -m "cleanup: remove unused DAO methods"
```

---

### Task 14: 移除未使用的 Repository 方法

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/repository/BrandRepository.kt:14`
- Modify: `app/src/main/java/com/lolita/app/data/repository/CategoryRepository.kt:14`

**Step 1: BrandRepository — 移除 getPresetBrands()**

删除 `BrandRepository.kt` line 14：

```kotlin
fun getPresetBrands(): Flow<List<Brand>> = brandDao.getPresetBrands()
```

同时检查 `BrandDao` 中的 `getPresetBrands()` 是否还有其他调用者。如果没有，也一并删除。

**Step 2: CategoryRepository — 移除 getPresetCategories()**

删除 `CategoryRepository.kt` line 14：

```kotlin
fun getPresetCategories(): Flow<List<Category>> = categoryDao.getPresetCategories()
```

同时检查 `CategoryDao` 中的 `getPresetCategories()` 是否还有其他调用者。如果没有，也一并删除。

**Step 3: Build and verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/repository/BrandRepository.kt
git add app/src/main/java/com/lolita/app/data/repository/CategoryRepository.kt
git commit -m "cleanup: remove unused Repository methods"
```

---

### Task 15: 最终构建验证

**Step 1: Clean build**

Run: `./gradlew.bat clean assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 2: 检查所有修改文件**

Run: `git diff --stat`
验证所有修改文件都在预期范围内。

**Step 3: 最终提交（如有遗漏）**

确认所有改动已提交，无未暂存文件。
