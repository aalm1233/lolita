# 现有功能优化 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix 7 issues across data integrity, skin consistency, and error handling in the Lolita fashion app.

**Architecture:** All changes are localized modifications to existing files — no new entities, screens, or navigation routes. Data integrity fixes touch Repository/ViewModel layers. Skin consistency fixes touch Screen composables. Error handling fixes touch ViewModel and BackupManager.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Material3

---

### Task 1: Coordinate image cleanup on edit

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/repository/CoordinateRepository.kt:47-63`

**Step 1: Modify `updateCoordinateWithItems` to clean up old image**

In `CoordinateRepository.kt`, replace the `updateCoordinateWithItems` method:

```kotlin
suspend fun updateCoordinateWithItems(
    coordinate: Coordinate,
    addedItemIds: Set<Long>,
    removedItemIds: Set<Long>
) {
    // Capture old imageUrl before transaction
    val oldCoordinate = coordinateDao.getCoordinateById(coordinate.id)
    val oldImageUrl = oldCoordinate?.imageUrl

    database.withTransaction {
        coordinateDao.updateCoordinate(coordinate.copy(updatedAt = System.currentTimeMillis()))
        removedItemIds.forEach { itemId ->
            val item = itemDao.getItemById(itemId)
            item?.let { itemDao.updateItem(it.copy(coordinateId = null)) }
        }
        addedItemIds.forEach { itemId ->
            val item = itemDao.getItemById(itemId)
            item?.let { itemDao.updateItem(it.copy(coordinateId = coordinate.id)) }
        }
    }

    // Clean up old image file if changed
    if (!oldImageUrl.isNullOrEmpty() && oldImageUrl != coordinate.imageUrl) {
        try { ImageFileHelper.deleteImage(oldImageUrl) } catch (_: Exception) {}
    }
}
```

Also apply the same pattern to `updateCoordinate` (line 65-66):

```kotlin
suspend fun updateCoordinate(coordinate: Coordinate) {
    val oldCoordinate = coordinateDao.getCoordinateById(coordinate.id)
    val oldImageUrl = oldCoordinate?.imageUrl

    coordinateDao.updateCoordinate(coordinate.copy(updatedAt = System.currentTimeMillis()))

    if (!oldImageUrl.isNullOrEmpty() && oldImageUrl != coordinate.imageUrl) {
        try { ImageFileHelper.deleteImage(oldImageUrl) } catch (_: Exception) {}
    }
}
```

**Step 2: Build and verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/repository/CoordinateRepository.kt
git commit -m "fix: clean up old image file when coordinate image changes"
```

---

### Task 2: Fix PriceViewModel.loadPrice paymentDate logic

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/price/PriceViewModel.kt:110-111`

**Step 1: Fix the payment date selection logic**

In `PriceViewModel.kt` line 111, change:

```kotlin
// OLD:
val firstPaidDate = payments.minByOrNull { it.createdAt }?.let { if (it.isPaid) it.paidDate else it.dueDate }
```

to:

```kotlin
// NEW:
val firstPaidDate = payments.minByOrNull { it.dueDate ?: Long.MAX_VALUE }?.let { if (it.isPaid) it.paidDate else it.dueDate }
```

**Step 2: Build and verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/price/PriceViewModel.kt
git commit -m "fix: use dueDate instead of createdAt for payment date ordering"
```

---

### Task 3: ItemListScreen FK deletion error friendly message

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt:479-487`

**Step 1: Add SQLiteConstraintException import and specific catch**

In `ItemViewModel.kt`, add import at top of file:

```kotlin
import android.database.sqlite.SQLiteConstraintException
```

Then replace the `deleteItem` method (lines 479-487):

```kotlin
fun deleteItem(item: Item) {
    viewModelScope.launch {
        try {
            itemRepository.deleteItem(item)
        } catch (e: SQLiteConstraintException) {
            _uiState.update { it.copy(errorMessage = "此服饰已被套装引用，无法删除。请先从套装中移除后再试。") }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = e.message ?: "删除失败") }
        }
    }
}
```

**Step 2: Build and verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt
git commit -m "fix: show friendly error when deleting item referenced by coordinate"
```

---

### Task 4: BackupManager calendar event failure feedback

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/file/BackupManager.kt:266-312`
- Modify: `app/src/main/java/com/lolita/app/data/file/BackupManager.kt:514-521`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/settings/BackupRestoreScreen.kt:105-109`

**Step 1: Add `calendarEventsFailed` field to ImportSummary**

In `BackupManager.kt` line 514, change:

```kotlin
data class ImportSummary(
    val totalImported: Int,
    val totalSkipped: Int,
    val totalErrors: Int = 0,
    val imageCount: Int = 0,
    val backupDate: Long,
    val backupVersion: String
)
```

