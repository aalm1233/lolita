# 皮肤系统 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a skin/theme system with 4 built-in skins (Default Pink, Gothic, Chinese, Classic Lolita), each with full customization of colors, fonts, shapes, and decorations, supporting light/dark modes.

**Architecture:** Extend existing Material3 theme with a `CompositionLocal`-based `LolitaSkinConfig`. Each skin defines its own `ColorScheme` (light+dark), `Typography`, card shape, and TopAppBar decoration. Replace all hardcoded Pink references with `MaterialTheme.colorScheme` properties or `LolitaSkin.current` accessors.

**Tech Stack:** Jetpack Compose Material3, CompositionLocal, DataStore Preferences, bundled .ttf fonts.

---

### Task 1: Add font files to resources

**Files:**
- Create: `app/src/main/res/font/cinzel_regular.ttf` (Gothic)
- Create: `app/src/main/res/font/noto_serif_sc_regular.ttf` (Chinese - Noto Serif SC subset)
- Create: `app/src/main/res/font/playfair_display_regular.ttf` (Classic)

**Step 1: Download fonts**

Download free fonts from Google Fonts:
- Cinzel Regular (Gothic skin) — Latin serif, ~50KB
- Noto Serif SC Regular (Chinese skin) — CJK serif, ~5MB subset
- Playfair Display Regular (Classic skin) — elegant serif, ~100KB

Place files in `app/src/main/res/font/`. Verify they appear in Android Studio resource browser.

**Step 2: Commit**

```bash
git add app/src/main/res/font/
git commit -m "feat: add bundled font files for skin system"
```

---

### Task 2: Create SkinType enum and LolitaSkinConfig data class

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/SkinType.kt`
- Create: `app/src/main/java/com/lolita/app/ui/theme/LolitaSkinConfig.kt`

**Step 1: Create SkinType enum**

```kotlin
// SkinType.kt
package com.lolita.app.ui.theme

enum class SkinType {
    DEFAULT, GOTHIC, CHINESE, CLASSIC
}
```

**Step 2: Create LolitaSkinConfig data class**

```kotlin
// LolitaSkinConfig.kt
package com.lolita.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily

data class LolitaSkinConfig(
    val skinType: SkinType,
    val name: String,
    val lightColorScheme: ColorScheme,
    val darkColorScheme: ColorScheme,
    val gradientColors: List<Color>,
    val gradientColorsDark: List<Color>,
    val accentColor: Color,
    val accentColorDark: Color,
    val cardColor: Color,
    val cardColorDark: Color,
    val fontFamily: FontFamily,
    val typography: Typography,
    val cardShape: Shape,
    val buttonShape: Shape,
    val topBarDecoration: String,
    val topBarDecorationAlpha: Float,
)
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/SkinType.kt
git add app/src/main/java/com/lolita/app/ui/theme/LolitaSkinConfig.kt
git commit -m "feat: add SkinType enum and LolitaSkinConfig data class"
```

---

### Task 3: Create skin definitions and CompositionLocal provider

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/SkinConfigs.kt`

**Step 1: Create SkinConfigs.kt with all 4 skin definitions**

This file contains:
- `LocalLolitaSkin` CompositionLocal
- `LolitaSkin` convenience object
- `getSkinConfig(skinType)` function
- `defaultSkinConfig()`, `gothicSkinConfig()`, `chineseSkinConfig()`, `classicSkinConfig()` factory functions
- Helper `buildTypography(fontFamily)` function that clones the existing Typography with a different FontFamily

Each skin defines:
- Full `lightColorScheme()` and `darkColorScheme()`
- Gradient colors for TopAppBar (light + dark)
- Accent color (light + dark) — used for bottom nav, FAB, etc.
- Card color (light + dark)
- FontFamily from bundled font (or Default for default skin)
- Typography built from that FontFamily
- Card shape (RoundedCornerShape with skin-specific radius)
- Button shape
- TopBar decoration string and alpha

Color specs per skin (from design doc):

**Default:** existing Pink palette, FontFamily.Default, 16dp corners, "✿" decoration
**Gothic:** deep purple #4A0E4E / bright purple #9B59B6, blood red #8B0000 / dark red #C0392B, Cinzel font, 8dp corners, "✝" decoration
**Chinese:** vermillion #C41E3A / dark red #8B2500, gold #DAA520 / dark gold #B8860B, Noto Serif SC font, 4dp corners, "☁" decoration
**Classic:** wine red #722F37 / dark wine #5B2333, brown #8B4513 / dark brown #6B3410, Playfair Display font, 12dp corners, "♠" decoration

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/SkinConfigs.kt
git commit -m "feat: add 4 skin definitions with CompositionLocal provider"
```

---

### Task 4: Update LolitaTheme to accept SkinType and provide LolitaSkinConfig

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/Theme.kt`

