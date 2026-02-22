# 三功能实现计划：穿搭日记图片改大 / 品牌商标图 / 颜色多选

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现三个功能增强：穿搭日记图片尺寸增大、品牌实体支持商标图上传与展示、颜色字段改为多选。

**Architecture:** 三个功能独立实现。功能一纯 UI 调整；功能二需要 DB migration 新增 Brand.logoUrl 字段 + 全局品牌展示组件；功能三需要 DB migration 将 Item.color 改为 JSON 数组存储 + 新建颜色选择器组件。

**Tech Stack:** Kotlin, Jetpack Compose, Room (Kapt), Coil, Gson, Material3

**Note:** 项目无测试套件，跳过 TDD 步骤，每个 Task 完成后通过 `./gradlew.bat assembleDebug` 验证编译。

---

## Task 1: 穿搭日记图片尺寸增大

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/outfit/OutfitLogListScreen.kt:192`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/outfit/OutfitLogDetailScreen.kt:173-174`

**Step 1: 修改列表页图片高度**

在 `OutfitLogListScreen.kt` 第 192 行，将：
```kotlin
.height(160.dp)
```
改为：
```kotlin
.height(220.dp)
```

**Step 2: 修改详情页多图宽度**

在 `OutfitLogDetailScreen.kt` 第 173 行，将：
```kotlin
.width(220.dp)
```
改为：
```kotlin
.width(300.dp)
```

**Step 3: 验证编译**

Run: `./gradlew.bat assembleDebug`

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/outfit/OutfitLogListScreen.kt app/src/main/java/com/lolita/app/ui/screen/outfit/OutfitLogDetailScreen.kt
git commit -m "feat: increase outfit diary image sizes"
```

---

## Task 2: Brand 实体新增 logoUrl 字段 + DB Migration

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/local/entity/Brand.kt`
- Modify: `app/src/main/java/com/lolita/app/data/local/LolitaDatabase.kt`

**Step 1: Brand 实体新增 logoUrl**

在 `Brand.kt` 的 `data class Brand` 中，在 `name` 字段后新增：
```kotlin
@ColumnInfo(name = "logo_url")
val logoUrl: String? = null,
```

**Step 2: 添加 DB Migration 9→10**

在 `LolitaDatabase.kt` 中：
- 将 `version = 9` 改为 `version = 10`
- 在 `MIGRATION_8_9` 后新增：
```kotlin
private val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE brands ADD COLUMN logo_url TEXT DEFAULT NULL")
    }
}
```
- 在 `.addMigrations(...)` 中追加 `MIGRATION_9_10`

**Step 3: 验证编译**

Run: `./gradlew.bat assembleDebug`

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/local/entity/Brand.kt app/src/main/java/com/lolita/app/data/local/LolitaDatabase.kt
git commit -m "feat: add logoUrl field to Brand entity with migration v9→v10"
```

---

## Task 3: BrandManageScreen 支持上传品牌商标图

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/settings/BrandManageScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/settings/BrandManageViewModel.kt`

**Step 1: BrandManageViewModel 增加 logo 处理**

在 `BrandManageViewModel.kt` 中：
- `addBrand` 方法签名改为 `fun addBrand(name: String, logoUrl: String? = null)`，创建 Brand 时传入 logoUrl
- `updateBrand` 方法签名改为 `fun updateBrand(brand: Brand, newName: String, logoUrl: String? = null)`，copy 时传入 logoUrl
- 新增 `fun updateBrandLogo(brand: Brand, logoUrl: String?)` 方法，用于单独更新 logo

**Step 2: BrandManageScreen 修改对话框**

在 `BrandManageScreen.kt` 中：

**AddBrandDialog：**
- 添加 `rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia())` 图片选择器
- 添加 `var logoUri by remember { mutableStateOf<String?>(null) }` 状态
- 在品牌名称输入框下方添加图片选择区域：点击选择图片，已选时显示缩略图 + 删除按钮
- `onConfirm` 回调改为传递 `(name: String, logoUrl: String?)`

