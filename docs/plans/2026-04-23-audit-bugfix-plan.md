# 审计报告功能 Bug 修复实施计划

**日期**: 2026-04-23
**依据**: `docs/plans/2026-04-23-audit-bugfix-design.md`
**策略**: 按模块分组，模块内按优先级排序，每模块改完执行 `assembleDebug` 验证

---

## 批次 1：备份恢复模块（8 个 Bug）

### Task 1.1: C-01 importFromJson 导入前安全备份 + 事务保护

**文件**: `BackupManager.kt`

1. `importFromJson()` 开头（line 243 后），在 `clearAllTables` 前备份数据库文件到 `.bak` 和 images 目录到 `images_pre_import_backup`
2. `clearAllTables()` 已在 `database.withTransaction` 内（line 260-276），保持
3. catch 块（line 340）中增加从备份恢复逻辑：关闭 DB → 恢复 `.bak` 覆盖当前 → 重新打开
4. 成功完成后清理备份文件

### Task 1.2: C-02 恢复前清理旧日历事件

**文件**: `BackupManager.kt`

1. 在 `database.withTransaction` 块之前（line 260 前），查询当前所有 payments 的 `calendarEventId`
2. 逐个调用 `CalendarEventHelper.deleteEvent()` 删除，包裹 try-catch

### Task 1.3: C-07 ZIP/JSON 格式选择逻辑

**文件**: `BackupRestoreViewModel.confirmImport()` (line 100-120)

1. 分析：`importFromJson` 内部通过 `prepareBackup` 的 `isZipFile` 已统一处理 ZIP/JSON
2. 但方法名 `importFromJson` 有误导性，重命名为 `importBackup`
3. `BackupManager` 和 `BackupRestoreViewModel` 中所有调用点同步更新

### Task 1.4: M-22 缓存字段线程安全

**文件**: `BackupManager.kt` line 57-58

1. `cachedPreparedBackup` 和 `cachedBackupUri` 加 `@Volatile`
2. 读写操作包裹 `synchronized(this)` 块

### Task 1.5: M-23 图片引用丢失计数

**文件**: `BackupManager.kt` → `buildImageArchiveNames` (line 427-431)

1. 统计 `filter` 过滤掉的路径数量
2. `PreparedBackup` 新增 `missingImageCount: Int = 0`
3. `ImportSummary` 新增 `missingImageCount: Int = 0`
4. UI 成功消息附加"X 张图片文件缺失"提示

### Task 1.6: M-24 回滚时安全恢复图片目录

**文件**: `BackupManager.kt` → `rollbackActivatedImages` (line 664-669)

1. 不再先 `deleteRecursivelyQuietly(liveImagesDir)`
2. 改用已有的 `moveDirectory` 原子替换：`backupImagesDir → liveImagesDir`

### Task 1.7: M-25 JSON 迁移异常计数

**文件**: `BackupManager.kt` → `migrateJsonString` (line 756-770)

1. `migrateJsonString` 返回 `Pair<String, Int>`（json + errorCount）
2. catch 块返回 `Pair(json, 1)` 而非静默返回原 json
3. `PreparedBackup` 新增 `migrationErrorCount: Int = 0`
4. 汇总到 `ImportSummary.totalErrors`

### Task 1.8: M-34 日历重建失败递增 totalErrors

**文件**: `BackupManager.kt` line 289-339

1. line 312 处 `calendarFailCount = -1` 改为 `calendarFailCount += unpaidPayments.size`
2. line 334 处 `totalErrors = 0` 改为 `totalErrors = calendarFailCount + migrationErrorCount`

**验证**: `./gradlew.bat installDebug` → 手机上手动测试备份恢复流程

### Task 2.1: C-03 Item 删除顺序修正

**文件**: `ItemRepository.kt` line 54-75

1. `doDelete` lambda 中只保留 DB 操作（payment 清理 + `itemDao.deleteItem`）
2. 图片删除移到事务外，包裹 try-catch

### Task 2.2: C-04 Location 删除顺序修正

**文件**: `LocationRepository.kt` line 29-32

1. 先 `locationDao.deleteLocation(location)`，成功后再删图片
2. 图片删除加 try-catch

### Task 2.3: C-05 OutfitLog 删除清理 CrossRef

**文件**: `OutfitLogRepository.kt` line 34-47

