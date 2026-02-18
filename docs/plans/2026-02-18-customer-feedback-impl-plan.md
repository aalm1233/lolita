# Customer Feedback 6 Issues Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix 6 customer-reported issues: coordinate deletion crash, balance payment import failure, coordinate list price display, coordinate cover image, coordinate list grid modes, and bottom nav bar height.

**Architecture:** Incremental changes to existing MVVM + Repository pattern. One DB migration (v5→v6) for Coordinate.imageUrl. UI changes in Coordinate screens and LolitaNavHost.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Material3, Coil

---

### Task 1: Database Migration — Coordinate 增加 imageUrl 字段

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/local/entity/Coordinate.kt`
- Modify: `app/src/main/java/com/lolita/app/data/local/LolitaDatabase.kt`

**Step 1: Coordinate 实体增加 imageUrl**

在 `Coordinate.kt` 的 data class 中，在 `description` 字段后增加：

```kotlin
@ColumnInfo(name = "image_url")
val imageUrl: String? = null,
```

**Step 2: LolitaDatabase 增加迁移并升版本**

1. `@Database` 注解的 `version = 5` 改为 `version = 6`
2. 在 `MIGRATION_4_5` 之后增加：

```kotlin
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE coordinates ADD COLUMN image_url TEXT DEFAULT NULL")
    }
}
```

3. `.addMigrations(...)` 中追加 `MIGRATION_5_6`

**Step 3: Build 验证**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add -A && git commit -m "feat: add imageUrl to Coordinate entity with DB migration v5→v6"
```

---

### Task 2: 尾款导入失败修复

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/import/TaobaoImportViewModel.kt`

**Step 1: 修改 executeImport() 的验证逻辑**

在 `TaobaoImportViewModel.kt` 第 374 行，将：

```kotlin
val validIndices = allItems.indices.filter { allItems[it].brandId > 0 && allItems[it].categoryId > 0 }.toSet()
```

改为：

```kotlin
val validIndices = allItems.indices.filter { idx ->
    val item = allItems[idx]
    if (item.paymentRole == PaymentRole.BALANCE && item.pairedWith != null) {
        // 已配对的尾款项只需价格有效
        item.price > 0
    } else {
        // 普通项和未配对尾款项需要品牌和分类
        item.brandId > 0 && item.categoryId > 0
    }
}.toSet()
```

**Step 2: 合并导入时尾款项只取价格和日期**

在同文件第 395-425 行的定金尾款合并导入块中，将 Item 创建部分：

```kotlin
color = mainItem.color.ifBlank { balanceItem.color.ifBlank { null } },
size = mainItem.size.ifBlank { balanceItem.size.ifBlank { null } },
imageUrl = mainItem.imageUrl ?: balanceItem.imageUrl,
```

改为（不从尾款项回填）：

```kotlin
color = mainItem.color.ifBlank { null },
size = mainItem.size.ifBlank { null },
imageUrl = mainItem.imageUrl,
```

**Step 3: Build 验证**

Run: `./gradlew.bat assembleDebug`

**Step 4: Commit**

```bash
git add -A && git commit -m "fix: balance payment import skips brand/category validation for paired items"
```

---

### Task 3: 套装删除功能 — ViewModel 层

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateViewModel.kt`

**Step 1: CoordinateDetailViewModel 增加 deleteCoordinate 方法**

在 `CoordinateDetailViewModel` 类中（`removeItemFromCoordinate` 方法之后）增加：

```kotlin
fun deleteCoordinate(onSuccess: () -> Unit) {
    val coordinate = _uiState.value.coordinate ?: return
    viewModelScope.launch {
        try {
            val coordinateRepository = com.lolita.app.di.AppModule.coordinateRepository()
            coordinateRepository.deleteCoordinate(coordinate)
            onSuccess()
        } catch (e: Exception) {
            // 删除失败静默处理，coordinate 已不存在时也视为成功
        }
    }
}
```

