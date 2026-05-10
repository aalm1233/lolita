# Step 2: Blur & Glass Effects

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Integrate Haze library for frosted glass / blur effects on TopAppBar, BottomNavBar, and Dialogs. Per-skin blur tokens control intensity and tint. Graceful fallback for API < 31.

**Architecture:** Additive — new HazeState + blur tokens in LolitaSkinConfig, minimal refactoring of existing components.

**Tech Stack:** Kotlin 2.1.0, Compose BOM 2024.12.01, Material3, **Haze 1.7.2** (new dependency)

**Prerequisite:** Phase 1 (visual token refinement) is committed on `feat/premium-ui-redesign`.

---

## Architecture Decision: TopBar in Scaffold vs Per-Screen

**Current state:** Each screen composable renders its own `GradientTopAppBar` inside its own layout. No `topBar` parameter passed to `Scaffold`. Content uses `Modifier.padding(paddingValues)` so it doesn't scroll behind bars.

**Decision: Keep per-screen TopAppBar pattern (Route A).**

Rationale:
- Lifting TopAppBar into Scaffold would require 30+ screen composables to be refactored
- Each screen has different title/actions/navigationIcon — a single Scaffold topBar would need complex state routing
- The `HazeState` can be provided via `CompositionLocal` from LolitaNavHost, consumed by both the per-screen TopAppBar and the bottom bar

**Pattern:**
1. `LolitaNavHost` creates `rememberHazeState()` and provides it via `LocalHazeState`
2. Bottom bar uses `hazeEffect(LocalHazeState.current)` directly (already in Scaffold)
3. NavHost content area uses `hazeSource(state = hazeState)` — blur source is the full screen
4. Each screen's `GradientTopAppBar` uses `hazeEffect(LocalHazeState.current)`
5. Content padding adjusted: top bar area = transparent, content extends behind it

---

## Task 1: Add Haze Dependency

**File:** `app/build.gradle.kts`

Add after the Coil dependency (line 119):
```kotlin
// Haze (blur / frosted glass effects)
implementation("dev.chrisbanes.haze:haze:1.7.2")
implementation("dev.chrisbanes.haze:haze-materials:1.7.2")
```

**QA:** `./gradlew.bat assembleDebug` — dependency resolves and compiles.

---

## Task 2: Add Blur Tokens to LolitaSkinConfig

**File:** `app/src/main/java/com/lolita/app/ui/theme/LolitaSkinConfig.kt`

Add after `accentDesaturationDark` field (before `icons`):

```kotlin
// Blur / glass effect tokens
val topBarBlurEnabled: Boolean = true,
val topBarBlurAlpha: Float = 0.7f,             // gradient overlay alpha (0=transparent, 1=opaque)
val topBarBlurTint: Color,                      // per-skin tint color for top bar glass
val topBarBlurTintDark: Color,                  // dark mode variant
val navBarBlurEnabled: Boolean = true,
val navBarBlurAlpha: Float = 0.7f,              // bottom bar overlay alpha
val navBarBlurTint: Color,                      // per-skin tint for bottom nav glass
val navBarBlurTintDark: Color,
val dialogBlurEnabled: Boolean = true,
val dialogBlurAlpha: Float = 0.6f,              // dialog surface alpha
```

**Design rationale:**
- `topBarBlurAlpha` 0.7 = mostly opaque with subtle blur peek-through (premium feel, readable text)
- Per-skin `blurTint` colors: VICTORIAN=gold/cream, GOTHIC=purple, DEFAULT=pink, etc.
- `Boolean` enabled flags let skins opt out

**QA:** `./gradlew.bat assembleDebug` — compiles with default values.

---

## Task 3: Update All 7 Skin Factory Functions with Blur Values

**File:** `app/src/main/java/com/lolita/app/ui/theme/SkinConfigs.kt`

Per-skin blur token values:

