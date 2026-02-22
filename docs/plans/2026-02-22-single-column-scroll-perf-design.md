# 单列列表滑动性能优化设计

日期：2026-02-22

## 问题

服饰单列列表（columnsPerRow == 1 的 LazyColumn）快速滑动时掉帧，30条以内数据就能感知到卡顿。

## 根因分析

1. **SkinBackgroundAnimation 持续重绘** — 全屏 Canvas 每 16ms 更新粒子，滑动时不暂停，与列表渲染争抢 GPU
2. **AsyncImage 全分辨率解码** — Coil 未指定目标尺寸，可能以原图分辨率解码
3. **DropdownMenu 始终组合** — 每个 ItemCard 内的 DropdownMenu 即使未展开也在组合树中
4. **isScrollInProgress 传参导致全量 recompose** — 滑动状态变化时所有可见 item 重组
5. **ItemCard 参数不稳定** — 多个独立参数无法被 Compose 编译器判定为稳定

## 设计

### 1. SkinBackgroundAnimation 滑动时暂停

- 引入 `LocalIsListScrolling: CompositionLocal<Boolean>`
- SkinBackgroundAnimation 读取该 Local，滑动时停止更新 `frameTime`，粒子冻结
- 滑动结束后恢复动画

文件变更：
- 新增 `LocalIsListScrolling` 定义（放在 skin/animation 包下）
- 修改 `SkinBackgroundAnimation.kt`：读取 Local，条件跳过 delay 循环
- 修改 `ItemListScreen.kt`：在 LazyColumn 外层提供 CompositionLocalProvider

### 2. AsyncImage 尺寸约束

- 使用 `ImageRequest.Builder(context).data(file).size(sizePx).build()` 作为 model
- 单列卡片图片为 80dp，按屏幕密度计算像素尺寸

文件变更：
- 修改 `ItemListScreen.kt` 中 `ItemCard` 的 AsyncImage 调用

### 3. DropdownMenu 懒加载

- 将 `DropdownMenu` 包裹在 `if (showMenu)` 条件中
- 仅在用户点击菜单按钮时才组合 DropdownMenu 及其子组件

文件变更：
- 修改 `ItemListScreen.kt` 中 `ItemCard` 的 DropdownMenu 部分

### 4. LocalIsListScrolling 替代参数传递

- `LolitaCard` / `skinCardGlow` 内部直接读取 `LocalIsListScrolling.current`
- 移除 `isScrolling` 参数的层层传递（ItemCard → LolitaCard → skinCardGlow）
- 滑动状态变化只触发读取该 Local 的组件重绘，不导致整个 item 树 recompose

文件变更：
- 新增 `LocalIsListScrolling` 定义
- 修改 `LolitaCard.kt`：移除 isScrolling 参数，内部读取 Local
- 修改 `SkinCardGlow.kt`：移除 isScrolling 参数，内部读取 Local
- 修改 `ItemListScreen.kt`：移除 isScrolling 传参，外层提供 Provider
- 修改 `ItemGridCard` 同理

### 5. @Immutable ItemCardData 数据类

- 创建 `@Immutable data class ItemCardData`，打包显示数据
- 在 ViewModel 的 combine 中构建 `List<ItemCardData>`
- ItemCard 只接收一个 ItemCardData 参数，Compose 通过引用相等性跳过 recomposition

```kotlin
@Immutable
data class ItemCardData(
    val item: Item,
    val brandName: String?,
    val categoryName: String?,
    val itemPrice: Double?,
    val showPrice: Boolean
)
```

文件变更：
- 新增 `ItemCardData.kt`（或放在 ItemListScreen.kt 内）
- 修改 `ItemListViewModel.kt`：UiState 中增加 `itemCardDataList`
- 修改 `ItemListScreen.kt`：ItemCard 改为接收 ItemCardData

### 保留项

- **SwipeToDeleteContainer** — 保留，不移除

## 影响范围

- `SkinBackgroundAnimation.kt`
- `SkinCardGlow.kt`
- `LolitaCard.kt`
- `ItemListScreen.kt`
- `ItemListViewModel.kt`（或对应 ViewModel）
- 新增 `LocalIsListScrolling` 定义文件
- 新增 `ItemCardData` 数据类