**Step 2: Build 验证**

Run: `./gradlew.bat assembleDebug`

**Step 3: Commit**

```bash
git add -A && git commit -m "feat: add deleteCoordinate to CoordinateDetailViewModel"
```

---

### Task 4: 套装删除功能 — DetailScreen UI

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateDetailScreen.kt`

**Step 1: 增加 onDelete 参数和删除确认对话框**

1. `CoordinateDetailScreen` 函数签名增加参数：

```kotlin
fun CoordinateDetailScreen(
    coordinateId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: () -> Unit = {},  // 新增
    viewModel: CoordinateDetailViewModel = viewModel()
)
```

2. 在现有的 `itemToRemove` 状态旁增加：

```kotlin
var showDeleteDialog by remember { mutableStateOf(false) }
```

3. 在 `itemToRemove` 的 AlertDialog 之后增加删除确认对话框：

```kotlin
if (showDeleteDialog) {
    AlertDialog(
        onDismissRequest = { showDeleteDialog = false },
        title = { Text("确认删除") },
        text = { Text("确定要删除套装「${uiState.coordinate?.name ?: ""}」吗？套装内的服饰不会被删除。") },
        confirmButton = {
            TextButton(
                onClick = {
                    showDeleteDialog = false
                    viewModel.deleteCoordinate { onDelete() }
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("删除")
            }
        },
        dismissButton = {
            TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
        }
    )
}
```

4. TopBar actions 中编辑按钮之后增加删除按钮：

```kotlin
actions = {
    IconButton(onClick = { onEdit(coordinateId) }) {
        Icon(Icons.Default.Edit, contentDescription = "编辑")
    }
    IconButton(onClick = { showDeleteDialog = true }) {
        Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
    }
}
```

**Step 2: Build 验证**

Run: `./gradlew.bat assembleDebug`

**Step 3: Commit**

```bash
git add -A && git commit -m "feat: add delete button and confirmation dialog to CoordinateDetailScreen"
```

---

### Task 5: 套装删除功能 — ListScreen 长按菜单

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateListScreen.kt`

**Step 1: CoordinateListScreen 增加导航回调**

函数签名增加：

```kotlin
fun CoordinateListScreen(
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToAdd: () -> Unit = {},
    onNavigateToEdit: (Long) -> Unit = {},  // 新增
    viewModel: CoordinateListViewModel = viewModel()
)
```

同步更新 `CoordinateListContent` 签名。

**Step 2: CoordinateCard 增加长按菜单**

1. 增加 import：`androidx.compose.foundation.combinedClickable`, `androidx.compose.foundation.ExperimentalFoundationApi`, `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.setValue`

2. CoordinateCard 增加参数：

```kotlin
private fun CoordinateCard(
    coordinate: Coordinate,
    itemCount: Int,
    itemImages: List<String?>,
    onClick: () -> Unit,
    onEdit: () -> Unit,      // 新增
    onDelete: () -> Unit,    // 新增
    modifier: Modifier = Modifier
)
```

3. 在 CoordinateCard 内部增加长按菜单状态和 DropdownMenu（参考 ItemGridCard 的实现模式）：

```kotlin
var showMenu by remember { mutableStateOf(false) }
```

4. LolitaCard 改为 `combinedClickable`，`onLongClick = { showMenu = true }`

5. 在 Card 内容末尾增加 DropdownMenu：

```kotlin
DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
    DropdownMenuItem(text = { Text("编辑") }, onClick = { showMenu = false; onEdit() })
    DropdownMenuItem(text = { Text("删除") }, onClick = { showMenu = false; onDelete() })
}
```

**Step 3: 列表调用处增加删除确认对话框**

在 `CoordinateListContent` 中增加：

```kotlin
var coordinateToDelete by remember { mutableStateOf<Coordinate?>(null) }

// 删除确认对话框
coordinateToDelete?.let { coord ->
    AlertDialog(
        onDismissRequest = { coordinateToDelete = null },
        title = { Text("确认删除") },
        text = { Text("确定要删除套装「${coord.name}」吗？") },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.deleteCoordinate(coord)
                    coordinateToDelete = null
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("删除") }
        },
        dismissButton = {
            TextButton(onClick = { coordinateToDelete = null }) { Text("取消") }
        }
    )
}
```

items 调用处传入回调：

```kotlin
CoordinateCard(
    coordinate = coordinate,
    itemCount = uiState.itemCounts[coordinate.id] ?: 0,
    itemImages = uiState.itemImagesByCoordinate[coordinate.id] ?: emptyList(),
    onClick = { onNavigateToDetail(coordinate.id) },
    onEdit = { onNavigateToEdit(coordinate.id) },
    onDelete = { coordinateToDelete = coordinate },
    modifier = Modifier.animateItem()
)
```

**Step 4: Build 验证**

Run: `./gradlew.bat assembleDebug`

**Step 5: Commit**

```bash
git add -A && git commit -m "feat: add long-press context menu (edit/delete) to CoordinateListScreen"
```

---

### Task 6: 套装删除功能 — 导航层接入

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt`

**Step 1: CoordinateDetailScreen 增加 onDelete 回调**

在 `LolitaNavHost.kt` 第 282-286 行，CoordinateDetailScreen 调用处增加 `onDelete`：

```kotlin
CoordinateDetailScreen(
    coordinateId = coordinateId,
    onBack = { navController.popBackStack() },
    onEdit = { navController.navigate(Screen.CoordinateEdit.createRoute(it)) },
    onDelete = { navController.popBackStack() }
)
```

**Step 2: CoordinateListScreen 增加 onNavigateToEdit**

找到 ItemListScreen 调用处（约第 146-160 行），其中已有 `onNavigateToCoordinateDetail`。CoordinateListScreen 不在 NavHost 中直接调用（它嵌在 ItemListScreen 的 tab 中）。

需要确认 CoordinateListScreen 的调用位置。如果它在 ItemListScreen 内部作为 tab 使用，则 `onNavigateToEdit` 需要从 ItemListScreen 传递下去。在 ItemListScreen 中找到 CoordinateListContent 的调用处，增加 `onNavigateToEdit` 参数：

```kotlin
onNavigateToEdit = { coordinateId ->
    navController.navigate(Screen.CoordinateEdit.createRoute(coordinateId))
}
```

**Step 3: Build 验证**

Run: `./gradlew.bat assembleDebug`

**Step 4: Commit**

```bash
git add -A && git commit -m "feat: wire up coordinate delete and edit navigation in LolitaNavHost"
```

---

### Task 7: 套装编辑页增加封面图选择

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateViewModel.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateEditScreen.kt`

**Step 1: CoordinateEditViewModel 增加图片状态**

1. `CoordinateEditUiState` 增加字段：

```kotlin
data class CoordinateEditUiState(
    val name: String = "",
    val description: String = "",
    val imageUrl: String? = null,  // 新增
    val allItems: List<Item> = emptyList(),
    val selectedItemIds: Set<Long> = emptySet(),
    val coordinateNames: Map<Long, String> = emptyMap(),
    val isSaving: Boolean = false
)
```

2. `CoordinateEditViewModel` 增加方法：

```kotlin
fun updateImageUrl(url: String?) {
    _uiState.value = _uiState.value.copy(imageUrl = url)
}
```

3. `loadCoordinate` 方法中加载图片：

```kotlin
coordinate?.let {
    originalCreatedAt = it.createdAt
    _uiState.value = _uiState.value.copy(
        name = it.name,
        description = it.description,
        imageUrl = it.imageUrl  // 新增
    )
}
```

4. `save()` 和 `update()` 方法中 Coordinate 构造增加 `imageUrl`：

```kotlin
val coordinate = Coordinate(
    name = _uiState.value.name,
    description = _uiState.value.description,
    imageUrl = _uiState.value.imageUrl,  // 新增
    // ... 其余字段
)
```

**Step 2: CoordinateEditScreen 增加图片选择 UI**

1. 增加 import：`Activity`, `ActivityResultContracts`, `rememberLauncherForActivityResult`, `AsyncImage`, `ImageFileHelper`, `Dispatchers`, `withContext`

2. 在 `CoordinateEditScreen` 中增加图片选择 launcher：

```kotlin
val context = LocalContext.current
val imagePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
) { uri ->
    uri?.let {
        coroutineScope.launch {
            val path = withContext(Dispatchers.IO) {
                com.lolita.app.data.file.ImageFileHelper.copyToInternalStorage(context, it)
            }
            path?.let { viewModel.updateImageUrl(it) }
        }
    }
}
```

3. 在套装名称输入框之前，增加图片选择区域：

```kotlin
// 封面图选择
Box(
    modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f)
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable { imagePickerLauncher.launch("image/*") },
    contentAlignment = Alignment.Center
) {
    if (uiState.imageUrl != null) {
        AsyncImage(
            model = uiState.imageUrl,
            contentDescription = "封面图",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Pink400, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(4.dp))
            Text("添加封面图", style = MaterialTheme.typography.bodySmall, color = Pink400)
        }
    }
}
Spacer(modifier = Modifier.height(16.dp))
```

**Step 3: Build 验证**

Run: `./gradlew.bat assembleDebug`

**Step 4: Commit**

```bash
git add -A && git commit -m "feat: add cover image picker to CoordinateEditScreen"
```

---

### Task 8: 套装列表 — ViewModel 增加价格和列数状态

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateViewModel.kt`

**Step 1: CoordinateListUiState 增加字段**

```kotlin
data class CoordinateListUiState(
    val coordinates: List<Coordinate> = emptyList(),
    val itemCounts: Map<Long, Int> = emptyMap(),
    val itemImagesByCoordinate: Map<Long, List<String?>> = emptyMap(),
    val priceByCoordinate: Map<Long, Double> = emptyMap(),  // 新增
    val columnsPerRow: Int = 1,  // 新增
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)
```

**Step 2: CoordinateListViewModel 增加 priceRepository 依赖和列数切换**

1. 构造函数增加 priceRepository：

```kotlin
class CoordinateListViewModel(
    private val coordinateRepository: CoordinateRepository = com.lolita.app.di.AppModule.coordinateRepository(),
    private val itemRepository: ItemRepository = com.lolita.app.di.AppModule.itemRepository(),
    private val priceRepository: PriceRepository = com.lolita.app.di.AppModule.priceRepository()
) : ViewModel()
```

2. `loadCoordinates()` 中 combine 增加价格数据。将现有的 3 路 combine 改为 4 路：

```kotlin
private fun loadCoordinates() {
    viewModelScope.launch {
        combine(
            coordinateRepository.getAllCoordinates(),
            coordinateRepository.getItemCountsByCoordinate(),
            itemRepository.getAllItems(),
            priceRepository.getItemPriceSums()
        ) { coordinates, itemCounts, allItems, priceSums ->
            val countMap = itemCounts.associate { it.coordinate_id to it.itemCount }
            val imageMap = allItems
                .filter { it.coordinateId != null }
                .groupBy { it.coordinateId!! }
                .mapValues { (_, items) -> items.take(4).map { it.imageUrl } }

            // 按 coordinateId 汇总价格
            val priceMap = priceSums.associate { it.itemId to it.totalPrice }
            val coordPriceMap = allItems
                .filter { it.coordinateId != null }
                .groupBy { it.coordinateId!! }
                .mapValues { (_, items) ->
                    items.sumOf { priceMap[it.id] ?: 0.0 }
                }

            Triple(coordinates, countMap, imageMap) to coordPriceMap
        }.collect { (triple, coordPriceMap) ->
            val (coordinates, countMap, imageMap) = triple
            _uiState.value = _uiState.value.copy(
                coordinates = coordinates,
                itemCounts = countMap,
                itemImagesByCoordinate = imageMap,
                priceByCoordinate = coordPriceMap,
                isLoading = false
            )
        }
    }
}
```

3. 增加列数切换方法：

```kotlin
fun setColumns(count: Int) {
    _uiState.value = _uiState.value.copy(columnsPerRow = count)
}
```

**Step 3: Build 验证**

Run: `./gradlew.bat assembleDebug`

**Step 4: Commit**

```bash
git add -A && git commit -m "feat: add price aggregation and column toggle to CoordinateListViewModel"
```

---

### Task 9: 套装列表 — UI 重构（3 种展示模式 + 封面图 + 价格 + 长按菜单）

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateListScreen.kt`

这是最大的 UI 改动任务。需要：

**Step 1: 增加必要 import**

```kotlin
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
```

**Step 2: CoordinateListScreen TopBar 增加切换按钮**

在 `Scaffold` 的 `topBar` 中，`GradientTopAppBar` 增加 `actions`：

```kotlin
GradientTopAppBar(
    title = { Text("套装管理") },
    compact = true,
    actions = {
        IconButton(onClick = {
            val next = when (uiState.columnsPerRow) { 1 -> 2; 2 -> 3; else -> 1 }
            viewModel.setColumns(next)
        }) {
            Icon(
                imageVector = when (uiState.columnsPerRow) {
                    1 -> Icons.Default.ViewAgenda
                    2 -> Icons.Default.GridView
                    else -> Icons.Default.Apps
                },
                contentDescription = "切换列数",
                tint = Pink400
            )
        }
    }
)
```

注意：需要将 `uiState` 的 collectAsState 提升到 `CoordinateListScreen` 层级（目前在 `CoordinateListContent` 中）。

**Step 3: CoordinateListContent 条件渲染 LazyColumn / LazyVerticalGrid**

参考 ItemListScreen 的模式：

```kotlin
if (uiState.columnsPerRow == 1) {
    LazyColumn(...) {
        items(uiState.coordinates, key = { it.id }) { coordinate ->
            CoordinateCard(
                coordinate = coordinate,
                itemCount = uiState.itemCounts[coordinate.id] ?: 0,
                itemImages = uiState.itemImagesByCoordinate[coordinate.id] ?: emptyList(),
                totalPrice = uiState.priceByCoordinate[coordinate.id] ?: 0.0,
                onClick = { onNavigateToDetail(coordinate.id) },
                onEdit = { onNavigateToEdit(coordinate.id) },
                onDelete = { coordinateToDelete = coordinate },
                modifier = Modifier.animateItem()
            )
        }
    }
} else {
    LazyVerticalGrid(
        columns = GridCells.Fixed(uiState.columnsPerRow),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(uiState.coordinates, key = { it.id }) { coordinate ->
            CoordinateGridCard(
                coordinate = coordinate,
                itemCount = uiState.itemCounts[coordinate.id] ?: 0,
                itemImages = uiState.itemImagesByCoordinate[coordinate.id] ?: emptyList(),
                totalPrice = uiState.priceByCoordinate[coordinate.id] ?: 0.0,
                onClick = { onNavigateToDetail(coordinate.id) },
                onEdit = { onNavigateToEdit(coordinate.id) },
                onDelete = { coordinateToDelete = coordinate }
            )
        }
    }
}
```

**Step 4: 更新 CoordinateCard（1 列模式）**

增加 `totalPrice` 参数，在名称行旁显示价格。增加封面图显示（如果有 imageUrl 则显示，否则保留现有的服饰缩略图拼图）。增加 `onEdit`/`onDelete` 参数和长按菜单。

**Step 5: 新增 CoordinateGridCard（2/3 列模式）**

参考 ItemGridCard 的布局：

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CoordinateGridCard(
    coordinate: Coordinate,
    itemCount: Int,
    itemImages: List<String?>,
    totalPrice: Double,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    LolitaCard(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { showMenu = true })
        ) {
            Column {
                // 图片区域
                Box {
                    if (coordinate.imageUrl != null) {
                        AsyncImage(
                            model = coordinate.imageUrl,
                            contentDescription = coordinate.name,
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.8f)
                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // 无封面图时显示服饰缩略图拼图或占位
                        // ... 渐变背景 + 套装图标
                    }

                    // 价格标签（右上角）
                    if (totalPrice > 0) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                            color = Color.Black.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "¥%.0f".format(totalPrice),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // 件数标签（左上角）
                    if (itemCount > 0) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                            color = Pink400.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${itemCount}件",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // 信息区域
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = coordinate.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (coordinate.description.isNotEmpty()) {
                        Text(
                            text = coordinate.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 长按菜单
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("编辑") }, onClick = { showMenu = false; onEdit() })
                DropdownMenuItem(text = { Text("删除") }, onClick = { showMenu = false; onDelete() })
            }
        }
    }
}
```

**Step 6: Build 验证**

Run: `./gradlew.bat assembleDebug`

**Step 7: Commit**

```bash
git add -A && git commit -m "feat: coordinate list with 3 display modes, cover image, price, and context menu"
```

---

### Task 10: 套装详情页展示封面图

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateDetailScreen.kt`

