package com.lolita.app.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.file.BackupManager
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.repository.OrderRepository
import com.lolita.app.ui.screen.price.PaymentEditViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Missing historical records must never create debt or fabricated actual payments. */
@RunWith(AndroidJUnit4::class)
class HistoricalPurchaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LolitaDatabase::class.java)
    private lateinit var database: LolitaDatabase
    private lateinit var orders: OrderRepository

    @Before fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        database.brandDao().insertBrand(Brand(id=1, name="历史品牌", createdAt=1))
        database.categoryDao().insertCategory(Category(id=1, name="历史分类", createdAt=1))
        orders = OrderRepository(database, context)
    }
    @After fun tearDown() { database.close() }

    private suspend fun historical(status: PurchaseRecordStatus): Pair<Long,Long> {
        val item = database.itemDao().insertItem(Item(brandId=1,categoryId=1,name="历史裙子",description="原始资料",status=ItemStatus.OWNED))
        val price = database.priceDao().insertPrice(Price(itemId=item,type=PriceType.DEPOSIT_BALANCE,totalPrice=1000.0,deposit=300.0,balance=700.0,createdAt=17))
        database.orderDao().insertBinding(OrderBinding(item,price,status))
        database.orderDao().refreshStatuses()
        return item to price
    }

    private suspend fun expectRejected(block: suspend () -> Unit) {
        try { block(); fail("无效的历史补录必须被拒绝") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun historicalOwnedKeepsOwnershipWithoutInventingSpendingOrDebt() = runBlocking {
        val (item,price) = historical(PurchaseRecordStatus.HISTORICAL_OWNED)
        database.orderDao().insertStage(PaymentStage(priceId=price,name="旧安排",amount=1000.0,dueDate=100))
        orders.reconcileReminders()
        assertEquals(ItemStatus.OWNED,database.itemDao().getItemById(item)!!.status)
        assertTrue(database.paymentDao().getAllPaymentsList().isEmpty())
        assertEquals(0.0,database.priceDao().getTotalSpending().first(),0.0)
        assertEquals(0.0,database.paymentDao().getTotalUnpaidAmount().first(),0.0)
        assertEquals(0,database.paymentDao().getTotalUnpaidCount().first())
        assertEquals(0.0,database.paymentDao().getOverdueAmount(1000).first(),0.0)
        assertTrue(database.paymentDao().getPaymentsWithItemInfoByDateRange(0,1000).first().isEmpty())
        assertEquals(1000.0,database.priceDao().getPriceById(price)!!.totalPrice,0.0)
    }

    @Test fun reviewConfirmationUpdatesTheCurrentFlowWithoutCreatingPayments() = runBlocking {
        val (item,price) = historical(PurchaseRecordStatus.NEEDS_REVIEW)
        assertEquals(ItemStatus.PENDING_REVIEW,database.itemDao().getItemById(item)!!.status)
        assertEquals(0.0,database.paymentDao().getTotalUnpaidAmount().first(),0.0)
        try { orders.recordPayment(price,100.0,1000); fail("待核对不能录入账目") }
        catch (_: IllegalArgumentException) { }
        val initial = CompletableDeferred<Unit>()
        val observed = async { orders.currentOrder(item).map { it!!.purchaseRecordStatus }.distinctUntilChanged().onEach { if (!initial.isCompleted) initial.complete(Unit) }.take(2).toList() }
        kotlinx.coroutines.withTimeout(5000) { initial.await() }
        orders.confirmHistoricalPurchase(item)
        val states = kotlinx.coroutines.withTimeout(5000) { observed.await() }
        assertEquals(listOf(PurchaseRecordStatus.NEEDS_REVIEW,PurchaseRecordStatus.HISTORICAL_OWNED),states)
        assertEquals(ItemStatus.OWNED,database.itemDao().getItemById(item)!!.status)
        assertTrue(database.paymentDao().getAllPaymentsList().isEmpty())
        orders.recordPayment(price,300.0,2000)
        assertEquals(ItemStatus.OWNED,database.itemDao().getItemById(item)!!.status)
        assertEquals(300.0,database.priceDao().getTotalSpending().first(),0.0)
        assertEquals(0.0,database.paymentDao().getTotalUnpaidAmount().first(),0.0)
        assertEquals(0,database.priceDao().getPricesWithStatusByDateRange(0,3000,3000).first().single().unpaidCount)
        val original=database.paymentDao().getAllPaymentsList()
        orders.archiveCurrentOrder(item)
        orders.bindHistoricalOrder(item,price)
        assertEquals(ItemStatus.OWNED,database.itemDao().getItemById(item)!!.status)
        assertEquals(PurchaseRecordStatus.HISTORICAL_OWNED,orders.currentOrder(item).first()!!.purchaseRecordStatus)
        assertEquals(original,database.paymentDao().getAllPaymentsList())
        assertEquals(0.0,database.paymentDao().getTotalUnpaidAmount().first(),0.0)
    }

    @Test fun historicalPaymentEntryRequiresAnExplicitRealDate() = runBlocking {
        val (_,price) = historical(PurchaseRecordStatus.HISTORICAL_OWNED)
        val editor=PaymentEditViewModel(orders)
        editor.loadPayment(price,null)
        val state=kotlinx.coroutines.withTimeout(5000) { editor.uiState.first { !it.isLoading } }
        assertNull(state.paidDate)
        editor.updateAmount("100")
        assertFalse(editor.isValid())
        editor.updatePaidDate(1234)
        assertTrue(editor.isValid())
        editor.save(price).getOrThrow()
        assertEquals(1234L,database.paymentDao().getAllPaymentsList().single().paidDate)
        assertEquals(ItemStatus.OWNED,database.itemDao().getItemById(database.priceDao().getPriceById(price)!!.itemId)!!.status)
    }

    @Test fun historicalEditorRequiresAnExplicitUnknownChoiceAndNeverAutoFillsReferences() = runBlocking {
        val (item,price) = historical(PurchaseRecordStatus.HISTORICAL_OWNED)
        val description = "淘宝订单：原始订单号 123；金额仅供核对"
        database.itemDao().updateItem(database.itemDao().getItemById(item)!!.copy(source="淘宝",description=description))
        orders.recordHistoricalPayment(price,300.0,null)
        val editor = PaymentEditViewModel(orders)
        editor.loadPayment(price,null)
        val state = kotlinx.coroutines.withTimeout(5000) { editor.uiState.first { !it.isLoading } }
        assertEquals("历史裙子",state.itemName)
        assertEquals("淘宝",state.purchaseSource)
        assertEquals(description,state.purchaseDescription)
        assertEquals(1000.0,state.orderTotal,0.0)
        assertEquals(300.0,state.recordedPaidAmount,0.0)
        assertEquals(700.0,state.remainingAmount,0.0)
        assertEquals("",state.amount)
        assertNull(state.paidDate)
        assertTrue(state.canRecordUnknownDate)
        assertFalse(state.isPaidDateUnknown)
        editor.fillUnrecordedAmount()
        assertEquals(700.0,editor.uiState.value.amount.toDouble(),0.0)
        assertFalse(editor.isValid())
        editor.updatePaidDateUnknown(true)
        assertTrue(editor.isValid())
        editor.updatePaidDateUnknown(false)
        assertFalse(editor.isValid())
        editor.updatePaidDate(1234)
        assertTrue(editor.isValid())
        assertFalse(editor.uiState.value.isPaidDateUnknown)
        editor.updatePaidDateUnknown(true)
        assertNull(editor.uiState.value.paidDate)
        val (_,otherPrice) = historical(PurchaseRecordStatus.HISTORICAL_OWNED)
        val original = database.paymentDao().getAllPaymentsList()
        assertTrue(editor.save(otherPrice).isFailure)
        assertEquals(original,database.paymentDao().getAllPaymentsList())
        val id = editor.save(price).getOrThrow()
        assertEquals(price,orders.getPayment(id)!!.priceId)
        assertNull(orders.getPayment(id)!!.paidDate)
        assertEquals(0L,orders.getPayment(id)!!.dueDate)
        assertEquals(ItemStatus.OWNED,database.itemDao().getItemById(item)!!.status)
        assertEquals(PurchaseRecordStatus.HISTORICAL_OWNED,orders.currentOrder(item).first()!!.purchaseRecordStatus)
    }

    @Test fun existingUnknownPaymentEditorPreservesItsDateWithoutCreatingAnotherRecord() = runBlocking {
        val (_,price) = historical(PurchaseRecordStatus.HISTORICAL_OWNED)
        val id = orders.recordHistoricalPayment(price,100.0,null)
        val editor = PaymentEditViewModel(orders)
        editor.loadPayment(price,id)
        val state = kotlinx.coroutines.withTimeout(5000) { editor.uiState.first { !it.isLoading } }
        assertTrue(state.canRecordUnknownDate)
        assertTrue(state.isPaidDateUnknown)
        assertNull(state.paidDate)
        assertTrue(editor.isValid())
        editor.fillUnrecordedAmount()
        assertEquals(100.0,editor.uiState.value.amount.toDouble(),0.0)
        editor.updateAmount("150")
        editor.update(id).getOrThrow()
        val payment = database.paymentDao().getAllPaymentsList().single()
        assertEquals(id,payment.id)
        assertEquals(150.0,payment.amount,0.0)
        assertNull(payment.paidDate)
        assertEquals(0L,payment.dueDate)
        assertTrue(editor.save(price).isFailure)
        assertEquals(listOf(payment),database.paymentDao().getAllPaymentsList())
    }

    @Test fun trackedNewPaymentEditorCannotChooseAnUnknownDate() = runBlocking {
        val (_,price) = historical(PurchaseRecordStatus.TRACKED)
        val editor = PaymentEditViewModel(orders)
        editor.loadPayment(price,null)
        val state = kotlinx.coroutines.withTimeout(5000) { editor.uiState.first { !it.isLoading } }
        assertFalse(state.canRecordUnknownDate)
        assertFalse(state.isPaidDateUnknown)
        assertNotNull(state.paidDate)
        editor.updateAmount("100")
        editor.updatePaidDateUnknown(true)
        assertFalse(editor.uiState.value.isPaidDateUnknown)
        assertEquals(state.paidDate,editor.uiState.value.paidDate)
        editor.updatePaidDate(null)
        assertFalse(editor.isValid())
        assertTrue(editor.save(price).isFailure)
        assertTrue(database.paymentDao().getAllPaymentsList().isEmpty())
    }

    @Test fun unknownHistoricalDateCountsActualSpendingWithoutCreatingMonthlySpendingOrDebt() = runBlocking {
        val (item,price) = historical(PurchaseRecordStatus.HISTORICAL_OWNED)
        val id = orders.recordHistoricalPayment(price,300.01,null)
        val actual = orders.getPayment(id)!!
        assertTrue(actual.isPaid)
        assertNull(actual.paidDate)
        assertEquals(0L,actual.dueDate)
        assertFalse(actual.reminderSet)
        assertNull(actual.calendarEventId)
        assertEquals(300.01,database.priceDao().getTotalSpending().first(),0.0)
        assertEquals(300.01,database.priceDao().getUndatedSpending().first(),0.0)
        assertEquals(300.01,database.priceDao().getSpendingByBrand().first().sumOf { it.totalSpending },0.0)
        assertEquals(300.01,database.priceDao().getSpendingByCategory().first().sumOf { it.totalSpending },0.0)
        assertEquals(300.01,database.priceDao().getSpendingByStyle().first().sumOf { it.totalSpending },0.0)
        assertEquals(300.01,database.priceDao().getSpendingBySeasonRaw().first().sumOf { it.totalSpending },0.0)
        assertTrue(database.priceDao().getMonthlySpending().first().isEmpty())
        assertTrue(database.priceDao().getItemsByPurchaseMonth("1970-01").first().isEmpty())
        assertTrue(database.paymentDao().getPaymentsWithItemInfoByDateRange(0,Long.MAX_VALUE).first().isEmpty())
        assertEquals(0.0,database.paymentDao().getTotalUnpaidAmount().first(),0.0)
        assertEquals(ItemStatus.OWNED,database.itemDao().getItemById(item)!!.status)
        assertEquals(PurchaseRecordStatus.HISTORICAL_OWNED,orders.currentOrder(item).first()!!.purchaseRecordStatus)
        assertEquals(item,orders.getOrderItem(price)!!.id)
        assertEquals("原始资料",orders.getOrderItem(price)!!.description)
        assertNull(orders.getOrderItem(Long.MAX_VALUE))
    }

    @Test fun editingActualPaymentsPreservesUnknownDatesAndProtectsTrackedKnownDates() = runBlocking {
        val (_,ownedPrice) = historical(PurchaseRecordStatus.HISTORICAL_OWNED)
        val unknown = orders.recordHistoricalPayment(ownedPrice,100.0,null)
        orders.editPayment(orders.getPayment(unknown)!!.copy(amount=150.0))
        assertNull(orders.getPayment(unknown)!!.paidDate)
        assertEquals(0L,orders.getPayment(unknown)!!.dueDate)
        val known = orders.recordHistoricalPayment(ownedPrice,200.0,1234)
        orders.editPayment(orders.getPayment(known)!!.copy(paidDate=null))
        assertNull(orders.getPayment(known)!!.paidDate)
        assertEquals(0L,orders.getPayment(known)!!.dueDate)
        val (_,trackedPrice) = historical(PurchaseRecordStatus.TRACKED)
        val oldUnknown = database.paymentDao().insertPayment(Payment(priceId=trackedPrice,amount=20.0,
            dueDate=77,isPaid=true,paidDate=null,reminderSet=true))
        orders.editPayment(orders.getPayment(oldUnknown)!!.copy(amount=25.0))
        assertNull(orders.getPayment(oldUnknown)!!.paidDate)
        assertEquals(77L,orders.getPayment(oldUnknown)!!.dueDate)
        assertFalse(orders.getPayment(oldUnknown)!!.reminderSet)
        val trackedKnown = orders.recordPayment(trackedPrice,30.0,2345)
        val original = orders.getPayment(trackedKnown)!!
        expectRejected { orders.editPayment(original.copy(paidDate=null)) }
        assertEquals(original,orders.getPayment(trackedKnown))
    }

    @Test fun historicalSupplementRejectsTrackedReviewAndArchivedOrders() = runBlocking {
        val (_,trackedPrice) = historical(PurchaseRecordStatus.TRACKED)
        val (_,reviewPrice) = historical(PurchaseRecordStatus.NEEDS_REVIEW)
        for (price in listOf(trackedPrice,reviewPrice)) {
            expectRejected { orders.recordHistoricalPayment(price,100.0,null) }
            expectRejected { orders.recordHistoricalPayment(price,100.0,1234) }
        }
        val (owned,ownedPrice) = historical(PurchaseRecordStatus.HISTORICAL_OWNED)
        orders.archiveCurrentOrder(owned)
        try { orders.recordHistoricalPayment(ownedPrice,100.0,null); fail("归档订单不可直接补录") }
        catch (_: IllegalStateException) { }
        assertTrue(database.paymentDao().getAllPaymentsList().isEmpty())
    }

    @Test fun historicalSupplementCannotDoubleBookOrExceedTheRecordingLimit() = runBlocking {
        val (item,price) = historical(PurchaseRecordStatus.HISTORICAL_OWNED)
        orders.recordHistoricalPayment(price,600.0,null)
        val original = database.paymentDao().getAllPaymentsList()
        expectRejected { orders.recordHistoricalPayment(price,600.0,null) }
        expectRejected { orders.recordHistoricalPayment(price,400.01,null) }
        assertEquals(original,database.paymentDao().getAllPaymentsList())
        orders.recordHistoricalPayment(price,400.0,null)
        expectRejected { orders.recordHistoricalPayment(price,0.01,null) }
        assertEquals(2,database.paymentDao().getAllPaymentsList().size)
        assertEquals(1000.0,database.priceDao().getTotalSpending().first(),0.0)
        assertEquals(PurchaseRecordStatus.HISTORICAL_OWNED,orders.currentOrder(item).first()!!.purchaseRecordStatus)
        assertEquals(ItemStatus.OWNED,database.itemDao().getItemById(item)!!.status)
        assertEquals(0.0,database.paymentDao().getTotalUnpaidAmount().first(),0.0)
    }

    @Test fun confirmedOutstandingAndNewOrdersStillCountActualDebt() = runBlocking {
        val (item,price) = historical(PurchaseRecordStatus.NEEDS_REVIEW)
        orders.confirmTrackedOrder(item, paymentRecordsVerified = true)
        assertEquals(ItemStatus.PENDING_BALANCE,database.itemDao().getItemById(item)!!.status)
        assertEquals(1000.0,database.paymentDao().getTotalUnpaidAmount().first(),0.0)
        orders.recordPayment(price,300.0,1000)
        assertEquals(700.0,database.paymentDao().getTotalUnpaidAmount().first(),0.0)
        val newItem=database.itemDao().insertItem(Item(brandId=1,categoryId=1,name="新订单",description="",status=ItemStatus.WISHED))
        orders.createOrder(Price(itemId=newItem,type=PriceType.FULL,totalPrice=100.0),listOf(PaymentStage(priceId=0,name="全款",amount=100.0)))
        assertEquals(ItemStatus.PENDING_PAYMENT,database.itemDao().getItemById(newItem)!!.status)
        assertEquals(800.0,database.paymentDao().getTotalUnpaidAmount().first(),0.0)
    }

    @Test fun backupRoundTripPreservesHistoricalEvidenceAndRawRows() = runBlocking {
        val (owned,ownedPrice) = historical(PurchaseRecordStatus.HISTORICAL_OWNED)
        val (review,_) = historical(PurchaseRecordStatus.NEEDS_REVIEW)
        orders.recordHistoricalPayment(ownedPrice,100.0,null)
        val originalPayments=database.paymentDao().getAllPaymentsList()
        val originalPrices=database.priceDao().getAllPricesList()
        val output=BackupManager(context,database).exportToJson().getOrThrow()
        val target=Room.inMemoryDatabaseBuilder(context,LolitaDatabase::class.java).build()
        try {
            val manager=BackupManager(context,target)
            val preview=manager.previewBackup(output).getOrThrow()
            assertEquals(1,preview.historicalOwnedCount); assertEquals(1,preview.needsReviewCount)
            manager.importBackup(output).getOrThrow()
            assertEquals(database.orderDao().getAllBindings(),target.orderDao().getAllBindings())
            assertEquals(originalPayments,target.paymentDao().getAllPaymentsList())
            assertNull(target.paymentDao().getAllPaymentsList().single().paidDate)
            assertEquals(0L,target.paymentDao().getAllPaymentsList().single().dueDate)
            assertEquals(100.0,target.priceDao().getTotalSpending().first(),0.0)
            assertTrue(target.priceDao().getMonthlySpending().first().isEmpty())
            assertEquals(originalPrices,target.priceDao().getAllPricesList())
            assertEquals(ItemStatus.OWNED,target.itemDao().getItemById(owned)!!.status)
            assertEquals(ItemStatus.PENDING_REVIEW,target.itemDao().getItemById(review)!!.status)
            assertEquals(0.0,target.paymentDao().getTotalUnpaidAmount().first(),0.0)
        } finally { target.close();context.contentResolver.delete(output,null,null) }
    }

    @Test fun migrationSeparatesPreservedOwnedEvidenceFromAlreadyOverwrittenStatus() = runBlocking {
        for (version in listOf(20,21)) {
            val name="historical_purchase_v$version"
            context.deleteDatabase(name)
            helper.createDatabase(name,version).apply {
                execSQL("INSERT INTO brands(id,name,created_at,is_preset) VALUES(1,'品牌',1,0)")
                execSQL("INSERT INTO categories(id,name,created_at,is_preset,category_group) VALUES(1,'分类',1,0,'CLOTHING')")
                for ((id,status) in listOf(1 to "OWNED",2 to "PENDING_BALANCE",3 to "OWNED",4 to "PENDING_BALANCE",5 to "OWNED")) {
                    execSQL("INSERT INTO items(id,brand_id,category_id,name,description,status,created_at,updated_at) VALUES($id,1,1,'裙子$id','原始资料','$status',10,11)")
                    execSQL("INSERT INTO prices(id,item_id,type,total_price,deposit,balance,created_at) VALUES($id,$id,'DEPOSIT_BALANCE',1000,300,700,17)")
                    if(version==21) execSQL("INSERT INTO order_bindings(item_id,price_id) VALUES($id,$id)")
                    if(id >= 3) {
                        val paid=if(id==5) 1 else 0
                        execSQL("INSERT INTO payments(id,price_id,amount,is_paid,paid_date,due_date,created_at,reminder_set) VALUES($id,$id,300,$paid,${if(paid==1) "123" else "NULL"},100,17,0)")
                        if(version==21) execSQL("INSERT INTO payment_stages(id,price_id,name,amount,due_date,reminder_set) VALUES($id,$id,'原计划',1000,100,0)")
                    }
                }
                close()
            }
            val migrated=helper.runMigrationsAndValidate(name,23,true,LolitaDatabase.MIGRATION_20_21,LolitaDatabase.MIGRATION_21_22,LolitaDatabase.MIGRATION_22_23)
            migrated.close()
            val target=Room.databaseBuilder(context,LolitaDatabase::class.java,name).build()
            try {
                target.orderDao().refreshStatuses()
                assertEquals(PurchaseRecordStatus.HISTORICAL_OWNED,target.orderDao().getBinding(1)!!.purchaseRecordStatus)
                assertEquals(PurchaseRecordStatus.NEEDS_REVIEW,target.orderDao().getBinding(2)!!.purchaseRecordStatus)
                assertEquals(ItemStatus.OWNED,target.itemDao().getItemById(1)!!.status)
                assertEquals(ItemStatus.PENDING_REVIEW,target.itemDao().getItemById(2)!!.status)
                assertEquals(PurchaseRecordStatus.HISTORICAL_OWNED,target.orderDao().getBinding(3)!!.purchaseRecordStatus)
                assertEquals(PurchaseRecordStatus.TRACKED,target.orderDao().getBinding(4)!!.purchaseRecordStatus)
                assertEquals(PurchaseRecordStatus.HISTORICAL_OWNED,target.orderDao().getBinding(5)!!.purchaseRecordStatus)
                assertEquals(ItemStatus.OWNED,target.itemDao().getItemById(3)!!.status)
                assertEquals(ItemStatus.PENDING_BALANCE,target.itemDao().getItemById(4)!!.status)
                assertEquals(ItemStatus.OWNED,target.itemDao().getItemById(5)!!.status)
                assertEquals(3,target.paymentDao().getAllPaymentsList().size)
                assertEquals(1,target.paymentDao().getAllPaymentsList().count { it.isPaid })
                assertEquals(3,target.orderDao().getAllStages().size)
                assertEquals(List(5) { 1000.0 },target.priceDao().getAllPricesList().map { it.totalPrice })
                assertEquals(1000.0,target.paymentDao().getTotalUnpaidAmount().first(),0.0)
            } finally { target.close();context.deleteDatabase(name) }
        }
    }
}
