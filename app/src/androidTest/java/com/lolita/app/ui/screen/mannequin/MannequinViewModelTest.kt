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
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.tryon.ImageServiceStatus
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** Real Room and coordinator; only credentials, service startup and controlled image bytes are injected. */
@RunWith(AndroidJUnit4::class)
class MannequinViewModelTest {
    private class Fixture : AutoCloseable {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        val root = File(target.cacheDir, "mannequin-vm-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) { override fun getFilesDir() = File(root, "files").apply { mkdirs() } }
        val db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        val repo = ItemRepository(db.itemDao(), database = db, context = context)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val coordinator = ImageTaskCoordinator(db, scope, {}, { ImageFileHelper.saveGeneratedImage(context, it) }, {}, { File(it).isFile },
            snapshotBuiltInImage = { ImageFileHelper.copyAssetToInternalStorage(context, it) })
        val stores = mutableListOf<ViewModelStore>()
        var key = true
        var calls = 0
        var retryReader: suspend (String) -> ImageTask? = db.imageTaskDao()::get
        lateinit var item: Item
        var submit: suspend (MannequinRequest, ImageGenerationProvider) -> String = { request, provider -> coordinator.submitMannequin(request, provider) }
        var append: suspend (Long, String) -> Unit = repo::appendGeneratedImage
        suspend fun setup() {
            ImageFileHelper.configure(context, db)
            coordinator.awaitStartup()
            db.brandDao().insertBrand(Brand(id = 1, name = "测试品牌"))
            db.categoryDao().insertCategory(Category(id = 1, name = "OP", categorySemantic = CategorySemantic.OP))
            item = Item(id = 11, name = "心愿裙", brandId = 1, categoryId = 1, description = "私人备注", status = ItemStatus.WISHED,
                imageUrls = listOf(image(), image()))
            db.itemDao().insertItem(item)
        }
        suspend fun image() = ImageFileHelper.saveGeneratedImage(context, bytes())
        fun bytes() = ByteArrayOutputStream().use { out ->
            Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888).also { b -> b.compress(Bitmap.CompressFormat.PNG, 100, out); b.recycle() }
            out.toByteArray()
        }
        suspend fun model(handle: SavedStateHandle = SavedStateHandle(mapOf("itemId" to 11L)), waitReady: Boolean = true): Pair<MannequinViewModel, ViewModelStore> {
            val store = ViewModelStore().also { stores += it }
            val vm = withContext(Dispatchers.Main) {
                MannequinViewModel(savedStateHandle = handle, item = db.itemDao().observeItem(11), categories = db.categoryDao().getAllCategories(),
                    readService = { ImageServiceStatus(ImageGenerationProvider.OPENAI, key) }, selectService = {}, serviceChanges = flowOf(0),
                    saveApiKey = { _, _ -> key = true }, clearApiKey = { key = false }, tasks = db.imageTaskDao().observe(), readRetryTask = { retryReader(it) },
                    submitTask = { request, provider -> calls++; submit(request, provider) },
                    appendLegacy = { id, path -> append(id, path) }, validateLegacy = { ImageFileHelper.isOwnedGeneratedImage(context, it) }, discardLegacy = {}).also { store.put("vm", it) }
            }
            if (waitReady) withTimeout(10_000) { vm.uiState.first { !it.isLoading && it.keyLoaded } }
            return vm to store
        }
        override fun close() {
            instrumentation.runOnMainSync { stores.forEach { it.clear() } }
            scope.cancel()
            instrumentation.waitForIdleSync()
            ImageFileHelper.configure(target, AppModule.database())
            db.close(); root.deleteRecursively()
        }
    }
    private suspend fun main(action: () -> Unit) = withContext(Dispatchers.Main) { action() }
    private suspend fun ready(vm: MannequinViewModel, condition: (MannequinUiState) -> Boolean) = withTimeout(10_000) { vm.uiState.first(condition) }

