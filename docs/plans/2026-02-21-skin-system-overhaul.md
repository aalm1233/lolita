# 皮肤系统深度定制 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace all 45 Material icons with Canvas-drawn per-skin icons (180 variants), add 4 distinct animation systems per skin, and refactor all UI components to be skin-aware.

**Architecture:** Skin Engine + Interface Abstraction. `SkinIconProvider` and `SkinAnimationProvider` interfaces injected via `LolitaSkinConfig` → `CompositionLocal`. Each skin implements its own provider. `BaseSkinIconProvider` provides Material icon fallbacks during incremental migration.

**Tech Stack:** Kotlin, Jetpack Compose Canvas API, Compose Animation, Material3, existing LolitaSkin CompositionLocal system.

**Base package:** `com.lolita.app`
**Base path:** `app/src/main/java/com/lolita/app`

---

## Phase 1: Infrastructure

### Task 1: Create SkinIconProvider interface hierarchy

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/SkinIconProvider.kt`

**Step 1: Create the interface file**

```kotlin
package com.lolita.app.ui.theme.skin.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

interface NavigationIcons {
    @Composable fun Home(modifier: Modifier, tint: Color)
    @Composable fun Wishlist(modifier: Modifier, tint: Color)
    @Composable fun Outfit(modifier: Modifier, tint: Color)
    @Composable fun Stats(modifier: Modifier, tint: Color)
    @Composable fun Settings(modifier: Modifier, tint: Color)
}

// __CONTINUE_INTERFACES__
```

Full interface list (5 sub-interfaces):
- `NavigationIcons`: Home, Wishlist, Outfit, Stats, Settings (5)
- `ActionIcons`: Add, Delete, Edit, Search, Sort, Save, Close, Share, FilterList, MoreVert, ContentCopy, Refresh (12)
- `ContentIcons`: Star, StarBorder, Image, Camera, AddPhoto, Link, LinkOff, Palette, FileOpen, CalendarMonth, Notifications, AttachMoney, Category (13)
- `ArrowIcons`: ArrowBack, ArrowForward, KeyboardArrowLeft, KeyboardArrowRight, ExpandMore, ExpandLess, ArrowDropDown, SwapVert, OpenInNew (9)
- `StatusIcons`: CheckCircle, Warning, Error, Info, Visibility, VisibilityOff (6)

Top-level aggregator:
```kotlin
interface SkinIconProvider {
    val navigation: NavigationIcons
    val action: ActionIcons
    val content: ContentIcons
    val arrow: ArrowIcons
    val status: StatusIcons
}
```

All methods share signature: `@Composable fun Name(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)`

**Step 2: Build to verify compilation**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/icon/SkinIconProvider.kt
git commit -m "feat(skin): add SkinIconProvider interface hierarchy with 5 sub-interfaces"
```

---

### Task 2: Create SkinAnimationProvider interface and data classes

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinAnimationProvider.kt`

**Step 1: Create the animation provider file**

```kotlin
package com.lolita.app.ui.theme.skin.animation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

data class SkinTransitionSpec(
    val durationMs: Int,
    val overlay: @Composable (progress: Float) -> Unit
)

data class TabSwitchAnimationSpec(
    val indicatorAnimation: AnimationSpec<Float>,
    val selectedEffect: @Composable (selected: Boolean) -> Modifier,
    val particleEffect: (@Composable (Offset) -> Unit)?
)

data class CardAnimationSpec(
    val enterTransition: EnterTransition,
    val exitTransition: ExitTransition,
    val staggerDelayMs: Int,
    val enterDurationMs: Int
)

data class InteractionFeedbackSpec(
    val pressScale: Float,
    val rippleColor: Color,
    val rippleAlpha: Float,
    val customRipple: (@Composable (Offset, Float) -> Unit)?
)

interface SkinAnimationProvider {
    val skinTransition: SkinTransitionSpec
    val tabSwitchAnimation: TabSwitchAnimationSpec
    val cardAnimation: CardAnimationSpec
    val interactionFeedback: InteractionFeedbackSpec
}
```

**Step 2: Build to verify**

Run: `./gradlew.bat assembleDebug`

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinAnimationProvider.kt
git commit -m "feat(skin): add SkinAnimationProvider interface with animation spec data classes"
```

---

### Task 3: Create IconKey enum and SkinIcon helper composable

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/IconKey.kt`
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/SkinIcon.kt`

**Step 1: Create IconKey enum**

```kotlin
package com.lolita.app.ui.theme.skin.icon

enum class IconKey {
    // Navigation
    Home, Wishlist, Outfit, Stats, Settings,
    // Action
    Add, Delete, Edit, Search, Sort, Save, Close, Share, FilterList, MoreVert, ContentCopy, Refresh,
    // Content
    Star, StarBorder, Image, Camera, AddPhoto, Link, LinkOff, Palette, FileOpen, CalendarMonth, Notifications, AttachMoney, Category,
    // Arrow
    ArrowBack, ArrowForward, KeyboardArrowLeft, KeyboardArrowRight, ExpandMore, ExpandLess, ArrowDropDown, SwapVert, OpenInNew,
    // Status
    CheckCircle, Warning, Error, Info, Visibility, VisibilityOff
}
```

**Step 2: Create SkinIcon helper composable**

```kotlin
package com.lolita.app.ui.theme.skin.icon

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.lolita.app.ui.theme.LolitaSkin

@Composable
fun SkinIcon(
    key: IconKey,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    val icons = LolitaSkin.current.icons
    when (key) {
        IconKey.Home -> icons.navigation.Home(modifier, tint)
        IconKey.Wishlist -> icons.navigation.Wishlist(modifier, tint)
        // ... all 45 mappings
    }
}
```

The `when` block maps all 45 `IconKey` values to the corresponding `SkinIconProvider` sub-interface method call.

**Step 3: Build and commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/icon/IconKey.kt \
       app/src/main/java/com/lolita/app/ui/theme/skin/icon/SkinIcon.kt
git commit -m "feat(skin): add IconKey enum and SkinIcon helper composable"
```

---

### Task 4: Create BaseSkinIconProvider with Material icon fallbacks

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/BaseSkinIconProvider.kt`

**Step 1: Create base class**

This class provides default implementations for all 45 icons using standard Material icons, so that during incremental migration, any icon not yet overridden by a skin still renders correctly.

```kotlin
package com.lolita.app.ui.theme.skin.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

open class BaseNavigationIcons : NavigationIcons {
    @Composable override fun Home(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.Home, null, modifier, tint)
    @Composable override fun Wishlist(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.Favorite, null, modifier, tint)
    @Composable override fun Outfit(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.DateRange, null, modifier, tint)
    @Composable override fun Stats(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.Info, null, modifier, tint)
    @Composable override fun Settings(modifier: Modifier, tint: Color) =
        Icon(Icons.Filled.Settings, null, modifier, tint)
}
// Same pattern for BaseActionIcons, BaseContentIcons, BaseArrowIcons, BaseStatusIcons
// Each open class implements its sub-interface with Material icon defaults

open class BaseSkinIconProvider : SkinIconProvider {
    override val navigation: NavigationIcons = BaseNavigationIcons()
    override val action: ActionIcons = BaseActionIcons()
    override val content: ContentIcons = BaseContentIcons()
    override val arrow: ArrowIcons = BaseArrowIcons()
    override val status: StatusIcons = BaseStatusIcons()
}
```

All 45 Material icon mappings must be implemented. Reference the current codebase usage:
- `Icons.Filled.Home` → Home
- `Icons.Filled.Favorite` → Wishlist
- `Icons.Filled.DateRange` → Outfit
- `Icons.Filled.Info` → Stats / Info
- `Icons.Filled.Settings` → Settings
- `Icons.Default.Add` → Add
- `Icons.Default.Delete` / `Icons.Filled.Delete` → Delete
- `Icons.Default.Edit` → Edit
- `Icons.Default.Search` → Search
- `Icons.AutoMirrored.Filled.Sort` → Sort
- `Icons.Default.Check` → Save
- `Icons.Default.Close` → Close
- `Icons.Default.Share` → Share
- `Icons.Default.FilterList` → FilterList
- `Icons.Default.MoreVert` → MoreVert
- `Icons.Default.ContentCopy` → ContentCopy
- `Icons.Default.Refresh` → Refresh
- `Icons.Filled.Star` → Star
- `Icons.Default.StarBorder` → StarBorder
- `Icons.Default.Image` → Image
- `Icons.Default.Camera` → Camera (or CameraAlt)
- `Icons.Default.AddPhotoAlternate` → AddPhoto
- `Icons.Default.Link` → Link
- `Icons.Default.LinkOff` → LinkOff
- `Icons.Default.Palette` → Palette
- `Icons.Default.FileOpen` → FileOpen
- `Icons.Default.CalendarMonth` → CalendarMonth
- `Icons.Default.Notifications` → Notifications
- `Icons.Default.AttachMoney` → AttachMoney
- `Icons.Default.Category` → Category
- `Icons.AutoMirrored.Filled.ArrowBack` → ArrowBack
- `Icons.AutoMirrored.Filled.ArrowForward` → ArrowForward
- `Icons.AutoMirrored.Filled.KeyboardArrowLeft` → KeyboardArrowLeft
- `Icons.AutoMirrored.Filled.KeyboardArrowRight` → KeyboardArrowRight
- `Icons.Default.ExpandMore` / `Icons.Default.KeyboardArrowDown` → ExpandMore
- `Icons.Default.ExpandLess` / `Icons.Default.KeyboardArrowUp` → ExpandLess
- `Icons.Default.ArrowDropDown` → ArrowDropDown
- `Icons.Default.SwapVert` → SwapVert
- `Icons.Default.OpenInNew` → OpenInNew
- `Icons.Default.CheckCircle` → CheckCircle
- `Icons.Default.Warning` → Warning
- `Icons.Default.Error` → Error
- `Icons.Default.Visibility` → Visibility
- `Icons.Default.VisibilityOff` → VisibilityOff