**EditBrandDialog：**
- 同样添加图片选择器
- 初始值从 `brand.logoUrl` 加载
- `onConfirm` 回调改为传递 `(newName: String, logoUrl: String?)`

**BrandCard：**
- 在品牌名称左侧添加 32dp 圆形图片（Coil AsyncImage）
- 无 logo 时显示品牌名首字作为占位符（圆形背景 + 居中文字）

**Step 3: 图片存储**

在 Screen 层调用 `ImageFileHelper.copyToInternalStorage(context, uri)` 获取本地路径，传给 ViewModel。

**Step 4: 验证编译**

Run: `./gradlew.bat assembleDebug`

**Step 5: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/settings/BrandManageScreen.kt app/src/main/java/com/lolita/app/ui/screen/settings/BrandManageViewModel.kt
git commit -m "feat: support brand logo upload in brand management"
```

---

## Task 4: 全局品牌 Logo 展示组件 + 各页面集成

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/screen/common/BrandLogo.kt` — 可复用的品牌 Logo 组件
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemDetailScreen.kt:246-249` — 品牌行显示 logo
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemEditScreen.kt` — 品牌选择器对话框中显示 logo
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt` — 卡片中品牌名旁显示 logo

**Step 1: 创建 BrandLogo 可复用组件**

创建 `BrandLogo.kt`，包含一个 `@Composable fun BrandLogo(brand: Brand?, size: Dp = 24.dp)` 组件：
- 有 logoUrl 时：Coil `AsyncImage` 加载圆形裁剪图片
- 无 logoUrl 时：圆形背景 + 品牌名首字居中

**Step 2: ItemDetailScreen 集成**

在 `ItemDetailScreen.kt` 第 246-249 行的品牌 DetailRow 处，将纯文本改为 Row 包含 BrandLogo + 品牌名。

**Step 3: ItemEditScreen 集成**

在品牌选择器对话框的品牌列表项中，每项前添加 BrandLogo 小图标。

**Step 4: ItemListScreen 集成**

在卡片中品牌名显示处，前面添加 BrandLogo 小图标。需要将 brands 列表（而非仅 brandNames Map）传递到卡片组件，或在 ItemCardData 中增加 brandLogoUrl 字段。

**Step 5: 验证编译**

Run: `./gradlew.bat assembleDebug`

**Step 6: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/common/BrandLogo.kt app/src/main/java/com/lolita/app/ui/screen/item/ItemDetailScreen.kt app/src/main/java/com/lolita/app/ui/screen/item/ItemEditScreen.kt app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt
git commit -m "feat: display brand logo across item screens"
```

---

