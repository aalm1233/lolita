package com.lolita.app.ui.screen.common

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeWithVelocity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.Brand
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.Coordinate
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemPriority
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.local.entity.Location
import com.lolita.app.data.local.entity.OrderBinding
import com.lolita.app.data.local.entity.OutfitLog
import com.lolita.app.data.local.entity.Payment
import com.lolita.app.data.local.entity.Price
import com.lolita.app.data.local.entity.PriceType
import com.lolita.app.data.local.entity.PurchaseRecordStatus
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.data.repository.CoordinateRepository
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.OutfitLogRepository
import com.lolita.app.data.repository.PriceRepository
import com.lolita.app.ui.screen.coordinate.CoordinateListContent
import com.lolita.app.ui.screen.coordinate.CoordinateListViewModel
import com.lolita.app.ui.screen.item.WishlistContent
import com.lolita.app.ui.screen.item.WishlistViewModel
import com.lolita.app.ui.screen.item.LocationListContent
import com.lolita.app.ui.screen.outfit.OutfitLogListScreen
import com.lolita.app.ui.screen.outfit.OutfitLogListViewModel
import com.lolita.app.ui.screen.stats.StatsContent
import com.lolita.app.ui.screen.stats.StatsViewModel
import com.lolita.app.ui.screen.stats.SpendingDistributionContent
import com.lolita.app.ui.screen.stats.SpendingDistributionViewModel
import com.lolita.app.ui.screen.stats.SpendingTrendContent
import com.lolita.app.ui.screen.stats.SpendingTrendViewModel
import com.lolita.app.ui.screen.stats.WishlistAnalysisContent
import com.lolita.app.ui.screen.stats.WishlistAnalysisViewModel
import com.lolita.app.ui.theme.LolitaTheme
import com.lolita.app.ui.theme.SkinType
import com.lolita.app.ui.theme.skin.animation.LocalIsListScrolling
import com.lolita.app.ui.theme.skin.animation.LocalSkinScrollActivity
import com.lolita.app.ui.theme.skin.animation.SkinScrollActivity
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual page gestures and Room-backed ViewModels; never reads or changes the application's data. */
@RunWith(AndroidJUnit4::class)
class ScrollPageTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: LolitaDatabase
    private lateinit var directory: File
    private lateinit var preferencesScope: CoroutineScope
    private lateinit var preferences: AppPreferences
    private lateinit var viewModelStore: ViewModelStore
    private lateinit var coordinates: CoordinateListViewModel
    private lateinit var outfits: OutfitLogListViewModel
    private lateinit var stats: StatsViewModel
    private lateinit var imagePath: String
    private val locations = (1..48).map { Location(id = it.toLong(), name = "位置 $it", description = "滚动回归衣橱") }
    private val activity = SkinScrollActivity()
    private val showPage = mutableStateOf(true)
    private val destinations = mutableListOf<Long>()
    private val filters = mutableListOf<String>()

    @Before fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        directory = File(context.cacheDir, "scroll-page-${UUID.randomUUID()}").apply { mkdirs() }
        val image = Bitmap.createBitmap(320, 400, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(193, 152, 175))
        }
        imagePath = File(directory, "wardrobe.png").absolutePath
        File(imagePath).outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        image.recycle()
        preferencesScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        preferences = AppPreferences(context, PreferenceDataStoreFactory.create(scope = preferencesScope,
            produceFile = { File(directory, "settings.preferences_pb") }))
        preferences.setShowTotalPrice(true)
        database.withTransaction {
            database.categoryDao().insertCategory(Category(id = 1, name = "主裙"))
            repeat(5) { database.brandDao().insertBrand(Brand(id = it + 1L, name = "回归品牌 ${it + 1}")) }
            locations.forEach { location ->
                database.locationDao().insertLocation(location)
                database.coordinateDao().insertCoordinate(Coordinate(id = location.id, name = "套装 ${location.id}",
                    description = "主裙与配件", imageUrls = listOf(imagePath)))
                database.outfitLogDao().insertOutfitLog(OutfitLog(id = location.id,
                    date = 1_700_000_000_000L + location.id * 86_400_000L,
                    note = "日记 ${location.id}", imageUrls = listOf(imagePath)))
                database.itemDao().insertItem(Item(id = location.id, brandId = location.id % 5 + 1,
                    categoryId = 1, name = "主裙 ${location.id}", description = "独立测试资料",
                    status = ItemStatus.OWNED, imageUrls = listOf(imagePath), coordinateId = location.id,
                    locationId = location.id))
            }
            val priceId = database.priceDao().insertPrice(Price(itemId = 1, type = PriceType.FULL,
                totalPrice = 123.45, purchaseRecordStatus = PurchaseRecordStatus.TRACKED))
            database.orderDao().insertBinding(OrderBinding(1, priceId, PurchaseRecordStatus.TRACKED))
            database.paymentDao().insertPayment(Payment(priceId = priceId, amount = 123.45,
                isPaid = true, dueDate = 1_700_000_000_000L, paidDate = 1_700_000_000_000L))
        }
        viewModelStore = ViewModelStore()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val itemRepository = ItemRepository(database.itemDao(), database = database)
            val coordinateRepository = CoordinateRepository(database.coordinateDao(), database.itemDao(), database)
            val outfitRepository = OutfitLogRepository(database.outfitLogDao(), database)
            val priceRepository = PriceRepository(database.priceDao(), database.paymentDao(), context, database)
            coordinates = CoordinateListViewModel(coordinateRepository, itemRepository, priceRepository, preferences)
            outfits = OutfitLogListViewModel(outfitRepository, itemRepository)
            stats = StatsViewModel(itemRepository, coordinateRepository, outfitRepository, priceRepository, preferences)
            viewModelStore.put("coordinates", coordinates)
            viewModelStore.put("outfits", outfits)
            viewModelStore.put("stats", stats)
        }
        withTimeout(10_000) {
            coordinates.uiState.first { !it.isLoading && it.coordinates.size == 48 }
            outfits.uiState.first { !it.isLoading && it.logs.size == 48 }
            stats.uiState.first { !it.isLoading && it.topBrands.size == 5 && it.showTotalPrice && it.totalSpending == 123.45 }
        }
        Unit
    }

    @After fun tearDown() {
        compose.mainClock.autoAdvance = true
        if (::viewModelStore.isInitialized) compose.runOnIdle { viewModelStore.clear() }
        if (::preferencesScope.isInitialized) preferencesScope.cancel()
        if (::database.isInitialized) database.close()
        if (::directory.isInitialized) directory.deleteRecursively()
    }

    @Test fun locationListKeepsMovingAfterReleaseAndCleansUpActivity() {
        show(SkinType.DEFAULT) {
            LocationListContent(locations = locations.map { it.copy(imageUrl = imagePath) },
                locationItemCounts = locations.associate { it.id to 3 },
                locationItemImages = locations.associate { it.id to List(3) { imagePath } },
                unassignedItemCount = 0, onLocationClick = { destinations += it })
        }
        assertDragAndFling()
        compose.mainClock.autoAdvance = true
        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        compose.onNodeWithText("位置 1").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf(1L), destinations) }
        assertRemovalDuringFling()
    }

    @Test fun coordinateListAndGridKeepMovingAndPreserveDetailDestination() {
        show(SkinType.GOTHIC) {
            CoordinateListContent(viewModel = coordinates, onNavigateToDetail = { destinations += it })
        }
        assertDragAndFling()
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { coordinates.setColumns(2) }
        compose.waitForIdle()
        assertDragAndFling()
        compose.mainClock.autoAdvance = true
        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        val first = coordinates.uiState.value.coordinates.first()
        compose.onNodeWithText(first.name).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf(first.id), destinations) }
        assertRemovalDuringFling()
    }

    @Test fun outfitPageKeepsMovingAndPreservesDetailDestination() {
        show(SkinType.VICTORIAN) {
            OutfitLogListScreen(viewModel = outfits, onNavigateToDetail = { destinations += it },
                onNavigateToEdit = {})
        }
        assertDragAndFling()
        compose.mainClock.autoAdvance = true
        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        val first = outfits.uiState.value.logs.first()
        compose.onNodeWithText(first.previewNote).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf(first.id), destinations) }
        assertRemovalDuringFling()
    }

    @Test fun statisticsPageKeepsMovingAndPreservesOverviewNavigation() {
        show(SkinType.NAVY) {
            StatsContent(viewModel = stats,
                onNavigateToFilteredList = { type, _, _ -> filters += type })
        }
        assertDragAndFling()
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("已拥有").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf("status_owned"), filters) }
        assertRemovalDuringFling()
    }

    @Test fun statisticsTabsAndHomeWishlistReportDraggingAndCleanUpWhenRemoved() {
        runBlocking {
            database.withTransaction {
                ItemPriority.entries.forEachIndexed { index, priority ->
                    val itemId = database.itemDao().insertItem(Item(brandId = 1, categoryId = 1,
                        name = "愿望回归 $index", description = "独立测试预算", status = ItemStatus.WISHED,
                        priority = priority))
                    database.priceDao().insertPrice(Price(itemId = itemId, type = PriceType.FULL,
                        totalPrice = 100.0 + index))
                }
            }
        }
        lateinit var distribution: SpendingDistributionViewModel
        lateinit var trend: SpendingTrendViewModel
        lateinit var wishlist: WishlistAnalysisViewModel
        lateinit var homeWishlist: WishlistViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val prices = PriceRepository(database.priceDao(), database.paymentDao(), context, database)
            val items = ItemRepository(database.itemDao(), database = database)
            distribution = SpendingDistributionViewModel(prices, preferences.showTotalPrice)
            trend = SpendingTrendViewModel(prices, preferences.showTotalPrice)
            wishlist = WishlistAnalysisViewModel(prices, items, preferences)
            homeWishlist = WishlistViewModel(items)
            viewModelStore.put("distribution", distribution)
            viewModelStore.put("trend", trend)
            viewModelStore.put("wishlist", wishlist)
            viewModelStore.put("homeWishlist", homeWishlist)
        }
        runBlocking {
            withTimeout(10_000) {
                distribution.uiState.first { !it.isLoading && it.chartData.isNotEmpty() }
                trend.uiState.first { !it.isLoading && it.showTotalPrice }
                wishlist.uiState.first { !it.isLoading && it.wishedCount == 3 && it.showTotalPrice }
                homeWishlist.uiState.first { !it.isLoading && it.filteredItems.size == 3 }
            }
        }
        val tab = mutableStateOf(0)
        show(SkinType.DEFAULT, height = 220) {
            when (tab.value) {
                0 -> SpendingDistributionContent(viewModel = distribution)
                1 -> SpendingTrendContent(viewModel = trend)
                2 -> WishlistAnalysisContent(viewModel = wishlist)
                else -> WishlistContent(viewModel = homeWishlist, onNavigateToDetail = {}, onNavigateToEdit = {})
            }
        }
        repeat(4) { index ->
            compose.mainClock.autoAdvance = false
            val before = scrollPosition()
            beginVerticalDrag()
            compose.runOnIdle { assertTrue("统计页签 $index 拖动时必须报告滚动", activity.isScrolling.value) }
            assertTrue("统计页签 $index 的真实视口必须移动", scrollPosition() > before)
            compose.onNode(hasScrollAction()).performTouchInput { advanceEventTime(200); up() }
            compose.mainClock.advanceTimeBy(1_500)
            compose.runOnIdle { assertFalse("统计页签 $index 停止后必须清理标记", activity.isScrolling.value) }
            beginVerticalDrag()
            compose.runOnIdle {
                assertTrue("移除前统计页签 $index 应处于拖动中", activity.isScrolling.value)
                showPage.value = false
            }
            compose.mainClock.advanceTimeBy(32)
            compose.onNodeWithTag("scrollViewport").performTouchInput { cancel() }
            compose.runOnIdle { assertFalse("移除统计页签 $index 不得遗留滚动标记", activity.isScrolling.value) }
            if (index < 3) {
                compose.runOnIdle { tab.value = index + 1; showPage.value = true }
                compose.mainClock.advanceTimeBy(1_500)
                compose.waitForIdle()
            }
        }
    }

    private fun show(skin: SkinType, height: Int = 520, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalSkinScrollActivity provides activity,
                LocalIsListScrolling provides activity.isScrolling) {
                LolitaTheme(skinType = skin) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.size(360.dp, height.dp).testTag("scrollViewport")) {
                            if (showPage.value) content()
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        compose.onNodeWithTag("scrollViewport").assertIsDisplayed()
    }

    private fun assertDragAndFling() {
        val scrollable = compose.onNode(hasScrollAction())
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { assertFalse("页面静止时不得暂停背景", activity.isScrolling.value) }
        val before = scrollPosition()
        scrollable.performTouchInput {
            down(percentOffset(0.5f, 0.80f))
            moveTo(percentOffset(0.5f, 0.73f), delayMillis = 16)
            moveTo(percentOffset(0.5f, 0.66f), delayMillis = 16)
        }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { assertTrue("手指拖动时必须报告滚动", activity.isScrolling.value) }
        assertTrue("真实页面的拖动必须改变视口", scrollPosition() > before)
        // Stop the first drag without velocity, then inject a known release velocity separately.
        scrollable.performTouchInput { advanceEventTime(200); up() }
        compose.mainClock.advanceTimeBy(1_500)
        compose.runOnIdle { assertFalse("拖动停止后必须解除背景暂停", activity.isScrolling.value) }
        releaseWithVelocity()
        val released = scrollPosition()
        compose.runOnIdle { assertTrue("松手后惯性期间仍须报告滚动", activity.isScrolling.value) }
        compose.mainClock.advanceTimeBy(96)
        assertTrue("松手后必须继续改变真实视口，不能仅在手指拖动时移动", scrollPosition() > released)
        compose.mainClock.advanceTimeBy(5_000)
        compose.runOnIdle { assertFalse("惯性结束后必须恢复背景", activity.isScrolling.value) }
    }

    private fun assertRemovalDuringFling() {
        compose.mainClock.autoAdvance = false
        releaseWithVelocity()
        compose.runOnIdle { assertTrue("离开前应处于真实惯性滚动", activity.isScrolling.value) }
        compose.runOnIdle { showPage.value = false }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { assertFalse("移除正在滚动的页面不得遗留暂停标记", activity.isScrolling.value) }
        compose.onNodeWithTag("scrollViewport").assertIsDisplayed()
    }

    private fun releaseWithVelocity() {
        compose.onNode(hasScrollAction()).performTouchInput {
            swipeWithVelocity(start = percentOffset(0.5f, 0.75f), end = percentOffset(0.5f, 0.50f),
                endVelocity = 2_200f, durationMillis = 140)
        }
        compose.mainClock.advanceTimeBy(32)
    }

    private fun beginVerticalDrag() {
        compose.onNode(hasScrollAction()).performTouchInput {
            down(percentOffset(0.5f, 0.80f))
            moveTo(percentOffset(0.5f, 0.73f), delayMillis = 16)
            moveTo(percentOffset(0.5f, 0.66f), delayMillis = 16)
        }
        compose.mainClock.advanceTimeBy(32)
    }

    private fun scrollPosition(): Float {
        val range = compose.onNode(hasScrollAction()).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        var value = 0f
        compose.runOnIdle { value = range.value() }
        return value
    }
}
