# UI Visual Refinement — Universal Base Layer Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add visual refinement tokens to the skin system and upgrade shared components (LolitaCard, GalleryCard) and ItemDetailScreen to use them, giving all 7 skins immediate visual improvement.

**Architecture:** Extend `LolitaSkinConfig` data class with style tokens (elevation, border, accent), then update 7 skin factory functions with per-skin values, upgrade `LolitaCard`/`GalleryCard` to consume tokens, create `SectionHeader` and `ImageFrame` composables, and refactor `ItemDetailScreen` as the reference implementation.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, existing LolitaSkin composition local system

---

### Task 1: Add Style Tokens to LolitaSkinConfig

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/LolitaSkinConfig.kt`

**Step 1: Add new properties to LolitaSkinConfig data class**

Add these imports at the top:
```kotlin
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
```

Add these properties after `topBarDecorationAlpha`:
```kotlin
val cardElevation: Dp,
val cardBorderStroke: BorderStroke?,
val imageFrameElevation: Dp,
val imageFrameStroke: BorderStroke?,
val imageFramePadding: Dp,
val sectionAccentColor: Color,
val sectionAccentColorDark: Color,
val sectionAccentWidth: Dp,
val sectionDividerColor: Color,
val sectionDividerColorDark: Color,
val sectionDividerHeight: Dp,
```

**Step 2: Build to verify compilation fails (expected)**

Run: `./gradlew.bat assembleDebug`
Expected: Compilation errors in SkinConfigs.kt (missing constructor args)

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/LolitaSkinConfig.kt
git commit -m "feat: add visual refinement style tokens to LolitaSkinConfig"
```

---

### Task 2: Populate Style Tokens in All 7 Skin Factory Functions

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/SkinConfigs.kt`

**Step 1: Add BorderStroke import**

Add at top:
```kotlin
import androidx.compose.foundation.BorderStroke
```

**Step 2: Add token values to defaultSkinConfig()**

After `animations = SweetAnimationProvider(),` add:
```kotlin
cardElevation = 1.dp,
cardBorderStroke = null,
imageFrameElevation = 2.dp,
imageFrameStroke = null,
imageFramePadding = 0.dp,
sectionAccentColor = Pink400,
sectionAccentColorDark = Pink400,
sectionAccentWidth = 3.dp,
sectionDividerColor = Pink200,
sectionDividerColorDark = Color(0xFF3A3A3A),
sectionDividerHeight = 1.dp,
```

**Step 3: Add token values to gothicSkinConfig()**

After `animations = GothicAnimationProvider(),` add:
```kotlin
cardElevation = 2.dp,
cardBorderStroke = BorderStroke(0.5.dp, Color(0xFF4A0E4E)),
imageFrameElevation = 3.dp,
imageFrameStroke = BorderStroke(0.5.dp, Color(0xFF9B59B6).copy(alpha = 0.4f)),
imageFramePadding = 2.dp,
sectionAccentColor = purple,
sectionAccentColorDark = brightPurple,
sectionAccentWidth = 3.dp,
sectionDividerColor = Color(0xFFD0C0D0),
sectionDividerColorDark = Color(0xFF3A3A50),
sectionDividerHeight = 1.dp,
```

**Step 4: Add token values to chineseSkinConfig()**

After `animations = ChineseAnimationProvider(),` add:
```kotlin
cardElevation = 1.dp,
cardBorderStroke = BorderStroke(0.5.dp, vermillion),
imageFrameElevation = 2.dp,
imageFrameStroke = BorderStroke(0.5.dp, vermillion.copy(alpha = 0.6f)),
imageFramePadding = 2.dp,
sectionAccentColor = vermillion,
sectionAccentColorDark = Color(0xFFE85050),
sectionAccentWidth = 3.dp,
sectionDividerColor = Color(0xFFE0D0B0),
sectionDividerColorDark = Color(0xFF3A3028),
sectionDividerHeight = 1.dp,
```

**Step 5: Add token values to classicSkinConfig()**

After `animations = ClassicAnimationProvider(),` add:
```kotlin
cardElevation = 2.dp,
cardBorderStroke = BorderStroke(0.5.dp, Color(0xFFB8860B)),
imageFrameElevation = 3.dp,
imageFrameStroke = BorderStroke(0.5.dp, Color(0xFFDAA520).copy(alpha = 0.5f)),
imageFramePadding = 2.dp,
sectionAccentColor = wine,
sectionAccentColorDark = Color(0xFFA05060),
sectionAccentWidth = 3.dp,
sectionDividerColor = Color(0xFFD0C0B0),
sectionDividerColorDark = Color(0xFF3A3030),
sectionDividerHeight = 1.dp,
```

**Step 6: Add token values to navySkinConfig()**

After `animations = NavyAnimationProvider(),` add:
```kotlin
cardElevation = 1.dp,
cardBorderStroke = null,
imageFrameElevation = 2.dp,
imageFrameStroke = null,
imageFramePadding = 0.dp,
sectionAccentColor = skyBlue,
sectionAccentColorDark = lightSkyBlue,
sectionAccentWidth = 3.dp,
sectionDividerColor = Color(0xFFBDD8EA),
sectionDividerColorDark = Color(0xFF253A50),
sectionDividerHeight = 1.dp,
```

**Step 7: Add token values to countrySkinConfig()**

After `animations = CountryAnimationProvider(),` add:
```kotlin
cardElevation = 1.dp,
cardBorderStroke = null,
imageFrameElevation = 2.dp,
imageFrameStroke = null,
imageFramePadding = 0.dp,
sectionAccentColor = berry,
sectionAccentColorDark = Color(0xFFD78678),
sectionAccentWidth = 3.dp,
sectionDividerColor = Color(0xFFE6D7BF),
sectionDividerColorDark = Color(0xFF394235),
sectionDividerHeight = 1.dp,
```

**Step 8: Add token values to victorianSkinConfig()**

After `animations = VictorianAnimationProvider(),` add:
```kotlin
cardElevation = 3.dp,
cardBorderStroke = BorderStroke(1.dp, gold),
imageFrameElevation = 4.dp,
imageFrameStroke = BorderStroke(1.dp, gold.copy(alpha = 0.7f)),
imageFramePadding = 3.dp,
sectionAccentColor = deepRose,
sectionAccentColorDark = roseRed,
sectionAccentWidth = 3.dp,
sectionDividerColor = Color(0xFFD0C0B0),
sectionDividerColorDark = Color(0xFF3A2820),
sectionDividerHeight = 1.dp,
```

**Step 9: Build to verify compilation passes**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 10: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/SkinConfigs.kt
git commit -m "feat: populate visual refinement tokens for all 7 skins"
```