## Task 5: Item 实体 color→colors 改造 + DB Migration

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/local/entity/Item.kt:82-83`
- Modify: `app/src/main/java/com/lolita/app/data/local/LolitaDatabase.kt`

**Step 1: 修改 Item 实体**

在 `Item.kt` 第 82-83 行，将：
```kotlin
@ColumnInfo(name = "color")
val color: String? = null,
```
改为：
```kotlin
@ColumnInfo(name = "colors")
val colors: String? = null,
```

这里 `colors` 存储 JSON 数组字符串如 `["粉色","白色"]`，由现有的 `List<String>` Gson TypeConverter 不直接适用（因为字段类型是 String? 而非 List<String>）。保持 String? 类型，在 ViewModel 层做 Gson 序列化/反序列化。

**Step 2: 添加 DB Migration 10→11**

在 `LolitaDatabase.kt` 中：
- 将 `version = 10` 改为 `version = 11`
- 新增 migration：
```kotlin
private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Rename color to colors and convert single value to JSON array
        db.execSQL("ALTER TABLE items RENAME COLUMN color TO colors")
        // Convert existing single values to JSON arrays: "粉色" → "[\"粉色\"]"
        db.execSQL("""
            UPDATE items SET colors = '[\"' || colors || '\"]'
            WHERE colors IS NOT NULL AND colors != ''
        """.trimIndent())
    }
}
```
- 在 `.addMigrations(...)` 中追加 `MIGRATION_10_11`

注意：`ALTER TABLE RENAME COLUMN` 需要 SQLite 3.25.0+（Android API 30+）。如果需要支持更低版本，改用创建新表+复制数据的方式。项目 minSdk 26，Room 会处理兼容性——实际上 Room 2.7.0 在低版本 Android 上使用自带的 SQLite 版本，支持 RENAME COLUMN。如果编译有问题，改用以下替代方案：
```kotlin
// 替代方案：不 rename，直接加新列 + 迁移数据 + 删旧列
db.execSQL("ALTER TABLE items ADD COLUMN colors TEXT DEFAULT NULL")
db.execSQL("UPDATE items SET colors = '[\"' || color || '\"]' WHERE color IS NOT NULL AND color != ''")
// 然后需要重建表去掉 color 列（Room 标准重建流程）
```

**Step 3: 验证编译**

Run: `./gradlew.bat assembleDebug`

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/local/entity/Item.kt app/src/main/java/com/lolita/app/data/local/LolitaDatabase.kt
git commit -m "feat: migrate Item.color to Item.colors as JSON array"
```

---

## Task 6: 颜色选择器组件 + ItemEditScreen 集成

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/screen/common/ColorSelector.kt` — 颜色多选组件
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemEditScreen.kt:235-243`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt`

**Step 1: 定义预定义颜色常量**

在 `ColorSelector.kt` 中定义：
```kotlin
data class PredefinedColor(val name: String, val hex: Long)

val PREDEFINED_COLORS = listOf(
    PredefinedColor("白色", 0xFFFFFFFF),
    PredefinedColor("黑色", 0xFF000000),
    PredefinedColor("粉色", 0xFFFFB6C1),
    PredefinedColor("红色", 0xFFFF0000),
    PredefinedColor("蓝色", 0xFF4169E1),
    PredefinedColor("紫色", 0xFF8A2BE2),
    PredefinedColor("绿色", 0xFF228B22),
    PredefinedColor("黄色", 0xFFFFD700),
    PredefinedColor("米色", 0xFFF5F5DC),
    PredefinedColor("棕色", 0xFF8B4513),
    PredefinedColor("灰色", 0xFF808080),
    PredefinedColor("酒红", 0xFF722F37),
    PredefinedColor("藏蓝", 0xFF003153),
)
```

**Step 2: 创建 ColorSelector 组件**

```kotlin
@Composable
fun ColorSelector(
    selectedColors: List<String>,
    onColorsChanged: (List<String>) -> Unit
)
```

UI 结构：
- `FlowRow` 布局
- 每个颜色项：圆形色块(20dp) + 文字，选中时有勾选标记和高亮边框
- 点击切换选中/取消
- 末尾 "+" 按钮，点击弹出自定义颜色对话框（输入名称 + 选色值）
- 自定义颜色对话框中提供简单的色板选择（几行预设色块供点击）

**Step 3: ItemViewModel 修改**

- `ItemEditUiState.color: String?` → `colors: List<String> = emptyList()`
- `updateColor(color: String?)` → `updateColors(colors: List<String>)`
- `saveItem()` 中：将 `List<String>` 用 Gson 序列化为 JSON 字符串存入 `Item.colors`
- `loadItem()` 中：将 `Item.colors` JSON 字符串反序列化为 `List<String>` 存入 UI state
- 颜色选项收集 `colorOpts`：从所有 items 的 colors JSON 中解析出所有颜色名去重

**Step 4: ItemEditScreen 替换颜色输入框**

将第 235-243 行的 `OutlinedTextField` 替换为：
```kotlin
ColorSelector(
    selectedColors = uiState.colors,
    onColorsChanged = { viewModel.updateColors(it) }
)
```

**Step 5: 验证编译**

Run: `./gradlew.bat assembleDebug`

**Step 6: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/common/ColorSelector.kt app/src/main/java/com/lolita/app/ui/screen/item/ItemEditScreen.kt app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt
git commit -m "feat: add color multi-select with predefined colors and custom support"
```

