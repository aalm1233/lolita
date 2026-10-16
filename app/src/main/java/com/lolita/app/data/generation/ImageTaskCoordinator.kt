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

/** Owns accepted mutations independently of a screen's lifetime. Never owns network execution. */
class ImageTaskCoordinator(
    private val db: LolitaDatabase,
    private val applicationScope: CoroutineScope,
    private val startService: (String) -> Unit,
    private val saveImage: suspend (ByteArray) -> String,
    private val deleteImage: suspend (String) -> Unit,
    private val validateImage: suspend (String) -> Boolean
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

    suspend fun submitTryOn(selected: List<TryOnSelection>, provider: ImageGenerationProvider): String {
        val snapshot = selected.toList()
        val request = TryOnRequest(snapshot)
        return accepted {
            enqueue(ImageTaskKind.TRY_ON, provider, "虚拟试衣",
                ImageTaskRequest(request.selections.map { ImageTaskGarment(it.itemId, it.name, it.role) }),
                request.selections.map { it.imagePath })
        }
    }

    suspend fun submitMannequin(request: MannequinRequest, provider: ImageGenerationProvider): String = accepted {
        val item = requireNotNull(db.itemDao().getItemById(request.itemId)) { "裙子已删除，请重新选择" }
        enqueue(ImageTaskKind.MANNEQUIN, provider, "${item.name} · 人台图",
            ImageTaskRequest(listOf(ImageTaskGarment(item.id, item.name, TryOnRole.MAIN)), request.instructions), listOf(request.imagePath))
    }

    private suspend fun enqueue(kind: ImageTaskKind, provider: ImageGenerationProvider, title: String,
        request: ImageTaskRequest, paths: List<String>): String {
        val task = ImageTask(UUID.randomUUID().toString(), kind, provider, title, request.encode(), paths)
        task.validate()
        state.withLock {
            dao.insert(task)
            synchronized(sessions) { pending += task.id }
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
                db.itemDao().updateImages(item.id, (item.imageUrls + path).distinct(), System.currentTimeMillis())
                dao.update(task.copy(status = ImageTaskStatus.COMPLETED, updatedAt = System.currentTimeMillis(), error = null))
            }
        }
    }

    suspend fun discard(id: String) = accepted {
        val path = state.withLock {
            val task = dao.get(id) ?: return@withLock null
            if (task.status != ImageTaskStatus.AWAITING_CONFIRMATION) return@withLock null
            dao.update(task.copy(status = ImageTaskStatus.DISCARDED, resultImagePath = null, updatedAt = System.currentTimeMillis()))
            task.resultImagePath
        }
        path?.let { cleanup(listOf(it)) }
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
                } catch (_: Exception) {
                    // Do not persist server text, URLs, credential fragments or response bodies.
                    finish(task.id, ImageTaskStatus.FAILED, "生成未完成，请检查服饰、图片和服务配置后重新发起")
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
            val result = db.tryOnDao().insert(TryOnResult(name = task.title, imageUrl = path,
                itemIds = selected.map { it.itemId.toString() }, itemNames = selected.map { it.name }, model = model))
            dao.update(task.copy(status = ImageTaskStatus.COMPLETED, sourceImagePaths = emptyList(), resultImagePath = path,
                tryOnResultId = result, updatedAt = System.currentTimeMillis(), error = null))
            result
        }

    suspend fun publishMannequin(id: String, bytes: ByteArray): String = publish(id, bytes) { task, path ->
        dao.update(task.copy(status = ImageTaskStatus.AWAITING_CONFIRMATION, sourceImagePaths = emptyList(), resultImagePath = path,
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
