package com.lolita.app.ui.screen.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.entity.Location
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationManageScreen(
    onBack: () -> Unit,
    viewModel: LocationManageViewModel = viewModel()
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
                title = { Text("位置管理") },
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
                Text("位置列表", style = MaterialTheme.typography.titleLarge)
            }
            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.primaryContainer, thickness = 1.dp)
            }
            items(uiState.locations, key = { it.id }) { location ->
                LocationCard(
                    location = location,
                    itemCount = uiState.locationItemCounts[location.id] ?: 0,
                    onEdit = { viewModel.showEditDialog(location) },
                    onDelete = { viewModel.showDeleteConfirm(location) }
                )
            }
        }
    }

    if (uiState.showAddDialog) {
        LocationEditDialog(
            title = "添加位置",
            onDismiss = { viewModel.hideAddDialog() },
            onConfirm = { name, desc, imageUrl ->
                viewModel.addLocation(name, desc, imageUrl)
            }
        )
    }

    if (uiState.showDeleteConfirm != null) {
        val loc = uiState.showDeleteConfirm!!
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteConfirm() },
            title = { Text("确认删除") },
            text = {
                if (uiState.deleteItemCount > 0) {
                    Text("确定删除「${loc.name}」？关联的 ${uiState.deleteItemCount} 件服饰将变为未分配")
                } else {
                    Text("确定删除「${loc.name}」？")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteLocation(loc) },
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

    if (uiState.editingLocation != null) {
        val loc = uiState.editingLocation!!
        LocationEditDialog(
            title = "编辑位置",
            initialName = loc.name,
            initialDescription = loc.description,
            initialImageUrl = loc.imageUrl,
            onDismiss = { viewModel.hideEditDialog() },
            onConfirm = { name, desc, imageUrl ->
                viewModel.updateLocation(loc, name, desc, imageUrl)
            }
        )
    }
}

@Composable
private fun LocationCard(
    location: Location,
    itemCount: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    LolitaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (location.imageUrl != null) {
                AsyncImage(
                    model = location.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            SkinIcon(IconKey.Location, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(location.name, style = MaterialTheme.typography.titleMedium)
                if (location.description.isNotBlank()) {
                    Text(
                        location.description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "${itemCount} 件服饰",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onEdit) { SkinIcon(IconKey.Edit) }
            IconButton(
                onClick = onDelete,
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { SkinIcon(IconKey.Delete) }
        }
    }
}

@Composable
private fun LocationEditDialog(
    title: String,
    initialName: String = "",
    initialDescription: String = "",
    initialImageUrl: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    var imageUrl by remember { mutableStateOf(initialImageUrl) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val internalPath = ImageFileHelper.copyToInternalStorage(context, it)
                if (internalPath != null) {
                    imageUrl = internalPath
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("位置名称") },
                    placeholder = { Text("例如：衣柜A、抽屉2") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述 (可选)") },
                    placeholder = { Text("例如：卧室右侧大衣柜") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                // Image picker
                if (imageUrl != null) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { imageUrl = null },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            SkinIcon(IconKey.Close, tint = Color.White)
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SkinIcon(IconKey.AddPhoto, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (imageUrl != null) "更换图片" else "选择图片")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name, description, imageUrl) }) {
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
