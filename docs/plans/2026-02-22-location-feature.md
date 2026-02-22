# Location Feature Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a Location entity and tab to the wardrobe screen, allowing users to organize items by physical storage location with images and descriptions.

**Architecture:** New `Location` Room entity with FK from `Item.locationId`. New LocationManage settings screen (follows BrandManage pattern). ItemListScreen tabs change from 全部/已拥有/套装 to 位置/已拥有/套装. Location tab shows card list, clicking enters LocationDetail page showing items at that location.

**Tech Stack:** Room (migration v6→v7), Jetpack Compose, MVVM + Repository, Coil for images, existing skin system.

---

### Task 1: Create Location Entity

**Files:**
- Create: `app/src/main/java/com/lolita/app/data/local/entity/Location.kt`

**Step 1: Create the Location entity**

```kotlin
package com.lolita.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "locations",
    indices = [Index(value = ["name"], unique = true)]
)
data class Location(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String = "",

    @ColumnInfo(name = "image_url")
    val imageUrl: String? = null,

    @ColumnInfo(name = "sort_order", defaultValue = "0")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/local/entity/Location.kt
git commit -m "feat: add Location entity"
```

### Task 2: Add locationId to Item Entity + DB Migration

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/local/entity/Item.kt`
- Modify: `app/src/main/java/com/lolita/app/data/local/LolitaDatabase.kt`

**Step 1: Add locationId field to Item entity**

In `Item.kt`, add a new foreign key to Location with ON DELETE SET NULL, add index on `location_id`, and add the field:

```kotlin
// Add to foreignKeys array:
ForeignKey(
    entity = Location::class,
    parentColumns = ["id"],
    childColumns = ["location_id"],
    onDelete = ForeignKey.SET_NULL
)

// Add to indices array:
Index(value = ["location_id"])

// Add field to data class:
@ColumnInfo(name = "location_id")
val locationId: Long? = null,
```

**Step 2: Update LolitaDatabase**

In `LolitaDatabase.kt`:
- Add `Location::class` to `@Database(entities = [...])`
- Bump `version = 6` to `version = 7`
- Add migration:

```kotlin
private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `locations` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `description` TEXT NOT NULL DEFAULT '',
                `image_url` TEXT DEFAULT NULL,
                `sort_order` INTEGER NOT NULL DEFAULT 0,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_locations_name` ON `locations` (`name`)")
        db.execSQL("ALTER TABLE items ADD COLUMN `location_id` INTEGER DEFAULT NULL REFERENCES locations(id) ON DELETE SET NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_location_id` ON `items` (`location_id`)")
    }
}
```

- Add `MIGRATION_6_7` to the `.addMigrations(...)` call in `getDatabase()`

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/local/entity/Item.kt \
      app/src/main/java/com/lolita/app/data/local/entity/Location.kt \
      app/src/main/java/com/lolita/app/data/local/LolitaDatabase.kt
git commit -m "feat: add Location table and Item.locationId FK with migration v6->v7"
```

### Task 3: Create LocationDao

**Files:**
- Create: `app/src/main/java/com/lolita/app/data/local/dao/LocationDao.kt`
- Modify: `app/src/main/java/com/lolita/app/data/local/LolitaDatabase.kt` (expose DAO)

**Step 1: Create LocationDao**

```kotlin
package com.lolita.app.data.local.dao

import androidx.room.*
import com.lolita.app.data.local.entity.Location
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations ORDER BY sort_order ASC, name ASC")
    fun getAllLocations(): Flow<List<Location>>

    @Query("SELECT * FROM locations ORDER BY sort_order ASC, name ASC")
    suspend fun getAllLocationsList(): List<Location>

    @Query("SELECT * FROM locations WHERE id = :id")
    fun getLocationById(id: Long): Flow<Location?>

    @Query("SELECT * FROM locations WHERE id = :id")
    suspend fun getLocationByIdSync(id: Long): Location?

    @Query("SELECT * FROM locations WHERE name = :name LIMIT 1")
    suspend fun getLocationByName(name: String): Location?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLocation(location: Location): Long

    @Update
    suspend fun updateLocation(location: Location)

    @Delete
    suspend fun deleteLocation(location: Location)

    @Query("DELETE FROM locations")
    suspend fun deleteAllLocations()
}
```

