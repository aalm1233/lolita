# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Debug build
./gradlew.bat assembleDebug

# Release build
./gradlew.bat assembleRelease

# Clean build
./gradlew.bat clean assembleDebug
```

Room schema exports go to `app/schemas/`. The project uses KSP (not Kapt) for Room annotation processing. No unit test suite exists.

### USB Device Test Suite

```bash
export MSYS_NO_PATHCONV=1          # Git Bash path conversion fix (required)
bash run_device_test.sh            # Run UI automation test suite
```

Based on `adb` + `uiautomator` + `screencap`. Requires USB-connected Android device with debug APK installed. 14 test groups covering navigation, FAB operations, settings scrolling, backup/restore, Taobao import, and crash detection. Results output to `test_results.txt`, screenshots in `test_screenshots/`. ADB path defaults to `C:/Users/User/AppData/Local/Android/Sdk/platform-tools/adb.exe`, overridable via `ADB` env var.

Key device coordinates (1116×2480 @ 480dpi): bottom nav y=2330 (gesture zone y≥2372), top tabs y=159, FAB at (984, 2168). Compose pages have limited `uiautomator` text support — use screencap pixel diffing for verification.

## Architecture

Android app (Kotlin, Jetpack Compose, Material3) for managing Lolita fashion items. Single-module project, MVVM + Repository pattern + `domain/` layer (`domain/model/`, `domain/usecase/MatchingEngine.kt`). Compile SDK 35, Min SDK 26, Target SDK 35.

**Data flow:** `Entity (Room) → DAO → Repository → ViewModel (StateFlow) → Composable`

**Additional docs:** `AGENTS.md` contains the Chinese-language development collaboration guide — it's the more detailed sibling document. When CLAUDE.md and AGENTS.md conflict on factual claims, trust the actual code.

### Dependency Injection

Manual service locator via `AppModule` singleton (no Hilt/Dagger). Initialized in `LolitaApplication.onCreate()`. Repositories are lazy singletons. ViewModels receive repositories as constructor default parameters from `AppModule`. A `domain/` layer (`domain/model/`, `domain/usecase/`) houses business logic such as `MatchingEngine.kt`:

```kotlin
class ItemListViewModel(
    private val itemRepository: ItemRepository = AppModule.itemRepository()
) : ViewModel()
```

`AppModule` also provides `BackupManager`, `AppPreferences` (DataStore-based), and `RecommendationRepository`.

### Navigation

Sealed interface `Screen` defines routes with typed arguments. `LolitaNavHost` contains the full navigation graph. Bottom nav has 5 tabs: 服饰, 愿望单, 穿搭, 统计, 设置. Start destination is `ItemList`.

Settings sub-screens: BrandManage, CategoryManage, StyleManage, SeasonManage, BackupRestore, TaobaoImport, ThemeSelect.

Additional screens: CoordinateDetail → CoordinateEdit, CatalogDetail → CatalogEdit, LocationDetail → LocationEdit, OutfitLogList → OutfitLogDetail → OutfitLogEdit → QuickOutfitLog, Recommendation (cosine similarity + co-occurrence boost), FilteredItemList (stats drilldown), PriceManage → PriceEdit, PaymentManage → PaymentEdit. ItemList has 4 top tabs: 位置 (Location), 服饰 (Items), 套装 (Coordinate), 图鉴 (Catalog).

### Database

Room database `LolitaDatabase`, currently version 18. Migrations are defined inline in the database class. 23 entities (including `CatalogEntry`, `SharedLibrarySyncState`, and 6 `Remote*` remote-sync entities):

- **Item** → Brand (RESTRICT), Category (RESTRICT), Coordinate (RESTRICT) — core entity with status, priority, color, season, style, size, sizeChartImageUrl
- **Brand**, **Category**, **Style**, **Season** — preset data tables with `isPreset` flag
- **Category** has a `group` field (`CLOTHING`/`ACCESSORY`) added in migration v3→v4
- **Coordinate** — grouping entity for items, has `imageUrl` (added in v5→v6)
- **Price** → Item (CASCADE) — supports `FULL` or `DEPOSIT_BALANCE` pricing models
- **Payment** → Price (CASCADE) — tracks payment status, due dates, reminder scheduling, and `calendarEventId` (added in v4→v5)
- **OutfitLog** — daily outfit tracking with date, note, imageUrls (List<String> via Gson)
- **OutfitItemCrossRef** — many-to-many between Item and OutfitLog (both FKs CASCADE)
- **Location** — storage location tracking for items, has `imageUrl`
- **Source** — purchase source/shop tracking for items
- **CatalogEntry** — catalog/reference items for premium "图鉴" library, with imageUrl
- **SharedLibrarySyncState** — tracks shared library sync metadata for cross-instance sync
- **Remote\* entities** — 6 entities for remote sync (RemoteCoordinate, RemoteItem, etc.)

Type converters handle enums (`ItemStatus`, `ItemPriority`, `PriceType`, `CategoryGroup`) and `List<String>` (via Gson JSON).

### Image Storage

`ImageFileHelper` copies picked images to `context.filesDir/images/` with UUID filenames. Also supports downloading images from URLs. Paths stored as strings in entities. Coil loads images from these file paths.

### Payment Reminders & Calendar

`PaymentReminderScheduler` uses `AlarmManager` for scheduling reminders (exact alarms on Android 12+ with fallback to inexact). `CalendarEventHelper` creates device calendar events for payment due dates. `PaymentReminderReceiver` handles alarm broadcasts; `BootCompletedReceiver` reschedules reminders on device reboot.

### Backup & Import

`BackupManager` exports/imports the full database as JSON, and exports to CSV. Supports preview-before-import with cached parsed data. `TaobaoOrderParser` parses Taobao-exported `.xlsx` files (via Apache POI) extracting order details and style specs.

### Skin System

7 skins: DEFAULT (甜美粉), GOTHIC, CHINESE (中华), CLASSIC, NAVY (清风水手), COUNTRY (田园), VICTORIAN (维多利亚). Each skin defines its own colors, fonts, shapes, icons, and animations via `LolitaSkinConfig`. 45 files in `ui/theme/skin/`.

**Icon System:**
- **SkinIconProvider** — 45 Canvas-drawn icons per skin, organized in 5 sub-interfaces: NavigationIcons (5), ActionIcons (12), ContentIcons (13), ArrowIcons (9), StatusIcons (6)
- **BaseSkinIconProvider** — default fallback implementations
- 7 skin-specific providers: `SweetIconProvider`, `GothicIconProvider`, `ChineseIconProvider`, `ClassicIconProvider`, `NavyIconProvider`, `CountryIconProvider`, `VictorianIconProvider`
- **SkinIcon** composable renders icons; **IconKey** enum maps icon identifiers

**Animation System (`SkinAnimationProvider`):**
- **SkinClickFeedbackSpec** — pressScale, rippleColor, rippleStyle (SOFT/SHARP/INK/GLOW), particles
- **SkinNavigationSpec** — enter/exit/pop transitions, overlay effects
- **SkinListAnimationSpec** — appearDirection (FROM_BOTTOM/FROM_LEFT/FADE_SCALE), stagger delay, fling friction
- **SkinAmbientAnimationSpec** — background particles, card glow effects
- **TabSwitchAnimationSpec** — indicator animation, selected effect
- Plus: SkinTransitionSpec, CardAnimationSpec, InteractionFeedbackSpec

**Skin-Aware UI Components:**
- **SkinClickable** — custom ripple (`SkinRippleEffect`) + press scale + click particles (`SkinClickParticles`)
- **SkinTransitionOverlay** — full-screen transition effects with per-skin particles
- **SkinTabIndicator** — per-skin tab indicator styles (used in ItemListScreen, StatsPageScreen)
- **SkinCardGlow** — per-skin card glow/decoration effects
- **SkinBackgroundAnimation** — ambient particle system (hearts/crosses/clouds/sparkles/anchors per skin)
- **SkinNavigationOverlay** + **SkinNavigationTransitions** — per-skin screen enter/exit animations
- **SkinItemAppear** — staggered list item appear animations
- **SkinFlingBehavior** — per-skin list scroll friction

**Per-skin particle types** in `particles/`: SweetParticles, GothicParticles, ChineseParticles, ClassicParticles, NavyParticles, CountryParticles, VictorianParticles.

When adding new icons, implement in `BaseSkinIconProvider` and all 7 skin-specific providers. When adding new screens, use skin-aware components (`SkinClickable`, `GradientTopAppBar`, `SkinItemAppear` for lists, `SkinTabIndicator` for tabs).

### Recommendation Engine

`RecommendationRepository` uses cosine similarity on style/color/season vectors with co-occurrence boost from outfit history to suggest matching items.

### Outfit Logging & Widget

`OutfitLog` tracks daily outfits with images and linked items (many-to-many). `QuickOutfitLog` provides fast entry. `OutfitWidget` provides home screen widget. `DailyOutfitReminderScheduler` handles daily reminders.

## Key Conventions

- UI language is Chinese (zh-CN)
- Theme: 7-skin system (DEFAULT/GOTHIC/CHINESE/CLASSIC/NAVY/COUNTRY/VICTORIAN) via `LolitaSkinConfig`, each with custom colors, fonts, shapes, Canvas-drawn icons, and animations. Default is pink-based (`Pink400` = #FF69B4), light/dark support. See below "Skin Design Language" section for full details
- `GradientTopAppBar` is the standard top bar, defaults to `compact = true` (tighter padding + flower decorations). All screens use it — do not add `compact = false` unless there's a specific reason
- The outer `Scaffold` in `LolitaNavHost` sets `contentWindowInsets = WindowInsets(0,0,0,0)` to avoid double status bar padding — `GradientTopAppBar` handles `statusBarsPadding()` internally
- `LolitaCard` wraps content in rounded cards
- ViewModels use `MutableStateFlow`/`StateFlow` for UI state, `viewModelScope.launch` for async
- Edit screens: load data → update fields individually → validate on save → insert/update via repository
- Repositories are thin wrappers around DAOs, returning `Flow` for reactive updates
- Preset data (brands, categories, styles, seasons) uses `isPreset` flag but presets are deletable — no deletion guard on `isPreset`
- Tab-based screens (ItemListScreen, StatsPageScreen) use `HorizontalPager` for swipe gesture support, synced with `TabRow` via `pagerState`
- Brand selector in ItemEditScreen uses a searchable dialog (not dropdown) due to 200+ entries
- Items support multi-criteria filtering: status, category group, season, style, color, brand, plus text search
- Item deletion is blocked by RESTRICT foreign keys if the item is referenced by a Coordinate
- `LolitaCard` supports `variant` parameter: `CardVariant.DEFAULT` (standard), `CardVariant.GALLERY` (borderless/zero padding, image-dominant), `CardVariant.FEATURED` (high shadow/large padding, showcase), `CardVariant.COMPACT` (low shadow/tight padding/thin border)
- Detail pages use `LolitaSection` (iOS Settings-style: `SectionHeader` + row content + embedded dividers, wrapped in `LolitaCard`) and `SectionHeader` (left accent bar + title + optional action button + divider, skin-aware)
- Images use `ImageFrame` (picture-frame container with per-skin border/shadow/padding tokens)
- **All image loading must use `LolitaShimmerImage`** (Coil wrapper with shimmer loading + circular reveal animation + initial fallback), not bare `AsyncImage`. Exceptions: full-screen image viewers (`ImageGalleryPager`, `FullScreenImageViewer`, `ImagePreviewDialog`) use `circularRevealEnabled = false`; inline button spinners keep `CircularProgressIndicator`
- Skeleton loading: use `ShimmerRect`/`ShimmerLine`/`ShimmerCircle` from `ui/screen/common/SkeletonShapes.kt` for list page loading states, not `CircularProgressIndicator`. Each card type has an `XxxCardSkeleton` composable
- **Hero shared element transitions**: `SharedTransitionLayout` wraps the outer `Scaffold` in `LolitaNavHost`. `Modifier.heroSharedElement(key)` in `ui/screen/common/HeroSharedElement.kt` wraps `sharedElement` with skin-gate check and scope null-safety. 5 entity types have hero transitions: Item, Coordinate, Catalog, OutfitLog (single-image only), Location. Key naming: `itemImage-${id}`, etc.
- **Haze blur**: Top bar and bottom nav support per-skin Haze frosted glass via `skin.topBarBlurEnabled`/`skin.navBarBlurEnabled` tokens. `LocalHazeState` CompositionLocal created in `LolitaNavHost` and consumed by `GradientTopAppBar` and bottom nav
- Dark mode adaptation: Card/image borders parse `*Dark` variant tokens via `isSystemInDarkTheme()`. Status and priority colors use lighter 300-level Material colors in dark mode. Icon tints use `bright300` / `normal500` pattern
- **Release builds require `clean` first** — incremental compilation can miss cross-file dependencies causing runtime fallback behavior in release but not debug

## Permissions

The app requires: `READ_MEDIA_IMAGES` (or `READ/WRITE_EXTERNAL_STORAGE` on SDK ≤32), `CAMERA`, `SCHEDULE_EXACT_ALARM`, `POST_NOTIFICATIONS`, `READ/WRITE_CALENDAR`, `RECEIVE_BOOT_COMPLETED`, `INTERNET`.

## Dependencies

Compose BOM 2024.12.01, Room 2.7.0, Navigation Compose 2.8.5, Coil 2.7.0, Gson 2.11.0, Material Icons Extended, Apache POI 5.2.5, DataStore Preferences 1.1.1, Coroutines 1.9.0, Glance 1.1.1 (app widget), Haze 1.6.9 (blur/frosted glass), compose-shimmer 1.3.2 (skeleton loading), Landscapist 2.4.7 (Coil wrappers with shimmer/circular reveal), Telephoto 0.14.0 (zoomable image gestures). Kotlin 2.1.0, AGP 8.8.0, JVM target 17.

## Data Consistency Rules

When adding new features, follow these rules to maintain data structure and feature consistency:

### Database Changes

- Increment `LolitaDatabase` version and add a corresponding `Migration` object inline in the database class
- New entities must be added to `@Database(entities = [...])` and have a corresponding DAO
- Foreign keys: use CASCADE for child records (Price→Item, Payment→Price), use RESTRICT when the referenced entity should not be deleted while in use (Item→Brand, Item→Category, Item→Coordinate)
- New DAO must be exposed via `LolitaDatabase`, wrapped in a `Repository`, and registered as a lazy singleton in `AppModule`

### Entity Field Conventions

- Season and style on `Item` are stored as strings (not FK references to Season/Style tables). When renaming a Season or Style, the corresponding Repository must cascade-update all Item records that reference the old name
- `List<String>` fields use Gson JSON via `TypeConverters` — reuse existing converters
- Enum fields need a `TypeConverter` — add to existing converter class
- Image paths are stored as strings pointing to `context.filesDir/images/` — use `ImageFileHelper` for all image operations, and clean up files on entity deletion

### New Screen Checklist

- Add route to `Screen` sealed interface with typed arguments
- Add composable to `LolitaNavHost`
- Use `GradientTopAppBar` with `compact = true` (default)
- Use skin-aware components: `SkinClickable` for clickable elements, `LolitaCard` for cards
- If the screen has lists, apply `SkinItemAppear` modifier for staggered animations and `SkinFlingBehavior` for scroll friction
- If the screen has tabs, use `HorizontalPager` + `TabRow` + `SkinTabIndicator` pattern (see `ItemListScreen`)
- ViewModel must use `MutableStateFlow`/`StateFlow`, receive repositories via constructor default params from `AppModule`

### New Icon Checklist

- Implement in `BaseSkinIconProvider` (default implementation)
- Override in all 7 skin-specific providers: `SweetIconProvider`, `GothicIconProvider`, `ChineseIconProvider`, `ClassicIconProvider`, `NavyIconProvider`, `CountryIconProvider`, `VictorianIconProvider`
- Icons are Canvas-drawn, not Material icons — follow existing patterns in each provider

### Version Bumping

- Every feature update must bump the version in `app/build.gradle.kts`: increment `versionCode` by 1, and update `versionName` accordingly (current: versionCode=53, versionName="2.34.1")
- Bug fixes: bump patch version (e.g. 2.0 → 2.0.1)
- New features: bump minor version (e.g. 2.0 → 2.1)
- Breaking/major changes: bump major version (e.g. 2.0 → 3.0)

### Release Build

- After every update is complete (code changes + version bump), run `./gradlew.bat assembleRelease` to produce a release APK
- Verify the build succeeds before considering the task done

### Backup Compatibility

- `BackupManager` serializes/deserializes the full database as JSON — new entities and fields must be included in both export and import logic
- Import must handle missing fields gracefully (for backward compatibility with older backups)
- New entities need corresponding CSV export columns if applicable

### Historical Data Refresh

- When adding new features or fields that derive state from existing data (e.g. PENDING_BALANCE status derived from payment state), must consider historical data that predates the feature
- Add a one-time refresh query (e.g. `ItemDao.refreshPendingBalanceStatus()`) and call it on app startup in `LolitaApplication.onCreate()` via `Dispatchers.IO`
- Prefer a single bulk SQL UPDATE over iterating individual records for performance

## Skin Design Language

Seven skins, each with distinct visual identity. When adding new UI elements or modifying existing ones, follow the corresponding skin's design language.

### DEFAULT (甜美粉) — Sweet Pink

- Colors: Primary `#FF69B4`, Secondary `#E6E6FA` (Lavender), Background `#FFF0F5`. Dark mode primary container `#E91E8C`
- Font: System default
- Shapes: `RoundedCornerShape(16.dp)` — soft, bubbly
- Top bar decoration: `"✿"` (flower) at 70% opacity
- Icon style: Stroke `0.08f` (thickest), `Round` cap/join. Motifs: hearts, bows, flowers, petals, rounded forms
- Animations: Spring with `DampingRatioMediumBouncy` + `StiffnessLow`. Press scale `0.92f` (most pronounced). Transition 350ms with heart-shaped particles. Card enter: fade + scale from 85%, stagger 60ms
- Ripple: `#FF69B4` at 18% alpha
- Philosophy: Cute, playful, maximum sweetness. Bouncy animations, joyful energy

