# 统计页数据分析增强设计

## 概述

扩展现有统计页（StatsPageScreen），从 2 个 Tab 增加到 5 个 Tab，新增消费分布分析、消费趋势图表、愿望单预算分析功能。图表使用 Compose Canvas 自定义绘制，与 app 粉色主题统一。

## Tab 结构

| Tab | 名称 | 内容 |
|-----|------|------|
| 0 | 总览 | 现有计数卡片 + 衣橱总价值、单品均价、最贵单品、品牌 Top5 数量排行 |
| 1 | 消费分布 | 环形饼图，可切换维度：品牌/分类/风格/季节，展示各维度消费金额占比 |
| 2 | 消费趋势 | 折线图，按月展示消费金额，支持年份切换，显示年度总消费 |
| 3 | 愿望单 | 愿望单总预算、按优先级饼图分布、已实现比例进度条 |
| 4 | 付款日历 | 现有付款日历，不变 |

## 自定义 Canvas 图表组件

### 1. 环形饼图 PieChart
- 输入：`List<PieChartData(label: String, value: Double, color: Color)>`
- 绘制：Canvas 扇形，中心留空（环形），中心显示总金额
- 交互：点击扇形高亮，下方显示该分类详细列表（名称 + 金额 + 占比%）
- 配色：从 Pink400 出发的渐变色系（粉、玫红、紫粉、浅粉、桃红等）
- 用于：消费分布 Tab、愿望单优先级分布

### 2. 折线图 LineChart

- 输入：`List<LineChartData(label: String, value: Double)>`（label 为月份）
- 绘制：Canvas 折线 + 数据点圆点 + 下方填充渐变区域
- X 轴：1-12 月，Y 轴：自动计算刻度
- 交互：点击数据点显示该月具体金额 tooltip
- 顶部年份切换器（← 年份 →）+ 年度总消费
- 用于：消费趋势 Tab

### 3. 进度条 ProgressBar

- 输入：`current: Double, total: Double, label: String`
- 绘制：圆角矩形背景 + 粉色填充 + 百分比文字
- 用于：愿望单已实现比例

所有图表在数据为空时显示 EmptyState 占位。

## 各 Tab 详细 UI 布局
### Tab 0 — 总览

- 顶部：2x2 网格卡片（已拥有数量、愿望单数量、套装数量、穿搭记录数量）— 保留现有
- 中部：衣橱价值卡片（总价值 + 单品均价，受 showTotalPrice 开关控制）
- 底部：品牌 Top5 排行列表（品牌名 + 数量 + 横向条形指示器），最贵单品卡片（名称 + 图片缩略 + 价格）

### Tab 1 — 消费分布

- 顶部：维度切换 FilterChip 行（品牌 / 分类 / 风格 / 季节）
- 中部：环形饼图，中心显示当前维度总消费
- 底部：排行列表（名称 + 金额 + 占比%），按金额降序，最多 10 项，其余归入"其他"
- 消费金额受 showTotalPrice 开关控制

### Tab 2 — 消费趋势

- 顶部：年份切换器（← 2024 →）+ 年度总消费金额
- 中部：折线图（12 个月）
- 底部：月度消费列表（月份 + 金额），高亮当前月
- 受 showTotalPrice 开关控制

### Tab 3 — 愿望单分析

- 顶部：总预算卡片（愿望单所有物品的 Price 总和）
- 中部：已实现进度条（已转为 OWNED 的比例）+ 优先级饼图（HIGH/MEDIUM/LOW 数量分布）
- 底部：按优先级分组的愿望单物品列表（优先级标签 + 物品数 + 预算小计）

### Tab 4 — 付款日历

现有实现不变。

## 新增 DAO 查询
需要在 PriceDao / ItemDao 中新增以下查询：

- 按品牌分组的消费总额（JOIN Item + Price + Brand，WHERE status='OWNED'）
- 按分类分组的消费总额（JOIN Item + Price + Category，WHERE status='OWNED'）
- 按风格分组的消费总额（JOIN Item + Price，WHERE status='OWNED'，style 为字符串字段直接分组）
- 按季节分组的消费总额（JOIN Item + Price，WHERE status='OWNED'，season 为逗号分隔字符串需特殊处理）
- 按月分组的消费总额（按 Price.purchase_date 分组，WHERE Item.status='OWNED'）
- 最贵单品查询（JOIN Item + Price，WHERE status='OWNED'，ORDER BY totalPrice DESC LIMIT 1）
- 单品均价（总消费 / OWNED 数量）
- 品牌 Top5 数量排行（GROUP BY brandId，COUNT，ORDER BY count DESC LIMIT 5）
- 愿望单总预算（JOIN Item + Price，WHERE status='WISHED'，SUM totalPrice）
- 按优先级分组的愿望单数量和金额（GROUP BY priority，WHERE status='WISHED'）
- 愿望单已实现数量（历史上从 WISHED 转为 OWNED 的数量 — 简化为当前 OWNED 中有 Price 的数量与 WISHED 数量的比例）

## 季节字段处理

season 字段是逗号分隔字符串（如 "春,夏"），按季节分组消费时需要在应用层拆分处理：
1. DAO 查询返回 Item + Price + season 原始数据
2. ViewModel 中拆分 season 字符串，将金额按比例分配到各季节（或全额计入每个季节）

## 技术约束

- 所有金额相关展示受 AppPreferences.showTotalPrice 开关控制
- 图表配色使用 app 现有 Pink 主题色系
- 新增查询均需返回 Flow 以支持响应式更新
- Canvas 图表组件作为独立 Composable，放在 ui/component/chart/ 目录下
- 新增的 StatsViewModel 状态扩展或拆分为多个 ViewModel（每个 Tab 一个）以保持职责清晰
