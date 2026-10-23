package com.lolita.app.ui.screen.settings

import androidx.compose.ui.graphics.Color
import android.net.Uri
import android.content.Intent
import android.provider.OpenableColumns
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.lolita.app.ui.screen.common.LolitaSection
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lolita.app.ui.screen.common.GradientTopAppBar
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.file.BackupManager
import com.lolita.app.data.file.BackupPreview
import com.lolita.app.di.AppModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import com.lolita.app.ui.screen.common.LolitaCard

enum class BackupExportFormat(val title: String, val mimeType: String, val summary: String) {
    ZIP("完整备份 · ZIP", "application/zip", "包含记录和图片"),
    JSON("数据备份 · JSON", "application/json", "不含图片"),
    CSV("表格导出 · CSV", "text/csv", "仅供查看，不能恢复")
}

data class BackupExportResult(val uri: Uri, val fileName: String, val format: BackupExportFormat)

data class BackupRestoreUiState(
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val isPreviewing: Boolean = false,
    val message: String? = null,
    val preview: BackupPreview? = null,
    val pendingImportUri: Uri? = null,
    val showConfirmDialog: Boolean = false,
    val lastExport: BackupExportResult? = null
) {
    val busy: Boolean get() = isExporting || isImporting || isPreviewing || showConfirmDialog
}

class BackupRestoreViewModel(
    private val backupManager: BackupManager = AppModule.backupManager(),
    private val exportName: suspend (Uri) -> String? = { uri ->
        withContext(Dispatchers.IO) {
            AppModule.context().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }
    }
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupRestoreUiState())
    val uiState: StateFlow<BackupRestoreUiState> = _uiState.asStateFlow()

    private fun exportDirectory() = if (android.os.Build.VERSION.SDK_INT >= 29) "下载目录" else "应用专用下载目录"

    fun exportJson() = export(BackupExportFormat.JSON)
    fun exportCsv() = export(BackupExportFormat.CSV)
    fun exportZip() = export(BackupExportFormat.ZIP)

    private fun export(format: BackupExportFormat) {
        if (_uiState.value.busy) return
        _uiState.value = _uiState.value.copy(isExporting = true, message = null, lastExport = null)
        viewModelScope.launch {
            val result = when (format) {
                BackupExportFormat.ZIP -> backupManager.exportToZip()
                BackupExportFormat.JSON -> backupManager.exportToJson()
                BackupExportFormat.CSV -> backupManager.exportToCsv()
            }
            result.fold(onSuccess = { uri ->
                val name = try { exportName(uri) } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    null
                }
                _uiState.value = _uiState.value.copy(
                    lastExport = BackupExportResult(uri, name ?: format.title, format),
                    message = "${format.title}已保存至${exportDirectory()}"
                )
            }, onFailure = {
                _uiState.value = _uiState.value.copy(message = backupFailure(it, "导出失败，请检查存储空间后重试。"))
            })
            _uiState.value = _uiState.value.copy(isExporting = false)
        }
    }

    fun openExportFailed() {
        _uiState.value = _uiState.value.copy(message = "文件已导出，可在${exportDirectory()}查找；也可安装支持该格式的查看器后重试。")
    }

    fun onFileSelected(uri: Uri?) {
        if (uri == null || _uiState.value.busy) return
        _uiState.value = _uiState.value.copy(pendingImportUri = uri, isPreviewing = true)
        viewModelScope.launch {
            backupManager.previewBackup(uri).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        preview = it,
                        showConfirmDialog = true,
                        isPreviewing = false
                    )
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(
                        message = backupFailure(it, "无法读取备份，请重新选择文件。"),
                        isPreviewing = false
                    )
                }
            )
        }
    }

    fun confirmImport() {
        if (!_uiState.value.showConfirmDialog || _uiState.value.isImporting) return
        val uri = _uiState.value.pendingImportUri ?: return
        _uiState.value = _uiState.value.copy(showConfirmDialog = false, isImporting = true, message = null)
        viewModelScope.launch {
            backupManager.importBackup(uri).fold(
                onSuccess = { summary ->
                    val imageMsg = if (summary.imageCount > 0) "，恢复 ${summary.imageCount} 张图片" else ""
                    val missingMsg = if (summary.missingImageCount > 0) "\n⚠ ${summary.missingImageCount} 张图片文件缺失" else ""
                    val calendarMsg = if (summary.calendarEventsFailed > 0) "\n⚠ ${summary.calendarEventsFailed} 个日历事件创建失败（可能需要授予日历权限）" else ""
                    _uiState.value = _uiState.value.copy(
                        message = "恢复完成！导入 ${summary.totalImported} 条数据$imageMsg$missingMsg$calendarMsg"
                    )
                },
                onFailure = { _uiState.value = _uiState.value.copy(message = backupFailure(it, "恢复未完成，请检查备份后重试。")) }
            )
            _uiState.value = _uiState.value.copy(
                isImporting = false,
                pendingImportUri = null,
                preview = null
            )
        }
    }

    fun dismissDialog() {
        _uiState.value = _uiState.value.copy(
            showConfirmDialog = false,
            pendingImportUri = null,
            preview = null
        )
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    onBack: () -> Unit,
    viewModel: BackupRestoreViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), viewModel::onFileSelected)
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }
    val context = LocalContext.current
    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
        GradientTopAppBar(title = { Text("备份与恢复") }, navigationIcon = {
            IconButton(onClick = onBack) { SkinIcon(IconKey.ArrowBack) }
        })
    }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        BackupRestoreContent(state, viewModel::exportZip, viewModel::exportJson, viewModel::exportCsv,
            { picker.launch(arrayOf("application/json", "application/zip")) },
            { result ->
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(result.uri, result.format.mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                } catch (_: android.content.ActivityNotFoundException) { viewModel.openExportFailed() }
                  catch (_: SecurityException) { viewModel.openExportFailed() }
            }, Modifier.padding(padding).consumeWindowInsets(padding))
    }
    if (state.showConfirmDialog) state.preview?.let { BackupRestoreConfirmation(it, viewModel::confirmImport, viewModel::dismissDialog) }
}

