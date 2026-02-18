# 统计页数据分析增强 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将统计页从 2 个 Tab 扩展为 5 个 Tab（总览、消费分布、消费趋势、愿望单分析、付款日历），使用自定义 Canvas 图表组件。

**Architecture:** 每个新 Tab 对应独立 ViewModel，新增 DAO 聚合查询通过 Repository 暴露为 Flow。图表组件为独立 Composable 放在 `ui/component/chart/` 下。

**Tech Stack:** Kotlin, Jetpack Compose Canvas, Room DAO, StateFlow, Material3

---

## Task 1: 新增 DAO 聚合查询数据类

**Files:**
- Create: `app/src/main/java/com/lolita/app/data/local/entity/StatsData.kt`

**Step 1: 创建统计数据类文件**

```kotlin
package com.lolita.app.data.local.entity

data class CategorySpending(
    val name: String,
    val totalSpending: Double
)

data class BrandSpending(
    val name: String,
    val totalSpending: Double
)
data class StyleSpending(
    val style: String,
    val totalSpending: Double
)

data class MonthlySpending(
    val yearMonth: String,  // "2024-01" 格式
    val totalSpending: Double
)

data class ItemWithSpending(
    val itemId: Long,
    val itemName: String,
    val imageUrl: String?,
    val totalSpending: Double
)

data class BrandItemCount(
    val brandName: String,
    val itemCount: Int
)

data class PriorityStats(
    val priority: String,
    val itemCount: Int,
    val totalBudget: Double
)
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/local/entity/StatsData.kt
git commit -m "feat(stats): add data classes for stats aggregation queries"
```

---

## Task 2: 新增 PriceDao 聚合查询

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/local/dao/PriceDao.kt`

**Step 1: 添加按品牌分组消费查询**

在 PriceDao 接口末尾（`getItemPriceSums` 之后）添加：

```kotlin
@Query("""
    SELECT b.name AS name, COALESCE(SUM(p.total_price), 0.0) AS totalSpending
    FROM prices p
    INNER JOIN items i ON p.item_id = i.id
    INNER JOIN brands b ON i.brand_id = b.id
    WHERE i.status = 'OWNED'
    GROUP BY b.name
    ORDER BY totalSpending DESC
""")
fun getSpendingByBrand(): Flow<List<BrandSpending>>
```
**Step 2: 添加按分类分组消费查询**

```kotlin
@Query("""
    SELECT c.name AS name, COALESCE(SUM(p.total_price), 0.0) AS totalSpending
    FROM prices p
    INNER JOIN items i ON p.item_id = i.id
    INNER JOIN categories c ON i.category_id = c.id
    WHERE i.status = 'OWNED'
    GROUP BY c.name
    ORDER BY totalSpending DESC
""")
fun getSpendingByCategory(): Flow<List<CategorySpending>>
```

**Step 3: 添加按风格分组消费查询**

```kotlin
@Query("""
    SELECT i.style AS style, COALESCE(SUM(p.total_price), 0.0) AS totalSpending
    FROM prices p
    INNER JOIN items i ON p.item_id = i.id
    WHERE i.status = 'OWNED' AND i.style IS NOT NULL AND i.style != ''
    GROUP BY i.style
    ORDER BY totalSpending DESC
""")
fun getSpendingByStyle(): Flow<List<StyleSpending>>
```

**Step 4: 添加按月分组消费查询**

```kotlin
@Query("""
    SELECT strftime('%Y-%m', p.purchase_date / 1000, 'unixepoch') AS yearMonth,
           COALESCE(SUM(p.total_price), 0.0) AS totalSpending
    FROM prices p
    INNER JOIN items i ON p.item_id = i.id
    WHERE i.status = 'OWNED' AND p.purchase_date IS NOT NULL
    GROUP BY yearMonth
    ORDER BY yearMonth ASC
""")
fun getMonthlySpending(): Flow<List<MonthlySpending>>
```
**Step 5: 添加最贵单品、季节原始数据、愿望单查询**

```kotlin
@Query("""
    SELECT i.id AS itemId, i.name AS itemName, i.image_url AS imageUrl,
           COALESCE(SUM(p.total_price), 0.0) AS totalSpending
    FROM prices p
    INNER JOIN items i ON p.item_id = i.id
    WHERE i.status = 'OWNED'
    GROUP BY i.id
    ORDER BY totalSpending DESC
    LIMIT 1
""")
fun getMostExpensiveItem(): Flow<ItemWithSpending?>

