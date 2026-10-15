package com.lolita.app.ui.screen.tryon

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.tryon.TryOnGenerator
import com.lolita.app.ui.navigation.LolitaNavHost
import com.lolita.app.ui.navigation.Screen
import com.lolita.app.ui.theme.LolitaTheme
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.LocalLolitaSkin
import com.lolita.app.ui.theme.skin.animation.SkinAnimationProvider
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Exercise the actual outer navigation and its process-wide generation gate without a paid call. */
@RunWith(AndroidJUnit4::class)
class TryOnNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var controller: NavHostController
    private lateinit var model: TryOnViewModel
    private lateinit var source: File
    private lateinit var generator: TryOnGenerator
    private var requests = 0
    private val quickEntry = mutableStateOf(false)

    private fun startRequestFromItemDetail() {
        source = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "tryon-navigation-${UUID.randomUUID()}.jpg").apply { writeBytes(byteArrayOf(1)) }
        val item = Item(id = 11, name = "导航测试裙", brandId = 1, categoryId = 1,
            description = "", status = ItemStatus.OWNED, imageUrls = listOf(source.absolutePath))
        generator = TryOnGenerator(findItem = { item.takeIf { row -> row.id == it } },
            readCredentials = { com.lolita.app.domain.model.ImageGenerationCredentials(com.lolita.app.domain.model.ImageGenerationProvider.OPENAI, "sk-controlled-test-only") }, prepareImages = { listOf(byteArrayOf(1), byteArrayOf(2)) },
            generateImage = { _, _, _ -> requests++; awaitCancellation() },
            saveResult = { _, _, _ -> error("取消不能保存结果") })
        compose.setContent {
            LolitaTheme {
                val skin = LolitaSkin.current
                val stillSkin = remember(skin) { skin.copy(topBarBlurEnabled = false, navBarBlurEnabled = false,
                    animations = object : SkinAnimationProvider by skin.animations {
                        override val ambientAnimation = skin.animations.ambientAnimation.copy(backgroundEnabled = false)
                    }) }
                val nav = rememberNavController()
                SideEffect { controller = nav }
                CompositionLocalProvider(LocalLolitaSkin provides stillSkin) {
                LolitaNavHost(openQuickOutfit = quickEntry.value, onQuickOutfitOpened = { quickEntry.value = false },
                    navController = nav, createTryOnViewModel = { handle ->
                        TryOnViewModel(savedStateHandle = handle, items = flowOf(listOf(item)),
                            categories = flowOf(listOf(Category(id = 1, name = "JSK"))), results = flowOf(emptyList()),
                            readService = { com.lolita.app.data.tryon.ImageServiceStatus(com.lolita.app.domain.model.ImageGenerationProvider.OPENAI, true) }, selectService = {}, serviceChanges = kotlinx.coroutines.flow.flowOf(0), saveApiKey = { _, value ->  }, clearApiKey = { _ ->  },
                            globalGenerating = generator.isGenerating, generateImage = generator::generate,
                            renameResult = { _, _ -> }, deleteResult = {}).also { model = it }
                    })
                }
            }
        }
        compose.runOnIdle { controller.navigate(Screen.ItemDetail.createRoute(11)) }
        compose.runOnIdle { controller.navigate(Screen.TryOn.createRoute(itemId = 11)) }
        compose.waitUntil(10_000) { ::model.isInitialized && model.uiState.value.canGenerate }
        compose.onNodeWithText("生成一张试衣图").performClick()
        compose.waitUntil(10_000) { generator.isGenerating.value && requests == 1 }
    }

    private fun cancel() {
        compose.onNodeWithText("取消等待").performClick()
        compose.onAllNodesWithText("取消等待").onLast().performClick()
        compose.waitUntil(10_000) { !generator.isGenerating.value && !model.uiState.value.isGenerating }
    }

    @After fun cleanup() {
        if (::model.isInitialized) compose.runOnIdle { model.cancelGeneration() }
        if (::generator.isInitialized) compose.waitUntil(10_000) { !generator.isGenerating.value }
        if (::source.isInitialized) source.delete()
    }

    @Test fun bottomNavigationCannotHideTheOnlyCancellationControl() {
        startRequestFromItemDetail()
        compose.onNodeWithText("穿搭").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(Screen.TryOn.route, controller.currentDestination?.route) }
        compose.onNodeWithText("取消等待").assertIsDisplayed()
        cancel()
        compose.onNodeWithText("穿搭").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(Screen.OutfitLogList.route, controller.currentDestination?.route)
            assertEquals(1, requests)
        }
    }

    @Test fun quickOutfitIntentWaitsUntilTheGenerationIsCancelled() {
        startRequestFromItemDetail()
        compose.runOnIdle { quickEntry.value = true }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(Screen.TryOn.route, controller.currentDestination?.route) }
        cancel()
        compose.waitUntil(10_000) { controller.currentDestination?.route == Screen.QuickOutfitLog.route }
        assertEquals(1, requests)
    }
}
