# 皮肤图标结构性差异化重设计 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Redesign 5 existing icons (ArrowBack, Add, FilterList, Sort) + add 3 new view-toggle icons (ViewAgenda, GridView, Apps) with structurally distinct Canvas implementations across all 4 skins, and fix screen files still using Material icons directly.

**Architecture:** Extend the existing SkinIconProvider interface with 3 new methods in ActionIcons. Rewrite Canvas drawing code in all 4 skin providers for 7 icons each. Fix 3 screen files to use SkinIcon instead of Material Icon.

**Tech Stack:** Kotlin, Jetpack Compose Canvas API, Material3

**No test suite exists in this project.** Verification is via `./gradlew.bat assembleDebug`.

---

### Task 1: Add 3 new IconKeys and wire interface layer

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/IconKey.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/SkinIconProvider.kt` (ActionIcons interface)
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/SkinIcon.kt`

**Step 1: Add enum values to IconKey.kt**

In `IconKey.kt`, add `ViewAgenda, GridView, Apps` to the Action line:

```kotlin
// Action
Add, Delete, Edit, Search, Sort, Save, Close, Share, FilterList, MoreVert, ContentCopy, Refresh,
ViewAgenda, GridView, Apps,
```

**Step 2: Add interface methods to SkinIconProvider.kt**

In `ActionIcons` interface (after `Refresh`), add:

```kotlin
@Composable fun ViewAgenda(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
@Composable fun GridView(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
@Composable fun Apps(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
```

**Step 3: Add when cases to SkinIcon.kt**

In `SkinIcon.kt`, after the `IconKey.Refresh` line, add:

```kotlin
IconKey.ViewAgenda -> icons.action.ViewAgenda(modifier, tint)
IconKey.GridView -> icons.action.GridView(modifier, tint)
IconKey.Apps -> icons.action.Apps(modifier, tint)
```

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/icon/IconKey.kt \
       app/src/main/java/com/lolita/app/ui/theme/skin/icon/SkinIconProvider.kt \
       app/src/main/java/com/lolita/app/ui/theme/skin/icon/SkinIcon.kt
git commit -m "feat(skin): add ViewAgenda, GridView, Apps to icon system interface"
```

---

### Task 2: Add base Material fallback implementations

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/BaseSkinIconProvider.kt`

**Step 1: Add imports and fallback methods**

Add imports at top of file:
```kotlin
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewAgenda
```

Add to `BaseActionIcons` class (after `Refresh`):
```kotlin
@Composable override fun ViewAgenda(modifier: Modifier, tint: Color) =
    Icon(Icons.Filled.ViewAgenda, null, modifier, tint)
@Composable override fun GridView(modifier: Modifier, tint: Color) =
    Icon(Icons.Filled.GridView, null, modifier, tint)
@Composable override fun Apps(modifier: Modifier, tint: Color) =
    Icon(Icons.Filled.Apps, null, modifier, tint)
```

**Step 2: Build to verify compilation**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL (will fail until skin providers implement the new methods — proceed to Task 3)

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/icon/BaseSkinIconProvider.kt
git commit -m "feat(skin): add Material fallback for ViewAgenda, GridView, Apps"
```

---

### Task 3: Redesign SweetIconProvider — 7 icons

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/SweetIconProvider.kt`

**Step 1: Rewrite ArrowBack — 丝带箭头**

Replace the existing `ArrowBack` function. The arrow body is a flowing ribbon that curls into a half-bow at the tail, with a rounded tip:

```kotlin
@Composable override fun ArrowBack(modifier: Modifier, tint: Color) {
    Canvas(modifier.size(24.dp)) {
        val s = size.minDimension
        // Ribbon body — flowing curve from right to left
        val ribbon = Path().apply {
            moveTo(s * 0.75f, s * 0.22f)
            // Upper ribbon edge curving to arrow tip
            cubicTo(s * 0.55f, s * 0.28f, s * 0.40f, s * 0.40f, s * 0.22f, s * 0.50f)
            // Lower ribbon edge curving back
            cubicTo(s * 0.40f, s * 0.60f, s * 0.55f, s * 0.72f, s * 0.75f, s * 0.78f)
        }
        drawPath(ribbon, tint, style = sweetStroke(s))
        // Rounded arrow tip
        drawCircle(tint, radius = s * 0.04f, center = Offset(s * 0.22f, s * 0.50f))
        // Half-bow curl at tail end (top)
        val bowTop = Path().apply {
            moveTo(s * 0.75f, s * 0.22f)
            cubicTo(s * 0.82f, s * 0.15f, s * 0.88f, s * 0.18f, s * 0.85f, s * 0.26f)
        }
        drawPath(bowTop, tint.copy(alpha = 0.6f), style = Stroke(s * 0.05f, cap = StrokeCap.Round))
        // Half-bow curl at tail end (bottom)
        val bowBot = Path().apply {
            moveTo(s * 0.75f, s * 0.78f)
            cubicTo(s * 0.82f, s * 0.85f, s * 0.88f, s * 0.82f, s * 0.85f, s * 0.74f)
        }
        drawPath(bowBot, tint.copy(alpha = 0.6f), style = Stroke(s * 0.05f, cap = StrokeCap.Round))
    }
}
```

**Step 2: Rewrite Add — 花朵加号**

Replace the existing `Add` function. A four-petal flower where each petal extends from the cross arms, with a center pistil dot:

```kotlin
@Composable override fun Add(modifier: Modifier, tint: Color) {
    Canvas(modifier.size(24.dp)) {
        val s = size.minDimension
        val cx = s * 0.5f; val cy = s * 0.5f
        // Four petals at cross endpoints
        val petalR = s * 0.12f
        listOf(
            Offset(cx, cy - s * 0.28f),  // top
            Offset(cx + s * 0.28f, cy),  // right
            Offset(cx, cy + s * 0.28f),  // bottom
            Offset(cx - s * 0.28f, cy)   // left
        ).forEach { center ->
            val petal = Path().apply {
                moveTo(cx, cy)
                cubicTo(
                    center.x - petalR * 0.8f, center.y - petalR * 0.8f,
                    center.x - petalR, center.y + petalR * 0.3f,
                    center.x, center.y
                )
                cubicTo(
                    center.x + petalR, center.y + petalR * 0.3f,
                    center.x + petalR * 0.8f, center.y - petalR * 0.8f,
                    cx, cy
                )
            }
            drawPath(petal, tint, style = Fill)
        }
        // Center pistil
        drawCircle(tint, radius = s * 0.06f, center = Offset(cx, cy))
    }
}
```

