package com.lolita.app.ui.screen.`import`

import android.app.Application
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.file.TaobaoProductDraft
import com.lolita.app.data.file.TaobaoProductParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.Collections
import java.util.UUID

data class TaobaoProductState(
    val input: String = "",
    val url: String? = null,
    val draft: TaobaoProductDraft? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val selectedVariantIds: Set<String> = emptySet(),
    val batchTotal: Int = 0,
    val batchIndex: Int = 0,
    val savedCount: Int = 0,
    val completed: Boolean = false,
    val paused: Boolean = false,
    val splitColorVariants: Boolean = false
)

/** One atomic saved-state value keeps result consumption and the next index together. */
internal data class TaobaoProductImportSession(
    val state: TaobaoProductState = TaobaoProductState(),
    val batch: TaobaoProductBatchProgress? = null,
    val masterDraft: TaobaoProductDraft? = null,
    val ownedMasterPaths: List<String> = emptyList()
)

class TaobaoProductViewModel(
    app: Application,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : AndroidViewModel(app) {
    private val gson = Gson()
    private val mutable = MutableStateFlow(TaobaoProductState())
    val state = mutable.asStateFlow()
    private var revision = 0
    private var extractionRequest = 0
    private var extractionTimeout: Job? = null
    private var job: Job? = null
    private var batch: TaobaoProductBatchProgress? = null
    private var masterDraft: TaobaoProductDraft? = null
    private val owned = Collections.synchronizedSet(mutableSetOf<String>())
    private val imageDirectory = File(app.filesDir, "images").canonicalFile
    private var cacheMissing = false
    private var invalidRestore = false
    @Volatile private var cleared = false

    init { restore() }

    fun input(value: String) {
        mutable.update { it.copy(input = value) }
        persist()
    }

    fun open() {
        val url = TaobaoProductParser.shareUrl(state.value.input)
        if (url == null) {
            mutable.update { it.copy(error = "请粘贴完整淘宝分享内容或商品链接") }
            persist()
            return
        }
        revision++
        extractionRequest++
        extractionTimeout?.cancel()
        job?.cancel()
        batch = null
        masterDraft = null
        cacheMissing = false
        invalidRestore = false
        deleteOwned(ownedSnapshot())
        mutable.value = TaobaoProductState(input = state.value.input, url = url)
        persist()
    }

    fun extracting(): Int {
        val request = ++extractionRequest
        mutable.update { it.copy(busy = true, error = null) }
        persist()
        extractionTimeout?.cancel()
        extractionTimeout = viewModelScope.launch {
            delay(15000)
            if (request == extractionRequest) {
                extractionRequest++
                mutable.update { it.copy(busy = false, error = "读取超时，请等待网页加载后重试") }
                persist()
            }
        }
        return request
    }

    fun releaseWeb() {
        if (state.value.draft == null) {
            extractionRequest++
            extractionTimeout?.cancel()
            mutable.update { it.copy(busy = false) }
            persist()
        }
    }

    fun extractionFailed(sourceUrl: String, request: Int, message: String) {
        if (state.value.url != sourceUrl || request != extractionRequest || batch != null) return
        extractionTimeout?.cancel()
        extractionRequest++
        mutable.update { it.copy(busy = false, error = message) }
        persist()
    }

    fun extract(raw: String, sourceUrl: String, request: Int) {
        if (state.value.url != sourceUrl || request != extractionRequest || batch != null) return
        extractionTimeout?.cancel()
        // A WebView callback or recomposition must not replace an already accepted draft.
        extractionRequest++
        try {
            var draft = TaobaoProductParser.parse(raw)
            val catalog = getApplication<Application>().assets.open("builtin/catalog.json")
                .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
            val groups = catalog.getAsJsonArray("brands").map { group ->
                group.asJsonObject.getAsJsonArray("names").map { it.asString }
            }
            val identity = draft.brand.ifBlank { draft.shopName }
            val verified = groups.singleOrNull { names -> names.any {
                TaobaoProductParser.normalizedBrand(it) == TaobaoProductParser.normalizedBrand(identity)
            } }
            if (verified != null) draft = draft.copy(
                brand = draft.brand.ifBlank { verified.first() }, brandAliases = verified)
            mutable.update { it.copy(draft = draft, busy = false, error = null,
                selectedVariantIds = draft.variants.orEmpty().map { variant -> variant.id }.toSet()) }
        } catch (e: Exception) {
            android.util.Log.w("TaobaoProductImport",
                "商品资料读取失败：${e.cause?.javaClass?.simpleName ?: e.javaClass.simpleName}: ${e.cause?.message ?: e.message}")
            mutable.update { it.copy(busy = false, error = e.message) }
        }
        persist()
    }

    fun toggleVariant(id: String) {
        if (state.value.busy || batch != null) return
        val draft = state.value.draft ?: return
        if (taobaoProductVariants(draft, state.value.splitColorVariants).none { it.id == id }) return
        mutable.update { current -> current.copy(selectedVariantIds =
            if (id in current.selectedVariantIds) current.selectedVariantIds - id else current.selectedVariantIds + id) }
        persist()
    }

    fun splitColorVariants(enabled: Boolean) {
        if (state.value.busy || batch != null) return
        val draft = state.value.draft ?: return
        if (draft.variants.orEmpty().isNotEmpty() || draft.colorVariants.orEmpty().size < 2) return
        mutable.update { it.copy(splitColorVariants = enabled, error = null) }
        persist()
    }

    fun referencePrice(variantId: String?, value: String) {
        if (state.value.busy || batch != null) return
        mutable.update { current ->
            val draft = current.draft ?: return@update current
            fun quote(spec: String) = value.takeIf { it.isNotBlank() }?.let {
                com.lolita.app.data.file.TaobaoProductQuote(it, spec, "手动核对")
            }
            current.copy(draft = if (variantId == null) draft.copy(referenceQuote = quote(draft.referenceQuote?.specification.orEmpty()))
                else draft.copy(variants = draft.variants.orEmpty().map { part ->
                    if (part.id == variantId) part.copy(referenceQuote = quote(part.name)) else part
                }, colorVariants = draft.colorVariants.orEmpty().map { part ->
                    if (part.id == variantId) part.copy(referenceQuote = quote(part.name)) else part
                }))
        }
        persist()
    }

    fun edit(onReady: (String) -> Unit) {
        var draft = state.value.draft ?: return
        val variants = taobaoProductVariants(draft, state.value.splitColorVariants)
        val quotes = if (variants.size > 1) variants.filter { it.id in state.value.selectedVariantIds }.mapNotNull { it.referenceQuote }
            else listOfNotNull(draft.referenceQuote)
        if (quotes.any { com.lolita.app.data.file.TaobaoProductQuotes.amount(it.amount) == null }) {
            mutable.update { it.copy(error = "参考金额请输入大于零、最多两位小数的数字，或留空待核对") }
            return
        }
        draft = draft.copy(description = com.lolita.app.data.file.TaobaoProductQuotes.withQuote(draft.description, draft.referenceQuote))
        if (state.value.busy || job?.isActive == true || state.value.completed) return
        if (invalidRestore || cacheMissing) return
        if (batch?.activeToken != null) {
            mutable.update { it.copy(error = "上一件编辑尚未返回，请先完成或取消编辑") }
            persist()
            return
        }
        if (batch == null) {
            val items = taobaoProductBatchItems(draft, state.value.selectedVariantIds, state.value.splitColorVariants)
            if (items.isEmpty()) {
                mutable.update { it.copy(error = "请至少选择一件物品") }
                persist()
                return
            }
            batch = TaobaoProductBatchProgress(items)
        }
        startPreparation(onReady)
    }

    fun editorReturned(result: String, onReady: (String) -> Unit) {
        val previous = batch ?: return
        val updated = previous.editorReturned(result)
        if (updated == previous) return
        val progress = if (cacheMissing && !updated.completed) updated.copy(paused = true) else updated
        batch = progress
        publishBatch(busy = false, error = if (cacheMissing && !progress.completed) missingCacheMessage() else null)
        // Persist consumption before preparing another item, so an old result cannot create it twice.
        persist()
        if (progress.completed) {
            masterDraft = null
            deleteOwned(ownedSnapshot())
            persist()
        } else if (!progress.paused) {
            startPreparation(onReady)
        }
    }

    private fun startPreparation(onReady: (String) -> Unit) {
        val previousJob = job
        val generation = revision
        batch = batch?.copy(paused = false)
        publishBatch(busy = true, error = null)
        persist()
        job = viewModelScope.launch {
            // Result callbacks can be delivered while navigation from the preceding job finishes.
            previousJob?.join()
            ensureActive()
            if (generation == revision) prepareCurrent(generation, onReady)
        }
    }

    private suspend fun prepareCurrent(generation: Int, onReady: (String) -> Unit) {
        val progress = batch ?: return
        val item = progress.currentItem() ?: return
        val copies = mutableListOf<String>()
        var token: String? = null
        var transferred = false
        try {
            // A restored save may advance the index even when its remaining shared cache is gone.
            // Keep that save, but never silently recreate the next editor or download the batch again.
            if (cacheMissing) error(missingCacheMessage())
            val master = masterDraft ?: downloadMaster(state.value.draft ?: error("商品资料已丢失"), generation)
            if (!hasMasterFiles(master)) {
                cacheMissing = true
                error(missingCacheMessage())
            }
            val copiedByMaster = linkedMapOf<String, String>()
            for (path in masterPaths(master)) {
                currentCoroutineContext().ensureActive()
                val copied = withContext(NonCancellable) {
                    ImageFileHelper.copyExistingImageToInternalStorage(getApplication(), path).also { copies += it }
                }
                currentCoroutineContext().ensureActive()
                copiedByMaster[path] = copied
            }
            currentCoroutineContext().ensureActive()
            check(generation == revision) { "导入内容已更新" }
            token = UUID.randomUUID().toString()
            val editorDraft = taobaoProductEditorDraft(item, master, copiedByMaster, token,
                progress.index, progress.items.size)
            batch = progress.copy(activeToken = token, paused = false)
            publishBatch(busy = true, error = null)
            persist()
            onReady(gson.toJson(editorDraft))
            // Only successful navigation transfers these copies; master files remain VM-owned.
            transferred = true
            copies.clear()
            if (generation == revision && batch?.activeToken == token) {
                publishBatch(busy = false, error = null)
                persist()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (generation == revision) {
                if (batch?.activeToken == token) batch = batch?.copy(activeToken = null)
                batch = batch?.copy(paused = true)
                // Keep retry disabled until every untransferred copy has actually been removed.
                publishBatch(busy = true, error = if (cacheMissing) missingCacheMessage()
                    else "图片读取失败：${e.message}，可重试")
                persist()
            }
        } finally {
            if (copies.isNotEmpty()) {
                withContext(NonCancellable + Dispatchers.IO) { copies.forEach { File(it).delete() } }
            }
            if (generation == revision && !transferred && !cleared) {
                if (batch?.activeToken == token) batch = batch?.copy(activeToken = null)
                batch = batch?.copy(paused = true)
                publishBatch(busy = false, error = state.value.error)
                persist()
            }
        }
    }

    private suspend fun downloadMaster(draft: TaobaoProductDraft, generation: Int): TaobaoProductDraft {
        val heads = draft.remoteImages.orEmpty().distinct()
        val details = draft.remoteDetailImages.orEmpty().distinct()
        require(heads.size <= 5) { "主图超过 5 张" }
        require(details.size <= 50) { "详情图片超过 50 张" }
        require((heads + details).all(TaobaoProductParser::allowedImage)) { "图片来源无效" }
        val downloaded = mutableListOf<String>()
        var committed = false
        try {
            val pathsByUrl = linkedMapOf<String, String>()
            var bytes = 0L
            for (url in (heads + details).distinct()) {
                currentCoroutineContext().ensureActive()
                val limit = minOf(MAX_IMAGE_BYTES, MAX_TOTAL_BYTES - bytes)
                require(limit > 0) { "图片总量过大" }
                val path = withContext(NonCancellable) {
                    ImageFileHelper.downloadFromUrl(getApplication(), url, maxBytes = limit).also {
                        downloaded += it
                        synchronized(owned) { if (cleared) File(it).delete() else owned += it }
                        persist()
                    }
                }
                currentCoroutineContext().ensureActive()
                bytes += File(path).length()
                require(bytes <= MAX_TOTAL_BYTES) { "图片总量过大" }
                withContext(Dispatchers.IO) {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(path, options)
                    require(options.outWidth > 0 && options.outHeight > 0) { "商品图片无法解码" }
                }
                pathsByUrl[url] = path
            }
            currentCoroutineContext().ensureActive()
            check(generation == revision) { "导入内容已更新" }
            val master = draft.copy(imagePaths = heads.map(pathsByUrl::getValue),
                sizeChartImagePath = null, sizeChartImagePaths = details.map(pathsByUrl::getValue),
                importToken = null, editorToken = null, batchIndex = 0, batchTotal = 0)
            masterDraft = master
            cacheMissing = false
            committed = true
            persist()
            return master
        } finally {
            if (!committed) withContext(NonCancellable) {
                deleteOwned(downloaded)
                persist()
            }
        }
    }

    private fun publishBatch(busy: Boolean, error: String?) {
        val progress = batch
        mutable.update { it.copy(busy = busy, error = error,
            batchTotal = progress?.items?.size ?: 0, batchIndex = progress?.index ?: 0,
            savedCount = progress?.savedCount ?: 0, completed = progress?.completed ?: false,
            paused = progress?.paused ?: false) }
    }

    private fun masterPaths(draft: TaobaoProductDraft): List<String> =
        (draft.imagePaths.orEmpty() + listOfNotNull(draft.sizeChartImagePath) +
            draft.sizeChartImagePaths.orEmpty()).distinct()

    private fun isInternalPath(path: String): Boolean = runCatching {
        File(path).canonicalFile.parentFile == imageDirectory
    }.getOrDefault(false)

    private fun hasMasterFiles(draft: TaobaoProductDraft): Boolean = masterPaths(draft).all {
        isInternalPath(it) && File(it).isFile
    }

    private fun missingCacheMessage() = "导入图片缓存已丢失，请退出后重新读取商品；已保存的 ${batch?.savedCount ?: 0} 件会保留"

    private fun ownedSnapshot(): List<String> = synchronized(owned) { owned.toList() }

    private fun deleteOwned(paths: Collection<String>) {
        paths.forEach { if (isInternalPath(it)) File(it).delete() }
        synchronized(owned) { owned.removeAll(paths.toSet()) }
    }

    private fun persist() {
        if (!cleared) {
            val cache = masterDraft?.let { master ->
                TaobaoProductDraft(name = "", brand = "", size = "", properties = "", description = "",
                    productUrl = "", remoteImages = emptyList(), imagePaths = master.imagePaths,
                    sizeChartImagePath = master.sizeChartImagePath, sizeChartImagePaths = master.sizeChartImagePaths)
            }
            savedStateHandle[SESSION_KEY] = gson.toJson(TaobaoProductImportSession(state.value,
                batch?.let(::taobaoProductCompactBatch), cache, ownedSnapshot()))
        }
    }

    private fun restore() {
        val json = savedStateHandle.get<String>(SESSION_KEY) ?: return
        try {
            val restored = requireNotNull(gson.fromJson(json, TaobaoProductImportSession::class.java))
            val restoredState = requireNotNull(restored.state)
            val progress = restored.batch?.let { saved ->
                taobaoProductRestoreBatch(saved, requireNotNull(restoredState.draft))
            }
            if (progress != null) {
                require(progress.items.isNotEmpty() && progress.items.size <= 30)
                require(progress.index in 0..progress.items.size && progress.savedCount == progress.index)
                require(progress.completed == (progress.index == progress.items.size))
            }
            batch = progress
            masterDraft = restored.masterDraft
            synchronized(owned) {
                owned.addAll((restored.ownedMasterPaths.orEmpty() + masterDraft?.let(::masterPaths).orEmpty())
                    .filter(::isInternalPath))
            }
            mutable.value = restoredState.copy(busy = false,
                selectedVariantIds = restoredState.selectedVariantIds.orEmpty())
            publishBatch(busy = false, error = state.value.error)
            if (progress?.completed == true || progress == null) {
                masterDraft = null
                deleteOwned(ownedSnapshot())
            } else if (masterDraft != null) {
                deleteOwned(ownedSnapshot().filter { it !in masterPaths(masterDraft!!) })
                if (!hasMasterFiles(masterDraft!!)) {
                    cacheMissing = true
                    batch = progress.copy(paused = progress.activeToken == null)
                    publishBatch(busy = false, error = missingCacheMessage())
                }
            } else if (progress.savedCount > 0 || progress.activeToken != null) {
                cacheMissing = true
                publishBatch(busy = false, error = missingCacheMessage())
            } else {
                // Only a never-opened first item may repeat an interrupted initial download.
                deleteOwned(ownedSnapshot())
                batch = progress.copy(paused = true)
                publishBatch(busy = false, error = "图片读取已中断，可重试")
            }
        } catch (_: Exception) {
            invalidRestore = true
            mutable.update { it.copy(busy = false, error = "导入进度读取失败，请退出后重新读取商品；已保存的物品会保留") }
        }
        persist()
    }

    override fun onCleared() {
        cleared = true
        job?.cancel()
        extractionTimeout?.cancel()
        deleteOwned(ownedSnapshot())
        super.onCleared()
    }

    companion object {
        internal const val SESSION_KEY = "taobao_product_session"
        private const val MAX_IMAGE_BYTES = 20L * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 80L * 1024 * 1024
    }
}
