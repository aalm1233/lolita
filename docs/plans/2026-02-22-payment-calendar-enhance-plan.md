# 付款日历增强 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 增强付款日历功能：Tab 前移、日历格子显示金额、统计行新增总待付卡片

**Architecture:** 修改 3 个文件，新增 1 个 DAO 查询，ViewModel 增加 1 个字段，UI 层重构日历格子和统计行

**Tech Stack:** Kotlin, Jetpack Compose, Room, StateFlow

---

### Task 1: DAO 新增总待付笔数查询

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/local/dao/PaymentDao.kt:96` (在 `getOverdueAmount` 之后)
- Modify: `app/src/main/java/com/lolita/app/data/repository/PaymentRepository.kt:151` (在 `getOverdueAmount` 之后)

**Step 1: 在 PaymentDao 添加查询**

在 `PaymentDao.kt` 的 `getOverdueAmount()` 方法之后（约第96行后）添加：

```kotlin
@Query("""
    SELECT COUNT(*) FROM payments p
    INNER JOIN prices pr ON p.price_id = pr.id
    INNER JOIN items i ON pr.item_id = i.id
    WHERE p.is_paid = 0 AND i.status = 'OWNED'
""")
fun getTotalUnpaidCount(): Flow<Int>
```

**Step 2: 在 PaymentRepository 添加包装方法**

在 `PaymentRepository.kt` 的 `getOverdueAmount()` 方法之后添加：

```kotlin
fun getTotalUnpaidCount(): Flow<Int> =
    paymentDao.getTotalUnpaidCount()
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/local/dao/PaymentDao.kt app/src/main/java/com/lolita/app/data/repository/PaymentRepository.kt
git commit -m "feat: add getTotalUnpaidCount query to PaymentDao"
```

---

### Task 2: ViewModel 增加 totalUnpaidCount 字段

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt:44-56` (UiState)
- Modify: `app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt:88-108` (loadData combine)

**Step 1: UiState 新增字段**

在 `PaymentCalendarUiState` data class 中，`overdueAmount` 之后添加：

```kotlin
val totalUnpaidCount: Int = 0,
```

完整 UiState 变为：
```kotlin
data class PaymentCalendarUiState(
    val currentYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val currentMonth: Int = Calendar.getInstance().get(Calendar.MONTH),
    val selectedDay: Int? = null,
    val monthPayments: List<PaymentWithItemInfo> = emptyList(),
    val monthUnpaidTotal: Double = 0.0,
    val totalUnpaidAmount: Double = 0.0,
    val totalUnpaidCount: Int = 0,
    val overdueAmount: Double = 0.0,
    val monthPaidTotal: Double = 0.0,
    val monthPaidCount: Int = 0,
    val monthUnpaidCount: Int = 0,
    val isLoading: Boolean = true
)
```

**Step 2: loadData 中 combine 增加第5个 Flow**

将 `loadData()` 中的 `combine` 从 4 个 Flow 改为 5 个。由于 `combine` 最多支持 5 个参数，刚好够用：

```kotlin
loadDataJob = viewModelScope.launch {
    combine(
        paymentRepository.getPaymentsWithItemInfoByDateRange(monthStart, monthEnd),
        paymentRepository.getMonthUnpaidTotal(monthStart, monthEnd),
        paymentRepository.getTotalUnpaidAmount(),
        paymentRepository.getOverdueAmount(now),
        paymentRepository.getTotalUnpaidCount()
    ) { payments, monthUnpaid, totalUnpaid, overdue, totalUnpaidCount ->
        val paidPayments = payments.filter { it.isPaid }
        val unpaidPayments = payments.filter { !it.isPaid }
        _uiState.value.copy(
            monthPayments = payments,
            monthUnpaidTotal = monthUnpaid,
            totalUnpaidAmount = totalUnpaid,
            totalUnpaidCount = totalUnpaidCount,
            overdueAmount = overdue,
            monthPaidTotal = paidPayments.sumOf { it.amount },
            monthPaidCount = paidPayments.size,
            monthUnpaidCount = unpaidPayments.size,
            isLoading = false
        )
    }.collect { _uiState.value = it }
}
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt
git commit -m "feat: add totalUnpaidCount to PaymentCalendarViewModel"
```

---

### Task 3: 统计行扩展为 4 卡片

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt:160-183` (PaymentCalendarContent 中 StatsRow 调用)
- Modify: `app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt:238-254` (StatsRow composable)

**Step 1: 修改 StatsRow 签名和布局**

```kotlin
@Composable
private fun StatsRow(
    monthPaid: Double,
    monthPaidCount: Int,
    monthUnpaid: Double,
    monthUnpaidCount: Int,
    totalUnpaid: Double,
    totalUnpaidCount: Int,
    overdue: Double
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MiniStatCard("当月已付", monthPaid, Color(0xFF4CAF50), Modifier.weight(1f), subtitle = "${monthPaidCount}笔")
        MiniStatCard("当月待付", monthUnpaid, MaterialTheme.colorScheme.primary, Modifier.weight(1f), subtitle = "${monthUnpaidCount}笔")
        MiniStatCard("总待付", totalUnpaid, Color(0xFFFF9800), Modifier.weight(1f), subtitle = "${totalUnpaidCount}笔")
        MiniStatCard("已逾期", overdue, Color(0xFFD32F2F), Modifier.weight(1f))
    }
}
```

注意：间距从 `8.dp` 缩小到 `6.dp`，标签从「总待付尾款」缩短为「总待付」以适配 4 卡片宽度。

**Step 2: 更新 PaymentCalendarContent 中的 StatsRow 调用**

```kotlin
StatsRow(
    monthPaid = uiState.monthPaidTotal,
    monthPaidCount = uiState.monthPaidCount,
    monthUnpaid = uiState.monthUnpaidTotal,
    monthUnpaidCount = uiState.monthUnpaidCount,
    totalUnpaid = uiState.totalUnpaidAmount,
    totalUnpaidCount = uiState.totalUnpaidCount,
    overdue = uiState.overdueAmount
)
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt
git commit -m "feat: add total unpaid card to stats row (4 cards)"
```

---

### Task 4: 日历格子增强 — 显示金额替代圆点

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt:374` (DayStatus enum 附近)
- Modify: `app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt:376-418` (DayCell composable)
- Modify: `app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt:306-372` (CalendarGrid composable)
- Modify: `app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt:486-516` (buildDayStatusMap)

