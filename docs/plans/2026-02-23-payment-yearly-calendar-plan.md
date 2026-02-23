# 付款年历 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the monthly payment calendar with a yearly 4x3 month-card view showing per-month stats, with expandable payment detail list on month selection.

**Architecture:** Complete rewrite of `PaymentCalendarScreen.kt` — new UiState, ViewModel queries full year, UI renders 4x3 month card grid + expandable detail list. Reuses existing `PaymentInfoCard` and `StatChip`. No DB/DAO changes needed.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, existing skin system colors.

---

### Task 1: Rewrite UiState and ViewModel for yearly data

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt:44-158`

**Step 1: Replace UiState data class**

Replace `PaymentCalendarUiState` (lines 44-57) with:

```kotlin
data class MonthStats(
    val month: Int, // 0-based
    val paidTotal: Double = 0.0,
    val paidCount: Int = 0,
    val unpaidTotal: Double = 0.0,
    val unpaidCount: Int = 0,
    val overdueAmount: Double = 0.0
)

data class PaymentCalendarUiState(
    val currentYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val selectedMonth: Int? = null, // 0-based, null = no month selected
    val yearPayments: List<PaymentWithItemInfo> = emptyList(),
    val monthStatsMap: Map<Int, MonthStats> = emptyMap(), // month(0-based) -> stats
    val yearPaidTotal: Double = 0.0,
    val yearPaidCount: Int = 0,
    val yearUnpaidTotal: Double = 0.0,
    val yearUnpaidCount: Int = 0,
    val yearOverdueAmount: Double = 0.0,
    val isLoading: Boolean = true
)
```

**Step 2: Replace ViewModel**

Replace `PaymentCalendarViewModel` (lines 59-158) with:

```kotlin
class PaymentCalendarViewModel(
    private val paymentRepository: PaymentRepository = AppModule.paymentRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentCalendarUiState())
    val uiState: StateFlow<PaymentCalendarUiState> = _uiState.asStateFlow()

    private var loadDataJob: Job? = null

    init {
        loadData()
    }

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
            paymentRepository.getPaymentsWithItemInfoByDateRange(yearStart, yearEnd)
                .collect { payments ->
                    val monthStatsMap = buildMonthStatsMap(payments, state.currentYear, now)
                    val yearPaid = payments.filter { it.isPaid }
                    val yearUnpaid = payments.filter { !it.isPaid }
                    _uiState.value = _uiState.value.copy(
                        yearPayments = payments,
                        monthStatsMap = monthStatsMap,
                        yearPaidTotal = yearPaid.sumOf { it.amount },
                        yearPaidCount = yearPaid.size,
                        yearUnpaidTotal = yearUnpaid.sumOf { it.amount },
                        yearUnpaidCount = yearUnpaid.size,
                        yearOverdueAmount = yearUnpaid.filter { it.dueDate < now }.sumOf { it.amount },
                        isLoading = false
                    )
                }
        }
    }

    fun previousYear() {
        _uiState.value = _uiState.value.copy(
            currentYear = _uiState.value.currentYear - 1,
            selectedMonth = null,
            isLoading = true
        )
        loadData()
    }

    fun nextYear() {
        _uiState.value = _uiState.value.copy(
            currentYear = _uiState.value.currentYear + 1,
            selectedMonth = null,
            isLoading = true
        )
        loadData()
    }

    fun selectMonth(month: Int) {
        val current = _uiState.value.selectedMonth
        _uiState.value = _uiState.value.copy(
            selectedMonth = if (current == month) null else month
        )
    }

    fun markAsPaid(paymentId: Long, itemName: String = "") {
        viewModelScope.launch {
            val payment = paymentRepository.getPaymentById(paymentId) ?: return@launch
            paymentRepository.updatePayment(
                payment.copy(isPaid = true, paidDate = System.currentTimeMillis()),
                itemName = itemName
            )
        }
    }

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
}
```

**Step 3: Add helper to get payments for a month**

Add after ViewModel class (replacing old `getPaymentsForDay`, `buildDayStatusMap`, `buildDayAmountMap`):

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
git commit -m "refactor: rewrite ViewModel and UiState for yearly payment calendar"
```

---

### Task 2: Rewrite YearHeader (replacing MonthHeader)

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt`

**Step 1: Replace MonthHeader with YearHeader**

Delete `MonthHeader` composable. Replace with:

```kotlin
@Composable
private fun YearHeader(
    year: Int,
    yearPaidTotal: Double,
    yearPaidCount: Int,
    yearUnpaidTotal: Double,
    yearUnpaidCount: Int,
    yearOverdueAmount: Double,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious) {
                    SkinIcon(IconKey.KeyboardArrowLeft)
                }
                Text(
                    "${year}年",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onNext) {
                    SkinIcon(IconKey.KeyboardArrowRight)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatChip("已付", yearPaidTotal, yearPaidCount, Color(0xFF4CAF50))
                StatChip("待付", yearUnpaidTotal, yearUnpaidCount, MaterialTheme.colorScheme.primary)
                if (yearOverdueAmount > 0) {
                    StatChip("逾期", yearOverdueAmount, null, Color(0xFFD32F2F))
                }
            }
        }
    }
}
```

`StatChip` composable is kept as-is (already exists, reusable).

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt
git commit -m "feat: add YearHeader with yearly stats, replace MonthHeader"
```

---

