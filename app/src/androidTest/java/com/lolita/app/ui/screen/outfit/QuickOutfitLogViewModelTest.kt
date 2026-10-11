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
import com.lolita.app.data.repository.BrandRepository
import com.lolita.app.data.repository.CategoryRepository
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.OutfitLogRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
import java.util.Calendar
import java.util.UUID

/** Exercises shortcut edits against real Room transactions, never the AppModule database. */
@RunWith(AndroidJUnit4::class)
class QuickOutfitLogViewModelTest {
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
        photoDirectory = File(context.cacheDir, "quick-outfit-test-${UUID.randomUUID()}")
        check(photoDirectory.mkdirs())
        database.brandDao().insertBrand(Brand(id = 101, name = "月光洋装", createdAt = 1))
        database.brandDao().insertBrand(Brand(id = 102, name = "Classic Alice", createdAt = 2))
        database.categoryDao().insertCategory(Category(id = 307, name = "JSK", createdAt = 3))
        database.categoryDao().insertCategory(Category(id = 308, name = "袜子", createdAt = 4))
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

    private data class Fixture(
        val log: OutfitLog,
        val ownedId: Long,
        val noLongerOwnedId: Long,
        val alternativeId: Long,
        val itemRows: List<Item>,
        val photoBytes: List<ByteArray>
    )

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

    private suspend fun seedTodayLog(): Fixture {
        val ownedId = database.itemDao().insertItem(
            Item(id = 501, brandId = 101, categoryId = 307, name = "月光花园JSK", description = "原服饰",
                status = ItemStatus.OWNED, createdAt = 10, updatedAt = 11)
        )
        val noLongerOwnedId = database.itemDao().insertItem(
            Item(id = 502, brandId = 101, categoryId = 307, name = "已不在衣橱的服饰", description = "历史穿搭关联",
                status = ItemStatus.WISHED, createdAt = 12, updatedAt = 13)
        )
        val alternativeId = database.itemDao().insertItem(
            Item(id = 503, brandId = 102, categoryId = 308, name = "日常袜子", description = "其他分类",
                status = ItemStatus.OWNED, createdAt = 14, updatedAt = 15)
        )
        val photos = listOf(photo("今日穿搭.png", Color.MAGENTA), photo("细节.png", Color.CYAN))
        val originalDate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 37)
            set(Calendar.SECOND, 23)
            set(Calendar.MILLISECOND, 456)
        }.timeInMillis
        val log = OutfitLog(
            id = 701,
            date = originalDate,
            note = "原备注",
            imageUrls = photos.map { it.absolutePath },
            createdAt = 21,
            updatedAt = 22
        )
        outfitLogs.insertOutfitLogWithItems(log, listOf(ownedId, noLongerOwnedId))
        return Fixture(log, ownedId, noLongerOwnedId, alternativeId,
            database.itemDao().getAllItemsList().sortedBy { it.id }, photos.map { it.readBytes() })
    }

    private fun newViewModel(): QuickOutfitLogViewModel {
        lateinit var result: QuickOutfitLogViewModel
        instrumentation.runOnMainSync {
            result = QuickOutfitLogViewModel(
                outfitLogRepository = outfitLogs,
                itemRepository = ItemRepository(database.itemDao(), database = database),
                brandRepository = BrandRepository(database.brandDao(), database.itemDao(), database),
                categoryRepository = CategoryRepository(database.categoryDao(), database.itemDao(), database)
            )
            viewModelStore.put("quick-outfit", result)
        }
        return result
    }

    private suspend fun loaded(viewModel: QuickOutfitLogViewModel): QuickOutfitLogUiState {
        val state = withTimeout(10_000) { viewModel.uiState.first { !it.isLoading } }
        assertFalse(state.loadFailed)
        assertNull(state.error)
        return state
    }

    private suspend fun save(viewModel: QuickOutfitLogViewModel) {
        instrumentation.runOnMainSync { viewModel.save() }
        val state = withTimeout(10_000) {
            viewModel.uiState.first { it.saveSuccess || it.error != null }
        }
        assertNull(state.error)
        assertTrue(state.saveSuccess)
        assertFalse(state.isSaving)
    }

    private suspend fun assertSaved(
        fixture: Fixture,
        expectedNote: String,
        expectedItemIds: Set<Long>
    ) {
        val saved = database.outfitLogDao().getOutfitLogById(fixture.log.id)!!
        // A full data-class comparison protects every original record field;
        // only the note and the repository-managed updatedAt may change.
        assertEquals(fixture.log.copy(note = expectedNote, updatedAt = saved.updatedAt), saved)
        assertTrue(saved.updatedAt > fixture.log.updatedAt)
        assertEquals(listOf(fixture.log.id), database.outfitLogDao().getAllOutfitLogsList().map { it.id })
        assertEquals(expectedItemIds,
            database.outfitLogDao().getOutfitItemCrossRefsByLogId(fixture.log.id).map { it.itemId }.toSet())
        assertEquals(fixture.itemRows, database.itemDao().getAllItemsList().sortedBy { it.id })
        fixture.log.imageUrls.forEachIndexed { index, path ->
            assertArrayEquals("photo $index", fixture.photoBytes[index], File(path).readBytes())
        }
        database.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use {
            assertFalse(it.moveToFirst())
        }
    }

    @Test
    fun filteringAndSavingExistingLogPreservesPhotosDateCreationAndHiddenSelection() = runBlocking {
        val fixture = seedTodayLog()
        val viewModel = newViewModel()
        val initial = loaded(viewModel)
        val originalSelection = setOf(fixture.ownedId, fixture.noLongerOwnedId)
        assertEquals(fixture.log.id, initial.existingLogId)
        assertEquals(originalSelection, initial.selectedItemIds)
        assertEquals(originalSelection, initial.selectedItems.map { it.id }.toSet())
        assertFalse(initial.items.any { it.id == fixture.noLongerOwnedId })

        instrumentation.runOnMainSync {
            viewModel.updateSearchQuery(" CLASSIC ")
            viewModel.selectCategory(308)
            viewModel.updateNote("今天换了备注")
        }
        val filtered = viewModel.uiState.value
        assertEquals(listOf(fixture.alternativeId), filtered.visibleItems.map { it.id })
        assertEquals(originalSelection, filtered.selectedItemIds)
        assertEquals(originalSelection, filtered.selectedItems.map { it.id }.toSet())

        save(viewModel)
        assertSaved(fixture, "今天换了备注", originalSelection)
    }

    @Test
    fun removingNoLongerOwnedItemFromPreviewUpdatesLinksWithoutChangingOriginalLogFields() = runBlocking {
        val fixture = seedTodayLog()
        val viewModel = newViewModel()
        loaded(viewModel)
        instrumentation.runOnMainSync {
            viewModel.selectCategory(308)
            viewModel.updateSearchQuery("袜子")
            viewModel.toggleItem(fixture.noLongerOwnedId)
        }
        assertEquals(setOf(fixture.ownedId), viewModel.uiState.value.selectedItemIds)
        assertEquals(listOf(fixture.ownedId), viewModel.uiState.value.selectedItems.map { it.id })
        assertEquals(listOf(fixture.alternativeId), viewModel.uiState.value.visibleItems.map { it.id })
        instrumentation.runOnMainSync { viewModel.toggleItem(fixture.alternativeId) }

        save(viewModel)
        assertSaved(fixture, fixture.log.note, setOf(fixture.ownedId, fixture.alternativeId))
    }
}
