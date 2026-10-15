package com.lolita.app.ui.screen.mannequin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.tryon.TryOnGenerationPhase
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.MannequinRequest
import com.lolita.app.domain.model.supportsMannequin
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class MannequinUiState(
    val item: Item? = null,
    val category: Category? = null,
    val selectedImage: String = "",
    val instructions: String = "",
    val previewPath: String? = null,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val keyLoaded: Boolean = false,
    val hasKey: Boolean = false,
    val keyRevision: Int = 0,
    val isConfiguring: Boolean = false,
    val isGenerating: Boolean = false,
    val globalGenerating: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val isLeaving: Boolean = false,
    val phase: TryOnGenerationPhase? = null,
    val error: String? = null
) {
    val busy get() = isGenerating || globalGenerating || isSaving || isConfiguring || isLeaving
    val selectionIssue: String? get() = when {
        item == null -> "这件裙子已被删除或无法读取"
        !supportsMannequin(category) -> "仅支持服装分类中的 JSK、OP、SK 和明确裙类"
        selectedImage.isBlank() || selectedImage !in item.imageUrls -> "参考图片已改变，请重新选择当前图片"
        else -> null
    }
    val canGenerate get() = !busy && !isLoading && !loadFailed && keyLoaded && hasKey && previewPath == null && !saved && selectionIssue == null
    val canSave get() = previewPath != null && item != null && !busy && !isLoading && !loadFailed && !saved
    val navigationBlocked get() = isLoading || isConfiguring || isLeaving || isGenerating || isSaving || previewPath != null
    val backUnavailable get() = isLoading || isSaving || isConfiguring || isLeaving
    val readyToLeave get() = isLeaving && !isLoading && !isGenerating && !isSaving && !isConfiguring && previewPath == null
}