**Step 2: Build and commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/icon/BaseSkinIconProvider.kt
git commit -m "feat(skin): add BaseSkinIconProvider with Material icon fallbacks for all 45 icons"
```

---

### Task 5: Extend LolitaSkinConfig and wire providers

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/LolitaSkinConfig.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/SkinConfigs.kt`

**Step 1: Add icons and animations fields to LolitaSkinConfig**

In `LolitaSkinConfig.kt`, add two new fields:

```kotlin
import com.lolita.app.ui.theme.skin.icon.SkinIconProvider
import com.lolita.app.ui.theme.skin.animation.SkinAnimationProvider

data class LolitaSkinConfig(
    // ... all existing fields unchanged ...
    val topBarDecoration: String,
    val topBarDecorationAlpha: Float,
    val icons: SkinIconProvider,
    val animations: SkinAnimationProvider,
)
```

**Step 2: Create a default SkinAnimationProvider implementation**

Create: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/DefaultAnimationProvider.kt`

```kotlin
package com.lolita.app.ui.theme.skin.animation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

class DefaultAnimationProvider : SkinAnimationProvider {
    override val skinTransition = SkinTransitionSpec(
        durationMs = 300,
        overlay = { _ -> Box(Modifier) } // no overlay
    )
    override val tabSwitchAnimation = TabSwitchAnimationSpec(
        indicatorAnimation = spring(stiffness = Spring.StiffnessMediumLow),
        selectedEffect = { _ -> Modifier },
        particleEffect = null
    )
    override val cardAnimation = CardAnimationSpec(
        enterTransition = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 },
        exitTransition = fadeOut(tween(200)),
        staggerDelayMs = 50,
        enterDurationMs = 300
    )
    override val interactionFeedback = InteractionFeedbackSpec(
        pressScale = 0.96f,
        rippleColor = Color.Black,
        rippleAlpha = 0.12f,
        customRipple = null
    )
}
```

**Step 3: Wire providers into SkinConfigs**

In `SkinConfigs.kt`, update each skin config to include `icons` and `animations`:

```kotlin
import com.lolita.app.ui.theme.skin.icon.BaseSkinIconProvider
import com.lolita.app.ui.theme.skin.animation.DefaultAnimationProvider

// In each skinConfig function, add at the end:
//   icons = BaseSkinIconProvider(),
//   animations = DefaultAnimationProvider(),
// Later tasks will replace these with skin-specific providers.
```

All four configs (`defaultSkinConfig()`, `gothicSkinConfig()`, `chineseSkinConfig()`, `classicSkinConfig()`) get `BaseSkinIconProvider()` and `DefaultAnimationProvider()` as initial values.

**Step 4: Build to verify**

Run: `./gradlew.bat assembleDebug`

**Step 5: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/DefaultAnimationProvider.kt \
       app/src/main/java/com/lolita/app/ui/theme/LolitaSkinConfig.kt \
       app/src/main/java/com/lolita/app/ui/theme/SkinConfigs.kt
git commit -m "feat(skin): wire SkinIconProvider and SkinAnimationProvider into LolitaSkinConfig"
```

---

## Phase 2: Icon Implementations

> Each task creates a full `SkinIconProvider` implementation with all 45 Canvas-drawn icons for one skin.
> Icons are drawn via `Canvas` composable (or `drawBehind`/`drawWithContent` modifiers) — no ImageVector or PNG assets.
> Each skin's icon set lives in its own file under `ui/theme/skin/icon/`.

### Task 6: Sweet (甜美粉) icon set — all 45 Canvas icons

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/SweetIconProvider.kt`

**Step 1: Create SweetIconProvider**

Design language for Sweet icons:
- **Stroke:** Rounded caps/joins, `strokeWidth = 2.dp`, soft pink tint blending
- **Shapes:** All corners rounded, circles preferred over rectangles
- **Decorations:** Tiny hearts, bows, flower petals as accent details on icons
- **Fills:** Soft linear gradients (pink → light pink), semi-transparent fills

Structure — extends `BaseSkinIconProvider`, overrides all 5 sub-interface classes:

```kotlin
package com.lolita.app.ui.theme.skin.icon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp

private class SweetNavigationIcons : BaseNavigationIcons() {
    @Composable override fun Home(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val stroke = s * 0.08f
            val paint = Paint().apply {
                color = tint; style = PaintingStyle.Stroke
                strokeWidth = stroke; strokeCap = StrokeCap.Round
                strokeJoin = StrokeJoin.Round
            }
            // Rounded house shape with heart in center
            val path = Path().apply {
                moveTo(s * 0.5f, s * 0.15f)  // roof peak
                lineTo(s * 0.15f, s * 0.5f)  // left eave
                lineTo(s * 0.15f, s * 0.82f)
                quadraticBezierTo(s * 0.15f, s * 0.88f, s * 0.22f, s * 0.88f)
                lineTo(s * 0.78f, s * 0.88f)
                quadraticBezierTo(s * 0.85f, s * 0.88f, s * 0.85f, s * 0.82f)
                lineTo(s * 0.85f, s * 0.5f)
                close()
            }
            drawContext.canvas.drawPath(path, paint)
            // Small heart decoration in center
            drawSweetHeart(center, s * 0.12f, tint)
        }
    }
    // ... Wishlist, Outfit, Stats, Settings — same Canvas pattern
    // Each with rounded shapes + heart/bow/petal decorations
}

// Same pattern for SweetActionIcons, SweetContentIcons,
// SweetArrowIcons, SweetStatusIcons

class SweetIconProvider : BaseSkinIconProvider() {
    override val navigation: NavigationIcons = SweetNavigationIcons()
    override val action: ActionIcons = SweetActionIcons()
    override val content: ContentIcons = SweetContentIcons()
    override val arrow: ArrowIcons = SweetArrowIcons()
    override val status: StatusIcons = SweetStatusIcons()
}

// Shared helper: draw a small heart shape
private fun DrawScope.drawSweetHeart(
    center: Offset, radius: Float, color: Color
) {
    val path = Path().apply {
        moveTo(center.x, center.y + radius * 0.3f)
        cubicTo(center.x - radius, center.y - radius * 0.5f,
                center.x - radius * 0.5f, center.y - radius,
                center.x, center.y - radius * 0.3f)
        cubicTo(center.x + radius * 0.5f, center.y - radius,
                center.x + radius, center.y - radius * 0.5f,
                center.x, center.y + radius * 0.3f)
    }
    drawPath(path, color, style = Fill)
}
```

All 45 icons must be implemented. Key icon design notes for Sweet skin:

| Sub-interface | Icons | Sweet design notes |
|---|---|---|
| Navigation (5) | Home, Wishlist, Outfit, Stats, Settings | House with heart, heart with bow, dress with ribbon, pie chart with flower, gear with petal |
| Action (12) | Add, Delete, Edit, Search, Sort, Save, Close, Share, FilterList, MoreVert, ContentCopy, Refresh | Rounded plus with sparkle, trash can with heart lid, pencil with bow, magnifier with heart lens, arrows with dots, floppy with heart, X with petals, share arrow with heart, funnel with bow, three hearts vertical, overlapping hearts, circular arrows with flower |
| Content (13) | Star, StarBorder, Image, Camera, AddPhoto, Link, LinkOff, Palette, FileOpen, CalendarMonth, Notifications, AttachMoney, Category | Five-point star with rounded tips, same outline, polaroid with heart, camera with heart lens, photo+ with bow, chain links as hearts, broken heart chain, palette with heart holes, folder with ribbon, calendar with heart date, bell with bow, coin with heart, grid with heart cells |
| Arrow (9) | ArrowBack/Forward, KbArrowL/R, ExpandMore/Less, ArrowDropDown, SwapVert, OpenInNew | All arrows use rounded caps, teardrop-shaped tips, subtle petal trail on directional arrows |
| Status (6) | CheckCircle, Warning, Error, Info, Visibility/Off | Check in heart shape, triangle with bow, circle with X + petal, heart with "i", eye with heart iris, eye with heart + slash |

Shared helper functions to extract into the file:
- `drawSweetHeart(center, radius, color)` — filled heart shape
- `drawSweetBow(center, width, color)` — small ribbon bow decoration
- `drawSweetPetal(center, radius, angle, color)` — single flower petal
- `drawSweetFlower(center, radius, color, petalCount)` — flower from petals

**Step 2: Build to verify**

Run: `./gradlew.bat assembleDebug`

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/icon/SweetIconProvider.kt
git commit -m "feat(skin): add SweetIconProvider with 45 Canvas-drawn sweet/cute icons"
```

---

### Task 7: Gothic (哥特暗黑) icon set — all 45 Canvas icons

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/GothicIconProvider.kt`

**Step 1: Create GothicIconProvider**

Design language for Gothic icons:
- **Stroke:** Sharp miter joins, `strokeWidth = 1.5.dp`, thin precise lines
- **Shapes:** Angular, pointed tips, diamond/rhombus motifs over circles
- **Decorations:** Crosses, thorns, bat wings, crescent moons, spider web fragments
- **Fills:** Stroke-based (minimal fill), when filled use flat dark tones, no gradients

```kotlin
package com.lolita.app.ui.theme.skin.icon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp

