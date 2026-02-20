# 易用性优化设计文档

## 目标

提升服饰列表、套装列表、愿望单、穿搭日记的操作便捷性，以及所有编辑页面的数据安全性。

## 功能一：排序功能（服饰列表 + 套装列表）

### UI 交互

- 在顶部操作区域添加排序图标按钮（`Icons.AutoMirrored.Filled.Sort`）
- 点击弹出 `DropdownMenu`，选项：
  - 默认排序（按更新时间，最新在前）
  - 按日期 — 最新优先
  - 按日期 — 最早优先
  - 按价格 — 最贵优先（仅 showTotalPrice=true 时显示）
  - 按价格 — 最便宜优先（仅 showTotalPrice=true 时显示）
- 当前选中项显示 checkmark

### 实现

- 新增 `SortOption` 枚举：`DEFAULT`, `DATE_DESC`, `DATE_ASC`, `PRICE_DESC`, `PRICE_ASC`
- 排序状态存在 ViewModel 的 UiState 中
- 服饰列表：在 `applyFilters()` 后追加排序，价格排序用已有的 priceRepository 数据
- 套装列表：在 CoordinateListViewModel 中添加排序，价格数据已有（coordinatePriceSums）

### 影响文件

- `ItemViewModel.kt` — 添加 sortOption 状态和排序逻辑
- `ItemListScreen.kt` — 添加排序按钮 UI
- `CoordinateViewModel.kt` — 添加排序逻辑
- `CoordinateListScreen.kt` — 添加排序按钮 UI

## 功能二：搜索扩展（愿望单 + 套装列表 + 穿搭日记）

### UI 交互

- 参考 ItemListScreen 搜索框样式
- 列表顶部 `OutlinedTextField`，带搜索图标和清除按钮
- 实时过滤，300ms 防抖

### 搜索范围

- 愿望单：搜索服饰名称
- 套装列表：搜索套装名称
- 穿搭日记：搜索备注内容

### 实现

- 各 ViewModel 添加 `searchQuery` 状态
- `combine(searchQuery, dataFlow)` 过滤
- 匹配逻辑：`contains(query, ignoreCase = true)`

### 影响文件

- `WishlistScreen.kt` — 添加搜索框 + ViewModel 搜索逻辑
- `CoordinateViewModel.kt` + `CoordinateListScreen.kt` — 添加搜索
- `OutfitLogViewModel.kt` + `OutfitLogListScreen.kt` — 添加搜索

## 功能三：未保存退出提醒（所有编辑页面）

### UI 交互

- 按返回键或点击顶栏返回箭头时，有未保存修改则弹出 AlertDialog
- 内容："有未保存的修改，确定要离开吗？"
- 按钮：「放弃」/ 「继续编辑」

### 实现

- 创建通用 `UnsavedChangesHandler` composable（放在 `ui/screen/common/`）
  - 参数：`hasUnsavedChanges: Boolean`, `onConfirmLeave: () -> Unit`
  - 内部 `BackHandler` 拦截系统返回
  - 管理对话框显示状态
  - 同时暴露 `onBackClick` 供顶栏返回按钮使用
- 各编辑 ViewModel 添加 `hasUnsavedChanges` 计算
  - 新建模式：任何字段非空即为 true
  - 编辑模式：对比初始值和当前值

### 影响文件

- 新建 `UnsavedChangesHandler.kt`
- `ItemEditScreen.kt` + `ItemViewModel.kt`
- `PriceEditScreen.kt` + `PriceViewModel.kt`
- `PaymentEditScreen.kt` + `PriceViewModel.kt`
- `CoordinateEditScreen.kt` + `CoordinateViewModel.kt`
- `OutfitLogEditScreen.kt` + `OutfitLogViewModel.kt`
