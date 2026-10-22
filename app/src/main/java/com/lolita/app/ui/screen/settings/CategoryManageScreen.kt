package com.lolita.app.ui.screen.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.CategoryGroup
import com.lolita.app.domain.model.*
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon

@Composable
fun CategoryManageScreen(onBack: () -> Unit, viewModel: CategoryManageViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.errorMessage) { state.errorMessage?.let { snackbar.showSnackbar(it); viewModel.clearError() } }
    Scaffold(containerColor = Color.Transparent, topBar = {
        GradientTopAppBar(title = { Text("类型管理") }, navigationIcon = { IconButton(onClick = onBack) { SkinIcon(IconKey.ArrowBack) } })
    }, floatingActionButton = { FloatingActionButton(onClick = viewModel::showAddDialog) { SkinIcon(IconKey.Add) } },
        snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(LolitaSkin.current.spacingLarge), verticalArrangement = Arrangement.spacedBy(LolitaSkin.current.cardGap)) {
            item { Text("类型决定试衣和展示方式，改名不会改变类型。", style = MaterialTheme.typography.bodyMedium) }
            items(state.categories, key = { it.id }) { category -> LolitaCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(category.name, style = MaterialTheme.typography.titleSmall)
                        Text("${category.categorySemantic.family.label} · ${(category.defaultWearRole ?: category.categorySemantic.defaultWearRole).label}", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { viewModel.showEditDialog(category) }) { SkinIcon(IconKey.Edit) }
                    IconButton(onClick = { viewModel.showDeleteConfirm(category) }) { SkinIcon(IconKey.Delete) }
                }
            } }
        }
    }
    if (state.showAddDialog) CategoryDialog(null, viewModel::hideAddDialog) { name, semantic, role ->
        viewModel.addCategory(name, if (semantic.family == WardrobeFamily.CLOTHING) CategoryGroup.CLOTHING else CategoryGroup.ACCESSORY, semantic, role)
    }
    state.editingCategory?.let { category -> CategoryDialog(category, viewModel::hideEditDialog) { name, semantic, role ->
        viewModel.updateCategory(category, name, if (semantic.family == WardrobeFamily.CLOTHING) CategoryGroup.CLOTHING else category.group, semantic, role)
    } }
    state.showDeleteConfirm?.let { category -> AlertDialog(onDismissRequest = viewModel::hideDeleteConfirm,
        title = { Text("删除类型") }, text = { Text("确定删除“${category.name}”？有物品使用时需先迁移，物品不会被删除。") },
        confirmButton = { TextButton(onClick = { viewModel.deleteCategory(category) }) { Text("删除") } },
        dismissButton = { TextButton(onClick = viewModel::hideDeleteConfirm) { Text("取消") } }) }
    state.pendingSemanticChange?.let { category -> AlertDialog(onDismissRequest = viewModel::cancelSemanticChange,
        title = { Text("更改关联物品的类型？") }, text = { Text("${state.affectedItemCount} 件物品将使用“${category.categorySemantic.label}”及对应穿戴位置。原规格、图片和订单完整保留。") },
        confirmButton = { TextButton(onClick = viewModel::confirmSemanticChange) { Text("确认变更") } },
        dismissButton = { TextButton(onClick = viewModel::cancelSemanticChange) { Text("取消") } }) }
}

@Composable
private fun CategoryDialog(category: Category?, onDismiss: () -> Unit, onSave: (String, CategorySemantic, TryOnRole?) -> Unit) {
    var name by remember { mutableStateOf(category?.name.orEmpty()) }
    var semantic by remember { mutableStateOf(category?.categorySemantic ?: CategorySemantic.UNCONFIRMED) }
    var family by remember { mutableStateOf(semantic.family) }
    var role by remember { mutableStateOf(category?.defaultWearRole) }
    var query by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (category == null) "添加类型" else "编辑类型") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("类型名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.horizontalScroll(rememberScrollState())) { WardrobeFamily.entries.forEach { value ->
                FilterChip(selected = family == value, onClick = { family = value }, label = { Text(value.label) })
            } }
            OutlinedTextField(query, { query = it }, label = { Text("搜索类型") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            LazyColumn(Modifier.heightIn(max = 150.dp)) { items(CategorySemantic.entries.filter { it.family == family && it.label.contains(query, true) }) { candidate ->
                TextButton(onClick = { semantic = candidate; role = null }) { Text("${if (semantic == candidate) "✓ " else ""}${candidate.label}") }
            } }
            Text("默认穿戴位置：${(role ?: semantic.defaultWearRole).label}")
            Row(Modifier.horizontalScroll(rememberScrollState())) { TryOnRole.entries.forEach { value ->
                FilterChip(selected = (role ?: semantic.defaultWearRole) == value, onClick = { role = value }, label = { Text(value.label) })
            } }
        }
    }, confirmButton = { TextButton(enabled = name.isNotBlank() && semantic.family == family && (category != null || semantic != CategorySemantic.UNCONFIRMED), onClick = { onSave(name, semantic, role) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
