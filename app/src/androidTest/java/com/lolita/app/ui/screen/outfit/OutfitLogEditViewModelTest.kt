package com.lolita.app.ui.screen.outfit

import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.Brand
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.local.entity.OutfitLog
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.OutfitLogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Exercises full-editor saves against isolated Room records and real photo files. */
@RunWith(AndroidJUnit4::class)
class OutfitLogEditViewModelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var database: LolitaDatabase
    private lateinit var viewModelStore: ViewModelStore
    private lateinit var outfitLogs: OutfitLogRepository
    private lateinit var photoDirectory: File

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        viewModelStore = ViewModelStore()
        outfitLogs = OutfitLogRepository(database.outfitLogDao(), database)
        photoDirectory = File(context.cacheDir, "outfit-edit-test-${UUID.randomUUID()}")
        check(photoDirectory.mkdirs())
        database.brandDao().insertBrand(Brand(id = 101, name = "月光洋装", createdAt = 1))
        database.categoryDao().insertCategory(Category(id = 307, name = "JSK", createdAt = 2))
        database.itemDao().insertItem(
            Item(id = 501, brandId = 101, categoryId = 307, name = "月光花园JSK", description = "原服饰",
                status = ItemStatus.OWNED, createdAt = 10, updatedAt = 11)
        )
        database.itemDao().insertItem(
            Item(id = 502, brandId = 101, categoryId = 307, name = "历史穿搭服饰", description = "保留关联",
                status = ItemStatus.WISHED, createdAt = 12, updatedAt = 13)
        )
        Unit
    }

    @After
    fun tearDown() {
        if (::viewModelStore.isInitialized) {
            instrumentation.runOnMainSync { viewModelStore.clear() }
            instrumentation.waitForIdleSync()
        }
        if (::database.isInitialized) database.close()
        if (::photoDirectory.isInitialized) photoDirectory.deleteRecursively()
    }

    private data class Fixture(val log: OutfitLog, val photoBytes: List<ByteArray>)

    private fun photo(name: String, color: Int): File {
        val file = File(photoDirectory, name)
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
        return file
    }

    private suspend fun seedExistingLog(): Fixture {
        val photos = listOf(photo("穿搭.png", Color.MAGENTA), photo("细节.png", Color.CYAN))
        val log = OutfitLog(
            id = 701,
            date = 1_700_000_123_456,
            note = "原备注",
            imageUrls = photos.map { it.absolutePath },
            createdAt = 21,
            updatedAt = 22
        )
        outfitLogs.insertOutfitLogWithItems(log, listOf(501, 502))
        return Fixture(log, photos.map { it.readBytes() })
    }

    private fun newViewModel(): OutfitLogEditViewModel {
        lateinit var result: OutfitLogEditViewModel
        instrumentation.runOnMainSync {
            result = OutfitLogEditViewModel(
                outfitLogRepository = outfitLogs,
                itemRepository = ItemRepository(database.itemDao(), database = database)
            )
            viewModelStore.put("outfit-edit", result)
        }
        return result
    }

    private suspend fun load(viewModel: OutfitLogEditViewModel, logId: Long): OutfitLogEditUiState {
        instrumentation.runOnMainSync { viewModel.loadOutfitLog(logId) }
        return withTimeout(10_000) { viewModel.uiState.first { it.date != null || it.isDeleted } }
    }

    private fun assertPhotosUnchanged(fixture: Fixture) {
        fixture.log.imageUrls.forEachIndexed { index, path ->
            assertArrayEquals("photo $index", fixture.photoBytes[index], File(path).readBytes())
        }
    }

    @Test
    fun editingExistingLogPreservesCreationDatePhotosAndItemLinks() = runBlocking {
        val fixture = seedExistingLog()
        val itemRows = database.itemDao().getAllItemsList().sortedBy { it.id }
        val viewModel = newViewModel()
        val initial = load(viewModel, fixture.log.id)
        assertEquals(setOf(501L, 502L), initial.selectedItemIds)
        instrumentation.runOnMainSync { viewModel.updateNote("编辑后的备注") }

        val beforeSave = System.currentTimeMillis()
        val result = withContext(Dispatchers.Main) { viewModel.save() }
        val afterSave = System.currentTimeMillis()

        assertTrue(result.isSuccess)
        assertEquals(701L, result.getOrThrow())
        val saved = database.outfitLogDao().getOutfitLogById(701)!!
        assertEquals("creation time must survive editing", 21L, saved.createdAt)
        assertEquals(1_700_000_123_456L, saved.date)
        assertEquals("编辑后的备注", saved.note)
        assertEquals(fixture.log.imageUrls, saved.imageUrls)
        assertTrue(saved.updatedAt in beforeSave..afterSave)
        assertTrue(saved.updatedAt > 22L)
        assertEquals(listOf(701L), database.outfitLogDao().getAllOutfitLogsList().map { it.id })
        assertEquals(setOf(501L, 502L),
            database.outfitLogDao().getOutfitItemCrossRefsByLogId(701).map { it.itemId }.toSet())
        assertEquals(itemRows, database.itemDao().getAllItemsList().sortedBy { it.id })
        assertPhotosUnchanged(fixture)
        assertFalse(viewModel.uiState.value.isSaving)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun savingNewLogPersistsChosenFieldsAndInitialCreationTime() = runBlocking {
        val image = photo("新增穿搭.png", Color.YELLOW)
        val imageBytes = image.readBytes()
        val viewModel = newViewModel()
        instrumentation.runOnMainSync {
            viewModel.loadOutfitLog(null)
            viewModel.updateDate(1_710_000_987_654)
            viewModel.updateNote("新增穿搭")
            viewModel.addImage(image.absolutePath)
            viewModel.toggleItemSelection(501)
        }

        val beforeSave = System.currentTimeMillis()
        val result = withContext(Dispatchers.Main) { viewModel.save() }
        val afterSave = System.currentTimeMillis()

        assertTrue(result.isSuccess)
        val logId = result.getOrThrow()
        val saved = database.outfitLogDao().getOutfitLogById(logId)!!
        assertTrue(logId > 0)
        assertEquals(1_710_000_987_654L, saved.date)
        assertEquals("新增穿搭", saved.note)
        assertEquals(listOf(image.absolutePath), saved.imageUrls)
        assertTrue(saved.createdAt in beforeSave..afterSave)
        assertTrue(saved.updatedAt in beforeSave..afterSave)
        assertEquals(listOf(logId), database.outfitLogDao().getAllOutfitLogsList().map { it.id })
        assertEquals(listOf(501L),
            database.outfitLogDao().getOutfitItemCrossRefsByLogId(logId).map { it.itemId })
        assertArrayEquals(imageBytes, image.readBytes())
        assertFalse(viewModel.uiState.value.isSaving)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun savingBeforeExistingLogLoadsDoesNotOverwriteItsData() = runBlocking {
        val fixture = seedExistingLog()
        val viewModel = newViewModel()

        // Keep both calls on the same main turn: the Room result cannot update
        // the editor before save checks whether its original record is loaded.
        val result = withContext(Dispatchers.Main) {
            viewModel.loadOutfitLog(fixture.log.id)
            viewModel.save()
        }

        assertTrue(result.isFailure)
        assertEquals(fixture.log, database.outfitLogDao().getOutfitLogById(701))
        assertEquals(setOf(501L, 502L),
            database.outfitLogDao().getOutfitItemCrossRefsByLogId(701).map { it.itemId }.toSet())
        assertPhotosUnchanged(fixture)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun loadingDeletedLogRejectsSaveWithoutRecreatingIt() = runBlocking {
        val fixture = seedExistingLog()
        database.outfitLogDao().deleteOutfitLogById(fixture.log.id)
        val viewModel = newViewModel()
        assertTrue(load(viewModel, fixture.log.id).isDeleted)

        val result = withContext(Dispatchers.Main) { viewModel.save() }

        assertTrue(result.isFailure)
        assertTrue(database.outfitLogDao().getAllOutfitLogsList().isEmpty())
        assertTrue(database.outfitLogDao().getAllOutfitItemCrossRefsList().isEmpty())
        assertPhotosUnchanged(fixture)
        assertFalse(viewModel.uiState.value.isSaving)
    }
}
