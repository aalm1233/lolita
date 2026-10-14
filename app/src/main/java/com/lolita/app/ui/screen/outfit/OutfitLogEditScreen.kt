package com.lolita.app.ui.screen.outfit

import androidx.compose.ui.graphics.Color
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.screen.common.LolitaShimmerImage
import com.lolita.app.ui.screen.common.CardVariant
import com.lolita.app.ui.screen.common.SectionHeader
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.UnsavedChangesHandler
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.component.skinClickable
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitLogEditScreen(
    logId: Long?,
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    viewModel: OutfitLogEditViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val skin = LolitaSkin.current
    val gridState = rememberLazyGridState()
    var showDiscardDialog by remember { mutableStateOf(false) }

    ObserveSkinScroll(gridState)

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val internalPath = ImageFileHelper.copyToInternalStorage(context, it)
                viewModel.addImage(internalPath)
            }
        }
    }

    LaunchedEffect(logId) {
        viewModel.loadOutfitLog(logId)
    }

    uiState.error?.let { errorMsg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("保存失败") },
            text = { Text(errorMsg) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("确定")
                }
            }
        )
    }

    UnsavedChangesHandler(
        hasUnsavedChanges = viewModel.hasUnsavedChanges,
        onBack = onBack
    )

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("放弃更改？") },
            text = { Text("有未保存的更改，确定要放弃吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onBack()
                }) {
                    Text("放弃", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("继续编辑")
                }
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            GradientTopAppBar(
                title = { Text(if (logId == null) "添加穿搭" else "编辑穿搭") },
                compact = true,
                navigationIcon = {
                    IconButton(onClick = {
                        if (viewModel.hasUnsavedChanges) {
                            showDiscardDialog = true
                        } else {
                            onBack()
                        }
                    }) {
                        SkinIcon(IconKey.ArrowBack)
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(skin.spacingLarge),
                horizontalArrangement = Arrangement.spacedBy(skin.spacingMedium),
                verticalArrangement = Arrangement.spacedBy(skin.spacingLarge)
            ) {
                item(key = "date", span = { GridItemSpan(maxLineSpan) }) {
                    DatePickerCard(
                        date = uiState.date,
                        onDateSelected = { viewModel.updateDate(it) }
                    )
                }

                item(key = "note", span = { GridItemSpan(maxLineSpan) }) {
                    NoteInputCard(
                        note = uiState.note,
                        onNoteChange = { viewModel.updateNote(it) }
                    )
                }

                item(key = "photos-heading", span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(title = "穿搭照片（${uiState.imageUrls.size}）")
                }

                uiState.imageUrls.forEach { imageUrl ->
                    item {
                        PhotoCardWithDelete(
                            imageUrl = imageUrl,
                            onDelete = { viewModel.removeImage(imageUrl) }
                        )
                    }
                }

                item(key = "add-photo") {
                    AddPhotoCard {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                }

                item(key = "items", span = { GridItemSpan(maxLineSpan) }) {
                    ItemSelectionCard(
                        availableItems = uiState.availableItems,
                        selectedIds = uiState.selectedItemIds,
                        onToggleItem = { viewModel.toggleItemSelection(it) }
                    )
                }
            }

            Button(
                onClick = {
                    coroutineScope.launch {
                        val currentState = viewModel.uiState.value
                        if (currentState.date != null && !currentState.isLoading && !currentState.isSaving) {
                            viewModel.save().onSuccess { onSaveSuccess() }
                        }
                    }
                },
                enabled = uiState.date != null && !uiState.isLoading && !uiState.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = skin.spacingLarge)
                    .padding(bottom = skin.spacingMedium)
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(skin.cornerRadiusMedium)
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    SkinIcon(IconKey.Save, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(skin.spacingMedium))
                Text(if (uiState.isSaving) "保存中…" else "保存穿搭")
            }
        }
    }
}

