package com.lolita.app.ui.screen.mannequin

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.generation.ImageTaskCoordinator
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.mannequin.WigHeadTemplateCatalog
import com.lolita.app.data.tryon.ImageServiceStatus
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Real Room, assets and FileHelper; no transport or service is started. */
@RunWith(AndroidJUnit4::class)
class WigHeadDraftOwnershipTest {
    private class Fixture {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(target.cacheDir, "head-owner-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(root, "no-backup").apply { mkdirs() }
        }
        val db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val stores = mutableListOf<ViewModelStore>()
        val releases = mutableListOf<CompletableDeferred<Unit>>()
        val catalog = WigHeadTemplateCatalog(context)
        val coordinator = ImageTaskCoordinator(db, scope, {}, { error("No generated output") },
            ImageFileHelper::deleteImage, { File(it).isFile })
        lateinit var item: Item

        suspend fun setup() {
            ImageFileHelper.configure(context, db)
            coordinator.awaitStartup()
            val source = File(context.filesDir, "images/${UUID.randomUUID()}.png").apply {
                parentFile!!.mkdirs()
                outputStream().use { output ->
                    Bitmap.createBitmap(16, 24, Bitmap.Config.ARGB_8888).also {
                        it.compress(Bitmap.CompressFormat.PNG, 100, output); it.recycle()
                    }
                }
            }
            val brand = db.brandDao().insertBrand(Brand(name = "测试"))
            val category = db.categoryDao().insertCategory(Category(name = "假发", categorySemantic = CategorySemantic.FULL_WIG))
            item = Item(id = 1, brandId = brand, categoryId = category, name = "假发", description = "", status = ItemStatus.OWNED, imageUrls = listOf(source.path))
            db.itemDao().insertItem(item)
        }

        suspend fun model(handle: SavedStateHandle = SavedStateHandle(mapOf("itemId" to item.id))): Pair<MannequinViewModel, ViewModelStore> {
            val pair = withContext(Dispatchers.Main) {
                val vm = MannequinViewModel(handle, item = db.itemDao().observeItem(item.id), categories = db.categoryDao().getAllCategories(),
                    readService = { ImageServiceStatus(ImageGenerationProvider.OPENAI, true) }, selectService = {}, serviceChanges = flowOf(0),
                    saveApiKey = { _, _ -> }, clearApiKey = {}, tasks = db.imageTaskDao().observe(), readRetryTask = db.imageTaskDao()::get,
                    validateLegacy = { false }, appendLegacy = { _, _ -> },
                    discardLegacy = { path -> scope.launch { ImageFileHelper.deleteImage(path) }; Unit }, submitTask = coordinator::submitMannequin,
                    readHeadTemplates = { catalog.templates }, snapshotHead = catalog::snapshot, readHeadPreference = { null }, writeHeadPreference = {},
                    pinCropReferences = { paths ->
                        val lease = ImageFileHelper.pinImageReferences(context, paths)
                        val released = CompletableDeferred<Unit>().also { releases += it }
                        suspend { try { lease.release() } finally { released.complete(Unit) }; Unit }
                    })
                vm to ViewModelStore().also { it.put("vm", vm); stores += it }
            }
            withTimeout(10_000) { pair.first.uiState.first { !it.isLoading && !it.isLoadingHead && it.headTemplates.isNotEmpty() && it.keyLoaded } }
            return pair
        }

