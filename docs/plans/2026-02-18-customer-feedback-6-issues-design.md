# 客户反馈 6 项问题设计方案

日期：2026-02-18

## 问题 1：套装删除闪退

### 根因

- `CoordinateListViewModel.deleteCoordinate()` 存在但从未被 UI 调用
- CoordinateListScreen 和 CoordinateDetailScreen 均无删除按钮
- 详情页 Flow 在套装删除后收到 null 状态，页面未导航离开

### 方案

- CoordinateDetailScreen TopBar 增加删除按钮，点击弹确认对话框
- CoordinateDetailViewModel 增加 `deleteCoordinate()` 方法
- 删除成功后通过 `onDelete` 回调导航返回列表
- LolitaNavHost 中 CoordinateDetailScreen 增加 `onDelete` 参数
- CoordinateListScreen 列表项支持长按菜单（编辑/删除），网格模式同样支持
- CoordinateListViewModel 的 `deleteCoordinate()` 已存在，UI 层接入即可

### 涉及文件

- `CoordinateDetailScreen.kt` — 增加删除按钮和确认对话框
- `CoordinateViewModel.kt` — CoordinateDetailViewModel 增加 delete 方法
- `CoordinateListScreen.kt` — 增加长按菜单（编辑/删除）
- `LolitaNavHost.kt` — CoordinateDetailScreen 增加 onDelete 回调

---

## 问题 2：尾款导入失败（部分字段为空）

### 根因

`executeImport()` 验证 `brandId > 0 && categoryId > 0` 对所有项目生效，尾款项通常只有价格和日期，品牌/分类为空导致被排除出有效集合，进而破坏定金尾款配对。

### 方案

- 尾款项（`paymentRole == BALANCE`）跳过品牌/分类验证，只需 `price > 0`
- 合并导入时尾款项只取 `price` 和 `purchaseDate`，忽略 color/size/imageUrl
- 未配对的尾款项单独导入时仍需品牌/分类（保持现有逻辑）

### 涉及文件

- `TaobaoImportViewModel.kt` — 修改 `executeImport()` 的验证逻辑

---

## 问题 3：套装列表展示价格

### 现状

CoordinateDetailScreen 已有价格汇总（从关联服饰的 Price 表计算），但列表页不显示价格。

### 方案

- `CoordinateListUiState` 增加 `priceByCoordinate: Map<Long, Double>`
- `loadCoordinates()` 中 combine `priceRepository` 数据，按 coordinateId 汇总总价
- 1 列模式：CoordinateCard 名称旁显示价格
- 2/3 列模式：CoordinateGridCard 图片右上角叠加价格标签（与 ItemGridCard 一致）

### 涉及文件

- `CoordinateViewModel.kt` — CoordinateListViewModel 增加价格数据加载
- `CoordinateListScreen.kt` — 卡片组件增加价格显示

---

## 问题 4：套装支持封面图

### 方案

- Coordinate 实体增加 `imageUrl: String?` 字段
- 数据库迁移 v5→v6：`ALTER TABLE coordinates ADD COLUMN image_url TEXT DEFAULT NULL`
- LolitaDatabase version 升至 6
- CoordinateEditScreen 增加图片选择区域（复用 ImageFileHelper + 相册/拍照选择）
- CoordinateEditViewModel 增加 `imageUrl` 状态和更新方法
- 列表卡片展示封面图，详情页展示封面图

### 涉及文件

- `Coordinate.kt` — 增加 imageUrl 字段
- `LolitaDatabase.kt` — 增加 v5→v6 迁移
- `CoordinateEditScreen.kt` — 增加图片选择 UI
- `CoordinateViewModel.kt` — CoordinateEditViewModel 增加图片状态
- `CoordinateListScreen.kt` — 卡片展示封面图
- `CoordinateDetailScreen.kt` — 详情页展示封面图

---

## 问题 5：套装列表 3 种展示模式

### 现状

CoordinateListScreen 使用 LazyColumn 单列卡片，无切换功能。服饰列表已实现 1/2/3 列切换（LazyColumn + LazyVerticalGrid）。

### 方案

复用服饰列表的模式切换模式：

- `CoordinateListUiState` 增加 `columnsPerRow: Int = 1`
- TopBar 增加切换按钮（ViewAgenda → GridView → Apps 图标循环）
- 1 列模式：LazyColumn + CoordinateCard（现有卡片增加封面图和价格）
- 2/3 列模式：LazyVerticalGrid + CoordinateGridCard 新组件
  - 封面图（aspectRatio 0.8f），无图时显示关联服饰缩略图拼图
  - 图片右上角价格标签
  - 底部：套装名称、服饰件数
  - 长按弹出菜单（编辑/删除）

### 涉及文件

- `CoordinateViewModel.kt` — 增加 columnsPerRow 状态和切换方法
- `CoordinateListScreen.kt` — 增加切换按钮、LazyVerticalGrid、CoordinateGridCard

---

## 问题 6：底部菜单栏高度缩窄

### 现状

NavigationBar 使用 Material3 默认高度（80dp），视觉上偏高有白边。

### 方案

- NavigationBar 添加 `Modifier.height(64.dp)` 限制高度
- 如需进一步调整，可配合 `windowInsets = WindowInsets(0)` 去除额外 insets padding

### 涉及文件

- `LolitaNavHost.kt` — NavigationBar 添加高度限制

---

## 数据库迁移汇总

当前版本：5 → 目标版本：6

迁移 SQL：
```sql
ALTER TABLE coordinates ADD COLUMN image_url TEXT DEFAULT NULL
```
