package com.lolita.app.ui.screen.calendar

import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.PaymentRepository
import com.lolita.app.data.repository.PriceRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger
import java.time.Instant
import java.util.concurrent.Executor

@RunWith(AndroidJUnit4::class)
class PaymentCalendarClockTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var database: LolitaDatabase
    private val store = ViewModelStore()
    private val calendarQueries = AtomicInteger()
    @Before fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java)
            .setQueryCallback({ sql, _ ->
                if (sql.contains("SELECT -id AS paymentId")) calendarQueries.incrementAndGet()
            }, Executor { it.run() }).build()
        database.brandDao().insertBrand(Brand(id = 1, name = "时钟回归品牌"))
        database.categoryDao().insertCategory(Category(id = 1, name = "OP"))
        database.itemDao().insertItem(Item(id = 1, name = "时钟回归服饰", brandId = 1, categoryId = 1, description = "", status = ItemStatus.OWNED))
        database.priceDao().insertPrice(Price(id = 1, itemId = 1, type = PriceType.FULL, totalPrice = 100.0))
        database.orderDao().insertBinding(OrderBinding(itemId = 1, priceId = 1))
        Unit
    }
    @After fun tearDown() {
        instrumentation.runOnMainSync { store.clear() }
        instrumentation.waitForIdleSync()
        database.close()
    }
    @Test fun elapsedDueTimeIsRecomputedOnRoomEmissionInsteadOfCapturedAtLoad() = runBlocking {
        val due = System.currentTimeMillis() + 4_000
        val month = Calendar.getInstance().apply { timeInMillis = due }.get(Calendar.MONTH)
        val stage = PaymentStage(id = 1, priceId = 1, name = "全款", amount = 50.0, dueDate = due)
        database.orderDao().insertStage(stage)
        lateinit var vm: PaymentCalendarViewModel
        instrumentation.runOnMainSync {
            vm = PaymentCalendarViewModel(PriceRepository(database.priceDao(), database.paymentDao(), context, database),
                PaymentRepository(database.paymentDao(), context), ItemRepository(database.itemDao(), database = database))
            store.put("calendar", vm)
        }
        withTimeout(10_000) { vm.uiState.first { !it.isLoading } }
        assertEquals(0.0, vm.uiState.value.yearOverdueAmount, 0.0)
        delay((due - System.currentTimeMillis()).coerceAtLeast(0) + 100)
        // No database mutation is required for a visible payment to become overdue.
        withTimeout(10_000) { vm.uiState.first { it.yearOverdueAmount == 50.0 } }
        database.orderDao().updateStage(stage.copy(amount = 60.0))
        val refreshed = withTimeout(10_000) { vm.uiState.first { it.yearUnpaidTotal == 60.0 } }
        assertEquals(60.0, refreshed.yearOverdueAmount, 0.0)
        assertEquals(60.0, refreshed.monthStatsMap.getValue(month).overdueAmount, 0.0)
    }
    @Test fun explicitClockRefreshUpdatesMonthAndYearWithoutRestartingRoom() = runBlocking {
        val now = AtomicLong(Instant.parse("2026-10-01T00:00:00Z").toEpochMilli())
        val due = now.get() + 600_000
        database.orderDao().insertStage(PaymentStage(id = 1, priceId = 1, name = "尾款", amount = 50.0, dueDate = due))
        lateinit var vm: PaymentCalendarViewModel
        instrumentation.runOnMainSync {
            vm = PaymentCalendarViewModel(PriceRepository(database.priceDao(), database.paymentDao(), context, database),
                PaymentRepository(database.paymentDao(), context), ItemRepository(database.itemDao(), database = database),
                currentTimeMillis = now::get, currentTimeZone = { TimeZone.getTimeZone("UTC") })
            store.put("calendar", vm)
        }
        withTimeout(10_000) { vm.uiState.first { !it.isLoading } }
        val queries = calendarQueries.get()
        repeat(4) { index ->
            now.set(due + index + 1)
            instrumentation.runOnMainSync { vm.refreshTime() }
            val after = withTimeout(10_000) { vm.uiState.first { it.observedAtMillis == now.get() } }
            assertEquals(50.0, after.yearOverdueAmount, 0.0)
            assertEquals(50.0, after.monthStatsMap.getValue(9).overdueAmount, 0.0)
        }
        assertEquals(queries, calendarQueries.get())
        now.set(due - 1)
        instrumentation.runOnMainSync { vm.refreshTime() }
        val rewound = withTimeout(10_000) { vm.uiState.first { it.observedAtMillis == now.get() } }
        assertEquals(0.0, rewound.yearOverdueAmount, 0.0)
        assertEquals(0.0, rewound.monthStatsMap.getValue(9).overdueAmount, 0.0)
    }

}
