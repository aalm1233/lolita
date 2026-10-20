package com.lolita.app.ui.screen.material

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.generation.ImageTaskCoordinator
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.tryon.ImageServiceStatus
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class MaterialFeeAction { PREPARE, DECOMPOSE, ADD_ACCESSORY }
data class MaterialFeeConfirmation(val action: MaterialFeeAction, val inputs: List<PreparedMaterialInput>,
    val provider: ImageGenerationProvider, val revision: Int, val instructions: String,
    val originalPath: String, val taskId: String?, val expectedMaterialId: String?, val base: AccessoryBaseSnapshot?)
data class MaterialBaseChoice(val snapshot: AccessoryBaseSnapshot, val imagePath: String)

data class MaterialWorkspaceState(
    val item: Item? = null, val semantic: CategorySemantic? = null,
    val material: ItemTryOnMaterial? = null, val task: ImageTask? = null,
    val loading: Boolean = true, val failed: Boolean = false, val busy: Boolean = false,
    val source: String = "", val originalPath: String? = null, val instructions: String = "",
    val provider: ImageGenerationProvider = ImageGenerationProvider.SEEDREAM,
    val hasKey: Boolean = false, val keyLoaded: Boolean = false, val keyRevision: Int = 0,
    val fee: MaterialFeeConfirmation? = null, val selectedLayer: String? = null,
    val checks: Set<Int> = emptySet(), val frontConfirmed: Boolean = false,
    val bases: List<MaterialBaseChoice> = emptyList(), val selectedBase: MaterialBaseChoice? = null,
    val clothingConfirmed: Boolean = false, val submittedTaskId: String? = null,
    val saved: Boolean = false, val error: String? = null
) {
    val request get() = task?.let { runCatching { ImageTaskRequest.decode(it.requestJson) }.getOrNull() }
    val layers get() = task?.let { runCatching { MaterialLayers.decode(it.layersJson) }.getOrDefault(emptyList()) }.orEmpty()
    val eligible get() = item?.status?.supportsVirtualTryOn == true && semantic == CategorySemantic.WAIST_ACCESSORY && !loading && !failed
    val pending get() = task?.status == ImageTaskStatus.AWAITING_CONFIRMATION
    val front get() = request?.materialSpec?.stage == MaterialStage.FRONT_VIEW
    val retryLayers get() = request?.materialSpec?.stage == MaterialStage.EXTRACT_LAYER && task?.sourceResourcesMissing == false &&
        task.status in setOf(ImageTaskStatus.FAILED, ImageTaskStatus.INTERRUPTED, ImageTaskStatus.CANCELLED)
    val layerSource get() = if (retryLayers) task?.sourceImagePaths?.singleOrNull() else task?.resultImagePath
    val selectedPath get() = selectedLayer?.takeIf { chosen -> layers.any { it.imagePath == chosen } } ?: task?.resultImagePath
}

