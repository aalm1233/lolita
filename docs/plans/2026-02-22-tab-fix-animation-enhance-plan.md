# Tab指示器修复 & 动画效果增强 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix SkinTabIndicator covering tab text, enhance card glow animations and background particles for all 4 skins.

**Architecture:** Three independent changes: (1) Fix Canvas drawing coordinates in SkinTabIndicator to use fixed 3dp at bottom, (2) Rewrite SkinCardGlow for DEFAULT/GOTHIC/CHINESE with distinctive visible effects, (3) Add 4 new particle classes and update animation providers with higher counts/alpha. No test suite exists; verify via `./gradlew.bat assembleRelease`.

**Tech Stack:** Kotlin, Jetpack Compose Canvas API, Material3 TabRow

---

### Task 1: Fix SkinTabIndicator drawing coordinates

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinTabIndicator.kt`

**Step 1: Rewrite the Canvas drawing to use fixed indicator height at bottom**

Replace the entire Canvas block. Key change: use `val indicatorH = 3.dp.toPx()` and `val topY = size.height - indicatorH` instead of `val h = size.height`.

```kotlin
Canvas(
    modifier
        .fillMaxWidth()
        .height(3.dp)
) {
    val left = indicatorOffset.toPx()
    val w = indicatorWidth.toPx()
    val indicatorH = 3.dp.toPx()
    val topY = size.height - indicatorH

    when (skin.skinType) {
        SkinType.DEFAULT -> {
            drawRoundRect(
                Pink400,
                topLeft = Offset(left + w * 0.1f, topY),
                size = Size(w * 0.8f, indicatorH),
                cornerRadius = CornerRadius(indicatorH / 2f)
            )
        }
        SkinType.GOTHIC -> {
            drawRect(
                Color(0xFF4A0E4E),
                topLeft = Offset(left, topY),
                size = Size(w, indicatorH)
            )
            drawRect(
                Color(0xFF4A0E4E).copy(alpha = 0.3f),
                topLeft = Offset(left - w * 0.1f, topY),
                size = Size(w * 0.1f, indicatorH)
            )
        }
        SkinType.CHINESE -> {
            val path = Path().apply {
                moveTo(left, topY + indicatorH)
                val steps = 20
                for (i in 0..steps) {
                    val px = left + w * i / steps
                    val py = topY + indicatorH * 0.5f + sin(i.toFloat() * 0.8f) * indicatorH * 0.3f
                    lineTo(px, py)
                }
                lineTo(left + w, topY + indicatorH)
                close()
            }
            drawPath(path, Color(0xFF2C2C2C).copy(alpha = 0.8f))
        }
        SkinType.CLASSIC -> {
            val gold = Color(0xFFD4AF37)
            drawRoundRect(
                gold.copy(alpha = 0.2f),
                topLeft = Offset(left + w * 0.05f, topY - indicatorH * 0.3f),
                size = Size(w * 0.9f, indicatorH * 1.6f),
                cornerRadius = CornerRadius(indicatorH)
            )
            drawRoundRect(
                gold,
                topLeft = Offset(left + w * 0.15f, topY + indicatorH * 0.2f),
                size = Size(w * 0.7f, indicatorH * 0.6f),
                cornerRadius = CornerRadius(indicatorH / 2f)
            )
        }
    }
}
```

**Step 2: Build to verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinTabIndicator.kt
git commit -m "fix(skin): fix tab indicator covering text by drawing at bottom of canvas"
```

---

### Task 2: Enhance card glow animations

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinCardGlow.kt`

**Step 1: Rewrite the `ContentDrawScope.draw()` for DEFAULT, GOTHIC, and CHINESE**

Keep CLASSIC unchanged. Replace the three other skin cases:

**DEFAULT — flowing heart light along card edge:**
- Calculate a point position along the card perimeter using `progress` (0→1 maps to full perimeter traversal)
- At that point, draw a small heart shape with `Pink400` at alpha 0.4
- Draw a radial gradient glow around the heart (radius ~20f, alpha 0.25)
- Also draw a subtle pink border stroke at alpha 0.08 for base visibility

**GOTHIC — corner shadow pulse + blood red flicker:**
- Calculate pulse alpha using `sin(progress * 2π)` mapped to 0.15~0.3
- Draw radial gradients from all 4 corners inward (radius = min(width,height) * 0.4)
- Color: `Color(0xFF4A0E4E)` (gothic purple) with pulsing alpha
- Add blood red edge flicker: draw thin border stroke with `Color(0xFF8B0000)` at `sin(progress * 4π) * 0.2` alpha (flickers twice per cycle)

**CHINESE — ink wash spread from bottom-right:**
- Calculate spread radius using `progress`: `maxRadius * (0.3 + 0.15 * sin(progress * 2π))`
- `maxRadius = hypot(width, height) * 0.5`
- Center point at `(width * 0.85, height * 0.85)` (bottom-right area)
- Draw radial gradient: `Color(0xFF2C2C2C)` at alpha 0.2 center → transparent edge
- Add a subtle ink dot at the center point (radius 3f, alpha 0.3)

**Step 2: Build to verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinCardGlow.kt
git commit -m "feat(skin): enhance card glow with distinctive per-skin animations"
```

---

### Task 3: Create new particle classes

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/SweetStarParticle.kt`
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/GothicEmberParticle.kt`
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/ChinesePlumBlossomParticle.kt`
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/ClassicDiamondParticle.kt`

