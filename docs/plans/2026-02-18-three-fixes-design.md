# 三项功能修正设计文档

## 概述

修正三个功能问题：
1. 淘宝订单导入时，定金无尾款支持手动输入尾款
2. 套装列表价格显示与设置联动
3. 型号款式解析算法优化（类型和颜色匹配）

---

## Issue 1: 定金导入支持手动输入尾款

### 当前行为
未配对的 DEPOSIT 项以 FULL 类型导入，丢失定金/尾款结构。

### 目标行为
在 DETAIL 步骤为未配对的定金订单提供尾款金额和计划付款时间输入，导入时创建 DEPOSIT_BALANCE 价格 + 两条 Payment 记录（定金已付、尾款未付）。

### 改动点

1. **ImportItemState** 新增字段：
   - `manualBalance: Double?` — 手动输入的尾款金额
   - `balanceDueDate: Long?` — 尾款计划付款日期

2. **ImportDetailScreen**：当 `paymentRole == DEPOSIT && pairedWith == null` 时显示：
   - 尾款金额输入框（必填）
   - 计划付款日期选择器（必填）

3. **executeImport()**：未配对 DEPOSIT 项的导入逻辑：
   - 创建 `Price(type=DEPOSIT_BALANCE, deposit=item.price, balance=manualBalance, totalPrice=deposit+balance)`
   - 创建 Payment 1: `Payment(amount=deposit, isPaid=true, paidDate=purchaseDate)`
   - 创建 Payment 2: `Payment(amount=balance, isPaid=false, dueDate=balanceDueDate)`

4. **验证**：未配对 DEPOSIT 项必须 `manualBalance > 0` 且 `balanceDueDate != null` 才能导入

### 涉及文件
- `TaobaoImportViewModel.kt` — ImportItemState、executeImport()
- `ImportDetailScreen.kt` — UI 输入字段

---

## Issue 2: 套装列表价格显示与设置联动

### 当前行为
CoordinateListScreen 始终显示价格，不受设置中「显示总价」开关控制。

### 目标行为
套装列表卡片价格受 `showTotalPrice` 设置控制；详情页不受影响，始终显示。

### 改动点

1. **CoordinateListViewModel**：读取 `AppPreferences.showTotalPrice`，存入 UiState
2. **CoordinateCard / CoordinateGridCard**：价格显示加 `if (showPrice)` 条件

### 涉及文件
- `CoordinateViewModel.kt` — 读取偏好
- `CoordinateListScreen.kt` — 条件显示

---

## Issue 3: 型号款式解析算法优化

### 当前问题
类型匹配失败率 79%，颜色问题率 39%。根因：按 `;/` 分割后对 part 做整体精确匹配，但实际数据中 type/color/size 经常混在同一个 part 里。

### 三阶段处理方案

#### 阶段 1: 增强噪声清理

在现有基础上增加：
- 支付/批次：`尾款`、`定金`、`现货`、`预约`、`全款`、`一批`~`七批`、`一团`~`二团`
- 括号噪声：`{尾款}`、`{定金}`、`（尾款)`、`（定金）`、`（不单售）`、`<慢团>`
- 点缀：`·尾款`、`·定金`、`本体`、`正常长度`
- 备注类：`自行备注尺码`、`尺码备注`、`单品尺码请备注`
- 日期时间模式

#### 阶段 2: 多级分割

1. 先按 `;/` 分割（主分隔符）
2. 对每个 part 再按空格和 `--` 分割（次分隔符）
3. 不按 `+` 分割（"KC+发包"、"OP+吊坠托领+手袖 SET" 是复合类型）

#### 阶段 3: 智能分类

对每个子 part，按优先级尝试：

1. **纯尺寸**：完整匹配 size pattern → size
2. **精确类型**：完整匹配 typeKeywords → type
3. **后缀类型**：以 OP/SK/JSK/SET/FS 结尾 → type，前缀检查颜色
4. **包含类型（新增）**：part 含 typeKeyword → 提取 type，剩余检查颜色（长关键词优先）
5. **前缀尺寸**：以尺寸开头 + 其他内容 → 提取 size，剩余重新分类
6. **颜色匹配**：含 colorKeyword → color
7. **兜底**：归为 color

#### 关键词补充

类型新增：`长裙`、`连衣裙`、`袖子`、`胸衣`、`蝙蝠胸衣`

颜色新增：
- 双色组合：`黑白`、`蓝黑`、`黑x红`、`黑x青`、`白×蓝`、`灰黑`、`绿金`、`粉绿`、`黑粉`
- 不带"色"后缀：`白金`、`深海`
- 系列色名：`织金`、`白玫瑰`、`黑玫瑰`、`黑夕`、`白昼`、`蓝暮`、`紫夜`、`白金`

### 涉及文件
- `TaobaoOrderParser.kt` — parseStyleSpec()、关键词列表、分类逻辑
