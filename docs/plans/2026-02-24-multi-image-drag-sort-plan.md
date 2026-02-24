# 多图片支持 + 穿搭内物品拖拽排序 实现计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为物品和穿搭组合添加多图片支持（最多5张），并在穿搭组合详情中支持物品拖拽排序。

**Architecture:** Item 和 Coordinate 的 `imageUrl: String?` 改为 `imageUrls: List<String>`，复用现有 Gson TypeConverter。Item 新增 `coordinateOrder: Int` 字段用于穿搭内排序。DB migration v14→v15 通过全表重建实现列变更（项目已有此模式）。UI 层新增共享的 `MultiImageEditor`、`ImageGalleryPager`、`FullScreenImageViewer` 组件，以及 `DraggableList` 拖拽排序组件。

**Tech Stack:** Kotlin, Jetpack Compose, Room 2.7.0, Coil 2.7.0, Compose Foundation (HorizontalPager, detectDragGesturesAfterLongPress)

---

## 注意事项

- 项目无测试套件，每个 Task 完成后运行 `./gradlew.bat assembleDebug` 验证编译
- Room `@Relation` 不支持 ORDER BY，穿搭内物品排序在 ViewModel 层用 `sortedBy { it.coordinateOrder }` 实现
- SQLite < 3.25.0（minSdk 26）不支持 RENAME COLUMN，需全表重建来移除旧列（项目已有此模式，见 MIGRATION_11_12）
- 所有代码中引用 `imageUrl` 的地方都需要改为 `imageUrls`，包括列表页缩略图、推荐页、Widget 等
- BackupManager 导入需兼容旧格式（`imageUrl` 单值字段）

---

### Task 1: Entity 变更 — Item 和 Coordinate

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/local/entity/Item.kt`
- Modify: `app/src/main/java/com/lolita/app/data/local/entity/Coordinate.kt`

**Step 1: 修改 Item 实体**

在 `Item.kt` 中：
- 将 `@ColumnInfo(name = "image_url") val imageUrl: String? = null` 改为 `@ColumnInfo(name = "image_urls") val imageUrls: List<String> = emptyList()`
- 新增 `@ColumnInfo(name = "coordinate_order") val coordinateOrder: Int = 0`

**Step 2: 修改 Coordinate 实体**

在 `Coordinate.kt` 中：
- 将 `@ColumnInfo(name = "image_url") val imageUrl: String? = null` 改为 `@ColumnInfo(name = "image_urls") val imageUrls: List<String> = emptyList()`

**Step 3: 暂不编译**（等 Migration 完成后一起编译）

---

### Task 2: Database Migration v14→v15

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/local/LolitaDatabase.kt`

**Step 1: 更新数据库版本**

将 `version = 14` 改为 `version = 15`。

**Step 2: 添加 MIGRATION_14_15**

在现有 migrations 之后添加。全表重建模式（参考 MIGRATION_11_12）：

Item 表重建：
1. `CREATE TABLE items_new` — 用新 schema（`image_urls TEXT NOT NULL DEFAULT '[]'`，`coordinate_order INTEGER NOT NULL DEFAULT 0`，去掉 `image_url`）
2. `INSERT INTO items_new SELECT ... CASE WHEN image_url IS NOT NULL THEN '["' || REPLACE(REPLACE(image_url, '\', '\\'), '"', '\"') || '"]' ELSE '[]' END AS image_urls, 0 AS coordinate_order ... FROM items`
3. `DROP TABLE items`
4. `ALTER TABLE items_new RENAME TO items`
5. 重建所有 indices（name, coordinate_id, brand_id, category_id, status, priority, location_id）

