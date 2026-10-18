package com.lolita.app.data

import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.generation.ImageTaskCoordinator
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ImageTaskAdmissionCleanupTest {
    private class Fixture {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(target.cacheDir, "admission-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) { override fun getFilesDir() = File(root, "files").apply { mkdirs() } }
        val db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val copied = mutableListOf<String>()
        var validate: suspend (String) -> Boolean = { File(it).isFile }
        var snapshot: suspend (String) -> String = { asset -> ImageFileHelper.copyAssetToInternalStorage(context, asset).also { copied += it } }
        val coordinator = ImageTaskCoordinator(db, scope, {}, { error("No output") }, ImageFileHelper::deleteImage,
            { true }, validateSourceImage = { validate(it) }, snapshotBuiltInImage = { snapshot(it) })
        lateinit var item: Item
        lateinit var source: File
        suspend fun setup() {
            ImageFileHelper.configure(context, db)
            coordinator.awaitStartup()
            source = File(context.filesDir, "images/${UUID.randomUUID()}.png").apply { parentFile!!.mkdirs(); writeText("controlled original input") }
            val brand = db.brandDao().insertBrand(Brand(name = "测试"))
            val category = db.categoryDao().insertCategory(Category(name = "裙子", categorySemantic = CategorySemantic.JSK))
            item = Item(id = 1, brandId = brand, categoryId = category, name = "裙子", description = "", status = ItemStatus.OWNED, imageUrls = listOf(source.path))
            db.itemDao().insertItem(item)
        }
        suspend fun tryOn() = coordinator.submitTryOn(listOf(TryOnSelection(item.id, item.name, source.path, TryOnRole.MAIN, CategorySemantic.JSK, ItemComposition.SINGLE)), ImageGenerationProvider.OPENAI)
        suspend fun mannequin() = coordinator.submitMannequin(MannequinRequest(item.id, source.path,
            subject = mannequinSubject(db.categoryDao().getCategoryById(item.categoryId))), ImageGenerationProvider.OPENAI)
        suspend fun close() {
            scope.coroutineContext.job.cancelAndJoin()
            ImageFileHelper.configure(target, AppModule.database())
            db.close(); root.deleteRecursively()
        }
    }
    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val f = Fixture()
        try { f.setup(); withTimeout(15_000) { block(f) } } finally { f.close() }
    }

    @Test fun missingProductDuringHashingRemovesOnlyTheNewDressFormCopy() = runBlocking { fixture { f ->
        f.source.delete()
        assertTrue(runCatching { f.mannequin() }.isFailure)
        assertEquals(1, f.copied.size)
        assertFalse("Failed admission must remove its new dress-form copy", File(f.copied.single()).exists())
        assertTrue(f.db.imageTaskDao().getAll().isEmpty())
        assertEquals(listOf(f.source.path), f.db.itemDao().getItemById(f.item.id)!!.imageUrls)
    } }

    @Test fun validationFailureRemovesNewBuiltInPersonButKeepsTheProduct() = runBlocking { fixture { f ->
        f.validate = { it != f.source.path }
        assertTrue(runCatching { f.tryOn() }.isFailure)
        assertEquals(1, f.copied.size)
        assertFalse(File(f.copied.single()).exists())
        assertTrue(f.source.isFile)
        assertTrue(f.db.imageTaskDao().getAll().isEmpty())
    } }

    @Test fun insertRollbackRemovesStagedInputWithoutRemovingOriginals() = runBlocking { fixture { f ->
        f.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_new_image_task BEFORE INSERT ON image_tasks BEGIN SELECT RAISE(ABORT, 'controlled insert failure'); END")
        assertTrue(runCatching { f.mannequin() }.isFailure)
        assertEquals(1, f.copied.size)
        assertFalse(File(f.copied.single()).exists())
        assertTrue(f.source.isFile)
        assertTrue(f.db.imageTaskDao().getAll().isEmpty())
    } }

    @Test fun cancelledAdmissionCleansStagedInputInNonCancellableContext() = runBlocking { fixture { f ->
        f.validate = { throw CancellationException("controlled cancellation after staging") }
        assertTrue(runCatching { f.tryOn() }.exceptionOrNull() is CancellationException)
        assertEquals(1, f.copied.size)
        assertFalse(File(f.copied.single()).exists())
        assertTrue(f.source.isFile)
        assertTrue(f.db.imageTaskDao().getAll().isEmpty())
    } }

    @Test fun failedAdmissionCannotDeleteAnExistingSharedReference() = runBlocking { fixture { f ->
        f.snapshot = { f.source.path }
        f.validate = { false }
        assertTrue(runCatching { f.mannequin() }.isFailure)
        assertTrue("Reference-aware cleanup must retain an existing Item file", f.source.isFile)
        assertTrue(f.db.imageTaskDao().getAll().isEmpty())
    } }

    @Test fun committedTaskRetainsTheStagedInputUntilExplicitTaskDeletion() = runBlocking { fixture { f ->
        val id = f.tryOn()
        assertEquals(1, f.copied.size)
        val staged = f.copied.single()
        assertTrue(File(staged).isFile)
        assertEquals(listOf(staged, f.source.path), f.db.imageTaskDao().get(id)!!.sourceImagePaths)
        f.coordinator.cancel(id)
        assertTrue(File(staged).isFile)
        f.coordinator.delete(id)
        assertFalse(File(staged).exists())
        assertTrue(f.source.isFile)
    } }
}
