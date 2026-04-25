# Lolita 应用代码审计报告

**审计日期**: 2026-04-23
**审计范围**: 全栈功能层代码（数据层 / 仓库层 / ViewModel / 文件操作 / 通知 / UI / 安全）
**技术栈**: Kotlin + Jetpack Compose + Room + Material3

---

## 审计概览

| 严重等级 | 数量 |
|---------|------|
| CRITICAL | 8 |
| HIGH | 25 |
| MEDIUM | 34 |
| LOW | 20 |
| **合计** | **87** |

---

## 一、CRITICAL 级别问题

### C-01 备份恢复中 clearAllTables 后导入失败导致数据全丢
- **文件**: `BackupManager.kt:260`
- **分类**: DATA_LOSS
- **描述**: 导入备份时先调用 `clearAllTables()` 清空数据库，再逐表插入。若导入中途失败（如磁盘满、JSON 格式错），Room 事务虽然能回滚，但 `clearAllTables()` 内部调用的 `sharedLibrarySyncDao` 清理可能不在同一事务保护内，且日历事件/闹钟等外部副作用不可回滚。用户原有数据被清除后无法恢复。
- **影响**: **用户全量数据丢失且无恢复路径**

### C-02 备份恢复后日历事件全部泄漏
- **文件**: `BackupManager.kt:273`
- **分类**: DATA_LOSS
- **描述**: 导入时所有 `calendarEventId` 被强制置 `null`，但旧日历事件未先删除。`clearAllTables()` 也不会清理系统日历。结果：旧日历事件在系统日历中永久残留，应用无法再管理它们。
- **影响**: **日历事件泄漏；用户无法删除/更新残留的付款提醒**

### C-03 删除 Item 时先删文件再删数据库记录
- **文件**: `ItemRepository.kt:66-68`
- **分类**: BUG / DATA_LOSS
- **描述**: `deleteItem()` 先删除图片文件（第 66-67 行），再执行 `itemDao.deleteItem()`（第 68 行）。若数据库删除失败（如 FK 约束），事务回滚但文件已不可恢复。Item 记录存活但图片文件丢失，用户看到破碎图片。
- **影响**: **永久性图片数据丢失**

### C-04 删除 Location 时先删图片再删数据库记录
- **文件**: `LocationRepository.kt:29-32`
- **分类**: BUG / DATA_LOSS
- **描述**: 与 C-03 相同模式。`deleteLocation()` 先删除位置图片，再删数据库记录。若 DB 删除失败，图片已不可恢复。且该操作无事务包裹。
- **影响**: **位置图片永久丢失，Location 记录指向不存在文件**

### C-05 OutfitLog 删除不清理 CrossRef，产生孤立记录
- **文件**: `OutfitLogRepository.kt:34-47`
- **分类**: BUG / DATA_INTEGRITY
- **描述**: `deleteOutfitLog()` 和 `deleteOutfitLogById()` 只删除 `OutfitLog` 实体，不删除 `outfit_item_cross_ref` 中的关联记录。`@Delete` 注解不会级联删除。孤立 cross-ref 累积后污染共现查询和穿搭-单品关联。
- **影响**: **数据关联性破坏；推荐算法基于脏数据**

### C-06 DailyOutfitReminderReceiver 使用 runBlocking 阻塞主线程
- **文件**: `DailyOutfitReminderReceiver.kt:18`
- **分类**: BUG / ANR
- **描述**: `onReceive()` 中使用 `runBlocking` 执行数据库查询。BroadcastReceiver 主线程被阻塞，如 DB 初始化或查询超过约 10 秒即触发 ANR，进程可能被系统杀死。`BootCompletedReceiver` 正确使用了 `goAsync()` 模式。
- **影响**: **设备 ANR；应用在开机/闹钟触发时崩溃**

