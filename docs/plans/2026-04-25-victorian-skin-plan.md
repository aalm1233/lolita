# Victorian Skin Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a 7th skin (VICTORIAN — 维多利亚) with dark-red velvet aesthetic, gold accents, fully custom Canvas icons, rose petal + gold dust particles.

**Architecture:** Follow the exact pattern established by Country skin (the most recently added skin). VictorianIconProvider delegates to VictorianDecorativeIcons helper file. VictorianAnimationProvider follows CountryAnimationProvider structure. Particles follow CountryParticles pattern. SkinConfigs.kt gets a new factory function.

**Tech Stack:** Kotlin + Jetpack Compose + Canvas + Material3

---

### Task 1: Add VICTORIAN to SkinType enum

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/SkinType.kt`

**Step 1: Add enum entry**

```kotlin
enum class SkinType {
    DEFAULT, GOTHIC, CHINESE, CLASSIC, NAVY, COUNTRY, VICTORIAN
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/SkinType.kt
git commit -m "feat: add VICTORIAN to SkinType enum"
```

---

### Task 2: Create Victorian particles

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/VictorianParticles.kt`

**Step 1: Create particle file**

Follow `CountryParticles.kt` pattern exactly. Two ambient particles.

```kotlin
package com.lolita.app.ui.theme.skin.animation.particles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.lolita.app.ui.theme.skin.animation.AmbientParticle
import kotlin.math.sin
import kotlin.random.Random

private val RoseRed = Color(0xFF9C254D)
private val DustGold = Color(0xFFD4A843)

class VictorianRosePetal : AmbientParticle() {
    override var x = 0f
    override var y = 0f
    override var alpha = 0f
    private var baseAlpha = 0f
    private var petalWidth = 0f
    private var petalHeight = 0f
    private var driftX = 0f
    private var driftY = 0f
    private var swayPhase = 0f
    private var swaySpeed = 0f
    private var rotation = 0f
    private var rotationSpeed = 0f

    override fun reset(width: Float, height: Float) {
        x = Random.nextFloat() * width
        y = -20f - Random.nextFloat() * 40f
        petalWidth = 5f + Random.nextFloat() * 6f
        petalHeight = 8f + Random.nextFloat() * 8f
        baseAlpha = 0.06f + Random.nextFloat() * 0.1f
        alpha = baseAlpha
        driftX = (-0.005f + Random.nextFloat() * 0.01f)
        driftY = 0.006f + Random.nextFloat() * 0.008f
        swayPhase = Random.nextFloat() * 6.28f
        swaySpeed = 0.0004f + Random.nextFloat() * 0.0006f
        rotation = Random.nextFloat() * 360f
        rotationSpeed = -0.008f + Random.nextFloat() * 0.016f
    }

    override fun update(deltaMs: Long, width: Float, height: Float) {
        swayPhase += swaySpeed * deltaMs
        x += (driftX + sin(swayPhase) * 0.008f) * deltaMs
        y += driftY * deltaMs
        rotation += rotationSpeed * deltaMs
        alpha = baseAlpha + sin(swayPhase * 0.8f) * baseAlpha * 0.35f
        if (y > height + petalHeight * 2f) reset(width, height)
        if (x > width + petalWidth * 2f) x = -petalWidth * 2f
        if (x < -petalWidth * 2f) x = width + petalWidth * 2f
    }

    override fun DrawScope.draw() {
        rotate(rotation, Offset(x, y)) {
            val petal = Path().apply {
                moveTo(x, y - petalHeight * 0.5f)
                quadraticBezierTo(x + petalWidth * 0.8f, y - petalHeight * 0.15f, x, y + petalHeight * 0.5f)
                quadraticBezierTo(x - petalWidth * 0.8f, y - petalHeight * 0.15f, x, y - petalHeight * 0.5f)
                close()
            }
            drawPath(petal, RoseRed.copy(alpha = alpha))
            drawLine(
                color = Color.White.copy(alpha = alpha * 0.3f),
                start = Offset(x, y - petalHeight * 0.4f),
                end = Offset(x, y + petalHeight * 0.4f),
                strokeWidth = 1f
            )
        }
    }
}

class VictorianGoldDust : AmbientParticle() {
    override var x = 0f
    override var y = 0f
    override var alpha = 0f
    private var baseAlpha = 0f
    private var radius = 0f
    private var driftX = 0f
    private var driftY = 0f
    private var swayPhase = 0f
    private var swaySpeed = 0f
    private var twinklePhase = 0f
    private var twinkleSpeed = 0f

    override fun reset(width: Float, height: Float) {
        x = Random.nextFloat() * width
        y = Random.nextFloat() * height
        radius = 1.5f + Random.nextFloat() * 2.5f
        baseAlpha = 0.05f + Random.nextFloat() * 0.12f
        alpha = baseAlpha
        driftX = (-0.002f + Random.nextFloat() * 0.004f)
        driftY = -(0.002f + Random.nextFloat() * 0.005f)
        swayPhase = Random.nextFloat() * 6.28f
        swaySpeed = 0.0006f + Random.nextFloat() * 0.0008f
        twinklePhase = Random.nextFloat() * 6.28f
        twinkleSpeed = 0.001f + Random.nextFloat() * 0.002f
    }

    override fun update(deltaMs: Long, width: Float, height: Float) {
        swayPhase += swaySpeed * deltaMs
        twinklePhase += twinkleSpeed * deltaMs
        x += (driftX + sin(swayPhase) * 0.004f) * deltaMs
        y += driftY * deltaMs
        alpha = baseAlpha * (0.5f + 0.5f * sin(twinklePhase))
        if (y < -radius * 2f) reset(width, height)
        if (x > width + radius * 2f) x = -radius * 2f
        if (x < -radius * 2f) x = width + radius * 2f
    }

    override fun DrawScope.draw() {
        drawCircle(DustGold.copy(alpha = alpha), radius, Offset(x, y))
        drawCircle(Color.White.copy(alpha = alpha * 0.4f), radius * 0.5f, Offset(x, y))
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/VictorianParticles.kt
git commit -m "feat: add Victorian ambient particles (rose petal + gold dust)"
```


---

### Task 3: Create Victorian animation provider

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/VictorianAnimationProvider.kt`

**Step 1: Create animation provider**

Follow `CountryAnimationProvider.kt` pattern. Velvet curtain reveal transition, GLOW ripple, gold dust click particles.

Key properties:
- `skinTransition`: Two burgundy panels closing from edges, with gold trim lines (velvet curtain effect). durationMs = 450.
- `tabSwitchAnimation`: Elegant spring, dampingRatio = 0.75, StiffnessLow
- `cardAnimation`: fadeIn + slideInVertically (it/5), staggerDelayMs = 40, enterDurationMs = 380
- `interactionFeedback`: pressScale 0.95, rippleColor = VictorianGold, customRipple with gold outer + cream inner circles
- `clickFeedback`: pressScale 0.92, rippleStyle = GLOW, hasParticles = true, particleCount = 6
- `navigation`: fadeIn + slideInVertically transitions, hasOverlayEffect = true
- `listAnimation`: AppearDirection.FROM_BOTTOM, staggerDelayMs = 40
- `ambientAnimation`: backgroundParticleCount = 26, cardGlowEffect = true

Private color constants:
- `VictorianBurgundy = Color(0xFF7B1E3A)`
- `VictorianGold = Color(0xFFB8860B)`
- `VictorianCream = Color(0xFFF5E6D3)`

See full code in design doc or reference `CountryAnimationProvider.kt` for the exact class structure.

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/VictorianAnimationProvider.kt
git commit -m "feat: add VictorianAnimationProvider"
```

---

### Task 4: Create Victorian decorative icons (Canvas drawing helpers)

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/VictorianDecorativeIcons.kt`

**Step 1: Create the decorative icons file**

This is the largest file (~900 lines). It mirrors `CountryDecorativeIcons.kt` structure exactly:

Structure:
- 4 `internal enum class` types: `VictorianActionKind` (16), `VictorianContentKind` (14), `VictorianArrowKind` (9), `VictorianStatusKind` (7)
- 4 `@Composable internal fun` dispatch functions
- 1 `@Composable private fun VictorianDecorCanvas(modifier, drawBlock)` - Canvas wrapper at 24.dp
- Private `DrawScope` extension functions for all visual primitives and glyphs
- Accent fill color: `private val VictorianIvory = Color(0xFFF5E6D3)`

**Visual language for Victorian icons:**
- Medium-thick strokes with serif/floral decorative endpoints
- Small scrollwork/floral accents at icon corners
- Navigation icons: ribbon/banner base shape with centered icon
- Action icons: ornate shield + subtle scrollwork corner accents
- Content icons: vintage picture frame with corner rosettes
- Arrow icons: arrowheads with feather fletching decoration
- Status icons: wax seal outer ring with rope border

**Key visual primitives (DrawScope extensions to implement):**

1. `drawVictorianScrollwork(center, scale, tint)` - C-scroll flourish for corners
2. `drawVictorianShield(center, width, height, tint)` - ornate shield shape for action icons
3. `drawVictorianFrame(center, width, height, tint)` - vintage picture frame with corner rosettes for content icons
4. `drawVictorianSeal(center, radius, tint)` - wax seal circle with rope border for status icons
5. `drawVictorianRibbon(center, width, height, tint)` - ribbon/banner base for navigation icons
6. `drawFeatherArrow(center, size, tint, direction, vertical)` - arrow with feather fletching

**Glyph helpers:**
- `drawVictorianPlusGlyph`, `drawVictorianCrossGlyph`, `drawVictorianCheckGlyph`
- `drawVictorianExclamationGlyph`, `drawVictorianInfoGlyph`, `drawVictorianQuestionGlyph`
- `drawVictorianQuillGlyph`

**Additional helpers for specific icons:**
- `drawVictorianEnvelope`, `drawVictorianCalendar`, `drawVictorianBell`
- `drawVictorianPurse`, `drawVictorianCabinet`, `drawVictorianSignpost`
- `drawVictorianCamera`, `drawVictorianChain(broken)`, `drawVictorianPalette`
- `drawVictorianStackedCards`, `drawVictorianWreath`

**CRITICAL: Every single icon must be fully drawn in Canvas.** No empty `when` branches. No fallback Material icons. All 51 icons must have custom Victorian-style Canvas drawings. Reference `CountryDecorativeIcons.kt` for how each `when` branch composes primitives + glyphs.

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/icon/VictorianDecorativeIcons.kt
git commit -m "feat: add VictorianDecorativeIcons with 51 custom Canvas icons"
```

---

### Task 5: Create Victorian icon provider

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/VictorianIconProvider.kt`

**Step 1: Create icon provider**

Follow `CountryIconProvider.kt` pattern exactly. 5 private inner classes extending base classes + 1 public class:

- `VictorianNavigationIcons : BaseNavigationIcons()` - 5 icons delegating to `VictorianNavIcon(IconKey.X, modifier, tint)`
- `VictorianActionIcons : BaseActionIcons()` - 16 icons delegating to `VictorianActionDecorativeIcon(VictorianActionKind.X, modifier, tint)`
- `VictorianContentIcons : BaseContentIcons()` - 14 icons delegating to `VictorianContentDecorativeIcon(VictorianContentKind.X, modifier, tint)`
- `VictorianArrowIcons : BaseArrowIcons()` - 9 icons delegating to `VictorianArrowDecorativeIcon(VictorianArrowKind.X, modifier, tint)`
- `VictorianStatusIcons : BaseStatusIcons()` - 7 icons delegating to `VictorianStatusDecorativeIcon(VictorianStatusKind.X, modifier, tint)`
- `class VictorianIconProvider : BaseSkinIconProvider()` - overrides all 5 category properties

Navigation icons use `VictorianNavIcon(key, modifier, tint)` defined in VictorianDecorativeIcons (follows `CountryBottomNavIcon` pattern).

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/icon/VictorianIconProvider.kt
git commit -m "feat: add VictorianIconProvider"
```

---

### Task 6: Add Victorian skin config factory and wire into getSkinConfig

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/SkinConfigs.kt`

**Step 1: Add `victorianSkinConfig()` factory function**

Add after `countrySkinConfig()`. Key values from the design:

- skinType = SkinType.VICTORIAN
- name = "维多利亚"
- fontFamily = FontFamily(Font(R.font.cormorant_garamond))
- Light: primary=burgundy(#7B1E3A), secondary=darkGold(#B8860B), background=ivory(#FFF8F0), surface=cream(#FFF5E6), onBackground=darkBrown(#3E2723)
- Dark: primary=roseRed(#C4566A), secondary=brightGold(#D4A843), background=darkBg(#1A1210), surface=darkSurface(#2C1E18), onBackground=warmWhite(#F5E6D3)
- gradientColors = listOf(burgundy, darkGold)
- gradientColorsDark = listOf(darkBg, roseRed)
- accentColor = deepRose(#9C254D), accentColorDark = roseRed(#C4566A)
- cardColor = cream, cardColorDark = darkSurface
- cardShape = RoundedCornerShape(12.dp), buttonShape = RoundedCornerShape(8.dp)
- topBarDecoration = "⚜", topBarDecorationAlpha = 0.5f
- icons = VictorianIconProvider(), animations = VictorianAnimationProvider()

**Step 2: Add VICTORIAN mapping in `getSkinConfig()`**

```kotlin
SkinType.VICTORIAN -> victorianSkinConfig()
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/SkinConfigs.kt
git commit -m "feat: add victorianSkinConfig factory and wire into getSkinConfig"
```

---

### Task 7: Wire Victorian particles into SkinBackgroundAnimation

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinBackgroundAnimation.kt`

**Step 1: Add Victorian particle creation**

Find the particle creation `when(skinType)` block. Add:

```kotlin
SkinType.VICTORIAN -> listOf(
    VictorianRosePetal().apply { reset(width, height) },
    VictorianGoldDust().apply { reset(width, height) }
)
```

Also add the necessary import for `VictorianRosePetal` and `VictorianGoldDust`.

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinBackgroundAnimation.kt
git commit -m "feat: wire Victorian particles into SkinBackgroundAnimation"
```

---

### Task 8: Build verification

**Step 1: Run debug build**

```bash
./gradlew.bat assembleDebug
```

Expected: BUILD SUCCESSFUL

**Step 2: If build fails, fix errors and re-run**

Common issues to watch for:
- Missing imports in SkinConfigs.kt (VictorianIconProvider, VictorianAnimationProvider)
- Missing VICTORIAN case in any `when` expression (exhaustive when)
- Particle class import issues in SkinBackgroundAnimation.kt

**Step 3: Final commit (if any fixes needed)**

```bash
git add -A
git commit -m "fix: resolve Victorian skin build issues"
```