### GOTHIC (哥特暗黑) — Gothic Dark

- Colors: Primary `#4A0E4E` (deep purple), Secondary `#8B0000` (blood red), Background `#F5F0F5`. Dark mode background `#1A1A2E`, surface `#2D2D44`
- Font: Cinzel Regular (`cinzel_regular.ttf`)
- Shapes: `RoundedCornerShape(8.dp)` — sharp, angular
- Top bar decoration: `"✝"` (cross) at 50% opacity
- Icon style: Stroke `0.06f` (thinnest), `Butt` cap / `Miter` join — sharp edges. Motifs: gothic crosses, thorns, bat wings, pointed arches, pentagrams, coffins, iron crosses
- Animations: Slow, dramatic. Tab switch 500ms with `CubicBezierEasing(0.2f, 0f, 0.1f, 1f)`. Press scale `0.97f` (subtlest). Transition 600ms (longest) with crack lines in blood red. Card enter: fade + slide from 50% down, stagger 80ms
- Ripple: `#4A0E4E` at 25% alpha (most opaque)
- Philosophy: Dark, mysterious, dramatic. Sharp angles, religious symbolism, Victorian gothic. Slow deliberate animations convey weight

### CHINESE (中华风韵) — Chinese Elegance

- Colors: Primary `#C41E3A` (vermillion), Secondary `#DAA520` (goldenrod), Background `#FFF8F0`. Dark mode background `#1A1410`, surface `#2D2520`
- Font: Noto Serif SC Regular (`noto_serif_sc_regular.ttf`)
- Shapes: `RoundedCornerShape(4.dp)` — minimal rounding, almost square
- Top bar decoration: `"☁"` (cloud) at 60% opacity
- Icon style: Stroke `0.07f`, `Round` cap/join — ink brush feel. Motifs: clouds, plum blossoms, ink splashes, seal stamps, curved eaves, calligraphy brushes, jade rings, mountain silhouettes
- Animations: Fluid, organic. Tab switch 400ms with `CubicBezierEasing(0.4f, 0f, 0.2f, 1f)`. Press scale `0.95f`. Transition 450ms with radial ink wash + splash dots. Card enter: fade + slide from 33% right, stagger 70ms
- Ripple: `#E34234` (vermillion) at 15% alpha
- Philosophy: Traditional Chinese ink painting aesthetics. Calligraphic strokes, natural motifs, warm red-gold harmony. Fluid animations mimic brush painting