**Step 1: Rewrite LolitaTheme**

- Add `skinType: SkinType = SkinType.DEFAULT` parameter
- Remove inline `LightColors` / `DarkColors` (they move into `defaultSkinConfig()`)
- Get skin config via `getSkinConfig(skinType)`
- Select colorScheme based on `darkTheme`
- Wrap content in `CompositionLocalProvider(LocalLolitaSkin provides skin)`
- Pass `skin.typography` to `MaterialTheme`
- Keep the existing `SideEffect` for status bar styling

```kotlin
@Composable
fun LolitaTheme(
    skinType: SkinType = SkinType.DEFAULT,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val skin = getSkinConfig(skinType)
    val colors = if (darkTheme) skin.darkColorScheme else skin.lightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalLolitaSkin provides skin) {
        MaterialTheme(
            colorScheme = colors,
            typography = skin.typography,
            content = content
        )
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/Theme.kt
git commit -m "feat: update LolitaTheme to support skin switching via CompositionLocal"
```

---

### Task 5: Add skinType to AppPreferences

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/preferences/AppPreferences.kt`

**Step 1: Add skinType preference**

Add to companion object:
```kotlin
private val SKIN_TYPE = stringPreferencesKey("skin_type")
```

Add property and setter:
```kotlin
val skinType: Flow<SkinType> = context.dataStore.data
    .map {
        try { SkinType.valueOf(it[SKIN_TYPE] ?: "DEFAULT") }
        catch (_: Exception) { SkinType.DEFAULT }
    }

suspend fun setSkinType(skinType: SkinType) {
    context.dataStore.edit { it[SKIN_TYPE] = skinType.name }
}
```

Add import: `import com.lolita.app.ui.theme.SkinType` and `import androidx.datastore.preferences.core.stringPreferencesKey`

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/preferences/AppPreferences.kt
git commit -m "feat: add skinType preference to AppPreferences"
```

---

### Task 6: Wire skinType through MainActivity

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/MainActivity.kt`

**Step 1: Collect skinType and pass to LolitaTheme**

```kotlin
setContent {
    val appPreferences = com.lolita.app.di.AppModule.appPreferences()
    val skinType by appPreferences.skinType.collectAsState(initial = SkinType.DEFAULT)
    LolitaTheme(skinType = skinType) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            LolitaNavHost()
        }
    }
}
```

Add imports: `SkinType`, `collectAsState`, `getValue`, `AppPreferences`.

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/MainActivity.kt
git commit -m "feat: wire skinType from AppPreferences to LolitaTheme in MainActivity"
```

---

### Task 7: Update GradientTopAppBar to use skin config

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/common/GradientTopAppBar.kt`

**Step 1: Replace hardcoded colors and decoration**

- Remove imports of `Pink300`, `Pink400`, `Pink600`
- Add import of `LolitaSkin`
- Replace gradient construction:
  ```kotlin
  val skin = LolitaSkin.current
  val gradient = if (isSystemInDarkTheme()) {
      Brush.horizontalGradient(skin.gradientColorsDark)
  } else {
      Brush.horizontalGradient(skin.gradientColors)
  }
  ```
- Replace hardcoded `"✿"` with `skin.topBarDecoration`
- Replace hardcoded `0.7f` alpha with `skin.topBarDecorationAlpha`

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/common/GradientTopAppBar.kt
git commit -m "feat: update GradientTopAppBar to use skin config for gradient and decoration"
```

---

### Task 8: Update LolitaCard to use skin config

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/common/LolitaCard.kt`

**Step 1: Replace hardcoded shape**

- Add import of `LolitaSkin`
- Replace `RoundedCornerShape(16.dp)` with `LolitaSkin.current.cardShape`

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/common/LolitaCard.kt
git commit -m "feat: update LolitaCard to use skin-defined card shape"
```

---

