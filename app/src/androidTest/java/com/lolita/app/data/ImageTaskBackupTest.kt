package com.lolita.app.data

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.lolita.app.data.file.*
import com.lolita.app.data.generation.*
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class ImageTaskBackupTest {
    @Test fun physicalTryOnFrozenDescriptionSurvivesExecutionAndZipRestore() = runBlocking { fixture { f ->
        val item = f.db.itemDao().getItemById(f.itemId)!!
        val category = f.db.categoryDao().getCategoryById(item.categoryId)!!.copy(categorySemantic = CategorySemantic.JSK)
        f.db.categoryDao().updateCategory(category)
        f.db.itemDao().updateItem(item.copy(status = ItemStatus.OWNED))
        val coordinator = ImageTaskCoordinator(f.db, f.scope, {},
            { ImageFileHelper.saveGeneratedImage(f.context, it) }, ImageFileHelper::deleteImage,
            { ImageFileHelper.isOwnedGeneratedImage(f.context, it) },
            snapshotBuiltInImage = { ImageFileHelper.copyAssetToInternalStorage(f.context, it) },
            validateSourceImage = { File(it).isFile })
        coordinator.awaitStartup()
        val description = "BNT 戴靠后，露出刘海；衬衫掖进裙腰。"
        val id = coordinator.submitTryOn(listOf(TryOnSelection(f.itemId, item.name, f.source, TryOnRole.MAIN, CategorySemantic.JSK)), ImageGenerationProvider.SEEDREAM, description)
        val frozen = ImageTaskRequest.decode(f.db.imageTaskDao().get(id)!!.requestJson)
        assertEquals(2, frozen.promptVersion)
        assertEquals(description, frozen.instructions)
        val emptyId = coordinator.submitTryOn(listOf(TryOnSelection(f.itemId, item.name, f.source, TryOnRole.MAIN)), ImageGenerationProvider.SEEDREAM, "")
        val empty = ImageTaskRequest.decode(f.db.imageTaskDao().get(emptyId)!!.requestJson)
        assertEquals(2, empty.promptVersion)
        assertEquals("", empty.instructions)
        coordinator.cancel(emptyId)
        var calls = 0
        val images = com.lolita.app.data.tryon.TryOnImages(f.context)
        val executor = ImageTaskExecutor(coordinator, f.db, { ImageGenerationCredentials(it, "controlled-key") },
            images::prepare, images::prepareMannequin, { _, refs, prompt ->
                calls++; assertEquals(2, refs.size); assertEquals(frozen.promptSnapshot, prompt)
                assertTrue(prompt.contains("PHYSICAL WEARING PLAN")); f.bytes
            }, prepareSnapshotImages = images::prepareSnapshots)
        // Editing current inventory cannot rewrite the queued request or its historical labels.
        f.db.itemDao().updateItem(item.copy(name = "后来改名", status = ItemStatus.OWNED))
        ImageTaskRunner(coordinator, executor::execute).runQueue()
        val task = f.db.imageTaskDao().get(id)!!
        assertEquals(ImageTaskStatus.COMPLETED, task.status)
        val history = f.db.tryOnDao().get(task.tryOnResultId!!)!!
        assertEquals(description, ImageTaskRequest.decode(history.requestSnapshotJson!!).instructions)
        val manager = BackupManager(f.context, f.db, withImageTasksPaused = { action -> coordinator.withRestorePaused(action) })
        val uri = manager.exportToZip().getOrThrow().also(f.exports::add)
        manager.importBackup(uri).getOrThrow()
        val restored = ImageTaskRequest.decode(f.db.imageTaskDao().get(id)!!.requestJson)
        assertEquals(frozen, restored)
        assertEquals(history.requestSnapshotJson, f.db.tryOnDao().get(history.id)!!.requestSnapshotJson)
        assertEquals(1, calls)
    } }

    @Test fun standaloneAccessoryJsonRoundTripStillConfirmsExactlyOnce() = runBlocking { standaloneRoundTrip(false) }
    @Test fun standaloneAccessoryZipRoundTripStillConfirmsExactlyOnce() = runBlocking { standaloneRoundTrip(true) }

    private suspend fun standaloneRoundTrip(zip: Boolean) = fixture { f ->
        val original = f.db.itemDao().getItemById(f.itemId)!!
        val category = f.db.categoryDao().getCategoryById(original.categoryId)!!.copy(name = "袜子", group = CategoryGroup.ACCESSORY)
        f.db.categoryDao().updateCategory(category)
        val second = ImageFileHelper.saveGeneratedImage(f.context, f.bytes)
        f.db.itemDao().updateItem(original.copy(name = "配套袜子", imageUrls = listOf(f.source, second, f.source)))
        val coordinator = ImageTaskCoordinator(f.db, f.scope, {},
            { ImageFileHelper.saveGeneratedImage(f.context, it) }, ImageFileHelper::deleteImage,
            { ImageFileHelper.isOwnedGeneratedImage(f.context, it) })
        coordinator.awaitStartup()
        val images = com.lolita.app.data.tryon.TryOnImages(f.context)
        var calls = 0
        val executor = ImageTaskExecutor(coordinator, f.db, { ImageGenerationCredentials(it, "controlled-key") },
            images::prepare, images::prepareMannequin,
            { _, _, _ -> error("Standalone display must use its dedicated generator") },
            prepareAccessory = images::prepareAccessory,
            generateMannequin = { _, references, prompt ->
                calls++
                assertEquals(1, references.size)
                assertTrue(prompt.contains("\"袜子\""))
                assertFalse(prompt.contains("Edit image 1, the canonical empty warm-white tailoring mannequin"))
                f.bytes
            })
        val id = coordinator.submitMannequin(MannequinRequest(f.itemId, f.source, "保留原来的一双", mannequinSubject(category)), ImageGenerationProvider.SEEDREAM)
        ImageTaskRunner(coordinator, executor::execute).runQueue()
        val pending = f.db.imageTaskDao().get(id)!!
        assertEquals(ImageTaskStatus.AWAITING_CONFIRMATION, pending.status)
        assertEquals(1, calls)
        assertEquals(MannequinSubject(category.id, "袜子", MannequinPresentation.ACCESSORY), ImageTaskRequest.decode(pending.requestJson).mannequinSubject)
        val manager = BackupManager(f.context, f.db, withImageTasksPaused = { action -> coordinator.withRestorePaused(action) })
        val uri = (if (zip) manager.exportToZip() else manager.exportToJson()).getOrThrow().also(f.exports::add)
        assertEquals(1, manager.previewBackup(uri).getOrThrow().imageTaskCount)
        manager.importBackup(uri).getOrThrow()
        val restored = f.db.imageTaskDao().get(id)!!
        assertEquals(pending.requestJson, restored.requestJson)
        assertEquals(ImageTaskStatus.AWAITING_CONFIRMATION, restored.status)
        assertArrayEquals(f.bytes, File(restored.resultImagePath!!).readBytes())
        // The paid preview is historical; later category edits cannot prevent attaching it.
        f.db.categoryDao().updateCategory(category.copy(name = "鞋"))
        val originalImages = f.db.itemDao().getItemById(f.itemId)!!.imageUrls
        assertEquals(3, originalImages.size)
        assertEquals(originalImages.first(), originalImages.last())
        assertTrue(originalImages.first() != originalImages[1])
        coordinator.confirmMannequin(id); coordinator.confirmMannequin(id)
        val item = f.db.itemDao().getItemById(f.itemId)!!
        assertEquals(originalImages + restored.resultImagePath, item.imageUrls)
        assertEquals(restored.resultImagePath, item.imageUrls.last())
        assertArrayEquals(f.bytes, File(item.imageUrls.first()).readBytes())
        assertEquals(ImageTaskStatus.COMPLETED, f.db.imageTaskDao().get(id)!!.status)
        assertEquals(1, calls)
    }

    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private inner class Fixture {
        val root = File(target.cacheDir,"image-task-backup-${UUID.randomUUID()}").apply { mkdirs() }
        var onCacheDir: () -> Unit = {}
        val context = object : ContextWrapper(target) {
            override fun getFilesDir() = File(root,"files").apply { mkdirs() }
            override fun getCacheDir() = File(root,"cache").apply { mkdirs(); onCacheDir() }
        }
        val db = Room.inMemoryDatabaseBuilder(context,LolitaDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val exports = mutableListOf<Uri>()
        lateinit var source: String
        lateinit var preview: String
        var itemId = 0L
        val bytes = ByteArrayOutputStream().use { output ->
            val image = Bitmap.createBitmap(32,48,Bitmap.Config.ARGB_8888)
            image.eraseColor(android.graphics.Color.MAGENTA)
            image.compress(Bitmap.CompressFormat.PNG,100,output); image.recycle(); output.toByteArray()
        }
        suspend fun init() {
            ImageFileHelper.configure(context,db)
            source = ImageFileHelper.saveGeneratedImage(context,bytes)
            preview = ImageFileHelper.saveGeneratedImage(context,bytes)
            val brand = db.brandDao().insertBrand(Brand(name="原品牌"))
            val category = db.categoryDao().insertCategory(Category(name="JSK"))
            itemId = db.itemDao().insertItem(Item(brandId=brand,categoryId=category,name="原裙子",description="保留备注",imageUrls=listOf(source),status=ItemStatus.OWNED))
        }
        fun task(status: ImageTaskStatus = ImageTaskStatus.AWAITING_CONFIRMATION) = ImageTask(
            UUID.randomUUID().toString(),ImageTaskKind.MANNEQUIN,ImageGenerationProvider.SEEDREAM,"原裙子人台图",
            ImageTaskRequest(listOf(ImageTaskGarment(itemId,"原裙子",TryOnRole.MAIN)),"保持腰线").encode(),
            if (status.retainsSourceImages) listOf(source) else emptyList(),status,true,
            if (status==ImageTaskStatus.AWAITING_CONFIRMATION) preview else null,null,123,124)
        suspend fun close() {
            scope.coroutineContext.job.cancelAndJoin()
            exports.forEach { context.contentResolver.delete(it,null,null) }
            db.close(); root.deleteRecursively()
            ImageFileHelper.configure(AppModule.context(),AppModule.database())
        }
    }
    private suspend fun fixture(block:suspend(Fixture)->Unit) {
        val f=Fixture(); try { f.init(); withTimeout(30_000) { block(f) } } finally { f.close() }
    }

    @Test fun zipPreservesPendingPreviewAndInterruptsActiveSnapshotWithoutRequiringDeletedResults() = runBlocking { fixture { f ->
        val temporarySource=ImageFileHelper.saveGeneratedImage(f.context,f.bytes)
        val pending=f.task(); val active=f.task(ImageTaskStatus.GENERATING).copy(sourceImagePaths=listOf(temporarySource))
        val deleted=f.task(ImageTaskStatus.COMPLETED).copy(resultImagePath="/missing/old-result.png")
        listOf(pending,active,deleted).forEach { f.db.imageTaskDao().insert(it) }
        val original=f.db.itemDao().getItemById(f.itemId)!!
        val manager=BackupManager(f.context,f.db)
        val zip=manager.exportToZip().getOrThrow().also(f.exports::add)
        val info=manager.previewBackup(zip).getOrThrow()
        assertEquals(3,info.imageTaskCount); assertEquals(3,info.imageCount)
        manager.importBackup(zip).getOrThrow()
        val restored=f.db.imageTaskDao().get(pending.id)!!
        assertEquals(ImageTaskStatus.AWAITING_CONFIRMATION,restored.status)
        assertNotEquals(pending.resultImagePath,restored.resultImagePath)
        assertArrayEquals(f.bytes,File(restored.resultImagePath!!).readBytes())
        assertEquals(pending.requestJson,restored.requestJson)
        val interrupted=f.db.imageTaskDao().get(active.id)!!
        assertEquals(ImageTaskStatus.INTERRUPTED,interrupted.status)
        assertTrue(interrupted.requestSent); assertTrue(interrupted.sourceImagePaths.isEmpty())
        assertNull(f.db.imageTaskDao().get(deleted.id)!!.resultImagePath)
        val item=f.db.itemDao().getItemById(f.itemId)!!
        assertEquals(original.copy(imageUrls=item.imageUrls),item)
        assertArrayEquals(f.bytes,File(item.imageUrls.single()).readBytes())
        assertTrue(File(pending.resultImagePath!!).exists()) // failed/old live images never overwritten
        assertTrue(File(temporarySource).exists()) // old live files remain intact
        assertEquals(5,File(f.context.filesDir,"images").listFiles()!!.size) // only two referenced restored images
    } }

    @Test fun invalidPendingImageFailsBeforePausingAndLeavesCurrentDataAndForeignFileUntouched() = runBlocking { fixture { f ->
        val foreign=File(f.root,"foreign.png").apply { writeBytes(f.bytes) }
        val bad=f.task().copy(resultImagePath=foreign.absolutePath)
        val data=BackupData(emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),imageTasks=listOf(bad))
        val file=File(f.context.cacheDir,"invalid.json").apply { writeText(Gson().toJson(data)) }
        var paused=false
        val manager=BackupManager(f.context,f.db,withImageTasksPaused={ action -> paused=true; action() })
        assertTrue(manager.importBackup(Uri.fromFile(file)).isFailure)
        assertFalse(paused)
        assertEquals("原裙子",f.db.itemDao().getItemById(f.itemId)!!.name)
        assertArrayEquals(f.bytes,foreign.readBytes())
        assertTrue(f.db.imageTaskDao().getAll().isEmpty())
    } }

    @Test fun restoreCancelsOldTransportBeforeReplacingRowsAndNeverResendsImportedTask() = runBlocking { fixture { f ->
        var calls=0; var exited=false; val entered=CompletableDeferred<Unit>()
        val coordinator=ImageTaskCoordinator(f.db,f.scope,{},
            { ImageFileHelper.saveGeneratedImage(f.context,it) },ImageFileHelper::deleteImage,
            { ImageFileHelper.isOwnedGeneratedImage(f.context,it) })
        coordinator.awaitStartup()
        val executor=ImageTaskExecutor(coordinator,f.db,
            { ImageGenerationCredentials(it,"controlled-key") },
            { listOf(byteArrayOf(1),byteArrayOf(2)) },{ listOf(byteArrayOf(1),byteArrayOf(2)) },
            { _,_,_ -> calls++; entered.complete(Unit); try { awaitCancellation() } finally { exited=true } })
        val runner=ImageTaskRunner(coordinator,executor::execute)
        val id=coordinator.submitMannequin(MannequinRequest(f.itemId,f.source,"保持腰线"),ImageGenerationProvider.SEEDREAM)
        val running=launch { runner.runQueue() }; entered.await()
        val manager=BackupManager(f.context,f.db,withImageTasksPaused={ action -> coordinator.withRestorePaused(action) })
        val zip=manager.exportToZip().getOrThrow().also(f.exports::add)
        assertFalse(exited) // export does not wait for or cancel cloud processing
        f.db.brandDao().insertBrand(Brand(name="仅当前资料"))
        manager.importBackup(zip).getOrThrow(); running.join()
        assertTrue(exited); assertEquals(1,calls)
        assertEquals(ImageTaskStatus.INTERRUPTED,f.db.imageTaskDao().get(id)!!.status)
        assertTrue(f.db.brandDao().getAllBrandsList().none { it.name=="仅当前资料" })
        runner.runQueue()
        assertEquals(1,calls); assertTrue(f.db.tryOnDao().getAll().isEmpty())
    } }

    @Test fun corruptOwnedPreviewCannotReplaceCurrentDatabase() = runBlocking { fixture { f ->
        File(f.preview).writeBytes(byteArrayOf(1,2,3))
        val bad=BackupData(emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),imageTasks=listOf(f.task()))
        val file=File(f.context.cacheDir,"corrupt.json").apply { writeText(Gson().toJson(bad)) }
        var paused=false
        val manager=BackupManager(f.context,f.db,withImageTasksPaused={ action -> paused=true; action() })
        assertTrue(manager.importBackup(Uri.fromFile(file)).isFailure)
        assertFalse(paused); assertEquals(1,f.db.itemDao().getAllItemsList().size)
        assertTrue(File(f.preview).exists())
    } }

    @Test fun leavingImportPageKeepsAcceptedRestoreAndBackupLockUntilItFinishes() = runBlocking { fixture { f ->
        val coordinator=ImageTaskCoordinator(f.db,f.scope,{},
            { ImageFileHelper.saveGeneratedImage(f.context,it) },ImageFileHelper::deleteImage,
            { ImageFileHelper.isOwnedGeneratedImage(f.context,it) })
        coordinator.awaitStartup()
        val pending=f.task(); f.db.imageTaskDao().insert(pending)
        val entered=CompletableDeferred<Unit>(); val release=CompletableDeferred<Unit>()
        val manager=BackupManager(f.context,f.db,withImageTasksPaused={ action ->
            coordinator.withRestorePaused { entered.complete(Unit); release.await(); action() }
        })
        val zip=manager.exportToZip().getOrThrow().also(f.exports::add)
        val importing=launch(Dispatchers.IO) { manager.importBackup(zip) }
        try {
            entered.await(); importing.cancel()
            assertNull("accepted restore must retain its caller's resources",withTimeoutOrNull(250) { importing.join(); true })
            val exporting=async(Dispatchers.IO) { manager.exportToZip().getOrThrow().also(f.exports::add) }
            assertNull("second backup must wait for restore",withTimeoutOrNull(250) { exporting.await() })
            release.complete(Unit); importing.join(); exporting.await()
            val restored=f.db.imageTaskDao().get(pending.id)!!
            assertNotEquals(pending.resultImagePath,restored.resultImagePath)
            assertArrayEquals(f.bytes,File(restored.resultImagePath!!).readBytes())
        } finally { release.complete(Unit); importing.cancelAndJoin() }
    } }

    @Test fun jsonRestoreKeepsImageThatIsOnlyRetainedByTheTaskBeingInterrupted() = runBlocking { fixture { f ->
        val coordinator=ImageTaskCoordinator(f.db,f.scope,{},
            { ImageFileHelper.saveGeneratedImage(f.context,it) },ImageFileHelper::deleteImage,
            { ImageFileHelper.isOwnedGeneratedImage(f.context,it) })
        coordinator.awaitStartup()
        val manager=BackupManager(f.context,f.db,withImageTasksPaused={ action -> coordinator.withRestorePaused(action) })
        val json=manager.exportToJson().getOrThrow().also(f.exports::add)
        coordinator.submitMannequin(MannequinRequest(f.itemId,f.source,"保持腰线"),ImageGenerationProvider.SEEDREAM)
        val item=f.db.itemDao().getItemById(f.itemId)!!
        f.db.itemDao().updateItem(item.copy(imageUrls=emptyList()))
        ImageFileHelper.deleteImage(f.source)
        assertTrue(File(f.source).exists()) // currently held only by the queued task
        manager.importBackup(json).getOrThrow()
        assertEquals(listOf(f.source),f.db.itemDao().getItemById(f.itemId)!!.imageUrls)
        assertArrayEquals(f.bytes,File(f.source).readBytes())
    } }

    @Test fun zipSnapshotKeepsPreviewWhileUserDiscardsItAndReleasesItAfterExport() = runBlocking { fixture { f ->
        val coordinator=ImageTaskCoordinator(f.db,f.scope,{},
            { ImageFileHelper.saveGeneratedImage(f.context,it) },ImageFileHelper::deleteImage,
            { ImageFileHelper.isOwnedGeneratedImage(f.context,it) })
        coordinator.awaitStartup()
        val pending=f.task(); f.db.imageTaskDao().insert(pending)
        val reached=CompletableDeferred<Unit>(); val release=CountDownLatch(1); val armed=AtomicBoolean(true)
        f.onCacheDir={ if (armed.getAndSet(false)) { reached.complete(Unit); check(release.await(5,TimeUnit.SECONDS)) } }
        val manager=BackupManager(f.context,f.db)
        val exporting=async(Dispatchers.IO) { manager.exportToZip().also { result -> result.getOrNull()?.let(f.exports::add) } }
        try {
            reached.await(); coordinator.discard(pending.id)
            assertTrue("snapshot must keep the discarded preview until its ZIP is copied",File(f.preview).exists())
            release.countDown()
            exporting.await().getOrThrow()
            assertFalse(File(f.preview).exists())
        } finally { release.countDown(); exporting.cancelAndJoin() }
    } }
}