@Composable
internal fun BackupRestoreContent(state: BackupRestoreUiState, onZip: () -> Unit, onJson: () -> Unit,
    onCsv: () -> Unit, onRestore: () -> Unit, onOpen: (BackupExportResult) -> Unit, modifier: Modifier = Modifier) {
    val skin = LolitaSkin.current
    val scroll = rememberScrollState()
    ObserveSkinScroll(scroll)
    Column(modifier.fillMaxSize().verticalScroll(scroll).padding(skin.spacingLarge).testTag("backupContent"),
        verticalArrangement = Arrangement.spacedBy(skin.cardGap)) {
        LolitaSection("完整备份 · ZIP") { row {
            Text("包含记录和图片，推荐使用", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onZip, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                if (state.isExporting) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(skin.spacingMedium)) }
                Text(if (state.isExporting) "正在导出…" else "导出完整备份")
            }
        } }
        state.lastExport?.let { result ->
            LolitaSection("已导出") { row {
                Text(result.fileName, style = MaterialTheme.typography.bodyMedium)
                Text("${result.format.summary} · ${if (android.os.Build.VERSION.SDK_INT >= 29) "下载目录" else "应用专用下载目录"}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { onOpen(result) }, enabled = !state.busy) { Text("查看文件") }
            } }
        }
        LolitaSection("其他导出方式") {
            row { BackupExportRow(BackupExportFormat.JSON, "导出数据备份", state.busy, onJson) }
            row { BackupExportRow(BackupExportFormat.CSV, "导出表格", state.busy, onCsv) }
        }
        LolitaSection("恢复备份") { row {
            Text("恢复成功后，当前记录将替换为备份内容。请先导出完整备份。", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium)
            Text("支持 ZIP 和 JSON；JSON 不含图片。", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onRestore, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                if (state.isImporting || state.isPreviewing) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(skin.spacingMedium)) }
                Text(if (state.isPreviewing) "读取备份中…" else if (state.isImporting) "恢复中…" else "选择备份文件")
            }
        } }
    }
}

