package com.lolita.app.ui.screen.`import`

import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
fun TaobaoProductScreen(onBack: () -> Unit, onEdit: (String) -> Unit, vm: TaobaoProductViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var web by remember { mutableStateOf<WebView?>(null) }
    val skin = LolitaSkin.current
    val scroll = rememberScrollState()
    ObserveSkinScroll(scroll)
    BackHandler(enabled = web?.canGoBack() == true && state.draft == null) { web?.goBack() }
    Scaffold(topBar = { GradientTopAppBar(title = { Text("淘宝商品导入（试用）") }, navigationIcon = {
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
                Text("登录并等待商品显示后，点击提取。", Modifier.padding(skin.spacingMedium))
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
                    web?.evaluateJavascript("JSON.stringify({url:location.href,text:document.body.innerText,detailImages:Array.from(document.querySelectorAll('#v6_pc_content img')).map(i=>({url:i.getAttribute('data-src')||i.currentSrc||i.src})),entry:window.__ICE_SUSPENSE_LOADER__&&window.__ICE_SUSPENSE_LOADER__.entry})") { value ->
                        val raw = runCatching { JsonParser.parseString(value).asString }.getOrDefault("{}")
                        vm.extract(raw, source, request)
                    }
                }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().padding(skin.spacingMedium)) { Text("提取商品资料") }
            } else {
                val draft = state.draft!!
                Column(Modifier.weight(1f).verticalScroll(scroll).padding(skin.spacingLarge), verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                    LolitaSection("核对商品资料") {
                        row { Text(draft.name, style = MaterialTheme.typography.titleMedium) }
                        row { Text("品牌：${draft.brand.ifBlank { "待填写" }}") }
                        row { Text("类别：${draft.category.ifBlank { "待填写" }}${if (draft.categoryCandidates.size > 1) "（商品含多种款式，请核对）" else ""}") }
                        row { Text(draft.properties) }
                        row { Text("可导入 ${draft.remoteImages.size} 张主图，${draft.remoteDetailImages.size} 张详情图将按原图放入尺码表；颜色需自行选择。") }
                    }
                    LolitaSection("报价与来源说明") { row { Text(draft.description) } }
                    Text("进入编辑后核对品牌、分类和图片。默认保存为心愿，不自动创建价格或付款记录。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = { vm.edit(onEdit) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().padding(skin.spacingMedium)) {
                    if (state.busy) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                    Text(if (state.busy) "读取图片中…" else "核对并进入服饰编辑")
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(skin.spacingMedium)) }
        }
    }
}