---

### Task 3: Upgrade LolitaCard

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/common/LolitaCard.kt`

**Step 1: Replace LolitaCard implementation**

Replace the entire file content with:

```kotlin
package com.lolita.app.ui.screen.common

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lolita.app.ui.theme.LolitaSkin

@Composable
fun LolitaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val skin = LolitaSkin.current
    val cardShape = skin.cardShape
    val cardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
    )
    val elevation = CardDefaults.cardElevation(defaultElevation = skin.cardElevation)
    val border = skin.cardBorderStroke
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = cardShape,
            colors = cardColors,
            elevation = elevation,
            border = border
        ) {
            content()
        }
    } else {
        Card(
            modifier = modifier,
            shape = cardShape,
            colors = cardColors,
            elevation = elevation,
            border = border
        ) {
            content()
        }
    }
}
```

**Step 2: Build to verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/common/LolitaCard.kt
git commit -m "feat: upgrade LolitaCard to use skin elevation and border tokens"
```

---

### Task 4: Upgrade GalleryCard

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/common/GalleryCard.kt`

**Step 1: Update GalleryCard to use skin tokens**

In the `Card(...)` call within `GalleryCard`, replace:
```kotlin
elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
```
with:
```kotlin
elevation = CardDefaults.cardElevation(defaultElevation = skin.cardElevation)
```

Add `border = skin.cardBorderStroke` parameter to the Card.

**Step 2: Build to verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/common/GalleryCard.kt
git commit -m "feat: upgrade GalleryCard to use skin elevation and border tokens"
```

---

