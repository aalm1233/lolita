# Price/Payment 数据统一 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 删除 Price.purchaseDate，统一所有数据路径都创建 Payment 记录，用 Payment.paidDate 替代购买时间语义。

**Architecture:** DB migration v13→v14 补建缺失的 Payment 记录并重建 Price 表删除 purchaseDate 列。DAO 查询从 Price.purchaseDate 改为 JOIN Payment.paidDate。所有数据入口（手动添加、淘宝导入）统一创建 Payment。

**Tech Stack:** Room 2.7.0, Kotlin, Jetpack Compose, SQLite migration

---

### Task 1: Database Migration v13→v14

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/local/LolitaDatabase.kt:386` (addMigrations)
- Modify: `app/src/main/java/com/lolita/app/data/local/LolitaDatabase.kt:13` (version = 13 → 14)

**Step 1: Add MIGRATION_13_14**

Add after `MIGRATION_12_13` (line ~376):

```kotlin
private val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Step 1: Create payments for Prices that have no Payment records
        // Case A: purchaseDate is not null — use it as paidDate
        db.execSQL("""
            INSERT INTO payments (price_id, amount, due_date, is_paid, paid_date, reminder_set, created_at)
            SELECT p.id, p.total_price, p.purchase_date, 1, p.purchase_date, 0, p.created_at
            FROM prices p
            WHERE p.id NOT IN (SELECT price_id FROM payments)
              AND p.purchase_date IS NOT NULL
        """.trimIndent())

        // Case B: purchaseDate is null — use createdAt as fallback
        db.execSQL("""
            INSERT INTO payments (price_id, amount, due_date, is_paid, paid_date, reminder_set, created_at)
            SELECT p.id, p.total_price, p.created_at, 1, p.created_at, 0, p.created_at
            FROM prices p
            WHERE p.id NOT IN (SELECT price_id FROM payments)
              AND p.purchase_date IS NULL
        """.trimIndent())

        // Step 2: Rebuild prices table without purchase_date column
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS prices_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                item_id INTEGER NOT NULL,
                type TEXT NOT NULL,
                total_price REAL NOT NULL,
                deposit REAL,
                balance REAL,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(item_id) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO prices_new (id, item_id, type, total_price, deposit, balance, created_at)
            SELECT id, item_id, type, total_price, deposit, balance, created_at FROM prices
        """.trimIndent())
        db.execSQL("DROP TABLE prices")
        db.execSQL("ALTER TABLE prices_new RENAME TO prices")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_prices_item_id ON prices(item_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_prices_type ON prices(type)")
    }
}
```

**Step 2: Update version number**

Change line 28: `version = 13` → `version = 14`

**Step 3: Register migration**

Change line 386: add `MIGRATION_13_14` to `.addMigrations(...)` call.

**Step 4: Build to verify migration compiles**

Run: `./gradlew.bat assembleDebug`

**Step 5: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/local/LolitaDatabase.kt
git commit -m "feat: add migration v13→v14 — backfill payments, remove purchaseDate column"
```

---

### Task 2: Remove purchaseDate from Price Entity

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/local/entity/Price.kt:47-48`

**Step 1: Delete purchaseDate field**

Remove lines 47-48:
```kotlin
    @ColumnInfo(name = "purchase_date")
    val purchaseDate: Long? = null
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/local/entity/Price.kt
git commit -m "refactor: remove purchaseDate from Price entity"
```

---

### Task 3: Rewrite PriceDao Queries

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/local/dao/PriceDao.kt:109-118` (getMonthlySpending)
- Modify: `app/src/main/java/com/lolita/app/data/local/dao/PriceDao.kt:159-167` (getItemsByPurchaseMonth)
- Modify: `app/src/main/java/com/lolita/app/data/local/dao/PriceDao.kt:172-184` (getPricesWithStatusByDateRange)
- Modify: `app/src/main/java/com/lolita/app/data/local/dao/PriceDao.kt:192-201` (PriceWithStatus data class)

**Step 1: Rewrite getMonthlySpending (lines 109-118)**

Replace with:
```kotlin
@Query("""
    SELECT strftime('%Y-%m', pay.paid_date / 1000, 'unixepoch') AS yearMonth,
           COALESCE(SUM(pay.amount), 0.0) AS totalSpending
    FROM payments pay
    INNER JOIN prices pr ON pay.price_id = pr.id
    INNER JOIN items i ON pr.item_id = i.id
    WHERE i.status IN ('OWNED', 'PENDING_BALANCE')
      AND pay.is_paid = 1
      AND pay.paid_date IS NOT NULL
    GROUP BY yearMonth
    ORDER BY yearMonth ASC
