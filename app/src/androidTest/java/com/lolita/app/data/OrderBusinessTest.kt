package com.lolita.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.file.BackupManager
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.OrderRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

/** Real Room transactions exercise the ledger invariants and the paid = received rule. */
@RunWith(AndroidJUnit4::class)
class OrderBusinessTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: LolitaDatabase
    private lateinit var orders: OrderRepository
    private lateinit var items: ItemRepository

    @Before fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        database.brandDao().insertBrand(Brand(id = 101, name = "业务测试品牌", createdAt = 1))
        database.categoryDao().insertCategory(Category(id = 307, name = "业务测试分类", createdAt = 2))
        orders = OrderRepository(database, context)
        items = ItemRepository(database.itemDao(), database = database)
    }

    @After fun tearDown() { database.close() }

    private suspend fun item(name: String = "裙子", parent: Long? = null, shared: Boolean = false) =
        items.insertItem(Item(brandId = 101, categoryId = 307, name = name, description = "", status = ItemStatus.WISHED,
            parentItemId = parent, includedInParentPrice = shared, createdAt = 10, updatedAt = 11))

    private fun stage(name: String, amount: Double) =
        PaymentStage(priceId = 0, name = name, amount = amount, dueDate = null, reminderSet = false)

    private suspend fun create(itemId: Long, total: Double, vararg stages: PaymentStage,
        type: PriceType = PriceType.FULL, shipping: Double = 0.0): Long =
        orders.createOrder(Price(itemId = itemId, type = type, totalPrice = total, shipping = shipping, createdAt = 20), stages.toList())

    private suspend fun status(itemId: Long) = items.getItemById(itemId)!!.status
    private suspend fun details(priceId: Long) = orders.getOrder(priceId).first()!!

    private suspend fun reject(block: suspend () -> Unit) {
        try {
            block()
            fail("应拒绝这次业务修改")
        } catch (exception: RuntimeException) {
            // Invalid input and missing business references use require()/error(), respectively.
            if (exception !is IllegalArgumentException && exception !is IllegalStateException) throw exception
        }
    }

    private data class LedgerState(
        val items: List<Item>, val prices: List<Price>, val stages: List<PaymentStage>,
        val payments: List<Payment>, val bindings: List<OrderBinding>, val journal: RestoreJournal?
    )
    private suspend fun state(db: LolitaDatabase = database) = LedgerState(
        db.itemDao().getAllItemsList().sortedBy { it.id },
        db.priceDao().getAllPricesList().sortedBy { it.id },
        db.orderDao().getAllStages().sortedBy { it.id },
        db.paymentDao().getAllPaymentsList().sortedBy { it.id },
        db.orderDao().getAllBindings().sortedBy { it.itemId }, db.restoreJournalDao().get()
    )

    @Test fun unallocatedActualPaymentsDoNotDuplicateCalendarDebt() = runBlocking {
        val itemId = item()
        val priceId = create(itemId, 1000.0, stage("定金",300.0).copy(dueDate=100),stage("尾款",700.0).copy(dueDate=200),type=PriceType.DEPOSIT_BALANCE)
        orders.recordPayment(priceId,500.0,300)
        val rows = database.paymentDao().getPaymentsWithItemInfoByDateRange(0,1000).first()
        assertEquals(500.0,rows.filter { !it.isPaid }.sumOf { it.amount },0.0)
        assertEquals(1,rows.count { !it.isPaid })
        assertEquals(500.0,database.paymentDao().getMonthUnpaidTotal(0,1000).first(),0.0)
        assertEquals(500.0,database.paymentDao().getOverdueAmount(1000).first(),0.0)
        assertEquals(500.0,database.priceDao().getTotalSpending().first(),0.0)
        assertEquals(300L,rows.single { it.isPaid }.dueDate)
    }

    @Test fun oversizedHistoricalPlansDoNotDuplicateCalendarOrOverdueDebt() = runBlocking {
        val id = item()
        val priceId = create(id,1000.0,stage("定金",300.0).copy(dueDate=100),stage("尾款",700.0).copy(dueDate=200))
        val stages = database.orderDao().getStages(priceId)
        // A historical plan can be inconsistent. Keep its original amount, limit debt projections.
        database.orderDao().updateStage(stages.last().copy(amount=1000.0))
        orders.recordPayment(priceId,100.0,300)
        assertEquals(900.0,details(priceId).remainingAmount,0.0)
        assertEquals(1000.0,database.orderDao().getStages(priceId).last().amount,0.0)
        val rows = database.paymentDao().getPaymentsWithItemInfoByDateRange(0,1000).first().filter { !it.isPaid }
        assertEquals(listOf(200.0,700.0),rows.map { it.amount })
        assertEquals(900.0,database.paymentDao().getMonthUnpaidTotal(0,1000).first(),0.0)
        assertEquals(900.0,database.paymentDao().getOverdueAmount(1000).first(),0.0)
    }

    @Test fun monthlyStatisticsUseActualDatesAndNeverInventDatesForLegacyPayments() = runBlocking {
        fun date(month: Int, day: Int) = Calendar.getInstance().apply {
            set(2026, month, day, 12, 0, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val id = item()
        val due = date(Calendar.JANUARY, 1)
        val priceId = create(id,300.0,stage("全款",300.0).copy(dueDate=due))
        val stageId = database.orderDao().getStages(priceId).single().id
        // Both records must remain as original evidence. The unpaid row is an old plan,
        // while the paid row has a known amount and an unknown actual date.
        database.paymentDao().insertPayment(Payment(priceId=priceId,amount=300.0,dueDate=due,stageId=stageId))
        database.paymentDao().insertPayment(Payment(priceId=priceId,amount=100.0,dueDate=due,isPaid=true,paidDate=null))
        orders.recordPayment(priceId,100.0,date(Calendar.FEBRUARY,15))
        orders.recordPayment(priceId,100.0,date(Calendar.MARCH,15))
        assertEquals(ItemStatus.OWNED,status(id))
        assertEquals(300.0,database.priceDao().getTotalSpending().first(),0.0)
        assertEquals(mapOf("2026-02" to 100.0,"2026-03" to 100.0),
            database.priceDao().getMonthlySpending().first().associate { it.yearMonth to it.totalSpending })
        assertTrue(database.priceDao().getItemsByPurchaseMonth("2026-01").first().isEmpty())
        assertEquals(id,database.priceDao().getItemsByPurchaseMonth("2026-02").first().single().id)
        assertTrue(database.paymentDao().getPaymentsWithItemInfoByDateRange(due,date(Calendar.JANUARY,31)).first().isEmpty())
        assertEquals(0,database.priceDao().getPricesWithStatusByDateRange(due,date(Calendar.DECEMBER,31),date(Calendar.DECEMBER,31)).first().single().unpaidCount)
        assertEquals(0,database.paymentDao().getTotalUnpaidCount().first())
        assertEquals(0.0,database.paymentDao().getTotalUnpaidAmount().first(),0.0)
        assertNull(database.paymentDao().getPaymentsByPriceList(priceId).single { it.isPaid && it.paidDate == null }.paidDate)
    }

    @Test fun deletingPricedStandaloneItemCleansTheBindingAndKeepsOtherItems() = runBlocking {
        val deleted = item()
        val kept = item("保留的裙子")
        val priceId = create(deleted,100.0,stage("全款",100.0))
        orders.recordPayment(priceId,100.0,1000)
        items.deleteItem(items.getItemById(deleted)!!)
        assertNull(items.getItemById(deleted)); assertNotNull(items.getItemById(kept))
        assertNull(database.orderDao().getBinding(deleted))
        assertTrue(database.priceDao().getPricesByItemList(deleted).isEmpty())
        assertTrue(database.orderDao().getAllStages().isEmpty())
        assertTrue(database.paymentDao().getAllPaymentsList().isEmpty())
    }

    @Test fun oneItemHasOneActivePriceAndPaidStatusCannotBeOverriddenByItemEditor() = runBlocking {
        val id = item()
        val priceId = create(id, 100.0, stage("全款", 100.0))
        assertEquals(ItemStatus.PENDING_PAYMENT, status(id))
        val before = state()
        reject { create(id, 200.0, stage("另一笔价格", 200.0)) }
        assertEquals(before, state())
        assertEquals(1, database.priceDao().getPricesByItemList(id).size)
        assertEquals(priceId, database.orderDao().getBinding(id)!!.priceId)

        orders.recordPayment(priceId, 100.0, 1_000, database.orderDao().getStages(priceId).single().id)
        assertEquals(ItemStatus.OWNED, status(id))
        items.updateItem(items.getItemById(id)!!.copy(status = ItemStatus.WISHED))
        assertEquals(ItemStatus.OWNED, status(id))
    }

    @Test fun changingTotalAndBalancePreservesTheActualDepositWithoutDuplicatingIt() = runBlocking {
        val id = item()
        val priceId = create(id, 1_000.0, stage("定金", 300.0), stage("尾款", 700.0), type = PriceType.DEPOSIT_BALANCE)
        val planned = database.orderDao().getStages(priceId)
        val paymentId = orders.recordPayment(priceId, 300.0, 1_000, planned[0].id)
        val originalPayment = orders.getPayment(paymentId)!!
        val changedPrice = database.priceDao().getPriceById(priceId)!!.copy(totalPrice = 1_100.0, deposit = 300.0, balance = 800.0)
        orders.updateOrder(changedPrice, listOf(planned[0], planned[1].copy(amount = 800.0)))

        assertEquals(listOf(originalPayment), database.paymentDao().getPaymentsByPriceList(priceId))
        assertEquals(1_100.0, details(priceId).price.totalPrice, 0.0)
        assertEquals(300.0, details(priceId).paidAmount, 0.0)
        assertEquals(800.0, details(priceId).remainingAmount, 0.0)
        assertEquals(ItemStatus.PENDING_BALANCE, status(id))
        orders.recordPayment(priceId, 800.0, 2_000, planned[1].id)
        assertEquals(ItemStatus.OWNED, status(id))
    }

    @Test fun deletingAnUnpaidStageDoesNotEraseTheOutstandingDebt() = runBlocking {
        val id = item()
        val priceId = create(id, 1_000.0, stage("定金", 300.0), stage("尾款", 700.0), type = PriceType.DEPOSIT_BALANCE)
        val stages = database.orderDao().getStages(priceId)
        orders.recordPayment(priceId, 300.0, 1_000, stages[0].id)
        orders.deleteStage(stages[1].id)
        assertEquals(1, database.orderDao().getStages(priceId).size)
        assertEquals(1_000.0, details(priceId).price.totalPrice, 0.0)
        assertEquals(700.0, details(priceId).remainingAmount, 0.0)
        assertEquals(ItemStatus.PENDING_BALANCE, status(id))
        orders.recordPayment(priceId, 700.0, 2_000)
        assertEquals(ItemStatus.OWNED, status(id))
    }

    @Test fun invalidMoneyAndInsufficientRepricedTotalsRollBackEveryLedgerTable() = runBlocking {
        val id = item()
        val emptyOrder = state()
        reject { create(id, 0.0, stage("全款", 0.0)) }
        reject { create(id, -100.0, stage("全款", -100.0)) }
        reject { create(id, 90_000_000_000_000.0, stage("超出备份范围", 90_000_000_000_000.0)) }
        reject { create(id, 100.0, stage("金额不符", 90.0)) }
        reject { create(id, 100.0, stage("全款", 100.0), shipping = 101.0) }
        assertEquals(emptyOrder, state())
        val priceId = create(id, 100.0, stage("第一段", 75.0), stage("第二段", 25.0), type = PriceType.MULTI_STAGE)
        val stages = database.orderDao().getStages(priceId)
        val paymentId = orders.recordPayment(priceId, 40.0, 1_000, stages[0].id)
        val baseline = state()
        for (amount in listOf(0.0, -1.0, 61.0, 1.001)) {
            reject { orders.recordPayment(priceId, amount, 2_000) }
            assertEquals("拒绝金额 $amount 时没有部分写入", baseline, state())
        }
        reject { orders.recordPayment(priceId, 36.0, 2_000, stages[0].id) }
        assertEquals(baseline, state())
        reject { orders.editPayment(orders.getPayment(paymentId)!!.copy(amount = 101.0)) }
        assertEquals(baseline, state())
        reject { orders.updateOrder(details(priceId).price.copy(totalPrice = 30.0), listOf(stages[0].copy(amount = 30.0))) }
        assertEquals(baseline, state())
        reject { orders.updateOrder(details(priceId).price, listOf(stages[0].copy(amount = 39.0), stages[1].copy(amount = 61.0))) }
        assertEquals(baseline, state())
        orders.archiveCurrentOrder(id)
        database.orderDao().updateStage(stages[0].copy(amount=-75.0))
        val archived = state()
        reject { orders.bindHistoricalOrder(id,priceId) }
        assertEquals(archived,state())
    }

    @Test fun shippingAndSeveralPartialPaymentsSettleInCentsAndUnknownDueDatesStayUnknown() = runBlocking {
        val id = item()
        val priceId = create(id, 120.03, stage("第一段", 0.10), stage("第二段", 0.20), stage("尾款与邮费", 119.73),
            type = PriceType.MULTI_STAGE, shipping = 20.0)
        val stages = database.orderDao().getStages(priceId)
        assertTrue(stages.all { it.dueDate == null })
        assertEquals(20.0, details(priceId).price.shipping, 0.0)
        orders.recordPayment(priceId, 0.10, 1_000, stages[0].id)
        orders.recordPayment(priceId, 0.20, 2_000, stages[1].id)
        orders.recordPayment(priceId, 100.0, 3_000, stages[2].id)
        assertEquals(ItemStatus.PENDING_PAYMENT, status(id))
        assertEquals(19.73, details(priceId).remainingAmount, 0.0)
        orders.recordPayment(priceId, 19.73, 4_000, stages[2].id)
        assertEquals(120.03, details(priceId).paidAmount, 0.0)
        assertEquals(0.0, details(priceId).remainingAmount, 0.0)
        assertEquals(ItemStatus.OWNED, status(id))
    }

    @Test fun sharedAccessoriesFollowTheParentWhileIndependentlyPricedOnesKeepTheirOwnState() = runBlocking {
        val parent = item()
        val parentPrice = create(parent, 1_000.0, stage("定金", 300.0), stage("尾款", 700.0), type = PriceType.DEPOSIT_BALANCE)
        val shared = item("配套头饰", parent, shared = true)
        val separate = item("另购袜子", parent)
        val separatePrice = create(separate, 100.0, stage("全款", 100.0))
        assertEquals(parentPrice, orders.currentOrder(shared).first()!!.price.id)
        assertEquals(separatePrice, orders.currentOrder(separate).first()!!.price.id)
        assertEquals(ItemStatus.PENDING_BALANCE, status(shared))
        orders.recordPayment(separatePrice, 100.0, 1_000)
        assertEquals(ItemStatus.OWNED, status(separate))
        assertEquals(ItemStatus.PENDING_BALANCE, status(shared))
        val before = state()
        reject { create(shared, 50.0, stage("重复计价", 50.0)) }
        reject { items.attachChild(separate, parent, includedInPrice = true) }
        assertEquals(before, state())

        val stages = database.orderDao().getStages(parentPrice)
        orders.recordPayment(parentPrice, 300.0, 2_000, stages[0].id)
        val balance = orders.recordPayment(parentPrice, 700.0, 3_000, stages[1].id)
        assertEquals(ItemStatus.OWNED, status(parent))
        assertEquals(ItemStatus.OWNED, status(shared))
        orders.deletePayment(balance)
        assertEquals(ItemStatus.PENDING_BALANCE, status(parent))
        assertEquals(ItemStatus.PENDING_BALANCE, status(shared))
        assertEquals(ItemStatus.OWNED, status(separate))
    }

    @Test fun hierarchyRejectsCyclesNestedAccessoriesAndDeletionOfAParentStillInUse() = runBlocking {
        val parent = item()
        val child = item("头饰", parent)
        val other = item("另一件小物")
        val before = state()
        reject { items.attachChild(parent, child, includedInPrice = false) }
        reject { items.attachChild(child, child, includedInPrice = false) }
        reject { items.attachChild(other, child, includedInPrice = false) }
        reject { items.attachChild(child, 999_999, includedInPrice = false) }
        reject { items.deleteItem(items.getItemById(parent)!!) }
        assertEquals(before, state())
        items.detachChild(child)
        assertNull(items.getItemById(child)!!.parentItemId)
        items.deleteItem(items.getItemById(parent)!!)
        assertNull(items.getItemById(parent))
        assertNotNull(items.getItemById(child))
    }

    @Test fun backupRestoresOrdersStagesAccessoriesAndPaidRecordsIntoAnAlreadyPopulatedDatabase() = runBlocking {
        val parent = item()
        val parentPrice = create(parent, 120.0, stage("定金", 30.0), stage("尾款与邮费", 90.0),
            type = PriceType.MULTI_STAGE, shipping = 20.0)
        item("共用价格头饰", parent, shared = true)
        val separate = item("独立计价袜子", parent)
        val separatePrice = create(separate, 20.0, stage("全款", 20.0))
        orders.recordPayment(parentPrice, 30.0, 1_000, database.orderDao().getStages(parentPrice).first().id)
        orders.recordPayment(separatePrice, 20.0, 2_000)
        val expected = state()
        val output = BackupManager(context, database).exportToJson().getOrThrow()
        val target = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        try {
            val manager = BackupManager(context, target)
            val preview = manager.previewBackup(output).getOrThrow()
            assertEquals(2, preview.orderBindingCount)
            assertEquals(3, preview.paymentStageCount)
            manager.importBackup(output).getOrThrow()
            assertEquals(expected, state(target))
            // A second restore exercises self-FK RESTRICT cleanup with parent and children present.
            manager.importBackup(output).getOrThrow()
            assertEquals(expected, state(target))
            assertTrue(target.orderDao().getAllStages().all { it.dueDate == null })
        } finally {
            target.close()
            context.contentResolver.delete(output, null, null)
        }
    }
}