1. `deleteOutfitLog` 和 `deleteOutfitLogById` 中增加 `deleteOutfitItemCrossRefsByLogId`
2. 整体包裹 `database.withTransaction`
3. 图片删除移到事务外
4. 需确认 `OutfitLogDao` 是否有 `deleteOutfitItemCrossRefsByLogId`，没有则新增

### Task 2.4: H-06 Brand/Category 删除 TOCTOU 竞态

**文件**: `BrandRepository.kt` line 20-26, `CategoryRepository.kt` line 20-26

1. 引入 `LolitaDatabase` 作为构造参数
2. 检查+删除包裹 `database.withTransaction`
3. 同步更新 `AppModule` 的构造调用

### Task 2.5: H-07 Coordinate 更新过期基线

**文件**: `CoordinateRepository.kt` line 47-70

1. 将 `oldCoordinate` 读取移入 `database.withTransaction` 块内部

### Task 2.6: H-08 Coordinate.updateCoordinate 事务保护

**文件**: `CoordinateRepository.kt` line 72-81

1. 整体包裹 `database.withTransaction`
2. `oldCoordinate` 读取在事务内

### Task 2.7: H-09 Season 对 CatalogEntry 逗号分隔值匹配

**文件**: `SeasonRepository.kt` line 21-57

1. 检查 `CatalogEntryDao.updateEntriesSeason` / `clearEntriesSeason` 的 SQL
2. 如果是 `WHERE season = :oldName` 精确匹配，改为应用层查询+逐条更新（与 Items 侧一致）

### Task 2.8: H-10 OutfitLog 删除图片泄漏（已在 Task 2.3 中处理）

### Task 2.9: H-11/H-12/H-13 Payment/Price 操作顺序修正

**H-11** `PaymentRepository.insertPayment`:
1. 先 `paymentDao.insertPayment`（DB），成功后再调度日历/闹钟（副作用）

**H-12** `PaymentRepository.updatePayment`:
1. 先创建新日历事件，成功后再删旧事件，暂存旧事件 ID

**H-13** `PriceRepository.deletePrice`:
1. 先 `priceDao.deletePrice`（DB），成功后再清日历/闹钟（副作用）

### Task 2.10: H-18 FilteredItemListViewModel 解析保护

**文件**: `FilteredItemListViewModel.kt` line 46, 48

1. `ItemPriority.valueOf(filterValue)` 包裹 try-catch，降级为 `MEDIUM`
2. `filterValue.toLong()` 包裹 try-catch，降级为 -1L 并返回空列表

### Task 2.11: M-09 CatalogEntry 删除图片清理保护

**文件**: `CatalogRepository.kt` line 26-34

1. 先 `catalogEntryDao.deleteCatalogEntry(entry)`，再清图片（顺序已正确，确认即可）

### Task 2.12: M-15 Location 删除前检查引用

**文件**: `LocationRepository.kt`

1. `deleteLocation` 中先 `itemDao.countItemsByLocation(location.id)`
2. 如果 > 0，抛 `IllegalStateException` 提示用户先移除引用

**验证**: `./gradlew.bat installDebug` → 手机上测试删除操作、支付流程

### Task 3.1: C-06 DailyOutfitReminderReceiver 改用 goAsync

**文件**: `DailyOutfitReminderReceiver.kt`

1. 重写 `onReceive`：`goAsync()` + `CoroutineScope(Dispatchers.IO).launch`
2. DB 查询和通知发送在协程内
3. `finally` 中 `pendingResult.finish()`
4. 通知渠道创建和通知发送逻辑提取到 `showReminderNotification` 私有方法

### Task 3.2: M-16 scheduleReminderForPayment SecurityException

**文件**: `PaymentRepository.kt` line 122-126

1. `scheduleReminder` 调用包裹 try-catch SecurityException

### Task 3.3: M-30 BootCompletedReceiver 超时处理

**文件**: `BootCompletedReceiver.kt` line 25

1. catch 块中增加 `Log.w` 记录超时
2. 简化方案：将关键信息存 SharedPreferences，MainActivity 启动时补调度

### Task 3.4: M-31 日历选择过滤只读日历

**文件**: `CalendarEventHelper.kt` line 75-106

1. projection 增加 `CALENDAR_ACCESS_LEVEL`
2. 查询条件排除只读日历
3. `it.getInt(1)` 改为 `it.getColumnIndex(IS_PRIMARY)` + 安全处理列不存在

