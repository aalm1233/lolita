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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
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
    private val compactFontScale = mutableStateOf(2f)

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
                LocalDensity provides Density(density.density, if (compact.value) compactFontScale.value else density.fontScale)) {
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

    @Test fun unpaidItemsAreAbsentFromRoomCountsAndPickerAndLostOwnershipClearsSelection() = runBlocking {
        val unpaid = db.itemDao().insertItem(Item(brandId=1,categoryId=1,name="未付尾款物品",description="旧位置保留",
            status=ItemStatus.PENDING_BALANCE,locationId=1,storageNodeId=shelf))
        compose.waitUntil(10_000) { model.uiState.value.allItems.size==3 && model.uiState.value.nodeItemCounts[cabinet]==2 }
        compose.onNodeWithText("移入物品").performScrollTo().performClick()
        compose.onNodeWithText("选择移入物品").assertIsDisplayed()
        compose.onNodeWithText("未付尾款物品").assertDoesNotExist()
        compose.onNodeWithText("取消").performClick()
        openCabinet()
        compose.onNodeWithContentDescription("选择玛丽珍鞋").performScrollTo().performClick()
        db.itemDao().updateItemStatus(shoe,"PENDING_PAYMENT")
        compose.waitUntil(10_000) { model.uiState.value.items.map {it.id}==listOf(accessory) && model.uiState.value.selectedItemIds.isEmpty() }
        assertEquals(1,model.uiState.value.nodeItemCounts[cabinet])
        val directory=File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"owned-storage").apply{mkdirs()}
        for(type in SkinType.entries) for(isDark in listOf(false,true)) {
            compose.runOnIdle{skin.value=type;dark.value=isDark;compact.value=true}
            compose.onNodeWithTag("storage-room-list").performScrollToNode(hasContentDescription("选择配套鞋扣"))
            compose.onNodeWithText("未付尾款物品").assertDoesNotExist()
            compose.onNodeWithContentDescription("选择玛丽珍鞋").assertDoesNotExist()
            File(directory,"room-${type.name}-${if(isDark) "dark" else "light"}.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
            }
        }
        db.itemDao().updateItemStatus(shoe,"OWNED")
        compose.waitUntil(10_000){model.uiState.value.items.size==2}
        assertEquals(shelf,db.itemDao().getItemById(shoe)!!.storageNodeId)
        assertEquals(shelf,db.itemDao().getItemById(unpaid)!!.storageNodeId)
    }

    private fun assertSingleLineAction(label: String, expectedFontScale: Float) {
        compose.onNode(hasText(label) and hasClickAction()).performScrollTo().assertIsDisplayed().assertIsEnabled()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals("The actual action text must expose its measured layout", 1, layouts.size)
        val layout = layouts.single()
        assertEquals(label, layout.layoutInput.text.text)
        assertEquals(expectedFontScale, layout.layoutInput.density.fontScale, 0.01f)
        val listWidth = compose.onNodeWithTag("storage-room-list").fetchSemanticsNode().boundsInRoot.width
        assertEquals("The real screen content must be constrained to 320dp", 320f,
            listWidth / layout.layoutInput.density.density, 1f)
        assertEquals("The complete action label must stay on one line: $label", 1, layout.lineCount)
        assertFalse("The action label must not be ellipsized", layout.isLineEllipsized(0))
        assertEquals(label.length, layout.getLineEnd(0))
        assertTrue("The measured text must fit its actual box",
            layout.getLineLeft(0) >= -1f && layout.getLineRight(0) <= layout.size.width + 1f &&
                layout.getLineTop(0) >= -1f && layout.getLineBottom(0) <= layout.size.height + 1f)
    }

    private fun assertRootActions(expectedFontScale: Float) {
        assertNull(model.uiState.value.currentNodeId)
        val createLabel = "新增柜架 / 收纳盒"
        assertSingleLineAction(createLabel, expectedFontScale)
        assertSingleLineAction("移入物品", expectedFontScale)
        val create = compose.onNode(hasText(createLabel) and hasClickAction()).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val move = compose.onNode(hasText("移入物品") and hasClickAction()).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Create and move must occupy separate vertical rows", create.bottom <= move.top)
        assertEquals("Both actions must use the same full content width", create.left, move.left, 1f)
        assertEquals("Both actions must use the same full content width", create.right, move.right, 1f)
    }

    @Test fun rootActionsStaySingleLineAndCreateRoomNodesAt320dpWithNormalAndLargeText() = runBlocking {
        listOf(1f, 2f).forEachIndexed { index, scale ->
            compose.runOnIdle { compact.value = true; compactFontScale.value = scale }
            assertRootActions(scale)
            val name = "根级收纳-${index + 1}"
            compose.onNodeWithText("新增柜架 / 收纳盒").performClick()
            compose.onNodeWithText("新增柜架或收纳盒").assertIsDisplayed()
            compose.onNodeWithText("位置名称").performTextInput(name)
            compose.onNodeWithText("保存").assertIsEnabled().performClick()
            compose.waitUntil(5_000) { model.uiState.value.nodes.any { it.name == name } && !model.uiState.value.isSaving }
            val saved = db.storageNodeDao().getAll().single { it.name == name }
            assertEquals("The actual dialog save must create a node in this room", 1L, saved.locationId)
            assertNull("A room-level action must create a cabinet, not a compartment", saved.parentNodeId)
            assertEquals(3 + index, db.storageNodeDao().getAll().size)
        }
    }

    @Test fun allSevenSkinsKeepRootActionsSingleLineAndSeparatedAt320dpInBothFontScales() {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "storage-root-action-screenshots/${UUID.randomUUID()}").apply { mkdirs() }
        SkinType.entries.forEach { candidate ->
            listOf(false, true).forEach { isDark ->
                listOf(1f, 2f).forEach { scale ->
                    compose.runOnIdle {
                        skin.value = candidate; dark.value = isDark; compact.value = true; compactFontScale.value = scale
                    }
                    assertRootActions(scale)
                    val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
                    File(directory, "${candidate.name.lowercase()}-${if (isDark) "dark" else "light"}-320dp-${scale.toInt()}x.png")
                        .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
        }
        assertEquals("Every skin, theme and font scale must produce an actual screenshot", 28,
            directory.listFiles { file -> file.extension == "png" }!!.size)
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