| Skin | topBarAlpha | topBarTint(L/D) | navBarAlpha | navBarTint(L/D) | dialogAlpha |
|------|-------------|-----------------|-------------|-----------------|-------------|
| DEFAULT | 0.72 | Pink50#FCE4EC / Pink900(0.3) | 0.75 | White / Gray800(0.3) | 0.65 |
| GOTHIC | 0.80 | Purple50#F3E5F5 / #1A1A2E(0.6) | 0.82 | White / #2D2D44(0.5) | 0.70 |
| CHINESE | 0.70 | #FFF8F0 / #2D2520(0.4) | 0.72 | #FFF8F0 / #2D2520(0.3) | 0.60 |
| CLASSIC | 0.75 | #FFF5F0 / #2D2525(0.4) | 0.78 | #FFF5F0 / #2D2525(0.35) | 0.65 |
| NAVY | 0.70 | #F0F8FF / #1B2D44(0.4) | 0.75 | #F0F8FF / #1B2D44(0.35) | 0.60 |
| COUNTRY | 0.68 | #F9F4E8 / #2D3529(0.35) | 0.70 | #F9F4E8 / #2D3529(0.3) | 0.58 |
| VICTORIAN | 0.82 | cream#FFF5E6(0.9) / #2C1E18(0.5) | 0.85 | cream#FFF5E6 / #2C1E18(0.45) | 0.72 |

Key: VICTORIAN heaviest glass (0.82), COUNTRY lightest (0.68). Dark tints heavily alpha'd (0.3-0.6).

**QA:** `./gradlew.bat assembleDebug` — all 7 factories compile.

---

## Task 4: Create LocalHazeState CompositionLocal

**File:** `app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt`

Add imports:
```kotlin
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
```

Add composition local (top-level, before LolitaNavHost):
```kotlin
val LocalHazeState = compositionLocalOf<HazeState?> { null }
```

In `LolitaNavHost()`, after `val skin = LolitaSkin.current`:
```kotlin
val hazeState = rememberHazeState()
```

Wrap the Scaffold in `CompositionLocalProvider(LocalHazeState provides hazeState)`:
```kotlin
CompositionLocalProvider(LocalHazeState provides hazeState) {
    Scaffold(...) { paddingValues -> ... }
}
```

**QA:** `./gradlew.bat assembleDebug` — compiles, no visual change yet.

---

## Task 5: Refactor LolitaNavHost — Bottom Bar Blur + hazeSource

**File:** `app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt`

### 5a: Bottom bar — replace opaque surface with blur

Replace `NavigationBar` modifier and containerColor:
```kotlin
NavigationBar(
    modifier = Modifier
        .height(60.dp)
        .then(
            if (skin.navBarBlurEnabled && hazeState != null) {
                Modifier.hazeEffect(
                    state = hazeState,
                    style = HazeStyle(
                        blurRadius = 20.dp,
                        backgroundColor = if (isSystemInDarkTheme()) skin.navBarBlurTintDark else skin.navBarBlurTint,
                        tints = listOf(
                            HazeTint(
                                (if (isSystemInDarkTheme()) skin.navBarBlurTintDark else skin.navBarBlurTint)
                                    .copy(alpha = skin.navBarBlurAlpha)
                            )
                        )
                    )
                )
            } else {
                Modifier
            }
        ),
    containerColor = if (skin.navBarBlurEnabled) {
        (if (isSystemInDarkTheme()) skin.navBarBlurTintDark else skin.navBarBlurTint)
            .copy(alpha = skin.navBarBlurAlpha)
    } else {
        MaterialTheme.colorScheme.surface
    },
    contentColor = accent,
    windowInsets = WindowInsets(0, 0, 0, 0)
)
```

### 5b: NavHost — add hazeSource, adjust padding

Change the NavHost modifier from:
```kotlin
modifier = Modifier.padding(paddingValues)
```
to:
```kotlin
modifier = Modifier
    .fillMaxSize()
    .padding(bottom = paddingValues.calculateBottomPadding())
    .hazeSource(state = hazeState)
```

