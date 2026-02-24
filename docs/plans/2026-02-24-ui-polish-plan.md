# UI Polish Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix three UI issues: bottom nav icon clipping, calendar month size inconsistency, and sparse location list.

**Architecture:** Three independent UI fixes. Tasks 1-2 are single-line changes. Task 3 requires a new DAO query, repository method, ViewModel state, and UI rewrite.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Material3

**Note:** This project has no test suite. Verification is via `./gradlew.bat assembleRelease`.

---

### Task 1: Fix bottom nav icon-label gap

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt:135`

**Step 1: Change label offset**

In `LolitaNavHost.kt` line 135, change:
```kotlin
label = { Text(item.label, modifier = Modifier.offset(y = (-2).dp)) },
```
to:
```kotlin
label = { Text(item.label, modifier = Modifier.offset(y = (-4).dp)) },
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt
git commit -m "fix: reduce bottom nav icon-label gap to prevent icon clipping"
```

---

### Task 2: Fix calendar month card height consistency

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt:378-386`

**Step 1: Add minHeight to MonthCard**

In `PaymentCalendarScreen.kt`, the `MonthCard` composable's `Card` modifier (line 378-385). Change:
```kotlin
    Card(
        modifier = modifier
            .then(
                if (isCurrentMonth) Modifier.border(
                    2.dp, primaryColor, MaterialTheme.shapes.medium
                ) else Modifier
            )
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
```
to:
```kotlin
    Card(
        modifier = modifier
            .heightIn(min = 100.dp)
            .then(
                if (isCurrentMonth) Modifier.border(
                    2.dp, primaryColor, MaterialTheme.shapes.medium
                ) else Modifier
            )
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt
git commit -m "fix: set consistent min height for calendar month cards"
```

---

### Task 3: Add DAO query for location item preview images

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/local/dao/ItemDao.kt`

**Step 1: Add query to ItemDao**

After the existing `getItemCountsByLocation()` method (line 146), add:

```kotlin
@Query("""
    SELECT location_id, image_url FROM items
    WHERE location_id IS NOT NULL AND image_url IS NOT NULL
    ORDER BY updated_at DESC
""")
fun getLocationItemImages(): Flow<List<LocationItemImage>>
```

After the `LocationItemCount` data class (line 182), add:

```kotlin
data class LocationItemImage(
    @ColumnInfo(name = "location_id") val locationId: Long,
    @ColumnInfo(name = "image_url") val imageUrl: String
)
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/local/dao/ItemDao.kt
git commit -m "feat: add DAO query for location item preview images"
```

---

### Task 4: Expose location item images via Repository and ViewModel

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/repository/LocationRepository.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt`

**Step 1: Add method to LocationRepository**

In `LocationRepository.kt`, add import at top:
```kotlin
import com.lolita.app.data.local.dao.LocationItemImage
```

Add method after `getItemCountsByLocation()` (line 46):
```kotlin
fun getLocationItemImages(): Flow<List<LocationItemImage>> = itemDao.getLocationItemImages()
```

**Step 2: Add state and loading to ItemListViewModel**

In `ItemViewModel.kt`, add after `_unassignedItemCount` (line 138):
```kotlin
private val _locationItemImages = MutableStateFlow<Map<Long, List<String>>>(emptyMap())
val locationItemImages: StateFlow<Map<Long, List<String>>> = _locationItemImages.asStateFlow()
```

In the `loadLocations()` method (around line 160), add a new coroutine block after the existing three:
```kotlin
viewModelScope.launch {
    locationRepository.getLocationItemImages().collect { images ->
        _locationItemImages.value = images
            .groupBy { it.locationId }
            .mapValues { (_, items) -> items.take(4).map { it.imageUrl } }
    }
}
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/repository/LocationRepository.kt \
      app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt
git commit -m "feat: expose location item images through repository and viewmodel"
```

---

