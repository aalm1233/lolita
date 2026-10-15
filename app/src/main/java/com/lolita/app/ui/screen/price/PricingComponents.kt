package com.lolita.app.ui.screen.price

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lolita.app.ui.screen.common.LolitaSection
import com.lolita.app.ui.screen.common.ShimmerLine
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.component.skinClickable
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import com.lolita.app.domain.time.localDateToPickerMillis
import com.lolita.app.domain.time.pickerMillisToLocalDate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun formatPriceAmount(amount: Double): String = "¥${String.format(Locale.CHINA, "%.2f", amount)}"
internal fun formatPriceDate(date: Long?): String = date?.let { SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA).format(Date(it)) } ?: "日期待定"

@Composable
internal fun PricingValueRow(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = LolitaSkin.current.spacingSmall), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
internal fun PricingSkeleton() {
    LolitaSection("价格与付款") {
        row { Column(verticalArrangement = Arrangement.spacedBy(LolitaSkin.current.spacingMedium)) { repeat(5) { ShimmerLine(widthFraction = if (it % 2 == 0) 0.8f else 0.6f) } } }
    }
}

@Composable
internal fun PriceMoneyField(value: String, onValueChange: (String) -> Unit, label: String, enabled: Boolean = true, modifier: Modifier = Modifier) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, placeholder = { Text("0.00") },
        prefix = { Text("¥") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true, enabled = enabled, modifier = modifier.fillMaxWidth())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PricingDateField(label: String, date: Long?, onDateChange: (Long?) -> Unit, enabled: Boolean = true, clearable: Boolean = true) {
    var showPicker by remember { mutableStateOf(false) }
    Column {
        Box(Modifier.fillMaxWidth().skinClickable(enabled = enabled) { showPicker = true }) {
            OutlinedTextField(value = date?.let { formatPriceDate(it) } ?: "", onValueChange = {}, readOnly = true,
                label = { Text(label) }, placeholder = { Text(if (clearable) "日期待定" else "点击选择日期") },
                trailingIcon = { SkinIcon(IconKey.CalendarMonth) }, modifier = Modifier.fillMaxWidth(), enabled = false,
                colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant, disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant, disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant))
        }
        if (date != null && clearable) TextButton(onClick = { onDateChange(null) }, enabled = enabled) { Text("日期待定") }
    }
    if (showPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = localDateToPickerMillis(date ?: System.currentTimeMillis()))
        DatePickerDialog(onDismissRequest = { showPicker = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onDateChange(pickerMillisToLocalDate(it)) }; showPicker = false }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("取消") } }) { DatePicker(state = state) }
    }
}

@Composable
internal fun StageDraftFields(stage: StageDraft, onChange: (StageDraft) -> Unit, enabled: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(LolitaSkin.current.spacingMedium)) {
        OutlinedTextField(value = stage.name, onValueChange = { onChange(stage.copy(name = it)) }, label = { Text("阶段名称") },
            singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth())
        PriceMoneyField(stage.amount, { onChange(stage.copy(amount = it)) }, "阶段应付金额", enabled)
        PricingDateField("应付日期（可待定）", stage.dueDate, { onChange(stage.copy(dueDate = it, reminderSet = it != null && stage.reminderSet)) }, enabled)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("到期提醒", modifier = Modifier.weight(1f).padding(top = LolitaSkin.current.spacingMedium))
            Switch(checked = stage.reminderSet, onCheckedChange = { onChange(stage.copy(reminderSet = it)) }, enabled = enabled && stage.dueDate != null)
        }
        if (stage.dueDate == null) Text("日期待定时不安排提醒", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (stage.reminderSet && stage.dueDate != null) OutlinedTextField(value = stage.reminderDays,
            onValueChange = { onChange(stage.copy(reminderDays = it)) }, label = { Text("提前天数") }, suffix = { Text("天") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth())
    }
}

/** Calendar and notification access is requested only when saving an enabled stage reminder. */
@Composable
internal fun rememberStageReminderSave(onSave: () -> Unit): (Boolean) -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentSave by rememberUpdatedState(onSave)
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { currentSave() }
    return { needsReminder ->
        val permissions = mutableListOf<String>()
        if (needsReminder) {
            if (!com.lolita.app.data.notification.CalendarEventHelper.hasCalendarPermission(context)) {
                permissions += android.Manifest.permission.READ_CALENDAR
                permissions += android.Manifest.permission.WRITE_CALENDAR
            }
            if (android.os.Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissions += android.Manifest.permission.POST_NOTIFICATIONS
            }
        }
        if (permissions.isEmpty()) currentSave() else launcher.launch(permissions.toTypedArray())
    }
}
