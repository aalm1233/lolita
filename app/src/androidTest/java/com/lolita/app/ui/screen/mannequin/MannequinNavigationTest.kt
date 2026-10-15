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
import android.view.KeyEvent
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
    private var acknowledgements = 0
    private val appendEntered = CompletableDeferred<Unit>()
    private val appendRelease = CompletableDeferred<Unit>()
    private val cancellationEntered = CompletableDeferred<Unit>()
    private val cancellationRelease = CompletableDeferred<Unit>()
    private val restoreEntered = CompletableDeferred<Unit>()
    private val restoreRelease = CompletableDeferred<Unit>()
    private var holdCancellation = false
    private lateinit var previewFile: File

    private fun start(restorePending: Boolean = false) {
        source = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "mannequin-navigation-${UUID.randomUUID()}.jpg").apply { writeBytes(byteArrayOf(1)) }
        previewFile = File(source.parentFile, "mannequin-preview-${UUID.randomUUID()}.png").apply { writeBytes(byteArrayOf(1)) }
        val item = Item(id = 11, name = "心愿裙", brandId = 1, categoryId = 1, description = "", status = ItemStatus.PENDING_PAYMENT, imageUrls = listOf(source.absolutePath))
        val category = Category(id = 1, name = "OP")
        generator = MannequinGenerator(findItem = { item.takeIf { row -> row.id == it } }, findCategory = { category }, readCredentials = { com.lolita.app.domain.model.ImageGenerationCredentials(com.lolita.app.domain.model.ImageGenerationProvider.OPENAI, "sk-controlled-only") },
            prepareImages = { listOf(byteArrayOf(1), byteArrayOf(2)) }, generateImage = { _, _, _ ->
                requests++
                try { response.await() } finally {
                    if (holdCancellation) withContext(NonCancellable) { cancellationEntered.complete(Unit); cancellationRelease.await() }
                }
            },
            savePreview = { previewFile.absolutePath }, discardPreview = {})
        compose.setContent {
            LolitaTheme {
                val skin = LolitaSkin.current
                val stillSkin = remember(skin) { skin.copy(topBarBlurEnabled = false, navBarBlurEnabled = false,
                    animations = object : SkinAnimationProvider by skin.animations {
                        override val ambientAnimation = skin.animations.ambientAnimation.copy(backgroundEnabled = false)
                    }) }
                val nav = rememberNavController(); SideEffect { controller = nav }
                CompositionLocalProvider(LocalLolitaSkin provides stillSkin) {
                    LolitaNavHost(openQuickOutfit = quickEntry.value, onQuickOutfitOpened = { acknowledgements++; quickEntry.value = false }, navController = nav,
                        createMannequinViewModel = { handle ->
                            if (restorePending) {
                                handle["mannequinPreview"] = previewFile.absolutePath
                                handle["mannequinPreviewItem"] = 11L
                            }
                            MannequinViewModel(savedStateHandle = handle, item = flowOf(item), categories = flowOf(listOf(category)),
                                readService = { com.lolita.app.data.tryon.ImageServiceStatus(com.lolita.app.domain.model.ImageGenerationProvider.OPENAI, true) }, selectService = {}, serviceChanges = kotlinx.coroutines.flow.flowOf(0), saveApiKey = { _, value ->  }, clearApiKey = { _ ->  }, globalGenerating = generator.isGenerating,
                                generateImage = generator::generate, appendImage = { _, _ -> appendEntered.complete(Unit); appendRelease.await() },
                                validatePreview = {
                                    withContext(Dispatchers.IO) { restoreEntered.complete(Unit); restoreRelease.await(); true }
                                }, discardImage = { discarded = true; File(it).delete(); Unit }).also { model = it }
                        })
                }
            }
        }
        compose.runOnIdle { controller.navigate(Screen.ItemDetail.createRoute(11)) }
        compose.runOnIdle { controller.navigate(Screen.Mannequin.createRoute(11)) }
        if (restorePending) {
            compose.waitUntil(10_000) { restoreEntered.isCompleted }
        } else {
            compose.waitUntil(10_000) { ::model.isInitialized && model.uiState.value.canGenerate }
            compose.onNodeWithText("生成正面人台图").performClick()
            compose.waitUntil(10_000) { generator.isGenerating.value && requests == 1 }
        }
    }
    @After fun cleanup() {
        compose.mainClock.autoAdvance = true
        cancellationRelease.complete(Unit)
        restoreRelease.complete(Unit)
        appendRelease.complete(Unit)
        if (::model.isInitialized) compose.runOnIdle { model.cancelGeneration(); model.discardPreview() }
        if (::generator.isInitialized) compose.waitUntil(10_000) { !generator.isGenerating.value }
        if (::source.isInitialized) source.delete()
        if (::previewFile.isInitialized) previewFile.delete()
    }
    private fun assertQuickDestinationAndRemovedSource() {
        compose.waitUntil(10_000) { controller.currentDestination?.route == Screen.QuickOutfitLog.route }
        compose.runOnIdle {
            assertEquals(1, acknowledgements)
            assertTrue(controller.currentBackStack.value.none { it.destination.route == Screen.Mannequin.route })
        }
    }

    @Test fun restoredPreviewValidationBlocksToolbarBackUntilExplicitDiscardCanOwnAndCleanIt() {
        start(restorePending = true)
        compose.onNodeWithContentDescription("返回").assertIsNotEnabled().performClick()
        finishRestoreAndDiscard()
    }

    @Test fun restoredPreviewValidationConsumesSystemBackUntilExplicitDiscardCanOwnAndCleanIt() {
        start(restorePending = true)
        compose.waitForIdle() // The loading page and its BackHandler must be committed before a key event.
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        finishRestoreAndDiscard()
    }

    private fun finishRestoreAndDiscard() {
        compose.runOnIdle {
            assertEquals(Screen.Mannequin.route, controller.currentDestination?.route)
            assertTrue(model.uiState.value.isLoading)
            assertTrue(previewFile.exists())
        }
        restoreRelease.complete(Unit)
        compose.waitUntil(10_000) { !model.uiState.value.isLoading && model.uiState.value.previewPath != null }
        compose.onNodeWithContentDescription("返回").assertIsEnabled().performClick()
        compose.onNodeWithText("继续核对").assertIsDisplayed()
        compose.runOnIdle { assertEquals(Screen.Mannequin.route, controller.currentDestination?.route) }
        compose.onNodeWithText("放弃并返回").performClick()
        compose.waitUntil(10_000) { controller.currentDestination?.route == Screen.ItemDetail.route }
        assertTrue(discarded); assertFalse(previewFile.exists()); assertTrue(source.exists())
    }

    @Test fun queuedQuickIntentWaitsForSaveDepartureThenAcknowledgesOnceAndRemovesMannequinEntry() {
        start()
        compose.runOnIdle { quickEntry.value = true }
        response.complete(byteArrayOf(1))
        compose.waitUntil(10_000) { model.uiState.value.previewPath != null && !model.uiState.value.isGenerating }
        compose.onNodeWithText("添加到裙子图片").performClick()
        compose.waitUntil(10_000) { appendEntered.isCompleted }
        // Freeze composition so the VM's terminal transition is examined before either
        // the outer quick-intent effect or inner departure effect can run.
        compose.mainClock.autoAdvance = false
        appendRelease.complete(Unit)
        compose.waitUntil(10_000) { model.uiState.value.saved && !model.uiState.value.isSaving }
        assertTrue("Save must keep outer navigation blocked until its own entry departs", model.uiState.value.navigationBlocked)
        compose.mainClock.autoAdvance = true
        assertQuickDestinationAndRemovedSource()
    }

    @Test fun queuedQuickIntentWaitsForCancelReturnDepartureThenAcknowledgesOnceAndRemovesMannequinEntry() {
        holdCancellation = true
        start()
        compose.runOnIdle { quickEntry.value = true }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onAllNodesWithText("取消等待").onLast().performClick()
        compose.waitUntil(10_000) { cancellationEntered.isCompleted }
        compose.mainClock.autoAdvance = false
        cancellationRelease.complete(Unit)
        compose.waitUntil(10_000) { !model.uiState.value.isGenerating && !generator.isGenerating.value }
        assertTrue("Cancel-and-return must keep outer navigation blocked until its own entry departs", model.uiState.value.navigationBlocked)
        compose.mainClock.autoAdvance = true
        assertQuickDestinationAndRemovedSource()
        assertEquals(1, requests)
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
