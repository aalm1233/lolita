package com.lolita.app.ui.screen.item

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.Brand
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemPurchaseReview
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.local.entity.OrderBinding
import com.lolita.app.data.local.entity.Payment
import com.lolita.app.data.local.entity.PaymentStage
import com.lolita.app.data.local.entity.Price
import com.lolita.app.data.local.entity.PriceType
import com.lolita.app.data.local.entity.PurchaseRecordStatus
import com.lolita.app.data.local.entity.RestoreJournal
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.data.repository.BrandRepository
import com.lolita.app.data.repository.CategoryRepository
import com.lolita.app.data.repository.CoordinateRepository
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.LocationRepository
import com.lolita.app.data.repository.OrderRepository
import com.lolita.app.data.repository.OutfitLogRepository
import com.lolita.app.data.repository.PriceRepository
import com.lolita.app.data.repository.SeasonRepository
import com.lolita.app.data.repository.SourceRepository
import com.lolita.app.data.repository.StyleRepository
import com.lolita.app.ui.screen.stats.StatsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
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
import java.io.File
import java.util.UUID

/** All entry points observe the same ledger projection, including after opening or rebuilding them. */
@RunWith(AndroidJUnit4::class)
class PurchaseReviewConsistencyTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var database: LolitaDatabase
    private lateinit var viewModelStore: ViewModelStore
    private lateinit var preferencesDirectory: File
    private lateinit var preferencesScope: CoroutineScope
    private lateinit var preferences: AppPreferences
    private lateinit var items: ItemRepository
    private lateinit var prices: PriceRepository
    private lateinit var orders: OrderRepository
    private var generation = 0
    // Ledger mutations reconcile Android PendingIntent identities, even for in-memory Room.
    private var nextPriceId = 1_832_200_001L
    private var nextStageId = 1_832_300_001L
    private var nextPaymentId = 1_832_400_001L

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        viewModelStore = ViewModelStore()
        preferencesDirectory = File(context.cacheDir, "purchase-review-consistency-${UUID.randomUUID()}")
        check(preferencesDirectory.mkdirs())
        preferencesScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        preferences = AppPreferences(context, PreferenceDataStoreFactory.create(scope = preferencesScope,
            produceFile = { File(preferencesDirectory, "settings.preferences_pb") }))
        items = ItemRepository(database.itemDao(), database = database)
        prices = PriceRepository(database.priceDao(), database.paymentDao(), context, database)
        orders = OrderRepository(database, context)
        generation = 0
        nextPriceId = 1_832_200_001L
        nextStageId = 1_832_300_001L
        nextPaymentId = 1_832_400_001L
        database.brandDao().insertBrand(Brand(id = 101, name = "购买核对一致性品牌"))
        database.categoryDao().insertCategory(Category(id = 301, name = "购买核对一致性分类"))
        Unit
    }

    @After
    fun tearDown() = runBlocking<Unit> {
        if (::viewModelStore.isInitialized) {
            instrumentation.runOnMainSync { viewModelStore.clear() }
            instrumentation.waitForIdleSync()
        }
        if (::preferencesScope.isInitialized) preferencesScope.coroutineContext[Job]!!.cancelAndJoin()
        if (::database.isInitialized) database.close()
        if (::preferencesDirectory.isInitialized) preferencesDirectory.deleteRecursively()
    }

    private data class Views(
        val home: ItemListViewModel,
        val filtered: FilteredItemListViewModel,
        val detail: ItemEditViewModel,
        val stats: StatsViewModel,
        val detailItemId: Long
    )

    private fun views(detailItemId: Long): Views {
        lateinit var result: Views
        instrumentation.runOnMainSync {
            val brands = BrandRepository(database.brandDao(), database.itemDao(), database)
            val categories = CategoryRepository(database.categoryDao(), database.itemDao(), database)
            val coordinates = CoordinateRepository(database.coordinateDao(), database.itemDao(), database)
            val locations = LocationRepository(database.locationDao(), database.itemDao())
            val logs = OutfitLogRepository(database.outfitLogDao(), database)
            val home = ItemListViewModel(itemRepository = items, brandRepository = brands,
                categoryRepository = categories, priceRepository = prices, appPreferences = preferences,
                locationRepository = locations, outfitLogRepository = logs)
            val filtered = FilteredItemListViewModel(filterType = "status_review", filterValue = "",
                itemRepository = items, priceRepository = prices)
            val detail = ItemEditViewModel(itemRepository = items, brandRepository = brands,
                categoryRepository = categories, coordinateRepository = coordinates, priceRepository = prices,
                styleRepository = StyleRepository(database.styleDao(), database.itemDao(), database),
                seasonRepository = SeasonRepository(database.seasonDao(), database.itemDao(), database),
                sourceRepository = SourceRepository(database.sourceDao(), database.itemDao(), database),
                locationRepository = locations)
            val stats = StatsViewModel(itemRepository = items, coordinateRepository = coordinates,
                outfitLogRepository = logs, priceRepository = prices, appPreferences = preferences)
            val key = generation++
            viewModelStore.put("home-$key", home)
            viewModelStore.put("filtered-$key", filtered)
            viewModelStore.put("detail-$key", detail)
            viewModelStore.put("stats-$key", stats)
            detail.loadItem(detailItemId)
            result = Views(home, filtered, detail, stats, detailItemId)
        }
        return result
    }

    private suspend fun assertConsistent(views: Views, expectedIds: Set<Long>): List<ItemPurchaseReview> {
        val expected = items.getPurchaseReviews().first().sortedBy { it.item.id }
        assertEquals(expectedIds, expected.map { it.item.id }.toSet())
        assertEquals(expectedIds.size, expected.size)
        val home = withTimeout(10_000) { views.home.uiState.first {
            !it.isLoading && it.purchaseReviews.sortedBy { review -> review.item.id } == expected
        } }
        val filtered = withTimeout(10_000) { views.filtered.uiState.first {
            !it.isLoading && it.purchaseReviews.sortedBy { review -> review.item.id } == expected
        } }
        val detail = withTimeout(10_000) { views.detail.uiState.first {
            !it.isLoading && it.item?.id == views.detailItemId &&
                it.purchaseReviews.sortedBy { review -> review.item.id } == expected
        } }
        val stats = withTimeout(10_000) { views.stats.uiState.first {
            !it.isLoading && it.purchaseReviews.sortedBy { review -> review.item.id } == expected
        } }
        assertEquals(expected, home.purchaseReviews.sortedBy { it.item.id })
        assertEquals(expected, filtered.purchaseReviews.sortedBy { it.item.id })
        assertEquals(expected, detail.purchaseReviews.sortedBy { it.item.id })
        assertEquals(expected, stats.purchaseReviews.sortedBy { it.item.id })
        assertEquals(expected.map { it.item }.sortedBy { it.id }, filtered.items.sortedBy { it.id })
        return expected
    }

    private suspend fun item(
        name: String,
        status: ItemStatus = ItemStatus.PENDING_REVIEW,
        parentId: Long? = null,
        shared: Boolean = false
    ): Long = items.insertItem(Item(brandId = 101, categoryId = 301, name = name,
        description = "原购买资料\n引号\"与反斜线\\完整保留", status = status,
        source = "原订单来源", parentItemId = parentId, includedInParentPrice = shared,
        createdAt = 11, updatedAt = 12))

    private suspend fun order(
        itemId: Long,
        evidence: PurchaseRecordStatus,
        total: Double = 100.01,
        paid: Double = 30.02,
        paidDate: Long? = 1_700_000_000_000L,
        bind: Boolean = true
    ): Long {
        val priceId = database.priceDao().insertPrice(Price(id = nextPriceId++, itemId = itemId,
            type = PriceType.FULL, totalPrice = total, shipping = 3.01,
            purchaseRecordStatus = evidence, createdAt = 13))
        val stageId = database.orderDao().insertStage(PaymentStage(id = nextStageId++, priceId = priceId,
            name = "原计划保留", amount = total, reminderSet = false))
        database.paymentDao().insertPayment(Payment(id = nextPaymentId++, priceId = priceId,
            stageId = stageId, amount = paid, dueDate = paidDate ?: 0L, paidDate = paidDate,
            isPaid = true, reminderSet = false, calendarEventId = null, createdAt = 14))
        if (bind) orders.bindHistoricalOrder(itemId, priceId)
        return priceId
    }

    private data class RawState(
        val items: List<Item>,
        val prices: List<Price>,
        val payments: List<Payment>,
        val stages: List<PaymentStage>,
        val bindings: List<OrderBinding>,
        val journal: RestoreJournal?
    )

    private suspend fun raw() = RawState(database.itemDao().getAllItemsList().sortedBy { it.id },
        database.priceDao().getAllPricesList().sortedBy { it.id },
        database.paymentDao().getAllPaymentsList().sortedBy { it.id },
        database.orderDao().getAllStages().sortedBy { it.id },
        database.orderDao().getAllBindings().sortedBy { it.itemId }, database.restoreJournalDao().get())

    @Test
    fun allEntryPointsShareCurrentLedgerReasonsAndOpeningOrRebuildingThemIsReadOnly() = runBlocking<Unit> {
        val parent = item("购买状态未知的主裙")
        val parentPrice = order(parent, PurchaseRecordStatus.NEEDS_REVIEW)
        val shared = item("共价小物", parentId = parent, shared = true)
        val independent = item("独立小物", parentId = parent)
        val independentPrice = order(independent, PurchaseRecordStatus.NEEDS_REVIEW, total = 20.05, paid = 5.01)
        val historical = item("已确认拥有但资料不完整的裙子")
        order(historical, PurchaseRecordStatus.HISTORICAL_OWNED)
        val unknownDate = item("账目完整且实付日期未知的裙子")
        order(unknownDate, PurchaseRecordStatus.TRACKED, paidDate = null)
        val unpriced = item("没有价格资料的已拥有裙子", ItemStatus.OWNED)
        val archivedOnly = item("只有旧价格还未选定订单的裙子", ItemStatus.OWNED)
        val archivedOnlyPrice = order(archivedOnly, PurchaseRecordStatus.NEEDS_REVIEW, bind = false)
        val normalOwing = item("实付已完整核对的正常欠款裙子")
        order(normalOwing, PurchaseRecordStatus.TRACKED)
        val unrelatedArchive = order(normalOwing, PurchaseRecordStatus.NEEDS_REVIEW,
            paidDate = null, total = 150.02, paid = 10.01, bind = false)
        val wished = item("纯愿望裙", ItemStatus.WISHED)
        val expected = setOf(parent, shared, independent, historical, unknownDate, unpriced, archivedOnly)
        val before = raw()
        val initial = views(parent)
        val reviews = assertConsistent(initial, expected).associateBy { it.item.id }
        assertEquals(parent, reviews.getValue(shared).orderItemId)
        assertEquals(parentPrice, reviews.getValue(shared).priceId)
        assertEquals(independent, reviews.getValue(independent).orderItemId)
        assertEquals(independentPrice, reviews.getValue(independent).priceId)
        assertTrue(reviews.getValue(unknownDate).hasUnknownPaymentDates)
        assertEquals(PurchaseRecordStatus.HISTORICAL_OWNED, reviews.getValue(historical).purchaseRecordStatus)
        assertNull(reviews.getValue(unpriced).priceId)
        assertNull(reviews.getValue(archivedOnly).priceId)
        assertFalse(reviews.containsKey(wished))
        assertFalse(reviews.containsKey(normalOwing))
        withTimeout(10_000) { initial.stats.uiState.first { it.incompleteCurrentOrderCount == 4 } }
        assertEquals(before, raw())

        // Search and category/status choices cannot suppress the independent review shortcut.
        instrumentation.runOnMainSync {
            initial.home.search("绝不会匹配任何服饰的搜索")
            initial.home.filterByStatuses(setOf(ItemStatus.WISHED))
        }
        withTimeout(10_000) { initial.home.uiState.first {
            it.searchQuery == "绝不会匹配任何服饰的搜索" &&
                it.filterStatuses == setOf(ItemStatus.WISHED) && it.filteredItems.isEmpty()
        } }
        assertConsistent(initial, expected)
        assertEquals(before, raw())

        // Recreate subscribers as navigating away/back or reconstructing a screen does.
        instrumentation.runOnMainSync { viewModelStore.clear() }
        instrumentation.waitForIdleSync()
        val recreated = views(shared)
        assertConsistent(recreated, expected)
        assertEquals(before, raw())
        assertEquals(PurchaseRecordStatus.NEEDS_REVIEW, database.priceDao().getPriceById(archivedOnlyPrice)!!.purchaseRecordStatus)
        assertEquals(PurchaseRecordStatus.NEEDS_REVIEW, database.priceDao().getPriceById(unrelatedArchive)!!.purchaseRecordStatus)
    }

    @Test
    fun ownershipSupplementsVerificationAndDateCorrectionStaySynchronizedAcrossEverySubscriber() = runBlocking<Unit> {
        val parent = item("分步补全资料的主裙")
        val priceId = order(parent, PurchaseRecordStatus.NEEDS_REVIEW)
        val shared = item("共价小物", parentId = parent, shared = true)
        val independent = item("独立计价小物", parentId = parent)
        val independentPrice = order(independent, PurchaseRecordStatus.NEEDS_REVIEW, total = 20.05, paid = 5.01)
        val original = raw()
        val screens = views(shared)
        val all = setOf(parent, shared, independent)
        assertConsistent(screens, all)

        orders.confirmHistoricalPurchase(parent)
        assertConsistent(screens, all)
        assertEquals(ItemStatus.OWNED, items.getItemById(parent)!!.status)
        assertEquals(ItemStatus.OWNED, items.getItemById(shared)!!.status)
        assertEquals(original.payments, database.paymentDao().getAllPaymentsList().sortedBy { it.id })
        assertEquals(original.stages, database.orderDao().getAllStages().sortedBy { it.id })

        val unknownPaymentId = orders.recordHistoricalPayment(priceId, 20.01, paidDate = null)
        assertConsistent(screens, all)
        val unknownPayment = database.paymentDao().getPaymentById(unknownPaymentId)!!
        assertNull(unknownPayment.paidDate)
        assertEquals(0L, unknownPayment.dueDate)
        orders.recordHistoricalPayment(priceId, 49.98, paidDate = 1_700_000_200_000L)
        val fullyRecorded = database.paymentDao().getAllPaymentsList().sortedBy { it.id }
        assertTrue(fullyRecorded.containsAll(original.payments))
        val completeAmountButUnverified = assertConsistent(screens, all)
        assertTrue(completeAmountButUnverified.filter { it.orderItemId == parent }.all {
            it.purchaseRecordStatus == PurchaseRecordStatus.HISTORICAL_OWNED && it.hasUnknownPaymentDates
        })

        orders.confirmTrackedOrder(parent, paymentRecordsVerified = true)
        val stillNeedsDates = assertConsistent(screens, all)
        assertTrue(stillNeedsDates.filter { it.orderItemId == parent }.all {
            it.purchaseRecordStatus == PurchaseRecordStatus.TRACKED && it.hasUnknownPaymentDates
        })
        assertEquals(fullyRecorded, database.paymentDao().getAllPaymentsList().sortedBy { it.id })
        assertEquals(original.stages, database.orderDao().getAllStages().sortedBy { it.id })

        val corrected = unknownPayment.copy(paidDate = 1_700_000_100_000L)
        orders.editPayment(corrected)
        assertConsistent(screens, setOf(independent))
        assertEquals(fullyRecorded.map { if (it.id == unknownPaymentId) corrected else it },
            database.paymentDao().getAllPaymentsList().sortedBy { it.id })
        assertEquals(original.stages, database.orderDao().getAllStages().sortedBy { it.id })
        assertEquals(original.prices.map {
            if (it.id == priceId) it.copy(purchaseRecordStatus = PurchaseRecordStatus.TRACKED) else it
        }, database.priceDao().getAllPricesList().sortedBy { it.id })
        assertEquals(original.prices.single { it.id == independentPrice }, database.priceDao().getPriceById(independentPrice))
        assertEquals(PurchaseRecordStatus.NEEDS_REVIEW, database.orderDao().getBinding(independent)!!.purchaseRecordStatus)
        withTimeout(10_000) { screens.stats.uiState.first { it.incompleteCurrentOrderCount == 1 } }
    }
}