    @Test fun accessorySubmissionUsesCurrentCategorySnapshotRatherThanLegacyDressDefault() = runBlocking {
        Fixture().use { f ->
            f.setup()
            f.db.categoryDao().updateCategory(Category(id = 1, name = "KC", group = CategoryGroup.ACCESSORY, categorySemantic = CategorySemantic.KC))
            val (vm, _) = f.model()
            main { vm.generate() }
            val final = ready(vm) { !it.isSubmitting && (it.latestTaskId != null || it.error != null) }
            assertNotNull("Standalone accessory must enqueue with its subject", final.latestTaskId)
            val request = ImageTaskRequest.decode(f.db.imageTaskDao().get(final.latestTaskId!!)!!.requestJson)
            assertEquals(MannequinSubject(1, "KC", MannequinPresentation.ACCESSORY, CategorySemantic.KC), request.mannequinSubject)
            assertEquals(listOf(f.item.imageUrls.first()), f.db.imageTaskDao().get(final.latestTaskId)!!.sourceImagePaths)
        }
    }

    @Test fun confirmedBlouseSubmitsTwoTorsoInputsInEitherLegacyGroup() = runBlocking {
        for (group in CategoryGroup.entries) Fixture().use { f ->
            f.setup()
            f.db.categoryDao().updateCategory(Category(id = 1, name = "自定义衬衫", group = group, categorySemantic = CategorySemantic.BLOUSE))
            val (vm, _) = f.model()
            assertNull(vm.uiState.value.selectionIssue)
            assertTrue(vm.uiState.value.canGenerate)
            main { vm.generate() }
            val final = ready(vm) { !it.isSubmitting && (it.latestTaskId != null || it.error != null) }
            assertNotNull("A confirmed blouse must enqueue its own torso presentation", final.latestTaskId)
            val task = f.db.imageTaskDao().get(final.latestTaskId!!)!!
            val request = ImageTaskRequest.decode(task.requestJson)
            assertEquals(4, request.version)
            assertEquals(MannequinSubject(1, "自定义衬衫", MannequinPresentation.DRESS_FORM, CategorySemantic.BLOUSE), request.mannequinSubject)
            assertEquals(2, task.sourceImagePaths.size)
            assertEquals(f.item.imageUrls.first(), task.sourceImagePaths.last())
            assertTrue(File(task.sourceImagePaths.first()).isFile)
            assertTrue(request.promptSnapshot!!.contains("only the blouse"))
            assertFalse(task.requestSent)
            assertEquals(1, f.calls)
        }
    }

    @Test fun aBlouseConfirmationCannotSubmitAfterItsStableCategoryChanges() = runBlocking {
        Fixture().use { f ->
            f.setup()
            f.db.categoryDao().updateCategory(Category(id = 1, name = "衬衫", categorySemantic = CategorySemantic.BLOUSE))
            val (vm, _) = f.model()
            val confirmed = requireNotNull(vm.uiState.value.generationRequest)
            f.db.categoryDao().updateCategory(Category(id = 1, name = "KC", group = CategoryGroup.ACCESSORY, categorySemantic = CategorySemantic.KC))
            ready(vm) { it.subject?.semantic == CategorySemantic.KC }
            main { vm.generate(confirmedRequest = confirmed, confirmedKeyRevision = vm.uiState.value.keyRevision) }
            assertEquals(0, f.calls)
            assertTrue(f.db.imageTaskDao().getAll().isEmpty())
            assertTrue(vm.uiState.value.error.orEmpty().contains("分类或说明已改变"))
        }
    }

