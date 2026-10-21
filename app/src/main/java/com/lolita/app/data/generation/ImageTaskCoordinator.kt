package com.lolita.app.data.generation

import androidx.room.withTransaction
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.ImageTask
import com.lolita.app.data.local.entity.TryOnResult
import com.lolita.app.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.io.File

/** Owns accepted mutations independently of a screen's lifetime. Never owns network execution. */
class ImageTaskCoordinator(
    private val db: LolitaDatabase,
    private val applicationScope: CoroutineScope,
    private val startService: (String) -> Unit,
    private val saveImage: suspend (ByteArray) -> String,
    private val deleteImage: suspend (String) -> Unit,
    private val validateImage: suspend (String) -> Boolean,
    private val validateSourceImage: suspend (String) -> Boolean = { File(it).let { f -> f.isFile && f.canRead() && f.length() > 0 } },
    private val snapshotBuiltInImage: suspend (String) -> String = { error("固定模板快照尚未接入") },
    private val appendImageProvenance: suspend (Long, String, String, String?) -> Unit = { _, _, _, _ -> },
    private val validateProductImage: suspend (String) -> Boolean = validateSourceImage,
    private val snapshotMaterialInput: suspend (String, Boolean) -> String = { _, _ -> error("素材输入准备尚未接入") },
    private val validateMaterialImage: suspend (String) -> Boolean = { false },
    private val pinMaterialInputs: (List<String>) -> (suspend () -> Unit) = { { } }
) {
    private val dao = db.imageTaskDao()
    private val admission = Mutex()
    private val state = Mutex()
    // This gate never suspends. Android can close a session immediately, without Room I/O
    // on its main thread; durable cleanup then runs in the application scope.
    private val sessions = Any()
    private val pending = mutableSetOf<String>()
    private val owners = mutableMapOf<String, ServiceSession>()
    class ServiceSession internal constructor() {
        internal val taskIds = mutableSetOf<String>()
        internal var stopped = false
        internal var cleanup: Job? = null
    }
    private var paused = false
    private val cancelling = mutableSetOf<String>()
    private val interrupting = mutableSetOf<String>()
    private var current: Pair<String, Job>? = null
    private val startup = applicationScope.async {
        val sources = dao.getAll().filter { it.status.retainsSourceImages }.flatMap { it.sourceImagePaths }
        dao.interruptUnfinished(System.currentTimeMillis(), "应用已重启，任务已中断；如需继续，请核对后重新发起")
        cleanup(sources)
    }

    suspend fun awaitStartup() = startup.await()

    /** onCreate captures existing pending starts, including foreground-promotion failure. */
    fun openServiceSession(): ServiceSession = synchronized(sessions) {
        ServiceSession().also { session -> pending.toList().forEach { assignPending(session, it) } }
    }

    /** Each later start can admit only its own durable submission ID. */
    fun admitServiceStart(session: ServiceSession, taskId: String?): Boolean = synchronized(sessions) {
        if (session.stopped) return@synchronized false
        taskId?.let { assignPending(session, it) }
        true
    }

    private fun assignPending(session: ServiceSession, id: String) {
        if (pending.remove(id)) { owners[id] = session; session.taskIds += id }
    }

    /** A stale failed onStartCommand must not sweep unrelated, later pending starts. */
    fun rejectServiceStart(taskId: String?): Job {
        val rejected = synchronized(sessions) {
            ServiceSession().also { session -> taskId?.let { assignPending(session, it) } }
        }
        return serviceStartFailed(rejected)
    }

    /** Close admission now; cancellation/join and source release survive Service.onDestroy. */
    fun serviceStopped(session: ServiceSession, reason: String = "后台服务已停止；请求可能仍会计费，请核对后重新发起"): Job = synchronized(sessions) {
        session.cleanup?.let { return@synchronized it }
        session.stopped = true
        val ids = session.taskIds.toList()
        applicationScope.launch {
            awaitStartup()
            val job = state.withLock { current?.takeIf { it.first in ids }?.second }
            job?.cancelAndJoin()
            // The reader has left before any source reference is removed. Do not hold
            // admission while joining: newer user submissions belong to another start.
            ids.forEach { finish(it, ImageTaskStatus.INTERRUPTED, reason) }
        }.also { session.cleanup = it }
    }

    private fun sessionStopped(session: ServiceSession) = synchronized(sessions) { session.stopped }
    private fun sessionIds(session: ServiceSession) = synchronized(sessions) { session.taskIds.toList() }

    val executionSummary get() = dao.observeExecutionSummary()

    private suspend fun <T> accepted(block: suspend () -> T): T = applicationScope.async {
        awaitStartup()
        admission.withLock { block() }
    }.await()

    suspend fun submitTryOn(selected: List<TryOnSelection>, provider: ImageGenerationProvider): String =
        submitTryOn(selected, provider, null)

    private suspend fun <T> acceptedMaterial(paths: List<String>, block: suspend () -> T): T {
        val release = pinMaterialInputs(paths)
        return applicationScope.async {
            try { awaitStartup(); admission.withLock { block() } }
            finally { withContext(NonCancellable) { release() } }
        }.await()
    }

    suspend fun prepareMaterialInput(path: String, preserveAlpha: Boolean): PreparedMaterialInput {
        require(validateProductImage(path)) { "图片已丢失或无法读取" }
        val sourceHash = imageSha256(path)
        val copy = snapshotMaterialInput(path, preserveAlpha)
        return try {
            require(validateSourceImage(copy) && imageSha256(path) == sourceHash) { "准备期间原图片已改变" }
            PreparedMaterialInput(copy, imageSha256(copy), path, sourceHash)
        } catch (failure: Throwable) {
            withContext(NonCancellable) { cleanup(listOf(copy)) }
            throw failure
        }
    }

    suspend fun submitMaterialBatch(batch: MaterialBatchIdentity, operationId: String, rows: List<BatchMaterialInput>,
        provider: ImageGenerationProvider): List<String> = acceptedMaterial(rows.flatMap { listOf(it.input.imagePath, it.input.sourcePath, it.originalPath) }) {
        validateBatchRows(rows.map { it.itemId }, rows.map { it.position })
        val jobs = rows.map { row ->
            val (item, request) = materialRequest(row.itemId, row.input, row.originalPath, row.instructions, provider, row.expectedMaterialId)
            batchTask(batch, operationId, row.position, ImageTaskKind.SINGLE_ITEM_MATERIAL, provider, "${item.name} · 整理主视图", request, listOf(row.input.imagePath))
        }
        enqueueBatch(jobs)
    }

    suspend fun submitLayerBatch(batch: MaterialBatchIdentity, operationId: String, rows: List<BatchLayerInput>): List<String> =
        acceptedMaterial(rows.flatMap { listOf(it.input.imagePath, it.input.sourcePath) }) {
            val parents = mutableListOf<ImageTask>()
            val jobs = rows.map { row ->
                val (parent, request) = layerRequest(row.taskId, row.input)
                require(parent.batchId == batch.id && parent.batchName == batch.name) { "任务不属于当前批次" }
                parents += parent
                batchTask(batch, operationId, requireNotNull(parent.batchPosition), ImageTaskKind.SINGLE_ITEM_MATERIAL, parent.provider,
                    "${request.garments.single().name} · 分离单品图层", request, listOf(row.input.imagePath))
            }
            validateBatchRows(jobs.map { ImageTaskRequest.decode(it.requestJson).garments.single().itemId }, jobs.map { requireNotNull(it.batchPosition) })
            enqueueBatch(jobs, parents)
        }

    private fun validateBatchRows(items: List<Long>, positions: List<Int>) {
        require(items.size in 1..MaterialBatchIdentity.MAX_ITEMS && items.distinct().size == items.size && items.all { it > 0 } &&
            positions.size == items.size && positions.distinct().size == positions.size && positions.all { it in 0 until MaterialBatchIdentity.MAX_ITEMS }) { "每批请选择1至30件不同物品" }
    }

    private fun batchTask(batch: MaterialBatchIdentity, operationId: String, position: Int, kind: ImageTaskKind,
        provider: ImageGenerationProvider, title: String, request: ImageTaskRequest, paths: List<String>) =
        ImageTask(batch.taskId(operationId, request.garments.single().itemId), kind, provider, title, request.encode(), paths,
            keepSourceImages = true, batchId = batch.id, batchName = batch.name, batchPosition = position)

    /** Validate the entire admission before its single transaction; start no service for a partial batch. */
    private suspend fun enqueueBatch(jobs: List<ImageTask>, parents: List<ImageTask> = emptyList()): List<String> {
        require(jobs.isNotEmpty())
        val existing = jobs.map { dao.get(it.id) }
        if (existing.any { it != null }) {
            require(existing.zip(jobs).all { (old, job) -> old != null && old.kind == job.kind && old.provider == job.provider &&
                old.requestJson == job.requestJson && old.sourceImagePaths == job.sourceImagePaths && old.batchId == job.batchId && old.batchPosition == job.batchPosition }) { "这次批量提交已发生变化，请重新核对" }
            return jobs.map { it.id }
        }
        val previous = dao.getAll().filter { it.batchId == jobs.first().batchId }
        require(previous.all { it.batchName == jobs.first().batchName }) { "批次名称已改变" }
        jobs.forEach { job ->
            job.validate()
            val request = ImageTaskRequest.decode(job.requestJson)
            job.sourceImagePaths.forEach { require(validateSourceImage(it)) { "实际输入已丢失" } }
            request.productOriginalImagePath?.let { require(validateProductImage(it)) { "原参考图已丢失" } }
            request.validateInputImages(job.sourceImagePaths)
            require(previous.filter { it.batchPosition == job.batchPosition }.all {
                ImageTaskRequest.decode(it.requestJson).garments.single().itemId == request.garments.single().itemId
            }) { "批次位置不能改绑到其他物品" }
            val latest = previous.filter { it.batchPosition == job.batchPosition &&
                ImageTaskRequest.decode(it.requestJson).materialSpec?.stage == request.materialSpec?.stage }
                .maxWithOrNull(compareBy<ImageTask> { it.createdAt }.thenBy { it.id })
            if (latest != null) {
                require(latest.status in setOf(ImageTaskStatus.FAILED, ImageTaskStatus.INTERRUPTED, ImageTaskStatus.CANCELLED)) { "本项已有新的任务或结果，请刷新批次后核对" }
                if (request.materialSpec?.stage == MaterialStage.EXTRACT_LAYER)
                    require(request.materialSpec.parentTaskId == latest.id) { "只能重新处理当前失败项" }
            }
        }
        state.withLock { db.withTransaction {
            check(!paused)
            jobs.forEach { job ->
                val request = ImageTaskRequest.decode(job.requestJson)
                val item = requireNotNull(db.itemDao().getItemById(request.garments.single().itemId)) { "批次中有物品已删除" }
                require(item.status.supportsVirtualTryOn && db.categoryDao().getCategoryById(item.categoryId)?.categorySemantic == request.garments.single().semantic &&
                    (request.version < 6 || item.composition == request.garments.single().composition)) { "批次中有物品类型、组合或状态已改变" }
                if (request.materialSpec?.stage == MaterialStage.FRONT_VIEW) {
                    require(request.productOriginalImagePath in item.productReferenceImages &&
                        db.itemTryOnMaterialDao().getForItem(item.id)?.id == request.materialSpec.expectedMaterialId) { "原图或当前素材已改变" }
                }
            }
            jobs.forEach { dao.insert(it) }
            parents.forEach { parent ->
                val current = requireNotNull(dao.get(parent.id))
                require(current == parent) { "主视图状态已改变" }
                if (current.status == ImageTaskStatus.AWAITING_CONFIRMATION)
                    dao.update(current.copy(status = ImageTaskStatus.COMPLETED, updatedAt = System.currentTimeMillis()))
            }
        } }
        synchronized(sessions) { pending.addAll(jobs.map { it.id }) }
        jobs.forEach { startAcceptedTask(it) }
        return jobs.map { it.id }
    }

    suspend fun cancelMaterialBatch(batchId: String, taskIds: List<String>) {
        val current = dao.getAll().filter { it.batchId == batchId && it.id in taskIds && it.status.retainsSourceImages }
        current.forEach { cancel(it.id) }
    }

    private suspend fun validatePrepared(input: PreparedMaterialInput, source: String) {
        require(input.sourcePath == source && validateProductImage(source) && imageSha256(source) == input.sourceSha256 &&
            validateSourceImage(input.imagePath) && imageSha256(input.imagePath) == input.sha256) { "实际输入已改变，请重新预览并确认费用" }
    }

    suspend fun submitMaterial(itemId: Long, input: PreparedMaterialInput, originalPath: String,
        instructions: String, provider: ImageGenerationProvider, expectedMaterialId: String?): String = acceptedMaterial(listOf(input.imagePath, input.sourcePath, originalPath)) {
        val (item, request) = materialRequest(itemId, input, originalPath, instructions, provider, expectedMaterialId)
        enqueue(ImageTaskKind.SINGLE_ITEM_MATERIAL, provider, "${item.name} · 整理主视图", request, listOf(input.imagePath))
    }

    private suspend fun materialRequest(itemId: Long, input: PreparedMaterialInput, originalPath: String,
        instructions: String, provider: ImageGenerationProvider, expectedMaterialId: String?): Pair<com.lolita.app.data.local.entity.Item, ImageTaskRequest> {
        require(provider == ImageGenerationProvider.SEEDREAM) { "首版素材整理请使用已验证的即梦服务" }
        val item = requireNotNull(db.itemDao().getItemById(itemId)) { "物品已删除" }
        require(item.status.supportsVirtualTryOn && originalPath in item.productReferenceImages) { "原参考图片或物品状态已改变" }
        val semantic = db.categoryDao().getCategoryById(item.categoryId)?.categorySemantic
        require(semantic?.supportsMaterialPreparation == true) { "请先在服饰编辑中核对物品类型" }
        require(instructions.length <= 200)
        validatePrepared(input, input.sourcePath)
        require(db.itemTryOnMaterialDao().getForItem(itemId)?.id == expectedMaterialId) { "当前素材已改变，请重新核对" }
        val stage = if (provider == ImageGenerationProvider.SEEDREAM) MaterialStage.FRONT_VIEW else MaterialStage.TRANSPARENT
        val spec = MaterialTaskSpec(stage, expectedMaterialId, input.imagePath, originalInputSha256 = input.sha256)
        val legacy = semantic == CategorySemantic.WAIST_ACCESSORY
        val prompt = if (legacy) TryOnMaterialPrompts.front(instructions) else TryOnMaterialPrompts.front(requireNotNull(semantic), item.composition, instructions)
        val request = ImageTaskRequest(listOf(ImageTaskGarment(item.id, item.name, requireNotNull(semantic).defaultWearRole, semantic, item.composition)),
            instructions, version = if (legacy) 5 else 6, promptVersion = if (legacy) 1 else MaterialPreparation.PROMPT_VERSION, promptSnapshot = prompt, serviceModel = provider.model,
            inputSha256s = listOf(input.sha256), productOriginalImagePath = originalPath, materialSpec = spec)
        return item to request
    }

    suspend fun submitMaterialLayers(parentTaskId: String, input: PreparedMaterialInput): String = acceptedMaterial(listOf(input.imagePath, input.sourcePath)) {
        val (parent, request) = layerRequest(parentTaskId, input)
        val id = enqueue(ImageTaskKind.SINGLE_ITEM_MATERIAL, parent.provider, "${request.garments.single().name} · 分离单品图层", request, listOf(input.imagePath))
        state.withLock { dao.get(parent.id)?.takeIf { it.status == ImageTaskStatus.AWAITING_CONFIRMATION }?.let {
            dao.update(it.copy(status = ImageTaskStatus.COMPLETED, updatedAt = System.currentTimeMillis()))
        } }
        id
    }

    private suspend fun layerRequest(parentTaskId: String, input: PreparedMaterialInput): Pair<ImageTask, ImageTaskRequest> {
        val parent = requireNotNull(dao.get(parentTaskId)) { "主视图任务已删除" }
        val old = ImageTaskRequest.decode(parent.requestJson)
        val firstDecomposition = old.materialSpec?.stage == MaterialStage.FRONT_VIEW && parent.status == ImageTaskStatus.AWAITING_CONFIRMATION
        val retryDecomposition = old.materialSpec?.stage == MaterialStage.EXTRACT_LAYER &&
            parent.status in setOf(ImageTaskStatus.FAILED, ImageTaskStatus.INTERRUPTED, ImageTaskStatus.CANCELLED) && !parent.sourceResourcesMissing
        require(parent.kind == ImageTaskKind.SINGLE_ITEM_MATERIAL && (firstDecomposition || retryDecomposition) &&
            parent.provider == ImageGenerationProvider.SEEDREAM) { "请重新查看待分层的主视图" }
        val source = if (firstDecomposition) requireNotNull(parent.resultImagePath) else parent.sourceImagePaths.single()
        require(validateImage(source)) { "主视图已丢失" }
        validatePrepared(input, source)
        val item = requireNotNull(db.itemDao().getItemById(old.garments.single().itemId)) { "原物品已删除" }
        require(item.status.supportsVirtualTryOn && db.categoryDao().getCategoryById(item.categoryId)?.categorySemantic == old.garments.single().semantic &&
            (old.version < 6 || item.composition == old.garments.single().composition)) { "原物品类型、组合或状态已改变" }
        val garment = old.garments.single()
        val request = old.copy(promptSnapshot = if (old.version == 5) TryOnMaterialPrompts.extract(old.instructions)
            else TryOnMaterialPrompts.extract(requireNotNull(garment.semantic), requireNotNull(garment.composition), old.instructions), inputSha256s = listOf(input.sha256),
            materialSpec = requireNotNull(old.materialSpec).copy(stage = MaterialStage.EXTRACT_LAYER, parentTaskId = parent.id))
        return parent to request
    }

    suspend fun submitAddAccessory(itemId: Long, materialId: String, base: AccessoryBaseSnapshot,
        baseInput: PreparedMaterialInput, itemInput: PreparedMaterialInput, instructions: String,
        provider: ImageGenerationProvider): String = acceptedMaterial(listOf(baseInput.imagePath, baseInput.sourcePath, itemInput.imagePath, itemInput.sourcePath)) {
        val request = accessoryRequest(itemId, materialId, base, baseInput, itemInput, instructions, provider)
        enqueue(ImageTaskKind.TRY_ON, provider, "${base.name.take(80)} · 加腰饰", request, listOf(baseInput.imagePath, itemInput.imagePath))
    }

    private suspend fun accessoryRequest(itemId: Long, materialId: String, base: AccessoryBaseSnapshot,
        baseInput: PreparedMaterialInput, itemInput: PreparedMaterialInput, instructions: String,
        provider: ImageGenerationProvider): ImageTaskRequest {
        base.validate()
        require(instructions.length <= 200)
        val item = requireNotNull(db.itemDao().getItemById(itemId)) { "所选物品已删除" }
        require(item.status.supportsVirtualTryOn && db.categoryDao().getCategoryById(item.categoryId)?.categorySemantic == CategorySemantic.WAIST_ACCESSORY)
        val material = requireNotNull(db.itemTryOnMaterialDao().getForItem(itemId)) { "请先确认试衣素材" }
        require(material.id == materialId && validateMaterialImage(material.imagePath) && imageSha256(material.imagePath) == material.resultSha256) { "当前素材已改变或丢失" }
        val basePath = when (base.type) {
            AccessoryBaseType.TRY_ON_RESULT -> requireNotNull(db.tryOnDao().getAll().firstOrNull { it.id == base.id && it.name == base.name }).imageUrl
            AccessoryBaseType.PERSONAL_MODEL -> requireNotNull(db.personalModelDao().get(base.id)?.takeIf { it.name == base.name }).imageUrl
        }
        validatePrepared(baseInput, basePath)
        validatePrepared(itemInput, material.imagePath)
        val request = ImageTaskRequest(listOf(ImageTaskGarment(item.id, item.name, TryOnRole.WAIST, CategorySemantic.WAIST_ACCESSORY, item.composition, materialId)),
            instructions, version = 5, promptVersion = 1, promptSnapshot = TryOnMaterialPrompts.add(item.name, instructions), serviceModel = provider.model,
            inputSha256s = listOf(baseInput.sha256, itemInput.sha256), accessoryBase = base)
        return request
    }

    suspend fun confirmMaterial(id: String, selectedPath: String, expectedCurrentId: String?, checked: Boolean): String = accepted {
        require(checked) { "请完成三项单品核对" }
        val released = mutableListOf<String>()
        val materialId = state.withLock { db.withTransaction { confirmMaterialLocked(id, selectedPath, expectedCurrentId, released) } }
        cleanup(released)
        materialId
    }

    suspend fun confirmMaterialBatch(batchId: String, rows: List<BatchMaterialConfirmation>, checked: Boolean): List<String> = accepted {
        require(checked && rows.size in 1..MaterialBatchIdentity.MAX_ITEMS && rows.map { it.taskId }.distinct().size == rows.size) { "请核对本次选中的单品" }
        val released = mutableListOf<String>()
        val materialIds = state.withLock { db.withTransaction {
            val jobs = rows.map { requireNotNull(dao.get(it.taskId)) }
            require(jobs.all { it.batchId == batchId } && jobs.map { it.batchPosition }.distinct().size == jobs.size)
            rows.map { confirmMaterialLocked(it.taskId, it.imagePath, it.expectedMaterialId, released) }
        } }
        cleanup(released)
        materialIds
    }

    private suspend fun confirmMaterialLocked(id: String, selectedPath: String, expectedCurrentId: String?, released: MutableList<String>): String {
            val task = requireNotNull(dao.get(id)) { "任务已删除" }
            require(task.kind == ImageTaskKind.SINGLE_ITEM_MATERIAL)
            if (task.status == ImageTaskStatus.COMPLETED) return requireNotNull(task.materialId)
            require(task.status == ImageTaskStatus.AWAITING_CONFIRMATION)
            val request = ImageTaskRequest.decode(task.requestJson)
            require(request.materialSpec?.stage != MaterialStage.FRONT_VIEW) { "请先分离主视图中的单品" }
            val itemId = request.garments.single().itemId
            val item = requireNotNull(db.itemDao().getItemById(itemId)) { "原物品已删除，无法保存素材" }
            require(item.status.supportsVirtualTryOn && db.categoryDao().getCategoryById(item.categoryId)?.categorySemantic == request.garments.single().semantic &&
                (request.version < 6 || item.composition == request.garments.single().composition)) { "原物品类型、组合或状态已改变，请重新核对" }
            val candidates = MaterialLayers.decode(task.layersJson)
            require(selectedPath == task.resultImagePath || candidates.any { it.imagePath == selectedPath }) { "所选图层不属于本任务" }
            require(validateImage(selectedPath) && validateMaterialImage(selectedPath)) { "这次未得到有效透明单品，请核对结果" }
            candidates.firstOrNull { it.imagePath == selectedPath }?.let { require(imageSha256(selectedPath) == it.sha256) }
            val previous = db.itemTryOnMaterialDao().getForItem(itemId)
            require(previous?.id == expectedCurrentId) { "当前素材已更新，请查看后明确替换" }
            previous?.let { released += it.protectedImagePaths() }
            val original = request.materialSpec?.originalInputPath ?: task.sourceImagePaths.single()
            require(validateSourceImage(original)) { "原输入已丢失，请重新核对" }
            val row = com.lolita.app.data.local.entity.ItemTryOnMaterial(UUID.randomUUID().toString(), itemId, selectedPath,
                original, task.sourceImagePaths.single(), imageSha256(selectedPath), task.id, request.historySnapshot())
            row.validate()
            db.itemTryOnMaterialDao().deleteForItem(itemId)
            db.itemTryOnMaterialDao().insert(row)
            dao.update(task.copy(status = ImageTaskStatus.COMPLETED, materialId = row.id, resultImagePath = selectedPath,
                layersJson = MaterialLayers.encode(candidates.filter { it.imagePath == selectedPath }),
                updatedAt = System.currentTimeMillis(), error = null))
            released += candidates.map { it.imagePath }
            return row.id
    }

    /** Shares the confirmed bytes with the album; all owners participate in normal cleanup/backup. */
    suspend fun setMaterialCover(itemId: Long, expectedId: String) = accepted {
        state.withLock { db.withTransaction {
            val item = requireNotNull(db.itemDao().getItemById(itemId)) { "原物品已删除" }
            val material = requireNotNull(db.itemTryOnMaterialDao().getForItem(itemId)) { "当前展示图已移除" }
            require(material.id == expectedId) { "当前展示图已更新，请重新查看" }
            val semantic = db.categoryDao().getCategoryById(item.categoryId)?.categorySemantic
            require(material.matchesSource(semantic, item.composition)) { "物品类型或组合已改变，请重新核对" }
            require(validateImage(material.imagePath) && validateMaterialImage(material.imagePath) &&
                imageSha256(material.imagePath) == material.resultSha256) { "展示图已改变或无法读取" }
            val images = listOf(material.imagePath) + item.imageUrls.filterNot { it == material.imagePath }
            if (images != item.imageUrls) check(db.itemDao().updateImages(itemId, images, System.currentTimeMillis()) == 1)
            val provenance = db.imageProvenanceDao()
            if (provenance.get(itemId, material.imagePath)?.sourceType != com.lolita.app.data.local.entity.ImageSourceType.AI_GENERATED) {
                provenance.upsert(com.lolita.app.data.local.entity.ImageProvenance(material.imagePath, itemId,
                    com.lolita.app.data.local.entity.ImageSourceType.AI_GENERATED, "SINGLE_ITEM_MATERIAL", material.sourceTaskId))
            }
        } }
    }

    suspend fun removeMaterial(itemId: Long, expectedId: String) = accepted {
        val paths = state.withLock { db.withTransaction {
            val row = db.itemTryOnMaterialDao().getForItem(itemId) ?: return@withTransaction emptyList<String>()
            require(row.id == expectedId) { "当前素材已改变，请重新查看" }
            db.itemTryOnMaterialDao().deleteForItem(itemId)
            row.protectedImagePaths().toList()
        } }
        cleanup(paths)
    }

    suspend fun submitTryOn(selected: List<TryOnSelection>, provider: ImageGenerationProvider, instructions: String): String =
        submitTryOnSnapshot(selected, provider, null, null, instructions, freezeWearing = true)

    suspend fun submitTryOn(selected: List<TryOnSelection>, provider: ImageGenerationProvider, personalModelId: Long?): String {
        return submitTryOnSnapshot(selected, provider, personalModelId, null, "")
    }

    suspend fun submitTryOn(selected: List<TryOnSelection>, provider: ImageGenerationProvider,
        personalModelId: Long, expectedModelImagePath: String, instructions: String = ""): String {
        require(expectedModelImagePath.isNotBlank()) { "请重新选择试衣模特" }
        return submitTryOnSnapshot(selected, provider, personalModelId, expectedModelImagePath, instructions, freezeWearing = true)
    }

    private suspend fun submitTryOnSnapshot(selected: List<TryOnSelection>, provider: ImageGenerationProvider,
        personalModelId: Long?, expectedModelImagePath: String?, instructions: String, freezeWearing: Boolean = false): String {
        val snapshot = selected.toList()
        val request = TryOnRequest(snapshot, instructions = instructions.trim())
        return accepted {
            // Legacy callers without semantic metadata retain their existing request protocol.
            // The current editor always explicitly supplies instructions, including an empty string.
            if (freezeWearing || snapshot.any { it.semantic != null } || request.instructions.isNotEmpty()) {
                snapshot.forEach { row ->
                    val item = requireNotNull(db.itemDao().getItemById(row.itemId)) { "所选物品已删除" }
                    val material = row.materialId?.let { db.itemTryOnMaterialDao().getForItem(row.itemId) }
                    require(item.status.supportsVirtualTryOn && if (row.materialId == null) row.imagePath in item.imageUrls
                        else material?.id == row.materialId && material.imagePath == row.imagePath &&
                            validateMaterialImage(row.imagePath) && imageSha256(row.imagePath) == material.resultSha256 &&
                            material.matchesSource(db.categoryDao().getCategoryById(item.categoryId)?.categorySemantic, item.composition)) { "所选物品或图片已改变" }
                    require(row.semantic == null || db.categoryDao().getCategoryById(item.categoryId)?.categorySemantic == row.semantic) { "分类语义已改变，请重新核对" }
                }
                val person = personalModelId?.let { requireNotNull(db.personalModelDao().get(it)) { "所选人物已删除" } }
                require(person == null || expectedModelImagePath == null || person.imageUrl == expectedModelImagePath) { "人物底图已改变，请重新确认" }
                suspend fun enqueueSnapshot(modelPath: String): String {
                    val sources = listOf(modelPath) + snapshot.map { it.imagePath }
                    val usesMaterials = snapshot.any { it.materialId != null }
                    val staged = mutableListOf<String>()
                    try {
                    val inputs = if (usesMaterials) sources.map { snapshotMaterialInput(it, provider == ImageGenerationProvider.OPENAI).also(staged::add) } else sources
                    val metadata = ImageTaskRequest(snapshot.map { ImageTaskGarment(it.itemId, it.name, it.role, it.semantic, it.composition, it.materialId) },
                        instructions = request.instructions, version = if (usesMaterials) 5 else 4, tryOnModel = person?.let { TryOnModelSnapshot(it.id, it.name) }, promptVersion = TryOnRequest.PROMPT_VERSION,
                        promptSnapshot = request.prompt(personalModel = person != null, provider = provider), serviceModel = provider.model,
                        inputSha256s = inputs.map(::imageSha256))
                    return enqueue(ImageTaskKind.TRY_ON, provider, "虚拟试衣", metadata, inputs)
                    } finally { if (usesMaterials) withContext(NonCancellable) { cleanup(staged + sources) } }
                }
                return@accepted if (person != null) enqueueSnapshot(person.imageUrl)
                else withStagedBuiltInInput(com.lolita.app.data.tryon.TryOnImages.MODEL_ASSET, block = ::enqueueSnapshot)
            }
            enqueue(ImageTaskKind.TRY_ON, provider, "虚拟试衣",
                ImageTaskRequest(request.selections.map { ImageTaskGarment(it.itemId, it.name, it.role) }),
                request.selections.map { it.imagePath }, personalModelId, expectedModelImagePath)
        }
    }

    suspend fun submitPersonalModel(request: PersonalModelRequest, paths: List<String>, provider: ImageGenerationProvider): String {
        val snapshot = PersonalModelRequest.decode(request.encode())
        val sources = paths.toList()
        snapshot.validatePaths(sources)
        return accepted {
            enqueue(ImageTaskKind.PERSONAL_MODEL, provider, "${snapshot.name} · 专属模特",
                ImageTaskRequest(emptyList(), version = 2, personalModel = snapshot), sources)
        }
    }

    suspend fun submitMannequin(request: MannequinRequest, provider: ImageGenerationProvider): String {
        var persisted=false
        return try { accepted {
        val currentItem=db.itemDao().getItemById(request.itemId)
        val item = mannequinAdmission(MannequinAdmissionFailureKind.REFERENCE) {
            requireNotNull(currentItem) { "物品已删除，请重新选择" }
        }
        val category=db.categoryDao().getCategoryById(item.categoryId)
        val subject = mannequinAdmission(MannequinAdmissionFailureKind.SUBJECT) {
            requireNotNull(mannequinSubject(category)) { "此分类暂不支持展示图" }.also { subject ->
                require(request.subject?.let { it == subject } ?: (subject.presentation == MannequinPresentation.DRESS_FORM)) {
                    "物品分类已改变，请重新核对展示方式"
                }
            }
        }
        if (subject.semantic != null) {
            suspend fun enqueueSnapshot(template: String?): String {
                val sources = listOfNotNull(template) + request.imagePath
                val metadata = ImageTaskRequest(listOf(ImageTaskGarment(item.id, item.name, TryOnRole.MAIN, subject.semantic, item.composition)),
                    request.instructions, version = 4, mannequinSubject = subject, promptVersion = 1,
                    promptSnapshot = request.prompt(), serviceModel = provider.model, inputSha256s = sources.mapIndexed { index,path ->
                        mannequinAdmission(if(template!=null && index==0)MannequinAdmissionFailureKind.TEMPLATE else MannequinAdmissionFailureKind.SOURCE) {imageSha256(path)}
                    }, wigHeadTemplate = request.wigHeadTemplate,
                    productOriginalImagePath = request.originalImagePath)
                return enqueue(ImageTaskKind.MANNEQUIN, provider, "${item.name} · ${subject.presentation.label}", metadata, sources,
                    onPersisted={persisted=true})
            }
            return@accepted when (subject.presentation) {
                MannequinPresentation.DRESS_FORM -> withStagedBuiltInInput(com.lolita.app.data.tryon.TryOnImages.MANNEQUIN_ASSET,
                    MannequinAdmissionFailureKind.TEMPLATE) { enqueueSnapshot(it) }
                MannequinPresentation.WIG_HEAD -> {
                    mannequinAdmission(MannequinAdmissionFailureKind.REFERENCE) {require(request.referenceViewConfirmed) { "请先核对假发正面视角与发梢完整性" }}
                    val head = mannequinAdmission(MannequinAdmissionFailureKind.TEMPLATE) {
                        requireNotNull(request.wigHeadTemplate) { "请选择本次头模" }.also { it.validateImage() }
                    }
                    enqueueSnapshot(head.imagePath)
                }
                MannequinPresentation.ACCESSORY -> enqueueSnapshot(null)
            }
        }
        enqueue(ImageTaskKind.MANNEQUIN, provider, "${item.name} · ${subject.presentation.label}",
            ImageTaskRequest(listOf(ImageTaskGarment(item.id, item.name, TryOnRole.MAIN)), request.instructions,
                version = 3, mannequinSubject = subject), listOf(request.imagePath),onPersisted={persisted=true})
        } } catch(failure:CancellationException) {throw failure}
        catch(failure:MannequinAdmissionFailure) {throw failure}
        catch(failure:Exception) {
            // A committed task may already have reached its service. Never label that as unsent.
            if(persisted)throw failure
            throw MannequinAdmissionFailure(MannequinAdmissionFailureKind.STORAGE)
        }
    }

    private suspend fun <T> mannequinAdmission(kind:MannequinAdmissionFailureKind,block:suspend ()->T):T = try {block()}
        catch(failure:CancellationException) {throw failure}
        catch(failure:MannequinAdmissionFailure) {throw failure}
        catch(_:Exception) {throw MannequinAdmissionFailure(kind)}

    /** Only copies created for this admission are staged here; existing user/head inputs are never swept. */
    private suspend fun <T> withStagedBuiltInInput(asset: String, failureKind:MannequinAdmissionFailureKind?=null, block: suspend (String) -> T): T {
        val path = if(failureKind==null)snapshotBuiltInImage(asset) else mannequinAdmission(failureKind) {snapshotBuiltInImage(asset)}
        return try {
            currentCoroutineContext().ensureActive()
            block(path)
        } catch (failure: Throwable) {
            // The callback still checks Room and live draft owners if insertion already committed.
            withContext(NonCancellable) { cleanup(listOf(path)) }
            throw failure
        }
    }

    private suspend fun enqueue(kind: ImageTaskKind, provider: ImageGenerationProvider, title: String,
        request: ImageTaskRequest, paths: List<String>, personalModelId: Long? = null, expectedModelImagePath: String? = null,
        onPersisted:()->Unit={}): String {
        suspend fun <T> admission(stage:MannequinAdmissionFailureKind,block:suspend ()->T):T =
            if(kind==ImageTaskKind.MANNEQUIN)mannequinAdmission(stage,block) else block()
        val task = admission(MannequinAdmissionFailureKind.STORAGE) { state.withLock {
            db.withTransaction {
                var metadata = request
                var sources = paths.toList()
                var referencedProductImages=emptySet<String>()
                if (personalModelId != null) {
                    val person = requireNotNull(db.personalModelDao().get(personalModelId)) { "所选模特已删除，请重新选择" }
                    require(expectedModelImagePath == null || person.imageUrl == expectedModelImagePath) { "模特图片已更换，请核对后重新选择" }
                    require(validateImage(person.imageUrl)) { "所选模特图片已丢失，请重新选择" }
                    metadata = request.copy(version = 2, tryOnModel = TryOnModelSnapshot(person.id, person.name))
                    sources = listOf(person.imageUrl) + sources
                }
                if (kind == ImageTaskKind.PERSONAL_MODEL) {
                    sources.forEach { require(validateSourceImage(it)) { "参考照片已丢失或无法完整读取，请重新选择" } }
                }
                if (kind == ImageTaskKind.MANNEQUIN) {
                    val currentItem=db.itemDao().getItemById(metadata.garments.single().itemId)
                    val item = admission(MannequinAdmissionFailureKind.REFERENCE) {
                        requireNotNull(currentItem) { "物品已删除，请重新选择" }.also {
                            require((metadata.version == 4 || sources.size == 1) && (metadata.productOriginalImagePath ?: sources.last()) in it.productReferenceImages) { "参考图片已改变，请重新选择" }
                        }
                    }
                    referencedProductImages=item.productReferenceImages.toSet()
                    val category=db.categoryDao().getCategoryById(item.categoryId)
                    admission(MannequinAdmissionFailureKind.SUBJECT) {
                        val currentSubject = mannequinSubject(category)
                        require(currentSubject != null && currentSubject == metadata.mannequinSubject) { "物品分类已改变，请重新核对展示方式" }
                    }
                }
                if (metadata.version >= 4) {
                    sources.forEachIndexed { index,path -> admission(if(metadata.mannequinSubject?.presentation?.inputCount==2 && index==0)
                        MannequinAdmissionFailureKind.TEMPLATE else MannequinAdmissionFailureKind.SOURCE) {
                        // A derived crop is not an existing Item image and keeps the strict UUID gate.
                        val existingProduct=kind==ImageTaskKind.MANNEQUIN && index==sources.lastIndex && path in referencedProductImages
                        val valid=if(existingProduct)validateProductImage(path) else validateSourceImage(path)
                        require(valid) { "任务实际输入不属于本机图片或已无法读取" }
                    } }
                    metadata.productOriginalImagePath?.let { path -> admission(MannequinAdmissionFailureKind.SOURCE) {
                        val valid=if(kind==ImageTaskKind.MANNEQUIN || kind==ImageTaskKind.SINGLE_ITEM_MATERIAL)validateProductImage(path) else validateSourceImage(path)
                        require(valid) { "裁切原图已丢失，请重新核对" }
                    } }
                    admission(MannequinAdmissionFailureKind.INPUT_CHANGED) {metadata.validateInputImages(sources)}
                }
                ImageTask(UUID.randomUUID().toString(), kind, provider, title, metadata.encode(), sources, keepSourceImages = metadata.version >= 4).also {
                    it.validate()
                    dao.insert(it)
                    synchronized(sessions) { pending += it.id }
                }
            }
        } }
        onPersisted()
        startAcceptedTask(task)
        return task.id
    }

    private suspend fun startAcceptedTask(task: ImageTask) {
        try { startService(task.id) } catch (_: Exception) {
            // An existing service may have claimed the new row before start rejection.
            val job = state.withLock {
                interrupting += task.id
                current?.takeIf { it.first == task.id }?.second
            }
            job?.cancelAndJoin()
            finish(task.id, ImageTaskStatus.INTERRUPTED, "后台执行未能启动，请回到应用后重新发起")
            state.withLock { interrupting -= task.id }
        }
    }

    suspend fun cancel(id: String) = accepted {
        val job = state.withLock {
            val task = dao.get(id) ?: return@withLock null
            if (!task.status.retainsSourceImages) return@withLock null
            // Keep the live status/source ownership until the reader has actually exited.
            cancelling += id
            current?.takeIf { it.first == id }?.second
        }
        job?.cancelAndJoin()
        finish(id, ImageTaskStatus.CANCELLED, "已取消本地等待；已发送的请求可能仍会计费")
        state.withLock { cancelling -= id }
    }

    suspend fun confirmMannequin(id: String) = accepted {
        state.withLock {
            db.withTransaction {
                val task = requireNotNull(dao.get(id)) { "任务已删除" }
                require(task.kind == ImageTaskKind.MANNEQUIN) { "此任务不是展示图" }
                if (task.status == ImageTaskStatus.COMPLETED) return@withTransaction
                require(task.status == ImageTaskStatus.AWAITING_CONFIRMATION) { "此任务没有待确认的图片" }
                val request = ImageTaskRequest.decode(task.requestJson)
                val item = requireNotNull(db.itemDao().getItemById(request.garments.single().itemId)) { "原服饰已删除，请放弃此预览" }
                val path = requireNotNull(task.resultImagePath)
                require(validateImage(path)) { "预览图片已丢失，请放弃后重新生成" }
                db.itemDao().updateImages(item.id, if (path in item.imageUrls) item.imageUrls else item.imageUrls + path, System.currentTimeMillis())
                appendImageProvenance(item.id, path, request.mannequinSubject?.presentation?.name ?: "DRESS_FORM", task.id)
                dao.update(task.copy(status = ImageTaskStatus.COMPLETED, updatedAt = System.currentTimeMillis(), error = null))
            }
        }
    }

    suspend fun confirmPersonalModel(id: String): Long = accepted {
        state.withLock {
            db.withTransaction {
                val task = requireNotNull(dao.get(id)) { "任务已删除" }
                require(task.kind == ImageTaskKind.PERSONAL_MODEL) { "这不是专属模特任务" }
                if (task.status == ImageTaskStatus.COMPLETED) return@withTransaction requireNotNull(task.personalModelId)
                require(task.status == ImageTaskStatus.AWAITING_CONFIRMATION) { "此任务没有待保存的模特" }
                val request = requireNotNull(ImageTaskRequest.decode(task.requestJson).personalModel)
                request.validatePaths(task.sourceImagePaths)
                val path = requireNotNull(task.resultImagePath)
                require(validateImage(path)) { "模特预览图片已丢失，请舍弃后重新生成" }
                task.sourceImagePaths.forEach { require(validateSourceImage(it)) { "原参考照片已丢失，请重新生成" } }
                val result = db.personalModelDao().insert(com.lolita.app.data.local.entity.PersonalModel(
                    name = request.name, requestJson = request.encode(), sourceImagePaths = task.sourceImagePaths,
                    imageUrl = path, provider = task.provider, model = task.provider.model))
                dao.update(task.copy(status = ImageTaskStatus.COMPLETED, personalModelId = result,
                    sourceImagePaths = emptyList(), updatedAt = System.currentTimeMillis(), error = null))
                result
            }
        }
    }

    suspend fun discard(id: String) = accepted {
        val paths = state.withLock {
            val task = dao.get(id) ?: return@withLock emptyList()
            if (task.status != ImageTaskStatus.AWAITING_CONFIRMATION) return@withLock emptyList()
            dao.update(task.copy(status = ImageTaskStatus.DISCARDED, resultImagePath = null, layersJson = "[]",
                sourceImagePaths = if (task.keepSourceImages) task.sourceImagePaths else emptyList(), updatedAt = System.currentTimeMillis()))
            (if (task.keepSourceImages) emptyList() else task.sourceImagePaths) + listOfNotNull(task.resultImagePath) + MaterialLayers.decode(task.layersJson).map { it.imagePath }
        }
        cleanup(paths)
    }

    suspend fun delete(id: String) = accepted {
        val paths = state.withLock {
            val task = dao.get(id) ?: return@withLock emptyList()
            require(!task.status.retainsSourceImages) { "请先取消正在执行的任务" }
            dao.delete(id)
            task.protectedImagePaths().toList() + task.sourceImagePaths + listOfNotNull(task.resultImagePath) + MaterialLayers.decode(task.layersJson).map { it.imagePath }
        }
        cleanup(paths)
    }

    /**
     * Join the accepted restore even if its screen disappears. The caller owns the backup
     * mutex and ZIP staging, so it must not unwind cleanup while this callback still runs.
     * Ordinary mutations keep their cancellable waits; only this resource barrier joins.
     */
    suspend fun <T> withRestorePaused(block: suspend () -> T): T = withContext(NonCancellable) {
        pauseExecution("恢复备份前已中断任务，请核对后重新发起", block)
    }

    fun serviceStartFailed(session: ServiceSession): Job =
        serviceStopped(session, "后台执行未能启动，请回到应用后重新发起")

    private suspend fun <T> pauseExecution(reason: String, block: suspend () -> T): T = accepted {
        val job = state.withLock { paused = true; current?.second }
        try {
            job?.cancelAndJoin()
            val paths = state.withLock {
                val rows = dao.getAll().filter { it.status.retainsSourceImages }
                dao.interruptUnfinished(System.currentTimeMillis(), reason)
                rows.flatMap { it.sourceImagePaths }
            }
            cleanup(paths)
            block()
        } finally { state.withLock { paused = false } }
    }

    internal suspend fun runNext(execute: suspend (ImageTask) -> Unit, onChanged: suspend (ImageTask?) -> Unit, session: ServiceSession = openServiceSession()): Boolean = coroutineScope {
        awaitStartup()
        val entry = state.withLock {
            if (paused || current != null || sessionStopped(session)) return@withLock null
            val queued = dao.oldestQueued(sessionIds(session)) ?: return@withLock null
            if (dao.claimQueued(queued.id, System.currentTimeMillis()) != 1) return@withLock null
            val task = requireNotNull(dao.get(queued.id))
            val child = launch(start = CoroutineStart.LAZY) {
                try {
                    if (sessionStopped(session)) throw CancellationException("后台服务已停止")
                    onChanged(task)
                    if (task.sourceResourcesMissing) {
                        finish(task.id, ImageTaskStatus.FAILED, ImageTask.SOURCE_RESOURCES_MISSING_MESSAGE)
                        return@launch
                    }
                    execute(task)
                } catch (failure: CancellationException) {
                    withContext(NonCancellable) { finish(task.id, ImageTaskStatus.INTERRUPTED, "执行已中断；服务可能仍会计费，请核对后重新发起") }
                    throw failure
                } catch (failure: Exception) {
                    // Only local typed categories may select durable text.
                    finish(task.id, ImageTaskStatus.FAILED, imageTaskFailureMessage(failure, dao.get(task.id)?.requestSent == true))
                } finally {
                    withContext(NonCancellable) {
                        releaseSources(task.id)
                        state.withLock { if (current?.first == task.id) current = null }
                        onChanged(dao.get(task.id))
                    }
                }
            }
            current = task.id to child
            task to child
        } ?: return@coroutineScope false
        try {
            entry.second.start()
            entry.second.join()
        } finally {
            // A lazy job cancelled before starting never reaches its finally block.
            withContext(NonCancellable) {
                entry.second.cancelAndJoin()
                finish(entry.first.id, ImageTaskStatus.INTERRUPTED, "执行已中断，请核对后重新发起")
                releaseSources(entry.first.id)
                state.withLock { if (current?.first == entry.first.id) current = null }
            }
        }
        true
    }

    suspend fun beforeRequest(id: String) = state.withLock {
        val task = requireActive(id)
        check(task.status == ImageTaskStatus.PREPARING)
        dao.update(task.copy(status = ImageTaskStatus.GENERATING, requestSent = true, updatedAt = System.currentTimeMillis()))
    }

    suspend fun publishTryOn(id: String, bytes: ByteArray, selected: List<TryOnSelection>, model: String): Long {
        var baseCopy: String? = null
        return try { publish(id, bytes) { task, path ->
            val request = ImageTaskRequest.decode(task.requestJson)
            val person = request.tryOnModel
            if (request.accessoryBase != null) baseCopy = saveImage(File(task.sourceImagePaths.first()).readBytes())
            val result = db.tryOnDao().insert(TryOnResult(name = task.title, imageUrl = path,
                itemIds = selected.map { it.itemId.toString() }, itemNames = selected.map { it.name }, model = model,
                personalModelId = person?.personalModelId ?: request.accessoryBase?.takeIf { it.type == AccessoryBaseType.PERSONAL_MODEL }?.id,
                personalModelName = person?.name ?: request.accessoryBase?.takeIf { it.type == AccessoryBaseType.PERSONAL_MODEL }?.name,
                requestSnapshotJson = request.takeIf { it.version >= 4 }?.historySnapshot(), baseSnapshotImagePath = baseCopy))
            dao.update(task.copy(status = ImageTaskStatus.COMPLETED, sourceImagePaths = if (task.keepSourceImages) task.sourceImagePaths else emptyList(), resultImagePath = path,
                tryOnResultId = result, updatedAt = System.currentTimeMillis(), error = null))
            result
        } } catch (failure: Throwable) {
            withContext(NonCancellable) { cleanup(listOfNotNull(baseCopy)) }
            throw failure
        }
    }

    suspend fun publishTransparentMaterial(id: String, bytes: ByteArray): String = publish(id, bytes) { task, path ->
        require(task.kind == ImageTaskKind.SINGLE_ITEM_MATERIAL && validateMaterialImage(path)) { "未返回有效透明单品" }
        dao.update(task.copy(status = ImageTaskStatus.AWAITING_CONFIRMATION, resultImagePath = path,
            updatedAt = System.currentTimeMillis(), error = null))
        path
    }

    suspend fun publishMaterialLayers(id: String, response: com.lolita.app.data.remote.GeneratedImageLayers) {
        state.withLock {
            val task = requireActive(id)
            require(task.kind == ImageTaskKind.SINGLE_ITEM_MATERIAL && task.status == ImageTaskStatus.GENERATING)
            dao.update(task.copy(status = ImageTaskStatus.SAVING, updatedAt = System.currentTimeMillis()))
        }
        withContext(NonCancellable) {
            val staged = mutableListOf<String>()
            try {
                require(response.layers.size in 1..16 && response.generatedImages in 2..17)
                val candidates = response.layers.map { layer ->
                    val path = saveImage(layer.bytes).also(staged::add)
                    if (!validateMaterialImage(path)) throw ImageGenerationFailure(ImageGenerationFailureKind.INVALID_IMAGE)
                    MaterialLayerCandidate(path, imageSha256(path), layer.zIndex, layer.name, layer.description, layer.normalizedBounds)
                }
                state.withLock { db.withTransaction {
                    val task = requireActive(id)
                    dao.update(task.copy(status = ImageTaskStatus.AWAITING_CONFIRMATION, resultImagePath = candidates.first().imagePath,
                        layersJson = MaterialLayers.encode(candidates), generatedImageCount = response.generatedImages,
                        updatedAt = System.currentTimeMillis(), error = null))
                } }
            } finally { cleanup(staged) }
        }
    }

    suspend fun publishMannequin(id: String, bytes: ByteArray): String = publish(id, bytes) { task, path ->
        dao.update(task.copy(status = ImageTaskStatus.AWAITING_CONFIRMATION, sourceImagePaths = if (task.keepSourceImages) task.sourceImagePaths else emptyList(), resultImagePath = path,
            updatedAt = System.currentTimeMillis(), error = null))
        path
    }

    suspend fun publishPersonalModel(id: String, bytes: ByteArray): String = publish(id, bytes) { task, path ->
        require(task.kind == ImageTaskKind.PERSONAL_MODEL)
        dao.update(task.copy(status = ImageTaskStatus.AWAITING_CONFIRMATION, resultImagePath = path,
            updatedAt = System.currentTimeMillis(), error = null))
        path
    }

    private suspend fun <T> publish(id: String, bytes: ByteArray, commit: suspend (ImageTask, String) -> T): T {
        state.withLock {
            val task = requireActive(id)
            check(task.status == ImageTaskStatus.GENERATING)
            dao.update(task.copy(status = ImageTaskStatus.SAVING, updatedAt = System.currentTimeMillis()))
        }
        return withContext(NonCancellable) {
            val path = saveImage(bytes)
            try {
                var sources: List<String> = emptyList()
                val result = state.withLock {
                    db.withTransaction {
                        val task = requireActive(id)
                        sources = task.sourceImagePaths
                        commit(task, path)
                    }
                }
                cleanup(sources)
                result
            } catch (failure: Throwable) {
                cleanup(listOf(path))
                throw failure
            }
        }
    }

    /** Also used by the generator's cancellation cleanup; committed previews remain protected. */
    suspend fun discardUnownedImage(path: String) = cleanup(listOf(path))

    private suspend fun requireActive(id: String): ImageTask {
        val task = dao.get(id) ?: throw CancellationException("任务已删除")
        val serviceStopped = synchronized(sessions) { owners[id]?.stopped == true }
        if (!task.status.isActive || paused || serviceStopped || id in cancelling || id in interrupting) throw CancellationException("任务已停止")
        return task
    }

    private suspend fun finish(id: String, status: ImageTaskStatus, error: String) {
        state.withLock {
            val task = dao.get(id) ?: return@withLock
            if (task.status.retainsSourceImages) dao.update(task.copy(
                status = if (id in cancelling) ImageTaskStatus.CANCELLED else status,
                error = if (id in cancelling) {
                    if (task.requestSent) "已取消本地等待；服务可能仍会计费" else "已取消"
                } else error, updatedAt = System.currentTimeMillis()))
        }
        releaseSources(id)
    }

    private suspend fun releaseSources(id: String) {
        val paths = state.withLock {
            val task = dao.get(id) ?: return@withLock emptyList()
            if (task.status.retainsSourceImages) return@withLock emptyList()
            synchronized(sessions) {
                pending -= id
                owners.remove(id)?.taskIds?.remove(id)
            }
            if (task.retainsSourceImages) return@withLock emptyList()
            if (task.sourceImagePaths.isEmpty()) return@withLock emptyList()
            dao.update(task.copy(sourceImagePaths = emptyList()))
            task.sourceImagePaths
        }
        cleanup(paths)
    }

    private suspend fun cleanup(paths: List<String>) {
        paths.distinct().forEach { path -> try { deleteImage(path) } catch (e: CancellationException) { throw e } catch (_: Exception) { } }
    }
}
