# 8项功能优化实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实施8项功能优化：Tab重命名、待补尾款状态、来源字段、属性管理合并、个人页面改造、位置卡片跳转、套装搜索、付款日历增强

**Architecture:** 在现有 MVVM + Repository 架构上扩展。数据库从 v8 升级到 v9（新增 Source 表 + Item.source 字段）。新增 PENDING_BALANCE 枚举值（无需 migration，TypeConverter 按字符串存储）。新增 AttributeManageScreen 和个人信息区域。改造 PaymentCalendarScreen 增加月度汇总。

**Tech Stack:** Kotlin, Jetpack Compose, Room, DataStore, Coil, Material3

**Design doc:** `docs/plans/2026-02-22-feature-optimizations-design.md`

---

## Task 1: Tab 重命名（功能1）

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt` — BottomNavItems 中 "服饰" → "首页"，"设置" → "个人"
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt` — 顶部标题 "服饰" → "首页"，子tab "已拥有" → "服饰"
- Modify: `app/src/main/java/com/lolita/app/ui/screen/settings/SettingsScreen.kt` — 顶部标题 "设置" → "个人"

**Step 1: 修改底部导航标签**

在 `LolitaNavHost.kt` 的 `BottomNavItems` 中：
- 将第一个 tab 的 label 从 `"服饰"` 改为 `"首页"`
- 将最后一个 tab 的 label 从 `"设置"` 改为 `"个人"`

**Step 2: 修改 ItemListScreen 标题和子tab**

在 `ItemListScreen.kt` 中：
- `GradientTopAppBar` 的 title 从 `"服饰"` 改为 `"首页"`
- 子 tab 标签从 `"已拥有"` 改为 `"服饰"`

**Step 3: 修改 SettingsScreen 标题**

在 `SettingsScreen.kt` 中：
- `GradientTopAppBar` 的 title 从 `"设置"` 改为 `"个人"`

**Step 4: Commit**
```bash
git add -A && git commit -m "feat: rename tabs - 服饰→首页, 已拥有→服饰, 设置→个人"
```

---
## Task 2: 新增 PENDING_BALANCE 枚举值（功能2 - 数据层）

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/local/entity/Enums.kt` — 新增 PENDING_BALANCE

**Step 1: 添加枚举值**

在 `Enums.kt` 的 `ItemStatus` 中添加 `PENDING_BALANCE`：
```kotlin
enum class ItemStatus {
    OWNED,
    WISHED,
    PENDING_BALANCE
}
```

TypeConverter 已按字符串存储枚举名，无需数据库 migration。

**Step 2: Commit**
```bash
git add -A && git commit -m "feat: add PENDING_BALANCE to ItemStatus enum"
```

---

## Task 3: 待补尾款状态联动逻辑（功能2 - 业务逻辑）

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/repository/PaymentRepository.kt` — updatePayment 时联动更新 Item 状态
- Modify: `app/src/main/java/com/lolita/app/data/repository/ItemRepository.kt` — 新增 checkAndUpdatePendingBalanceStatus 方法
- Modify: `app/src/main/java/com/lolita/app/data/local/dao/ItemDao.kt` — 新增 updateItemStatus query
- Modify: `app/src/main/java/com/lolita/app/data/local/dao/PaymentDao.kt` — 新增查询某 item 是否有未付尾款的 query

**Step 1: ItemDao 新增 status 更新方法**

在 `ItemDao.kt` 中添加：
```kotlin
@Query("UPDATE items SET status = :status WHERE id = :itemId")
suspend fun updateItemStatus(itemId: Long, status: String)
```

**Step 2: PaymentDao 新增查询方法**

在 `PaymentDao.kt` 中添加查询：检查某 item 是否有未付尾款（通过 Price → Item 关联）
```kotlin
@Query("""
    SELECT COUNT(*) FROM payments p
    INNER JOIN prices pr ON p.price_id = pr.id
    WHERE pr.item_id = :itemId AND pr.type = 'DEPOSIT_BALANCE'
    AND p.is_paid = 0 AND p.description LIKE '%尾款%'
""")
suspend fun countUnpaidBalanceForItem(itemId: Long): Int
```

