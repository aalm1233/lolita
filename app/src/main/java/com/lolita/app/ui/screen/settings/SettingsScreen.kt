package com.lolita.app.ui.screen.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lolita.app.BuildConfig
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.notification.DailyOutfitReminderScheduler
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import java.io.File
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToAttributeManage: () -> Unit,
    onNavigateToBackupRestore: () -> Unit,
    onNavigateToTaobaoImport: () -> Unit = {},
    onNavigateToSharedLibrarySync: () -> Unit = {},
    onNavigateToThemeSelect: () -> Unit = {},
    appPreferences: AppPreferences = com.lolita.app.di.AppModule.appPreferences(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val showTotalPrice by appPreferences.showTotalPrice.collectAsState(initial = false)
    val uiState by settingsViewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var showNicknameDialog by remember { mutableStateOf(false) }

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

    Scaffold(
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProfileSection(
                uiState = uiState,
                onAvatarClick = { imagePickerLauncher.launch("image/*") },
                onNicknameClick = { showNicknameDialog = true }
            )

            Spacer(modifier = Modifier.height(4.dp))

            SettingsMenuItem(
                title = "属性管理",
                description = "管理品牌、类型、风格、季节、位置、来源",
                iconKey = IconKey.Category,
                iconTint = Color(0xFF7E57C2),
                onClick = onNavigateToAttributeManage
            )

            SettingsMenuItem(
                title = "数据备份与恢复",
                description = "导出/导入数据，支持 JSON 和 CSV 格式",
                iconKey = IconKey.FileOpen,
                iconTint = Color(0xFF64B5F6),
                onClick = onNavigateToBackupRestore
            )

            SettingsMenuItem(
                title = "淘宝订单导入",
                description = "从淘宝订单 Excel 文件批量导入服饰",
                iconKey = IconKey.Link,
                iconTint = Color(0xFFFF8A65),
                onClick = onNavigateToTaobaoImport
            )

            SettingsMenuItem(
                title = "共享资料同步",
                description = "连接后端并刷新共享图鉴缓存",
                iconKey = IconKey.Refresh,
                iconTint = Color(0xFF4DB6AC),
                onClick = onNavigateToSharedLibrarySync
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "穿搭提醒",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            val outfitReminderEnabled by appPreferences.outfitReminderEnabled.collectAsState(initial = false)
            val outfitReminderHour by appPreferences.outfitReminderHour.collectAsState(initial = 20)

            SettingsToggleItem(
                title = "每日穿搭提醒",
                description = "每天 ${outfitReminderHour}:00 提醒记录穿搭",
                iconKey = IconKey.Notifications,
                iconTint = Color(0xFFE57373),
                checked = outfitReminderEnabled,
                onCheckedChange = { enabled ->
                    coroutineScope.launch {
                        appPreferences.setOutfitReminderEnabled(enabled)
                        val scheduler = DailyOutfitReminderScheduler(com.lolita.app.di.AppModule.context())
                        if (enabled) scheduler.schedule(outfitReminderHour) else scheduler.cancel()
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "显示设置",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            SettingsToggleItem(
                title = "显示总价",
                description = "在服饰列表右上角显示筛选结果的总价",
                iconKey = IconKey.AttachMoney,
                iconTint = Color(0xFFFFB74D),
                checked = showTotalPrice,
                onCheckedChange = {
                    coroutineScope.launch { appPreferences.setShowTotalPrice(it) }
                }
            )

            SettingsMenuItem(
                title = "皮肤选择",
                description = "切换应用主题风格",
                iconKey = IconKey.Palette,
                iconTint = Color(0xFF9C27B0),
                onClick = onNavigateToThemeSelect
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
                    style = MaterialTheme.typography.titleMedium,
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
private fun ProfileSection(
    uiState: SettingsUiState,
    onAvatarClick: () -> Unit,
    onNicknameClick: () -> Unit
) {
    LolitaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .clickable(onClick = onAvatarClick),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.avatarPath.isNotEmpty() && File(uiState.avatarPath).exists()) {
                    AsyncImage(
                        model = File(uiState.avatarPath),
                        contentDescription = "头像",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        "🎀",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onNicknameClick)
                ) {
                    Text(
                        text = uiState.nickname.ifEmpty { "点击设置昵称" },
                        style = MaterialTheme.typography.titleMedium,
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

                val formattedSpent = NumberFormat.getNumberInstance(Locale.CHINA)
                    .apply { maximumFractionDigits = 0 }
                    .format(uiState.totalSpent)

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "服饰 ${uiState.totalItems} 件 | 图鉴 ${uiState.totalCatalogEntries} 条 | 套装 ${uiState.totalCoordinates} 套",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "总花费 ¥$formattedSpent",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
private fun SettingsMenuItem(
    title: String,
    description: String,
    iconKey: IconKey,
    iconTint: Color,
    onClick: () -> Unit
) {
    LolitaCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                    style = MaterialTheme.typography.titleMedium,
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
    onCheckedChange: (Boolean) -> Unit
) {
    LolitaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                    style = MaterialTheme.typography.titleMedium,
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
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}
