package com.lolita.app.ui.screen.item

import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.dao.OutfitLogWithItems
import com.lolita.app.data.local.entity.Brand
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.local.entity.OutfitLog
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.data.repository.BrandRepository
import com.lolita.app.data.repository.CategoryRepository
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.LocationRepository
import com.lolita.app.data.repository.OutfitLogRepository
import com.lolita.app.data.repository.PriceRepository
import com.lolita.app.ui.screen.outfit.QuickOutfitLogViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Verifies the homepage shortcut against a separate Room database, never AppModule data. */
@RunWith(AndroidJUnit4::class)
class TodayOutfitViewModelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var database: LolitaDatabase
    private lateinit var viewModelStore: ViewModelStore
    private lateinit var logs: OutfitLogRepository
    private val now = AtomicLong(System.currentTimeMillis())
    private val zone = AtomicReference(TimeZone.getDefault())

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        viewModelStore = ViewModelStore()
        logs = OutfitLogRepository(database.outfitLogDao(), database)
        now.set(System.currentTimeMillis())
        zone.set(TimeZone.getDefault())
        database.brandDao().insertBrand(Brand(id = 101, name = "测试品牌"))
        database.categoryDao().insertCategory(Category(id = 301, name = "JSK"))
        database.itemDao().insertItem(Item(id = 501, brandId = 101, categoryId = 301,
            name = "第一件", description = "", status = ItemStatus.OWNED, imageUrls = listOf("/test/first.jpg")))
        database.itemDao().insertItem(Item(id = 502, brandId = 101, categoryId = 301,
            name = "第二件", description = "", status = ItemStatus.OWNED, imageUrls = listOf("/test/second.jpg")))
        Unit
    }

    @After
    fun tearDown() {
        if (::viewModelStore.isInitialized) {
            instrumentation.runOnMainSync { viewModelStore.clear() }
            instrumentation.waitForIdleSync()
        }
        if (::database.isInitialized) database.close()
    }

    private fun homeViewModel(): ItemListViewModel {
        lateinit var result: ItemListViewModel
        instrumentation.runOnMainSync {
            result = ItemListViewModel(
                itemRepository = ItemRepository(database.itemDao(), database = database),
                brandRepository = BrandRepository(database.brandDao(), database.itemDao(), database),
                categoryRepository = CategoryRepository(database.categoryDao(), database.itemDao(), database),
                priceRepository = PriceRepository(database.priceDao(), database.paymentDao(), context, database),
                appPreferences = AppPreferences(context),
                locationRepository = LocationRepository(database.locationDao(), database.itemDao()),
                outfitLogRepository = logs,
                currentTimeMillis = now::get,
                currentTimeZone = zone::get
            )
            viewModelStore.put("home", result)
        }
        return result
    }

    private fun quickViewModel(): QuickOutfitLogViewModel {
        lateinit var result: QuickOutfitLogViewModel
        instrumentation.runOnMainSync {
            result = QuickOutfitLogViewModel(
                outfitLogRepository = logs,
                itemRepository = ItemRepository(database.itemDao(), database = database),
                brandRepository = BrandRepository(database.brandDao(), database.itemDao(), database),
                categoryRepository = CategoryRepository(database.categoryDao(), database.itemDao(), database)
            )
            viewModelStore.put("quick", result)
        }
        return result
    }

    private suspend fun waitForHome(
        viewModel: ItemListViewModel,
        predicate: (ItemListUiState) -> Boolean
    ): ItemListUiState = withTimeout(10_000) { viewModel.uiState.first(predicate) }

    private fun timestamp(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance(zone.get()).apply {
            clear()
            set(year, month - 1, day, hour, 0, 0)
        }.timeInMillis

    @Test
    fun quickSaveAndLaterEditsAndDeletionRefreshExistingHomepage() = runBlocking<Unit> {
        val home = homeViewModel()
        waitForHome(home) { !it.isLoading }
        assertFalse(home.uiState.value.hasTodayOutfit)
        val quick = quickViewModel()
        withTimeout(10_000) { quick.uiState.first { !it.isLoading } }
        instrumentation.runOnMainSync {
            quick.toggleItem(501)
            quick.updateNote("实时记录")
            quick.save()
        }
        withTimeout(10_000) { quick.uiState.first { it.saveSuccess || it.error != null } }
        assertNull(quick.uiState.value.error)
        assertTrue(quick.uiState.value.saveSuccess)
        val saved = logs.getTodayOutfitLog()!!
        val afterSave = waitForHome(home) { it.todayOutfitLogId == saved.outfitLog.id }
        assertTrue(afterSave.hasTodayOutfit)
        assertEquals(listOf("/test/first.jpg"), afterSave.todayOutfitItemImages)

        logs.updateOutfitLogWithItems(saved.outfitLog.copy(note = "改选第二件"), listOf(502))
        waitForHome(home) { it.todayOutfitItemImages == listOf("/test/second.jpg") }
        val item = database.itemDao().getItemById(502)!!
        database.itemDao().updateItem(item.copy(imageUrls = listOf("/test/changed.jpg")))
        waitForHome(home) { it.todayOutfitItemImages == listOf("/test/changed.jpg") }

        logs.deleteOutfitLogById(saved.outfitLog.id)
        val afterDelete = waitForHome(home) { !it.hasTodayOutfit && it.todayOutfitLogId == null }
        assertEquals(emptyList<String?>(), afterDelete.todayOutfitItemImages)
    }

    @Test
    fun sameDayLatestRecordAndIdTieBreakerAgreeForHomeAndShortcut() = runBlocking<Unit> {
        // The shortcut uses the real local day; keep fixture times within that same day.
        val range = logs.dayRangeAt(now.get(), zone.get())
        logs.insertOutfitLogWithItems(OutfitLog(note = "", id = 701, date = range.start + 1_000), listOf(501))
        logs.insertOutfitLogWithItems(OutfitLog(note = "", id = 702, date = range.start + 2_000), listOf(502))
        logs.insertOutfitLogWithItems(OutfitLog(note = "", id = 703, date = range.start + 2_000), listOf(501))
        val home = homeViewModel()
        val quick = quickViewModel()
        waitForHome(home) { it.todayOutfitLogId == 703L }
        val quickState = withTimeout(10_000) { quick.uiState.first { !it.isLoading } }
        assertEquals(703L, quickState.existingLogId)
        assertEquals(setOf(501L), quickState.selectedItemIds)
        assertEquals(703L, logs.getTodayOutfitLog().outfitLogId())

        logs.deleteOutfitLogById(703)
        waitForHome(home) { it.todayOutfitLogId == 702L }
        assertEquals(702L, logs.getTodayOutfitLog().outfitLogId())
    }

    @Test
    fun dayAndTimeZoneChangesReplaceTheObservedRange() = runBlocking<Unit> {
        zone.set(TimeZone.getTimeZone("UTC"))
        now.set(timestamp(2030, 1, 1, 23))
        logs.insertOutfitLogWithItems(OutfitLog(note = "", id = 711, date = timestamp(2030, 1, 1, 12)), listOf(501))
        logs.insertOutfitLogWithItems(OutfitLog(note = "", id = 712, date = timestamp(2030, 1, 2, 12)), listOf(502))
        val home = homeViewModel()
        waitForHome(home) { it.todayOutfitLogId == 711L }

        // Midnight crossing and returning to the foreground use the same refresh hook.
        now.set(timestamp(2030, 1, 2, 23))
        instrumentation.runOnMainSync { home.refreshTodayOutfitDay() }
        waitForHome(home) { it.todayOutfitLogId == 712L }
        assertEquals(712L, logs.getTodayOutfitLog(now.get(), zone.get()).outfitLogId())

        // At the same instant, UTC+8 is already the next local day and has no record.
        zone.set(TimeZone.getTimeZone("Asia/Shanghai"))
        instrumentation.runOnMainSync { home.refreshTodayOutfitDay() }
        waitForHome(home) { !it.hasTodayOutfit && it.todayOutfitLogId == null }
        assertNull(logs.getTodayOutfitLog(now.get(), zone.get()))

        // Manual clock changes back into a day with a record restart the subscription too.
        now.set(timestamp(2030, 1, 2, 12))
        instrumentation.runOnMainSync { home.refreshTodayOutfitDay() }
        waitForHome(home) { it.todayOutfitLogId == 712L }
    }

    @Test
    fun localDayBoundsRespectDaylightSavingAndExcludeFollowingMidnight() = runBlocking<Unit> {
        zone.set(TimeZone.getTimeZone("America/New_York"))
        val spring = logs.dayRangeAt(timestamp(2030, 3, 10), zone.get())
        val autumn = logs.dayRangeAt(timestamp(2030, 11, 3), zone.get())
        assertEquals(23 * 60 * 60 * 1_000L, spring.endExclusive - spring.start)
        assertEquals(25 * 60 * 60 * 1_000L, autumn.endExclusive - autumn.start)
        logs.insertOutfitLogWithItems(OutfitLog(note = "", id = 721, date = spring.endExclusive - 1), listOf(501))
        logs.insertOutfitLogWithItems(OutfitLog(note = "", id = 722, date = spring.endExclusive), listOf(502))
        assertEquals(721L, logs.observeOutfitLogByDay(spring).first().outfitLogId())
    }

    private fun OutfitLogWithItems?.outfitLogId(): Long? = this?.outfitLog?.id
}
