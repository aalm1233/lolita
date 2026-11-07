package com.lolita.app.ui.screen.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.lolita.app.data.diagnostics.LocalDiagnostics
import com.lolita.app.ui.theme.skin.icon.IconKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class DiagnosticExportViewModel(
    private val context: android.content.Context = com.lolita.app.di.AppModule.context()
) : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    fun clearMessage() { _message.value = null }
    fun export(uri: android.net.Uri) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { LocalDiagnostics.export(context, uri) }
                _message.value = "诊断记录已保存"
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _message.value = "诊断记录未保存，请重新选择位置后重试" }
            finally { _busy.value = false }
        }
    }
}

@Composable
internal fun DiagnosticExportEntry(model: DiagnosticExportViewModel = viewModel()) {
    val context = LocalContext.current
    val exporting by model.busy.collectAsState()
    val message by model.message.collectAsState()
    var choosing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(message) {
        message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); model.clearMessage() }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        choosing = false
        uri?.let(model::export)
    }
    SettingsMenuItem(
        title = if (exporting) "正在导出诊断记录…" else "导出诊断记录",
        description = "设备、异常位置与内存状态；不含衣橱内容",
        iconKey = IconKey.FileOpen,
        iconTint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag("diagnostic-export"),
        onClick = {
            if (!exporting && !choosing) try { choosing = true; picker.launch("lolita-diagnostics.txt") }
            catch (_: Exception) { choosing = false; Toast.makeText(context, "无法打开保存位置，请检查文件应用后重试", Toast.LENGTH_LONG).show() }
        }
    )
}
