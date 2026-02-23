# 付款年历按应付款时间(dueDate)展示 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将付款年历从 Price 粒度 + paidDate 驱动改为 Payment 粒度 + dueDate 驱动，使所有付款（含未付）按应付款时间归入月份。

**Architecture:** 新增 `PaymentWithItemInfo` 数据类和对应 DAO 查询，按 `dueDate` 范围筛选 Payment 级别数据。ViewModel 状态模型和 UI 详情卡片同步改为 Payment 粒度。

**Tech Stack:** Kotlin, Room, Jetpack Compose, StateFlow

---

### Task 1: 新增 PaymentWithItemInfo 数据类和 DAO 查询

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/local/dao/PriceDao.kt:193-209`

**Step 1: 在 PriceDao.kt 末尾（PriceWithStatus 之后）添加 PaymentWithItemInfo 数据类**

```kotlin
data class PaymentWithItemInfo(
    val paymentId: Long,
    val amount: Double,
    val dueDate: Long,
    val isPaid: Boolean,
    val paidDate: Long?,
    val priceId: Long,
    val priceType: com.lolita.app.data.local.entity.PriceType,
    val itemName: String,
    val itemId: Long
)
```

**Step 2: 在 PriceDao 接口中添加新查询方法（在 getPricesWithStatusByDateRange 之后）**

```kotlin
@Query("""
    SELECT pay.id AS paymentId, pay.amount, pay.due_date AS dueDate,
           pay.is_paid AS isPaid, pay.paid_date AS paidDate,
           pr.id AS priceId, pr.type AS priceType,
           i.name AS itemName, i.id AS itemId
    FROM payments pay
    INNER JOIN prices pr ON pay.price_id = pr.id
    INNER JOIN items i ON pr.item_id = i.id
    WHERE pay.due_date BETWEEN :startDate AND :endDate
      AND i.status IN ('OWNED', 'PENDING_BALANCE')
    ORDER BY pay.due_date ASC
""")
fun getPaymentsWithItemInfoByDateRange(startDate: Long, endDate: Long): Flow<List<PaymentWithItemInfo>>
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/local/dao/PriceDao.kt
git commit -m "feat: add PaymentWithItemInfo data class and DAO query by dueDate"
```

---

### Task 2: 在 PriceRepository 中暴露新查询

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/repository/PriceRepository.kt:92-93`

**Step 1: 在 PriceRepository 中添加新方法（在 getPricesWithStatusByDateRange 之后）**

```kotlin
fun getPaymentsWithItemInfoByDateRange(startDate: Long, endDate: Long) =
    priceDao.getPaymentsWithItemInfoByDateRange(startDate, endDate)
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/repository/PriceRepository.kt
git commit -m "feat: expose getPaymentsWithItemInfoByDateRange in PriceRepository"
```

---

### Task 3: 重写 ViewModel 状态模型和数据加载逻辑

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt:25-163`

**Step 1: 更新 import，将 PriceWithStatus 替换为 PaymentWithItemInfo**

替换:
```kotlin
import com.lolita.app.data.local.dao.PriceWithStatus
```
为:
```kotlin
import com.lolita.app.data.local.dao.PaymentWithItemInfo
```

**Step 2: 更新 PaymentCalendarUiState，将 yearPrices 改为 yearPayments**

```kotlin
data class PaymentCalendarUiState(
    val currentYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val selectedMonth: Int? = null,
    val yearPayments: List<PaymentWithItemInfo> = emptyList(),
    val monthStatsMap: Map<Int, MonthStats> = emptyMap(),
    val yearPaidTotal: Double = 0.0,
    val yearPaidCount: Int = 0,
    val yearUnpaidTotal: Double = 0.0,
    val yearUnpaidCount: Int = 0,
    val yearOverdueAmount: Double = 0.0,
    val isLoading: Boolean = true
)
```

**Step 3: 重写 loadData() 方法，使用新查询并按 Payment 粒度统计**

```kotlin
private fun loadData() {
    loadDataJob?.cancel()
    val state = _uiState.value
    val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, state.currentYear)
        set(Calendar.MONTH, Calendar.JANUARY)
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val yearStart = cal.timeInMillis
    cal.add(Calendar.YEAR, 1)
    val yearEnd = cal.timeInMillis - 1
    val now = System.currentTimeMillis()

    loadDataJob = viewModelScope.launch {
        priceRepository.getPaymentsWithItemInfoByDateRange(yearStart, yearEnd)
            .collect { payments ->
                val monthStatsMap = buildMonthStatsMap(payments, state.currentYear, now)
                val paid = payments.filter { it.isPaid }
                val unpaid = payments.filter { !it.isPaid }
                _uiState.value = _uiState.value.copy(
                    yearPayments = payments,
                    monthStatsMap = monthStatsMap,
                    yearPaidTotal = paid.sumOf { it.amount },
                    yearPaidCount = paid.size,
                    yearUnpaidTotal = unpaid.sumOf { it.amount },
                    yearUnpaidCount = unpaid.size,
                    yearOverdueAmount = unpaid.filter { it.dueDate < now }.sumOf { it.amount },
                    isLoading = false
                )
            }
    }
}
```

**Step 4: 重写 buildMonthStatsMap() 方法，基于 Payment 粒度 + dueDate**

```kotlin
private fun buildMonthStatsMap(
    payments: List<PaymentWithItemInfo>,
    year: Int,
    now: Long
): Map<Int, MonthStats> {
    val cal = Calendar.getInstance()
    val monthPayments = mutableMapOf<Int, MutableList<PaymentWithItemInfo>>()
    payments.forEach { p ->
        cal.timeInMillis = p.dueDate
        if (cal.get(Calendar.YEAR) == year) {
            val month = cal.get(Calendar.MONTH)
            monthPayments.getOrPut(month) { mutableListOf() }.add(p)
        }
    }
    return monthPayments.mapValues { (month, list) ->
        val paid = list.filter { it.isPaid }
        val unpaid = list.filter { !it.isPaid }
        MonthStats(
            month = month,
            paidTotal = paid.sumOf { it.amount },
            paidCount = paid.size,
            unpaidTotal = unpaid.sumOf { it.amount },
            unpaidCount = unpaid.size,
            overdueAmount = unpaid.filter { it.dueDate < now }.sumOf { it.amount }
        )
    }
}
```

**Step 5: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt
git commit -m "feat: rewrite PaymentCalendarViewModel to use Payment granularity + dueDate"
```