@Query("""
    SELECT i.season AS style, COALESCE(SUM(p.total_price), 0.0) AS totalSpending
    FROM prices p
    INNER JOIN items i ON p.item_id = i.id
    WHERE i.status = 'OWNED' AND i.season IS NOT NULL AND i.season != ''
    GROUP BY i.season
""")
fun getSpendingBySeasonRaw(): Flow<List<StyleSpending>>

@Query("""
    SELECT COALESCE(SUM(p.total_price), 0.0)
    FROM prices p
    INNER JOIN items i ON p.item_id = i.id
    WHERE i.status = 'WISHED'
""")
fun getWishlistTotalBudget(): Flow<Double>

@Query("""
    SELECT i.priority AS priority, COUNT(i.id) AS itemCount,
           COALESCE(SUM(p.total_price), 0.0) AS totalBudget
    FROM items i
    LEFT JOIN prices p ON p.item_id = i.id
    WHERE i.status = 'WISHED'
    GROUP BY i.priority
""")
fun getWishlistByPriorityStats(): Flow<List<PriorityStats>>
```

**Step 6: 添加必要的 import**

在 PriceDao.kt 顶部添加：
```kotlin
import com.lolita.app.data.local.entity.BrandSpending
import com.lolita.app.data.local.entity.CategorySpending
import com.lolita.app.data.local.entity.StyleSpending
import com.lolita.app.data.local.entity.MonthlySpending
import com.lolita.app.data.local.entity.ItemWithSpending
import com.lolita.app.data.local.entity.PriorityStats
```

**Step 7: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/local/dao/PriceDao.kt
git commit -m "feat(stats): add aggregation queries to PriceDao"
```

---

## Task 3: 新增 ItemDao 查询

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/local/dao/ItemDao.kt`

**Step 1: 添加品牌 Top5 数量排行和愿望单计数查询**

在 ItemDao 接口末尾添加：

```kotlin
@Query("""
    SELECT b.name AS brandName, COUNT(i.id) AS itemCount
    FROM items i
    INNER JOIN brands b ON i.brand_id = b.id
    WHERE i.status = 'OWNED'
    GROUP BY b.name
    ORDER BY itemCount DESC
    LIMIT 5
""")
fun getTopBrandsByCount(): Flow<List<BrandItemCount>>

@Query("SELECT COUNT(*) FROM items WHERE status = 'OWNED'")
fun getOwnedCount(): Flow<Int>

@Query("SELECT COUNT(*) FROM items WHERE status = 'WISHED'")
fun getWishedCount(): Flow<Int>
```

添加 import：
```kotlin
import com.lolita.app.data.local.entity.BrandItemCount
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/local/dao/ItemDao.kt
git commit -m "feat(stats): add brand ranking and count queries to ItemDao"
```
---

## Task 4: 扩展 Repository 层

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/repository/PriceRepository.kt`
- Modify: `app/src/main/java/com/lolita/app/data/repository/ItemRepository.kt`

**Step 1: 在 PriceRepository 添加新方法**

在 PriceRepository 类末尾添加：

```kotlin
fun getSpendingByBrand() = priceDao.getSpendingByBrand()
fun getSpendingByCategory() = priceDao.getSpendingByCategory()
fun getSpendingByStyle() = priceDao.getSpendingByStyle()
fun getSpendingBySeasonRaw() = priceDao.getSpendingBySeasonRaw()
fun getMonthlySpending() = priceDao.getMonthlySpending()
fun getMostExpensiveItem() = priceDao.getMostExpensiveItem()
fun getWishlistTotalBudget() = priceDao.getWishlistTotalBudget()
fun getWishlistByPriorityStats() = priceDao.getWishlistByPriorityStats()
```

**Step 2: 在 ItemRepository 添加新方法**

在 ItemRepository 类末尾添加：

```kotlin
fun getTopBrandsByCount() = itemDao.getTopBrandsByCount()
fun getOwnedCount() = itemDao.getOwnedCount()
fun getWishedCount() = itemDao.getWishedCount()
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/repository/PriceRepository.kt
git add app/src/main/java/com/lolita/app/data/repository/ItemRepository.kt
git commit -m "feat(stats): expose new aggregation queries in repositories"
```

---

## Task 5: 创建图表配色常量

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/component/chart/ChartColors.kt`

**Step 1: 创建图表配色文件**

```kotlin
package com.lolita.app.ui.component.chart

import androidx.compose.ui.graphics.Color

