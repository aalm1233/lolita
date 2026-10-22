package com.lolita.app.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.repository.*
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon

@Composable
fun AiConfigurationScreen(onBack: () -> Unit, viewModel: AiConfigurationViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val skin = LolitaSkin.current
    val scroll = rememberLazyListState()
    ObserveSkinScroll(scroll)
    BackHandler(enabled = state.busy) { }
    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
        GradientTopAppBar(title = { Text("API 配置") }, navigationIcon = {
            IconButton(onClick = onBack, enabled = !state.busy) {
                SkinIcon(IconKey.ArrowBack, modifier = Modifier.semantics { contentDescription = "返回" })
            }
        })
    }) { padding ->
        LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).testTag("aiConfiguration"),
            contentPadding = PaddingValues(skin.spacingLarge), verticalArrangement = Arrangement.spacedBy(skin.cardGap)) {
            item("intro") {
                Text("分别选择生图与识图的默认服务。", style = MaterialTheme.typography.bodyMedium)
            }
            if (state.isLoading) item("loading") {
                LolitaCard { Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                    repeat(4) { ShimmerLine(widthFraction = 0.85f, height = 24.dp) }
                } }
            } else {
                if (state.error != null && state.editing == null) item("error") { Text(state.error!!, color = MaterialTheme.colorScheme.error) }
                state.message?.let { message -> item("message") { Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) } }
                if (state.configuration.generation == null || state.configuration.recognition == null) item("selection-error") {
                    Text("部分默认服务无法读取，请在下方重新选择。", color = MaterialTheme.colorScheme.error)
                }
                for (generation in listOf(true, false)) item(if (generation) "generation" else "recognition") {
                    LolitaSection(if (generation) "生图服务" else "识图服务") {
                        row { Text(if (generation) "用于虚拟试衣、展示图与我的模特" else "用于商品资料与尺码图识别", style = MaterialTheme.typography.bodySmall) }
                        AiKeySlot.entries.filter { (it.generation != null) == generation }.forEach { slot -> row {
                            val status = state.configuration.keys[slot] ?: AiKeyStatus.EMPTY
                            Row(Modifier.fillMaxWidth().testTag("apiSlot-${slot.name}"), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                                RadioButton(selected = state.configuration.isSelected(slot), onClick = { viewModel.select(slot) }, enabled = !state.busy,
                                    modifier = Modifier.semantics { contentDescription = "设为默认${slot.purpose}服务：${slot.label}" })
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                                    Text(slot.label, style = MaterialTheme.typography.titleSmall)
                                    Text(when (status) { AiKeyStatus.EMPTY -> "未配置"; AiKeyStatus.CONFIGURED -> "已配置"; AiKeyStatus.UNREADABLE -> "需重新配置" } +
                                        if (state.configuration.isSelected(slot)) " · 默认服务" else "", style = MaterialTheme.typography.bodySmall,
                                        color = if (status == AiKeyStatus.UNREADABLE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { viewModel.edit(slot) }, enabled = !state.busy, modifier = Modifier.semantics {
                                    contentDescription = "配置${slot.purpose}密钥：${slot.label}"
                                }) { Text(if (status == AiKeyStatus.EMPTY) "配置" else "更换") }
                            }
                        } }
                    }
                }
                item("privacy") { Text("密钥仅在本机加密保存，不包含在备份中。", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    state.editing?.let { slot ->
        ApiKeyDialog(hasKey = state.configuration.keys[slot]?.let { it != AiKeyStatus.EMPTY } ?: false,
            keyRevision = state.configuration.revision(slot), isConfiguring = state.busy, busy = state.busy, error = state.error,
            onSave = { viewModel.saveKey(slot, it) }, onClear = { viewModel.clearKey(slot) }, onDismiss = viewModel::dismissEditor,
            serviceId = slot.name, serviceLabel = "${slot.label} · ${slot.purpose}", keyHelp = slot.help)
    }
}
