# 付款日历增强设计

日期: 2026-02-22

## 需求概述

1. 将「付款日历」Tab 从第5位移到第2位（总览之后）
2. 日历格子内显示当天已付/待付金额（替代彩色圆点）
3. 统计行新增「总待付尾款」卡片（4卡片单行排列）

## 变更文件

- `StatsPageScreen.kt` — Tab 顺序调整
- `PaymentCalendarScreen.kt` — 日历格子增强 + 统计行扩展 + ViewModel 新增字段
- `PaymentDao.kt` — 新增总待付笔数查询

## 设计详情

### 1. Tab 顺序调整

当前: 总览 → 消费分布 → 消费趋势 → 愿望单 → 付款日历
新: 总览 → 付款日历 → 消费分布 → 消费趋势 → 愿望单

修改 `StatsPageScreen.kt` 中 `tabs` 列表和 `when` 分支。

### 2. 统计行扩展

从 3 卡片改为 4 卡片，单行横向排列（每个 `weight(1f)`）。

| 卡片 | 数据源 | 颜色 |
|------|--------|------|
| 当月已付 | monthPaidTotal + monthPaidCount | #4CAF50 绿色 |
| 当月待付 | monthUnpaidTotal + monthUnpaidCount | primary |
| 总待付尾款 | totalUnpaidAmount + totalUnpaidCount(新增) | #FF9800 橙色 |
| 已逾期 | overdueAmount | #D32F2F 红色 |

ViewModel 新增 `totalUnpaidCount: Int` 字段。
DAO 新增 `getTotalUnpaidCount(): Flow<Int>` 查询。

### 3. 日历格子增强

替换 `DayCell` 中的彩色圆点为金额文字：

- 已付金额: 绿色 9.sp，格式 `¥120`
- 待付金额: 红色/逾期色 9.sp，格式 `¥200`
- 无付款日期只显示数字，下方留空
- 格子 padding 从 `vertical = 6.dp` 增大到 `8.dp`

新增数据结构:
```kotlin
data class DayAmountInfo(
    val paidTotal: Double = 0.0,
    val unpaidTotal: Double = 0.0
)
```

新增辅助函数 `buildDayAmountMap()` 返回 `Map<Int, DayAmountInfo>`。

选中日期高亮和点击展开详情逻辑保持不变。