**Step 2: Add ItemDao queries for location**

In `ItemDao.kt`, add:

```kotlin
@Query("SELECT * FROM items WHERE location_id = :locationId ORDER BY updated_at DESC")
fun getItemsByLocationId(locationId: Long): Flow<List<Item>>

@Query("SELECT * FROM items WHERE location_id IS NULL ORDER BY updated_at DESC")
fun getItemsWithNoLocation(): Flow<List<Item>>

@Query("SELECT COUNT(*) FROM items WHERE location_id = :locationId")
suspend fun countItemsByLocation(locationId: Long): Int

@Query("SELECT COUNT(*) FROM items WHERE location_id IS NULL")
fun countItemsWithNoLocation(): Flow<Int>

@Query("SELECT location_id, COUNT(*) as count FROM items WHERE location_id IS NOT NULL GROUP BY location_id")
fun getItemCountsByLocation(): Flow<List<LocationItemCount>>
```

Also add a simple data class (can be in ItemDao.kt or a separate file):

```kotlin
data class LocationItemCount(
    @ColumnInfo(name = "location_id") val locationId: Long,
    @ColumnInfo(name = "count") val count: Int
)
```

**Step 3: Expose LocationDao in LolitaDatabase**

```kotlin
abstract fun locationDao(): LocationDao
```

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/local/dao/LocationDao.kt \
      app/src/main/java/com/lolita/app/data/local/dao/ItemDao.kt \
      app/src/main/java/com/lolita/app/data/local/LolitaDatabase.kt
git commit -m "feat: add LocationDao and location-related ItemDao queries"
```

### Task 4: Create LocationRepository + Register in AppModule

**Files:**
- Create: `app/src/main/java/com/lolita/app/data/repository/LocationRepository.kt`
- Modify: `app/src/main/java/com/lolita/app/di/AppModule.kt`

**Step 1: Create LocationRepository**

```kotlin
package com.lolita.app.data.repository

import com.lolita.app.data.local.dao.ItemDao
import com.lolita.app.data.local.dao.LocationDao
import com.lolita.app.data.local.entity.Location
import kotlinx.coroutines.flow.Flow

