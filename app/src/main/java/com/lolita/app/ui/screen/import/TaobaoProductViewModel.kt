package com.lolita.app.ui.screen.`import`

import android.app.Application
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.file.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

data class TaobaoProductState(val input: String = "", val url: String? = null,
    val draft: TaobaoProductDraft? = null, val busy: Boolean = false, val error: String? = null)

class TaobaoProductViewModel(app: Application) : AndroidViewModel(app) {
    private val mutable = MutableStateFlow(TaobaoProductState())
    val state = mutable.asStateFlow()
    private var revision = 0
    private var extractionRequest = 0
    private var extractionTimeout: Job? = null
    private val owned = mutableListOf<String>()
    private var job: Job? = null
    fun input(value: String) { mutable.update { it.copy(input = value) } }
    fun open() {
        val url = TaobaoProductParser.shareUrl(state.value.input)
        if (url == null) { mutable.update { it.copy(error = "请粘贴完整淘宝分享内容或商品链接") }; return }
        revision++; job?.cancel()
        mutable.update { it.copy(url = url, draft = null, busy = false, error = null) }
    }
    fun extracting(): Int {
        val request = ++extractionRequest
        mutable.update { it.copy(busy = true, error = null) }
        extractionTimeout?.cancel()
        extractionTimeout = viewModelScope.launch {
            delay(15000)
            if (request == extractionRequest) {
                extractionRequest++
                mutable.update { it.copy(busy = false, error = "读取超时，请等待网页加载后重试") }
            }
        }
        return request
    }
    fun releaseWeb() {
        if (state.value.draft == null) {
            extractionRequest++; extractionTimeout?.cancel()
            mutable.update { it.copy(busy = false) }
        }
    }
    fun extract(raw: String, sourceUrl: String, request: Int) {
        if (state.value.url != sourceUrl || request != extractionRequest) return
        extractionTimeout?.cancel()
        try {
            var draft = TaobaoProductParser.parse(raw)
            val catalog = getApplication<Application>().assets.open("builtin/catalog.json").bufferedReader().use { com.google.gson.JsonParser.parseReader(it).asJsonObject }
            val groups = catalog.getAsJsonArray("brands").map { group -> group.asJsonObject.getAsJsonArray("names").map { it.asString } }
            val identity = draft.brand.ifBlank { draft.shopName }
            val verified = groups.singleOrNull { names -> names.any { TaobaoProductParser.normalizedBrand(it) == TaobaoProductParser.normalizedBrand(identity) } }
            if (verified != null) draft = draft.copy(brand = draft.brand.ifBlank { verified.first() }, brandAliases = verified)
            mutable.update { it.copy(draft = draft, busy = false, error = null) } }
        catch (e: Exception) {
            android.util.Log.w("TaobaoProductImport", "商品资料读取失败：${e.cause?.javaClass?.simpleName ?: e.javaClass.simpleName}: ${e.cause?.message ?: e.message}")
            mutable.update { it.copy(busy = false, error = e.message) }
        }
    }
    fun edit(onReady: (String) -> Unit) {
        val draft = state.value.draft ?: return
        if (state.value.busy) return
        val generation = revision
        mutable.update { it.copy(busy = true, error = null) }
        job = viewModelScope.launch {
            val paths = mutableListOf<String>()
            val detailPaths = mutableListOf<String>()
            try {
                withContext(Dispatchers.IO) {
                    for (url in draft.remoteImages) {
                        ensureActive()
                        val path = withContext(NonCancellable) {
                            ImageFileHelper.downloadFromUrl(getApplication(), url, maxBytes = minOf(20L * 1024 * 1024, 80L * 1024 * 1024 - paths.sumOf { File(it).length() })).also { paths += it }
                        }
                        require(paths.sumOf { File(it).length() } <= 80L * 1024 * 1024) { "图片总量过大" }
                        ensureActive()
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(path, opts)
                        require(opts.outWidth > 0 && opts.outHeight > 0) { "商品图片无法解码" }
                    }
                    for (url in draft.remoteDetailImages.orEmpty()) {
                        ensureActive()
                        withContext(NonCancellable) { ImageFileHelper.downloadFromUrl(getApplication(), url, maxBytes = minOf(20L * 1024 * 1024, 80L * 1024 * 1024 - paths.sumOf { File(it).length() })).also { paths += it; detailPaths += it } }
                        require(paths.sumOf { File(it).length() } <= 80L * 1024 * 1024) { "图片总量过大" }
                    }
                    ensureActive()
                    detailPaths.forEach { path ->
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(path, opts)
                        require(opts.outWidth > 0 && opts.outHeight > 0) { "详情图片无法解码" }
                    }
                }
                ensureActive()
                if (generation != revision) return@launch
                owned += paths
                onReady(Gson().toJson(draft.copy(imagePaths = paths.filter { it !in detailPaths }, sizeChartImagePaths = detailPaths.toList())))
                // Ownership passes to the editor only after navigation succeeds.
                owned.removeAll(paths.toSet()); paths.clear()
                mutable.update { it.copy(busy = false) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(busy = false, error = "图片读取失败：${e.message}，可重试") } }
            finally { withContext(NonCancellable + Dispatchers.IO) { paths.forEach { File(it).delete() } } }
        }
    }
    override fun onCleared() { owned.forEach { File(it).delete() }; super.onCleared() }
}
