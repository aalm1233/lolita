# Premium UI Redesign — Master Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Transform the lolita wardrobe app's visual quality from "functional with decoration" to "cohesively premium" — matching the polish level of iOS native apps and high-end fashion apps like ZARA/Pinterest while preserving the 7-skin immersion system.

**Architecture:** Incremental enhancement of the existing `LolitaSkinConfig` token system. No rewrites, no new frameworks. Each phase adds tokens/components/effects on top of the existing architecture, maintaining backward compatibility throughout.

**Tech Stack:** Kotlin 2.1.0, Jetpack Compose (BOM 2024.12.01), Material3, Haze (blur), compose-shimmer (loading states), Landscapist (image loading)

---

## Current State Assessment

### What Works
- ✅ Token-based skin system (`LolitaSkinConfig` 28 properties + CompositionLocal injection)
- ✅ 7 distinct skins with per-skin icons, animations, colors, shapes
- ✅ Skin-aware components: LolitaCard, SectionHeader, ImageFrame, GradientTopAppBar
- ✅ Canvas-drawn icons per skin (42 icon keys × 7 providers)
- ✅ Rich animation system: tab indicators, navigation overlays, click particles, ambient particles

### What's Missing (Gap Analysis vs iOS/Premium Apps)
- ❌ No blur/frosted glass effects (TopAppBar, BottomNavBar, dialogs)
- ❌ No skeleton/shimmer loading states
- ❌ Inconsistent spring animation parameters across skins (no unified motion token)
- ❌ Typography hierarchy too flat (titleSmall + Bold for section headers = insufficient contrast)
- ❌ No spacing/padding token system (hardcoded 8.dp, 16.dp scattered)
- ❌ Card system lacks variants (only one LolitaCard)
- ❌ Dark mode accent colors not desaturated (GOTHIC: brightPurple on dark = eye strain)
- ❌ Card background uses runtime `alpha=0.75f` instead of pre-baked color
- ❌ No SharedTransitionLayout for hero animations (grid → detail)
- ❌ Accent color overuse (appears in TopAppBar gradient + Section bar + FAB + Tab = noise)

---

## Phase Overview

| Phase | Name | Key Deliverables | New Dependencies | Est. Effort |
|-------|------|-----------------|-----------------|-------------|
| **1** | Visual Token Refinement | Expand LolitaSkinConfig with spacing/radius/motion/shadow tokens; unify spring animation; refine typography hierarchy | 0 | Medium |
| **2** | Blur & Glass Effects | Haze integration; TopAppBar/BottomNavBar/Dialog frosted glass; per-skin blur tokens | haze-blur | Medium |
| **3** | Loading & Image Polish | compose-shimmer skeleton; Landscapist image loading with circular reveal; image-forward layout | compose-shimmer, landscapist-coil | Medium |
| **4** | Card Variant System | LolitaCard.Default/Gallery/Featured/Compact; iOS Section-style grouping; content-first layouts | 0 | Large |
| **5** | Dark Mode Mastery | GOTHIC/other dark themes: tonal elevation, desaturated accents, softened text; dark-mode-specific tokens | 0 | Small |
| **6** | Hero Transitions | SharedTransitionLayout for grid→detail; cross-fade skin switching; predictive back | 0 | Medium |
| **7** | Liquid Glass (Skin-specific) | VICTORIAN gold glass, GOTHIC dark glass via shader effects | AndroidLiquidGlass (optional) | Large |

---

## Phase 1: Visual Token Refinement (Detailed plan in separate file)

**No new dependencies. Pure token/config changes.**

### 1.1 Expand LolitaSkinConfig

Add these tokens to the data class:

```kotlin
// Spacing system (inspired by 8pt grid)
val spacingSmall: Dp      // 4dp DEFAULT → varies per skin
val spacingMedium: Dp     // 8dp
val spacingLarge: Dp      // 16dp
val spacingExtraLarge: Dp // 24dp

// Unified corner radius system (3 levels)
val cornerRadiusSmall: Dp  // 8dp DEFAULT → 4dp GOTHIC → 12dp VICTORIAN
val cornerRadiusMedium: Dp // 16dp DEFAULT → 8dp GOTHIC → 16dp VICTORIAN
val cornerRadiusLarge: Dp  // 24dp DEFAULT → 12dp GOTHIC → 20dp VICTORIAN

// Motion tokens (aligned with M3 Expressive MotionScheme)
val spatialSpring: SpringSpec<Float>   // position/size changes
val effectsSpring: SpringSpec<Float>   // color/alpha changes

// Shadow refinement
val cardShadowAmbientAlpha: Float  // 0.08f (soft)
val cardShadowSpotAlpha: Float     // 0.12f (directional)

// Card inner padding
val cardInnerPadding: Dp     // 16dp → 20dp for luxurious skins
val cardGap: Dp              // 8dp → 12dp between cards

// Dark mode accent desaturation
val accentDesaturationDark: Float  // 0.0 (no change) → 0.3 (30% desaturation)
```

### 1.2 Refine Typography in buildTypography

- Section headers: `titleSmall + Bold` → `titleMedium + Bold` (bigger, more contrast)
- Screen titles: use `headlineSmall` instead of `titleLarge` in detail pages
- Tighten `letterSpacing` for headings: `(-0.5).sp` for display, `(-0.25).sp` for headline

### 1.3 Unify Spring Animation Parameters

Add `spatialSpring` and `effectsSpring` to LolitaSkinConfig, consumed by:
- `SkinAnimationProvider.listAnimation.animationSpec` → `spatialSpring`
- `SkinAnimationProvider.clickFeedback.scaleAnimationSpec` → `spatialSpring`
- `SkinAnimationProvider.tabSwitchAnimation.indicatorAnimation` → `effectsSpring`

### 1.4 Fix Card Surface Color

Replace `MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)` with a dedicated `cardContainerColor` / `cardContainerColorDark` token in LolitaSkinConfig, pre-baked per skin.

### 1.5 Update 7 Skin Factory Functions

Every factory function gets the new tokens with per-skin values.

### 1.6 Update LolitaCard to consume new tokens

- Use `cardContainerColor` token instead of runtime alpha
- Use `cardInnerPadding` for content padding
- Use `cardCornerRadiusSmall/Medium/Large` where applicable

---

## Phase 2: Blur & Glass Effects

**Dependency:** `dev.chrisbanes.haze:haze-blur:<version>` (1.6.0+)

### Key Changes
- Add blur tokens to LolitaSkinConfig: `topBarBlurRadius`, `topBarBlurTint`, `dialogBlurRadius`, `navBarBlurEnabled`
- Refactor GradientTopAppBar: on scroll, fade gradient → blur background
- Refactor LolitaNavHost bottom bar: translucent blur + content peek-through
- Refactor dialog/bottom-sheet backgrounds: frosted glass
- Progressive blur (HazeProgressive) for top bar: fully opaque at top, fading to transparent
- Per-skin blur tint: DEFAULT=pink tint, GOTHIC=purple tint, VICTORIAN=gold tint, etc.
- Fallback for API < 31: semi-transparent Surface (current behavior)

### Affected Files
- `LolitaSkinConfig.kt` (new tokens)
- `SkinConfigs.kt` (7 factories)
- `GradientTopAppBar.kt` (blur integration)
- `LolitaNavHost.kt` (bottom bar blur)
- `build.gradle.kts` (haze dependency)

---

## Phase 3: Loading & Image Polish

**Dependencies:** `com.valentinilk.shimmer:compose-shimmer:1.3.3`, `com.github.skydoves:landscapist-coil:<version>`

### Key Changes
- Add `ShimmerTheme` inside `LolitaTheme`, with per-skin shimmer colors
- Replace empty loading states with skeleton cards
- Replace direct Coil calls with Landscapist `ShimmerImage` / `CircularRevealImage`
- Image-forward detail page layout: full-bleed hero image at top

### Affected Files
- `Theme.kt` (ShimmerTheme integration)
- `LolitaSkinConfig.kt` (shimmer color tokens)
- All list screens (skeleton placeholders)
- All detail screens (hero image layout)