**Step 3: Rewrite FilterList — 蛋糕层叠**

Replace the existing `FilterList` function. Three cake tiers widest on top narrowing down, wavy edges, cherry on top:

```kotlin
@Composable override fun FilterList(modifier: Modifier, tint: Color) {
    Canvas(modifier.size(24.dp)) {
        val s = size.minDimension
        val cx = s * 0.5f
        // Three tiers — wavy lines (using cubic curves for wave effect)
        data class Tier(val y: Float, val halfW: Float, val thick: Float)
        val tiers = listOf(
            Tier(s * 0.30f, s * 0.38f, s * 0.08f),
            Tier(s * 0.52f, s * 0.26f, s * 0.07f),
            Tier(s * 0.74f, s * 0.14f, s * 0.06f)
        )
        tiers.forEach { tier ->
            val wave = Path().apply {
                moveTo(cx - tier.halfW, tier.y)
                val segments = 4
                val segW = tier.halfW * 2 / segments
                for (i in 0 until segments) {
                    val x0 = cx - tier.halfW + segW * i
                    val x1 = x0 + segW
                    val cpY = if (i % 2 == 0) tier.y - s * 0.03f else tier.y + s * 0.03f
                    quadraticBezierTo(x0 + segW / 2, cpY, x1, tier.y)
                }
            }
            drawPath(wave, tint, style = Stroke(tier.thick, cap = StrokeCap.Round))
        }
        // Cherry on top
        drawCircle(tint, radius = s * 0.04f, center = Offset(cx, s * 0.18f))
        // Cherry stem
        val stem = Path().apply {
            moveTo(cx, s * 0.18f)
            cubicTo(cx + s * 0.03f, s * 0.14f, cx + s * 0.05f, s * 0.12f, cx + s * 0.04f, s * 0.10f)
        }
        drawPath(stem, tint, style = Stroke(s * 0.025f, cap = StrokeCap.Round))
    }
}
```

**Step 4: Rewrite Sort — 糖果阶梯**

Replace the existing `Sort` function. Three decreasing lines with lollipop balls at right ends:

```kotlin
@Composable override fun Sort(modifier: Modifier, tint: Color) {
    Canvas(modifier.size(24.dp)) {
        val s = size.minDimension
        data class Bar(val y: Float, val endX: Float)
        val bars = listOf(
            Bar(s * 0.25f, s * 0.80f),
            Bar(s * 0.50f, s * 0.62f),
            Bar(s * 0.75f, s * 0.44f)
        )
        val startX = s * 0.18f
        bars.forEach { bar ->
            // Stick
            drawLine(tint, Offset(startX, bar.y), Offset(bar.endX - s * 0.04f, bar.y),
                strokeWidth = s * 0.06f, cap = StrokeCap.Round)
            // Lollipop ball
            drawCircle(tint, radius = s * 0.055f, center = Offset(bar.endX, bar.y))
        }
    }
}
```

**Step 5: Add ViewAgenda — 饼干堆叠**

Two rounded rectangles stacked vertically with wavy edges and small heart decorations:

```kotlin
@Composable override fun ViewAgenda(modifier: Modifier, tint: Color) {
    Canvas(modifier.size(24.dp)) {
        val s = size.minDimension
        val r = s * 0.06f
        // Top cookie
        drawRoundRect(tint, topLeft = Offset(s * 0.15f, s * 0.12f),
            size = Size(s * 0.70f, s * 0.30f),
            cornerRadius = CornerRadius(r, r), style = Stroke(s * 0.06f, cap = StrokeCap.Round))
        // Bottom cookie
        drawRoundRect(tint, topLeft = Offset(s * 0.15f, s * 0.58f),
            size = Size(s * 0.70f, s * 0.30f),
            cornerRadius = CornerRadius(r, r), style = Stroke(s * 0.06f, cap = StrokeCap.Round))
        // Heart on top-right of each
        drawSweetHeart(Offset(s * 0.78f, s * 0.18f), s * 0.04f, tint.copy(alpha = 0.5f))
        drawSweetHeart(Offset(s * 0.78f, s * 0.64f), s * 0.04f, tint.copy(alpha = 0.5f))
    }
}
```

**Step 6: Add GridView — 软糖方块**

Four extremely rounded squares in 2×2 layout:

```kotlin
@Composable override fun GridView(modifier: Modifier, tint: Color) {
    Canvas(modifier.size(24.dp)) {
        val s = size.minDimension
        val gap = s * 0.08f
        val cellSize = (s - gap * 3) / 2
        val r = cellSize * 0.35f
        for (row in 0..1) {
            for (col in 0..1) {
                val x = gap + col * (cellSize + gap)
                val y = gap + row * (cellSize + gap)
                drawRoundRect(tint, topLeft = Offset(x, y),
                    size = Size(cellSize, cellSize),
                    cornerRadius = CornerRadius(r, r),
                    style = Stroke(s * 0.055f, cap = StrokeCap.Round))
            }
        }
    }
}
```

**Step 7: Add Apps — 糖珠排列**

Nine small circles in 3×3 with slight size variation for playfulness:

```kotlin
@Composable override fun Apps(modifier: Modifier, tint: Color) {
    Canvas(modifier.size(24.dp)) {
        val s = size.minDimension
        val gap = s / 4f
        val sizes = listOf(0.065f, 0.055f, 0.06f, 0.055f, 0.07f, 0.055f, 0.06f, 0.055f, 0.065f)
        var idx = 0
        for (row in 0..2) {
            for (col in 0..2) {
                drawCircle(tint,
                    radius = s * sizes[idx],
                    center = Offset(gap + col * gap, gap + row * gap))
                idx++
            }
        }
    }
}
```