private class GothicNavigationIcons : BaseNavigationIcons() {
    @Composable override fun Home(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val stroke = s * 0.06f
            val paint = Paint().apply {
                color = tint; style = PaintingStyle.Stroke
                strokeWidth = stroke; strokeCap = StrokeCap.Butt
                strokeJoin = StrokeJoin.Miter
            }
            // Gothic pointed arch house with cross on top
            val path = Path().apply {
                moveTo(s * 0.5f, s * 0.08f)   // sharp peak
                lineTo(s * 0.12f, s * 0.52f)  // left wall
                lineTo(s * 0.12f, s * 0.9f)
                lineTo(s * 0.88f, s * 0.9f)
                lineTo(s * 0.88f, s * 0.52f)
                close()
            }
            drawContext.canvas.drawPath(path, paint)
            // Small cross at peak
            drawGothicCross(Offset(s * 0.5f, s * 0.08f), s * 0.06f, tint)
            // Pointed arch door
            val door = Path().apply {
                moveTo(s * 0.38f, s * 0.9f)
                lineTo(s * 0.38f, s * 0.62f)
                quadraticBezierTo(s * 0.5f, s * 0.5f, s * 0.62f, s * 0.62f)
                lineTo(s * 0.62f, s * 0.9f)
            }
            drawContext.canvas.drawPath(door, paint)
        }
    }
    // ... Wishlist, Outfit, Stats, Settings — same Canvas pattern
    // Wishlist: thorned heart, Outfit: coffin-shaped wardrobe,
    // Stats: bar chart with cross tops, Settings: gear with bat wings
}

// Same pattern for GothicActionIcons, GothicContentIcons,
// GothicArrowIcons, GothicStatusIcons

class GothicIconProvider : BaseSkinIconProvider() {
    override val navigation: NavigationIcons = GothicNavigationIcons()
    override val action: ActionIcons = GothicActionIcons()
    override val content: ContentIcons = GothicContentIcons()
    override val arrow: ArrowIcons = GothicArrowIcons()
    override val status: StatusIcons = GothicStatusIcons()
}

// Shared helpers
private fun DrawScope.drawGothicCross(
    center: Offset, armLen: Float, color: Color
) {
    drawLine(color, Offset(center.x, center.y - armLen),
        Offset(center.x, center.y + armLen), strokeWidth = armLen * 0.3f)
    drawLine(color, Offset(center.x - armLen * 0.6f, center.y - armLen * 0.3f),
        Offset(center.x + armLen * 0.6f, center.y - armLen * 0.3f),
        strokeWidth = armLen * 0.3f)
}

private fun DrawScope.drawGothicThorn(
    start: Offset, end: Offset, thornLen: Float, color: Color
) { /* thorn spikes along a line segment */ }
```

All 45 icons must be implemented. Key icon design notes for Gothic skin:

| Sub-interface | Icons | Gothic design notes |
|---|---|---|
| Navigation (5) | Home, Wishlist, Outfit, Stats, Settings | Pointed arch house + cross, thorned heart, coffin wardrobe, bar chart with cross tops, gear with bat wings |
| Action (12) | Add~Refresh | Plus with thorn tips, trash with cross lid, quill pen, magnifier with web, swords crossed for sort, coffin-shaped save, X with thorns, share with bat wing, funnel with cross, three crosses vertical, overlapping diamonds, circular thorns |
| Content (13) | Star~Category | Pentagram star, same outline, frame with cross, camera with bat wing, photo+ with thorn, chain links angular, broken chain, palette with cross holes, tome/book, calendar with moon, bell with bat, coin with cross, grid with diamond cells |
| Arrow (9) | ArrowBack~OpenInNew | Sharp pointed arrows, dagger-tip style, angular chevrons, diamond-shaped drop |
| Status (6) | CheckCircle~VisibilityOff | Check in pentagram, triangle with cross, circle with X + thorns, diamond with "i", eye with slit pupil, eye with cross slash |

Shared helper functions:
- `drawGothicCross(center, armLen, color)` — ornate cross
- `drawGothicThorn(start, end, thornLen, color)` — thorn spikes along a line
- `drawGothicBatWing(anchor, span, color)` — small bat wing decoration
- `drawGothicArch(rect, color)` — pointed arch shape

**Step 2: Build to verify**

Run: `./gradlew.bat assembleDebug`

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/icon/GothicIconProvider.kt
git commit -m "feat(skin): add GothicIconProvider with 45 Canvas-drawn gothic/dark icons"
```

---

### Task 8: Chinese (中华风韵) icon set — all 45 Canvas icons

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/ChineseIconProvider.kt`

**Step 1: Create ChineseIconProvider**

Design language for Chinese icons:
- **Stroke:** Varying thickness simulating brush strokes, `strokeWidth` ranges 1.dp–3.dp within a single icon
- **Shapes:** Minimal rounding, rectangular with slight brush flare at endpoints
- **Decorations:** Auspicious clouds (祥云), plum blossoms (梅花), ink splash dots, seal stamp (印章) accents
- **Fills:** Ink wash effect — semi-transparent layered fills simulating 水墨 (ink wash), vermillion accent fills

```kotlin
package com.lolita.app.ui.theme.skin.icon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp

private class ChineseNavigationIcons : BaseNavigationIcons() {
    @Composable override fun Home(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            // Chinese pavilion (亭) with curved eaves
            val roof = Path().apply {
                moveTo(s * 0.5f, s * 0.1f)
                // Brush-stroke thick→thin roof line
                cubicTo(s * 0.3f, s * 0.15f,
                        s * 0.1f, s * 0.35f,
                        s * 0.05f, s * 0.4f)
                moveTo(s * 0.5f, s * 0.1f)
                cubicTo(s * 0.7f, s * 0.15f,
                        s * 0.9f, s * 0.35f,
                        s * 0.95f, s * 0.4f)
            }
            // Varying stroke width for brush effect
            drawPath(roof, tint, style = Stroke(
                width = s * 0.07f, cap = StrokeCap.Round))
            // Pillars
            drawLine(tint, Offset(s * 0.3f, s * 0.4f),
                Offset(s * 0.3f, s * 0.88f), strokeWidth = s * 0.04f)
            drawLine(tint, Offset(s * 0.7f, s * 0.4f),
                Offset(s * 0.7f, s * 0.88f), strokeWidth = s * 0.04f)
            // Small cloud decoration (祥云)
            drawChineseCloud(Offset(s * 0.85f, s * 0.2f),
                s * 0.08f, tint.copy(alpha = 0.5f))
        }
    }
    // ... Wishlist, Outfit, Stats, Settings
    // Wishlist: ink-brush heart with plum blossom
    // Outfit: hanfu silhouette with cloud collar
    // Stats: scroll (卷轴) with ink chart
    // Settings: bagua (八卦) inspired gear
}

// Same pattern for ChineseActionIcons, ChineseContentIcons,
// ChineseArrowIcons, ChineseStatusIcons

class ChineseIconProvider : BaseSkinIconProvider() {
    override val navigation: NavigationIcons = ChineseNavigationIcons()
    override val action: ActionIcons = ChineseActionIcons()
    override val content: ContentIcons = ChineseContentIcons()
    override val arrow: ArrowIcons = ChineseArrowIcons()
    override val status: StatusIcons = ChineseStatusIcons()
}

// Shared helpers
private fun DrawScope.drawChineseCloud(
    center: Offset, radius: Float, color: Color
) {
    // 祥云: three overlapping arcs forming auspicious cloud
    val path = Path().apply {
        moveTo(center.x - radius, center.y)
        arcTo(Rect(center.x - radius, center.y - radius,
            center.x, center.y), 180f, 180f, false)
        arcTo(Rect(center.x - radius * 0.5f, center.y - radius * 1.2f,
            center.x + radius * 0.5f, center.y), 180f, 180f, false)
        arcTo(Rect(center.x, center.y - radius,
            center.x + radius, center.y), 180f, 180f, false)
    }
    drawPath(path, color, style = Stroke(width = radius * 0.2f,
        cap = StrokeCap.Round))
}

private fun DrawScope.drawPlumBlossom(
    center: Offset, radius: Float, color: Color
) { /* 5-petal plum blossom with center dot */ }

private fun DrawScope.drawInkSplash(
    center: Offset, radius: Float, color: Color
) { /* semi-transparent irregular ink dot cluster */ }

private fun DrawScope.drawBrushStrokeLine(
    start: Offset, end: Offset, maxWidth: Float, color: Color
) { /* line with varying width: thin→thick→thin */ }
```

All 45 icons must be implemented. Key icon design notes for Chinese skin:

| Sub-interface | Icons | Chinese design notes |
|---|---|---|
| Navigation (5) | Home, Wishlist, Outfit, Stats, Settings | Pavilion (亭) with curved eaves + cloud, ink-brush heart with plum blossom, hanfu silhouette with cloud collar, scroll (卷轴) with ink chart, bagua (八卦) gear |
| Action (12) | Add~Refresh | Brush-stroke plus with ink splash, trash as broken vase, calligraphy brush, magnifier as jade bi (玉璧), bamboo slips for sort, seal stamp (印章) for save, X as crossed brushes, share as flying crane, funnel as tea strainer, three ink dots vertical, overlapping scrolls, circular cloud swirl |
| Content (13) | Star~Category | Five-point star as plum blossom, same outline, landscape painting frame, camera as ink stone, photo+ with seal, chain as jade links, broken jade chain, palette as ink stone set, bamboo scroll, calendar with lunar date, bell as temple bell, coin as ancient coin (铜钱), grid as window lattice (窗棂) |
| Arrow (9) | ArrowBack~OpenInNew | Brush-stroke arrows with flared tips, cloud-trail on directional arrows, fan-fold for expand/collapse |
| Status (6) | CheckCircle~VisibilityOff | Check in jade circle, triangle as mountain with warning flag, circle with X + ink splash, lantern with "i", eye with plum iris, eye with ink slash |

Shared helper functions:
- `drawChineseCloud(center, radius, color)` — 祥云 auspicious cloud
- `drawPlumBlossom(center, radius, color)` — 5-petal plum blossom
- `drawInkSplash(center, radius, color)` — ink dot cluster
- `drawBrushStrokeLine(start, end, maxWidth, color)` — varying-width brush line

**Step 2: Build to verify**

Run: `./gradlew.bat assembleDebug`

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/icon/ChineseIconProvider.kt
git commit -m "feat(skin): add ChineseIconProvider with 45 Canvas-drawn ink-wash style icons"
```

---

