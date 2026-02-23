# 付款年历改为按应付款时间(dueDate) + Payment 粒度展示

日期: 2026-02-23

## 背景

当前付款年历按 `paidDate`（实际付款日期）筛选和归月，且以 Price 为粒度展示。这导致：
- 未付款的 Payment 完全不显示
- 定金尾款模式下，整个 Price 按最早 paidDate 归入单个月份

## 需求

- 按 `dueDate`（应付款时间）归入月份
- 每个 Payment 独立归月（定金和尾款可能分属不同月份）
- 金额统计用 `Payment.amount`（单笔金额）
- 包含未付款的 Payment
- 仍然只显示 OWNED/PENDING_BALANCE 状态的 item

## 方案：新增 DAO 查询 + 新数据类

### 新数据类 PaymentWithItemInfo

```kotlin
data class PaymentWithItemInfo(
    val paymentId: Long,
    val amount: Double,        // Payment.amount
    val dueDate: Long,         // Payment.dueDate
    val isPaid: Boolean,       // Payment.isPaid
    val paidDate: Long?,       // Payment.paidDate
    val priceId: Long,
    val priceType: PriceType,  // FULL or DEPOSIT_BALANCE
    val itemName: String,
    val itemId: Long
)
```

### 新 DAO 查询

```sql
SELECT pay.id AS paymentId, pay.amount, pay.due_date AS dueDate,
       pay.is_paid AS isPaid, pay.paid_date AS paidDate,
       pr.id AS priceId, pr.type AS priceType,
       i.name AS itemName, i.id AS itemId
FROM payments pay
INNER JOIN prices pr ON pay.price_id = pr.id
INNER JOIN items i ON pr.item_id = i.id
WHERE pay.due_date BETWEEN :startDate AND :endDate
  AND i.status IN ('OWNED', 'PENDING_BALANCE')
ORDER BY pay.due_date ASC
```

### ViewModel 变更

- `yearPrices: List<PriceWithStatus>` → `yearPayments: List<PaymentWithItemInfo>`
- `MonthStats` 基于 Payment 粒度计算：
  - paid: `isPaid == true` 的 amount 之和
  - unpaid: `isPaid == false` 的 amount 之和
  - overdue: `isPaid == false && dueDate < now` 的 amount 之和
- 月份归属用 `payment.dueDate` 提取年月

### UI 变更

- 月卡片统计改为 Payment 粒度
- 详情卡片改为 PaymentInfoCard：商品名、付款类型、amount、dueDate、状态
- 年度汇总改为 Payment 粒度

### 不变部分

- 只显示 OWNED/PENDING_BALANCE 的 item
- 年份导航、月卡片网格布局、皮肤组件
