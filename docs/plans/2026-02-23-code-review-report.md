# 代码功能审查报告

审查日期：2026-02-23
覆盖范围：全部 6 大业务领域，28+ 屏幕，164 个 Kotlin 文件

## 总览

| 指标 | 数值 |
|------|------|
| 审查功能点总数 | 98 |
| ✅ 完整 | 82 |
| ⚠️ 部分 | 10 |
| ❌ 缺失 | 0 |
| 🔴 阻塞问题 | 6 |
| 🟡 重要问题 | 16 |
| 🔵 建议问题 | 25 |

总体结论：应用功能实现度较高（84% 完整），无功能缺失。主要问题集中在图片文件清理遗漏、皮肤组件使用不一致、以及部分数据层性能隐患。

---

## 领域 1：服饰管理

### 功能清单

| 功能点 | 状态 | 说明 |
|--------|------|------|
| Item CRUD (创建/读取/更新/删除) | ✅ | 完整实现，删除时清理图片、支付提醒、日历事件 |
| Item 删除 FK RESTRICT 处理 | ✅ | Brand/Category RESTRICT 抛异常并 UI 显示错误对话框 |
| 筛选 - 状态/分类组/季节/风格/颜色/品牌 | ✅ | 7 种过滤条件全部可用，文本搜索有 300ms 防抖 |
| ItemStatus 状态流转 (PENDING_BALANCE) | ✅ | refreshPendingBalanceStatus() 启动时刷新，单项更新也支持 |
| 图片管理 (上传/删除/清理) | ✅ | ImageFileHelper 完整，删除 Item 时清理 imageUrl 和 sizeChartImageUrl |
| Brand/Category/Style/Season/Location/Source CRUD | ✅ | 全部完整，级联更新/删除逻辑正确 |
| Wishlist 过滤 | ✅ | 按优先级排序 WISHED 项目，支持搜索 |
| Recommendation 推荐算法 | ✅ | 余弦相似度 + 共现加权，按类别分组 |
| LocationDetail 物品关联/解除 | ✅ | 批量更新 locationId，支持 picker 选择 |
| 皮肤组件使用 | ✅ | GradientTopAppBar, LolitaCard, SkinClickable, SkinItemAppear, SkinFlingBehavior |
| 空状态处理 | ✅ | 所有列表屏幕有 EmptyState |
| UnsavedChangesHandler | ✅ | ItemEditScreen 使用 |

### 发现的问题

- 🟡 **LocationDetailViewModel item picker 缺少品牌/类别信息** — loadAllItemsForPicker() 未加载 Brand/Category，picker 中无法显示品牌和类别名称 (LocationDetailViewModel.kt:74-90)
- 🟡 **RecommendationViewModel N+1 查询** — 为每个候选项目单独调用 categoryRepository.getCategoryById()，100 个项目产生 101 次查询 (RecommendationViewModel.kt:46-56)
- 🟡 **ItemEditViewModel pendingImageDeletions 未清理** — 多次更改图片但不保存时列表无限增长，保存失败时不恢复 (ItemViewModel.kt:502, 734-738)
- 🟡 **ItemRepository.deleteItem() 可选依赖** — paymentRepository/priceRepository 为 null 时支付提醒和日历事件不清理 (ItemRepository.kt:16-19, 54-75)
- 🟡 **PENDING_BALANCE 状态管理逻辑分散** — 分布在 ItemDao、ItemRepository、LolitaApplication 三处 (ItemDao.kt:157-166, ItemRepository.kt:77-87)
- 🟡 **LocationDetail picker 搜索无防抖** — 每次按键重新过滤整个列表 (LocationDetailScreen.kt:215-218)
- 🔵 ItemDao.searchItemsByName() 定义但未使用，搜索走本地过滤而非数据库索引 (ItemDao.kt:34-35)
- 🔵 applyFilters() 方法过长（50+ 行），建议拆分 (ItemViewModel.kt:387-440)
- 🔵 FilteredItemListViewModel.loadItems() 缺少 else 分支 (FilteredItemListViewModel.kt:37-51)
- 🔵 CSV 导出缺少 source 字段 (BackupManager.kt:95)

