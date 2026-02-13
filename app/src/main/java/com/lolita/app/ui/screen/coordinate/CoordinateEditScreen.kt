package com.lolita.app.ui.screen.coordinate

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.theme.Pink400
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoordinateEditScreen(
    coordinateId: Long?,
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    viewModel: CoordinateEditViewModel = viewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            errorMessage = null
        }
    }

    LaunchedEffect(coordinateId) {
        viewModel.loadCoordinate(coordinateId)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GradientTopAppBar(
                title = { Text(if (coordinateId == null) "新建套装" else "编辑套装") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                val result = if (coordinateId == null) {
                                    viewModel.save()
                                } else {
                                    viewModel.update(coordinateId)
                                }
                                result.onSuccess {
                                    onSaveSuccess()
                                }.onFailure { e ->
                                    errorMessage = e.message ?: "保存失败"
                                }
                            }
                        },
                        enabled = uiState.name.isNotBlank() && !uiState.isSaving
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Check, contentDescription = "保存")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.name,
                onValueChange = { viewModel.updateName(it) },
                label = { Text("套装名称") },
                placeholder = { Text("例如: 玫瑰花园套装") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                ),
                singleLine = true,
                enabled = !uiState.isSaving
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.description,
                onValueChange = { viewModel.updateDescription(it) },
                label = { Text("描述") },
                placeholder = { Text("记录这个套装的搭配灵感...") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                ),
                minLines = 2,
                maxLines = 4,
                enabled = !uiState.isSaving
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "选择服饰 (${uiState.selectedItemIds.size}件)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Pink400
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.allItems.isEmpty()) {
                Text(
                    "暂无服饰，请先添加服饰",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(uiState.allItems, key = { it.id }) { item ->
                        val isSelected = item.id in uiState.selectedItemIds
                        Surface(
                            onClick = { viewModel.toggleItemSelection(item.id) },
                            shape = MaterialTheme.shapes.small,
                            color = if (isSelected)
                                Pink400.copy(alpha = 0.08f)
                            else
                                MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { viewModel.toggleItemSelection(item.id) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Pink400
                                    )
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        item.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                                    )
                                    val details = listOfNotNull(
                                        item.color,
                                        item.season,
                                        item.style
                                    ).joinToString(" · ")
                                    if (details.isNotEmpty()) {
                                        Text(
                                            details,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    // Show warning if item belongs to another coordinate
                                    val otherCoordId = item.coordinateId
                                    if (otherCoordId != null && !isSelected) {
                                        val coordName = uiState.coordinateNames[otherCoordId]
                                        if (coordName != null) {
                                            Text(
                                                "已属于套装「$coordName」",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