val ChartPalette = listOf(
    Color(0xFFFF69B4), // Pink400
    Color(0xFFFF91A4), // Pink300
    Color(0xFFFF1493), // Pink500
    Color(0xFFE91E8C), // Pink600
    Color(0xFFFFB6C1), // Pink200
    Color(0xFFFF007F), // Rose
    Color(0xFFE6E6FA), // Lavender
    Color(0xFFFFD93D), // Yellow
    Color(0xFF6BCF7F), // Green
    Color(0xFFFF6B6B), // Red
)
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/component/chart/ChartColors.kt
git commit -m "feat(stats): add chart color palette"
```
---

## Task 6: 创建环形饼图组件

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/component/chart/PieChart.kt`

**Step 1: 创建 PieChart Composable**

数据类：
```kotlin
data class PieChartData(
    val label: String,
    val value: Double,
    val color: Color
)
```

Composable 签名：
```kotlin
@Composable
fun DonutChart(
    data: List<PieChartData>,
    modifier: Modifier = Modifier,
    centerText: String = "",
    selectedIndex: Int = -1,
    onSliceClick: (Int) -> Unit = {}
)
```

实现要点：
- Canvas 绘制扇形，使用 `drawArc`，`useCenter = false`，`style = Stroke(strokeWidth)`
- 中心留空（环形），strokeWidth 约 60.dp
- 选中扇形时 strokeWidth 增大到 70.dp 表示高亮
- 中心用 `drawContext.canvas.nativeCanvas` 绘制总金额文字
- 通过 `pointerInput` 检测点击位置，计算角度确定点击的扇形索引
- 数据为空时不绘制

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/component/chart/PieChart.kt
git commit -m "feat(stats): add DonutChart canvas component"
```

---

## Task 7: 创建折线图组件

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/component/chart/LineChart.kt`

**Step 1: 创建 LineChart Composable**

数据类：
```kotlin
data class LineChartData(
    val label: String,
    val value: Double
)
```

Composable 签名：
```kotlin
@Composable
fun LineChart(
    data: List<LineChartData>,
    modifier: Modifier = Modifier,
    lineColor: Color = Pink400,
    selectedIndex: Int = -1,
    onPointClick: (Int) -> Unit = {}
)
```

实现要点：
- Canvas 绘制：X 轴标签（月份）、Y 轴刻度线（自动计算 4-5 个刻度）
- 数据点之间用 `drawLine` 连接，数据点用 `drawCircle`
- 折线下方用 `drawPath` + `Brush.verticalGradient` 填充渐变区域（Pink400 到透明）
- 选中数据点时显示 tooltip（圆角矩形背景 + 金额文字）
- 通过 `pointerInput` 检测点击，找最近的数据点
- 左侧留 40.dp 给 Y 轴刻度，底部留 24.dp 给 X 轴标签

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/component/chart/LineChart.kt
git commit -m "feat(stats): add LineChart canvas component"
```
---

## Task 8: 创建进度条组件

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/component/chart/StatsProgressBar.kt`

**Step 1: 创建 StatsProgressBar Composable**

```kotlin
@Composable
fun StatsProgressBar(
    current: Double,
    total: Double,
    label: String,
    modifier: Modifier = Modifier,
    barColor: Color = Pink400,
    backgroundColor: Color = Gray200
)
```

实现要点：
- Canvas 绘制圆角矩形背景（backgroundColor）+ 填充部分（barColor）
- 填充比例 = `(current / total).coerceIn(0.0, 1.0)`
- 使用 `animateFloatAsState` 实现填充动画
- 右侧显示百分比文字
- 上方显示 label 文字
- total 为 0 时显示空状态

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/component/chart/StatsProgressBar.kt
git commit -m "feat(stats): add StatsProgressBar canvas component"
```

---

## Task 9: 增强 StatsViewModel（总览 Tab）

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/stats/StatsScreen.kt`

**Step 1: 扩展 StatsUiState**

在现有字段基础上添加：

```kotlin
data class StatsUiState(
    // 现有字段保留
    val ownedCount: Int = 0,
    val wishedCount: Int = 0,
    val coordinateCount: Int = 0,
    val outfitLogCount: Int = 0,
    val totalSpending: Double = 0.0,
    val showTotalPrice: Boolean = false,
    val isLoading: Boolean = true,
    // 新增字段
    val averagePrice: Double = 0.0,
    val mostExpensiveItem: ItemWithSpending? = null,
    val topBrands: List<BrandItemCount> = emptyList()
)
```

**Step 2: 在 StatsViewModel.loadStats() 中添加新数据源**