Coordinate 表重建：
1. `CREATE TABLE coordinates_new` — 用新 schema（`image_urls TEXT NOT NULL DEFAULT '[]'`，去掉 `image_url`）
2. `INSERT INTO coordinates_new SELECT ... CASE WHEN image_url IS NOT NULL THEN '["' || REPLACE(REPLACE(image_url, '\', '\\'), '"', '\"') || '"]' ELSE '[]' END AS image_urls ... FROM coordinates`
3. `DROP TABLE coordinates`
4. `ALTER TABLE coordinates_new RENAME TO coordinates`
5. 重建 indices（name, created_at）

**Step 3: 注册 migration**

在 `Room.databaseBuilder` 的 `.addMigrations(...)` 中添加 `MIGRATION_14_15`。

**Step 4: 编译验证**

运行 `./gradlew.bat assembleDebug`，此时会有大量编译错误（imageUrl 引用），这是预期的。先 commit entity + migration 变更。

**Step 5: Commit**

```bash
git add -A && git commit -m "feat: add migration v14→v15 for multi-image and coordinate order"
```

---

### Task 3: DAO 变更

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/local/dao/ItemDao.kt`
- Modify: `app/src/main/java/com/lolita/app/data/local/dao/CoordinateDao.kt`

**Step 1: ItemDao 新增批量更新排序方法**

添加方法：
```kotlin
@Query("UPDATE items SET coordinate_order = :order WHERE id = :itemId")
suspend fun updateCoordinateOrder(itemId: Long, order: Int)
```

**Step 2: CoordinateDao — CoordinateWithItems 排序**

`@Relation` 不支持 ORDER BY，排序在 ViewModel 层处理。DAO 无需改动查询。

但需要确认 `CoordinateWithItems` data class 中 `items: List<Item>` 的类型不需要改动（Item 实体变更后自动适配）。

**Step 3: Commit**

```bash
git add -A && git commit -m "feat: add coordinate order update method to ItemDao"
```

---

### Task 4: Repository 变更

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/repository/ItemRepository.kt`
- Modify: `app/src/main/java/com/lolita/app/data/repository/CoordinateRepository.kt`

**Step 1: ItemRepository — 适配多图片删除**

`deleteItem()` 方法中，将单图片删除改为遍历 `imageUrls` 列表删除：
```kotlin
// 旧: item.imageUrl?.let { ImageFileHelper.deleteImage(it) }
// 新:
item.imageUrls.forEach { ImageFileHelper.deleteImage(it) }
item.sizeChartImageUrl?.let { ImageFileHelper.deleteImage(it) }
```

新增方法：
```kotlin
suspend fun updateCoordinateOrder(itemId: Long, order: Int) {
    itemDao.updateCoordinateOrder(itemId, order)
}
```

**Step 2: CoordinateRepository — 适配多图片删除和变更检测**

`updateCoordinateWithItems()` 中，旧图片清理逻辑从单值比较改为列表差集：
```kotlin
// 找出被移除的图片（旧列表中有但新列表中没有的）
val removedImages = oldCoordinate.imageUrls.filter { it !in coordinate.imageUrls }
removedImages.forEach { ImageFileHelper.deleteImage(it) }
```

`deleteCoordinate()` 中：
```kotlin
// 旧: coordinate.imageUrl?.let { ImageFileHelper.deleteImage(it) }
// 新:
coordinate.imageUrls.forEach { ImageFileHelper.deleteImage(it) }
```

**Step 3: Commit**

```bash
git add -A && git commit -m "feat: adapt repositories for multi-image and coordinate order"
```

---

