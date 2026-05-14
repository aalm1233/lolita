# Payment Calendar Liquid Glass — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply iOS 26 Liquid Glass style (squircle corners + frosted glass + specular highlights) to all cards in the payment calendar tab.

**Architecture:** Two new independent files — `SquircleShape` (custom Shape with continuous curvature) and `GlassSurface` (standalone glass container composable). Then refactor 4 card types in `PaymentCalendarScreen.kt` to use GlassSurface instead of raw Material3 `Card`.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Haze (blur), existing LolitaSkin

---

### Task 1: Create SquircleShape

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/shape/SquircleShape.kt`

- [ ] **Step 1: Write SquircleShape**

```kotlin
package com.lolita.app.ui.theme.shape

import android.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * Continuous-curvature rounded rectangle (squircle).
 *
 * Uses cubic Bezier curves with control-point coefficient 0.63
 * (vs 0.552 for standard circular arcs). The higher coefficient
 * causes the curve to begin earlier from the straight edge,
 * producing a fan-like flare at each corner — the signature
 * iOS 26 Liquid Glass corner treatment.
 */
class SquircleShape(private val radius: Dp) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val r = with(density) { radius.toPx() }.coerceAtMost(size.minDimension() / 2f)
        if (r <= 0f) return Outline.Rectangle(size.toRect())

        val c = r * 0.63f // control point distance — 0.63 = squircle, 0.552 = standard

        val path = Path().apply {
            // Top-left corner
            moveTo(0f, r)
            cubicTo(0f, r - c, r - c, 0f, r, 0f)

            // Top edge → top-right corner
            lineTo(size.width - r, 0f)
            cubicTo(
                size.width - r + c, 0f,
                size.width, r - c,
                size.width, r
            )

            // Right edge → bottom-right corner
            lineTo(size.width, size.height - r)
            cubicTo(
                size.width, size.height - r + c,
                size.width - r + c, size.height,
                size.width - r, size.height
            )

            // Bottom edge → bottom-left corner
            lineTo(r, size.height)
            cubicTo(
                r - c, size.height,
                0f, size.height - r + c,
                0f, size.height - r
            )

            close()
        }
        return Outline.Generic(path)
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/shape/SquircleShape.kt
git commit -m "feat: add SquircleShape with continuous-curvature Bezier corners"
```

---

### Task 2: Create GlassSurface composable

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/screen/common/GlassSurface.kt`

- [ ] **Step 1: Write GlassSurface**

```kotlin
package com.lolita.app.ui.screen.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.shadow
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lolita.app.ui.theme.shape.SquircleShape
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

@Composable
fun GlassSurface(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    shape: Shape = SquircleShape(16.dp),
    blurRadius: Dp = 12.dp,
    glassAlpha: Float = 0.65f,
    highlightAlpha: Float = 0.15f,
    elevation: Dp = 2.dp,
    hazeState: HazeState? = null,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    content: @Composable () -> Unit
) {
    val isDark = isSystemInDarkTheme()

    // Base glass color — semi-transparent white
    val glassColor = Color.White.copy(alpha = glassAlpha)

    // Specular highlight: bright top edge fading to transparent bottom
    val highlightBrush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = highlightAlpha),
            Color.White.copy(alpha = highlightAlpha * 0.25f),
            Color.White.copy(alpha = 0f)
        )
    )

    // Shadow + blur + background modifier chain
    val glassModifier = if (hazeState != null) {
        modifier
            .shadow(elevation, shape, clip = false)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = glassColor,
                    tint = HazeTint(glassColor),
                    blurRadius = blurRadius
                )
            )
    } else {
        modifier
            .shadow(elevation, shape, clip = false)
            .background(glassColor, shape)
    }

    // Highlight border over the glass
    val highlightedModifier = glassModifier
        .border(1.dp, highlightBrush, shape)

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = highlightedModifier,
            shape = shape,
            color = Color.Transparent
        ) {
            Box(modifier = Modifier.padding(contentPadding)) {
                content()
            }
        }
    } else {
        Surface(
            modifier = highlightedModifier,
            shape = shape,
            color = Color.Transparent
        ) {
            Box(modifier = Modifier.padding(contentPadding)) {
                content()
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/common/GlassSurface.kt
git commit -m "feat: add GlassSurface composable with squircle corners and glass effect"
```

---