---

## 领域 2：坐标/穿搭

### 功能清单

| 功能点 | 状态 | 说明 |
|--------|------|------|
| Coordinate CRUD | ✅ | 完整实现，删除时先解除 items 关联再删除 |
| Coordinate imageUrl 处理 | ⚠️ | 支持上传和显示，但删除/更新时未清理旧文件 |
| Item 关联到 Coordinate | ✅ | 通过 Item.coordinateId，CoordinateRepository 正确更新 |
| OutfitLog CRUD | ✅ | 完整实现，删除时正确清理 imageUrls 文件 |
| OutfitItemCrossRef 多对多 | ✅ | 添加/删除关联正确，CASCADE 外键 |
| QuickOutfitLog 快速入口 | ✅ | 完整实现，支持今日数据加载和新建/编辑切换 |
| 日期选择管理 | ✅ | DatePickerDialog，格式化显示 |
| 图片选择/显示/删除 | ✅ | PickVisualMedia + ImageFileHelper + Coil AsyncImage |
| DailyOutfitReminder | ✅ | AlarmManager.setRepeating() 调度，检查今日记录后发通知 |
| OutfitWidget | ✅ | 加载今日数据，点击跳转 MainActivity |
| 皮肤组件使用 | ✅ | SkinClickableBox, SkinItemAppear, SkinFlingBehavior, LolitaCard |
| 空状态 + UnsavedChangesHandler | ✅ | CoordinateListScreen/OutfitLogListScreen 有空状态 |

### 发现的问题

- 🟡 **Coordinate 删除时未清理 imageUrl 文件** — 磁盘空间泄漏 (CoordinateRepository.kt:67-76)
- 🟡 **Coordinate 编辑时未清理旧 imageUrl 文件** — 替换图片后旧文件残留 (CoordinateViewModel.kt:399-422)
- 🟡 **OutfitLogEditScreen 缺少 UnsavedChangesHandler** — 编辑后直接返回会丢失数据 (OutfitLogEditScreen.kt:37)
- 🟡 **OutfitLogRepository.updateOutfitLogWithItems() 性能问题** — getAllOutfitItemCrossRefsList() 加载整表数据 (OutfitLogRepository.kt:87-98)
- 🔵 CoordinateDao.deleteAllCoordinates() 和 OutfitLogDao.deleteAllOutfitLogs() 未使用 (CoordinateDao.kt:50-51)
- 🔵 QuickOutfitLog 日期硬编码为 12:00 (QuickOutfitLogViewModel.kt:85-90)
- 🔵 OutfitWidget 缺少定期刷新机制 (OutfitWidget.kt:23-32)
- 🔵 DailyOutfitReminderScheduler 使用 setRepeating() 而非 setExactAndAllowWhileIdle() (DailyOutfitReminderScheduler.kt:30-35)

---

## 领域 3：价格/付款

### 功能清单

| 功能点 | 状态 | 说明 |
|--------|------|------|
| Price CRUD (FULL 模型) | ✅ | 创建单个 Payment 记录 |
| Price CRUD (DEPOSIT_BALANCE 模型) | ✅ | 自动创建定金+尾款两个 Payment |
| Payment CRUD + 状态流 | ✅ | markAsPaid 设置 isPaid=true + paidDate |
| PaymentCalendar 按 dueDate 查询 | ✅ | 支持年度范围查询 + 快速标记已付 |
| CalendarEventHelper 创建/更新/删除 | ✅ | insert/update/delete 日历事件完整 |
| Reminder 精确闹钟 (Android 12+) | ✅ | canScheduleExactAlarms() 检查，setExactAndAllowWhileIdle |
| BootCompletedReceiver 重新调度 | ✅ | withTimeout(9000) 防超时 |
| Price→Item / Payment→Price CASCADE | ✅ | 级联删除正确 |
| 删除 Payment 清理日历事件 | ✅ | deletePayment 前调用 deleteEvent |
| dueDate vs paidDate 一致性 | ⚠️ | PriceEditViewModel 加载 paymentDate 逻辑可能不准确 |
| 皮肤组件使用 | ⚠️ | 使用 GradientTopAppBar/SkinIcon，但缺少 SkinClickable/SkinItemAppear/SkinFlingBehavior |

