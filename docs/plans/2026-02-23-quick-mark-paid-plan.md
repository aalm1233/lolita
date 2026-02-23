# 付款年历快速标记已付款 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在付款年历的 PaymentInfoCard 上添加"标记已付"按钮，点击确认后原地标记付款为已付。

**Architecture:** ViewModel 新增 PaymentRepository 和 ItemRepository 依赖，添加 markAsPaid 方法复用现有副作用链。UI 层在未付款卡片上显示按钮，点击弹出确认对话框。Room Flow 自动刷新数据。

**Tech Stack:** Kotlin, Jetpack Compose, Room, StateFlow

---

### Task 1: ViewModel 新增依赖和 markAsPaid 方法

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt:25-64`

**Step 1: 添加 import**

在现有 import 区域添加：
```kotlin
import com.lolita.app.data.repository.PaymentRepository
import com.lolita.app.data.repository.ItemRepository
```

**Step 2: 更新 ViewModel 构造函数，新增两个依赖**

```kotlin
class PaymentCalendarViewModel(
    private val priceRepository: PriceRepository = AppModule.priceRepository(),
    private val paymentRepository: PaymentRepository = AppModule.paymentRepository(),
    private val itemRepository: ItemRepository = AppModule.itemRepository()
) : ViewModel() {
```

**Step 3: 在 selectMonth() 方法之后添加 markAsPaid 方法**

```kotlin
fun markAsPaid(payment: PaymentWithItemInfo) {
    viewModelScope.launch {
        val price = priceRepository.getPriceById(payment.priceId)
        val item = price?.let { itemRepository.getItemById(it.itemId) }
        val itemName = item?.name ?: "服饰"

        val fullPayment = paymentRepository.getPaymentById(payment.paymentId)
        if (fullPayment != null && !fullPayment.isPaid) {
            paymentRepository.updatePayment(
                fullPayment.copy(isPaid = true, paidDate = System.currentTimeMillis()),
                itemName
            )
        }
    }
}
```

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt
git commit -m "feat: add markAsPaid method to PaymentCalendarViewModel"
```

---

### Task 2: UI — PaymentInfoCard 添加按钮和确认对话框

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt:166-466`

**Step 1: 添加 import**

```kotlin
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
```

**Step 2: 更新 PaymentCalendarContent 中 items 调用，传递 onMarkPaid**

将：
```kotlin
items(selectedPayments, key = { it.paymentId }) { payment ->
    PaymentInfoCard(payment = payment)
}
```
替换为：
```kotlin
items(selectedPayments, key = { it.paymentId }) { payment ->
    PaymentInfoCard(
        payment = payment,
        onMarkPaid = if (!payment.isPaid) {{ viewModel.markAsPaid(payment) }} else null
    )
}
```

**Step 3: 更新 PaymentInfoCard 签名和实现**

替换整个 `PaymentInfoCard` composable，添加 `onMarkPaid` 参数、确认对话框和按钮：

```kotlin
@Composable
private fun PaymentInfoCard(
    payment: PaymentWithItemInfo,
    onMarkPaid: (() -> Unit)? = null
) {
    val typeLabel = when (payment.priceType) {
        PriceType.DEPOSIT_BALANCE -> "定金尾款"
        PriceType.FULL -> "全款"
    }
    val now = System.currentTimeMillis()
    val isOverdue = !payment.isPaid && payment.dueDate < now
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (showConfirmDialog && onMarkPaid != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("确认付款") },
            text = {
                Text("确认将 ${payment.itemName} 的 ¥${String.format("%.2f", payment.amount)} 标记为已付款？")
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    onMarkPaid()
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("取消") }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isOverdue) CardDefaults.cardColors(
            containerColor = Color(0xFFD32F2F).copy(alpha = 0.06f)
        ) else CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    payment.itemName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = if (payment.isPaid) Color(0xFF4CAF50).copy(alpha = 0.1f)
                    else if (isOverdue) Color(0xFFD32F2F).copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        if (payment.isPaid) "已付清" else if (isOverdue) "已逾期" else "待付款",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (payment.isPaid) Color(0xFF4CAF50)
                        else if (isOverdue) Color(0xFFD32F2F)
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$typeLabel ¥${String.format("%.2f", payment.amount)}  应付: ${sdf.format(Date(payment.dueDate))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (onMarkPaid != null) {
                    TextButton(
                        onClick = { showConfirmDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("标记已付", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
```

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt
git commit -m "feat: add quick mark-as-paid button with confirmation dialog in PaymentInfoCard"
```

---

### Task 3: 构建验证

**Step 1: 运行 release 构建确认编译通过**

```bash
./gradlew.bat assembleRelease
```

Expected: BUILD SUCCESSFUL