**Step 8: Build verify**

Run: `./gradlew.bat assembleDebug`

**Step 9: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/icon/SweetIconProvider.kt
git commit -m "feat(skin): redesign Sweet icons — ArrowBack, Add, FilterList, Sort + new view toggles"
```

---

### Task 4: Redesign GothicIconProvider — 7 icons

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/GothicIconProvider.kt`

**Step 1: Rewrite ArrowBack — 匕首箭头**

Sharp dagger outline with barbs/thorns on the shaft:

```kotlin
@Composable override fun ArrowBack(modifier: Modifier, tint: Color) {
    Canvas(modifier.size(24.dp)) {
        val s = size.minDimension
        // Dagger blade pointing left
        val blade = Path().apply {
            moveTo(s * 0.18f, s * 0.50f)  // sharp tip
            lineTo(s * 0.42f, s * 0.35f)  // upper blade edge
            lineTo(s * 0.78f, s * 0.40f)  // upper shaft
            lineTo(s * 0.78f, s * 0.60f)  // lower shaft
            lineTo(s * 0.42f, s * 0.65f)  // lower blade edge
            close()
        }
        drawPath(blade, tint, style = Stroke(s * 0.05f, join = StrokeJoin.Miter))
        // Barbs on shaft
        drawLine(tint, Offset(s * 0.55f, s * 0.38f), Offset(s * 0.52f, s * 0.30f), strokeWidth = s * 0.03f)
        drawLine(tint, Offset(s * 0.65f, s * 0.39f), Offset(s * 0.62f, s * 0.31f), strokeWidth = s * 0.03f)
        drawLine(tint, Offset(s * 0.55f, s * 0.62f), Offset(s * 0.52f, s * 0.70f), strokeWidth = s * 0.03f)
        drawLine(tint, Offset(s * 0.65f, s * 0.61f), Offset(s * 0.62f, s * 0.69f), strokeWidth = s * 0.03f)
    }
}
```

**Step 2: Rewrite Add — 铁十字**

Iron Cross shape — four arms widening outward from center, flat-cut ends, tiny serrations:

```kotlin
@Composable override fun Add(modifier: Modifier, tint: Color) {
    Canvas(modifier.size(24.dp)) {
        val s = size.minDimension
        val cx = s * 0.5f; val cy = s * 0.5f
        // Iron cross — four trapezoidal arms
        val ironCross = Path().apply {
            // Top arm
            moveTo(cx - s * 0.06f, cy - s * 0.08f)
            lineTo(cx - s * 0.10f, cy - s * 0.38f)
            lineTo(cx + s * 0.10f, cy - s * 0.38f)
            lineTo(cx + s * 0.06f, cy - s * 0.08f)
            // Right arm
            moveTo(cx + s * 0.08f, cy - s * 0.06f)
            lineTo(cx + s * 0.38f, cy - s * 0.10f)
            lineTo(cx + s * 0.38f, cy + s * 0.10f)
            lineTo(cx + s * 0.08f, cy + s * 0.06f)
            // Bottom arm
            moveTo(cx + s * 0.06f, cy + s * 0.08f)
            lineTo(cx + s * 0.10f, cy + s * 0.38f)
            lineTo(cx - s * 0.10f, cy + s * 0.38f)
            lineTo(cx - s * 0.06f, cy + s * 0.08f)
            // Left arm
            moveTo(cx - s * 0.08f, cy + s * 0.06f)
            lineTo(cx - s * 0.38f, cy + s * 0.10f)
            lineTo(cx - s * 0.38f, cy - s * 0.10f)
            lineTo(cx - s * 0.08f, cy - s * 0.06f)
        }
        drawPath(ironCross, tint, style = Fill)
        // Center square
        drawRect(tint, Offset(cx - s * 0.08f, cy - s * 0.08f), Size(s * 0.16f, s * 0.16f))
    }
}
```

**Step 3: Rewrite FilterList — 倒三角锁链**

Three horizontal bars connected by vertical chain links forming an inverted triangle cage, spiked ends:

```kotlin
@Composable override fun FilterList(modifier: Modifier, tint: Color) {
    Canvas(modifier.size(24.dp)) {
        val s = size.minDimension
        val cx = s * 0.5f
        data class Bar(val y: Float, val halfW: Float)
        val bars = listOf(
            Bar(s * 0.22f, s * 0.38f),
            Bar(s * 0.50f, s * 0.24f),
            Bar(s * 0.78f, s * 0.10f)
        )
        // Horizontal bars with spike ends
        bars.forEach { bar ->
            drawLine(tint, Offset(cx - bar.halfW, bar.y), Offset(cx + bar.halfW, bar.y),
                strokeWidth = s * 0.05f)
            // Left spike
            drawLine(tint, Offset(cx - bar.halfW, bar.y),
                Offset(cx - bar.halfW - s * 0.04f, bar.y - s * 0.04f), strokeWidth = s * 0.03f)
            drawLine(tint, Offset(cx - bar.halfW, bar.y),
                Offset(cx - bar.halfW - s * 0.04f, bar.y + s * 0.04f), strokeWidth = s * 0.03f)
            // Right spike
            drawLine(tint, Offset(cx + bar.halfW, bar.y),
                Offset(cx + bar.halfW + s * 0.04f, bar.y - s * 0.04f), strokeWidth = s * 0.03f)
            drawLine(tint, Offset(cx + bar.halfW, bar.y),
                Offset(cx + bar.halfW + s * 0.04f, bar.y + s * 0.04f), strokeWidth = s * 0.03f)
        }
        // Vertical chain links connecting bars (left side)
        for (i in 0 until bars.size - 1) {
            val x1 = cx - bars[i].halfW; val x2 = cx - bars[i + 1].halfW
            val y1 = bars[i].y; val y2 = bars[i + 1].y
            drawLine(tint, Offset(x1, y1), Offset(x2, y2), strokeWidth = s * 0.03f)
        }
        // Vertical chain links (right side)
        for (i in 0 until bars.size - 1) {
            val x1 = cx + bars[i].halfW; val x2 = cx + bars[i + 1].halfW
            val y1 = bars[i].y; val y2 = bars[i + 1].y
            drawLine(tint, Offset(x1, y1), Offset(x2, y2), strokeWidth = s * 0.03f)
        }
    }
}
```

