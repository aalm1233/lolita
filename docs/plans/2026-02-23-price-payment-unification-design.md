# Price/Payment 数据统一设计

## 问题

两条数据录入路径不一致：
- 淘宝导入普通商品：创建 Price（带 purchaseDate），不创建 Payment
- 手动添加价格：创建 Price + Payment，purchaseDate 仅 OWNED 状态可填

## 决策

1. 所有路径都创建 Payment 记录
2. 删除 Price.purchaseDate，统一用 Payment.paidDate 表示购买时间
3. 自动迁移已有数据（为孤立 Price 补建 Payment）
4. 统计查询从 Price.purchaseDate 改为 Payment.paidDate
5. 手动添加价格时所有状态都显示付款日期字段

## 数据库迁移 (v6→v7)

### Step 1：补建 Payment

为没有 Payment 的 Price 补建已付款记录：

```sql
-- purchaseDate 不为空的
INSERT INTO payments (price_id, amount, due_date, is_paid, paid_date, reminder_set, created_at)
SELECT p.id, p.total_price, p.purchase_date, 1, p.purchase_date, 0, p.created_at
FROM prices p
WHERE p.id NOT IN (SELECT price_id FROM payments)
  AND p.purchase_date IS NOT NULL;

-- purchaseDate 为空的，用 createdAt 兜底
INSERT INTO payments (price_id, amount, due_date, is_paid, paid_date, reminder_set, created_at)
SELECT p.id, p.total_price, p.created_at, 1, p.created_at, 0, p.created_at
FROM prices p
WHERE p.id NOT IN (SELECT price_id FROM payments)
  AND p.purchase_date IS NULL;
```

### Step 2：重建 Price 表

```sql
CREATE TABLE prices_new (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  item_id INTEGER NOT NULL,
  type TEXT NOT NULL,
  total_price REAL NOT NULL,
  deposit REAL,
  balance REAL,
  created_at INTEGER NOT NULL,
  FOREIGN KEY(item_id) REFERENCES items(id) ON DELETE CASCADE
);
INSERT INTO prices_new SELECT id, item_id, type, total_price, deposit, balance, created_at FROM prices;
DROP TABLE prices;
ALTER TABLE prices_new RENAME TO prices;
CREATE INDEX IF NOT EXISTS index_prices_item_id ON prices(item_id);
```

### Step 3：Price 实体删除 purchaseDate 字段

## DAO 查询改写

### getMonthlySpending()

```sql
SELECT strftime('%Y-%m', pay.paid_date / 1000, 'unixepoch') AS yearMonth,
       COALESCE(SUM(pay.amount), 0.0) AS totalSpending
FROM payments pay
INNER JOIN prices pr ON pay.price_id = pr.id
INNER JOIN items i ON pr.item_id = i.id
WHERE i.status IN ('OWNED', 'PENDING_BALANCE')
  AND pay.is_paid = 1 AND pay.paid_date IS NOT NULL
GROUP BY yearMonth
ORDER BY yearMonth ASC
```

### getItemsByPurchaseMonth()

```sql
SELECT DISTINCT i.* FROM items i
INNER JOIN prices pr ON pr.item_id = i.id
INNER JOIN payments pay ON pay.price_id = pr.id
WHERE i.status IN ('OWNED', 'PENDING_BALANCE')
  AND pay.is_paid = 1 AND pay.paid_date IS NOT NULL
  AND strftime('%Y-%m', pay.paid_date / 1000, 'unixepoch') = :yearMonth
ORDER BY i.updated_at DESC
```

### getPricesWithStatusByDateRange()

PriceWithStatus 数据类调整：`purchaseDate` → `firstPaidDate`

```sql
SELECT pr.id AS priceId, pr.total_price AS totalPrice,
       MIN(pay.paid_date) AS firstPaidDate,
       pr.type AS priceType, i.name AS itemName, i.id AS itemId,
       (SELECT COUNT(*) FROM payments p WHERE p.price_id = pr.id AND p.is_paid = 0) AS unpaidCount,
       (SELECT COUNT(*) FROM payments p WHERE p.price_id = pr.id AND p.is_paid = 0 AND p.due_date < :now) AS overdueCount
FROM prices pr
INNER JOIN items i ON pr.item_id = i.id
INNER JOIN payments pay ON pay.price_id = pr.id
WHERE pay.is_paid = 1
  AND pay.paid_date BETWEEN :startDate AND :endDate
  AND i.status IN ('OWNED', 'PENDING_BALANCE')
GROUP BY pr.id
ORDER BY firstPaidDate ASC
```

## ViewModel / UI 改动

### PriceViewModel
- 删除 `purchaseDate` 字段和 `updatePurchaseDate()`
- 新增 `paymentDate: Long?`（统一付款时间）
- save() 时：FULL → 1 条 Payment；DEPOSIT_BALANCE → 2 条 Payment
- OWNED 状态的 Payment 自动标记 `isPaid=true, paidDate=paymentDate`
- 非 OWNED 状态的 Payment 保持 `isPaid=false`

### PriceEditScreen
- 「购买日期」→「付款日期」，所有状态都显示
- DEPOSIT_BALANCE 显示定金日期 + 尾款日期

### PriceManageScreen / ItemDetailScreen
- 展示 Payment.paidDate 替代 Price.purchaseDate

### PaymentCalendarScreen
- `p.purchaseDate` → `p.firstPaidDate`

## 导入改动

### TaobaoImportViewModel
- Scenario C（普通商品）：新增创建 Payment（isPaid=true, paidDate=orderTime）
- Scenario A/B：不变（已有 Payment 创建逻辑）
- Price 构造不再传 purchaseDate

### ImportDetailScreen
- 「购买日期」标签改为「付款日期」，值写入 Payment.paidDate

## 备份改动

### BackupManager
- CSV：Price header 去掉 purchase_date 列
- JSON：Price 序列化去掉 purchaseDate
- 导入兼容：旧备份中的 purchaseDate 字段忽略

## 影响文件清单

| 文件 | 改动类型 |
|------|----------|
| Price.kt | 删除 purchaseDate 字段 |
| PriceDao.kt | 改写 3 个查询 + PriceWithStatus 数据类 |
| LolitaDatabase.kt | 新增 Migration v6→v7 |
| PriceRepository.kt | 可能需要适配新查询签名 |
| PriceViewModel.kt | purchaseDate → paymentDate |
| PriceEditScreen.kt | UI 字段替换 |
| PriceManageScreen.kt | 展示改为 Payment.paidDate |
| ItemDetailScreen.kt | 展示改为 Payment.paidDate |
| PaymentCalendarScreen.kt | purchaseDate → firstPaidDate |
| TaobaoImportViewModel.kt | Scenario C 补建 Payment |
| ImportDetailScreen.kt | 标签文案调整 |
| BackupManager.kt | CSV/JSON 去掉 purchaseDate |