**Step 3: ItemRepository 新增状态检查方法**

在 `ItemRepository.kt` 中添加：
```kotlin
suspend fun checkAndUpdatePendingBalanceStatus(itemId: Long) {
    val unpaidCount = paymentDao.countUnpaidBalanceForItem(itemId)
    val item = itemDao.getItemById(itemId) ?: return
    if (item.status == ItemStatus.OWNED || item.status == ItemStatus.PENDING_BALANCE) {
        val newStatus = if (unpaidCount > 0) ItemStatus.PENDING_BALANCE else ItemStatus.OWNED
        if (item.status != newStatus) {
            itemDao.updateItemStatus(itemId, newStatus.name)
        }
    }
}
```

**Step 4: PaymentRepository 联动调用**

在 `PaymentRepository.updatePayment()` 中，付款状态变更后调用 `itemRepository.checkAndUpdatePendingBalanceStatus(itemId)`。需要通过 Price 获取 itemId。

**Step 5: Commit**
```bash
git add -A && git commit -m "feat: auto-toggle PENDING_BALANCE status on payment changes"
```

---
## Task 4: 待补尾款 UI 展示（功能2 - UI层）

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt` — 「服饰」子tab 同时显示 OWNED + PENDING_BALANCE，列表中用标签区分
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemListViewModel.kt` — 筛选逻辑包含 PENDING_BALANCE

**Step 1: 修改 ViewModel 筛选逻辑**

在 `ItemListViewModel.kt` 中，「服饰」tab（原已拥有）的筛选条件从 `status == OWNED` 改为 `status == OWNED || status == PENDING_BALANCE`。

**Step 2: 列表项标签区分**

在 `ItemListScreen.kt` 中，当 item.status == PENDING_BALANCE 时，在列表项上显示一个「待补尾款」标签（用 Surface + Text，颜色用 warning 色如 `Color(0xFFFF9800)`）。

**Step 3: 筛选器增加选项**

在筛选面板中增加「待补尾款」筛选选项，允许用户只看待补尾款的服饰。

**Step 4: Commit**
```bash
git add -A && git commit -m "feat: show PENDING_BALANCE items in clothing tab with label"
```

---

## Task 5: Source 实体和 DAO（功能3 - 数据层）

**Files:**
- Create: `app/src/main/java/com/lolita/app/data/local/entity/Source.kt`
- Create: `app/src/main/java/com/lolita/app/data/local/dao/SourceDao.kt`
- Modify: `app/src/main/java/com/lolita/app/data/local/entity/Item.kt` — 新增 source 字段
- Modify: `app/src/main/java/com/lolita/app/data/local/LolitaDatabase.kt` — version 8→9, 新增 migration, 注册 Source 实体和 DAO
- Modify: `app/src/main/java/com/lolita/app/data/local/dao/ItemDao.kt` — 新增 source 相关 query

**Step 1: 创建 Source 实体**

参考 `Style.kt` 结构：
```kotlin
@Entity(tableName = "sources", indices = [Index(value = ["name"], unique = true)])
data class Source(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "is_preset", defaultValue = "0") val isPreset: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
```

**Step 2: 创建 SourceDao**

参考 `StyleDao.kt`，包含 getAllSources (Flow), getSourceById, insert, update, delete, getSourceByName, getAllSourcesList, deleteAllSources。

**Step 3: Item 实体新增 source 字段**

在 `Item.kt` 中添加：
```kotlin
@ColumnInfo(name = "source") val source: String? = null,
```

**Step 4: ItemDao 新增 source 相关方法**

```kotlin
@Query("UPDATE items SET source = :newName WHERE source = :oldName")
suspend fun updateItemsSource(oldName: String, newName: String)

@Query("UPDATE items SET source = NULL WHERE source = :name")
suspend fun clearItemsSource(name: String)

@Query("SELECT * FROM items WHERE source = :source ORDER BY created_at DESC")
fun getItemsBySource(source: String): Flow<List<Item>>
```

**Step 5: 数据库 migration v8→v9**

