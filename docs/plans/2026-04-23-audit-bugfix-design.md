# 审计报告功能 Bug 修复设计

**日期**: 2026-04-23
**范围**: 审计报告 87 个问题中，48 个直接影响用户可见功能的 Bug
**策略**: 按模块分组、模块内按优先级排序

---

## 总览

| 模块 | CRITICAL | HIGH | MEDIUM | LOW | 合计 |
|------|----------|------|--------|-----|------|
| 备份恢复 | 3 | 0 | 5 | 0 | 8 |
| Repository 层 | 3 | 8 | 3 | 0 | 14 |
| 数据库/Migration | 0 | 5 | 0 | 0 | 5 |
| 通知/日历 | 1 | 0 | 4 | 0 | 5 |
| ViewModel 层 | 0 | 3 | 6 | 0 | 9 |
| UI/导航层 | 0 | 3 | 3 | 1 | 7 |
| **合计** | **7** | **19** | **21** | **1** | **48** |

---

## 模块 1：备份恢复（8 个 Bug）

| 编号 | 严重度 | 问题 | 修复方案 |
|------|--------|------|----------|
| C-01 | CRITICAL | clearAllTables 后导入失败导致数据全丢 | 导入前先备份数据库文件(.bak)；clearAllTables 与后续插入放同一 withTransaction；失败则从备份恢复 |
| C-02 | CRITICAL | 恢复后日历事件全部泄漏 | 导入前先查询并删除应用创建的日历事件，再执行 clearAllTables + 导入 |
| C-07 | CRITICAL | ZIP/JSON 格式选择逻辑缺失 | confirmImport() 检测文件扩展名/MIME，ZIP 走 importFromZip，JSON 走 importFromJson |
| M-22 | MEDIUM | 缓存字段无线程安全保护 | cachedItems/cachedEntries 改用 @Volatile + AtomicReference 或加锁 |
| M-23 | MEDIUM | 不存在的图片引用备份时静默丢弃 | 记录丢失图片数量，导入后警告通知用户 |
| M-24 | MEDIUM | 回滚时先删目录再恢复，中间失败全丢 | 回滚时先恢复到临时目录，成功后再替换 |
| M-25 | MEDIUM | JSON 迁移异常被吞 | 捕获异常后记录到 totalErrors 并递增错误计数 |
| M-34 | MEDIUM | 日历重建失败但 totalErrors 仍为 0 | 日历创建失败时递增 totalErrors |

---

## 模块 2：Repository 层（14 个 Bug）

| 编号 | 严重度 | 问题 | 修复方案 |
|------|--------|------|----------|
| C-03 | CRITICAL | Item 删除先删文件再删 DB | 调整顺序：先 DB 事务内删除，成功后再删文件 + try-catch |
| C-04 | CRITICAL | Location 删除同上模式 | 同 C-03 策略 |
| C-05 | CRITICAL | OutfitLog 删除不清理 CrossRef | 同时调用 refDao.deleteByOutfitLogId()，包裹 withTransaction |
| H-06 | HIGH | Brand/Category 删除 TOCTOU 竞态 | 检查+删除包裹 withTransaction；或用单条 SQL DELETE WHERE count=0 |
| H-07 | HIGH | Coordinate 更新使用过期基线 | 将 oldCoordinate 读取移入事务内部 |
| H-08 | HIGH | Coordinate.updateCoordinate 无事务 | 包裹 withTransaction |
| H-09 | HIGH | Season 对 CatalogEntry 逗号分隔值匹配错误 | 改用 LIKE 模式匹配或应用层逐条更新 |
| H-10 | HIGH | OutfitLog 删除后图片泄漏 | 事务内删 DB，事务成功后再清图片 + try-catch |
| H-11 | HIGH | Payment 插入非原子 | 先 DB 插入，成功后再调度日历/闹钟；失败回滚 DB |
| H-12 | HIGH | Payment 更新先删旧日历再建新 | 先创建新日历事件，成功后再删旧事件 |
| H-13 | HIGH | Price 删除先清日历/闹钟再删 DB | 调整顺序：先 DB 删除，成功后再清日历/闹钟 |
| H-18 | HIGH | FilteredItemListViewModel 枚举/数字解析无保护 | valueOf() 和 toLong() 加 try-catch 降级为默认值 |
| M-09 | MEDIUM | CatalogEntry 删除图片崩溃则文件泄漏 | 图片清理放 DB 删除之后 + try-catch + 日志 |
| M-15 | MEDIUM | Location 删除未检查 Item 引用 | 删除前查引用数，有引用时提示用户或级联处理 |

**统一范式**：所有删除操作遵循"先 DB 后副作用"原则——先在事务内完成数据库操作，成功后再清理外部资源（文件、日历、闹钟），外部清理加 try-catch 防止级联失败。

---

## 模块 3：数据库 / Migration（5 个 Bug）

