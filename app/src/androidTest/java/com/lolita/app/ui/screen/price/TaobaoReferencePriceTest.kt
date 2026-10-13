package com.lolita.app.ui.screen.price

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.repository.OrderRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

class TaobaoReferencePriceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var db: LolitaDatabase
    private lateinit var orders: OrderRepository
    private lateinit var vm: PriceEditViewModel
    private var itemId = 0L
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, LolitaDatabase::class.java).build()
        orders = OrderRepository(db, instrumentation.targetContext)
        val brand = db.brandDao().insertBrand(Brand(name="报价测试"))
        val category = db.categoryDao().insertCategory(Category(name="测试套装"))
        itemId = db.itemDao().insertItem(Item(brandId=brand, categoryId=category, name="男款SET", description="淘宝款式参考报价：¥658（男款SET）", status=ItemStatus.WISHED))
        instrumentation.runOnMainSync { vm = PriceEditViewModel(orders) }
    }
    @After fun close() { db.close() }
    private fun offer(description: String) = instrumentation.runOnMainSync { vm.offerReferenceQuote(description) }
    private fun adopt() = instrumentation.runOnMainSync { vm.useReferenceQuote() }
    @Test fun quoteNeedsExplicitAdoptionAndSavingNeverCreatesAnActualPayment() = runBlocking {
        offer(db.itemDao().getItemById(itemId)!!.description)
        assertEquals("",vm.uiState.value.totalPrice)
        assertNull(orders.currentOrder(itemId).first())
        adopt()
        assertEquals("658",vm.uiState.value.totalPrice)
        assertEquals("658",vm.uiState.value.stages.single().amount)
        assertTrue(vm.isValid())
        assertNull(orders.currentOrder(itemId).first())
        val result = withContext(Dispatchers.Main) { vm.save(itemId) }
        assertTrue(result.isSuccess)
        val saved = orders.currentOrder(itemId).first()!!
        assertEquals(658.0,saved.price.totalPrice,0.001)
        assertEquals(658.0,saved.stages.single().amount,0.001)
        assertEquals(0.0,saved.paidAmount,0.001)
        assertTrue(saved.payments.isEmpty())
    }
    @Test fun referenceCannotOverwriteManualAmount() {
        offer("淘宝款式参考报价：¥658（男款SET）")
        instrumentation.runOnMainSync { vm.updateTotalPrice("600") }
        adopt()
        assertEquals("600",vm.uiState.value.totalPrice)
    }
    @Test fun oldListingNotesDoNotBecomeAConfirmedQuote() {
        offer("网页报价（仅供核对，不代表实付）：449\n男款SET 全款658")
        adopt()
        assertEquals("",vm.uiState.value.totalPrice)
    }
}