在现有 `combine` 流中添加：
- `priceRepository.getMostExpensiveItem()`
- `itemRepository.getTopBrandsByCount()`
- 计算 averagePrice = totalSpending / ownedCount（ownedCount > 0 时）

**Step 3: 更新 StatsContent UI**

在现有 4 张计数卡片和消费卡片之后添加：
- 衣橱价值卡片：总价值 + 单品均价（受 showTotalPrice 控制）
- 品牌 Top5 排行：`LazyColumn` 中每行显示品牌名 + 数量 + 横向 `LinearProgressIndicator`（宽度按比例）
- 最贵单品卡片：物品名 + 缩略图（Coil AsyncImage）+ 价格

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/stats/StatsScreen.kt
git commit -m "feat(stats): enhance overview tab with value summary and brand ranking"
```
---

## Task 10: 创建消费分布 ViewModel 和 UI

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/screen/stats/SpendingDistributionScreen.kt`

**Step 1: 定义维度枚举和 UiState**

```kotlin
enum class SpendingDimension {
    BRAND, CATEGORY, STYLE, SEASON
}

data class SpendingDistributionUiState(
    val dimension: SpendingDimension = SpendingDimension.BRAND,
    val chartData: List<PieChartData> = emptyList(),
    val rankingList: List<SpendingRankItem> = emptyList(),
    val totalSpending: Double = 0.0,
    val selectedIndex: Int = -1,
    val showTotalPrice: Boolean = false,
    val isLoading: Boolean = true
)

data class SpendingRankItem(
    val name: String,
    val amount: Double,
    val percentage: Double
)
```

**Step 2: 创建 SpendingDistributionViewModel**

构造函数注入 `priceRepository` 和 `appPreferences`。

关键逻辑：
- `switchDimension(dimension)` 方法切换维度
- 根据当前维度 collect 对应 Flow：
  - BRAND → `priceRepository.getSpendingByBrand()`
  - CATEGORY → `priceRepository.getSpendingByCategory()`
  - STYLE → `priceRepository.getSpendingByStyle()`
  - SEASON → `priceRepository.getSpendingBySeasonRaw()` 然后在 ViewModel 中拆分逗号分隔的 season 字符串，将金额全额计入每个季节
- 将查询结果转换为 `PieChartData`（分配 ChartPalette 颜色）
- 最多取前 10 项，其余归入"其他"
- 计算每项占比百分比

**Step 3: 创建 SpendingDistributionContent Composable**

布局：
- 顶部：`FlowRow` 中 4 个 `FilterChip`（品牌/分类/风格/季节）
- 中部：`DonutChart`，centerText 显示总金额
- 底部：排行列表，每行显示色块圆点 + 名称 + 金额 + 占比%
- 所有金额受 `showTotalPrice` 控制，隐藏时显示占位提示

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/stats/SpendingDistributionScreen.kt
git commit -m "feat(stats): add spending distribution tab with pie chart"
```

---

## Task 11: 创建消费趋势 ViewModel 和 UI

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/screen/stats/SpendingTrendScreen.kt`

**Step 1: 定义 UiState**

```kotlin
data class SpendingTrendUiState(
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val monthlyData: List<LineChartData> = emptyList(),
    val yearTotal: Double = 0.0,
    val selectedMonthIndex: Int = -1,
    val monthlyDetails: List<MonthDetail> = emptyList(),
    val showTotalPrice: Boolean = false,
    val isLoading: Boolean = true
)

data class MonthDetail(
    val month: String,
    val amount: Double,
    val isCurrentMonth: Boolean
)
```
**Step 2: 创建 SpendingTrendViewModel**

构造函数注入 `priceRepository` 和 `appPreferences`。

关键逻辑：
- collect `priceRepository.getMonthlySpending()` 获取所有月度数据
- 按 `selectedYear` 过滤，生成 12 个月的 `LineChartData`（缺失月份补 0）
- 计算年度总消费 `yearTotal`
- `previousYear()` / `nextYear()` 切换年份并重新过滤
- `selectMonth(index)` 更新选中月份
- 生成 `monthlyDetails` 列表，标记当前月份 `isCurrentMonth`

**Step 3: 创建 SpendingTrendContent Composable**

布局：
- 顶部：年份切换器（IconButton ← + 年份文字 + IconButton →）+ 年度总消费金额
- 中部：`LineChart` 组件，高度 200.dp
- 底部：月度消费列表，`LazyColumn` 每行显示月份 + 金额，当前月份行背景高亮 Pink30
- 所有金额受 `showTotalPrice` 控制

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/stats/SpendingTrendScreen.kt
git commit -m "feat(stats): add spending trend tab with line chart"
```

---

## Task 12: 创建愿望单分析 ViewModel 和 UI

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/screen/stats/WishlistAnalysisScreen.kt`