/** Only local preparation/review state; paid work always enters the durable coordinator. */
class MaterialWorkspaceViewModel(
    private val handle: SavedStateHandle,
    items: Flow<List<Item>> = AppModule.itemRepository().getAllItems(),
    categories: Flow<List<Category>> = AppModule.categoryRepository().getAllCategories(),
    materials: Flow<List<ItemTryOnMaterial>> = AppModule.itemTryOnMaterialRepository().materials,
    tasks: Flow<List<ImageTask>> = AppModule.imageTaskRepository().tasks,
    results: Flow<List<TryOnResult>> = AppModule.tryOnRepository().results,
    people: Flow<List<PersonalModel>> = AppModule.personalModelRepository().observe(),
    private val coordinator: ImageTaskCoordinator = AppModule.imageTaskCoordinator(),
    private val readService: suspend () -> ImageServiceStatus = { AppModule.tryOnSettings().readStatus() },
    changes: Flow<Int> = AppModule.tryOnSettings().changes,
    private val pin: (List<String>) -> (suspend () -> Unit) = { paths ->
        val lease = ImageFileHelper.pinImageReferences(AppModule.context(), paths)
        val release: suspend () -> Unit = { lease.release() }; release
    },
    private val cleanup: (List<String>) -> Unit = { ImageFileHelper.cleanupUnreferencedImages(it); Unit }
) : ViewModel() {
    private val itemId = handle.get<Long>("itemId") ?: 0L
    private var generation = 0
    private var releases = emptyList<suspend () -> Unit>()
    private var cropRelease: (suspend () -> Unit)? = null
    private var cropPath: String? = handle["materialCrop"]
    private val state = MutableStateFlow(MaterialWorkspaceState(source = handle.get<String>("materialSource").orEmpty(),
        originalPath = handle["materialOriginal"], instructions = handle.get<String>("materialInstructions").orEmpty().take(200),
        selectedLayer = handle["materialLayer"], checks = handle.get<List<Int>>("materialChecks").orEmpty().toSet(),
        frontConfirmed = handle["materialFrontConfirmed"] ?: false))
    val uiState = state.asStateFlow()
    private var activeTaskId: String? = handle.get<String>("taskId")?.takeIf(String::isNotBlank)

    init {
        cropPath?.let { cropRelease = pin(listOf(it)) }
        viewModelScope.launch {
            combine(items, categories, materials, tasks) { rows, groups, assets, jobs ->
                val item = rows.firstOrNull { it.id == itemId }
                val task = jobs.firstOrNull { it.id == activeTaskId }
                val semantic = groups.firstOrNull { it.id == item?.categoryId }?.categorySemantic
                Triple(item to semantic, assets.firstOrNull { it.itemId == itemId }, task)
            }.catch { failure ->
                if (failure is CancellationException) throw failure
                state.update { it.copy(loading = false, failed = true, error = "资料读取失败，请返回后重试") }
            }.collect { (pair, material, task) ->
                val old = state.value
                if (old.item != pair.first || old.material != material || old.task?.resultImagePath != task?.resultImagePath) invalidateFee()
                state.update { it.copy(item = pair.first, semantic = pair.second, material = material, task = task, loading = false,
                    source = it.source.ifBlank { pair.first?.productReferenceImages?.firstOrNull().orEmpty() }) }
            }
        }
        viewModelScope.launch {
            combine(results, people, tasks) { images, persons, jobs ->
                val retry = jobs.firstOrNull { it.id == activeTaskId }?.let { runCatching { ImageTaskRequest.decode(it.requestJson) }.getOrNull() }
                if (retry?.accessoryBase != null && !handle.contains("materialBaseId")) {
                    handle["materialBaseId"] = retry.accessoryBase.id
                    handle["materialBaseType"] = retry.accessoryBase.type.name
                }
                if (retry != null && !handle.contains("materialInstructions")) {
                    handle["materialInstructions"] = retry.instructions
                    state.update { it.copy(instructions = retry.instructions) }
                }
                images.map { MaterialBaseChoice(AccessoryBaseSnapshot(AccessoryBaseType.TRY_ON_RESULT, it.id, it.name.take(160), true), it.imageUrl) } +
                    persons.map { MaterialBaseChoice(AccessoryBaseSnapshot(AccessoryBaseType.PERSONAL_MODEL, it.id, it.name.take(160), true), it.imageUrl) }
            }.catch { failure ->
                if (failure is CancellationException) throw failure
                state.update { it.copy(error = "试衣底图读取失败，请返回后重试") }
            }.collect { choices ->
                val type = handle.get<String>("materialBaseType")
                val id = handle.get<Long>("materialBaseId") ?: handle.get<Long>("baseResultId")
                val chosen = choices.firstOrNull { it.snapshot.id == id && (it.snapshot.type.name == type || type == null && it.snapshot.type == AccessoryBaseType.TRY_ON_RESULT) }
                if (state.value.selectedBase != chosen) { invalidateFee(); state.update { it.copy(clothingConfirmed = false) } }
                state.update { it.copy(bases = choices, selectedBase = chosen) }
            }
        }
        viewModelScope.launch {
            changes.collect {
                invalidateFee()
                state.update { it.copy(keyLoaded = false, keyRevision = it.keyRevision + 1) }
                try {
                    val status = withContext(Dispatchers.IO) { readService() }
                    state.update { it.copy(provider = status.provider, hasKey = status.hasKey, keyLoaded = true) }
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    state.update { it.copy(hasKey = false, keyLoaded = true, error = "图像服务配置读取失败") }
                }
            }
        }
    }

    fun selectSource(path: String) {
        if (state.value.busy || path !in state.value.item?.productReferenceImages.orEmpty()) return
        invalidateFee(); releaseCrop()
        handle["materialSource"] = path; handle["materialOriginal"] = null
        state.update { it.copy(source = path, originalPath = null, error = null) }
    }
    fun acceptCrop(original: String, cropped: String) {
        if (state.value.busy || original !in state.value.item?.productReferenceImages.orEmpty()) { cleanup(listOf(cropped)); return }
        invalidateFee(); releaseCrop()
        cropPath = cropped; cropRelease = pin(listOf(cropped, original))
        handle["materialCrop"] = cropped; handle["materialSource"] = cropped; handle["materialOriginal"] = original
        state.update { it.copy(source = cropped, originalPath = original) }
    }
    fun instructions(value: String) {
        if (state.value.busy) return
        invalidateFee(); handle["materialInstructions"] = value.take(200)
        state.update { it.copy(instructions = value.take(200), error = null) }
    }
    fun selectLayer(path: String) {
        if (state.value.busy || state.value.layers.none { it.imagePath == path }) return
        handle["materialLayer"] = path; handle["materialChecks"] = emptyList<Int>()
        state.update { it.copy(selectedLayer = path, checks = emptySet(), saved = false) }
    }
    fun check(index: Int, value: Boolean) {
        if (state.value.busy || index !in 0..2) return
        state.update { it.copy(checks = if (value) it.checks + index else it.checks - index) }
        handle["materialChecks"] = state.value.checks.toList()
    }
    fun frontConfirmed(value: Boolean) { state.update { it.copy(frontConfirmed = value) }; handle["materialFrontConfirmed"] = value }
    fun selectBase(base: MaterialBaseChoice) {
        if (state.value.busy || base !in state.value.bases) return
        invalidateFee(); handle["materialBaseType"] = base.snapshot.type.name; handle["materialBaseId"] = base.snapshot.id
        state.update { it.copy(selectedBase = base, clothingConfirmed = false) }
    }
    fun clothingConfirmed(value: Boolean) { invalidateFee(); state.update { it.copy(clothingConfirmed = value) } }

    fun prepareFee(action: MaterialFeeAction) {
        val current = state.value
        if (current.busy || !current.eligible || !current.hasKey || !current.keyLoaded) return
        if (action != MaterialFeeAction.ADD_ACCESSORY && current.provider != ImageGenerationProvider.SEEDREAM) return
        if (action == MaterialFeeAction.DECOMPOSE && ((!current.retryLayers && (!current.pending || !current.front)) || !current.frontConfirmed)) return
        if (action == MaterialFeeAction.ADD_ACCESSORY && (current.material == null || current.selectedBase == null || !current.clothingConfirmed)) return
        if (action == MaterialFeeAction.PREPARE && (current.originalPath ?: current.source) !in current.item!!.productReferenceImages) return
        invalidateFee()
        val ticket = generation
        state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val prepared = mutableListOf<PreparedMaterialInput>()
            val leases = mutableListOf<suspend () -> Unit>()
            try {
                val paths = when (action) {
                    MaterialFeeAction.PREPARE -> listOf(current.source)
                    MaterialFeeAction.DECOMPOSE -> listOf(requireNotNull(current.layerSource))
                    MaterialFeeAction.ADD_ACCESSORY -> listOf(requireNotNull(current.selectedBase).imagePath, requireNotNull(current.material).imagePath)
                }
                for (path in paths) {
                    val input = withContext(Dispatchers.IO) { coordinator.prepareMaterialInput(path, action == MaterialFeeAction.ADD_ACCESSORY && current.provider == ImageGenerationProvider.OPENAI) }
                    prepared += input; leases += pin(listOf(input.imagePath))
                }
                if (ticket != generation) return@launch
                releases = leases.toList()
                state.update { it.copy(fee = MaterialFeeConfirmation(action, prepared.toList(), current.provider, current.keyRevision, current.instructions,
                    current.originalPath ?: current.source, current.task?.id, current.material?.id, current.selectedBase?.snapshot)) }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                state.update { it.copy(error = "无法准备实际输入，请核对图片与服务后重试") }
            } finally {
                if (state.value.fee?.inputs != prepared) { leases.forEach { it() }; cleanup(prepared.map { it.imagePath }) }
                state.update { it.copy(busy = false) }
            }
        }
    }

    fun submitFee(fee: MaterialFeeConfirmation) {
        if (state.value.busy || state.value.fee != fee || !state.value.hasKey || state.value.provider != fee.provider || state.value.keyRevision != fee.revision) return
        state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val status = withContext(Dispatchers.IO) { readService() }
                require(status.hasKey && status.provider == fee.provider && state.value.keyRevision == fee.revision && state.value.fee == fee)
                val id = when (fee.action) {
                    MaterialFeeAction.PREPARE -> coordinator.submitMaterial(itemId, fee.inputs.single(), fee.originalPath, fee.instructions, fee.provider, fee.expectedMaterialId)
                    MaterialFeeAction.DECOMPOSE -> coordinator.submitMaterialLayers(requireNotNull(fee.taskId), fee.inputs.single())
                    MaterialFeeAction.ADD_ACCESSORY -> coordinator.submitAddAccessory(itemId, requireNotNull(fee.expectedMaterialId), requireNotNull(fee.base), fee.inputs[0], fee.inputs[1], fee.instructions, fee.provider)
                }
                state.update { it.copy(submittedTaskId = id) }
                invalidateFee()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                state.update { it.copy(error = "提交未完成，请重新核对资料与费用；不会自动重试") }
            } finally { state.update { it.copy(busy = false) } }
        }
    }

    fun saveMaterial(expectedCurrentId: String?) {
        val current = state.value
        if (current.busy || !current.pending || current.front || current.checks != setOf(0,1,2)) return
        mutate {
            coordinator.confirmMaterial(requireNotNull(current.task).id, requireNotNull(current.selectedPath), expectedCurrentId, checked = true)
            state.update { it.copy(saved = true) }
        }
    }
    fun removeMaterial(expectedId: String) = mutate { coordinator.removeMaterial(itemId, expectedId) }
    fun discardTask() { val id = state.value.task?.id ?: return; mutate { coordinator.discard(id) } }
    fun consumeSubmission() { state.update { it.copy(submittedTaskId = null) } }
    fun dismissFee() { if (!state.value.busy) invalidateFee() }
    private fun mutate(block: suspend () -> Unit) {
        if (state.value.busy) return
        state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { block() }
            catch (failure: Exception) { if (failure is CancellationException) throw failure; state.update { it.copy(error = "操作未完成，原资料仍保留；请核对当前素材与图片后重试") } }
            finally { state.update { it.copy(busy = false) } }
        }
    }
    private fun invalidateFee() {
        generation++
        val images = state.value.fee?.inputs.orEmpty().map { it.imagePath }
        val oldReleases = releases; releases = emptyList()
        state.update { it.copy(fee = null) }
        releaseMaterialInputs(oldReleases, images, cleanup)
    }
    private fun releaseCrop() {
        val old = cropPath; val release = cropRelease; cropRelease = null; cropPath = null
        handle["materialCrop"] = null
        releaseMaterialInputs(listOfNotNull(release), listOfNotNull(old), cleanup)
    }
    override fun onCleared() { invalidateFee(); releaseCrop(); super.onCleared() }
}

private val materialCleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private fun releaseMaterialInputs(releases: List<suspend () -> Unit>, paths: List<String>, cleanup: (List<String>) -> Unit) {
    materialCleanupScope.launch { try { releases.forEach { it() } } finally { cleanup(paths) } }
}
