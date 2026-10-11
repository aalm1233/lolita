package com.lolita.app.ui.screen.price

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.repository.OrderRepository
import com.lolita.app.ui.theme.LolitaTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Render the actual mixed lazy list using separate tables that deliberately share primary keys. */
@RunWith(AndroidJUnit4::class)
class PaymentManageScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: LolitaDatabase
    private val viewModelStore = ViewModelStore()
    private lateinit var viewModel: PaymentManageViewModel

    @Before fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        runBlocking {
            database.brandDao().insertBrand(Brand(id = 1, name = "测试品牌"))
            database.categoryDao().insertCategory(Category(id = 1, name = "OP"))
            database.itemDao().insertItem(Item(id = 1, name = "付款页回归样本", brandId = 1, categoryId = 1,
                description = "列表键回归", status = ItemStatus.OWNED))
            database.priceDao().insertPrice(Price(id = 1, itemId = 1, type = PriceType.DEPOSIT_BALANCE,
                totalPrice = 100.0, deposit = 30.0, balance = 70.0))
            database.orderDao().insertBinding(OrderBinding(itemId = 1, priceId = 1))
            database.orderDao().insertStage(PaymentStage(id = 1, priceId = 1, name = "定金", amount = 30.0))
            database.orderDao().insertStage(PaymentStage(id = 2, priceId = 1, name = "尾款", amount = 70.0))
            database.paymentDao().insertPayment(Payment(id = 1, priceId = 1, stageId = 1, amount = 30.0,
                dueDate = 1_700_000_000_000, paidDate = 1_700_000_000_000, isPaid = true))
            database.paymentDao().insertPayment(Payment(id = 2, priceId = 1, stageId = 2, amount = 70.0,
                dueDate = 1_700_086_400_000, paidDate = 1_700_086_400_000, isPaid = true))
        }
        compose.runOnIdle {
            viewModel = PaymentManageViewModel(OrderRepository(database, context), priceId = 1)
            viewModelStore.put("isolated-payment-page", viewModel)
        }
        compose.setContent {
            LolitaTheme {
                PaymentManageScreen(priceId = 1, onBack = {}, onNavigateToPaymentEdit = {}, viewModel = viewModel)
            }
        }
        compose.waitUntil(10_000) { !viewModel.uiState.value.isLoading }
        compose.runOnIdle {
            assertTrue(viewModel.uiState.value.exists)
            assertTrue(viewModel.uiState.value.isCurrent)
        }
    }

    @After fun tearDown() {
        if (::viewModel.isInitialized) compose.runOnIdle { viewModelStore.clear() }
        if (::database.isInitialized) database.close()
    }

    @Test fun stagesAndActualPaymentsWithMatchingIdsRenderAndScrollWithoutCrashing() {
        // Scrolling forces both item groups through the lazy key map; an unprefixed id=1/2 fails here.
        repeat(2) {
            compose.onNodeWithTag("paymentManageList").performScrollToNode(hasTestTag("actualPayment-2"))
            compose.onNodeWithTag("actualPayment-2").assertIsDisplayed()
            compose.onNodeWithTag("paymentManageList").performScrollToNode(hasTestTag("actualPayment-1"))
            compose.onNodeWithTag("actualPayment-1").assertIsDisplayed()
            compose.onNodeWithTag("paymentManageList").performScrollToNode(hasTestTag("paymentStage-1"))
            compose.onNodeWithTag("paymentStage-1").assertIsDisplayed()
        }
        // Keep a stage and a real payment in the same viewport as well as testing both scroll directions.
        compose.onNodeWithTag("paymentManageList").performScrollToNode(hasTestTag("paymentStage-2"))
        compose.onNodeWithTag("paymentStage-2").assertIsDisplayed()
        compose.onNodeWithTag("actualPayment-1").assertIsDisplayed()
        runBlocking {
            assertEquals(listOf(1L, 2L), database.orderDao().getAllStages().map { it.id }.sorted())
            assertEquals(listOf(1L, 2L), database.paymentDao().getAllPaymentsList().map { it.id }.sorted())
            assertEquals(listOf(30.0, 70.0), database.paymentDao().getAllPaymentsList().sortedBy { it.id }.map { it.amount })
        }
    }
}
