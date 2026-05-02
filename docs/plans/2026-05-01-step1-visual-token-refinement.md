# Step 1: Visual Token Refinement

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Expand LolitaSkinConfig with spacing, corner radius, motion, shadow, and dark mode tokens; refine typography hierarchy; unify spring animation parameters; fix card surface color. Zero new dependencies.

**Architecture:** Additive changes to existing data class + 7 factory functions. All new tokens have defaults matching current behavior.

**Tech Stack:** Kotlin 2.1.0, Compose BOM 2024.12.01, Material3

---

## Task 1: Add New Tokens to LolitaSkinConfig

**File:** `app/src/main/java/com/lolita/app/ui/theme/LolitaSkinConfig.kt`

Add after `sectionDividerHeight` field, before `icons`:

```kotlin
val spacingSmall: Dp = 4.dp,
val spacingMedium: Dp = 8.dp,
val spacingLarge: Dp = 16.dp,
val spacingExtraLarge: Dp = 24.dp,
val cornerRadiusSmall: Dp = 8.dp,
val cornerRadiusMedium: Dp = 16.dp,
val cornerRadiusLarge: Dp = 24.dp,
val spatialSpring: SpringSpec<Float>,
val effectsSpring: SpringSpec<Float>,
val cardShadowAmbientAlpha: Float = 0.08f,
val cardShadowSpotAlpha: Float = 0.12f,
val cardContainerColor: Color,
val cardContainerColorDark: Color,
val cardInnerPadding: Dp = 16.dp,
val cardGap: Dp = 8.dp,
val accentDesaturationDark: Float = 0.0f,
```

Add import: `import androidx.compose.animation.core.SpringSpec`

---

## Task 2: Update All 7 Skin Factory Functions

**File:** `app/src/main/java/com/lolita/app/ui/theme/SkinConfigs.kt`

Add import: `import androidx.compose.animation.core.spring`

After each factory's `sectionDividerHeight = ...` line, add the new tokens.

Per-skin values (spacing/corner/motion/shadow/color/padding/desaturation):

| Skin | spacingS/M/L/XL | cornerS/M/L | spatial(damp,stiff) | effects(damp,stiff) | shadowA/S | cardColorLight | cardColorDark | innerPad | gap | accentDesat |
|------|-----------------|-------------|---------------------|---------------------|-----------|---------------|--------------|----------|-----|-------------|
| DEFAULT | 4/8/16/24 | 8/16/24 | 0.75,400 | 1.0,1600 | 0.08/0.12 | White(0.75) | Gray800(0.75) | 16 | 8 | 0.0 |
| GOTHIC | 4/8/16/24 | 4/8/12 | 0.85,300 | 1.0,1200 | 0.12/0.18 | White(0.75) | darkSurface(0.8) | 16 | 8 | 0.15 |
| CHINESE | 4/8/14/20 | 2/4/8 | 0.8,350 | 1.0,1400 | 0.06/0.10 | #FFFDF5(0.75) | darkSurface(0.75) | 14 | 6 | 0.1 |
| CLASSIC | 4/8/16/24 | 6/12/16 | 0.82,350 | 1.0,1400 | 0.10/0.14 | #FFF8F5(0.75) | darkSurface(0.8) | 16 | 8 | 0.1 |
| NAVY | 4/8/16/24 | 8/14/20 | 0.7,450 | 1.0,1600 | 0.06/0.10 | #F5FAFF(0.75) | darkSurface(0.75) | 16 | 8 | 0.05 |
| COUNTRY | 6/10/18/26 | 10/20/28 | 0.65,400 | 1.0,1600 | 0.06/0.10 | warmWhite(0.75) | darkSurface(0.75) | 18 | 10 | 0.1 |
| VICTORIAN | 4/8/16/24 | 6/12/16 | 0.8,300 | 1.0,1200 | 0.12/0.18 | cream(0.8) | darkSurface(0.85) | 20 | 10 | 0.15 |

Build: `./gradlew.bat assembleDebug` - Expected: PASS

---

## Task 3: Refine buildTypography

**File:** `app/src/main/java/com/lolita/app/ui/theme/SkinConfigs.kt`

Replace `buildTypography` function. Key changes:
- Tighten `letterSpacing` on display/headline: -0.5sp to -0.1sp (tighter = more premium)
- `titleSmall`: FontWeight.Medium -> SemiBold, fontSize 16sp -> 15sp (clearer hierarchy gap with titleMedium)
- Add imports: `FontWeight`, `sp`

Build: `./gradlew.bat assembleDebug` - Expected: PASS

---

## Task 4: Update LolitaCard to Consume New Tokens

**File:** `app/src/main/java/com/lolita/app/ui/screen/common/LolitaCard.kt`

Key changes:
- Replace `MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)` with `if (isDark) skin.cardContainerColorDark else skin.cardContainerColor`
- Wrap content in `Box(modifier = Modifier.padding(skin.cardInnerPadding))` 
- Add imports: `isSystemInDarkTheme`, `Box`

**IMPORTANT:** Audit all LolitaCard callers for double-padding. Search for `LolitaCard(` and remove redundant inner `padding()` calls that now duplicate `cardInnerPadding`.

Build: `./gradlew.bat assembleDebug` - Expected: PASS

---

## Task 5: Wire Motion Tokens into SkinAnimationProvider

**Files:**
- `app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinAnimationProvider.kt`
- All 7 provider files

Add to interface:
```kotlin
val spatialSpring: AnimationSpec<Float>
    get() = spring(dampingRatio = 0.75f, stiffness = 400f)
val effectsSpring: AnimationSpec<Float>
    get() = spring(dampingRatio = 1.0f, stiffness = 1600f)
```

Override in each provider (see Task 2 table for values).

Update defaults in interface:
- `listAnimation.animationSpec` -> `spatialSpring` (was `tween(300)`)
- `clickFeedback.scaleAnimationSpec` -> `spatialSpring` (was `spring()`)

Build: `./gradlew.bat assembleDebug` - Expected: PASS

---

## Task 6: Update SectionHeader Typography

**File:** `app/src/main/java/com/lolita/app/ui/screen/common/SectionHeader.kt`

Change line 52: `MaterialTheme.typography.titleSmall` -> `MaterialTheme.typography.titleMedium`

This gives section headers 18sp SemiBold (after Task 3) instead of 15sp, creating clearer hierarchy.

Build: `./gradlew.bat assembleDebug` - Expected: PASS

---

## Verification

After all tasks complete:
1. `./gradlew.bat assembleDebug` - must PASS
2. `./gradlew.bat assembleRelease` - must PASS (clean build check for R8)
3. Spot-check: each skin should render with same visual structure, slightly refined
4. No double-padding visual artifacts on any card