class LocationRepository(
    private val locationDao: LocationDao,
    private val itemDao: ItemDao
) {
    fun getAllLocations(): Flow<List<Location>> = locationDao.getAllLocations()

    suspend fun getAllLocationsList(): List<Location> = locationDao.getAllLocationsList()

    fun getLocationById(id: Long): Flow<Location?> = locationDao.getLocationById(id)

    suspend fun getLocationByIdSync(id: Long): Location? = locationDao.getLocationByIdSync(id)

    suspend fun insertLocation(location: Location): Long = locationDao.insertLocation(location)

    suspend fun updateLocation(location: Location) =
        locationDao.updateLocation(location.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteLocation(location: Location) {
        // FK SET NULL handles clearing item references automatically
        location.imageUrl?.let { com.lolita.app.data.file.ImageFileHelper.deleteImage(it) }
        locationDao.deleteLocation(location)
    }

    suspend fun getLocationByName(name: String): Location? = locationDao.getLocationByName(name)

    fun getItemsByLocationId(locationId: Long): Flow<List<com.lolita.app.data.local.entity.Item>> =
        itemDao.getItemsByLocationId(locationId)

    fun getItemsWithNoLocation(): Flow<List<com.lolita.app.data.local.entity.Item>> =
        itemDao.getItemsWithNoLocation()

    suspend fun countItemsByLocation(locationId: Long): Int =
        itemDao.countItemsByLocation(locationId)

    fun countItemsWithNoLocation(): Flow<Int> = itemDao.countItemsWithNoLocation()

    fun getItemCountsByLocation() = itemDao.getItemCountsByLocation()
}
```

**Step 2: Register in AppModule**

In `AppModule.kt`, add:

```kotlin
private val _locationRepository by lazy {
    LocationRepository(database.locationDao(), database.itemDao())
}
fun locationRepository() = _locationRepository
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/repository/LocationRepository.kt \
      app/src/main/java/com/lolita/app/di/AppModule.kt
git commit -m "feat: add LocationRepository and register in AppModule"
```

### Task 5: Add Location Icon to Skin System

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/IconKey.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/SkinIconProvider.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/BaseSkinIconProvider.kt`
- Modify: 4 skin-specific icon providers (Sweet, Gothic, Chinese, Classic)
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/SkinIcon.kt` (add Location to when-mapping)

**Step 1: Add IconKey.Location**

In `IconKey.kt`, add `Location` to the Content section:

```kotlin
// Content
Star, StarBorder, Image, Camera, AddPhoto, Link, LinkOff, Palette, FileOpen, CalendarMonth, Notifications, AttachMoney, Category, Location,
```

**Step 2: Add Location to ContentIcons interface**

In `SkinIconProvider.kt`, add to `ContentIcons`:

```kotlin
@Composable fun Location(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
```

**Step 3: Add default implementation in BaseSkinIconProvider**

In `BaseSkinIconProvider.kt`, add to `BaseContentIcons`:

```kotlin
@Composable override fun Location(modifier: Modifier, tint: Color) =
    Icon(Icons.Filled.LocationOn, null, modifier, tint)
```

**Step 4: Implement in all 4 skin-specific providers**

Each provider should draw a Canvas-based location icon following its skin's design language. Find the 4 provider files:
- `SweetIconProvider.kt` — house with heart-shaped window, stroke 0.08f, Round cap
- `GothicIconProvider.kt` — gothic pointed arch building, stroke 0.06f, Butt cap/Miter join
- `ChineseIconProvider.kt` — Chinese curved eave roof, stroke 0.07f, Round cap, ink brush feel
- `ClassicIconProvider.kt` — Victorian wardrobe/armoire, stroke 0.065f, Round cap

**Step 5: Add Location to SkinIcon composable's when-mapping**

In `SkinIcon.kt`, add the `IconKey.Location` case:

```kotlin
IconKey.Location -> icons.content.Location(modifier, tint)
```

**Step 6: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/icon/
git commit -m "feat: add Location icon to skin system with 4 skin variants"
```

### Task 6: Create LocationManageScreen + ViewModel (Settings)

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/screen/settings/LocationManageViewModel.kt`
- Create: `app/src/main/java/com/lolita/app/ui/screen/settings/LocationManageScreen.kt`

**Step 1: Create LocationManageViewModel**

Follow BrandManageViewModel pattern (`app/src/main/java/com/lolita/app/ui/screen/settings/BrandManageViewModel.kt`):

```kotlin
package com.lolita.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.Location
import com.lolita.app.data.repository.LocationRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class LocationManageUiState(
    val locations: List<Location> = emptyList(),
    val locationItemCounts: Map<Long, Int> = emptyMap(),
    val showAddDialog: Boolean = false,
    val showDeleteConfirm: Location? = null,
    val deleteItemCount: Int = 0,
    val editingLocation: Location? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class LocationManageViewModel(
    private val locationRepository: LocationRepository = com.lolita.app.di.AppModule.locationRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocationManageUiState())
    val uiState: StateFlow<LocationManageUiState> = _uiState.asStateFlow()

    init { loadLocations() }

    private fun loadLocations() {
        viewModelScope.launch {
            locationRepository.getAllLocations().collect { locations ->
                val counts = mutableMapOf<Long, Int>()
                locations.forEach { loc ->
                    counts[loc.id] = locationRepository.countItemsByLocation(loc.id)
                }
                _uiState.value = _uiState.value.copy(
                    locations = locations,
                    locationItemCounts = counts,
                    isLoading = false
                )
            }
        }
    }

    fun showAddDialog() { _uiState.update { it.copy(showAddDialog = true) } }
    fun hideAddDialog() { _uiState.update { it.copy(showAddDialog = false) } }
    fun showEditDialog(location: Location) { _uiState.update { it.copy(editingLocation = location) } }
    fun hideEditDialog() { _uiState.update { it.copy(editingLocation = null) } }

    fun showDeleteConfirm(location: Location) {
        viewModelScope.launch {
            val count = locationRepository.countItemsByLocation(location.id)
            _uiState.update { it.copy(showDeleteConfirm = location, deleteItemCount = count) }
        }
    }
    fun hideDeleteConfirm() { _uiState.update { it.copy(showDeleteConfirm = null) } }

    fun addLocation(name: String, description: String, imageUrl: String?) {
        viewModelScope.launch {
            try {
                locationRepository.insertLocation(
                    Location(name = name.trim(), description = description.trim(), imageUrl = imageUrl)
                )
                hideAddDialog()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "添加失败：位置名称已存在") }
            }
        }
    }

    fun updateLocation(location: Location, name: String, description: String, imageUrl: String?) {
        viewModelScope.launch {
            try {
                val oldImageUrl = location.imageUrl
                if (oldImageUrl != null && oldImageUrl != imageUrl) {
                    com.lolita.app.data.file.ImageFileHelper.deleteImage(oldImageUrl)
                }
                locationRepository.updateLocation(
                    location.copy(name = name.trim(), description = description.trim(), imageUrl = imageUrl)
                )
                hideEditDialog()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "更新失败") }
            }
        }
    }

    fun deleteLocation(location: Location) {
        viewModelScope.launch {
            try {
                locationRepository.deleteLocation(location)
                hideDeleteConfirm()
            } catch (e: Exception) {
                hideDeleteConfirm()
                _uiState.update { it.copy(errorMessage = e.message ?: "删除失败") }
            }
        }
    }

    fun clearError() { _uiState.update { it.copy(errorMessage = null) } }
}
```

**Step 2: Create LocationManageScreen**

Follow BrandManageScreen pattern. Key differences from BrandManage:
- Each list item shows: thumbnail image (64.dp), name, description (1 line), item count badge
- Add/Edit dialog includes: name TextField, description TextField, image picker (camera + gallery via ImageFileHelper)
- Delete confirm dialog shows: "确定删除「{name}」？关联的 {N} 件服饰将变为未分配"
- Uses `GradientTopAppBar` with compact = true, title "位置管理"
- FAB with Add icon
- `SkinItemAppear` for list animations, `SkinFlingBehavior` for scroll
- `LolitaCard` for each location item

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/settings/LocationManageViewModel.kt \
      app/src/main/java/com/lolita/app/ui/screen/settings/LocationManageScreen.kt
git commit -m "feat: add LocationManage settings screen with add/edit/delete"
```