@Composable
private fun BackupExportRow(format: BackupExportFormat, action: String, busy: Boolean, onExport: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(LolitaSkin.current.spacingSmall)) {
        Text(format.title, style = MaterialTheme.typography.titleSmall)
        Text(format.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onExport, enabled = !busy) { Text(action) }
    }
}

@Composable
internal fun BackupRestoreConfirmation(p: BackupPreview, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(p.backupDate))
    AlertDialog(onDismissRequest = onDismiss, title = { Text("恢复这份备份？") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("恢复成功后，当前记录将替换为备份内容。", color = MaterialTheme.colorScheme.error)
            Text("备份时间：$date")
            Text("物品 ${p.itemCount} 件 · 图片 ${p.imageCount} 张")
            Text("共 ${p.totalCount} 条记录", style = MaterialTheme.typography.bodySmall)
            if (p.missingImageCount > 0) Text("缺少 ${p.missingImageCount} 张图片，请使用含图片的 ZIP 备份。", color = MaterialTheme.colorScheme.error)
            if (p.historicalOwnedCount > 0) Text("历史已购 ${p.historicalOwnedCount} 笔，付款资料仍待补齐。")
            if (p.needsReviewCount > 0) Text("购买资料待核对 ${p.needsReviewCount} 笔。")
            if (p.archivedHistoricalOwnedCount > 0 || p.archivedNeedsReviewCount > 0)
                Text("归档订单中，历史已购 ${p.archivedHistoricalOwnedCount} 笔、待核对 ${p.archivedNeedsReviewCount} 笔。")
            com.lolita.app.ui.screen.common.ExpandableInfo("备份明细") {
                Text("备份格式：${p.backupVersion}")
                Text("品牌 ${p.brandCount} · 类型 ${p.categoryCount} · 风格 ${p.styleCount} · 季节 ${p.seasonCount}")
                Text("房间 ${p.locationCount} · 来源 ${p.sourceCount}")
                Text("套装 ${p.coordinateCount} · 价格 ${p.priceCount} · 付款 ${p.paymentCount}")
                Text("当前订单 ${p.orderBindingCount} · 付款阶段 ${p.paymentStageCount} · 归档订单 ${p.archivedOrderCount}")
                Text("穿搭日记 ${p.outfitLogCount} · 历史图鉴 ${p.archiveCount} · 月度预算 ${p.monthlyBudgetCount}")
                Text("试衣历史 ${p.tryOnResultCount} · 图像任务 ${p.imageTaskCount} · 模特 ${p.personalModelCount}")
                Text("导入会话 ${p.orderImportSessionCount} · 导入资料 ${p.orderImportRowCount} · 来源回执 ${p.orderImportReceiptCount} · 商品来源 ${p.itemSourceRecordCount}")
                Text("模特草稿 ${p.personalModelDraftCount} · 收纳位置 ${p.storageNodeCount} · 物品草稿 ${p.itemDraftCount}")
                Text("识图任务 ${p.recognitionTaskCount} · 识图来源 ${p.itemEvidenceCount} · 图片来源 ${p.imageProvenanceCount}")
                Text("试衣素材 ${p.itemTryOnMaterialCount} 份 · 关联记录 ${p.additionalRecordCount} 条")
            }
        }
    }, confirmButton = { TextButton(onClick = onConfirm) { Text("确认恢复") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

private fun backupFailure(error: Throwable, fallback: String): String {
    val message = error.message.orEmpty()
    return when {
        "预览后发生变化" in message -> "备份文件已变化，请重新选择。"
        "权限" in message || error is SecurityException -> "文件或日历权限不可用，请检查权限后重试。"
        "缺失" in message || "丢失" in message -> "所需图片或记录缺失，请检查原资料并使用完整 ZIP 备份。"
        "版本" in message || "不支持" in message -> "备份格式不受支持，请使用兼容的应用版本。"
        listOf("校验", "损坏", "无效", "重复", "不一致", "无法无损").any { it in message } -> "资料未通过完整性检查，请重新选择完整备份。"
        else -> fallback
    }
}