### C-07 备份恢复界面 JSON/ZIP 格式选择逻辑缺失
- **文件**: `BackupRestoreScreen.kt:104`
- **分类**: BUG
- **描述**: 文件选择器接受 `application/json` 和 `application/zip` 两种 MIME 类型，但 `confirmImport()` 始终调用 `importFromJson(uri)`。用户选择 ZIP 文件时会触发 JSON 解析失败，无法恢复备份。
- **影响**: **ZIP 格式备份无法恢复；用户数据丢失风险**

### C-08 签名密钥明文存储且密码极弱
- **文件**: `local.properties:11-13`, `build.gradle.kts:31-34`
- **分类**: SECRET_EXPOSURE
- **描述**: Release 签名密钥密码以明文存储在 `local.properties` 中。虽然该文件在 `.gitignore` 中，但密码是品牌名+6位数字的字典式弱密码。密钥文件 `lolita-release.jks` 物理存在于项目根目录。
- **影响**: **攻击者可伪造官方签名 APK，用于钓鱼/恶意软件分发**

---

## 二、HIGH 级别问题

### H-01 Room Migration 10->11 生成错误 JSON
- **文件**: `LolitaDatabase.kt:288`
- **分类**: BUG / DATA_INTEGRITY
- **描述**: `UPDATE items SET colors = '["' || color || '"]'` 当 color 含双引号或反斜杠时生成畸形 JSON。

### H-02 Room Migration 11->12 修复逻辑自身有缺陷
- **文件**: `LolitaDatabase.kt:295`
- **分类**: BUG
- **描述**: `REPLACE(colors, '"', '"')` 配合 `LIKE '%\%'`，但 SQLite LIKE 中 `\` 非转义字符，导致 WHERE 条件匹配错误行。

### H-03 Room Migration 14->15 图片 URL 转换含转义问题
- **文件**: `LolitaDatabase.kt:463-466`
- **分类**: DATA_INTEGRITY
- **描述**: `image_url` 转 `image_urls` 时，反斜杠处理顺序可能导致含 `\n`/`\t` 的 URL 被错误双重转义。

### H-04 Migration 中 SQL 注入风险
- **文件**: `LolitaDatabase.kt:100-107, 266`
- **分类**: BUG
- **描述**: 预设数据（风格/来源等）中文字符串直接拼入 SQL `''`，未使用 `replace("'", "''")` 转义。DatabaseCallback 中正确转义了，但 Migration 中遗漏。

### H-05 Item.colors 字段类型不一致
- **文件**: `Item.kt:85-86`
- **分类**: BUG / DATA_INTEGRITY
- **描述**: `Item.colors` 为 `String?`，而 `CatalogEntry.colors` 为 `List<String>` + `defaultValue="[]"`。两者数据类型不一致，Item 侧未走 Gson TypeConverter 反序列化路径。

### H-06 Brand/Category 删除存在 TOCTOU 竞态
- **文件**: `BrandRepository.kt:20-25`, `CategoryRepository.kt:20-25`
- **分类**: CONCURRENCY
- **描述**: 先 `countItemsByBrand`/`countEntriesByBrand` 检查引用数，再 `deleteBrand`。两步间无事务保护，并发插入可使检查失效。

### H-07 Coordinate 更新使用过期基线对比图片
- **文件**: `CoordinateRepository.kt:47-70`
- **分类**: CONCURRENCY / BUG
- **描述**: `updateCoordinateWithItems` 在事务外读取 `oldCoordinate`，事务内使用它做图片清理对比，可能误删或漏删图片。

### H-08 Coordinate.updateCoordinate 无事务保护
- **文件**: `CoordinateRepository.kt:72-81`
- **分类**: TRANSACTION
- **描述**: `updateCoordinate` 未包裹 `withTransaction`，且数据库更新与图片清理非原子操作。

### H-09 Season 更新/删除对 CatalogEntry 逗号分隔值处理错误
- **文件**: `SeasonRepository.kt:41-57, 21-39`
- **分类**: BUG
- **描述**: Items 侧正确处理逗号分隔 season 字符串，但 CatalogEntry 侧使用精确匹配 `WHERE season = :name`，多季节值的条目不会被更新。

### H-10 OutfitLog 删除后图片文件泄漏
- **文件**: `OutfitLogRepository.kt:34-38`
- **分类**: RESOURCE_LEAK
- **描述**: 删除未在事务中执行，DB 删除成功但图片清理失败/崩溃时文件泄漏。

### H-11 Payment 插入非原子操作（DB + 日历 + 闹钟）
- **文件**: `PaymentRepository.kt:35-63`
- **分类**: TRANSACTION
- **描述**: `insertPayment` 的 DB 插入、闹钟调度、日历事件创建是三个独立操作。任一步失败导致状态不同步。

### H-12 Payment 更新先删旧日历事件再建新事件，中间失败则永久丢失
- **文件**: `PaymentRepository.kt:65-104`
- **分类**: BUG / DATA_LOSS
- **描述**: `updatePayment` 先删除旧日历事件，再创建新的。若新事件创建失败，旧事件已不可恢复。

### H-13 Price 删除先清理日历/闹钟再删 DB 记录
- **文件**: `PriceRepository.kt:51-62`
- **分类**: BUG
- **描述**: 与 C-03 同模式。日历事件/闹钟先删除，若 DB 删除失败，事件不可恢复。

### H-14 全局明文流量 `usesCleartextTraffic=true`
- **文件**: `AndroidManifest.xml:23`
- **分类**: NETWORK_SECURITY
- **描述**: 无 `network_security_config.xml` 限制，全局允许 HTTP 明文通信。同步 API 和图片下载均可被中间人攻击截获/篡改。

### H-15 同步 API URL 归一化默认使用 HTTP
- **文件**: `SharedLibrarySyncApi.kt:33-37`
- **分类**: NETWORK_SECURITY
- **描述**: 用户输入无协议前缀的 URL 时，默认添加 `http://` 而非 `https://`。