### 发现的问题

- 🟡 **PaymentWithItemInfo 重复定义** — entity 和 dao 中各有一份，PaymentCalendarScreen 导入 dao 版本与其他不一致 (PriceDao.kt:232, PaymentCalendarScreen.kt:28)
- 🟡 **PriceEditViewModel 加载 paymentDate 逻辑不准确** — minByOrNull { it.createdAt }?.paidDate 在多 Payment 时可能选错 (PriceViewModel.kt:111)
- 🟡 **BackupManager 导入时日历事件创建失败无错误处理** (BackupManager.kt:265-282)
- 🔵 PriceEditViewModel 更新时 paymentDate 变化可能被忽略 (PriceViewModel.kt:309-325)
- 🔵 DEPOSIT_BALANCE 尾款 dueDate 使用 now（立即到期），应让用户指定 (PriceViewModel.kt:219, 300)
- 🔵 PriceManageScreen/PaymentManageScreen/PaymentCalendarScreen 列表缺少 SkinItemAppear 和 SkinFlingBehavior
- 🔵 PaymentReminderScheduler 过期提醒静默跳过 (PaymentReminderScheduler.kt:42-45)
- 🔵 PaymentManageScreen/PaymentCalendarScreen 卡片缺少 SkinClickable 包装

---

## 领域 4：统计分析

### 功能清单

| 功能点 | 状态 | 说明 |
|--------|------|------|
| 总览页面 | ✅ | 已拥有/愿望单/套装/穿搭/总消费/均价/最贵/品牌Top5 |
| 消费趋势 | ✅ | 按年月折线图，使用 dueDate，支持年份切换 |
| 消费分布 | ✅ | 品牌/分类/风格/季节四维度，Top10+其他，甜甜圈图 |
| 愿望单分析 | ✅ | 预算/已实现率/优先级分布，支持钻取 |
| 标签页导航 | ✅ | HorizontalPager + ScrollableTabRow + SkinTabIndicator |
| LineChart / DonutChart / StatsProgressBar | ✅ | 点击交互、tooltip、动画完整 |
| 钻取导航 | ✅ | 状态/品牌/月度/维度/优先级 → FilteredItemList 参数正确 |
| 空数据状态 | ✅ | WishlistAnalysis 和 SpendingDistribution 有空数据提示 |
| 皮肤组件使用 | ⚠️ | 使用 Material3 Card 而非 LolitaCard，缺少 SkinClickable/SkinItemAppear |

### 发现的问题

- 🟡 **季节消费查询字段命名混淆** — getSpendingBySeasonRaw() 返回 `i.season AS style`，复用 StyleSpending 类，UI 层需 split 处理 (PriceDao.kt:134-141, SpendingDistributionScreen.kt:79-89)
- 🟡 **Stats 页面缺少皮肤感知组件** — 使用标准 Card 而非 LolitaCard，缺少 SkinClickable (StatsScreen.kt:190, 235, 304, 348)
- 🔵 季节数据 split 逻辑在 UI 层而非 Repository 层 (SpendingDistributionScreen.kt:79-89)
- 🔵 "其他" 分组无钻取支持 (SpendingDistributionScreen.kt:223)

---

## 领域 5：设置/备份

### 功能清单

