package com.lolita.app.ui.screen.copy

import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.entity.ImageTask
import com.lolita.app.data.repository.AiConfigurationSnapshot
import com.lolita.app.domain.model.*
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.screen.settings.*
import com.lolita.app.ui.screen.tasks.ImageTaskResultCard
import com.lolita.app.ui.theme.*
import com.lolita.app.ui.theme.skin.component.SkinArtBackground
import com.lolita.app.ui.theme.skin.icon.IconKey
import kotlinx.coroutines.flow.flowOf
import org.junit.*
import org.junit.Assert.*
import java.io.File

class CreativeExperienceScreenTest {
    @get:Rule val compose = createComposeRule()
    private val skin = mutableStateOf(SkinType.DEFAULT)
    private val dark = mutableStateOf(false)
    private val page = mutableStateOf("profile")
    private val money = mutableStateOf(true)
    private val large = mutableStateOf(false)
    private val busy = mutableStateOf(false)
    private var action = ""
    private val store = ViewModelStore()
    private lateinit var laboratory: LaboratoryViewModel
    private val result = BackupExportResult(Uri.parse("content://test/backup"), "lolita_backup_test.zip", BackupExportFormat.ZIP)
    private val task = ImageTask("00000000-0000-0000-0000-000000000001", ImageTaskKind.MANNEQUIN,
        ImageGenerationProvider.SEEDREAM, "裙装展示图", "{}", emptyList(), status = ImageTaskStatus.AWAITING_CONFIRMATION)

    @Before fun prepare() { InstrumentationRegistry.getInstrumentation().runOnMainSync {
        laboratory = LaboratoryViewModel(flowOf(emptyList()), flowOf(emptyList()), flowOf(AiConfigurationSnapshot()))
        store.put("laboratory", laboratory)
    } }
    @After fun close() { InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() } }
    private fun show() {
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config,
                LocalDensity provides Density(LocalDensity.current.density, if (large.value) 2f else LocalDensity.current.fontScale)) {
                LolitaTheme(skin.value, dark.value) {
                    Surface(Modifier.fillMaxSize()) { Box {
                        SkinArtBackground()
                        key(page.value, skin.value, dark.value) {
                            when (page.value) {
                                "profile" -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                                    ProfileSection(SettingsUiState(nickname = "我的衣橱", totalItems = 3, totalSpent = 123456789.12), money.value, {}, {})
                                }
                                "backup" -> BackupRestoreContent(BackupRestoreUiState(isExporting = busy.value, lastExport = result),
                                    { action = "zip" }, { action = "json" }, { action = "csv" }, { action = "restore" }, { action = it.uri.toString() })
                                "empty" -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                                    SkinEmptyState(IconKey.Home, "从心爱之物开始整理", "照片、名称，先记下来就好",
                                        actionLabel = "添加第一件物品", onAction = { action = "add" }, illustration = { WardrobeEmptyIllustration() })
                                }
                                "task" -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                                    ImageTaskResultCard(task, null) { action = "review" }
                                    ImageTaskResultCard(task.copy(id = "failed", status = ImageTaskStatus.INTERRUPTED), null) { action = "reason" }
                                    ImageTaskResultCard(task.copy(id = "complete", status = ImageTaskStatus.COMPLETED), "/no-such-result.png") { action = "missing" }
                                }
                                "laboratory" -> LaboratoryScreen({}, { action = "batch" }, { action = "tryon" }, {}, {}, {}, {}, {}, laboratory)
                            }
                        }
                    } }
                }
            }
        }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "creative-experience-native").apply { mkdirs() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    @Test fun profileMoneyChangesLiveAndPreservesTwoDecimalPlaces() {
        show()
        compose.onNodeWithText("当前订单实付 ¥123,456,789.12").assertIsDisplayed()
        compose.runOnIdle { money.value = false }
        compose.onAllNodes(hasText("当前订单实付", substring = true), useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithText("服饰 3 件 | 套装 0 套").assertIsDisplayed()
        compose.runOnIdle { money.value = true; large.value = true }
        compose.onNodeWithText("当前订单实付 ¥123,456,789.12").assertIsDisplayed()
        capture("profile-large-money")
    }
    @Test fun backupKeepsRealResultAndWarningsAndDisablesConflictingActions() {
        page.value = "backup"; show()
        compose.onNodeWithText("lolita_backup_test.zip").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("查看文件").performScrollTo().performClick()
        assertEquals(result.uri.toString(), action)
        compose.onNodeWithText("不含图片").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("仅供查看，不能恢复").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("恢复成功后，当前记录将替换为备份内容。请先导出完整备份。").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("选择备份文件").performScrollTo().performClick(); assertEquals("restore", action)
        compose.runOnIdle { busy.value = true }
        for (label in listOf("正在导出…", "导出数据备份", "导出表格", "选择备份文件", "查看文件")) {
            compose.onNodeWithText(label).performScrollTo().assertIsNotEnabled()
        }
    }
    @Test fun emptyStateAndTaskActionsHaveExplicitLocalDestinations() {
        page.value = "empty"; show()
        compose.onNodeWithText("添加第一件物品").performClick(); assertEquals("add", action)
        compose.runOnIdle { page.value = "task" }
        compose.onNodeWithText("核对结果").performScrollTo().performClick(); assertEquals("review", action)
        compose.onNodeWithText("查看原因").performScrollTo().performClick(); assertEquals("reason", action)
        compose.onNodeWithText("结果图片不可用，请进入任务核对").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("查看结果").assertDoesNotExist()
        compose.onNodeWithText("查看任务").performScrollTo().performClick(); assertEquals("missing", action)
    }
    @Test fun changedPagesUseSevenNativeSkinsInLightAndDark() {
        show()
        for (mode in listOf("profile", "backup", "empty", "task", "laboratory")) {
            for (type in SkinType.entries) for (night in listOf(false, true)) {
                compose.runOnIdle { page.value = mode; skin.value = type; dark.value = night }
                if (mode == "backup") compose.onNodeWithText("完整备份 · ZIP").performScrollTo().assertIsDisplayed()
                if (mode == "laboratory") compose.onNodeWithTag("laboratoryBatch").assertIsDisplayed()
                capture("$mode-${type.name.lowercase()}-${if (night) "dark" else "light"}")
            }
        }
    }
}
