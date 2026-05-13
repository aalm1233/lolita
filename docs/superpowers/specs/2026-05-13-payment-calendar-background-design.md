# Payment Calendar Custom Background — Design Spec

**Date:** 2026-05-13
**Status:** Approved

## Summary

Allow users to set a custom background image on the payment calendar tab (Stats → 付款年历). The 13 foreground cards gain a light frosted-glass (毛玻璃) effect. The feature is independent of the skin system.

## Design Decisions

| Decision | Choice |
|----------|--------|
| Fill mode | Cover (crop-to-fill) |
| Frosted glass intensity | Light (8-12dp blur, ~70% opacity) |
| Upload entry | Long-press empty area → bottom sheet |
| Skin integration | None — independent of skin system |
| Default state | Existing look (no background, no haze) |

## Data Layer

### Storage

`AppPreferences` (DataStore) gains one key:

- `paymentCalendarBackgroundPath: Flow<String?>` — nullable, null = no custom background
- `suspend fun setPaymentCalendarBackgroundPath(path: String?)`

### Image Management

Uses existing `ImageFileHelper.copyToInternalStorage()` to persist picked images to `context.filesDir/images/`. On "reset to default" or "change background", the old file is deleted via `ImageFileHelper.deleteImage()`.

## UI Structure

```
Box(fillMaxSize) {
    // Layer 1: Background image (only when path != null)
    LolitaShimmerImage(path, contentScale = Crop, Modifier.fillMaxSize().hazeSource(hazeState))

    // Layer 2: Existing content (unchanged structure)
    LazyColumn(padding = 16dp, verticalArrangement = spacedBy(12dp)) {
        YearHeader      ← Card + hazeEffect(hazeState, lightStyle)
        MonthCardGrid   ← 12x MonthCard + hazeEffect
        PaymentInfoCard ← LolitaCard + hazeEffect
    }
}
```

### Long-press gesture

`Modifier.pointerInput` with `detectTapGestures(onLongPress = ...)` on the outer `Box`. The 500ms hold + finger displacement threshold ensures no conflict with scroll.

### Bottom sheet menu

| Option | Condition | Action |
|--------|-----------|--------|
| 从相册选择 | Always | `PickVisualMedia` → `ImageFileHelper.copyToInternalStorage()` → save path |
| 拍照 | Always | `FileProvider` → `ImageFileHelper.copyToInternalStorage()` → save path |
| 查看大图 | path != null | `FullScreenImageViewer` |
| 恢复默认 | path != null | `ImageFileHelper.deleteImage()` → set path to null |

### Haze style

Light frost: `HazeStyle(blurRadius = 8.dp, backgroundColor = Color.White.copy(alpha = 0.3f), ...)` — tuned during implementation.

## Edge Cases

- **No background set**: Render exactly as today — no background layer, no haze, no long-press handler
- **Reset to default**: Delete image file, clear DataStore key
- **Image file missing** (path non-null but file deleted by user/storage cleanup): Silently fall back to no-background rendering
- **Orientation**: Cover mode handles both portrait and landscape naturally
- **Permission**: `PickVisualMedia` needs no new permissions; camera reuses existing `CAMERA` permission

## Files Changed

| File | Change |
|------|--------|
| `app/src/main/java/com/lolita/app/di/AppPreferences.kt` | New key + setter |
| `app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt` | Background layer, haze, long-press, bottom sheet |
| `app/build.gradle.kts` | Version bump (2.35.0 → 2.36.0, code 59 → 60) |

No new files. No database migration. No dependency changes (Haze 1.6.9 already in project).
