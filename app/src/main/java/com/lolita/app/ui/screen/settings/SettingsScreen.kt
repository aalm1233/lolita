package com.lolita.app.ui.screen.settings

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.component.skinClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.ui.screen.common.LolitaShimmerImage
import com.lolita.app.BuildConfig
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.notification.DailyOutfitReminderScheduler
import com.lolita.app.data.notification.NotificationChannelSetup
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.CardVariant
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import java.io.File
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToAttributeManage: () -> Unit,
    onNavigateToBackupRestore: () -> Unit,
    onNavigateToTaobaoImport: () -> Unit = {},


    onNavigateToThemeSelect: () -> Unit = {},
    onNavigateToLaboratory: () -> Unit = {},
    appPreferences: AppPreferences = com.lolita.app.di.AppModule.appPreferences(),
    settingsViewModel: SettingsViewModel = viewModel(),
    appUpdateViewModel: AppUpdateViewModel = viewModel()
) {
    val isDark = isSystemInDarkTheme()
    val showTotalPrice by appPreferences.showTotalPrice.collectAsState(initial = false)
    val uiState by settingsViewModel.uiState.collectAsState()
    val updateState by appUpdateViewModel.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var showNicknameDialog by remember { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var showNotificationSettings by rememberSaveable { mutableStateOf(false) }
    var savingReminder by remember { mutableStateOf(false) }
    val reminderConfig by appPreferences.outfitReminderConfig.collectAsState(initial = null)

    fun saveReminderEnabled(enabled: Boolean) {
        coroutineScope.launch {
            savingReminder = true
            try {
                appPreferences.setOutfitReminderEnabled(enabled)
                DailyOutfitReminderScheduler(context).reschedule()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("SettingsScreen", "Failed to update outfit reminder", e)
                Toast.makeText(context, "提醒设置失败，请重试", Toast.LENGTH_SHORT).show()
            } finally {
                savingReminder = false
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && NotificationChannelSetup.canShowOutfitNotifications(context)) {
            saveReminderEnabled(true)
        } else {
            savingReminder = false
            showNotificationSettings = true
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val path = ImageFileHelper.copyToInternalStorage(context, it)
                settingsViewModel.setAvatarPath(path)
            }
        }
    }

    val scroll = rememberScrollState()
    com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll(scroll)
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            GradientTopAppBar(
                title = { Text("个人") },
                compact = true
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProfileSection(
                uiState = uiState,
                showTotalPrice = showTotalPrice,
                onAvatarClick = { imagePickerLauncher.launch("image/*") },
                onNicknameClick = { showNicknameDialog = true }
            )

            Spacer(modifier = Modifier.height(4.dp))

            LaboratoryEntry(onNavigateToLaboratory)

            SettingsMenuItem(
                title = "属性管理",
                description = "品牌、类型与收纳等",
                iconKey = IconKey.Category,
                iconTint = if (isDark) Color(0xFFB39DDB) else Color(0xFF7E57C2),
                onClick = onNavigateToAttributeManage
            )

            SettingsMenuItem(
                title = "数据备份与恢复",
                description = "使用 ZIP 备份记录和图片",
                iconKey = IconKey.FileOpen,
                iconTint = if (isDark) Color(0xFF90CAF9) else Color(0xFF64B5F6),
                onClick = onNavigateToBackupRestore
            )

            SettingsMenuItem(
                title = "淘宝订单导入",
                description = "从淘宝订单 Excel 文件批量导入服饰",
                iconKey = IconKey.Link,
                iconTint = if (isDark) Color(0xFFFFAB91) else Color(0xFFFF8A65),
                onClick = onNavigateToTaobaoImport
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "穿搭提醒",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            val reminderTime = reminderConfig?.let { String.format(Locale.CHINA, "%02d:%02d", it.hour, it.minute) }
            SettingsToggleItem(
                title = "每日穿搭提醒",
                description = reminderTime?.let { "每天 $it 提醒记录穿搭" } ?: "正在读取提醒设置",
                iconKey = IconKey.Notifications,
                iconTint = if (isDark) Color(0xFFEF9A9A) else Color(0xFFE57373),
                checked = reminderConfig?.enabled == true,
                enabled = reminderConfig != null && !savingReminder,
                onCheckedChange = { enabled ->
                    if (!enabled) {
                        saveReminderEnabled(false)
                    } else if (Build.VERSION.SDK_INT >= 33 &&
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        savingReminder = true
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else if (!NotificationChannelSetup.canShowOutfitNotifications(context)) {
                        showNotificationSettings = true
                    } else {
                        saveReminderEnabled(true)
                    }
                }
            )

            if (reminderConfig?.enabled == true) {
                SettingsMenuItem(
                    title = "提醒时间",
                    description = reminderTime.orEmpty(),
                    iconKey = IconKey.CalendarMonth,
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = { if (!savingReminder) showTimePicker = true }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "显示设置",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            SettingsToggleItem(
                title = "显示金额",
                description = "显示价格、消费统计和购买资料提醒",
                iconKey = IconKey.AttachMoney,
                iconTint = if (isDark) Color(0xFFFFCC80) else Color(0xFFFFB74D),
                checked = showTotalPrice,
                onCheckedChange = {
                    coroutineScope.launch { appPreferences.setShowTotalPrice(it) }
                }
            )

            SettingsMenuItem(
                title = "皮肤选择",
                description = "切换应用主题风格",
                iconKey = IconKey.Palette,
                iconTint = if (isDark) Color(0xFFCE93D8) else Color(0xFF9C27B0),
                onClick = onNavigateToThemeSelect
            )

            SettingsMenuItem(
                title = "检查更新",
                description = if (updateState.isChecking) "正在检查…" else "当前版本 v${BuildConfig.VERSION_NAME}",
                iconKey = IconKey.Refresh,
                iconTint = MaterialTheme.colorScheme.primary,
                onClick = appUpdateViewModel::checkForUpdate
            )

            Spacer(modifier = Modifier.height(24.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.primaryContainer, thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "我的 Lolita",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "记录你的每一份美好",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showTimePicker && reminderConfig != null) {
        val config = reminderConfig!!
        val timeState = rememberTimePickerState(initialHour = config.hour, initialMinute = config.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("提醒时间") },
            text = { TimeInput(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    val hour = timeState.hour
                    val minute = timeState.minute
                    showTimePicker = false
                    coroutineScope.launch {
                        savingReminder = true
                        try {
                            appPreferences.setOutfitReminderTime(hour, minute)
                            DailyOutfitReminderScheduler(context).reschedule()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            android.util.Log.e("SettingsScreen", "Failed to save outfit reminder time", e)
                            Toast.makeText(context, "提醒时间保存失败，请重试", Toast.LENGTH_SHORT).show()
                        } finally {
                            savingReminder = false
                        }
                    }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("取消") } }
        )
    }

    if (showNotificationSettings) {
        AlertDialog(
            onDismissRequest = { showNotificationSettings = false },
            title = { Text("请允许通知") },
            text = { Text("系统通知或穿搭提醒通知渠道未开启，无法发送提醒。请在系统设置允许穿搭提醒，然后开启每日穿搭提醒。") },
            confirmButton = {
                TextButton(onClick = {
                    showNotificationSettings = false
                    context.startActivity(NotificationChannelSetup.outfitNotificationSettingsIntent(context))
                }) { Text("打开系统设置") }
            },
            dismissButton = { TextButton(onClick = { showNotificationSettings = false }) { Text("取消") } }
        )
    }

    if (showNicknameDialog) {
        NicknameEditDialog(
            currentNickname = uiState.nickname,
            onDismiss = { showNicknameDialog = false },
            onConfirm = { name ->
                settingsViewModel.setNickname(name)
                showNicknameDialog = false
            }
        )
    }
}

@Composable
internal fun ProfileSection(
    uiState: SettingsUiState,
    showTotalPrice: Boolean,
    onAvatarClick: () -> Unit,
    onNicknameClick: () -> Unit
) {
    LolitaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .skinClickable(onClick = onAvatarClick),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.avatarPath.isNotEmpty() && File(uiState.avatarPath).exists()) {
                    LolitaShimmerImage(
                        model = File(uiState.avatarPath),
                        contentDescription = "头像",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        placeholderInitial = uiState.nickname.firstOrNull()?.toString()
                    )
                } else {
                    SkinIcon(IconKey.Wishlist, modifier = Modifier.size(40.dp))
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.skinClickable(onClick = onNicknameClick)
                ) {
                    Text(
                        text = uiState.nickname.ifEmpty { "点击设置昵称" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (uiState.nickname.isEmpty()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )

                    Spacer(modifier = Modifier.width(4.dp))
                    SkinIcon(
                        key = IconKey.Edit,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "服饰 ${uiState.totalItems} 件 | 套装 ${uiState.totalCoordinates} 套",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (showTotalPrice) {
                        val formattedSpent = NumberFormat.getNumberInstance(Locale.CHINA)
                            .apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }
                            .format(uiState.totalSpent)
                        Text(
                            text = "当前订单实付 ¥$formattedSpent",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NicknameEditDialog(
    currentNickname: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentNickname) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置昵称") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 20) text = it },
                label = { Text("昵称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
internal fun SettingsMenuItem(
    title: String,
    description: String,
    iconKey: IconKey,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: CardVariant = CardVariant.DEFAULT
) {
    val skin = LolitaSkin.current
    LolitaCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        variant = variant,
        contentPadding = PaddingValues(skin.cardInnerPadding)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = iconTint.copy(alpha = 0.1f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SkinIcon(
                        key = iconKey,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp).testTag("settingsIcon-${iconKey.name}")
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SkinIcon(
                key = IconKey.ArrowForward,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsToggleItem(
    title: String,
    description: String,
    iconKey: IconKey,
    iconTint: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    LolitaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = iconTint.copy(alpha = 0.1f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SkinIcon(
                        key = iconKey,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}
