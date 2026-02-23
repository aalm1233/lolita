# 全局UI压缩 + 粒子效果重构 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 压缩全局页面空间占用，移除卡片粒子效果，增强背景粒子，卡片半透明化

**Architecture:** 直接修改各组件的 padding/spacing 值，删除 SkinCardGlow，修改 LolitaCard 透明度，增加背景粒子数量

**Tech Stack:** Kotlin, Jetpack Compose, Material3

---

### Task 1: 压缩 GradientTopAppBar

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/common/GradientTopAppBar.kt:54`

**Step 1: Reduce compact mode vertical padding**

Change line 54 from:
```kotlin
.padding(horizontal = 4.dp, vertical = 4.dp),
```
to:
```kotlin
.padding(horizontal = 4.dp, vertical = 2.dp),
```

**Step 2: Build to verify**

Run: `./gradlew.bat assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/common/GradientTopAppBar.kt
git commit -m "refactor: compress GradientTopAppBar compact vertical padding 4dp→2dp"
```

---

### Task 2: 压缩 ItemListScreen 搜索栏和筛选区域

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt`

**Step 1: Compress search bar padding (line 153)**

Change:
```kotlin
.padding(horizontal = 16.dp, vertical = 4.dp),
```
to:
```kotlin
.padding(horizontal = 12.dp, vertical = 2.dp),
```

**Step 2: Compress quick outfit card outer padding (line 247)**

Change:
```kotlin
modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
```
to:
```kotlin
modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
```

**Step 3: Compress quick outfit card inner padding (line 250)**

Change:
```kotlin
modifier = Modifier.fillMaxWidth().padding(12.dp),
```
to:
```kotlin
modifier = Modifier.fillMaxWidth().padding(8.dp),
```

**Step 4: Compress category filter row padding (line 282)**

Change:
```kotlin
.padding(horizontal = 16.dp, vertical = 4.dp),
```
to:
```kotlin
.padding(horizontal = 12.dp, vertical = 2.dp),
```

**Step 5: Compress filter chip height (line 300)**

Change:
```kotlin
modifier = Modifier.height(28.dp)
```
to:
```kotlin
modifier = Modifier.height(24.dp)
```

**Step 6: Compress list vertical spacing (line 361)**

Change:
```kotlin
verticalArrangement = Arrangement.spacedBy(12.dp),
```
to:
```kotlin
verticalArrangement = Arrangement.spacedBy(8.dp),
```

**Step 7: Compress list horizontal padding (line 362)**

Change:
```kotlin
contentPadding = PaddingValues(horizontal = 16.dp),
```
to:
```kotlin
contentPadding = PaddingValues(horizontal = 12.dp),
```

**Step 8: Compress grid spacing (lines 393-395)**

Change:
```kotlin
verticalArrangement = Arrangement.spacedBy(12.dp),
horizontalArrangement = Arrangement.spacedBy(12.dp),
contentPadding = PaddingValues(horizontal = 16.dp)
```
to:
```kotlin
verticalArrangement = Arrangement.spacedBy(8.dp),
horizontalArrangement = Arrangement.spacedBy(8.dp),
contentPadding = PaddingValues(horizontal = 12.dp)
```

**Step 9: Build to verify**

Run: `./gradlew.bat assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 10: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt
git commit -m "refactor: compress ItemListScreen search/filter/list spacing"
```

---

### Task 3: 压缩 StatsPageScreen TabRow

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/stats/StatsPageScreen.kt:32`

**Step 1: Reduce TabRow edge padding**

Change:
```kotlin
edgePadding = 8.dp,
```
to:
```kotlin
edgePadding = 4.dp,
```

**Step 2: Build to verify**

Run: `./gradlew.bat assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/stats/StatsPageScreen.kt
git commit -m "refactor: compress StatsPageScreen TabRow edge padding 8dp→4dp"
```

---

### Task 4: 压缩底部导航栏

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt:115`

**Step 1: Reduce NavigationBar height**

Change:
```kotlin
modifier = Modifier.height(64.dp),
```
to:
```kotlin
modifier = Modifier.height(52.dp),
```