**Step 4: Rewrite Sort — 阶梯尖塔**

Three decreasing bars with spire tips rising from right ends, small bat-wing at left:

```kotlin
@Composable override fun Sort(modifier: Modifier, tint: Color) {
    Canvas(modifier.size(24.dp)) {
        val s = size.minDimension
        data class Bar(val y: Float, val endX: Float)
        val bars = listOf(
            Bar(s * 0.28f, s * 0.82f),
            Bar(s * 0.52f, s * 0.64f),
            Bar(s * 0.76f, s * 0.46f)
        )
        val startX = s * 0.16f
        bars.forEach { bar ->
            // Main bar
            drawLine(tint, Offset(startX, bar.y), Offset(bar.endX, bar.y), strokeWidth = s * 0.05f)
            // Spire tip rising from right end
            val spire = Path().apply {
                moveTo(bar.endX - s * 0.04f, bar.y)
                lineTo(bar.endX, bar.y - s * 0.10f)
                lineTo(bar.endX + s * 0.04f, bar.y)
            }
            drawPath(spire, tint, style = Fill)
        }
        // Small bat-wing at left of top bar
        val wing = Path().apply {
            moveTo(startX, bars[0].y)
            lineTo(startX - s * 0.06f, bars[0].y - s * 0.08f)
            cubicTo(startX - s * 0.02f, bars[0].y - s * 0.04f,
                startX - s * 0.02f, bars[0].y - s * 0.02f,
                startX, bars[0].y)
        }
        drawPath(wing, tint, style = Fill)
    }
}
```

**Step 5: Add ViewAgenda — 墓碑堆叠**

Two sharp-cornered rectangles stacked vertically with internal cross texture:

```kotlin
@Composable override fun ViewAgenda(modifier: Modifier, tint: Color) {
    Canvas(modifier.size(24.dp)) {
        val s = size.minDimension
        val stroke = Stroke(s * 0.05f, join = StrokeJoin.Miter)
        // Top tombstone
        drawRect(tint, Offset(s * 0.14f, s * 0.10f), Size(s * 0.72f, s * 0.32f), style = stroke)
        // Cross inside top
        drawLine(tint, Offset(s * 0.50f, s * 0.16f), Offset(s * 0.50f, s * 0.34f), strokeWidth = s * 0.025f)
        drawLine(tint, Offset(s * 0.38f, s * 0.24f), Offset(s * 0.62f, s * 0.24f), strokeWidth = s * 0.025f)
        // Bottom tombstone
        drawRect(tint, Offset(s * 0.14f, s * 0.58f), Size(s * 0.72f, s * 0.32f), style = stroke)
        // Cross inside bottom
        drawLine(tint, Offset(s * 0.50f, s * 0.64f), Offset(s * 0.50f, s * 0.82f), strokeWidth = s * 0.025f)
        drawLine(tint, Offset(s * 0.38f, s * 0.72f), Offset(s * 0.62f, s * 0.72f), strokeWidth = s * 0.025f)
    }
}
```

**Step 6: Add GridView — 菱形铁窗格**

Four diamonds in 2×2 connected by thin lines:

```kotlin
@Composable override fun GridView(modifier: Modifier, tint: Color) {
    Canvas(modifier.size(24.dp)) {
        val s = size.minDimension
        val positions = listOf(
            Offset(s * 0.30f, s * 0.30f),
            Offset(s * 0.70f, s * 0.30f),
            Offset(s * 0.30f, s * 0.70f),
            Offset(s * 0.70f, s * 0.70f)
        )
        val r = s * 0.12f
        positions.forEach { c ->
            val diamond = Path().apply {
                moveTo(c.x, c.y - r); lineTo(c.x + r, c.y)
                lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close()
            }
            drawPath(diamond, tint, style = Stroke(s * 0.045f, join = StrokeJoin.Miter))
        }
        // Connecting lines between diamonds
        drawLine(tint, Offset(s * 0.30f, s * 0.42f), Offset(s * 0.30f, s * 0.58f), strokeWidth = s * 0.025f)
        drawLine(tint, Offset(s * 0.70f, s * 0.42f), Offset(s * 0.70f, s * 0.58f), strokeWidth = s * 0.025f)
        drawLine(tint, Offset(s * 0.42f, s * 0.30f), Offset(s * 0.58f, s * 0.30f), strokeWidth = s * 0.025f)
        drawLine(tint, Offset(s * 0.42f, s * 0.70f), Offset(s * 0.58f, s * 0.70f), strokeWidth = s * 0.025f)
    }
}
```

**Step 7: Add Apps — 钻石阵列**

Nine small diamonds in 3×3 grid:

```kotlin
@Composable override fun Apps(modifier: Modifier, tint: Color) {
    Canvas(modifier.size(24.dp)) {
        val s = size.minDimension
        val gap = s / 4f
        val r = s * 0.06f
        for (row in 0..2) {
            for (col in 0..2) {
                val cx = gap + col * gap; val cy = gap + row * gap
                val diamond = Path().apply {
                    moveTo(cx, cy - r); lineTo(cx + r, cy)
                    lineTo(cx, cy + r); lineTo(cx - r, cy); close()
                }
                drawPath(diamond, tint, style = Fill)
            }
        }
    }
}
```

**Step 8: Build verify**

Run: `./gradlew.bat assembleDebug`

**Step 9: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/icon/GothicIconProvider.kt
git commit -m "feat(skin): redesign Gothic icons — ArrowBack, Add, FilterList, Sort + new view toggles"
```

---

### Task 5: Redesign ChineseIconProvider — 7 icons

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/ChineseIconProvider.kt`

**Step 1: Rewrite ArrowBack — 书法撇笔**

Calligraphy "pie" stroke — heavy start tapering to thin tip, with flying-white (feibai) effect at arrow point:

