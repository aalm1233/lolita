package com.lolita.app.ui.screen.copy

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import com.lolita.app.ui.screen.calendar.YearHeader
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.file.BackupPreview
import com.lolita.app.data.local.entity.Location
import com.lolita.app.ui.screen.item.LocationListContent
import com.lolita.app.ui.screen.settings.AttributeManageScreen
import com.lolita.app.ui.screen.settings.BackupRestoreConfirmation
import com.lolita.app.ui.screen.stats.WishlistAnalysisBody
import com.lolita.app.ui.screen.stats.WishlistAnalysisUiState
import com.lolita.app.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real presentation branches: no file restore, database mutation or paid request. */
class UiCopyReviewScreenTest {
    @get:Rule val compose = createComposeRule()
    private val skin = mutableStateOf(SkinType.DEFAULT)
    private val dark = mutableStateOf(false)
    private val page = mutableStateOf("room")
    private val money = mutableStateOf(true)
    private val compact = mutableStateOf(false)
    private var navigated = ""
    private var confirmed = 0
    private val preview = BackupPreview(brandCount = 2, categoryCount = 1, coordinateCount = 1,
        itemCount = 3, priceCount = 2, paymentCount = 1, outfitLogCount = 1,
        imageCount = 4, missingImageCount = 1, historicalOwnedCount = 1,
        needsReviewCount = 1, backupDate = 1792627200000, backupVersion = "13")