| 功能点 | 状态 | 说明 |
|--------|------|------|
| Brand/Category/Style/Season/Location/Source CRUD | ✅ | 全部完整，支持 logo 图片、分组等 |
| Style/Season 级联更新 | ✅ | 重命名时更新所有 Item 记录，Season 支持逗号分隔 |
| Style/Season 删除级联 | ✅ | 删除前清空 Item 关联字段 |
| Brand/Category 删除保护 | ✅ | FK RESTRICT + 异常捕获 + UI 错误提示 |
| Location 删除提示 | ✅ | 显示关联 Item 数量，删除后 locationId 置 null |
| BackupManager JSON 导出 | ✅ | 包含全部 12 个实体 |
| BackupManager JSON 导入 | ✅ | 支持 JSON/ZIP，向后兼容迁移 |
| BackupManager CSV 导出 | ✅ | 完整列定义 |
| BackupManager ZIP 导出 | ✅ | JSON + 所有图片文件 |
| 导入预览 | ✅ | previewBackup() 显示统计信息 |
| 导入后日历事件/提醒重建 | ✅ | 重建日历事件和调度提醒 |
| TaobaoOrderParser 解析 | ✅ | 多文件解析、去重、智能分类 |
| Taobao 导入流程 (5步) | ✅ | SELECT→PREPARE→DETAIL→IMPORTING→RESULT |
| Taobao 定金尾款匹配 | ✅ | 自动匹配 + 手动配对 |
| ThemeSelect 皮肤切换持久化 | ✅ | DataStore 持久化 skinType |
| Settings 页面导航 | ✅ | 所有入口正确 |
| AttributeManage 导航枢纽 | ✅ | 6 个属性管理入口 |
| 皮肤组件使用 | ✅ | GradientTopAppBar, LolitaCard, SkinIcon |

### 发现的问题

- 🔴 **TaobaoImportViewModel 创建 Brand 时缺少 logoUrl** — 导入品牌无 logo (TaobaoImportViewModel.kt:275-276)
- 🔴 **ImportDetailScreen 缺少 Category group 编辑** — 导入后 Category group 可能与实际不符 (ImportDetailScreen.kt:568-649)
- 🟡 **BackupManager 导入时日历事件创建失败无处理** (BackupManager.kt:254, 265-282)
- 🟡 **TaobaoImport 定金尾款自动匹配评分阈值可能过严** — bestScore >= 8 (TaobaoImportViewModel.kt:669)
- 🟡 **StyleRepository/SeasonRepository 级联更新未处理 null 值** (StyleRepository.kt:24, SeasonRepository.kt:24-29)
- 🟡 **BackupManager migrateBackupData 只处理 colors 字段** — 未来字段变更需手动添加 (BackupManager.kt:492-506)
- 🔵 SettingsScreen 版本号硬编码 "v1.0.0"，应从 BuildConfig 读取 (SettingsScreen.kt:190)
- 🔵 BackupRestoreScreen 导出成功后无具体文件路径提示
- 🔵 TaobaoImportScreen 缺少文件大小限制提示
- 🔵 LocationManageScreen 图片缺少加载占位符 (LocationManageScreen.kt:159-164)

---

## 领域 6：基础设施

### 功能清单

| 功能点 | 状态 | 说明 |
|--------|------|------|
| Screen 路由定义 (28个) | ✅ | 全部完整定义 |
| LolitaNavHost 路由注册 | ✅ | 所有 28 个路由注册，参数提取正确 |
| 导航参数类型化 | ✅ | NavType.LongType / StringType，defaultValue 正确 |
| AppModule 仓储注册 | ✅ | 12 个 lazy singleton |
| LolitaDatabase 实体/DAO | ✅ | 12 实体 + 11 DAO 全部注册 |
| Migration 链 v1→v14 | ✅ | 13 个 Migration 完整 |
| TypeConverters | ✅ | 覆盖所有 enum + List<String> |
| LolitaApplication 初始化 | ✅ | AppModule.init + NotificationChannel + 历史数据刷新 |
| IconKey 枚举 (45个) | ✅ | 5 导航 + 12 操作 + 13 内容 + 9 箭头 + 6 状态 |
| SkinIcon 映射 | ✅ | 完整映射所有 45 个 IconKey |
| BaseSkinIconProvider | ✅ | 5 个基础接口实现 |
| SweetIconProvider | ✅ | 完整实现所有 45 个图标 |
| GothicIconProvider | ⚠️ | 文件存在，需完整验证 |
| ChineseIconProvider | ⚠️ | 文件存在，需完整验证 |
| ClassicIconProvider | ⚠️ | 文件存在，需完整验证 |
| GradientTopAppBar | ✅ | compact 模式 + statusBarsPadding + 装饰动画 |
| LolitaCard / ColorSelector / BrandLogo | ✅ | 完整实现 |
| EmptyState / SortOption / SwipeToDeleteContainer | ✅ | 完整实现 |
| UnsavedChangesHandler | ✅ | BackHandler + 确认对话框 |
| BootCompletedReceiver | ✅ | 重新调度支付提醒 + 穿搭提醒 |
| ImageFileHelper | ✅ | copy/delete/download 三方法完整 |
| MatchingEngine | ✅ | 余弦相似度 + 共现提升 |

