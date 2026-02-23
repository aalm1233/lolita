# 付款年历快速标记已付款

日期: 2026-02-23

## 背景

当前付款年历只能查看付款状态，要标记已付款需要导航到 PaymentManageScreen。用户希望在年历页面内直接快速标记。

## 需求

- 在 PaymentInfoCard 上为未付款的卡片添加"标记已付"按钮
- 点击后弹出确认对话框，防止误触
- 确认后原地标记已付款，复用现有副作用链（取消日历事件、取消提醒、自动更新 item 状态）
- 数据通过 Room Flow 自动刷新

## 设计

### ViewModel 变更

`PaymentCalendarViewModel` 新增注入 `PaymentRepository` 和 `ItemRepository`。

新增 `markAsPaid(payment: PaymentWithItemInfo)` 方法：
1. 通过 `priceRepository.getPriceById` 获取 Price
2. 通过 `itemRepository.getItemById` 获取 itemName
3. 通过 `paymentRepository.getPaymentById` 获取完整 Payment 实体
4. 调用 `paymentRepository.updatePayment(payment.copy(isPaid=true, paidDate=now), itemName)`

完整复用 `PaymentRepository.updatePayment()` 的副作用链：
- 删除日历事件
- 取消提醒（AlarmManager）
- 自动更新 item 状态（PENDING_BALANCE → OWNED）

### UI 变更

- `PaymentInfoCard` 新增 `onMarkPaid` 回调参数（仅未付款时传入）
- 未付款卡片底部显示"标记已付"按钮
- 点击弹出 `AlertDialog`：标题"确认付款"，内容"确认将 {itemName} 的 ¥{amount} 标记为已付款？"
- 确认后调用 `viewModel.markAsPaid(payment)`
- 已付款卡片不显示按钮

### 数据刷新

Room Flow 自动推送更新，无需手动 reload。

### 不变部分

- 月卡片网格、年度汇总、导航逻辑
- 副作用处理完全复用现有 Repository 层
- 皮肤组件