### CLASSIC (经典优雅) — Classic Elegance

- Colors: Primary `#722F37` (wine/burgundy), Secondary `#8B4513` (saddle brown), Background `#FAF5F0`. Dark mode background `#1A1515`, surface `#2D2525`
- Font: Playfair Display Regular (`playfair_display_regular.ttf`)
- Shapes: `RoundedCornerShape(12.dp)` — moderate, refined
- Top bar decoration: `"♠"` (spade) at 50% opacity
- Icon style: Stroke `0.065f`, `Round` cap/join — smooth, polished. Motifs: spades, crowns, scrollwork, Victorian arches, rococo dresses, pocket watches, ornate frames, wax seals, lace scallops, gold accent strokes
- Animations: Smooth, refined. Tab switch 380ms with `CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)`. Press scale `0.96f`. Transition 400ms with golden light sweep left-to-right. Card enter: fade + slide from 25% down, stagger 55ms
- Ripple: `#722F37` at 14% alpha
- Philosophy: Victorian and Rococo elegance. Wine and gold evoke luxury. Balanced animations convey sophistication and timeless grace

### NAVY (清风水手) — Sailor Lolita

- Colors: Primary `#4A90D9` (sky blue), Secondary `#DAA520` (gold), Background `#F0F8FF` (Alice Blue). Dark mode background `#0D1B2A`, surface `#1B2D44`
- Font: Pacifico Regular (`pacifico_regular.ttf`)
- Shapes: `RoundedCornerShape(14.dp)` — soft, between sweet and classic
- Top bar decoration: `"⚓"` (anchor) at 60% opacity
- Icon style: Stroke `0.07f`, `Round` cap/join — smooth, flowing. Motifs: anchors, rope knots, sailor collars, helms, starfish, shells, telescopes, lighthouses, life preservers, sailboats, compasses, waves
- Animations: Light and breezy. Tab switch 380ms with `CubicBezierEasing(0.3f, 0f, 0.2f, 1f)`. Press scale `0.94f`. Transition 380ms with water ripple rings from center. Card enter: fade + slide from 25% bottom, stagger 65ms
- Ripple: `#4A90D9` at 16% alpha, SOFT style
- Philosophy: Sailor Lolita freshness. Sky blue and gold evoke ocean breezes and nautical charm. Light animations feel like gentle waves

