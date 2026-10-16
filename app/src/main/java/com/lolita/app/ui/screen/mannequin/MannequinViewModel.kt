package com.lolita.app.ui.screen.mannequin

import com.lolita.app.data.tryon.ImageServiceStatus
import com.lolita.app.domain.model.ImageGenerationProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.domain.model.ImageTaskRequest
import com.lolita.app.data.local.entity.ImageTask
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
    val latestTaskId: String? = null,
    val latestTask: ImageTask? = null,
    val legacyPreviewAvailable: Boolean = false,
    val legacyPreviewPath: String? = null,
    val savingLegacy: Boolean = false,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val keyLoaded: Boolean = false,
    val provider: ImageGenerationProvider = ImageGenerationProvider.SEEDREAM,
    val hasKey: Boolean = false,
    val keyRevision: Int = 0,
    val isConfiguring: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null
) {
    val busy get() = isSubmitting || isConfiguring || savingLegacy
    val selectionIssue: String? get() = when {
        item == null -> "这件裙子已被删除或无法读取"
        !supportsMannequin(category) -> "仅支持服装分类中的 JSK、OP、SK 和明确裙类"
        selectedImage.isBlank() || selectedImage !in item.imageUrls -> "参考图片已改变，请重新选择当前图片"
        else -> null
    }
    val canGenerate get() = !busy && !isLoading && !loadFailed && keyLoaded && hasKey && selectionIssue == null

}