在 `LolitaDatabase.kt` 中：
- version 改为 9
- 新增 `Source::class` 到 entities 列表
- 新增 `abstract fun sourceDao(): SourceDao`
- 新增 MIGRATION_8_9：
  ```sql
  CREATE TABLE IF NOT EXISTS sources (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, is_preset INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL)
  CREATE UNIQUE INDEX IF NOT EXISTS index_sources_name ON sources (name)
  ALTER TABLE items ADD COLUMN source TEXT DEFAULT NULL
  INSERT preset sources: 淘宝, 咸鱼, 线下
  ```
- 在 addMigrations 中加入 MIGRATION_8_9
- 在 DatabaseCallback.onCreate 中也插入预置 sources

**Step 6: Commit**
```bash
git add -A && git commit -m "feat: add Source entity, DAO, and database migration v8→v9"
```

---
## Task 6: SourceRepository 和 AppModule 注册（功能3 - Repository层）

**Files:**
- Create: `app/src/main/java/com/lolita/app/data/repository/SourceRepository.kt`
- Modify: `app/src/main/java/com/lolita/app/di/AppModule.kt` — 注册 SourceRepository

**Step 1: 创建 SourceRepository**

参考 `StyleRepository.kt`，包含：
- `getAllSources(): Flow<List<Source>>`
- `insertSource(source: Source): Long`
- `updateSource(source: Source, oldName: String?)` — 使用 `database.withTransaction`，若改名则级联更新 items
- `deleteSource(source: Source)` — 先 `clearItemsSource`，再删除
- `getSourceByName(name: String): Source?`

**Step 2: AppModule 注册**

在 `AppModule.kt` 中添加：
```kotlin
private val _sourceRepository by lazy {
    SourceRepository(database.sourceDao(), database.itemDao(), database)
}
fun sourceRepository() = _sourceRepository
```

**Step 3: Commit**
```bash
git add -A && git commit -m "feat: add SourceRepository and register in AppModule"
```

---

## Task 7: 来源管理页面（功能3 - UI层）

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/screen/settings/SourceManageScreen.kt` — 包含 ViewModel + Screen
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/Screen.kt` — 新增 SourceManage 路由
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt` — 注册 SourceManage composable

**Step 1: 新增 Screen.SourceManage 路由**

在 `Screen.kt` 中添加：
```kotlin
data object SourceManage : Screen {
    override val route = "source_manage"
}
```

**Step 2: 创建 SourceManageScreen**

参考 `StyleManageScreen.kt` 或 `SeasonManageScreen.kt` 结构，包含：
- SourceManageViewModel（MutableStateFlow, 从 AppModule.sourceRepository() 获取数据）
- SourceManageScreen composable（GradientTopAppBar compact=true, LolitaCard 列表, 新增/编辑/删除对话框）
- 使用 SkinClickable, SkinItemAppear 等皮肤组件

**Step 3: LolitaNavHost 注册**

在 `LolitaNavHost.kt` 中添加 composable 路由：
```kotlin
composable(Screen.SourceManage.route) {
    SourceManageScreen(onBack = { navController.popBackStack() })
}
```

**Step 4: Commit**
```bash
git add -A && git commit -m "feat: add SourceManageScreen with CRUD operations"
```

---

## Task 8: Item 编辑/详情页集成来源（功能3 - 集成）

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemEditScreen.kt` — 新增来源选择器
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemDetailScreen.kt` — 展示来源
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt` — 加载 sources 列表

**Step 1: ViewModel 加载 sources**

在 ItemViewModel（或 ItemEditViewModel）中添加 sources Flow，从 `AppModule.sourceRepository().getAllSources()` 获取。

**Step 2: ItemEditScreen 新增来源选择器**

参考 style/season 选择器的模式，在编辑页面中添加来源下拉选择。使用 `ExposedDropdownMenuBox` 或对话框选择。

**Step 3: ItemDetailScreen 展示来源**

在详情页中，如果 item.source 不为 null，显示「来源：xxx」。

**Step 4: Commit**
```bash
git add -A && git commit -m "feat: integrate source field in item edit and detail screens"
```

---

