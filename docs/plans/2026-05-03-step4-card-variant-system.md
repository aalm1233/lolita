# Phase 4: Card Variant System — Design & Implementation Plan

## Context

Current `LolitaCard` is a single-variant composable used 68 times across 28 files. Multiple screens bypass it with raw `Card(...)` when they need different styling (gallery cards, inline sub-cards, note cards). `SectionHeader` is only adopted in 2 of 5 detail screens; the rest use ad-hoc section headers. `ImageFrame` is imported but never actually called.

## Design

### 1. CardVariant Enum

```kotlin
enum class CardVariant {
    DEFAULT,   // Current behavior — skin tokens as-is
    GALLERY,   // Image-dominant: no inner padding, no border, low elevation
    FEATURED,  // Prominent: higher elevation, larger corner radius, more padding
    COMPACT    // Minimal: tight padding, thin border, low elevation
}
```

### 2. Variant Tokens in LolitaSkinConfig

Add per-variant token groups. Each variant has its own `elevation`, `borderStroke`, `innerPadding`, `shape` (as `Shape` override, null = use default `cardShape`):

| Token | DEFAULT | GALLERY | FEATURED | COMPACT |
|-------|---------|---------|----------|---------|
| elevation | `cardElevation` | 0dp | `cardElevation * 2` | `cardElevation / 2` |
| borderStroke | `cardBorderStroke` | null | `cardBorderStroke` | thin (1dp) |
| innerPadding | `cardInnerPadding` | 0dp | `cardInnerPadding + 4dp` | 8dp |
| shape | null (use cardShape) | null | null | null |

New tokens added to LolitaSkinConfig:
- `galleryCardElevation: Dp`
- `galleryCardBorderStroke: BorderStroke?`
- `galleryCardInnerPadding: Dp`
- `featuredCardElevation: Dp`
- `featuredCardBorderStroke: BorderStroke?`
- `featuredCardInnerPadding: Dp`
- `compactCardElevation: Dp`
- `compactCardBorderStroke: BorderStroke?`
- `compactCardInnerPadding: Dp`

### 3. Updated LolitaCard API

```kotlin
@Composable
fun LolitaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    variant: CardVariant = CardVariant.DEFAULT,
    content: @Composable () -> Unit
)
```

The composable selects tokens based on variant. Backward compatible — default variant = current behavior.

### 4. LolitaSection Component

iOS Settings-style grouped container: `SectionHeader` + content inside a single `LolitaCard`, with embedded dividers between child rows.

```kotlin
@Composable
fun LolitaSection(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
    content: LolitaSectionScope.() -> Unit
)
```

`LolitaSectionScope` provides:
- `row(content: @Composable () -> Unit)` — adds a content row with optional divider before it
- `row(title: String, value: String)` — convenience for key-value rows

This replaces the pattern of `LolitaCard { SectionHeader("title"); content }` which is currently used in ItemDetailScreen and StatsScreen.

### 5. Detail Screen Migration Plan

| Screen | Current Pattern | Target Pattern |
|--------|----------------|----------------|
| ItemDetailScreen | `LolitaCard { SectionHeader + content }` × 5 | `LolitaSection("title") { row { ... } }` × 5 |
| CatalogDetailScreen | Private `CatalogDetailSection()` (bold text only) | `LolitaSection("title") { row { ... } }` |
| CoordinateDetailScreen | Raw `Text(titleMedium)` headers | `SectionHeader` → then `LolitaSection` |
| OutfitLogDetailScreen | Raw `Text(titleSmall, Bold)` headers | `LolitaSection("title") { row { ... } }` |
| LocationDetailScreen | Raw `Text(titleSmall)` + `HorizontalDivider` | `LolitaSection("title") { row { ... } }` |
| StatsScreen | `SectionHeader` inside `LolitaCard` | `LolitaSection("title") { ... }` |

### 6. Gallery Card Unification

`GalleryCard.kt` (standalone, uses raw `Card(...)`) → `LolitaCard(variant = GALLERY)`.

The GalleryCard's image-dominant layout (full-bleed image, gradient text overlay, `combinedClickable`) stays as the content lambda, but the card chrome comes from LolitaCard with gallery variant tokens.

Similarly, `CatalogGalleryCard` in CatalogListContent uses LolitaCard already — just needs `variant = GALLERY`.

### 7. Raw Card Replacement

Replace raw `Card(...)` calls with appropriate LolitaCard variants:
- ItemDetailScreen: 3 raw Cards (price entries, empty state) → `LolitaCard(variant = COMPACT)`
- OutfitLogDetailScreen: 1 raw Card (note) → `LolitaCard(variant = COMPACT)`
- CatalogDetailScreen: 2 raw Cards (reference URL, remote notice) → `LolitaCard(variant = COMPACT)`
- CoordinateDetailScreen: 1 raw Card (empty state) → `LolitaCard(variant = COMPACT)`

## Commit Plan

| # | Commit | Files | Scope |
|---|--------|-------|-------|
| 1 | Add CardVariant tokens to LolitaSkinConfig + 7 factories | LolitaSkinConfig.kt, SkinConfigs.kt | Token layer only |
| 2 | Update LolitaCard + create LolitaSection | LolitaCard.kt, LolitaSection.kt (new) | Component layer |
| 3 | Migrate gallery cards to variant | GalleryCard.kt, CatalogListContent.kt | Gallery unification |
| 4 | Migrate detail screen section headers | CatalogDetailScreen, CoordinateDetailScreen, OutfitLogDetailScreen, LocationDetailScreen | Section migration |
| 5 | Migrate ItemDetailScreen + StatsScreen to LolitaSection | ItemDetailScreen, StatsScreen | Section migration |
| 6 | Replace raw Card() calls | All detail screens | Variant adoption |
| 7 | Update AGENTS.md | AGENTS.md | Docs |
