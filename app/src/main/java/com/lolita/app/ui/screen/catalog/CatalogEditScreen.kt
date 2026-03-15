package com.lolita.app.ui.screen.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.Brand
import com.lolita.app.data.local.entity.Category
import com.lolita.app.ui.component.MultiImageEditor
import com.lolita.app.ui.screen.common.BrandLogo
import com.lolita.app.ui.screen.common.ColorSelector
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.UnsavedChangesHandler
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CatalogEditScreen(
    catalogEntryId: Long?,
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    viewModel: CatalogEditViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var showError by remember { mutableStateOf<String?>(null) }
    var hasAttemptedSave by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    UnsavedChangesHandler(
        hasUnsavedChanges = viewModel.hasUnsavedChanges,
        onBack = onBack
    )

    LaunchedEffect(catalogEntryId) {
        viewModel.loadCatalogEntry(catalogEntryId)
    }

    showError?.let { message ->
        AlertDialog(
            onDismissRequest = { showError = null },
            title = { Text("提示") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { showError = null }) {
                    Text("确定")
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("删除后图鉴记录会移除，但已转化的衣橱条目不会被删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        scope.launch {
                            viewModel.deleteCatalogEntry()
                                .onSuccess { onSaveSuccess() }
                                .onFailure { showError = it.message ?: "删除失败" }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            GradientTopAppBar(
                title = { Text(if (catalogEntryId == null) "新建图鉴" else "编辑图鉴") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        SkinIcon(IconKey.ArrowBack)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            hasAttemptedSave = true
                            scope.launch {
                                viewModel.saveCatalogEntry()
                                    .onSuccess { onSaveSuccess() }
                                    .onFailure { showError = it.message ?: "保存失败" }
                            }
                        },
                        enabled = !uiState.isSaving && uiState.name.isNotBlank()
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
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
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = viewModel::updateName,
                    label = { Text("图鉴名称 *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = hasAttemptedSave && uiState.name.isBlank(),
                    supportingText = if (hasAttemptedSave && uiState.name.isBlank()) {
                        { Text("请输入图鉴名称") }
                    } else {
                        null
                    }
                )

                OutlinedTextField(
                    value = uiState.seriesName,
                    onValueChange = viewModel::updateSeriesName,
                    label = { Text("系列名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = uiState.referenceUrl,
                    onValueChange = viewModel::updateReferenceUrl,
                    label = { Text("来源链接") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("https://") }
                )

                CatalogBrandSelector(
                    selectedBrandId = uiState.brandId,
                    brands = uiState.brands,
                    onBrandSelected = viewModel::updateBrand
                )

                CatalogCategorySelector(
                    selectedCategoryId = uiState.categoryId,
                    categories = uiState.categories,
                    onCategorySelected = viewModel::updateCategory
                )

                ColorSelector(
                    selectedColors = uiState.colors,
                    onColorsChanged = viewModel::updateColors
                )

                CatalogSingleChoiceChips(
                    title = "风格",
                    selectedValue = uiState.style,
                    options = uiState.styleOptions,
                    onValueSelected = viewModel::updateStyle
                )

                CatalogSingleChoiceChips(
                    title = "季节",
                    selectedValue = uiState.season,
                    options = uiState.seasonOptions,
                    onValueSelected = viewModel::updateSeason
                )

                CatalogSingleChoiceChips(
                    title = "来源",
                    selectedValue = uiState.source,
                    options = uiState.sourceOptions,
                    onValueSelected = viewModel::updateSource
                )

                OutlinedTextField(
                    value = uiState.size.orEmpty(),
                    onValueChange = { viewModel.updateSize(it.ifBlank { null }) },
                    label = { Text("尺码") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "图片",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MultiImageEditor(
                        imageUrls = uiState.imageUrls,
                        maxImages = 9,
                        onAddImage = viewModel::addImage,
                        onRemoveImage = viewModel::removeImage
                    )
                }

                OutlinedTextField(
                    value = uiState.description,
                    onValueChange = viewModel::updateDescription,
                    label = { Text("描述") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8
                )

                if (catalogEntryId != null && uiState.entry != null) {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        SkinIcon(IconKey.Delete)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("删除图鉴")
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogBrandSelector(
    selectedBrandId: Long?,
    brands: List<Brand>,
    onBrandSelected: (Long?) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val selectedName = brands.find { it.id == selectedBrandId }?.name.orEmpty()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("品牌") },
            trailingIcon = { SkinIcon(IconKey.Search) },
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }

    if (showDialog) {
        var searchQuery by remember { mutableStateOf("") }
        val filteredBrands = remember(searchQuery, brands) {
            if (searchQuery.isBlank()) {
                brands
            } else {
                brands.filter { it.name.contains(searchQuery, ignoreCase = true) }
            }
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("选择品牌") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("搜索品牌") },
                        leadingIcon = { SkinIcon(IconKey.Search) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    TextButton(
                        onClick = {
                            onBrandSelected(null)
                            showDialog = false
                        }
                    ) {
                        Text("不设置品牌")
                    }
                    LazyColumn(modifier = Modifier.height(320.dp)) {
                        items(filteredBrands, key = { it.id }) { brand ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onBrandSelected(brand.id)
                                        showDialog = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                BrandLogo(brand = brand, size = 24.dp)
                                Text(
                                    text = brand.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (brand.id == selectedBrandId) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }
}

@Composable
private fun CatalogCategorySelector(
    selectedCategoryId: Long?,
    categories: List<Category>,
    onCategorySelected: (Long?) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val selectedName = categories.find { it.id == selectedCategoryId }?.name.orEmpty()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("分类") },
            trailingIcon = { SkinIcon(IconKey.Search) },
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }

    if (showDialog) {
        var searchQuery by remember { mutableStateOf("") }
        val filteredCategories = remember(searchQuery, categories) {
            if (searchQuery.isBlank()) {
                categories
            } else {
                categories.filter { it.name.contains(searchQuery, ignoreCase = true) }
            }
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("选择分类") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("搜索分类") },
                        leadingIcon = { SkinIcon(IconKey.Search) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    TextButton(
                        onClick = {
                            onCategorySelected(null)
                            showDialog = false
                        }
                    ) {
                        Text("不设置分类")
                    }
                    LazyColumn(modifier = Modifier.height(320.dp)) {
                        items(filteredCategories, key = { it.id }) { category ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onCategorySelected(category.id)
                                        showDialog = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 4.dp)
                            ) {
                                Text(
                                    text = category.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (category.id == selectedCategoryId) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatalogSingleChoiceChips(
    title: String,
    selectedValue: String?,
    options: List<String>,
    onValueSelected: (String?) -> Unit
) {
    if (options.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedValue == null,
                onClick = { onValueSelected(null) },
                label = { Text("不设置") }
            )
            options.forEach { option ->
                FilterChip(
                    selected = selectedValue == option,
                    onClick = {
                        onValueSelected(if (selectedValue == option) null else option)
                    },
                    label = { Text(option) }
                )
            }
        }
    }
}
