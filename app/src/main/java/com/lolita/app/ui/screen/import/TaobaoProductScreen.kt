package com.lolita.app.ui.screen.`import`

import androidx.compose.ui.graphics.Color
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.JsonParser
import com.lolita.app.data.file.TaobaoProductParser
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.icon.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaobaoProductScreen(onBack: () -> Unit, onEdit: (String) -> Unit,
    editorResult: String? = null, onConsumeEditorResult: () -> Unit = {},
    vm: TaobaoProductViewModel = viewModel(),
    supplementTargetName: String? = null,
    onSupplementReady: (com.lolita.app.data.file.TaobaoProductDraft) -> Unit = {}) {
    val state by vm.state.collectAsState()
    LaunchedEffect(editorResult) {
        if (editorResult != null) {
            vm.editorReturned(editorResult, onEdit)
            onConsumeEditorResult()
        }
    }
    val context = LocalContext.current
    var web by remember { mutableStateOf<WebView?>(null) }
    val skin = LolitaSkin.current
    val scroll = rememberScrollState()
    ObserveSkinScroll(scroll)
    BackHandler(enabled = web?.canGoBack() == true && state.draft == null) { web?.goBack() }
    Scaffold(containerColor = Color.Transparent, topBar = { GradientTopAppBar(title = { Text(if(supplementTargetName==null) "淘宝商品导入（试用）" else "补全本件商品资料") }, navigationIcon = {
        IconButton(onClick = onBack) { SkinIcon(IconKey.ArrowBack) }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            if (state.url == null) {
                Column(Modifier.verticalScroll(scroll).padding(skin.spacingLarge), verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                    LolitaSection("粘贴商品链接") {
                        if(supplementTargetName!=null) row { Text("绑定物品：$supplementTargetName。读取后比较字段，不会另建物品或修改账本。") }
                        row { Text("复制淘宝商品分享内容，在这里粘贴。首次读取可能需要在网页登录淘宝。") }
                        row { OutlinedTextField(value = state.input, onValueChange = vm::input, label = { Text("分享内容或商品链接") }, modifier = Modifier.fillMaxWidth(), minLines = 3) }
                        row { Button(onClick = vm::open, enabled = state.input.isNotBlank()) { Text("打开商品网页") } }
                    }
                }
            } else if (state.draft == null) {
                Text("等待商品显示后提取。若需指定款式报价，请先打开规格并选好款式、颜色和尺码。", Modifier.padding(skin.spacingMedium))
                key(state.url) {
                    AndroidView(factory = {
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                    !TaobaoProductParser.allowedPage(request.url.toString())
                            }
                            web = this
                            loadUrl(state.url!!)
                        }
                    }, modifier = Modifier.weight(1f).fillMaxWidth(), onRelease = { vm.releaseWeb(); it.stopLoading(); it.destroy(); if (web === it) web = null })
                }
                Button(onClick = {
                    val source = state.url ?: return@Button
                    val request = vm.extracting()
                    val currentWeb = web ?: return@Button
                    currentWeb.evaluateJavascript(TaobaoProductWebReader.script) { value ->
                        val first = runCatching { JsonParser.parseString(value).asString }.getOrDefault("{}")
                        currentWeb.postDelayed({
                            if (web === currentWeb) currentWeb.evaluateJavascript(TaobaoProductWebReader.script) { next ->
                                val raw = runCatching { JsonParser.parseString(next).asString }.getOrDefault("{}")
                                if (TaobaoProductWebReader.consistentSnapshots(first, raw)) vm.extract(raw, source, request)
                                else vm.extractionFailed(source, request, "规格或报价正在变化，请等待加载完成后重试")
                            }
                        }, 1000)
                    }
                }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().padding(skin.spacingMedium)) { Text("提取商品资料") }
            } else {
                val draft = state.draft!!
                val variants = taobaoProductVariants(draft, state.splitColorVariants, state.splitSkuVariants)
                val requiresSelection = taobaoProductRequiresSelection(draft, state.splitColorVariants, state.splitSkuVariants)
                val selectedCount = variants.count { it.id in state.selectedVariantIds }
                Column(Modifier.weight(1f).verticalScroll(scroll).padding(skin.spacingLarge), verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                    LolitaSection("核对商品资料") {
                        row { Text(draft.name, style = MaterialTheme.typography.titleMedium) }
                        row { Text("品牌：${draft.brand.ifBlank { "待填写" }}") }
                        row { Text("类别：${draft.category.ifBlank { "待填写" }}${if (draft.categoryCandidates.size > 1) "（商品含多种款式，请核对）" else ""}") }
                        row { Text("将导入 ${draft.remoteImages.size} 张商品图和 ${draft.remoteDetailImages.size} 张资料图。") }
                    }
                    if (supplementTargetName==null && state.batchTotal == 0 && draft.skuVariants.orEmpty().isNotEmpty()) {
                        LolitaSection("分别导入") {
                            row {
                                Row(Modifier.fillMaxWidth().toggleable(state.splitSkuVariants,
                                    enabled = !state.busy, role = Role.Switch, onValueChange = vm::splitSkuVariants),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text("按颜色与尺码", Modifier.weight(1f).padding(end = skin.spacingMedium))
                                    Switch(checked = state.splitSkuVariants, onCheckedChange = null, enabled = !state.busy)
                                }
                            }
                            row { Text("每个勾选组合保存一件物品，同色可选多个尺码。") }
                        }
                    } else if (supplementTargetName==null && state.batchTotal == 0 && draft.variants.orEmpty().isEmpty() && draft.colorVariants.orEmpty().size > 1) {
                        LolitaSection("分别导入") {
                            row {
                                Row(Modifier.fillMaxWidth().toggleable(state.splitColorVariants,
                                    enabled = !state.busy, role = Role.Switch,
                                    onValueChange = vm::splitColorVariants), verticalAlignment = Alignment.CenterVertically) {
                                    Text("按颜色分类", Modifier.weight(1f).padding(end = skin.spacingMedium))
                                    Switch(checked = state.splitColorVariants, onCheckedChange = null, enabled = !state.busy)
                                }
                            }
                            row { Text("颜色分类可能包含不同款式；只勾选需要保存的款式，尺码不拆分。") }
                        }
                    }
                    if (state.batchTotal == 0 && draft.skuSelectionIssue != null) {
                        LolitaSection("尺码选择") { row { Text(draft.skuSelectionIssue, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                    }
                    if (state.batchTotal == 0 && state.splitSkuVariants) {
                        SkuCombinationSelector(variants, state.selectedVariantIds, !state.busy, vm::toggleVariant, vm::referencePrice)
                    } else if (state.batchTotal == 0 && variants.size > 1) {
                        LolitaSection("选择要拆分的物品") {
                            row { Text("每件保留完整详情图。报价只对应已确认规格，未读到的款式请补填或留空，不能沿用页面起价。") }
                            variants.forEach { part ->
                                row {
                                    val selected = part.id in state.selectedVariantIds
                                    Row(Modifier.fillMaxWidth().toggleable(selected, enabled = !state.busy, role = Role.Checkbox, onValueChange = { vm.toggleVariant(part.id) }),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = selected, onCheckedChange = null)
                                        Column(Modifier.weight(1f).padding(start = skin.spacingSmall)) {
                                            Text(part.name, style = MaterialTheme.typography.bodyLarge)
                                            Text("类别：${part.category.ifBlank { "待核对" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (selected) ReferencePriceField(part.referenceQuote, { vm.referencePrice(part.id, it) })
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (state.batchTotal == 0 && !requiresSelection) {
                        LolitaSection("参考报价") { row { ReferencePriceField(draft.referenceQuote, { vm.referencePrice(null, it) }) } }
                    }
                    if (state.batchTotal > 0) {
                        LolitaSection("逐件核对进度") {
                            row { Text(if (state.completed) "已保存 ${state.savedCount} 件物品，每件均保留详情原图。" else "已保存 ${state.savedCount} / ${state.batchTotal} 件，正在核对第 ${state.batchIndex + 1} 件。") }
                            if (state.paused) row { Text("当前物品尚未保存，可以继续核对。退出会结束剩余导入，已保存物品会保留。") }
                        }
                    }
                    LolitaCard { ExpandableInfo("商品参数") { Text(draft.properties) } }
                    LolitaCard { ExpandableInfo("报价与来源说明") { Text(draft.description) } }
                    Text("编辑时核对资料。参考报价可在物品详情录价时采用，实付另行记录。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = { if(supplementTargetName!=null) vm.prepareSupplement(onSupplementReady) else if (state.completed) onBack() else vm.edit(onEdit) },
                    enabled = !state.busy && (supplementTargetName!=null || state.batchTotal > 0 || !requiresSelection || selectedCount > 0), modifier = Modifier.fillMaxWidth().padding(skin.spacingMedium)) {
                    if (state.busy) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                    Text(when {
                        state.busy -> "读取图片中…"
                        supplementTargetName!=null -> "读取图片并比较本件资料"
                        state.completed -> "完成并返回"
                        state.batchTotal > 0 -> "继续核对第 ${state.batchIndex + 1} / ${state.batchTotal} 件"
                        state.splitSkuVariants && selectedCount == 0 -> "请至少选择一个组合"
                        requiresSelection && selectedCount == 0 -> "请至少选择一款"
                        requiresSelection -> "核对并逐件编辑（${selectedCount}件）"
                        else -> "核对并进入服饰编辑"
                    })
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(skin.spacingMedium)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkuCombinationSelector(variants: List<com.lolita.app.data.file.TaobaoProductVariant>, selectedIds: Set<String>,
    enabled: Boolean, onToggle: (String) -> Unit, onPrice: (String?, String) -> Unit) {
    val skin = LolitaSkin.current
    var query by rememberSaveable { mutableStateOf("") }
    val visible = variants.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    LolitaSection("选择颜色和尺码组合") {
        row {
            OutlinedTextField(value = query, onValueChange = { query = it }, enabled = enabled,
                label = { Text("搜索颜色、款式或尺码") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("已选 ${variants.count { it.id in selectedIds }} 个组合（最多30件），搜索保留已选。", style = MaterialTheme.typography.bodySmall)
            if (query.isNotEmpty()) TextButton(onClick = { query = "" }, enabled = enabled) { Text("清空搜索") }
        }
        if (visible.isEmpty()) row { Text("没有匹配的规格，请调整搜索；已选组合仍然保留。") }
        visible.groupBy { it.specificationGroup.orEmpty() }.forEach { (group, choices) ->
            row {
                Text(group.ifBlank { "商品规格" }, style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(skin.spacingSmall),
                    verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                    choices.forEach { part ->
                        val selected = part.id in selectedIds
                        FilterChip(selected = selected, onClick = { onToggle(part.id) }, enabled = enabled,
                            leadingIcon = if (selected) { { SkinIcon(IconKey.CheckCircle, modifier = Modifier.size(18.dp)) } } else null,
                            modifier = Modifier.semantics { contentDescription = "选择规格：${part.name}" }, label = { Text(part.size.orEmpty()) })
                    }
                }
                choices.filter { it.id in selectedIds }.forEach { part ->
                    Text(part.name, style = MaterialTheme.typography.bodyMedium)
                    ReferencePriceField(part.referenceQuote, { onPrice(part.id, it) })
                }
            }
        }
    }
}

@Composable
private fun ReferencePriceField(quote: com.lolita.app.data.file.TaobaoProductQuote?, onChange: (String) -> Unit) {
    val value = quote?.amount.orEmpty()
    val invalid = value.isNotBlank() && com.lolita.app.data.file.TaobaoProductQuotes.amount(value) == null
    OutlinedTextField(value = value, onValueChange = onChange,
        label = { Text("参考金额（元）") }, placeholder = { Text("待核对，可留空") },
        supportingText = { Text(if (invalid) "请输入正数，最多两位小数" else quote?.specification?.takeIf(String::isNotBlank) ?: "仅供核对，不代表实付") },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
        isError = invalid, singleLine = true, modifier = Modifier.fillMaxWidth())
}