**Step 1: 定义 UiState**

```kotlin
data class WishlistAnalysisUiState(
    val totalBudget: Double = 0.0,
    val ownedCount: Int = 0,
    val wishedCount: Int = 0,
    val fulfillmentRate: Double = 0.0,
    val priorityChartData: List<PieChartData> = emptyList(),
    val priorityDetails: List<PriorityDetail> = emptyList(),
    val selectedPriorityIndex: Int = -1,
    val showTotalPrice: Boolean = false,
    val isLoading: Boolean = true
)

data class PriorityDetail(
    val priorityLabel: String,
    val itemCount: Int,
    val budget: Double
)
```

**Step 2: 创建 WishlistAnalysisViewModel**

构造函数注入 `priceRepository`, `itemRepository`, `appPreferences`。

关键逻辑：
- combine 多个 Flow：
  - `priceRepository.getWishlistTotalBudget()` → totalBudget
  - `itemRepository.getOwnedCount()` → ownedCount
  - `itemRepository.getWishedCount()` → wishedCount
  - `priceRepository.getWishlistByPriorityStats()` → 优先级数据
- fulfillmentRate = ownedCount / (ownedCount + wishedCount)
- 将 PriorityStats 转换为 PieChartData，优先级标签映射：HIGH→"高", MEDIUM→"中", LOW→"低"
- 为每个优先级分配颜色：HIGH→Pink500, MEDIUM→Pink400, LOW→Pink200

**Step 3: 创建 WishlistAnalysisContent Composable**

布局：
- 顶部：总预算卡片（LolitaCard 包裹，显示总金额 + ShoppingCart 图标）
- 中部上：已实现进度条 `StatsProgressBar`（current=ownedCount, total=ownedCount+wishedCount, label="已实现"）
- 中部下：优先级饼图 `DonutChart`
- 底部：优先级分组列表，每行显示优先级标签 + 物品数 + 预算小计
- 金额受 `showTotalPrice` 控制

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/stats/WishlistAnalysisScreen.kt
git commit -m "feat(stats): add wishlist analysis tab with progress bar and pie chart"
```
---

## Task 13: 更新 StatsPageScreen 为 5 个 Tab

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/stats/StatsPageScreen.kt`

**Step 1: 扩展 Tab 列表**

将 `tabTitles` 从 `listOf("数据统计", "付款日历")` 改为：
```kotlin
val tabTitles = listOf("总览", "消费分布", "消费趋势", "愿望单", "付款日历")
```

**Step 2: 更新 TabRow 为 ScrollableTabRow**

由于 5 个 Tab 可能超出屏幕宽度，将 `TabRow` 替换为 `ScrollableTabRow`：
```kotlin
ScrollableTabRow(
    selectedTabIndex = pagerState.currentPage,
    edgePadding = 8.dp,
    containerColor = Color.Transparent,
    contentColor = Pink400,
    divider = {}
)
```

**Step 3: 更新 HorizontalPager 内容**

```kotlin
HorizontalPager(state = pagerState) { page ->
    when (page) {
        0 -> StatsContent()           // 总览（现有，已增强）
        1 -> SpendingDistributionContent()  // 消费分布（新）
        2 -> SpendingTrendContent()         // 消费趋势（新）
        3 -> WishlistAnalysisContent()      // 愿望单分析（新）
        4 -> PaymentCalendarContent()       // 付款日历（现有）
    }
}
```

**Step 4: 更新 pagerState pageCount**

确保 `rememberPagerState` 的 `pageCount` 从 2 改为 5：
```kotlin
val pagerState = rememberPagerState(pageCount = { 5 })
```

**Step 5: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/stats/StatsPageScreen.kt
git commit -m "feat(stats): expand stats page to 5 tabs with new analysis screens"
```

---

## Task 14: 构建验证

**Step 1: 运行 debug 构建**

```bash
./gradlew.bat assembleDebug
```

预期：BUILD SUCCESSFUL，无编译错误。

**Step 2: 修复可能的编译问题**

检查常见问题：
- import 缺失
- Room 注解处理器生成的代码是否正确（DAO 查询返回类型匹配）
- Compose 编译器版本兼容性

**Step 3: 最终 Commit**

如有修复：
```bash
git add -A
git commit -m "fix(stats): resolve build issues in stats enhancement"
```
