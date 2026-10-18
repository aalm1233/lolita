package com.lolita.app.ui.screen.item

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.generation.ImageTaskCoordinator
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.repository.*
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.*
import com.lolita.app.ui.theme.LolitaTheme
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runners.model.MultipleFailureException
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** Actual detail buttons with the same durable-draft repository used in production. No generation. */
class ItemDetailCoverDraftTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target = instrumentation.targetContext
    private lateinit var fixture: Fixture

    private inner class Fixture {
        val root = File(target.cacheDir, "detail-cover-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        }
        val db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val items = ItemRepository(db.itemDao(), database = db, context = context)
        val drafts = ItemDraftRepository(db, items)
        val stores = mutableListOf<ViewModelStore>()
        val viewModelJobs = mutableListOf<Job>()
        val coordinator = ImageTaskCoordinator(db, scope,
            startService = { error("A cover change must never start generation") },
            saveImage = { error("A cover change must never execute generation") },
            deleteImage = { ImageFileHelper.cleanupUnreferencedImages(listOf(it)) },
            validateImage = { ImageFileHelper.isOwnedGeneratedImage(context, it) },
            appendImageProvenance = { itemId, path, mode, taskId ->
                db.imageProvenanceDao().upsert(ImageProvenance(path, itemId, ImageSourceType.AI_GENERATED, mode, taskId))
            })
        lateinit var original: String

        suspend fun init() {
            ImageFileHelper.configure(context, db)
            coordinator.awaitStartup()
            original = image(Color.BLUE)
            db.brandDao().insertBrand(Brand(id = 1, name = "封面保留品牌"))
            db.categoryDao().insertCategory(Category(id = 1, name = "封面测试 OP", categorySemantic = CategorySemantic.OP))
            db.locationDao().insertLocation(Location(id = 1, name = "原房间"))
            db.itemDao().insertItem(Item(id = 1, brandId = 1, categoryId = 1, name = "持久原物品",
                description = "原备注与账本必须保留", imageUrls = listOf(original), status = ItemStatus.OWNED,
                locationId = 1, size = "M", colors = listOf("蓝色"), createdAt = 100, updatedAt = 200))
            db.priceDao().insertPrice(Price(id = 10, itemId = 1, type = PriceType.FULL, totalPrice = 253.45,
                shipping = 3.45, createdAt = 300))
            db.orderDao().insertBinding(OrderBinding(1, 10))
            db.orderDao().insertStage(PaymentStage(id = 11, priceId = 10, name = "原全款阶段", amount = 253.45, dueDate = 400))
            db.paymentDao().insertPayment(Payment(id = 12, priceId = 10, stageId = 11, amount = 253.45,
                dueDate = 400, isPaid = true, paidDate = 500, createdAt = 350))
        }

        suspend fun image(color: Int): String {
            val bitmap = Bitmap.createBitmap(64, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            val bytes = ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)); output.toByteArray()
            }
            bitmap.recycle()
            return ImageFileHelper.saveGeneratedImage(context, bytes)
        }

        suspend fun legacyImage(): String = image(Color.MAGENTA).also { items.appendGeneratedImage(1, it) }

        /** Exercise the real pending-result confirmation transaction, with no service/transport. */
        suspend fun taskImage(): String {
            val item = db.itemDao().getItemById(1)!!
            val template = image(Color.LTGRAY)
            val path = image(Color.GREEN)
            val provider = ImageGenerationProvider.SEEDREAM
            val request = ImageTaskRequest(
                garments = listOf(ImageTaskGarment(1, item.name, TryOnRole.MAIN, CategorySemantic.OP, ItemComposition.SINGLE)),
                version = 4,
                mannequinSubject = MannequinSubject(1, "封面测试 OP", MannequinPresentation.DRESS_FORM, CategorySemantic.OP),
                promptVersion = 1, promptSnapshot = "A controlled pending dress-form result; never submit it.",
                serviceModel = provider.model, inputSha256s = listOf(template, original).map(::imageSha256))
            val task = ImageTask(UUID.randomUUID().toString(), ImageTaskKind.MANNEQUIN, provider,
                "已生成待确认人台图", request.encode(), listOf(template, original),
                status = ImageTaskStatus.AWAITING_CONFIRMATION, requestSent = true, resultImagePath = path,
                keepSourceImages = true)
            task.validate()
            db.imageTaskDao().insert(task)
            coordinator.confirmMannequin(task.id)
            assertEquals(ImageTaskStatus.COMPLETED, db.imageTaskDao().get(task.id)!!.status)
            assertEquals(task.id, db.imageProvenanceDao().get(1, path)!!.taskId)
            return path
        }

        suspend fun draft(modified: Boolean): ItemDraft {
            val item = db.itemDao().getItemById(1)!!
            val evidence = image(Color.YELLOW)
            val data = ItemDraftData(name = if (modified) "还未保存的名称" else item.name,
                description = if (modified) "保留未完成备注" else item.description,
                brandId = item.brandId, categoryId = item.categoryId, status = item.status, priority = item.priority,
                colors = item.colors, size = item.size, locationId = item.locationId,
                specificationsJson = item.specificationsJson, composition = item.composition,
                baselineUpdatedAt = item.updatedAt, recognitionSessionIds = listOf("保留旧识图会话"))
            val generated = db.imageProvenanceDao().getForItem(1).map { it.imagePath }
            return ItemDraft(targetItemId = 1, dataJson = data.encode(), imagePaths = item.imageUrls,
                evidenceImagePaths = listOf(evidence), appearanceOnlyImagePaths = generated, updatedAt = 700).also { drafts.save(it) }
        }

        suspend fun viewModel(): ItemEditViewModel = withContext(Dispatchers.Main) {
            ItemEditViewModel(items, BrandRepository(db.brandDao(), db.itemDao(), db),
                CategoryRepository(db.categoryDao(), db.itemDao(), db), CoordinateRepository(db.coordinateDao(), db.itemDao(), db),
                PriceRepository(db.priceDao(), db.paymentDao(), context, db), StyleRepository(db.styleDao(), db.itemDao(), db),
                SeasonRepository(db.seasonDao(), db.itemDao(), db), SourceRepository(db.sourceDao(), db.itemDao(), db),
                LocationRepository(db.locationDao(), db.itemDao()), drafts, StorageRepository(db)).also { vm ->
                    viewModelJobs += vm.viewModelScope.coroutineContext.job
                    stores += ViewModelStore().apply { put("cover-detail", vm) }
                }
        }

        suspend fun snapshot(): Preserved {
            val item = db.itemDao().getItemById(1)!!
            val draft = drafts.getForItem(1)
            val tasks = db.imageTaskDao().getAll()
            val paths = (item.imageUrls + draft?.protectedImagePaths().orEmpty() + tasks.flatMap { it.protectedImagePaths() }).distinct()
            return Preserved(item, draft, db.priceDao().getAllPricesList(), db.paymentDao().getAllPaymentsList(),
                db.orderDao().getAllBindings(), db.orderDao().getAllStages(),
                db.imageProvenanceDao().getForItem(1).sortedBy { it.imagePath }, tasks, paths.associateWith(::imageSha256))
        }
    }

    private data class Preserved(val item: Item, val draft: ItemDraft?, val prices: List<Price>, val payments: List<Payment>,
        val bindings: List<OrderBinding>, val stages: List<PaymentStage>, val provenance: List<ImageProvenance>,
        val tasks: List<ImageTask>, val hashes: Map<String, String>)

    @Before fun setup() = runBlocking { fixture = Fixture(); fixture.init() }

    @After fun close() {
        if (!::fixture.isInitialized) return
        val failures = mutableListOf<Throwable>()
        fun cleanup(block: () -> Unit) { runCatching(block).exceptionOrNull()?.let(failures::add) }
        cleanup { instrumentation.runOnMainSync { fixture.stores.forEach { it.clear() } } }
        cleanup { runBlocking { fixture.viewModelJobs.forEach { it.cancelAndJoin() } } }
        cleanup { runBlocking { fixture.scope.coroutineContext.job.cancelAndJoin() } }
        cleanup { fixture.db.close() }
        cleanup { fixture.root.deleteRecursively() }
        cleanup { ImageFileHelper.configure(AppModule.context(), AppModule.database()) }
        MultipleFailureException.assertEmpty(failures)
    }

    private suspend fun showDetail(): ItemEditViewModel {
        val vm = fixture.viewModel()
        // Keep the Activity Context/registry for the real host; all image files are absolute owned paths.
        compose.setContent { LolitaTheme { ItemDetailScreen(1, {}, {}, viewModel = vm) } }
        compose.waitUntil(5_000) { !vm.uiState.value.isLoading && vm.uiState.value.item != null &&
            (vm.uiState.value.purchaseReviewsLoaded || vm.uiState.value.loadError != null) }
        return vm
    }

    private fun makeCover(vm: ItemEditViewModel, path: String) {
        val index = vm.uiState.value.item!!.imageUrls.indexOf(path)
        assertTrue("The generated photo must already belong to the persisted item", index > 0)
        repeat(index) { compose.onNodeWithTag("item-detail-images").performTouchInput { swipeLeft() }; compose.waitForIdle() }
        compose.onNodeWithText("设为封面").assertIsEnabled().performClick()
        compose.waitUntil(5_000) { !vm.uiState.value.isSettingCover }
        assertEquals("A durable edit draft must not silently block the detail cover action", path,
            runBlocking { fixture.db.itemDao().getItemById(1)!!.imageUrls.first() })
        compose.onNodeWithText("当前封面").assertIsDisplayed().assertIsNotEnabled()
    }

    private suspend fun assertPreserved(before: Preserved, cover: String) {
        val after = fixture.snapshot()
        val images = listOf(cover) + before.item.imageUrls.filterNot { it == cover }
        assertEquals(before.item.copy(imageUrls = images, updatedAt = after.item.updatedAt), after.item)
        assertEquals("Detail must not restore, rewrite, delete or rebase the pending draft", before.draft, after.draft)
        assertEquals(before.prices, after.prices); assertEquals(before.payments, after.payments)
        assertEquals(before.bindings, after.bindings); assertEquals(before.stages, after.stages)
        assertEquals(before.provenance, after.provenance); assertEquals(before.tasks, after.tasks)
        assertEquals(before.hashes, after.hashes)
        assertEquals(ImageSourceType.AI_GENERATED, fixture.db.imageProvenanceDao().get(1, cover)!!.sourceType)
        assertTrue(RecognitionRepository(fixture.db, fixture.context).isAppearanceOnly(cover))
    }

    @Test fun modifiedDraftDoesNotBlockLegacyGeneratedCoverOrLoseItsUnfinishedFields() = runBlocking {
        val legacy = fixture.legacyImage()
        fixture.draft(modified = true)
        val before = fixture.snapshot()
        val vm = showDetail()
        makeCover(vm, legacy)
        assertPreserved(before, legacy)
        assertEquals("legacy_display", fixture.db.imageProvenanceDao().get(1, legacy)!!.mode)
    }

    @Test fun unchangedDraftDoesNotDisableTheConfirmedTaskPhotoCover() = runBlocking {
        val generated = fixture.taskImage()
        fixture.draft(modified = false)
        val before = fixture.snapshot()
        val vm = showDetail()
        makeCover(vm, generated)
        assertPreserved(before, generated)
    }

    @Test fun staleDraftCannotBlockDetailObservationOrTheNewTaskPhotoCover() = runBlocking {
        fixture.draft(modified = true)
        val generated = fixture.taskImage()
        val vm = showDetail()
        assertNull("A stale edit draft must not turn the persisted detail into an edit conflict", vm.uiState.value.loadError)
        val legacy = fixture.legacyImage()
        compose.waitUntil(5_000) { legacy in vm.uiState.value.item!!.imageUrls }
        val before = fixture.snapshot()
        makeCover(vm, generated)
        assertPreserved(before, generated)
        withContext(Dispatchers.Main) { vm.loadItem(1) }
        compose.waitUntil(5_000) { vm.uiState.value.hasDraftConflict }
        assertNotNull("Opening the editor after a detail action must still reject the old baseline", vm.uiState.value.loadError)
        assertEquals(before.draft!!.data().baselineUpdatedAt, fixture.drafts.get(before.draft.id)!!.data().baselineUpdatedAt)
    }

    @Test fun readingDetailWithoutAnEditDoesNotCreateADraftIdentity() = runBlocking {
        val legacy = fixture.legacyImage()
        val before = fixture.snapshot()
        val vm = showDetail()
        assertNull("Read-only detail must not enter the durable editor lifecycle", vm.uiState.value.draftId)
        assertNull(fixture.drafts.getForItem(1))
        makeCover(vm, legacy)
        assertPreserved(before, legacy)
    }

    @Test fun editingStillRestoresTheDraftAndRejectsAnImmediateCoverWrite() = runBlocking {
        val legacy = fixture.legacyImage()
        val draft = fixture.draft(modified = true)
        val before = fixture.db.itemDao().getItemById(1)!!
        val vm = fixture.viewModel()
        withContext(Dispatchers.Main) { vm.loadItem(1) }
        withTimeout(5_000) { while (vm.uiState.value.isLoading || vm.uiState.value.name != "还未保存的名称") delay(10) }
        assertTrue(vm.hasUnsavedChanges)
        assertEquals(draft.id, vm.uiState.value.draftId)
        withContext(Dispatchers.Main) { vm.setImageCover(legacy) }
        assertEquals(before, fixture.db.itemDao().getItemById(1))
        assertEquals(legacy, fixture.drafts.get(draft.id)!!.imagePaths.last())
        assertEquals(draft.data().baselineUpdatedAt, fixture.drafts.get(draft.id)!!.data().baselineUpdatedAt)
    }

    @Test fun switchingFromDetailToEditingTheSameItemRestoresItsOriginalDraft() = runBlocking {
        fixture.legacyImage()
        val draft = fixture.draft(modified = true)
        val vm = fixture.viewModel()
        withContext(Dispatchers.Main) { vm.loadDetail(1) }
        withTimeout(5_000) { while (vm.uiState.value.isLoading || !vm.uiState.value.purchaseReviewsLoaded) delay(10) }
        assertNull(vm.uiState.value.draftId)
        assertFalse(vm.hasUnsavedChanges)
        assertEquals("持久原物品", vm.uiState.value.name)
        assertEquals(draft, fixture.drafts.get(draft.id))
        withContext(Dispatchers.Main) { vm.loadItem(1) }
        withTimeout(5_000) { while (vm.uiState.value.isLoading || vm.uiState.value.draftId != draft.id) delay(10) }
        assertNull(vm.uiState.value.loadError)
        assertTrue(vm.hasUnsavedChanges)
        assertEquals("还未保存的名称", vm.uiState.value.name)
        assertEquals("保留未完成备注", vm.uiState.value.description)
        assertEquals(draft.data().baselineUpdatedAt, fixture.drafts.get(draft.id)!!.data().baselineUpdatedAt)
    }

    @Test fun invalidDetailIdsBecomeReadErrorsWithoutCreatingAnEditorDraft() = runBlocking {
        val vm = fixture.viewModel()
        for (id in listOf(0L, -1L)) {
            withContext(Dispatchers.Main) { vm.loadDetail(id) }
            withTimeout(5_000) { while (vm.uiState.value.isLoading) delay(10) }
            assertNotNull(vm.uiState.value.loadError)
            assertNull(vm.uiState.value.item)
            assertNull(vm.uiState.value.draftId)
            assertFalse(vm.hasUnsavedChanges)
            assertTrue(fixture.db.itemDraftDao().getAll().isEmpty())
        }
    }
}