## Task 9: BackupManager 集成来源（功能3 - 备份）

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/file/BackupManager.kt` — BackupData 新增 sources 字段，导出/导入逻辑

**Step 1: BackupData 新增 sources**

```kotlin
val sources: List<Source> = emptyList(),  // 默认空列表保证向后兼容
```

**Step 2: 导出逻辑**

在 exportToJson 中添加 `sources = database.sourceDao().getAllSourcesList()`。
在 exportToCsv 中添加 sources 表的 CSV 输出。

**Step 3: 导入逻辑**

在 importFromJson 中添加 sources 的导入（先删后插），处理 sources 为 null 的旧备份兼容。

**Step 4: Commit**
```bash
git add -A && git commit -m "feat: include sources in backup/restore"
```

---
## Task 10: 属性管理合并页面（功能4）

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/screen/settings/AttributeManageScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/Screen.kt` — 新增 AttributeManage 路由
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt` — 注册 AttributeManage composable
- Modify: `app/src/main/java/com/lolita/app/ui/screen/settings/SettingsScreen.kt` — 用「属性管理」替换6个独立管理入口

**Step 1: 新增 Screen.AttributeManage 路由**

在 `Screen.kt` 中添加：
```kotlin
data object AttributeManage : Screen {
    override val route = "attribute_manage"
}
```

**Step 2: 创建 AttributeManageScreen**

纯 UI 页面，无需 ViewModel。列出6个属性管理入口：
- 品牌管理 → BrandManage
- 类型管理 → CategoryManage
- 风格管理 → StyleManage
- 季节管理 → SeasonManage
- 位置管理 → LocationManage
- 来源管理 → SourceManage

使用 `GradientTopAppBar(compact = true)`，每项用 `SettingsMenuItem` 样式（复用或提取公共组件）。

```kotlin
@Composable
fun AttributeManageScreen(
    onBack: () -> Unit,
    onNavigateToBrand: () -> Unit,
    onNavigateToCategory: () -> Unit,
    onNavigateToStyle: () -> Unit,
    onNavigateToSeason: () -> Unit,
    onNavigateToLocation: () -> Unit,
    onNavigateToSource: () -> Unit
)
```

**Step 3: LolitaNavHost 注册**

添加 composable 路由，传入所有导航回调。

**Step 4: SettingsScreen 改造**

移除6个独立管理入口（品牌、类型、风格、季节、位置），替换为一个「属性管理」入口：
```kotlin
SettingsMenuItem(
    title = "属性管理",
    description = "管理品牌、类型、风格、季节、位置、来源",
    icon = Icons.Default.Settings,  // 或合适的图标
    iconTint = Color(0xFF7E57C2),
    onClick = onNavigateToAttributeManage
)
```

同时更新 SettingsScreen 的参数：移除 onNavigateToBrand/Category/Style/Season/Location，新增 onNavigateToAttributeManage。

**Step 5: Commit**
```bash
git add -A && git commit -m "feat: merge attribute management into single entry point"
```

---

## Task 11: 个人页面 - AppPreferences 扩展（功能5 - 数据层）

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/preferences/AppPreferences.kt` — 新增 nickname, avatarPath

**Step 1: 添加 preference keys**

在 `AppPreferences.kt` companion object 中添加：
```kotlin
private val NICKNAME = stringPreferencesKey("nickname")
private val AVATAR_PATH = stringPreferencesKey("avatar_path")
```

**Step 2: 添加读写方法**

```kotlin
val nickname: Flow<String> = context.dataStore.data
    .map { it[NICKNAME] ?: "" }

suspend fun setNickname(name: String) {
    context.dataStore.edit { it[NICKNAME] = name }
}

val avatarPath: Flow<String> = context.dataStore.data
    .map { it[AVATAR_PATH] ?: "" }

suspend fun setAvatarPath(path: String) {
    context.dataStore.edit { it[AVATAR_PATH] = path }
}
```

**Step 3: Commit**
```bash
git add -A && git commit -m "feat: add nickname and avatarPath to AppPreferences"
```

---