**Step 1: CoordinateInfoCard 增加封面图展示**

在 `CoordinateInfoCard` 的 Column 最顶部（名称行之前），如果 coordinate.imageUrl 不为空则显示封面图：

```kotlin
if (coordinate.imageUrl != null) {
    AsyncImage(
        model = coordinate.imageUrl,
        contentDescription = "封面图",
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Crop
    )
    Spacer(modifier = Modifier.height(12.dp))
}
```

需要增加 import：`coil.compose.AsyncImage`, `ContentScale`, `RoundedCornerShape`, `Modifier.clip`, `Modifier.aspectRatio`

**Step 2: Build 验证**

Run: `./gradlew.bat assembleDebug`

**Step 3: Commit**

```bash
git add -A && git commit -m "feat: show cover image in CoordinateDetailScreen"
```

---

### Task 11: 底部菜单栏高度缩窄

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt`

**Step 1: NavigationBar 添加高度限制**

在 `LolitaNavHost.kt` 第 101-103 行，NavigationBar 增加 Modifier 和 windowInsets：

```kotlin
NavigationBar(
    modifier = Modifier.height(64.dp),
    containerColor = MaterialTheme.colorScheme.surface,
    contentColor = Pink400,
    windowInsets = WindowInsets(0, 0, 0, 0)
)
```

需要增加 import：`androidx.compose.foundation.layout.height`, `androidx.compose.ui.unit.dp`（如果尚未导入）

**Step 2: Build 验证**

Run: `./gradlew.bat assembleDebug`

**Step 3: Commit**

```bash
git add -A && git commit -m "fix: reduce bottom navigation bar height from 80dp to 64dp"
```

---

### Task 12: 最终集成验证

**Step 1: Clean build**

Run: `./gradlew.bat clean assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 2: 手动验证清单**

在设备/模拟器上安装并验证：

1. 套装删除：详情页删除按钮 → 确认 → 返回列表；列表页长按 → 删除 → 确认
2. 淘宝导入：导入含尾款（无品牌/分类）的订单 → 不报错，定金尾款正确合并
3. 套装列表价格：列表卡片显示汇总价格
4. 套装封面图：编辑页选择图片 → 列表和详情页展示
5. 套装列表模式：点击切换按钮 → 1/2/3 列正确切换
6. 底部菜单栏：高度缩窄，无白边

**Step 3: Final commit**

如有微调，统一提交。
