package com.lolita.app.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.Season
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.theme.Pink100
import com.lolita.app.ui.theme.Pink400

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonManageScreen(
    onBack: () -> Unit,
    viewModel: SeasonManageViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
// PLACEHOLDER_SEASON_BODY

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            GradientTopAppBar(
                title = { Text("季节管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showAddDialog() },
                containerColor = Pink400,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加季节", tint = Color.White)
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
                Text("季节列表", style = MaterialTheme.typography.titleLarge)
            }
            item {
                HorizontalDivider(color = Pink100, thickness = 1.dp)
            }
            items(uiState.seasons) { season ->
                SeasonCard(season = season, onDelete = { viewModel.showDeleteConfirm(season) })
            }
        }
    }
// PLACEHOLDER_SEASON_DIALOGS

    if (uiState.showAddDialog) {
        AddSeasonDialog(
            onDismiss = { viewModel.hideAddDialog() },
            onConfirm = { name -> viewModel.addSeason(name) }
        )
    }

    if (uiState.showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteConfirm() },
            title = { Text("确认删除") },
            text = { Text("确定要删除季节 \"${uiState.showDeleteConfirm?.name}\" 吗？") },
            confirmButton = {
                TextButton(
                    onClick = { uiState.showDeleteConfirm?.let { viewModel.deleteSeason(it) } },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteConfirm() }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SeasonCard(season: Season, onDelete: () -> Unit) {
    LolitaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(season.name, style = MaterialTheme.typography.titleMedium)
            }
            IconButton(
                onClick = onDelete,
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "删除")
            }
        }
    }
}

@Composable
private fun AddSeasonDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加季节") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("季节名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name) }) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
