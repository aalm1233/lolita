# 统计页下钻 & 服饰点击跳转详情

日期: 2026-02-20

## 目标

1. 统计页所有数据支持点击下钻到对应的服饰列表
2. 所有展示服饰的地方支持点击跳转到服饰详情

## 现状

| 页面 | 当前状态 |
|------|---------|
| ItemListScreen | 已支持点击跳转 ItemDetailScreen |
| WishlistScreen | 已支持点击跳转 ItemDetailScreen |
| OutfitLogDetailScreen | 已支持 onNavigateToItem |
| CoordinateDetailScreen | 不支持点击跳转（仅有移除按钮） |
| StatsPageScreen 总览 | 4个计数卡片、消费卡片、最贵单品、品牌Top5 均不可点击 |
| SpendingDistributionScreen | 排行榜列表不可点击 |
| SpendingTrendScreen | 月份明细行不可点击 |
| WishlistAnalysisScreen | 优先级明细行不可点击 |

## 设计

### 新路由: FilteredItemList

```kotlin
data object FilteredItemList : Screen {
    override val route = "filtered_item_list?filterType={filterType}&filterValue={filterValue}&title={title}"
    fun createRoute(filterType: String, filterValue: String, title: String) =
        "filtered_item_list?filterType=$filterType&filterValue=${Uri.encode(filterValue)}&title=${Uri.encode(title)}"
}
```

### 筛选类型

| filterType | filterValue 示例 | 说明 |
|-----------|-----------------|------|
| `status_owned` | (空) | 已拥有的服饰 |
| `status_wished` | (空) | 愿望单服饰 |
| `brand` | "Baby, the Stars Shine Bright" | 按品牌筛选 |
| `category` | "JSK" | 按分类筛选 |
| `style` | "甜系" | 按风格筛选 |
| `season` | "春" | 按季节筛选 |
| `month` | "2025-03" | 按购买月份筛选 |
| `priority` | "HIGH" / "MEDIUM" / "LOW" | 按优先级筛选愿望单 |

### FilteredItemListScreen

- GradientTopAppBar + 返回按钮，标题为传入的 title 参数
- FilteredItemListViewModel 根据 filterType/filterValue 查询对应服饰
- 复用现有 ItemCard 组件展示列表
- 点击 item → ItemDetailScreen
- 空状态提示

### 统计页改造

StatsPageScreen 接收 `onNavigateToFilteredList(filterType, filterValue, title)` 和 `onNavigateToItemDetail(itemId)` 回调，传递给各子页面。

#### 总览页 (StatsContent)
- "已拥有" 卡片 → `status_owned`, "", "已拥有"
- "愿望单" 卡片 → `status_wished`, "", "愿望单"
- "套装" 卡片 → 跳转到套装 tab（底部导航切换，暂不实现，或不做点击）
- "穿搭记录" 卡片 → 跳转到穿搭列表（底部导航切换，暂不实现，或不做点击）
- "最贵单品" 卡片 → `onNavigateToItemDetail(itemId)`
- 品牌 Top 5 每行 → `brand`, brandName, "品牌: $brandName"

#### 消费分布页 (SpendingDistributionContent)
- 排行榜每行可点击 → 根据当前维度:
  - BRAND → `brand`, name, "品牌: $name"
  - CATEGORY → `category`, name, "分类: $name"
  - STYLE → `style`, name, "风格: $name"
  - SEASON → `season`, name, "季节: $name"

#### 消费趋势页 (SpendingTrendContent)
- 月份行可点击 → `month`, "2025-03", "2025年3月"

#### 愿望单分析页 (WishlistAnalysisContent)
- 优先级行可点击 → `priority`, "HIGH", "高优先级"

### 套装详情改造

CoordinateDetailScreen 接收 `onNavigateToItem: (Long) -> Unit`，CoordinateItemCard 整体可点击。

### 不需要改动

- ItemListScreen — 已有点击跳转
- WishlistScreen — 已有点击跳转
- OutfitLogDetailScreen — 已有 onNavigateToItem
- 付款日历 — 不涉及服饰列表

## 涉及文件

| 文件 | 改动 |
|------|------|
| Screen.kt | 新增 FilteredItemList 路由 |
| LolitaNavHost.kt | 注册 FilteredItemList 路由 |
| FilteredItemListScreen.kt (新) | 筛选结果页面 |
| FilteredItemListViewModel.kt (新) | 筛选查询逻辑 |
| StatsPageScreen.kt | 接收导航回调 |
| StatsScreen.kt | StatCard/SpendingCard/最贵单品/品牌Top5 可点击 |
| SpendingDistributionScreen.kt | 排行榜行可点击 |
| SpendingTrendScreen.kt | 月份行可点击 |
| WishlistAnalysisScreen.kt | 优先级行可点击 |
| CoordinateDetailScreen.kt | CoordinateItemCard 可点击 |
| ItemRepository.kt | 可能需要新增筛选查询方法 |
| ItemDao.kt | 可能需要新增筛选查询 |
