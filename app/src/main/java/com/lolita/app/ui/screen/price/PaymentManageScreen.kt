package com.lolita.app.ui.screen.price

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.Payment
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentManageScreen(
    priceId: Long,
    onBack: () -> Unit,
    onNavigateToPaymentEdit: (Long?) -> Unit,
    viewModel: PaymentManageViewModel = viewModel(
        factory = PaymentManageViewModelFactory(priceId)
    )
) {
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("付款管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToPaymentEdit(null) }) {
                Icon(Icons.Default.Add, contentDescription = "添加付款记录")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 统计卡片
            item {
                PaymentStatsCard(
                    totalPrice = uiState.totalPrice,
                    paidAmount = uiState.paidAmount,
                    unpaidAmount = uiState.unpaidAmount
                )
            }

            // 付款记录列表
            item {
                Text(
                    "付款记录",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (uiState.payments.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                        ) {
                            Text(
                                "暂无付款记录",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(uiState.payments) { payment ->
                    PaymentCard(
                        payment = payment,
                        onMarkPaid = {
                            coroutineScope.launch {
                                viewModel.markAsPaid(payment)
                            }
                        },
                        onDelete = {
                            coroutineScope.launch {
                                viewModel.deletePayment(payment)
                            }
                        },
                        onClick = { onNavigateToPaymentEdit(payment.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PaymentStatsCard(
    totalPrice: Double,
    paidAmount: Double,
    unpaidAmount: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "价格统计",
                style = MaterialTheme.typography.titleMedium
            )

            Divider()

            StatRow("总价", "¥${String.format("%.2f", totalPrice)}")
            StatRow("已付款", "¥${String.format("%.2f", paidAmount)}", MaterialTheme.colorScheme.primary)
            StatRow("未付款", "¥${String.format("%.2f", unpaidAmount)}", MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun PaymentCard(
    payment: Payment,
    onMarkPaid: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "¥${String.format("%.2f", payment.amount)}",
                    style = MaterialTheme.typography.titleLarge
                )

                PaymentStatusChip(payment.isPaid)
            }

            Text(
                "应付款时间: ${formatDate(payment.dueDate)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (payment.reminderSet && !payment.isPaid) {
                Text(
                    "提醒已设置",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (!payment.isPaid) {
                    TextButton(onClick = onMarkPaid) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("标记已付款")
                    }
                }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除")
                }
            }
        }
    }
}

@Composable
private fun PaymentStatusChip(isPaid: Boolean) {
    Surface(
        color = if (isPaid) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = if (isPaid) "已付款" else "未付款",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = if (isPaid) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            }
        )
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

class PaymentManageViewModelFactory(private val priceId: Long) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return PaymentManageViewModel(
            paymentRepository = com.lolita.app.di.AppModule.paymentRepository(),
            priceRepository = com.lolita.app.di.AppModule.priceRepository(),
            itemRepository = com.lolita.app.di.AppModule.itemRepository(),
            priceId = priceId
        ) as T
    }
}