### Task 7: Create LocationListContent (位置 Tab Content)

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/screen/item/LocationListContent.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt` (add location state to ItemListViewModel)

**Step 1: Add location state to ItemListViewModel**

In `ItemViewModel.kt`, add to `ItemListViewModel`:

```kotlin
// Add locationRepository constructor param:
private val locationRepository: LocationRepository = com.lolita.app.di.AppModule.locationRepository()

// Add state flows:
private val _locations = MutableStateFlow<List<Location>>(emptyList())
val locations: StateFlow<List<Location>> = _locations.asStateFlow()

private val _locationItemCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
val locationItemCounts: StateFlow<Map<Long, Int>> = _locationItemCounts.asStateFlow()

private val _unassignedItemCount = MutableStateFlow(0)
val unassignedItemCount: StateFlow<Int> = _unassignedItemCount.asStateFlow()
```

In `init` block, add collectors:

```kotlin
viewModelScope.launch {
    locationRepository.getAllLocations().collect { _locations.value = it }
}
viewModelScope.launch {
    locationRepository.getItemCountsByLocation().collect { counts ->
        _locationItemCounts.value = counts.associate { it.locationId to it.count }
    }
}
viewModelScope.launch {
    locationRepository.countItemsWithNoLocation().collect { _unassignedItemCount.value = it }
}
```

**Step 2: Create LocationListContent composable**

```kotlin
package com.lolita.app.ui.screen.item

