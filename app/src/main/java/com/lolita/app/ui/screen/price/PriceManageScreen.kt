package com.lolita.app.ui.screen.price

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.dao.PriceWithPayments
import com.lolita.app.data.local.entity.PriceType
import com.lolita.app.ui.screen.common.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceManageScreen(
    itemId: Long,
    onBack: () -> Unit,
    onNavigateToPriceEdit: (Long?) -> Unit,
    onNavigateToPaymentManage: (Long) -> Unit,
    viewModel: PriceManageViewModel = viewModel(
        factory = PriceManageViewModelFactory(itemId)
    )
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("价格管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToPriceEdit(null) }) {
                Icon(Icons.Default.Add, contentDescription = "添加价格")
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
            if (uiState.prices.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.ShoppingCart,
                        title = "暂无价格信息",
                        subtitle = "点击 + 添加价格"
                    )
                }
            } else {
                items(uiState.prices) { priceWithPayments ->
                    PriceCard(
                        priceWithPayments = priceWithPayments,
                        onClick = { onNavigateToPaymentManage(priceWithPayments.price.id) },
                        onEdit = { onNavigateToPriceEdit(priceWithPayments.price.id) },
                        onDelete = { viewModel.deletePrice(priceWithPayments.price) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PriceCard(
    priceWithPayments: PriceWithPayments,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val price = priceWithPayments.price
    val payments = priceWithPayments.payments
    val paidAmount = payments.filter { it.isPaid }.sumOf { it.amount }
    val unpaidAmount = payments.filter { !it.isPaid }.sumOf { it.amount }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    when (price.type) {
                        PriceType.FULL -> "全价"
                        PriceType.DEPOSIT_BALANCE -> "定金+尾款"
                    },
                    style = MaterialTheme.typography.titleMedium
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "编辑")
                    }
                    IconButton(
                        onClick = onDelete,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                }
            }

            HorizontalDivider()

            when (price.type) {
                PriceType.FULL -> {
                    PriceRow("总价", "¥${String.format("%.2f", price.totalPrice)}")
                }
                PriceType.DEPOSIT_BALANCE -> {
                    PriceRow("总价", "¥${String.format("%.2f", price.totalPrice)}")
                    PriceRow("定金", "¥${String.format("%.2f", price.deposit ?: 0.0)}")
                    PriceRow("尾款", "¥${String.format("%.2f", price.balance ?: 0.0)}")
                }
            }

            if (payments.isNotEmpty()) {
                HorizontalDivider()
                PriceRow(
                    "已付款",
                    "¥${String.format("%.2f", paidAmount)}",
                    MaterialTheme.colorScheme.primary
                )
                PriceRow(
                    "未付款",
                    "¥${String.format("%.2f", unpaidAmount)}",
                    if (unpaidAmount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }

            if (payments.isNotEmpty()) {
                Button(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("查看付款详情 (${payments.size})")
                }
            }
        }
    }
}

@Composable
private fun PriceRow(label: String, value: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

class PriceManageViewModelFactory(private val itemId: Long) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return PriceManageViewModel(
            priceRepository = com.lolita.app.di.AppModule.priceRepository(),
            itemId = itemId
        ) as T
    }
}
