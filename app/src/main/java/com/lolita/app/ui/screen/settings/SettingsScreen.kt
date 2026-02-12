package com.lolita.app.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    onNavigateToBrand: () -> Unit,
    onNavigateToCategory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineMedium
        )

        SettingsMenuItem(
            title = "品牌管理",
            description = "管理预置和自定义品牌",
            onClick = onNavigateToBrand
        )

        SettingsMenuItem(
            title = "类型管理",
            description = "管理服饰类型",
            onClick = onNavigateToCategory
        )

        SettingsMenuItem(
            title = "数据备份",
            description = "导出数据到文件"
        ) {
            // TODO: Implement backup
        }

        SettingsMenuItem(
            title = "数据恢复",
            description = "从文件导入数据"
        ) {
            // TODO: Implement restore
        }
    }
}

@Composable
private fun SettingsMenuItem(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
