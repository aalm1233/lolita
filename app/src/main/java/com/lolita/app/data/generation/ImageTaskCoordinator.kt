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
    private val appendImageProvenance: suspend (Long, String, String, String?) -> Unit = { _, _, _, _ -> }
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

    suspend fun submitTryOn(selected: List<TryOnSelection>, provider: ImageGenerationProvider, personalModelId: Long?): String {
        return submitTryOnSnapshot(selected, provider, personalModelId, null)
    }

    suspend fun submitTryOn(selected: List<TryOnSelection>, provider: ImageGenerationProvider,
        personalModelId: Long, expectedModelImagePath: String): String {
        require(expectedModelImagePath.isNotBlank()) { "请重新选择试衣模特" }
        return submitTryOnSnapshot(selected, provider, personalModelId, expectedModelImagePath)
    }

    private suspend fun submitTryOnSnapshot(selected: List<TryOnSelection>, provider: ImageGenerationProvider,
        personalModelId: Long?, expectedModelImagePath: String?): String {
        val snapshot = selected.toList()
        val request = TryOnRequest(snapshot)
        return accepted {
            if (snapshot.any { it.semantic != null }) {
                snapshot.forEach { row ->
                    val item = requireNotNull(db.itemDao().getItemById(row.itemId)) { "所选物品已删除" }
                    require(item.status == com.lolita.app.data.local.entity.ItemStatus.OWNED && row.imagePath in item.imageUrls) { "所选物品或图片已改变" }
                    require(db.categoryDao().getCategoryById(item.categoryId)?.categorySemantic == row.semantic) { "分类语义已改变，请重新核对" }
                }
                val person = personalModelId?.let { requireNotNull(db.personalModelDao().get(it)) { "所选人物已删除" } }
                require(person == null || expectedModelImagePath == null || person.imageUrl == expectedModelImagePath) { "人物底图已改变，请重新确认" }
                val modelPath = person?.imageUrl ?: snapshotBuiltInImage(com.lolita.app.data.tryon.TryOnImages.MODEL_ASSET)
                val sources = listOf(modelPath) + snapshot.map { it.imagePath }
                val metadata = ImageTaskRequest(snapshot.map { ImageTaskGarment(it.itemId, it.name, it.role, it.semantic, it.composition) },
                    version = 4, tryOnModel = person?.let { TryOnModelSnapshot(it.id, it.name) }, promptVersion = 1,
                    promptSnapshot = request.prompt(personalModel = person != null), serviceModel = provider.model,
                    inputSha256s = sources.map(::imageSha256))
                return@accepted enqueue(ImageTaskKind.TRY_ON, provider, "虚拟试衣", metadata, sources)
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

    suspend fun submitMannequin(request: MannequinRequest, provider: ImageGenerationProvider): String = accepted {
        val item = requireNotNull(db.itemDao().getItemById(request.itemId)) { "物品已删除，请重新选择" }
        val subject = requireNotNull(mannequinSubject(db.categoryDao().getCategoryById(item.categoryId))) { "此分类暂不支持展示图" }
        require(request.subject?.let { it == subject } ?: (subject.presentation == MannequinPresentation.DRESS_FORM)) {
            "物品分类已改变，请重新核对展示方式"
        }
        if (subject.semantic != null) {
            val template = when (subject.presentation) {
                MannequinPresentation.DRESS_FORM -> snapshotBuiltInImage(com.lolita.app.data.tryon.TryOnImages.MANNEQUIN_ASSET)
                MannequinPresentation.WIG_HEAD -> { require(request.referenceViewConfirmed) { "请先核对假发正面视角与发梢完整性" }; requireNotNull(request.wigHeadTemplate) { "请选择本次头模" }.also { it.validateImage() }.imagePath }
                MannequinPresentation.ACCESSORY -> null
            }
            val sources = listOfNotNull(template) + request.imagePath
            val metadata = ImageTaskRequest(listOf(ImageTaskGarment(item.id, item.name, TryOnRole.MAIN, subject.semantic, item.composition)),
                request.instructions, version = 4, mannequinSubject = subject, promptVersion = 1,
                promptSnapshot = request.prompt(), serviceModel = provider.model, inputSha256s = sources.map(::imageSha256), wigHeadTemplate = request.wigHeadTemplate)
            return@accepted enqueue(ImageTaskKind.MANNEQUIN, provider, "${item.name} · ${subject.presentation.label}", metadata, sources)
        }
        enqueue(ImageTaskKind.MANNEQUIN, provider, "${item.name} · ${subject.presentation.label}",
            ImageTaskRequest(listOf(ImageTaskGarment(item.id, item.name, TryOnRole.MAIN)), request.instructions,
                version = 3, mannequinSubject = subject), listOf(request.imagePath))
    }

    private suspend fun enqueue(kind: ImageTaskKind, provider: ImageGenerationProvider, title: String,
        request: ImageTaskRequest, paths: List<String>, personalModelId: Long? = null, expectedModelImagePath: String? = null): String {
        val task = state.withLock {
            db.withTransaction {
                var metadata = request
                var sources = paths.toList()
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
                    val item = requireNotNull(db.itemDao().getItemById(metadata.garments.single().itemId)) { "物品已删除，请重新选择" }
                    require((metadata.version == 4 || sources.size == 1) && sources.last() in item.imageUrls) { "参考图片已改变，请重新选择" }
                    val currentSubject = mannequinSubject(db.categoryDao().getCategoryById(item.categoryId))
                    require(currentSubject != null && currentSubject == metadata.mannequinSubject) { "物品分类已改变，请重新核对展示方式" }
                }
                if (metadata.version == 4) {
                    sources.forEach { require(validateSourceImage(it)) { "任务实际输入不属于本机图片或已无法读取" } }
                    metadata.validateInputImages(sources)
                }
                ImageTask(UUID.randomUUID().toString(), kind, provider, title, metadata.encode(), sources, keepSourceImages = metadata.version == 4).also {
                    it.validate()
                    dao.insert(it)
                    synchronized(sessions) { pending += it.id }
                }
            }
        }
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
        return task.id
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
            dao.update(task.copy(status = ImageTaskStatus.DISCARDED, resultImagePath = null,
                sourceImagePaths = if (task.keepSourceImages) task.sourceImagePaths else emptyList(), updatedAt = System.currentTimeMillis()))
            (if (task.keepSourceImages) emptyList() else task.sourceImagePaths) + listOfNotNull(task.resultImagePath)
        }
        cleanup(paths)
    }

    suspend fun delete(id: String) = accepted {
        val paths = state.withLock {
            val task = dao.get(id) ?: return@withLock emptyList()
            require(!task.status.retainsSourceImages) { "请先取消正在执行的任务" }
            dao.delete(id)
            task.sourceImagePaths + listOfNotNull(task.resultImagePath)
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

    suspend fun publishTryOn(id: String, bytes: ByteArray, selected: List<TryOnSelection>, model: String): Long =
        publish(id, bytes) { task, path ->
            val person = ImageTaskRequest.decode(task.requestJson).tryOnModel
            val result = db.tryOnDao().insert(TryOnResult(name = task.title, imageUrl = path,
                itemIds = selected.map { it.itemId.toString() }, itemNames = selected.map { it.name }, model = model,
                personalModelId = person?.personalModelId, personalModelName = person?.name,
                requestSnapshotJson = ImageTaskRequest.decode(task.requestJson).takeIf { it.version == 4 }?.historySnapshot()))
            dao.update(task.copy(status = ImageTaskStatus.COMPLETED, sourceImagePaths = if (task.keepSourceImages) task.sourceImagePaths else emptyList(), resultImagePath = path,
                tryOnResultId = result, updatedAt = System.currentTimeMillis(), error = null))
            result
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
