# Payment Calendar Liquid Glass Redesign

**Date**: 2026-05-14
**Scope**: Payment calendar tab (付款年历) only — YearHeader, MonthCard grid, PaymentInfoCard, empty state card

## Motivation

Redesign the payment calendar cards to adopt iOS 26 "Liquid Glass" design language:
- Continuous curvature (squircle) corners replacing square `RoundedCornerShape(0.dp)`
- Frosted glass material with specular highlights and depth
- Content adjustments to prevent clipping from new corner geometry

## Architecture

Two new files, no changes to existing component infrastructure:

| File | Purpose |
|------|---------|
| `ui/theme/shape/SquircleShape.kt` | Custom `Shape` with continuous curvature corners |
| `ui/screen/common/GlassSurface.kt` | Standalone glass container composable |

No changes to `LolitaCard`, `CardVariant`, or skin configs. GlassSurface is an independent component.

## GlassSurface Component

```kotlin
@Composable
fun GlassSurface(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    shape: Shape = SquircleShape(16.dp),
    blurRadius: Dp = 12.dp,
    glassAlpha: Float = 0.65f,
    highlightAlpha: Float = 0.15f,
    elevation: Dp = 2.dp,
    hazeState: HazeState? = null,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    content: @Composable () -> Unit
)
```

### Visual Layers (bottom to top)

1. **Shadow layer** — `Modifier.shadow(elevation, shape)` for floating depth
2. **Glass layer** — semi-transparent background + optional Haze blur
3. **Highlight border** — `Modifier.border()` with gradient stroke (bright top, dim bottom), simulating light hitting glass edge

### Haze Strategy

- When `hazeState != null` (user set a background image): apply `hazeEffect` for real backdrop blur
- When `hazeState == null` (no background): simulate glass via semi-transparent background + subtle vertical gradient (no actual blur needed — nothing interesting behind the card)
- Highlight border and shadow apply in both modes

## SquircleShape

Implements `Shape` using cubic Bezier curves with control point coefficient `0.63` (vs standard `0.552` for circular arcs). The larger coefficient causes the curve to begin earlier from the straight edge, creating continuous curvature that flares like a fan at corners.

```kotlin
class SquircleShape(private val radius: Dp) : Shape
```

Key detail: `c = r * 0.63f` — the control point distance that determines how "flared" the corner feels. At `0.552` it's a standard rounded corner; at `0.63` it's a visible squircle.

## Card-by-Card Changes

All changes confined to `PaymentCalendarScreen.kt`.

### YearHeader

- **Shape**: `RoundedCornerShape(0.dp)` → `SquircleShape(20.dp)`
- **Container**: `Card(...)` → `GlassSurface(...)`
- **Padding**: `4.dp` → `12.dp` (horizontal), `8.dp` (vertical)
- **Width**: Keep `fillMaxWidth()`

### MonthCard (×12, 3 rows × 4 columns)

- **Shape**: `RoundedCornerShape(0.dp)` → `SquircleShape(12.dp)`
- **Container**: `Card(...)` → `GlassSurface(...)`
- **Height**: `42.dp` → `48.dp` (extra 6dp for corner breathing room)
- **Grid gap**: `3.dp` → `4.dp`
- **Font sizes**: month label 11sp → 10sp, amounts 9sp → 8sp, overdue 8sp → 7sp
- **Content padding**: `horizontal=6.dp, vertical=4.dp`

### PaymentInfoCard

- **Shape**: `RoundedCornerShape(0.dp)` → `SquircleShape(16.dp)`
- **Container**: `Card(...)` → `GlassSurface(...)`
- **Content padding**: keep `12.dp`
- **Status badge**: reposition with top padding to clear the upper squircle curve

### Empty State Card

- **Shape**: `RoundedCornerShape(0.dp)` → `SquircleShape(16.dp)`
- **Container**: `Card(...)` → `GlassSurface(...)`
- **Content padding**: keep `12.dp`

## Visual Parameters (Unified)

| Parameter | Value | Notes |
|-----------|-------|-------|
| blurRadius | 12dp | Backdrop blur intensity |
| glassAlpha | 0.65f | Semi-transparent background opacity |
| highlightAlpha | 0.15f | Top-edge specular highlight |
| elevation | 2dp | Floating shadow depth |
| highlight gradient | top → bottom | Simulates overhead light source |

## Content Safety Rules

1. All cards get `contentPadding` sufficient to keep text inside the squircle safe zone
2. MonthCard height increased to compensate for corner intrusion
3. Font sizes in MonthCard reduced by 1sp across the board
4. PaymentInfoCard status badge gets extra top padding

## What's NOT Changed

- Payment calendar ViewModel, data flow, and business logic
- Haze source wiring (background image → hazeSource pattern unchanged)
- Bottom sheet, camera/gallery launchers, full-screen viewer
- All other stats tabs (overview, distribution, trend, wishlist)
- `LolitaCard`, `CardVariant`, skin configs, or any existing component infrastructure
