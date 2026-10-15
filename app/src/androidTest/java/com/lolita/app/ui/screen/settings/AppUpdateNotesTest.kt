package com.lolita.app.ui.screen.settings

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.domain.model.AppRelease
import com.lolita.app.domain.model.ReleaseUpdate
import com.lolita.app.ui.theme.LolitaTheme
import com.lolita.app.ui.theme.SkinType
import java.io.File
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual dialog/host; never downloads an APK or uses the network or user preferences. */
@RunWith(AndroidJUnit4::class)
class AppUpdateNotesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun manualCurrentVersionCheckStillShowsLatestReleaseNotes() {
        val store = ViewModelStore()
        val vm = compose.runOnUiThread {
            AppUpdateViewModel("2.51.0", { ReleaseUpdate(release("生成后可离开页面，在任务中心查看进度。"), 0) },
                Dispatchers.Main.immediate, { true }).also { store.put("notes", it) }
        }
        try {
            compose.setContent { LolitaTheme { AppUpdateHost(vm) } }
            compose.waitUntil(5_000) { !vm.state.value.isChecking }
            compose.runOnIdle { vm.checkForUpdate() }
            compose.onNodeWithText("已是最新版本").assertIsDisplayed()
            compose.onNodeWithText("本版更新").assertIsDisplayed()
            compose.onNodeWithText("生成后可离开页面，在任务中心查看进度。").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("知道了").assertIsDisplayed().performClick()
            compose.onAllNodes(isDialog()).assertCountEquals(0)
        } finally { compose.runOnUiThread { store.clear() } }
    }

    @Test fun missingNotesHaveReadableFallback() {
        compose.setContent { LolitaTheme { AppUpdateDialog(
            AppUpdateUiState(showDialog = true, update = ReleaseUpdate(release(""), 0)),
            "2.51.0", {}, {}, {}) } }
        compose.onNodeWithText("此版本暂未提供更新说明").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("知道了").assertIsDisplayed()
    }

    @Test fun longNotesScrollAndActionsRemainReachableAcrossSevenSkinsAtLargeFont() {
        val presentation = mutableStateOf(SkinType.DEFAULT to false)
        val notes = (1..24).joinToString("\n\n") { "第${it}项更新：图像生成可以在后台继续，离开页面后到任务中心查看结果。" } +
            "\n\n最后一项：保留原始服饰图片。"
        var opened: String? = null
        compose.setContent {
            val (skin, dark) = presentation.value
            val config = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = 320
                screenHeightDp = 640
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config,
                LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                LolitaTheme(skinType = skin, darkTheme = dark) {
                    Surface { Box(Modifier.size(320.dp, 640.dp)) {
                        AppUpdateDialog(AppUpdateUiState(showDialog = true, update = ReleaseUpdate(release(notes), 1)),
                            "2.50.0", {}, {}, { opened = it })
                    } }
                }
            }
        }
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "image-task-update-notes").apply { mkdirs() }
        for (skin in SkinType.entries) for (dark in listOf(false, true)) {
            compose.runOnIdle { presentation.value = skin to dark }
            compose.onNodeWithText("下载 APK").assertIsDisplayed().assertHasClickAction()
            compose.onNodeWithText("关闭").assertIsDisplayed().assertHasClickAction()
            compose.onNodeWithText("最后一项：保留原始服饰图片。").performScrollTo().assertIsDisplayed()
            val bitmap = compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
            File(output, "${skin.name.lowercase()}-${if (dark) "dark" else "light"}.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.onNodeWithText("下载 APK").performClick()
            assertEquals("https://gitee.com/aalm1233/lolita/releases/download/v2.51.0/lolita-2.51.0.apk", opened)
            compose.onNodeWithTag("appUpdateContent").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -100_000f) }
        }
    }

    private fun release(notes: String) = AppRelease("2.51.0", "https://gitee.com/aalm1233/lolita/releases/v2.51.0",
        "https://gitee.com/aalm1233/lolita/releases/download/v2.51.0/lolita-2.51.0.apk", notes)
}