### COUNTRY (田园牧歌) — Pastoral Country

- Colors: Primary `#7C9A69` (sage green), Secondary `#C56759` (berry), Background `#F9F4E8` (cream). Dark mode background `#1F261C`, surface `#2D3529`
- Font: Cormorant Garamond Regular (`cormorant_garamond.ttf`)
- Shapes: `RoundedCornerShape(20.dp)` — softest, most rounded; button `18.dp`
- Top bar decoration: `"✿"` (flower) at 70% opacity
- Icon style: Stroke ~0.07-0.08f, `Round` cap/join — handcrafted, folk-art feel. Motifs: bows, wish jars, dress charms, rosettes, bonnets, wheat sheaves, butterflies, ribbons, postcards, wreaths, camera charms, chain links, swatch fans, letters, bell charms, coin purses, cabinets, pennants
- Animations: Gentle, pastoral. Tab switch with bouncy spring. Press scale `0.92f`. Transition 420ms. Card enter: fade + slide from 20% down, stagger 48ms. Ripple: SOFT style
- Ripple: `#7C9A69` at ~16% alpha, SOFT style, 450ms
- Philosophy: Cottagecore pastoral charm. Sage green and berry evoke English country gardens. Soft shapes, folk-art icons, gentle dignified animations

### VICTORIAN (维多利亚) — Victorian Elegance