This lets content scroll behind the top bar (no top padding) but keeps bottom padding for the bottom bar. The `hazeSource` makes the entire content area the blur source.

Keep the SkinBackgroundAnimation modifier as-is (still padded with paddingValues).

**QA:** Build passes. Visual: bottom nav shows blurred content when scrolling.

---

## Task 6: Refactor GradientTopAppBar for Blur

**File:** `app/src/main/java/com/lolita/app/ui/screen/common/GradientTopAppBar.kt`

### 6a: Add parameter

```kotlin
fun GradientTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = true,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    hazeState: HazeState? = LocalHazeState.current  // NEW
)
```

### 6b: Change Surface modifier

For both compact=true and compact=false branches, replace the Surface modifier:

```kotlin
val blurEnabled = skin.topBarBlurEnabled && hazeState != null

Surface(
    modifier = modifier
        .fillMaxWidth()
        .then(
            if (blurEnabled) {
                Modifier.hazeEffect(
                    state = hazeState!!,
                    style = HazeStyle(
                        blurRadius = 25.dp,
                        backgroundColor = if (isSystemInDarkTheme()) skin.topBarBlurTintDark else skin.topBarBlurTint,
                        tints = listOf(
                            HazeTint(
                                (if (isSystemInDarkTheme()) skin.topBarBlurTintDark else skin.topBarBlurTint)
                                    .copy(alpha = skin.topBarBlurAlpha)
                            )
                        )
                    )
                )
            } else {
                Modifier.background(gradient)
            }
        ),
    color = Color.Transparent
)
```

### 6c: Change Row background

Replace `Modifier.background(gradient)` with:
```kotlin
Modifier.then(if (blurEnabled) Modifier else Modifier.background(gradient))
```

When blur is enabled, the HazeStyle tint provides the visual overlay. When disabled, the original opaque gradient is the fallback.

**QA:** Build passes. Visual: top bar shows blurred content underneath.

---

## Task 7: Handle API Level Fallback

Haze internally handles API < 31 by falling back to scrim (translucent overlay without blur). The `hazeEffect` modifier still works — it just renders the tint color without actual blur on old devices.

To ensure a good experience on API < 31:
- The `containerColor` with `copy(alpha = skin.topBarBlurAlpha)` already provides a semi-transparent overlay
- On old devices, this renders as a tinted semi-transparent bar (acceptable fallback)
- No code changes needed — Haze handles this automatically

**QA:** No code changes. Verify on API < 31 emulator if available.

---

## Task 8: Update GalleryCard to Use Skin Tokens

**File:** `app/src/main/java/com/lolita/app/ui/screen/common/GalleryCard.kt`

GalleryCard currently uses `MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)` (line 53). Replace with the new skin tokens:

```kotlin
containerColor = if (isSystemInDarkTheme()) skin.cardContainerColorDark else skin.cardContainerColor
```

This makes GalleryCard consistent with LolitaCard (which already uses these tokens from Phase 1).

**QA:** Build passes. GalleryCard uses skin-aware color tokens.

---

## Task 9: Update AGENTS.md

Add to the visual token table and relevant sections:
- New blur tokens: topBarBlurEnabled/Alpha/Tint, navBarBlurEnabled/Alpha/Tint, dialogBlurEnabled/Alpha
- Haze dependency in build config
- LocalHazeState composition local pattern
- GradientTopAppBar now accepts optional hazeState parameter
- GalleryCard now uses skin token cardContainerColor

**QA:** AGENTS.md updated with Phase 2 changes.

---

## Verification

After all tasks complete:
1. `./gradlew.bat assembleDebug` — must PASS
2. `./gradlew.bat assembleRelease` — must PASS (clean build for R8)
3. Visual: both top and bottom bars show frosted glass with content blur
4. Visual: 7 skins each show their tint color through the glass
5. Visual: API < 31 fallback = semi-transparent tinted bar (no crash, no blank)
6. No content hidden behind bars (padding values correct)