### Task 5: BackupManager 兼容性

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/file/BackupManager.kt`

**Step 1: 导出适配**

导出逻辑无需特殊处理 — Gson 会自动将 `List<String>` 序列化为 JSON 数组，`Int` 序列化为数字。新字段 `imageUrls` 和 `coordinateOrder` 会自动包含在导出中。

**Step 2: 导入兼容旧格式**

在 `migrateBackupData()` 或新增 `migrateImageFields()` 方法中处理旧备份：

对 Item 列表：如果 JSON 中有 `imageUrl`（旧字段）但没有 `imageUrls`（新字段），需要在反序列化后转换。

方案：使用自定义 Gson TypeAdapter 或在导入后手动修补。推荐后者（更简单）：

```kotlin
// 在 importFromJson 的数据解析后、插入前
val migratedItems = backupData.items.map { item ->
    if (item.imageUrls.isEmpty() && /* check raw JSON for imageUrl field */) {
        // 需要从原始 JSON 中提取 imageUrl
    }
    item
}
```

更实际的方案：在 `migrateJsonString()` 中用字符串替换处理：
```kotlin
// 将 "imageUrl":"xxx" 转为 "imageUrls":["xxx"]
// 将 "imageUrl":null 转为 "imageUrls":[]
```

在 `migrateJsonString()` 方法中添加正则替换，处理 items 和 coordinates 数组中的 `imageUrl` → `imageUrls` 字段迁移。同时确保新字段 `coordinateOrder` 缺失时默认为 0（Kotlin data class 默认值会处理）。

**Step 3: collectImagePaths 适配**

`collectImagePaths()` 方法中，将 `item.imageUrl` 改为 `item.imageUrls`（flatMap），`coordinate.imageUrl` 改为 `coordinate.imageUrls`（flatMap）。

**Step 4: CSV 导出适配**

`exportToCsv()` 中 Item 和 Coordinate 的列头和数据行，将 `imageUrl` 改为 `imageUrls`（输出为逗号分隔或 JSON 字符串）。

**Step 5: Commit**

```bash
git add -A && git commit -m "feat: adapt BackupManager for multi-image fields with backward compatibility"
```

---

### Task 6: ViewModel 变更

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateViewModel.kt`

**Step 1: ItemEditViewModel**

`ItemEditUiState` 中：
- `imageUrl: String? = null` → `imageUrls: List<String> = emptyList()`
- 移除 `imageUrlToDelete: String? = null`，改为 `imageUrlsToDelete: List<String> = emptyList()`

更新方法：
- `updateImageUrl(url)` → `addImage(url: String)` — 追加到列表（最多5张）
- 新增 `removeImage(index: Int)` — 从列表移除，加入待删除列表
- 新增 `reorderImages(fromIndex: Int, toIndex: Int)` — 交换图片顺序
- `loadItem()` 中加载 `imageUrls` 而非 `imageUrl`
- `saveItem()` 中构建 Item 时用 `imageUrls`，保存后清理 `imageUrlsToDelete` 中的文件

**Step 2: CoordinateEditViewModel**

`CoordinateEditUiState` 中：
- `imageUrl: String? = null` → `imageUrls: List<String> = emptyList()`

更新方法：
- `updateImageUrl(url)` → `addImage(url: String)`
- 新增 `removeImage(index: Int)`
- 新增 `reorderImages(fromIndex: Int, toIndex: Int)`
- `save()` 和 `update()` 中用 `imageUrls`

**Step 3: CoordinateDetailViewModel**

新增方法：
```kotlin
fun reorderItems(fromIndex: Int, toIndex: Int) {
    val currentItems = _uiState.value.items.toMutableList()
    val item = currentItems.removeAt(fromIndex)
    currentItems.add(toIndex, item)
    _uiState.update { it.copy(items = currentItems) }
    // 持久化新顺序
    viewModelScope.launch {
        currentItems.forEachIndexed { index, item ->
            itemRepository.updateCoordinateOrder(item.id, index)
        }
    }
}
```

在 `loadCoordinate()` 的 collect 中，对 items 按 `coordinateOrder` 排序：
```kotlin
items = (result?.items ?: emptyList()).sortedBy { it.coordinateOrder }
```

`addItemToCoordinate()` 中，设置新物品的 `coordinateOrder` 为当前最大值 + 1。

**Step 4: 编译验证**

运行 `./gradlew.bat assembleDebug`，此时仍会有 UI 层编译错误（Screen 文件引用旧字段）。

**Step 5: Commit**

```bash
git add -A && git commit -m "feat: adapt ViewModels for multi-image and coordinate order"
```

