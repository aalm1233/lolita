# 套装详情页添加服饰 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a BottomSheet item picker to CoordinateDetailScreen so users can add/remove items without navigating to the edit screen.

**Architecture:** Extend CoordinateDetailViewModel with item selection state and a ModalBottomSheet in CoordinateDetailScreen. Reuse existing `CoordinateRepository.updateCoordinateWithItems()` for atomic batch updates.

**Tech Stack:** Kotlin, Jetpack Compose (ModalBottomSheet), Room (existing DAOs), StateFlow

---

### Task 1: Extend CoordinateDetailViewModel with item picker state

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateViewModel.kt`

**Step 1: Add fields to CoordinateDetailUiState**

Add these fields to the `CoordinateDetailUiState` data class (line 39):

```kotlin
data class CoordinateDetailUiState(
    val coordinate: Coordinate? = null,
    val items: List<Item> = emptyList(),
    val totalPrice: Double = 0.0,
    val paidAmount: Double = 0.0,
    val unpaidAmount: Double = 0.0,
    val isLoading: Boolean = true,
    // Item picker state
    val allItems: List<Item> = emptyList(),
    val coordinateNames: Map<Long, String> = emptyMap(),
    val pickerSelectedItemIds: Set<Long> = emptySet(),
    val pickerSearchQuery: String = ""
)
```

**Step 2: Add ItemRepository and CoordinateRepository dependencies to CoordinateDetailViewModel**

Update constructor (line 182):

```kotlin
class CoordinateDetailViewModel(
    private val coordinateRepository: CoordinateRepository = com.lolita.app.di.AppModule.coordinateRepository(),
    private val priceRepository: PriceRepository = com.lolita.app.di.AppModule.priceRepository(),
    private val itemRepository: ItemRepository = com.lolita.app.di.AppModule.itemRepository()
) : ViewModel()
```

**Step 3: Add item picker methods to CoordinateDetailViewModel**

Add after `deleteCoordinate()` (after line 239):

```kotlin
fun loadAllItemsForPicker() {
    viewModelScope.launch {
        combine(
            itemRepository.getAllItems(),
            coordinateRepository.getAllCoordinates()
        ) { items, coordinates ->
            val nameMap = coordinates.associate { it.id to it.name }
            Pair(items, nameMap)
        }.first().let { (items, nameMap) ->
            val currentItemIds = _uiState.value.items.map { it.id }.toSet()
            _uiState.update {
                it.copy(
                    allItems = items,
                    coordinateNames = nameMap,
                    pickerSelectedItemIds = currentItemIds,
                    pickerSearchQuery = ""
                )
            }
        }
    }
}

fun togglePickerItemSelection(itemId: Long) {
    val current = _uiState.value.pickerSelectedItemIds
    _uiState.update {
        it.copy(
            pickerSelectedItemIds = if (itemId in current) current - itemId else current + itemId
        )
    }
}

fun updatePickerSearchQuery(query: String) {
    _uiState.update { it.copy(pickerSearchQuery = query) }
}

fun confirmPickerSelection(onComplete: () -> Unit) {
    val coordinate = _uiState.value.coordinate ?: return
    val originalItemIds = _uiState.value.items.map { it.id }.toSet()
    val selectedIds = _uiState.value.pickerSelectedItemIds
    val addedIds = selectedIds - originalItemIds
    val removedIds = originalItemIds - selectedIds

    if (addedIds.isEmpty() && removedIds.isEmpty()) {
        onComplete()
        return
    }

    viewModelScope.launch {
        coordinateRepository.updateCoordinateWithItems(coordinate, addedIds, removedIds)
        onComplete()
    }
}
```

**Step 4: Add import for `first` and `ItemRepository`**

Ensure these imports exist at the top of the file:
```kotlin
import com.lolita.app.data.repository.ItemRepository
```
(`first` and `combine` are already imported)

**Step 5: Build to verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateViewModel.kt
git commit -m "feat: add item picker state and methods to CoordinateDetailViewModel"
```

---

