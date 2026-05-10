# Phase 3: Loading & Image Polish — Implementation Plan

**Goal:** Replace all `CircularProgressIndicator` loading states with shimmer skeleton cards, and upgrade image loading from bare Coil `AsyncImage` to Landscapist `CoilImage` with shimmer placeholder + circular reveal animation.

**Dependencies (verified on Maven Central, compatible with BOM 2024.12.01):**
```kotlin
implementation("com.valentinilk.shimmer:compose-shimmer:1.3.2")
implementation("com.github.skydoves:landscapist-coil:2.4.7")
implementation("com.github.skydoves:landscapist-placeholder:2.4.7")
implementation("com.github.skydoves:landscapist-animation:2.4.7")
```

---

## Architecture Decisions

1. **Shared skeleton composables per card layout shape** — not per screen. Each card type gets a matching `XxxCardSkeleton` composable.
2. **`LolitaShimmerImage` wrapper** — skin-aware CoilImage wrapper that handles ShimmerPlugin + CircularRevealPlugin + failure state.
3. **ShimmerTheme via CompositionLocal** — provided inside LolitaTheme, derived from LolitaSkinConfig.
4. **Replace full-screen CPI only** — inline button spinners (BackupRestoreScreen, saving states) remain as CircularProgressIndicator.

## Shimmer Strategy

| Use case | Which shimmer | Why |
|----------|--------------|-----|
| Image loading placeholder | Landscapist `ShimmerPlugin` | Auto-lifecycle tied to loading state |
| Skeleton cards in lists | compose-shimmer `Modifier.shimmer()` | Generic modifier for any content |
| Image appearance | `CircularRevealPlugin` | Smooth circular clip-reveal animation |

**Never apply both shimmers on the same composable.**

---

## 8-Commit Execution Plan

### Commit 1: `feat: add compose-shimmer and landscapist dependencies`
- File: `app/build.gradle.kts` — add 4 dependencies
- Verify: `./gradlew.bat clean assembleDebug`

### Commit 2: `feat: add skin-aware ShimmerTheme and LolitaShimmerImage`
- 2A: `ui/theme/shimmer/ShimmerTheme.kt` (new) — LocalLolitaShimmerTheme, skinShimmerTheme()
- 2B: `ui/screen/common/LolitaShimmerImage.kt` (new) — CoilImage wrapper with ShimmerPlugin + CircularRevealPlugin
- 2C: `ui/screen/common/SkeletonShapes.kt` (new) — ShimmerRect, ShimmerCircle, ShimmerLine
- Update `Theme.kt` to provide LocalShimmerTheme
- Verify: `./gradlew.bat clean assembleDebug`

### Commit 3: `feat: add skeleton composables for item list cards`
- ItemCardSkeleton, ItemGridCardSkeleton, GalleryCardSkeleton
- Replace CircularProgressIndicator in ItemListScreen
- Verify: build + visual check

### Commit 4: `feat: add skeleton composables for coordinate, catalog, wishlist cards`
- CoordinateCardSkeleton, CoordinateGridCardSkeleton
- CatalogListCardSkeleton, CatalogGridCardSkeleton, CatalogGalleryCardSkeleton
- WishlistItemCardSkeleton, OutfitLogListItemCardSkeleton
- Replace CPI in: CoordinateListScreen, CatalogListContent, WishlistScreen, OutfitLogListScreen
- Verify: build + visual check

### Commit 5: `feat: add skeleton for detail screens and remaining list screens`
- ItemDetail, CoordinateDetail, CatalogDetail, OutfitLogDetail skeleton
- FilteredItemList, Recommendation, LocationDetail skeleton (reuse ItemCardSkeleton)
- Verify: build + navigate

### Commit 6: `feat: replace AsyncImage with LolitaShimmerImage in all card composables`
- 42 AsyncImage call sites across 26 files
- Priority: card composables → detail screens → edit screens → misc
- Special cases: ImageGalleryPager, FullScreenImageViewer, ImagePreviewDialog (circularRevealEnabled = false)
- Verify: `./gradlew.bat clean assembleDebug`

### Commit 7: `feat: add shimmer tokens to LolitaSkinConfig and update AGENTS.md`
- LolitaSkinConfig: +4 shimmer tokens (shimmerBaseColor/Dark, shimmerHighlightColor/Dark)
- SkinConfigs: +7 factory function values
- ShimmerTheme + LolitaShimmerImage consume new tokens
- AGENTS.md updated
- Verify: build

### Commit 8: `chore: remove unused Coil AsyncImage imports and clean up`
- Remove unused imports
- Verify: `./gradlew.bat clean assembleRelease`

---

## Dependency Graph

```
Commit 1 (deps)
    ├── Commit 2A (ShimmerTheme)
    ├── Commit 2B (LolitaShimmerImage)
    └── Commit 2C (SkeletonShapes)
            │
            ▼
      Commit 3 (Item skeletons)
      ┌─────┼──────────┐
      ▼     ▼          ▼
  Commit 4  Commit 5  Commit 6
  (lists)   (details) (AsyncImage migration)
      │     │          │
      └─────┼──────────┘
            ▼
      Commit 7 (tokens + AGENTS.md)
            ▼
      Commit 8 (cleanup)
```
