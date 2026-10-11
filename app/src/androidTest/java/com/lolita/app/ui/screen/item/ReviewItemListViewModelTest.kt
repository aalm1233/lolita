package com.lolita.app.ui.screen.item

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
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.OrderRepository
import com.lolita.app.data.repository.PriceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Review queues include accessories and refresh from the same Room transaction as ownership. */
@RunWith(AndroidJUnit4::class)
class ReviewItemListViewModelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var database: LolitaDatabase
    private lateinit var viewModelStore: ViewModelStore
    private lateinit var items: ItemRepository
    private lateinit var prices: PriceRepository
    private lateinit var orders: OrderRepository
    // Ledger cleanup uses Android PendingIntent identities even with an in-memory database.
    private var nextPriceId = 1_831_200_001L
    private var nextStageId = 1_831_300_001L
    private var nextPaymentId = 1_831_400_001L

    @Before
    fun setUp() = runBlocking {
        nextPriceId = 1_831_200_001L
        nextStageId = 1_831_300_001L
        nextPaymentId = 1_831_400_001L
        database = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        viewModelStore = ViewModelStore()
        items = ItemRepository(database.itemDao(), database = database)
        prices = PriceRepository(database.priceDao(), database.paymentDao(), context, database)
        orders = OrderRepository(database, context)
        database.brandDao().insertBrand(Brand(id = 101, name = "待核对清单品牌"))
        database.categoryDao().insertCategory(Category(id = 301, name = "待核对清单分类"))
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

    private suspend fun item(
        name: String,
        status: ItemStatus = ItemStatus.PENDING_REVIEW,
        parentId: Long? = null,
        includedInPrice: Boolean = false
    ): Long = items.insertItem(Item(
        brandId = 101, categoryId = 301, name = name, description = "保留的购买说明",
        status = status, parentItemId = parentId, includedInParentPrice = includedInPrice
    ))

    private fun viewModel(): FilteredItemListViewModel {
        lateinit var result: FilteredItemListViewModel
        instrumentation.runOnMainSync {
            result = FilteredItemListViewModel(
                filterType = "status_review", filterValue = "",
                itemRepository = items, priceRepository = prices
            )
            viewModelStore.put("review-items", result)
        }
        return result
    }

    private suspend fun assertIds(viewModel: FilteredItemListViewModel, expected: Set<Long>) {
        val authoritative = items.getPurchaseReviews().first().sortedBy { it.item.id }
        assertEquals(expected, authoritative.map { it.item.id }.toSet())
        val state = withTimeout(10_000) {
            viewModel.uiState.first {
                !it.isLoading && it.items.map { item -> item.id }.toSet() == expected &&
                    it.purchaseReviews.sortedBy { review -> review.item.id } == authoritative
            }
        }
        assertEquals(expected, state.items.map { it.id }.toSet())
        assertEquals(expected.size, state.items.size)
        assertEquals(expected, state.purchaseReviews.map { it.item.id }.toSet())
        assertEquals(expected.size, state.purchaseReviews.size)
    }

    private suspend fun reviewOrder(itemId: Long, total: Double, paid: Double): Long {
        return seededOrder(itemId, total, paid, PurchaseRecordStatus.NEEDS_REVIEW)
    }

    private suspend fun seededOrder(
        itemId: Long,
        total: Double,
        paid: Double,
        evidence: PurchaseRecordStatus,
        paidDate: Long? = 1_700_000_000_000L
    ): Long {
        val priceId = database.priceDao().insertPrice(Price(
            id = nextPriceId++, itemId = itemId, type = PriceType.FULL, totalPrice = total, shipping = 3.01,
            purchaseRecordStatus = evidence, createdAt = 17
        ))
        val stageId = database.orderDao().insertStage(PaymentStage(
            id = nextStageId++, priceId = priceId, name = "保留的原计划", amount = total, reminderSet = false
        ))
        database.paymentDao().insertPayment(Payment(
            id = nextPaymentId++, priceId = priceId, stageId = stageId, amount = paid, isPaid = true,
            dueDate = paidDate ?: 0L, paidDate = paidDate, createdAt = 18
        ))
        orders.bindHistoricalOrder(itemId, priceId)
        return priceId
    }

    @Test
    fun reviewQueueUsesLedgerCompletenessAndIncludesUnpricedOwnedAccessories() = runBlocking<Unit> {
        val parentId = item("待核对主裙")
        val sharedId = item("共价头饰", parentId = parentId, includedInPrice = true)
        val ownedParentId = item("付款资料完整的已拥有主裙", ItemStatus.OWNED)
        seededOrder(ownedParentId, total = 10.01, paid = 10.01, evidence = PurchaseRecordStatus.TRACKED)
        val independentId = item("尚未录入价格的独立小物", parentId = ownedParentId)
        val unpricedOwnedId = item("确认拥有但尚无付款资料的裙子", ItemStatus.OWNED)
        item("愿望裙", ItemStatus.WISHED)
        val owingId = item("已完整核对但尚待付款的裙子", ItemStatus.PENDING_PAYMENT)
        seededOrder(owingId, total = 20.05, paid = 5.01, evidence = PurchaseRecordStatus.TRACKED)

        assertIds(viewModel(), setOf(parentId, sharedId, independentId, unpricedOwnedId))
    }

    @Test
    fun unpricedOwnershipConfirmationKeepsMissingDataVisibleWithoutCreatingLedgerRows() = runBlocking<Unit> {
        val parentId = item("没有购买金额的历史主裙")
        val sharedId = item("共价且没有购买金额的小物", parentId = parentId, includedInPrice = true)
        val originalParent = items.getItemById(parentId)!!
        val originalShared = items.getItemById(sharedId)!!
        val queue = viewModel()
        assertIds(queue, setOf(parentId, sharedId))
        assertTrue(database.priceDao().getAllPricesList().isEmpty())
        assertTrue(database.paymentDao().getAllPaymentsList().isEmpty())
        assertTrue(database.orderDao().getAllStages().isEmpty())
        assertTrue(database.orderDao().getAllBindings().isEmpty())

        // Without a current order, the existing edit flow can record explicit ownership evidence.
        items.updateItem(originalParent.copy(status = ItemStatus.OWNED))

        assertIds(queue, setOf(parentId, sharedId))
        assertTrue(queue.uiState.value.purchaseReviews.all { it.priceId == null && it.orderItemId == parentId })
        val savedParent = items.getItemById(parentId)!!
        val savedShared = items.getItemById(sharedId)!!
        assertEquals(originalParent.copy(status = ItemStatus.OWNED, updatedAt = savedParent.updatedAt), savedParent)
        assertEquals(originalShared.copy(status = ItemStatus.OWNED, updatedAt = savedShared.updatedAt), savedShared)
        assertEquals(setOf(parentId, sharedId), database.itemDao().getAllItemsList().map { it.id }.toSet())
        assertTrue(database.priceDao().getAllPricesList().isEmpty())
        assertTrue(database.paymentDao().getAllPaymentsList().isEmpty())
        assertTrue(database.orderDao().getAllStages().isEmpty())
        assertTrue(database.orderDao().getAllBindings().isEmpty())
    }

    @Test
    fun historicalOwnershipConfirmationKeepsParentAndAccessoriesUntilTheirLedgersAreVerified() = runBlocking<Unit> {
        val parentId = item("历史主裙")
        val parentPriceId = reviewOrder(parentId, total = 100.01, paid = 30.02)
        val sharedId = item("共价小物", parentId = parentId, includedInPrice = true)
        val independentId = item("独立待核对小物", parentId = parentId)
        val independentPriceId = reviewOrder(independentId, total = 20.05, paid = 5.01)
        val originalParent = database.priceDao().getPriceById(parentPriceId)!!
        val originalIndependent = database.priceDao().getPriceById(independentPriceId)!!
        val originalPayments = database.paymentDao().getAllPaymentsList().sortedBy { it.id }
        val originalStages = database.orderDao().getAllStages().sortedBy { it.id }
        val queue = viewModel()
        assertIds(queue, setOf(parentId, sharedId, independentId))

        orders.confirmHistoricalPurchase(parentId)

        assertIds(queue, setOf(parentId, sharedId, independentId))
        assertEquals(ItemStatus.OWNED, items.getItemById(parentId)!!.status)
        assertEquals(ItemStatus.OWNED, items.getItemById(sharedId)!!.status)
        assertEquals(ItemStatus.PENDING_REVIEW, items.getItemById(independentId)!!.status)
        assertEquals(originalParent.copy(purchaseRecordStatus = PurchaseRecordStatus.HISTORICAL_OWNED),
            database.priceDao().getPriceById(parentPriceId))
        assertEquals(originalIndependent, database.priceDao().getPriceById(independentPriceId))
        assertEquals(originalPayments, database.paymentDao().getAllPaymentsList().sortedBy { it.id })
        assertEquals(originalStages, database.orderDao().getAllStages().sortedBy { it.id })

        orders.confirmHistoricalPurchase(independentId)
        assertIds(queue, setOf(parentId, sharedId, independentId))
        assertEquals(originalPayments, database.paymentDao().getAllPaymentsList().sortedBy { it.id })

        orders.confirmTrackedOrder(parentId, paymentRecordsVerified = true)
        assertIds(queue, setOf(independentId))
        orders.confirmTrackedOrder(independentId, paymentRecordsVerified = true)
        assertIds(queue, emptySet())
        assertEquals(originalPayments, database.paymentDao().getAllPaymentsList().sortedBy { it.id })
        assertEquals(originalStages, database.orderDao().getAllStages().sortedBy { it.id })
    }

    @Test
    fun partialAndFullHistoricalSupplementsStayVisibleUntilExplicitLedgerVerification() = runBlocking<Unit> {
        val parentId = item("已购但付款资料分批补录的裙子")
        val priceId = reviewOrder(parentId, total = 100.01, paid = 30.02)
        val sharedId = item("共价小物", parentId = parentId, includedInPrice = true)
        val originalPrice = database.priceDao().getPriceById(priceId)!!
        val originalPayments = database.paymentDao().getAllPaymentsList()
        val originalStages = database.orderDao().getAllStages()
        val queue = viewModel()
        assertIds(queue, setOf(parentId, sharedId))

        orders.confirmHistoricalPurchase(parentId)
        assertIds(queue, setOf(parentId, sharedId))
        orders.recordHistoricalPayment(priceId, 20.01, paidDate = 1_700_000_100_000L)
        assertIds(queue, setOf(parentId, sharedId))
        orders.recordHistoricalPayment(priceId, 49.98, paidDate = 1_700_000_200_000L)
        assertIds(queue, setOf(parentId, sharedId))
        assertEquals(PurchaseRecordStatus.HISTORICAL_OWNED, queue.uiState.value.purchaseReviews.first().purchaseRecordStatus)
        assertEquals(100.01, orders.currentOrder(parentId).first()!!.paidAmount, 0.0)
        val fullySupplemented = database.paymentDao().getAllPaymentsList().sortedBy { it.id }
        assertTrue(fullySupplemented.containsAll(originalPayments))
        assertEquals(originalStages, database.orderDao().getAllStages())
        assertEquals(originalPrice.copy(purchaseRecordStatus = PurchaseRecordStatus.HISTORICAL_OWNED),
            database.priceDao().getPriceById(priceId))

        orders.confirmTrackedOrder(parentId, paymentRecordsVerified = true)
        assertIds(queue, emptySet())
        assertEquals(fullySupplemented, database.paymentDao().getAllPaymentsList().sortedBy { it.id })
        assertEquals(originalStages, database.orderDao().getAllStages())
        assertEquals(originalPrice.copy(purchaseRecordStatus = PurchaseRecordStatus.TRACKED),
            database.priceDao().getPriceById(priceId))
    }

    @Test
    fun trackedPaymentsWithUnknownDatesRemainVisibleUntilTheirRealDatesAreCorrected() = runBlocking<Unit> {
        val parentId = item("金额已录完整但实付日期待补的裙子")
        val priceId = seededOrder(parentId, total = 100.01, paid = 100.01,
            evidence = PurchaseRecordStatus.HISTORICAL_OWNED, paidDate = null)
        val sharedId = item("共价小物", parentId = parentId, includedInPrice = true)
        val originalPayment = database.paymentDao().getPaymentsByPriceList(priceId).single()
        val originalPrice = database.priceDao().getPriceById(priceId)!!
        val originalStages = database.orderDao().getAllStages()
        val queue = viewModel()
        assertIds(queue, setOf(parentId, sharedId))

        orders.confirmTrackedOrder(parentId, paymentRecordsVerified = true)
        assertIds(queue, setOf(parentId, sharedId))
        val reviewRows = withTimeout(10_000) { queue.uiState.first { state ->
            state.purchaseReviews.size == 2 && state.purchaseReviews.all {
                it.purchaseRecordStatus == PurchaseRecordStatus.TRACKED && it.hasUnknownPaymentDates
            }
        } }.purchaseReviews
        assertEquals(setOf(parentId, sharedId), reviewRows.map { it.item.id }.toSet())
        assertEquals(originalPayment, database.paymentDao().getPaymentById(originalPayment.id))
        assertEquals(ItemStatus.OWNED, items.getItemById(parentId)!!.status)

        val corrected = originalPayment.copy(paidDate = 1_700_000_300_000L)
        orders.editPayment(corrected)
        assertIds(queue, emptySet())
        assertEquals(corrected, database.paymentDao().getPaymentById(originalPayment.id))
        assertEquals(originalStages, database.orderDao().getAllStages())
        assertEquals(originalPrice.copy(purchaseRecordStatus = PurchaseRecordStatus.TRACKED),
            database.priceDao().getPriceById(priceId))
    }

    @Test
    fun trackingRequiresVerifiedPaymentsAndCannotSilentlyRemoveReviewItems() = runBlocking<Unit> {
        val parentId = item("未确认真实余额的裙子")
        val priceId = reviewOrder(parentId, total = 100.01, paid = 30.02)
        val sharedId = item("共价袖套", parentId = parentId, includedInPrice = true)
        val queue = viewModel()
        assertIds(queue, setOf(parentId, sharedId))
        val originalItems = database.itemDao().getAllItemsList().sortedBy { it.id }
        val originalPrice = database.priceDao().getPriceById(priceId)!!
        val originalPayments = database.paymentDao().getAllPaymentsList().sortedBy { it.id }
        val originalStages = database.orderDao().getAllStages().sortedBy { it.id }
        val originalBindings = database.orderDao().getAllBindings()
        val originalJournal = database.restoreJournalDao().get()

        try {
            orders.confirmTrackedOrder(parentId, paymentRecordsVerified = false)
            fail("未核对真实付款与余额时不能移出待核对清单")
        } catch (_: IllegalArgumentException) {
            // The domain guard must reject before committing any ledger or state change.
        }

        assertIds(queue, setOf(parentId, sharedId))
        assertEquals(originalItems, database.itemDao().getAllItemsList().sortedBy { it.id })
        assertEquals(originalPrice, database.priceDao().getPriceById(priceId))
        assertEquals(originalPayments, database.paymentDao().getAllPaymentsList().sortedBy { it.id })
        assertEquals(originalStages, database.orderDao().getAllStages().sortedBy { it.id })
        assertEquals(originalBindings, database.orderDao().getAllBindings())
        assertEquals(originalJournal, database.restoreJournalDao().get())

        orders.confirmTrackedOrder(parentId, paymentRecordsVerified = true)
        assertIds(queue, emptySet())
        assertEquals(ItemStatus.PENDING_PAYMENT, items.getItemById(parentId)!!.status)
        assertEquals(ItemStatus.PENDING_PAYMENT, items.getItemById(sharedId)!!.status)
        assertEquals(originalPrice.copy(purchaseRecordStatus = PurchaseRecordStatus.TRACKED),
            database.priceDao().getPriceById(priceId))
        assertEquals(originalPayments, database.paymentDao().getAllPaymentsList().sortedBy { it.id })
        assertEquals(originalStages, database.orderDao().getAllStages().sortedBy { it.id })
    }
}
