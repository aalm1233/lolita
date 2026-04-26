# UI Visual Refinement Design — Universal Base Layer

## Problem

The current UI lacks visual refinement despite having a rich skin system. Key issues:

1. **Cards are flat** — `LolitaCard` uses 75% alpha surface + 0 elevation + no border, feeling like translucent color blocks rather than styled containers.
2. **Detail pages lack visual hierarchy** — Information is stacked as plain text rows with no section separation, color coding, or decorative accents.
3. **Images lack framing** — `AsyncImage` is clipped directly with no border, shadow, or padding, missing the "framed artwork" feel appropriate for a fashion app.

## Approach

**Style Tokens + Universal Component Upgrades** — extend `LolitaSkinConfig` with a small set of visual tokens, then upgrade the shared components (`LolitaCard`, `GalleryCard`, detail pages) to consume them. All 7 skins benefit immediately. Per-skin custom decorations will be added in future iterations.

## Design

### 1. Style Tokens in `LolitaSkinConfig`

Add these properties to `LolitaSkinConfig` data class and `SkinConfigBuilder`:

```kotlin
val cardElevation: Dp                    // Card shadow elevation (currently hardcoded 0.dp)
val cardBorderStroke: BorderStroke?       // Optional card border (currently none)
val imageFrameElevation: Dp              // Image area shadow
val imageFrameStroke: BorderStroke?       // Optional image border
val imageFramePadding: Dp                // Padding between image and frame border
val sectionAccentColor: Color            // Section header left accent bar color
val sectionAccentWidth: Dp               // Section header accent bar width
val sectionDividerColor: Color           // Section divider line color
val sectionDividerHeight: Dp             // Section divider thickness
```

#### Per-Skin Defaults

| Skin | cardElevation | cardBorder | imageFrame | accentColor |
|------|-------------|-----------|------------|-------------|
| DEFAULT 甜美粉 | 1dp | null | null border, 2dp elevation, 0dp padding | Pink400 |
| GOTHIC 哥特暗黑 | 2dp | darkPurple 0.5dp | silverGray 0.5dp border, 3dp elevation, 2dp padding | Purple |
| CHINESE 中华风韵 | 1dp | vermillion 0.5dp | vermillion 0.5dp border, 2dp elevation, 2dp padding | Vermillion |
| CLASSIC 经典优雅 | 2dp | darkGold 0.5dp | darkGold 0.5dp border, 3dp elevation, 2dp padding | Wine |
| NAVY 清风水手 | 1dp | null | null border, 2dp elevation, 0dp padding | SkyBlue |
| COUNTRY 牧歌田园 | 1dp | null | null border, 2dp elevation, 0dp padding | Berry |
| VICTORIAN 维多利亚 | 3dp | gold 1dp | gold 1dp border, 4dp elevation, 3dp padding | DeepRose |

### 2. `LolitaCard` Upgrade

**File:** `app/src/main/java/com/lolita/app/ui/screen/common/LolitaCard.kt`

Current: `elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)`, no border.

Change to:
- Use `skin.cardElevation` instead of `0.dp`
- If `skin.cardBorderStroke != null`, use `OutlinedCard` or `Card` with `border = skin.cardBorderStroke`
- Keep `containerColor = surface.copy(alpha = 0.75f)` for background transparency

### 3. `GalleryCard` Upgrade

**File:** `app/src/main/java/com/lolita/app/ui/screen/common/GalleryCard.kt`

Changes:
- Apply `skin.cardElevation` to card elevation
- Apply `skin.cardBorderStroke` to card border
- Image area: wrap `AsyncImage` in a `Surface` with `skin.imageFrameStroke` as border and `skin.imageFrameElevation` for shadow
- Add `skin.imageFramePadding` between image and frame

### 4. New `SectionHeader` Component

**File:** `app/src/main/java/com/lolita/app/ui/screen/common/SectionHeader.kt`

Reusable composable for detail page section headers:

```kotlin
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    val skin = LolitaSkin.current
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left accent bar
        Box(
            modifier = Modifier
                .width(skin.sectionAccentWidth)
                .height(20.dp)
                .clip(RoundedCornerShape(skin.sectionAccentWidth / 2))
                .background(skin.sectionAccentColor)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        action?.invoke()
    }
    // Optional divider
    HorizontalDivider(
        color = skin.sectionDividerColor,
        thickness = skin.sectionDividerHeight
    )
}
```

### 5. Image Frame Container

**File:** `app/src/main/java/com/lolita/app/ui/screen/common/ImageFrame.kt`

Reusable composable for framed image display:

```kotlin
@Composable
fun ImageFrame(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val skin = LolitaSkin.current
    Surface(
        modifier = modifier,
        shape = skin.cardShape,
        color = MaterialTheme.colorScheme.surface,
        border = skin.imageFrameStroke,
        shadowElevation = skin.imageFrameElevation
    ) {
        Box(modifier = Modifier.padding(skin.imageFramePadding)) {
            content()
        }
    }
}
```

### 6. Detail Page Upgrades

**Primary target:** `ItemDetailScreen.kt`

Changes:
- Wrap each information section (基本信息, 价格信息, 风格属性, etc.) in `LolitaCard`
- Replace section title Text() with `SectionHeader`
- Wrap main image in `ImageFrame`
- Apply `SectionHeader` divider between sections

**Secondary targets (same pattern):**
- `CoordinateDetailScreen.kt`
- `CatalogDetailScreen.kt`
- `OutfitLogDetailScreen.kt`
- `LocationDetailScreen.kt`

### 7. Dark Mode Support

All new tokens need dark-mode variants. Follow existing pattern:
- `sectionAccentColor` → derived from `accentColor`/`accentColorDark`
- `cardBorderStroke` → darken/lighten as needed for dark mode
- `sectionDividerColor` → use `outlineVariant` from color scheme (auto dark-mode aware)
- `imageFrameStroke` → same approach as card border

## Scope

**In scope (this iteration):**
- Style tokens in `LolitaSkinConfig`
- `LolitaCard` upgrade
- `GalleryCard` upgrade
- `SectionHeader` component
- `ImageFrame` component
- `ItemDetailScreen` refactor as reference implementation

**Out of scope (future iterations):**
- Per-skin Canvas-drawn decorations (`SkinDecorationProvider`)
- Other detail pages (apply same pattern later)
- Dialog skinning
- New skin-specific visual effects

## Success Criteria

- All 7 skins show visible improvement in card depth, image framing, and section hierarchy
- `ItemDetailScreen` demonstrates the full pattern with clear visual sections
- No regression in existing skin animations/particles
- `assembleDebug` and `assembleRelease` both pass