@Composable
private fun DatePickerCard(date: Long?, onDateSelected: (Long) -> Unit) {
    val skin = LolitaSkin.current
    val dateFormat = remember { SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA) }
    val dateLabel = date?.let { dateFormat.format(Date(it)) } ?: "选择日期"
    var showDatePicker by remember { mutableStateOf(false) }

    LolitaCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "日期：$dateLabel，点击修改" },
        variant = CardVariant.COMPACT,
        onClick = { showDatePicker = true }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(skin.spacingMedium)
        ) {
            SkinIcon(IconKey.CalendarMonth, modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary)
            Text(
                text = "日期",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
            SkinIcon(IconKey.ArrowDropDown, modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showDatePicker) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val calendar = Calendar.getInstance()
        date?.let { calendar.timeInMillis = it }
        DisposableEffect(Unit) {
            val dialog = android.app.DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    val cal = Calendar.getInstance()
                    cal.set(year, month, dayOfMonth)
                    onDateSelected(cal.timeInMillis)
                    showDatePicker = false
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).apply {
                setOnDismissListener { showDatePicker = false }
            }
            dialog.show()
            onDispose { dialog.dismiss() }
        }
    }
}

@Composable
private fun NoteInputCard(note: String, onNoteChange: (String) -> Unit) {
    val skin = LolitaSkin.current
    LolitaCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            SectionHeader(title = "备注")
            Spacer(modifier = Modifier.height(skin.spacingMedium))
            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                placeholder = { Text("记录穿搭心得...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(skin.cornerRadiusMedium),
                minLines = 3,
                maxLines = 6
            )
        }
    }
}

@Composable
private fun PhotoCardWithDelete(imageUrl: String, onDelete: () -> Unit) {
    val skin = LolitaSkin.current
    val shape = RoundedCornerShape(skin.cornerRadiusMedium)
    LolitaCard(
        modifier = Modifier.fillMaxWidth(),
        variant = CardVariant.GALLERY,
        contentPadding = PaddingValues(0.dp),
        shape = shape
    ) {
        Box {
            LolitaShimmerImage(
                model = imageUrl,
                contentDescription = "穿搭照片",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(shape),
                contentScale = ContentScale.Crop,
                circularRevealEnabled = false
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(skin.spacingSmall)
                    .semantics { contentDescription = "删除穿搭照片" },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                )
            ) {
                SkinIcon(IconKey.Close, tint = MaterialTheme.colorScheme.onError)
            }
        }
    }
}

@Composable
private fun AddPhotoCard(onAddPhoto: () -> Unit) {
    val skin = LolitaSkin.current
    LolitaCard(
        modifier = Modifier
            .fillMaxWidth(),
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(skin.cornerRadiusMedium),
        onClick = onAddPhoto
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(skin.spacingMedium),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SkinIcon(IconKey.Add, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(skin.spacingMedium))
                Text(
                    text = "添加照片",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ItemSelectionCard(
    availableItems: List<Item>,
    selectedIds: Set<Long>,
    onToggleItem: (Long) -> Unit
) {
    val skin = LolitaSkin.current
    var expanded by remember { mutableStateOf(false) }

    LolitaCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            SectionHeader(title = "关联服饰（${selectedIds.size}）")
            Spacer(modifier = Modifier.height(skin.spacingMedium))
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(skin.cornerRadiusMedium)
            ) {
                SkinIcon(IconKey.Outfit, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(skin.spacingMedium))
                Text("选择服饰")
            }
        }
    }

    if (expanded) {
        AlertDialog(
            onDismissRequest = { expanded = false },
            title = { Text("选择穿搭服饰") },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    items(availableItems, key = { it.id }) { item ->
                        val isSelected = selectedIds.contains(item.id)
                        val isEnabled = item.status == ItemStatus.OWNED

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(skin.cornerRadiusSmall))
                                .skinClickable(enabled = isEnabled) { onToggleItem(item.id) }
                                .padding(vertical = skin.spacingSmall),
                            horizontalArrangement = Arrangement.spacedBy(skin.spacingMedium),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = null,
                                enabled = isEnabled
                            )
                            Text(
                                text = item.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isEnabled) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { expanded = false }) {
                    Text("完成")
                }
            }
        )
    }
}
