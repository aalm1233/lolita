package com.lolita.app.ui.screen.price

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.Brand
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.local.entity.OrderBinding
import com.lolita.app.data.local.entity.Payment
import com.lolita.app.data.local.entity.PaymentStage
import com.lolita.app.data.local.entity.Price
import com.lolita.app.data.local.entity.PriceType
import com.lolita.app.data.local.entity.PurchaseRecordStatus
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.OrderRepository
import com.lolita.app.ui.theme.LolitaTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise the visible review dialogs against an isolated ledger, without the app database. */
@RunWith(AndroidJUnit4::class)
class PriceManageScreenReviewTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: LolitaDatabase
    private lateinit var orders: OrderRepository
    private lateinit var items: ItemRepository
    private lateinit var viewModel: PriceManageViewModel
    private val viewModelStore = ViewModelStore()

    @Before fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        database.brandDao().insertBrand(Brand(id = 901, name = "核对入口测试品牌", createdAt = 1))
        database.categoryDao().insertCategory(Category(id = 902, name = "核对入口测试分类", createdAt = 2))
        orders = OrderRepository(database, context)
        items = ItemRepository(database.itemDao(), database = database)
    }

    @After fun tearDown() {
        if (::viewModel.isInitialized) compose.runOnIdle { viewModelStore.clear() }
        if (::database.isInitialized) database.close()
    }

    @Test fun historicalConfirmationPreservesPartialActualPaymentsAndTheirDates() {
        val original = runBlocking { seedReviewOrder() }
        showPriceScreen(original.item.id)
        scrollTo("reviewHistoricalEntry")
        compose.onNodeWithTag("reviewHistoricalEntry").assertIsDisplayed().performClick()
        compose.onNodeWithTag("purchaseReviewDialog").assertIsDisplayed()
        compose.onNodeWithTag("reviewConfirmHistorical").assertIsEnabled().performClick()
        awaitEvidence(PurchaseRecordStatus.HISTORICAL_OWNED)

        runBlocking {
            assertEquals(original.item.copy(status = ItemStatus.OWNED), database.itemDao().getItemById(original.item.id))
            assertOrderPreserved(original, PurchaseRecordStatus.HISTORICAL_OWNED)
        }
        compose.onNodeWithTag("purchaseReviewDialog").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(null, viewModel.uiState.value.order!!.knownRemainingAmount)
            assertEquals(321.09, viewModel.uiState.value.order!!.paidAmount, 0.0)
        }
    }

    @Test fun trackingRequiresExplicitVerificationAndCancelKeepsEveryOriginalRecord() {
        val original = runBlocking { seedReviewOrder() }
        showPriceScreen(original.item.id)
        scrollTo("reviewTrackedEntry")
        compose.onNodeWithTag("reviewTrackedEntry").performClick()
        compose.onNodeWithTag("reviewPaymentRecordsVerified").assertIsOff()
        compose.onNodeWithTag("reviewConfirmTracked").assertIsNotEnabled()
        compose.onNodeWithTag("reviewCancel").performClick()
        compose.onNodeWithTag("purchaseReviewDialog").assertDoesNotExist()
        runBlocking { assertEquals(original, rawOrder(original.item.id, original.price.id)) }

        scrollTo("reviewTrackedEntry")
        compose.onNodeWithTag("reviewTrackedEntry").performClick()
        compose.onNodeWithTag("reviewPaymentRecordsVerified").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithTag("reviewConfirmTracked").assertIsEnabled().performClick()
        awaitEvidence(PurchaseRecordStatus.TRACKED)
        runBlocking {
            assertEquals(original.item.copy(status = ItemStatus.PENDING_BALANCE), database.itemDao().getItemById(original.item.id))
            assertOrderPreserved(original, PurchaseRecordStatus.TRACKED)
        }
        compose.runOnIdle {
            assertEquals(321.09, viewModel.uiState.value.order!!.paidAmount, 0.0)
            assertEquals(680.14, viewModel.uiState.value.order!!.knownRemainingAmount!!, 0.0)
        }
    }

    @Test fun sharedAccessoryReviewNavigatesToParentWithoutChangingEitherLedger() {
        val original = runBlocking { seedReviewOrder() }
        val childId = 1_830_100_002L
        runBlocking {
            database.itemDao().insertItem(Item(id = childId, brandId = 901, categoryId = 902,
                name = "共用主件价格的小物", description = "原小物说明", status = ItemStatus.PENDING_REVIEW,
                parentItemId = original.item.id, includedInParentPrice = true, createdAt = 101, updatedAt = 102))
            // Preserve an archived independent order as well as the current inherited parent order.
            database.priceDao().insertPrice(Price(id = 1_830_200_002L, itemId = childId, type = PriceType.FULL,
                totalPrice = 80.01, createdAt = 103, purchaseRecordStatus = PurchaseRecordStatus.HISTORICAL_OWNED))
            database.paymentDao().insertPayment(Payment(id = 1_830_400_003L, priceId = 1_830_200_002L,
                amount = 60.02, dueDate = 1_690_000_000_000L, paidDate = null, isPaid = true, createdAt = 104))
        }
        val before = runBlocking { databaseSnapshot() }
        val parentDestinations = mutableListOf<Long>()
        showPriceScreen(childId) { parentDestinations += it }
        scrollTo("reviewParentPriceEntry")
        compose.onNodeWithTag("reviewParentPriceEntry").assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(listOf(original.item.id), parentDestinations)
            assertTrue(viewModel.uiState.value.includedInParentPrice)
            assertEquals(original.price.id, viewModel.uiState.value.order!!.price.id)
        }
        compose.onNodeWithTag("reviewHistoricalEntry").assertDoesNotExist()
        compose.onNodeWithTag("reviewTrackedEntry").assertDoesNotExist()
        runBlocking { assertEquals(before, databaseSnapshot()) }
    }

    @Test fun unpricedReviewOpensItemEditorWithoutCreatingAnOrderOrPayment() {
        val itemId = 1_830_100_003L
        runBlocking {
            database.itemDao().insertItem(Item(id = itemId, brandId = 901, categoryId = 902,
                name = "没有价格的待核对裙子", description = "保留原服饰资料", status = ItemStatus.PENDING_REVIEW,
                source = "原购买来源", createdAt = 201, updatedAt = 202))
        }
        val before = runBlocking { databaseSnapshot() }
        val editDestinations = mutableListOf<Long>()
        showPriceScreen(itemId, expectOrder = false, onNavigateToItemEdit = { editDestinations += it })
        scrollTo("reviewUnpricedItemEntry")
        compose.onNodeWithTag("reviewUnpricedItemEntry").assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(listOf(itemId), editDestinations)
            assertEquals(null, viewModel.uiState.value.order)
            assertTrue(viewModel.uiState.value.historicalOrders.isEmpty())
            assertFalse(viewModel.uiState.value.includedInParentPrice)
        }
        runBlocking {
            assertEquals(before, databaseSnapshot())
            assertTrue(database.priceDao().getAllPricesList().isEmpty())
            assertTrue(database.orderDao().getAllBindings().isEmpty())
            assertTrue(database.paymentDao().getAllPaymentsList().isEmpty())
        }
    }

    private fun showPriceScreen(itemId: Long, expectOrder: Boolean = true,
        onNavigateToItemEdit: (Long) -> Unit = {}, onNavigateToParent: (Long) -> Unit = {}) {
        compose.runOnIdle {
            viewModel = PriceManageViewModel(orderRepository = orders, itemRepository = items, itemId = itemId)
            viewModelStore.put("isolated-review-price", viewModel)
        }
        compose.setContent {
            LolitaTheme {
                PriceManageScreen(itemId = itemId, onBack = {}, onNavigateToPriceEdit = {},
                    onNavigateToPaymentManage = {}, onNavigateToParentPriceManage = onNavigateToParent,
                    onNavigateToItemEdit = onNavigateToItemEdit,
                    viewModel = viewModel)
            }
        }
        compose.waitUntil(10_000) { !viewModel.uiState.value.isLoading }
        compose.runOnIdle {
            assertFalse(viewModel.uiState.value.loadFailed)
            if (expectOrder) assertEquals(PurchaseRecordStatus.NEEDS_REVIEW, viewModel.uiState.value.order!!.purchaseRecordStatus)
            else assertEquals(null, viewModel.uiState.value.order)
        }
    }

    private fun scrollTo(tag: String) {
        compose.onNodeWithTag("priceManageList").performScrollToNode(hasTestTag(tag))
    }

    private fun awaitEvidence(status: PurchaseRecordStatus) {
        compose.waitUntil(10_000) {
            !viewModel.uiState.value.isUpdating && viewModel.uiState.value.order?.purchaseRecordStatus == status
        }
        compose.runOnIdle { assertEquals(null, viewModel.uiState.value.error) }
    }

    private suspend fun seedReviewOrder(): RawOrder {
        val itemId = 1_830_100_001L
        val priceId = 1_830_200_001L
        database.itemDao().insertItem(Item(id = itemId, brandId = 901, categoryId = 902,
            name = "待核对裙子", description = "原购买资料", status = ItemStatus.PENDING_REVIEW,
            source = "原淘宝来源", createdAt = 11, updatedAt = 12))
        database.priceDao().insertPrice(Price(id = priceId, itemId = itemId, type = PriceType.DEPOSIT_BALANCE,
            totalPrice = 1001.23, shipping = 20.01, deposit = 321.09, balance = 680.14,
            createdAt = 13, purchaseRecordStatus = PurchaseRecordStatus.NEEDS_REVIEW))
        database.orderDao().insertBinding(OrderBinding(itemId, priceId, PurchaseRecordStatus.NEEDS_REVIEW))
        database.orderDao().insertStage(PaymentStage(id = 1_830_300_001L, priceId = priceId,
            name = "原定金", amount = 321.09, dueDate = null))
        database.orderDao().insertStage(PaymentStage(id = 1_830_300_002L, priceId = priceId,
            name = "原尾款", amount = 680.14, dueDate = null))
        database.paymentDao().insertPayment(Payment(id = 1_830_400_001L, priceId = priceId,
            stageId = 1_830_300_001L, amount = 200.03, dueDate = 1_700_000_000_000L,
            paidDate = 1_700_000_000_000L, isPaid = true, createdAt = 14))
        database.paymentDao().insertPayment(Payment(id = 1_830_400_002L, priceId = priceId,
            stageId = 1_830_300_001L, amount = 121.06, dueDate = 0L,
            paidDate = null, isPaid = true, createdAt = 15))
        return rawOrder(itemId, priceId)
    }

    private data class RawOrder(val item: Item, val price: Price, val binding: OrderBinding,
        val stages: List<PaymentStage>, val payments: List<Payment>)

    private suspend fun rawOrder(itemId: Long, priceId: Long) = RawOrder(
        database.itemDao().getItemById(itemId)!!, database.priceDao().getPriceById(priceId)!!,
        database.orderDao().getBinding(itemId)!!, database.orderDao().getStages(priceId).sortedBy { it.id },
        database.paymentDao().getPaymentsByPriceList(priceId).sortedBy { it.id })

    private suspend fun assertOrderPreserved(original: RawOrder, evidence: PurchaseRecordStatus) {
        val current = rawOrder(original.item.id, original.price.id)
        assertEquals(original.price.copy(purchaseRecordStatus = evidence), current.price)
        assertEquals(original.binding.copy(purchaseRecordStatus = evidence), current.binding)
        assertEquals(original.stages, current.stages)
        assertEquals(original.payments, current.payments)
    }

    private data class DatabaseSnapshot(val items: List<Item>, val prices: List<Price>,
        val bindings: List<OrderBinding>, val stages: List<PaymentStage>, val payments: List<Payment>)

    private suspend fun databaseSnapshot() = DatabaseSnapshot(
        database.itemDao().getAllItemsList().sortedBy { it.id }, database.priceDao().getAllPricesList().sortedBy { it.id },
        database.orderDao().getAllBindings().sortedBy { it.itemId }, database.orderDao().getAllStages().sortedBy { it.id },
        database.paymentDao().getAllPaymentsList().sortedBy { it.id })
}