### H-16 图片下载无 HTTPS 强制且无证书固定
- **文件**: `ImageFileHelper.kt:89`
- **分类**: NETWORK_SECURITY
- **描述**: `downloadFromUrl` 使用 `HttpURLConnection`，无 HTTPS 强制、无证书固定、无 URL 协议校验。支持 `file://` 等危险协议。

### H-17 ItemEditViewModel 使用非原子 StateFlow 更新
- **文件**: `ItemViewModel.kt:708-810`
- **分类**: STATE_MANAGEMENT
- **描述**: 所有 `updateXxx` 方法使用 `_uiState.value = _uiState.value.copy(...)` 而非 `_uiState.update {}`。快速连续操作可能丢失状态更新。`saveItem` 的 `isSaving` 标志同样非原子，双击保存按钮可创建重复 Item。

### H-18 FilteredItemListViewModel 枚举/数字解析无保护
- **文件**: `FilteredItemListViewModel.kt:46, 48`
- **分类**: BUG
- **描述**: `ItemPriority.valueOf(filterValue)` 和 `filterValue.toLong()` 无 try-catch，无效值直接崩溃。

### H-19 OutfitLog 编辑时若原记录已被删除，保存将产生错误数据
- **文件**: `OutfitLogViewModel.kt:215-228`
- **分类**: BUG
- **描述**: `loadOutfitLog()` 返回 null 时 `originalItemIds` 为空集，后续 `save()` 将所有当前选中项视为新增，可能产生重复 cross-ref 或约束冲突。

### H-20 LocationDetailViewModel 批量更新 Item 无事务保护
- **文件**: `LocationDetailViewModel.kt:122-132`
- **分类**: BUG
- **描述**: `confirmPickerSelection()` 逐条更新 Item，无数据库事务。中途失败导致部分 Item 已迁移、部分未迁移。