### Task 3: Add MonthCardGrid and MonthCard composables

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt`

**Step 1: Add MonthCardGrid**

Delete `CalendarGrid`, `DayCell`, `DayStatus`, `DayAmountInfo` composables/classes. Add:

```kotlin
@Composable
private fun MonthCardGrid(
    monthStatsMap: Map<Int, MonthStats>,
    selectedMonth: Int?,
    currentYear: Int,
    onMonthClick: (Int) -> Unit
) {
    val todayCal = Calendar.getInstance()
    val isCurrentYear = todayCal.get(Calendar.YEAR) == currentYear
    val currentMonth = if (isCurrentYear) todayCal.get(Calendar.MONTH) else -1

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(4) { col ->
                    val month = row * 4 + col
                    MonthCard(
                        month = month,
                        stats = monthStatsMap[month],
                        isCurrentMonth = month == currentMonth,
                        isSelected = month == selectedMonth,
                        modifier = Modifier.weight(1f),
                        onClick = { onMonthClick(month) }
                    )
                }
            }
        }
    }
}
```

**Step 2: Add MonthCard**

```kotlin
@Composable
private fun MonthCard(
    month: Int,
    stats: MonthStats?,
    isCurrentMonth: Boolean,
    isSelected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val hasPayments = stats != null
    val hasOverdue = (stats?.overdueAmount ?: 0.0) > 0

    val bgColor by animateColorAsState(
        if (isSelected) primaryColor.copy(alpha = 0.15f)
        else MaterialTheme.colorScheme.surface,
        animationSpec = tween(200), label = "monthBg"
    )

    Card(
        modifier = modifier
            .then(
                if (isCurrentMonth) Modifier.border(
                    2.dp, primaryColor, MaterialTheme.shapes.medium
                ) else Modifier
            )
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                "${month + 1}月",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (hasPayments) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            if (stats != null) {
                if (stats.paidTotal > 0) {
                    Text(
                        "已付 ¥${String.format("%.0f", stats.paidTotal)}",
                        fontSize = 10.sp,
                        color = Color(0xFF4CAF50),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "(${stats.paidCount}笔)",
                        fontSize = 9.sp,
                        color = Color(0xFF4CAF50).copy(alpha = 0.7f)
                    )
                }
                if (stats.unpaidTotal > 0) {
                    val unpaidColor = if (hasOverdue) Color(0xFFD32F2F) else primaryColor
                    Text(
                        "待付 ¥${String.format("%.0f", stats.unpaidTotal)}",
                        fontSize = 10.sp,
                        color = unpaidColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "(${stats.unpaidCount}笔)",
                        fontSize = 9.sp,
                        color = unpaidColor.copy(alpha = 0.7f)
                    )
                }
                if (hasOverdue) {
                    Text(
                        "逾期 ¥${String.format("%.0f", stats.overdueAmount)}",
                        fontSize = 9.sp,
                        color = Color(0xFFD32F2F)
                    )
                }
            }
        }
    }
}
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt
git commit -m "feat: add MonthCardGrid and MonthCard for yearly calendar view"
```

---

### Task 4: Rewrite PaymentCalendarContent for yearly view

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt`

**Step 1: Replace PaymentCalendarContent**

Replace the entire `PaymentCalendarContent` composable (lines 163-235) with:

```kotlin
@Composable
fun PaymentCalendarContent(
    viewModel: PaymentCalendarViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            YearHeader(
                year = uiState.currentYear,
                yearPaidTotal = uiState.yearPaidTotal,
                yearPaidCount = uiState.yearPaidCount,
                yearUnpaidTotal = uiState.yearUnpaidTotal,
                yearUnpaidCount = uiState.yearUnpaidCount,
                yearOverdueAmount = uiState.yearOverdueAmount,
                onPrevious = viewModel::previousYear,
                onNext = viewModel::nextYear
            )
        }
        item {
            MonthCardGrid(
                monthStatsMap = uiState.monthStatsMap,
                selectedMonth = uiState.selectedMonth,
                currentYear = uiState.currentYear,
                onMonthClick = viewModel::selectMonth
            )
        }

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
                    PaymentInfoCard(
                        payment = payment,
                        onMarkPaid = {
                            coroutineScope.launch {
                                viewModel.markAsPaid(payment.paymentId, payment.itemName)
                            }
                        }
                    )
                }
            }
        }
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt
git commit -m "feat: rewrite PaymentCalendarContent for yearly calendar view"
```

---

### Task 5: Clean up dead code and verify build

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt`

**Step 1: Remove dead code**

Delete all remaining monthly-calendar-specific code:
- `CalendarGrid` composable
- `DayCell` composable
- `DayStatus` enum
- `DayAmountInfo` data class
- `buildDayStatusMap` function
- `buildDayAmountMap` function
- `getPaymentsForDay` function

Also remove unused imports:
- `CircleShape` (was used by DayCell today highlight)
- `TextAlign` (was used by weekday headers)

**Step 2: Verify build**

```bash
./gradlew.bat compileDebugKotlin 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt
git commit -m "refactor: remove dead monthly calendar code"
```

---

### Task 6: Version bump and release build

**Files:**
- Modify: `app/build.gradle.kts`

**Step 1: Bump version**

Check current version, increment versionCode by 1 and bump versionName minor version.

**Step 2: Build release APK**

```bash
./gradlew.bat assembleRelease 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "chore: bump version for payment yearly calendar"
```
