# 代码功能审查实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to execute this plan with parallel agents.

**Goal:** 对 Lolita 时尚管理 App 全部功能模块进行完整性审查，产出功能清单 + 问题列表。

**Architecture:** 6 个并行 Explore agent 各负责一个业务领域，端到端阅读数据层→VM→UI 代码，按标准化检查项审查。最终由主 agent 汇总为一份报告。

**Tech Stack:** Kotlin, Jetpack Compose, Room, MVVM

---

## Task 1: 审查领域 1 — 服饰管理（并行）

**Files to read:**
- Entity: `app/src/main/java/com/lolita/app/data/local/entity/Item.kt`, `Brand.kt`, `Category.kt`, `Style.kt`, `Season.kt`, `Location.kt`, `Source.kt`, `CategoryGroup.kt`
- DAO: `app/src/main/java/com/lolita/app/data/local/dao/ItemDao.kt`, `BrandDao.kt`, `CategoryDao.kt`, `StyleDao.kt`, `SeasonDao.kt`, `LocationDao.kt`, `SourceDao.kt`
- Repo: `app/src/main/java/com/lolita/app/data/repository/ItemRepository.kt`, `BrandRepository.kt`, `CategoryRepository.kt`, `StyleRepository.kt`, `SeasonRepository.kt`, `LocationRepository.kt`, `SourceRepository.kt`, `RecommendationRepository.kt`
- VM: `app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt`, `FilteredItemListViewModel.kt`, `LocationDetailViewModel.kt`, `RecommendationViewModel.kt`
- Screen: `app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt`, `ItemDetailScreen.kt`, `ItemEditScreen.kt`, `FilteredItemListScreen.kt`, `WishlistScreen.kt`, `RecommendationScreen.kt`, `LocationDetailScreen.kt`, `LocationListContent.kt`

**检查项：**
1. Item CRUD：创建、读取、更新、删除是否完整，删除时 FK RESTRICT 是否有用户提示
2. 筛选/搜索：状态、分类组、季节、风格、颜色、品牌、文本搜索是否全部可用
3. 状态流转：ItemStatus 各状态间转换是否合理（特别是 PENDING_BALANCE）
4. 图片管理：ImageFileHelper 调用是否正确，删除 Item 时图片文件是否清理
5. Brand/Category/Style/Season/Location/Source 的 CRUD 是否完整
6. FK 约束：Item→Brand(RESTRICT), Item→Category(RESTRICT), Item→Coordinate(RESTRICT) 是否正确处理
7. Wishlist 筛选逻辑是否正确
8. Recommendation 推荐算法数据流是否完整
9. LocationDetail 物品关联/解除是否正确
10. 各屏幕是否使用 SkinClickable, LolitaCard, GradientTopAppBar
11. 列表空状态处理
12. 编辑屏幕 UnsavedChangesHandler
13. DAO 中未被调用的方法

**产出格式：** 功能清单表格 + 问题列表（含文件:行号 + 严重程度）

---

## Task 2: 审查领域 2 — 坐标/穿搭（并行）

**Files to read:**
- Entity: `app/src/main/java/com/lolita/app/data/local/entity/Coordinate.kt`, `OutfitLog.kt`, `OutfitItemCrossRef.kt`
- DAO: `app/src/main/java/com/lolita/app/data/local/dao/CoordinateDao.kt`, `OutfitLogDao.kt`
- Repo: `app/src/main/java/com/lolita/app/data/repository/CoordinateRepository.kt`, `OutfitLogRepository.kt`
- VM: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateViewModel.kt`, `app/src/main/java/com/lolita/app/ui/screen/outfit/OutfitLogViewModel.kt`, `QuickOutfitLogViewModel.kt`
- Screen: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateListScreen.kt`, `CoordinateDetailScreen.kt`, `CoordinateEditScreen.kt`, `app/src/main/java/com/lolita/app/ui/screen/outfit/OutfitLogListScreen.kt`, `OutfitLogDetailScreen.kt`, `OutfitLogEditScreen.kt`, `QuickOutfitLogScreen.kt`
- Notification: `app/src/main/java/com/lolita/app/data/notification/DailyOutfitReminderScheduler.kt`, `DailyOutfitReminderReceiver.kt`
- Widget: `app/src/main/java/com/lolita/app/ui/widget/OutfitWidget.kt`, `OutfitWidgetReceiver.kt`

