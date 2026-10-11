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
        val state = withTimeout(10_000) {
            viewModel.uiState.first { !it.isLoading && it.items.map { item -> item.id }.toSet() == expected }
        }
        assertEquals(expected, state.items.map { it.id }.toSet())
        assertEquals(expected.size, state.items.size)
    }

    private suspend fun reviewOrder(itemId: Long, total: Double, paid: Double): Long {
        val priceId = database.priceDao().insertPrice(Price(
            id = nextPriceId++, itemId = itemId, type = PriceType.FULL, totalPrice = total, shipping = 3.01,
            purchaseRecordStatus = PurchaseRecordStatus.NEEDS_REVIEW, createdAt = 17
        ))
        val stageId = database.orderDao().insertStage(PaymentStage(
            id = nextStageId++, priceId = priceId, name = "保留的原计划", amount = total, reminderSet = false
        ))
        database.paymentDao().insertPayment(Payment(
            id = nextPaymentId++, priceId = priceId, stageId = stageId, amount = paid, isPaid = true,
            dueDate = 1_700_000_000_000L, paidDate = 1_700_000_000_000L, createdAt = 18
        ))
        orders.bindHistoricalOrder(itemId, priceId)
        return priceId
    }

    @Test
    fun reviewFilterExcludesOtherStatesAndIncludesIndependentAccessories() = runBlocking<Unit> {
        val parentId = item("待核对主裙")
        val sharedId = item("共价头饰", parentId = parentId, includedInPrice = true)
        val ownedParentId = item("已拥有主裙", ItemStatus.OWNED)
        val independentId = item("独立计价小物", parentId = ownedParentId)
        item("愿望裙", ItemStatus.WISHED)
        item("待付款裙", ItemStatus.PENDING_PAYMENT)
        item("待补尾款裙", ItemStatus.PENDING_BALANCE)

        assertIds(viewModel(), setOf(parentId, sharedId, independentId))
    }

    @Test
    fun unpricedOwnershipConfirmationRemovesParentAndSharedChildWithoutCreatingLedgerRows() = runBlocking<Unit> {
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

        assertIds(queue, emptySet())
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
    fun historicalConfirmationRemovesParentAndSharedChildButKeepsIndependentChild() = runBlocking<Unit> {
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

        assertIds(queue, setOf(independentId))
        assertEquals(ItemStatus.OWNED, items.getItemById(parentId)!!.status)
        assertEquals(ItemStatus.OWNED, items.getItemById(sharedId)!!.status)
        assertEquals(ItemStatus.PENDING_REVIEW, items.getItemById(independentId)!!.status)
        assertEquals(originalParent.copy(purchaseRecordStatus = PurchaseRecordStatus.HISTORICAL_OWNED),
            database.priceDao().getPriceById(parentPriceId))
        assertEquals(originalIndependent, database.priceDao().getPriceById(independentPriceId))
        assertEquals(originalPayments, database.paymentDao().getAllPaymentsList().sortedBy { it.id })
        assertEquals(originalStages, database.orderDao().getAllStages().sortedBy { it.id })

        orders.confirmHistoricalPurchase(independentId)
        assertIds(queue, emptySet())
        assertEquals(originalPayments, database.paymentDao().getAllPaymentsList().sortedBy { it.id })
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
