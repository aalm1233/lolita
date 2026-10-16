package com.lolita.app.ui.screen.personalmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.entity.ImageTask
import com.lolita.app.data.local.entity.PersonalModel
import com.lolita.app.data.local.entity.PersonalModelDraft
import com.lolita.app.data.tryon.ImageServiceStatus
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

data class PersonalModelEditUiState(
    val draft: PersonalModelDraftData = PersonalModelDraftData(),
    val paths: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val isSavingDraft: Boolean = false,
    val draftSaveFailed: Boolean = false,
    val isCopying: Boolean = false,
    val isSubmitting: Boolean = false,
    val isConfiguring: Boolean = false,
    val isDiscarding: Boolean = false,
    val discarded: Boolean = false,
    val dirty: Boolean = false,
    val provider: ImageGenerationProvider = ImageGenerationProvider.SEEDREAM,
    val hasKey: Boolean = false,
    val keyLoaded: Boolean = false,
    val keyRevision: Int = 0,
    val taskId: String? = null,
    val error: String? = null
) {
    val busy get() = isCopying || isSubmitting || isConfiguring || isDiscarding || taskId != null
    /** Stored photos stay in the draft when a different mode hides them. */
    fun effectivePhotos(): List<Pair<PersonalModelPhotoRole, String>> = draft.photoRoles.zip(paths).filter { (role, _) ->
        if (draft.mode == PersonalModelMode.PHOTO) role != PersonalModelPhotoRole.VIRTUAL_FACE else role == PersonalModelPhotoRole.VIRTUAL_FACE
    }
    fun request(): PersonalModelRequest {
        fun number(raw: String, title: String): Double = raw.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
            ?: throw IllegalArgumentException("请填写有效的$title")
        fun optional(raw: String, title: String): Double? = if (raw.isBlank()) null else number(raw, title)
        return PersonalModelRequest(draft.name.trim(), draft.mode,
            BodyMeasurements(number(draft.heightCm, "身高"), number(draft.weightKg, "体重"), optional(draft.bustCm, "胸围"),
                optional(draft.waistCm, "腰围"), optional(draft.hipCm, "臀围"), optional(draft.shoulderCm, "肩宽"), optional(draft.inseamCm, "内侧腿长")),
            effectivePhotos().map { it.first }, draft.appearance, draft.instructions)
    }
    val validationIssue: String? get() = runCatching { request().validatePaths(effectivePhotos().map { it.second }) }.exceptionOrNull()?.message
    val canGenerate get() = !isLoading && !loadFailed && !busy && !isSavingDraft && !draftSaveFailed && keyLoaded && validationIssue == null
}