---

## Task 7: 颜色展示与筛选适配

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemDetailScreen.kt:265-267` — 颜色展示改为多色块标签
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt:192,384,413-414` — 筛选逻辑适配
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt` — 筛选器颜色改为多选 Chip

**Step 1: ItemDetailScreen 颜色展示**

将第 265-267 行：
```kotlin
item.color?.let { color ->
    if (color.isNotEmpty()) DetailRow(label = "颜色", value = color)
}
```
改为解析 JSON 数组，用 FlowRow 展示多个色块+文字标签。复用 `ColorSelector.kt` 中的 `PREDEFINED_COLORS` 查找色值。

**Step 2: ItemViewModel 颜色选项收集**

第 192 行，将：
```kotlin
val colorOpts = items.mapNotNull { it.color?.takeIf { c -> c.isNotBlank() } }.distinct().sorted()
```
改为从 JSON 数组中解析所有颜色名：
```kotlin
val colorOpts = items.flatMap { item ->
    item.colors?.let { json ->
        try { Gson().fromJson(json, Array<String>::class.java).toList() }
        catch (_: Exception) { emptyList() }
    } ?: emptyList()
}.filter { it.isNotBlank() }.distinct().sorted()
```

**Step 3: 筛选逻辑适配**

第 413-414 行，将：
```kotlin
if (color != null) {
    result = result.filter { it.color == color }
}
```
改为"包含任一选中颜色"匹配：
```kotlin
if (color != null) {
    result = result.filter { item ->
        val itemColors = item.colors?.let { json ->
            try { Gson().fromJson(json, Array<String>::class.java).toList() }
            catch (_: Exception) { emptyList() }
        } ?: emptyList()
        itemColors.contains(color)
    }
}
```

**Step 4: 验证编译**

Run: `./gradlew.bat assembleDebug`

**Step 5: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/ItemDetailScreen.kt app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt
git commit -m "feat: adapt color display and filtering for multi-select"
```

---

## Task 8: BackupManager 兼容性更新

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/file/BackupManager.kt`

**Step 1: Brand 序列化/反序列化**

Brand 新增了 `logoUrl` 字段，由于 Gson 序列化会自动包含新字段，导出无需改动。导入时 Gson 反序列化旧备份中缺少 `logoUrl` 的 JSON 会自动赋 null（因为字段有默认值），无需特殊处理。

确认 CSV 导出中 Brand 部分是否需要新增 logoUrl 列。如果有 CSV 导出 Brand 的逻辑，添加 logoUrl 列。

**Step 2: Item color→colors 兼容**

CSV 导出中将 `color` 列名改为 `colors`。导入旧备份时需要兼容：
- JSON 导入：如果旧备份 Item 有 `color` 字段而无 `colors` 字段，将 `color` 值转为 JSON 数组赋给 `colors`
- 可通过 Gson 自定义 TypeAdapter 或导入后手动处理

**Step 3: 验证编译**

Run: `./gradlew.bat assembleDebug`

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/file/BackupManager.kt
git commit -m "feat: update backup compatibility for brand logo and color multi-select"
```

---

## Task 9: 版本号更新 + Release 构建

**Files:**
- Modify: `app/build.gradle.kts`

**Step 1: 更新版本号**

三个新功能属于 minor 版本更新。在 `app/build.gradle.kts` 中：
- `versionCode` 递增 1
- `versionName` 更新为下一个 minor 版本

**Step 2: Release 构建**

Run: `./gradlew.bat assembleRelease`

**Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "chore: bump version for outfit image resize, brand logo, color multi-select"
```