to:

```kotlin
data class ImportSummary(
    val totalImported: Int,
    val totalSkipped: Int,
    val totalErrors: Int = 0,
    val imageCount: Int = 0,
    val calendarEventsFailed: Int = 0,
    val backupDate: Long,
    val backupVersion: String
)
```

**Step 2: Track calendar event failures in importFromJson**

In `BackupManager.kt`, replace the calendar event recreation block (lines 266-287):

```kotlin
// Recreate calendar events for unpaid payments with due dates
var calendarFailCount = 0
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
            } else {
                calendarFailCount++
            }
        } catch (e: Exception) {
            calendarFailCount++
            Log.e("BackupManager", "Failed to create calendar event for payment ${payment.id}", e)
        }
    }
} catch (e: Exception) {
    calendarFailCount = -1 // indicate total failure
    Log.e("BackupManager", "Failed to recreate calendar events during import", e)
}
```

Then update the `Result.success` call (line 305) to pass `calendarFailCount`:

```kotlin
Result.success(ImportSummary(
    totalImported = imported,
    totalSkipped = 0,
    totalErrors = 0,
    imageCount = imageCount,
    calendarEventsFailed = if (calendarFailCount > 0) calendarFailCount else 0,
    backupDate = backupData.backupDate,
    backupVersion = backupData.appVersion
))
```

**Step 3: Update BackupRestoreScreen to display calendar failure info**

In `BackupRestoreScreen.kt`, replace lines 105-109:

```kotlin
onSuccess = { summary ->
    val imageMsg = if (summary.imageCount > 0) "，恢复 ${summary.imageCount} 张图片" else ""
    val calendarMsg = if (summary.calendarEventsFailed > 0) "\n⚠ ${summary.calendarEventsFailed} 个日历事件创建失败（可能需要授予日历权限）" else ""
    _uiState.value = _uiState.value.copy(
        message = "恢复完成！导入 ${summary.totalImported} 条数据$imageMsg$calendarMsg"
    )
},
```

**Step 4: Build and verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/file/BackupManager.kt \
       app/src/main/java/com/lolita/app/ui/screen/settings/BackupRestoreScreen.kt
git commit -m "fix: report calendar event creation failures during backup import"
```

---

### Task 5: PriceManageScreen skin animations

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/price/PriceManageScreen.kt`

**Step 1: Add skin animation imports**

Add these imports to `PriceManageScreen.kt`:

```kotlin
import androidx.compose.foundation.lazy.itemsIndexed
import com.lolita.app.ui.theme.skin.animation.skinItemAppear
import com.lolita.app.ui.theme.skin.animation.rememberSkinFlingBehavior
```

**Step 2: Update LazyColumn with flingBehavior and skinItemAppear**

Replace the LazyColumn block (lines 75-100):

```kotlin
val flingBehavior = rememberSkinFlingBehavior()
LazyColumn(
    modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
    flingBehavior = flingBehavior
) {
    if (uiState.prices.isEmpty()) {
        item {
            EmptyState(
                icon = Icons.Default.ShoppingCart,
                title = "暂无价格信息",
                subtitle = "点击 + 添加价格"
            )
        }
    } else {
        itemsIndexed(uiState.prices, key = { _, it -> it.price.id }) { index, priceWithPayments ->
            PriceCard(
                priceWithPayments = priceWithPayments,
                onClick = { onNavigateToPaymentManage(priceWithPayments.price.id) },
                onEdit = { onNavigateToPriceEdit(priceWithPayments.price.id) },
                onDelete = { priceToDelete = priceWithPayments.price },
                modifier = Modifier.skinItemAppear(index)
            )
        }
    }
}
```

Also add `modifier` parameter to `PriceCard` composable signature (line 135):

```kotlin
@Composable
private fun PriceCard(
    priceWithPayments: PriceWithPayments,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
```

And apply it to the `LolitaCard` (line 147):

```kotlin
LolitaCard(
    modifier = modifier.fillMaxWidth()
) {
```

**Step 3: Build and verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/price/PriceManageScreen.kt
git commit -m "feat: add skin fling behavior and item appear animations to PriceManageScreen"
```

---

### Task 6: PaymentManageScreen skin animations

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/price/PaymentManageScreen.kt`

**Step 1: Add skin animation imports**

Add these imports to `PaymentManageScreen.kt`:

```kotlin
import androidx.compose.foundation.lazy.itemsIndexed
import com.lolita.app.ui.theme.skin.animation.skinItemAppear
import com.lolita.app.ui.theme.skin.animation.rememberSkinFlingBehavior
```

