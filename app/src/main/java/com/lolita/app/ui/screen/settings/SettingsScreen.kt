package com.lolita.app.ui.screen.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.notification.DailyOutfitReminderScheduler
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.LolitaCard
import kotlinx.coroutines.launch
import java.io.File
import com.lolita.app.BuildConfig
import java.text.NumberFormat
import java.util.Locale

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
            // Profile section
            ProfileSection(
                uiState = uiState,
                onAvatarClick = { imagePickerLauncher.launch("image/*") },
                onNicknameClick = { showNicknameDialog = true }
            )

            Spacer(modifier = Modifier.height(4.dp))

            SettingsMenuItem(
                title = "属性管理",
                description = "管理品牌、类型、风格、季节、位置、来源",
                icon = Icons.Default.Build,
                iconTint = Color(0xFF7E57C2),
                onClick = onNavigateToAttributeManage
            )

            SettingsMenuItem(
                title = "数据备份与恢复",
                description = "导出/导入数据，支持JSON和CSV格式",
                icon = Icons.Default.Build,
                iconTint = Color(0xFF64B5F6),
                onClick = onNavigateToBackupRestore
            )

            SettingsMenuItem(
                title = "淘宝订单导入",
                description = "从淘宝订单Excel文件批量导入服饰",
                icon = Icons.Default.ShoppingCart,
                iconTint = Color(0xFFFF8A65),
                onClick = onNavigateToTaobaoImport
            )

            // 穿搭提醒设置
            SettingsMenuItem(
                title = "共享资料同步",
                description = "连接后端并刷新共享图鉴缓存",
                icon = Icons.Default.CloudDownload,
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
                icon = Icons.Default.Notifications,
                iconTint = Color(0xFFE57373),
                checked = outfitReminderEnabled,
                onCheckedChange = { enabled ->
                    coroutineScope.launch {
                        appPreferences.setOutfitReminderEnabled(enabled)
                        val scheduler = DailyOutfitReminderScheduler(com.lolita.app.di.AppModule.context())
                        if (enabled) scheduler.schedule(outfitReminderHour)
                        else scheduler.cancel()
                    }
                }
            )

            // Display settings section
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
                icon = Icons.Default.AttachMoney,
                iconTint = Color(0xFFFFB74D),
                checked = showTotalPrice,
                onCheckedChange = {
                    coroutineScope.launch { appPreferences.setShowTotalPrice(it) }
                }
            )

            SettingsMenuItem(
                title = "皮肤选择",
                description = "切换应用主题风格",
                icon = Icons.Default.Palette,
                iconTint = Color(0xFF9C27B0),
                onClick = onNavigateToThemeSelect
            )

            Spacer(modifier = Modifier.height(24.dp))

            // About section
            HorizontalDivider(color = MaterialTheme.colorScheme.primaryContainer, thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "我的Lolita",
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

    // Nickname edit dialog
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
            // Avatar
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

            // Nickname + stats
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
                        color = if (uiState.nickname.isEmpty())
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "编辑昵称",
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
                        text = "服饰 ${uiState.totalItems}件 | 图鉴 ${uiState.totalCatalogEntries}条 | 套装 ${uiState.totalCoordinates}套",
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
    icon: ImageVector,
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
                    Icon(
                        icon,
                        contentDescription = null,
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
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
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
    icon: ImageVector,
    iconTint: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    LolitaCard(
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
                    Icon(
                        icon,
                        contentDescription = null,
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