### Task 9: Classic (经典优雅) icon set — all 45 Canvas icons

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/ClassicIconProvider.kt`

**Step 1: Create ClassicIconProvider**

Design language for Classic icons:
- **Stroke:** Fine precise lines, `strokeWidth = 1.8.dp`, consistent weight, slight serif-like flares at terminals
- **Shapes:** Medium rounding, balanced proportions, symmetrical compositions
- **Decorations:** Scrollwork flourishes, spade (♠) motifs, small crowns, laurel accents
- **Fills:** Line art primary + gold-toned fill accents (`Color(0xFFD4AF37)`), hatching for shading

```kotlin
package com.lolita.app.ui.theme.skin.icon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp

private val GoldAccent = Color(0xFFD4AF37)

private class ClassicNavigationIcons : BaseNavigationIcons() {
    @Composable override fun Home(modifier: Modifier, tint: Color) {
        Canvas(modifier.size(24.dp)) {
            val s = size.minDimension
            val sw = s * 0.07f
            // Elegant manor house with crown finial
            val house = Path().apply {
                moveTo(s * 0.5f, s * 0.12f)   // roof peak
                lineTo(s * 0.1f, s * 0.45f)   // left eave
                lineTo(s * 0.1f, s * 0.85f)
                arcTo(Rect(s * 0.1f, s * 0.82f, s * 0.18f, s * 0.88f),
                    180f, 90f, false)
                lineTo(s * 0.82f, s * 0.88f)
                arcTo(Rect(s * 0.82f, s * 0.82f, s * 0.9f, s * 0.88f),
                    90f, 90f, false)
                lineTo(s * 0.9f, s * 0.45f)
                close()
            }
            drawPath(house, tint, style = Stroke(sw, cap = StrokeCap.Round))
            // Gold-filled door with arch
            val door = Path().apply {
                moveTo(s * 0.4f, s * 0.88f)
                lineTo(s * 0.4f, s * 0.6f)
                arcTo(Rect(s * 0.4f, s * 0.52f, s * 0.6f, s * 0.68f),
                    180f, 180f, false)
                lineTo(s * 0.6f, s * 0.88f)
            }
            drawPath(door, GoldAccent, style = Fill)
            drawPath(door, tint, style = Stroke(sw * 0.7f))
            // Small crown at peak
            drawClassicCrown(Offset(s * 0.5f, s * 0.1f), s * 0.07f, tint)
        }
    }
    // ... Wishlist, Outfit, Stats, Settings
    // Wishlist: heart with scrollwork border + gold fill
    // Outfit: mannequin with crown, Stats: framed chart with laurel
    // Settings: gear with spade center + gold teeth
}

// Same pattern for ClassicActionIcons, ClassicContentIcons,
// ClassicArrowIcons, ClassicStatusIcons

class ClassicIconProvider : BaseSkinIconProvider() {
    override val navigation: NavigationIcons = ClassicNavigationIcons()
    override val action: ActionIcons = ClassicActionIcons()
    override val content: ContentIcons = ClassicContentIcons()
    override val arrow: ArrowIcons = ClassicArrowIcons()
    override val status: StatusIcons = ClassicStatusIcons()
}

// Shared helpers
private fun DrawScope.drawClassicCrown(
    center: Offset, width: Float, color: Color
) {
    val path = Path().apply {
        val h = width * 0.6f
        moveTo(center.x - width, center.y)
        lineTo(center.x - width * 0.6f, center.y - h)
        lineTo(center.x - width * 0.3f, center.y - h * 0.4f)
        lineTo(center.x, center.y - h)
        lineTo(center.x + width * 0.3f, center.y - h * 0.4f)
        lineTo(center.x + width * 0.6f, center.y - h)
        lineTo(center.x + width, center.y)
        close()
    }
    drawPath(path, color, style = Stroke(width * 0.15f))
}

private fun DrawScope.drawClassicScrollwork(
    center: Offset, radius: Float, color: Color
) { /* S-curve flourish with spiral ends */ }

private fun DrawScope.drawClassicSpade(
    center: Offset, radius: Float, color: Color
) { /* ♠ spade shape: inverted heart + stem */ }

private fun DrawScope.drawClassicLaurel(
    center: Offset, radius: Float, color: Color
) { /* two mirrored leaf branches curving upward */ }
```

All 45 icons must be implemented. Key icon design notes for Classic skin:

| Sub-interface | Icons | Classic design notes |
|---|---|---|
| Navigation (5) | Home, Wishlist, Outfit, Stats, Settings | Manor house with crown finial + gold door, heart with scrollwork border + gold fill, mannequin with crown, framed chart with laurel wreath, gear with spade center + gold teeth |
| Action (12) | Add~Refresh | Plus with serif terminals, trash with crown lid, fountain pen, magnifier with scrollwork handle, crossed swords for sort, wax seal for save, X with flourish ends, share with laurel, funnel with gold rim, three spades vertical, overlapping framed pages, circular laurel arrows |
| Content (13) | Star~Category | Five-point star with gold fill, same outline, ornate frame, camera with crown, photo+ with seal, chain as gold links, broken gold chain, palette with spade holes, leather-bound book, calendar with crown header, bell with laurel, coin with crown, grid with spade cells |
| Arrow (9) | ArrowBack~OpenInNew | Arrows with serif-flared tips, scrollwork tails, chevrons with gold accent stroke |
| Status (6) | CheckCircle~VisibilityOff | Check in laurel wreath, triangle with crown, circle with X + scrollwork, shield with "i", eye with crown iris, eye with spade slash |

Shared helper functions:
- `drawClassicCrown(center, width, color)` — small crown decoration
- `drawClassicScrollwork(center, radius, color)` — S-curve flourish
- `drawClassicSpade(center, radius, color)` — spade motif
- `drawClassicLaurel(center, radius, color)` — laurel branch pair

**Step 2: Build to verify**

Run: `./gradlew.bat assembleDebug`

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/icon/ClassicIconProvider.kt
git commit -m "feat(skin): add ClassicIconProvider with 45 Canvas-drawn elegant line-art icons"
```

---

## Phase 3: Animation Implementations

> Each task creates a full `SkinAnimationProvider` implementation for one skin.
> All 4 animation specs must be implemented: skin transition, tab switch, card animation, interaction feedback.
> Animation files live under `ui/theme/skin/animation/`.

### Task 10: Sweet (甜美粉) animation system

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/SweetAnimationProvider.kt`

**Step 1: Create SweetAnimationProvider**

Sweet animation personality: bouncy, playful, overshoot springs, particle hearts and bubbles.

```kotlin
package com.lolita.app.ui.theme.skin.animation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lolita.app.ui.theme.Pink400
import kotlin.math.*
import kotlin.random.Random

class SweetAnimationProvider : SkinAnimationProvider {

    override val skinTransition = SkinTransitionSpec(
        durationMs = 350,
        overlay = { progress ->
            // Pink heart particles burst from center, fade out
            Canvas(Modifier.fillMaxSize()) {
                val count = 12
                for (i in 0 until count) {
                    val angle = (2 * PI * i / count).toFloat()
                    val dist = size.minDimension * 0.5f * progress
                    val cx = center.x + cos(angle) * dist
                    val cy = center.y + sin(angle) * dist
                    val alpha = (1f - progress).coerceIn(0f, 1f)
                    val r = size.minDimension * 0.02f * (1f - progress * 0.5f)
                    // Draw small heart at (cx, cy)
                    drawSweetHeartParticle(
                        Offset(cx, cy), r,
                        Pink400.copy(alpha = alpha)
                    )
                }
            }
        }
    )

    override val tabSwitchAnimation = TabSwitchAnimationSpec(
        indicatorAnimation = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        selectedEffect = { selected ->
            if (selected) Modifier else Modifier
            // Actual bounce scale applied via animateFloatAsState
            // in the NavigationBar composable
        },
        particleEffect = { position ->
            // Small bubble particles rising from tap point
            SweetBubbleEffect(position)
        }
    )

    override val cardAnimation = CardAnimationSpec(
        enterTransition = fadeIn(tween(300, easing = FastOutSlowInEasing)) +
            scaleIn(
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                initialScale = 0.85f
            ),
        exitTransition = fadeOut(tween(200)) +
            scaleOut(tween(200), targetScale = 0.9f),
        staggerDelayMs = 60,
        enterDurationMs = 350
    )

    override val interactionFeedback = InteractionFeedbackSpec(
        pressScale = 0.92f,  // bouncy press-down
        rippleColor = Pink400,
        rippleAlpha = 0.18f,
        customRipple = { center, progress ->
            // Heart-shaped ripple expanding from press point
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension * 0.3f * progress
                val alpha = (1f - progress).coerceIn(0f, 0.4f)
                drawCircle(
                    Pink400.copy(alpha = alpha),
                    radius = radius,
                    center = center
                )
            }
        }
    )
}

// Helper composable: bubbles rising from a point
@Composable
private fun SweetBubbleEffect(origin: Offset) {
    val particles = remember { List(6) {
        BubbleParticle(
            offsetX = Random.nextFloat() * 20f - 10f,
            speed = 0.5f + Random.nextFloat() * 0.5f,
            size = 3f + Random.nextFloat() * 4f
        )
    }}
    // Animate each bubble upward with fade
    // Uses LaunchedEffect + Animatable for each particle
}

private data class BubbleParticle(
    val offsetX: Float, val speed: Float, val size: Float
)

// Heart particle drawing helper
private fun androidx.compose.ui.graphics.drawscope.DrawScope
    .drawSweetHeartParticle(
    center: Offset, radius: Float, color: Color
) {
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(center.x, center.y + radius * 0.3f)
        cubicTo(center.x - radius, center.y - radius * 0.5f,
                center.x - radius * 0.5f, center.y - radius,
                center.x, center.y - radius * 0.3f)
        cubicTo(center.x + radius * 0.5f, center.y - radius,
                center.x + radius, center.y - radius * 0.5f,
                center.x, center.y + radius * 0.3f)
    }
    drawPath(path, color)
}
```

**Step 2: Build to verify**

Run: `./gradlew.bat assembleDebug`

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/SweetAnimationProvider.kt
git commit -m "feat(skin): add SweetAnimationProvider with bouncy springs and heart particles"
```