    @Test fun selectedPhotoInstructionsAndLatestTaskPersistWithoutOwningPreview() = runBlocking {
        Fixture().use { f ->
            f.setup(); val handle = SavedStateHandle(mapOf("itemId" to 11L)); val (vm, store) = f.model(handle)
            main { vm.selectImage(f.item.imageUrls[1]); vm.updateInstructions("排除独立手袖"); vm.generate(); vm.generate() }
            val submitted = ready(vm) { it.latestTaskId != null && !it.isSubmitting }
            assertEquals(1, f.calls)
            val id = submitted.latestTaskId!!
            val task = f.db.imageTaskDao().get(id)!!
            assertEquals(2, task.sourceImagePaths.size)
            assertEquals(f.item.imageUrls[1], task.sourceImagePaths.last())
            assertTrue(File(task.sourceImagePaths.first()).isFile)
            assertEquals("排除独立手袖", ImageTaskRequest.decode(task.requestJson).instructions)
            main { store.clear() }
            f.coordinator.runNext({ active -> f.coordinator.beforeRequest(active.id); f.coordinator.publishMannequin(active.id, f.bytes()) }, {})
            val preview = f.db.imageTaskDao().get(id)!!.resultImagePath!!
            val restarted = ImageTaskCoordinator(f.db, f.scope, {}, { error("no automatic paid work") }, {}, { true })
            restarted.awaitStartup()
            assertEquals(ImageTaskStatus.AWAITING_CONFIRMATION, f.db.imageTaskDao().get(id)!!.status)
            assertTrue(File(preview).isFile)
            val (restored, _) = f.model(handle)
            assertEquals(id, restored.uiState.value.latestTaskId)
            assertEquals(f.item.imageUrls[1], restored.uiState.value.selectedImage)
            assertEquals("排除独立手袖", restored.uiState.value.instructions)
            assertTrue(restored.uiState.value.canGenerate)
        }
    }
    @Test fun acceptedSubmissionSurvivesClearingItsWaiter() = runBlocking {
        Fixture().use { f ->
            f.setup(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            f.submit = { request, provider -> f.scope.async { entered.complete(Unit); release.await(); f.coordinator.submitMannequin(request, provider) }.await() }
            val (vm, store) = f.model(); main { vm.generate() }; entered.await(); main { store.clear() }
            release.complete(Unit)
            withTimeout(10_000) { f.db.imageTaskDao().observe().first { it.size == 1 } }
            assertEquals(1, f.calls)
        }
    }
    @Test fun staleSourceDeletedItemAndMissingKeyRejectSubmissionAndSecretsStayOutOfSavedState() = runBlocking {
        Fixture().use { f ->
            f.setup(); f.key = false
            val handle = SavedStateHandle(mapOf("itemId" to 11L)); val (vm, _) = f.model(handle)
            main { vm.generate(); vm.saveKey("sk-private-not-saved-state") }
            ready(vm) { it.hasKey && !it.isConfiguring }
            assertFalse(handle.keys().any { handle.get<Any>(it).toString().contains("sk-private") })
            f.db.itemDao().updateItem(f.item.copy(imageUrls = f.item.imageUrls.drop(1)))
            ready(vm) { it.item?.imageUrls?.size == 1 }
            assertFalse(vm.uiState.value.canGenerate)
            main { vm.generate(); vm.selectImage(f.item.imageUrls[1]); vm.updateInstructions("x".repeat(201)) }
            assertEquals(200, vm.uiState.value.instructions.length)
            f.db.itemDao().deleteItem(f.item); ready(vm) { it.item == null }; main { vm.generate() }
            assertEquals(0, f.calls)
        }
    }
    @Test fun retryUsesCurrentPhotoAndRetainsInstructionsWithoutEnqueuing() = runBlocking {
        Fixture().use { f ->
            f.setup()
            f.db.imageTaskDao().insert(ImageTask("retry-mannequin", ImageTaskKind.MANNEQUIN, ImageGenerationProvider.OPENAI, "原任务",
                ImageTaskRequest(listOf(ImageTaskGarment(11, "旧名称", TryOnRole.MAIN)), "排除独立手袖").encode(), emptyList(), status = ImageTaskStatus.FAILED))
            val (vm, _) = f.model(SavedStateHandle(mapOf("itemId" to 11L, "retryTaskId" to "retry-mannequin")))
            assertEquals(f.item.imageUrls.first(), vm.uiState.value.selectedImage)
            assertEquals("排除独立手袖", vm.uiState.value.instructions)
            assertEquals(0, f.calls)
            assertEquals(ImageTaskStatus.FAILED, f.db.imageTaskDao().get("retry-mannequin")!!.status)
        }
    }

    @Test fun restoredRetryKeepsEditedAndExplicitlyClearedInstructionsWithoutResubmitting() = runBlocking {
        Fixture().use { f ->
            f.setup()
            f.db.imageTaskDao().insert(ImageTask("retry-edited", ImageTaskKind.MANNEQUIN, ImageGenerationProvider.OPENAI, "原任务",
                ImageTaskRequest(listOf(ImageTaskGarment(11, "旧名称", TryOnRole.MAIN)), "最初说明").encode(), emptyList(), status = ImageTaskStatus.FAILED))
            var handle = SavedStateHandle(mapOf("itemId" to 11L, "retryTaskId" to "retry-edited"))
            var (vm, owner) = f.model(handle)
            assertEquals("最初说明", vm.uiState.value.instructions)
            for (edited in listOf("用户核对后的新说明", "")) {
                main { vm.updateInstructions(edited); vm.selectImage(f.item.imageUrls[1]); owner.clear() }
                handle = SavedStateHandle(handle.keys().associateWith { key -> handle.get<Any?>(key) })
                val restored = f.model(handle)
                vm = restored.first; owner = restored.second
                assertEquals("重建不得把用户草稿覆盖回原任务说明", edited, vm.uiState.value.instructions)
                assertEquals(f.item.imageUrls[1], vm.uiState.value.selectedImage)
                assertEquals(0, f.calls)
            }
        }
    }

    @Test fun retryDraftCannotBeEditedBeforeItsInstructionsFinishLoading() = runBlocking {
        Fixture().use { f ->
            f.setup(); f.retryReader = { awaitCancellation() }
            val (vm, _) = f.model(SavedStateHandle(mapOf("itemId" to 11L, "retryTaskId" to "loading-task")), waitReady = false)
            ready(vm) { it.keyLoaded }
            assertTrue(vm.uiState.value.isLoading)
            main { vm.updateInstructions("不应被稍后载入的旧说明覆盖") }
            assertEquals("", vm.uiState.value.instructions)
        }
    }

    @Test fun providerMismatchRejectsAndSubmissionFailureNeverAutomaticallyRetries() = runBlocking {
        Fixture().use { f ->
            f.setup(); val (vm, _) = f.model()
            main { vm.generate(ImageGenerationProvider.SEEDREAM) }; assertEquals(0, f.calls)
            f.submit = { _, _ -> error("controlled failure") }; main { vm.generate() }
            ready(vm) { !it.isSubmitting && it.error != null }; assertEquals(1, f.calls)
        }
    }
    @Test fun legacyPreviewRetainsFileAcrossOwnerClearAndExplicitSaveMergesLatestItemOnce() = runBlocking {
        Fixture().use { f ->
            f.setup(); val path = f.image(); val handle = SavedStateHandle(mapOf("itemId" to 11L, "mannequinPreview" to path, "mannequinPreviewItem" to 11L))
            val (vm, store) = f.model(handle); assertEquals(path, vm.uiState.value.legacyPreviewPath)
            main { store.clear() }; assertTrue(File(path).isFile)
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); val committed = CompletableDeferred<Unit>()
            f.append = { id, image -> entered.complete(Unit); release.await(); f.repo.appendGeneratedImage(id, image); committed.complete(Unit) }
            val (restored, secondStore) = f.model(handle)
            main { restored.saveLegacyPreview(); restored.saveLegacyPreview() }; entered.await()
            f.db.itemDao().updateItem(f.item.copy(name = "最新裙名"))
            main { secondStore.clear() }; release.complete(Unit); withTimeout(10_000) { committed.await() }
            assertEquals("最新裙名", f.db.itemDao().getItemById(11)!!.name)
            assertEquals(f.item.imageUrls + path, f.db.itemDao().getItemById(11)!!.imageUrls)
            assertEquals(0, f.calls)
        }
    }
    @Test fun rejectedLegacyExternalCorruptAliasesAndWrongOwnerRemainUntouched() = runBlocking {
        Fixture().use { f ->
            f.setup(); val valid = f.image(); val original = File(f.item.imageUrls.first())
            val external = File(f.root, "unrelated.png").apply { writeBytes(File(valid).readBytes()) }
            val corrupt = File(File(valid).parentFile, "broken.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val alias = "${original.parent}/../images/${original.name}"
            for ((path, owner) in listOf(external.path to 11L, corrupt.path to 11L, alias to 11L, valid to 12L)) {
                val handle = SavedStateHandle(mapOf("itemId" to 11L, "mannequinPreview" to path, "mannequinPreviewItem" to owner))
                val (vm, store) = f.model(handle); assertNull(vm.uiState.value.legacyPreviewPath)
                main { vm.discardLegacyPreview(); store.clear() }; assertTrue(File(path).exists())
                assertEquals(path, handle.get<String>("mannequinPreview"))
            }
        }
    }
}