// LocationListContent.kt
// Composable that displays a LazyColumn of location cards
// Parameters:
//   locations: List<Location>
//   locationItemCounts: Map<Long, Int>
//   unassignedItemCount: Int
//   onLocationClick: (Long) -> Unit  -- locationId, -1 for unassigned
//
// Each card (LolitaCard):
//   - Row layout: image (80.dp, rounded) | Column(name, description max 2 lines, "N 件服饰")
//   - If location.imageUrl is null, show placeholder icon (SkinIcon Location)
//   - Apply SkinItemAppear modifier for stagger animation
//
// At the end of the list, add "未分配" virtual card:
//   - No image, show a folder/inbox icon
//   - Name: "未分配"
//   - Description: "未设置位置的服饰"
//   - Count: unassignedItemCount
//   - Only show if unassignedItemCount > 0
//
// Use SkinFlingBehavior for scroll friction
// Use SkinClickable for card click handling
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/LocationListContent.kt \
      app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt
git commit -m "feat: add LocationListContent and location state in ItemListViewModel"
```

### Task 8: Create LocationDetail Screen

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/screen/item/LocationDetailScreen.kt`
- Create: `app/src/main/java/com/lolita/app/ui/screen/item/LocationDetailViewModel.kt`

**Step 1: Create LocationDetailViewModel**

```kotlin
package com.lolita.app.ui.screen.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.Location
import com.lolita.app.data.repository.LocationRepository
import com.lolita.app.data.repository.BrandRepository
import com.lolita.app.data.repository.CategoryRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class LocationDetailUiState(
    val location: Location? = null,
    val items: List<Item> = emptyList(),
    val brandNames: Map<Long, String> = emptyMap(),
    val categoryNames: Map<Long, String> = emptyMap(),
    val isUnassigned: Boolean = false,
    val isLoading: Boolean = true
)

class LocationDetailViewModel(
    private val locationRepository: LocationRepository = com.lolita.app.di.AppModule.locationRepository(),
    private val brandRepository: BrandRepository = com.lolita.app.di.AppModule.brandRepository(),
    private val categoryRepository: CategoryRepository = com.lolita.app.di.AppModule.categoryRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocationDetailUiState())
    val uiState: StateFlow<LocationDetailUiState> = _uiState.asStateFlow()

    fun loadLocation(locationId: Long) {
        viewModelScope.launch {
            if (locationId == -1L) {
                // Unassigned items
                _uiState.update { it.copy(isUnassigned = true, isLoading = false) }
                locationRepository.getItemsWithNoLocation().collect { items ->
                    _uiState.update { it.copy(items = items) }
                }
            } else {
                launch {
                    locationRepository.getLocationById(locationId).collect { loc ->
                        _uiState.update { it.copy(location = loc, isLoading = false) }
                    }
                }
                launch {
                    locationRepository.getItemsByLocationId(locationId).collect { items ->
                        _uiState.update { it.copy(items = items) }
                    }
                }
            }
        }
        // Load brand/category names for display
        viewModelScope.launch {
            brandRepository.getAllBrands().collect { brands ->
                _uiState.update { it.copy(brandNames = brands.associate { b -> b.id to b.name }) }
            }
        }
        viewModelScope.launch {
            categoryRepository.getAllCategories().collect { cats ->
                _uiState.update { it.copy(categoryNames = cats.associate { c -> c.id to c.name }) }
            }
        }
    }
}
```

**Step 2: Create LocationDetailScreen**

```
// LocationDetailScreen.kt
// Parameters:
//   locationId: Long (-1 for unassigned)
//   onBack: () -> Unit
//   onItemClick: (Long) -> Unit  -- navigate to ItemDetail
//
// Layout:
//   GradientTopAppBar(title = location.name or "未分配", compact = true, back button)
//   LazyColumn:
//     - If not unassigned: header section with location image (full width, 200.dp) + description
//     - Item grid/list: each item shows image thumbnail, name, brand, category
//       Use LolitaCard for each item, SkinItemAppear for animation
//       SkinClickable for click → onItemClick(item.id)
//   SkinFlingBehavior for scroll
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/LocationDetailViewModel.kt \
      app/src/main/java/com/lolita/app/ui/screen/item/LocationDetailScreen.kt
git commit -m "feat: add LocationDetail screen with item list"
```