/** Durable drafts own references; UI lifecycle cancellation never discards the draft. */
class PersonalModelEditViewModel @JvmOverloads constructor(
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val readDraft: suspend (String) -> PersonalModelDraft? = { AppModule.personalModelRepository().getDraft(it) },
    private val saveDraft: suspend (PersonalModelDraft) -> Unit = { AppModule.personalModelRepository().saveDraft(it) },
    private val deleteDraft: suspend (String) -> Unit = { AppModule.personalModelRepository().deleteDraft(it) },
    private val readModel: suspend (Long) -> PersonalModel? = { AppModule.personalModelRepository().get(it) },
    private val readRetryTask: suspend (String) -> ImageTask? = { AppModule.imageTaskRepository().get(it) },
    private val readService: suspend () -> ImageServiceStatus = { AppModule.tryOnSettings().readStatus() },
    serviceChanges: Flow<Int> = AppModule.tryOnSettings().changes,
    private val selectService: suspend (ImageGenerationProvider) -> Unit = { AppModule.tryOnSettings().selectProvider(it) },
    private val saveApiKey: suspend (ImageGenerationProvider, String) -> Unit = { provider, key -> AppModule.tryOnSettings().saveKey(key, provider) },
    private val clearApiKey: suspend (ImageGenerationProvider) -> Unit = { AppModule.tryOnSettings().clearKey(it) },
    private val submitTask: suspend (PersonalModelRequest, List<String>, ImageGenerationProvider) -> String = { request, paths, provider ->
        AppModule.imageTaskCoordinator().submitPersonalModel(request, paths, provider)
    },
    private val imageReadable: suspend (String) -> Boolean = { withContext(Dispatchers.IO) { File(it).isFile && File(it).canRead() } },
    private val copyPhoto: suspend (Context, Uri) -> String = { context, uri ->
        val path = ImageFileHelper.copyToInternalStorage(context, uri)
        try { check(ImageFileHelper.isOwnedReadableImage(context, path)) {
            if (android.os.Build.VERSION.SDK_INT < 28) "照片无法完整读取，请另存为 PNG 或 JPEG 后重新选择"
            else "照片无法完整读取，请选择完整照片，或另存为 PNG / JPEG 后重试"
        }; path }
        catch (e: Throwable) { withContext(NonCancellable) { ImageFileHelper.deleteImage(path) }; throw e }
    },
    private val deleteImage: suspend (String) -> Unit = { ImageFileHelper.deleteImage(it) }
) : ViewModel() {
    private val draftId: String = savedStateHandle.get<String>(DRAFT_ID) ?: UUID.randomUUID().toString().also { savedStateHandle[DRAFT_ID] = it }
    private val state = MutableStateFlow(PersonalModelEditUiState(taskId = savedStateHandle[LAST_TASK]))
    val uiState = state.asStateFlow()
    private val persistence = Mutex()
    private var pendingWrites = 0
    private var copyJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val existing = readDraft(draftId)
                if (existing != null) {
                    existing.validate()
                    state.update { it.copy(draft = existing.data(), paths = existing.sourceImagePaths, dirty = true, isLoading = false) }
                } else {
                    val retryId = savedStateHandle.get<String>("retryTaskId")?.takeIf(String::isNotBlank)
                    val modelId = savedStateHandle.get<Long>("modelId")?.takeIf { it > 0 }
                    when {
                        retryId != null -> {
                            val task = readRetryTask(retryId) ?: error("原任务已删除，请重新创建模特")
                            check(task.kind == ImageTaskKind.PERSONAL_MODEL) { "原任务不是个人模特任务" }
                            val request = ImageTaskRequest.decode(task.requestJson).personalModel ?: error("原任务参数无法读取")
                            request.validatePaths(task.sourceImagePaths)
                            val references = request.photoRoles.zip(task.sourceImagePaths).filter { imageReadable(it.second) }
                            val missing = references.size != task.sourceImagePaths.size
                            // Missing photos may be reselected; loading never triggers another paid call.
                            prefill(request, references.map { it.second }, request.name, references.map { it.first })
                            state.update { it.copy(error = if (missing) "部分原参考照片已移除，请重新选择并核对后生成" else "已载入原参数与参考照片，请核对后重新确认生成") }
                        }
                        modelId != null -> {
                            val model = readModel(modelId) ?: error("原模特已删除，请返回模特库重新选择")
                            val request = model.request()
                            val paths = if (request.mode == PersonalModelMode.PHOTO) model.sourceImagePaths else listOf(model.imageUrl)
                            val adjusted = if (request.mode == PersonalModelMode.PARAMETERS) request.copy(photoRoles = listOf(PersonalModelPhotoRole.VIRTUAL_FACE)) else request
                            adjusted.validatePaths(paths)
                            check(paths.all { imageReadable(it) }) { "模特参考照片已移除，请返回重新选择" }
                            prefill(adjusted, paths, "${model.name.take(55)}（调整）")
                        }
                        else -> state.update { it.copy(isLoading = false) }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { state.update { it.copy(isLoading = false, loadFailed = true, error = e.message ?: "草稿读取失败，请返回重试") } }
        }
        viewModelScope.launch {
            serviceChanges.collect {
                try {
                    val status = withContext(Dispatchers.IO) { readService() }
                    state.update { it.copy(provider = status.provider, hasKey = status.hasKey, keyLoaded = true) }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { state.update { it.copy(keyLoaded = true, hasKey = false, error = "图像服务配置读取失败，请重新配置") } }
            }
        }
    }
    private suspend fun prefill(request: PersonalModelRequest, paths: List<String>, name: String, roles: List<PersonalModelPhotoRole> = request.photoRoles) {
        val m = request.measurements
        fun Double?.raw() = this?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.orEmpty()
        val data = PersonalModelDraftData(name = name, mode = request.mode, heightCm = m.heightCm.raw(), weightKg = m.weightKg.raw(),
            bustCm = m.bustCm.raw(), waistCm = m.waistCm.raw(), hipCm = m.hipCm.raw(), shoulderCm = m.shoulderCm.raw(),
            inseamCm = m.inseamCm.raw(), photoRoles = roles, appearance = request.appearance, instructions = request.instructions)
        // Persist ownership before enabling the screen; deleting the original cannot strand an adjustment.
        persistence.withLock { saveDraft(PersonalModelDraft(draftId, data.encode(), paths)) }
        state.update { it.copy(draft = data, paths = paths, dirty = true, isLoading = false) }
    }
    fun update(transform: (PersonalModelDraftData) -> PersonalModelDraftData) {
        val old = state.value
        if (old.isLoading || old.loadFailed || old.busy) return
        val next = transform(old.draft)
        if (next == old.draft || next.photoRoles != old.draft.photoRoles) return
        state.update { it.copy(draft = next, dirty = true, error = null) }
        persist()
    }
    private fun persist() {
        val snapshot = state.value
        pendingWrites++
        state.update { it.copy(isSavingDraft = true) }
        viewModelScope.launch {
            try {
                persistence.withLock { saveDraft(PersonalModelDraft(draftId, snapshot.draft.encode(), snapshot.paths)) }
                state.update { it.copy(draftSaveFailed = false) }
            }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { state.update { it.copy(error = "草稿保存失败，请重试保存后再生成", draftSaveFailed = true) } }
            finally { pendingWrites--; state.update { it.copy(isSavingDraft = pendingWrites > 0) } }
        }
    }
    fun retrySaveDraft() {
        if (!state.value.busy && !state.value.isLoading && !state.value.loadFailed && !state.value.isSavingDraft) {
            state.update { it.copy(error = null) }
            persist()
        }
    }
    fun addPhoto(context: Context, uri: Uri, role: PersonalModelPhotoRole) {
        if (state.value.busy || state.value.isLoading || state.value.loadFailed || role == PersonalModelPhotoRole.VIRTUAL_FACE) return
        state.update { it.copy(isCopying = true, error = null) }
        copyJob = viewModelScope.launch {
            var copied: String? = null
            var adopted = false
            try {
                val path = copyPhoto(context, uri)
                copied = path
                ensureActive()
                val previous = state.value
                val entries = previous.draft.photoRoles.zip(previous.paths).filterNot { it.first == role } + (role to path)
                val data = previous.draft.copy(photoRoles = entries.map { it.first })
                // A completed copy is either committed to a durable row or cleaned up on cancellation/failure.
                withContext(NonCancellable) {
                    persistence.withLock { saveDraft(PersonalModelDraft(draftId, data.encode(), entries.map { it.second })) }
                    adopted = true
                    state.update { it.copy(draft = data, paths = entries.map { row -> row.second }, dirty = true) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { state.update { it.copy(error = e.message ?: "照片读取失败，请重新选择") } }
            finally {
                if (!adopted) copied?.let { path -> withContext(NonCancellable) { deleteImage(path) } }
                state.update { it.copy(isCopying = false) }
            }
        }
    }
    fun cancelPhotoCopy() { copyJob?.cancel() }
    fun removePhoto(role: PersonalModelPhotoRole) {
        val old = state.value
        if (old.busy || old.isLoading || old.loadFailed) return
        val entries = old.draft.photoRoles.zip(old.paths).filterNot { it.first == role }
        state.update { it.copy(draft = it.draft.copy(photoRoles = entries.map { row -> row.first }), paths = entries.map { row -> row.second }, dirty = true) }
        persist()
    }
    fun generate(confirmedProvider: ImageGenerationProvider = state.value.provider) {
        val snapshot = state.value
        if (snapshot.isLoading || snapshot.loadFailed || snapshot.busy || snapshot.isSavingDraft || snapshot.draftSaveFailed || !snapshot.keyLoaded) return
        if (!snapshot.hasKey) { state.update { it.copy(error = "请先配置个人 API Key") }; return }
        if (confirmedProvider != snapshot.provider) { state.update { it.copy(error = "图像服务已改变，请重新确认") }; return }
        val request = try { snapshot.request().also { it.validatePaths(snapshot.effectivePhotos().map { photo -> photo.second }) } }
            catch (e: Exception) { state.update { it.copy(error = e.message) }; return }
        val paths = snapshot.effectivePhotos().map { it.second }
        state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            try {
                check(paths.all { imageReadable(it) }) { "参考照片已移除，请重新选择" }
                val id = submitTask(request, paths, confirmedProvider)
                savedStateHandle[LAST_TASK] = id
                state.update { it.copy(taskId = id, dirty = false) }
                // The accepted task owns all submitted references before the draft releases any files.
                persistence.withLock { deleteDraft(draftId) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { state.update { it.copy(error = e.message ?: "任务提交失败，请核对照片与配置") } }
            finally { state.update { it.copy(isSubmitting = false) } }
        }
    }
    fun discard() {
        val old = state.value
        if (old.busy || old.isSavingDraft || old.isLoading) return
        state.update { it.copy(isDiscarding = true) }
        viewModelScope.launch {
            try { persistence.withLock { deleteDraft(draftId) }; state.update { it.copy(discarded = true, dirty = false) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { state.update { it.copy(error = "草稿未能删除，请重试") } }
            finally { state.update { it.copy(isDiscarding = false) } }
        }
    }
    fun selectProvider(provider: ImageGenerationProvider) { if (provider != state.value.provider) configure { selectService(provider) } }
    fun saveKey(key: String) { if (key.isBlank()) return; val provider = state.value.provider; configure { saveApiKey(provider, key) } }
    fun clearKey() { val provider = state.value.provider; configure { clearApiKey(provider) } }
    private fun configure(operation: suspend () -> Unit) {
        if (state.value.busy || !state.value.keyLoaded) return
        state.update { it.copy(isConfiguring = true) }
        viewModelScope.launch {
            try {
                val status = withContext(Dispatchers.IO) { operation(); readService() }
                state.update { it.copy(provider = status.provider, hasKey = status.hasKey, keyRevision = it.keyRevision + 1) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { state.update { it.copy(error = "服务配置失败，请重试") } }
            finally { state.update { it.copy(isConfiguring = false) } }
        }
    }
    companion object { private const val DRAFT_ID = "personalModelDraftId"; private const val LAST_TASK = "personalModelLastTask" }
}