### Task 3: Adapt YearHeader and MonthCardGrid to GlassSurface

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt`

- [ ] **Step 1: Add new imports at the top of PaymentCalendarScreen.kt**

Replace the existing import block with these additions (keep all existing imports, add the new ones):

```kotlin
import com.lolita.app.ui.screen.common.GlassSurface
import com.lolita.app.ui.theme.shape.SquircleShape
```

(Remove `import androidx.compose.foundation.shape.RoundedCornerShape` if it's only used by the cards being refactored — but keep it if other code in the file still uses it. In this file, `RoundedCornerShape` is used in the YearHeader overdue badge `Surface(shape = RoundedCornerShape(4.dp))` and in `BottomSheetOption` — keep it.)

- [ ] **Step 2: Rewrite YearHeader composable (lines 491-577)**

Replace the entire `YearHeader` function:

```kotlin
@Composable
private fun YearHeader(
    year: Int,
    yearPaidTotal: Double,
    yearUnpaidTotal: Double,
    yearOverdueAmount: Double,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    frosted: Boolean = false
) {
    val isDark = isSystemInDarkTheme()
    val skin = LolitaSkin.current
    val primaryColor = if (isDark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = SquircleShape(20.dp),
        hazeState = if (frosted) hazeState else null,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Single row: <  ¥xx已  2026年  ¥xx待  >
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious, modifier = Modifier.size(24.dp)) {
                    SkinIcon(IconKey.KeyboardArrowLeft, modifier = Modifier.size(16.dp))
                }

                Text(
                    "¥${formatAmount(yearPaidTotal)}已",
                    style = MaterialTheme.typography.labelMedium,
                    color = paidColor(),
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    "  ${year}年  ",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "¥${formatAmount(yearUnpaidTotal)}待",
                    style = MaterialTheme.typography.labelMedium,
                    color = primaryColor,
                    fontWeight = FontWeight.SemiBold
                )

                IconButton(onClick = onNext, modifier = Modifier.size(24.dp)) {
                    SkinIcon(IconKey.KeyboardArrowRight, modifier = Modifier.size(16.dp))
                }
            }

            if (yearOverdueAmount > 0) {
                Surface(
                    color = overdueColor().copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚠", fontSize = 10.sp)
                        Spacer(Modifier.width(2.dp))
                        Text(
                            "逾期 ¥${formatAmount(yearOverdueAmount)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = overdueColor(),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
```

Key changes from original:
- `Card(...)` → `GlassSurface(shape = SquircleShape(20.dp), ...)`
- `containerColor` / `elevation` / `colors` logic removed (GlassSurface handles it)
- Content padding changed from `4.dp` to `horizontal=12.dp, vertical=8.dp`
- Inner `Column` padding removed (moved to GlassSurface contentPadding)

- [ ] **Step 3: Update MonthCardGrid spacing (lines 583-612)**

Replace the grid and row `spacedBy` values:

```kotlin
@Composable
private fun MonthCardGrid(
    monthStatsMap: Map<Int, MonthStats>,
    selectedMonth: Int?,
    currentYear: Int,
    onMonthClick: (Int) -> Unit,
    cardModifier: Modifier = Modifier,
    frosted: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(4) { col ->
                    val month = row * 4 + col
                    MonthCard(
                        month = month,
                        stats = monthStatsMap[month],
                        isSelected = month == selectedMonth,
                        frosted = frosted,
                        modifier = Modifier.weight(1f).then(cardModifier),
                        onClick = { onMonthClick(month) }
                    )
                }
            }
        }
    }
}
```

Key change: `spacedBy(3.dp)` → `spacedBy(4.dp)` in both `Column` and `Row`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt
git commit -m "feat: adapt YearHeader and MonthCardGrid to GlassSurface with squircle corners"
```

---

