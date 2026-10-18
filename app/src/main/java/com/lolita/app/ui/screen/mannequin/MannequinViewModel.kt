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
import com.lolita.app.domain.model.MannequinPresentation
import com.lolita.app.domain.model.MannequinSubject
import com.lolita.app.domain.model.mannequinSubject
import com.lolita.app.domain.model.WigHeadTemplateSnapshot
import com.lolita.app.data.mannequin.WigHeadTemplate
import com.lolita.app.data.mannequin.WigHeadTemplateCatalog
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
    val error: String? = null,
    val headTemplates: List<WigHeadTemplate> = emptyList(),
    val selectedHeadTemplate: WigHeadTemplateSnapshot? = null,
    val isLoadingHead: Boolean = false,
    val referenceViewConfirmed: Boolean = false,
    val originalImagePath: String? = null
) {
    val subject: MannequinSubject? get() = mannequinSubject(category, confirmedSemantics = true)
    val presentation: MannequinPresentation get() = subject?.presentation ?: MannequinPresentation.DRESS_FORM
    val busy get() = isSubmitting || isConfiguring || savingLegacy || isLoadingHead
    val selectionIssue: String? get() = when {
        item == null || item.id <= 0 -> "这件物品已被删除或无法读取"
        subject == null -> "请先在分类中确认稳定品类；非裙类服装暂不支持展示图"
        presentation == MannequinPresentation.WIG_HEAD && selectedHeadTemplate == null -> "请选择并核对本次使用的头模候选"
        presentation == MannequinPresentation.WIG_HEAD && !referenceViewConfirmed -> "请核对原图能看到刘海、头顶、正面轮廓及完整发梢"
        selectedImage.isBlank() || (originalImagePath ?: selectedImage) !in item.imageUrls -> "参考图片已改变，请重新选择当前图片"
        else -> null
    }
    val canGenerate get() = !busy && !isLoading && !loadFailed && keyLoaded && hasKey && selectionIssue == null
    val generationRequest: MannequinRequest? get() = if (selectionIssue == null)
        MannequinRequest(requireNotNull(item).id, selectedImage, instructions, requireNotNull(subject),
            selectedHeadTemplate.takeIf { presentation == MannequinPresentation.WIG_HEAD }, referenceViewConfirmed, originalImagePath) else null

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
    },
    private val readHeadTemplates: suspend () -> List<WigHeadTemplate> = { WigHeadTemplateCatalog(AppModule.context()).templates },
    private val snapshotHead: suspend (String) -> WigHeadTemplateSnapshot = { WigHeadTemplateCatalog(AppModule.context()).snapshot(it) },
    private val readHeadPreference: suspend () -> WigHeadTemplateSnapshot? = { WigHeadTemplateCatalog.readPreference(AppModule.context()) },
    private val writeHeadPreference: suspend (WigHeadTemplateSnapshot) -> Unit = { WigHeadTemplateCatalog.writePreference(AppModule.context(), it) },
    private val pinCropReferences: (List<String>) -> (suspend () -> Unit) = { paths ->
        val lease = ImageFileHelper.pinImageReferences(AppModule.context(), paths)
        val release: suspend () -> Unit = { lease.release() }
        release
    }
) : ViewModel() {
    private val itemId = savedStateHandle.get<Long>("itemId") ?: 0L
    private val state = MutableStateFlow(MannequinUiState(
        latestTaskId = savedStateHandle[LAST_TASK],
        legacyPreviewAvailable = savedStateHandle.contains(PREVIEW),
        selectedImage = savedStateHandle.get<String>(SOURCE).orEmpty(),
        instructions = savedStateHandle.get<String>(INSTRUCTIONS).orEmpty().take(200),
        selectedHeadTemplate = savedStateHandle.get<String>(HEAD_TEMPLATE)?.let { runCatching { WigHeadTemplateSnapshot.decode(it) }.getOrNull() },
        referenceViewConfirmed = savedStateHandle[VIEW_CONFIRMED] ?: false,
        originalImagePath = savedStateHandle[CROP_ORIGINAL]
    ))
    val uiState: StateFlow<MannequinUiState> = state.asStateFlow()
    private var restored = false
    private var retryEntryConsumed = savedStateHandle.get<Boolean>(RETRY_ENTRY_CONSUMED) == true || savedStateHandle.contains(INSTRUCTIONS)
    private var observedTasks = emptyList<ImageTask>()
    private var headCatalogStarted = false
    private var releaseCropLease: (suspend () -> Unit)? = state.value.originalImagePath?.let { pinCropReferences(listOf(it, state.value.selectedImage)) }
    private val legacySaveScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var cleared = false

    init {
        viewModelScope.launch {
            combine(item, categories) { row, groups -> row to groups.firstOrNull { it.id == row?.categoryId } }
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                    state.update { it.copy(isLoading = false, loadFailed = true, error = "物品读取失败，请返回后重试") }
                }.collect { (row, category) ->
                    if (!restored) {
                        restored = true
                        if (!savedStateHandle.contains(SOURCE)) {
                            savedStateHandle[SOURCE] = row?.imageUrls?.firstOrNull { it.isNotBlank() }.orEmpty()
                        }
                        if (!retryEntryConsumed) {
                            savedStateHandle.get<String>("retryTaskId")?.takeIf { it.isNotBlank() }?.let { id ->
                                val retry = try { readRetryTask(id)?.let { ImageTaskRequest.decode(it.requestJson) } }
                                catch (failure: CancellationException) { throw failure }
                                catch (_: Exception) { null }
                                if (retry != null && retry.garments.singleOrNull()?.itemId == itemId) {
                                    retry.wigHeadTemplate?.let { head ->
                                        savedStateHandle[HEAD_TEMPLATE] = com.google.gson.Gson().toJson(head)
                                        state.update { it.copy(selectedHeadTemplate = head, referenceViewConfirmed = false) }
                                    }
                                    savedStateHandle[INSTRUCTIONS] = retry.instructions
                                    state.update { it.copy(instructions = retry.instructions, error = "已载入当前图片，请核对后再次确认费用；原任务保留") }
                                }
                            }
                            retryEntryConsumed = true
                            savedStateHandle[RETRY_ENTRY_CONSUMED] = true
                        }
                        val legacy = savedStateHandle.get<String>(PREVIEW)
                        val owner = savedStateHandle.get<Long>("mannequinPreviewItem")
                        if (legacy != null && owner == itemId && row?.id == itemId && legacy !in row.imageUrls && validLegacy(legacy)) {
                            state.update { it.copy(legacyPreviewPath = legacy) }
                        }
                        // Rejected legacy paths remain untouched and cannot be used or deleted by this page.
                        state.update { it.copy(selectedImage = savedStateHandle.get<String>(SOURCE).orEmpty()) }
                    }
                    val oldCategory = state.value.category
                    state.update { it.copy(item = row?.takeIf { it.id == itemId }, category = category, isLoading = false,
                        referenceViewConfirmed = if (oldCategory != null && oldCategory.categorySemantic != category?.categorySemantic) false else it.referenceViewConfirmed) }
                    if (mannequinSubject(category, confirmedSemantics = true)?.presentation == MannequinPresentation.WIG_HEAD && !headCatalogStarted) loadHeadCatalog()
                }
        }
        viewModelScope.launch { tasks.collect { rows -> observedTasks = rows; state.update { it.copy(latestTask = rows.firstOrNull { row -> row.id == it.latestTaskId }) } } }
        viewModelScope.launch {
            serviceChanges.collect {
                state.update { it.copy(keyLoaded = false, keyRevision = it.keyRevision + 1) }
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

    private fun loadHeadCatalog() {
        headCatalogStarted = true
        state.update { it.copy(isLoadingHead = true) }
        viewModelScope.launch {
            try {
                val templates = withContext(Dispatchers.IO) { readHeadTemplates() }
                state.update { it.copy(headTemplates = templates) }
                val restoredHead = state.value.selectedHeadTemplate
                if (restoredHead != null) withContext(Dispatchers.IO) { restoredHead.validateImage() }
                else {
                    val preferred = withContext(Dispatchers.IO) { readHeadPreference() }
                    val candidate = if (preferred == null) templates.firstOrNull() else templates.firstOrNull {
                        it.id == preferred.id && it.version == preferred.version && it.sha256 == preferred.sha256
                    }
                    requireNotNull(candidate) { "原头模偏好已换版或下架，请明确重新选择；不会自动替换" }
                    val snapshot = withContext(Dispatchers.IO) { snapshotHead(candidate.id).also { writeHeadPreference(it) } }
                    savedStateHandle[HEAD_TEMPLATE] = com.google.gson.Gson().toJson(snapshot)
                    state.update { it.copy(selectedHeadTemplate = snapshot) }
                }
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { state.update { it.copy(selectedHeadTemplate = null, error = "头模资源缺失、偏好已换版或校验失败，请明确重新选择候选") } }
            finally { state.update { it.copy(isLoadingHead = false) } }
        }
    }

    fun selectHeadTemplate(id: String) {
        val current = state.value
        if (current.busy || current.headTemplates.none { it.id == id }) return
        state.update { it.copy(isLoadingHead = true, referenceViewConfirmed = false, error = null) }
        savedStateHandle[VIEW_CONFIRMED] = false
        viewModelScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) { snapshotHead(id).also { writeHeadPreference(it) } }
                savedStateHandle[HEAD_TEMPLATE] = com.google.gson.Gson().toJson(snapshot)
                state.update { it.copy(selectedHeadTemplate = snapshot) }
                current.selectedHeadTemplate?.takeIf { it.imagePath != snapshot.imagePath }?.let { discardLegacy(it.imagePath) }
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { state.update { it.copy(selectedHeadTemplate = null, error = "头模读取或校验失败，请重新选择") } }
            finally { state.update { it.copy(isLoadingHead = false) } }
        }
    }

    fun confirmReferenceView(value: Boolean) {
        if (state.value.busy) return
        savedStateHandle[VIEW_CONFIRMED] = value
        state.update { it.copy(referenceViewConfirmed = value) }
    }

    fun acceptCroppedImage(originalPath: String, croppedPath: String) {
        val current = state.value
        if (current.busy || originalPath !in current.item?.imageUrls.orEmpty() || croppedPath.isBlank() || croppedPath == originalPath) {
            discardLegacy(croppedPath)
            return
        }
        val previous = current.selectedImage.takeIf { current.originalImagePath != null }
        val oldLease = releaseCropLease
        releaseCropLease = pinCropReferences(listOf(originalPath, croppedPath))
        savedStateHandle[SOURCE] = croppedPath
        savedStateHandle[CROP_ORIGINAL] = originalPath
        savedStateHandle[VIEW_CONFIRMED] = false
        state.update { it.copy(selectedImage = croppedPath, originalImagePath = originalPath, referenceViewConfirmed = false, error = null) }
        cropCleanupScope.launch { oldLease?.invoke(); previous?.takeIf { it != croppedPath }?.let(discardLegacy) }
    }

    fun useOriginalImage() { state.value.originalImagePath?.let(::selectImage) }

    fun selectImage(path: String) {
        val current = state.value
        if (current.busy || path.isBlank() || path !in current.item?.imageUrls.orEmpty()) return
        val previous = current.selectedImage.takeIf { current.originalImagePath != null }
        val oldLease = releaseCropLease
        releaseCropLease = null
        savedStateHandle.remove<String>(CROP_ORIGINAL)
        savedStateHandle[SOURCE] = path
        savedStateHandle[VIEW_CONFIRMED] = false
        state.update { it.copy(selectedImage = path, originalImagePath = null, referenceViewConfirmed = false, error = null) }
        cropCleanupScope.launch { oldLease?.invoke(); previous?.let(discardLegacy) }
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
    fun generate(confirmedProvider: ImageGenerationProvider = state.value.provider,
        confirmedRequest: MannequinRequest? = null, confirmedKeyRevision: Int? = null) {
        val current = state.value
        if (current.busy || current.isLoading || current.loadFailed || !current.keyLoaded) return
        if (confirmedProvider != current.provider) {
            state.update { it.copy(error = "图像服务已改变，请核对后重新确认") }; return
        }
        if (confirmedKeyRevision != null && confirmedKeyRevision != current.keyRevision) {
            state.update { it.copy(error = "服务配置已改变，请核对后重新确认") }; return
        }
        if (!current.hasKey) { state.update { it.copy(error = "请先配置个人 API Key") }; return }
        current.selectionIssue?.let { issue -> state.update { it.copy(error = issue) }; return }
        val request = requireNotNull(current.generationRequest)
        if (confirmedRequest != null && confirmedRequest != request) {
            state.update { it.copy(error = "图片、分类或说明已改变，请重新核对后确认") }; return
        }
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
        val lease = releaseCropLease
        releaseCropLease = null
        cropCleanupScope.launch { lease?.invoke() }
        if (!state.value.savingLegacy) legacySaveScope.cancel()
        // Legacy preview ownership is deliberately retained across navigation/recreation.
        super.onCleared()
    }
    companion object {
        private val cropCleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private const val CROP_ORIGINAL = "mannequinCropOriginalImage"
        private const val HEAD_TEMPLATE = "mannequinHeadTemplateSnapshot"
        private const val VIEW_CONFIRMED = "mannequinReferenceViewConfirmed"
        private const val RETRY_ENTRY_CONSUMED = "mannequinRetryEntryConsumed"
        private const val LAST_TASK = "mannequinLatestTaskId"
        private const val SOURCE = "mannequinSource"
        private const val INSTRUCTIONS = "mannequinInstructions"
        private const val PREVIEW = "mannequinPreview"
    }
}
