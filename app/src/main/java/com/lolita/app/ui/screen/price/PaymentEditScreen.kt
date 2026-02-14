package com.lolita.app.ui.screen.price

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.notification.CalendarEventHelper
import com.lolita.app.ui.screen.common.GradientTopAppBar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentEditScreen(
    priceId: Long,
    paymentId: Long?,
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    viewModel: PaymentEditViewModel = viewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Save action extracted so it can be called after permission result
    val performSave: () -> Unit = {
        coroutineScope.launch {
            val result = if (paymentId == null) {
                viewModel.save(priceId)
            } else {
                viewModel.update(paymentId)
            }
            result.onSuccess {
                onSaveSuccess()
            }
        }
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Proceed with save regardless of permission result
        performSave()
    }

    LaunchedEffect(paymentId) {
        viewModel.loadPayment(paymentId)
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

    if (showDatePicker) {
        AndroidDatePickerDialog(
            onDateSelected = { date ->
                if (date != null) {
                    viewModel.updateDueDate(date)
                }
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
            initialDate = uiState.dueDate?.let { Date(it) } ?: Date()
        )
    }

    Scaffold(
        topBar = {
            GradientTopAppBar(
                title = { Text(if (paymentId == null) "添加付款记录" else "编辑付款记录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (CalendarEventHelper.hasCalendarPermission(context)) {
                                performSave()
                            } else {
                                calendarPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_CALENDAR,
                                        Manifest.permission.WRITE_CALENDAR
                                    )
                                )
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
            OutlinedTextField(
                value = uiState.amount,
                onValueChange = { viewModel.updateAmount(it) },
                label = { Text("付款金额") },
                placeholder = { Text("0.00") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                prefix = { Text("¥") },
                enabled = !uiState.isSaving
            )

            OutlinedTextField(
                value = uiState.dueDate?.let { formatDate(it) } ?: "",
                onValueChange = {},
                label = { Text("应付款时间") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "选择日期")
                    }
                },
                enabled = !uiState.isSaving
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("提醒设置", style = MaterialTheme.typography.titleMedium)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("设置提醒")
                        Switch(
                            checked = uiState.reminderSet,
                            onCheckedChange = { viewModel.updateReminderSet(it) }
                        )
                    }

                    if (uiState.reminderSet) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("提前天数", style = MaterialTheme.typography.bodyMedium)

                            OutlinedTextField(
                                value = uiState.customReminderDays,
                                onValueChange = { viewModel.updateCustomReminderDays(it) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                suffix = { Text("天") },
                                placeholder = { Text("1") },
                                enabled = !uiState.isSaving
                            )

                            Text(
                                "将在应付款日期前发送提醒通知",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun AndroidDatePickerDialog(
    onDateSelected: (Long?) -> Unit,
    onDismiss: () -> Unit,
    initialDate: Date
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val calendar = Calendar.getInstance()
    calendar.time = initialDate

    DisposableEffect(Unit) {
        val dialog = android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val cal = Calendar.getInstance()
                cal.set(year, month, dayOfMonth)
                onDateSelected(cal.timeInMillis)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setOnCancelListener { onDismiss() }
            setOnDismissListener { onDismiss() }
        }
        dialog.show()
        onDispose { dialog.dismiss() }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