```kotlin
@Composable override fun ArrowBack(modifier: Modifier, tint: Color) {
    Canvas(modifier.size(24.dp)) {
        val s = size.minDimension
        // Main brush stroke — thick to thin curve (撇)
        val stroke = Path().apply {
            moveTo(s * 0.78f, s * 0.20f)
            cubicTo(s * 0.60f, s * 0.30f, s * 0.40f, s * 0.42f, s * 0.20f, s * 0.52f)
        }
        // Thick start tapering to thin end
        drawPath(stroke, tint, style = Stroke(s * 0.10f, cap = StrokeCap.Round))
        // Overlay thinner line to create taper illusion
        val taper = Path().apply {
            moveTo(s * 0.50f, s * 0.38f)
            cubicTo(s * 0.38f, s * 0.44f, s * 0.28f, s * 0.48f, s * 0.18f, s * 0.52f)
        }
        drawPath(taper, tint, style = Stroke(s * 0.04f, cap = StrokeCap.Round))
        // Flying-white (飞白) effect — dashed fragments near tip
        drawLine(tint.copy(alpha = 0.4f),
            Offset(s * 0.22f, s * 0.56f), Offset(s * 0.16f, s * 0.60f),
            strokeWidth = s * 0.025f, cap = StrokeCap.Round)
        drawLine(tint.copy(alpha = 0.3f),
            Offset(s * 0.26f, s * 0.58f), Offset(s * 0.20f, s * 0.63f),
            strokeWidth = s * 0.02f, cap = StrokeCap.Round)
        // Ink splash at start point
        drawInkSplash(Offset(s * 0.78f, s * 0.20f), s * 0.04f, tint)
    }
}
```

**Step 2: Rewrite Add — 篆书"十"**

Seal-script style "十" character — thick square strokes with chisel-cut feel:

```kotlin
@Composable override fun Add(modifier: Modifier, tint: Color) {
    Canvas(modifier.size(24.dp)) {
        val s = size.minDimension
        val cx = s * 0.5f; val cy = s * 0.5f
        val thick = s * 0.11f
        // Vertical stroke — slightly tapered with square ends
        val vStroke = Path().apply {
            moveTo(cx - thick * 0.5f, s * 0.14f)
            lineTo(cx + thick * 0.5f, s * 0.14f)
            lineTo(cx + thick * 0.45f, s * 0.86f)
            lineTo(cx - thick * 0.45f, s * 0.86f)
            close()
        }
        drawPath(vStroke, tint, style = Fill)
        // Horizontal stroke
        val hStroke = Path().apply {
            moveTo(s * 0.14f, cy - thick * 0.5f)
            lineTo(s * 0.86f, cy - thick * 0.5f)
            lineTo(s * 0.86f, cy + thick * 0.45f)
            lineTo(s * 0.14f, cy + thick * 0.45f)
            close()
        }
        drawPath(hStroke, tint, style = Fill)
        // Ink splash accent
        drawInkSplash(Offset(s * 0.82f, s * 0.18f), s * 0.035f, tint)
    }
}
```

**Step 3: Rewrite FilterList — 折扇**

Three arc lines arranged as a folding fan (wide top, converging to pivot at bottom):

```kotlin
@Composable override fun FilterList(modifier: Modifier, tint: Color) {
    Canvas(modifier.size(24.dp)) {
        val s = size.minDimension
        val pivotX = s * 0.50f; val pivotY = s * 0.88f
        // Three fan ribs — arcs from pivot
        data class Rib(val radius: Float, val thick: Float, val sweep: Float)
        val ribs = listOf(
            Rib(s * 0.62f, s * 0.07f, 100f),
            Rib(s * 0.44f, s * 0.06f, 80f),
            Rib(s * 0.26f, s * 0.05f, 60f)
        )
        ribs.forEach { rib ->
            val rect = Rect(
                pivotX - rib.radius, pivotY - rib.radius,
                pivotX + rib.radius, pivotY + rib.radius
            )
            drawArc(tint, startAngle = -90f - rib.sweep / 2, sweepAngle = rib.sweep,
                useCenter = false, topLeft = rect.topLeft, size = rect.size,
                style = Stroke(rib.thick, cap = StrokeCap.Round))
        }
        // Pivot point (fan axis)
        drawCircle(tint, radius = s * 0.04f, center = Offset(pivotX, pivotY))
    }
}
```

**Step 4: Rewrite Sort — 山水层叠**

Three decreasing undulating mountain silhouette lines, longest lightest, shortest darkest:

```kotlin
@Composable override fun Sort(modifier: Modifier, tint: Color) {
    Canvas(modifier.size(24.dp)) {
        val s = size.minDimension
        data class Mountain(val y: Float, val startX: Float, val endX: Float, val alpha: Float, val thick: Float)
        val mountains = listOf(
            Mountain(s * 0.28f, s * 0.10f, s * 0.90f, 0.4f, s * 0.06f),
            Mountain(s * 0.52f, s * 0.15f, s * 0.70f, 0.7f, s * 0.07f),
            Mountain(s * 0.76f, s * 0.20f, s * 0.50f, 1.0f, s * 0.08f)
        )
        mountains.forEach { m ->
            val w = m.endX - m.startX
            val peak = Path().apply {
                moveTo(m.startX, m.y)
                // Gentle mountain undulation
                cubicTo(m.startX + w * 0.25f, m.y - s * 0.06f,
                    m.startX + w * 0.40f, m.y - s * 0.08f,
                    m.startX + w * 0.50f, m.y - s * 0.03f)
                cubicTo(m.startX + w * 0.65f, m.y + s * 0.02f,
                    m.startX + w * 0.80f, m.y - s * 0.05f,
                    m.endX, m.y)
            }
            drawPath(peak, tint.copy(alpha = m.alpha),
                style = Stroke(m.thick, cap = StrokeCap.Round))
        }
    }
}
```

**Step 5: Add ViewAgenda — 竖卷轴**

Two scroll rolls stacked vertically, with axis knobs at ends:

