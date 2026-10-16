package com.lolita.app.data

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.lolita.app.data.file.*
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PersonalModelBackupTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val f = Fixture()
        try { ImageFileHelper.configure(f.context, f.db); block(f) }
        finally {
            f.exports.forEach { f.context.contentResolver.delete(it, null, null) }
            f.db.close(); f.root.deleteRecursively()
            ImageFileHelper.configure(AppModule.context(), AppModule.database())
        }
    }
    private inner class Fixture {
        val root = File(target.cacheDir, "personal-backup-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        }
        val db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        val exports = mutableListOf<Uri>()
        val bytes = ByteArrayOutputStream().use { output ->
            val bitmap = Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.BLUE)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); bitmap.recycle(); output.toByteArray()
        }
        suspend fun image() = ImageFileHelper.saveGeneratedImage(context, bytes)
        val request = PersonalModelRequest("我的模特", PersonalModelMode.PHOTO, BodyMeasurements(162.0, 55.0), listOf(PersonalModelPhotoRole.FACE))
        fun task(source: String, preview: String?, status: ImageTaskStatus) = ImageTask(
            UUID.randomUUID().toString(), ImageTaskKind.PERSONAL_MODEL, ImageGenerationProvider.SEEDREAM,
            "我的模特", ImageTaskRequest(emptyList(), version = 2, personalModel = request).encode(),
            listOf(source), status, true, preview, createdAt = 123, updatedAt = 124)
    }

    // Dropping new arrays or using status-only retention loses accepted/draft/retry photos.
    @Test fun zipRestoresAcceptedDraftAndPendingRetryPhotosToOwnedMappedFiles() = runBlocking { fixture { f ->
        val accepted = PersonalModel(id = 12, name = "我的模特", requestJson = f.request.encode(), sourceImagePaths = listOf(f.image()), imageUrl = f.image(), provider = ImageGenerationProvider.SEEDREAM, model = "api-model", createdAt = 120, updatedAt = 121)
        val draft = PersonalModelDraft(UUID.randomUUID().toString(), PersonalModelDraftData(name = "草稿", mode = PersonalModelMode.PHOTO, heightCm = "162", weightKg = "55", photoRoles = listOf(PersonalModelPhotoRole.FACE)).encode(), listOf(f.image()), 125)
        f.db.personalModelDao().insert(accepted); f.db.personalModelDao().upsertDraft(draft)
        val pending = f.task(f.image(), f.image(), ImageTaskStatus.AWAITING_CONFIRMATION)
        val active = f.task(f.image(), null, ImageTaskStatus.GENERATING)
        val failed = f.task(f.image(), null, ImageTaskStatus.FAILED)
        listOf(pending, active, failed).forEach { f.db.imageTaskDao().insert(it) }
        val manager = BackupManager(f.context, f.db)
        val zip = manager.exportToZip().getOrThrow().also(f.exports::add)
        val preview = manager.previewBackup(zip).getOrThrow()
        assertEquals(1, preview.personalModelCount); assertEquals(1, preview.personalModelDraftCount)
        assertEquals(5, preview.totalCount)
        assertEquals(7, preview.imageCount)
        assertEquals(5, manager.importBackup(zip).getOrThrow().totalImported)
        val model = f.db.personalModelDao().get(12)!!
        assertEquals(accepted.copy(imageUrl = model.imageUrl, sourceImagePaths = model.sourceImagePaths), model)
        assertNotEquals(accepted.imageUrl, model.imageUrl)
        val restoredDraft = f.db.personalModelDao().getDraft(draft.id)!!
        assertEquals(draft.copy(sourceImagePaths = restoredDraft.sourceImagePaths), restoredDraft)
        val pendingAfter = f.db.imageTaskDao().get(pending.id)!!
        assertEquals(ImageTaskStatus.AWAITING_CONFIRMATION, pendingAfter.status)
        val activeAfter = f.db.imageTaskDao().get(active.id)!!
        assertEquals(ImageTaskStatus.INTERRUPTED, activeAfter.status)
        val failedAfter = f.db.imageTaskDao().get(failed.id)!!
        assertEquals(ImageTaskStatus.FAILED, failedAfter.status)
        val paths = model.sourceImagePaths + model.imageUrl + restoredDraft.sourceImagePaths + pendingAfter.sourceImagePaths + pendingAfter.resultImagePath!! + activeAfter.sourceImagePaths + failedAfter.sourceImagePaths
        assertEquals(7, paths.distinct().size)
        paths.forEach { assertArrayEquals(f.bytes, File(it).readBytes()) }
        assertTrue(activeAfter.requestSent)
    } }

    @Test fun jsonRestoreKeepsPersonalSourcesWithoutResumingPaidWork() = runBlocking { fixture { f ->
        val active = f.task(f.image(), null, ImageTaskStatus.GENERATING)
        f.db.imageTaskDao().insert(active)
        val manager = BackupManager(f.context, f.db)
        val json = manager.exportToJson().getOrThrow().also(f.exports::add)
        manager.importBackup(json).getOrThrow()
        val restored = f.db.imageTaskDao().get(active.id)!!
        assertEquals(ImageTaskStatus.INTERRUPTED, restored.status)
        assertEquals(active.sourceImagePaths, restored.sourceImagePaths)
        assertArrayEquals(f.bytes, File(restored.sourceImagePaths.single()).readBytes())
    } }

    @Test fun corruptPersonalSourceFailsBeforeReplacingRowsOrDeletingOldFiles() = runBlocking { fixture { f ->
        val source = f.image()
        File(source).writeBytes(byteArrayOf(1, 2, 3))
        val task = f.task(source, f.image(), ImageTaskStatus.AWAITING_CONFIRMATION)
        val data = BackupData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), imageTasks = listOf(task))
        val file = File(f.context.cacheDir, "corrupt-personal.json").apply { writeText(Gson().toJson(data)) }
        var paused = false
        val manager = BackupManager(f.context, f.db, withImageTasksPaused = { action -> paused = true; action() })
        assertTrue(manager.importBackup(Uri.fromFile(file)).isFailure)
        assertFalse(paused); assertTrue(File(source).exists()); assertTrue(File(task.resultImagePath!!).exists())
    } }

    @Test fun sameFilenameFromDifferentSourcesGetsSeparateArchiveEntriesAndRestoredPaths() = runBlocking { fixture { f ->
        val first = File(f.root, "first/photo.png").apply { parentFile!!.mkdirs(); writeBytes(f.bytes) }
        val otherBytes = ByteArrayOutputStream().use { output ->
            val bitmap = Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.YELLOW)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); bitmap.recycle(); output.toByteArray()
        }
        val second = File(f.root, "second/photo.png").apply { parentFile!!.mkdirs(); writeBytes(otherBytes) }
        val a = f.db.brandDao().insertBrand(Brand(name = "第一张", logoUrl = first.absolutePath))
        val b = f.db.brandDao().insertBrand(Brand(name = "第二张", logoUrl = second.absolutePath))
        val manager = BackupManager(f.context, f.db)
        val zip = manager.exportToZip().getOrThrow().also(f.exports::add)
        assertEquals(2, manager.previewBackup(zip).getOrThrow().imageCount)
        manager.importBackup(zip).getOrThrow()
        val restored = f.db.brandDao().getAllBrandsList().associateBy { it.id }
        val aPath = restored.getValue(a).logoUrl!!; val bPath = restored.getValue(b).logoUrl!!
        assertNotEquals(aPath, bPath)
        assertArrayEquals(f.bytes, File(aPath).readBytes())
        assertArrayEquals(otherBytes, File(bPath).readBytes())
        assertTrue(first.exists()); assertTrue(second.exists())
    } }

    @Test fun repeatedZipRestoreMapsSharedOriginalReferenceOnceAcrossProfileDraftAndPendingTask() = runBlocking { fixture { f ->
        val original = f.image()
        val profile = PersonalModel(id = 12, name = "我的模特", requestJson = f.request.encode(), sourceImagePaths = listOf(original), imageUrl = f.image(), provider = ImageGenerationProvider.SEEDREAM, model = "api-model", createdAt = 120)
        val draft = PersonalModelDraft(dataJson = PersonalModelDraftData(name = "草稿", photoRoles = listOf(PersonalModelPhotoRole.FACE)).encode(), sourceImagePaths = listOf(original))
        val task = f.task(original, f.image(), ImageTaskStatus.AWAITING_CONFIRMATION)
        f.db.personalModelDao().insert(profile); f.db.personalModelDao().upsertDraft(draft); f.db.imageTaskDao().insert(task)
        val manager = BackupManager(f.context, f.db)
        var prior = original
        repeat(2) {
            val zip = manager.exportToZip().getOrThrow().also(f.exports::add)
            assertEquals(3, manager.previewBackup(zip).getOrThrow().imageCount)
            manager.importBackup(zip).getOrThrow()
            val mapped = f.db.personalModelDao().get(12)!!.sourceImagePaths.single()
            assertNotEquals(prior, mapped)
            assertEquals(mapped, f.db.personalModelDao().getDraft(draft.id)!!.sourceImagePaths.single())
            assertEquals(mapped, f.db.imageTaskDao().get(task.id)!!.sourceImagePaths.single())
            assertArrayEquals(f.bytes, File(mapped).readBytes())
            assertTrue(File(prior).exists())
            prior = mapped
        }
    } }
}
