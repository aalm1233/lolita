package com.lolita.app.ui.screen.tasks

import android.content.ContextWrapper
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
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.generation.ImageTaskCoordinator
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.domain.model.*
import com.lolita.app.ui.theme.*
import com.lolita.app.ui.theme.skin.animation.SkinAnimationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ImageTaskScreenTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var db: LolitaDatabase
    private lateinit var directory: File
    private lateinit var preview: String
    private lateinit var coordinator: ImageTaskCoordinator
    private lateinit var model: ImageTaskViewModel
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = ViewModelStore()
    private val presentation = mutableStateOf(Presentation())
    private val detail = mutableStateOf(true)
    private var retried = 0
    private var defaultFailure = false
    private var defaultModelId: Long? = null
    private var personalConfirmCalls = 0
    private val pendingId = UUID.randomUUID().toString()
    private val failedId = UUID.randomUUID().toString()
    private val runningId = UUID.randomUUID().toString()

    @Before fun setup() = runBlocking {
        val target = instrumentation.targetContext
        directory = File(target.cacheDir, "task-ui-${UUID.randomUUID()}").apply { mkdirs() }
        preview = File(directory, "pending.png").also { file ->
            Bitmap.createBitmap(160, 220, Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.eraseColor(android.graphics.Color.rgb(183, 144, 168))
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            }
        }.absolutePath
        db = Room.inMemoryDatabaseBuilder(target, LolitaDatabase::class.java).build()
        db.brandDao().insertBrand(Brand(id = 1, name = "独立品牌"))
        db.categoryDao().insertCategory(Category(id = 1, name = "OP"))
        db.itemDao().insertItem(Item(id = 11, name = "月光印花连衣裙", brandId = 1, categoryId = 1, description = "保留原字段", status = ItemStatus.OWNED, imageUrls = listOf("/original.png")))
        coordinator = newCoordinator()
        coordinator.awaitStartup()
        db.imageTaskDao().insert(task(pendingId, ImageTaskStatus.AWAITING_CONFIRMATION).copy(resultImagePath = preview))
        db.imageTaskDao().insert(task(failedId, ImageTaskStatus.FAILED).copy(error = "生成未完成，请核对当前服饰与服务配置", requestSent = true))
        db.imageTaskDao().insert(task(runningId, ImageTaskStatus.GENERATING).copy(sourceImagePaths = listOf("/original.png"), requestSent = true))
        model = withContext(Dispatchers.Main) {
            ImageTaskViewModel(db.imageTaskDao().observe(), db.itemDao().getAllItems(), db.tryOnDao().observe(),
                coordinator::cancel, coordinator::confirmMannequin, coordinator::discard, coordinator::delete,
                personalModels = db.personalModelDao().observe(), setDefaultPersonalModel = { if (defaultFailure) error("受控偏好失败") else defaultModelId = it },
                confirmPersonalTask = { personalConfirmCalls++; coordinator.confirmPersonalModel(it) }).also { store.put("tasks", it) }
        }
        withTimeout(10_000) { model.uiState.first { !it.isLoading && it.tasks.size == 3 } }
        Unit
    }
    private fun newCoordinator() = ImageTaskCoordinator(db, appScope, { error("tests never start paid service") }, { error("no generation") }, { path ->
        // This independent test store contains no shared files. Confirmed paths remain protected.
        if (db.itemDao().getAllItemsList().none { path in it.imageUrls } && db.imageTaskDao().getAll().none { path in it.protectedImagePaths() }) File(path).delete()
        Unit
    }, { File(it).isFile })
    private fun task(id: String, status: ImageTaskStatus) = ImageTask(id, ImageTaskKind.MANNEQUIN, ImageGenerationProvider.SEEDREAM,
        "月光印花连衣裙 · 人台图", ImageTaskRequest(listOf(ImageTaskGarment(11, "月光印花连衣裙", TryOnRole.MAIN))).encode(), emptyList(), status = status)
    @After fun cleanup() {
        instrumentation.runOnMainSync { store.clear() }
        appScope.cancel(); db.close(); directory.deleteRecursively()
    }
    private fun show(id: String = pendingId) {
        compose.setContent {
            val display = presentation.value
            val config = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = 320
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (display.dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config, LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                LolitaTheme(skinType = display.skin, darkTheme = display.dark) {
                    val skin = LolitaSkin.current
                    val still = remember(skin) { skin.copy(topBarBlurEnabled = false, navBarBlurEnabled = false,
                        animations = object : SkinAnimationProvider by skin.animations { override val ambientAnimation = skin.animations.ambientAnimation.copy(backgroundEnabled = false) }) }
                    CompositionLocalProvider(LocalLolitaSkin provides still) {
                        Surface(color = MaterialTheme.colorScheme.background) { Box(Modifier.size(320.dp, 640.dp)) {
                            if (detail.value) ImageTaskDetailScreen(id, {}, { retried++ }, model)
                            else ImageTaskScreen({}, {}, model)
                        } }
                    }
                }
            }
        }
    }
    private fun scrollTo(text: String) {
        compose.onNodeWithTag(if (detail.value) "imageTaskDetail" else "imageTasks").performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performScrollTo()
    }

    @Test fun tryOnTaskDetailsRetainTheOriginalWearingDescription() = runBlocking {
        val description = "BNT 戴靠前，系带在下巴下；裙撑留在裙内。"
        db.imageTaskDao().update(task(failedId, ImageTaskStatus.FAILED).copy(kind = ImageTaskKind.TRY_ON,
            requestJson = ImageTaskRequest(listOf(ImageTaskGarment(11, "月光裙", TryOnRole.MAIN)), instructions = description).encode()))
        withTimeout(10_000) { model.uiState.first { it.tasks.any { row -> row.id == failedId && row.kind == ImageTaskKind.TRY_ON } } }
        show(failedId)
        scrollTo("穿戴要求：$description")
        compose.onNodeWithText("穿戴要求：$description").assertIsDisplayed()
        Unit
    }
    @Test fun personalSaveRetainsAcceptedModelWhenDefaultFailsAndPreferenceCanBeRetried() = runBlocking {
        val request = PersonalModelRequest("我的模特", PersonalModelMode.PARAMETERS, BodyMeasurements(165.0, 52.0))
        db.imageTaskDao().update(task(pendingId, ImageTaskStatus.AWAITING_CONFIRMATION).copy(
            kind = ImageTaskKind.PERSONAL_MODEL, title = "我的模特 · 个人模特", resultImagePath = preview,
            requestJson = ImageTaskRequest(emptyList(), version = 2, personalModel = request).encode()))
        withTimeout(10_000) { model.uiState.first { it.tasks.any { row -> row.id == pendingId && row.kind == ImageTaskKind.PERSONAL_MODEL } } }
        defaultFailure = true
        show()
        scrollTo("保存个人模特")
        compose.onNode(isToggleable()).assertIsOn()
        compose.onNodeWithText("保存个人模特").performScrollTo().performClick()
        compose.onNodeWithText("确认保存模特").performClick()
        withTimeout(10_000) { model.uiState.first { pendingId in it.defaultSaveFailed } }
        assertEquals(1, db.personalModelDao().getAll().size)
        assertEquals(ImageTaskStatus.COMPLETED, db.imageTaskDao().get(pendingId)!!.status)
        assertTrue(File(preview).isFile)
        defaultFailure = false
        scrollTo("重试设为默认")
        compose.onNodeWithText("重试设为默认").performClick()
        withTimeout(10_000) { model.uiState.first { pendingId !in it.defaultSaveFailed && it.mutating.isEmpty() } }
        assertEquals(db.personalModelDao().getAll().single().id, defaultModelId)
        assertEquals(1, db.personalModelDao().getAll().size)
        assertEquals(1, personalConfirmCalls)
        db.personalModelDao().delete(defaultModelId!!)
        withTimeout(10_000) { model.uiState.first { it.personalModels.isEmpty() } }
        assertNull(model.uiState.value.resultPath(db.imageTaskDao().get(pendingId)!!))
    }

    @Test fun personalPreviewAdjustKeepsOriginalAndCannotAppendToGarment() = runBlocking {
        val request = PersonalModelRequest("我的模特", PersonalModelMode.PARAMETERS, BodyMeasurements(165.0, 52.0))
        db.imageTaskDao().update(task(pendingId, ImageTaskStatus.AWAITING_CONFIRMATION).copy(
            kind = ImageTaskKind.PERSONAL_MODEL, title = "我的模特 · 个人模特", resultImagePath = preview,
            requestJson = ImageTaskRequest(emptyList(), version = 2, personalModel = request).encode()))
        withTimeout(10_000) { model.uiState.first { it.tasks.any { row -> row.id == pendingId && row.kind == ImageTaskKind.PERSONAL_MODEL } } }
        show()
        scrollTo("调整后另存")
        compose.onNodeWithText("调整后另存").performClick()
        assertEquals(1, retried)
        assertFalse(model.uiState.value.canAppend(db.imageTaskDao().get(pendingId)!!))
        assertEquals(ImageTaskStatus.AWAITING_CONFIRMATION, db.imageTaskDao().get(pendingId)!!.status)
        assertTrue(File(preview).isFile)
    }

    @Test fun backgroundAdviceCanBeOpenedAndDismissedWithoutStartingTask() {
        detail.value = false; show()
        scrollTo("后台与息屏")
        compose.onNodeWithText("后台与息屏").performClick()
        compose.onNodeWithText("电池设置").assertExists()
        compose.onNodeWithText("应用设置").assertExists()
        compose.onNodeWithText("vivo 手机：电池 → 后台耗电管理 → 允许高后台耗电。").assertExists()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("电池设置").assertDoesNotExist()
    }

    @Test fun pendingPreviewSurvivesRestartAndExplicitAppendMergesLatestItemOnce() = runBlocking {
        val restarted = newCoordinator(); restarted.awaitStartup()
        assertEquals(ImageTaskStatus.AWAITING_CONFIRMATION, db.imageTaskDao().get(pendingId)!!.status)
        show()
        compose.onNodeWithTag("imageTaskDetail").performScrollToNode(hasContentDescription("放大生成结果"))
        compose.onNodeWithContentDescription("放大生成结果").performClick()
        compose.onNodeWithContentDescription("关闭图片预览").assertIsDisplayed().performClick()
        val latest = db.itemDao().getItemById(11)!!.copy(name = "已编辑的裙名", description = "最新备注", imageUrls = listOf("/original.png", "/added.png"))
        db.itemDao().updateItem(latest)
        scrollTo("添加到原物品图片"); compose.onNodeWithText("添加到原物品图片").performClick()
        compose.onNodeWithText("确认添加到相册").performClick()
        withTimeout(10_000) { db.imageTaskDao().observe(pendingId).first { it?.status == ImageTaskStatus.COMPLETED } }
        val saved = db.itemDao().getItemById(11)!!
        assertEquals("已编辑的裙名", saved.name); assertEquals("最新备注", saved.description)
        assertEquals(listOf("/original.png", "/added.png", preview), saved.imageUrls)
        assertTrue(File(preview).isFile)
        // Repeated/stale confirmation is idempotent at the mutation boundary.
        restarted.confirmMannequin(pendingId)
        assertEquals(3, db.itemDao().getItemById(11)!!.imageUrls.size)
    }
    @Test fun deletedOriginalCannotAppendButPreviewCanStillBeViewedAndDiscarded() = runBlocking {
        db.itemDao().deleteItem(db.itemDao().getItemById(11)!!)
        show(); scrollTo("添加到原物品图片")
        compose.onNodeWithText("添加到原物品图片").assertIsNotEnabled()
        compose.onNodeWithText("舍弃预览").performScrollTo().performClick()
        compose.onNodeWithText("确认舍弃").performClick()
        withTimeout(10_000) { db.imageTaskDao().observe(pendingId).first { it?.status == ImageTaskStatus.DISCARDED } }
        withTimeout(10_000) { while (File(preview).exists()) delay(20) }
    }
    @Test fun failedRetryOnlyNavigatesToReviewAndDoesNotEnqueue() = runBlocking {
        show(failedId); scrollTo("核对后重新发起")
        compose.onNodeWithText("核对后重新发起").performClick()
        assertEquals(1, retried); assertEquals(3, db.imageTaskDao().getAll().size)
    }
    @Test fun activeCancellationRequiresFeeWarningAndThenShowsCancelled() = runBlocking {
        show(runningId); scrollTo("取消任务"); compose.onNodeWithText("取消任务").performClick()
        compose.onNodeWithText("停止本地等待可能仍会计费，应用不会自动重试。").assertIsDisplayed()
        compose.onNodeWithText("确认停止等待").performClick()
        withTimeout(10_000) { db.imageTaskDao().observe(runningId).first { it?.status == ImageTaskStatus.CANCELLED } }
        assertTrue(db.imageTaskDao().get(runningId)!!.requestSent)
    }
    @Test fun filtersShowPendingRunningAndFinishedWithoutChangingRecords() {
        detail.value = false; show()
        compose.onNodeWithContentDescription("任务筛选：待确认").performClick(); assertEquals(listOf(pendingId), model.uiState.value.visibleTasks.map { it.id })
        compose.onNodeWithText("进行中").performClick(); assertEquals(listOf(runningId), model.uiState.value.visibleTasks.map { it.id })
        compose.onNodeWithText("已结束").performClick(); assertEquals(listOf(failedId), model.uiState.value.visibleTasks.map { it.id })
    }
    @Test fun sevenSkinsPersonalPreviewActionsFit320dpDoubleFontAndExportActualScreens() = runBlocking {
        val request = PersonalModelRequest("我的模特", PersonalModelMode.PARAMETERS, BodyMeasurements(165.0, 52.0))
        db.imageTaskDao().update(task(pendingId, ImageTaskStatus.AWAITING_CONFIRMATION).copy(
            kind = ImageTaskKind.PERSONAL_MODEL, title = "我的模特 · 个人模特", resultImagePath = preview,
            requestJson = ImageTaskRequest(emptyList(), version = 2, personalModel = request).encode()))
        withTimeout(10_000) { model.uiState.first { it.tasks.any { row -> row.id == pendingId && row.kind == ImageTaskKind.PERSONAL_MODEL } } }
        show()
        val output = File(instrumentation.targetContext.filesDir, "personal-task-ui-contact-sheets").apply { mkdirs() }
        for (skin in SkinType.entries) for (dark in listOf(false, true)) {
            compose.runOnIdle { presentation.value = Presentation(skin, dark) }
            scrollTo("保存个人模特")
            compose.onNodeWithText("保存个人模特").assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithText("调整后另存").performScrollTo().assertIsDisplayed().assertHasClickAction()
            save(output, skin, dark, "personal-actions")
            compose.onNodeWithTag("imageTaskDetail").performScrollToNode(hasContentDescription("放大生成结果"))
            compose.onNodeWithContentDescription("放大生成结果").assertHasClickAction()
            save(output, skin, dark, "personal-preview")
        }
    }

    @Test fun sevenSkinsLightDark320dpDoubleFontKeepDetailActionsAndFiltersReachable() {
        show()
        val output = File(instrumentation.targetContext.filesDir, "image-task-ui-contact-sheets").apply { mkdirs() }
        for (skin in SkinType.entries) for (dark in listOf(false, true)) {
            compose.runOnIdle { presentation.value = Presentation(skin, dark); detail.value = true }
            scrollTo("添加到原物品图片")
            compose.onNodeWithText("添加到原物品图片").assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithText("舍弃预览").performScrollTo().assertIsDisplayed().assertHasClickAction()
            save(output, skin, dark, "detail-actions")
            compose.onNodeWithTag("imageTaskDetail").performScrollToNode(hasContentDescription("放大生成结果"))
            compose.onNodeWithContentDescription("放大生成结果").assertHasClickAction()
            save(output, skin, dark, "detail-preview")
            compose.runOnIdle { detail.value = false; model.filter(ImageTaskFilter.ALL) }
            compose.onNodeWithText("全部").assertIsDisplayed()
            compose.onNodeWithContentDescription("任务筛选：待确认").assertHasClickAction()
            compose.onNodeWithText("已结束").performScrollTo().assertIsDisplayed()
            save(output, skin, dark, "center")
        }
    }
    private fun save(output: File, skin: SkinType, dark: Boolean, part: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(output, "${skin.name.lowercase()}-${if (dark) "dark" else "light"}-$part.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private data class Presentation(val skin: SkinType = SkinType.DEFAULT, val dark: Boolean = false)
}
