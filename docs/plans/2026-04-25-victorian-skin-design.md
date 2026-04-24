# Victorian Skin Design

## Overview

Add a 7th skin — **VICTORIAN** (维多利亚) — to the existing 6 skins (DEFAULT, GOTHIC, CHINESE, CLASSIC, NAVY, COUNTRY). The skin follows a dark-red velvet Victorian aesthetic with gold accents, rose petal particles, and fully custom Canvas icons.

## Identity

| Attribute | Value |
|---|---|
| Enum name | `VICTORIAN` |
| Display name | 维多利亚 |
| Decoration | ⚜ (alpha 0.5) |
| Font | Cormorant Garamond (reused from COUNTRY) |

## Color Scheme

### Light

| Role | Color | Hex |
|---|---|---|
| Primary | Burgundy | `#7B1E3A` |
| Secondary | Dark Gold | `#B8860B` |
| Background | Ivory | `#FFF8F0` |
| Surface/Card | Cream | `#FFF5E6` |
| On Surface | Dark Brown | `#3E2723` |
| Accent | Deep Rose | `#9C254D` |

### Dark

| Role | Color | Hex |
|---|---|---|
| Primary | Rose Red | `#C4566A` |
| Secondary | Gold | `#D4A843` |
| Background | Deep Brown | `#1A1210` |
| Surface/Card | Dark Brown | `#2C1E18` |
| On Surface | Warm White | `#F5E6D3` |
| Accent | Bright Rose | `#E07088` |

### Gradients

- Light: burgundy → dark gold
- Dark: deep brown → dark rose red

### Shapes

- Card: `RoundedCornerShape(12.dp)`
- Button: `RoundedCornerShape(8.dp)`

## Icons — VictorianIconProvider

All 51 icons drawn with Canvas, uniform visual language:

- Stroke: medium-thick (2-3dp), serif/floral decorative endpoints
- Small scrollwork/floral accents at icon corners
- Decorative elements rendered with semi-transparent overlay

| Category | Count | Style |
|---|---|---|
| Navigation (5) | Home, Wishlist, Outfit, Stats, Settings | Ribbon/ribbon base shape, icon centered |
| Action (16) | Add, Delete, Edit, Save, etc. | Main icon body + subtle floral corner accents |
| Content (14) | Star, Image, Camera, Link, etc. | Vintage picture frame border feel |
| Arrow (9) | ArrowBack, Forward, Expand, etc. | Arrowheads with feather fletching decoration |
| Status (7) | CheckCircle, Warning, Error, etc. | Outer ring as vintage badge/wax seal shape |

File: `skin/icon/VictorianIconProvider.kt`

## Animation — VictorianAnimationProvider

| Property | Design |
|---|---|
| skinTransition | Velvet curtain reveal: horizontal expand from center, fading in new skin colors |
| tabSwitchAnimation | Slow elegant spring, dampingRatio = 0.75 |
| cardAnimation | Light scale + fade-in, cardGlow = true (dark gold glow) |
| interactionFeedback | Ripple style = `GLOW` (gold glow ripple) |
| clickFeedback | Small gold dust particles on click |
| navigation | Default implementation (fade + slight slide) |
| listAnimation | Staggered fade-in, 40ms interval |
| ambientAnimation | Background floating particles (see below) |
| appearDirection | `FROM_BOTTOM` (rising like a curtain) |

## Particles

### Ambient (2 types)

1. **VictorianRosePetal** — Deep red semi-transparent irregular ellipse, slow rotation + drift down, lifetime 6-8s
2. **VictorianGoldDust** — Tiny gold light points drifting upward slowly, like candlelit dust, lifetime 3-5s

### Click

- 5-8 gold sparkles radiating outward from click point, fast fade-out

File: `skin/animation/particles/VictorianParticles.kt` (ambient + click combined)

## File Changes

### New Files (3)

| File | Content |
|---|---|
| `skin/icon/VictorianIconProvider.kt` | 51 custom Canvas icons |
| `skin/animation/VictorianAnimationProvider.kt` | Animation provider |
| `skin/animation/particles/VictorianParticles.kt` | Rose petal + gold dust ambient + click particles |

### Modified Files (2)

| File | Change |
|---|---|
| `SkinType.kt` | Add `VICTORIAN` enum entry |
| `SkinConfigs.kt` | Add `victorianSkinConfig()` factory + mapping in `getSkinConfig()` |

### No Changes Needed

- `ThemeSelectScreen.kt` — already iterates `SkinType.entries`
- `IconKey` — no new icon keys
- `BaseSkinIconProvider` — unchanged, Victorian extends it
- `AppPreferences` — already supports any `SkinType.valueOf()`
- Navigation/Screen — auto-included
