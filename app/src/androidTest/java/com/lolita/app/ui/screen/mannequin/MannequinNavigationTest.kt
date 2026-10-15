package com.lolita.app.ui.screen.mannequin

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.mannequin.MannequinGenerator
import com.lolita.app.ui.navigation.LolitaNavHost
import com.lolita.app.ui.navigation.Screen
import com.lolita.app.ui.theme.*
import com.lolita.app.ui.theme.skin.animation.SkinAnimationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MannequinNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var controller: NavHostController
    private lateinit var model: MannequinViewModel
    private lateinit var source: File
    private lateinit var generator: MannequinGenerator
    private val response = CompletableDeferred<ByteArray>()
    private val quickEntry = mutableStateOf(false)
    private var discarded = false
    private var requests = 0

    private fun start() {
        source = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "mannequin-navigation-${UUID.randomUUID()}.jpg").apply { writeBytes(byteArrayOf(1)) }
        val item = Item(id = 11, name = "心愿裙", brandId = 1, categoryId = 1, description = "", status = ItemStatus.PENDING_PAYMENT, imageUrls = listOf(source.absolutePath))
        val category = Category(id = 1, name = "OP")
        generator = MannequinGenerator(findItem = { item.takeIf { row -> row.id == it } }, findCategory = { category }, readKey = { "sk-controlled-only" },
            prepareImages = { listOf(byteArrayOf(1), byteArrayOf(2)) }, generateImage = { _, _, _ -> requests++; response.await() },
            savePreview = { source.absolutePath }, discardPreview = {})
        compose.setContent {
            LolitaTheme {
                val skin = LolitaSkin.current
                val stillSkin = remember(skin) { skin.copy(topBarBlurEnabled = false, navBarBlurEnabled = false,
                    animations = object : SkinAnimationProvider by skin.animations {
                        override val ambientAnimation = skin.animations.ambientAnimation.copy(backgroundEnabled = false)
                    }) }
                val nav = rememberNavController(); SideEffect { controller = nav }
                CompositionLocalProvider(LocalLolitaSkin provides stillSkin) {
                    LolitaNavHost(openQuickOutfit = quickEntry.value, onQuickOutfitOpened = { quickEntry.value = false }, navController = nav,
                        createMannequinViewModel = { handle ->
                            MannequinViewModel(savedStateHandle = handle, item = flowOf(item), categories = flowOf(listOf(category)),
                                hasApiKey = { true }, saveApiKey = {}, clearApiKey = {}, globalGenerating = generator.isGenerating,
                                generateImage = generator::generate, appendImage = { _, _ -> }, validatePreview = { true }, discardImage = { discarded = true }).also { model = it }
                        })
                }
            }
        }
        compose.runOnIdle { controller.navigate(Screen.ItemDetail.createRoute(11)) }
        compose.runOnIdle { controller.navigate(Screen.Mannequin.createRoute(11)) }
        compose.waitUntil(10_000) { ::model.isInitialized && model.uiState.value.canGenerate }
        compose.onNodeWithText("生成正面人台图").performClick()
        compose.waitUntil(10_000) { generator.isGenerating.value && requests == 1 }
    }
    @After fun cleanup() {
        if (::model.isInitialized) compose.runOnIdle { model.cancelGeneration(); model.discardPreview() }
        if (::generator.isInitialized) compose.waitUntil(10_000) { !generator.isGenerating.value }
        if (::source.isInitialized) source.delete()
    }
    @Test fun globalGenerationAndUnsavedPreviewPreventBottomAndQuickNavigationUntilExplicitDiscard() {
        start()
        compose.onNodeWithText("穿搭").assertIsNotEnabled().performClick()
        compose.runOnIdle { quickEntry.value = true }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(Screen.Mannequin.route, controller.currentDestination?.route) }
        response.complete(byteArrayOf(1)); compose.waitUntil(10_000) { model.uiState.value.previewPath != null && !generator.isGenerating.value }
        compose.onNodeWithText("穿搭").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(Screen.Mannequin.route, controller.currentDestination?.route) }
        compose.onNodeWithText("添加到裙子图片").assertIsDisplayed()
        compose.onNodeWithText("放弃预览").performClick(); compose.onNodeWithText("确认放弃").performClick()
        compose.waitUntil(10_000) { controller.currentDestination?.route == Screen.QuickOutfitLog.route }
        assertTrue(discarded); assertEquals(1, requests)
    }
}