        suspend fun close() {
            withContext(Dispatchers.Main) { stores.forEach(ViewModelStore::clear) }
            withTimeout(10_000) { releases.forEach { it.await() } }
            scope.coroutineContext.job.cancelAndJoin()
            ImageFileHelper.configure(target, AppModule.database())
            db.close(); root.deleteRecursively()
        }
    }

    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val f = Fixture()
        try { f.setup(); withTimeout(20_000) { block(f) } } finally { f.close() }
    }

    @Test fun deletingPriorTaskCannotDeleteTheHeadSelectedByAnOpenDraft() = runBlocking { fixture { f ->
        val head = f.catalog.snapshot(f.catalog.templates.first().id)
        val subject = mannequinSubject(f.db.categoryDao().getCategoryById(f.item.categoryId))!!
        val previous = f.coordinator.submitMannequin(MannequinRequest(f.item.id, f.item.imageUrls.single(), subject = subject,
            wigHeadTemplate = head, referenceViewConfirmed = true), ImageGenerationProvider.OPENAI)
        f.db.imageTaskDao().update(f.db.imageTaskDao().get(previous)!!.copy(status = ImageTaskStatus.COMPLETED))
        val (vm, _) = f.model()
        assertEquals(head.imagePath, vm.uiState.value.selectedHeadTemplate!!.imagePath)
        f.coordinator.delete(previous)
        assertTrue("A live draft must retain the reused head after task deletion", File(head.imagePath).isFile)
        vm.uiState.value.selectedHeadTemplate!!.validateImage()
        withContext(Dispatchers.Main) { vm.confirmReferenceView(true); vm.generate() }
        val submitted = withTimeout(10_000) { vm.uiState.first { !it.isSubmitting && (it.latestTaskId != null || it.error != null) } }
        assertNotNull(submitted.latestTaskId)
        assertEquals(listOf(head.imagePath, f.item.imageUrls.single()), f.db.imageTaskDao().get(submitted.latestTaskId!!)!!.sourceImagePaths)
    } }

    @Test fun anotherDraftChangingHeadCannotDeleteSharedHeadDuringRecreation() = runBlocking { fixture { f ->
        val handle = SavedStateHandle(mapOf("itemId" to f.item.id))
        val (first, _) = f.model()
        val (second, secondStore) = f.model(handle)
        val shared = second.uiState.value.selectedHeadTemplate!!
        assertEquals(shared.imagePath, first.uiState.value.selectedHeadTemplate!!.imagePath)
        withContext(Dispatchers.Main) { secondStore.clear() }
        val (restored, _) = f.model(handle)
        withContext(Dispatchers.Main) { first.selectHeadTemplate(f.catalog.templates.last().id) }
        withTimeout(10_000) { first.uiState.first { !it.isLoadingHead && it.selectedHeadTemplate?.id == f.catalog.templates.last().id } }
        // Drain the same reference-aware cleanup used after a template change.
        ImageFileHelper.deleteImage(shared.imagePath)
        assertTrue("The recreated draft must pin the exact restored snapshot", File(shared.imagePath).isFile)
        assertEquals(shared, restored.uiState.value.selectedHeadTemplate)
        restored.uiState.value.selectedHeadTemplate!!.validateImage()
    } }

    @Test fun missingRestoredHeadKeepsItsIdentityAndBlocksSubmissionWithoutFallback() = runBlocking { fixture { f ->
        val head = f.catalog.snapshot(f.catalog.templates.last().id)
        File(head.imagePath).delete()
        val handle = SavedStateHandle(mapOf("itemId" to f.item.id,
            "mannequinHeadTemplateSnapshot" to com.google.gson.Gson().toJson(head), "mannequinReferenceViewConfirmed" to true))
        val (vm, _) = f.model(handle)
        assertEquals("A missing snapshot must not silently become the default head", head, vm.uiState.value.selectedHeadTemplate)
        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.canGenerate)
        withContext(Dispatchers.Main) { vm.generate() }
        assertTrue(f.db.imageTaskDao().getAll().isEmpty())
        assertEquals(head, WigHeadTemplateSnapshot.decode(handle["mannequinHeadTemplateSnapshot"]!!))
    } }

    @Test fun disposingTheLastDraftReleasesADeferredHeadDeletion() = runBlocking { fixture { f ->
        val (vm, store) = f.model()
        val path = vm.uiState.value.selectedHeadTemplate!!.imagePath
        ImageFileHelper.deleteImage(path)
        assertTrue("A selected head must be protected until the draft is disposed", File(path).isFile)
        withContext(Dispatchers.Main) { store.clear() }
        withTimeout(10_000) { while (File(path).exists()) delay(10) }
        assertFalse(File(path).exists())
    } }
}