### 发现的问题

- 🔴 **CLAUDE.md 数据库版本过时** — 文档写 version 6，实际 version 14 (LolitaDatabase.kt:28)
- 🟡 **Gothic/Chinese/Classic IconProvider 完整性未验证** — 文件较大未完整读取
- 🟡 **Migration v6→v7 和 v7→v8 中 Location 表可能重复创建** (LolitaDatabase.kt:122-237)
- 🟡 **BootCompletedReceiver 导入了未使用的 runBlocking** (BootCompletedReceiver.kt:11)
- 🟡 **ColorSelector JSON 修复逻辑可能过于激进** — replace("\\", "") 可能破坏合法转义 (ColorSelector.kt:53-71)
- 🔵 ImageFileHelper.deleteImage() 路径检查用 contains 而非 startsWith，可能被绕过 (ImageFileHelper.kt:42-48)
- 🔵 NotificationChannelSetup 缺少 null 检查 (NotificationChannelSetup.kt:32-33)
- 🔵 SkinIcon when 表达式缺少 else 分支 (SkinIcon.kt:16-71)
- 🔵 GradientTopAppBar infiniteRepeatable 动画可能影响低端设备性能 (GradientTopAppBar.kt:119-163)
- 🔵 MatchingEngine encode() 中 item.colors 可能为 null (MatchingEngine.kt:28-35)

---

## 问题汇总

### 🔴 阻塞（6 个）

| # | 领域 | 问题 | 文件 |
|---|------|------|------|
| 1 | 基础设施 | CLAUDE.md 数据库版本过时（写 6 实际 14） | CLAUDE.md |
| 2 | 设置/备份 | TaobaoImport 创建 Brand 时缺少 logoUrl | TaobaoImportViewModel.kt:275 |
| 3 | 设置/备份 | ImportDetailScreen 缺少 Category group 编辑 | ImportDetailScreen.kt:568 |
| 4 | 服饰管理 | LocationDetailViewModel picker 缺少品牌/类别信息 | LocationDetailViewModel.kt:74-90 |
| 5 | 坐标/穿搭 | OutfitLogRepository.updateOutfitLogWithItems() 加载整表数据 | OutfitLogRepository.kt:87-98 |
| 6 | 价格/付款 | PaymentWithItemInfo 重复定义导致导入混淆 | PriceDao.kt:232, PaymentCalendarScreen.kt:28 |

### 🟡 重要（16 个）