### Task 2: Add BottomSheet UI to CoordinateDetailScreen

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateDetailScreen.kt`

**Step 1: Add imports**

Add these imports to the top of the file:

```kotlin
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.saveable.rememberSaveable
import com.lolita.app.ui.theme.skin.SkinClickable
```

**Step 2: Add BottomSheet state and trigger button**

In `CoordinateDetailScreen`, add state variable:

```kotlin
var showItemPicker by remember { mutableStateOf(false) }
```

Replace the "包含服饰" title `item {}` block (line 163-168) with:

```kotlin
item {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "包含服饰 (${uiState.items.size})",
            style = MaterialTheme.typography.titleMedium
        )
        SkinClickable(
            onClick = {
                viewModel.loadAllItemsForPicker()
                showItemPicker = true
            }
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SkinIcon(IconKey.Add, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "添加服饰",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
```

**Step 3: Add ModalBottomSheet composable**

Add the BottomSheet right before the closing `}` of the `Scaffold` content lambda (before line 198), inside the `else` branch:

```kotlin
if (showItemPicker) {
    ModalBottomSheet(
        onDismissRequest = { showItemPicker = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        ItemPickerContent(
            allItems = uiState.allItems,
            selectedItemIds = uiState.pickerSelectedItemIds,
            coordinateNames = uiState.coordinateNames,
            currentCoordinateId = coordinateId,
            searchQuery = uiState.pickerSearchQuery,
            onSearchQueryChange = { viewModel.updatePickerSearchQuery(it) },
            onToggleItem = { viewModel.togglePickerItemSelection(it) },
            onConfirm = {
                viewModel.confirmPickerSelection {
                    showItemPicker = false
                }
            }
        )
    }
}
```

**Step 4: Create ItemPickerContent composable**

Add a new private composable function after `StatusBadge` (after line 445):

```kotlin
@Composable
private fun ItemPickerContent(
    allItems: List<Item>,
    selectedItemIds: Set<Long>,
    coordinateNames: Map<Long, String>,
    currentCoordinateId: Long,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleItem: (Long) -> Unit,
    onConfirm: () -> Unit
) {
    val filteredItems = remember(allItems, searchQuery) {
        if (searchQuery.isBlank()) allItems
        else allItems.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("选择服饰", style = MaterialTheme.typography.titleMedium)
            SkinClickable(onClick = onConfirm) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "确认 (${selectedItemIds.size})",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // Search
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("搜索服饰...") },
            leadingIcon = { SkinIcon(IconKey.Search, modifier = Modifier.size(20.dp)) },
            singleLine = true
        )

        // Item list
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .heightIn(max = 400.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(filteredItems, key = { it.id }) { item ->
                val isSelected = item.id in selectedItemIds
                val belongsToOther = item.coordinateId != null
                    && item.coordinateId != currentCoordinateId
                val otherCoordName = if (belongsToOther) {
                    coordinateNames[item.coordinateId]
                } else null

                PickerItemRow(
                    item = item,
                    isSelected = isSelected,
                    otherCoordinateName = otherCoordName,
                    onToggle = { onToggleItem(item.id) }
                )
            }
        }
    }
}
```

**Step 5: Create PickerItemRow composable**

Add after `ItemPickerContent`:

```kotlin
@Composable
private fun PickerItemRow(
    item: Item,
    isSelected: Boolean,
    otherCoordinateName: String?,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surface,
        onClick = onToggle
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary
                )
            )

            // Thumbnail
            if (item.imageUrl != null) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.name,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                )
                            ),
                            RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.name.firstOrNull()?.toString() ?: "?",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                val colorDisplay = parseColorsJson(item.colors).joinToString("、").ifEmpty { null }
                val details = listOfNotNull(colorDisplay, item.style, item.season).joinToString(" · ")
                if (details.isNotEmpty()) {
                    Text(
                        details,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (otherCoordinateName != null) {
                    Text(
                        "已属于套装「$otherCoordinateName」",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
```

**Step 6: Build to verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 7: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateDetailScreen.kt
git commit -m "feat: add item picker BottomSheet to CoordinateDetailScreen"
```

---

### Task 3: Version bump and release build

**Files:**
- Modify: `app/build.gradle.kts`

**Step 1: Bump version**

Change in `app/build.gradle.kts` (line 25-26):
```kotlin
versionCode = 15
versionName = "2.10"
```

**Step 2: Commit version bump**

```bash
git add app/build.gradle.kts
git commit -m "chore: bump version to 2.10 (versionCode 15)"
```

**Step 3: Release build**

Run: `./gradlew.bat assembleRelease`
Expected: BUILD SUCCESSFUL
