# 套装列表 3 项问题设计方案

日期：2026-02-18

## 问题 1：套装列表展示切换没有生效

### 根因

`ItemListScreen` 的列数切换按钮调用 `ItemListViewModel.setColumns()`，只影响服饰列表。套装 tab 使用的 `CoordinateListContent` 有独立的 `CoordinateListViewModel`，从未收到列数变更。

### 方案

- 在 `ItemListScreen` 中创建 `CoordinateListViewModel` 实例
- 当 pager 在套装 tab（page == 2）时，列数切换按钮调用 `CoordinateListViewModel.setColumns()`
- 将同一个 ViewModel 实例传给 `CoordinateListContent`
- 切换图标也根据当前 tab 读取对应 ViewModel 的 columnsPerRow

### 涉及文件

- `ItemListScreen.kt` — 列数切换逻辑按 tab 分发

---

## 问题 2：套装卡片展示关联服饰小图

### 现状

`itemImages` 数据已在 ViewModel 中加载并传递给卡片组件，但卡片 UI 未渲染。

### 方案

- `CoordinateCard`（1列模式）：在件数标签旁展示一行小圆形缩略图（最多4张，28dp，重叠排列，offset -8dp）
- `CoordinateGridCard`（2/3列模式）：在信息区域底部展示小圆形缩略图行
- 无图的 item 显示粉色渐变圆形占位

### 涉及文件

- `CoordinateListScreen.kt` — CoordinateCard 和 CoordinateGridCard 增加缩略图渲染

---

## 问题 3：左滑删除

### 方案

- 使用 Material3 `SwipeToDismissBox` 实现左滑露出红色删除背景
- 仅在1列模式（LazyColumn）下生效，2/3列网格模式保持长按菜单
- 套装列表和服饰列表都支持
- 释放后触发现有的删除确认对话框（不直接删除）
- 提取公共 `SwipeToDeleteContainer` 组件供两个列表复用

### 涉及文件

- `CoordinateListScreen.kt` — 套装1列模式增加左滑删除
- `ItemListScreen.kt` — 服饰1列模式增加左滑删除