### H-21 ItemEditScreen 顶栏返回按钮绕过未保存变更检查
- **文件**: `ItemEditScreen.kt:57-59`
- **分类**: BUG
- **描述**: `UnsavedChangesHandler` 拦截系统返回键，但顶栏返回箭头直接调用 `onBack()` 跳过检查，用户未保存的编辑将丢失。

### H-22 LolitaNavHost 导航过渡状态永远为 true
- **文件**: `LolitaNavHost.kt:210-211`
- **分类**: BUG
- **描述**: `isNavigating` 基于是否有 back stack entry 判断，而非是否正在过渡。`SkinNavigationOverlay` 始终收到 `isTransitioning=true`，导致皮肤导航动画异常。

### H-23 淘宝导入后不跳转到新创建的 Item 详情页
- **文件**: `LolitaNavHost.kt:596-601`
- **分类**: BUG
- **描述**: `onNavigateToDetail` 回调接收 `itemId` 但用 `_ -> navController.popBackStack()` 忽略它。用户导入后被弹回上一页，需手动查找新条目。

### H-24 AppModule.database 缺少 @Volatile，可能读到未初始化值
- **文件**: `AppModule.kt:10`
- **分类**: BUG
- **描述**: `lateinit var database` 无 `@Volatile` 注解。`init()` 是 `@Synchronized`，但 `database()` 读取非同步，可能因可见性问题抛出 `UninitializedPropertyAccessException`。

### H-25 LolitaApplication 创建无生命周期管理的泄漏协程
- **文件**: `LolitaApplication.kt:15`
- **分类**: MEMORY_LEAK
- **描述**: `CoroutineScope(Dispatchers.IO).launch` 无 parent Job、无引用存储、无 try-catch。协程泄漏且异常无法捕获。
---

## 三、MEDIUM 级别问题

