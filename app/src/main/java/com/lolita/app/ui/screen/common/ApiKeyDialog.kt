package com.lolita.app.ui.screen.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll

@Composable
fun ApiKeyDialog(hasKey: Boolean, keyRevision: Int, isConfiguring: Boolean, busy: Boolean, error: String?, onSave: (String) -> Unit, onClear: () -> Unit, onDismiss: () -> Unit,
    serviceId: String, serviceLabel: String, keyHelp: String) {
    val skin = LolitaSkin.current
    var value by remember(serviceId) { mutableStateOf("") }
    val initialRevision = remember(serviceId) { keyRevision }
    val scroll = rememberScrollState()
    ObserveSkinScroll(scroll)
    LaunchedEffect(keyRevision) { if (keyRevision != initialRevision) { value = ""; onDismiss() } }
    AlertDialog(onDismissRequest = { if (!isConfiguring) onDismiss() }, modifier = Modifier.imePadding(), title = { Text("配置 API Key") },
        text = {
            Column(Modifier.verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                Text(serviceLabel, style = MaterialTheme.typography.titleSmall)
                Text(keyHelp, style = MaterialTheme.typography.bodySmall)
                if (hasKey) Text("输入新密钥可替换本机配置，旧密钥不会回填。", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = value, onValueChange = { value = it }, enabled = !busy,
                    label = { Text("API Key") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(skin.cornerRadiusMedium))
                Text("密钥经 Android Keystore 加密保存在本机，不进入业务备份。", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (hasKey) TextButton(onClick = onClear, enabled = !busy) { Text("删除本机密钥") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value); value = "" }, enabled = value.isNotBlank() && !busy) {
            if (isConfiguring) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text(if (hasKey) "保存替换" else "保存密钥")
        } },
        dismissButton = { TextButton(onClick = { value = ""; onDismiss() }, enabled = !isConfiguring) { Text("返回") } })
}

