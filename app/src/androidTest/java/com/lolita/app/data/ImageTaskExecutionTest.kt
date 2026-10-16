package com.lolita.app.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.generation.*
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.domain.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ImageTaskExecutionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private inner class Fixture {
        val db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val folder = File(context.cacheDir, "task-${UUID.randomUUID()}").apply { mkdirs() }
        val source = File(folder, "source.png").apply { writeBytes(byteArrayOf(1)) }
        var imageValid = true
        var cancelDuringSave: Job? = null
        var starts = 0
        var calls = 0
        var start: () -> Unit = { starts++ }
        var transport: suspend (ImageGenerationCredentials, List<ByteArray>, String) -> ByteArray = { _, _, _ -> calls++; byteArrayOf(2) }
        var prepare: suspend (List<String>) -> List<ByteArray> = { listOf(byteArrayOf(1), byteArrayOf(1)) }
        val coordinator = ImageTaskCoordinator(db, scope, { start() }, { bytes ->
            cancelDuringSave?.cancel()
            File(folder, "${UUID.randomUUID()}.png").apply { writeBytes(bytes) }.absolutePath
        }, { path ->
            // The production file helper protects both task previews and business records.
            if (db.imageTaskDao().getAll().none { path in it.protectedImagePaths() } &&
                db.tryOnDao().getAll().none { it.imageUrl == path } &&
                db.itemDao().getAllItemsList().none { path in it.imageUrls }) File(path).delete()
        }, { imageValid })
        val executor = ImageTaskExecutor(coordinator, db,
            { ImageGenerationCredentials(it, "controlled-key") }, { prepare(it) }, { prepare(it) },
            { c, images, prompt -> transport(c, images, prompt) })
        val runner = ImageTaskRunner(coordinator, executor::execute)
        lateinit var selection: TryOnSelection
        suspend fun init() {
            coordinator.awaitStartup()
            val brand = db.brandDao().insertBrand(Brand(name = "测试"))
            val category = db.categoryDao().insertCategory(Category(name = "JSK"))
            val item = db.itemDao().insertItem(Item(brandId = brand, categoryId = category, name = "裙子", description = "", imageUrls = listOf(source.absolutePath), status = ItemStatus.OWNED))
            selection = TryOnSelection(item, "裙子", source.absolutePath, TryOnRole.MAIN)
        }
        suspend fun submit() = coordinator.submitTryOn(listOf(selection), ImageGenerationProvider.SEEDREAM)
        suspend fun close() { scope.coroutineContext.job.cancelAndJoin(); db.close(); folder.deleteRecursively() }
    }
    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val f = Fixture()
        try { f.init(); withTimeout(15_000) { block(f) } } finally { f.close() }
    }

    @Test fun concurrentRunnersSendOnceAndAtomicallyPublishCapturedProvider() = runBlocking { fixture { f ->
        val id = f.submit()
        f.transport = { credentials, _, _ ->
            f.calls++
            val task = f.db.imageTaskDao().get(id)!!
            assertEquals(ImageTaskStatus.GENERATING, task.status); assertTrue(task.requestSent)
            assertEquals(ImageGenerationProvider.SEEDREAM, credentials.provider)
            byteArrayOf(3)
        }
        coroutineScope { listOf(async { f.runner.runQueue() }, async { f.runner.runQueue() }).awaitAll() }
        val task = f.db.imageTaskDao().get(id)!!
        assertEquals(1, f.calls); assertEquals(ImageTaskStatus.COMPLETED, task.status)
        assertEquals(task.resultImagePath, f.db.tryOnDao().get(task.tryOnResultId!!)!!.imageUrl)
        assertTrue(File(task.resultImagePath!!).exists()); assertTrue(task.sourceImagePaths.isEmpty())
        f.coordinator.cancel(id)
        assertEquals(ImageTaskStatus.COMPLETED, f.db.imageTaskDao().get(id)!!.status)
    } }

    @Test fun cancellationBeforeAndAfterSendingDoesNotRetry() = runBlocking { fixture { f ->
        val first = f.submit(); f.coordinator.cancel(first); f.runner.runQueue()
        assertEquals(0, f.calls); assertFalse(f.db.imageTaskDao().get(first)!!.requestSent)
        val second = f.submit(); val entered = CompletableDeferred<Unit>()
        f.transport = { _, _, _ -> f.calls++; entered.complete(Unit); awaitCancellation() }
        val running = launch { f.runner.runQueue() }; entered.await(); f.coordinator.cancel(second); running.join()
        f.runner.runQueue()
        assertEquals(1, f.calls); assertTrue(f.db.imageTaskDao().get(second)!!.requestSent)
        assertEquals(ImageTaskStatus.CANCELLED, f.db.imageTaskDao().get(second)!!.status)
        assertTrue(f.db.tryOnDao().getAll().isEmpty())
    } }

    @Test fun restoreCancelsAndJoinsTransportBeforeItsBlockAndAllowsLaterSubmission() = runBlocking { fixture { f ->
        val id = f.submit(); val entered = CompletableDeferred<Unit>(); var exited = false
        f.transport = { _, _, _ -> try { entered.complete(Unit); awaitCancellation() } finally { exited = true } }
        val running = launch { f.runner.runQueue() }; entered.await()
        assertEquals(42, f.coordinator.withRestorePaused {
            assertTrue(exited); assertEquals(ImageTaskStatus.INTERRUPTED, f.db.imageTaskDao().get(id)!!.status); 42
        })
        running.join()
        f.transport = { _, _, _ -> byteArrayOf(4) }
        val next = f.submit(); f.runner.runQueue()
        assertEquals(ImageTaskStatus.COMPLETED, f.db.imageTaskDao().get(next)!!.status)
    } }

    @Test fun serviceStartRejectionIsVisibleAndNeverSends() = runBlocking { fixture { f ->
        f.start = { throw IllegalStateException("background start rejected") }
        val id = f.submit(); f.runner.runQueue()
        assertEquals(ImageTaskStatus.INTERRUPTED, f.db.imageTaskDao().get(id)!!.status)
        assertEquals(0, f.calls)
    } }

    @Test fun disposedSubmitCallerDoesNotCancelAcceptedSubmission() = runBlocking { fixture { f ->
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val barrier = launch { f.coordinator.withRestorePaused { entered.complete(Unit); release.await() } }
        entered.await()
        val caller = launch(start = CoroutineStart.UNDISPATCHED) { f.submit() }
        caller.cancelAndJoin(); release.complete(Unit); barrier.join()
        withTimeout(5_000) { while (f.db.imageTaskDao().getAll().isEmpty()) yield() }
        f.runner.runQueue()
        assertEquals(ImageTaskStatus.COMPLETED, f.db.imageTaskDao().getAll().single().status)
    } }

    @Test fun mannequinConfirmDiscardRaceKeepsExactlyOneOwner() = runBlocking { fixture { f ->
        val id = f.coordinator.submitMannequin(MannequinRequest(f.selection.itemId, f.selection.imagePath), ImageGenerationProvider.OPENAI)
        f.runner.runQueue()
        val preview = f.db.imageTaskDao().get(id)!!.resultImagePath!!
        assertEquals(ImageTaskStatus.AWAITING_CONFIRMATION, f.db.imageTaskDao().get(id)!!.status)
        coroutineScope { listOf(async { runCatching { f.coordinator.confirmMannequin(id) } }, async { f.coordinator.discard(id) }).awaitAll() }
        val task = f.db.imageTaskDao().get(id)!!
        val attached = preview in f.db.itemDao().getItemById(f.selection.itemId)!!.imageUrls
        assertEquals(task.status == ImageTaskStatus.COMPLETED, attached)
        assertEquals(attached, File(preview).exists())
    } }

    @Test fun deletedItemRejectsConfirmationAndKeepsPreviewUntilDiscard() = runBlocking { fixture { f ->
        val id = f.coordinator.submitMannequin(MannequinRequest(f.selection.itemId, f.selection.imagePath), ImageGenerationProvider.OPENAI)
        f.runner.runQueue(); val preview = f.db.imageTaskDao().get(id)!!.resultImagePath!!
        f.db.itemDao().deleteItem(f.db.itemDao().getItemById(f.selection.itemId)!!)
        assertTrue(runCatching { f.coordinator.confirmMannequin(id) }.isFailure)
        assertTrue(File(preview).exists()); assertEquals(ImageTaskStatus.AWAITING_CONFIRMATION, f.db.imageTaskDao().get(id)!!.status)
        f.coordinator.discard(id); assertFalse(File(preview).exists())
    } }
    @Test fun cancellationAtPublicationKeepsCommittedMannequinPreview() = runBlocking { fixture { f ->
        val id = f.coordinator.submitMannequin(MannequinRequest(f.selection.itemId, f.selection.imagePath), ImageGenerationProvider.OPENAI)
        val running = launch(start = CoroutineStart.LAZY) { f.runner.runQueue() }
        f.cancelDuringSave = running
        running.start(); running.join()
        val task = f.db.imageTaskDao().get(id)!!
        assertEquals(ImageTaskStatus.AWAITING_CONFIRMATION, task.status)
        assertTrue(File(task.resultImagePath!!).exists())
        f.coordinator.cancel(id)
        assertEquals(ImageTaskStatus.AWAITING_CONFIRMATION, f.db.imageTaskDao().get(id)!!.status)
    } }

    @Test fun timeoutInterruptsWithoutRetryAndNextQueuedTaskStillRuns() = runBlocking { fixture { f ->
        val first = f.submit(); val second = f.submit()
        f.transport = { _, _, _ -> f.calls++; if (f.calls == 1) awaitCancellation() else byteArrayOf(8) }
        ImageTaskRunner(f.coordinator, f.executor::execute, timeoutMillis = 1_000).runQueue()
        assertEquals(ImageTaskStatus.INTERRUPTED, f.db.imageTaskDao().get(first)!!.status)
        assertEquals(ImageTaskStatus.COMPLETED, f.db.imageTaskDao().get(second)!!.status)
        assertEquals(2, f.calls)
    } }

    @Test fun startupRecoveryInterruptsPreviouslyAcceptedQueueExactlyOnce() = runBlocking { fixture { f ->
        val first = f.submit()
        val recovered = ImageTaskCoordinator(f.db, f.scope, {}, { error("no save") }, {}, { true })
        recovered.awaitStartup()
        assertEquals(ImageTaskStatus.INTERRUPTED, f.db.imageTaskDao().get(first)!!.status)
        val next = recovered.submitTryOn(listOf(f.selection), ImageGenerationProvider.OPENAI)
        recovered.awaitStartup()
        assertEquals(ImageTaskStatus.QUEUED, f.db.imageTaskDao().get(next)!!.status)
        assertEquals(0, f.calls)
    } }

    @Test fun submissionDuringEmptyQueueCallbackIsDrainedByNextServiceStart() = runBlocking { fixture { f ->
        var next: String? = null
        f.runner.runQueue { if (it == null && next == null) next = f.submit() }
        f.runner.runQueue()
        assertEquals(ImageTaskStatus.COMPLETED, f.db.imageTaskDao().get(next!!)!!.status)
        assertEquals(1, f.calls); assertEquals(1, f.starts)
    } }

    @Test fun realFileHelperProtectsQueuedSourcesAndPendingPreviewsThenReleasesThem() = runBlocking {
        val root = File(context.cacheDir, "task-files-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : android.content.ContextWrapper(context) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
        }
        val db = Room.inMemoryDatabaseBuilder(isolated, LolitaDatabase::class.java).build()
        try {
            com.lolita.app.data.file.ImageFileHelper.configure(isolated, db)
            val bytes = java.io.ByteArrayOutputStream().use { output ->
                val bitmap = android.graphics.Bitmap.createBitmap(16, 24, android.graphics.Bitmap.Config.ARGB_8888)
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
                bitmap.recycle(); output.toByteArray()
            }
            val source = com.lolita.app.data.file.ImageFileHelper.saveGeneratedImage(isolated, bytes)
            val preview = com.lolita.app.data.file.ImageFileHelper.saveGeneratedImage(isolated, bytes)
            val task = ImageTask("protection", ImageTaskKind.MANNEQUIN, ImageGenerationProvider.OPENAI, "裙子",
                ImageTaskRequest(listOf(ImageTaskGarment(1, "裙子", TryOnRole.MAIN))).encode(), listOf(source))
            db.imageTaskDao().insert(task)
            com.lolita.app.data.file.ImageFileHelper.deleteImage(source)
            assertTrue(File(source).isFile)
            db.imageTaskDao().update(task.copy(status = ImageTaskStatus.AWAITING_CONFIRMATION,
                sourceImagePaths = emptyList(), resultImagePath = preview))
            com.lolita.app.data.file.ImageFileHelper.deleteImage(source)
            com.lolita.app.data.file.ImageFileHelper.deleteImage(preview)
            assertFalse(File(source).exists()); assertTrue(File(preview).isFile)
            db.imageTaskDao().delete(task.id)
            com.lolita.app.data.file.ImageFileHelper.deleteImage(preview)
            assertFalse(File(preview).exists())
        } finally {
            com.lolita.app.data.file.ImageFileHelper.configure(context, com.lolita.app.di.AppModule.database())
            db.close(); root.deleteRecursively()
        }
    }

    @Test fun serviceRejectionAfterExistingRunnerClaimsCancelsReaderBeforeReleasingSources() = runBlocking { fixture { f ->
        val entered = CompletableDeferred<Unit>()
        var exited = false
        f.transport = { _, _, _ -> try { entered.complete(Unit); awaitCancellation() } finally { exited = true } }
        f.start = {
            f.scope.launch { f.runner.runQueue() }
            runBlocking { withTimeout(5_000) { entered.await() } }
            error("foreground start rejected")
        }
        val id = f.submit()
        assertTrue("The claimed network reader must exit before rejection returns", exited)
        assertEquals(ImageTaskStatus.INTERRUPTED, f.db.imageTaskDao().get(id)!!.status)
        assertTrue(f.db.imageTaskDao().get(id)!!.sourceImagePaths.isEmpty())
        assertTrue(f.db.tryOnDao().getAll().isEmpty())
    } }

    @Test fun invalidExistingPreviewCannotBeConfirmedAndRemainsPendingForDiscard() = runBlocking { fixture { f ->
        val id = f.coordinator.submitMannequin(MannequinRequest(f.selection.itemId, f.selection.imagePath), ImageGenerationProvider.OPENAI)
        f.runner.runQueue(); val task = f.db.imageTaskDao().get(id)!!
        assertTrue(File(task.resultImagePath!!).exists())
        f.imageValid = false
        assertTrue(runCatching { f.coordinator.confirmMannequin(id) }.isFailure)
        assertEquals(ImageTaskStatus.AWAITING_CONFIRMATION, f.db.imageTaskDao().get(id)!!.status)
        assertEquals(listOf(f.selection.imagePath), f.db.itemDao().getItemById(f.selection.itemId)!!.imageUrls)
        assertTrue(File(task.resultImagePath).exists())
    } }

    @Test fun foregroundPromotionFailureInterruptsAcceptedQueueWithoutSending() = runBlocking { fixture { f ->
        val id = f.submit()
        f.coordinator.serviceStartFailed(f.coordinator.openServiceSession()).join()
        f.runner.runQueue()
        assertEquals(ImageTaskStatus.INTERRUPTED, f.db.imageTaskDao().get(id)!!.status)
        assertEquals(0, f.calls)
    } }

    @Test fun serviceStopInterruptsItsQueueAfterReaderExitAndLaterSubmissionRuns() = runBlocking { fixture { f ->
        val first = f.submit(); val second = f.submit()
        val entered = CompletableDeferred<Unit>(); val cancelled = CompletableDeferred<Unit>()
        val exit = CompletableDeferred<Unit>(); var exited = false
        f.transport = { _, _, _ ->
            f.calls++
            try { entered.complete(Unit); awaitCancellation() }
            finally { withContext(NonCancellable) { cancelled.complete(Unit); exit.await(); exited = true } }
        }
        val session = f.coordinator.openServiceSession()
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val running = serviceScope.launch { f.runner.runQueue(session) }; entered.await()
        // Remove the business owner to make early source release observable on disk.
        f.db.itemDao().updateImages(f.selection.itemId, emptyList(), System.currentTimeMillis())
        val stopped = f.coordinator.serviceStopped(session)
        serviceScope.cancel() // Android stops immediately; app-scope cleanup must survive.
        cancelled.await()
        assertFalse(exited)
        assertFalse(f.db.imageTaskDao().get(first)!!.sourceImagePaths.isEmpty())
        assertFalse(f.db.imageTaskDao().get(second)!!.sourceImagePaths.isEmpty())
        assertTrue(f.source.exists())
        exit.complete(Unit); stopped.join(); running.join()
        assertTrue(exited)
        assertFalse("Unowned source is released only after the reader exits", f.source.exists())
        for (id in listOf(first, second)) {
            val task = f.db.imageTaskDao().get(id)!!
            assertEquals("Stopped service must make every admitted row actionable", ImageTaskStatus.INTERRUPTED, task.status)
            assertTrue(task.sourceImagePaths.isEmpty())
            assertTrue(task.error!!.contains("重新发起"))
        }
        assertEquals(1, f.calls)
        f.source.writeBytes(byteArrayOf(1))
        f.db.itemDao().updateImages(f.selection.itemId, listOf(f.source.absolutePath), System.currentTimeMillis())
        f.transport = { _, _, _ -> f.calls++; byteArrayOf(4) }
        val later = f.submit(); f.runner.runQueue()
        assertEquals(ImageTaskStatus.COMPLETED, f.db.imageTaskDao().get(later)!!.status)
        assertEquals(2, f.calls)
    } }

    @Test fun delayedOldServiceStopCannotInterruptNewServiceOrLaterPendingSubmission() = runBlocking { fixture { f ->
        val first = f.submit(); val oldQueued = f.submit()
        val oldSession = f.coordinator.openServiceSession()
        val oldEntered = CompletableDeferred<Unit>(); val oldCancelled = CompletableDeferred<Unit>()
        val oldExit = CompletableDeferred<Unit>(); val newEntered = CompletableDeferred<Unit>()
        val newExit = CompletableDeferred<Unit>()
        f.transport = { _, _, _ ->
            f.calls++
            if (f.calls == 1) {
                try { oldEntered.complete(Unit); awaitCancellation() }
                finally { withContext(NonCancellable) { oldCancelled.complete(Unit); oldExit.await() } }
            } else { newEntered.complete(Unit); newExit.await(); byteArrayOf(5) }
        }
        val oldRunning = launch { f.runner.runQueue(oldSession) }; oldEntered.await()
        val newer = f.submit()
        val newSession = f.coordinator.openServiceSession()
        val newRunning = launch { f.runner.runQueue(newSession) }
        // Deliver the old stop only after the new instance/start has admitted its row.
        val stopped = f.coordinator.serviceStopped(oldSession)
        oldCancelled.await()
        val pending = f.submit() // Not admitted to either stopped or already-started session.
        assertFalse(f.coordinator.admitServiceStart(oldSession, newer))
        assertFalse(stopped.isCompleted)
        assertEquals(ImageTaskStatus.QUEUED, f.db.imageTaskDao().get(newer)!!.status)
        assertEquals(ImageTaskStatus.QUEUED, f.db.imageTaskDao().get(pending)!!.status)
        oldExit.complete(Unit); stopped.join(); oldRunning.join(); newEntered.await()
        // onDestroy/promotion failure from the old instance is idempotent and scoped.
        f.coordinator.serviceStartFailed(oldSession).join()
        assertEquals(ImageTaskStatus.GENERATING, f.db.imageTaskDao().get(newer)!!.status)
        assertEquals(ImageTaskStatus.QUEUED, f.db.imageTaskDao().get(pending)!!.status)
        assertEquals(ImageTaskStatus.INTERRUPTED, f.db.imageTaskDao().get(first)!!.status)
        assertEquals(ImageTaskStatus.INTERRUPTED, f.db.imageTaskDao().get(oldQueued)!!.status)
        assertEquals(2, f.calls)
        newExit.complete(Unit); newRunning.join()
        f.runner.runQueue()
        assertEquals(ImageTaskStatus.COMPLETED, f.db.imageTaskDao().get(newer)!!.status)
        assertEquals(ImageTaskStatus.COMPLETED, f.db.imageTaskDao().get(pending)!!.status)
        assertEquals(3, f.calls)
    } }

    @Test fun promotionFailureBeforeStartCommandOwnsOnlyItsCapturedPendingRows() = runBlocking { fixture { f ->
        val rejected = f.submit()
        val failedSession = f.coordinator.openServiceSession() // Service.onCreate
        val later = f.submit()
        val laterSession = f.coordinator.openServiceSession()
        f.coordinator.serviceStartFailed(failedSession).join()
        assertFalse(f.coordinator.admitServiceStart(failedSession, later))
        assertEquals(ImageTaskStatus.INTERRUPTED, f.db.imageTaskDao().get(rejected)!!.status)
        assertEquals(ImageTaskStatus.QUEUED, f.db.imageTaskDao().get(later)!!.status)
        f.runner.runQueue(laterSession)
        assertEquals(ImageTaskStatus.COMPLETED, f.db.imageTaskDao().get(later)!!.status)
        assertEquals(1, f.calls)
    } }

    @Test fun lateRejectedStartCannotAdoptGenuinelyLaterPendingOrOwnedRows() = runBlocking { fixture { f ->
        val stale = f.submit(); val oldSession = f.coordinator.openServiceSession()
        f.coordinator.serviceStopped(oldSession).join()
        val owned = f.submit(); val newSession = f.coordinator.openServiceSession()
        val rejected = f.submit(); val pending = f.submit()
        f.coordinator.rejectServiceStart(rejected).join()
        assertEquals(ImageTaskStatus.INTERRUPTED, f.db.imageTaskDao().get(rejected)!!.status)
        // Deliver the exact rejection protocol with the old Intent's durable task ID.
        f.coordinator.rejectServiceStart(stale).join()
        assertEquals(ImageTaskStatus.INTERRUPTED, f.db.imageTaskDao().get(stale)!!.status)
        assertEquals(ImageTaskStatus.QUEUED, f.db.imageTaskDao().get(owned)!!.status)
        assertEquals("Late old start rejection must not sweep later pending submissions",
            ImageTaskStatus.QUEUED, f.db.imageTaskDao().get(pending)!!.status)
        f.runner.runQueue(newSession); f.runner.runQueue()
        assertEquals(ImageTaskStatus.COMPLETED, f.db.imageTaskDao().get(owned)!!.status)
        assertEquals(ImageTaskStatus.COMPLETED, f.db.imageTaskDao().get(pending)!!.status)
        assertEquals(2, f.calls)
    } }

}