---

### Task 4: 更新 UI 层 — 列表和详情卡片改为 Payment 粒度

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt:165-477`

**Step 1: 更新 PaymentCalendarContent 中的列表部分**

将 `getPricesForMonth` 调用替换为 `getPaymentsForMonth`，将 `items` 的 key 从 `priceId` 改为 `paymentId`，将 `PriceInfoCard` 改为 `PaymentInfoCard`：

```kotlin
val selectedPayments = uiState.selectedMonth?.let { month ->
    getPaymentsForMonth(uiState.yearPayments, uiState.currentYear, month)
} ?: emptyList()

if (uiState.selectedMonth != null) {
    item {
        Text(
            "${uiState.selectedMonth!! + 1}月 付款记录",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
    if (selectedPayments.isEmpty()) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "当月无付款记录",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        items(selectedPayments, key = { it.paymentId }) { payment ->
            PaymentInfoCard(payment = payment)
        }
    }
}
```

**Step 2: 替换 PriceInfoCard 为 PaymentInfoCard**

删除原 `PriceInfoCard` composable，替换为：

```kotlin
@Composable
private fun PaymentInfoCard(payment: PaymentWithItemInfo) {
    val typeLabel = when (payment.priceType) {
        PriceType.DEPOSIT_BALANCE -> "定金尾款"
        PriceType.FULL -> "全款"
    }
    val now = System.currentTimeMillis()
    val isOverdue = !payment.isPaid && payment.dueDate < now
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isOverdue) CardDefaults.cardColors(
            containerColor = Color(0xFFD32F2F).copy(alpha = 0.06f)
        ) else CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    payment.itemName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = if (payment.isPaid) Color(0xFF4CAF50).copy(alpha = 0.1f)
                    else if (isOverdue) Color(0xFFD32F2F).copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        if (payment.isPaid) "已付清" else if (isOverdue) "已逾期" else "待付款",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (payment.isPaid) Color(0xFF4CAF50)
                        else if (isOverdue) Color(0xFFD32F2F)
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "$typeLabel ¥${String.format("%.2f", payment.amount)}  应付: ${sdf.format(Date(payment.dueDate))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

**Step 3: 替换 getPricesForMonth 为 getPaymentsForMonth**

删除原 `getPricesForMonth` 函数，替换为：

```kotlin
private fun getPaymentsForMonth(
    payments: List<PaymentWithItemInfo>,
    year: Int,
    month: Int
): List<PaymentWithItemInfo> {
    val cal = Calendar.getInstance()
    return payments.filter { p ->
        cal.timeInMillis = p.dueDate
        cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month
    }
}
```

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt
git commit -m "feat: update calendar UI to Payment granularity with PaymentInfoCard"
```

---

### Task 5: 构建验证

**Step 1: 运行 release 构建确认编译通过**

```bash
./gradlew.bat assembleRelease
```

Expected: BUILD SUCCESSFUL
