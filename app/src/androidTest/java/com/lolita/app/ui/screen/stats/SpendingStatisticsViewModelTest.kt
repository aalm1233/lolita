package com.lolita.app.ui.screen.stats

import androidx.lifecycle.ViewModel
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
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.OrderRepository
import com.lolita.app.data.repository.PriceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.util.Calendar
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.roundToLong

/** Real Room invalidation protects dimension/year selection and actual-payment totals. */
@RunWith(AndroidJUnit4::class)
class SpendingStatisticsViewModelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    private lateinit var database: LolitaDatabase
    private lateinit var items: ItemRepository
    private lateinit var orders: OrderRepository
    private lateinit var prices: PriceRepository
    private lateinit var showPrice: MutableStateFlow<Boolean>
    private lateinit var viewModelStore: ViewModelStore
    private lateinit var observationScope: CoroutineScope
    private var viewModelNumber = 0

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        items = ItemRepository(database.itemDao(), database = database)
        orders = OrderRepository(database, context)
        prices = PriceRepository(database.priceDao(), database.paymentDao(), context, database)
        showPrice = MutableStateFlow(true)
        viewModelStore = ViewModelStore()
        observationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        database.brandDao().insertBrand(Brand(id = 101, name = "统计品牌甲", createdAt = 1))
        database.brandDao().insertBrand(Brand(id = 102, name = "统计品牌乙", createdAt = 2))
        database.categoryDao().insertCategory(Category(id = 307, name = "统计服饰", createdAt = 3))
        database.categoryDao().insertCategory(Category(id = 308, name = "统计配饰", createdAt = 4))
        Unit
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync {
            if (::observationScope.isInitialized) observationScope.cancel()
            if (::viewModelStore.isInitialized) viewModelStore.clear()
        }
        instrumentation.waitForIdleSync()
        if (::database.isInitialized) database.close()
    }

    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)

    private fun <T : ViewModel> newViewModel(factory: () -> T): T {
        lateinit var result: T
        onMain {
            result = factory()
            viewModelStore.put("stats-${viewModelNumber++}", result)
        }
        return result
    }

    private fun distribution() = newViewModel {
        SpendingDistributionViewModel(priceRepository = prices, showTotalPrice = showPrice)
    }

    private fun trend() = newViewModel {
        SpendingTrendViewModel(priceRepository = prices, showTotalPrice = showPrice)
    }

    private fun <T> observe(flow: StateFlow<T>): CopyOnWriteArrayList<T> {
        val observed = CopyOnWriteArrayList<T>()
        onMain {
            observationScope.launch(start = CoroutineStart.UNDISPATCHED) {
                flow.collect { observed.add(it) }
            }
        }
        return observed
    }

    private suspend fun <T> await(flow: StateFlow<T>, predicate: (T) -> Boolean): T =
        withTimeout(10_000) { flow.first(predicate) }

    private fun cents(amount: Double) = (amount * 100).roundToLong()

    private fun date(year: Int, month: Int, day: Int = 15): Long = Calendar.getInstance().apply {
        clear()
        set(year, month - 1, day, 12, 0, 0)
    }.timeInMillis

    private suspend fun item(
        name: String,
        brandId: Long = 101,
        categoryId: Long = 307,
        style: String? = "甜系",
        season: String? = "春,夏",
        parentItemId: Long? = null,
        includedInParentPrice: Boolean = false
    ): Long = items.insertItem(
        Item(name = name, description = "独立统计回归样本", brandId = brandId, categoryId = categoryId,
            status = ItemStatus.OWNED, style = style, season = season,
            parentItemId = parentItemId, includedInParentPrice = includedInParentPrice,
            createdAt = 10, updatedAt = 11)
    )

    private suspend fun order(itemId: Long, total: Double = 1_000.0): Long = orders.createOrder(
        Price(itemId = itemId, type = PriceType.FULL, totalPrice = total, createdAt = 12),
        listOf(PaymentStage(priceId = 0, name = "全款", amount = total, dueDate = null))
    )

    private suspend fun distributionState(
        viewModel: SpendingDistributionViewModel,
        dimension: SpendingDimension,
        expectedCents: Long,
        showTotalPrice: Boolean = true
    ): SpendingDistributionUiState = await(viewModel.uiState) {
        !it.isLoading && it.dimension == dimension && cents(it.totalSpending) == expectedCents &&
            it.showTotalPrice == showTotalPrice
    }

    private fun assertNoDimensionDrift(
        states: CopyOnWriteArrayList<SpendingDistributionUiState>,
        from: Int,
        expected: SpendingDimension
    ) {
        val changes = states.toList().drop(from)
        assertTrue("Expected observed UI updates", changes.isNotEmpty())
        assertTrue("Old dimension emitted after selecting $expected: ${changes.map { it.dimension }}",
            changes.all { it.dimension == expected })
    }

    @Test
    fun emptyDistributionFinishesAfterRapidDimensionAndVisibilityChanges() = runBlocking {
        val viewModel = distribution()
        val observed = observe(viewModel.uiState)
        val dimensions = SpendingDimension.entries

        // Prime the real Room flows, including their unchanged empty query results.
        for (dimension in dimensions) {
            onMain { viewModel.switchDimension(dimension) }
            val state = distributionState(viewModel, dimension, 0)
            assertTrue(state.chartData.isEmpty())
            assertTrue(state.rankingList.isEmpty())
        }
        repeat(8) {
            onMain {
                dimensions.forEach(viewModel::switchDimension)
                viewModel.switchDimension(SpendingDimension.BRAND)
            }
            val empty = distributionState(viewModel, SpendingDimension.BRAND, 0)
            assertTrue(empty.chartData.isEmpty())
            assertEquals(-1, empty.selectedIndex)

            // Also finish a rapid batch on a different dimension so an old settled
            // BRAND state cannot satisfy the wait without consuming the new selection.
            onMain { dimensions.forEach(viewModel::switchDimension) }
            distributionState(viewModel, SpendingDimension.SEASON, 0)
            instrumentation.waitForIdleSync()
            assertEquals(SpendingDimension.SEASON, viewModel.uiState.value.dimension)
            assertFalse(viewModel.uiState.value.isLoading)

            onMain {
                showPrice.value = false
                dimensions.forEach(viewModel::switchDimension)
                viewModel.switchDimension(SpendingDimension.BRAND)
            }
            distributionState(viewModel, SpendingDimension.BRAND, 0, showTotalPrice = false)
            onMain { showPrice.value = true }
            distributionState(viewModel, SpendingDimension.BRAND, 0)

            // Reselecting the settled dimension must not restart an empty loading state.
            val settled = viewModel.uiState.value
            onMain { viewModel.switchDimension(SpendingDimension.BRAND) }
            assertEquals(settled, viewModel.uiState.value)
            assertFalse(viewModel.uiState.value.isLoading)
        }
        var checkpoint = 0
        onMain {
            checkpoint = observed.size
            viewModel.switchDimension(SpendingDimension.SEASON)
        }
        distributionState(viewModel, SpendingDimension.SEASON, 0)
        onMain { showPrice.value = false }
        distributionState(viewModel, SpendingDimension.SEASON, 0, showTotalPrice = false)
        onMain { showPrice.value = true }
        distributionState(viewModel, SpendingDimension.SEASON, 0)
        instrumentation.waitForIdleSync()
        assertNoDimensionDrift(observed, checkpoint, SpendingDimension.SEASON)
        assertTrue(database.priceDao().getAllPricesList().isEmpty())
        assertTrue(database.paymentDao().getAllPaymentsList().isEmpty())
    }

    @Test
    fun paymentAndPriceVisibilityChangesNeverRestoreAnOldDistributionDimension() = runBlocking {
        val priceId = order(item("响应式统计服饰"))
        orders.recordPayment(priceId, 10.0, date(currentYear, 1))
        val viewModel = distribution()
        val observed = observe(viewModel.uiState)
        distributionState(viewModel, SpendingDimension.BRAND, 1_000)

        for (dimension in listOf(SpendingDimension.CATEGORY, SpendingDimension.STYLE, SpendingDimension.SEASON)) {
            var checkpoint = 0
            onMain {
                checkpoint = observed.size
                viewModel.switchDimension(dimension)
            }
            distributionState(viewModel, dimension, 1_000)

            val paymentId = orders.recordPayment(priceId, 1.01, date(currentYear, 2))
            distributionState(viewModel, dimension, 1_101)
            orders.editPayment(orders.getPayment(paymentId)!!.copy(amount = 2.02))
            distributionState(viewModel, dimension, 1_202)

            onMain { showPrice.value = false }
            distributionState(viewModel, dimension, 1_202, showTotalPrice = false)
            onMain { showPrice.value = true }
            distributionState(viewModel, dimension, 1_202)

            orders.deletePayment(paymentId)
            distributionState(viewModel, dimension, 1_000)
            instrumentation.waitForIdleSync()
            assertNoDimensionDrift(observed, checkpoint, dimension)
        }
        assertEquals(10.0, prices.getTotalSpending().first(), 0.0)
    }

    private suspend fun trendState(
        viewModel: SpendingTrendViewModel,
        year: Int,
        expectedYearCents: Long,
        expectedUndatedCents: Long,
        selectedMonthIndex: Int? = null,
        showTotalPrice: Boolean = true
    ): SpendingTrendUiState = await(viewModel.uiState) {
        !it.isLoading && it.selectedYear == year && cents(it.yearTotal) == expectedYearCents &&
            cents(it.undatedSpending) == expectedUndatedCents && it.showTotalPrice == showTotalPrice &&
            (selectedMonthIndex == null || it.selectedMonthIndex == selectedMonthIndex)
    }

    private fun assertMonths(state: SpendingTrendUiState, expected: Map<Int, Long>) {
        assertEquals(12, state.monthlyData.size)
        assertEquals(12, state.monthlyDetails.size)
        for (month in 1..12) {
            val expectedCents = expected[month] ?: 0L
            assertEquals("chart month $month", expectedCents, cents(state.monthlyData[month - 1].value))
            assertEquals("detail month $month", expectedCents, cents(state.monthlyDetails[month - 1].amount))
        }
    }

    @Test
    fun yearAndMonthTotalsReactToPaymentDateEditsAndKeepUnknownDatesSeparate() = runBlocking {
        val priceId = order(item("跨年付款服饰"))
        val januaryPayment = orders.recordPayment(priceId, 10.0, date(currentYear, 1))
        orders.recordPayment(priceId, 20.0, date(currentYear - 1, 12))
        // Legacy actual payments may lack an actual date. A plan due date is not
        // evidence of the payment month and must never be used as a fallback.
        val unknownPayment = database.paymentDao().insertPayment(
            Payment(priceId = priceId, amount = 7.03, isPaid = true, paidDate = null,
                dueDate = date(currentYear, 7), createdAt = date(currentYear, 8))
        )
        database.paymentDao().insertPayment(
            Payment(priceId = priceId, amount = 99.0, isPaid = false, paidDate = null,
                dueDate = date(currentYear, 6), createdAt = date(currentYear, 6))
        )
        val viewModel = trend()
        val observed = observe(viewModel.uiState)
        assertMonths(trendState(viewModel, currentYear, 1_000, 703), mapOf(1 to 1_000))
        onMain { viewModel.selectMonth(0) }
        trendState(viewModel, currentYear, 1_000, 703, selectedMonthIndex = 0)

        var previousYearCheckpoint = 0
        onMain {
            previousYearCheckpoint = observed.size
            viewModel.previousYear()
        }
        assertMonths(trendState(viewModel, currentYear - 1, 2_000, 703, selectedMonthIndex = -1),
            mapOf(12 to 2_000))
        val movingPayment = orders.recordPayment(priceId, 5.55, date(currentYear - 1, 3))
        assertMonths(trendState(viewModel, currentYear - 1, 2_555, 703), mapOf(3 to 555, 12 to 2_000))
        orders.editPayment(orders.getPayment(movingPayment)!!.copy(amount = 6.66, paidDate = date(currentYear - 1, 4)))
        assertMonths(trendState(viewModel, currentYear - 1, 2_666, 703), mapOf(4 to 666, 12 to 2_000))
        orders.editPayment(orders.getPayment(movingPayment)!!.copy(amount = 8.88, paidDate = date(currentYear, 2)))
        assertMonths(trendState(viewModel, currentYear - 1, 2_000, 703), mapOf(12 to 2_000))
        instrumentation.waitForIdleSync()
        assertTrue("An old year emitted while viewing the previous year",
            observed.toList().drop(previousYearCheckpoint).all { it.selectedYear == currentYear - 1 })

        onMain { viewModel.selectMonth(11) }
        trendState(viewModel, currentYear - 1, 2_000, 703, selectedMonthIndex = 11)
        var currentYearCheckpoint = 0
        onMain {
            currentYearCheckpoint = observed.size
            viewModel.nextYear()
        }
        assertMonths(trendState(viewModel, currentYear, 1_888, 703, selectedMonthIndex = -1),
            mapOf(1 to 1_000, 2 to 888))
        orders.editPayment(orders.getPayment(januaryPayment)!!.copy(paidDate = date(currentYear - 1, 11)))
        assertMonths(trendState(viewModel, currentYear, 888, 703), mapOf(2 to 888))
        orders.editPayment(orders.getPayment(unknownPayment)!!.copy(paidDate = date(currentYear, 5)))
        assertMonths(trendState(viewModel, currentYear, 1_591, 0), mapOf(2 to 888, 5 to 703))
        onMain { viewModel.selectMonth(1) }
        trendState(viewModel, currentYear, 1_591, 0, selectedMonthIndex = 1)
        onMain { showPrice.value = false }
        trendState(viewModel, currentYear, 1_591, 0, selectedMonthIndex = 1, showTotalPrice = false)
        onMain { showPrice.value = true }
        trendState(viewModel, currentYear, 1_591, 0, selectedMonthIndex = 1)
        instrumentation.waitForIdleSync()
        assertTrue("An old year emitted while viewing the current year",
            observed.toList().drop(currentYearCheckpoint).all { it.selectedYear == currentYear })
        assertEquals(45.91, prices.getTotalSpending().first(), 0.000001)
        assertEquals(date(currentYear, 5), orders.getPayment(unknownPayment)!!.paidDate)
    }

    @Test
    fun everyDimensionConservesPaidCentsAndSharedAccessoriesDoNotCountTwice() = runBlocking {
        val parentId = item("主件", style = "古典", season = "春, 夏, 秋,春")
        val parentPrice = order(parentId, 200.0)
        orders.recordPayment(parentPrice, 10.01, date(currentYear, 1))
        val sharedId = item("共用价格小物", brandId = 102, categoryId = 308,
            style = "哥特", season = "冬", parentItemId = parentId, includedInParentPrice = true)
        val independentId = item("独立价格小物", brandId = 102, categoryId = 308,
            style = null, season = null, parentItemId = parentId)
        val independentPrice = order(independentId, 100.0)
        orders.recordPayment(independentPrice, 2.03, date(currentYear, 2))
        val blankTagsPrice = order(item("空标签服饰", style = " ", season = " , "), 100.0)
        orders.recordPayment(blankTagsPrice, 0.04, date(currentYear, 3))
        database.paymentDao().insertPayment(Payment(priceId = independentPrice, amount = 0.05,
            isPaid = true, paidDate = null, dueDate = date(currentYear, 6), createdAt = 18))
        database.paymentDao().insertPayment(Payment(priceId = parentPrice, amount = 99.0,
            isPaid = false, paidDate = null, dueDate = date(currentYear, 4), createdAt = 19))
        // An archived historical order must not be counted in current-order statistics.
        val historicalPrice = database.priceDao().insertPrice(
            Price(itemId = parentId, type = PriceType.FULL, totalPrice = 500.0, createdAt = 20)
        )
        database.paymentDao().insertPayment(Payment(priceId = historicalPrice, amount = 25.0,
            isPaid = true, paidDate = date(currentYear, 1), dueDate = date(currentYear, 1), createdAt = 21))
        assertTrue(database.priceDao().getPricesByItemList(sharedId).isEmpty())
        assertNull(database.orderDao().getBinding(sharedId))
        assertEquals(12.13, prices.getTotalSpending().first(), 0.000001)

        val viewModel = distribution()
        for (dimension in SpendingDimension.entries) {
            onMain { viewModel.switchDimension(dimension) }
            val state = distributionState(viewModel, dimension, 1_213)
            assertEquals("$dimension ranking total", 1_213L, state.rankingList.sumOf { cents(it.amount) })
            assertEquals("$dimension chart total", 1_213L, state.chartData.sumOf { cents(it.value) })
            assertTrue("$dimension introduced fractional cents", state.rankingList.all {
                BigDecimal.valueOf(it.amount).stripTrailingZeros().scale() <= 2
            })
            val byName = state.rankingList.associateBy { it.name }
            when (dimension) {
                SpendingDimension.BRAND -> {
                    assertEquals(1_005L, cents(byName.getValue("统计品牌甲").amount))
                    assertEquals(208L, cents(byName.getValue("统计品牌乙").amount))
                }
                SpendingDimension.CATEGORY -> {
                    assertEquals(1_005L, cents(byName.getValue("统计服饰").amount))
                    assertEquals(208L, cents(byName.getValue("统计配饰").amount))
                }
                SpendingDimension.STYLE -> {
                    assertEquals(1_001L, cents(byName.getValue("古典").amount))
                    assertEquals(212L, cents(byName.getValue("未设置风格").amount))
                    assertNull(byName.getValue("未设置风格").filterValue)
                    assertFalse(byName.containsKey("哥特"))
                }
                SpendingDimension.SEASON -> {
                    assertEquals(setOf("春", "夏", "秋", "未设置季节"), byName.keys)
                    val allocated = listOf("春", "夏", "秋").map { cents(byName.getValue(it).amount) }.sorted()
                    assertEquals(listOf(333L, 334L, 334L), allocated)
                    assertEquals(212L, cents(byName.getValue("未设置季节").amount))
                    assertNull(byName.getValue("未设置季节").filterValue)
                    listOf("春", "夏", "秋").forEach { tag ->
                        assertEquals(tag, byName.getValue(tag).filterValue)
                    }
                    assertFalse(byName.containsKey("冬"))
                }
            }
        }
        val trendState = trendState(trend(), currentYear, 1_208, 5)
        assertMonths(trendState, mapOf(1 to 1_001, 2 to 203, 3 to 4))
        assertEquals(1_213L, cents(trendState.yearTotal) + cents(trendState.undatedSpending))
        database.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use {
            assertFalse(it.moveToFirst())
        }
    }
}