```kotlin
@Composable override fun ViewAgenda(modifier: Modifier, tint: Color) {
    Canvas(modifier.size(24.dp)) {
        val s = size.minDimension
        val stroke = brushStroke(s)
        // Top scroll body
        drawRoundRect(tint, Offset(s * 0.20f, s * 0.12f), Size(s * 0.60f, s * 0.30f),
            cornerRadius = CornerRadius(s * 0.03f), style = stroke)
        // Top scroll axis knobs
        drawCircle(tint, s * 0.045f, Offset(s * 0.18f, s * 0.27f))
        drawCircle(tint, s * 0.045f, Offset(s * 0.82f, s * 0.27f))
        // Bottom scroll body
        drawRoundRect(tint, Offset(s * 0.20f, s * 0.58f), Size(s * 0.60f, s * 0.30f),
            cornerRadius = CornerRadius(s * 0.03f), style = stroke)
        // Bottom scroll axis knobs
        drawCircle(tint, s * 0.045f, Offset(s * 0.18f, s * 0.73f))
        drawCircle(tint, s * 0.045f, Offset(s * 0.82f, s * 0.73f))
    }
}
```

**Step 6: Add GridView — 田字窗棂**

Four squares in "田" character layout, simulating wooden lattice window with thicker intersections:

```kotlin
@Composable override fun GridView(modifier: Modifier, tint: Color) {
    Canvas(modifier.size(24.dp)) {
        val s = size.minDimension
        val pad = s * 0.12f
        val inner = s - pad * 2
        val mid = pad + inner / 2
        // Outer frame
        drawRect(tint, Offset(pad, pad), Size(inner, inner),
            style = Stroke(s * 0.06f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // Vertical divider (thicker at cross)
        drawLine(tint, Offset(mid, pad), Offset(mid, pad + inner),
            strokeWidth = s * 0.07f, cap = StrokeCap.Round)
        // Horizontal divider
        drawLine(tint, Offset(pad, mid), Offset(pad + inner, mid),
            strokeWidth = s * 0.07f, cap = StrokeCap.Round)
    }
}
```

**Step 7: Add Apps — 围棋星位**

Nine slightly square dots in 3×3 grid, like Go board star points:

```kotlin
@Composable override fun Apps(modifier: Modifier, tint: Color) {
    Canvas(modifier.size(24.dp)) {
        val s = size.minDimension
        val gap = s / 4f
        val r = s * 0.055f
        for (row in 0..2) {
            for (col in 0..2) {
                val cx = gap + col * gap; val cy = gap + row * gap
                // Slightly square dot (rounded rect instead of circle)
                drawRoundRect(tint,
                    Offset(cx - r, cy - r), Size(r * 2, r * 2),
                    cornerRadius = CornerRadius(r * 0.3f),
                    style = Fill)
            }
        }
    }
}
```

**Step 8: Build verify**

Run: `./gradlew.bat assembleDebug`

**Step 9: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/icon/ChineseIconProvider.kt
git commit -m "feat(skin): redesign Chinese icons — ArrowBack, Add, FilterList, Sort + new view toggles"
```

---

### Task 6: Redesign ClassicIconProvider — 7 icons

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/ClassicIconProvider.kt`

**Step 1: Rewrite ArrowBack — 卷纹箭头**

Arrow with volute scrollwork at tail end and small spiral at tip:

```kotlin
@Composable override fun ArrowBack(modifier: Modifier, tint: Color) {
    Canvas(modifier.size(24.dp)) {
        val s = size.minDimension
        // Main arrow shaft
        val shaft = Path().apply {
            moveTo(s * 0.75f, s * 0.50f)
            lineTo(s * 0.30f, s * 0.50f)
        }
        drawPath(shaft, tint, style = classicStroke(s))
        // Arrow chevron
        val chevron = Path().apply {
            moveTo(s * 0.48f, s * 0.28f)
            lineTo(s * 0.26f, s * 0.50f)
            lineTo(s * 0.48f, s * 0.72f)
        }
        drawPath(chevron, tint, style = classicStroke(s))
        // Volute curl at top of tail
        val voluteTop = Path().apply {
            moveTo(s * 0.75f, s * 0.50f)
            cubicTo(s * 0.80f, s * 0.42f, s * 0.86f, s * 0.36f, s * 0.82f, s * 0.30f)
            cubicTo(s * 0.78f, s * 0.26f, s * 0.74f, s * 0.30f, s * 0.76f, s * 0.34f)
        }
        drawPath(voluteTop, tint.copy(alpha = 0.6f), style = thinClassic(s))
        // Volute curl at bottom of tail
        val voluteBot = Path().apply {
            moveTo(s * 0.75f, s * 0.50f)
            cubicTo(s * 0.80f, s * 0.58f, s * 0.86f, s * 0.64f, s * 0.82f, s * 0.70f)
            cubicTo(s * 0.78f, s * 0.74f, s * 0.74f, s * 0.70f, s * 0.76f, s * 0.66f)
        }
        drawPath(voluteBot, tint.copy(alpha = 0.6f), style = thinClassic(s))
        // Small spiral at arrow tip
        val spiral = Path().apply {
            moveTo(s * 0.26f, s * 0.50f)
            cubicTo(s * 0.22f, s * 0.47f, s * 0.19f, s * 0.50f, s * 0.22f, s * 0.53f)
        }
        drawPath(spiral, tint.copy(alpha = 0.5f), style = thinClassic(s))
    }
}
```

**Step 2: Rewrite Add — 百合十字**

Cross with fleur-de-lis tips at each arm end, elegant symmetry with thickness gradient:

