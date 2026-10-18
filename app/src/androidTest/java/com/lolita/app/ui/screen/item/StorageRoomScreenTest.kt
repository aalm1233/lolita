package com.lolita.app.ui.screen.item

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.repository.*
import com.lolita.app.ui.theme.LolitaTheme
import com.lolita.app.ui.theme.SkinType
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class StorageRoomScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: LolitaDatabase
    private lateinit var model: LocationDetailViewModel
    private val store = ViewModelStore()
    private var cabinet = 0L
    private var shelf = 0L
    private var shoe = 0L
    private var accessory = 0L
    private val skin = mutableStateOf(SkinType.DEFAULT)
    private val dark = mutableStateOf(false)
    private val compact = mutableStateOf(false)

    @Before fun setup() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        db.locationDao().insertLocation(Location(id = 1, name = "玄关"))
        db.locationDao().insertLocation(Location(id = 2, name = "卧室"))
        db.brandDao().insertBrand(Brand(id = 1, name = "测试品牌"))
        db.categoryDao().insertCategory(Category(id = 1, name = "鞋履"))
        val storage = StorageRepository(db)
        cabinet = storage.createNode(1, null, "鞋柜")
        shelf = storage.createNode(1, cabinet, "第二层")
        shoe = db.itemDao().insertItem(Item(brandId = 1, categoryId = 1, name = "玛丽珍鞋", description = "", status = ItemStatus.OWNED,
            locationId = 1, storageNodeId = shelf))
        accessory = db.itemDao().insertItem(Item(brandId = 1, categoryId = 1, name = "配套鞋扣", description = "", status = ItemStatus.OWNED,
            locationId = 1, storageNodeId = cabinet, parentItemId = shoe))
        compose.runOnIdle {
            model = LocationDetailViewModel(LocationRepository(db.locationDao(), db.itemDao(), db, storage),
                BrandRepository(db.brandDao(), db.itemDao(), db), CategoryRepository(db.categoryDao(), db.itemDao(), db),
                ItemRepository(db.itemDao(), database = db), storage)
            store.put("storage", model)
        }
        compose.setContent {
            val density = LocalDensity.current
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration,
                LocalDensity provides Density(density.density, if (compact.value) 2f else density.fontScale)) {
                Box(if (compact.value) Modifier.width(320.dp) else Modifier) {
                    LolitaTheme(skinType = skin.value, darkTheme = dark.value) {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            LocationDetailScreen(1, {}, {}, model)
                        }
                    }
                }
            }
        }
        compose.waitUntil(10_000) { !model.uiState.value.isLoading && model.uiState.value.nodes.size == 2 }
    }
    @After fun teardown() { compose.runOnIdle { store.clear() }; db.close() }

    private fun openCabinet() {
        val button = compose.onNodeWithText("鞋柜", substring = false)
        button.performScrollTo()
        compose.waitForIdle()
        button.performClick()
        try {
            compose.waitUntil(5_000) { model.uiState.value.currentNodeId == cabinet }
        } catch (failure: Throwable) {
            val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
                "storage-diagnostics/${UUID.randomUUID()}").apply { mkdirs() }
            File(directory, "after-touch-state.txt").writeText(model.uiState.value.toString())
            File(directory, "semantics.txt").writeText(compose.onRoot().printToString())
            File(directory, "after-touch.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            throw failure
        }
    }

    @Test fun roomShowsCabinetsAndCabinetFindsAttachmentsAndNestedItemsWithoutDuplicates() {
        assertTrue(model.uiState.value.items.isEmpty())
        openCabinet()
        assertEquals(2, model.uiState.value.recordCount)
        assertEquals(setOf(shoe, accessory), model.uiState.value.items.map { it.id }.toSet())
        compose.onNodeWithText("搜索名称、品牌、分类或位置路径").performTextInput("第二层")
        compose.waitUntil(5_000) { model.uiState.value.items.size == 1 }
        assertEquals(shoe, model.uiState.value.items.single().id)
    }

    @Test fun selectionSurvivesFilteringAndOnlyMovesAfterAffectedCountConfirmation() = runBlocking {
        openCabinet()
        compose.onNodeWithContentDescription("选择配套鞋扣").performScrollTo().performClick()
        compose.onNodeWithText("搜索名称、品牌、分类或位置路径").performScrollTo().performTextInput("玛丽珍鞋")
        assertEquals(setOf(accessory), model.uiState.value.selectedItemIds)
        compose.onNodeWithText("移动 1 条记录").performClick()
        compose.onNodeWithText("搜索位置路径").performTextInput("卧室")
        compose.onNode(hasText("卧室") and hasClickAction() and !hasSetTextAction()).performClick()
        compose.onNodeWithText("将移动 1 条记录").assertIsDisplayed()
        assertEquals(1L, db.itemDao().getItemById(accessory)!!.locationId)
        compose.onNodeWithText("确认移动").performClick()
        compose.waitUntil(5_000) { model.uiState.value.allItems.find { it.id == accessory }?.locationId == 2L }
        assertNull(db.itemDao().getItemById(accessory)!!.storageNodeId)
        assertEquals(1L, db.itemDao().getItemById(shoe)!!.locationId)
        assertEquals(shoe, db.itemDao().getItemById(accessory)!!.parentItemId)
    }

    @Test fun allSevenSkinsKeepSelectionAndMoveControlsReachableOnSmallScreensWithLargeText() {
        openCabinet()
        compose.onNodeWithContentDescription("选择配套鞋扣").performScrollTo().performClick()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "storage-screenshots/${UUID.randomUUID()}").apply { mkdirs() }
        SkinType.entries.forEach { candidate ->
            listOf(false, true).forEach { isDark ->
                compose.runOnIdle { skin.value = candidate; dark.value = isDark; compact.value = true }
                compose.onNodeWithText("移动 1 条记录").assertIsDisplayed().assertIsEnabled()
                assertEquals(setOf(accessory), model.uiState.value.selectedItemIds)
                compose.onNodeWithTag("storage-room-list").performScrollToNode(hasContentDescription("选择配套鞋扣"))
                compose.onNodeWithContentDescription("选择配套鞋扣").assertIsDisplayed()
                val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
                File(directory, "${candidate.name.lowercase()}-${if (isDark) "dark" else "light"}-320dp-2x.png")
                    .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
}
