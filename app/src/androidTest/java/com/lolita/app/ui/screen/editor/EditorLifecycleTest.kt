package com.lolita.app.ui.screen.editor

import android.content.ContextWrapper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.repository.*
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.di.AppModule
import com.lolita.app.ui.screen.coordinate.CoordinateEditViewModel
import com.lolita.app.ui.screen.item.ItemEditViewModel
import com.lolita.app.ui.screen.item.ItemEditScreen
import com.lolita.app.ui.screen.common.SizeOptionsViewModel
import com.lolita.app.ui.screen.outfit.OutfitLogEditScreen
import com.lolita.app.ui.screen.outfit.OutfitLogEditViewModel
import com.lolita.app.ui.theme.LolitaTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executor
import kotlin.coroutines.CoroutineContext

/** Real Room/editor regressions; every image lives in an isolated image root. */
class EditorLifecycleTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var db: LolitaDatabase
    private lateinit var directory: File
    private lateinit var items: ItemRepository
    private lateinit var coordinates: CoordinateRepository
    private lateinit var outfits: OutfitLogRepository
    private val store = ViewModelStore()
    private var key = 0
    private val saveGate = SaveGate()

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java)
            .setQueryCallback({ sql, _ -> saveGate.onQuery(sql) }, Executor { it.run() }).build()
        directory = File(context.cacheDir, "editor-lifecycle-${UUID.randomUUID()}").apply { mkdirs() }
        ImageFileHelper.configure(object : ContextWrapper(context) {
            override fun getFilesDir(): File = directory
        }, db)
        items = ItemRepository(db.itemDao(), database = db)
        coordinates = CoordinateRepository(db.coordinateDao(), db.itemDao(), db)
        outfits = OutfitLogRepository(db.outfitLogDao(), db)
        db.brandDao().insertBrand(Brand(id = 1, name = "编辑测试"))
        db.categoryDao().insertCategory(Category(id = 1, name = "JSK"))
        db.itemDao().insertItem(Item(id = 1, name = "已有服饰", description = "原资料", brandId = 1,
            categoryId = 1, createdAt = 11, updatedAt = 12, status = ItemStatus.WISHED))
        coordinates.insertCoordinate(Coordinate(id = 1, name = "已有套装", description = "原资料", createdAt = 13))
        outfits.insertOutfitLogWithItems(OutfitLog(id = 1, date = 1000, note = "原资料", createdAt = 14), listOf(1))
    }

    @After fun close() = runBlocking {
        withContext(Dispatchers.Main) { store.clear() }
        // Drain cleanup jobs before restoring the process-wide image helper.
        ImageFileHelper.cleanupUnreferencedImages(File(directory, "images").listFiles().orEmpty().map { it.path }).join()
        ImageFileHelper.configure(context, AppModule.database())
        db.close()
        directory.deleteRecursively()
        Unit
    }

    private suspend fun <T : ViewModel> own(factory: () -> T): T = withContext(Dispatchers.Main) {
        factory().also { store.put("editor-${key++}", it) }
    }
    private suspend fun itemEditor() = own { ItemEditViewModel(items,
        BrandRepository(db.brandDao(), db.itemDao(), db), CategoryRepository(db.categoryDao(), db.itemDao(), db),
        coordinates, PriceRepository(db.priceDao(), db.paymentDao(), context, db),
        StyleRepository(db.styleDao(), db.itemDao(), db), SeasonRepository(db.seasonDao(), db.itemDao(), db),
        SourceRepository(db.sourceDao(), db.itemDao(), db), LocationRepository(db.locationDao(), db.itemDao())) }
    private fun photo() = File(File(directory, "images").apply { mkdirs() }, "${UUID.randomUUID()}.png")
        .apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
    private suspend fun settle() { delay(300); instrumentation.waitForIdleSync() }
    private suspend fun awaitDeleted(file: File) = withTimeout(5000) { while (file.exists()) delay(20) }

    /** Pauses only the screen caller's continuation, leaving Room and the VM main scope free. */
    private class ScreenDispatcher : CoroutineDispatcher() {
        private val pending = mutableListOf<Pair<CoroutineContext, Runnable>>()
        @Volatile var paused = false
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            synchronized(pending) {
                if (paused) pending.add(context to block) else Dispatchers.Main.dispatch(context, block)
            }
        }
        fun resume() {
            synchronized(pending) {
                paused = false
                pending.forEach { (context, block) -> Dispatchers.Main.dispatch(context, block) }
                pending.clear()
            }
        }
    }

    private class SaveGate {
        @Volatile private var table: String? = null
        @Volatile private var writeSeen = false
        private var dispatcher: ScreenDispatcher? = null
        private var reached = CountDownLatch(1)
        @Volatile private var blockWrite = false
        private var writeRelease = CountDownLatch(1)
        fun arm(table: String, dispatcher: ScreenDispatcher) {
            reached = CountDownLatch(1)
            this.dispatcher = dispatcher
            writeSeen = false
            this.table = table
        }
        fun blockWrite(table: String) {
            arm(table, ScreenDispatcher())
            writeRelease = CountDownLatch(1)
            blockWrite = true
        }
        fun releaseWrite() = writeRelease.countDown()
        fun onQuery(sql: String) {
            val target = table ?: return
            val normalized = sql.uppercase().replace("`", "")
            if ((normalized.startsWith("INSERT") || normalized.startsWith("UPDATE")) &&
                normalized.contains(target.uppercase())) {
                writeSeen = true
                if (blockWrite) {
                    blockWrite = false
                    table = null
                    reached.countDown()
                    check(writeRelease.await(10, TimeUnit.SECONDS)) { "Test did not release the Room write" }
                }
            }
            if (writeSeen && normalized == "END TRANSACTION") {
                table = null
                dispatcher!!.paused = true
                reached.countDown()
            }
        }
        suspend fun awaitCommitBoundary() = withContext(Dispatchers.IO) {
            check(reached.await(5, TimeUnit.SECONDS)) { "Room did not reach the write transaction boundary" }
        }
    }

    private suspend fun preparedNewItem(): ItemEditViewModel {
        val vm = itemEditor()
        withContext(Dispatchers.Main) { vm.loadItem(0) }
        withTimeout(5000) { vm.uiState.first { !it.isLoading && it.purchaseReviewsLoaded } }
        withContext(Dispatchers.Main) { vm.updateName("待保存服饰"); vm.updateBrand(1); vm.updateCategory(1) }
        return vm
    }

    private suspend fun whileItemWriteBlocked(vm: ItemEditViewModel, body: suspend () -> Unit) {
        saveGate.blockWrite("items")
        val saving = CoroutineScope(SupervisorJob() + Dispatchers.Main).launch { vm.saveItem() }
        try {
            saveGate.awaitCommitBoundary()
            body()
        } finally {
            saveGate.releaseWrite()
            withTimeout(5000) { saving.join() }
        }
    }

    @Test fun savingItemRejectsEveryEditableFieldMutation() = runBlocking {
        val vm = preparedNewItem()
        whileItemWriteBlocked(vm) {
            val before = vm.uiState.value
            assertTrue(before.isSaving)
            withContext(Dispatchers.Main) {
                vm.updateName("不应接受"); vm.updateDescription("不应接受")
                vm.updateBrand(99); vm.updateCategory(99); vm.updateCoordinate(1)
                vm.updateParent(1); vm.updateIncludedInParentPrice(true)
                vm.updateStatus(ItemStatus.WISHED); vm.updatePriority(ItemPriority.HIGH)
                vm.updateColors(listOf("红")); vm.toggleSeason("春")
                vm.updateStyle("古典"); vm.updateSource("测试"); vm.updateSize("XXL"); vm.updateLocation(99)
            }
            assertEquals(before, vm.uiState.value)
        }
    }

    @Test fun savingItemActuallyDisablesFieldsAndSelectorsInCompose() = runBlocking {
        val vm = preparedNewItem()
        val preferenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val preferences = AppPreferences(context, PreferenceDataStoreFactory.create(scope = preferenceScope,
            produceFile = { File(directory, "editor.preferences_pb") }))
        val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
        withContext(Dispatchers.Main) {
            ViewModelProvider(store, object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return SizeOptionsViewModel(preferences, items) as T
                }
            })[SizeOptionsViewModel::class.java]
        }
        compose.setContent { CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            LolitaTheme { ItemEditScreen(null, onBack = {}, onSaveSuccess = {}, viewModel = vm) }
        } }
        try {
            whileItemWriteBlocked(vm) {
                compose.onNodeWithText("服饰名称 *").assertIsNotEnabled()
                compose.onNodeWithText("描述").assertIsNotEnabled()
                compose.onNodeWithText("品牌 *").performScrollTo().assertIsNotEnabled()
                compose.onNodeWithText("尺码").performScrollTo().assertIsNotEnabled()
                compose.onAllNodesWithText("添加图片")[1].performScrollTo().assertIsNotEnabled()
            }
        } finally {
            preferenceScope.cancel()
        }
    }

    private suspend fun cancelScreenAtCommit(table: String, save: suspend () -> Unit, verifyCommitted: suspend () -> Boolean) {
        val dispatcher = ScreenDispatcher()
        val screen = CoroutineScope(SupervisorJob() + dispatcher)
        saveGate.arm(table, dispatcher)
        try {
            screen.launch { save() }
            saveGate.awaitCommitBoundary()
            withTimeout(5000) { while (!verifyCommitted()) delay(20) }
            screen.cancel()
        } finally {
            screen.cancel()
            dispatcher.resume()
        }
        settle()
    }

    @Test fun itemSaveSurvivesCancelledScreenAfterCommitAndRetryDoesNotInsertAgain() = runBlocking {
        val vm = itemEditor()
        withContext(Dispatchers.Main) { vm.loadItem(0) }
        withTimeout(5000) { vm.uiState.first { !it.isLoading && it.purchaseReviewsLoaded } }
        withContext(Dispatchers.Main) { vm.updateName("新建服饰"); vm.updateBrand(1); vm.updateCategory(1) }
        cancelScreenAtCommit("items", { vm.saveItem(); Unit }, { db.itemDao().getAllItemsList().size == 2 })
        val committed = db.itemDao().getAllItemsList().single { it.id != 1L }
        val retry = withContext(Dispatchers.Main) { vm.saveItem() }
        assertTrue(retry.isSuccess)
        assertEquals(committed.id, retry.getOrThrow())
        assertEquals(2, db.itemDao().getAllItemsList().size)
        assertEquals(committed.id, vm.uiState.value.item?.id)
    }

    @Test fun coordinateSaveSurvivesCancelledScreenAfterCommitAndRetryDoesNotInsertAgain() = runBlocking {
        val vm = own { CoordinateEditViewModel(coordinates, items) }
        withContext(Dispatchers.Main) { vm.loadCoordinate(null); vm.updateName("新建套装") }
        cancelScreenAtCommit("coordinates", { vm.save(); Unit }, { db.coordinateDao().getAllCoordinatesList().size == 2 })
        val committed = db.coordinateDao().getAllCoordinatesList().single { it.id != 1L }
        val retry = withContext(Dispatchers.Main) { vm.save() }
        assertTrue(retry.isSuccess)
        assertEquals(committed.id, retry.getOrThrow())
        assertEquals(2, db.coordinateDao().getAllCoordinatesList().size)
    }

    @Test fun outfitSaveSurvivesCancelledScreenAfterCommitAndRetryDoesNotInsertAgain() = runBlocking {
        val vm = own { OutfitLogEditViewModel(outfits, items) }
        withContext(Dispatchers.Main) { vm.loadOutfitLog(null); vm.updateNote("新建穿搭") }
        cancelScreenAtCommit("outfit_logs", { vm.save(); Unit }, { db.outfitLogDao().getAllOutfitLogsList().size == 2 })
        val committed = db.outfitLogDao().getAllOutfitLogsList().single { it.id != 1L }
        val retry = withContext(Dispatchers.Main) { vm.save() }
        assertTrue(retry.isSuccess)
        assertEquals(committed.id, retry.getOrThrow())
        assertEquals(2, db.outfitLogDao().getAllOutfitLogsList().size)
    }

    @Test fun cancelledScreenStillCompletesOldItemAndCoordinateImageCleanup() = runBlocking {
        val itemImage = photo(); val coordinateImage = photo()
        db.itemDao().updateItem(db.itemDao().getItemById(1)!!.copy(imageUrls = listOf(itemImage.path)))
        db.coordinateDao().updateCoordinate(coordinates.getCoordinateById(1)!!.copy(imageUrls = listOf(coordinateImage.path)))
        val item = itemEditor()
        val coordinate = own { CoordinateEditViewModel(coordinates, items) }
        withContext(Dispatchers.Main) { item.loadItem(1); coordinate.loadCoordinate(1) }
        withTimeout(5000) { item.uiState.first { it.item != null && !it.isLoading } }
        withTimeout(5000) { coordinate.uiState.first { it.name.isNotBlank() && !it.isLoading } }
        withContext(Dispatchers.Main) { item.removeImage(0); coordinate.removeImage(0) }
        cancelScreenAtCommit("items", { item.saveItem(); Unit }, { db.itemDao().getItemById(1)!!.imageUrls.isEmpty() })
        awaitDeleted(itemImage)
        cancelScreenAtCommit("coordinates", { coordinate.update(1); Unit }, { coordinates.getCoordinateById(1)!!.imageUrls.isEmpty() })
        awaitDeleted(coordinateImage)
    }

    @Test fun existingItemReloadKeepsDraft() = runBlocking {
        val vm = itemEditor()
        withContext(Dispatchers.Main) { vm.loadItem(1) }
        withTimeout(5000) { vm.uiState.first { it.item?.id == 1L && !it.isLoading } }
        withContext(Dispatchers.Main) { vm.updateName("未保存草稿"); vm.loadItem(1) }
        settle()
        assertEquals("未保存草稿", vm.uiState.value.name)
        assertEquals(11L, vm.uiState.value.item!!.createdAt)
    }

    @Test fun newItemReloadKeepsDraft() = runBlocking {
        val vm = itemEditor()
        withContext(Dispatchers.Main) { vm.loadItem(0) }
        withTimeout(5000) { vm.uiState.first { it.purchaseReviewsLoaded && !it.isLoading } }
        withContext(Dispatchers.Main) { vm.updateName("新增草稿"); vm.loadItem(0) }
        settle()
        assertEquals("新增草稿", vm.uiState.value.name)
    }

    @Test fun existingCoordinateReloadKeepsDraft() = runBlocking {
        val vm = own { CoordinateEditViewModel(coordinates, items) }
        withContext(Dispatchers.Main) { vm.loadCoordinate(1) }
        withTimeout(5000) { vm.uiState.first { it.name == "已有套装" } }
        settle()
        withContext(Dispatchers.Main) { vm.updateName("套装草稿"); vm.loadCoordinate(1) }
        settle()
        assertEquals("套装草稿", vm.uiState.value.name)
    }

    @Test fun existingOutfitReloadKeepsDraftAndOriginalFields() = runBlocking {
        val vm = own { OutfitLogEditViewModel(outfits, items) }
        withContext(Dispatchers.Main) { vm.loadOutfitLog(1) }
        withTimeout(5000) { vm.uiState.first { it.date == 1000L && !it.isLoading } }
        withContext(Dispatchers.Main) { vm.updateNote("穿搭草稿"); vm.loadOutfitLog(1) }
        settle()
        assertEquals("穿搭草稿", vm.uiState.value.note)
        assertTrue(withContext(Dispatchers.Main) { vm.save() }.isSuccess)
        assertEquals(14L, outfits.getOutfitLogById(1)!!.createdAt)
    }

    @Test fun newOutfitReloadKeepsChosenDate() = runBlocking {
        val vm = own { OutfitLogEditViewModel(outfits, items) }
        withContext(Dispatchers.Main) { vm.loadOutfitLog(null); vm.updateDate(12345); vm.loadOutfitLog(null) }
        assertEquals(12345L, vm.uiState.value.date)
    }

    @Test fun switchingEntityResetsDraftAndLateLoadsCannotRestorePreviousRecord() = runBlocking {
        val item = itemEditor()
        val coordinate = own { CoordinateEditViewModel(coordinates, items) }
        val outfit = own { OutfitLogEditViewModel(outfits, items) }
        withContext(Dispatchers.Main) {
            item.loadItem(1); item.loadItem(0)
            coordinate.loadCoordinate(1); coordinate.loadCoordinate(null)
            outfit.loadOutfitLog(1); outfit.loadOutfitLog(null)
        }
        settle()
        assertNull(item.uiState.value.item)
        assertEquals("", item.uiState.value.name)
        assertEquals("", coordinate.uiState.value.name)
        assertTrue(coordinate.uiState.value.selectedItemIds.isEmpty())
        assertEquals("", outfit.uiState.value.note)
        assertTrue(outfit.uiState.value.selectedItemIds.isEmpty())
    }

    @Test fun missingRecordCanBeRetriedAndDoesNotSaveAsNew() = runBlocking {
        val item = itemEditor()
        val coordinate = own { CoordinateEditViewModel(coordinates, items) }
        val outfit = own { OutfitLogEditViewModel(outfits, items) }
        withContext(Dispatchers.Main) {
            item.loadItem(99); coordinate.loadCoordinate(99); outfit.loadOutfitLog(99)
        }
        settle()
        withContext(Dispatchers.Main) {
            item.updateName("不应新增"); item.updateBrand(1); item.updateCategory(1)
        }
        assertTrue(withContext(Dispatchers.Main) { item.saveItem() }.isFailure)
        db.itemDao().insertItem(Item(id = 99, name = "恢复服饰", description = "", brandId = 1, categoryId = 1,
            status = ItemStatus.OWNED))
        coordinates.insertCoordinate(Coordinate(id = 99, name = "恢复套装", description = ""))
        outfits.insertOutfitLog(OutfitLog(id = 99, date = 99, note = "恢复穿搭"))
        withContext(Dispatchers.Main) {
            item.loadItem(99); coordinate.loadCoordinate(99); outfit.loadOutfitLog(99)
        }
        withTimeout(5000) { item.uiState.first { it.item?.id == 99L && !it.isLoading } }
        withTimeout(5000) { coordinate.uiState.first { it.name == "恢复套装" } }
        withTimeout(5000) { outfit.uiState.first { it.note == "恢复穿搭" && !it.isLoading } }
        assertFalse(outfit.uiState.value.isDeleted)
        assertTrue(withContext(Dispatchers.Main) { outfit.save() }.isSuccess)
    }

    @Test fun abandoningEditorsDeletesOnlyNewUnreferencedImages() = runBlocking {
        val item = itemEditor()
        val coordinate = own { CoordinateEditViewModel(coordinates, items) }
        val outfit = own { OutfitLogEditViewModel(outfits, items) }
        val files = List(3) { photo() }
        val shared = photo()
        db.itemDao().updateItem(db.itemDao().getItemById(1)!!.copy(imageUrls = listOf(shared.path)))
        withContext(Dispatchers.Main) {
            item.addImage(files[0].path); item.addImage(shared.path)
            coordinate.addImage(files[1].path); coordinate.addImage(shared.path)
            outfit.addImage(files[2].path); outfit.addImage(shared.path)
            store.clear()
        }
        files.forEach { awaitDeleted(it) }
        assertTrue("persistent shared image survives", shared.exists())
    }

    @Test fun removingNewImagesCleansThemBeforeLeavingEditor() = runBlocking {
        val item = itemEditor()
        val coordinate = own { CoordinateEditViewModel(coordinates, items) }
        val outfit = own { OutfitLogEditViewModel(outfits, items) }
        val files = List(3) { photo() }
        withContext(Dispatchers.Main) {
            item.addImage(files[0].path); item.removeImage(0)
            coordinate.addImage(files[1].path); coordinate.removeImage(0)
            outfit.addImage(files[2].path); outfit.removeImage(files[2].path)
        }
        files.forEach { awaitDeleted(it) }
    }

    @Test fun savedOutfitRemovesOldOrphanAndProtectsSharedAndNewImages() = runBlocking {
        val old = photo(); val shared = photo(); val added = photo()
        db.itemDao().updateItem(db.itemDao().getItemById(1)!!.copy(imageUrls = listOf(shared.path)))
        db.outfitLogDao().updateOutfitLog(outfits.getOutfitLogById(1)!!.copy(imageUrls = listOf(old.path, shared.path)))
        val vm = own { OutfitLogEditViewModel(outfits, items) }
        withContext(Dispatchers.Main) { vm.loadOutfitLog(1) }
        withTimeout(5000) { vm.uiState.first { it.date != null && !it.isLoading } }
        withContext(Dispatchers.Main) { vm.removeImage(old.path); vm.removeImage(shared.path); vm.addImage(added.path) }
        assertTrue(old.exists())
        assertTrue(withContext(Dispatchers.Main) { vm.save() }.isSuccess)
        awaitDeleted(old)
        withContext(Dispatchers.Main) { store.clear() }
        settle()
        assertTrue(shared.exists()); assertTrue(added.exists())
    }

    @Test fun savedItemAndCoordinatePreserveOriginalRecordsAndProtectCommittedImages() = runBlocking {
        val oldItem = photo(); val oldCoordinate = photo(); val newItem = photo(); val newCoordinate = photo()
        val originalItem = db.itemDao().getItemById(1)!!.copy(imageUrls = listOf(oldItem.path),
            colors = listOf("粉色（格纹）"), size = "定制", style = "古典", sizeChartImageUrl = oldItem.path)
        val originalCoordinate = coordinates.getCoordinateById(1)!!.copy(imageUrls = listOf(oldCoordinate.path))
        db.itemDao().updateItem(originalItem)
        db.coordinateDao().updateCoordinate(originalCoordinate)
        val item = itemEditor()
        val coordinate = own { CoordinateEditViewModel(coordinates, items) }
        withContext(Dispatchers.Main) { item.loadItem(1); coordinate.loadCoordinate(1) }
        withTimeout(5000) { item.uiState.first { it.item?.id == 1L && !it.isLoading } }
        withTimeout(5000) { coordinate.uiState.first { it.name == originalCoordinate.name && !it.isLoading } }
        withContext(Dispatchers.Main) {
            item.removeImage(0); item.addImage(newItem.path); item.updateName("新服饰名")
            coordinate.removeImage(0); coordinate.addImage(newCoordinate.path); coordinate.updateName("新套装名")
        }
        assertTrue(oldItem.exists()); assertTrue(oldCoordinate.exists())
        assertTrue(withContext(Dispatchers.Main) { item.saveItem() }.isSuccess)
        assertTrue(withContext(Dispatchers.Main) { coordinate.update(1) }.isSuccess)
        awaitDeleted(oldCoordinate)
        val savedItem = db.itemDao().getItemById(1)!!
        val savedCoordinate = coordinates.getCoordinateById(1)!!
        assertEquals(originalItem.copy(name = "新服饰名", imageUrls = listOf(newItem.path), updatedAt = savedItem.updatedAt), savedItem)
        assertEquals(originalCoordinate.copy(name = "新套装名", imageUrls = listOf(newCoordinate.path), updatedAt = savedCoordinate.updatedAt), savedCoordinate)
        withContext(Dispatchers.Main) { store.clear() }
        settle()
        assertTrue("same file retained as size chart", oldItem.exists())
        assertTrue(newItem.exists()); assertTrue(newCoordinate.exists())
    }

    @Test fun selectedNonOwnedItemCanBeRemovedInRealPicker() = runBlocking {
        val vm = own { OutfitLogEditViewModel(outfits, items) }
        compose.setContent { LolitaTheme { OutfitLogEditScreen(1, {}, {}, vm) } }
        compose.waitUntil(5000) { !vm.uiState.value.isLoading && 1L in vm.uiState.value.selectedItemIds }
        compose.onNodeWithText("选择物品").performScrollTo().performClick()
        compose.onNodeWithText("已有服饰").performClick()
        compose.runOnIdle { assertFalse(1L in vm.uiState.value.selectedItemIds) }
        compose.onNodeWithText("完成").performClick()
        assertTrue(withContext(Dispatchers.Main) { vm.save() }.isSuccess)
        assertTrue(db.outfitLogDao().getOutfitItemCrossRefsByLogId(1).isEmpty())
    }
}
