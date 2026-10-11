package com.lolita.app.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Order evidence survives changes to the current selection and inherited accessory status. */
@RunWith(AndroidJUnit4::class)
class PersistentOrderEvidenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LolitaDatabase::class.java)
    private lateinit var database: LolitaDatabase
    private lateinit var orders: OrderRepository
    private lateinit var items: ItemRepository

    @Before fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        database.brandDao().insertBrand(Brand(id = 101, name = "购买证据品牌", createdAt = 1))
        database.categoryDao().insertCategory(Category(id = 307, name = "购买证据分类", createdAt = 2))
        orders = OrderRepository(database, context)
        items = ItemRepository(database.itemDao(), database = database)
    }

    @After fun tearDown() { database.close() }

    private suspend fun item(name: String): Long = items.insertItem(
        Item(brandId = 101, categoryId = 307, name = name, description = "原始购买说明",
            status = ItemStatus.WISHED, source = "原始来源", createdAt = 10, updatedAt = 11)
    )

    private suspend fun historicalPrice(
        itemId: Long, evidence: PurchaseRecordStatus, total: Double = 1000.0, paid: Double = 300.0
    ): Long {
        val priceId = database.priceDao().insertPrice(Price(itemId = itemId, type = PriceType.DEPOSIT_BALANCE,
            totalPrice = total, shipping = 20.0, deposit = 300.0, balance = total - 300.0,
            createdAt = 17, purchaseRecordStatus = evidence))
        val stageId = database.orderDao().insertStage(PaymentStage(priceId = priceId, name = "原付款计划",
            amount = total, dueDate = 3000, reminderSet = false))
        database.paymentDao().insertPayment(Payment(priceId = priceId, stageId = stageId, amount = paid,
            dueDate = 1234, paidDate = 1234, isPaid = true, createdAt = 18))
        return priceId
    }

    private suspend fun bind(itemId: Long, priceId: Long) = orders.bindHistoricalOrder(itemId, priceId)

    private data class RawOrder(val price: Price, val stages: List<PaymentStage>, val payments: List<Payment>)
    private suspend fun raw(priceId: Long, db: LolitaDatabase = database) = RawOrder(
        db.priceDao().getPriceById(priceId)!!, db.orderDao().getStages(priceId),
        db.paymentDao().getPaymentsByPriceList(priceId)
    )

    private data class DatabaseState(
        val items: List<Item>, val prices: List<Price>, val stages: List<PaymentStage>,
        val payments: List<Payment>, val bindings: List<OrderBinding>, val journal: RestoreJournal?
    )
    private suspend fun state(db: LolitaDatabase = database) = DatabaseState(
        db.itemDao().getAllItemsList().sortedBy { it.id }, db.priceDao().getAllPricesList().sortedBy { it.id },
        db.orderDao().getAllStages().sortedBy { it.id }, db.paymentDao().getAllPaymentsList().sortedBy { it.id },
        db.orderDao().getAllBindings().sortedBy { it.itemId }, db.restoreJournalDao().get()
    )

    private suspend fun reject(block: suspend () -> Unit) {
        try { block(); fail("未核对完整实付资料不能启用付款跟踪") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun historicalAccessoryKeepsItsEvidenceAfterSharingDetachingAndRebinding() = runBlocking {
        val accessory = item("历史已购小物")
        val accessoryPrice = historicalPrice(accessory, PurchaseRecordStatus.HISTORICAL_OWNED)
        bind(accessory, accessoryPrice)
        val original = raw(accessoryPrice)
        val parent = item("未付清主件")
        val parentPrice = orders.createOrder(Price(itemId = parent, type = PriceType.FULL, totalPrice = 500.0),
            listOf(PaymentStage(priceId = 0, name = "全款", amount = 500.0)))

        orders.archiveCurrentOrder(accessory)
        assertNull(database.orderDao().getBinding(accessory))
        assertEquals(original, raw(accessoryPrice))
        items.attachChild(accessory, parent, includedInPrice = true)
        assertEquals(ItemStatus.PENDING_PAYMENT, items.getItemById(accessory)!!.status)
        assertEquals(parentPrice, orders.currentOrder(accessory).first()!!.price.id)
        items.detachChild(accessory)
        assertEquals(ItemStatus.PENDING_PAYMENT, items.getItemById(accessory)!!.status)
        bind(accessory, accessoryPrice)

        assertEquals(ItemStatus.OWNED, items.getItemById(accessory)!!.status)
        assertEquals(PurchaseRecordStatus.HISTORICAL_OWNED, database.orderDao().getBinding(accessory)!!.purchaseRecordStatus)
        assertEquals(original, raw(accessoryPrice))
        assertNull(orders.currentOrder(accessory).first()!!.knownRemainingAmount)
        assertEquals(500.0, database.paymentDao().getTotalUnpaidAmount().first(), 0.0)
        assertEquals(1, database.paymentDao().getTotalUnpaidCount().first())
    }

    @Test fun switchingHistoricalOrdersDoesNotTransferPurchaseEvidenceBetweenThem() = runBlocking {
        val itemId = item("有两笔旧价格的服饰")
        val historical = historicalPrice(itemId, PurchaseRecordStatus.HISTORICAL_OWNED)
        val tracked = historicalPrice(itemId, PurchaseRecordStatus.TRACKED, total = 1200.0, paid = 200.0)
        val originalHistorical = raw(historical)
        val originalTracked = raw(tracked)
        bind(itemId, historical)
        assertEquals(ItemStatus.OWNED, items.getItemById(itemId)!!.status)

        repeat(2) {
            bind(itemId, tracked)
            assertEquals(PurchaseRecordStatus.TRACKED, orders.currentOrder(itemId).first()!!.purchaseRecordStatus)
            assertEquals(ItemStatus.PENDING_BALANCE, items.getItemById(itemId)!!.status)
            assertEquals(1000.0, database.paymentDao().getTotalUnpaidAmount().first(), 0.0)
            assertEquals(originalHistorical, raw(historical))
            bind(itemId, historical)
            assertEquals(PurchaseRecordStatus.HISTORICAL_OWNED, orders.currentOrder(itemId).first()!!.purchaseRecordStatus)
            assertEquals(ItemStatus.OWNED, items.getItemById(itemId)!!.status)
            assertEquals(0.0, database.paymentDao().getTotalUnpaidAmount().first(), 0.0)
            assertEquals(originalTracked, raw(tracked))
        }
        assertEquals(originalHistorical, raw(historical))
    }

    @Test fun archivedPurchaseEvidenceSurvivesJsonBackupAndRestoreBeforeRebinding() = runBlocking {
        val itemId = item("归档的历史已购物品")
        val priceId = historicalPrice(itemId, PurchaseRecordStatus.HISTORICAL_OWNED)
        bind(itemId, priceId)
        orders.archiveCurrentOrder(itemId)
        // A later unpriced-item edit cannot erase the archived order's purchase evidence.
        items.updateItem(items.getItemById(itemId)!!.copy(status = ItemStatus.WISHED))
        val original = state()
        val output = BackupManager(context, database).exportToJson().getOrThrow()
        val target = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        try {
            val manager = BackupManager(context, target)
            val preview = manager.previewBackup(output).getOrThrow()
            assertEquals(1, preview.archivedOrderCount)
            assertEquals(1, preview.archivedHistoricalOwnedCount)
            assertEquals(0, preview.archivedNeedsReviewCount)
            manager.importBackup(output).getOrThrow()
            assertEquals(original.items, state(target).items)
            assertEquals(original.prices, state(target).prices)
            assertEquals(original.stages, state(target).stages)
            assertEquals(original.payments, state(target).payments)
            assertEquals(original.bindings, state(target).bindings)
            assertNull(target.orderDao().getBinding(itemId))
            assertEquals(PurchaseRecordStatus.HISTORICAL_OWNED, target.priceDao().getPriceById(priceId)!!.purchaseRecordStatus)

            val restoredOrders = OrderRepository(target, context)
            restoredOrders.bindHistoricalOrder(itemId, priceId)
            assertEquals(PurchaseRecordStatus.HISTORICAL_OWNED, restoredOrders.currentOrder(itemId).first()!!.purchaseRecordStatus)
            assertEquals(ItemStatus.OWNED, target.itemDao().getItemById(itemId)!!.status)
            assertEquals(0.0, target.paymentDao().getTotalUnpaidAmount().first(), 0.0)
            assertEquals(original.prices, state(target).prices)
            assertEquals(original.payments, state(target).payments)
            assertEquals(original.stages, state(target).stages)
        } finally {
            target.close()
            context.contentResolver.delete(output, null, null)
        }
    }

    @Test fun enablingTrackingRequiresExplicitVerificationAndNeverCreatesActualPayments() = runBlocking {
        for (evidence in listOf(PurchaseRecordStatus.HISTORICAL_OWNED, PurchaseRecordStatus.NEEDS_REVIEW)) {
            val itemId = item("核对付款资料 $evidence")
            val priceId = historicalPrice(itemId, evidence)
            bind(itemId, priceId)
            val original = state()
            reject { orders.confirmTrackedOrder(itemId) }
            assertEquals(original, state())
            reject { orders.confirmTrackedOrder(itemId, paymentRecordsVerified = false) }
            assertEquals(original, state())

            orders.confirmTrackedOrder(itemId, paymentRecordsVerified = true)
            assertEquals(PurchaseRecordStatus.TRACKED, database.orderDao().getBinding(itemId)!!.purchaseRecordStatus)
            assertEquals(PurchaseRecordStatus.TRACKED, database.priceDao().getPriceById(priceId)!!.purchaseRecordStatus)
            assertEquals(ItemStatus.PENDING_BALANCE, items.getItemById(itemId)!!.status)
            assertEquals(original.payments, state().payments)
            assertEquals(original.stages, state().stages)
            assertEquals(original.prices.map { if (it.id == priceId) it.copy(purchaseRecordStatus = PurchaseRecordStatus.TRACKED) else it }, state().prices)
            assertEquals(700.0, orders.currentOrder(itemId).first()!!.knownRemainingAmount!!, 0.0)
        }
    }

    @Test fun migration22To23PreservesAllRawAmountsAndCurrentEvidenceAndReviewsUnboundPrices() = runBlocking {
        val name = "persistent_order_evidence_v22"
        context.deleteDatabase(name)
        val evidence = listOf(PurchaseRecordStatus.TRACKED, PurchaseRecordStatus.HISTORICAL_OWNED, PurchaseRecordStatus.NEEDS_REVIEW)
        val expectedPrices = (1L..4L).map { id ->
            Price(id = id, itemId = id, createdAt = 17 + id, type = PriceType.DEPOSIT_BALANCE,
                totalPrice = 1000.123456789, shipping = 20.123456789, deposit = 300.123456789,
                balance = 700.987654321, purchaseRecordStatus = evidence.getOrNull((id - 1).toInt()) ?: PurchaseRecordStatus.NEEDS_REVIEW)
        }
        val expectedStages = (1L..4L).map { id ->
            PaymentStage(id = id, priceId = id, name = "原阶段 $id", amount = 700.987654321,
                dueDate = 3000 + id, reminderSet = true, customReminderDays = 3, calendarEventId = 100 + id)
        }
        val expectedPayments = (1L..4L).map { id ->
            Payment(id = id, priceId = id, stageId = id, amount = 300.123456789,
                dueDate = 1234 + id, paidDate = if (id % 2L == 0L) null else 2345 + id,
                isPaid = id != 4L, createdAt = 18 + id, reminderSet = true,
                customReminderDays = 2, calendarEventId = 200 + id)
        }
        try {
            helper.createDatabase(name, 22).use { db ->
                db.execSQL("INSERT INTO brands(id,name,created_at,is_preset) VALUES(1,'迁移品牌',1,0)")
                db.execSQL("INSERT INTO categories(id,name,created_at,is_preset,category_group) VALUES(1,'迁移分类',2,0,'CLOTHING')")
                for (price in expectedPrices) {
                    val id = price.id
                    db.execSQL("INSERT INTO items(id,brand_id,category_id,name,description,status,created_at,updated_at) VALUES($id,1,1,'服饰 $id','原始资料','OWNED',10,11)")
                    db.execSQL("INSERT INTO prices(id,item_id,type,total_price,shipping,deposit,balance,created_at) VALUES($id,$id,'DEPOSIT_BALANCE',${price.totalPrice},${price.shipping},${price.deposit},${price.balance},${price.createdAt})")
                    if (id < 4L) db.execSQL("INSERT INTO order_bindings(item_id,price_id,purchase_record_status) VALUES($id,$id,'${price.purchaseRecordStatus.name}')")
                }
                for (stage in expectedStages) db.execSQL("INSERT INTO payment_stages(id,price_id,name,amount,due_date,reminder_set,custom_reminder_days,calendar_event_id) VALUES(${stage.id},${stage.priceId},'${stage.name}',${stage.amount},${stage.dueDate},1,${stage.customReminderDays},${stage.calendarEventId})")
                for (payment in expectedPayments) db.execSQL("INSERT INTO payments(id,price_id,stage_id,amount,due_date,paid_date,is_paid,created_at,reminder_set,custom_reminder_days,calendar_event_id) VALUES(${payment.id},${payment.priceId},${payment.stageId},${payment.amount},${payment.dueDate},${payment.paidDate ?: "NULL"},${if (payment.isPaid) 1 else 0},${payment.createdAt},1,${payment.customReminderDays},${payment.calendarEventId})")
            }
            helper.runMigrationsAndValidate(name, 23, true, LolitaDatabase.MIGRATION_22_23).close()
            val target = Room.databaseBuilder(context, LolitaDatabase::class.java, name).build()
            try {
                assertEquals(expectedPrices, target.priceDao().getAllPricesList().sortedBy { it.id })
                assertEquals(expectedStages, target.orderDao().getAllStages().sortedBy { it.id })
                assertEquals(expectedPayments, target.paymentDao().getAllPaymentsList().sortedBy { it.id })
                assertEquals(evidence.mapIndexed { index, status -> OrderBinding(index + 1L, index + 1L, status) },
                    target.orderDao().getAllBindings().sortedBy { it.itemId })
                assertNull(target.orderDao().getBinding(4))
                assertEquals(List(4) { ItemStatus.OWNED }, target.itemDao().getAllItemsList().sortedBy { it.id }.map { it.status })
            } finally { target.close() }
        } finally { context.deleteDatabase(name) }
    }
}
