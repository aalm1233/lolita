# 套装详情页添加服饰

日期: 2026-02-23

## 需求

在套装详情页（CoordinateDetailScreen）中，支持直接添加衣服到套装，无需跳转到编辑页。

## 交互设计

- 入口：「包含服饰 (N)」标题行右侧添加「+ 添加服饰」按钮
- 点击后弹出 ModalBottomSheet
- BottomSheet 内容：
  1. 标题栏：「选择服饰」+ 右侧「确认 (N)」按钮
  2. 搜索框：按名称过滤
  3. 衣服列表：缩略图、名称、颜色/风格标签、勾选框
     - 已在当前套装：预勾选，取消 = 移除
     - 已属于其他套装：显示警告「已属于套装「XXX」」
     - 未分配：正常显示
- 多选模式，确认后批量更新

## 技术方案

方案 A：在 CoordinateDetailScreen 内直接集成 BottomSheet。

### ViewModel 变更 (CoordinateDetailViewModel)

新增字段：
- `allItems: List<Item>` — 全部衣服
- `coordinateNames: Map<Long, String>` — 套装名称映射
- `selectedItemIds: Set<Long>` — 选中的 item ID
- `searchQuery: String` — 搜索关键词

新增方法：
- `loadAllItems()` — 加载衣服和套装名称
- `toggleItemSelection(itemId)` — 切换选中
- `updateSearchQuery(query)` — 更新搜索
- `confirmAddItems()` — 计算 added/removed，调用 `updateCoordinateWithItems()`

### 数据流

1. 计算 addedIds = selectedItemIds - originalItemIds
2. 计算 removedIds = originalItemIds - selectedItemIds
3. 调用 `coordinateRepository.updateCoordinateWithItems()` 原子更新
4. 关闭 BottomSheet，Flow 自动刷新列表

### 不需要改动

- 无新 Screen / 导航路由
- 无新 DAO 方法
- 无数据库变更

## 涉及文件

- `CoordinateDetailScreen.kt` — 添加 BottomSheet UI
- `CoordinateViewModel.kt` — CoordinateDetailViewModel 新增状态和方法