---

### Task 11: Gothic (哥特暗黑) animation system

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/GothicAnimationProvider.kt`

**Step 1: Create GothicAnimationProvider**

Gothic animation personality: slow, dramatic, heavy easing, dark shadow spread, crack/shatter effects.

```kotlin
package com.lolita.app.ui.theme.skin.animation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import kotlin.math.*

private val GothicPurple = Color(0xFF4A0E4E)
private val BloodRed = Color(0xFF8B0000)

class GothicAnimationProvider : SkinAnimationProvider {

    override val skinTransition = SkinTransitionSpec(
        durationMs = 600,
        overlay = { progress ->
            // Dark shadow spreading from edges inward, then receding
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                // Four shadow rectangles closing in from edges
                val inset = w * 0.5f * (1f - progress)
                drawRect(
                    Color.Black.copy(alpha = 0.7f * (1f - progress)),
                    topLeft = Offset.Zero,
                    size = androidx.compose.ui.geometry.Size(w, h)
                )
                // Crack lines radiating from center
                val crackCount = 8
                for (i in 0 until crackCount) {
                    val angle = (2 * PI * i / crackCount).toFloat()
                    val len = w * 0.4f * progress
                    drawLine(
                        BloodRed.copy(alpha = progress * 0.6f),
                        center,
                        Offset(
                            center.x + cos(angle) * len,
                            center.y + sin(angle) * len
                        ),
                        strokeWidth = 2f
                    )
                }
            }
        }
    )

    override val tabSwitchAnimation = TabSwitchAnimationSpec(
        indicatorAnimation = tween(
            durationMillis = 500,
            easing = CubicBezierEasing(0.2f, 0f, 0.1f, 1f)
        ),
        selectedEffect = { _ -> Modifier },
        particleEffect = { position ->
            GothicShadowDripEffect(position)
        }
    )

    override val cardAnimation = CardAnimationSpec(
        enterTransition = fadeIn(tween(500, easing = LinearOutSlowInEasing)) +
            slideInVertically(
                tween(600, easing = CubicBezierEasing(0.2f, 0f, 0.1f, 1f))
            ) { it / 2 },
        exitTransition = fadeOut(tween(400)) +
            slideOutVertically(tween(400)) { it / 3 },
        staggerDelayMs = 80,
        enterDurationMs = 600
    )

    override val interactionFeedback = InteractionFeedbackSpec(
        pressScale = 0.97f,  // subtle, heavy press
        rippleColor = GothicPurple,
        rippleAlpha = 0.25f,
        customRipple = { center, progress ->
            // Dark shadow pool expanding from press point
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension * 0.35f * progress
                val alpha = (1f - progress) * 0.5f
                drawCircle(
                    Brush.radialGradient(
                        listOf(
                            Color.Black.copy(alpha = alpha),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
            }
        }
    )
}

// Shadow drip effect: dark droplets falling from tap point
@Composable
private fun GothicShadowDripEffect(origin: Offset) {
    // 4-5 dark droplets animate downward with gravity easing
    // Each droplet: small circle that elongates as it falls
    // Uses LaunchedEffect + multiple Animatable instances
}
```

**Step 2: Build to verify**

Run: `./gradlew.bat assembleDebug`

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/GothicAnimationProvider.kt
git commit -m "feat(skin): add GothicAnimationProvider with dramatic shadows and crack effects"
```

---

### Task 12: Chinese (中华风韵) animation system

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/ChineseAnimationProvider.kt`

**Step 1: Create ChineseAnimationProvider**

Chinese animation personality: flowing, organic, ink wash spread, cloud drift, calligraphic brush reveal.

```kotlin
package com.lolita.app.ui.theme.skin.animation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import kotlin.math.*

private val Vermillion = Color(0xFFE34234)
private val InkBlack = Color(0xFF1A1A2E)

class ChineseAnimationProvider : SkinAnimationProvider {

    override val skinTransition = SkinTransitionSpec(
        durationMs = 450,
        overlay = { progress ->
            // Ink wash spread: circle of ink expanding from center,
            // revealing new skin underneath as ink fades
            Canvas(Modifier.fillMaxSize()) {
                val maxR = hypot(size.width, size.height) / 2f
                val radius = maxR * progress
                // Outer ink ring
                drawCircle(
                    Brush.radialGradient(
                        listOf(
                            Color.Transparent,
                            InkBlack.copy(alpha = 0.6f * (1f - progress)),
                            InkBlack.copy(alpha = 0.3f * (1f - progress)),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius.coerceAtLeast(1f)
                    ),
                    radius = radius,
                    center = center
                )
                // Scattered ink splash dots at the wavefront
                val dotCount = 10
                for (i in 0 until dotCount) {
                    val angle = (2 * PI * i / dotCount).toFloat()
                    val jitter = radius * 0.05f
                    val dx = cos(angle) * (radius + jitter)
                    val dy = sin(angle) * (radius + jitter)
                    drawCircle(
                        InkBlack.copy(alpha = 0.4f * (1f - progress)),
                        radius = size.minDimension * 0.008f,
                        center = Offset(center.x + dx, center.y + dy)
                    )
                }
            }
        }
    )

    override val tabSwitchAnimation = TabSwitchAnimationSpec(
        indicatorAnimation = tween(
            durationMillis = 400,
            easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
        ),
        selectedEffect = { _ -> Modifier },
        particleEffect = { position ->
            ChineseCloudDriftEffect(position)
        }
    )

    override val cardAnimation = CardAnimationSpec(
        enterTransition = fadeIn(tween(400, easing = LinearOutSlowInEasing)) +
            slideInHorizontally(
                tween(450, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f))
            ) { it / 3 },  // scroll-unroll feel: slide from right
        exitTransition = fadeOut(tween(300)) +
            slideOutHorizontally(tween(300)) { -it / 4 },
        staggerDelayMs = 70,
        enterDurationMs = 450
    )

    override val interactionFeedback = InteractionFeedbackSpec(
        pressScale = 0.95f,
        rippleColor = Vermillion,
        rippleAlpha = 0.15f,
        customRipple = { center, progress ->
            // Ink drop ripple: circle with feathered edge
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension * 0.3f * progress
                val alpha = (1f - progress) * 0.35f
                // Multiple concentric rings fading outward
                for (i in 0..2) {
                    val r = radius * (1f - i * 0.2f)
                    val a = alpha * (1f - i * 0.3f)
                    drawCircle(
                        InkBlack.copy(alpha = a),
                        radius = r,
                        center = center,
                        style = Stroke(width = r * 0.08f)
                    )
                }
            }
        }
    )
}

// Cloud drift effect: small 祥云 shapes floating upward
@Composable
private fun ChineseCloudDriftEffect(origin: Offset) {
    // 3-4 small cloud shapes animate upward + sideways
    // Each cloud: drawChineseCloud helper from icon package
    // Uses LaunchedEffect + Animatable for position + alpha
}
```

**Step 2: Build to verify**

Run: `./gradlew.bat assembleDebug`

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/ChineseAnimationProvider.kt
git commit -m "feat(skin): add ChineseAnimationProvider with ink wash spread and cloud drift"
```

---

### Task 13: Classic (经典优雅) animation system

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/ClassicAnimationProvider.kt`

**Step 1: Create ClassicAnimationProvider**

Classic animation personality: elegant, refined, measured timing, gold frame reveals, page-turn transitions.

```kotlin
package com.lolita.app.ui.theme.skin.animation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp

private val GoldAccent = Color(0xFFD4AF37)
private val WineRed = Color(0xFF722F37)

class ClassicAnimationProvider : SkinAnimationProvider {

    override val skinTransition = SkinTransitionSpec(
        durationMs = 400,
        overlay = { progress ->
            // Gold frame expanding from center, revealing new skin
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                val frameW = w * progress
                val frameH = h * progress
                val left = (w - frameW) / 2f
                val top = (h - frameH) / 2f
                // Outer gold border
                drawRect(
                    GoldAccent.copy(alpha = (1f - progress) * 0.8f),
                    topLeft = Offset(left, top),
                    size = Size(frameW, frameH),
                    style = Stroke(width = 4f)
                )
                // Inner decorative border
                val inset = 6f
                drawRect(
                    GoldAccent.copy(alpha = (1f - progress) * 0.5f),
                    topLeft = Offset(left + inset, top + inset),
                    size = Size(frameW - inset * 2, frameH - inset * 2),
                    style = Stroke(width = 1.5f)
                )
                // Corner scrollwork flourishes at the 4 corners
                // (simplified as small arcs)
            }
        }
    )

    override val tabSwitchAnimation = TabSwitchAnimationSpec(
        indicatorAnimation = tween(
            durationMillis = 380,
            easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
        ),
        selectedEffect = { _ -> Modifier },
        particleEffect = { position ->
            ClassicGoldSparkleEffect(position)
        }
    )

    override val cardAnimation = CardAnimationSpec(
        enterTransition = fadeIn(tween(350, easing = FastOutSlowInEasing)) +
            expandVertically(
                tween(400, easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)),
                expandFrom = Alignment.Top
            ),  // page-turn reveal from top
        exitTransition = fadeOut(tween(250)) +
            shrinkVertically(tween(300), shrinkTowards = Alignment.Top),
        staggerDelayMs = 55,
        enterDurationMs = 400
    )

    override val interactionFeedback = InteractionFeedbackSpec(
        pressScale = 0.97f,  // subtle, dignified
        rippleColor = GoldAccent,
        rippleAlpha = 0.15f,
        customRipple = { center, progress ->
            // Gold ring expanding with fade
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension * 0.25f * progress
                val alpha = (1f - progress) * 0.4f
                drawCircle(
                    GoldAccent.copy(alpha = alpha),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 2.5f)
                )
                // Second thinner ring slightly behind
                val r2 = radius * 0.85f
                drawCircle(
                    GoldAccent.copy(alpha = alpha * 0.5f),
                    radius = r2,
                    center = center,
                    style = Stroke(width = 1f)
                )
            }
        }
    )
}

