package com.lolita.app.ui.screen.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.domain.model.SizeOptions
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SizeSelector(
    selectedSize: String?,
    onSizeSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SizeOptionsViewModel = viewModel()
) {
    val options by viewModel.options.collectAsState()
    val skin = LolitaSkin.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = selectedSize.orEmpty(), onValueChange = {}, readOnly = true,
                label = { Text("尺码 (可选)") }, placeholder = { Text("请选择尺码") }, singleLine = true,
                trailingIcon = { SkinIcon(if (expanded) IconKey.ExpandLess else IconKey.ExpandMore) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 320.dp)) {
                DropdownMenuItem(text = { Text("未设置") }, onClick = { onSizeSelected(null); expanded = false })
                SizeOptions.merge(options, listOfNotNull(selectedSize)).forEach { size ->
                    DropdownMenuItem(text = { Text(size) }, onClick = { onSizeSelected(size); expanded = false })
                }
            }
        }
        IconButton(onClick = { expanded = false; draft = ""; error = null; showAdd = true },
            modifier = Modifier.semantics { contentDescription = "添加尺码" }) {
            SkinIcon(IconKey.Add, tint = MaterialTheme.colorScheme.primary)
        }
    }
    if (showAdd) {
        AlertDialog(
            onDismissRequest = { if (!saving) showAdd = false },
            title = { Text("添加尺码") },
            text = {
                OutlinedTextField(value = draft, onValueChange = { draft = it; error = null },
                    label = { Text("自定义尺码") }, placeholder = { Text("例如：XXXL、定制、鞋码 37") },
                    singleLine = true, enabled = !saving, isError = error != null,
                    supportingText = error?.let { message -> { Text(message) } })
            },
            confirmButton = {
                TextButton(enabled = draft.isNotBlank() && !saving, onClick = {
                    saving = true
                    scope.launch {
                        try {
                            val size = viewModel.addSize(draft)
                            onSizeSelected(size)
                            showAdd = false
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            error = "尺码未能保存，请重试"
                        } finally {
                            saving = false
                        }
                    }
                }) { Text(if (saving) "添加中…" else "添加并选用") }
            },
            dismissButton = { TextButton(enabled = !saving, onClick = { showAdd = false }) { Text("取消") } }
        )
    }
}
