package com.lolita.app.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.data.notification.DailyOutfitReminderScheduler
import com.lolita.app.data.preferences.AppPreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToAttributeManage: () -> Unit,
    onNavigateToBackupRestore: () -> Unit,
    onNavigateToTaobaoImport: () -> Unit = {},
    onNavigateToThemeSelect: () -> Unit = {},
    appPreferences: AppPreferences = com.lolita.app.di.AppModule.appPreferences()
) {
    val showTotalPrice by appPreferences.showTotalPrice.collectAsState(initial = false)
    val coroutineScope = rememberCoroutineScope()
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
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "🎀",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "我的Lolita",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "v1.0.0",
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