| # | 文件 | 行号 | 分类 | 描述 |
|---|------|------|------|------|
| M-01 | LolitaDatabase.kt | 115-119 | DATA_INTEGRITY | Migration 3->4 中 category name 未 SQL 转义 |
| M-02 | LolitaDatabase.kt | 393-409 | DATA_INTEGRITY | Deposit-balance 类型价格迁移只生成一条全额付款记录 |
| M-03 | LolitaDatabase.kt | 343-349 | ERROR_HANDLING | PRAGMA 查询 cursor 异常路径未关闭 |
| M-04 | LolitaDatabase.kt | 711-949 | ERROR_HANDLING | onCreate 预设数据插入无事务包裹 |
| M-05 | Converters.kt | 17-22 | DATA_INTEGRITY | 未知 ItemStatus 静默降级为 WISHED |
| M-06 | Converters.kt | 65-69 | ERROR_HANDLING | JSON 解析失败静默返回 emptyList()，数据丢失无感知 |
| M-07 | BrandRepository.kt | 235-239 | CONCURRENCY | ensurePresetBrands 无事务，并发调用可 ABORT 冲突 |
| M-08 | CategoryRepository.kt | 43-64 | CONCURRENCY | ensurePresetCategories 同上 |
| M-09 | CatalogRepository.kt | 26-34 | RESOURCE_LEAK | 删除 entry 后图片清理崩溃则文件泄漏 |
| M-10 | CatalogRepository.kt | 29-31 | ERROR_HANDLING | 图片删除异常静默吞掉，无日志 |
| M-11 | ItemRepository.kt | 14-20 | CODE_QUALITY | 构造器接受 nullable 依赖，部分功能静默跳过 |
| M-12 | ItemRepository.kt | 54-75 | CONCURRENCY | database 为 null 时 deleteItem 无事务保护 |
| M-13 | ItemRepository.kt | 66-67 | ERROR_HANDLING | 图片删除未 try-catch，IO 异常导致整个删除失败 |
| M-14 | LocationRepository.kt | 29-32 | ERROR_HANDLING | 图片删除未 try-catch |
| M-15 | LocationRepository.kt | 29-32 | CODE_QUALITY | 删除前未检查 Item 引用，FK 约束可能导致崩溃或孤立引用 |
| M-16 | PaymentRepository.kt | 122-126 | BUG | scheduleReminderForPayment 无 try-catch 处理 SecurityException |
| M-17 | PaymentRepository.kt | 13-18 | RESOURCE_LEAK | 构造器接收 Context 可能泄漏 Activity |
| M-18 | PaymentRepository.kt | 89-103 | ERROR_HANDLING | 状态检查在 DB 更新后无事务保护 |
| M-19 | PriceRepository.kt | 54-59 | ERROR_HANDLING | context 为 null 时跳过日历/闹钟清理，事件泄漏 |
| M-20 | PriceRepository.kt | 57 | ERROR_HANDLING | CalendarEventHelper.deleteEvent 未 try-catch |
| M-21 | OutfitLogRepository.kt | 64-74 | CODE_QUALITY | getTodayOutfitLog 使用可变 java.util.Calendar |
| M-22 | BackupManager.kt | 57-58 | BUG | 缓存字段无线程安全保护 |
| M-23 | BackupManager.kt | 456 | DATA_LOSS | 不存在的图片引用在备份时静默丢弃 |
| M-24 | BackupManager.kt | 664-669 | DATA_LOSS | 回滚时先删 liveImagesDir 再恢复，中间失败则全丢 |
| M-25 | BackupManager.kt | 768-770 | ERROR_HANDLING | JSON 迁移异常被吞，Gson 反序列化报错掩盖根因 |
| M-26 | TaobaoOrderParser.kt | 30-31 | RESOURCE_LEAK | XSSFWorkbook 大文件可能导致 OOM |
| M-27 | TaobaoOrderParser.kt | 21-23 | ERROR_HANDLING | 多文件导入一个失败则全部丢弃 |
| M-28 | TaobaoOrderParser.kt | 25 | BUG | distinctBy 按 orderId 去重，同订单跨文件的不同商品被丢弃 |
| M-29 | TaobaoOrder.kt | 11 | CODE_QUALITY | 金额用 Double 存储，浮点精度问题 |
| M-30 | BootCompletedReceiver.kt | 25 | ERROR_HANDLING | withTimeout(9000) 初始化可能超时，提醒不会被重新调度 |
| M-31 | CalendarEventHelper.kt | 92-103 | BUG | 选择主要日历逻辑可能选中只读日历，后续插入静默失败 |
| M-32 | CalendarEventHelper.kt | 95 | BUG | IS_PRIMARY 列索引硬编码为 1，部分设备可能不兼容 |
| M-33 | DailyOutfitReminderScheduler.kt | 30 | BUG | setRepeating 在 Android 12+ 不可靠，且未检查 canScheduleExactAlarms |
| M-34 | BackupManager.kt | 289-313 | ERROR_HANDLING | 日历重建失败时 totalErrors 仍为 0，用户无感知 |

---

## 四、MEDIUM 级别问题（续，ViewModel/UI 层）

