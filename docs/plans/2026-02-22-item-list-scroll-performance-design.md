# 服饰列表滑动性能优化设计

## 问题

服饰页面列表滑动时卡顿超过3秒，所有皮肤均复现。数据量约50条以内。

## 根因分析

1. **SkinCardGlow 无限动画**：每张 LolitaCard 创建独立 `rememberInfiniteTransition`（4秒循环），20张可见卡片 = 20个无限动画同时运行，每帧触发重组和重绘
2. **`indexOf()` O(n) 查找**：`ItemListScreen.kt:341` 每个 item 调用 `filteredItems.indexOf(item)`，50个 item = 2500次比较
3. **SkinItemAppear 逐项动画**：每个列表项创建独立 `Animatable` + `LaunchedEffect`，滑动时新 item 不断创建动画协程

## 方案：最小改动优化

### 改动1：SkinCardGlow — 滑动时跳过绘制

- `skinCardGlow()` 增加 `isScrolling: Boolean` 参数
- 滑动时直接返回 `this`，不附加动画 modifier
- `LolitaCard` 增加 `isScrolling` 参数透传
- `ItemListScreen` 通过 `LazyListState.isScrollInProgress` 传入

### 改动2：indexOf → itemsIndexed

```kotlin
// Before
items(items = uiState.filteredItems, key = { it.id }) { item ->
    val index = uiState.filteredItems.indexOf(item)

// After
itemsIndexed(items = uiState.filteredItems, key = { _, item -> item.id }) { index, item ->
```

### 改动3：SkinItemAppear — 首屏阈值

- index 超过阈值（~10）时直接设 progress = 1f，跳过动画
- 避免滚动时不断创建动画协程

### 改动4：LazyVerticalGrid 同步修复

- Grid 路径的 LolitaCard 同样传入 isScrolling

## 影响范围

| 文件 | 改动 |
|------|------|
| `SkinCardGlow.kt` | 增加 isScrolling 判断 |
| `LolitaCard.kt` | 增加 isScrolling 参数透传 |
| `SkinItemAppear.kt` | 增加首屏阈值判断 |
| `ItemListScreen.kt` | itemsIndexed + 传递 isScrollInProgress |