### Task 9: Update Navigation (Screen routes + LolitaNavHost)

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/Screen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt`

**Step 1: Add routes to Screen sealed interface**

In `Screen.kt`, add:

```kotlin
data object LocationManage : Screen {
    override val route = "location_manage"
}

data object LocationDetail : Screen {
    override val route = "location_detail/{locationId}"
    fun createRoute(locationId: Long): String = "location_detail/$locationId"
}
```

**Step 2: Add composables to LolitaNavHost**

In `LolitaNavHost.kt`:

Add to SettingsScreen's navigation callbacks:
```kotlin
onNavigateToLocation = { navController.navigate(Screen.LocationManage.route) }
```

Add composable registrations:
```kotlin
// Location Manage (settings sub-screen)
composable(Screen.LocationManage.route) {
    LocationManageScreen(onBack = { navController.popBackStack() })
}

// Location Detail
composable(
    route = Screen.LocationDetail.route,
    arguments = listOf(navArgument("locationId") { type = NavType.LongType })
) { backStackEntry ->
    val locationId = backStackEntry.arguments?.getLong("locationId") ?: return@composable
    LocationDetailScreen(
        locationId = locationId,
        onBack = { navController.popBackStack() },
        onItemClick = { itemId -> navController.navigate(Screen.ItemDetail.createRoute(itemId)) }
    )
}
```

Update ItemListScreen call to pass location navigation:
```kotlin
composable(Screen.ItemList.route) {
    ItemListScreen(
        // ... existing params ...
        onNavigateToLocationDetail = { locationId ->
            navController.navigate(Screen.LocationDetail.createRoute(locationId))
        }
    )
}
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/navigation/Screen.kt \
      app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt
git commit -m "feat: add Location navigation routes and composable registrations"
```

### Task 10: Update ItemListScreen Tabs (全部 → 位置)

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt`

**Step 1: Update tab definitions**

Change tab list from `listOf("全部", "已拥有", "套装")` to `listOf("位置", "已拥有", "套装")`.

**Step 2: Update pager LaunchedEffect**

```kotlin
LaunchedEffect(pagerState.currentPage) {
    when (pagerState.currentPage) {
        0 -> { /* Location tab - no status filter needed */ }
        1 -> viewModel.filterByStatus(ItemStatus.OWNED)
        // Tab 2 is coordinates, handled separately
    }
}
```

**Step 3: Update HorizontalPager content**

Page 0 now renders `LocationListContent` instead of the item list:

```kotlin
HorizontalPager(state = pagerState) { page ->
    when (page) {
        0 -> {
            // Location tab
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
        }
        1 -> {
            // Owned items list (existing code, was page 0/1 before)
            // ... existing item list content ...
        }
        2 -> {
            // Coordinates (existing code, unchanged)
            CoordinateListContent(...)
        }
    }
}
```

**Step 4: Add `onNavigateToLocationDetail` parameter to ItemListScreen**

```kotlin
@Composable
fun ItemListScreen(
    // ... existing params ...
    onNavigateToLocationDetail: (Long) -> Unit = {},
    // ...
)
```

