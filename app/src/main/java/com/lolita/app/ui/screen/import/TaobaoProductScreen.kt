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
import androidx.compose.ui.Modifier
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
    vm: TaobaoProductViewModel = viewModel()) {
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
    Scaffold(containerColor = Color.Transparent, topBar = { GradientTopAppBar(title = { Text("淘宝商品导入（试用）") }, navigationIcon = {
        IconButton(onClick = onBack) { SkinIcon(IconKey.ArrowBack) }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (state.url == null) {
                Column(Modifier.verticalScroll(scroll).padding(skin.spacingLarge), verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                    LolitaSection("分享商品") {
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
                Column(Modifier.weight(1f).verticalScroll(scroll).padding(skin.spacingLarge), verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                    LolitaSection("核对商品资料") {
                        row { Text(draft.name, style = MaterialTheme.typography.titleMedium) }
                        row { Text("品牌：${draft.brand.ifBlank { "待填写" }}") }
                        row { Text("类别：${draft.category.ifBlank { "待填写" }}${if (draft.categoryCandidates.size > 1) "（商品含多种款式，请核对）" else ""}") }
                        row { Text("可导入 ${draft.remoteImages.size} 张主图，${draft.remoteDetailImages.size} 张详情图将按原图放入尺码表；颜色需自行选择。") }
                    }
                    if (state.batchTotal == 0 && draft.variants.size > 1) {
                        LolitaSection("选择要拆分的物品") {
                            row { Text("每件保留完整详情图。报价只对应已确认规格，未读到的款式请补填或留空，不能沿用页面起价。") }
                            draft.variants.forEach { part ->
                                row {
                                    val selected = part.id in state.selectedVariantIds
                                    Row(Modifier.fillMaxWidth().toggleable(selected, role = Role.Checkbox, onValueChange = { vm.toggleVariant(part.id) }),
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
                    if (state.batchTotal == 0 && draft.variants.size <= 1) {
                        LolitaSection("参考报价") { row { ReferencePriceField(draft.referenceQuote, { vm.referencePrice(null, it) }) } }
                    }
                    if (state.batchTotal > 0) {
                        LolitaSection("逐件核对进度") {
                            row { Text(if (state.completed) "已保存 ${state.savedCount} 件物品，每件均保留详情原图。" else "已保存 ${state.savedCount} / ${state.batchTotal} 件，正在核对第 ${state.batchIndex + 1} 件。") }
                            if (state.paused) row { Text("当前物品尚未保存，可以继续核对。退出会结束剩余导入，已保存物品会保留。") }
                        }
                    }
                    LolitaSection("商品参数") { row { Text(draft.properties) } }
                    LolitaSection("报价与来源说明") { row { Text(draft.description) } }
                    Text("进入编辑后核对品牌、分类和图片。参考报价存入来源说明。保存后到服饰详情的“录入价格”使用报价并确认订单；实付另行记录。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = { if (state.completed) onBack() else vm.edit(onEdit) },
                    enabled = !state.busy && (state.batchTotal > 0 || draft.variants.size <= 1 || state.selectedVariantIds.isNotEmpty()), modifier = Modifier.fillMaxWidth().padding(skin.spacingMedium)) {
                    if (state.busy) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                    Text(when {
                        state.busy -> "读取图片中…"
                        state.completed -> "完成并返回"
                        state.batchTotal > 0 -> "继续核对第 ${state.batchIndex + 1} / ${state.batchTotal} 件"
                        draft.variants.size > 1 -> "核对并逐件编辑（${state.selectedVariantIds.size}件）"
                        else -> "核对并进入服饰编辑"
                    })
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(skin.spacingMedium)) }
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
