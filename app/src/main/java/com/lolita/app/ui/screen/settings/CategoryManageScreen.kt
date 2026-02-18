package com.lolita.app.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.CategoryGroup
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.LolitaCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManageScreen(
    onBack: () -> Unit,
    viewModel: CategoryManageViewModel = viewModel()
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
                title = { Text("类型管理") },
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
                containerColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加类型", tint = Color.White)
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
                Text(
                    "服饰类型列表",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.primaryContainer, thickness = 1.dp)
            }

            items(uiState.categories, key = { it.id }) { category ->
                CategoryCard(
                    category = category,
                    onEdit = { viewModel.showEditDialog(category) },
                    onDelete = { viewModel.showDeleteConfirm(category) }
                )
            }
        }
    }

    if (uiState.showAddDialog) {
        AddCategoryDialog(
            onDismiss = { viewModel.hideAddDialog() },
            onConfirm = { name, group ->
                viewModel.addCategory(name, group)
            }
        )
    }

    if (uiState.showDeleteConfirm != null) {
        DeleteConfirmDialog(
            categoryName = uiState.showDeleteConfirm?.name ?: "",
            onDismiss = { viewModel.hideDeleteConfirm() },
            onConfirm = {
                uiState.showDeleteConfirm?.let { viewModel.deleteCategory(it) }
            }
        )
    }

    if (uiState.editingCategory != null) {
        EditCategoryDialog(
            currentName = uiState.editingCategory!!.name,
            currentGroup = uiState.editingCategory!!.group,
            onDismiss = { viewModel.hideEditDialog() },
            onConfirm = { newName, newGroup ->
                viewModel.updateCategory(uiState.editingCategory!!, newName, newGroup)
            }
        )
    }
}

@Composable
private fun CategoryCard(
    category: Category,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    LolitaCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    category.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    if (category.group == CategoryGroup.CLOTHING) "服装" else "配饰",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "编辑")
            }

            IconButton(
                onClick = onDelete,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = "删除")
            }
        }
    }
}

@Composable
private fun AddCategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, CategoryGroup) -> Unit
) {
    var categoryName by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var selectedGroup by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(CategoryGroup.CLOTHING) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加类型") },
        text = {
            Column {
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    label = { Text("类型名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedGroup == CategoryGroup.CLOTHING,
                        onClick = { selectedGroup = CategoryGroup.CLOTHING },
                        label = { Text("服装") }
                    )
                    FilterChip(
                        selected = selectedGroup == CategoryGroup.ACCESSORY,
                        onClick = { selectedGroup = CategoryGroup.ACCESSORY },
                        label = { Text("配饰") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (categoryName.isNotBlank()) {
                        onConfirm(categoryName, selectedGroup)
                    }
                }
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun DeleteConfirmDialog(
    categoryName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认删除") },
        text = { Text("确定要删除类型 \"$categoryName\" 吗？") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun EditCategoryDialog(
    currentName: String,
    currentGroup: CategoryGroup,
    onDismiss: () -> Unit,
    onConfirm: (String, CategoryGroup) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var selectedGroup by remember { mutableStateOf(currentGroup) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑类型") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("类型名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedGroup == CategoryGroup.CLOTHING,
                        onClick = { selectedGroup = CategoryGroup.CLOTHING },
                        label = { Text("服装") }
                    )
                    FilterChip(
                        selected = selectedGroup == CategoryGroup.ACCESSORY,
                        onClick = { selectedGroup = CategoryGroup.ACCESSORY },
                        label = { Text("配饰") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name, selectedGroup) }) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