### Task 9: Update LolitaNavHost bottom navigation to use skin config

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt`

**Step 1: Replace hardcoded Pink references in bottom nav**

- Remove imports of `Pink400`, `Pink100`
- Add import of `LolitaSkin`, `isSystemInDarkTheme`
- Get accent color: `val skin = LolitaSkin.current; val accent = if (isSystemInDarkTheme()) skin.accentColorDark else skin.accentColor`
- Replace `contentColor = Pink400` → `contentColor = accent`
- Replace `selectedIconColor = Pink400` → `selectedIconColor = accent`
- Replace `selectedTextColor = Pink400` → `selectedTextColor = accent`
- Replace `indicatorColor = Pink100` → `indicatorColor = MaterialTheme.colorScheme.primaryContainer`

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt
git commit -m "feat: update bottom navigation colors to use skin config"
```

---

### Task 10: Replace hardcoded Pink references across all screen files

This is the largest task. Replace all `Pink400`, `Pink100`, `Pink300`, `Pink30`, `Pink50` references in screen files with `MaterialTheme.colorScheme` equivalents.

**Mapping rules:**
- `Pink400` → `MaterialTheme.colorScheme.primary` (most cases)
- `Pink100` → `MaterialTheme.colorScheme.primaryContainer` (dividers, indicators)
- `Pink300` → `MaterialTheme.colorScheme.tertiary` (secondary accent)
- `Pink300.copy(alpha=0.3f)` → `MaterialTheme.colorScheme.tertiary.copy(alpha=0.3f)`
- `Pink400.copy(alpha=X)` → `MaterialTheme.colorScheme.primary.copy(alpha=X)`
- `Pink30` → `MaterialTheme.colorScheme.background`
- `Pink50` → `MaterialTheme.colorScheme.surfaceVariant`

**Files to modify (in order):**

1. `ui/screen/common/EmptyState.kt` — 2 refs (Pink400)
2. `ui/screen/settings/SettingsScreen.kt` — 4 refs (Pink400, Pink100)
3. `ui/screen/settings/BrandManageScreen.kt` — 2 refs (Pink400, Pink100)
4. `ui/screen/settings/CategoryManageScreen.kt` — 2 refs (Pink400, Pink100)
5. `ui/screen/settings/StyleManageScreen.kt` — 2 refs (Pink400, Pink100)
6. `ui/screen/settings/SeasonManageScreen.kt` — 2 refs (Pink400, Pink100)
7. `ui/screen/settings/BackupRestoreScreen.kt` — 3 refs (Pink100)
8. `ui/screen/item/ItemListScreen.kt` — ~30 refs (Pink400, Pink300, Pink100)
9. `ui/screen/item/ItemDetailScreen.kt` — ~20 refs (Pink400, Pink300, Pink100)
10. `ui/screen/item/ItemEditScreen.kt` — 1 ref (Pink400)
11. `ui/screen/item/WishlistScreen.kt` — 1 ref (Pink400)
12. `ui/screen/item/RecommendationScreen.kt` — 6 refs (Pink400, Pink100)
13. `ui/screen/coordinate/CoordinateListScreen.kt` — ~12 refs (Pink400, Pink300)
14. `ui/screen/coordinate/CoordinateDetailScreen.kt` — ~10 refs (Pink400, Pink300)
15. `ui/screen/coordinate/CoordinateEditScreen.kt` — 5 refs (Pink400)
16. `ui/screen/outfit/OutfitLogListScreen.kt` — 4 refs (Pink400)
17. `ui/screen/outfit/OutfitLogDetailScreen.kt` — 2 refs (Pink400)
18. `ui/screen/outfit/QuickOutfitLogScreen.kt` — 8 refs (Pink400, Pink100)
19. `ui/screen/price/PriceManageScreen.kt` — 1 ref (Pink400)
20. `ui/screen/price/PriceEditScreen.kt` — 1 ref (Pink400)
21. `ui/screen/price/PaymentManageScreen.kt` — 2 refs (Pink400, Pink100)
22. `ui/screen/import/TaobaoImportScreen.kt` — ~15 refs (Pink400)
23. `ui/screen/import/ImportDetailScreen.kt` — ~10 refs (Pink400)
24. `ui/screen/calendar/PaymentCalendarScreen.kt` — 6 refs (Pink400)
25. `ui/screen/stats/StatsPageScreen.kt` — 2 refs (Pink400, Pink100)
26. `ui/screen/stats/SpendingTrendScreen.kt` — 5 refs (Pink400, Pink30)
27. `ui/screen/stats/StatsScreen.kt` — 1 ref (Pink400)
28. `ui/component/chart/StatsProgressBar.kt` — 1 ref (Pink400)
29. `ui/component/chart/ChartColors.kt` — hardcoded hex values, replace with skin-aware colors

