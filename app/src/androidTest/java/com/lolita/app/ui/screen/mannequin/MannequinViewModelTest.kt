package com.lolita.app.ui.screen.mannequin

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.tryon.TryOnGenerationPhase
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.MannequinRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** A missing busy/preview guard would pay twice; missing save ownership would delete a committed photo. */
@RunWith(AndroidJUnit4::class)
class MannequinViewModelTest {
    private class Fixture : AutoCloseable {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        val root = File(target.cacheDir, "mannequin-vm-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) { override fun getFilesDir() = File(root, "files").apply { mkdirs() } }
        val db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        val repo = ItemRepository(db.itemDao(), database = db, context = context)
        val stores = mutableListOf<ViewModelStore>()
        val global = MutableStateFlow(false)
        var key = true
        var calls = 0
        lateinit var item: Item
        var generation: suspend (MannequinRequest, (TryOnGenerationPhase) -> Unit) -> String = { _, _ -> image() }
        var append: suspend (Long, String) -> Unit = repo::appendGeneratedImage
        suspend fun setup() {
            ImageFileHelper.configure(context, db)
            db.brandDao().insertBrand(Brand(id = 1, name = "测试品牌"))
            db.categoryDao().insertCategory(Category(id = 1, name = "OP"))
            item = Item(id = 11, name = "心愿裙", brandId = 1, categoryId = 1, description = "私人备注", status = ItemStatus.WISHED,
                imageUrls = listOf(image(), image()))
            db.itemDao().insertItem(item)
        }
        suspend fun image(): String {
            val bytes = ByteArrayOutputStream().use { out ->
                Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888).also { b -> b.compress(Bitmap.CompressFormat.PNG, 100, out); b.recycle() }
                out.toByteArray()
            }
            return ImageFileHelper.saveGeneratedImage(context, bytes)
        }
        suspend fun model(handle: SavedStateHandle = SavedStateHandle(mapOf("itemId" to 11L))): Pair<MannequinViewModel, ViewModelStore> {
            val store = ViewModelStore().also { stores += it }
            val vm = withContext(Dispatchers.Main) {
                MannequinViewModel(savedStateHandle = handle, item = db.itemDao().observeItem(11),
                    categories = db.categoryDao().getAllCategories(), hasApiKey = { key }, saveApiKey = { key = true }, clearApiKey = { key = false },
                    globalGenerating = global, generateImage = { request, phase -> calls++; generation(request, phase) },
                    appendImage = { id, path -> append(id, path) }, validatePreview = { ImageFileHelper.isOwnedGeneratedImage(context, it) },
                    discardImage = { ImageFileHelper.cleanupUnreferencedImages(listOf(it)); Unit }).also { store.put("vm", it) }
            }
            withTimeout(10_000) { vm.uiState.first { !it.isLoading && it.keyLoaded } }
            return vm to store
        }
        override fun close() {
            instrumentation.runOnMainSync { stores.forEach { it.clear() } }
            instrumentation.waitForIdleSync()
            ImageFileHelper.configure(target, AppModule.database())
            db.close(); root.deleteRecursively()
        }
    }
    private suspend fun main(action: () -> Unit) = withContext(Dispatchers.Main) { action() }
    private suspend fun ready(vm: MannequinViewModel, condition: (MannequinUiState) -> Boolean) = withTimeout(10_000) { vm.uiState.first(condition) }