All extend `AmbientParticle` from `SkinBackgroundAnimation.kt`. Follow existing patterns in `SweetParticles.kt`, `GothicParticles.kt`, etc.

**Step 1: Create SweetStarParticle**

Five-pointed star shape. Fields: x, y, alpha, starSize (8~15f), speed (slow drift upward), wobblePhase, wobbleSpeed, breathPhase, breathSpeed. The star flickers via rapid alpha pulse: `baseAlpha + sin(breathPhase * 3f) * baseAlpha * 0.8f` (3x frequency = fast twinkle). BaseAlpha 0.15~0.35. Draw using a 5-point star Path (10 vertices alternating outer/inner radius). Color: `Pink400`.

**Step 2: Create GothicEmberParticle**

Small ember rising from bottom. Fields: x, y, alpha, size (3~8f), riseSpeed, wobblePhase, tailLength (15~25f). Starts at bottom (`y = height + random`), rises upward. Alpha fades as it rises: `baseAlpha * (y / height)`. Draw: small circle at (x,y) + gradient line downward for tail effect using `Color(0xFF8B0000)` (blood red). Reset when y < -tailLength.

**Step 3: Create ChinesePlumBlossomParticle**

5-petal plum blossom falling. Fields: x, y, alpha, petalSize (10~18f), fallSpeed, wobblePhase, wobbleSpeed, rotation, rotationSpeed. Falls from top, wobbles left-right. Draw: 5 elliptical petals arranged in a circle (72° apart) around center, with a small center dot. Color: `Color(0xFFC41E3A)` (vermillion) at alpha. Reset when y > height + petalSize.

**Step 4: Create ClassicDiamondParticle**

Diamond/rhombus shape with sparkle. Fields: x, y, alpha, diamondSize (6~12f), driftSpeedX, driftSpeedY, sparklePhase, sparkleSpeed. Alpha uses sparkle pattern: `baseAlpha * max(0, sin(sparklePhase))` — goes fully dark between flashes. Draw: diamond Path (4 points: top, right, bottom, left). Color: `Color(0xFFD4AF37)` (gold) with radial gradient for glow.

**Step 5: Build to verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/
git commit -m "feat(skin): add new particle types for all 4 skins"
```

---

### Task 4: Update animation providers and particle creation

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/SweetAnimationProvider.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/GothicAnimationProvider.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/ChineseAnimationProvider.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/ClassicAnimationProvider.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinBackgroundAnimation.kt`

**Step 1: Update SweetAnimationProvider.ambientAnimation**

Change `backgroundParticleCount` from `15` to `20`, `backgroundAlphaRange` from `0.1f..0.3f` to `0.15f..0.4f`.

**Step 2: Update GothicAnimationProvider.ambientAnimation**

Change `backgroundParticleCount` from `4` to `12`, `backgroundAlphaRange` from `0.08f..0.2f` to `0.12f..0.3f`.

**Step 3: Update ChineseAnimationProvider.ambientAnimation**

Change `backgroundParticleCount` from `3` to `10`, `backgroundAlphaRange` from `0.05f..0.15f` to `0.1f..0.25f`.

**Step 4: Update ClassicAnimationProvider.ambientAnimation**

Change `backgroundParticleCount` from `7` to `14`, `backgroundAlphaRange` from `0.1f..0.25f` to `0.15f..0.35f`.

**Step 5: Update SkinBackgroundAnimation.createParticles**

Update the `createParticles` function to mix new particle types:

```kotlin
private fun createParticles(skinType: SkinType, count: Int): List<AmbientParticle> {
    return when (skinType) {
        SkinType.DEFAULT -> {
            val bubbles = List((count * 0.4f).toInt().coerceAtLeast(1)) { SweetBubbleParticle() }
            val petals = List((count * 0.3f).toInt().coerceAtLeast(1)) { SweetPetalParticle() }
            val stars = List((count * 0.3f).toInt().coerceAtLeast(1)) { SweetStarParticle() }
            bubbles + petals + stars
        }
        SkinType.GOTHIC -> {
            val smoke = List((count * 0.5f).toInt().coerceAtLeast(1)) { GothicSmokeParticle() }
            val embers = List((count * 0.5f).toInt().coerceAtLeast(1)) { GothicEmberParticle() }
            smoke + embers
        }
        SkinType.CHINESE -> {
            val clouds = List((count * 0.4f).toInt().coerceAtLeast(1)) { ChineseCloudParticle() }
            val blossoms = List((count * 0.6f).toInt().coerceAtLeast(1)) { ChinesePlumBlossomParticle() }
            clouds + blossoms
        }
        SkinType.CLASSIC -> {
            val sparkles = List((count * 0.5f).toInt().coerceAtLeast(1)) { ClassicSparkleParticle() }
            val diamonds = List((count * 0.5f).toInt().coerceAtLeast(1)) { ClassicDiamondParticle() }
            sparkles + diamonds
        }
    }
}
```

Add imports for the 4 new particle classes.

**Step 6: Build to verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 7: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/
git commit -m "feat(skin): increase particle counts and mix new particle types"
```

---

### Task 5: Version bump and release build

**Files:**
- Modify: `app/build.gradle.kts:25-26`

**Step 1: Bump version**

Change `versionCode` from `2` to `3`, `versionName` from `"2.0"` to `"2.1"` (new feature = minor bump).

**Step 2: Release build**

Run: `./gradlew.bat assembleRelease`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "chore: bump version to 2.1 (versionCode 3)"
```