**Strategy:** For each file, remove `import com.lolita.app.ui.theme.PinkXXX` and replace usages with `MaterialTheme.colorScheme.primary` / `.primaryContainer` / `.tertiary` as appropriate. This is mechanical find-and-replace work.

**Step: Commit after each batch of ~5 files**

```bash
git commit -m "refactor: replace hardcoded Pink refs with MaterialTheme.colorScheme in [batch description]"
```

---

### Task 11: Add ThemeSelect route to Screen sealed interface

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/Screen.kt`

**Step 1: Add ThemeSelect screen**

```kotlin
data object ThemeSelect : Screen {
    override val route = "theme_select"
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/navigation/Screen.kt
git commit -m "feat: add ThemeSelect route to Screen sealed interface"
```

---

### Task 12: Create ThemeSelectScreen composable

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/screen/settings/ThemeSelectScreen.kt`

**Step 1: Implement ThemeSelectScreen**

Layout:
- `GradientTopAppBar` with title "选择皮肤" and back button
- 2-column `LazyVerticalGrid` with 4 skin preview cards
- Each card shows:
  - Skin name (in that skin's font if possible, or just text)
  - Horizontal gradient preview bar using the skin's gradient colors
  - Decoration symbol
  - Preview text "Lolita" in the skin's font
- Currently selected skin has a highlighted border (`MaterialTheme.colorScheme.primary`, 2dp)
- Clicking a card calls `appPreferences.setSkinType(skinType)` — effect is immediate via DataStore Flow

```kotlin
@Composable
fun ThemeSelectScreen(
    onBack: () -> Unit,
    appPreferences: AppPreferences = AppModule.appPreferences()
) {
    val currentSkin by appPreferences.skinType.collectAsState(initial = SkinType.DEFAULT)
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            GradientTopAppBar(
                title = { Text("选择皮肤") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(SkinType.entries) { skinType ->
                SkinPreviewCard(
                    skinType = skinType,
                    isSelected = skinType == currentSkin,
                    onClick = {
                        coroutineScope.launch { appPreferences.setSkinType(skinType) }
                    }
                )
            }
        }
    }
}
```

`SkinPreviewCard` composable:
- Gets skin config via `getSkinConfig(skinType)`
- Shows gradient bar, decoration, name, font preview
- Border highlight if selected

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/settings/ThemeSelectScreen.kt
git commit -m "feat: add ThemeSelectScreen with skin preview cards"
```

---

### Task 13: Add ThemeSelect to navigation and SettingsScreen

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/settings/SettingsScreen.kt`

**Step 1: Add navigation route in LolitaNavHost**

Add import for `ThemeSelectScreen`, then add composable:
```kotlin
composable(Screen.ThemeSelect.route) {
    ThemeSelectScreen(onBack = { navController.popBackStack() })
}
```

Add `onNavigateToThemeSelect` callback to `SettingsScreen` call:
```kotlin
composable(Screen.Settings.route) {
    SettingsScreen(
        ...existing params...,
        onNavigateToThemeSelect = { navController.navigate(Screen.ThemeSelect.route) }
    )
}
```

**Step 2: Add skin entry in SettingsScreen**

Add `onNavigateToThemeSelect: () -> Unit = {}` parameter to `SettingsScreen`.

In the "显示设置" section, after the "显示总价" toggle, add:
```kotlin
SettingsMenuItem(
    title = "皮肤选择",
    description = "切换应用主题风格",
    icon = Icons.Default.Palette,
    iconTint = Color(0xFF9C27B0),
    onClick = onNavigateToThemeSelect
)
```

Add import: `Icons.Default.Palette` (from material-icons-extended).

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt
git add app/src/main/java/com/lolita/app/ui/screen/settings/SettingsScreen.kt
git commit -m "feat: add skin selection entry in settings and navigation"
```

---

### Task 14: Build verification

**Step 1: Run debug build**

```bash
./gradlew.bat assembleDebug
```

Expected: BUILD SUCCESSFUL. Fix any compilation errors.

**Step 2: Manual verification on device/emulator**

- Open app → Settings → 皮肤选择
- Switch between 4 skins, verify:
  - TopAppBar gradient and decoration change
  - Bottom nav colors change
  - Card shapes change
  - Font changes (for Gothic/Chinese/Classic)
  - Light/dark mode works for each skin
- Navigate through all screens to check no hardcoded Pink remains visible

**Step 3: Final commit**

```bash
git add -A
git commit -m "feat: complete skin system implementation"
```