/** Editable draft only. Accepted jobs and preview files belong to the application coordinator. */
class MannequinViewModel @JvmOverloads constructor(
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    item: Flow<Item?> = AppModule.itemRepository().observeItem(savedStateHandle.get<Long>("itemId") ?: 0L),
    categories: Flow<List<Category>> = AppModule.categoryRepository().getAllCategories(),
    private val readService: suspend () -> ImageServiceStatus = { AppModule.tryOnSettings().readStatus() },
    private val selectService: suspend (ImageGenerationProvider) -> Unit = { AppModule.tryOnSettings().selectProvider(it) },
    serviceChanges: Flow<Int> = AppModule.tryOnSettings().changes,
    private val saveApiKey: suspend (ImageGenerationProvider, String) -> Unit = { provider, value -> AppModule.tryOnSettings().saveKey(value, provider) },
    private val clearApiKey: suspend (ImageGenerationProvider) -> Unit = { AppModule.tryOnSettings().clearKey(it) },
    tasks: Flow<List<ImageTask>> = AppModule.imageTaskRepository().tasks,
    private val readRetryTask: suspend (String) -> ImageTask? = { AppModule.imageTaskRepository().get(it) },
    private val validateLegacy: suspend (String) -> Boolean = { ImageFileHelper.isOwnedGeneratedImage(AppModule.context(), it) },
    private val appendLegacy: suspend (Long, String) -> Unit = { id, path -> AppModule.itemRepository().appendGeneratedImage(id, path) },
    private val discardLegacy: (String) -> Unit = { ImageFileHelper.cleanupUnreferencedImages(listOf(it)); Unit },
    private val submitTask: suspend (MannequinRequest, ImageGenerationProvider) -> String = { request, provider ->
        AppModule.imageTaskCoordinator().submitMannequin(request, provider)
    }
) : ViewModel() {
    private val itemId = savedStateHandle.get<Long>("itemId") ?: 0L
    private val state = MutableStateFlow(MannequinUiState(
        latestTaskId = savedStateHandle[LAST_TASK],
        legacyPreviewAvailable = savedStateHandle.contains(PREVIEW),
        selectedImage = savedStateHandle.get<String>(SOURCE).orEmpty(),
        instructions = savedStateHandle.get<String>(INSTRUCTIONS).orEmpty().take(200)
    ))
    val uiState: StateFlow<MannequinUiState> = state.asStateFlow()
    private var restored = false
    private var observedTasks = emptyList<ImageTask>()
    private val legacySaveScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var cleared = false

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
                        savedStateHandle.get<String>("retryTaskId")?.takeIf { it.isNotBlank() }?.let { id ->
                            val retry = runCatching { readRetryTask(id)?.let { ImageTaskRequest.decode(it.requestJson) } }.getOrNull()
                            if (retry != null && retry.garments.singleOrNull()?.itemId == itemId) {
                                savedStateHandle[INSTRUCTIONS] = retry.instructions
                                state.update { it.copy(instructions = retry.instructions, error = "已载入当前图片，请核对后再次确认费用；原任务保留") }
                            }
                        }
                        val legacy = savedStateHandle.get<String>(PREVIEW)
                        val owner = savedStateHandle.get<Long>("mannequinPreviewItem")
                        if (legacy != null && owner == itemId && row?.id == itemId && legacy !in row.imageUrls && validLegacy(legacy)) {
                            state.update { it.copy(legacyPreviewPath = legacy) }
                        }
                        // Rejected legacy paths remain untouched and cannot be used or deleted by this page.
                        state.update { it.copy(selectedImage = savedStateHandle.get<String>(SOURCE).orEmpty()) }
                    }
                    state.update { it.copy(item = row?.takeIf { it.id == itemId }, category = category, isLoading = false) }
                }
        }
        viewModelScope.launch { tasks.collect { rows -> observedTasks = rows; state.update { it.copy(latestTask = rows.firstOrNull { row -> row.id == it.latestTaskId }) } } }
        viewModelScope.launch {
            serviceChanges.collect {
                state.update { it.copy(keyLoaded = false) }
                try {
                    val status = withContext(Dispatchers.IO) { readService() }
                    state.update { it.copy(provider = status.provider, hasKey = status.hasKey, keyLoaded = true) }
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    state.update { it.copy(hasKey = false, keyLoaded = true, error = "图像服务配置读取失败，请重新选择服务") }
                }
            }
        }
    }

    fun selectImage(path: String) {
        val current = state.value
        if (current.busy || path.isBlank() || path !in current.item?.imageUrls.orEmpty()) return
        savedStateHandle[SOURCE] = path
        state.update { it.copy(selectedImage = path, error = null) }
    }
    fun updateInstructions(value: String) {
        if (state.value.busy || state.value.isLoading) return
        savedStateHandle[INSTRUCTIONS] = value.take(200)
        state.update { it.copy(instructions = value.take(200)) }
    }
    fun selectProvider(provider: ImageGenerationProvider) {
        if (state.value.busy || !state.value.keyLoaded || provider == state.value.provider) return
        configureService { selectService(provider) }
    }
    fun saveKey(value: String) {
        if (state.value.busy || !state.value.keyLoaded) return
        if (value.isBlank()) { state.update { it.copy(error = "请填写 API Key") }; return }
        val provider = state.value.provider
        configureService { saveApiKey(provider, value) }
    }
    fun clearKey() {
        if (state.value.busy || !state.value.keyLoaded) return
        val provider = state.value.provider
        configureService { clearApiKey(provider) }
    }
    private fun configureService(operation: suspend () -> Unit) {
        state.update { it.copy(isConfiguring = true, error = null) }
        viewModelScope.launch {
            try {
                val status = withContext(Dispatchers.IO) { operation(); readService() }
                state.update { it.copy(provider = status.provider, hasKey = status.hasKey, keyLoaded = true, keyRevision = it.keyRevision + 1) }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                state.update { it.copy(error = "图像服务配置失败，请重新输入后重试") }
            } finally { state.update { it.copy(isConfiguring = false) } }
        }
    }
    fun generate(confirmedProvider: ImageGenerationProvider = state.value.provider) {
        val current = state.value
        if (current.busy || current.isLoading || current.loadFailed || !current.keyLoaded) return
        if (confirmedProvider != current.provider) {
            state.update { it.copy(error = "图像服务已改变，请核对后重新确认") }; return
        }
        if (!current.hasKey) { state.update { it.copy(error = "请先配置个人 API Key") }; return }
        current.selectionIssue?.let { issue -> state.update { it.copy(error = issue) }; return }
        val request = MannequinRequest(itemId, current.selectedImage, current.instructions)
        state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            try {
                val id = submitTask(request, confirmedProvider)
                savedStateHandle[LAST_TASK] = id
                state.update { it.copy(latestTaskId = id, latestTask = observedTasks.firstOrNull { row -> row.id == id }) }
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { state.update { it.copy(error = "任务提交失败，请检查图片与服务配置后重试") } }
            finally { state.update { it.copy(isSubmitting = false) } }
        }
    }
    private suspend fun validLegacy(path: String): Boolean = try { validateLegacy(path) }
    catch (failure: CancellationException) { throw failure }
    catch (_: Exception) { false }

    fun saveLegacyPreview() {
        val current = state.value
        val path = current.legacyPreviewPath ?: return
        if (current.busy || current.item == null) return
        state.update { it.copy(savingLegacy = true, error = null) }
        legacySaveScope.launch {
            try {
                check(validateLegacy(path))
                appendLegacy(itemId, path)
                savedStateHandle.remove<String>(PREVIEW)
                savedStateHandle.remove<Long>("mannequinPreviewItem")
                state.update { it.copy(legacyPreviewPath = null, legacyPreviewAvailable = false) }
            } catch (_: Exception) { state.update { it.copy(error = "旧版图片添加失败，请核对原裙子和预览后重试") } }
            finally { state.update { it.copy(savingLegacy = false) }; if (cleared) legacySaveScope.cancel() }
        }
    }
    fun discardLegacyPreview() {
        val current = state.value
        val path = current.legacyPreviewPath ?: return
        if (current.busy) return
        // Only a validated owned preview can enter this branch; cleanup still checks all references.
        savedStateHandle.remove<String>(PREVIEW)
        savedStateHandle.remove<Long>("mannequinPreviewItem")
        state.update { it.copy(legacyPreviewPath = null, legacyPreviewAvailable = false) }
        discardLegacy(path)
    }
    override fun onCleared() {
        cleared = true
        if (!state.value.savingLegacy) legacySaveScope.cancel()
        // Legacy preview ownership is deliberately retained across navigation/recreation.
        super.onCleared()
    }
    companion object {
        private const val LAST_TASK = "mannequinLatestTaskId"
        private const val SOURCE = "mannequinSource"
        private const val INSTRUCTIONS = "mannequinInstructions"
        private const val PREVIEW = "mannequinPreview"
    }
}