**Step 1: 新增 DayAmountInfo 数据类**

在 `DayStatus` enum 附近添加：

```kotlin
private data class DayAmountInfo(
    val paidTotal: Double = 0.0,
    val unpaidTotal: Double = 0.0
)
```

**Step 2: 新增 buildDayAmountMap 辅助函数**

在 `buildDayStatusMap` 函数附近添加：

```kotlin
private fun buildDayAmountMap(
    payments: List<PaymentWithItemInfo>,
    year: Int,
    month: Int
): Map<Int, DayAmountInfo> {
    val cal = Calendar.getInstance()
    val dayAmounts = mutableMapOf<Int, DayAmountInfo>()
    payments.forEach { p ->
        cal.timeInMillis = p.dueDate
        if (cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month) {
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val current = dayAmounts.getOrPut(day) { DayAmountInfo() }
            if (p.isPaid) {
                dayAmounts[day] = current.copy(paidTotal = current.paidTotal + p.amount)
            } else {
                dayAmounts[day] = current.copy(unpaidTotal = current.unpaidTotal + p.amount)
            }
        }
    }
    return dayAmounts
}
```

**Step 3: 修改 CalendarGrid 传递 dayAmountMap**

在 `CalendarGrid` composable 中，在 `dayStatusMap` 计算之后添加：

```kotlin
val dayAmountMap = buildDayAmountMap(payments, year, month)
```

修改 `DayCell` 调用，传入 `amountInfo` 参数：

```kotlin
DayCell(
    day = day,
    status = status,
    amountInfo = dayAmountMap[day],
    isSelected = isSelected,
    modifier = Modifier.weight(1f),
    onClick = { onDayClick(day) }
)
```

**Step 4: 重写 DayCell composable**

替换圆点为金额文字：

```kotlin
@Composable
private fun DayCell(
    day: Int,
    status: DayStatus?,
    amountInfo: DayAmountInfo?,
    isSelected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
        animationSpec = tween(200), label = "dayBg"
    )

    Column(
        modifier = modifier
            .clip(CircleShape)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            day.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        if (amountInfo != null) {
            if (amountInfo.paidTotal > 0) {
                Text(
                    "¥${String.format("%.0f", amountInfo.paidTotal)}",
                    fontSize = 9.sp,
                    color = Color(0xFF4CAF50),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 10.sp
                )
            }
            if (amountInfo.unpaidTotal > 0) {
                Text(
                    "¥${String.format("%.0f", amountInfo.unpaidTotal)}",
                    fontSize = 9.sp,
                    color = when (status) {
                        DayStatus.OVERDUE -> Color(0xFFD32F2F)
                        DayStatus.UPCOMING -> Color(0xFFFF9800)
                        else -> MaterialTheme.colorScheme.primary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 10.sp
                )
            }
        }
    }
}
```

**Step 5: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt
git commit -m "feat: show paid/unpaid amounts in calendar day cells"
```

---

### Task 5: Tab 顺序调整

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/stats/StatsPageScreen.kt:23` (tabs 列表)
- Modify: `app/src/main/java/com/lolita/app/ui/screen/stats/StatsPageScreen.kt:64-79` (when 分支)

**Step 1: 修改 tabs 列表**

```kotlin
val tabs = listOf("总览", "付款日历", "消费分布", "消费趋势", "愿望单")
```

**Step 2: 修改 when 分支**

```kotlin
when (page) {
    0 -> StatsContent(
        onNavigateToFilteredList = onNavigateToFilteredList,
        onNavigateToItemDetail = onNavigateToItemDetail
    )
    1 -> PaymentCalendarContent()
    2 -> SpendingDistributionContent(
        onNavigateToFilteredList = onNavigateToFilteredList
    )
    3 -> SpendingTrendContent(
        onNavigateToFilteredList = onNavigateToFilteredList
    )
    4 -> WishlistAnalysisContent(
        onNavigateToFilteredList = onNavigateToFilteredList
    )
}
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/stats/StatsPageScreen.kt
git commit -m "feat: move payment calendar tab to second position"
```

---

### Task 6: 版本号更新 + Release 构建

**Files:**
- Modify: `app/build.gradle.kts` — versionCode +1, versionName bump minor

**Step 1: 更新版本号**

versionCode: 2 → 3
versionName: "2.0" → "2.1"

**Step 2: Release 构建**

```bash
./gradlew.bat assembleRelease
```

验证构建成功。

**Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "chore: bump version to 2.1 (versionCode 3)"
```
