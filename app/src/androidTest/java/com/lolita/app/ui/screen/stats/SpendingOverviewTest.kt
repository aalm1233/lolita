package com.lolita.app.ui.screen.stats

import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.Brand
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.local.entity.Payment
import com.lolita.app.data.local.entity.PaymentStage
import com.lolita.app.data.local.entity.Price
import com.lolita.app.data.local.entity.PriceType
import com.lolita.app.data.local.entity.PurchaseRecordStatus
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.data.repository.CoordinateRepository
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.OrderRepository
import com.lolita.app.data.repository.OutfitLogRepository
import com.lolita.app.data.repository.PriceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Current-order and all-recorded totals must keep their separate, explicit meanings. */
@RunWith(AndroidJUnit4::class)
class SpendingOverviewTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var database: LolitaDatabase
    private lateinit var viewModelStore: ViewModelStore
    private lateinit var orders: OrderRepository
    private lateinit var items: ItemRepository
    private lateinit var prices: PriceRepository

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        viewModelStore = ViewModelStore()
        orders = OrderRepository(database, context)
        items = ItemRepository(database.itemDao(), database = database)
        prices = PriceRepository(database.priceDao(), database.paymentDao(), context, database)
        database.brandDao().insertBrand(Brand(id = 101, name = "消费概览测试品牌"))
        database.categoryDao().insertCategory(Category(id = 301, name = "消费概览测试分类"))
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

    private fun viewModel(): StatsViewModel {
        lateinit var result: StatsViewModel
        instrumentation.runOnMainSync {
            result = StatsViewModel(
                itemRepository = items,
                coordinateRepository = CoordinateRepository(database.coordinateDao(), database.itemDao(), database),
                outfitLogRepository = OutfitLogRepository(database.outfitLogDao(), database),
                priceRepository = prices,
                appPreferences = AppPreferences(context)
            )
            viewModelStore.put("spending-overview", result)
        }
        return result
    }

    private suspend fun item(name: String, status: ItemStatus = ItemStatus.OWNED): Long =
        items.insertItem(Item(brandId = 101, categoryId = 301, name = name,
            description = "", status = status))

    private suspend fun trackedOrder(itemId: Long, total: Double): Long = orders.createOrder(
        Price(itemId = itemId, type = PriceType.FULL, totalPrice = total),
        listOf(PaymentStage(priceId = 0, name = "全款", amount = total))
    )

    private suspend fun historicalOrder(
        itemId: Long,
        total: Double,
        status: PurchaseRecordStatus = PurchaseRecordStatus.HISTORICAL_OWNED
    ): Long = prices.insertPrice(
        Price(itemId = itemId, type = PriceType.FULL, totalPrice = total, purchaseRecordStatus = status)
    )

    private suspend fun assertTotals(
        viewModel: StatsViewModel,
        current: Double,
        allRecorded: Double,
        incompleteCount: Int = 0
    ): StatsUiState {
        val state = withTimeout(10_000) {
            viewModel.uiState.first {
                !it.isLoading && it.totalSpending == current &&
                    it.allRecordedSpending == allRecorded &&
                    it.incompleteCurrentOrderCount == incompleteCount
            }
        }
        assertEquals(current, state.totalSpending, 0.0)
        assertEquals(allRecorded, state.allRecordedSpending, 0.0)
        assertEquals(incompleteCount, state.incompleteCurrentOrderCount)
        return state
    }

    @Test
    fun archivingReducesCurrentTotalAndRetainsEveryRecordedActualPayment() = runBlocking<Unit> {
        val itemId = item("待付款裙子")
        val priceId = trackedOrder(itemId, 100.01)
        orders.recordPayment(priceId, 40.02, 1_700_000_000_000L)
        // Old unpaid plans are preserved, but neither total treats them as actual money.
        database.paymentDao().insertPayment(Payment(priceId = priceId, amount = 59.99,
            dueDate = 1_700_000_100_000L, isPaid = false))
        val originalPayments = database.paymentDao().getPaymentsByPriceList(priceId).sortedBy { it.id }
        val overview = viewModel()
        assertTotals(overview, 40.02, 40.02)

        orders.archiveCurrentOrder(itemId)
        assertTotals(overview, 0.0, 40.02)
        assertEquals(originalPayments, database.paymentDao().getPaymentsByPriceList(priceId).sortedBy { it.id })
        assertEquals(40.02, prices.getAllRecordedSpending().first(), 0.0)
    }

    @Test
    fun sharingAccessoryPriceAvoidsCurrentDuplicationAndKeepsItsArchivedActuals() = runBlocking<Unit> {
        val parentId = item("主裙")
        val childId = item("独立购买的小物")
        val parentPriceId = trackedOrder(parentId, 100.05)
        val childPriceId = trackedOrder(childId, 20.06)
        orders.recordPayment(parentPriceId, 100.05, 1_700_000_000_000L)
        orders.recordPayment(childPriceId, 20.06, 1_700_000_000_000L)
        val originalPayments = database.paymentDao().getAllPaymentsList().sortedBy { it.id }
        val overview = viewModel()
        assertTotals(overview, 120.11, 120.11)

        orders.archiveCurrentOrder(childId)
        items.attachChild(childId, parentId, includedInPrice = true)
        assertTotals(overview, 100.05, 120.11)
        assertEquals(ItemStatus.OWNED, items.getItemById(childId)!!.status)
        assertNull(database.orderDao().getBinding(childId))
        assertEquals(originalPayments, database.paymentDao().getAllPaymentsList().sortedBy { it.id })
    }

    @Test
    fun missingHistoricalPaymentsRemainUnknownAndCountedAsIncompleteWithoutFabrication() = runBlocking<Unit> {
        val historicalId = item("历史已购")
        val historicalPriceId = historicalOrder(historicalId, 888.88)
        val reviewId = item("资料待核对", ItemStatus.PENDING_REVIEW)
        val reviewPriceId = historicalOrder(reviewId, 123.45, PurchaseRecordStatus.NEEDS_REVIEW)
        val overview = viewModel()
        val state = assertTotals(overview, 0.0, 0.0, incompleteCount = 2)
        assertEquals(888.88, state.averagePrice, 0.0)
        assertNull(orders.getOrder(historicalPriceId).first()!!.knownRemainingAmount)
        assertNull(orders.getOrder(reviewPriceId).first()!!.knownRemainingAmount)
        assertTrue(database.paymentDao().getAllPaymentsList().isEmpty())

        orders.confirmTrackedOrder(reviewId, paymentRecordsVerified = true)
        assertTotals(overview, 0.0, 0.0, incompleteCount = 1)
        assertTrue(database.paymentDao().getAllPaymentsList().isEmpty())
        assertEquals(ItemStatus.OWNED, items.getItemById(historicalId)!!.status)
    }

    @Test
    fun historicalSupplementsAndRevocationsContinuouslyUpdateTotalsInCents() = runBlocking<Unit> {
        val itemId = item("历史实付补录")
        val priceId = historicalOrder(itemId, 3.0)
        val overview = viewModel()
        assertTotals(overview, 0.0, 0.0, incompleteCount = 1)

        val unknownDateId = orders.recordHistoricalPayment(priceId, 0.10, paidDate = null)
        assertTotals(overview, 0.10, 0.10, incompleteCount = 1)
        val knownDateId = orders.recordHistoricalPayment(priceId, 0.20, paidDate = 1_700_000_000_000L)
        assertTotals(overview, 0.30, 0.30, incompleteCount = 1)

        orders.deletePayment(unknownDateId)
        assertTotals(overview, 0.20, 0.20, incompleteCount = 1)
        orders.deletePayment(knownDateId)
        assertTotals(overview, 0.0, 0.0, incompleteCount = 1)
        assertEquals(ItemStatus.OWNED, items.getItemById(itemId)!!.status)
        assertNull(orders.getOrder(priceId).first()!!.knownRemainingAmount)
    }
}
