package com.lolita.app.ui.screen.`import`

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.file.TaobaoProductDraft
import com.lolita.app.data.file.TaobaoProductVariant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** Real image ownership and serialized import progress; no Room or settings mutations. */
@RunWith(AndroidJUnit4::class)
class TaobaoProductViewModelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val application = instrumentation.targetContext.applicationContext as Application
    private val gson = Gson()
    private val stores = mutableListOf<ViewModelStore>()
    private val testFiles = Collections.synchronizedSet(mutableSetOf<String>())
    private lateinit var sourceDirectory: File

    @Before
    fun setUp() {
        sourceDirectory = File(application.cacheDir, "taobao-product-test-${UUID.randomUUID()}")
        check(sourceDirectory.mkdirs())
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { stores.forEach(ViewModelStore::clear) }
        instrumentation.waitForIdleSync()
        synchronized(testFiles) { testFiles.forEach { File(it).delete() } }
        if (::sourceDirectory.isInitialized) sourceDirectory.deleteRecursively()
    }

    private data class Fixture(
        val original: TaobaoProductDraft,
        val items: List<TaobaoProductDraft>,
        val master: TaobaoProductDraft,
        val masterBytes: Map<String, ByteArray>
    ) {
        fun session(index: Int = 0, activeToken: String? = null, paused: Boolean = false) =
            TaobaoProductImportSession(
                state = TaobaoProductState(
                    input = "月夜系列的淘宝分享",
                    url = original.productUrl,
                    draft = original,
                    selectedVariantIds = original.variants.map { it.id }.toSet()
                ),
                batch = TaobaoProductBatchProgress(items, index = index, savedCount = index,
                    activeToken = activeToken, paused = paused),
                masterDraft = master,
                ownedMasterPaths = masterBytes.keys.toList()
            )
    }

    private data class Model(
        val viewModel: TaobaoProductViewModel,
        val handle: SavedStateHandle,
        val store: ViewModelStore
    )

    private inner class EditorResults {
        val drafts = CopyOnWriteArrayList<TaobaoProductDraft>()
        private val ready = Channel<TaobaoProductDraft>(Channel.UNLIMITED)
        val onReady: (String) -> Unit = { json ->
            val draft = gson.fromJson(json, TaobaoProductDraft::class.java)
            rememberEditorFiles(draft)
            drafts += draft
            check(ready.trySend(draft).isSuccess)
        }

        suspend fun next(viewModel: TaobaoProductViewModel): TaobaoProductDraft {
            val draft = withTimeout(10_000) { ready.receive() }
            awaitState(viewModel) { !it.busy }
            return draft
        }

        fun assertNoPendingEditor() {
            assertTrue("unexpected duplicate editor handoff", ready.tryReceive().isFailure)
        }
    }

    private fun imagePaths(draft: TaobaoProductDraft): List<String> =
        draft.imagePaths + listOfNotNull(draft.sizeChartImagePath) + draft.sizeChartImagePaths

    private fun rememberEditorFiles(draft: TaobaoProductDraft) {
        testFiles += imagePaths(draft)
    }

    private suspend fun fixture(itemCount: Int = 2): Fixture {
        val variants = listOf(
            TaobaoProductVariant("piece-op", "OP连衣裙", "OP"),
            TaobaoProductVariant("piece-kc", "KC头饰", "KC"),
            TaobaoProductVariant("piece-cape", "披肩", "披肩")
        ).take(itemCount)
        val original = TaobaoProductDraft(
            name = "月夜系列",
            brand = "月光洋装",
            size = "S / M / L",
            properties = "材质：棉；套装：OP连衣裙、KC头饰、披肩",
            description = "完整宝贝详情\n手洗，阴干。",
            productUrl = "https://item.taobao.com/item.htm?id=123456",
            remoteImages = listOf("https://img.alicdn.com/head-0.png", "https://img.alicdn.com/head-1.png"),
            shopName = "月光洋装官方店",
            brandAliases = listOf("月光洋装", "Moonlight"),
            category = "OP",
            categoryCandidates = listOf("OP", "KC", "披肩"),
            remoteDetailImages = (0 until 28).map { "https://img.alicdn.com/detail-$it.png" },
            variants = variants
        )
        val heads = (0 until 2).map { masterImage(it) }
        val details = (0 until 28).map { masterImage(it + 2) }
        val master = original.copy(imagePaths = heads, sizeChartImagePaths = details)
        // Seed the persisted queue independently of the production queue builder.
        val items = variants.map { variant ->
            original.copy(name = "月夜系列 · ${variant.name}", category = variant.category,
                categoryCandidates = listOf(variant.category), variants = emptyList(),
                importToken = UUID.randomUUID().toString())
        }
        return Fixture(original, items, master, imagePaths(master).associateWith { File(it).readBytes() })
    }

    private suspend fun masterImage(index: Int): String {
        val source = File(sourceDirectory, "source-$index-${UUID.randomUUID()}.png")
        val bitmap = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.rgb((index * 7 + 11) % 256, (index * 13 + 23) % 256,
                (index * 19 + 37) % 256))
            source.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
        return ImageFileHelper.copyExistingImageToInternalStorage(application, source.absolutePath)
            .also { testFiles += it }
    }

    private suspend fun newModel(session: TaobaoProductImportSession): Model =
        newModel(gson.toJson(session))

    private suspend fun newModel(serializedSession: String): Model = withContext(Dispatchers.Main) {
        val handle = SavedStateHandle(mapOf(TaobaoProductViewModel.SESSION_KEY to serializedSession))
        val store = ViewModelStore()
        stores += store
        val viewModel = TaobaoProductViewModel(application, handle)
        store.put("taobao-product", viewModel)
        Model(viewModel, handle, store)
    }

    private suspend fun awaitState(
        viewModel: TaobaoProductViewModel,
        predicate: (TaobaoProductState) -> Boolean
    ): TaobaoProductState = withTimeout(10_000) { viewModel.state.first(predicate) }

    private suspend fun awaitFilesDeleted(paths: Collection<String>) {
        withTimeout(10_000) {
            while (paths.any { File(it).exists() }) delay(10)
        }
    }

    private fun assertEditorDraft(fixture: Fixture, draft: TaobaoProductDraft, position: Int) {
        val names = listOf("月夜系列 · OP连衣裙", "月夜系列 · KC头饰", "月夜系列 · 披肩")
        val categories = listOf("OP", "KC", "披肩")
        assertEquals(names[position], draft.name)
        assertEquals(categories[position], draft.category)
        assertEquals(listOf(categories[position]), draft.categoryCandidates)
        assertEquals("月光洋装", draft.brand)
        assertEquals("月光洋装官方店", draft.shopName)
        assertEquals(listOf("月光洋装", "Moonlight"), draft.brandAliases)
        assertEquals("S / M / L", draft.size)
        assertEquals("材质：棉；套装：OP连衣裙、KC头饰、披肩", draft.properties)
        assertEquals("完整宝贝详情\n手洗，阴干。", draft.description)
        assertEquals("https://item.taobao.com/item.htm?id=123456", draft.productUrl)
        assertEquals(fixture.original.remoteImages, draft.remoteImages)
        assertEquals(fixture.original.remoteDetailImages, draft.remoteDetailImages)
        assertTrue("one editor must contain one piece", draft.variants.isEmpty())
        assertEquals(position + 1, draft.batchIndex)
        assertEquals(fixture.items.size, draft.batchTotal)
        assertFalse(draft.importToken.isNullOrBlank())
        assertEquals(fixture.items[position].importToken, draft.importToken)
        assertFalse(draft.editorToken.isNullOrBlank())
        assertEquals(2, draft.imagePaths.size)
        assertNull(draft.sizeChartImagePath)
        assertEquals("all detail originals are retained", 28, draft.sizeChartImagePaths.size)

        val copies = imagePaths(draft)
        val masters = imagePaths(fixture.master)
        assertEquals("every original has a distinct copy", 30, copies.toSet().size)
        assertTrue("masters must never reach an editor", copies.none { it in masters })
        copies.forEachIndexed { index, path ->
            val file = File(path)
            assertEquals(File(application.filesDir, "images").canonicalFile, file.canonicalFile.parentFile)
            assertTrue("internal copies use UUID filenames", Regex("[0-9a-f-]{36}\\.png").matches(file.name))
            assertArrayEquals("original bytes and order for image $index",
                fixture.masterBytes.getValue(masters[index]), file.readBytes())
        }
    }

    private fun assertEditorsIntact(fixture: Fixture, vararg drafts: TaobaoProductDraft) {
        drafts.forEachIndexed { editorIndex, draft ->
            imagePaths(draft).forEachIndexed { index, path ->
                assertArrayEquals("editor $editorIndex image $index survives lifecycle cleanup",
                    fixture.masterBytes.getValue(imagePaths(fixture.master)[index]), File(path).readBytes())
            }
        }
    }

    @Test
    fun savingFirstPieceAutomaticallyHandsOffSecondWithAllIndependentDetailImages() = runBlocking {
        val fixture = fixture()
        val model = newModel(fixture.session())
        val editors = EditorResults()
        withContext(Dispatchers.Main) { model.viewModel.edit(editors.onReady) }
        val first = editors.next(model.viewModel)
        assertEditorDraft(fixture, first, 0)
        assertEquals(0, model.viewModel.state.value.savedCount)
        assertEquals(0, model.viewModel.state.value.batchIndex)

        withContext(Dispatchers.Main) {
            model.viewModel.editorReturned("${first.editorToken}:saved", editors.onReady)
        }
        val second = editors.next(model.viewModel)
        assertEditorDraft(fixture, second, 1)
        assertNotEquals(first.importToken, second.importToken)
        assertTrue(imagePaths(first).intersect(imagePaths(second).toSet()).isEmpty())
        assertEquals(1, model.viewModel.state.value.savedCount)
        assertEquals(1, model.viewModel.state.value.batchIndex)
        assertFalse(model.viewModel.state.value.completed)

        withContext(Dispatchers.Main) {
            model.viewModel.editorReturned("${second.editorToken}:saved", editors.onReady)
        }
        val completed = awaitState(model.viewModel) { it.completed && !it.busy }
        assertEquals(2, completed.savedCount)
        assertEquals(2, completed.batchIndex)
        assertEquals(2, completed.batchTotal)
        assertFalse(completed.paused)
        assertNull(completed.error)
        awaitFilesDeleted(fixture.masterBytes.keys)
        assertEditorsIntact(fixture, first, second)
        editors.assertNoPendingEditor()
    }

    @Test
    fun staleResultsAndRepeatedEditDoNotDuplicateAnActiveSecondPiece() = runBlocking {
        val fixture = fixture()
        val model = newModel(fixture.session())
        val editors = EditorResults()
        withContext(Dispatchers.Main) { model.viewModel.edit(editors.onReady) }
        val first = editors.next(model.viewModel)
        withContext(Dispatchers.Main) {
            model.viewModel.editorReturned("${first.editorToken}:saved", editors.onReady)
        }
        val second = editors.next(model.viewModel)

        withContext(Dispatchers.Main) {
            model.viewModel.editorReturned("${first.editorToken}:saved", editors.onReady)
            model.viewModel.editorReturned("${first.editorToken}:discarded", editors.onReady)
            model.viewModel.editorReturned("unrelated-token:saved", editors.onReady)
            model.viewModel.edit(editors.onReady)
        }
        assertEquals(1, model.viewModel.state.value.savedCount)
        assertEquals(1, model.viewModel.state.value.batchIndex)
        assertEquals(2, editors.drafts.size)
        editors.assertNoPendingEditor()
        assertEditorsIntact(fixture, first, second)

        withContext(Dispatchers.Main) {
            model.viewModel.editorReturned("${second.editorToken}:saved", editors.onReady)
            model.viewModel.editorReturned("${second.editorToken}:saved", editors.onReady)
            model.viewModel.edit(editors.onReady)
        }
        assertTrue(model.viewModel.state.value.completed)
        assertEquals(2, model.viewModel.state.value.savedCount)
        assertEquals(2, editors.drafts.size)
        editors.assertNoPendingEditor()
    }

    @Test
    fun discardingSecondPiecePausesAndContinuingPreservesFirstEditorsFiles() = runBlocking {
        val fixture = fixture()
        val model = newModel(fixture.session())
        val editors = EditorResults()
        withContext(Dispatchers.Main) { model.viewModel.edit(editors.onReady) }
        val first = editors.next(model.viewModel)
        withContext(Dispatchers.Main) {
            model.viewModel.editorReturned("${first.editorToken}:saved", editors.onReady)
        }
        val discarded = editors.next(model.viewModel)
        // The existing editor owns and removes its abandoned copies.
        imagePaths(discarded).forEach { check(File(it).delete()) }
        withContext(Dispatchers.Main) {
            model.viewModel.editorReturned("${discarded.editorToken}:discarded", editors.onReady)
        }
        val paused = awaitState(model.viewModel) { it.paused && !it.busy }
        assertEquals(1, paused.savedCount)
        assertEquals(1, paused.batchIndex)
        assertFalse(paused.completed)
        assertEditorsIntact(fixture, first)
        assertTrue(fixture.masterBytes.keys.all { File(it).isFile })
        editors.assertNoPendingEditor()

        withContext(Dispatchers.Main) { model.viewModel.edit(editors.onReady) }
        val resumed = editors.next(model.viewModel)
        assertEditorDraft(fixture, resumed, 1)
        assertEquals(discarded.importToken, resumed.importToken)
        assertNotEquals(discarded.editorToken, resumed.editorToken)
        assertTrue(imagePaths(first).intersect(imagePaths(resumed).toSet()).isEmpty())
        assertTrue(imagePaths(discarded).intersect(imagePaths(resumed).toSet()).isEmpty())
        assertFalse(model.viewModel.state.value.paused)
        assertEquals(1, model.viewModel.state.value.savedCount)
        withContext(Dispatchers.Main) {
            model.viewModel.editorReturned("${discarded.editorToken}:saved", editors.onReady)
        }
        assertEquals(1, model.viewModel.state.value.savedCount)
        editors.assertNoPendingEditor()
        assertEditorsIntact(fixture, first, resumed)
    }

    @Test
    fun clearingViewModelDeletesOnlyItsMasterCacheAndKeepsEditorAndUnrelatedFiles() = runBlocking {
        val fixture = fixture()
        val unrelated = masterImage(101)
        val unrelatedBytes = File(unrelated).readBytes()
        val model = newModel(fixture.session())
        val editors = EditorResults()
        withContext(Dispatchers.Main) { model.viewModel.edit(editors.onReady) }
        val first = editors.next(model.viewModel)

        withContext(Dispatchers.Main) { model.store.clear() }

        awaitFilesDeleted(fixture.masterBytes.keys)
        assertArrayEquals(unrelatedBytes, File(unrelated).readBytes())
        assertEditorsIntact(fixture, first)
        editors.assertNoPendingEditor()
    }

    @Test
    fun serializedRestoreKeepsActiveTokenAndContinuesAtTheNextUnsavedPiece() = runBlocking {
        val fixture = fixture(itemCount = 3)
        val original = newModel(fixture.session())
        val originalEditors = EditorResults()
        withContext(Dispatchers.Main) { original.viewModel.edit(originalEditors.onReady) }
        val first = originalEditors.next(original.viewModel)
        withContext(Dispatchers.Main) {
            original.viewModel.editorReturned("${first.editorToken}:saved", originalEditors.onReady)
        }
        val second = originalEditors.next(original.viewModel)
        val snapshot = withContext(Dispatchers.Main) {
            requireNotNull(original.handle.get<String>(TaobaoProductViewModel.SESSION_KEY))
        }
        // Gson round-trip models the saved JSON surviving process death. Clearing an old
        // ViewModel would be an explicit exit and intentionally deletes its master cache.
        val persisted = gson.fromJson(snapshot, TaobaoProductImportSession::class.java)
        assertEquals(second.editorToken, persisted.batch?.activeToken)
        assertEquals(1, persisted.batch?.index)
        val restored = newModel(gson.toJson(persisted))
        val restoredEditors = EditorResults()
        assertEquals(1, restored.viewModel.state.value.batchIndex)
        assertEquals(1, restored.viewModel.state.value.savedCount)
        assertEquals(3, restored.viewModel.state.value.batchTotal)
        assertEquals(setOf("piece-op", "piece-kc", "piece-cape"),
            restored.viewModel.state.value.selectedVariantIds)

        withContext(Dispatchers.Main) {
            restored.viewModel.edit(restoredEditors.onReady)
            restored.viewModel.editorReturned("${first.editorToken}:saved", restoredEditors.onReady)
        }
        restoredEditors.assertNoPendingEditor()
        assertEquals(1, restored.viewModel.state.value.savedCount)
        withContext(Dispatchers.Main) {
            restored.viewModel.editorReturned("${second.editorToken}:saved", restoredEditors.onReady)
        }
        val third = restoredEditors.next(restored.viewModel)
        assertEditorDraft(fixture, third, 2)
        assertEquals(2, restored.viewModel.state.value.savedCount)
        assertNotEquals(second.importToken, third.importToken)
        assertTrue(imagePaths(first).intersect(imagePaths(third).toSet()).isEmpty())
        assertTrue(imagePaths(second).intersect(imagePaths(third).toSet()).isEmpty())
        withContext(Dispatchers.Main) {
            restored.viewModel.editorReturned("${second.editorToken}:saved", restoredEditors.onReady)
            restored.viewModel.editorReturned("${third.editorToken}:saved", restoredEditors.onReady)
        }
        assertTrue(restored.viewModel.state.value.completed)
        assertEquals(3, restored.viewModel.state.value.savedCount)
        assertEquals(1, restoredEditors.drafts.size)
        restoredEditors.assertNoPendingEditor()
        awaitFilesDeleted(fixture.masterBytes.keys)
        assertEditorsIntact(fixture, first, second, third)
    }

    @Test
    fun missingRestoredMasterFileShowsErrorAndCannotSilentlyRestartTheBatch() = runBlocking {
        val fixture = fixture()
        check(File(fixture.master.sizeChartImagePaths[7]).delete())
        val model = newModel(fixture.session(index = 1, paused = true))
        val editors = EditorResults()

        withContext(Dispatchers.Main) {
            repeat(3) { model.viewModel.edit(editors.onReady) }
        }

        val state = model.viewModel.state.value
        assertNotNull("missing cache must be visible", state.error)
        assertTrue(state.error.orEmpty().contains("缓存已丢失"))
        assertTrue(state.error.orEmpty().contains("已保存的 1 件会保留"))
        assertFalse(state.busy)
        assertTrue(state.paused)
        assertEquals(1, state.savedCount)
        assertEquals(1, state.batchIndex)
        assertFalse(state.completed)
        assertTrue(editors.drafts.isEmpty())
        editors.assertNoPendingEditor()
    }

    @Test
    fun savedResultAfterRestoringMissingCacheCountsTheSaveButNeverRecreatesNextPiece() = runBlocking {
        val fixture = fixture(itemCount = 3)
        val original = fixture.original.copy(remoteImages = emptyList(), remoteDetailImages = emptyList())
        val items = fixture.items.map { it.copy(remoteImages = emptyList(), remoteDetailImages = emptyList()) }
        val session = TaobaoProductImportSession(
            state = TaobaoProductState(draft = original,
                selectedVariantIds = original.variants.map { it.id }.toSet()),
            batch = TaobaoProductBatchProgress(items, index = 1, savedCount = 1, activeToken = "second"),
            masterDraft = null
        )
        val model = newModel(session)
        val editors = EditorResults()
        assertTrue(model.viewModel.state.value.error.orEmpty().contains("缓存已丢失"))

        withContext(Dispatchers.Main) {
            model.viewModel.editorReturned("second:saved", editors.onReady)
        }
        val state = awaitState(model.viewModel) { !it.busy && it.savedCount == 2 }

        assertEquals(2, state.batchIndex)
        assertEquals(2, state.savedCount)
        assertFalse(state.completed)
        assertTrue("cache loss must stay explicit after the editor saves",
            state.error.orEmpty().contains("缓存已丢失"))
        assertTrue(state.error.orEmpty().contains("已保存的 2 件会保留"))
        assertTrue(state.paused)
        assertTrue("a restored missing cache must not create another editor", editors.drafts.isEmpty())
        editors.assertNoPendingEditor()
        withContext(Dispatchers.Main) {
            model.viewModel.editorReturned("second:saved", editors.onReady)
            model.viewModel.edit(editors.onReady)
        }
        assertEquals(2, model.viewModel.state.value.savedCount)
        editors.assertNoPendingEditor()

        val discarded = newModel(session)
        withContext(Dispatchers.Main) { discarded.viewModel.editorReturned("second:discarded", editors.onReady) }
        assertEquals(1, discarded.viewModel.state.value.savedCount)
        assertEquals(1, discarded.viewModel.state.value.batchIndex)
        assertTrue(discarded.viewModel.state.value.paused)
        assertTrue("cache loss must stay explicit after discard",
            discarded.viewModel.state.value.error.orEmpty().contains("缓存已丢失"))
        editors.assertNoPendingEditor()
    }

    @Test
    fun failedEditorHandoffDeletesOnlyUntransferredCopiesAndRetryKeepsTheSamePiece() = runBlocking {
        val fixture = fixture()
        val model = newModel(fixture.session())
        val attempted = CompletableDeferred<TaobaoProductDraft>()
        withContext(Dispatchers.Main) {
            model.viewModel.edit { json ->
                val draft = gson.fromJson(json, TaobaoProductDraft::class.java)
                rememberEditorFiles(draft)
                attempted.complete(draft)
                throw IllegalStateException("test navigation failure")
            }
        }
        val rejected = withTimeout(10_000) { attempted.await() }
        val failed = awaitState(model.viewModel) { it.paused && !it.busy && it.error != null }
        awaitFilesDeleted(imagePaths(rejected))
        assertEquals(0, failed.savedCount)
        assertEquals(0, failed.batchIndex)
        fixture.masterBytes.forEach { (path, bytes) -> assertArrayEquals(bytes, File(path).readBytes()) }

        val editors = EditorResults()
        withContext(Dispatchers.Main) { model.viewModel.edit(editors.onReady) }
        val retry = editors.next(model.viewModel)
        assertEditorDraft(fixture, retry, 0)
        assertEquals(rejected.importToken, retry.importToken)
        assertNotEquals(rejected.editorToken, retry.editorToken)
        assertTrue(imagePaths(rejected).intersect(imagePaths(retry).toSet()).isEmpty())
        assertEquals(0, model.viewModel.state.value.savedCount)
        assertNull(model.viewModel.state.value.error)
    }
}