""")
fun getMonthlySpending(): Flow<List<MonthlySpending>>
```

**Step 2: Rewrite getItemsByPurchaseMonth (lines 159-167)**

Replace with:
```kotlin
@Query("""
    SELECT DISTINCT i.* FROM items i
    INNER JOIN prices pr ON pr.item_id = i.id
    INNER JOIN payments pay ON pay.price_id = pr.id
    WHERE i.status IN ('OWNED', 'PENDING_BALANCE')
      AND pay.is_paid = 1
      AND pay.paid_date IS NOT NULL
      AND strftime('%Y-%m', pay.paid_date / 1000, 'unixepoch') = :yearMonth
    ORDER BY i.updated_at DESC
""")
fun getItemsByPurchaseMonth(yearMonth: String): Flow<List<Item>>
```

**Step 3: Rewrite getPricesWithStatusByDateRange (lines 172-184)**

Replace with:
```kotlin
@Query("""
    SELECT pr.id AS priceId, pr.total_price AS totalPrice,
           MIN(pay.paid_date) AS firstPaidDate,
           pr.type AS priceType, i.name AS itemName, i.id AS itemId,
           (SELECT COUNT(*) FROM payments p WHERE p.price_id = pr.id AND p.is_paid = 0) AS unpaidCount,
           (SELECT COUNT(*) FROM payments p WHERE p.price_id = pr.id AND p.is_paid = 0 AND p.due_date < :now) AS overdueCount
    FROM prices pr
    INNER JOIN items i ON pr.item_id = i.id
    INNER JOIN payments pay ON pay.price_id = pr.id
    WHERE pay.is_paid = 1
      AND pay.paid_date BETWEEN :startDate AND :endDate
      AND i.status IN ('OWNED', 'PENDING_BALANCE')
    GROUP BY pr.id
    ORDER BY firstPaidDate ASC
