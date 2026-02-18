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

Room schema exports go to `app/schemas/`. The project uses Kapt (not KSP) for Room annotation processing. No test suite exists.

## Architecture

Android app (Kotlin, Jetpack Compose, Material3) for managing Lolita fashion items. Single-module project, MVVM + Repository pattern. Compile SDK 35, Min SDK 26.

**Data flow:** `Entity (Room) → DAO → Repository → ViewModel (StateFlow) → Composable`

### Dependency Injection

Manual service locator via `AppModule` singleton (no Hilt/Dagger). Initialized in `LolitaApplication.onCreate()`. Repositories are lazy singletons. ViewModels receive repositories as constructor default parameters from `AppModule`:

```kotlin
class ItemListViewModel(
    private val itemRepository: ItemRepository = AppModule.itemRepository()
) : ViewModel()
```

`AppModule` also provides `BackupManager` and `AppPreferences` (DataStore-based).

### Navigation

Sealed interface `Screen` defines routes with typed arguments. `LolitaNavHost` contains the full navigation graph. Bottom nav has 5 tabs: 服饰, 愿望单, 穿搭, 统计, 设置. Start destination is `ItemList`.

Settings sub-screens: BrandManage, CategoryManage, StyleManage, SeasonManage, BackupRestore, TaobaoImport.

### Database

Room database `LolitaDatabase`, currently version 5. Migrations are defined inline in the database class. 10 entities with foreign key relationships:

- **Item** → Brand (RESTRICT), Category (RESTRICT), Coordinate (RESTRICT) — core entity with status, priority, color, season, style, size, sizeChartImageUrl
- **Brand**, **Category**, **Style**, **Season** — preset data tables with `isPreset` flag
- **Category** has a `group` field (`CLOTHING`/`ACCESSORY`) added in migration v3→v4
- **Price** → Item (CASCADE) — supports `FULL` or `DEPOSIT_BALANCE` pricing models
- **Payment** → Price (CASCADE) — tracks payment status, due dates, reminder scheduling, and `calendarEventId` (added in v4→v5)
- **OutfitItemCrossRef** — many-to-many between Item and OutfitLog

Type converters handle enums (`ItemStatus`, `ItemPriority`, `PriceType`, `CategoryGroup`) and `List<String>` (via Gson JSON).

### Image Storage

`ImageFileHelper` copies picked images to `context.filesDir/images/` with UUID filenames. Also supports downloading images from URLs. Paths stored as strings in entities. Coil loads images from these file paths.

### Payment Reminders & Calendar

`PaymentReminderScheduler` uses `AlarmManager` for scheduling reminders (exact alarms on Android 12+ with fallback to inexact). `CalendarEventHelper` creates device calendar events for payment due dates. `PaymentReminderReceiver` handles alarm broadcasts; `BootCompletedReceiver` reschedules reminders on device reboot.

### Backup & Import

`BackupManager` exports/imports the full database as JSON, and exports to CSV. Supports preview-before-import with cached parsed data. `TaobaoOrderParser` parses Taobao-exported `.xlsx` files (via Apache POI) extracting order details and style specs.

## Key Conventions

- UI language is Chinese (zh-CN)
- Theme: Pink-based palette (`Pink400` = #FF69B4), light/dark support
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

## Permissions

The app requires: `READ_MEDIA_IMAGES` (or `READ/WRITE_EXTERNAL_STORAGE` on SDK ≤32), `CAMERA`, `SCHEDULE_EXACT_ALARM`, `POST_NOTIFICATIONS`, `READ/WRITE_CALENDAR`, `RECEIVE_BOOT_COMPLETED`, `INTERNET`.

## Dependencies

Compose BOM 2024.12.01, Room 2.7.0, Navigation Compose 2.8.5, Coil 2.7.0, Gson 2.11.0, Material Icons Extended, Apache POI 5.2.5, DataStore Preferences 1.1.1, Coroutines 1.9.0. Kotlin 2.1.0, AGP 8.8.0, JVM target 17.