---

### Task 7: 共享 UI 组件

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/component/MultiImageEditor.kt`
- Create: `app/src/main/java/com/lolita/app/ui/component/ImageGalleryPager.kt`
- Create: `app/src/main/java/com/lolita/app/ui/component/FullScreenImageViewer.kt`

**Step 1: MultiImageEditor — 编辑页面用的多图片编辑器**

```kotlin
@Composable
fun MultiImageEditor(
    imageUrls: List<String>,
    maxImages: Int = 5,
    onAddImage: (String) -> Unit,
    onRemoveImage: (Int) -> Unit,
    onReorderImages: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
)
```

布局：
- `LazyRow`，高度 120.dp
- 每个图片项：100.dp x 120.dp，`RoundedCornerShape(12.dp)`，右上角删除按钮（X）
- 末尾添加按钮：虚线边框 + "+" 图标 + "添加图片" 文字
- 图片数量 >= maxImages 时隐藏添加按钮
- 使用 `ActivityResultContracts.PickVisualMedia()` 选择图片
- 选择后调用 `ImageFileHelper.copyToInternalStorage()` 再回调 `onAddImage`

**Step 2: ImageGalleryPager — 详情页面用的图片画廊**

```kotlin
@Composable
fun ImageGalleryPager(
    imageUrls: List<String>,
    onImageClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = ""
)
```

布局：
- `HorizontalPager` 全宽，高度 380.dp（与现有 ItemDetailScreen 一致）
- 每页一个 `AsyncImage`，`ContentScale.Crop`
- 底部居中页码指示器：小圆点行，当前页高亮（皮肤主色）
- 单张或零张图片时不显示指示器
- 点击图片回调 `onImageClick(pageIndex)`

**Step 3: FullScreenImageViewer — 全屏查看对话框**

```kotlin
@Composable
fun FullScreenImageViewer(
    imageUrls: List<String>,
    initialPage: Int = 0,
    onDismiss: () -> Unit
)
```

布局：
- 全屏 `Dialog`（`usePlatformDefaultWidth = false`）
- 黑色半透明背景
- `HorizontalPager` 全屏显示图片，`ContentScale.Fit`
- 右上角关闭按钮
- 底部页码指示器（白色圆点）

**Step 4: Commit**

```bash
git add -A && git commit -m "feat: add shared multi-image UI components"
```

---

### Task 8: ItemEditScreen 多图片集成

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemEditScreen.kt`

**Step 1: 替换 ImageUploaderSection**

将现有的 `ImageUploaderSection`（约 lines 583-660）中的单图片逻辑替换为 `MultiImageEditor`：

```kotlin
MultiImageEditor(
    imageUrls = uiState.imageUrls,
    maxImages = 5,
    onAddImage = { viewModel.addImage(it) },
    onRemoveImage = { viewModel.removeImage(it) },
    onReorderImages = { from, to -> viewModel.reorderImages(from, to) }
)
```

移除旧的 `photoPickerLauncher`、单图片显示和删除逻辑。

**Step 2: 更新 SizeChartImageSection**

`SizeChartImageSection`（约 lines 775-852）保持单图片不变（尺码表只需一张）。

**Step 3: Commit**

```bash
git add -A && git commit -m "feat: integrate MultiImageEditor into ItemEditScreen"
```

---