## Task 12: 个人页面 - UI 改造（功能5 - UI层）

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/settings/SettingsScreen.kt` — 顶部新增个人信息区域
- Modify: `app/src/main/java/com/lolita/app/ui/screen/settings/SettingsViewModel.kt`（如需新建）— 加载统计数据

**Step 1: 创建或修改 SettingsViewModel**

需要加载：
- nickname, avatarPath（从 AppPreferences）
- 服饰总数（从 ItemRepository）
- 套装数（从 CoordinateRepository）
- 总花费（从 PriceRepository）

**Step 2: 个人信息区域 UI**

在 SettingsScreen 顶部（Scaffold content 最上方）添加个人信息卡片：
- 圆形头像（Coil AsyncImage，clip CircleShape），点击触发图片选择
- 昵称文本，点击弹出编辑对话框
- 数据概览行：服饰 N件 | 套装 N套 | 总花费 ¥N

使用 `ImageFileHelper` 处理头像图片存储。

**Step 3: 图片选择器**

使用 `rememberLauncherForActivityResult(ActivityResultContracts.GetContent())` 选择图片，通过 `ImageFileHelper.copyImageToAppStorage()` 保存，路径存入 AppPreferences。

**Step 4: Commit**
```bash
git add -A && git commit -m "feat: add profile section with avatar, nickname, and stats overview"
```

---
## Task 13: 位置卡片点击跳转（功能6）

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt` — 位置 tab 中卡片添加点击事件
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/FilteredItemListViewModel.kt` — 新增 "location" filterType 支持
- Modify: `app/src/main/java/com/lolita/app/data/local/dao/ItemDao.kt` — 新增按 locationId 查询（如不存在）
- Modify: `app/src/main/java/com/lolita/app/data/repository/ItemRepository.kt` — 新增 getItemsByLocationId 方法（如不存在）

**Step 1: ItemDao/ItemRepository 新增按 location 查询**

在 `ItemDao.kt` 中添加（如不存在）：
```kotlin
@Query("SELECT * FROM items WHERE location_id = :locationId ORDER BY created_at DESC")
fun getItemsByLocationId(locationId: Long): Flow<List<Item>>
```

在 `ItemRepository.kt` 中暴露该方法。

**Step 2: FilteredItemListViewModel 支持 location 筛选**

在 `FilteredItemListViewModel.kt` 的 `loadItems()` when 分支中添加：
```kotlin
"location" -> itemRepository.getItemsByLocationId(filterValue.toLong())
```

**Step 3: ItemListScreen 位置卡片添加点击**

在位置 tab（page 0）中，给每个位置卡片添加 onClick，导航到：
```kotlin
Screen.FilteredItemList.createRoute(
    filterType = "location",
    filterValue = location.id.toString(),
    title = location.name
)
```

**Step 4: Commit**
```bash
git add -A && git commit -m "feat: click location card to view filtered item list"
```

---

## Task 14: 套装编辑搜索服装（功能7）

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateEditScreen.kt` — Item 选择区域新增搜索框

**Step 1: 添加搜索状态**

在 CoordinateEditScreen 或其 ViewModel 中添加：
```kotlin
var itemSearchQuery by remember { mutableStateOf("") }
```

**Step 2: 添加搜索框 UI**

在 Item 选择列表顶部添加 `OutlinedTextField`：
```kotlin
OutlinedTextField(
    value = itemSearchQuery,
    onValueChange = { itemSearchQuery = it },
    label = { Text("搜索服饰") },
    modifier = Modifier.fillMaxWidth(),
    singleLine = true,
    leadingIcon = { SkinIcon(IconKey.Search, modifier = Modifier.size(20.dp)) }
)
```

**Step 3: 过滤列表**

将 items 列表用 searchQuery 过滤：
```kotlin
val filteredItems = allItems.filter {
    itemSearchQuery.isBlank() || it.name.contains(itemSearchQuery, ignoreCase = true)
}
```

用 `filteredItems` 替代原来的 `allItems` 渲染列表。

**Step 4: Commit**
```bash
git add -A && git commit -m "feat: add search field in coordinate edit item selection"
```

---

