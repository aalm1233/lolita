# 功能优化设计方案

日期：2026-02-22

## 功能1：Tab 重命名

- 底部导航：「服饰」→「首页」
- ItemListScreen 顶部标题：「服饰」→「首页」
- 内部子 tab：「已拥有」→「服饰」
- 涉及文件：`LolitaNavHost.kt`（BottomNavItems）、`ItemListScreen.kt`（标题 + tab 标签）

## 功能2：待补尾款状态

- `ItemStatus` 新增 `PENDING_BALANCE` 枚举值
- `Enums.kt` 中 TypeConverter 已支持字符串存储，无需 migration
- 触发逻辑：
  - 当 Item 的 Price 类型为 `DEPOSIT_BALANCE` 且尾款 Payment 未付款 → Item 状态设为 `PENDING_BALANCE`
  - 当尾款 Payment 标记已付款 → Item 状态自动回转为 `OWNED`
- 触发时机：`PaymentRepository` 或 `PaymentViewModel` 中更新 Payment 状态时联动更新 Item
- UI：「服饰」子 tab 同时显示 OWNED 和 PENDING_BALANCE，列表中用标签区分；筛选器增加待补尾款选项

## 功能3：来源字段

- `Item` 实体新增 `source: String?` 可选字段
- 新增 `Source` 实体（id, name, isPreset），结构同 Brand/Style/Season
- 新增 `SourceDao`、`SourceRepository`，注册到 `AppModule`
- 预置数据：淘宝、咸鱼、线下
- 数据库 migration v8→v9：ALTER TABLE item ADD COLUMN source TEXT; CREATE TABLE Source
- `ItemEditScreen` 新增来源选择器
- `ItemDetailScreen` 展示来源
- `BackupManager` 增加 Source 表导入导出

## 功能4：设置页属性管理合并

- 将品牌、类型、风格、季节、位置、来源管理合并为一个「属性管理」入口
- 新增 `AttributeManageScreen`：列出所有可配置属性，点击进入对应管理页
- 导航：个人 → 属性管理 → 品牌/类型/风格/季节/位置/来源
- 新增 `Screen.AttributeManage` 路由

## 功能5：个人页面改造

- 底部导航：「设置」→「个人」，页面标题同步修改
- 顶部新增个人信息区域：
  - 头像：圆形，支持点击上传/更换，`ImageFileHelper` 存储
  - 昵称：支持点击编辑
  - 数据概览：服饰总数、套装数、总花费
- 头像和昵称存储在 `AppPreferences`（DataStore），不需要数据库
- 下方保留原有菜单：属性管理、数据备份与恢复、淘宝订单导入、每日穿搭提醒、显示总价、皮肤选择

## 功能6：位置卡片点击跳转

- 「位置」tab 中点击位置卡片 → 跳转 `FilteredItemList`，按 locationId 筛选
- 复用现有 `FilteredItemList` 路由，增加 locationId 参数
- 扩展 FilteredItemList ViewModel 支持 locationId 筛选

## 功能7：套装编辑搜索服装

- `CoordinateEditScreen` Item 选择区域顶部新增搜索框
- 按名称本地过滤可选 Item 列表
- `filter { it.name.contains(query, ignoreCase = true) }`

## 功能8：付款日历月度增强

- 改造现有 `PaymentCalendarScreen`：
  - 月历格子用不同颜色/标记区分已付款和待付款日期
  - 显示当月汇总：已付款总额、待付款总额、笔数
  - 点击日期查看当天付款详情
  - 左右滑动切换月份
