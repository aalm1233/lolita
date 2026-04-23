package com.lolita.app.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.SharedLibraryCacheSummary
import com.lolita.app.data.local.entity.SharedLibraryPreviewItem
import com.lolita.app.data.local.entity.SharedLibrarySyncState
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.data.repository.SharedLibrarySyncOverview
import com.lolita.app.data.repository.SharedLibrarySyncRepository
import com.lolita.app.di.AppModule
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class SharedLibrarySyncUiState(
    val backendBaseUrl: String = "",
    val savedBackendBaseUrl: String = "",
    val hasUserEditedUrl: Boolean = false,
    val isSyncing: Boolean = false,
    val isClearing: Boolean = false,
    val syncState: SharedLibrarySyncState? = null,
    val cacheSummary: SharedLibraryCacheSummary = SharedLibraryCacheSummary(),
    val recentSharedItems: List<SharedLibraryPreviewItem> = emptyList(),
    val recentCatalogEntries: List<SharedLibraryPreviewItem> = emptyList(),
    val message: String? = null
)

class SharedLibrarySyncViewModel(
    private val appPreferences: AppPreferences = AppModule.appPreferences(),
    private val repository: SharedLibrarySyncRepository = AppModule.sharedLibrarySyncRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(SharedLibrarySyncUiState())
    val uiState: StateFlow<SharedLibrarySyncUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                appPreferences.sharedLibraryBaseUrl,
                repository.observeOverview()
            ) { backendUrl, overview ->
                backendUrl to overview
            }.collect { (backendUrl, overview) ->
                _uiState.value = mergeOverview(_uiState.value, backendUrl, overview)
            }
        }
    }

    fun updateBackendBaseUrl(value: String) {
        _uiState.value = _uiState.value.copy(
            backendBaseUrl = value,
            hasUserEditedUrl = true
        )
    }

    fun saveBackendBaseUrl() {
        val url = _uiState.value.backendBaseUrl.trim()
        viewModelScope.launch {
            appPreferences.setSharedLibraryBaseUrl(url)
            _uiState.value = _uiState.value.copy(
                savedBackendBaseUrl = url,
                hasUserEditedUrl = false,
                message = "后端地址已保存。"
            )
        }
    }

    fun sync(forceFull: Boolean) {
        val url = _uiState.value.backendBaseUrl.trim()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true, message = null)
            runCatching {
                appPreferences.setSharedLibraryBaseUrl(url)
                repository.sync(url, forceFull = forceFull)
            }.onSuccess { result ->
                val modeLabel = if (result.fullSync) "全量重建" else "增量同步"
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    savedBackendBaseUrl = url,
                    hasUserEditedUrl = false,
                    message = "$modeLabel 已完成，当前游标 ${result.nextCursor}。"
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    message = error.message ?: "同步失败。"
                )
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isClearing = true, message = null)
            runCatching {
                repository.clearCache()
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isClearing = false,
                    message = "共享资料缓存已清空。"
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isClearing = false,
                    message = error.message ?: "清空缓存失败。"
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private fun mergeOverview(
        current: SharedLibrarySyncUiState,
        backendUrl: String,
        overview: SharedLibrarySyncOverview
    ): SharedLibrarySyncUiState {
        val shouldRefreshInput = !current.hasUserEditedUrl ||
            current.backendBaseUrl.isBlank() ||
            current.backendBaseUrl.trim() == current.savedBackendBaseUrl.trim()

        return current.copy(
            backendBaseUrl = if (shouldRefreshInput) backendUrl else current.backendBaseUrl,
            savedBackendBaseUrl = backendUrl,
            syncState = overview.syncState,
            cacheSummary = overview.cacheSummary,
            recentSharedItems = overview.recentSharedItems,
            recentCatalogEntries = overview.recentCatalogEntries
        )
    }
}