### Task 9: ItemDetailScreen 多图片集成

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemDetailScreen.kt`

**Step 1: 替换顶部图片区域**

将现有的单图片 `AsyncImage`（约 lines 168-218）替换为 `ImageGalleryPager`：

```kotlin
if (item.imageUrls.isNotEmpty()) {
    var showFullScreen by remember { mutableStateOf(false) }
    var selectedPage by remember { mutableIntStateOf(0) }

    ImageGalleryPager(
        imageUrls = item.imageUrls,
        onImageClick = { page ->
            selectedPage = page
            showFullScreen = true
        },
        contentDescription = item.name
    )

    if (showFullScreen) {
        FullScreenImageViewer(
            imageUrls = item.imageUrls,
            initialPage = selectedPage,
            onDismiss = { showFullScreen = false }
        )
    }
} else {
    // 保留现有的占位符卡片
}
```

**Step 2: 更新其他 imageUrl 引用**

文件中其他引用 `item.imageUrl` 的地方（如浮动顶栏的条件判断）改为 `item.imageUrls.isNotEmpty()`。

**Step 3: Commit**

```bash
git add -A && git commit -m "feat: integrate ImageGalleryPager into ItemDetailScreen"
```

---

### Task 10: CoordinateEditScreen 多图片集成

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateEditScreen.kt`

**Step 1: 替换封面图片区域**

将现有的单图片选择器（约 lines 140-163）替换为 `MultiImageEditor`：

```kotlin
MultiImageEditor(
    imageUrls = uiState.imageUrls,
    maxImages = 5,
    onAddImage = { viewModel.addImage(it) },
    onRemoveImage = { viewModel.removeImage(it) },
    onReorderImages = { from, to -> viewModel.reorderImages(from, to) }
)
```

移除旧的 `imagePickerLauncher`。

**Step 2: Commit**

```bash
git add -A && git commit -m "feat: integrate MultiImageEditor into CoordinateEditScreen"
```

---