| # | 文件 | 行号 | 分类 | 描述 |
|---|------|------|------|------|
| M-35 | ItemViewModel.kt | 588 | BUG | pendingImageDeletions 是 MutableList，UI 线程和协程并发访问不安全 |
| M-36 | ItemViewModel.kt | 676-697 | BUG | supportingDataLoaded 标志非线程安全，并发 loadItem 可重复加载 |
| M-37 | ItemViewModel.kt | 159 | CODE_QUALITY | 直接调用 AppModule.outfitLogRepository() 而非构造注入 |
| M-38 | OutfitLogViewModel.kt | 127-129 | ERROR_HANDLING | deleteOutfitLog 异常静默吞掉，用户无反馈 |
| M-39 | OutfitLogViewModel.kt | 152-153 | STATE_MANAGEMENT | loadOutfitLogDetail 使用非原子直接赋值 |
| M-40 | OutfitLogViewModel.kt | 269-293 | STATE_MANAGEMENT | save() 非原子 isSaving 更新，存在双击重复保存风险 |
| M-41 | QuickOutfitLogViewModel.kt | 46-57 | ERROR_HANDLING | getTodayOutfitLog 无 try-catch，失败静默回退到新建模式，可能创建重复穿搭记录 |
| M-42 | RecommendationViewModel.kt | 77 | ERROR_HANDLING | e.message 可能为 null，UI 显示空错误信息 |
| M-43 | RecommendationViewModel.kt | 35 | LIFECYCLE | loadRecommendations 未防重入，多次调用导致协程竞争 |
| M-44 | LocationDetailViewModel.kt | 43-73 | LIFECYCLE | loadLocation 未取消旧 Flow 收集，重调用时旧 collector 继续运行 |
| M-45 | ItemDetailScreen.kt | 59 | CODE_QUALITY | 使用已弃用的 LocalLifecycleOwner.current |
| M-46 | ItemDetailScreen.kt | 60-63 | STATE | repeatOnLifecycle(RESUMED) 每次 resume 都全量重载数据 |
| M-47 | ItemEditScreen.kt | 747 | BUG | SizeChartImage 的 AsyncImage 未用 File() 包裹本地路径，Coil 可能加载失败 |
| M-48 | ItemListScreen.kt | 733 | BUG | SearchModeBar 忽略 placeholder 参数，硬编码为搜索服饰 |
| M-49 | ItemListScreen.kt | 132-138 | BUG | 离开 服饰 tab 时 status filter 未清除，其他 tab 可能受影响 |
| M-50 | BackupRestoreScreen.kt | 48-53 | STATE | ViewModel 状态更新为多次 .copy() 非原子操作 |
| M-51 | BackupManager.kt | 746-749 | CODE_QUALITY | CSV 导出使用非标准的 === SECTION === 标记，不可导入 |

---

## 五、LOW 级别问题

| # | 文件 | 行号 | 分类 | 描述 |
|---|------|------|------|------|
| L-01 | LolitaDatabase.kt | 696-708 | CODE_QUALITY | 双重检查锁定模式可简化 |
| L-02 | Converters.kt | 10 | CODE_QUALITY | Gson 实例未使用 companion object 复用 |
| L-03 | Item.kt | 88-92 | CODE_QUALITY | season/style 用 String 而非外键，仅靠应用层保证引用完整性 |
| L-04 | BrandRepository.kt | 235-239 | CODE_QUALITY | ensurePresetBrands 逐条读写约 400 次 DB 调用，非常慢 |
| L-05 | TaobaoOrderParser.kt | 66 | ERROR_HANDLING | 数量解析失败默认为 1，用户无感知 |
| L-06 | BootCompletedReceiver.kt | 26 | CODE_QUALITY | 每次开机都调 AppModule.init，可能多余 |
| L-07 | DailyOutfitReminderReceiver.kt | 30-34 | CODE_QUALITY | 通知渠道内联创建，与 NotificationChannelSetup 不统一 |
| L-08 | CalendarEventHelper.kt | 37 | CODE_QUALITY | 日历事件固定 1 小时，付款截止日无时间语义 |
| L-09 | ImageFileHelper.kt | 82-85 | BUG | URL 扩展名提取逻辑脆弱，可能生成错误扩展名 |
| L-10 | ImageFileHelper.kt | 93 | CODE_QUALITY | 伪造浏览器 User-Agent 绕过服务器限制 |
| L-11 | ImageFileHelper.kt | 20-40 | ERROR_HANDLING | copyToInternalStorage 未验证写入是否成功 |
| L-12 | AppModule.kt | 12 | CODE_QUALITY | @Volatile 与 lateinit var 组合语义有限 |
| L-13 | AppModule.kt | 51-58 | CODE_QUALITY | Repository 间循环依赖风险 |
| L-14 | LolitaApplication.kt | 15-17 | ERROR_HANDLING | 启动协程无 try-catch，异常静默丢失 |
| L-15 | ItemListScreen.kt | 594 | CODE_QUALITY | tabs 变量声明但未使用（死代码） |
| L-16 | ItemListScreen.kt | 479 | CODE_QUALITY | Gallery 视图 indexOf 做 O(n) 查找，整体 O(n^2) |
| L-17 | ItemListScreen.kt | 754 | ACCESSIBILITY | 取消按钮用 Modifier.clickable 而非 TextButton，无障碍服务无法识别 |
| L-18 | ItemDetailScreen.kt | 425, 740-743 | CODE_QUALITY | SimpleDateFormat 未缓存或使用非线程安全实例 |
| L-19 | Screen.kt | 149 | BUG | filterType 未 URI 编码，特殊字符可导致路由异常 |
| L-20 | AndroidManifest.xml | 4-8 | PERMISSION | WRITE_EXTERNAL_STORAGE 可能多余（图片存内部存储） |