### Task 4: Adapt MonthCard, PaymentInfoCard, and empty state to GlassSurface

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt`

- [ ] **Step 1: Rewrite MonthCard composable (lines 614-686)**

Replace the entire `MonthCard` function:

```kotlin
@Composable
private fun MonthCard(
    month: Int,
    stats: MonthStats?,
    isSelected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    frosted: Boolean = false
) {
    val skin = LolitaSkin.current
    val isDark = isSystemInDarkTheme()
    val primaryColor = MaterialTheme.colorScheme.primary
    val hasPayments = stats != null
    val hasOverdue = (stats?.overdueAmount ?: 0.0) > 0

    val bgColor by animateColorAsState(
        if (isSelected) primaryColor.copy(alpha = 0.15f)
        else Color.Transparent,
        animationSpec = tween(200), label = "monthBg"
    )

    // When selected, overlay the tint on top of the glass background
    val selectedOverlay = if (isSelected) {
        Modifier.background(bgColor, SquircleShape(12.dp))
    } else Modifier

    GlassSurface(
        onClick = onClick,
        modifier = modifier.height(48.dp).then(selectedOverlay),
        shape = SquircleShape(12.dp),
        hazeState = if (frosted) hazeState else null,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
        elevation = 1.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                "${month + 1}月",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (hasPayments) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            )
            if (stats != null) {
                if (stats.paidTotal > 0) {
                    Text(
                        "已 ¥${formatCompactAmount(stats.paidTotal)}",
                        fontSize = 8.sp,
                        color = paidColor(),
                        maxLines = 1
                    )
                }
                if (stats.unpaidTotal > 0) {
                    val unpaidColor = if (hasOverdue) overdueColor() else primaryColor
                    Text(
                        "待 ¥${formatCompactAmount(stats.unpaidTotal)}",
                        fontSize = 8.sp,
                        color = unpaidColor,
                        maxLines = 1
                    )
                }
                if (hasOverdue && stats.overdueAmount > 0) {
                    Text(
                        "逾期",
                        fontSize = 7.sp,
                        color = overdueColor(),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
```

Key changes:
- `Card(...)` → `GlassSurface(onClick = onClick, shape = SquircleShape(12.dp), ...)`
- Height: `42.dp` → `48.dp`
- Font sizes: `11.sp` → `10.sp`, `9.sp` → `8.sp`, `8.sp` → `7.sp`
- Selected state: overlay `Modifier.background(bgColor)` on top of GlassSurface instead of changing container color
- `containerColor` / `colors` / `elevation` logic removed
- Added `Modifier.fillMaxSize()` to inner Column to center content vertically

- [ ] **Step 2: Rewrite PaymentInfoCard composable (lines 719-818)**

Replace the `Card(...)` wrapper in PaymentInfoCard with GlassSurface. The function signature stays the same; only the outer container changes.

Find this block (approximately lines 756-765):
```kotlin
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (frosted) Color.Transparent
            else if (isDark) skin.cardContainerColorDark
            else skin.cardContainerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
```

Replace with:
```kotlin
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = SquircleShape(16.dp),
        hazeState = if (frosted) hazeState else null,
        contentPadding = PaddingValues(12.dp)
    ) {
        Column {
```

And replace the closing `}` for the Card (the one right after the Column closes, before the function's closing brace — approximately line 817):
```kotlin
        }
    }
}
```
with:
```kotlin
        }
    }
```

(Remove one level of `}` since GlassSurface doesn't use a separate `Card` wrapper — the content Column is directly inside GlassSurface's content lambda.)

- [ ] **Step 3: Rewrite empty state card (lines 382-399)**

Replace:
```kotlin
                    item {
                        Card(
                            modifier = (if (hasBackground) hazeModifier else Modifier).fillMaxWidth(),
                            shape = RoundedCornerShape(0.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (hasBackground) Color.Transparent
                                else if (isSystemInDarkTheme()) LolitaSkin.current.cardContainerColorDark
                                else LolitaSkin.current.cardContainerColor
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Text(
                                "当月无付款记录",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
```

With:
```kotlin
                    item {
                        GlassSurface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = SquircleShape(16.dp),
                            hazeState = if (hasBackground) hazeState else null,
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Text(
                                "当月无付款记录",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
```

- [ ] **Step 4: Verify — check that no `RoundedCornerShape(0.dp)` remains in card contexts**

All four card types (YearHeader, MonthCard, PaymentInfoCard, empty state) should now use `SquircleShape` via `GlassSurface`. The `RoundedCornerShape(0.dp)` references in these functions should be gone. Other uses of `RoundedCornerShape` (overdue badge, bottom sheet) are fine.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt
git commit -m "feat: adapt MonthCard, PaymentInfoCard, and empty state to GlassSurface"
```

---

### Task 5: Build and verify

- [ ] **Step 1: Run debug build**

```bash
./gradlew.bat assembleDebug
```

Expected: BUILD SUCCESSFUL, no compile errors.

- [ ] **Step 2: Verify no compile errors in the two new files**

Check that `SquircleShape.kt` and `GlassSurface.kt` compile without issues.

- [ ] **Step 3: Verify PaymentCalendarScreen.kt compiles**

All imports should resolve: `GlassSurface`, `SquircleShape`, `PaddingValues` (already imported via `foundation.layout.*`).

- [ ] **Step 4: Commit any final fixes if needed**

```bash
git add -A
git commit -m "chore: fix any compilation issues from liquid glass migration"
```