### Task 11: CoordinateDetailScreen — 多图片 + 拖拽排序

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateDetailScreen.kt`

**Step 1: 替换封面图片区域**

在 `CoordinateInfoCard`（约 lines 254-380）中，将单图片 `AsyncImage`（约 lines 268-277）替换为 `ImageGalleryPager`：

```kotlin
if (coordinate.imageUrls.isNotEmpty()) {
    var showFullScreen by remember { mutableStateOf(false) }
    var selectedPage by remember { mutableIntStateOf(0) }

    ImageGalleryPager(
        imageUrls = coordinate.imageUrls,
        onImageClick = { page ->
            selectedPage = page
            showFullScreen = true
        },
        modifier = Modifier.clip(RoundedCornerShape(12.dp)),
        contentDescription = "封面图"
    )

    if (showFullScreen) {
        FullScreenImageViewer(
            imageUrls = coordinate.imageUrls,
            initialPage = selectedPage,
            onDismiss = { showFullScreen = false }
        )
    }
}
```

**Step 2: 物品列表拖拽排序**

在物品列表区域（`CoordinateItemCard` 的 LazyColumn/Column），实现长按拖拽：

创建一个 `rememberDragDropListState` 辅助函数，管理拖拽状态：
```kotlin
class DragDropListState(
    val lazyListState: LazyListState,
    private val onMove: (Int, Int) -> Unit
) {
    var draggedIndex by mutableIntStateOf(-1)
    var draggedOffset by mutableFloatStateOf(0f)
    // ... 拖拽逻辑
}
```

物品列表改为 `LazyColumn` + `Modifier.pointerInput` 处理长按拖拽手势：
- `detectDragGesturesAfterLongPress` 触发拖拽
- 拖拽中的物品卡片：`graphicsLayer { translationY = offset; scaleX = 1.02f; scaleY = 1.02f }` + 阴影
- 拖拽经过其他物品时交换位置（实时更新 UI）
- 松手后调用 `viewModel.reorderItems(fromIndex, toIndex)`
- 使用 `LazyColumn` 的 `animateItemPlacement()` 实现平滑动画

每个物品卡片左侧添加拖拽手柄图标（六点图标），提示用户可拖拽。

**Step 3: Commit**

```bash
git add -A && git commit -m "feat: add multi-image gallery and drag-sort to CoordinateDetailScreen"
```

---

### Task 12: 修复所有剩余 imageUrl 引用

**Files:**
- 全局搜索 `imageUrl` 和 `image_url`，修复所有引用

**Step 1: 搜索所有引用**

运行全局搜索，找出所有仍引用 `imageUrl`（非 `imageUrls`）的文件。预期需要修改的文件包括：

- `ItemListScreen.kt` — 列表缩略图：`item.imageUrl` → `item.imageUrls.firstOrNull()`
- `CoordinateListScreen.kt` — 列表缩略图：`coordinate.imageUrl` → `coordinate.imageUrls.firstOrNull()`
- `RecommendationScreen.kt` — 推荐物品缩略图
- `FilteredItemListScreen.kt` — 筛选结果缩略图
- `WishlistScreen.kt` — 愿望单缩略图
- `OutfitLogEditScreen.kt` — 物品选择器缩略图
- `QuickOutfitLogScreen.kt` — 快速记录物品缩略图
- `OutfitLogDetailScreen.kt` — 物品缩略图
- `LocationDetailScreen.kt` — 位置详情物品缩略图
- `OutfitWidget.kt` — Widget 中的物品图片
- `CoordinateDetailScreen.kt` — `CoordinateItemCard` 中的物品缩略图（约 line 398-406）
- `ItemDao.kt` — `LocationItemImage` data class 如果引用了 imageUrl
- 其他可能引用 `imageUrl` 的 ViewModel 或 Repository

**Step 2: 逐文件修复**

对每个文件，将 `item.imageUrl` 改为 `item.imageUrls.firstOrNull()`，将 `coordinate.imageUrl` 改为 `coordinate.imageUrls.firstOrNull()`。

对于条件判断：
- `if (item.imageUrl != null)` → `if (item.imageUrls.isNotEmpty())`
- `item.imageUrl!!` → `item.imageUrls.first()`

**Step 3: 编译验证**

运行 `./gradlew.bat assembleDebug`，确保零编译错误。

**Step 4: Commit**

```bash
git add -A && git commit -m "fix: update all imageUrl references to imageUrls across codebase"
```

---

### Task 13: 版本升级 + Release 构建

**Files:**
- Modify: `app/build.gradle.kts`

**Step 1: 版本升级**

- `versionCode` 从 28 → 29
- `versionName` 从 "2.14" → "2.15"

**Step 2: Release 构建**

运行 `./gradlew.bat assembleRelease`，确保构建成功。

**Step 3: Commit**

```bash
git add -A && git commit -m "chore: bump version to 2.15 (29)"
```

---

## Task 依赖关系

```
Task 1 (Entity) ──→ Task 2 (Migration) ──→ Task 3 (DAO) ──→ Task 4 (Repository)
                                                                      │
                                                                      ▼
                                              Task 5 (BackupManager) ──→ Task 6 (ViewModel)
                                                                              │
                                                                              ▼
                                                                    Task 7 (共享UI组件)
                                                                      │
                                                          ┌───────────┼───────────┐
                                                          ▼           ▼           ▼
                                                    Task 8        Task 10     Task 11
                                                  (ItemEdit)   (CoordEdit) (CoordDetail)
                                                          │           │           │
                                                          ▼           │           │
                                                    Task 9          │           │
                                                  (ItemDetail)      │           │
                                                          │           │           │
                                                          └───────────┼───────────┘
                                                                      ▼
                                                              Task 12 (修复引用)
                                                                      │
                                                                      ▼
                                                              Task 13 (版本+构建)
```

## 风险点

1. **Migration 全表重建** — Item 表有多个外键和索引，重建时必须完整复制所有列和约束，遗漏会导致数据丢失
2. **BackupManager 向后兼容** — 旧备份的 `imageUrl` 字段需要正确迁移为 `imageUrls` 列表，否则导入会丢失图片路径
3. **全局 imageUrl 引用** — 遗漏任何一处引用都会导致编译错误或运行时崩溃，Task 12 需要彻底搜索
4. **拖拽排序性能** — 大量物品时频繁 recomposition 可能卡顿，需要用 `key` 和 `animateItemPlacement` 优化
