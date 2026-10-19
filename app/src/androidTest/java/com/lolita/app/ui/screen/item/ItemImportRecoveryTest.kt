package com.lolita.app.ui.screen.item

import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.file.TaobaoProductDraft
import com.lolita.app.data.file.TaobaoProductVariant
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.Brand
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemPriority
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.repository.BrandRepository
import com.lolita.app.data.repository.CategoryRepository
import com.lolita.app.data.repository.CoordinateRepository
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.ItemDraftRepository
import com.lolita.app.ui.screen.`import`.taobaoProductBatchItems
import com.lolita.app.ui.screen.`import`.taobaoProductEditorDraft
import com.lolita.app.data.repository.LocationRepository
import com.lolita.app.data.repository.PriceRepository
import com.lolita.app.data.repository.SeasonRepository
import com.lolita.app.data.repository.SourceRepository
import com.lolita.app.data.repository.StyleRepository
import com.lolita.app.di.AppModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Covers a committed import whose editor result was lost before navigation resumed. */
@RunWith(AndroidJUnit4::class)
class ItemImportRecoveryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val gson = Gson()
    private lateinit var database: LolitaDatabase
    private lateinit var items: ItemRepository
    private lateinit var sourceDirectory: File
    private val stores = mutableListOf<ViewModelStore>()
    private val jobs = mutableListOf<Job>()
    private val testFiles = mutableSetOf<String>()
    private var helperConfigured = false

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        items = ItemRepository(database.itemDao(), database = database,
            paymentDao = database.paymentDao(), context = context)
        sourceDirectory = File(context.cacheDir, "item-import-recovery-${UUID.randomUUID()}")
        check(sourceDirectory.mkdirs())
        database.brandDao().insertBrand(Brand(id = 101, name = "恢复测试品牌", createdAt = 1))
        database.categoryDao().insertCategory(Category(id = 307, name = "OP", createdAt = 2))
        // Reference-aware editor cleanup must query only this isolated database.
        ImageFileHelper.configure(context, database)
        helperConfigured = true
        Unit
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { stores.forEach(ViewModelStore::clear) }
        runBlocking { jobs.joinAll() }
        instrumentation.waitForIdleSync()
        if (helperConfigured) ImageFileHelper.configure(context, AppModule.database())
        if (::database.isInitialized) database.close()
        testFiles.forEach { File(it).delete() }
        if (::sourceDirectory.isInitialized) sourceDirectory.deleteRecursively()
    }

    private data class Fixture(
        val saved: Item,
        val draftJson: String,
        val savedBytes: Map<String, ByteArray>,
        val orphanPath: String?
    )

    private suspend fun fixture(includeOrphan: Boolean = false): Fixture {
        val heads = (0 until 2).map { image(it) }
        val details = (0 until 28).map { image(it + 2) }
        val token = UUID.randomUUID().toString()
        val candidate = Item(
            brandId = 101,
            categoryId = 307,
            name = "核对后更正的月夜OP",
            description = "手动追加的保养说明",
            imageUrls = heads,
            status = ItemStatus.WISHED,
            priority = ItemPriority.HIGH,
            colors = listOf("绀色"),
            season = "秋,冬",
            style = "古典",
            size = "L（已核对）",
            sizeChartImageUrl = details.first(),
            sizeChartImageUrls = details.drop(1),
            source = "淘宝 / 已核对",
            createdAt = 17,
            updatedAt = 19
        )
        // This compiles before the Item field exists: old Gson ignores the property;
        // the new model persists it and lets the recreated editor find the committed row.
        val itemJson = gson.toJsonTree(candidate).asJsonObject.apply { addProperty("importToken", token) }
        val tokenized = gson.fromJson(itemJson, Item::class.java)
        val savedId = items.insertItem(tokenized)
        val saved = requireNotNull(items.getItemById(savedId))
        val orphan = if (includeOrphan) image(101) else null
        val draft = TaobaoProductDraft(
            name = "网页原始月夜OP",
            brand = "恢复测试品牌",
            size = "M",
            properties = "类型：OP",
            description = "网页原始详情，尚未经用户核对",
            productUrl = "https://item.taobao.com/item.htm?id=123456",
            remoteImages = emptyList(),
            imagePaths = heads,
            brandAliases = listOf("恢复测试品牌"),
            category = "OP",
            remoteDetailImages = emptyList(),
            sizeChartImagePaths = details + listOfNotNull(orphan),
            importToken = token,
            batchIndex = 1,
            batchTotal = 2
        )
        return Fixture(saved, gson.toJson(draft),
            (heads + details).associateWith { File(it).readBytes() }, orphan)
    }

    private suspend fun image(index: Int): String {
        val source = File(sourceDirectory, "source-$index-${UUID.randomUUID()}.png")
        val bitmap = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.rgb((index * 7 + 11) % 256, (index * 13 + 23) % 256,
                (index * 19 + 37) % 256))
            source.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
        return ImageFileHelper.copyExistingImageToInternalStorage(context, source.absolutePath)
            .also { testFiles += it }
    }

    private suspend fun editor(fixture: Fixture, durableDraft: Boolean = false): Pair<ItemEditViewModel, ViewModelStore> =
        withContext(Dispatchers.Main) {
            val store = ViewModelStore().also { stores += it }
            val viewModel = ItemEditViewModel(
                itemRepository = items,
                brandRepository = BrandRepository(database.brandDao(), database.itemDao(), database),
                categoryRepository = CategoryRepository(database.categoryDao(), database.itemDao(), database),
                coordinateRepository = CoordinateRepository(database.coordinateDao(), database.itemDao(), database),
                priceRepository = PriceRepository(database.priceDao(), database.paymentDao(), context, database),
                styleRepository = StyleRepository(database.styleDao(), database.itemDao(), database),
                seasonRepository = SeasonRepository(database.seasonDao(), database.itemDao(), database),
                sourceRepository = SourceRepository(database.sourceDao(), database.itemDao(), database),
                locationRepository = LocationRepository(database.locationDao(), database.itemDao()),
                draftRepository = if (durableDraft) ItemDraftRepository(database, items) else null
            )
            jobs += requireNotNull(viewModel.viewModelScope.coroutineContext[Job])
            store.put("recovered-import", viewModel)
            viewModel.loadItem(0, importedDraftJson = fixture.draftJson)
            viewModel to store
        }

    private suspend fun loaded(viewModel: ItemEditViewModel): ItemEditUiState = withTimeout(10_000) {
        viewModel.uiState.first { !it.isLoading && it.name.isNotBlank() && it.purchaseReviewsLoaded }
    }

    private fun assertSavedImagesIntact(fixture: Fixture) {
        assertEquals(2, fixture.saved.imageUrls.size)
        assertEquals(28, listOfNotNull(fixture.saved.sizeChartImageUrl).size + fixture.saved.sizeChartImageUrls.size)
        assertEquals(30, fixture.savedBytes.size)
        fixture.savedBytes.forEach { (path, bytes) ->
            assertTrue("committed image must survive editor recovery: $path", File(path).isFile)
            assertArrayEquals("committed image bytes", bytes, File(path).readBytes())
        }
    }

    @Test
    fun selectedTwoColorStylesCommitIndependentWishesWithAllOriginalImagesAndNoPayments() = runBlocking {
        val original = fixture()
        database.categoryDao().insertCategory(Category(id = 308, name = "连裤袜"))
        val colors = (1..4).map { TaobaoProductVariant("color-$it", "款式$it", "连裤袜") }
        val source = gson.fromJson(original.draftJson, TaobaoProductDraft::class.java).copy(
            name = "摇篮时光印花连裤袜", category = "连裤袜", categoryCandidates = listOf("连裤袜"),
            colorVariants = colors, importToken = null)
        val parts = taobaoProductBatchItems(source, setOf(colors[0].id, colors[2].id), true)
        assertEquals(2, parts.size)
        val saved = parts.mapIndexed { index, part ->
            val copies = original.savedBytes.keys.associateWith { path ->
                ImageFileHelper.copyExistingImageToInternalStorage(context, path).also { testFiles += it }
            }
            val draft = taobaoProductEditorDraft(part, source, copies, UUID.randomUUID().toString(), index, parts.size)
            val (viewModel, _) = editor(original.copy(draftJson = gson.toJson(draft)), durableDraft = true)
            val state = loaded(viewModel)
            assertEquals(ItemStatus.WISHED, state.status)
            val id = withContext(Dispatchers.Main) { viewModel.saveItem().getOrThrow() }
            requireNotNull(items.getItemById(id)).also { item ->
                assertEquals(308L, item.categoryId)
                assertEquals(ItemStatus.WISHED, item.status)
                assertEquals(2, item.imageUrls.size)
                assertEquals(28, listOfNotNull(item.sizeChartImageUrl).size + item.sizeChartImageUrls.size)
                val paths = item.imageUrls + listOfNotNull(item.sizeChartImageUrl) + item.sizeChartImageUrls
                original.savedBytes.values.zip(paths).forEach { (bytes, path) -> assertArrayEquals(bytes, File(path).readBytes()) }
            }
        }
        assertEquals(listOf("摇篮时光印花连裤袜 · 款式1", "摇篮时光印花连裤袜 · 款式3"), saved.map { it.name })
        assertEquals(2, saved.map { it.importToken }.toSet().size)
        val first = saved[0].imageUrls + listOfNotNull(saved[0].sizeChartImageUrl) + saved[0].sizeChartImageUrls
        val second = saved[1].imageUrls + listOfNotNull(saved[1].sizeChartImageUrl) + saved[1].sizeChartImageUrls
        assertTrue(first.toSet().intersect(second.toSet()).isEmpty())
        assertEquals(3, database.itemDao().getAllItemsList().size)
        assertTrue(database.itemDraftDao().getAll().isEmpty())
        assertTrue(database.priceDao().getAllPricesList().isEmpty())
        assertTrue(database.paymentDao().getAllPaymentsList().isEmpty())
        assertTrue(database.orderDao().getAllBindings().isEmpty())
        assertEquals(original.saved, items.getItemById(original.saved.id))
        assertSavedImagesIntact(original)
    }

    @Test
    fun committedImportWithLostResultLoadsSavedRecordAndRepeatedSaveDoesNotInsertOrOverwriteIt() = runBlocking {
        val fixture = fixture()
        val (editor, _) = editor(fixture)
        val restored = loaded(editor)

        assertEquals("recover the committed row instead of opening a new item", fixture.saved, restored.item)
        assertEquals("核对后更正的月夜OP", restored.name)
        assertEquals("手动追加的保养说明", restored.description)
        assertEquals("L（已核对）", restored.size)
        assertEquals("淘宝 / 已核对", restored.source)
        assertEquals(listOf("绀色"), restored.colors)
        assertEquals(listOf("秋", "冬"), restored.seasons)
        assertEquals(ItemPriority.HIGH, restored.priority)
        assertEquals(fixture.saved.imageUrls, restored.imageUrls)
        assertEquals(fixture.saved.sizeChartImageUrl, restored.sizeChartImageUrl)
        assertEquals(fixture.saved.sizeChartImageUrls, restored.sizeChartImageUrls)
        assertFalse(editor.hasUnsavedChanges)

        repeat(2) {
            val result = withContext(Dispatchers.Main) { editor.saveItem() }
            assertTrue("repeat save should return the already committed id", result.isSuccess)
            assertEquals(fixture.saved.id, result.getOrThrow())
            val current = requireNotNull(items.getItemById(fixture.saved.id))
            assertEquals("original creation time survives recovery", 17L, current.createdAt)
            assertEquals("stale page fields must not overwrite the user's saved edits",
                fixture.saved.copy(updatedAt = current.updatedAt), current)
            assertEquals(listOf(fixture.saved.id), database.itemDao().getAllItemsList().map { item -> item.id })
        }
        assertSavedImagesIntact(fixture)
        assertTrue(database.priceDao().getAllPricesList().isEmpty())
        assertTrue(database.paymentDao().getAllPaymentsList().isEmpty())
        assertTrue(database.orderDao().getAllBindings().isEmpty())
    }

    @Test
    fun clearingRecoveredEditorKeepsCommittedMainAndAllDetailImages() = runBlocking {
        val fixture = fixture(includeOrphan = true)
        val (editor, store) = editor(fixture)
        loaded(editor)

        withContext(Dispatchers.Main) { store.clear() }
        // An orphan in the stale draft is a real cleanup completion signal. Waiting
        // for its deletion avoids declaring success before asynchronous cleanup runs.
        val orphan = requireNotNull(fixture.orphanPath)
        withTimeout(10_000) { while (File(orphan).exists()) delay(10) }
        instrumentation.waitForIdleSync()

        assertSavedImagesIntact(fixture)
        assertEquals(fixture.saved, items.getItemById(fixture.saved.id))
        assertEquals(listOf(fixture.saved.id), database.itemDao().getAllItemsList().map { it.id })
    }
}
