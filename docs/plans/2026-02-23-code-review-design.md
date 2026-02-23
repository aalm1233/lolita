# 代码功能审查设计方案

日期：2026-02-23

## 目标

对 Lolita 时尚管理 App 进行全面的功能完整性检查，覆盖所有 28+ 屏幕、164 个 Kotlin 文件，产出功能清单 + 问题列表。

## 审查方案

按 6 大业务领域端到端审查，每个领域由独立 agent 并行执行。

## 6 大审查领域

### 领域 1：服饰管理

- **屏幕：** ItemList, ItemDetail, ItemEdit, FilteredItemList, Wishlist, Recommendation, LocationDetail
- **数据层：** Item, Brand, Category, Style, Season, Location, Source + 对应 DAO/Repo/VM
- **审查重点：** CRUD 完整性、筛选/搜索、状态流转、FK 约束、图片管理

### 领域 2：坐标/穿搭

- **屏幕：** CoordinateDetail, CoordinateEdit, OutfitLogList, OutfitLogDetail, OutfitLogEdit, QuickOutfitLog
- **数据层：** Coordinate, OutfitLog, OutfitItemCrossRef + 对应 DAO/Repo/VM
- **审查重点：** 多对多关系、物品关联/解除、日期管理、图片管理

### 领域 3：价格/付款

- **屏幕：** PriceManage, PriceEdit, PaymentManage, PaymentEdit, PaymentCalendar
- **数据层：** Price, Payment, PaymentWithItemInfo + 对应 DAO/Repo/VM
- **审查重点：** 定金/尾款模型、付款状态流转、日历事件、提醒调度

### 领域 4：统计分析

- **屏幕：** Stats, StatsPage, SpendingTrend, SpendingDistribution, WishlistAnalysis
- **数据层：** StatsData + 各种聚合查询
- **审查重点：** 数据聚合正确性、图表数据源、钻取导航

### 领域 5：设置/备份

- **屏幕：** Settings, BrandManage, CategoryManage, StyleManage, SeasonManage, LocationManage, SourceManage, AttributeManage, BackupRestore, TaobaoImport, ThemeSelect
- **数据层：** 各预设表 + BackupManager + TaobaoOrderParser
- **审查重点：** 预设数据 CRUD、名称级联更新、备份导入导出完整性、淘宝导入

### 领域 6：基础设施

- **屏幕：** Navigation (LolitaNavHost), Widget, 通知系统
- **数据层：** Skin system (29 files), Notification (7 files), Widget (2 files)
- **审查重点：** 导航路由完整性、皮肤一致性、通知调度、Widget 数据刷新

## 标准化检查项

### 数据层

- Entity 字段是否完整（对照 UI 需要的数据）
- DAO 方法是否覆盖所有 CRUD 操作 + 必要的查询
- Repository 是否正确暴露了 DAO 的所有方法
- FK 约束是否合理（CASCADE vs RESTRICT）
- TypeConverter 是否覆盖所有自定义类型

### ViewModel 层

- StateFlow 是否覆盖了屏幕所有需要的状态
- 是否有未被 UI 消费的状态，或 UI 需要但 VM 未提供的状态
- 异步操作是否在 viewModelScope 中执行
- 错误处理是否存在（保存失败、加载失败等）

### UI 层

- 屏幕是否使用了正确的皮肤组件（SkinClickable, LolitaCard, GradientTopAppBar 等）
- 列表是否有空状态处理
- 编辑屏幕是否有未保存变更提示（UnsavedChangesHandler）
- 导航参数传递是否正确
- 图片选择/显示流程是否完整

### 跨层

- DAO 中定义但从未被 Repository/VM 调用的方法
- Repository 中定义但从未被 VM 调用的方法
- 导航路由是否都在 NavHost 中注册
- 数据删除时的级联清理（图片文件、日历事件等）

## 产出格式

### 各领域审查结果

| 功能点 | 状态 | 说明 |
|--------|------|------|
| 功能名称 | ✅ 完整 / ⚠️ 部分 / ❌ 缺失 | 具体说明 |

### 问题严重程度

- 🔴 阻塞：功能无法正常使用
- 🟡 重要：功能可用但有明显缺陷
- 🔵 建议：可改进但不影响使用

## 执行方式

6 个并行 agent，每个负责一个业务领域，独立阅读相关文件并按检查项审查，最终汇总为一份报告。
