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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
                message = "Backend URL saved."
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
                val modeLabel = if (result.fullSync) "Full sync" else "Incremental sync"
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    savedBackendBaseUrl = url,
                    hasUserEditedUrl = false,
                    message = "$modeLabel completed. Cursor ${result.nextCursor}."
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    message = error.message ?: "Sync failed."
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
                    message = "Shared library cache cleared."
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isClearing = false,
                    message = error.message ?: "Clearing cache failed."
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
                title = { Text("Shared Library Sync") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
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
                title = "Recently synced shared items",
                items = uiState.recentSharedItems
            )
            PreviewSection(
                title = "Recently synced catalog entries",
                items = uiState.recentCatalogEntries
            )
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear shared cache?") },
            text = {
                Text(
                    "This only removes synchronized shared-library data. " +
                        "It will not touch your own wardrobe, catalog, payments, or outfit records."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearCache()
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
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
            SectionTitle("Backend")
            OutlinedTextField(
                value = uiState.backendBaseUrl,
                onValueChange = onBackendUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Example: http://192.168.1.10:8080") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null
                    )
                },
                singleLine = true
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onSave,
                    enabled = !uiState.isSyncing && !uiState.isClearing
                ) {
                    Text("Save URL")
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
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sync")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    onClick = onFullSync,
                    enabled = !uiState.isSyncing && !uiState.isClearing
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Full rebuild")
                }

                TextButton(
                    onClick = onClearCache,
                    enabled = !uiState.isSyncing && !uiState.isClearing
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = Color(0xFFD32F2F)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear cache", color = Color(0xFFD32F2F))
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
            SectionTitle("Status")
            DetailLine("Saved backend URL", savedBackendBaseUrl.ifBlank { "Not set" })
            DetailLine("Asset base URL", syncState?.assetBaseUrl?.ifBlank { "Not synced yet" } ?: "Not synced yet")
            DetailLine("Schema version", syncState?.schemaVersion?.toString() ?: "0")
            DetailLine("Cursor", syncState?.nextCursor?.toString() ?: "0")
            DetailLine("Last synced", formatTimestamp(syncState?.lastSyncedAt))
            syncState?.lastError?.takeIf { it.isNotBlank() }?.let { lastError ->
                DetailLine("Last error", lastError, MaterialTheme.colorScheme.error)
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
            SectionTitle("Cache Summary")
            SummaryLine("Brands", summary.brandCount)
            SummaryLine("Categories", summary.categoryCount)
            SummaryLine("Styles", summary.styleCount)
            SummaryLine("Seasons", summary.seasonCount)
            SummaryLine("Sources", summary.sourceCount)
            SummaryLine("Catalog entries", summary.catalogCount)
            SummaryLine("Coordinates", summary.coordinateCount)
            SummaryLine("Shared items", summary.itemCount)
            SummaryLine("Price plans", summary.pricePlanCount)
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
                    text = "No synchronized data yet.",
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
        return "Never"
    }
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))
}
