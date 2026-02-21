package com.lolita.app.ui.screen.common

import androidx.activity.compose.BackHandler
import androidx.compose.material3.*
import androidx.compose.runtime.*

@Composable
fun UnsavedChangesHandler(
    hasUnsavedChanges: Boolean,
    onBack: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (hasUnsavedChanges) {
            showDialog = true
        } else {
            onBack()
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("确认返回") },
            text = { Text("有未保存的修改，确定要返回吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    onBack()
                }) { Text("返回") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("继续编辑") }
            }
        )
    }
}
