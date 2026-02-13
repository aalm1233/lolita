package com.lolita.app.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.theme.Pink100
import com.lolita.app.ui.theme.Pink400

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToSearch: () -> Unit = {},
    onNavigateToBrand: () -> Unit,
    onNavigateToCategory: () -> Unit,
    onNavigateToBackupRestore: () -> Unit,
    onNavigateToStats: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            GradientTopAppBar(
                title = { Text("设置") },
                compact = true
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsMenuItem(
                title = "数据统计",
                description = "查看服饰和消费统计",
                icon = Icons.Default.DateRange,
                iconTint = Color(0xFFE91E8C),
                onClick = onNavigateToStats
            )

            SettingsMenuItem(
                title = "搜索",
                description = "按名称搜索服饰",
                icon = Icons.Default.Search,
                iconTint = Color(0xFFFFD93D),
                onClick = onNavigateToSearch
            )

            SettingsMenuItem(
                title = "品牌管理",
                description = "管理预置和自定义品牌",
                icon = Icons.Default.Favorite,
                iconTint = Color(0xFFFF69B4),
                onClick = onNavigateToBrand
            )

            SettingsMenuItem(
                title = "类型管理",
                description = "管理服饰类型",
                icon = Icons.AutoMirrored.Filled.List,
                iconTint = Color(0xFF6BCF7F),
                onClick = onNavigateToCategory
            )

            SettingsMenuItem(
                title = "数据备份与恢复",
                description = "导出/导入数据，支持JSON和CSV格式",
                icon = Icons.Default.Build,
                iconTint = Color(0xFF64B5F6),
                onClick = onNavigateToBackupRestore
            )

            Spacer(modifier = Modifier.weight(1f))

            // About section
            HorizontalDivider(color = Pink100, thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = Pink400.copy(alpha = 0.1f),
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
                    color = Pink400
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