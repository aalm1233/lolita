# Navy Skin (清风水手) Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a 5th skin "NAVY" (清风水手) — sailor Lolita aesthetic with sky blue + white + gold colors, nautical Canvas-drawn icons, water-ripple animations, and anchor/rope/bubble ambient particles.

**Architecture:** Follow existing skin pattern exactly — add enum value, config function, icon provider, animation provider, navigation transitions, and particle classes. No new abstractions needed.

**Tech Stack:** Kotlin, Jetpack Compose Canvas API, Material3 ColorScheme, Spring/Tween animations.

**Design doc:** `docs/plans/2026-02-23-navy-skin-design.md`

**Note:** No test suite exists in this project. Verification is via `./gradlew.bat assembleRelease`.

---

### Task 1: Add NAVY enum value to SkinType

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/SkinType.kt:3-4`

**Step 1: Add enum value**

```kotlin
enum class SkinType {
    DEFAULT, GOTHIC, CHINESE, CLASSIC, NAVY
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/SkinType.kt
git commit -m "feat(skin): add NAVY enum value to SkinType"
```

---

### Task 2: Add Pacifico font file

**Files:**
- Create: `app/src/main/res/font/pacifico_regular.ttf`

**Step 1: Download Pacifico font**

Download from Google Fonts: `https://fonts.google.com/specimen/Pacifico`
Place `pacifico_regular.ttf` (renamed from `Pacifico-Regular.ttf`) into `app/src/main/res/font/`.

**Step 2: Commit**

```bash
git add app/src/main/res/font/pacifico_regular.ttf
git commit -m "feat(skin): add Pacifico font for Navy skin"
```

---

### Task 3: Create Navy particle classes

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/NavyParticles.kt`
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/NavyBubbleParticle.kt`

**Step 1: Create NavyParticles.kt**

Contains two particle classes following the `AmbientParticle` pattern (see `ClassicParticles.kt`):

- `NavyAnchorParticle` — small anchor shape (⚓) drawn with Path, drifts slowly with gentle rocking rotation. Sky blue color `#4A90D9`. Size 8-14f. Alpha range 0.08-0.25f.
- `NavyRopeKnotParticle` — figure-eight / pretzel knot shape drawn with cubicTo curves, drifts with slow rotation. Gold color `#DAA520`. Size 6-10f. Alpha range 0.08-0.20f.

Both must implement: `reset()`, `update()`, `DrawScope.draw()`. Follow drift/wrap pattern from ClassicParticles.

**Step 2: Create NavyBubbleParticle.kt**

- `NavyBubbleParticle` — small circle with radial gradient (light blue center fading to transparent), rises upward (negative driftSpeedY). Size 4-12f. Color `#87CEEB`. Alpha breathing via sin(). Wraps from top to bottom.

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/NavyParticles.kt
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/NavyBubbleParticle.kt
git commit -m "feat(skin): add Navy ambient particles (anchor, rope knot, bubble)"
```

---

### Task 4: Wire Navy particles into SkinBackgroundAnimation

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinBackgroundAnimation.kt:76-100`

**Step 1: Add imports** (after line 22)

```kotlin
import com.lolita.app.ui.theme.skin.animation.particles.NavyAnchorParticle
import com.lolita.app.ui.theme.skin.animation.particles.NavyRopeKnotParticle
import com.lolita.app.ui.theme.skin.animation.particles.NavyBubbleParticle
```

**Step 2: Add NAVY branch** in `createParticles()` before the closing `}`

```kotlin
SkinType.NAVY -> {
    val anchors = List((count * 0.4f).toInt().coerceAtLeast(1)) { NavyAnchorParticle() }
    val knots = List((count * 0.3f).toInt().coerceAtLeast(1)) { NavyRopeKnotParticle() }
    val bubbles = List((count * 0.3f).toInt().coerceAtLeast(1)) { NavyBubbleParticle() }
    anchors + knots + bubbles
}
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinBackgroundAnimation.kt
git commit -m "feat(skin): wire Navy particles into SkinBackgroundAnimation"
```

---

### Task 5: Add Navy navigation transitions

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinNavigationTransitions.kt`

**Step 1: Add 4 transition functions** at end of file (after line 79)

Navy uses horizontal slide (like Chinese) but with slightly different timing — 380ms, easing `CubicBezierEasing(0.3f, 0f, 0.2f, 1f)`, 30% offset:

```kotlin
// Navy: horizontal wave-like slide
fun navyEnterTransition(): EnterTransition =
    fadeIn(tween(380, easing = CubicBezierEasing(0.3f, 0f, 0.2f, 1f))) +
        slideInHorizontally(tween(380, easing = CubicBezierEasing(0.3f, 0f, 0.2f, 1f))) { it / 3 }

fun navyExitTransition(): ExitTransition =
    fadeOut(tween(280)) + slideOutHorizontally(tween(320)) { -it / 3 }

fun navyPopEnterTransition(): EnterTransition =
    fadeIn(tween(380)) + slideInHorizontally(tween(380)) { -it / 3 }

fun navyPopExitTransition(): ExitTransition =
    fadeOut(tween(280)) + slideOutHorizontally(tween(320)) { it / 3 }
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinNavigationTransitions.kt
git commit -m "feat(skin): add Navy navigation transitions"
```

---

### Task 6: Create NavyAnimationProvider

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/NavyAnimationProvider.kt`

**Step 1: Implement NavyAnimationProvider**

Follow `ClassicAnimationProvider.kt` pattern exactly. Key values from design:

```kotlin
package com.lolita.app.ui.theme.skin.animation

// imports: same as ClassicAnimationProvider + CubicBezierEasing

private val SkyBlue = Color(0xFF4A90D9)
private val LightSkyBlue = Color(0xFF87CEEB)

class NavyAnimationProvider : SkinAnimationProvider {

    override val skinTransition = SkinTransitionSpec(
        durationMs = 380,
        overlay = { progress ->
            // Water ripple: concentric circles expanding from center
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val maxRadius = size.maxDimension * 0.8f
                for (i in 0..2) {
                    val rippleProgress = (progress - i * 0.15f).coerceIn(0f, 1f)
                    val radius = maxRadius * rippleProgress
                    val alpha = (1f - rippleProgress) * 0.1f
                    drawCircle(
                        SkyBlue.copy(alpha = alpha),
                        radius = radius,
                        center = Offset(cx, cy),
                        style = Stroke(width = 3f)
                    )
                }
            }
        }
    )

    override val tabSwitchAnimation = TabSwitchAnimationSpec(
        indicatorAnimation = tween(
            durationMillis = 380,
            easing = CubicBezierEasing(0.3f, 0f, 0.2f, 1f)
        ),
        selectedEffect = { _ -> Modifier },
        particleEffect = null
    )

    override val cardAnimation = CardAnimationSpec(
        enterTransition = fadeIn(tween(350)) +
            slideInVertically(
                tween(380, easing = CubicBezierEasing(0.3f, 0f, 0.2f, 1f))
            ) { it / 4 },
        exitTransition = fadeOut(tween(250)) +
            slideOutVertically(tween(250)) { -it / 6 },
        staggerDelayMs = 65,
        enterDurationMs = 380
    )

    override val interactionFeedback = InteractionFeedbackSpec(
        pressScale = 0.94f,
        rippleColor = SkyBlue,
        rippleAlpha = 0.16f,
        customRipple = { center, progress ->
            // Water ripple rings
            Canvas(Modifier.fillMaxSize()) {
                for (i in 0..1) {
                    val p = (progress - i * 0.2f).coerceIn(0f, 1f)
                    val radius = size.minDimension * 0.3f * p
                    val alpha = (1f - p) * 0.25f
                    drawCircle(
                        SkyBlue.copy(alpha = alpha),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 1.5f)
                    )
                }
            }
        }
    )

    override val clickFeedback = SkinClickFeedbackSpec(
        pressScale = 0.94f,
        scaleAnimationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        rippleColor = SkyBlue,
        rippleDuration = 400,
        rippleStyle = RippleStyle.SOFT,
        hasParticles = true,
        particleCount = 5
    )

    override val navigation = SkinNavigationSpec(
        enterTransition = navyEnterTransition(),
        exitTransition = navyExitTransition(),
        popEnterTransition = navyPopEnterTransition(),
        popExitTransition = navyPopExitTransition(),
        hasOverlayEffect = true,
        overlayDuration = 400
    )

    override val listAnimation = SkinListAnimationSpec(
        appearDirection = AppearDirection.FROM_BOTTOM,
        appearOffsetPx = 40f,
        staggerDelayMs = 65,
        animationSpec = tween(350, easing = CubicBezierEasing(0.3f, 0f, 0.2f, 1f)),
        flingFrictionMultiplier = 1.1f
    )

    override val ambientAnimation = SkinAmbientAnimationSpec(
        backgroundEnabled = true,
        backgroundParticleCount = 22,
        backgroundCycleDurationRange = 8000..15000,
        backgroundAlphaRange = 0.08f..0.25f,
        topBarDecorationAnimated = true,
        cardGlowEffect = true
    )
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/NavyAnimationProvider.kt
git commit -m "feat(skin): add NavyAnimationProvider"
```

---

### Task 7: Create NavyIconProvider — Navigation & Action icons (24 icons)

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/NavyIconProvider.kt`

This is the largest task. Follow `ClassicIconProvider.kt` pattern exactly. All 45 icons are Canvas-drawn with nautical motifs.

**Step 1: Create file with helpers + NavigationIcons + ActionIcons**

Shared helpers:

```kotlin
private fun navyStroke(s: Float) = Stroke(
    width = s * 0.07f, cap = StrokeCap.Round, join = StrokeJoin.Round
)

private fun thinNavy(s: Float) = Stroke(
    width = s * 0.035f, cap = StrokeCap.Round, join = StrokeJoin.Round
)
```

Helper draw functions for reusable nautical motifs:
- `drawAnchor(c, r, color)` — anchor shape with curved arms and crossbar
- `drawRopeKnot(c, r, color)` — figure-eight rope knot
- `drawHelm(c, r, color)` — ship's wheel with spokes
- `drawWave(c, w, color)` — small wave decoration

**NavigationIcons (5):** Nautical-themed versions of Home/Wishlist/Outfit/Stats/Settings
- Home → lighthouse silhouette
- Wishlist → anchor with heart at top
- Outfit → sailor collar (水手领)
- Stats → ship's helm/wheel
- Settings → compass rose

**ActionIcons (12):** Nautical-themed versions
- Add → life preserver with plus
- Delete → anchor with X
- Edit → quill/feather pen (航海日志)
- Search → telescope/spyglass
- Sort → stacked waves
- Save → anchor dropping down
- Close → rope X
- Share → signal flags
- FilterList → fishing net lines
- MoreVert → three rope knots vertical
- ContentCopy → two overlapping signal flags
- Refresh → compass needle spinning

Each icon: `Canvas(modifier.size(24.dp))` → get `s = size.minDimension` → draw with `navyStroke(s)` and `thinNavy(s)`.

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/icon/NavyIconProvider.kt
git commit -m "feat(skin): add NavyIconProvider navigation + action icons (24/45)"
```

---

### Task 8: Add Content, Arrow, and Status icons to NavyIconProvider (21 icons)

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/NavyIconProvider.kt`

**Step 1: Add ContentIcons (13)**
- Star → starfish (five arms, organic curves)
- StarBorder → starfish outline
- Image → porthole (round window with cross)
- Camera → porthole with lens
- AddPhoto → porthole with plus
- Link → chain link
- LinkOff → broken chain
- Palette → shell/scallop
- FileOpen → scroll/map
- CalendarMonth → ship's log book
- Notifications → ship's bell
- AttachMoney → gold coin with anchor
- Category → compass quadrants
- Location → lighthouse beacon

**Step 2: Add ArrowIcons (9)**
- ArrowBack/Forward → wave-styled arrows
- KeyboardArrowLeft/Right → small wave arrows
- ExpandMore/Less → wave chevrons
- ArrowDropDown → anchor pointing down
- SwapVert → tidal arrows (up/down waves)
- OpenInNew → telescope extending

**Step 3: Add StatusIcons (6)**
- CheckCircle → life preserver with check
- Warning → lighthouse warning beam
- Error → storm warning flag
- Info → message in bottle
- Visibility → telescope open
- VisibilityOff → telescope closed

**Step 4: Wire up the provider class**

```kotlin
class NavyIconProvider : BaseSkinIconProvider() {
    override val navigation = NavyNavigationIcons()
    override val action = NavyActionIcons()
    override val content = NavyContentIcons()
    override val arrow = NavyArrowIcons()
    override val status = NavyStatusIcons()
}
```

**Step 5: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/icon/NavyIconProvider.kt
git commit -m "feat(skin): complete NavyIconProvider all 45 icons"
```

---

### Task 9: Add navySkinConfig() and wire into getSkinConfig()

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/SkinConfigs.kt`

**Step 1: Add NAVY branch to getSkinConfig()** (line 29, before closing `}`)

```kotlin
fun getSkinConfig(skinType: SkinType): LolitaSkinConfig = when (skinType) {
    SkinType.DEFAULT -> defaultSkinConfig()
    SkinType.GOTHIC -> gothicSkinConfig()
    SkinType.CHINESE -> chineseSkinConfig()
    SkinType.CLASSIC -> classicSkinConfig()
    SkinType.NAVY -> navySkinConfig()
}
```

**Step 2: Add navySkinConfig() function** (after `classicSkinConfig()`, after line 233)

```kotlin
fun navySkinConfig(): LolitaSkinConfig {
    val fontFamily = FontFamily(Font(R.font.pacifico_regular))
    val skyBlue = Color(0xFF4A90D9)
    val lightSkyBlue = Color(0xFF5BA0E9)
    val gold = Color(0xFFDAA520)
    val darkGold = Color(0xFFB8860B)
    val darkBg = Color(0xFF0D1B2A)
    val darkSurface = Color(0xFF1B2D44)
    return LolitaSkinConfig(
        skinType = SkinType.NAVY,
        name = "清风水手",
        lightColorScheme = lightColorScheme(
            primary = skyBlue, onPrimary = White,
            primaryContainer = Color(0xFFD6EAFF), onPrimaryContainer = Color(0xFF1B3A5C),
            secondary = gold, onSecondary = Color(0xFF1A1A1A),
            secondaryContainer = Color(0xFFFFF8E1), onSecondaryContainer = darkGold,
            tertiary = gold, onTertiary = Color(0xFF1A1A1A),
            background = Color(0xFFF0F8FF), onBackground = Color(0xFF1A1A1A),
            surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1A1A1A),
            surfaceVariant = Color(0xFFE8F4FD),
            error = Color(0xFFD32F2F), onError = White,
            outline = Color(0xFF8EAEC0), outlineVariant = Color(0xFFBDD8EA)
        ),
        darkColorScheme = darkColorScheme(
            primary = lightSkyBlue, onPrimary = White,
            primaryContainer = Color(0xFF2E6EB5), onPrimaryContainer = Color(0xFFD6EAFF),
            secondary = darkGold, onSecondary = White,
            secondaryContainer = darkSurface,
            tertiary = gold, onTertiary = Color(0xFF1A1A1A),
            background = darkBg, onBackground = Color(0xFFD0E0F0),
            surface = darkSurface, onSurface = Color(0xFFD0E0F0),
            surfaceVariant = Color(0xFF253A50),
            error = Color(0xFFCF6679), onError = Black
        ),
        gradientColors = listOf(skyBlue, Color(0xFF87CEEB)),
        gradientColorsDark = listOf(Color(0xFF1B3A5C), Color(0xFF2E6EB5)),
        accentColor = skyBlue, accentColorDark = lightSkyBlue,
        cardColor = Color(0xFFF5FAFF), cardColorDark = darkSurface,
        fontFamily = fontFamily, typography = buildTypography(fontFamily),
        cardShape = RoundedCornerShape(14.dp),
        buttonShape = RoundedCornerShape(14.dp),
        topBarDecoration = "⚓", topBarDecorationAlpha = 0.6f,
        icons = NavyIconProvider(),
        animations = NavyAnimationProvider(),
    )
}
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/SkinConfigs.kt
git commit -m "feat(skin): add navySkinConfig() and wire into getSkinConfig()"
```

---

### Task 10: Version bump + build verification

**Files:**
- Modify: `app/build.gradle.kts:25-26`

**Step 1: Bump version**

```
versionCode = 23
versionName = "2.12"
```

(New feature → minor version bump)

**Step 2: Build release**

```bash
./gradlew.bat assembleRelease
```

Expected: BUILD SUCCESSFUL. Fix any compilation errors before proceeding.

**Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "chore: bump version to 2.12 (versionCode 23) for Navy skin"
```

---

## Task Dependency Graph

```
Task 1 (SkinType enum)
  ↓
Task 2 (Pacifico font) ─────────────────────────┐
  ↓                                               │
Task 3 (Particles) → Task 4 (Wire particles)     │
  ↓                                               │
Task 5 (Nav transitions) → Task 6 (AnimProvider) │
  ↓                                               │
Task 7 (Icons 24/45) → Task 8 (Icons 45/45)     │
  ↓                                               │
Task 9 (SkinConfigs — depends on ALL above) ←────┘
  ↓
Task 10 (Version bump + build)
```

Tasks 2-8 can be parallelized after Task 1, but Task 9 depends on all of them. Task 10 is final verification.