**Step 2: Update LazyColumn with flingBehavior and skinItemAppear**

Replace the LazyColumn block (lines 74-121):

```kotlin
val flingBehavior = rememberSkinFlingBehavior()
LazyColumn(
    modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
    flingBehavior = flingBehavior
) {
    // 统计卡片
    item {
        PaymentStatsCard(
            totalPrice = uiState.totalPrice,
            paidAmount = uiState.paidAmount,
            unpaidAmount = uiState.unpaidAmount
        )
    }

    // 付款记录列表
    item {
        Text(
            "付款记录",
            style = MaterialTheme.typography.titleMedium
        )
    }

    if (uiState.payments.isEmpty()) {
        item {
            EmptyState(
                icon = Icons.Default.Payment,
                title = "暂无付款记录",
                subtitle = "点击 + 添加付款记录"
            )
        }
    } else {
        itemsIndexed(uiState.payments, key = { _, it -> it.id }) { index, payment ->
            PaymentCard(
                payment = payment,
                onMarkPaid = {
                    coroutineScope.launch {
                        viewModel.markAsPaid(payment)
                    }
                },
                onDelete = { paymentToDelete = payment },
                onClick = { onNavigateToPaymentEdit(payment.id) },
                modifier = Modifier.skinItemAppear(index)
            )
        }
    }
}
```

Also add `modifier` parameter to `PaymentCard` composable (line 217):

```kotlin
@Composable
private fun PaymentCard(
    payment: Payment,
    onMarkPaid: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LolitaCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
```

**Step 3: Build and verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/price/PaymentManageScreen.kt
git commit -m "feat: add skin fling behavior and item appear animations to PaymentManageScreen"
```

---

### Task 7: PaymentCalendarScreen Card → LolitaCard + SkinClickable

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt`

**Step 1: Add LolitaCard import**

Add this import to `PaymentCalendarScreen.kt`:

```kotlin
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.theme.skin.component.SkinClickable
```

**Step 2: Replace Empty State Card (line 240)**

Replace:
```kotlin
Card(modifier = Modifier.fillMaxWidth()) {
    Text(
        "当月无付款记录",
        modifier = Modifier.padding(16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
```

With:
```kotlin
LolitaCard(modifier = Modifier.fillMaxWidth()) {
    Text(
        "当月无付款记录",
        modifier = Modifier.padding(16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
```

**Step 3: Replace YearHeader Card (line 272)**

Replace:
```kotlin
Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(12.dp)) {
```

With:
```kotlin
LolitaCard(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(12.dp)) {
```

**Step 4: Replace MonthCard Card + clickable (lines 378-387)**

Replace:
```kotlin
Card(
    modifier = modifier
        .heightIn(min = 100.dp)
        .then(
            if (isCurrentMonth) Modifier.border(
                2.dp, primaryColor, MaterialTheme.shapes.medium
            ) else Modifier
        )
        .clickable(onClick = onClick),
    colors = CardDefaults.cardColors(containerColor = bgColor)
) {
```

With:
```kotlin
LolitaCard(
    modifier = modifier
        .heightIn(min = 100.dp)
        .then(
            if (isCurrentMonth) Modifier.border(
                2.dp, primaryColor, MaterialTheme.shapes.medium
            ) else Modifier
        ),
    onClick = onClick,
    containerColor = bgColor
) {
```

Note: Check if `LolitaCard` supports `containerColor` parameter. If not, wrap the content with `Surface(color = bgColor)` or use `Modifier.background(bgColor)` inside.

**Step 5: Replace PaymentInfoCard Card (lines 472-477)**

Replace:
```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    colors = if (isOverdue) CardDefaults.cardColors(
        containerColor = Color(0xFFD32F2F).copy(alpha = 0.06f)
    ) else CardDefaults.cardColors()
) {
```

With:
```kotlin
LolitaCard(
    modifier = Modifier.fillMaxWidth()
) {
```

Note: The overdue background color styling may need to be handled via a `Modifier.background()` on the inner Column if `LolitaCard` doesn't support `containerColor`. Verify `LolitaCard` API before implementing.

**Step 6: Build and verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 7: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt
git commit -m "feat: replace Material3 Card with LolitaCard in PaymentCalendarScreen"
```

---

### Task 8: Version bump and release build

**Files:**
- Modify: `app/build.gradle.kts:25-26`

**Step 1: Bump version**

In `app/build.gradle.kts`, change:
```kotlin
versionCode = 27
versionName = "2.13"
```
to:
```kotlin
versionCode = 28
versionName = "2.14"
```

**Step 2: Release build**

Run: `./gradlew.bat assembleRelease`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "chore: bump version to 2.14 (28)"
```