/** Owns only the uncommitted preview; attaching it always reads the latest Item in a Room transaction. */
class MannequinViewModel @JvmOverloads constructor(
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    item: Flow<Item?> = AppModule.itemRepository().observeItem(savedStateHandle.get<Long>("itemId") ?: 0L),
    categories: Flow<List<Category>> = AppModule.categoryRepository().getAllCategories(),
    private val hasApiKey: suspend () -> Boolean = { AppModule.tryOnSettings().hasKey() },
    private val saveApiKey: suspend (String) -> Unit = { AppModule.tryOnSettings().saveKey(it) },
    private val clearApiKey: suspend () -> Unit = { AppModule.tryOnSettings().clearKey() },
    globalGenerating: StateFlow<Boolean> = AppModule.mannequinGenerator().isGenerating,
    private val generateImage: suspend (MannequinRequest, (TryOnGenerationPhase) -> Unit) -> String = { request, phase ->
        AppModule.mannequinGenerator().generate(request, phase)
    },
    private val appendImage: suspend (Long, String) -> Unit = { id, path -> AppModule.itemRepository().appendGeneratedImage(id, path) },
    private val validatePreview: suspend (String) -> Boolean = { ImageFileHelper.isOwnedGeneratedImage(AppModule.context(), it) },
    private val discardImage: (String) -> Unit = { ImageFileHelper.cleanupUnreferencedImages(listOf(it)); Unit }
) : ViewModel() {
    private val itemId = savedStateHandle.get<Long>("itemId") ?: 0L
    private val state = MutableStateFlow(MannequinUiState(
        selectedImage = savedStateHandle.get<String>(SOURCE).orEmpty(),
        instructions = savedStateHandle.get<String>(INSTRUCTIONS).orEmpty().take(200)
    ))
    val uiState: StateFlow<MannequinUiState> = state.asStateFlow()
    private var generationJob: Job? = null
    // Saving must finish even when a UI waiter or the navigation entry is cancelled.
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var cleared = false
    private var restored = false

    init {
        viewModelScope.launch {
            combine(item, categories) { row, groups -> row to groups.firstOrNull { it.id == row?.categoryId } }
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                    state.update { it.copy(isLoading = false, loadFailed = true, error = "裙子读取失败，请返回后重试") }
                }.collect { (row, category) ->
                    if (!restored) {
                        restored = true
                        if (!savedStateHandle.contains(SOURCE)) {
                            savedStateHandle[SOURCE] = row?.imageUrls?.firstOrNull { it.isNotBlank() }.orEmpty()
                        }
                        val path = savedStateHandle.get<String>(PREVIEW)
                        val owner = savedStateHandle.get<Long>(PREVIEW_ITEM)
                        val valid = path != null && owner == itemId && itemId > 0 && row?.id == itemId &&
                            path !in row.imageUrls && validateRestoredPreview(path)
                        // Rejected paths were never owned by this VM and must never be cleaned up.
                        if (!valid) clearStoredPreview()
                        state.update { it.copy(previewPath = path.takeIf { valid }, selectedImage = savedStateHandle.get<String>(SOURCE).orEmpty()) }
                    }
                    state.update { it.copy(item = row?.takeIf { it.id == itemId }, category = category, isLoading = false) }
                }
        }
        viewModelScope.launch { globalGenerating.collect { busy -> state.update { it.copy(globalGenerating = busy) } } }
        viewModelScope.launch {
            val configured = try { withContext(Dispatchers.IO) { hasApiKey() } }
                catch (failure: Exception) { if (failure is CancellationException) throw failure; false }
            state.update { it.copy(hasKey = configured, keyLoaded = true) }
        }
    }

    fun selectImage(path: String) {
        val current = state.value
        if (current.busy || current.previewPath != null || path.isBlank() || path !in current.item?.imageUrls.orEmpty()) return
        savedStateHandle[SOURCE] = path
        state.update { it.copy(selectedImage = path, error = null) }
    }
    fun updateInstructions(value: String) {
        if (state.value.busy || state.value.previewPath != null) return
        savedStateHandle[INSTRUCTIONS] = value.take(200)
        state.update { it.copy(instructions = value.take(200)) }
    }
    fun saveKey(value: String) {
        if (state.value.busy) return
        if (value.isBlank()) { state.update { it.copy(error = "请填写 API Key") }; return }
        configureKey { saveApiKey(value); true }
    }
    fun clearKey() {
        if (!state.value.busy) configureKey { clearApiKey(); false }
    }
    private fun configureKey(operation: suspend () -> Boolean) {
        state.update { it.copy(isConfiguring = true, error = null) }
        viewModelScope.launch {
            try {
                val configured = withContext(Dispatchers.IO) { operation() }
                state.update { it.copy(hasKey = configured, keyLoaded = true, keyRevision = it.keyRevision + 1) }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                state.update { it.copy(error = "API Key 配置失败，请重新输入后重试") }
            } finally { state.update { it.copy(isConfiguring = false) } }
        }
    }
    fun generate() {
        val current = state.value
        if (current.busy || current.isLoading || current.loadFailed || current.previewPath != null || current.saved || !current.keyLoaded) return
        if (!current.hasKey) { state.update { it.copy(error = "请先配置个人 API Key") }; return }
        current.selectionIssue?.let { issue -> state.update { it.copy(error = issue) }; return }
        val request = MannequinRequest(itemId, current.selectedImage, current.instructions)
        state.update { it.copy(isGenerating = true, phase = TryOnGenerationPhase.PREPARING, error = null) }
        generationJob = viewModelScope.launch {
            var delivered: String? = null
            try {
                val path = generateImage(request) { phase -> state.update { it.copy(phase = phase) } }
                delivered = path
                currentCoroutineContext().ensureActive()
                savedStateHandle[PREVIEW_ITEM] = itemId
                savedStateHandle[PREVIEW] = path
                state.update { it.copy(previewPath = path) }
                delivered = null // ownership has moved into the preview state
            } catch (failure: CancellationException) {
                state.update { it.copy(error = "已取消等待；服务端可能仍在处理并计费，重试需主动点击") }
                throw failure
            } catch (failure: Exception) {
                state.update { it.copy(error = safeMessage(failure, "人台图生成失败，请检查图片与配置后主动重试")) }
            } finally {
                delivered?.let(discardImage)
                state.update { it.copy(isGenerating = false, phase = null) }
            }
        }
    }
    fun cancelGeneration(leavePage: Boolean = false) {
        if (leavePage && !state.value.isLoading && !state.value.isSaving && !state.value.isConfiguring) {
            // Publish the departure before cancellation can release the global gate.
            state.update { it.copy(isLeaving = true) }
        }
        generationJob?.cancel()
    }
    fun requestDeparture() {
        val current = state.value
        if (!current.backUnavailable && !current.isGenerating && current.previewPath == null) {
            state.update { it.copy(isLeaving = true) }
        }
    }
    fun cancelDeparture() {
        if (!state.value.isSaving && !state.value.saved) state.update { it.copy(isLeaving = false) }
    }
    fun savePreview() {
        val current = state.value
        if (!current.canSave) return
        val path = current.previewPath ?: return
        state.update { it.copy(isSaving = true, error = null) }
        saveScope.launch {
            try {
                appendImage(itemId, path)
                clearStoredPreview()
                state.update { it.copy(previewPath = null, saved = true, isLeaving = true) }
            } catch (failure: Exception) {
                state.update { it.copy(error = safeMessage(failure, "图片添加失败，请核对裙子是否仍存在后重试")) }
            } finally {
                state.update { it.copy(isSaving = false) }
                if (cleared) { releasePreview(); saveScope.cancel() }
            }
        }
    }
    fun discardPreview(leavePage: Boolean = false) {
        if (state.value.isLoading || state.value.isSaving || state.value.isGenerating) return
        if (leavePage) state.update { it.copy(isLeaving = true) }
        releasePreview()
    }
    private fun releasePreview() {
        val path = state.value.previewPath
        clearStoredPreview()
        state.update { it.copy(previewPath = null) }
        path?.let(discardImage)
    }
    private suspend fun validateRestoredPreview(path: String): Boolean = try {
        validatePreview(path)
    } catch (failure: Exception) {
        if (failure is CancellationException) throw failure
        false
    }
    private fun clearStoredPreview() {
        savedStateHandle.remove<String>(PREVIEW)
        savedStateHandle.remove<Long>(PREVIEW_ITEM)
    }
    override fun onCleared() {
        cleared = true
        if (!state.value.isSaving) { releasePreview(); saveScope.cancel() }
        super.onCleared()
    }
    private fun safeMessage(failure: Exception, fallback: String): String = failure.message
        ?.takeIf { it.length <= 180 && it.any { c -> c in '\u4e00'..'\u9fff' } && !it.contains("sk-") } ?: fallback

    companion object {
        private const val SOURCE = "mannequinSource"
        private const val INSTRUCTIONS = "mannequinInstructions"
        private const val PREVIEW = "mannequinPreview"
        private const val PREVIEW_ITEM = "mannequinPreviewItem"
    }
}
