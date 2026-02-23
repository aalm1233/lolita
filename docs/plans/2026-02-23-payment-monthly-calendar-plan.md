# 付款月历 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Redesign the payment calendar from a compact grid to a large-cell monthly calendar view with inline amount previews, merged stats in the month header, and expanded detail list on day selection.

**Architecture:** Pure UI refactor of `PaymentCalendarScreen.kt` + tab rename in `StatsPageScreen.kt`. ViewModel gets a minor helper method addition. No database, DAO, navigation, or skin system changes.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, existing skin system colors.

---

### Task 1: Rename tab title "付款日历" → "付款月历"

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/stats/StatsPageScreen.kt:23`

**Step 1: Change tab title**

In `StatsPageScreen.kt` line 23, change:
```kotlin
val tabs = listOf("总览", "付款日历", "消费分布", "消费趋势", "愿望单")
```
to:
```kotlin
val tabs = listOf("总览", "付款月历", "消费分布", "消费趋势", "愿望单")
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/stats/StatsPageScreen.kt
git commit -m "refactor: rename payment tab from 付款日历 to 付款月历"
```

---

### Task 2: Add ViewModel helper for day payment count

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt:42-57` (UiState) and add helper

**Step 1: Add `dayPaymentCountMap` to DayAmountInfo**

In `PaymentCalendarScreen.kt`, update `DayAmountInfo` (line 386-389) to also track payment count:

```kotlin
private data class DayAmountInfo(
    val paidTotal: Double = 0.0,
    val unpaidTotal: Double = 0.0,
    val paidCount: Int = 0,
    val unpaidCount: Int = 0
)
```

**Step 2: Update `buildDayAmountMap` to track counts**

In `buildDayAmountMap` (line 546-566), update the accumulation logic:

```kotlin
if (p.isPaid) {
    dayAmounts[day] = current.copy(
        paidTotal = current.paidTotal + p.amount,
        paidCount = current.paidCount + 1
    )
} else {
    dayAmounts[day] = current.copy(
        unpaidTotal = current.unpaidTotal + p.amount,
        unpaidCount = current.unpaidCount + 1
    )
}
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt
git commit -m "refactor: add payment counts to DayAmountInfo for monthly calendar"
```

---

### Task 3: Rewrite MonthHeader with embedded stats

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt`

**Step 1: Replace MonthHeader composable**

Delete the existing `MonthHeader` (lines 293-312) and `StatsRow` + `MiniStatCard` (lines 243-291). Replace `MonthHeader` with a new version that includes stats:

```kotlin
@Composable
private fun MonthHeader(
    year: Int,
    month: Int,
    monthPaidTotal: Double,
    monthPaidCount: Int,
    monthUnpaidTotal: Double,
    monthUnpaidCount: Int,
    overdueAmount: Double,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Row 1: arrows + year/month
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious) {
                    SkinIcon(IconKey.KeyboardArrowLeft)
                }
                Text(
                    "${year}年${month + 1}月",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onNext) {
                    SkinIcon(IconKey.KeyboardArrowRight)
                }
            }
            // Row 2: stats summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatChip("已付", monthPaidTotal, monthPaidCount, Color(0xFF4CAF50))
                StatChip("待付", monthUnpaidTotal, monthUnpaidCount, MaterialTheme.colorScheme.primary)
                if (overdueAmount > 0) {
                    StatChip("逾期", overdueAmount, null, Color(0xFFD32F2F))
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, amount: Double, count: Int?, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "¥${String.format("%.0f", amount)}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        if (count != null) {
            Text(
                "(${count})",
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.7f)
            )
        }
    }
}
```

**Step 2: Update PaymentCalendarContent to pass stats to MonthHeader**

In `PaymentCalendarContent`, remove the `StatsRow` item block (lines 178-188) and update the `MonthHeader` call (lines 189-196):

```kotlin
item {
    MonthHeader(
        year = uiState.currentYear,
        month = uiState.currentMonth,
        monthPaidTotal = uiState.monthPaidTotal,
        monthPaidCount = uiState.monthPaidCount,
        monthUnpaidTotal = uiState.monthUnpaidTotal,
        monthUnpaidCount = uiState.monthUnpaidCount,
        overdueAmount = uiState.overdueAmount,
        onPrevious = viewModel::previousMonth,
        onNext = viewModel::nextMonth
    )
}
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt
git commit -m "feat: merge stats into month header, remove StatsRow"
```

---

### Task 4: Rewrite CalendarGrid as fixed 6-row large-cell grid

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt`

**Step 1: Replace CalendarGrid composable**

Delete the existing `CalendarGrid` (lines 314-382) and replace with a fixed 6-row version. Key changes:
- Always render 6 rows x 7 columns = 42 cells
- Non-current-month cells show as empty with faded background
- Each cell is taller to accommodate amount previews
- Today gets a circular highlight on the day number
- Selected day gets a themed border

