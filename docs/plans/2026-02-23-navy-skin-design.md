# Navy Skin Design — 清风水手

Date: 2026-02-23

## Overview

新增第5套皮肤 NAVY（清风水手），以水手Lolita为美学核心，天蓝+白+金配色，清新活泼的少女航海感。

## Color System

### Light Mode
| Token | Value | Description |
|-------|-------|-------------|
| Primary | `#4A90D9` | 天蓝 |
| Secondary | `#DAA520` | 金色（绳结/金属扣点缀） |
| Background | `#F0F8FF` | Alice Blue |
| Surface | `#FFFFFF` | 白色 |
| Card | `#F5FAFF` | 微蓝白 |
| Accent | `#4A90D9` | 天蓝 |
| Gradient | `#4A90D9` → `#87CEEB` | 天蓝渐变 |

### Dark Mode
| Token | Value | Description |
|-------|-------|-------------|
| Primary | `#5BA0E9` | 亮天蓝 |
| Background | `#0D1B2A` | 深海蓝黑 |
| Surface | `#1B2D44` | 深蓝灰 |
| Card | `#1B2D44` | 深蓝灰 |
| Accent | `#5BA0E9` | 亮天蓝 |
| Primary Container | `#2E6EB5` | 中蓝 |
| Gradient | `#1B3A5C` → `#2E6EB5` | 深海渐变 |

Ripple: `#4A90D9` at 16% alpha

## Typography & Shape

- **Font:** Pacifico Regular (`pacifico_regular.ttf`) — 手写连笔风格
- **Card Shape:** `RoundedCornerShape(14.dp)`
- **Button Shape:** `RoundedCornerShape(14.dp)`
## Top Bar Decoration

- Symbol: `"⚓"`
- Alpha: `0.6f`
- Animated: `true`（轻微摇摆）

## Icon System

- Stroke width: `0.07f`
- Stroke cap: `Round`
- Stroke join: `Round`
- Motifs: 锚、绳结、水手领、舵轮、海星、贝壳、望远镜、灯塔、救生圈、帆船
- 45 Canvas-drawn icons across 5 categories (Navigation 5, Action 12, Content 13, Arrow 9, Status 6)

## Animation System

### Interaction Feedback
- Press scale: `0.94f`
- Scale animation: Spring `DampingRatioMediumBouncy` + `StiffnessMediumLow`
- Ripple style: `SOFT`
- Ripple duration: `400ms`
- Click particles: 小气泡上浮消散

### Screen Transitions
- Duration: `380ms`
- Enter: fadeIn + slideInHorizontally (30% from right)
- Exit: fadeOut + slideOutHorizontally (30% to left)
- Overlay: 水波纹从中心扩散, `#4A90D9` at 10% alpha, 400ms

### Tab Switch
- Duration: `380ms`
- Easing: `CubicBezierEasing(0.3f, 0f, 0.2f, 1f)`
- Indicator: 波浪形底部指示器

### Card/List Animation
- Appear direction: `FROM_BOTTOM`
- Appear offset: `40f`
- Stagger delay: `65ms`
- Enter duration: `350ms`
- Fling friction: `1.1f`

### Ambient Animation
- Background enabled: `true`
- Particle count: `22`
- Particle types: 小锚(40%) + 绳结(30%) + 气泡(30%)
- Cycle duration: `8000..15000ms`
- Alpha range: `0.08f..0.25f`
- Card glow: `true`（淡蓝色光晕）

## Skin Quick Reference

| Aspect | DEFAULT | GOTHIC | CHINESE | CLASSIC | NAVY |
|--------|---------|--------|---------|---------|------|
| Corner Radius | 16dp | 8dp | 4dp | 12dp | 14dp |
| Stroke Width | 0.08f | 0.06f | 0.07f | 0.065f | 0.07f |
| Stroke Cap | Round | Butt | Round | Round | Round |
| Press Scale | 0.92f | 0.97f | 0.95f | 0.96f | 0.94f |
| Transition | 350ms | 600ms | 450ms | 400ms | 380ms |
| Font | System | Cinzel | Noto Serif SC | Playfair | Pacifico |
| Decoration | ✿ | ✝ | ☁ | ♠ | ⚓ |
| Primary | Pink | Deep Purple | Vermillion | Wine | Sky Blue |

## Files to Create

1. `NavyIconProvider.kt` — 45 Canvas-drawn nautical icons
2. `NavyAnimationProvider.kt` — animation config
3. `NavyParticles.kt` — anchor + rope knot particles
4. `NavyBubbleParticle.kt` — bubble particles
5. `pacifico_regular.ttf` — font file in `res/font/`

## Files to Modify

1. `SkinType.kt` — add `NAVY` enum value
2. `SkinConfigs.kt` — add `navySkinConfig()` + `getSkinConfig()` branch
3. `SkinNavigationTransitions.kt` — add navy transition functions
4. `SkinBackgroundAnimation.kt` — add NAVY branch in `createParticles()`