---

## Phase 4: Card Variant System

**No new dependencies. Component refactoring.**

### Key Changes
- `LolitaCard.Default` — current behavior, consumes skin tokens
- `LolitaCard.Gallery` — no border, no shadow, tight radius, image-optimized
- `LolitaCard.Featured` — higher elevation, larger radius, prominent
- `LolitaCard.Compact` — minimal padding, tight spacing
- iOS Section-style component: `LolitaSection` (grouped container with title + embedded dividers)
- Content-first layouts: image occupies 70%+ of card area, text overlays on bottom gradient

### Affected Files
- `LolitaCard.kt` (variant enum)
- New: `LolitaSection.kt` (iOS-style grouped container)
- `SectionHeader.kt` (adapt to work inside LolitaSection)
- Detail screens (migrate to LolitaSection pattern)
- List/grid screens (use GalleryCard for image grids)

---

## Phase 5: Dark Mode Mastery

**No new dependencies. Token refinement.**

### Key Changes
- GOTHIC dark background: ensure `#1A1A2E` (not pure black) — already correct ✅
- Dark text: use `#E6E1E5` (M3 onSurface) not pure `#FFFFFF`
- Desaturate accent colors in dark mode using `accentDesaturationDark` token
- Use M3 tonal elevation: `surfaceContainerLow` → `surfaceContainerHigh` in dark schemes
- Add `surfaceTint` to dark color schemes for tonal elevation
- Image anchoring: subtle border or shadow on images in dark mode

### Affected Files
- `SkinConfigs.kt` (dark color scheme refinements)
- `LolitaCard.kt` (tonal elevation usage)

---

## Phase 6: Hero Transitions

**No new dependencies. Compose 1.7+ APIs.**

### Key Changes
- Wrap app in `SharedTransitionLayout`
- Grid item → detail page: shared element image transition
- Skin switching: `Crossfade` wrapper instead of instant swap
- Predictive back gesture support (Android 14+)
- Staggered list appear animation refinement

### Affected Files
- `LolitaNavHost.kt` (SharedTransitionLayout wrapper)
- Grid screens (Modifier.sharedElement)
- Detail screens (Modifier.sharedElement)
- `Theme.kt` (Crossfade for skin switching)

---

## Phase 7: Liquid Glass (Skin-specific)

**Optional dependency:** AndroidLiquidGlass (API 33+ only, fallback for older)

### Key Changes
- VICTORIAN skin: gold-tinted glass cards, ornate frame overlay
- GOTHIC skin: dark-tinted glass with purple/green refraction
- Other skins: standard frosted glass (from Phase 2)
- Refraction + chromatic aberration for premium feel
- Only apply to specific decorative elements, not all cards

### Affected Files
- New: `skin/effect/LiquidGlassEffect.kt`
- VICTORIAN/GOTHIC factory functions (enable flag)
- `build.gradle.kts` (optional dependency)

---

## Success Metrics

| Metric | Current | Target |
|--------|---------|--------|
| Visual token count | 28 | ~40 |
| Card variants | 1 | 4 |
| Spring animation consistency | Per-provider ad-hoc | Centralized tokens |
| Dark mode quality | Functional (bright accents) | Premium (tonal elevation, desaturated) |
| Loading states | Empty/spinner | Shimmer skeleton |
| Top/Bottom bar blur | None | Per-skin frosted glass |
| Image→Detail transition | Standard nav | Shared element hero |
| Typography hierarchy levels | 2 (titleSmall/Bold, body) | 4+ (headline, title, body, caption) |

---

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Haze performance on low-end devices | Disable blur on API <31, use semi-transparent fallback |
| Too many tokens → LolitaSkinConfig bloat | Group into sub-configs: `SpacingTokens`, `MotionTokens`, `BlurTokens` |
| Breaking existing screens with new defaults | All new tokens have sensible defaults matching current behavior |
| Liquid Glass API 33+ limit | Make it opt-in per skin, graceful fallback to Phase 2 blur |
| Incremental build cache issues | `clean assembleRelease` before release (per AGENTS.md) |
