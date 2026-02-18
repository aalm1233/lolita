package com.lolita.app.ui.screen.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

data class BackupRestoreUiState(
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val isPreviewing: Boolean = false,
    val message: String? = null,
    val preview: BackupPreview? = null,
    val pendingImportUri: Uri? = null,
    val showConfirmDialog: Boolean = false
)

class BackupRestoreViewModel : ViewModel() {
    private val backupManager: BackupManager = AppModule.backupManager()

    private val _uiState = MutableStateFlow(BackupRestoreUiState())
    val uiState: StateFlow<BackupRestoreUiState> = _uiState.asStateFlow()

    fun exportJson() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, message = null)
            backupManager.exportToJson().fold(
                onSuccess = { _uiState.value = _uiState.value.copy(message = "JSON备份成功！文件已保存到下载目录") },
                onFailure = { _uiState.value = _uiState.value.copy(message = "备份失败: ${it.message}") }
            )
            _uiState.value = _uiState.value.copy(isExporting = false)
        }
    }
    fun exportCsv() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, message = null)
            backupManager.exportToCsv().fold(
                onSuccess = { _uiState.value = _uiState.value.copy(message = "CSV导出成功！文件已保存到下载目录") },
                onFailure = { _uiState.value = _uiState.value.copy(message = "导出失败: ${it.message}") }
            )
            _uiState.value = _uiState.value.copy(isExporting = false)
        }
    }

    fun exportZip() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, message = null)
            backupManager.exportToZip().fold(
                onSuccess = { _uiState.value = _uiState.value.copy(message = "ZIP备份成功！文件已保存到下载目录（含图片）") },
                onFailure = { _uiState.value = _uiState.value.copy(message = "备份失败: ${it.message}") }
            )
            _uiState.value = _uiState.value.copy(isExporting = false)
        }
    }

    fun onFileSelected(uri: Uri?) {
        if (uri == null) return
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
                        message = "无法读取备份文件: ${it.message}",
                        isPreviewing = false
                    )
                }
            )
        }
    }

    fun confirmImport() {
        val uri = _uiState.value.pendingImportUri ?: return
        _uiState.value = _uiState.value.copy(showConfirmDialog = false, isImporting = true, message = null)
        viewModelScope.launch {
            backupManager.importFromJson(uri).fold(
                onSuccess = { summary ->
                    val imageMsg = if (summary.imageCount > 0) "，恢复 ${summary.imageCount} 张图片" else ""
                    _uiState.value = _uiState.value.copy(
                        message = "恢复完成！导入 ${summary.totalImported} 条数据$imageMsg"
                    )
                },
                onFailure = { _uiState.value = _uiState.value.copy(message = "恢复失败: ${it.message}") }
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
    val uiState by viewModel.uiState.collectAsState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> viewModel.onFileSelected(uri) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            GradientTopAppBar(
                title = { Text("数据备份与恢复") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Export section
            Text("数据导出", style = MaterialTheme.typography.titleMedium)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("导出为JSON", style = MaterialTheme.typography.bodyLarge)
                    Text("包含所有数据，可用于完整备份和恢复", style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = { viewModel.exportJson() },
                        enabled = !uiState.isExporting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.isExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("导出JSON备份")
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("导出为ZIP（含图片）", style = MaterialTheme.typography.bodyLarge)
                    Text("包含所有数据和图片，推荐用于完整备份", style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = { viewModel.exportZip() },
                        enabled = !uiState.isExporting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.isExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("导出ZIP备份")
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("导出为CSV", style = MaterialTheme.typography.bodyLarge)
                    Text("方便在Excel中查看数据", style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = { viewModel.exportCsv() },
                        enabled = !uiState.isExporting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.isExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("导出CSV")
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.primaryContainer, thickness = 1.dp)

            // Import section
            Text("数据恢复", style = MaterialTheme.typography.titleMedium)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("从备份恢复", style = MaterialTheme.typography.bodyLarge)
                    Text("选择之前导出的备份文件（支持ZIP和JSON格式），将清空当前数据并替换为备份数据", style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = { filePickerLauncher.launch(arrayOf("application/json", "application/zip")) },
                        enabled = !uiState.isImporting && !uiState.isPreviewing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.isImporting || uiState.isPreviewing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("选择备份文件")
                    }
                }
            }
        }
    }

    // Confirm import dialog
    if (uiState.showConfirmDialog && uiState.preview != null) {
        val p = uiState.preview!!
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(p.backupDate))
        AlertDialog(
            onDismissRequest = { viewModel.dismissDialog() },
            title = { Text("确认恢复数据") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("备份时间: $dateStr")
                    Text("版本: ${p.backupVersion}")
                    HorizontalDivider(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.padding(vertical = 4.dp))
                    Text("品牌: ${p.brandCount} 条")
                    Text("类型: ${p.categoryCount} 条")
                    Text("风格: ${p.styleCount} 条")
                    Text("季节: ${p.seasonCount} 条")
                    Text("套装: ${p.coordinateCount} 条")
                    Text("服饰: ${p.itemCount} 条")
                    Text("价格: ${p.priceCount} 条")
                    Text("付款: ${p.paymentCount} 条")
                    Text("穿搭日记: ${p.outfitLogCount} 条")
                    if (p.imageCount > 0) {
                        Text("图片: ${p.imageCount} 张")
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.padding(vertical = 4.dp))
                    Text("共 ${p.totalCount} 条数据")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "⚠ 恢复将清空当前所有数据",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmImport() }) {
                    Text("确认恢复")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDialog() }) {
                    Text("取消")
                }
            }
        )
    }
}