@Composable
fun SharedLibrarySyncScreen(
    onBack: () -> Unit,
    viewModel: SharedLibrarySyncViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearMessage()
    }

    Scaffold(
        topBar = {
            GradientTopAppBar(
                title = { Text("共享资料同步") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        SkinIcon(IconKey.ArrowBack, modifier = Modifier.width(22.dp))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            UrlSection(
                uiState = uiState,
                onBackendUrlChange = viewModel::updateBackendBaseUrl,
                onSave = viewModel::saveBackendBaseUrl,
                onSync = { viewModel.sync(forceFull = false) },
                onFullSync = { viewModel.sync(forceFull = true) },
                onClearCache = { showClearDialog = true }
            )

            StatusSection(uiState.syncState, uiState.savedBackendBaseUrl)
            SummarySection(uiState.cacheSummary)
            PreviewSection(
                title = "最近同步的共享服饰",
                items = uiState.recentSharedItems
            )
            PreviewSection(
                title = "最近同步的共享图鉴",
                items = uiState.recentCatalogEntries
            )
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空共享缓存？") },
            text = {
                Text("这只会删除已同步的共享资料缓存，不会影响你自己的衣橱、图鉴、付款记录和穿搭记录。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearCache()
                    }
                ) {
                    Text("清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun UrlSection(
    uiState: SharedLibrarySyncUiState,
    onBackendUrlChange: (String) -> Unit,
    onSave: () -> Unit,
    onSync: () -> Unit,
    onFullSync: () -> Unit,
    onClearCache: () -> Unit
) {
    LolitaCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionTitle("后端地址")
            OutlinedTextField(
                value = uiState.backendBaseUrl,
                onValueChange = onBackendUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("例如：http://192.168.1.10:8080") },
                leadingIcon = {
                    SkinIcon(IconKey.Link)
                },
                singleLine = true
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onSave,
                    enabled = !uiState.isSyncing && !uiState.isClearing
                ) {
                    Text("保存地址")
                }

                Button(
                    onClick = onSync,
                    enabled = !uiState.isSyncing && !uiState.isClearing
                ) {
                    if (uiState.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    SkinIcon(IconKey.Refresh)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("同步")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    onClick = onFullSync,
                    enabled = !uiState.isSyncing && !uiState.isClearing
                ) {
                    SkinIcon(IconKey.Refresh)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("全量重建")
                }

                TextButton(
                    onClick = onClearCache,
                    enabled = !uiState.isSyncing && !uiState.isClearing
                ) {
                    SkinIcon(
                        key = IconKey.Delete,
                        tint = Color(0xFFD32F2F)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("清空缓存", color = Color(0xFFD32F2F))
                }
            }
        }
    }
}

@Composable
private fun StatusSection(
    syncState: SharedLibrarySyncState?,
    savedBackendBaseUrl: String
) {
    LolitaCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionTitle("同步状态")
            DetailLine("已保存的后端地址", savedBackendBaseUrl.ifBlank { "未设置" })
            DetailLine("资源地址", syncState?.assetBaseUrl?.ifBlank { "尚未同步" } ?: "尚未同步")
            DetailLine("协议版本", syncState?.schemaVersion?.toString() ?: "0")
            DetailLine("当前游标", syncState?.nextCursor?.toString() ?: "0")
            DetailLine("上次同步时间", formatTimestamp(syncState?.lastSyncedAt))
            syncState?.lastError?.takeIf { it.isNotBlank() }?.let { lastError ->
                DetailLine("最近一次错误", lastError, MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SummarySection(summary: SharedLibraryCacheSummary) {
    LolitaCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SectionTitle("缓存概览")
            SummaryLine("品牌", summary.brandCount)
            SummaryLine("类别", summary.categoryCount)
            SummaryLine("风格", summary.styleCount)
            SummaryLine("季节", summary.seasonCount)
            SummaryLine("来源", summary.sourceCount)
            SummaryLine("图鉴", summary.catalogCount)
            SummaryLine("套装", summary.coordinateCount)
            SummaryLine("共享服饰", summary.itemCount)
            SummaryLine("价格档期", summary.pricePlanCount)
        }
    }
}

@Composable
private fun PreviewSection(
    title: String,
    items: List<SharedLibraryPreviewItem>
) {
    LolitaCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionTitle(title)
            if (items.isEmpty()) {
                Text(
                    text = "还没有同步数据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                items.forEachIndexed { index, item ->
                    if (index > 0) {
                        HorizontalDivider()
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = item.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = formatTimestamp(item.updatedAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor
        )
    }
}

@Composable
private fun SummaryLine(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatTimestamp(value: Long?): String {
    if (value == null || value <= 0L) {
        return "从未同步"
    }
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))
}