// Gold sparkle effect: small gold dots twinkling around tap point
@Composable
private fun ClassicGoldSparkleEffect(origin: Offset) {
    // 5-6 tiny gold circles appear at random offsets around origin
    // Each fades in quickly then fades out slowly
    // Uses LaunchedEffect + delay + Animatable for alpha
}
```

**Step 2: Build to verify**

Run: `./gradlew.bat assembleDebug`

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/ClassicAnimationProvider.kt
git commit -m "feat(skin): add ClassicAnimationProvider with gold frame expand and page-turn"
```

---

## Phase 4: Skin-aware Components

> Refactor shared UI components to consume `SkinAnimationProvider` and `SkinIconProvider` from `LolitaSkin.current`.
> New composables live under `ui/theme/skin/component/`.

### Task 14: SkinClickable modifier + SkinRippleIndication

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/component/SkinClickable.kt`

**Step 1: Create SkinClickable**

A drop-in replacement for `Modifier.clickable` that applies skin-specific press scale and custom ripple from `InteractionFeedbackSpec`.

```kotlin
package com.lolita.app.ui.theme.skin.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import com.lolita.app.ui.theme.LolitaSkin

@Composable
fun Modifier.skinClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val skin = LolitaSkin.current
    val feedback = skin.animations.interactionFeedback
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) feedback.pressScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ), label = "skinPressScale"
    )

    return this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = SkinRippleIndication(feedback),
            enabled = enabled,
            onClick = onClick
        )
}

// Custom Indication that draws skin-specific ripple
// Uses feedback.rippleColor, feedback.rippleAlpha,
// and feedback.customRipple if non-null
class SkinRippleIndication(
    private val feedback:
        com.lolita.app.ui.theme.skin.animation.InteractionFeedbackSpec
) : androidx.compose.foundation.Indication {
    @Composable
    override fun rememberUpdatedInstance(
        interactionSource: MutableInteractionSource
    ): androidx.compose.foundation.IndicationInstance {
        // Track press interactions, animate ripple progress 0→1
        // On press: record press position, animate progress
        // Draw: if customRipple != null, invoke it;
        //        else draw standard circle ripple with rippleColor/Alpha
        return remember(interactionSource) {
            SkinRippleIndicationInstance(feedback, interactionSource)
        }
    }
}
```

**Step 2: Build to verify**

Run: `./gradlew.bat assembleDebug`

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/component/SkinClickable.kt
git commit -m "feat(skin): add skinClickable modifier with per-skin press scale and ripple"
```

---

### Task 15: SkinTransitionOverlay (full-screen skin switch transition)

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/component/SkinTransitionOverlay.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/MainActivity.kt`

**Step 1: Create SkinTransitionOverlay composable**

A full-screen overlay that plays the skin-specific transition animation when the user switches skins. Sits on top of all content in `MainActivity`.

```kotlin
package com.lolita.app.ui.theme.skin.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.SkinType

@Composable
fun SkinTransitionOverlay(
    currentSkin: SkinType,
    onTransitionComplete: () -> Unit = {}
) {
    val skin = LolitaSkin.current
    val spec = skin.animations.skinTransition
    var isAnimating by remember { mutableStateOf(false) }
    var previousSkin by remember { mutableStateOf(currentSkin) }

    val progress = remember { Animatable(0f) }

    LaunchedEffect(currentSkin) {
        if (currentSkin != previousSkin) {
            isAnimating = true
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = spec.durationMs,
                    easing = FastOutSlowInEasing
                )
            )
            isAnimating = false
            previousSkin = currentSkin
            onTransitionComplete()
        }
    }

    if (isAnimating) {
        Box(Modifier.fillMaxSize()) {
            spec.overlay(progress.value)
        }
    }
}
```

**Step 2: Wire into MainActivity**

In `MainActivity.kt`, wrap the existing content with `SkinTransitionOverlay`:

```kotlin
// Inside setContent { LolitaTheme { ... } }
Box {
    // existing Scaffold / NavHost content
    SkinTransitionOverlay(currentSkin = currentSkinType)
}
```

**Step 3: Build to verify**

Run: `./gradlew.bat assembleDebug`

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/component/SkinTransitionOverlay.kt \
       app/src/main/java/com/lolita/app/ui/MainActivity.kt
git commit -m "feat(skin): add SkinTransitionOverlay with per-skin full-screen transition"
```

---

### Task 16: Refactor NavigationBar bottom bar

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt`

**Step 1: Replace Material icons with SkinIcon in bottom navigation**

In the bottom `NavigationBar` inside `LolitaNavHost`, replace all `Icon(Icons.Filled.xxx, ...)` calls with `SkinIcon(IconKey.xxx, ...)`:

```kotlin
// Before:
Icon(Icons.Filled.Home, contentDescription = "服饰")

// After:
SkinIcon(IconKey.Home, tint = if (selected) selectedColor else unselectedColor)
```

Replace all 5 navigation icons: Home, Wishlist (Favorite), Outfit (DateRange), Stats (Info), Settings.

**Step 2: Add tab switch animation from SkinAnimationProvider**

Read `LolitaSkin.current.animations.tabSwitchAnimation` and apply:
- Use `tabSwitchAnimation.indicatorAnimation` as the `AnimationSpec` for the tab indicator position
- Wrap each nav item icon with `tabSwitchAnimation.selectedEffect(selected)` modifier
- On tab selection, if `tabSwitchAnimation.particleEffect` is non-null, trigger it at the icon's position

```kotlin
val skin = LolitaSkin.current
val tabAnim = skin.animations.tabSwitchAnimation

NavigationBarItem(
    selected = selected,
    onClick = {
        onTabSelected(index)
        // Trigger particle effect if available
    },
    icon = {
        Box(modifier = tabAnim.selectedEffect(selected)) {
            SkinIcon(key = tabIconKey, tint = iconTint)
        }
    },
    // ...
)
```

**Step 3: Build to verify**

Run: `./gradlew.bat assembleDebug`

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt
git commit -m "feat(skin): replace bottom nav icons with SkinIcon + add tab switch animations"
```

---

### Task 17: Refactor GradientTopAppBar, LolitaCard, SwipeToDeleteContainer

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/common/GradientTopAppBar.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/common/LolitaCard.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/common/SwipeToDeleteContainer.kt`

**Step 1: Refactor GradientTopAppBar — Canvas decorations per skin**

Currently `GradientTopAppBar` renders the `topBarDecoration` string (✿/✝/☁/♠) as `Text`. Replace with Canvas-drawn decorations per skin:

- Sweet: Canvas-drawn five-petal flower (✿) with gradient fill, gently rotating animation
- Gothic: Canvas-drawn ornate cross (✝) with thin lines, subtle pulse animation
- Chinese: Canvas-drawn auspicious cloud (☁) with brush-stroke style, slow drift animation
- Classic: Canvas-drawn spade (♠) with gold outline, elegant fade-in animation

```kotlin
// In GradientTopAppBar.kt, replace:
Text(text = skin.topBarDecoration, ...)

// With:
val icons = skin.icons
Box(modifier = Modifier.size(16.dp)) {
    // Delegate to a new @Composable that draws the decoration
    // based on current skin type via Canvas
    SkinTopBarDecoration(skinType = currentSkinType)
}
```

Also replace any `Icon()` calls in `navigationIcon` and `actions` slots that use Material icons — callers will be updated in Phase 5, but ensure the component itself uses `SkinIcon` for its own internal icons (e.g., back arrow default).

**Step 2: Refactor LolitaCard — card enter/exit animations**

Add an `index` parameter to `LolitaCard` for staggered animation. Read `CardAnimationSpec` from `LolitaSkin.current.animations.cardAnimation` and wrap content in `AnimatedVisibility`:

```kotlin
@Composable
fun LolitaCard(
    modifier: Modifier = Modifier,
    index: Int = 0,  // NEW: for stagger delay
    content: @Composable ColumnScope.() -> Unit
) {
    val skin = LolitaSkin.current
    val cardAnim = skin.animations.cardAnimation

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(cardAnim.staggerDelayMs.toLong() * index)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = cardAnim.enterTransition,
        exit = cardAnim.exitTransition
    ) {
        Card(
            modifier = modifier,
            shape = RoundedCornerShape(skin.cardCornerRadius),
            // ... existing card styling
        ) {
            Column(content = content)
        }
    }
}
```

**Step 3: Refactor SwipeToDeleteContainer — skin-aware delete background**

Replace the hardcoded red delete background with skin-aware styling:

- Sweet: Soft pink-red gradient background, heart-shaped delete icon via `SkinIcon(IconKey.Delete)`
- Gothic: Dark purple-black background, cross-styled delete icon, thorn border on swipe edge
- Chinese: Ink-wash red background, brush-stroke delete icon, ink splash at swipe edge
- Classic: Wine-red background with gold border accent, spade-styled delete icon

```kotlin
// In SwipeToDeleteContainer.kt:
val skin = LolitaSkin.current

// Replace Icon(Icons.Default.Delete, ...) with:
SkinIcon(IconKey.Delete, tint = Color.White)

// Replace background color with skin-aware gradient:
val bgBrush = when (skin) {
    // Read from skin config or use skin-specific delete background colors
    else -> Brush.horizontalGradient(
        listOf(skin.accentColor.copy(alpha = 0.8f), skin.accentColor)
    )
}
```

**Step 4: Build to verify**

Run: `./gradlew.bat assembleDebug`

**Step 5: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/common/GradientTopAppBar.kt \
       app/src/main/java/com/lolita/app/ui/screen/common/LolitaCard.kt \
       app/src/main/java/com/lolita/app/ui/screen/common/SwipeToDeleteContainer.kt
git commit -m "feat(skin): refactor GradientTopAppBar, LolitaCard, SwipeToDeleteContainer to be skin-aware"
```

