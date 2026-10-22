package com.lolita.app.ui.screen.item

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.asAndroidBitmap
import android.content.res.Configuration
import android.content.Context
import android.content.ContextWrapper
import com.lolita.app.data.recognition.RecognitionCoordinator
import com.lolita.app.data.recognition.RecognitionSettings
import com.lolita.app.ui.screen.item.recognition.ItemRecognitionViewModel
import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import kotlin.math.max
import kotlin.math.min
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.repository.*
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.ui.screen.common.SizeOptionsViewModel
import com.lolita.app.ui.theme.LolitaTheme
import com.lolita.app.ui.theme.SkinType
import com.lolita.app.domain.model.WardrobeSpecifications
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID
import org.junit.*
import org.junit.Assert.*

class ItemSizeSelectorTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: LolitaDatabase
    private lateinit var editor: ItemEditViewModel
    private val store = ViewModelStore()
    private lateinit var preferences: AppPreferences
    private lateinit var preferencesScope: CoroutineScope
    private lateinit var directory: File
    private lateinit var recognitionContext: Context
    private val skin = mutableStateOf(SkinType.DEFAULT)
    private val dark = mutableStateOf(false)
    private val compact = mutableStateOf(false)

    private fun openPreferences() {
        preferencesScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        preferences = AppPreferences(context, PreferenceDataStoreFactory.create(scope = preferencesScope,
            produceFile = { File(directory, "sizes.preferences_pb") }))
    }

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        directory = File(context.cacheDir, "size-selector-${UUID.randomUUID()}").apply { mkdirs() }
        recognitionContext = object : ContextWrapper(context) {
            override fun getNoBackupFilesDir() = File(directory, "keys").apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int) =
                context.getSharedPreferences("${directory.name}-$name", mode)
        }
        openPreferences()
        db.brandDao().insertBrand(Brand(id = 1, name = "尺码测试品牌"))
        db.categoryDao().insertCategory(Category(id = 1, name = "JSK"))
        db.itemDao().insertItem(Item(id = 1, name = "已有特殊尺码的裙子", description = "", status = ItemStatus.OWNED,
            brandId = 1, categoryId = 1, specificationsJson = WardrobeSpecifications().encode(), size = "定制 88/72"))
        compose.runOnIdle {
            editor = ItemEditViewModel(
                ItemRepository(db.itemDao(), database = db), BrandRepository(db.brandDao(), db.itemDao(), db),
                CategoryRepository(db.categoryDao(), db.itemDao(), db), CoordinateRepository(db.coordinateDao(), db.itemDao(), db),
                PriceRepository(db.priceDao(), db.paymentDao(), context, db), StyleRepository(db.styleDao(), db.itemDao(), db),
                SeasonRepository(db.seasonDao(), db.itemDao(), db), SourceRepository(db.sourceDao(), db.itemDao(), db),
                LocationRepository(db.locationDao(), db.itemDao()))
            store.put("editor", editor)
            ViewModelProvider(store, object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return when (modelClass) {
                        SizeOptionsViewModel::class.java -> SizeOptionsViewModel(preferences, ItemRepository(db.itemDao()))
                        ItemRecognitionViewModel::class.java -> ItemRecognitionViewModel(
                            RecognitionRepository(db, recognitionContext),
                            RecognitionCoordinator(db, recognitionContext, preferencesScope,
                                readKey = { error("No key in a size selector test") }, keyRevision = { 0 },
                                recognize = { _, _, _ -> error("No network in a size selector test") }, providerReady = { false }),
                            RecognitionSettings(recognitionContext))
                        else -> error("Unexpected ViewModel ${modelClass.name}")
                    } as T
                }
            }).also { provider ->
                provider[SizeOptionsViewModel::class.java]
                provider[ItemRecognitionViewModel::class.java]
            }
        }
        val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
        compose.setContent {
            val density = LocalDensity.current
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner, LocalConfiguration provides config,
                LocalDensity provides Density(density.density, if (compact.value) 2f else density.fontScale)) {
            Box(if (compact.value) Modifier.width(320.dp) else Modifier) {
                LolitaTheme(skinType = skin.value, darkTheme = dark.value) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        ItemEditScreen(1, onBack = {}, onSaveSuccess = {}, viewModel = editor)
                    }
                }
            }
        } }
        compose.waitUntil(10_000) { editor.uiState.value.item?.id == 1L }
    }
    @After fun close() = runBlocking {
        compose.runOnIdle { store.clear() }
        preferencesScope.coroutineContext[Job]!!.cancelAndJoin()
        db.close()
        File(context.applicationInfo.dataDir, "shared_prefs").listFiles()
            ?.filter { it.name.startsWith(directory.name + "-") && it.name.endsWith(".xml") }
            ?.forEach { context.deleteSharedPreferences(it.name.removeSuffix(".xml")) }
        directory.deleteRecursively(); Unit
    }

    @Test fun existingCustomSizeCanBeReplacedFromDropdownAndCleared() {
        compose.onNodeWithText("尺码").performScrollTo().performClick()
        compose.onNodeWithText("M", useUnmergedTree = true).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("M", editor.uiState.value.size) }
        compose.onNodeWithText("尺码").performClick()
        compose.onNodeWithText("未设置").performClick()
        compose.runOnIdle { assertNull(editor.uiState.value.size) }
        runBlocking { assertEquals("定制 88/72", db.itemDao().getItemById(1)!!.size) }
    }

    private fun addSize(text: String) {
        compose.onNodeWithContentDescription("添加尺码").performScrollTo().performClick()
        compose.onNodeWithText("自定义尺码").performTextInput(text)
        compose.onNodeWithText("添加并选用").performClick()
        compose.waitUntil(5_000) { editor.uiState.value.size == text.trim() &&
            compose.onAllNodesWithText("自定义尺码").fetchSemanticsNodes().isEmpty() }
        compose.waitForIdle()
    }

    @Test fun quickAddSelectsTrimsAndPersistsAcrossReopeningSettings() = runBlocking {
        addSize("  鞋码 37  ")
        assertEquals(listOf("鞋码 37"), preferences.customSizes.first())
        preferencesScope.coroutineContext[Job]!!.cancelAndJoin()
        openPreferences()
        assertEquals(listOf("鞋码 37"), preferences.customSizes.first())
        assertEquals("定制 88/72", db.itemDao().getItemById(1)!!.size)
    }

    @Test fun duplicateSizeIsReusedAndBlankOrCancelledAddDoesNotChangeSelection() = runBlocking {
        addSize("定制")
        addSize(" 定制 ")
        assertEquals(listOf("定制"), preferences.customSizes.first())
        compose.onNodeWithContentDescription("添加尺码").performClick()
        compose.onNodeWithText("添加并选用").assertIsNotEnabled()
        compose.onNodeWithText("自定义尺码").performTextInput("   ")
        compose.onNodeWithText("添加并选用").assertIsNotEnabled()
        compose.onNodeWithText("取消").performClick()
        assertEquals("定制", editor.uiState.value.size)
        assertEquals(listOf("定制"), preferences.customSizes.first())
    }

    @Test fun selectingBuiltInSizeSavesOnlyChosenItemSizeAndRetainsLegacyOption() = runBlocking {
        val before = db.itemDao().getItemById(1)!!
        compose.onNodeWithText("尺码").performScrollTo().performClick()
        compose.onNodeWithText("XL", useUnmergedTree = true).performClick()
        compose.onNodeWithText("尺码").performClick()
        compose.onNodeWithText("定制 88/72", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("均码", useUnmergedTree = true).performScrollTo().performClick()
        withContext(Dispatchers.Main) { editor.saveItem().getOrThrow() }
        val after = db.itemDao().getItemById(1)!!
        assertEquals(before.copy(size = "均码", updatedAt = after.updatedAt), after)
        assertTrue(preferences.customSizes.first().isEmpty())
    }

    @Test fun sevenSkinsLightAndDarkKeepDropdownAndAddUsableAtLargeFont() {
        val output = File(context.filesDir, "size-selector-screenshots").apply { mkdirs() }
        for (type in SkinType.entries) for (isDark in listOf(false, true)) {
            compose.runOnIdle { skin.value = type; dark.value = isDark; compact.value = true }
            compose.onNodeWithText("尺码").performScrollTo().assertIsDisplayed().performClick()
            compose.onNode(hasText("XL") and hasAnyAncestor(isPopup()), useUnmergedTree = true)
                .performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription("添加尺码").assertIsDisplayed().performClick()
            compose.onNodeWithText("自定义尺码").assertIsDisplayed()
            compose.onNodeWithText("取消").performClick()
            compose.onNodeWithText("尺码").performScrollTo()
            val field = compose.onNodeWithText("尺码").fetchSemanticsNode().boundsInRoot
            val add = compose.onNodeWithContentDescription("添加尺码").fetchSemanticsNode().boundsInRoot
            compose.onRoot().captureToImage().asAndroidBitmap().let { full ->
                val left = max(0, min(field.left, add.left).toInt() - 24)
                val top = max(0, min(field.top, add.top).toInt() - 24)
                val right = min(full.width, max(field.right, add.right).toInt() + 24)
                val bottom = min(full.height, max(field.bottom, add.bottom).toInt() + 24)
                val bitmap = Bitmap.createBitmap(full, left, top, right - left, bottom - top)
                File(output, "${type.name}-${if (isDark) "dark" else "light"}.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
    }
}
