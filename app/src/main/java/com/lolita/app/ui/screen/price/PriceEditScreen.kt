package com.lolita.app.ui.screen.price

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.PriceType
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.theme.Pink400
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceEditScreen(
    itemId: Long,
    priceId: Long?,
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    viewModel: PriceEditViewModel = viewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(priceId) {
        viewModel.loadPrice(priceId)
    }

    uiState.error?.let { errorMsg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("保存失败") },
            text = { Text(errorMsg) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("确定")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            GradientTopAppBar(
                title = { Text(if (priceId == null) "添加价格" else "编辑价格") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                val result = if (priceId == null) {
                                    viewModel.save(itemId)
                                } else {
                                    viewModel.update(priceId)
                                }
                                result.onSuccess {
                                    onSaveSuccess()
                                }
                            }
                        },
                        enabled = viewModel.isValid() && !uiState.isSaving
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Check, contentDescription = "保存")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 价格类型选择
            Text(
                "价格类型",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PriceTypeOption(
                    text = "全价",
                    selected = uiState.priceType == PriceType.FULL,
                    onClick = { viewModel.updatePriceType(PriceType.FULL) },
                    modifier = Modifier.weight(1f)
                )
                PriceTypeOption(
                    text = "定金+尾款",
                    selected = uiState.priceType == PriceType.DEPOSIT_BALANCE,
                    onClick = { viewModel.updatePriceType(PriceType.DEPOSIT_BALANCE) },
                    modifier = Modifier.weight(1f)
                )
            }

            // 根据类型显示不同输入框
            when (uiState.priceType) {
                PriceType.FULL -> {
                    OutlinedTextField(
                        value = uiState.totalPrice,
                        onValueChange = { viewModel.updateTotalPrice(it) },
                        label = { Text("总价") },
                        placeholder = { Text("0.00") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        ),
                        singleLine = true,
                        prefix = { Text("¥") },
                        enabled = !uiState.isSaving
                    )
                }
                PriceType.DEPOSIT_BALANCE -> {
                    OutlinedTextField(
                        value = uiState.totalPrice,
                        onValueChange = { viewModel.updateTotalPrice(it) },
                        label = { Text("总价") },
                        placeholder = { Text("0.00") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        ),
                        singleLine = true,
                        prefix = { Text("¥") },
                        enabled = !uiState.isSaving
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.deposit,
                            onValueChange = { viewModel.updateDeposit(it) },
                            label = { Text("定金") },
                            placeholder = { Text("0.00") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal
                            ),
                            singleLine = true,
                            prefix = { Text("¥") },
                            enabled = !uiState.isSaving
                        )

                        OutlinedTextField(
                            value = uiState.balance,
                            onValueChange = { viewModel.updateBalance(it) },
                            label = { Text("尾款") },
                            placeholder = { Text("0.00") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal
                            ),
                            singleLine = true,
                            prefix = { Text("¥") },
                            enabled = !uiState.isSaving
                        )
                    }

                    // 计算提示
                    val total = uiState.totalPrice.toDoubleOrNull() ?: 0.0
                    val deposit = uiState.deposit.toDoubleOrNull() ?: 0.0
                    val balance = uiState.balance.toDoubleOrNull() ?: 0.0
                    val sum = deposit + balance

                    if (total > 0 && sum != total) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                "定金 + 尾款 (¥${String.format("%.2f", sum)}) 应等于总价 (¥${String.format("%.2f", total)})",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // 购买日期选择
            var showDatePicker by remember { mutableStateOf(false) }
            val dateFormat = remember { SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()) }

            Box(modifier = Modifier.fillMaxWidth().clickable(enabled = !uiState.isSaving) { showDatePicker = true }) {
                OutlinedTextField(
                    value = uiState.purchaseDate?.let { dateFormat.format(Date(it)) } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("购买日期 (可选)") },
                    placeholder = { Text("点击选择日期") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = "选择日期")
                        }
                    },
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            if (showDatePicker) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = uiState.purchaseDate ?: System.currentTimeMillis()
                )
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let {
                                viewModel.updatePurchaseDate(it)
                            }
                            showDatePicker = false
                        }) {
                            Text("确定")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            viewModel.updatePurchaseDate(null)
                            showDatePicker = false
                        }) {
                            Text("清除")
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }
        }
    }
}

@Composable
private fun PriceTypeOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        modifier = modifier
    )
}
