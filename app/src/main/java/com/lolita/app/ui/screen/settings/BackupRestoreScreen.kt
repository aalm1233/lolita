package com.lolita.app.ui.screen.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.file.BackupManager
import com.lolita.app.data.file.BackupPreview
import com.lolita.app.di.AppModule
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupRestoreViewModel : ViewModel() {
    private val backupManager: BackupManager = AppModule.backupManager()

    var isExporting by mutableStateOf(false)
        private set
    var isImporting by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var preview by mutableStateOf<BackupPreview?>(null)
        private set
    var pendingImportUri by mutableStateOf<Uri?>(null)
        private set
    var showConfirmDialog by mutableStateOf(false)
        private set

    fun exportJson() {
        viewModelScope.launch {
            isExporting = true
            message = null
            backupManager.exportToJson().fold(
                onSuccess = { message = "JSON备份成功！文件已保存到下载目录" },
                onFailure = { message = "备份失败: ${it.message}" }
            )
            isExporting = false
        }
    }
    fun exportCsv() {
        viewModelScope.launch {
            isExporting = true
            message = null
            backupManager.exportToCsv().fold(
                onSuccess = { message = "CSV导出成功！文件已保存到下载目录" },
                onFailure = { message = "导出失败: ${it.message}" }
            )
            isExporting = false
        }
    }

    fun onFileSelected(uri: Uri?) {
        if (uri == null) return
        pendingImportUri = uri
        viewModelScope.launch {
            backupManager.previewBackup(uri).fold(
                onSuccess = {
                    preview = it
                    showConfirmDialog = true
                },
                onFailure = { message = "无法读取备份文件: ${it.message}" }
            )
        }
    }

    fun confirmImport() {
        val uri = pendingImportUri ?: return
        showConfirmDialog = false
        viewModelScope.launch {
            isImporting = true
            message = null
            backupManager.importFromJson(uri).fold(
                onSuccess = { summary ->
                    message = "恢复完成！导入 ${summary.totalImported} 条，跳过 ${summary.totalSkipped} 条"
                },
                onFailure = { message = "恢复失败: ${it.message}" }
            )
            isImporting = false
            pendingImportUri = null
            preview = null
        }
    }

    fun dismissDialog() {
        showConfirmDialog = false
        pendingImportUri = null
        preview = null
    }

    fun clearMessage() { message = null }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    onBack: () -> Unit,
    viewModel: BackupRestoreViewModel = viewModel()
) {
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> viewModel.onFileSelected(uri) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.message) {
        viewModel.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("数据备份与恢复") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
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
                        enabled = !viewModel.isExporting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (viewModel.isExporting) {
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
                    Text("导出为CSV", style = MaterialTheme.typography.bodyLarge)
                    Text("方便在Excel中查看数据", style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = { viewModel.exportCsv() },
                        enabled = !viewModel.isExporting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (viewModel.isExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("导出CSV")
                    }
                }
            }

            Divider()

            // Import section
            Text("数据恢复", style = MaterialTheme.typography.titleMedium)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("从JSON备份恢复", style = MaterialTheme.typography.bodyLarge)
                    Text("选择之前导出的JSON备份文件，已有数据不会被覆盖", style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = { filePickerLauncher.launch(arrayOf("application/json")) },
                        enabled = !viewModel.isImporting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (viewModel.isImporting) {
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
    if (viewModel.showConfirmDialog && viewModel.preview != null) {
        val p = viewModel.preview!!
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(p.backupDate))
        AlertDialog(
            onDismissRequest = { viewModel.dismissDialog() },
            title = { Text("确认恢复数据") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("备份时间: $dateStr")
                    Text("版本: ${p.backupVersion}")
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("品牌: ${p.brandCount} 条")
                    Text("类型: ${p.categoryCount} 条")
                    Text("套装: ${p.coordinateCount} 条")
                    Text("服饰: ${p.itemCount} 条")
                    Text("价格: ${p.priceCount} 条")
                    Text("付款: ${p.paymentCount} 条")
                    Text("穿搭日记: ${p.outfitLogCount} 条")
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("共 ${p.totalCount} 条数据")
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
