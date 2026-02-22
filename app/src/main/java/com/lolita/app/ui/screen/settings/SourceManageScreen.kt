package com.lolita.app.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.Source
import com.lolita.app.data.repository.SourceRepository
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SourceManageUiState(
    val sources: List<Source> = emptyList(),
    val showAddDialog: Boolean = false,
    val showDeleteConfirm: Source? = null,
    val editingSource: Source? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class SourceManageViewModel(
    private val sourceRepository: SourceRepository = com.lolita.app.di.AppModule.sourceRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SourceManageUiState())
    val uiState: StateFlow<SourceManageUiState> = _uiState.asStateFlow()

    init {
        loadSources()
    }

    fun loadSources() {
        viewModelScope.launch {
            sourceRepository.getAllSources().collect { sources ->
                _uiState.value = _uiState.value.copy(sources = sources, isLoading = false)
            }
        }
    }

    fun showAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = true)
    }

    fun hideAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false)
    }

    fun addSource(name: String) {
        viewModelScope.launch {
            try {
                sourceRepository.insertSource(Source(name = name.trim(), isPreset = false))
                hideAddDialog()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "添加失败：来源名称已存在")
            }
        }
    }

    fun showDeleteConfirm(source: Source) {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = source)
    }

    fun hideDeleteConfirm() {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = null)
    }

    fun deleteSource(source: Source) {
        viewModelScope.launch {
            try {
                sourceRepository.deleteSource(source)
                hideDeleteConfirm()
            } catch (e: Exception) {
                hideDeleteConfirm()
                _uiState.value = _uiState.value.copy(errorMessage = e.message ?: "删除失败")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun showEditDialog(source: Source) {
        _uiState.value = _uiState.value.copy(editingSource = source)
    }

    fun hideEditDialog() {
        _uiState.value = _uiState.value.copy(editingSource = null)
    }

    fun updateSource(source: Source, newName: String) {
        viewModelScope.launch {
            try {
                sourceRepository.updateSource(source.copy(name = newName.trim()), oldName = source.name)
                hideEditDialog()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "重命名失败：来源名称已存在"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceManageScreen(
    onBack: () -> Unit,
    viewModel: SourceManageViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            GradientTopAppBar(
                title = { Text("来源管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        SkinIcon(IconKey.ArrowBack)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showAddDialog() },
                containerColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(16.dp)
            ) {
                SkinIcon(IconKey.Add, tint = Color.White)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("来源列表", style = MaterialTheme.typography.titleLarge)
            }
            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.primaryContainer, thickness = 1.dp)
            }
            items(uiState.sources, key = { it.id }) { source ->
                SourceCard(source = source, onEdit = { viewModel.showEditDialog(source) }, onDelete = { viewModel.showDeleteConfirm(source) })
            }
        }
    }

    if (uiState.showAddDialog) {
        AddSourceDialog(
            onDismiss = { viewModel.hideAddDialog() },
            onConfirm = { name -> viewModel.addSource(name) }
        )
    }

    if (uiState.showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteConfirm() },
            title = { Text("确认删除") },
            text = { Text("确定要删除来源 \"${uiState.showDeleteConfirm?.name}\" 吗？") },
            confirmButton = {
                TextButton(
                    onClick = { uiState.showDeleteConfirm?.let { viewModel.deleteSource(it) } },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    SkinIcon(IconKey.Delete, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteConfirm() }) { Text("取消") }
            }
        )
    }

    if (uiState.editingSource != null) {
        EditSourceDialog(
            currentName = uiState.editingSource!!.name,
            onDismiss = { viewModel.hideEditDialog() },
            onConfirm = { newName -> viewModel.updateSource(uiState.editingSource!!, newName) }
        )
    }
}

@Composable
private fun SourceCard(source: Source, onEdit: () -> Unit, onDelete: () -> Unit) {
    LolitaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(source.name, style = MaterialTheme.typography.titleMedium)
            }
            IconButton(onClick = onEdit) {
                SkinIcon(IconKey.Edit)
            }
            IconButton(
                onClick = onDelete,
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                SkinIcon(IconKey.Delete)
            }
        }
    }
}

@Composable
private fun AddSourceDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加来源") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("来源名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name) }) {
                SkinIcon(IconKey.Save, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun EditSourceDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑来源") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("来源名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name) }) {
                SkinIcon(IconKey.Save, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}





