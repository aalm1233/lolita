package com.lolita.app.data

import android.Manifest
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.lolita.app.data.file.BackupManager
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
import com.lolita.app.data.local.entity.RestoreJournal
import com.lolita.app.data.notification.CalendarEventHelper
import com.lolita.app.data.repository.OrderRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated Room data; calendar cleanup must fail deterministically without touching a provider. */
@RunWith(AndroidJUnit4::class)
class ReminderJournalTest {
    private val baseContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val deniedContext = object : ContextWrapper(baseContext) {
        override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
            if (isCalendar(permission)) PackageManager.PERMISSION_DENIED
            else super.checkPermission(permission, pid, uid)

        override fun checkSelfPermission(permission: String): Int =
            if (isCalendar(permission)) PackageManager.PERMISSION_DENIED
            else super.checkSelfPermission(permission)

        private fun isCalendar(permission: String) =
            permission == Manifest.permission.READ_CALENDAR || permission == Manifest.permission.WRITE_CALENDAR
    }
    private val gson = Gson()
    private lateinit var database: LolitaDatabase
    private lateinit var orders: OrderRepository
    private lateinit var first: Fixture
    private lateinit var second: Fixture

    private data class Fixture(val priceId: Long, val stageId: Long, val paymentId: Long)

    @Before
    fun setUp() = runBlocking {
        assertFalse(CalendarEventHelper.hasCalendarPermission(deniedContext))
        database = Room.inMemoryDatabaseBuilder(baseContext, LolitaDatabase::class.java).build()
        database.brandDao().insertBrand(Brand(id = 101, name = "提醒回归品牌", createdAt = 1))
        database.categoryDao().insertCategory(Category(id = 307, name = "提醒回归分类", createdAt = 2))
        orders = OrderRepository(database, deniedContext)
        // High, separate test IDs avoid matching ordinary application reminder PendingIntents.
        // reminderSet=false means reconciliation never creates alarms or calendar events.
        first = fixture(1, 301)
        second = fixture(2, 401)
        assertNull(database.restoreJournalDao().get())
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    private suspend fun fixture(offset: Long, calendarId: Long): Fixture {
        val itemId = 1_600_000_000L + offset
        val priceId = 1_700_000_000L + offset
        val stageId = 1_800_000_000L + offset
        val paymentId = 1_500_000_000L + offset
        database.itemDao().insertItem(Item(id = itemId, brandId = 101, categoryId = 307,
            name = "提醒回归订单$offset", description = "内存数据库", status = ItemStatus.PENDING_PAYMENT,
            createdAt = 10, updatedAt = 11))
        database.priceDao().insertPrice(Price(id = priceId, itemId = itemId, type = PriceType.FULL,
            totalPrice = 100.0, createdAt = 12))
        database.orderDao().insertBinding(OrderBinding(itemId, priceId))
        database.orderDao().insertStage(PaymentStage(id = stageId, priceId = priceId, name = "全款",
            amount = 100.0, dueDate = 1_900_000_000_000L, reminderSet = false, calendarEventId = calendarId))
        database.paymentDao().insertPayment(Payment(id = paymentId, priceId = priceId, stageId = stageId,
            amount = 10.0, dueDate = 123, isPaid = true, paidDate = 123, createdAt = 13,
            calendarEventId = calendarId + 1_000))
        return Fixture(priceId, stageId, paymentId)
    }

    private fun pointers(journal: RestoreJournal): List<Payment> =
        gson.fromJson(journal.previousPaymentsJson, Array<Payment>::class.java).toList()

    private fun scope(journal: RestoreJournal): Set<Long>? = journal.affectedPriceIdsJson?.let {
        gson.fromJson(it, Array<Long>::class.java).toSet()
    }

    private suspend fun editFirst(amount: Double) =
        orders.editPayment(database.paymentDao().getPaymentById(first.paymentId)!!.copy(amount = amount))

    @Test
    fun repeatedOrderChangesOnlyRetainItsCleanupPointersWithoutJournalGrowth() = runBlocking {
        val secondStage = database.orderDao().getStage(second.stageId)!!
        val secondPayment = database.paymentDao().getPaymentById(second.paymentId)!!
        editFirst(11.0)
        val initial = database.restoreJournalDao().get()!!
        assertEquals(setOf(first.priceId), scope(initial))
        assertTrue(pointers(initial).all { it.priceId == first.priceId })

        // A retry must keep the earlier provider pointer even if another pointer replaces it.
        database.orderDao().updateStageCalendarEventId(first.stageId, 302)
        repeat(50) { index ->
            editFirst(if (index % 2 == 0) 12.0 else 11.0)
            val journal = database.restoreJournalDao().get()!!
            val previous = pointers(journal)
            assertEquals(setOf(first.priceId), scope(journal))
            assertTrue(previous.all { it.priceId == first.priceId })
            assertEquals(setOf(first.paymentId to 1_301L, first.paymentId to null,
                -first.stageId to 301L, -first.stageId to 302L),
                previous.map { it.id to it.calendarEventId }.toSet())
            assertEquals(4, previous.size)
        }
        assertEquals(secondStage, database.orderDao().getStage(second.stageId))
        assertEquals(secondPayment, database.paymentDao().getPaymentById(second.paymentId))
        assertEquals(2, database.paymentDao().getAllPaymentsList().size)
        assertEquals(2, database.orderDao().getAllStages().size)
    }

    @Test
    fun mergingAnExistingFullReconciliationKeepsItsFullScopeAndOldPointers() = runBlocking {
        val oldSecond = database.orderDao().getStage(second.stageId)!!
        val pointer = Payment(id = -oldSecond.id, priceId = oldSecond.priceId, amount = oldSecond.amount,
            dueDate = oldSecond.dueDate!!, calendarEventId = oldSecond.calendarEventId)
        database.restoreJournalDao().save(RestoreJournal(previousPaymentsJson = gson.toJson(listOf(pointer)),
            affectedPriceIdsJson = null))
        editFirst(11.0)
        val journal = database.restoreJournalDao().get()!!
        assertNull(journal.affectedPriceIdsJson)
        val previous = pointers(journal)
        assertEquals(setOf(first.priceId, second.priceId), previous.map { it.priceId }.toSet())
        assertTrue(previous.any { it.id == -second.stageId && it.calendarEventId == oldSecond.calendarEventId })
        assertEquals(3, previous.size)
    }

    @Test
    fun fullReconciliationPromotesAScopedRetryAndPreservesOldPointersWithoutGrowth() = runBlocking {
        val secondStage = database.orderDao().getStage(second.stageId)!!
        val secondPayment = database.paymentDao().getPaymentById(second.paymentId)!!
        editFirst(11.0)
        assertEquals(setOf(first.priceId), scope(database.restoreJournalDao().get()!!))
        // The old calendar pointer must remain available after the current pointer changes.
        database.orderDao().updateStageCalendarEventId(first.stageId, 302)
        val expected = setOf(first.paymentId to 1_301L, first.paymentId to null,
            -first.stageId to 301L, -first.stageId to 302L,
            second.paymentId to 1_401L, -second.stageId to 401L)

        repeat(2) {
            orders.reconcileReminders()
            val journal = database.restoreJournalDao().get()!!
            val previous = pointers(journal)
            assertNull(journal.affectedPriceIdsJson)
            assertEquals(setOf(first.priceId, second.priceId), previous.map { it.priceId }.toSet())
            assertEquals(expected, previous.map { it.id to it.calendarEventId }.toSet())
            assertEquals(expected.size, previous.size)
            assertEquals(secondStage, database.orderDao().getStage(second.stageId))
            assertEquals(secondPayment, database.paymentDao().getPaymentById(second.paymentId))
        }
    }

    @Test
    fun scopedRetriesFromDifferentOrdersMergeTheirScopesWithoutDuplicatePointers() = runBlocking {
        editFirst(11.0)
        orders.editPayment(database.paymentDao().getPaymentById(second.paymentId)!!.copy(amount = 12.0))
        val journal = database.restoreJournalDao().get()!!
        assertEquals(setOf(first.priceId, second.priceId), scope(journal))
        val previous = pointers(journal)
        assertEquals(4, previous.size)
        assertEquals(4, previous.map { it.id to it.calendarEventId }.toSet().size)
        assertTrue(previous.any { it.id == -first.stageId && it.calendarEventId == 301L })
        assertTrue(previous.any { it.id == -second.stageId && it.calendarEventId == 401L })
    }

    @Test
    fun cleanupWithoutCalendarPointersClearsAScopedJournalDespiteDeniedCalendarPermission() = runBlocking {
        database.orderDao().updateStageCalendarEventId(first.stageId, null)
        val payment = database.paymentDao().getPaymentById(first.paymentId)!!.copy(calendarEventId = null)
        database.paymentDao().updatePayment(payment)
        val stage = database.orderDao().getStage(first.stageId)!!
        val pointer = Payment(id = -stage.id, priceId = stage.priceId, amount = stage.amount,
            dueDate = stage.dueDate!!, calendarEventId = null)
        database.restoreJournalDao().save(RestoreJournal(previousPaymentsJson = gson.toJson(listOf(payment, pointer)),
            affectedPriceIdsJson = gson.toJson(listOf(first.priceId))))
        assertNotNull(database.restoreJournalDao().get())
        assertEquals(0, BackupManager(deniedContext, database).recoverPendingReminders())
        assertNull(database.restoreJournalDao().get())
        // The unrelated order's retained calendar pointer must not enter this successful cleanup.
        assertEquals(401L, database.orderDao().getStage(second.stageId)!!.calendarEventId)
        editFirst(11.0)
        assertNull(database.restoreJournalDao().get())
    }
}