    private fun show() {
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config, LocalDensity provides Density(LocalDensity.current.density, if (compact.value) 2f else LocalDensity.current.fontScale)) {
                LolitaTheme(skin.value, dark.value) {
                    Surface(Modifier.fillMaxSize()) {
                        key(page.value, skin.value, dark.value) {
                            when (page.value) {
                                "calendar" -> Box(if (compact.value) Modifier.width(320.dp) else Modifier) {
                                    YearHeader(2026, 123456789.12, 987654321.98, 0.0, { navigated = "previous" }, { navigated = "next" })
                                }
                                "room" -> LocationListContent(listOf(Location(1, "我的房间", "用户备注不被删除")),
                                    mapOf(1L to 2), emptyMap(), 0) { navigated = "room-$it" }
                                "backup" -> BackupRestoreConfirmation(preview, { confirmed++ }, { navigated = "dismiss" })
                                "wishlist" -> WishlistAnalysisBody(WishlistAnalysisUiState(totalBudget = 1280.5,
                                    ownedCount = 2, wishedCount = 1, isLoading = false, showTotalPrice = money.value))
                                "attributes" -> AttributeManageScreen({}, { navigated = "brand" }, { navigated = "type" },
                                    { navigated = "style" }, { navigated = "season" }, { navigated = "room" }, { navigated = "source" })
                            }
                        }
                    }
                }
            }
        }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val node = if (page.value == "backup") compose.onNode(isDialog()) else compose.onRoot()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "ui-copy-native").apply { mkdirs() }
        node.captureToImage().asAndroidBitmap().let { bitmap ->
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    @Test fun recordsWithoutPhotosAreNeverReportedAsAnEmptyRoom() {
        show()
        compose.onNodeWithText("2 条记录").assertIsDisplayed()
        compose.onNodeWithText("暂无物品").assertDoesNotExist()
        compose.onNodeWithText("暂无照片").assertDoesNotExist()
        compose.onNodeWithText("用户备注不被删除").assertDoesNotExist()
        compose.onNodeWithText("我的房间").performClick()
        assertEquals("room-1", navigated)
    }
    @Test fun restoreConsequencesStayVisibleWhileCompleteCountsRemainExpandable() {
        page.value = "backup"; show()
        compose.onNodeWithText("恢复成功后，当前记录将替换为备份内容。").assertIsDisplayed()
        compose.onNodeWithText("缺少 1 张图片，请使用含图片的 ZIP 备份。").assertIsDisplayed()
        compose.onNodeWithText("购买资料待核对 1 笔。").assertIsDisplayed()
        compose.onNodeWithText("备份格式：13").assertDoesNotExist()
        compose.onNodeWithText("备份明细").performScrollTo().performClick()
        compose.onNodeWithText("备份格式：13").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("收起备份明细").performScrollTo().performClick()
        compose.onNodeWithText("备份格式：13").assertDoesNotExist()
        assertEquals(0, confirmed)
        compose.onNodeWithText("取消").performClick()
        assertEquals("dismiss", navigated)
    }
    @Test fun wishLabelsMatchReferencePriceAndOwnershipAndRespectLiveMoneyPrivacy() {
        page.value = "wishlist"; show()
        compose.onNodeWithText("心愿参考总价").assertIsDisplayed()
        compose.onNodeWithText("已拥有占比 (2/3)").assertIsDisplayed()
        compose.onNodeWithText("¥1280.50").assertIsDisplayed()
        compose.onNodeWithText("统计口径").performClick()
        compose.onNodeWithText("参考总价仅合计有唯一价格的独立计价心愿物品；未录价不代表免费。").assertIsDisplayed()
        compose.runOnIdle { money.value = false }
        compose.onNodeWithText("¥1280.50").assertDoesNotExist()
        compose.onNodeWithText("¥***").assertIsDisplayed()
    }
    @Test fun calendarYearAmountsRemainCompleteAtSmallWidthAndDoubleFont() {
        page.value = "calendar"; compact.value = true; show()
        capture("calendar-small-double-font")
        for (label in listOf("已付 ¥123,456,789", "待付 ¥987,654,322")) {
            val node = compose.onNodeWithText(label).assertIsDisplayed().fetchSemanticsNode()
            val layouts = mutableListOf<TextLayoutResult>()
            assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts))
            assertTrue(layouts.isNotEmpty())
            val layout = layouts.single()
            // FlowRow first measures at its full width, then places the narrower text.
            // Check actual glyph bounds and complete content rather than paragraph's constraint width.
            assertEquals(label.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
            for (line in 0 until layout.lineCount) {
                assertFalse(layout.isLineEllipsized(line))
                assertTrue(layout.getLineLeft(line) >= -1f)
                assertTrue(layout.getLineRight(line) <= layout.size.width + 1f)
                assertTrue(layout.getLineBottom(line) <= layout.size.height + 1f)
            }
        }
        compose.onNodeWithContentDescription("上一年").performClick()
        assertEquals("previous", navigated)
        compose.onNodeWithContentDescription("下一年").performClick()
        assertEquals("next", navigated)
        capture("calendar-small-double-font")
    }
    @Test fun realSmallRestoreKeepsWarningsReadableAndConfirmationReachable() {
        val config = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration
        org.junit.Assume.assumeTrue(config.screenWidthDp <= 320 && config.fontScale >= 1.99f)
        page.value = "backup"; show()
        compose.onNodeWithText("恢复成功后，当前记录将替换为备份内容。").performScrollTo().assertIsDisplayed()
        capture("backup-small-replacement")
        compose.onNodeWithText("缺少 1 张图片，请使用含图片的 ZIP 备份。").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("购买资料待核对 1 笔。").performScrollTo().assertIsDisplayed()
        capture("backup-small-warnings")
        compose.onNodeWithText("确认恢复").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("备份明细").performScrollTo().performClick()
        compose.onNodeWithText("备份格式：13").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("取消").assertIsDisplayed().performClick()
        assertEquals(0, confirmed)
        assertEquals("dismiss", navigated)
    }
    @Test fun actualCopyHierarchyAcrossSevenLightAndDarkSkins() {
        show()
        for (current in listOf("room", "attributes", "wishlist", "backup", "calendar")) {
            for (night in listOf(false, true)) for (type in SkinType.entries) {
                compose.runOnIdle { page.value = current; skin.value = type; dark.value = night }
                capture("$current-${type.name.lowercase()}-${if (night) "dark" else "light"}")
            }
        }
        compose.runOnIdle { page.value = "attributes" }
        compose.onNodeWithText("类型").performClick()
        assertEquals("type", navigated)
        compose.onNodeWithText("管理服饰类型").assertDoesNotExist()
    }
}