| 编号 | 严重度 | 问题 | 修复方案 |
|------|--------|------|----------|
| H-01 | HIGH | Migration 10→11 生成错误 JSON | SQL 中对 color 值先转义双引号和反斜杠再拼接；或改用应用层逐行转换 |
| H-02 | HIGH | Migration 11→12 修复逻辑 WHERE 条件错误 | 用 GLOB 替代 LIKE 匹配反斜杠；或改用应用层处理 |
| H-03 | HIGH | Migration 14→15 图片 URL 双重转义 | 调整反斜杠处理顺序：先 \\→\ 再处理其他转义；或逐行应用层转换 |
| H-04 | HIGH | Migration 中 SQL 注入风险 | 所有中文预设字符串拼入 SQL 前加 replace("'","''") 转义 |
| H-05 | HIGH | Item.colors 类型不一致 | 将 Item.colors 改为 List\<String\> + TypeConverter；新增 Migration 将现有数据标准化 |

**注意**：Migration 修改只影响从旧版直接升级的用户，已升级用户不受影响。H-05 需要新增数据库版本和 Migration。

---

## 模块 4：通知 / 日历（5 个 Bug）

| 编号 | 严重度 | 问题 | 修复方案 |
|------|--------|------|----------|
| C-06 | CRITICAL | DailyOutfitReminderReceiver 使用 runBlocking 阻塞主线程 | 改用 goAsync() + 协程，与 BootCompletedReceiver 一致 |
| M-16 | MEDIUM | scheduleReminderForPayment 无 SecurityException 处理 | 加 try-catch 捕获，记录日志并跳过 |
| M-30 | MEDIUM | BootCompletedReceiver withTimeout 超时后提醒不调度 | 超时后将待调度信息存临时存储，后续可重试 |
| M-31 | MEDIUM | 日历选择可能选中只读日历 | 查询增加 CALENDAR_ACCESS_LEVEL 过滤 |
| M-33 | MEDIUM | setRepeating 在 Android 12+ 不可靠 | 12+ 使用 setExactAndAllowWhileIdle + 手动重新调度 |

---

## 模块 5：ViewModel 层（9 个 Bug）

| 编号 | 严重度 | 问题 | 修复方案 |
|------|--------|------|----------|
| H-17 | HIGH | ItemEditViewModel 非原子 StateFlow 更新 | 全部改用 _uiState.update {}；isSaving 用原子更新 |
| H-19 | HIGH | OutfitLog 编辑原记录已删除，保存产生错误数据 | loadOutfitLog 返回 null 时标记 isDeleted，保存时检查并提示 |
| H-20 | HIGH | LocationDetailViewModel 批量更新无事务 | 包裹 withTransaction |
| M-35 | MEDIUM | pendingImageDeletions MutableList 并发不安全 | 改用 MutableStateFlow\<Set\<String\>\> |
| M-36 | MEDIUM | supportingDataLoaded 非线程安全 | 改用 AtomicBoolean |
| M-38 | MEDIUM | deleteOutfitLog 异常静默吞掉 | catch 中更新 UI 状态显示错误 |
| M-40 | MEDIUM | OutfitLog save() 非原子 isSaving | 改用 _uiState.update {} |
| M-41 | MEDIUM | QuickOutfitLogViewModel 无 try-catch | 加 try-catch，失败回退新建模式 + 日志 |
| M-43 | MEDIUM | loadRecommendations 未防重入 | 加 isLoading 标志检查 |

**统一范式**：所有 StateFlow 更新统一使用 `_uiState.update {}` 原子操作，避免 `.value = .value.copy()` 模式。

---

## 模块 6：UI / 导航层（7 个 Bug）

| 编号 | 严重度 | 问题 | 修复方案 |
|------|--------|------|----------|
| H-21 | HIGH | ItemEditScreen 顶栏返回绕过未保存检查 | 返回箭头改用与 UnsavedChangesHandler 相同逻辑 |
| H-22 | HIGH | isNavigating 永远为 true | 改用 TransitionProgress 或 BackStackEntryFlow 判断过渡状态 |
| H-23 | HIGH | 淘宝导入后不跳转详情页 | onNavigateToDetail 用 itemId 调用 navigate 替代 popBackStack |
| M-39 | MEDIUM | loadOutfitLogDetail 非原子赋值 | 改用 _uiState.update {} |
| M-46 | MEDIUM | ItemDetailScreen 每次 resume 全量重载 | 改用 collectAsStateWithLifecycle + Flow，去掉 repeatOnLifecycle |
| M-47 | MEDIUM | SizeChartImage 本地路径未用 File() 包裹 | 用 File(path).toUri() 或 Uri.fromFile() 包裹 |
| L-19 | LOW | filterType 未 URI 编码 | 使用 Uri.encode() 编码后拼入 route |

---

## 修复执行顺序

1. **备份恢复** — 3 个 CRITICAL，影响最广
2. **Repository 层** — 3 个 CRITICAL + 8 个 HIGH，数据安全核心
3. **通知/日历** — 1 个 CRITICAL（ANR），独立性强
4. **数据库/Migration** — 5 个 HIGH，需要仔细测试升级路径
5. **ViewModel 层** — 3 个 HIGH，状态管理核心
6. **UI/导航层** — 3 个 HIGH，用户体验相关

每个模块修完后执行 `assembleDebug` 验证编译通过。