### Task 3.5: M-33 Android 12+ 闹钟可靠性

**文件**: `DailyOutfitReminderScheduler.kt`

1. Android 12+ 使用 `setExactAndAllowWhileIdle` 替代 `setRepeating`
2. Receiver 的 onReceive 中重新调度下一次提醒
3. 检查 `canScheduleExactAlarms()` 权限

**验证**: `./gradlew.bat installDebug` → 手机上测试闹钟/日历提醒触发

### Task 4.1: H-01 Migration 10->11 JSON 生成修复

**文件**: `LolitaDatabase.kt` line 288

1. 修改 SQL 中 color 转 JSON 数组的逻辑
2. 对 color 值先转义特殊字符：`REPLACE(REPLACE(color, '\', '\\'), '"', '\"')`
3. 完整 SQL: `UPDATE items SET colors = '["' || REPLACE(REPLACE(color, '\', '\\'), '"', '\"') || '"]' WHERE color IS NOT NULL`

### Task 4.2: H-02 Migration 11->12 WHERE 条件修复

**文件**: `LolitaDatabase.kt` line 295

1. 将 `LIKE '%\%'` 改为 `GLOB '*\\*'`（GLOB 中 `\` 是转义字符）
2. 或改用应用层代码在 Migration 回调中逐行处理

### Task 4.3: H-03 Migration 14->15 图片 URL 转义修复

**文件**: `LolitaDatabase.kt` line 463-466

1. 调整 `image_url` -> `image_urls` 转换中的反斜杠处理顺序
2. 先处理 `\\` -> `\`，再处理 `\n`/`\t` 等
3. 或改用应用层代码逐行转换

### Task 4.4: H-04 Migration SQL 转义

**文件**: `LolitaDatabase.kt` line 100-107, 266

1. 所有中文预设数据拼入 SQL 前加 `replace("'", "''")` 转义
2. 对照 `DatabaseCallback` 中的正确转义逻辑，确保 Migration 中一致

### Task 4.5: H-05 Item.colors 类型统一

**文件**: `Item.kt`, `Converters.kt`, `LolitaDatabase.kt`

1. `Item.colors` 从 `String?` 改为 `List<String>`，默认值 `emptyList()`
2. 新增/复用 TypeConverter（`Converters.kt` 中已有 `fromStringList`/`toStringList`）
3. 新增数据库版本 + Migration：将现有 `colors` 列的 JSON 字符串转为标准格式
4. `BackupManager.migrateBackupData` 中的 colors 转换逻辑可移除（TypeConverter 统一处理）
5. 同步更新 `BackupData`/导出中引用 `item.colors` 的地方

**验证**: `./gradlew.bat installDebug` → 手机上从旧版 APK 升级测试 Migration

### Task 5.1: H-17 ItemEditViewModel 非原子 StateFlow 更新

**文件**: `ItemViewModel.kt` line 708-810

1. 所有 `_uiState.value = _uiState.value.copy(...)` 改为 `_uiState.update { it.copy(...) }`
2. 引入 `kotlinx.coroutines.flow.update`
3. `saveItem` 中 `isSaving` 标志也用原子更新
4. `isSaving = true` 在保存入口设置，防止双击重复创建

### Task 5.2: H-19 OutfitLog 编辑原记录已删除

**文件**: `OutfitLogViewModel.kt` line 215-228

1. `loadOutfitLog()` 返回 null 时，UI state 中标记 `isDeleted = true`
2. `save()` 方法检查 `isDeleted`，为 true 时提示"原记录已被删除"并返回
3. `OutfitLogUiState` 新增 `isDeleted: Boolean = false` 字段

### Task 5.3: H-20 LocationDetailViewModel 批量更新事务

**文件**: `LocationDetailViewModel.kt` line 122-132

1. `confirmPickerSelection()` 包裹 `database.withTransaction`
2. 需引入 `LolitaDatabase` 依赖（通过构造参数或 AppModule）

### Task 5.4: M-35 pendingImageDeletions 并发安全

**文件**: `ItemViewModel.kt` line 588

1. `pendingImageDeletions` 从 `MutableList<String>` 改为 `MutableStateFlow<Set<String>>`
2. 所有读写操作通过 `_pendingImageDeletions.update {}` 原子更新

### Task 5.5: M-36 supportingDataLoaded 线程安全

**文件**: `ItemViewModel.kt` line 676-697

1. `supportingDataLoaded` 改为 `AtomicBoolean`

### Task 5.6: M-38 deleteOutfitLog 异常提示

**文件**: `OutfitLogViewModel.kt`

1. catch 块中更新 UI state 显示错误信息

### Task 5.7: M-40 OutfitLog save() 原子 isSaving

**文件**: `OutfitLogViewModel.kt` line 269-293

1. `_uiState.value = _uiState.value.copy(isSaving = ...)` 改为 `_uiState.update {}`

### Task 5.8: M-41 QuickOutfitLogViewModel try-catch

**文件**: `QuickOutfitLogViewModel.kt` line 46-57

1. `getTodayOutfitLog()` 调用包裹 try-catch
2. 失败时回退到新建模式并 `Log.e` 记录

### Task 5.9: M-43 loadRecommendations 防重入

**文件**: `RecommendationViewModel.kt` line 35

1. 新增 `_isLoading = AtomicBoolean(false)`
2. `loadRecommendations` 入口检查，已在加载则直接返回
3. 加载完成后重置标志

**验证**: `./gradlew.bat installDebug` → 手机上测试编辑保存、穿搭记录、推荐功能

### Task 6.1: H-21 ItemEditScreen 顶栏返回绕过未保存检查

**文件**: `ItemEditScreen.kt` line 57-59

1. 顶栏返回箭头的 `onClick` 从直接 `onBack()` 改为检查 `hasUnsavedChanges`
2. 如有未保存变更，显示确认对话框；无变更则直接返回
3. 参考现有 `UnsavedChangesHandler` 的逻辑

### Task 6.2: H-22 isNavigating 永远为 true

**文件**: `LolitaNavHost.kt` line 210-211

1. 将 `isNavigating` 判断从 `navBackStackEntryForOverlay != null` 改为基于过渡状态
2. 使用 `navController.currentBackStackEntryFlow` 收集过渡进度
3. 或使用 Compose Navigation 的 `AnimatedNavHost` 提供的过渡回调

### Task 6.3: H-23 淘宝导入后跳转详情页

**文件**: `LolitaNavHost.kt` line 596-601

1. `onNavigateToDetail` 回调中将 `_ -> navController.popBackStack()` 改为：
   `itemId -> navController.navigate(Screen.ItemDetail.createRoute(itemId))`
2. 需确认 `TaobaoImportScreen` 传出的 `itemId` 是否正确

### Task 6.4: M-39 loadOutfitLogDetail 非原子赋值

**文件**: `OutfitLogViewModel.kt` line 127-129 / line 152-153

1. 直接赋值改用 `_uiState.update {}`

### Task 6.5: M-46 ItemDetailScreen 每次 resume 全量重载

**文件**: `ItemDetailScreen.kt` line 59-63

1. 去掉 `repeatOnLifecycle(RESUMED)` + 手动 `viewModel.loadItem()`
2. 改用 `collectAsStateWithLifecycle` + Flow 模式，只在首次创建时加载
3. 或改用 `LaunchedEffect(Unit)` 只触发一次

### Task 6.6: M-47 SizeChartImage 本地路径包裹

**文件**: `ItemEditScreen.kt` line 747

1. 本地图片路径用 `Uri.fromFile(File(path))` 包裹后传给 Coil `AsyncImage`

### Task 6.7: L-19 filterType URI 编码

**文件**: `Screen.kt` line 149

1. `FilteredItemList.createRoute` 中 `filterType` 参数使用 `Uri.encode()` 编码
2. 接收端用 `Uri.decode()` 解码（当前已对 filterValue 和 title 编码，只需补充 filterType）

**验证**: `./gradlew.bat installDebug` → 手机上测试导航、淘宝导入、编辑返回

每个批次完成后：
- [ ] `./gradlew.bat installDebug` 编译并安装到手机
- [ ] 手机上手动验证本批次修复的功能点
- [ ] git commit 前确认无无关改动
- [ ] 关键改动点手动复查（删除顺序、事务包裹、原子更新）

全批次完成后：
- [ ] `./gradlew.bat installDebug` 最终编译安装
- [ ] 检查是否有遗漏的 `import` 或编译警告
- [ ] 完整回归测试：备份恢复、删除、支付、导航
- [ ] 更新 `versionCode` + `versionName`
