package com.lolita.app.ui.screen.common

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.entity.Location
import com.lolita.app.domain.model.StorageTemplate
import com.lolita.app.ui.theme.LocalLolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/** Copies picked photos into normal image ownership; disposal cleans only uncommitted copies. */
@Composable
fun StorageEditDialog(
    title: String,
    initialName: String = "",
    initialDescription: String = "",
    initialImageUrl: String? = null,
    busy: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    var image by remember { mutableStateOf(initialImageUrl) }
    var copying by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val staged = remember { mutableListOf<String>() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val skin = LocalLolitaSkin.current
    val scroll = rememberScrollState()
    ObserveSkinScroll(scroll)
    DisposableEffect(Unit) { onDispose { ImageFileHelper.cleanupUnreferencedImages(staged.toList()) } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch {
            copying = true
            try {
                val path = ImageFileHelper.copyToInternalStorage(context, uri)
                if (path == null) error = "读取图片失败，请重新选择"
                else { staged.add(path); image = path }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                error = "读取图片失败，请重新选择"
            } finally { copying = false }
        }
    }
    AlertDialog(onDismissRequest = { if (!busy && !copying) onDismiss() }, title = { Text(title) }, text = {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
            OutlinedTextField(name, { name = it }, label = { Text("位置名称") }, singleLine = true,
                enabled = !busy, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text("描述（可选）") },
                enabled = !busy, modifier = Modifier.fillMaxWidth(), maxLines = 3)
            image?.let { path ->
                LolitaShimmerImage(model = path, contentDescription = "位置封面预览", circularRevealEnabled = false,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(132.dp)
                        .clip(RoundedCornerShape(skin.cornerRadiusMedium)))
                TextButton(onClick = { image = null }, enabled = !busy) { Text("移除封面") }
            }
            OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !busy && !copying, modifier = Modifier.fillMaxWidth()) {
                SkinIcon(IconKey.AddPhoto, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(skin.spacingMedium))
                Text(if (copying) "正在读取图片…" else if (image == null) "选择照片" else "更换照片")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = { onConfirm(name.trim(), description.trim(), image) },
        enabled = !busy && !copying && name.isNotBlank() && name.trim().length <= 120) { Text(if (busy) "保存中…" else "保存") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy && !copying) { Text("取消") } })
}

@Composable
fun StorageTemplateDialog(
    locations: List<Location>,
    templates: List<StorageTemplate>,
    initialLocationId: Long?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onAdopt: (Long, Set<String>) -> Unit
) {
    var roomId by remember { mutableStateOf(initialLocationId) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    val listState = rememberLazyListState()
    val skin = LocalLolitaSkin.current
    ObserveSkinScroll(listState)
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("添加常用收纳") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
            Text("插图仅作位置示意。选择所属现有房间后，才会创建你勾选的收纳位置。")
            StorageLocationPicker(roomId, null, locations, emptyList(), { room, _ -> roomId = room }, enabled = !busy)
            LazyColumn(state = listState, modifier = Modifier.heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                items(templates, key = { it.id }) { template ->
                    Surface(onClick = { if (!busy) selected = if (template.id in selected) selected - template.id else selected + template.id },
                        color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(skin.cornerRadiusSmall)) {
                        Row(Modifier.fillMaxWidth().padding(skin.spacingMedium), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                            Checkbox(template.id in selected, null)
                            LolitaShimmerImage(model = "file:///android_asset/${template.assetPath}", contentDescription = null,
                                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(skin.cornerRadiusSmall)), contentScale = ContentScale.Crop)
                            Text(template.name, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { roomId?.let { onAdopt(it, selected) } },
        enabled = !busy && roomId != null && selected.isNotEmpty()) { Text(if (busy) "添加中…" else "添加选中收纳（${selected.size}）") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } })
}
