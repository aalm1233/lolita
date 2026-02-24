package com.lolita.app.ui.screen.coordinate

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import com.lolita.app.ui.component.MultiImageEditor
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.UnsavedChangesHandler
import com.lolita.app.ui.screen.common.parseColorsJson
import kotlinx.coroutines.launch
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon

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
    var showError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(coordinateId) {
        viewModel.loadCoordinate(coordinateId)
    }

    if (showError != null) {
        AlertDialog(
            onDismissRequest = { showError = null },
            title = { Text("保存失败") },
            text = { Text(showError ?: "") },
            confirmButton = {
                TextButton(onClick = { showError = null }) {
                    Text("确定")
                }
            }
        )
    }

    UnsavedChangesHandler(
        hasUnsavedChanges = viewModel.hasUnsavedChanges,
        onBack = onBack
    )

    Scaffold(
        topBar = {
            GradientTopAppBar(
                title = { Text(if (coordinateId == null) "新建套装" else "编辑套装") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        SkinIcon(IconKey.ArrowBack)
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
                                    showError = e.message ?: "保存失败"
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
                            SkinIcon(IconKey.Save)
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

            // 封面图选择
            MultiImageEditor(
                imageUrls = uiState.imageUrls,
                maxImages = 5,
                onAddImage = { viewModel.addImage(it) },
                onRemoveImage = { viewModel.removeImage(it) }
            )
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
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            var itemSearchQuery by remember { mutableStateOf("") }

            OutlinedTextField(
                value = itemSearchQuery,
                onValueChange = { itemSearchQuery = it },
                label = { Text("搜索服饰") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { SkinIcon(IconKey.Search, modifier = Modifier.size(20.dp)) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            val filteredItems = uiState.allItems.filter {
                itemSearchQuery.isBlank() || it.name.contains(itemSearchQuery, ignoreCase = true)
            }

            if (filteredItems.isEmpty()) {
                Text(
                    if (itemSearchQuery.isBlank()) "暂无服饰，请先添加服饰" else "未找到匹配的服饰",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        val isSelected = item.id in uiState.selectedItemIds
                        Surface(
                            onClick = { viewModel.toggleItemSelection(item.id) },
                            shape = MaterialTheme.shapes.small,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
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
                                        checkedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        item.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                                    )
                                    val colorDisplay = parseColorsJson(item.colors).joinToString("、").ifEmpty { null }
                                    val details = listOfNotNull(
                                        colorDisplay,
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