---

## Phase 5: Global Icon Replacement

> Systematically replace every `Icon(Icons.xxx, ...)` call across all screens with `SkinIcon(IconKey.xxx, ...)`.
> Each task covers a group of related screens. Use project-wide search for `Icons.` to ensure nothing is missed.
> Import: `com.lolita.app.ui.theme.skin.icon.SkinIcon` and `com.lolita.app.ui.theme.skin.icon.IconKey`

### Task 18: Replace Icon() calls in item screens

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemDetailScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemEditScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/FilteredItemListScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/WishlistScreen.kt`

**Step 1: Replace all Icon() calls in each file**

For each file, search for `Icon(Icons.` and replace with the corresponding `SkinIcon(IconKey.xxx)`. Preserve the `modifier` and `tint` parameters. Remove unused `Icons.*` imports, add `SkinIcon` and `IconKey` imports.

Replacement pattern:
```kotlin
// Before:
Icon(Icons.Default.Add, contentDescription = "添加", modifier = Modifier.size(24.dp))
// After:
SkinIcon(IconKey.Add, modifier = Modifier.size(24.dp))

// Before:
Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
// After:
SkinIcon(IconKey.ArrowBack, tint = Color.White)
```

Expected replacements per file:

**ItemListScreen.kt:**
- `Icons.Default.Add` → `IconKey.Add` (FAB)
- `Icons.Default.Search` → `IconKey.Search` (search bar)
- `Icons.Default.Close` → `IconKey.Close` (clear search)
- `Icons.Default.FilterList` → `IconKey.FilterList` (filter button)
- `Icons.AutoMirrored.Filled.Sort` → `IconKey.Sort` (sort button)
- `Icons.Default.ExpandMore` / `ExpandLess` → `IconKey.ExpandMore` / `ExpandLess`
- `Icons.Filled.Star` → `IconKey.Star` (priority indicator)

**ItemDetailScreen.kt:**
- `Icons.AutoMirrored.Filled.ArrowBack` → `IconKey.ArrowBack`
- `Icons.Default.Edit` → `IconKey.Edit`
- `Icons.Default.Delete` → `IconKey.Delete`
- `Icons.Default.Share` → `IconKey.Share`
- `Icons.Default.ContentCopy` → `IconKey.ContentCopy`
- `Icons.Default.Image` → `IconKey.Image`
- `Icons.Default.Link` → `IconKey.Link`
- `Icons.Default.AttachMoney` → `IconKey.AttachMoney`
- `Icons.Default.CalendarMonth` → `IconKey.CalendarMonth`
- `Icons.Default.Category` → `IconKey.Category`
- `Icons.Default.Palette` → `IconKey.Palette`

**ItemEditScreen.kt:**
- `Icons.AutoMirrored.Filled.ArrowBack` → `IconKey.ArrowBack`
- `Icons.Default.Check` → `IconKey.Save` (save button)
- `Icons.Default.Camera` → `IconKey.Camera`
- `Icons.Default.Image` → `IconKey.Image`
- `Icons.Default.AddPhotoAlternate` → `IconKey.AddPhoto`
- `Icons.Default.Close` → `IconKey.Close`
- `Icons.Default.ArrowDropDown` → `IconKey.ArrowDropDown`
- `Icons.Default.Link` → `IconKey.Link`
- `Icons.Default.LinkOff` → `IconKey.LinkOff`

**FilteredItemListScreen.kt:**
- `Icons.AutoMirrored.Filled.ArrowBack` → `IconKey.ArrowBack`
- `Icons.Default.Search` → `IconKey.Search`
- `Icons.Default.Close` → `IconKey.Close`
- `Icons.AutoMirrored.Filled.Sort` → `IconKey.Sort`

**WishlistScreen.kt:**
- `Icons.Default.Add` → `IconKey.Add`
- `Icons.Default.Delete` → `IconKey.Delete`
- `Icons.Filled.Star` / `Icons.Default.StarBorder` → `IconKey.Star` / `IconKey.StarBorder`
- `Icons.Default.OpenInNew` → `IconKey.OpenInNew`
- `Icons.Default.CheckCircle` → `IconKey.CheckCircle`

**Step 2: Build to verify**

Run: `./gradlew.bat assembleDebug`

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/
git commit -m "feat(skin): replace all Icon() calls with SkinIcon() in item screens"
```

---

### Task 19: Replace Icon() calls in outfit/coordinate/price screens

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/outfit/OutfitLogListScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/outfit/OutfitLogDetailScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/outfit/OutfitLogEditScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateListScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateDetailScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateEditScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/price/PriceManageScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/price/PaymentManageScreen.kt`

**Step 1: Replace all Icon() calls in each file**

Same replacement pattern as Task 18. Search each file for `Icon(Icons.` and replace.

Expected replacements per file:

**OutfitLogListScreen.kt:**
- `Icons.Default.Add` → `IconKey.Add`
- `Icons.Default.Delete` → `IconKey.Delete`
- `Icons.Default.CalendarMonth` → `IconKey.CalendarMonth`
- `Icons.Default.Image` → `IconKey.Image`

**OutfitLogDetailScreen.kt:**
- `Icons.AutoMirrored.Filled.ArrowBack` → `IconKey.ArrowBack`
- `Icons.Default.Edit` → `IconKey.Edit`
- `Icons.Default.Delete` → `IconKey.Delete`
- `Icons.Default.Share` → `IconKey.Share`
- `Icons.Default.Image` → `IconKey.Image`
- `Icons.Default.CalendarMonth` → `IconKey.CalendarMonth`

**OutfitLogEditScreen.kt:**
- `Icons.AutoMirrored.Filled.ArrowBack` → `IconKey.ArrowBack`
- `Icons.Default.Check` → `IconKey.Save`
- `Icons.Default.Add` → `IconKey.Add`
- `Icons.Default.Close` → `IconKey.Close`
- `Icons.Default.Camera` → `IconKey.Camera`
- `Icons.Default.Image` → `IconKey.Image`
- `Icons.Default.CalendarMonth` → `IconKey.CalendarMonth`

**CoordinateListScreen.kt:**
- `Icons.Default.Add` → `IconKey.Add`
- `Icons.Default.Search` → `IconKey.Search`
- `Icons.Default.Close` → `IconKey.Close`

**CoordinateDetailScreen.kt:**
- `Icons.AutoMirrored.Filled.ArrowBack` → `IconKey.ArrowBack`
- `Icons.Default.Edit` → `IconKey.Edit`
- `Icons.Default.Delete` → `IconKey.Delete`
- `Icons.Default.Image` → `IconKey.Image`

**CoordinateEditScreen.kt:**
- `Icons.AutoMirrored.Filled.ArrowBack` → `IconKey.ArrowBack`
- `Icons.Default.Check` → `IconKey.Save`
- `Icons.Default.Add` → `IconKey.Add`
- `Icons.Default.Close` → `IconKey.Close`
- `Icons.Default.Camera` → `IconKey.Camera`
- `Icons.Default.Image` → `IconKey.Image`

**PriceManageScreen.kt:**
- `Icons.AutoMirrored.Filled.ArrowBack` → `IconKey.ArrowBack`
- `Icons.Default.Add` → `IconKey.Add`
- `Icons.Default.Edit` → `IconKey.Edit`
- `Icons.Default.Delete` → `IconKey.Delete`
- `Icons.Default.AttachMoney` → `IconKey.AttachMoney`
- `Icons.Default.ExpandMore` / `ExpandLess` → `IconKey.ExpandMore` / `ExpandLess`

**PaymentManageScreen.kt:**
- `Icons.AutoMirrored.Filled.ArrowBack` → `IconKey.ArrowBack`
- `Icons.Default.Add` → `IconKey.Add`
- `Icons.Default.Edit` → `IconKey.Edit`
- `Icons.Default.Delete` → `IconKey.Delete`
- `Icons.Default.Notifications` → `IconKey.Notifications`
- `Icons.Default.CalendarMonth` → `IconKey.CalendarMonth`
- `Icons.Default.CheckCircle` → `IconKey.CheckCircle`
- `Icons.Default.Warning` → `IconKey.Warning`

**Step 2: Build to verify**

Run: `./gradlew.bat assembleDebug`

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/outfit/ \
       app/src/main/java/com/lolita/app/ui/screen/coordinate/ \
       app/src/main/java/com/lolita/app/ui/screen/price/
git commit -m "feat(skin): replace all Icon() calls with SkinIcon() in outfit/coordinate/price screens"
```

---

### Task 20: Replace Icon() calls in settings/stats/other screens + ThemeSelectScreen enhancement

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/settings/ThemeSelectScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/settings/BrandManageScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/settings/CategoryManageScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/settings/StyleManageScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/settings/SeasonManageScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/settings/BackupRestoreScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/stats/StatsPageScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/import/TaobaoImportScreen.kt`

**Step 1: Replace all Icon() calls in settings screens**

Same replacement pattern as Tasks 18-19. Search each file for `Icon(Icons.` and replace.

**SettingsScreen.kt:**
- `Icons.AutoMirrored.Filled.ArrowForward` → `IconKey.ArrowForward` (list item trailing)
- `Icons.Default.Palette` → `IconKey.Palette` (theme entry)
- `Icons.Default.Category` → `IconKey.Category` (category manage entry)
- `Icons.Default.Refresh` → `IconKey.Refresh` (backup entry)
- `Icons.Default.FileOpen` → `IconKey.FileOpen` (import entry)
- `Icons.Default.Info` → `IconKey.Info` (about entry)

**BrandManageScreen.kt / CategoryManageScreen.kt / StyleManageScreen.kt / SeasonManageScreen.kt:**
- `Icons.AutoMirrored.Filled.ArrowBack` → `IconKey.ArrowBack`
- `Icons.Default.Add` → `IconKey.Add`
- `Icons.Default.Edit` → `IconKey.Edit`
- `Icons.Default.Delete` → `IconKey.Delete`
- `Icons.Default.Search` → `IconKey.Search`
- `Icons.Default.Close` → `IconKey.Close`
- `Icons.Default.Check` → `IconKey.Save` (confirm in dialogs)

**BackupRestoreScreen.kt:**
- `Icons.AutoMirrored.Filled.ArrowBack` → `IconKey.ArrowBack`
- `Icons.Default.Share` → `IconKey.Share` (export)
- `Icons.Default.FileOpen` → `IconKey.FileOpen` (import)
- `Icons.Default.Warning` → `IconKey.Warning` (overwrite warning)
- `Icons.Default.CheckCircle` → `IconKey.CheckCircle` (success)
- `Icons.Default.Error` → `IconKey.Error` (failure)

**StatsPageScreen.kt:**
- `Icons.Default.ExpandMore` / `ExpandLess` → `IconKey.ExpandMore` / `ExpandLess`
- `Icons.Default.Info` → `IconKey.Info`
- `Icons.Default.AttachMoney` → `IconKey.AttachMoney`
- `Icons.Default.Category` → `IconKey.Category`
- `Icons.Default.Palette` → `IconKey.Palette`
- `Icons.Default.CalendarMonth` → `IconKey.CalendarMonth`

**TaobaoImportScreen.kt:**
- `Icons.AutoMirrored.Filled.ArrowBack` → `IconKey.ArrowBack`
- `Icons.Default.FileOpen` → `IconKey.FileOpen`
- `Icons.Default.CheckCircle` → `IconKey.CheckCircle`
- `Icons.Default.Warning` → `IconKey.Warning`
- `Icons.Default.Error` → `IconKey.Error`
- `Icons.Default.Refresh` → `IconKey.Refresh`
- `Icons.Default.Visibility` → `IconKey.Visibility`

**Step 2: Enhance ThemeSelectScreen with skin preview**

In `ThemeSelectScreen.kt`, enhance the skin selection cards to show a live preview of each skin's icon set and animation style. Each skin card should display:

1. A row of 5 sample Canvas icons from that skin's `SkinIconProvider` (Home, Star, Edit, ArrowBack, CheckCircle)
2. The skin's decoration drawn via Canvas (flower/cross/cloud/spade)
3. A mini animation preview: tap the card to see the skin's `interactionFeedback` ripple

```kotlin
@Composable
private fun SkinPreviewCard(
    skinType: SkinType,
    skinConfig: LolitaSkinConfig,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val icons = skinConfig.icons
    val feedback = skinConfig.animations.interactionFeedback

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .skinClickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                skinConfig.accentColor.copy(alpha = 0.1f)
            else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) BorderStroke(2.dp, skinConfig.accentColor) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Skin name + decoration
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(skinType.displayName, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                // Canvas-drawn decoration preview
                SkinTopBarDecoration(skinType)
            }
            Spacer(Modifier.height(12.dp))
            // Icon preview row
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                icons.navigation.Home(Modifier.size(20.dp), skinConfig.accentColor)
                icons.content.Star(Modifier.size(20.dp), skinConfig.accentColor)
                icons.action.Edit(Modifier.size(20.dp), skinConfig.accentColor)
                icons.arrow.ArrowBack(Modifier.size(20.dp), skinConfig.accentColor)
                icons.status.CheckCircle(Modifier.size(20.dp), skinConfig.accentColor)
            }
        }
    }
}
```

**Step 3: Final sweep — verify zero remaining `Icon(Icons.` calls**

Run a project-wide search to confirm no `Icon(Icons.` calls remain:

```bash
grep -rn "Icon(Icons\." app/src/main/java/com/lolita/app/ui/
```

Expected: zero results. If any remain, replace them following the same pattern.

**Step 4: Build to verify**

Run: `./gradlew.bat assembleDebug`

**Step 5: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/settings/ \
       app/src/main/java/com/lolita/app/ui/screen/stats/ \
       app/src/main/java/com/lolita/app/ui/screen/import/
git commit -m "feat(skin): replace all Icon() calls in settings/stats/import screens + enhance ThemeSelectScreen"
```

---

## Phase 6: Final Wiring & Verification

### Task 21: Wire skin-specific providers into SkinConfigs

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/SkinConfigs.kt`

**Step 1: Replace BaseSkinIconProvider/DefaultAnimationProvider with skin-specific implementations**

Now that all 4 icon providers and 4 animation providers exist, update each skin config to use its own:

```kotlin
import com.lolita.app.ui.theme.skin.icon.*
import com.lolita.app.ui.theme.skin.animation.*

fun defaultSkinConfig(): LolitaSkinConfig = LolitaSkinConfig(
    // ... existing fields ...
    icons = SweetIconProvider(),
    animations = SweetAnimationProvider(),
)

fun gothicSkinConfig(): LolitaSkinConfig = LolitaSkinConfig(
    // ... existing fields ...
    icons = GothicIconProvider(),
    animations = GothicAnimationProvider(),
)

fun chineseSkinConfig(): LolitaSkinConfig = LolitaSkinConfig(
    // ... existing fields ...
    icons = ChineseIconProvider(),
    animations = ChineseAnimationProvider(),
)

fun classicSkinConfig(): LolitaSkinConfig = LolitaSkinConfig(
    // ... existing fields ...
    icons = ClassicIconProvider(),
    animations = ClassicAnimationProvider(),
)
```

**Step 2: Build to verify**

Run: `./gradlew.bat assembleDebug`

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/SkinConfigs.kt
git commit -m "feat(skin): wire skin-specific icon and animation providers into all 4 SkinConfigs"
```

---

### Task 22: Clean up unused Material icon imports + final build verification

**Files:**
- Modify: All screen files touched in Tasks 18-20

**Step 1: Remove unused Material icon imports**

Run a project-wide search for unused imports:

```bash
grep -rn "import androidx.compose.material.icons" app/src/main/java/com/lolita/app/ui/screen/
```

For each file, remove any `import androidx.compose.material.icons.*` lines that are no longer referenced. The only files that should still import Material icons are:
- `BaseSkinIconProvider.kt` (fallback implementations)
- The 4 skin icon provider files (if they use Material icons as starting points — they shouldn't after full Canvas implementation)

**Step 2: Full clean build**

Run: `./gradlew.bat clean assembleDebug`

Expected: BUILD SUCCESSFUL with zero warnings related to unused imports.

**Step 3: Manual verification checklist**

Switch between all 4 skins in ThemeSelectScreen and verify:
- [ ] All 5 bottom nav icons render as Canvas-drawn (not Material fallback)
- [ ] Top bar decoration renders as Canvas-drawn per skin
- [ ] Skin transition overlay plays on skin switch
- [ ] Tab switch animation differs per skin
- [ ] Card enter animations differ per skin (bounce vs slide vs ink vs page-turn)
- [ ] Press feedback differs per skin (scale + ripple style)
- [ ] SwipeToDeleteContainer background matches skin
- [ ] No Material icon fallbacks visible anywhere (all 45 replaced)
- [ ] ThemeSelectScreen shows icon preview row per skin card

**Step 4: Commit**

```bash
git add -A
git commit -m "chore(skin): clean up unused Material icon imports, final verification pass"
```

---

## Summary

| Phase | Tasks | New Files | Modified Files | Description |
|---|---|---|---|---|
| 1: Infrastructure | 1-5 | 6 | 2 | Interfaces, enums, base provider, config wiring |
| 2: Icons | 6-9 | 4 | 0 | 180 Canvas-drawn icons (45 per skin) |
| 3: Animations | 10-13 | 4 | 0 | 16 animation specs (4 per skin) |
| 4: Components | 14-17 | 2 | 4 | Skin-aware clickable, overlay, nav bar, shared components |
| 5: Replacement | 18-20 | 0 | ~20 | Global Icon() → SkinIcon() replacement |
| 6: Wiring | 21-22 | 0 | ~21 | Final provider wiring + cleanup |
| **Total** | **22 tasks** | **16 new** | **~47 modified** | **180 icons + 16 animations + full component overhaul** |

### New file tree

```
app/src/main/java/com/lolita/app/ui/theme/skin/
├── icon/
│   ├── SkinIconProvider.kt          (Task 1)
│   ├── IconKey.kt                   (Task 3)
│   ├── SkinIcon.kt                  (Task 3)
│   ├── BaseSkinIconProvider.kt      (Task 4)
│   ├── SweetIconProvider.kt         (Task 6)
│   ├── GothicIconProvider.kt        (Task 7)
│   ├── ChineseIconProvider.kt       (Task 8)
│   └── ClassicIconProvider.kt       (Task 9)
├── animation/
│   ├── SkinAnimationProvider.kt     (Task 2)
│   ├── DefaultAnimationProvider.kt  (Task 5)
│   ├── SweetAnimationProvider.kt    (Task 10)
│   ├── GothicAnimationProvider.kt   (Task 11)
│   ├── ChineseAnimationProvider.kt  (Task 12)
│   └── ClassicAnimationProvider.kt  (Task 13)
└── component/
    ├── SkinClickable.kt             (Task 14)
    └── SkinTransitionOverlay.kt     (Task 15)
```

### Execution order

Tasks must be executed in order 1→22. Each task builds on the previous. The build verification step after each task ensures incremental correctness.

### Risk notes

- **Icon rendering performance:** 45 Canvas-drawn icons per skin means 180 `@Composable` functions with `Canvas` calls. Keep draw operations simple (< 20 draw calls per icon). Avoid allocations inside `DrawScope`.
- **Animation memory:** Particle effects (hearts, bubbles, ink splashes) should use a fixed pool size (max 12 particles) and reuse `Animatable` instances.
- **Incremental migration safety:** `BaseSkinIconProvider` ensures any icon not yet overridden still renders via Material fallback. This means the app is functional after every task, not just at the end.