```kotlin
@Composable override fun Add(modifier: Modifier, tint: Color) {
    Canvas(modifier.size(24.dp)) {
        val s = size.minDimension
        val cx = s * 0.5f; val cy = s * 0.5f
        // Cross arms with thickness gradient (thinner at center, wider at ends)
        // Vertical arm
        val vArm = Path().apply {
            moveTo(cx - s * 0.04f, cy - s * 0.06f)
            lineTo(cx - s * 0.08f, cy - s * 0.34f)
            // Fleur-de-lis top
            cubicTo(cx - s * 0.14f, cy - s * 0.38f, cx - s * 0.12f, cy - s * 0.44f, cx, cy - s * 0.40f)
            cubicTo(cx + s * 0.12f, cy - s * 0.44f, cx + s * 0.14f, cy - s * 0.38f, cx + s * 0.08f, cy - s * 0.34f)
            lineTo(cx + s * 0.04f, cy - s * 0.06f)
        }
        drawPath(vArm, tint, style = Fill)
        // Bottom arm (mirror)
        val bArm = Path().apply {
            moveTo(cx - s * 0.04f, cy + s * 0.06f)
            lineTo(cx - s * 0.08f, cy + s * 0.34f)
            cubicTo(cx - s * 0.14f, cy + s * 0.38f, cx - s * 0.12f, cy + s * 0.44f, cx, cy + s * 0.40f)
            cubicTo(cx + s * 0.12f, cy + s * 0.44f, cx + s * 0.14f, cy + s * 0.38f, cx + s * 0.08f, cy + s * 0.34f)
            lineTo(cx + s * 0.04f, cy + s * 0.06f)
        }
        drawPath(bArm, tint, style = Fill)
        // Right arm
        val rArm = Path().apply {
            moveTo(cx + s * 0.06f, cy - s * 0.04f)
            lineTo(cx + s * 0.34f, cy - s * 0.08f)
            cubicTo(cx + s * 0.38f, cy - s * 0.14f, cx + s * 0.44f, cy - s * 0.12f, cx + s * 0.40f, cy)
            cubicTo(cx + s * 0.44f, cy + s * 0.12f, cx + s * 0.38f, cy + s * 0.14f, cx + s * 0.34f, cy + s * 0.08f)
            lineTo(cx + s * 0.06f, cy + s * 0.04f)
        }
        drawPath(rArm, tint, style = Fill)
        // Left arm
        val lArm = Path().apply {
            moveTo(cx - s * 0.06f, cy - s * 0.04f)
            lineTo(cx - s * 0.34f, cy - s * 0.08f)
            cubicTo(cx - s * 0.38f, cy - s * 0.14f, cx - s * 0.44f, cy - s * 0.12f, cx - s * 0.40f, cy)
            cubicTo(cx - s * 0.44f, cy + s * 0.12f, cx - s * 0.38f, cy + s * 0.14f, cx - s * 0.34f, cy + s * 0.08f)
            lineTo(cx - s * 0.06f, cy + s * 0.04f)
        }
        drawPath(lArm, tint, style = Fill)
        // Center circle
        drawCircle(tint, s * 0.06f, Offset(cx, cy))
    }
}
```

**Step 3: Rewrite FilterList — 枝形烛台**

Three decreasing horizontal bars connected by a central vertical stem, with scrollwork ball finials at bar ends:

```kotlin
@Composable override fun FilterList(modifier: Modifier, tint: Color) {
    Canvas(modifier.size(24.dp)) {
        val s = size.minDimension
        val cx = s * 0.5f
        data class Bar(val y: Float, val halfW: Float)
        val bars = listOf(
            Bar(s * 0.24f, s * 0.36f),
            Bar(s * 0.50f, s * 0.24f),
            Bar(s * 0.76f, s * 0.12f)
        )
        // Central stem
        drawLine(tint, Offset(cx, s * 0.18f), Offset(cx, s * 0.82f),
            strokeWidth = s * 0.04f, cap = StrokeCap.Round)
        // Horizontal bars with ball finials
        bars.forEach { bar ->
            drawLine(tint, Offset(cx - bar.halfW, bar.y), Offset(cx + bar.halfW, bar.y),
                strokeWidth = s * 0.055f, cap = StrokeCap.Round)
            // Scrollwork ball at each end
            drawCircle(tint, s * 0.035f, Offset(cx - bar.halfW, bar.y))
            drawCircle(tint, s * 0.035f, Offset(cx + bar.halfW, bar.y))
            // Tiny scrollwork curls
            drawScrollwork(Offset(cx - bar.halfW - s * 0.01f, bar.y), s * 0.025f, tint.copy(alpha = 0.4f))
            drawScrollwork(Offset(cx + bar.halfW + s * 0.01f, bar.y), s * 0.025f, tint.copy(alpha = 0.4f))
        }
    }
}
```

**Step 4: Rewrite Sort — 罗马柱阶梯**

Three decreasing bars aligned left to a vertical column shaft, with ball capitals at bar ends:

```kotlin
@Composable override fun Sort(modifier: Modifier, tint: Color) {
    Canvas(modifier.size(24.dp)) {
        val s = size.minDimension
        data class Bar(val y: Float, val endX: Float)
        val bars = listOf(
            Bar(s * 0.25f, s * 0.82f),
            Bar(s * 0.50f, s * 0.64f),
            Bar(s * 0.75f, s * 0.46f)
        )
        val colX = s * 0.18f
        // Column shaft
        drawLine(tint, Offset(colX, s * 0.15f), Offset(colX, s * 0.85f),
            strokeWidth = s * 0.05f, cap = StrokeCap.Round)
        // Column base and capital
        drawLine(tint, Offset(colX - s * 0.06f, s * 0.85f), Offset(colX + s * 0.06f, s * 0.85f),
            strokeWidth = s * 0.04f, cap = StrokeCap.Round)
        drawLine(tint, Offset(colX - s * 0.06f, s * 0.15f), Offset(colX + s * 0.06f, s * 0.15f),
            strokeWidth = s * 0.04f, cap = StrokeCap.Round)
        // Horizontal bars
        bars.forEach { bar ->
            drawLine(tint, Offset(colX, bar.y), Offset(bar.endX, bar.y),
                strokeWidth = s * 0.055f, cap = StrokeCap.Round)
            // Ball capital at end
            drawCircle(tint, s * 0.04f, Offset(bar.endX, bar.y))
        }
    }
}
```

**Step 5: Add ViewAgenda — 书页堆叠**

Two bordered rectangles stacked vertically with serif line endings and internal ruled lines:

