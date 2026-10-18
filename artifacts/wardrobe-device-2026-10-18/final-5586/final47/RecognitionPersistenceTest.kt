package com.lolita.app.data

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.recognition.RecognitionCoordinator
import com.lolita.app.data.remote.RecognitionApiResult
import com.lolita.app.data.repository.ItemDraftRepository
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.RecognitionRepository
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** Persistence/ownership with an injected in-process result function; never a real provider. */
@RunWith(AndroidJUnit4::class)
class RecognitionPersistenceTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private inner class Fixture {
        val root = File(target.cacheDir, "recognition-room-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        }
        val db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repository = RecognitionRepository(db, context)
        val draftId = UUID.randomUUID().toString()
        var revision = 4
        var calls = 0
        lateinit var album: String
        lateinit var region: String
        lateinit var item: Item
        suspend fun image(color: Int = android.graphics.Color.GREEN): String {
            val bitmap = Bitmap.createBitmap(24, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            val bytes = ByteArrayOutputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); output.toByteArray() }
            bitmap.recycle()
            return ImageFileHelper.saveGeneratedImage(context, bytes)
        }
        suspend fun init() {
            ImageFileHelper.configure(context, db)
            album = image(android.graphics.Color.BLUE); region = image()
            db.brandDao().insertBrand(Brand(id = 1, name = "保留品牌"))
            db.categoryDao().insertCategory(Category(id = 1, name = "全头假发", categorySemantic = CategorySemantic.FULL_WIG))
            item = Item(id = 1, name = "原名", description = "保留手工备注", brandId = 1, categoryId = 1,
                imageUrls = listOf(album), status = ItemStatus.OWNED)
            db.itemDao().insertItem(item)
            db.itemDraftDao().save(ItemDraft(draftId, 1, ItemDraftData(name = "原名", brandId = 1, categoryId = 1,
                baselineUpdatedAt = item.updatedAt).encode(), imagePaths = listOf(album)))
        }
        suspend fun add(path: String = region): String = repository.addRegion(draftId, path,
            mapOf(RecognitionField.NAME to "原名"), WardrobeFamily.WIG, false)
        fun result(request: RecognitionRequest) = RecognitionResult(candidates = listOf(
            RecognitionCandidate("name", RecognitionField.NAME, "图中假发名称", RecognitionSource.TEXT, listOf(request.regions.first().id), "品名：图中假发名称")))
        suspend fun coordinator(ready: Boolean = true,
            run: suspend (String, RecognitionRequest, List<ByteArray>) -> RecognitionResult = { key, request, bytes ->
                calls++; assertEquals("in-process-controlled-key", key)
                assertEquals(request.regions.map { it.sha256 }, bytes.map { value -> java.security.MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) } })
                result(request)
            }): RecognitionCoordinator = RecognitionCoordinator(db, context, scope, { "in-process-controlled-key" }, { revision }, run, { ready }).also {
                it.withRestorePaused { }
            }
        suspend fun waitFor(id: String, status: RecognitionStatus): RecognitionTask = withTimeout(10_000) {
            while (true) {
                db.recognitionDao().get(id)?.takeIf { it.status == status }?.let { return@withTimeout it }
                delay(10)
            }
            error("unreachable")
        }
        suspend fun close() {
            scope.coroutineContext.job.cancelAndJoin()
            db.close(); root.deleteRecursively()
            ImageFileHelper.configure(AppModule.context(), AppModule.database())
        }
    }
    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val f = Fixture()
        try { f.init(); withTimeout(30_000) { block(f) } } finally { f.close() }
    }

    @Test fun regionsPersistExactHashesLimitRollbackAndRemovalCleansOnlyTheRemovedCopy() = runBlocking { fixture { f ->
        val id = f.add()
        val first = f.repository.get(id)!!
        assertEquals(imageSha256(f.region), first.request().regions.single().sha256)
        assertEquals(24, first.request().regions.single().width); assertEquals(32, first.request().regions.single().height)
        val copies = (1..4).map { f.image(android.graphics.Color.rgb(it * 30, 100, 80)) }
        copies.forEach { assertEquals(id, f.add(it)) }
        val full = f.repository.get(id)!!
        val extra = f.image()
        assertTrue(runCatching { f.add(extra) }.isFailure)
        assertEquals(full, f.repository.get(id)); assertTrue(File(extra).exists())
        f.repository.removeRegion(id, 1)
        assertFalse(File(copies.first()).exists()); assertTrue(File(f.album).exists())
        val remaining = f.repository.get(id)!!
        assertEquals(4, remaining.regionImagePaths.size)
        assertEquals(remaining.regionImagePaths.map(::imageSha256), remaining.request().regions.map { it.sha256 })
        assertEquals(listOf(f.album), f.db.itemDao().getItemById(1)!!.imageUrls)
    } }

    @Test fun reviewedResultAndEvidenceSurviveRecreationCommitAndTaskDiscardWithoutEnteringTheAlbum() = runBlocking { fixture { f ->
        val coordinator = f.coordinator()
        val id = f.add()
        val confirmation = f.repository.confirmation(id, mapOf(RecognitionField.NAME to "原名"), WardrobeFamily.WIG)
        coordinator.submit(confirmation, 4)
        val awaiting = f.waitFor(id, RecognitionStatus.AWAITING_REVIEW)
        assertEquals(1, f.calls); assertTrue(awaiting.requestSent)
        val review = RecognitionReview(selectedIds = setOf("name"), edits = mapOf("name" to "核对后的名称"))
        f.repository.saveReview(id, review.encode())
        val recreated = RecognitionRepository(f.db, f.context)
        assertEquals(review, RecognitionReview.decode(recreated.get(id)!!.reviewJson!!, setOf("name")))
        val merge = recreated.apply(id, review) { request, candidates, selected ->
            val draft = f.db.itemDraftDao().get(f.draftId)!!
            val data = draft.data()
            RecognitionMerge.apply(request.draftId, draft.id, request.baseline, mapOf(RecognitionField.NAME to data.name), candidates, selected.selectedIds).also { applied ->
                f.db.itemDraftDao().save(draft.copy(dataJson = data.copy(name = applied.values[RecognitionField.NAME] ?: data.name).encode()))
            }
        }
        assertEquals(setOf("name"), merge.acceptedIds)
        assertEquals(RecognitionStatus.APPLIED, recreated.get(id)!!.status)
        assertEquals(1, f.db.recognitionDao().getEvidence().size)
        val drafts = ItemDraftRepository(f.db, ItemRepository(f.db.itemDao(), database = f.db))
        drafts.commit(f.draftId, f.item.copy(name = "核对后的名称"))
        assertNull(f.db.itemDraftDao().get(f.draftId))
        assertEquals(1L, f.db.recognitionDao().getEvidence().single().itemId)
        recreated.discard(id)
        assertNull(recreated.get(id)); assertTrue(File(f.region).isFile)
        assertEquals(listOf(f.album), f.db.itemDao().getItemById(1)!!.imageUrls)
        assertEquals("核对后的名称", f.db.itemDao().getItemById(1)!!.name)
        assertTrue(runCatching { recreated.apply(id, review) { _, _, _ -> error("Cannot reapply deleted history") } }.isFailure)
    } }

    @Test fun staleConfirmationAndChangedKeyNeverCallTheInjectedTransport() = runBlocking { fixture { f ->
        val coordinator = f.coordinator()
        val id = f.add()
        val confirmation = f.repository.confirmation(id, emptyMap(), WardrobeFamily.WIG)
        f.revision++
        assertTrue(runCatching { coordinator.submit(confirmation, 4) }.isFailure)
        f.revision = 4
        f.add(f.image(android.graphics.Color.RED))
        assertTrue(runCatching { coordinator.submit(confirmation, 4) }.isFailure)
        assertEquals(0, f.calls); assertFalse(f.repository.get(id)!!.requestSent)
    } }

    @Test fun processRecreationInterruptsPreparedAndRunningTasksWithoutAnyAutomaticResend() = runBlocking { fixture { f ->
        val preparedId = f.add()
        val prepared = f.repository.get(preparedId)!!
        val running = prepared.copy(id = UUID.randomUUID().toString(), status = RecognitionStatus.RUNNING, requestSent = true)
        f.db.recognitionDao().insert(running)
        f.coordinator()
        assertEquals(RecognitionStatus.INTERRUPTED, f.repository.get(preparedId)!!.status)
        assertEquals(RecognitionStatus.INTERRUPTED, f.repository.get(running.id)!!.status)
        assertTrue(f.repository.get(running.id)!!.requestSent)
        assertEquals(0, f.calls); assertTrue(File(f.region).exists())
    } }

    @Test fun restoreBarrierCancelsTheWaiterBeforeReplacementAndNeverResendsTheCancelledTask() = runBlocking { fixture { f ->
        val entered = CompletableDeferred<Unit>()
        var exited = false
        val coordinator = f.coordinator(run = { _, _, _ ->
            f.calls++; entered.complete(Unit)
            try { awaitCancellation() } finally { exited = true }
        })
        val id = f.add(); coordinator.submit(f.repository.confirmation(id, emptyMap(), WardrobeFamily.WIG), 4)
        withTimeout(5_000) { entered.await() }
        coordinator.withRestorePaused {
            assertTrue(exited)
            assertEquals(RecognitionStatus.INTERRUPTED, f.repository.get(id)!!.status)
            f.db.recognitionDao().delete(id)
        }
        assertEquals(1, f.calls); assertNull(f.repository.get(id))
    } }

    @Test fun alteredCropBytesNeverReachTheInjectedTransportOrOverwriteTheSnapshot() = runBlocking { fixture { f ->
        val coordinator = f.coordinator()
        val id = f.add(); val confirmation = f.repository.confirmation(id, emptyMap(), WardrobeFamily.WIG)
        val before = confirmation.requestJson
        val other = f.image(android.graphics.Color.RED)
        File(other).copyTo(File(f.region), overwrite = true)
        coordinator.submit(confirmation, 4)
        withTimeout(5_000) {
            while (f.repository.get(id)!!.status == RecognitionStatus.RUNNING) delay(10)
        }
        assertEquals("Changed bytes must fail before invoking even an injected provider", 0, f.calls)
        assertEquals(RecognitionStatus.FAILED, f.repository.get(id)!!.status)
        assertEquals(before, f.repository.get(id)!!.requestJson)
    } }

    @Test fun deepSeekReadsOnlyItsOwnKeyAndPersistsTheActualResponseModel() = runBlocking { fixture { f ->
        val keyReads = mutableListOf<RecognitionProvider>()
        val coordinator = RecognitionCoordinator(f.db, f.context, f.scope,
            readKey = { error("Legacy default-provider key must not be read") },
            keyRevision = { f.revision },
            recognize = { _, _, _ -> error("The metadata transport must own this request") },
            providerReady = { true }, selectedProvider = { RecognitionProvider.DEEPSEEK },
            readProviderKey = { provider -> keyReads += provider; "controlled-deepseek-key" },
            recognizeWithMetadata = { key, request, bytes ->
                f.calls++
                assertEquals("controlled-deepseek-key", key)
                assertEquals(RecognitionProvider.DEEPSEEK, request.provider)
                assertEquals(RecognitionProvider.DEEPSEEK.model, request.model)
                assertEquals(request.regions.map { it.sha256 }, bytes.map { value ->
                    java.security.MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
                })
                RecognitionApiResult(f.result(request), RecognitionProvider.DEEPSEEK, "deepseek-flash-controlled-20261018")
            })
        coordinator.withRestorePaused { }
        val id = f.add()
        val confirmation = f.repository.confirmation(id, emptyMap(), WardrobeFamily.WIG, RecognitionProvider.DEEPSEEK)
        coordinator.submit(confirmation, 4)
        val awaiting = f.waitFor(id, RecognitionStatus.AWAITING_REVIEW)
        assertEquals(listOf(RecognitionProvider.DEEPSEEK), keyReads)
        assertEquals(1, f.calls)
        assertTrue(awaiting.requestSent)
        assertEquals("deepseek-flash-controlled-20261018", awaiting.actualModel)
        assertEquals(confirmation.requestJson, awaiting.requestJson)
    } }

    @Test fun changingRevisionOrProviderWhileReadingTheKeyRejectsBeforeClaimOrTransport() = runBlocking {
        for (changeRevision in listOf(true, false)) fixture { f ->
            var selected = RecognitionProvider.DEEPSEEK
            val keyReads = mutableListOf<RecognitionProvider>()
            val coordinator = RecognitionCoordinator(f.db, f.context, f.scope,
                readKey = { error("Legacy default-provider key must not be read") },
                keyRevision = { f.revision },
                recognize = { _, request, _ -> f.calls++; f.result(request) },
                providerReady = { true }, selectedProvider = { selected },
                readProviderKey = { provider ->
                    keyReads += provider
                    if (changeRevision) f.revision++ else selected = RecognitionProvider.VOLCENGINE_ARK
                    "controlled-deepseek-key"
                },
                recognizeWithMetadata = { _, request, _ ->
                    f.calls++; RecognitionApiResult(f.result(request), request.provider, "controlled-actual-model")
                })
            coordinator.withRestorePaused { }
            val id = f.add()
            val confirmation = f.repository.confirmation(id, emptyMap(), WardrobeFamily.WIG, RecognitionProvider.DEEPSEEK)
            assertTrue("Configuration changed during key read must reject submission", runCatching {
                coordinator.submit(confirmation, 4)
            }.isFailure)
            val unchanged = f.repository.get(id)!!
            assertEquals(listOf(RecognitionProvider.DEEPSEEK), keyReads)
            assertEquals(0, f.calls)
            assertEquals(RecognitionStatus.PREPARED, unchanged.status)
            assertFalse(unchanged.requestSent)
            assertEquals(confirmation, unchanged)
        }
    }
}