""")
fun getPricesWithStatusByDateRange(startDate: Long, endDate: Long, now: Long): Flow<List<PriceWithStatus>>
```

**Step 4: Update PriceWithStatus data class (lines 192-201)**

Replace with:
```kotlin
data class PriceWithStatus(
    val priceId: Long,
    val totalPrice: Double,
    val firstPaidDate: Long,
    val priceType: com.lolita.app.data.local.entity.PriceType,
    val itemName: String,
    val itemId: Long,
    val unpaidCount: Int,
    val overdueCount: Int
)
```

**Step 5: Build to verify queries compile**

Run: `./gradlew.bat assembleDebug`

**Step 6: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/local/dao/PriceDao.kt
git commit -m "refactor: rewrite PriceDao queries to use Payment.paidDate"
```

---

### Task 4: Update PriceViewModel — Remove purchaseDate, Add paymentDate

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/price/PriceViewModel.kt:24-33` (PriceEditUiState)
- Modify: `app/src/main/java/com/lolita/app/ui/screen/price/PriceViewModel.kt:109-126` (loadPrice)
- Modify: `app/src/main/java/com/lolita/app/ui/screen/price/PriceViewModel.kt:148-151` (updatePurchaseDate)
- Modify: `app/src/main/java/com/lolita/app/ui/screen/price/PriceViewModel.kt:157-240` (save)
- Modify: `app/src/main/java/com/lolita/app/ui/screen/price/PriceViewModel.kt:242-316` (update)

**Step 1: Update PriceEditUiState (line 29)**

Replace `purchaseDate: Long? = null` with `paymentDate: Long? = null`.
Remove `itemStatus: ItemStatus? = null` field (no longer needed for conditional display).

**Step 2: Remove loadItemStatus method (lines 102-107)**

Delete the `loadItemStatus` function entirely.

**Step 3: Update loadPrice (line 121)**

Change `purchaseDate = p.purchaseDate` to load paymentDate from the first Payment's paidDate instead:
```kotlin
fun loadPrice(priceId: Long?) {
    if (priceId == null) return
    viewModelScope.launch {
        val price = priceRepository.getPriceById(priceId)
        price?.let { p ->
            // Load paymentDate from first payment's paidDate
            val payments = paymentRepository.getPaymentsByPriceList(p.id)
            val firstPaidDate = payments.minByOrNull { it.createdAt }?.paidDate
            _uiState.update {
                it.copy(
                    priceType = p.type,
                    totalPrice = p.totalPrice.toString(),
                    deposit = p.deposit?.toString() ?: "",
                    balance = p.balance?.toString() ?: "",
                    paymentDate = firstPaidDate
                )
            }
        }
    }
}
```

**Step 4: Rename updatePurchaseDate → updatePaymentDate (lines 148-151)**

```kotlin
fun updatePaymentDate(date: Long?) {
    hasUnsavedChanges = true
    _uiState.value = _uiState.value.copy(paymentDate = date)
}
```

**Step 5: Update save() — remove purchaseDate from Price, use paymentDate for Payment**

In `save()` (line 163-174), remove `purchaseDate` from Price constructor:
```kotlin
val price = Price(
    itemId = itemId,
    type = _uiState.value.priceType,
    totalPrice = totalPrice,
    deposit = if (_uiState.value.priceType == PriceType.DEPOSIT_BALANCE) {
        _uiState.value.deposit.toDoubleOrNull()
    } else null,
    balance = if (_uiState.value.priceType == PriceType.DEPOSIT_BALANCE) {
        _uiState.value.balance.toDoubleOrNull()
    } else null
)
```

Update Payment creation (lines 184-232) to use `paymentDate`:
- For FULL: `dueDate = paymentDate ?: now`, and if paymentDate is set: `isPaid = true, paidDate = paymentDate`
- For DEPOSIT_BALANCE deposit: same logic
- For DEPOSIT_BALANCE balance: `isPaid = false, dueDate = now`

```kotlin
val paymentDate = _uiState.value.paymentDate
val isAlreadyPaid = paymentDate != null

when (_uiState.value.priceType) {
    PriceType.FULL -> {
        paymentRepository.insertPayment(
            Payment(
                priceId = priceId,
                amount = totalPrice,
                dueDate = paymentDate ?: now,
                isPaid = isAlreadyPaid,
                paidDate = paymentDate,
                reminderSet = !isAlreadyPaid,
                customReminderDays = if (!isAlreadyPaid) 1 else null
            ),
            itemName
        )
    }
    PriceType.DEPOSIT_BALANCE -> {
        val depositAmount = _uiState.value.deposit.toDoubleOrNull() ?: 0.0
        val balanceAmount = _uiState.value.balance.toDoubleOrNull() ?: 0.0
        if (depositAmount > 0) {
            paymentRepository.insertPayment(
                Payment(
                    priceId = priceId,
                    amount = depositAmount,
                    dueDate = paymentDate ?: now,
                    isPaid = isAlreadyPaid,
                    paidDate = paymentDate,
                    reminderSet = !isAlreadyPaid,
                    customReminderDays = if (!isAlreadyPaid) 1 else null
                ),
                itemName
            )
        }
        if (balanceAmount > 0) {
            paymentRepository.insertPayment(
                Payment(
                    priceId = priceId,
                    amount = balanceAmount,
                    dueDate = now,
                    isPaid = false,
                    reminderSet = true,
                    customReminderDays = 1
                ),
                itemName
            )
        }
    }
}
```

**Step 6: Update update() — remove purchaseDate from Price copy**

In `update()` (line 250-260), remove `purchaseDate`:
```kotlin
val price = existing.copy(
    type = _uiState.value.priceType,
    totalPrice = totalPrice,
    deposit = ...,
    balance = ...
)
```

**Step 7: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/price/PriceViewModel.kt
git commit -m "refactor: replace purchaseDate with paymentDate in PriceViewModel"
```

---

### Task 5: Update PriceEditScreen — paymentDate for All Statuses

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/price/PriceEditScreen.kt:22` (remove ItemStatus import)
- Modify: `app/src/main/java/com/lolita/app/ui/screen/price/PriceEditScreen.kt:48-50` (remove loadItemStatus)
- Modify: `app/src/main/java/com/lolita/app/ui/screen/price/PriceEditScreen.kt:229-285` (date picker section)

**Step 1: Remove loadItemStatus LaunchedEffect (lines 48-50)**

Delete:
```kotlin
LaunchedEffect(itemId) {
    viewModel.loadItemStatus(itemId)
}
```

**Step 2: Replace date picker section (lines 229-285)**

Remove the `if (uiState.itemStatus == ItemStatus.OWNED)` guard. Show date picker for all statuses. Replace `purchaseDate` → `paymentDate`, `updatePurchaseDate` → `updatePaymentDate`, label from "购买日期 (可选)" → "付款日期 (可选)":

```kotlin
// 付款日期选择 — 所有状态都显示
var showDatePicker by remember { mutableStateOf(false) }
val dateFormat = remember { SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()) }

Box(modifier = Modifier.fillMaxWidth().clickable(enabled = !uiState.isSaving) { showDatePicker = true }) {
    OutlinedTextField(
        value = uiState.paymentDate?.let { dateFormat.format(Date(it)) } ?: "",
        onValueChange = {},
        readOnly = true,
        label = { Text("付款日期 (可选)") },
        placeholder = { Text("点击选择日期") },
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            IconButton(onClick = { showDatePicker = true }) {
                SkinIcon(IconKey.CalendarMonth)
            }
        },
        enabled = false,
        colors = OutlinedTextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = MaterialTheme.colorScheme.outline,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

if (showDatePicker) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = uiState.paymentDate ?: System.currentTimeMillis()
    )
    DatePickerDialog(
        onDismissRequest = { showDatePicker = false },
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let {
                    viewModel.updatePaymentDate(it)
                }
                showDatePicker = false
            }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                viewModel.updatePaymentDate(null)
                showDatePicker = false
            }) {
                Text("清除")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}
```

**Step 3: Remove unused ItemStatus import (line 22)**

Delete: `import com.lolita.app.data.local.entity.ItemStatus`

**Step 4: Build to verify**

Run: `./gradlew.bat assembleDebug`

**Step 5: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/price/PriceEditScreen.kt
git commit -m "refactor: show paymentDate picker for all statuses in PriceEditScreen"
```

---

### Task 6: Update PriceManageScreen and ItemDetailScreen

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/price/PriceManageScreen.kt:194-196`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemDetailScreen.kt:487-493`

**Step 1: Update PriceManageScreen (lines 194-196)**

Replace `price.purchaseDate` display with first Payment's paidDate. Since PriceManageScreen uses `PriceWithPayments`, we can get it from `payments`:

```kotlin
// Replace:
price.purchaseDate?.let { date ->
    PriceRow("购买日期", dateFormat.format(Date(date)))
}
// With:
priceWithPayments.payments
    .filter { it.isPaid }
    .minByOrNull { it.paidDate ?: Long.MAX_VALUE }
    ?.paidDate?.let { date ->
        PriceRow("付款日期", dateFormat.format(Date(date)))
    }
```

**Step 2: Update ItemDetailScreen (lines 487-493)**

Same pattern — replace `price.purchaseDate` with first paid Payment's paidDate. This screen also uses `PriceWithPayments` via the detail data.

```kotlin
// Replace:
price.purchaseDate?.let { date ->
    Text(
        "购买日期: ${dateFormat.format(java.util.Date(date))}",
        ...
    )
}
// With: use the payments list from the PriceWithPayments
payments.filter { it.isPaid }
    .minByOrNull { it.paidDate ?: Long.MAX_VALUE }
    ?.paidDate?.let { date ->
        Text(
            "付款日期: ${dateFormat.format(java.util.Date(date))}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/price/PriceManageScreen.kt \
      app/src/main/java/com/lolita/app/ui/screen/item/ItemDetailScreen.kt
git commit -m "refactor: display Payment.paidDate instead of Price.purchaseDate in UI"
```

---

### Task 7: Update PaymentCalendarScreen

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt:144` (purchaseDate → firstPaidDate)
- Modify: `app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt:459` (purchaseDate → firstPaidDate)
- Modify: `app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt:474` (purchaseDate → firstPaidDate)

**Step 1: Replace all `p.purchaseDate` / `price.purchaseDate` references with `p.firstPaidDate` / `price.firstPaidDate`**

These are references to `PriceWithStatus.purchaseDate` which was renamed to `firstPaidDate` in Task 3.

Also update the display label from "购入" to "付款" at line 459:
```kotlin
// Before:
"$typeLabel ¥${String.format("%.2f", price.totalPrice)}  购入: ${sdf.format(Date(price.purchaseDate))}"
// After:
"$typeLabel ¥${String.format("%.2f", price.totalPrice)}  付款: ${sdf.format(Date(price.firstPaidDate))}"
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt
git commit -m "refactor: use PriceWithStatus.firstPaidDate in PaymentCalendarScreen"
```

---

### Task 8: Update TaobaoImportViewModel — Create Payment for Regular Items

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/import/TaobaoImportViewModel.kt:518-525` (Scenario C)
- Modify: `app/src/main/java/com/lolita/app/ui/screen/import/TaobaoImportViewModel.kt:446` (Scenario A purchaseDate)
- Modify: `app/src/main/java/com/lolita/app/ui/screen/import/TaobaoImportViewModel.kt:480` (Scenario B purchaseDate)

**Step 1: Update Scenario C — add Payment creation (lines 518-525)**

After `priceRepository.insertPrice(...)`, add Payment creation:
```kotlin
val priceId = priceRepository.insertPrice(
    Price(
        itemId = itemId,
        type = PriceType.FULL,
        totalPrice = importItem.price
    )
)
// Create paid Payment record for imported item
val purchaseMillis = parseDateToMillis(importItem.purchaseDate)
val paidDate = purchaseMillis ?: System.currentTimeMillis()
paymentRepository.insertPayment(
    Payment(
        priceId = priceId,
        amount = importItem.price,
        dueDate = paidDate,
        isPaid = true,
        paidDate = paidDate,
        reminderSet = false
    ),
    importItem.name
)
```

Note: `priceRepository.insertPrice` already returns `Long` (the priceId). The current code discards it — we need to capture it.

**Step 2: Update Scenario A and B — remove purchaseDate from Price constructor**

Line 446: `purchaseDate = parseDateToMillis(mainItem.purchaseDate)` → remove this parameter
Line 480: `purchaseDate = parseDateToMillis(importItem.purchaseDate)` → remove this parameter

The paidDate is already set on the Payment records in these scenarios.

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/import/TaobaoImportViewModel.kt
git commit -m "feat: create Payment for regular Taobao imports, remove purchaseDate from Price"
```

---

### Task 9: Update ImportDetailScreen Labels

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/import/ImportDetailScreen.kt:246` (label text)
- Modify: `app/src/main/java/com/lolita/app/ui/screen/import/ImportDetailScreen.kt:311` (label text)

**Step 1: Change labels**

Line 246: `label = { Text("购买日期") }` → `label = { Text("付款日期") }`
Line 311: `label = { Text("购买日期") }` → `label = { Text("付款日期") }`

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/import/ImportDetailScreen.kt
git commit -m "refactor: rename 购买日期 to 付款日期 in ImportDetailScreen"
```

---

### Task 10: Update BackupManager

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/file/BackupManager.kt:137-139` (CSV export)

**Step 1: Update CSV header and data (lines 137-139)**

```kotlin
// Before:
sb.appendLine("id,item_id,type,total_price,deposit,balance,purchase_date")
database.priceDao().getAllPricesList().forEach { p ->
    sb.appendLine("${p.id},${p.itemId},${p.type},${p.totalPrice},${p.deposit},${p.balance},${p.purchaseDate}")
}
// After:
sb.appendLine("id,item_id,type,total_price,deposit,balance")
database.priceDao().getAllPricesList().forEach { p ->
    sb.appendLine("${p.id},${p.itemId},${p.type},${p.totalPrice},${p.deposit},${p.balance}")
}
```

**Step 2: Check JSON export/import for purchaseDate references**

Search BackupManager for any Gson serialization of Price objects. If Price is serialized directly, removing the field from the entity is sufficient — Gson will simply not include it. For import, Gson ignores unknown JSON fields by default, so old backups with `purchaseDate` will import fine.

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/file/BackupManager.kt
git commit -m "refactor: remove purchaseDate from CSV export in BackupManager"
```

---

### Task 11: Update PriceRepository

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/repository/PriceRepository.kt:90` (getItemsByPurchaseMonth)

**Step 1: No signature changes needed**

`getItemsByPurchaseMonth` and `getPricesWithStatusByDateRange` are thin wrappers — they just delegate to DAO. The DAO signatures haven't changed, only the SQL. No code changes needed in PriceRepository unless compilation fails.

**Step 2: Build full project**

Run: `./gradlew.bat assembleDebug`

Fix any remaining compilation errors.

**Step 3: Commit if any fixes were needed**

---

### Task 12: Version Bump and Release Build

**Files:**
- Modify: `app/build.gradle.kts` (versionCode, versionName)

**Step 1: Bump version**

Increment `versionCode` by 1, bump minor version (e.g. 2.0 → 2.1 or current → +0.1).

**Step 2: Release build**

Run: `./gradlew.bat assembleRelease`

**Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "chore: bump version for price/payment unification"
```
