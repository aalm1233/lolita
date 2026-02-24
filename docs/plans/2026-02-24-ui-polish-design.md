# UI 优化设计：底部栏 + 年历 + 位置列表

日期：2026-02-24

## 1. 底部栏图标间距修复

**问题：** 底部导航栏图标部分被遮挡，52dp 高度下图标和文字间距过大。

**方案：** 将 label 的 `offset(y = (-2).dp)` 改为 `offset(y = (-4).dp)`，缩小图标与文字间距，给图标上方腾出空间。

**文件：** `LolitaNavHost.kt` 第 135 行

## 2. 年历月份固定高度

**问题：** `MonthCard` 无固定高度，有数据月份显示多行内容，无数据月份只有月份名，导致卡片高度参差不齐。

**方案：** 给 `MonthCard` 的 `Card` 添加 `heightIn(min = 100.dp)`，所有月份卡片保持一致最小高度。

**文件：** `PaymentCalendarScreen.kt` 第 378 行 MonthCard composable

## 3. 位置列表优化

**问题：** 位置卡片内容单薄，只有图片/占位图标 + 名称 + 描述 + 件数。

**方案：**

### 数据层
- `ItemDao` 添加查询：获取每个位置下前 4 件服饰的 imageUrl
- `LocationRepository` 添加方法暴露该查询
- `ItemListViewModel` 维护 `Map<Long, List<String>>` (locationId → imageUrls)

### UI 层 (LocationListContent.kt)
- 位置图片从 80dp 缩小到 56dp
- 名称/描述/件数下方增加服饰缩略图预览行（最多 4 张，36dp 圆角小图，重叠排列）
- 件数用 SurfaceVariant 背景标签样式展示
- 无服饰位置显示"暂无服饰"占位文字
- `LocationListContent` 参数增加 `locationItemImages: Map<Long, List<String>>`
