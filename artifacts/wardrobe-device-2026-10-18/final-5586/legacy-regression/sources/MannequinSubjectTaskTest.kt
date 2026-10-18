package com.lolita.app.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.generation.ImageTaskCoordinator
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
class MannequinSubjectTaskTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private inner class Fixture {
        val db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val folder = File(context.cacheDir, "subject-${UUID.randomUUID()}").apply { mkdirs() }
        val source = File(folder, "source.jpg").apply { writeBytes(byteArrayOf(1)) }
        val coordinator = ImageTaskCoordinator(db, scope, {}, { error("No image API in admission tests") }, {}, { true })
        lateinit var category: Category
        lateinit var item: Item
        val request get() = MannequinRequest(item.id, source.absolutePath, "只保留红色KC", mannequinSubject(category))
        suspend fun init() {
            coordinator.awaitStartup()
            val brandId = db.brandDao().insertBrand(Brand(name = "独立测试"))
            val categoryId = db.categoryDao().insertCategory(Category(name = "KC", group = CategoryGroup.ACCESSORY))
            category = db.categoryDao().getCategoryById(categoryId)!!
            val id = db.itemDao().insertItem(Item(name = "红色KC", description = "", brandId = brandId,
                categoryId = categoryId, imageUrls = listOf(source.absolutePath), status = ItemStatus.OWNED))
            item = db.itemDao().getItemById(id)!!
        }
        suspend fun close() { scope.coroutineContext.job.cancelAndJoin(); db.close(); folder.deleteRecursively() }
    }
    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val f = Fixture()
        try { f.init(); withTimeout(15_000) { block(f) } } finally { f.close() }
    }

    @Test fun acceptedAccessoryKeepsConfirmedCategoryAndSingleSource() = runBlocking { fixture { f ->
        val confirmed = f.request
        val id = f.coordinator.submitMannequin(confirmed, ImageGenerationProvider.SEEDREAM)
        val task = f.db.imageTaskDao().get(id)!!
        val request = ImageTaskRequest.decode(task.requestJson)
        assertEquals(3, request.version)
        assertEquals(confirmed.subject, request.mannequinSubject)
        assertEquals(MannequinPresentation.ACCESSORY, request.mannequinSubject!!.presentation)
        assertEquals(listOf(f.source.absolutePath), task.sourceImagePaths)
        assertEquals(ImageTaskStatus.QUEUED, task.status)
    } }

    @Test fun categoryRenameAfterConfirmationRejectsSubmissionWithoutQueuing() = runBlocking { fixture { f ->
        val confirmed = f.request
        f.db.categoryDao().updateCategory(f.category.copy(name = "鞋"))
        assertTrue(runCatching { f.coordinator.submitMannequin(confirmed, ImageGenerationProvider.SEEDREAM) }.isFailure)
        assertTrue(f.db.imageTaskDao().getAll().isEmpty())
    } }

    @Test fun removedImageAfterConfirmationRejectsSubmissionWithoutQueuing() = runBlocking { fixture { f ->
        val confirmed = f.request
        f.db.itemDao().updateItem(f.item.copy(imageUrls = emptyList()))
        assertTrue(runCatching { f.coordinator.submitMannequin(confirmed, ImageGenerationProvider.SEEDREAM) }.isFailure)
        assertTrue(f.db.imageTaskDao().getAll().isEmpty())
    } }

    @Test fun categoryGroupChangeWithSameNameRejectsOldStandaloneConfirmation() = runBlocking { fixture { f ->
        val confirmed = f.request
        f.db.categoryDao().updateCategory(f.category.copy(group = CategoryGroup.CLOTHING))
        assertTrue(runCatching { f.coordinator.submitMannequin(confirmed, ImageGenerationProvider.SEEDREAM) }.isFailure)
        assertTrue(f.db.imageTaskDao().getAll().isEmpty())
    } }
}
