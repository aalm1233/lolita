package com.lolita.app.ui.screen.mannequin

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.entity.*
import com.lolita.app.ui.theme.LolitaTheme
import com.lolita.app.ui.theme.SkinType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Actual Compose state transitions; external image service and credentials are the only controlled boundaries. */
@RunWith(AndroidJUnit4::class)
class MannequinScreenTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var store: ViewModelStore
    private lateinit var directory: File
    private lateinit var vm: MannequinViewModel
    private lateinit var photos: List<String>
    private var key: String? = null
    private var provider = com.lolita.app.domain.model.ImageGenerationProvider.OPENAI
    private var generated = CompletableDeferred<String>()
    private var calls = 0
    private var appended: String? = null
    private var discarded: String? = null
    private var navigated = 0
    private var configured = 0
    private var blocked = false
    private lateinit var categoryRows: MutableStateFlow<List<Category>>
    private var submitted: com.lolita.app.domain.model.MannequinRequest? = null
    private val presentation = mutableStateOf(Presentation())

    @Before fun setup() {
        directory = File(instrumentation.targetContext.cacheDir, "mannequin-ui-${UUID.randomUUID()}").apply { mkdirs() }
        photos = (1..5).map { index -> File(directory, "$index.png").also { file ->
            Bitmap.createBitmap(if (index >= 4) 240 else 40, if (index >= 4) 640 else 60, Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.eraseColor(if (index == 1) android.graphics.Color.MAGENTA else android.graphics.Color.BLUE)
                if (index >= 4) {
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (index == 4) 0xFFAA4B70.toInt() else 0xFF304A75.toInt()
                    }
                    for (shift in listOf(0f, 92f)) {
                        val path = android.graphics.Path().apply {
                            moveTo(28f+shift,50f);lineTo(72f+shift,50f);lineTo(72f+shift,460f)
                            lineTo(118f+shift,535f);lineTo(108f+shift,565f);lineTo(48f+shift,565f)
                            lineTo(28f+shift,490f);close()
                        }
                        canvas.drawPath(path,paint)
                    }
                    paint.color = android.graphics.Color.WHITE
                    for (shift in listOf(0f,92f)) for (y in listOf(115f,175f,235f,295f,355f,415f))
                        canvas.drawCircle(50f+shift,y,7f,paint)
                }
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            }
        }.absolutePath }
        store = ViewModelStore()
        categoryRows = MutableStateFlow(listOf(Category(id = 1, name = "OP", categorySemantic = com.lolita.app.domain.model.CategorySemantic.OP)))
        instrumentation.runOnMainSync {
            vm = MannequinViewModel(SavedStateHandle(mapOf("itemId" to 11L)),
                item = flowOf(Item(id = 11, name = "月光心愿裙", brandId = 1, categoryId = 1, description = "", status = ItemStatus.WISHED,
                    imageUrls = photos.take(2), sizeChartImageUrl = photos[3], sizeChartImageUrls = listOf(photos[4]))),
                categories = categoryRows,
                readService = { com.lolita.app.data.tryon.ImageServiceStatus(provider, key != null) }, selectService = { provider = it }, serviceChanges = kotlinx.coroutines.flow.flowOf(0), saveApiKey = { _, value -> key = value }, clearApiKey = { _ -> key = null },
                tasks = flowOf(emptyList()), submitTask = { request, _ -> calls++; submitted = request; generated.await() })
            store.put("vm", vm)
        }
        compose.waitUntil(10_000) { !vm.uiState.value.isLoading && vm.uiState.value.keyLoaded }
    }
    @After fun cleanup() { compose.runOnIdle { store.clear() }; directory.deleteRecursively() }
    private fun show() {
        compose.setContent {
            val display = presentation.value
            val config = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = display.width
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (display.dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config, LocalDensity provides Density(LocalDensity.current.density, display.scale)) {
                LolitaTheme(skinType = display.skin, darkTheme = display.dark) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.size(display.width.dp, 640.dp)) {
                            MannequinScreen(onNavigateBack = { navigated++ }, viewModel = vm, onConfigureApi = { configured++ })
                        }
                    }
                }
            }
        }
    }
    private fun scroll(text: String) {
        compose.onNodeWithTag("mannequinWorkspace").performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performScrollTo()
    }
    private fun configure() {
        compose.runOnIdle { vm.saveKey("controlled-ui-private") }
        compose.waitUntil(10_000) { vm.uiState.value.hasKey && !vm.uiState.value.isConfiguring }
    }

    @Test fun detailSourceSelectionAcrossSevenSkinsAndSmallTextKeepsActualConfirmedInput() {
        compose.runOnIdle { categoryRows.value = listOf(Category(id = 1, name = "连裤袜", group = CategoryGroup.ACCESSORY,
            categorySemantic = com.lolita.app.domain.model.CategorySemantic.KNEE_SOCKS)) }
        compose.waitUntil(5_000) { vm.uiState.value.presentation == com.lolita.app.domain.model.MannequinPresentation.ACCESSORY }
        show(); configure()
        scroll("详情图（2）"); compose.onNodeWithText("详情图（2）").performClick()
        scroll("详情图 2"); compose.onNodeWithText("详情图 2").performClick()
        assertEquals(photos[4], vm.uiState.value.selectedImage)
        scroll("商品图（2）"); compose.onNodeWithText("商品图（2）").performClick()
        compose.onNodeWithText("已选：详情图 2").assertExists()
        assertEquals(photos[4], vm.uiState.value.selectedImage)
        scroll("详情图（2）"); compose.onNodeWithText("详情图（2）").performClick().assertIsSelected()
        val output = File(instrumentation.targetContext.filesDir, "mannequin-detail-source-screens").apply { mkdirs() }
        for (skin in SkinType.entries) for (dark in listOf(false, true)) {
            compose.runOnIdle { presentation.value = Presentation(width = 360, scale = 1f, skin = skin, dark = dark) }
            scroll("详情图（2）")
            compose.onNodeWithText("已选：详情图 2").assertExists()
            File(output, "${skin.name.lowercase()}-${if (dark) "dark" else "light"}.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.runOnIdle { presentation.value = Presentation(width = 320, scale = 2f) }
        scroll("详情图（2）")
        compose.onNodeWithText("详情图（2）").assertIsSelected()
        compose.onNodeWithTag("mannequinWorkspace").performScrollToIndex(0)
        scroll("详情图 2")
        compose.onNodeWithText("生成单品展示图").assertIsDisplayed().assertIsEnabled()
        File(output, "small-320dp-2x.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("生成单品展示图").performClick()
        assertEquals(0, calls)
        compose.onNodeWithText("确认并加入任务").performClick()
        compose.waitUntil(10_000) { calls == 1 }
        assertEquals(photos[4], submitted!!.imagePath)
        assertEquals(com.lolita.app.domain.model.CategorySemantic.KNEE_SOCKS, submitted!!.subject!!.semantic)
        generated.complete("detail-source-task")
        compose.waitUntil(10_000) { !vm.uiState.value.isSubmitting }
    }
    @Test fun standaloneAccessoryDeclaresOnePhotoAndSubmitsTheConfirmedCategory() {
        compose.runOnIdle { categoryRows.value = listOf(Category(id = 1, name = "KC", group = CategoryGroup.ACCESSORY, categorySemantic = com.lolita.app.domain.model.CategorySemantic.KC)) }
        compose.waitUntil(10_000) { vm.uiState.value.category?.name == "KC" }
        show(); configure()
        compose.onNodeWithText("AI 展示图 · 单品展示图").assertIsDisplayed()
        compose.onNodeWithTag("mannequinUploadSummary").assertTextContains("共1张图片", substring = true)
        val output = File(instrumentation.targetContext.filesDir, "accessory-ui-contact-sheets").apply { mkdirs() }
        for (skin in SkinType.entries) for (dark in listOf(false, true)) {
            compose.runOnIdle { presentation.value = Presentation(skin = skin, dark = dark) }
            compose.onNodeWithText("生成单品展示图").assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithTag("mannequinUploadSummary").assertTextContains("共1张图片", substring = true)
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(output, "${skin.name.lowercase()}-${if (dark) "dark" else "light"}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithText("生成单品展示图").performClick()
        compose.onNodeWithText("确认生成单品展示图？").assertIsDisplayed()
        compose.waitForIdle()
        instrumentation.uiAutomation.takeScreenshot().also { bitmap ->
            File(output, "confirmation.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
        assertEquals(0, calls)
        compose.onNodeWithText("确认并加入任务").performClick()
        compose.waitUntil(10_000) { calls == 1 }
        generated.complete("accessory-task")
        compose.waitUntil(10_000) { !vm.uiState.value.isSubmitting }
        assertEquals(photos.first(), submitted!!.imagePath)
        assertEquals(com.lolita.app.domain.model.MannequinSubject(1, "KC", com.lolita.app.domain.model.MannequinPresentation.ACCESSORY, com.lolita.app.domain.model.CategorySemantic.KC), submitted!!.subject)
    }

    @Test fun categoryRenameWhileConfirmationIsOpenRequiresNewConfirmation() {
        show(); configure()
        compose.onNodeWithText("生成正面人台图").performClick()
        compose.runOnIdle { categoryRows.value = listOf(Category(id = 1, name = "SK", categorySemantic = com.lolita.app.domain.model.CategorySemantic.SK)) }
        compose.waitUntil(10_000) { vm.uiState.value.category?.name == "SK" }
        compose.onNodeWithText("确认并加入任务").performClick()
        compose.waitUntil(10_000) { calls > 0 || vm.uiState.value.error != null }
        compose.runOnIdle { assertEquals(0, calls) }
    }

    @Test fun selectedPhotoChangedWhileConfirmationIsOpenRequiresNewConfirmation() {
        show(); configure()
        compose.onNodeWithText("生成正面人台图").performClick()
        compose.runOnIdle { vm.selectImage(photos[1]) }
        compose.onNodeWithText("确认并加入任务").performClick()
        compose.waitUntil(10_000) { calls > 0 || vm.uiState.value.error != null }
        compose.runOnIdle { assertEquals(0, calls) }
    }

    @Test fun instructionsChangedWhileConfirmationIsOpenRequireNewConfirmation() {
        show(); configure()
        compose.onNodeWithText("生成正面人台图").performClick()
        compose.runOnIdle { vm.updateInstructions("保留原裙摆") }
        compose.onNodeWithText("确认并加入任务").performClick()
        compose.waitUntil(10_000) { calls > 0 || vm.uiState.value.error != null }
        compose.runOnIdle { assertEquals(0, calls) }
    }

    @Test fun seedreamSelectionUsesMaskedKeyCorrectRecipientAndSevenSkinControls() {
        show()
        scroll("即梦 5.0 Pro")
        compose.onNodeWithTag("imageService-SEEDREAM").performClick()
        compose.waitUntil(10_000) { vm.uiState.value.provider == com.lolita.app.domain.model.ImageGenerationProvider.SEEDREAM && !vm.uiState.value.busy }
        compose.onNodeWithTag("imageService-SEEDREAM").assertIsSelected()
        compose.onNodeWithTag("imageService-OPENAI").assertIsNotSelected()
        compose.runOnIdle { vm.saveKey("private-seed-test") }
        compose.waitUntil(10_000) { vm.uiState.value.hasKey && !vm.uiState.value.busy }
        compose.onNodeWithText("上传所选原图、固定模板及补充说明至 火山方舟，共2张图片；API 单独计费。").assertIsDisplayed()
        val output = File(instrumentation.targetContext.filesDir, "seedream-service-contact-sheets").apply { mkdirs() }
        for (skin in SkinType.entries) for (dark in listOf(false, true)) {
            compose.runOnIdle { presentation.value = Presentation(skin = skin, dark = dark) }
            scroll("即梦 5.0 Pro")
            compose.onNodeWithTag("imageService-SEEDREAM").assertIsDisplayed().assertIsSelected()
            compose.onNodeWithTag("imageService-OPENAI").assertIsDisplayed().assertIsNotSelected()
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(output, "${skin.name.lowercase()}-${if (dark) "dark" else "light"}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun missingKeyAndServiceActionBothOpenUnifiedConfigurationWithoutSending() {
        show()
        compose.onNodeWithText("配置服务密钥").performClick()
        scroll("API 配置"); compose.onNodeWithText("API 配置").performClick()
        assertEquals(2, configured)
        assertNull(key)
        assertEquals(0, calls)
    }
    @Test fun selectionAndInstructionsSubmitOnlyAfterPaidConfirmationAndBackRemainsAvailable() {
        show(); configure()
        scroll("参考图片 2"); compose.onNodeWithText("参考图片 2").performClick()
        compose.waitUntil(10_000) { vm.uiState.value.selectedImage == photos[1] }
        scroll("补充说明（可选）"); compose.onNodeWithText("补充说明（可选）").performTextInput("排除独立手袖")
        compose.onNodeWithText("生成正面人台图").performClick()
        assertEquals(0, calls)
        compose.onNodeWithText("确认并加入任务").performClick()
        compose.waitUntil(10_000) { vm.uiState.value.isSubmitting }
        generated.complete("task-uuid")
        compose.waitUntil(10_000) { vm.uiState.value.latestTaskId == "task-uuid" && !vm.uiState.value.isSubmitting }
        assertEquals(1, calls)
        scroll("最近提交的任务")
        compose.onNodeWithText("查看任务").assertHasClickAction()
        compose.onNodeWithContentDescription("返回").performClick()
        assertEquals(1, navigated)
    }
    @Test fun legacyPreviewCanBeZoomedAndExplicitlySavedWithoutNetworkOrLeavingCleanup() {
        instrumentation.runOnMainSync {
            vm = MannequinViewModel(SavedStateHandle(mapOf("itemId" to 11L, "mannequinPreview" to photos[2], "mannequinPreviewItem" to 11L)),
                item = flowOf(Item(id = 11, name = "旧版裙子", brandId = 1, categoryId = 1, description = "", status = ItemStatus.OWNED, imageUrls = photos.take(2))),
                categories = flowOf(listOf(Category(id = 1, name = "OP", categorySemantic = com.lolita.app.domain.model.CategorySemantic.OP))), readService = { com.lolita.app.data.tryon.ImageServiceStatus(provider, false) },
                selectService = {}, serviceChanges = flowOf(0), saveApiKey = { _, _ -> }, clearApiKey = {}, tasks = flowOf(emptyList()),
                validateLegacy = { true }, appendLegacy = { _, path -> appended = path }, discardLegacy = { discarded = it }, submitTask = { _, _ -> error("legacy must not pay") })
            store.put("legacy", vm)
        }
        compose.waitUntil(10_000) { !vm.uiState.value.isLoading }
        show(); scroll("添加到物品相册")
        compose.onNodeWithContentDescription("放大旧版预览").performClick()
        compose.onNodeWithContentDescription("关闭图片预览").performClick()
        compose.onNodeWithText("添加到物品相册").performClick()
        compose.waitUntil(10_000) { appended == photos[2] && !vm.uiState.value.savingLegacy }
        assertNull(discarded); assertEquals(0, calls)
    }

    @Test fun sevenSkinsLightDarkLargeFontKeepTaskAndSubmitControlsReachable() {
        show(); configure(); generated.complete("task-uuid")
        compose.onNodeWithText("生成正面人台图").performClick(); compose.onNodeWithText("确认并加入任务").performClick()
        compose.waitUntil(10_000) { vm.uiState.value.latestTaskId != null && !vm.uiState.value.isSubmitting }
        val output = File(instrumentation.targetContext.filesDir, "mannequin-ui-contact-sheets").apply { mkdirs() }
        for (skin in SkinType.entries) for (dark in listOf(false, true)) {
            compose.runOnIdle { presentation.value = Presentation(skin = skin, dark = dark) }
            scroll("最近提交的任务")
            compose.onNodeWithText("查看任务").assertIsDisplayed().assertHasClickAction()
            compose.onNodeWithContentDescription("任务中心").assertIsDisplayed().assertHasClickAction()
            compose.onNodeWithText("生成正面人台图").assertIsDisplayed().assertIsEnabled()
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(output, "${skin.name.lowercase()}-${if (dark) "dark" else "light"}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    private data class Presentation(val width: Int = 320, val scale: Float = 2f, val skin: SkinType = SkinType.DEFAULT, val dark: Boolean = false)
}
