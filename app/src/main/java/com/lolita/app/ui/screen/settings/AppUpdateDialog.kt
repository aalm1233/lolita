package com.lolita.app.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.lolita.app.data.remote.GiteeReleaseApi
import com.lolita.app.ui.theme.LolitaSkin

@Composable
internal fun AppUpdateDialog(
    state: AppUpdateUiState,
    currentVersion: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    if (!state.showDialog) return
    val update = state.update
    val hasUpdate = update?.hasUpdate == true
    val downloadUrl = update?.release?.apkDownloadUrl?.takeIf { hasUpdate }
    val releaseUrl = update?.release?.releaseUrl ?: GiteeReleaseApi.RELEASES_URL

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = LolitaSkin.current.cardShape,
        title = {
            Text(when {
                state.isChecking -> "检查更新"
                state.error != null -> "检查失败"
                hasUpdate -> "发现新版本"
                update != null && update.versionComparison < 0 -> "当前版本较新"
                else -> "已是最新版本"
            })
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("当前版本：v$currentVersion")
                when {
                    state.isChecking -> Text("正在连接 Gitee…")
                    state.error != null -> Text(state.error, color = MaterialTheme.colorScheme.error)
                    update != null -> {
                        Text("最新发布：v${update.release.versionName}")
                        if (hasUpdate) {
                            Text(if (downloadUrl != null) "在浏览器中下载 APK 后覆盖安装。" else "请前往发布页选择安装包下载。")
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                state.isChecking -> TextButton(onClick = {}, enabled = false) { Text("检查中") }
                state.error != null -> TextButton(onClick = onRetry) { Text("重试") }
                hasUpdate -> TextButton(onClick = { onOpenUrl(downloadUrl ?: releaseUrl) }) {
                    Text(if (downloadUrl != null) "下载 APK" else "查看发布页")
                }
                else -> TextButton(onClick = onDismiss) { Text("知道了") }
            }
        },
        dismissButton = {
            Row {
                if (state.error != null || downloadUrl != null) {
                    TextButton(onClick = { onOpenUrl(releaseUrl) }) { Text("查看发布页") }
                }
                if (state.isChecking || state.error != null || hasUpdate) {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        }
    )
}
