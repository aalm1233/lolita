package com.lolita.app.data

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.file.ImageRegionCropper
import com.lolita.app.data.file.NormalizedCropRect
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.recognition.RecognitionCoordinator
import com.lolita.app.data.recognition.RecognitionSettings
import com.lolita.app.data.repository.*
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.*
import com.lolita.app.ui.screen.item.ItemEditViewModel
import com.lolita.app.ui.screen.item.recognition.ItemRecognitionViewModel
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Random
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Real Room and owned image bytes only; no credentials, coordinator or network. */
@RunWith(AndroidJUnit4::class)
class RecognitionRegionReplacementTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private inner class Fixture {
        val root = File(target.cacheDir, "region-replacement-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(root, "keys").apply { mkdirs() }
        }
        val db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        val repository = RecognitionRepository(db, context)
        val draftId = UUID.randomUUID().toString()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val stores = mutableListOf<ViewModelStore>()
        val settings = RecognitionSettings(context)
        val transportCalls = AtomicInteger()
        val keyReads = AtomicInteger()

        suspend fun init() {
            ImageFileHelper.configure(context, db)
            db.itemDraftDao().save(ItemDraft(id = draftId, dataJson = ItemDraftData(name = "未保存物品").encode()))
        }
        fun imageBytes(width: Int = 24, height: Int = 32, noisy: Boolean = false): ByteArray {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                if (noisy) {
                    val random = Random(42)
                    bitmap.setPixels(IntArray(width * height) {
                        Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
                    }, 0, width, 0, 0, width, height)
                } else bitmap.eraseColor(Color.GREEN)
                return ByteArrayOutputStream().use { out ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
                    out.toByteArray()
                }
            } finally { bitmap.recycle() }
        }
        suspend fun image(width: Int = 24, height: Int = 32) =
            ImageFileHelper.saveGeneratedImage(context, imageBytes(width, height))
        suspend fun add(path: String, appearanceOnly: Boolean = false) = repository.addRegion(draftId, path,
            mapOf(RecognitionField.NAME to "未保存物品"), WardrobeFamily.WIG, appearanceOnly)
        suspend fun unchangedAfterRejection(task: RecognitionTask, index: Int, newPath: String) {
            assertTrue("Invalid replacement must be rejected", runCatching {
                repository.replaceRegion(task.id, index, newPath)
            }.isFailure)
            assertEquals("Rejection must leave the entire task unchanged", task, repository.get(task.id))
            assertTrue("The caller retains the new file for its failure cleanup", File(newPath).exists())
        }
        suspend fun editor(): ItemEditViewModel {
            db.brandDao().insertBrand(Brand(id = 1, name = "保留品牌"))
            db.categoryDao().insertCategory(Category(id = 1, name = "替换测试假发", categorySemantic = CategorySemantic.FULL_WIG))
            val item = Item(id = 1, brandId = 1, categoryId = 1, name = "未保存物品", description = "", status = ItemStatus.OWNED)
            db.itemDao().insertItem(item)
            db.itemDraftDao().save(db.itemDraftDao().get(draftId)!!.copy(targetItemId = 1,
                dataJson = ItemDraftData(name = item.name, brandId = 1, categoryId = 1, baselineUpdatedAt = item.updatedAt).encode()))
            val items = ItemRepository(db.itemDao(), database = db, context = context)
            val vm = withContext(Dispatchers.Main) {
                ItemEditViewModel(items, BrandRepository(db.brandDao(), db.itemDao(), db), CategoryRepository(db.categoryDao(), db.itemDao(), db),
                    CoordinateRepository(db.coordinateDao(), db.itemDao(), db), PriceRepository(db.priceDao(), db.paymentDao(), context, db),
                    StyleRepository(db.styleDao(), db.itemDao(), db), SeasonRepository(db.seasonDao(), db.itemDao(), db),
                    SourceRepository(db.sourceDao(), db.itemDao(), db), LocationRepository(db.locationDao(), db.itemDao()),
                    ItemDraftRepository(db, items), StorageRepository(db)).also {
                    stores.add(ViewModelStore().apply { put("editor", it) })
                    it.loadItem(1)
                }
            }
            withTimeout(5_000) { while (vm.uiState.value.isLoading || vm.uiState.value.draftId == null) delay(10) }
            assertNull(vm.uiState.value.loadError)
            assertEquals(draftId, vm.uiState.value.draftId)
            return vm
        }
        suspend fun recognition(): ItemRecognitionViewModel {
            val coordinator = RecognitionCoordinator(db, context, scope,
                readKey = { keyReads.incrementAndGet(); "controlled-local-key" }, keyRevision = { settings.changes.value },
                recognize = { _, request, bytes ->
                    transportCalls.incrementAndGet()
                    assertEquals(request.regions.map { it.sha256 }, bytes.map { value ->
                        MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
                    })
                    RecognitionResult(candidates = listOf(RecognitionCandidate("name", RecognitionField.NAME,
                        "本地受控候选", RecognitionSource.TEXT, listOf(request.regions.first().id), "品名：本地受控候选")))
                }, providerReady = { true }, selectedProvider = { settings.readProvider() })
            coordinator.withRestorePaused { }
            return withContext(Dispatchers.Main) {
                ItemRecognitionViewModel(repository, coordinator, settings).also {
                    stores.add(ViewModelStore().apply { put("recognition", it) })
                    it.bind(draftId, 1)
                }
            }
        }
        suspend fun close() {
            withContext(Dispatchers.Main) { stores.forEach { it.clear() } }
            scope.coroutineContext.job.cancelAndJoin()
            ImageFileHelper.configure(AppModule.context(), AppModule.database())
            db.close()
            root.deleteRecursively()
        }
    }
    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val f = Fixture()
        try { f.init(); block(f) } finally { f.close() }
    }
    private fun sha(path: String) = MessageDigest.getInstance("SHA-256").digest(File(path).readBytes())
        .joinToString("") { "%02x".format(it) }

    @Test fun aFullFiveRegionTaskReplacesOnlyTheChosenSlotAndDeletesItsUnreferencedOldCopy() = runBlocking { fixture { f ->
        val paths = (0..4).map { f.image(24 + it, 32) }
        val id = f.add(paths.first())
        paths.drop(1).forEach { assertEquals(id, f.add(it)) }
        val before = f.repository.get(id)!!
        val replacement = f.image(40, 48)
        f.repository.replaceRegion(id, 2, replacement)
        val after = f.repository.get(id)!!
        val regions = before.request().regions.toMutableList().apply {
            this[2] = this[2].copy(sha256 = sha(replacement), width = 40, height = 48)
        }
        val expectedPaths = paths.toMutableList().apply { this[2] = replacement }
        assertEquals(before.copy(requestJson = before.request().copy(regions = regions).encode(),
            regionImagePaths = expectedPaths, updatedAt = after.updatedAt), after)
        assertTrue(after.updatedAt > before.updatedAt)
        assertEquals("Replacement must not create or append a task", listOf(id), f.db.recognitionDao().getAll().map { it.id })
        assertEquals(5, after.request().regions.size)
        assertFalse(File(paths[2]).exists())
        (paths.filterIndexed { index, _ -> index != 2 } + replacement).forEach { assertTrue(File(it).isFile) }
    } }

    @Test fun sharedOldImagesStayProtectedAndReplacingNeverLaundersAppearanceOnlySources() = runBlocking { fixture { f ->
        val shared = f.image()
        val id = f.add(shared, appearanceOnly = true)
        f.add(shared)
        val restricted = f.image(30, 30)
        val draft = f.db.itemDraftDao().get(f.draftId)!!
        f.db.itemDraftDao().save(draft.copy(imagePaths = listOf(restricted), appearanceOnlyImagePaths = listOf(restricted)))
        f.repository.replaceRegion(id, 1, restricted)
        assertTrue("The other slot still protects the old file", File(shared).exists())
        assertTrue(f.repository.get(id)!!.request().regions[1].appearanceOnly)
        val fresh = f.image(32, 32)
        f.repository.replaceRegion(id, 0, fresh)
        assertTrue("An existing appearance-only slot remains restricted", f.repository.get(id)!!.request().regions[0].appearanceOnly)
        assertFalse("The old file is removed only after its last reference is gone", File(shared).exists())
        assertTrue("The draft continues protecting its restricted source", File(restricted).exists())
    } }

    @Test fun sentOrNonPreparedTasksAndClosedMismatchedOrDeletedTargetsRejectWithoutMutation() = runBlocking { fixture { f ->
        val id = f.add(f.image())
        val original = f.repository.get(id)!!
        val replacement = f.image(40, 40)
        for (state in listOf(original.copy(requestSent = true), original.copy(status = RecognitionStatus.INTERRUPTED))) {
            f.db.recognitionDao().update(state)
            f.unchangedAfterRejection(state, 0, replacement)
        }
        f.db.recognitionDao().update(original)
        val draft = f.db.itemDraftDao().get(f.draftId)!!
        f.db.itemDraftDao().save(draft.copy(targetItemId = 42))
        f.unchangedAfterRejection(original, 0, replacement)
        val orphan = original.copy(targetItemId = 42, requestJson = original.request().copy(targetItemId = 42).encode())
        f.db.recognitionDao().update(orphan)
        f.unchangedAfterRejection(orphan, 0, replacement)
        f.db.itemDraftDao().delete(f.draftId)
        f.unchangedAfterRejection(orphan, 0, replacement)
    } }

    @Test fun invalidIndicesExternalCorruptOversizedAndTooWideInputsLeaveThePreparedTaskIntact() = runBlocking { fixture { f ->
        val id = f.add(f.image())
        val before = f.repository.get(id)!!
        val valid = f.image(40, 40)
        f.unchangedAfterRejection(before, -1, valid)
        f.unchangedAfterRejection(before, 1, valid)
        val external = File(f.root, "outside.png").apply { writeBytes(f.imageBytes()) }
        f.unchangedAfterRejection(before, 0, external.path)
        val corrupt = File(File(f.context.filesDir, "images"), "${UUID.randomUUID()}.jpg").apply { writeText("invalid image") }
        f.unchangedAfterRejection(before, 0, corrupt.path)
        val oversized = ImageFileHelper.saveGeneratedImage(f.context, f.imageBytes(1536, 864, noisy = true))
        assertTrue(File(oversized).length() > 3L * 1024 * 1024)
        f.unchangedAfterRejection(before, 0, oversized)
        val tooWide = File(File(f.context.filesDir, "images"), "${UUID.randomUUID()}.png").apply { writeBytes(f.imageBytes(4097, 1)) }
        f.unchangedAfterRejection(before, 0, tooWide.path)
    } }

    @Test fun replacingOneSmallRegionCannotExceedTheTwelveMiBTotalAndRollsBackAllMetadata() = runBlocking { fixture { f ->
        val bytes = f.imageBytes(1024, 864, noisy = true)
        assertTrue("The fixture must test the total limit rather than the individual limit",
            bytes.size.toLong() in (12L * 1024 * 1024 / 5 + 1)..(3L * 1024 * 1024))
        val large = (0..3).map { ImageFileHelper.saveGeneratedImage(f.context, bytes) }
        val id = f.add(large.first())
        large.drop(1).forEach { f.add(it) }
        val small = f.image()
        f.add(small)
        val before = f.repository.get(id)!!
        val replacement = ImageFileHelper.saveGeneratedImage(f.context, bytes)
        f.unchangedAfterRejection(before, 4, replacement)
        assertTrue(File(small).exists())
        large.forEach { assertTrue(File(it).exists()) }
    } }

    @Test fun submitVisibleRejectsTheOldImageSnapshotAfterReplacementWithoutClaimOrTransport() = runBlocking { fixture { f ->
        val editor = f.editor()
        val recognition = f.recognition()
        val id = f.add(f.image())
        val displayed = f.repository.get(id)!!
        f.repository.replaceRegion(id, 0, f.image(40, 40))
        val current = f.repository.get(id)!!
        var prepared = 0
        withContext(Dispatchers.Main) {
            recognition.submitVisible(displayed, f.settings.changes.value, RecognitionProvider.VOLCENGINE_ARK,
                editor, WardrobeFamily.WIG) { prepared++ }
        }
        withTimeout(5_000) { while (recognition.uiState.value.busy) delay(10) }
        assertTrue(recognition.uiState.value.error.orEmpty().contains("识图图片或状态已变化"))
        assertEquals(current, f.repository.get(id))
        assertFalse(f.repository.get(id)!!.requestSent)
        assertEquals(0, prepared)
        assertEquals(0, f.keyReads.get())
        assertEquals(0, f.transportCalls.get())
    } }

    @Test fun submitVisibleRejectsChangedProviderAndKeyRevisionBeforeClaimOrTransport() = runBlocking { fixture { f ->
        val editor = f.editor()
        val recognition = f.recognition()
        val id = f.add(f.image())
        val displayed = f.repository.get(id)!!
        var prepared = 0
        for (changeProvider in listOf(true, false)) {
            f.settings.selectProvider(RecognitionProvider.VOLCENGINE_ARK)
            val displayedRevision = f.settings.changes.value
            if (changeProvider) f.settings.selectProvider(RecognitionProvider.DEEPSEEK)
            else f.settings.saveKey("controlled-local-rotated-key", RecognitionProvider.VOLCENGINE_ARK)
            withContext(Dispatchers.Main) {
                recognition.submitVisible(displayed, displayedRevision, RecognitionProvider.VOLCENGINE_ARK,
                    editor, WardrobeFamily.WIG) { prepared++ }
            }
            withTimeout(5_000) { while (recognition.uiState.value.busy) delay(10) }
            assertTrue(recognition.uiState.value.error.orEmpty().contains("服务或密钥已更改"))
            assertEquals(displayed, f.repository.get(id))
            assertFalse(f.repository.get(id)!!.requestSent)
        }
        assertEquals(0, prepared)
        assertEquals(0, f.keyReads.get())
        assertEquals(0, f.transportCalls.get())
    } }

    @Test fun markingAnAlreadyPreparedImageAsAiInvalidatesItsOldConfirmationWithoutChangingPixels() = runBlocking { fixture { f ->
        val editor=f.editor()
        val recognition=f.recognition()
        val id=f.add(f.image())
        val original=f.repository.get(id)!!
        val hashes=original.regionImagePaths.map(::sha)
        f.repository.markAppearanceOnly(id)
        val protected=f.repository.get(id)!!
        assertTrue(protected.request().regions.all {it.appearanceOnly})
        assertEquals(original.regionImagePaths,protected.regionImagePaths)
        assertEquals(hashes,protected.regionImagePaths.map(::sha))
        assertEquals(original.request().regions.map {it.id},protected.request().regions.map {it.id})
        withContext(Dispatchers.Main) { recognition.submitVisible(original,f.settings.changes.value,RecognitionProvider.VOLCENGINE_ARK,editor,WardrobeFamily.WIG) {error("Stale confirmation must not be accepted")} }
        withTimeout(5_000) {while(recognition.uiState.value.busy) delay(10)}
        assertNotNull(recognition.uiState.value.error)
        assertEquals(protected,f.repository.get(id))
        assertEquals(0,f.transportCalls.get())
    } }

    @Test fun aFailedTaskNeverResendsUntilAnExplicitRetryCreatesExactlyOneControlledRequest() = runBlocking { fixture { f ->
        val editor = f.editor()
        val id = f.add(f.image())
        val failed = f.repository.get(id)!!.copy(status = RecognitionStatus.FAILED, requestSent = true, error = "历史失败")
        f.db.recognitionDao().update(failed)
        val recognition = f.recognition()
        assertEquals(0, f.transportCalls.get())
        assertEquals(listOf(failed), f.db.recognitionDao().getAll())
        var preparedId: String? = null
        withContext(Dispatchers.Main) {
            recognition.submitVisible(failed, f.settings.changes.value, RecognitionProvider.VOLCENGINE_ARK,
                editor, WardrobeFamily.WIG) { preparedId = it }
        }
        withTimeout(5_000) { while (recognition.uiState.value.busy) delay(10) }
        assertNull(recognition.uiState.value.error)
        val nextId = requireNotNull(preparedId)
        assertNotEquals(id, nextId)
        val next = withTimeout(10_000) {
            while (f.repository.get(nextId)?.status != RecognitionStatus.AWAITING_REVIEW) delay(10)
            f.repository.get(nextId)!!
        }
        assertEquals(1, f.keyReads.get())
        assertEquals(1, f.transportCalls.get())
        assertEquals(2, f.db.recognitionDao().getAll().size)
        assertEquals(failed, f.repository.get(id))
        assertEquals(failed.regionImagePaths, next.regionImagePaths)
        assertEquals(failed.request().regions, next.request().regions)
        assertTrue(next.requestSent)
    } }

    @Test fun failedAndInterruptedTasksPrepareFreshLocalCropsWithoutChangingOldSnapshotsOrSending() = runBlocking {
        for(status in listOf(RecognitionStatus.FAILED,RecognitionStatus.INTERRUPTED)) fixture { f ->
            f.editor()
            val paths=listOf(f.image(24,32),f.image(40,48))
            val id=f.add(paths[0],appearanceOnly=true)
            f.add(paths[1])
            val previous=f.repository.get(id)!!.copy(status=status,requestSent=true,error="历史输入校验失败")
            f.db.recognitionDao().update(previous)
            val oldHashes=paths.map(::sha)
            val recognition=f.recognition()
            var preparedId: String?=null
            var normalized=0
            withContext(Dispatchers.Main) {
                recognition.prepareAgain(previous,normalize={ path ->
                    normalized++
                    ImageRegionCropper.crop(f.context,path,NormalizedCropRect.FULL).path
                }) { preparedId=it }
            }
            withTimeout(5_000) { while(recognition.uiState.value.busy) delay(10) }
            assertNull(recognition.uiState.value.error)
            val next=f.repository.get(requireNotNull(preparedId))!!
            assertEquals(2,normalized)
            assertNotEquals(previous.id,next.id)
            assertEquals(RecognitionStatus.PREPARED,next.status)
            assertFalse(next.requestSent)
            assertEquals(previous,f.repository.get(id))
            assertEquals(oldHashes,paths.map(::sha))
            assertEquals(previous.request().regions.map {it.id},next.request().regions.map {it.id})
            assertEquals(listOf(true,false),next.request().regions.map {it.appearanceOnly})
            assertEquals(previous.request().copy(regions=next.request().regions),next.request())
            next.regionImagePaths.forEachIndexed { index,path ->
                assertNotEquals(paths[index],path)
                assertNotEquals(oldHashes[index],sha(path))
                assertEquals(sha(path),next.request().regions[index].sha256)
                assertTrue(File(path).isFile)
            }
            assertEquals(2,f.db.recognitionDao().getAll().size)
            assertEquals(0,f.keyReads.get());assertEquals(0,f.transportCalls.get())
            // A stale displayed snapshot cannot start even local normalization.
            withContext(Dispatchers.Main) {
                recognition.prepareAgain(previous.copy(updatedAt=previous.updatedAt+1),normalize={ error("Stale input must not normalize") }) {
                    error("Stale input must not prepare")
                }
            }
            withTimeout(5_000) { while(recognition.uiState.value.busy) delay(10) }
            assertNotNull(recognition.uiState.value.error)
            assertEquals(2,f.db.recognitionDao().getAll().size)
            assertEquals(previous,f.repository.get(id))
        }
    }

    @Test fun failedLocalRepreparationWithdrawsOnlyTheNewTaskAndAllItsUnreferencedCopies() = runBlocking { fixture { f ->
        f.editor()
        val paths=listOf(f.image(24,32),f.image(40,48))
        val id=f.add(paths[0],appearanceOnly=true)
        f.add(paths[1])
        val previous=f.repository.get(id)!!.copy(status=RecognitionStatus.FAILED,requestSent=true,error="历史失败")
        f.db.recognitionDao().update(previous)
        val oldHashes=paths.map(::sha)
        val created=mutableListOf<String>()
        val recognition=f.recognition()
        var prepared=0
        withContext(Dispatchers.Main) {
            recognition.prepareAgain(previous,normalize={ path ->
                val output=if(created.isEmpty()) ImageRegionCropper.crop(f.context,path,NormalizedCropRect.FULL).path else
                    File(File(f.context.filesDir,"images"),"${UUID.randomUUID()}.jpg").apply {writeText("invalid normalized crop")}.path
                created.add(output)
                output
            }) {prepared++}
        }
        withTimeout(5_000) { while(recognition.uiState.value.busy) delay(10) }
        assertNotNull(recognition.uiState.value.error)
        assertEquals(0,prepared)
        assertEquals(listOf(previous),f.db.recognitionDao().getAll())
        assertEquals(oldHashes,paths.map(::sha))
        assertEquals(2,created.size)
        created.forEach {assertFalse("Failed local copies must not leak",File(it).exists())}
        assertEquals(0,f.keyReads.get());assertEquals(0,f.transportCalls.get())
    } }
}