```kotlin
@Composable
private fun CalendarGrid(
    year: Int,
    month: Int,
    selectedDay: Int?,
    payments: List<PaymentWithItemInfo>,
    onDayClick: (Int) -> Unit
) {
    val weekDays = listOf("日", "一", "二", "三", "四", "五", "六")
    val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sunday
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val now = System.currentTimeMillis()
    val sevenDaysLater = now + 7L * 24 * 60 * 60 * 1000

    val todayCal = Calendar.getInstance()
    val isCurrentMonth = todayCal.get(Calendar.YEAR) == year && todayCal.get(Calendar.MONTH) == month
    val today = if (isCurrentMonth) todayCal.get(Calendar.DAY_OF_MONTH) else -1

    val dayStatusMap = buildDayStatusMap(payments, year, month, now, sevenDaysLater)
    val dayAmountMap = buildDayAmountMap(payments, year, month)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Weekday headers
            Row(modifier = Modifier.fillMaxWidth()) {
                weekDays.forEach { day ->
                    Text(
                        day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            // Fixed 6 rows
            repeat(6) { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    repeat(7) { col ->
                        val cellIndex = row * 7 + col
                        val dayOfMonth = cellIndex - firstDayOfWeek + 1
                        val isInMonth = dayOfMonth in 1..daysInMonth

                        if (isInMonth) {
                            DayCell(
                                day = dayOfMonth,
                                isToday = dayOfMonth == today,
                                status = dayStatusMap[dayOfMonth],
                                amountInfo = dayAmountMap[dayOfMonth],
                                isSelected = dayOfMonth == selectedDay,
                                modifier = Modifier.weight(1f),
                                onClick = { onDayClick(dayOfMonth) }
                            )
                        } else {
                            // Empty cell for out-of-month days
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp)
                                    .padding(1.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        MaterialTheme.shapes.extraSmall
                                    )
                            )
                        }
                    }
                }
                if (row < 5) Spacer(Modifier.height(2.dp))
            }
        }
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt
git commit -m "feat: rewrite CalendarGrid as fixed 6-row large-cell layout"
```

---

### Task 5: Rewrite DayCell as large cell with amount previews

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt`

**Step 1: Replace DayCell composable**

Delete the existing `DayCell` (lines 391-447) and replace with a large-cell version. Key changes:
- Fixed height 64.dp for consistent grid
- Day number top-left with today circle highlight
- Amount list below (max 2 lines, green=paid, themed/red=unpaid)
- "+N" badge bottom-right when more than 2 entries
- Selected state: themed border (2.dp) instead of background fill
- Add necessary imports: `androidx.compose.foundation.border`, `androidx.compose.foundation.layout.Box`

```kotlin
@Composable
private fun DayCell(
    day: Int,
    isToday: Boolean,
    status: DayStatus?,
    amountInfo: DayAmountInfo?,
    isSelected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val borderColor by animateColorAsState(
        if (isSelected) primaryColor else Color.Transparent,
        animationSpec = tween(200), label = "dayBorder"
    )

    Box(
        modifier = modifier
            .height(64.dp)
            .padding(1.dp)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = borderColor,
                shape = MaterialTheme.shapes.extraSmall
            )
            .background(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.shapes.extraSmall
            )
            .clickable(onClick = onClick)
            .padding(3.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Day number with today highlight
            if (isToday) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(primaryColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        day.toString(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            } else {
                Text(
                    day.toString(),
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Amount previews
            if (amountInfo != null) {
                val totalEntries = amountInfo.paidCount + amountInfo.unpaidCount
                var linesShown = 0

                if (amountInfo.paidTotal > 0 && linesShown < 2) {
                    Text(
                        "¥${String.format("%.0f", amountInfo.paidTotal)}",
                        fontSize = 9.sp,
                        color = Color(0xFF4CAF50),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 10.sp
                    )
                    linesShown++
                }
                if (amountInfo.unpaidTotal > 0 && linesShown < 2) {
                    Text(
                        "¥${String.format("%.0f", amountInfo.unpaidTotal)}",
                        fontSize = 9.sp,
                        color = when (status) {
                            DayStatus.OVERDUE -> Color(0xFFD32F2F)
                            else -> primaryColor
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 10.sp
                    )
                    linesShown++
                }

                // +N badge if more than what's shown
                if (totalEntries > 2) {
                    Text(
                        "+${totalEntries - 2}",
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}
```

Note: `Modifier.align(Alignment.End)` inside `Column` uses `ColumnScope`. The "+N" text aligns to the end of the column.

**Step 2: Add missing imports at top of file**

Ensure these imports exist:
```kotlin
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt
git commit -m "feat: rewrite DayCell as large cell with amount previews and today highlight"
```

---

### Task 6: Clean up unused code and remove old imports

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt`

**Step 1: Remove dead code**

- Delete `StatsRow` composable (was replaced by stats in MonthHeader)
- Delete `MiniStatCard` composable (was only used by StatsRow)
- Remove any unused imports that were only needed by the deleted composables

**Step 2: Verify no compile errors**

```bash
./gradlew.bat compileDebugKotlin 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt
git commit -m "refactor: remove unused StatsRow and MiniStatCard"
```

---

### Task 7: Version bump and release build

**Files:**
- Modify: `app/build.gradle.kts` — bump versionCode +1, versionName minor bump

**Step 1: Bump version**

Per CLAUDE.md conventions, this is a UI redesign (new feature level), so bump minor version. Check current version first, then increment versionCode by 1 and update versionName.

**Step 2: Build release APK**

```bash
./gradlew.bat assembleRelease 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "chore: bump version for payment monthly calendar redesign"
```
