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
        try { val draft = TaobaoProductParser.parse(raw); mutable.update { it.copy(draft = draft, busy = false, error = null) } }
        catch (e: Exception) { mutable.update { it.copy(busy = false, error = e.message) } }
    }
    fun edit(onReady: (String) -> Unit) {
        val draft = state.value.draft ?: return
        if (state.value.busy) return
        val generation = revision
        mutable.update { it.copy(busy = true, error = null) }
        job = viewModelScope.launch {
            val paths = mutableListOf<String>()
            try {
                withContext(Dispatchers.IO) {
                    for (url in draft.remoteImages) {
                        ensureActive()
                        val path = withContext(NonCancellable) {
                            ImageFileHelper.downloadFromUrl(getApplication(), url).also { paths += it }
                        }
                        ensureActive()
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(path, opts)
                        require(opts.outWidth > 0 && opts.outHeight > 0) { "商品图片无法解码" }
                    }
                }
                ensureActive()
                if (generation != revision) return@launch
                owned += paths
                onReady(Gson().toJson(draft.copy(imagePaths = paths)))
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