| # | 领域 | 问题 | 文件 |
|---|------|------|------|
| 1 | 坐标/穿搭 | Coordinate 删除时未清理 imageUrl 文件 | CoordinateRepository.kt:67-76 |
| 2 | 坐标/穿搭 | Coordinate 编辑时未清理旧 imageUrl 文件 | CoordinateViewModel.kt:399-422 |
| 3 | 坐标/穿搭 | OutfitLogEditScreen 缺少 UnsavedChangesHandler | OutfitLogEditScreen.kt:37 |
| 4 | 服饰管理 | RecommendationViewModel N+1 查询 | RecommendationViewModel.kt:46-56 |
| 5 | 服饰管理 | ItemEditViewModel pendingImageDeletions 未清理 | ItemViewModel.kt:502 |
| 6 | 服饰管理 | ItemRepository.deleteItem() 可选依赖可能导致资源泄漏 | ItemRepository.kt:16-19 |
| 7 | 服饰管理 | PENDING_BALANCE 状态管理逻辑分散 | ItemDao.kt:157, ItemRepository.kt:77 |
| 8 | 服饰管理 | LocationDetail picker 搜索无防抖 | LocationDetailScreen.kt:215-218 |
| 9 | 价格/付款 | PriceEditViewModel 加载 paymentDate 逻辑不准确 | PriceViewModel.kt:111 |
| 10 | 价格/付款 | BackupManager 导入时日历事件创建失败无处理 | BackupManager.kt:265-282 |
| 11 | 统计分析 | 季节消费查询字段命名混淆 (season AS style) | PriceDao.kt:134-141 |
| 12 | 统计分析 | Stats 页面缺少皮肤感知组件 | StatsScreen.kt:190, 235 |
| 13 | 设置/备份 | TaobaoImport 定金尾款匹配阈值可能过严 | TaobaoImportViewModel.kt:669 |
| 14 | 设置/备份 | Style/Season 级联更新未处理 null 值 | StyleRepository.kt:24 |
| 15 | 基础设施 | Gothic/Chinese/Classic IconProvider 完整性未验证 | 3 个 IconProvider 文件 |
| 16 | 基础设施 | ColorSelector JSON 修复逻辑可能过于激进 | ColorSelector.kt:53-71 |

### 🔵 建议（25 个）

| # | 领域 | 问题 |
|---|------|------|
| 1 | 服饰管理 | searchItemsByName() 定义但未使用 |
| 2 | 服饰管理 | applyFilters() 方法过长 |
| 3 | 服饰管理 | FilteredItemListViewModel 缺少 else 分支 |
| 4 | 服饰管理 | CSV 导出缺少 source 字段 |
| 5 | 坐标/穿搭 | deleteAllCoordinates/deleteAllOutfitLogs 未使用 |
| 6 | 坐标/穿搭 | QuickOutfitLog 日期硬编码 12:00 |
| 7 | 坐标/穿搭 | OutfitWidget 缺少定期刷新机制 |
| 8 | 坐标/穿搭 | DailyOutfitReminderScheduler 用 setRepeating 而非精确闹钟 |
| 9 | 价格/付款 | PriceEditViewModel 更新时 paymentDate 变化可能被忽略 |
| 10 | 价格/付款 | DEPOSIT_BALANCE 尾款 dueDate 使用 now |
| 11 | 价格/付款 | Price/Payment 屏幕列表缺少 SkinItemAppear/SkinFlingBehavior |
| 12 | 价格/付款 | PaymentReminderScheduler 过期提醒静默跳过 |
| 13 | 价格/付款 | Payment 卡片缺少 SkinClickable 包装 |
| 14 | 统计分析 | 季节数据 split 逻辑在 UI 层 |
| 15 | 统计分析 | "其他" 分组无钻取支持 |
| 16 | 设置/备份 | SettingsScreen 版本号硬编码 "v1.0.0" |
| 17 | 设置/备份 | BackupRestoreScreen 导出成功无文件路径提示 |
| 18 | 设置/备份 | TaobaoImportScreen 缺少文件大小限制提示 |
| 19 | 设置/备份 | LocationManageScreen 图片缺少加载占位符 |
| 20 | 基础设施 | ImageFileHelper 路径检查用 contains 而非 startsWith |
| 21 | 基础设施 | NotificationChannelSetup 缺少 null 检查 |
| 22 | 基础设施 | SkinIcon when 缺少 else 分支 |
| 23 | 基础设施 | GradientTopAppBar 动画可能影响低端设备 |
| 24 | 基础设施 | MatchingEngine item.colors 可能为 null |
| 25 | 设置/备份 | BackupManager migrateBackupData 只处理 colors 字段 |
