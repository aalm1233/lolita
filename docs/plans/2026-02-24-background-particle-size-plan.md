# 背景动画粒子尺寸翻倍 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Double all background animation particle sizes across all 5 skins.

**Architecture:** Direct modification of size parameters in each particle class's `reset()` method. No structural changes.

**Tech Stack:** Kotlin, Jetpack Compose Canvas

---

### Task 1: Sweet — SweetBubbleParticle & SweetPetalParticle

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/SweetParticles.kt`

**Step 1: Double SweetBubbleParticle radius**

Line 26: `radius = 4f + Random.nextFloat() * 8f` → `radius = 8f + Random.nextFloat() * 16f`

**Step 2: Double SweetPetalParticle petalSize**

Line 62: `petalSize = 6f + Random.nextFloat() * 8f` → `petalSize = 12f + Random.nextFloat() * 16f`

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/SweetParticles.kt
git commit -m "feat: double Sweet bubble and petal particle sizes"
```

---

### Task 2: Sweet — SweetStarParticle

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/SweetStarParticle.kt`

**Step 1: Double starSize**

Line 28: `starSize = 8f + Random.nextFloat() * 7f` → `starSize = 16f + Random.nextFloat() * 14f`

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/SweetStarParticle.kt
git commit -m "feat: double Sweet star particle size"
```

---

### Task 3: Gothic — GothicSmokeParticle

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/GothicParticles.kt`

**Step 1: Double smoke radius**

Line 24: `radius = 60f + Random.nextFloat() * 60f` → `radius = 120f + Random.nextFloat() * 120f`

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/GothicParticles.kt
git commit -m "feat: double Gothic smoke particle size"
```

---

### Task 4: Gothic — GothicEmberParticle

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/GothicEmberParticle.kt`

**Step 1: Double emberSize**

Line 24: `emberSize = 3f + Random.nextFloat() * 5f` → `emberSize = 6f + Random.nextFloat() * 10f`

**Step 2: Double tailLength**

Line 30: `tailLength = 15f + Random.nextFloat() * 10f` → `tailLength = 30f + Random.nextFloat() * 20f`

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/GothicEmberParticle.kt
git commit -m "feat: double Gothic ember particle size and tail length"
```

---

### Task 5: Chinese — ChineseCloudParticle

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/ChineseParticles.kt`

**Step 1: Double cloudWidth**

Line 27: `cloudWidth = 80f + Random.nextFloat() * 60f` → `cloudWidth = 160f + Random.nextFloat() * 120f`

**Step 2: Double cloudHeight**

Line 28: `cloudHeight = 30f + Random.nextFloat() * 20f` → `cloudHeight = 60f + Random.nextFloat() * 40f`

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/ChineseParticles.kt
git commit -m "feat: double Chinese cloud particle size"
```

---

### Task 6: Chinese — ChinesePlumBlossomParticle

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/ChinesePlumBlossomParticle.kt`

**Step 1: Double petalSize**

Line 26: `petalSize = 10f + Random.nextFloat() * 8f` → `petalSize = 20f + Random.nextFloat() * 16f`

**Step 2: Double center dot radius**

Line 62: `radius = 2f,` → `radius = 4f,`

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/ChinesePlumBlossomParticle.kt
git commit -m "feat: double Chinese plum blossom particle size"
```

---

### Task 7: Classic — ClassicSparkleParticle

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/ClassicParticles.kt`

**Step 1: Double sparkle radius**

Line 25: `radius = 10f + Random.nextFloat() * 30f` → `radius = 20f + Random.nextFloat() * 60f`

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/ClassicParticles.kt
git commit -m "feat: double Classic sparkle particle size"
```

---

### Task 8: Classic — ClassicDiamondParticle

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/ClassicDiamondParticle.kt`

**Step 1: Double diamondSize**

Line 26: `diamondSize = 6f + Random.nextFloat() * 6f` → `diamondSize = 12f + Random.nextFloat() * 12f`

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/ClassicDiamondParticle.kt
git commit -m "feat: double Classic diamond particle size"
```

---

### Task 9: Navy — NavyAnchorParticle & NavyRopeKnotParticle

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/NavyParticles.kt`

**Step 1: Double anchorSize**

Line 30: `anchorSize = 8f + Random.nextFloat() * 6f` → `anchorSize = 16f + Random.nextFloat() * 12f`

**Step 2: Double knotSize**

Line 111: `knotSize = 6f + Random.nextFloat() * 4f` → `knotSize = 12f + Random.nextFloat() * 8f`

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/NavyParticles.kt
git commit -m "feat: double Navy anchor and rope knot particle sizes"
```

---

### Task 10: Navy — NavyBubbleParticle

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/NavyBubbleParticle.kt`

**Step 1: Double baseRadius**

Line 28: `baseRadius = 4f + Random.nextFloat() * 8f` → `baseRadius = 8f + Random.nextFloat() * 16f`

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/NavyBubbleParticle.kt
git commit -m "feat: double Navy bubble particle size"
```

---

### Task 11: Version bump & release build

**Files:**
- Modify: `app/build.gradle.kts`

**Step 1: Bump version**

Increment `versionCode` by 1, bump `versionName` minor version.

**Step 2: Build release**

```bash
./gradlew.bat assembleRelease
```

**Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "chore: bump version for background particle size update"
```
