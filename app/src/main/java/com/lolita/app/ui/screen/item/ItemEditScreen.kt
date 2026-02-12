package com.lolita.app.ui.screen.item

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.entity.ItemPriority
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.ui.theme.Pink400
import kotlinx.coroutines.launch

/**
 * Item Edit Screen - 添加/编辑服饰界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditScreen(
    itemId: Long?,
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    viewModel: ItemEditViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var showError by remember { mutableStateOf<String?>(null) }
    var hasAttemptedSave by remember { mutableStateOf(false) }

    // Load item data if editing
    LaunchedEffect(itemId) {
        viewModel.loadItem(itemId ?: 0L)
    }

    // Show error dialog
    if (showError != null) {
        AlertDialog(
            onDismissRequest = { showError = null },
            title = { Text("提示") },
            text = { Text(showError ?: "") },
            confirmButton = {
                TextButton(onClick = { showError = null }) {
                    Text("确定")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (itemId == null) "添加服饰" else "编辑服饰") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    // Save button
                    IconButton(
                        onClick = {
                            hasAttemptedSave = true
                            coroutineScope.launch {
                                viewModel.saveItem(
                                    onSuccess = { onSaveSuccess() },
                                    onError = { showError = it }
                                )
                            }
                        },
                        enabled = !uiState.isSaving
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Check, "保存")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Pink400,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
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
                // Name field
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = { viewModel.updateName(it) },
                    label = { Text("服饰名称 *") },
                    placeholder = { Text("例如：梦幻童话JSK") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = hasAttemptedSave && uiState.name.isBlank(),
                    supportingText = if (hasAttemptedSave && uiState.name.isBlank()) {
                        { Text("请输入服饰名称") }
                    } else null
                )

                // Description field
                OutlinedTextField(
                    value = uiState.description,
                    onValueChange = { viewModel.updateDescription(it) },
                    label = { Text("描述") },
                    placeholder = { Text("例如：粉色款，无瑕疵") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )

                // Brand selector
                BrandSelector(
                    selectedBrandId = uiState.brandId,
                    brands = uiState.brands,
                    onBrandSelected = { viewModel.updateBrand(it) },
                    isError = hasAttemptedSave && uiState.brandId == 0L
                )

                // Category selector
                CategorySelector(
                    selectedCategoryId = uiState.categoryId,
                    categories = uiState.categories,
                    onCategorySelected = { viewModel.updateCategory(it) },
                    isError = hasAttemptedSave && uiState.categoryId == 0L
                )

                // Coordinate selector (optional)
                CoordinateSelector(
                    selectedCoordinateId = uiState.coordinateId,
                    coordinates = uiState.coordinates,
                    onCoordinateSelected = { viewModel.updateCoordinate(it) }
                )

                // Status selector
                StatusSelector(
                    selectedStatus = uiState.status,
                    onStatusSelected = { viewModel.updateStatus(it) }
                )

                // Priority selector (only show when status is WISHED)
                if (uiState.status == ItemStatus.WISHED) {
                    PrioritySelector(
                        selectedPriority = uiState.priority,
                        onPrioritySelected = { viewModel.updatePriority(it) }
                    )
                }

                // Image upload placeholder
                ImageUploaderSection(
                    imageUrl = uiState.imageUrl,
                    onImageChanged = { viewModel.updateImageUrl(it) }
                )

                // Delete button (only when editing)
                if (itemId != null && uiState.item != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                viewModel.deleteItem(
                                    onSuccess = { onSaveSuccess() },
                                    onError = { showError = it }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(Modifier.width(8.dp))
                        Text("删除服饰")
                    }
                }
            }
        }
    }
}

/**
 * Brand Selector Component
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrandSelector(
    selectedBrandId: Long,
    brands: List<com.lolita.app.data.local.entity.Brand>,
    onBrandSelected: (Long) -> Unit,
    isError: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = brands.find { it.id == selectedBrandId }?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("品牌 *") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            isError = isError,
            supportingText = if (isError) {
                { Text("请选择品牌") }
            } else null
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            brands.forEach { brand ->
                DropdownMenuItem(
                    text = { Text(brand.name) },
                    onClick = {
                        onBrandSelected(brand.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Category Selector Component
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySelector(
    selectedCategoryId: Long,
    categories: List<com.lolita.app.data.local.entity.Category>,
    onCategorySelected: (Long) -> Unit,
    isError: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = categories.find { it.id == selectedCategoryId }?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("类型 *") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            isError = isError,
            supportingText = if (isError) {
                { Text("请选择类型") }
            } else null
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        onCategorySelected(category.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Coordinate Selector Component
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoordinateSelector(
    selectedCoordinateId: Long?,
    coordinates: List<com.lolita.app.data.local.entity.Coordinate>,
    onCoordinateSelected: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = if (selectedCoordinateId == null) {
        "无"
    } else {
        coordinates.find { it.id == selectedCoordinateId }?.name ?: "无"
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("所属套装 (可选)") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("无") },
                onClick = {
                    onCoordinateSelected(null)
                    expanded = false
                }
            )
            coordinates.forEach { coordinate ->
                DropdownMenuItem(
                    text = { Text(coordinate.name) },
                    onClick = {
                        onCoordinateSelected(coordinate.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Status Selector Component
 */
@Composable
private fun StatusSelector(
    selectedStatus: ItemStatus,
    onStatusSelected: (ItemStatus) -> Unit
) {
    Column {
        Text(
            text = "状态 *",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ItemStatus.values().forEach { status ->
                FilterChip(
                    selected = selectedStatus == status,
                    onClick = { onStatusSelected(status) },
                    label = {
                        Text(
                            when (status) {
                                ItemStatus.OWNED -> "已拥有"
                                ItemStatus.WISHED -> "愿望单"
                            }
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Priority Selector Component
 */
@Composable
private fun PrioritySelector(
    selectedPriority: ItemPriority,
    onPrioritySelected: (ItemPriority) -> Unit
) {
    Column {
        Text(
            text = "愿望单优先级",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ItemPriority.values().forEach { priority ->
                FilterChip(
                    selected = selectedPriority == priority,
                    onClick = { onPrioritySelected(priority) },
                    label = {
                        Text(
                            when (priority) {
                                ItemPriority.HIGH -> "高"
                                ItemPriority.MEDIUM -> "中"
                                ItemPriority.LOW -> "低"
                            }
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Image Uploader Section Component
 */
@Composable
private fun ImageUploaderSection(
    imageUrl: String?,
    onImageChanged: (String?) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val internalPath = ImageFileHelper.copyToInternalStorage(context, it)
                onImageChanged(internalPath)
            }
        }
    }

    Column {
        Text(
            text = "图片",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                if (imageUrl != null) {
                    Box {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = "服饰图片",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = {
                                ImageFileHelper.deleteImage(imageUrl)
                                onImageChanged(null)
                            },
                            modifier = Modifier.align(Alignment.TopEnd),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                            )
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "删除图片",
                                tint = MaterialTheme.colorScheme.onError
                            )
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "点击添加图片",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "(从相册选择)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
