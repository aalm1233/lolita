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
fun ApiKeyDialog(hasKey: Boolean, keyRevision: Int, isConfiguring: Boolean, busy: Boolean, error: String?, onSave: (String) -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    val skin = LolitaSkin.current
    var value by remember { mutableStateOf("") }
    val initialRevision = remember { keyRevision }
    val scroll = rememberScrollState()
    ObserveSkinScroll(scroll)
    LaunchedEffect(keyRevision) { if (keyRevision != initialRevision) { value = ""; onDismiss() } }
    AlertDialog(onDismissRequest = { if (!isConfiguring) onDismiss() }, title = { Text("个人 API Key") },
        text = {
            Column(Modifier.verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                Text(if (hasKey) "当前已配置。输入新密钥可替换，旧密钥不会回填。" else "填写 OpenAI API Key 后才能生成。API 需单独充值，ChatGPT 订阅不含此额度。", style = MaterialTheme.typography.bodySmall)
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