### Task 5: Create SectionHeader Component

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/screen/common/SectionHeader.kt`

**Step 1: Write the component**

```kotlin
package com.lolita.app.ui.screen.common

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lolita.app.ui.theme.LolitaSkin

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    val skin = LolitaSkin.current
    val isDark = isSystemInDarkTheme()
    val accentColor = if (isDark) skin.sectionAccentColorDark else skin.sectionAccentColor
    val dividerColor = if (isDark) skin.sectionDividerColorDark else skin.sectionDividerColor
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(skin.sectionAccentWidth)
                    .height(18.dp)
                    .clip(RoundedCornerShape(skin.sectionAccentWidth / 2))
                    .background(accentColor)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (action != null) {
                action()
            }
        }
        HorizontalDivider(
            color = dividerColor,
            thickness = skin.sectionDividerHeight
        )
    }
}
```

**Step 2: Build to verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/common/SectionHeader.kt
git commit -m "feat: add SectionHeader component with skin-aware accent bar"
```

---

### Task 6: Create ImageFrame Component

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/screen/common/ImageFrame.kt`

**Step 1: Write the component**

```kotlin
package com.lolita.app.ui.screen.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lolita.app.ui.theme.LolitaSkin

@Composable
fun ImageFrame(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val skin = LolitaSkin.current
    Surface(
        modifier = modifier,
        shape = skin.cardShape,
        color = MaterialTheme.colorScheme.surface,
        border = skin.imageFrameStroke,
        shadowElevation = skin.imageFrameElevation
    ) {
        Box(modifier = Modifier.padding(skin.imageFramePadding)) {
            content()
        }
    }
}
```

**Step 2: Build to verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/common/ImageFrame.kt
git commit -m "feat: add ImageFrame component with skin-aware border and shadow"
```

---

### Task 7: Refactor ItemDetailScreen to Use New Components

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemDetailScreen.kt`

This is the largest task. The key structural changes:

1. Add imports for `SectionHeader`, `ImageFrame`
2. Change the content Column spacing from `Arrangement.spacedBy(16.dp)` to `Arrangement.spacedBy(12.dp)`
3. Replace the first `HorizontalDivider` (after name/status, line ~256) — remove it (sections now have their own dividers)
4. Wrap the basic info section (brand, category, coordinate, colors, season, style, source — lines ~259-322) in a `LolitaCard` with `SectionHeader(title = "基本信息")`
5. Remove the second `HorizontalDivider` (line ~324)
6. Wrap the description section (lines ~327-340) in a `LolitaCard` with `SectionHeader(title = "描述")`
7. Remove the third `HorizontalDivider` (line ~344)
8. Wrap the size info section (lines ~346-371) in a `LolitaCard` with `SectionHeader(title = "尺码信息")`
9. Remove the fourth `HorizontalDivider` (line ~373)
10. Wrap the price section (lines ~375-507) in a `LolitaCard` with `SectionHeader(title = "价格信息", action = { OutlinedButton(manage prices) })` — move the existing title Row + OutlinedButton into the SectionHeader action slot
11. Remove the `HorizontalDivider` after the recommendation button (line ~525)
12. Remove the `HorizontalDivider` after metadata (line ~528)
13. Wrap the metadata rows (创建时间, 更新时间) in a `LolitaCard` with `SectionHeader(title = "记录信息")`

**Step 1: Add imports**

Add after existing imports:
```kotlin
import com.lolita.app.ui.screen.common.SectionHeader
import com.lolita.app.ui.screen.common.ImageFrame
```

**Step 2: Refactor the content Column**

The content Column (lines ~228-540) needs to be restructured from flat text+divider layout into card-grouped sections. Each group becomes:

```kotlin
LolitaCard(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader(title = "基本信息")
        Spacer(Modifier.height(8.dp))
        // existing brand/category/coordinate/color/season/style/source rows
    }
}
```

Same pattern for description, size, price, and metadata sections. The price SectionHeader includes the "管理价格" button as the action parameter.

**Step 3: Build to verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/ItemDetailScreen.kt
git commit -m "feat: refactor ItemDetailScreen with SectionHeader and LolitaCard sections"
```

---

### Task 8: Final Verification

**Step 1: Clean build**

Run: `./gradlew.bat clean assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 2: Release build**

Run: `./gradlew.bat clean assembleRelease`
Expected: BUILD SUCCESSFUL

**Step 3: Verify no regressions in skin system**

Check that all 7 skins compile and the new tokens are correctly consumed by LolitaCard, GalleryCard, SectionHeader, and ImageFrame.