---

## 六、按模块分类统计

| 模块 | CRITICAL | HIGH | MEDIUM | LOW | 合计 |
|------|----------|------|--------|-----|------|
| 数据库/Migration | 0 | 4 | 4 | 1 | 9 |
| Entity/Converter | 0 | 1 | 2 | 2 | 5 |
| Repository 层 | 2 | 9 | 10 | 1 | 22 |
| 备份恢复 | 3 | 0 | 6 | 0 | 9 |
| 通知/日历 | 1 | 0 | 4 | 3 | 8 |
| ViewModel 层 | 0 | 4 | 8 | 0 | 12 |
| UI/导航层 | 0 | 3 | 5 | 5 | 13 |
| 安全/配置 | 1 | 3 | 0 | 3 | 7 |
| 文件操作 | 0 | 0 | 2 | 2 | 4 |
| **合计** | **7** | **24** | **41** | **17** | **89** |

---

## 七、修复优先级建议

### P0 - 立即修复（数据安全/崩溃风险）
1. **C-03/C-04**: 调整删除顺序，先删 DB 再删文件；或先备份文件再删 DB，失败则恢复
2. **C-05**: deleteOutfitLog/deleteOutfitLogById 必须同时删除 CrossRef
3. **C-06**: DailyOutfitReminderReceiver 改用 goAsync() 模式
4. **C-07**: BackupRestoreScreen 根据文件类型分发到正确的导入方法
5. **C-08**: 更换强密码、启用 CI 环境变量或加密存储签名密钥

### P1 - 本迭代修复（数据一致性/用户体验）
1. **H-01~H-04**: 审查并修复所有 Room Migration 中的 SQL 转义和 JSON 生成逻辑
2. **H-05**: 统一 Item.colors 类型为 List<String>
3. **H-06**: Brand/Category 删除加入事务保护
4. **H-09**: Season 更新/删除对 CatalogEntry 使用 LIKE 匹配逗号分隔值
5. **H-11/H-12/H-13**: 统一付款相关操作的事务策略，先 DB 后副作用
6. **H-14/H-15/H-16**: 启用 HTTPS、添加 network_security_config.xml、禁止危险协议
7. **H-17**: ViewModel StateFlow 更新统一使用 `_uiState.update {}`
8. **H-21**: ItemEditScreen 顶栏返回也走 UnsavedChangesHandler

### P2 - 近期修复（健壮性/质量）
1. 所有 Repository 的图片/日历清理统一先 DB 后副作用 + try-catch
2. FilteredItemListViewModel 加入枚举/数字解析保护
3. LocationDetailViewModel 批量操作加事务
4. OutfitLog 编辑处理原记录为 null 的边界情况
5. BackupManager 导入前先备份当前数据以支持回滚

### P3 - 后续优化
1. SimpleDateFormat 统一替换为 java.time.format.DateTimeFormatter
2. 确保 AppModule 初始化幂等性
3. Gallery 视图性能优化
4. CSV 导出格式标准化
5. 通知渠道统一管理