    @Test fun chosenCurrentPhotoAndInstructionsPersistAndPendingPreviewPreventsAnotherPaidRequest() = runBlocking {
        Fixture().use { f ->
            f.setup(); val handle = SavedStateHandle(mapOf("itemId" to 11L)); val (vm, _) = f.model(handle)
            assertTrue(vm.uiState.value.canGenerate)
            val pending = CompletableDeferred<String>()
            f.generation = { request, phase ->
                assertEquals(f.item.imageUrls[1], request.imagePath); assertEquals("排除独立手袖", request.instructions)
                phase(TryOnGenerationPhase.GENERATING); pending.await()
            }
            main { vm.selectImage(f.item.imageUrls[1]); vm.updateInstructions("排除独立手袖"); vm.generate(); vm.generate(); vm.selectImage(f.item.imageUrls[0]) }
            ready(vm) { it.phase == TryOnGenerationPhase.GENERATING }
            assertEquals(1, f.calls)
            val preview = f.image(); pending.complete(preview)
            ready(vm) { it.previewPath == preview && !it.isGenerating }
            main { vm.generate() }; assertEquals(1, f.calls)
            val (restored, _) = f.model(handle)
            assertEquals(preview, restored.uiState.value.previewPath)
            assertEquals(f.item.imageUrls[1], restored.uiState.value.selectedImage)
            assertEquals("排除独立手袖", restored.uiState.value.instructions)
            main { restored.discardPreview() }
            withTimeout(10_000) { while (File(preview).exists()) delay(20) }
            assertEquals(f.item.imageUrls, f.db.itemDao().getItemById(11)!!.imageUrls)
        }
    }
    @Test fun staleReferenceDeletionAndGlobalBusyDisablePaidRequestsAndMissingKeyStaysOutOfSavedState() = runBlocking {
        Fixture().use { f ->
            f.setup(); f.key = false
            val handle = SavedStateHandle(mapOf("itemId" to 11L)); val (vm, _) = f.model(handle)
            main { vm.generate(); vm.saveKey("sk-private-not-saved-state") }
            ready(vm) { it.hasKey && !it.isConfiguring }
            assertFalse(handle.keys().any { handle.get<Any>(it).toString().contains("sk-private") })
            f.global.value = true; ready(vm) { it.globalGenerating }; main { vm.generate() }; assertEquals(0, f.calls)
            f.global.value = false; ready(vm) { !it.globalGenerating }
            f.db.itemDao().updateItem(f.item.copy(imageUrls = f.item.imageUrls.drop(1)))
            ready(vm) { it.item?.imageUrls?.size == 1 }
            assertFalse(vm.uiState.value.canGenerate)
            main { vm.selectImage(f.item.imageUrls[1]); vm.updateInstructions("x".repeat(201)) }
            assertTrue(vm.uiState.value.instructions.length <= 200)
            f.db.itemDao().deleteItem(f.item); ready(vm) { it.item == null }
            main { vm.generate(); vm.savePreview() }; assertEquals(0, f.calls)
        }
    }
    @Test fun saveContinuesAfterOwnerClearedAndAppendsOnceWithoutLosingLatestFields() = runBlocking {
        Fixture().use { f ->
            f.setup(); val preview = f.image(); f.generation = { _, _ -> preview }
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); val committed = CompletableDeferred<Unit>()
            f.append = { id, path -> entered.complete(Unit); release.await(); f.repo.appendGeneratedImage(id, path); committed.complete(Unit) }
            val (vm, store) = f.model(); main { vm.generate() }; ready(vm) { it.previewPath != null }
            main { vm.savePreview(); vm.savePreview() }; entered.await()
            f.db.itemDao().updateItem(f.item.copy(name = "最新裙名", imageUrls = f.item.imageUrls + f.image()))
            main { store.clear() }; assertTrue(File(preview).exists())
            release.complete(Unit); withTimeout(10_000) { committed.await() }
            val saved = f.db.itemDao().getItemById(11)!!
            assertEquals("最新裙名", saved.name); assertEquals(4, saved.imageUrls.size)
            assertEquals(f.item.imageUrls, saved.imageUrls.take(2)); assertEquals(preview, saved.imageUrls.last()); assertTrue(File(preview).exists())
        }
    }
    @Test fun cancelledOrFailedRequestsReleaseBusyWithoutAutomaticRetryAndDeletedItemCannotSave() = runBlocking {
        Fixture().use { f ->
            f.setup(); f.generation = { _, _ -> awaitCancellation() }; val (vm, _) = f.model()
            main { vm.generate() }; ready(vm) { it.isGenerating }; main { vm.cancelGeneration() }
            ready(vm) { !it.isGenerating && it.error != null }; assertTrue(vm.uiState.value.error!!.contains("计费")); assertEquals(1, f.calls)
            f.generation = { _, _ -> error("受控生成失败") }; main { vm.generate() }; ready(vm) { !it.isGenerating && it.error != null }
            assertEquals(2, f.calls)
            f.generation = { _, _ -> f.image() }; main { vm.generate() }; ready(vm) { it.previewPath != null }
            val preview = vm.uiState.value.previewPath!!
            f.db.itemDao().deleteItem(f.item); ready(vm) { it.item == null }
            main { vm.savePreview() }; assertFalse(vm.uiState.value.isSaving); assertTrue(File(preview).exists())
            main { vm.discardPreview() }; withTimeout(10_000) { while (File(preview).exists()) delay(20) }
        }
    }
    @Test fun restoredCanonicalAliasesCannotClaimOrDeleteAnExistingGarmentPhoto() = runBlocking {
        Fixture().use { f ->
            f.setup()
            val original = File(f.item.imageUrls.first())
            val alias = "${original.parent}/../images/${original.name}"
            val handle = SavedStateHandle(mapOf("itemId" to 11L, "mannequinPreview" to alias, "mannequinPreviewItem" to 11L))
            val (vm, store) = f.model(handle)
            assertNull("A path alias must not bypass existing-gallery ownership", vm.uiState.value.previewPath)
            main { store.clear() }
            assertTrue(original.isFile)
        }
    }

    @Test fun restoredPathsRequireMatchingItemOwnedRootAndCompletePngAndAreNeverDeletedOnRejection() = runBlocking {
        Fixture().use { f ->
            f.setup(); val valid = f.image()
            val external = File(f.root, "unrelated.png").apply { writeBytes(File(valid).readBytes()) }
            val corrupt = File(File(valid).parentFile, "broken.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val truncated = File(File(valid).parentFile, "partial.png").apply { writeBytes(File(valid).readBytes().dropLast(12).toByteArray()) }
            for ((path, owner) in listOf(external.absolutePath to 11L, corrupt.absolutePath to 11L, truncated.absolutePath to 11L, valid to 12L)) {
                val handle = SavedStateHandle(mapOf("itemId" to 11L, "mannequinPreview" to path, "mannequinPreviewItem" to owner))
                val (vm, store) = f.model(handle); assertNull(vm.uiState.value.previewPath)
                main { store.clear() }; assertTrue(File(path).exists())
            }
            assertTrue(ImageFileHelper.isOwnedGeneratedImage(f.context, valid))
        }
    }
}
