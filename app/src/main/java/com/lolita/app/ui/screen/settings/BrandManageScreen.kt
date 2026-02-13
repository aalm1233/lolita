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
import com.lolita.app.data.local.entity.Brand
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.theme.Pink100
import com.lolita.app.ui.theme.Pink400

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrandManageScreen(
    onBack: () -> Unit,
    viewModel: BrandManageViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadBrands()
    }

    Scaffold(
        topBar = {
            GradientTopAppBar(
                title = { Text("品牌管理") },
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
                Icon(Icons.Default.Add, contentDescription = "添加品牌", tint = Color.White)
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
                    "品牌列表",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            item {
                HorizontalDivider(color = Pink100, thickness = 1.dp)
            }

            items(uiState.brands) { brand ->
                BrandCard(
                    brand = brand,
                    onDelete = { viewModel.showDeleteConfirm(brand) }
                )
            }
        }
    }

    if (uiState.showAddDialog) {
        AddBrandDialog(
            onDismiss = { viewModel.hideAddDialog() },
            onConfirm = { name ->
                viewModel.addBrand(name)
            }
        )
    }

    if (uiState.showDeleteConfirm != null) {
        DeleteConfirmDialog(
            brandName = uiState.showDeleteConfirm?.name ?: "",
            onDismiss = { viewModel.hideDeleteConfirm() },
            onConfirm = {
                uiState.showDeleteConfirm?.let { viewModel.deleteBrand(it) }
            }
        )
    }
}

@Composable
private fun BrandCard(
    brand: Brand,
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
                    brand.name,
                    style = MaterialTheme.typography.titleMedium
                )
                if (brand.isPreset) {
                    Text(
                        "预置品牌",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (!brand.isPreset) {
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
}

@Composable
private fun AddBrandDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var brandName by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加品牌") },
        text = {
            OutlinedTextField(
                value = brandName,
                onValueChange = { brandName = it },
                label = { Text("品牌名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (brandName.isNotBlank()) {
                        onConfirm(brandName)
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
    brandName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认删除") },
        text = { Text("确定要删除品牌 \"$brandName\" 吗？") },
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