```kotlin
@Composable override fun ViewAgenda(modifier: Modifier, tint: Color) {
    Canvas(modifier.size(24.dp)) {
        val s = size.minDimension
        val stroke = classicStroke(s)
        // Top page
        drawRoundRect(tint, Offset(s * 0.14f, s * 0.10f), Size(s * 0.72f, s * 0.32f),
            cornerRadius = CornerRadius(s * 0.02f), style = stroke)
        // Ruled lines inside top page
        drawLine(tint.copy(alpha = 0.3f), Offset(s * 0.22f, s * 0.20f), Offset(s * 0.78f, s * 0.20f),
            strokeWidth = s * 0.02f)
        drawLine(tint.copy(alpha = 0.3f), Offset(s * 0.22f, s * 0.30f), Offset(s * 0.78f, s * 0.30f),
            strokeWidth = s * 0.02f)
        // Bottom page
        drawRoundRect(tint, Offset(s * 0.14f, s * 0.58f), Size(s * 0.72f, s * 0.32f),
            cornerRadius = CornerRadius(s * 0.02f), style = stroke)
        // Ruled lines inside bottom page
        drawLine(tint.copy(alpha = 0.3f), Offset(s * 0.22f, s * 0.68f), Offset(s * 0.78f, s * 0.68f),
            strokeWidth = s * 0.02f)
        drawLine(tint.copy(alpha = 0.3f), Offset(s * 0.22f, s * 0.78f), Offset(s * 0.78f, s * 0.78f),
            strokeWidth = s * 0.02f)
    }
}
```

**Step 6: Add GridView — 卷纹方块**

Four squares in 2×2 with tiny diagonal scrollwork inside each:

```kotlin
@Composable override fun GridView(modifier: Modifier, tint: Color) {
    Canvas(modifier.size(24.dp)) {
        val s = size.minDimension
        val gap = s * 0.08f
        val cellSize = (s - gap * 3) / 2
        val stroke = Stroke(s * 0.05f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        for (row in 0..1) {
            for (col in 0..1) {
                val x = gap + col * (cellSize + gap)
                val y = gap + row * (cellSize + gap)
                drawRoundRect(tint, Offset(x, y), Size(cellSize, cellSize),
                    cornerRadius = CornerRadius(s * 0.02f), style = stroke)
                // Diagonal scrollwork accent
                drawScrollwork(Offset(x + cellSize * 0.5f, y + cellSize * 0.5f),
                    s * 0.03f, tint.copy(alpha = 0.35f))
            }
        }
    }
}
```

**Step 7: Add Apps — 珍珠阵列**

Nine small circles in 3×3, each with a fine outer ring (pearl effect):

```kotlin
@Composable override fun Apps(modifier: Modifier, tint: Color) {
    Canvas(modifier.size(24.dp)) {
        val s = size.minDimension
        val gap = s / 4f
        val innerR = s * 0.045f
        val outerR = s * 0.065f
        for (row in 0..2) {
            for (col in 0..2) {
                val cx = gap + col * gap; val cy = gap + row * gap
                // Outer ring
                drawCircle(tint.copy(alpha = 0.35f), outerR, Offset(cx, cy),
                    style = Stroke(s * 0.02f))
                // Inner pearl
                drawCircle(tint, innerR, Offset(cx, cy), style = Fill)
            }
        }
    }
}
```

**Step 8: Build verify**

Run: `./gradlew.bat assembleDebug`

**Step 9: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/icon/ClassicIconProvider.kt
git commit -m "feat(skin): redesign Classic icons — ArrowBack, Add, FilterList, Sort + new view toggles"
```

---

### Task 7: Fix screen files to use SkinIcon

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/common/SortOption.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateListScreen.kt`

**Step 1: Fix SortOption.kt — replace Material Sort icon with SkinIcon**

Replace the `Icon(Icons.AutoMirrored.Filled.Sort, ...)` call in `SortMenuButton` (line 29-35) with:

```kotlin
IconButton(onClick = { expanded = true }, modifier = modifier) {
    SkinIcon(
        IconKey.Sort,
        tint = if (currentSort != SortOption.DEFAULT)
            MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
    )
}
```

Remove the now-unused import: `import androidx.compose.material.icons.automirrored.filled.Sort`

**Step 2: Fix ItemListScreen.kt — replace view toggle placeholder**

Replace the view toggle `IconButton` block (lines 191-211) where all three states use `IconKey.Sort`:

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
) {
    val currentColumns = if (pagerState.currentPage == 2) coordinateUiState.columnsPerRow else uiState.columnsPerRow
    SkinIcon(
        when (currentColumns) {
            1 -> IconKey.ViewAgenda
            2 -> IconKey.GridView
            else -> IconKey.Apps
        },
        tint = MaterialTheme.colorScheme.primary
    )
}
```

**Step 3: Fix CoordinateListScreen.kt — replace Material view toggle icons**

Replace the view toggle `IconButton` block (lines 68-81) where Material `Icons.Default.ViewAgenda/GridView/Apps` are used:

```kotlin
IconButton(onClick = {
    val next = when (uiState.columnsPerRow) { 1 -> 2; 2 -> 3; else -> 1 }
    viewModel.setColumns(next)
}) {
    SkinIcon(
        when (uiState.columnsPerRow) {
            1 -> IconKey.ViewAgenda
            2 -> IconKey.GridView
            else -> IconKey.Apps
        },
        tint = MaterialTheme.colorScheme.primary
    )
}
```

Remove unused Material icon imports from CoordinateListScreen.kt:
- `import androidx.compose.material.icons.filled.GridView`
- `import androidx.compose.material.icons.filled.ViewAgenda`
- `import androidx.compose.material.icons.filled.Apps`

Add import if not present:
- `import com.lolita.app.ui.theme.skin.icon.IconKey`
- `import com.lolita.app.ui.theme.skin.icon.SkinIcon`

**Step 4: Build verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/common/SortOption.kt \
       app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt \
       app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateListScreen.kt
git commit -m "fix(skin): replace remaining Material icons with SkinIcon in SortOption, ItemList, CoordinateList"
```

---

### Task 8: Final build verification

**Step 1: Clean build**

Run: `./gradlew.bat clean assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 2: Commit plan document**

```bash
git add docs/plans/2026-02-21-skin-icon-redesign-plan.md
git commit -m "docs: add skin icon redesign implementation plan"
```
