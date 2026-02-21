package com.lolita.app.ui.screen.price

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.lolita.app.ui.screen.common.UnsavedChangesHandler
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon

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
    var showExactAlarmDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Actual save logic — only called once
    val doSave: () -> Unit = {
        // Check exact alarm permission when reminder is enabled (non-blocking info)
        if (uiState.reminderSet && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                showExactAlarmDialog = true
            }
        }
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
        // Proceed with save after permission result (only save entry point from permission flow)
        doSave()
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

    if (showExactAlarmDialog) {
        AlertDialog(
            onDismissRequest = { showExactAlarmDialog = false },
            title = { Text("精确提醒权限") },
            text = { Text("当前未授予精确闹钟权限，提醒时间可能不准确。是否前往设置开启？") },
            confirmButton = {
                TextButton(onClick = {
                    showExactAlarmDialog = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    }
                }) { Text("去设置") }
            },
            dismissButton = {
                TextButton(onClick = { showExactAlarmDialog = false }) { Text("暂不") }
            }
        )
    }

    UnsavedChangesHandler(
        hasUnsavedChanges = viewModel.hasUnsavedChanges,
        onBack = onBack
    )

    Scaffold(
        topBar = {
            GradientTopAppBar(
                title = { Text(if (paymentId == null) "添加付款记录" else "编辑付款记录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        SkinIcon(IconKey.ArrowBack)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val permissionsNeeded = mutableListOf<String>()
                            if (!CalendarEventHelper.hasCalendarPermission(context)) {
                                permissionsNeeded.add(Manifest.permission.READ_CALENDAR)
                                permissionsNeeded.add(Manifest.permission.WRITE_CALENDAR)
                            }
                            if (uiState.reminderSet && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val notifPerm = Manifest.permission.POST_NOTIFICATIONS
                                if (context.checkSelfPermission(notifPerm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                    permissionsNeeded.add(notifPerm)
                                }
                            }
                            if (permissionsNeeded.isEmpty()) {
                                doSave()
                            } else {
                                // Save will be triggered in calendarPermissionLauncher callback
                                calendarPermissionLauncher.launch(permissionsNeeded.toTypedArray())
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
                            SkinIcon(IconKey.Save)
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

            Box(modifier = Modifier.fillMaxWidth().clickable(enabled = !uiState.isSaving) { showDatePicker = true }) {
                OutlinedTextField(
                    value = uiState.dueDate?.let { formatDate(it) } ?: "",
                    onValueChange = {},
                    label = { Text("应付款时间") },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            SkinIcon(IconKey.CalendarMonth)
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
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
