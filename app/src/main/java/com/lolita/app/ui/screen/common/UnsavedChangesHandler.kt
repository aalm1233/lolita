package com.lolita.app.ui.screen.common

import androidx.activity.compose.BackHandler
import androidx.compose.material3.*
import androidx.compose.runtime.*

@Composable
fun UnsavedChangesHandler(
    hasUnsavedChanges: Boolean,
    onBack: () -> Unit
): () -> Unit {
    var showDialog by remember { mutableStateOf(false) }
    val requestBack: () -> Unit = {
        if (hasUnsavedChanges) {
            showDialog = true
        } else {
            onBack()
        }
    }
    BackHandler(enabled = true, onBack = requestBack)

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("放弃未保存的修改？") },
            text = { Text("返回后，本次未保存的修改将丢失。") },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    onBack()
                }) { Text("放弃并返回") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("继续编辑") }
            }
        )
    }
    return requestBack
}