### Task 5: Rewrite LocationListContent with thumbnails and improved layout

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/LocationListContent.kt`

**Step 1: Update LocationListContent signature**

Add `locationItemImages` parameter:
```kotlin
@Composable
fun LocationListContent(
    locations: List<Location>,
    locationItemCounts: Map<Long, Int>,
    locationItemImages: Map<Long, List<String>>,
    unassignedItemCount: Int,
    onLocationClick: (Long) -> Unit
)
```

Pass it through to `LocationCardItem`:
```kotlin
LocationCardItem(
    name = location.name,
    description = location.description,
    imageUrl = location.imageUrl,
    itemCount = locationItemCounts[location.id] ?: 0,
    itemImages = locationItemImages[location.id] ?: emptyList()
)
```

And for unassigned:
```kotlin
LocationCardItem(
    name = "未分配",
    description = "未设置位置的服饰",
    imageUrl = null,
    itemCount = unassignedItemCount,
    itemImages = emptyList(),
    isUnassigned = true
)
```

**Step 2: Rewrite LocationCardItem**

Replace the entire `LocationCardItem` composable with:

```kotlin
@Composable
private fun LocationCardItem(
    name: String,
    description: String,
    imageUrl: String?,
    itemCount: Int,
    itemImages: List<String> = emptyList(),
    isUnassigned: Boolean = false
) {
    LolitaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Location image - 56dp
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        SkinIcon(
                            if (isUnassigned) IconKey.Info else IconKey.Location,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                if (description.isNotBlank()) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
                // Item count badge
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "${itemCount} 件服饰",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                // Item thumbnail preview row
                Spacer(Modifier.height(6.dp))
                if (itemImages.isNotEmpty()) {
                    Row {
                        itemImages.forEachIndexed { index, url ->
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(36.dp)
                                    .offset(x = (-(index * 6)).dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                } else {
                    Text(
                        "暂无服饰",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
            SkinIcon(IconKey.KeyboardArrowRight, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
```

Note: Add `import androidx.compose.ui.unit.IntOffset` and `import androidx.compose.foundation.layout.offset` if not already imported.

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/LocationListContent.kt
git commit -m "feat: enhance location list with thumbnails and improved layout"
```

---

### Task 6: Wire locationItemImages into ItemListScreen

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt:336-346`

**Step 1: Pass locationItemImages to LocationListContent**

In `ItemListScreen.kt`, around line 336-346, the `LocationListContent` call site. Change:
```kotlin
val locations by viewModel.locations.collectAsState()
val locationItemCounts by viewModel.locationItemCounts.collectAsState()
val unassignedCount by viewModel.unassignedItemCount.collectAsState()
LocationListContent(
    locations = locations,
    locationItemCounts = locationItemCounts,
    unassignedItemCount = unassignedCount,
    onLocationClick = { locationId ->
        onNavigateToLocationDetail(locationId)
    }
)
```
to:
```kotlin
val locations by viewModel.locations.collectAsState()
val locationItemCounts by viewModel.locationItemCounts.collectAsState()
val locationItemImages by viewModel.locationItemImages.collectAsState()
val unassignedCount by viewModel.unassignedItemCount.collectAsState()
LocationListContent(
    locations = locations,
    locationItemCounts = locationItemCounts,
    locationItemImages = locationItemImages,
    unassignedItemCount = unassignedCount,
    onLocationClick = { locationId ->
        onNavigateToLocationDetail(locationId)
    }
)
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt
git commit -m "feat: wire location item images into ItemListScreen"
```

---

### Task 7: Version bump and release build

**Files:**
- Modify: `app/build.gradle.kts`

**Step 1: Bump version**

Increment `versionCode` by 1 (23 → 24) and update `versionName` (e.g. "2.12" → "2.13").

**Step 2: Build release**

```bash
./gradlew.bat assembleRelease
```

Verify build succeeds with no errors.

**Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "chore: bump version to 2.13 (24)"
```