**Step 5: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt
git commit -m "feat: replace 全部 tab with 位置 tab in ItemListScreen"
```

### Task 11: Add Location Selector to ItemEditScreen

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt` (ItemEditUiState + ItemEditViewModel)
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemEditScreen.kt`

**Step 1: Update ItemEditUiState**

In `ItemViewModel.kt`, add to `ItemEditUiState`:

```kotlin
val locationId: Long? = null,
val locations: List<com.lolita.app.data.local.entity.Location> = emptyList(),
```

**Step 2: Update ItemEditViewModel**

Add `locationRepository` constructor param:

```kotlin
private val locationRepository: LocationRepository = com.lolita.app.di.AppModule.locationRepository()
```

In the init/load logic where brands, categories, coordinates are loaded, also load locations:

```kotlin
viewModelScope.launch {
    locationRepository.getAllLocations().collect { locations ->
        _uiState.update { it.copy(locations = locations) }
    }
}
```

When loading an existing item (`loadItem`), set `locationId`:

```kotlin
_uiState.update { it.copy(locationId = item.locationId, /* ... other fields ... */) }
```

Add update function:

```kotlin
fun updateLocation(locationId: Long?) {
    _uiState.update { it.copy(locationId = locationId) }
    hasUnsavedChanges = true
}
```

In the save function, include `locationId` when constructing the Item:

```kotlin
val item = Item(
    // ... existing fields ...
    locationId = uiState.value.locationId,
)
```

**Step 3: Add LocationSelector composable to ItemEditScreen**

Add after the CoordinateSelector (around line 208-212):

```kotlin
// Location selector (optional)
LocationSelector(
    selectedLocationId = uiState.locationId,
    locations = uiState.locations,
    onLocationSelected = { viewModel.updateLocation(it) }
)
```

Create the `LocationSelector` private composable (follow `CoordinateSelector` pattern at line 434):

```kotlin
@Composable
private fun LocationSelector(
    selectedLocationId: Long?,
    locations: List<com.lolita.app.data.local.entity.Location>,
    onLocationSelected: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = if (selectedLocationId == null) {
        "无"
    } else {
        locations.find { it.id == selectedLocationId }?.name ?: "无"
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("存放位置 (可选)") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("无") },
                onClick = { onLocationSelected(null); expanded = false }
            )
            locations.forEach { location ->
                DropdownMenuItem(
                    text = { Text(location.name) },
                    onClick = { onLocationSelected(location.id); expanded = false }
                )
            }
        }
    }
}
```

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt \
      app/src/main/java/com/lolita/app/ui/screen/item/ItemEditScreen.kt
git commit -m "feat: add location selector to ItemEditScreen"
```

### Task 12: Add Location to Settings Screen

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/settings/SettingsScreen.kt`

**Step 1: Add LocationManage menu item**

Add `onNavigateToLocation: () -> Unit` parameter to `SettingsScreen`.

Add a new `SettingsMenuItem` after the "季节管理" entry:

```kotlin
SettingsMenuItem(
    title = "位置管理",
    description = "管理服饰存放位置（衣柜、抽屉等）",
    icon = Icons.Default.LocationOn,
    iconTint = Color(0xFF81C784),
    onClick = onNavigateToLocation
)
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/settings/SettingsScreen.kt
git commit -m "feat: add location management entry to settings screen"
```

### Task 13: Update BackupManager for Location Data

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/file/BackupManager.kt`

**Step 1: Add locations to BackupData**

```kotlin
data class BackupData(
    // ... existing fields ...
    val locations: List<Location> = emptyList(),  // Add with default for backward compat
)
```

**Step 2: Update export**

In `exportToJson()`, add to the `BackupData` construction inside `database.withTransaction`:

```kotlin
locations = database.locationDao().getAllLocationsList(),
```

**Step 3: Update import**

In `importFromJson()`, add after the seasons import inside `database.withTransaction`:

```kotlin
backupData.locations.forEach { database.locationDao().insertLocation(it); imported++ }
```

This must be inserted BEFORE items import, since items reference locations via FK.

**Step 4: Update CSV export if applicable**

If there's a CSV export function, add Location columns. Check the existing CSV export and add location name resolution for items.

**Step 5: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/file/BackupManager.kt
git commit -m "feat: add Location to backup export/import with backward compatibility"
```

### Task 14: Version Bump + Build Verification

**Files:**
- Modify: `app/build.gradle.kts`

**Step 1: Bump version**

In `app/build.gradle.kts`, update:
- `versionCode` from current value to current + 1
- `versionName` from "2.1.1" to "2.2"

**Step 2: Build**

```bash
./gradlew.bat assembleRelease
```

Verify build succeeds with no errors.

**Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "chore: bump version to 2.2 for location feature"
```