## Task 15: 付款日历月度增强（功能8）

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt` — 增强月度汇总和日历标记

**Step 1: ViewModel 新增月度已付款统计**

在 `PaymentCalendarViewModel` 的 `PaymentCalendarUiState` 中添加：
```kotlin
val monthPaidTotal: Double = 0.0,
val monthPaidCount: Int = 0,
val monthUnpaidCount: Int = 0
```

在 `loadData()` 中计算这些值（从 monthPayments 中统计）。

**Step 2: StatsRow 增强**

改造 `StatsRow` composable，展示当月汇总：
- 当月已付：金额 + 笔数
- 当月待付：金额 + 笔数
- 已逾期（保留）

```kotlin
@Composable
private fun StatsRow(
    monthPaid: Double,
    monthPaidCount: Int,
    monthUnpaid: Double,
    monthUnpaidCount: Int,
    overdue: Double
)
```

MiniStatCard 中显示金额和笔数：
```kotlin
MiniStatCard("当月已付", monthPaid, "${monthPaidCount}笔", Color(0xFF4CAF50), Modifier.weight(1f))
MiniStatCard("当月待付", monthUnpaid, "${monthUnpaidCount}笔", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
MiniStatCard("已逾期", overdue, "", Color(0xFFD32F2F), Modifier.weight(1f))
```

**Step 3: 日历格子颜色增强**

当前 `CalendarGrid` 已有 `DayStatus`（OVERDUE, UPCOMING, UNPAID, ALL_PAID）和对应颜色标记，这部分已经实现。确认颜色区分清晰即可：
- 绿色圆点 = 全部已付
- 蓝色圆点 = 待付款
- 橙色圆点 = 即将到期
- 红色圆点 = 已逾期

如需增强，可以在已付款日期上显示绿色圆点（当前只在有未付款时才显示标记）。

**Step 4: 支持 HorizontalPager 左右滑动切换月份**

将 MonthHeader + CalendarGrid 包裹在 `HorizontalPager` 中，支持左右滑动切换月份（当前只有箭头按钮）。或者保持箭头按钮方式（已实现），视复杂度决定。

**Step 5: Commit**
```bash
git add -A && git commit -m "feat: enhance payment calendar with monthly paid/unpaid summary"
```

---

## Task 16: 版本号更新和 Release 构建

**Files:**
- Modify: `app/build.gradle.kts` — versionCode + versionName

**Step 1: 更新版本号**

```kotlin
versionCode = 3  // 从当前值 +1
versionName = "2.4"  // minor version bump
```

**Step 2: Release 构建**

```bash
./gradlew.bat assembleRelease
```

**Step 3: Commit**
```bash
git add -A && git commit -m "chore: bump version to 2.4 for 8 feature optimizations"
```

---

## 执行顺序和依赖关系

```
Task 1 (Tab重命名) — 无依赖，可独立执行
Task 2 (枚举值) — 无依赖
Task 3 (联动逻辑) — 依赖 Task 2
Task 4 (待补尾款UI) — 依赖 Task 2, 3
Task 5 (Source数据层) — 无依赖
Task 6 (SourceRepository) — 依赖 Task 5
Task 7 (来源管理页面) — 依赖 Task 5, 6
Task 8 (Item集成来源) — 依赖 Task 5, 6
Task 9 (备份集成) — 依赖 Task 5, 6
Task 10 (属性管理合并) — 依赖 Task 7
Task 11 (AppPreferences) — 无依赖
Task 12 (个人页面UI) — 依赖 Task 10, 11
Task 13 (位置跳转) — 无依赖
Task 14 (套装搜索) — 无依赖
Task 15 (日历增强) — 无依赖
Task 16 (版本号) — 依赖所有其他 Task
```

**推荐执行批次：**
1. 批次A（并行）：Task 1, Task 2, Task 5, Task 11, Task 13, Task 14, Task 15
2. 批次B（依赖A）：Task 3, Task 6
3. 批次C（依赖B）：Task 4, Task 7, Task 8, Task 9
4. 批次D（依赖C）：Task 10
5. 批次E（依赖D）：Task 12
6. 批次F（最后）：Task 16