**检查项：**
1. Coordinate CRUD 完整性，imageUrl 字段处理
2. 物品关联到坐标：添加/移除物品是否正确操作 OutfitItemCrossRef 或 Item.coordinateId
3. OutfitLog CRUD 完整性，imageUrls (List<String>) 序列化
4. OutfitLog 多对多关系：OutfitItemCrossRef 的增删是否正确
5. QuickOutfitLog 快速记录流程是否完整
6. 日期选择和管理
7. 图片选择/显示/删除流程
8. DailyOutfitReminder 调度和接收是否正确
9. OutfitWidget 数据加载和刷新
10. 各屏幕皮肤组件使用
11. 列表空状态、UnsavedChangesHandler
12. DAO/Repo 未调用方法

**产出格式：** 功能清单表格 + 问题列表（含文件:行号 + 严重程度）

---

## Task 3: 审查领域 3 — 价格/付款（并行）

**Files to read:**
- Entity: `app/src/main/java/com/lolita/app/data/local/entity/Price.kt`, `Payment.kt`, `PaymentWithItemInfo.kt`
- DAO: `app/src/main/java/com/lolita/app/data/local/dao/PriceDao.kt`, `PaymentDao.kt`
- Repo: `app/src/main/java/com/lolita/app/data/repository/PriceRepository.kt`, `PaymentRepository.kt`
- VM: `app/src/main/java/com/lolita/app/ui/screen/price/PriceViewModel.kt`
- Screen: `app/src/main/java/com/lolita/app/ui/screen/price/PriceManageScreen.kt`, `PriceEditScreen.kt`, `PaymentManageScreen.kt`, `PaymentEditScreen.kt`, `app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt`
- Notification: `app/src/main/java/com/lolita/app/data/notification/PaymentReminderScheduler.kt`, `PaymentReminderReceiver.kt`, `CalendarEventHelper.kt`

**检查项：**
1. Price CRUD：FULL 和 DEPOSIT_BALANCE 两种模型是否都正确处理
2. Payment CRUD：付款状态流转（未付→已付）
3. 定金/尾款模型：创建 DEPOSIT_BALANCE 类型 Price 时是否自动创建两条 Payment
4. Payment 日期管理：dueDate vs paidDate 使用是否一致
5. PaymentCalendar：按 dueDate 范围查询、快速标记已付功能
6. 日历事件：CalendarEventHelper 创建/更新/删除日历事件
7. 提醒调度：PaymentReminderScheduler exact alarm 设置，Android 12+ 兼容
8. BootCompletedReceiver 重新调度提醒
9. Price→Item(CASCADE), Payment→Price(CASCADE) 级联删除是否正确
10. 删除 Payment 时清理 calendarEventId 对应的日历事件
11. 各屏幕皮肤组件使用
12. DAO/Repo 未调用方法

**产出格式：** 功能清单表格 + 问题列表（含文件:行号 + 严重程度）

---

## Task 4: 审查领域 4 — 统计分析（并行）

**Files to read:**
- Data: `app/src/main/java/com/lolita/app/data/local/entity/StatsData.kt`
- Screen: `app/src/main/java/com/lolita/app/ui/screen/stats/StatsScreen.kt`, `StatsPageScreen.kt`, `SpendingTrendScreen.kt`, `SpendingDistributionScreen.kt`, `WishlistAnalysisScreen.kt`
- Chart: `app/src/main/java/com/lolita/app/ui/component/chart/ChartColors.kt`, `LineChart.kt`, `PieChart.kt`, `StatsProgressBar.kt`
- 相关 DAO 查询（ItemDao, PriceDao, PaymentDao 中的统计查询）

**检查项：**
1. StatsData 数据模型是否覆盖所有统计维度
2. 各统计页面数据源：是否从正确的 DAO 查询获取数据
3. 消费趋势：是否使用 dueDate（而非 paidDate）
4. 消费分布：分类/品牌/状态维度是否完整
5. 愿望单分析：数据聚合逻辑
6. 图表组件：LineChart, PieChart 数据绑定是否正确
7. 钻取导航：从统计图表点击跳转到 FilteredItemList 参数传递
8. Tab 切换：HorizontalPager + TabRow + SkinTabIndicator 模式
9. 空数据状态处理
10. 皮肤组件使用

**产出格式：** 功能清单表格 + 问题列表（含文件:行号 + 严重程度）

---

## Task 5: 审查领域 5 — 设置/备份（并行）

