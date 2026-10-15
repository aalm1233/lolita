package com.lolita.app.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lolita.app.data.remote.GiteeReleaseApi
import com.lolita.app.ui.screen.common.SectionHeader
import com.lolita.app.ui.theme.LolitaSkin

@Composable
@OptIn(ExperimentalLayoutApi::class)
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
    val skin = LolitaSkin.current
    val configuration = LocalConfiguration.current

    AlertDialog(
        modifier = Modifier.widthIn(max = (configuration.screenWidthDp.dp - skin.spacingLarge * 2).coerceIn(200.dp, 560.dp)),
        onDismissRequest = onDismiss,
        shape = skin.cardShape,
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
            Column(
                modifier = Modifier
                    .heightIn(max = (configuration.screenHeightDp.dp * 0.5f).coerceIn(160.dp, 400.dp))
                    .verticalScroll(rememberScrollState())
                    .testTag("appUpdateContent"),
                verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)
            ) {
                Text("当前版本：v$currentVersion")
                when {
                    state.isChecking -> Text("正在连接 Gitee…")
                    state.error != null -> Text(state.error, color = MaterialTheme.colorScheme.error)
                    update != null -> {
                        Text("最新发布：v${update.release.versionName}")
                        if (hasUpdate) {
                            Text(if (downloadUrl != null) "在浏览器中下载 APK 后覆盖安装。" else "请前往发布页选择安装包下载。")
                        }
                        SectionHeader("本版更新")
                        val notes = update.release.releaseNotes.takeIf { it.isNotBlank() }
                        if (notes == null) {
                            Text("此版本暂未提供更新说明")
                        } else {
                            SelectionContainer {
                                Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                                    notes.replace("\r\n", "\n").trim().split(Regex("\n[\\t ]*\n")).forEach { paragraph ->
                                        Text(paragraph)
                                    }
                                }
                            }
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
            FlowRow(horizontalArrangement = Arrangement.End) {
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