- Colors: Primary `#7B1E3A` (burgundy), Secondary `#B8860B` (dark gold), Background `#FFF8F0` (ivory). Dark mode background `#1A1210`, surface `#2C1E18`
- Font: Cormorant Garamond Regular (`cormorant_garamond.ttf`)
- Shapes: `RoundedCornerShape(12.dp)` — refined, button `8.dp`
- Top bar decoration: `"⚜"` (fleur-de-lis) at 50% opacity
- Icon style: Stroke ~0.04-0.08f, `Round` cap — ornate, intricate. Motifs: scrollwork, shields, ornate frames, wax seals, ribbons, feather arrows, quills, stars, envelopes, bells, purses, cabinets, wreaths, curtains, medallions
- Animations: Refined, opulent. Tab switch with damped spring (`0.75f`). Press scale `0.92f`. Transition 440ms. Card enter: fade + slide from 20% down, stagger 40ms. Ripple: GLOW style
- Ripple: `#7B1E3A` at ~18% alpha, GLOW style, 480ms
- Philosophy: Victorian grandeur and opulence. Burgundy and gold evoke antique luxury. Ornate ornamentation, fleur-de-lis, heraldic motifs. Animations carry weight and ceremony

### Skin Quick Reference

| Aspect | DEFAULT | GOTHIC | CHINESE | CLASSIC | NAVY | COUNTRY | VICTORIAN |
|--------|---------|--------|---------|---------|------|---------|-----------|
| Corner Radius | 16dp | 8dp | 4dp | 12dp | 14dp | 20dp | 12dp |
| Stroke Width | 0.08f | 0.06f | 0.07f | 0.065f | 0.07f | 0.07f | 0.04-0.08f |
| Stroke Cap | Round | Butt | Round | Round | Round | Round | Round |
| Press Scale | 0.92f | 0.97f | 0.95f | 0.96f | 0.94f | 0.92f | 0.92f |
| Transition | 350ms | 600ms | 450ms | 400ms | 380ms | 420ms | 440ms |
| Font | System | Cinzel | Noto Serif SC | Playfair Display | Pacifico | Cormorant Garamond | Cormorant Garamond |
| Primary Motif | Hearts & Flowers | Crosses & Thorns | Clouds & Plums | Spades & Scrolls | Anchors & Waves | Bows & Rosettes | Fleur-de-lis & Scrolls |