**Step 2: Build to verify**

Run: `./gradlew.bat assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt
git commit -m "refactor: compress bottom navigation bar height 64dp→52dp"
```

---

### Task 5: 移除卡片粒子效果 + 卡片半透明化

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/common/LolitaCard.kt`
- Delete: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinCardGlow.kt`

**Step 1: Rewrite LolitaCard.kt**

Replace the entire file with:
```kotlin
package com.lolita.app.ui.screen.common

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lolita.app.ui.theme.LolitaSkin

@Composable
fun LolitaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val cardShape = LolitaSkin.current.cardShape
    val cardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
    )
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = cardShape,
            colors = cardColors,
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            content()
        }
    } else {
        Card(
            modifier = modifier,
            shape = cardShape,
            colors = cardColors,
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            content()
        }
    }
}
```

**Step 2: Delete SkinCardGlow.kt**

```bash
git rm app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinCardGlow.kt
```

**Step 3: Build to verify**

Run: `./gradlew.bat assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/common/LolitaCard.kt
git commit -m "refactor: remove card glow effects, add 0.75f alpha transparency, reduce elevation to 2dp"
```

---

### Task 6: 增加背景粒子数量

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/SweetAnimationProvider.kt:115`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/GothicAnimationProvider.kt:117`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/ChineseAnimationProvider.kt:136`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/ClassicAnimationProvider.kt:123`

**Step 1: SweetAnimationProvider — 20 → 28**

Change:
```kotlin
backgroundParticleCount = 20,
```
to:
```kotlin
backgroundParticleCount = 28,
```

**Step 2: GothicAnimationProvider — 12 → 18**

Change:
```kotlin
backgroundParticleCount = 12,
```
to:
```kotlin
backgroundParticleCount = 18,
```

**Step 3: ChineseAnimationProvider — 10 → 16**

Change:
```kotlin
backgroundParticleCount = 10,
```
to:
```kotlin
backgroundParticleCount = 16,
```

**Step 4: ClassicAnimationProvider — 14 → 20**

Change:
```kotlin
backgroundParticleCount = 14,
```
to:
```kotlin
backgroundParticleCount = 20,
```

**Step 5: Build to verify**

Run: `./gradlew.bat assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/SweetAnimationProvider.kt \
       app/src/main/java/com/lolita/app/ui/theme/skin/animation/GothicAnimationProvider.kt \
       app/src/main/java/com/lolita/app/ui/theme/skin/animation/ChineseAnimationProvider.kt \
       app/src/main/java/com/lolita/app/ui/theme/skin/animation/ClassicAnimationProvider.kt
git commit -m "refactor: increase background particle counts (Sweet 28, Gothic 18, Chinese 16, Classic 20)"
```

---

### Task 7: 清理 cardGlowEffect 配置

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/SweetAnimationProvider.kt:119`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/GothicAnimationProvider.kt:121`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/ChineseAnimationProvider.kt:140`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/ClassicAnimationProvider.kt:127`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinAnimationProvider.kt:78,124`

**Step 1: Set all cardGlowEffect to false**

In all 4 animation providers, change:
```kotlin
cardGlowEffect = true
```
to:
```kotlin
cardGlowEffect = false
```

**Step 2: Build to verify**

Run: `./gradlew.bat assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/SweetAnimationProvider.kt \
       app/src/main/java/com/lolita/app/ui/theme/skin/animation/GothicAnimationProvider.kt \
       app/src/main/java/com/lolita/app/ui/theme/skin/animation/ChineseAnimationProvider.kt \
       app/src/main/java/com/lolita/app/ui/theme/skin/animation/ClassicAnimationProvider.kt
git commit -m "refactor: disable cardGlowEffect in all skin animation providers"
```

---

### Task 8: 版本号更新 + Release 构建

**Files:**
- Modify: `app/build.gradle.kts` — versionCode and versionName

**Step 1: Bump version**

Increment `versionCode` by 1, update `versionName` to next minor (check current values first).

**Step 2: Release build**

Run: `./gradlew.bat assembleRelease 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "chore: bump version for UI compression and particle refactor"
```