**Files to read:**
- Settings: `app/src/main/java/com/lolita/app/ui/screen/settings/SettingsScreen.kt`, `SettingsViewModel.kt`
- Preset: `BrandManageScreen.kt`, `BrandManageViewModel.kt`, `CategoryManageScreen.kt`, `CategoryManageViewModel.kt`, `StyleManageScreen.kt`, `StyleManageViewModel.kt`, `SeasonManageScreen.kt`, `SeasonManageViewModel.kt`, `LocationManageScreen.kt`, `LocationManageViewModel.kt`, `SourceManageScreen.kt`, `AttributeManageScreen.kt`（均在 `app/src/main/java/com/lolita/app/ui/screen/settings/` 下）
- Backup: `app/src/main/java/com/lolita/app/data/file/BackupManager.kt`
- BackupUI: `app/src/main/java/com/lolita/app/ui/screen/settings/BackupRestoreScreen.kt`
- Import: `app/src/main/java/com/lolita/app/data/file/TaobaoOrderParser.kt`, `app/src/main/java/com/lolita/app/data/model/TaobaoOrder.kt`, `app/src/main/java/com/lolita/app/ui/screen/import/TaobaoImportScreen.kt`, `TaobaoImportViewModel.kt`, `ImportDetailScreen.kt`
- Theme: `app/src/main/java/com/lolita/app/ui/screen/settings/ThemeSelectScreen.kt`
- Prefs: `app/src/main/java/com/lolita/app/data/preferences/AppPreferences.kt`

**检查项：**
1. 各预设管理（Brand/Category/Style/Season/Location/Source）CRUD 完整性
2. 预设重命名时级联更新 Item 记录（Season, Style 是字符串存储）
3. 预设删除：是否有 FK 约束保护或用户提示
4. BackupManager JSON 导出：是否包含所有 16 个 Entity
5. BackupManager JSON 导入：是否处理缺失字段（向后兼容）
6. BackupManager CSV 导出：列是否完整
7. 导入预览功能是否正常
8. TaobaoOrderParser xlsx 解析：字段映射是否正确
9. 淘宝导入流程：解析→预览→确认→写入数据库
10. ThemeSelect 皮肤切换是否持久化到 AppPreferences
11. Settings 页面所有入口导航是否正确
12. 各屏幕皮肤组件使用

**产出格式：** 功能清单表格 + 问题列表（含文件:行号 + 严重程度）

---

## Task 6: 审查领域 6 — 基础设施（并行）

**Files to read:**
- Nav: `app/src/main/java/com/lolita/app/ui/navigation/Screen.kt`, `LolitaNavHost.kt`
- App: `app/src/main/java/com/lolita/app/LolitaApplication.kt`, `app/src/main/java/com/lolita/app/di/AppModule.kt`
- DB: `app/src/main/java/com/lolita/app/data/local/LolitaDatabase.kt`, `app/src/main/java/com/lolita/app/data/local/converters/Converters.kt`
- Enums: `app/src/main/java/com/lolita/app/data/local/entity/Enums.kt`
- Image: `app/src/main/java/com/lolita/app/data/file/ImageFileHelper.kt`
- Notification: `app/src/main/java/com/lolita/app/data/notification/NotificationChannelSetup.kt`, `BootCompletedReceiver.kt`
- Common: `app/src/main/java/com/lolita/app/ui/screen/common/` 下全部 8 个文件
- Activity: `app/src/main/java/com/lolita/app/ui/MainActivity.kt`
- Skin icons: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/` 下全部 8 个文件
- Skin animation: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/` 下全部 15 个文件
- Skin particles: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/` 下全部 8 个文件
- Skin component: `app/src/main/java/com/lolita/app/ui/theme/skin/component/` 下 2 个文件
- Theme: `app/src/main/java/com/lolita/app/ui/theme/` 下 6 个文件

**检查项：**
1. Screen 路由：所有 28+ 路由是否都在 LolitaNavHost 中注册
2. 导航参数：typed arguments 传递是否正确
3. AppModule：所有 Repository 是否注册为 lazy singleton
4. LolitaDatabase：version 6，所有 Entity 是否在 @Database 注解中，所有 DAO 是否暴露
5. Migration 链：v1→v2→...→v6 是否完整
6. Converters：是否覆盖所有 enum 和 List<String>
7. LolitaApplication.onCreate：初始化顺序、历史数据刷新
8. IconKey enum：是否有 IconKey 定义了但没有实现的图标
9. 4 个皮肤 IconProvider：是否都实现了 BaseSkinIconProvider 的所有方法
10. 4 个皮肤 AnimationProvider：配置是否完整
11. Common 组件：GradientTopAppBar, LolitaCard 等是否被正确使用
12. BootCompletedReceiver：重启后提醒重新调度

**产出格式：** 功能清单表格 + 问题列表（含文件:行号 + 严重程度）

---

## Task 7: 汇总审查报告（依赖 Task 1-6 全部完成）

**Step 1:** 收集 6 个领域的审查结果

**Step 2:** 汇总为统一报告，保存到 `docs/plans/2026-02-23-code-review-report.md`，格式：
- 总览（审查日期、覆盖范围、总体结论）
- 各领域功能清单表格
- 各领域问题列表
- 问题汇总（按严重程度分级：🔴 阻塞 / 🟡 重要 / 🔵 建议）

**Step 3:** Commit 报告
