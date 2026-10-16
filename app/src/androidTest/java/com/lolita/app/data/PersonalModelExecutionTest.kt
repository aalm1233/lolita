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
class PersonalModelExecutionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private inner class Fixture {
        val db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val directory = File(context.cacheDir, "person-task-${UUID.randomUUID()}").apply { mkdirs() }
        val photo = File(directory, "face.jpg").apply { writeBytes(byteArrayOf(1)) }
        var calls = 0
        val coordinator = ImageTaskCoordinator(db, scope, {},
            { bytes -> File(directory, "${UUID.randomUUID()}.png").apply { writeBytes(bytes) }.absolutePath },
            { path ->
                val owned = db.imageTaskDao().getAll().any { path in it.protectedImagePaths() } ||
                    db.personalModelDao().getAll().any { path in it.protectedImagePaths() } ||
                    db.tryOnDao().getAll().any { it.imageUrl == path }
                if (!owned) File(path).delete()
            }, { File(it).isFile }, validateSourceImage = { File(it).isFile })
        var preparedModelPath: String? = null
        val executor = ImageTaskExecutor(coordinator, db,
            { ImageGenerationCredentials(it, "controlled-key") },
            { paths -> List(paths.size + 1) { byteArrayOf(1) } },
            { listOf(byteArrayOf(1), byteArrayOf(2)) },
            { _, _, _ -> calls++; byteArrayOf(3) },
            preparePersonalModel = { paths -> List(paths.size + 1) { byteArrayOf(1) } },
            preparePersonalTryOn = { model, paths -> preparedModelPath = model; List(paths.size + 1) { byteArrayOf(1) } },
            validateSourceImage = { File(it).isFile })
        val runner = ImageTaskRunner(coordinator, executor::execute)
        fun request() = PersonalModelRequest("本人", PersonalModelMode.PHOTO, BodyMeasurements(163.0, 52.0), listOf(PersonalModelPhotoRole.FACE))
        suspend fun submit() = coordinator.submitPersonalModel(request(), listOf(photo.absolutePath), ImageGenerationProvider.SEEDREAM)
        suspend fun close() { scope.coroutineContext.job.cancelAndJoin(); db.close(); directory.deleteRecursively() }
    }
    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val f = Fixture()
        try { f.coordinator.awaitStartup(); withTimeout(20_000) { block(f) } } finally { f.close() }
    }

    @Test fun personalPreviewKeepsOriginalAndConfirmationCreatesExactlyOneModel() = runBlocking { fixture { f ->
        val id = f.submit()
        f.runner.runQueue()
        val pending = f.db.imageTaskDao().get(id)!!
        assertEquals(ImageTaskStatus.AWAITING_CONFIRMATION, pending.status)
        assertEquals(listOf(f.photo.absolutePath), pending.sourceImagePaths)
        assertTrue(f.photo.isFile)
        assertEquals(1, f.calls)
        val first = f.coordinator.confirmPersonalModel(id)
        assertEquals(first, f.coordinator.confirmPersonalModel(id))
        val accepted = f.db.personalModelDao().get(first)!!
        assertEquals(pending.resultImagePath, accepted.imageUrl)
        assertEquals(listOf(f.photo.absolutePath), accepted.sourceImagePaths)
        assertEquals(1, f.db.personalModelDao().getAll().size)
        assertEquals(first, f.db.imageTaskDao().get(id)!!.personalModelId)
        f.coordinator.delete(id)
        assertTrue(File(accepted.imageUrl).isFile)
        assertTrue(f.photo.isFile)
    } }

    @Test fun discardReleasesPendingOutputAndItsOriginalPhotos() = runBlocking { fixture { f ->
        val id = f.submit(); f.runner.runQueue()
        val path = f.db.imageTaskDao().get(id)!!.resultImagePath!!
        f.coordinator.discard(id)
        assertEquals(ImageTaskStatus.DISCARDED, f.db.imageTaskDao().get(id)!!.status)
        assertTrue(f.db.imageTaskDao().get(id)!!.sourceImagePaths.isEmpty())
        assertFalse(File(path).exists())
        assertFalse(f.photo.exists())
    } }

    @Test fun queuedTryOnUsesCapturedImageEvenAfterPersonalModelIsDeleted() = runBlocking { fixture { f ->
        val generated = f.submit(); f.runner.runQueue()
        val modelId = f.coordinator.confirmPersonalModel(generated)
        val person = f.db.personalModelDao().get(modelId)!!
        val brand = f.db.brandDao().insertBrand(Brand(name = "测试"))
        val category = f.db.categoryDao().insertCategory(Category(name = "JSK"))
        val garmentPhoto = File(f.directory, "dress.jpg").apply { writeBytes(byteArrayOf(2)) }
        val itemId = f.db.itemDao().insertItem(Item(name = "裙子", description = "", brandId = brand,
            categoryId = category, status = ItemStatus.OWNED, imageUrls = listOf(garmentPhoto.absolutePath)))
        val task = f.coordinator.submitTryOn(listOf(TryOnSelection(itemId, "裙子", garmentPhoto.absolutePath, TryOnRole.MAIN)),
            ImageGenerationProvider.SEEDREAM, modelId)
        f.db.personalModelDao().delete(modelId)
        assertTrue(File(person.imageUrl).isFile)
        f.runner.runQueue()
        assertEquals(person.imageUrl, f.preparedModelPath)
        val savedTask = f.db.imageTaskDao().get(task)!!
        val result = f.db.tryOnDao().get(savedTask.tryOnResultId!!)!!
        assertEquals(modelId, result.personalModelId)
        assertEquals("本人", result.personalModelName)
        assertEquals(ImageTaskStatus.COMPLETED, savedTask.status)
    } }

    @Test fun restartKeepsPersonalInputsButNeverAutomaticallySubmitsAgain() = runBlocking { fixture { f ->
        val task = f.submit()
        f.db.imageTaskDao().interruptUnfinished(123, "重启")
        val interrupted = f.db.imageTaskDao().get(task)!!
        assertEquals(ImageTaskStatus.INTERRUPTED, interrupted.status)
        assertEquals(listOf(f.photo.absolutePath), interrupted.sourceImagePaths)
        assertTrue(f.photo.isFile)
        f.runner.runQueue()
        assertEquals(0, f.calls)
    } }

    @Test fun replacedModelWithSameIdCannotUseAnEarlierConfirmation() = runBlocking { fixture { f ->
        val generated = f.submit(); f.runner.runQueue()
        val modelId = f.coordinator.confirmPersonalModel(generated)
        val original = f.db.personalModelDao().get(modelId)!!
        val replacement = File(f.directory, "different-person.png").apply { writeBytes(byteArrayOf(4)) }
        f.db.personalModelDao().update(original.copy(imageUrl = replacement.absolutePath))
        val brand = f.db.brandDao().insertBrand(Brand(name = "测试"))
        val category = f.db.categoryDao().insertCategory(Category(name = "JSK"))
        val garment = File(f.directory, "dress.jpg").apply { writeBytes(byteArrayOf(2)) }
        val itemId = f.db.itemDao().insertItem(Item(name = "裙子", description = "", brandId = brand,
            categoryId = category, status = ItemStatus.OWNED, imageUrls = listOf(garment.absolutePath)))
        val before = f.db.imageTaskDao().getAll().size
        val failure = runCatching {
            f.coordinator.submitTryOn(
                listOf(TryOnSelection(itemId, "裙子", garment.absolutePath, TryOnRole.MAIN)),
                ImageGenerationProvider.SEEDREAM, modelId, original.imageUrl)
        }.exceptionOrNull()
        assertNotNull(failure)
        assertEquals(before, f.db.imageTaskDao().getAll().size)
        assertEquals(1, f.calls)
    } }
}
