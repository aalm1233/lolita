package com.lolita.app.ui.screen.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lolita.app.domain.model.AppRelease
import com.lolita.app.domain.model.ReleaseUpdate
import com.lolita.app.ui.theme.LolitaTheme
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real startup host without accessing update repositories, Room or settings. */
@RunWith(AndroidJUnit4::class)
class AppUpdateHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val viewModelStore = ViewModelStore()
    private var nextViewModelKey = 0

    @After
    fun tearDown() {
        compose.runOnUiThread { viewModelStore.clear() }
    }

    @Test
    fun downloadableNewVersionAppearsOnlyAfterItsLinkIsConfirmed() {
        val checks = AtomicInteger()
        val probes = AtomicInteger()
        val release = CompletableDeferred<ReleaseUpdate>()
        val reachable = CompletableDeferred<Boolean>()
        val viewModel = createViewModel(
            checkUpdate = { checks.incrementAndGet(); release.await() },
            canDownload = { probes.incrementAndGet(); reachable.await() }
        )
        showHost(mutableStateOf(viewModel))

        compose.waitUntil(5_000) { checks.get() == 1 }
        assertNoUpdateDialog()
        compose.runOnIdle { release.complete(newerRelease) }
        compose.waitUntil(5_000) { probes.get() == 1 }
        assertNoUpdateDialog()

        compose.runOnIdle { reachable.complete(true) }
        awaitAutomaticCheck(viewModel, checks)
        assertDownloadPrompt()
        compose.onNodeWithText("关闭").performClick()
        assertNoUpdateDialog()
    }

    @Test
    fun equalVersionFinishesWithoutShowingAnUpdateDialog() {
        val checks = AtomicInteger()
        val viewModel = createViewModel(
            checkUpdate = { checks.incrementAndGet(); currentRelease },
            canDownload = { true }
        )
        showHost(mutableStateOf(viewModel))

        awaitAutomaticCheck(viewModel, checks)
        assertNoUpdateDialog()
    }

    @Test
    fun releaseWithoutAnApkFinishesWithoutShowingAnUpdateDialog() {
        val checks = AtomicInteger()
        val viewModel = createViewModel(
            checkUpdate = {
                checks.incrementAndGet()
                newerRelease.copy(release = newerRelease.release.copy(apkDownloadUrl = null))
            },
            canDownload = { true }
        )
        showHost(mutableStateOf(viewModel))

        awaitAutomaticCheck(viewModel, checks)
        assertNoUpdateDialog()
    }

    @Test
    fun failedReleaseCheckFinishesWithoutShowingAnErrorDialog() {
        val checks = AtomicInteger()
        val viewModel = createViewModel(
            checkUpdate = { checks.incrementAndGet(); throw IOException("offline") },
            canDownload = { true }
        )
        showHost(mutableStateOf(viewModel))

        awaitAutomaticCheck(viewModel, checks)
        assertNoUpdateDialog()
    }

    @Test
    fun unreachableApkFinishesWithoutShowingAnUpdateDialog() {
        val checks = AtomicInteger()
        val probes = AtomicInteger()
        val viewModel = createViewModel(
            checkUpdate = { checks.incrementAndGet(); newerRelease },
            canDownload = { probes.incrementAndGet(); false }
        )
        showHost(mutableStateOf(viewModel))

        awaitAutomaticCheck(viewModel, checks)
        assertEquals(1, probes.get())
        assertNoUpdateDialog()
    }

    @Test
    fun failedApkProbeFinishesWithoutShowingAnErrorDialog() {
        val checks = AtomicInteger()
        val probes = AtomicInteger()
        val viewModel = createViewModel(
            checkUpdate = { checks.incrementAndGet(); newerRelease },
            canDownload = { probes.incrementAndGet(); throw IOException("broken link") }
        )
        showHost(mutableStateOf(viewModel))

        awaitAutomaticCheck(viewModel, checks)
        assertEquals(1, probes.get())
        assertNoUpdateDialog()
    }

    @Test
    fun reenteringHostWithTheSameViewModelDoesNotRecheckOrReopenThePrompt() {
        val checks = AtomicInteger()
        val hostVisible = mutableStateOf(true)
        val viewModel = createViewModel(
            checkUpdate = { checks.incrementAndGet(); newerRelease },
            canDownload = { true }
        )
        showHost(mutableStateOf(viewModel), hostVisible)
        awaitAutomaticCheck(viewModel, checks)
        assertDownloadPrompt()
        compose.onNodeWithText("关闭").performClick()

        compose.runOnIdle { hostVisible.value = false }
        compose.waitForIdle()
        compose.runOnIdle { hostVisible.value = true }
        compose.waitForIdle()

        assertEquals(1, checks.get())
        assertNoUpdateDialog()
    }

    @Test
    fun recreatedHostWithANewViewModelStartsItsFirstAutomaticCheck() {
        val firstChecks = AtomicInteger()
        val firstViewModel = createViewModel(
            checkUpdate = { firstChecks.incrementAndGet(); currentRelease },
            canDownload = { true }
        )
        val currentViewModel = mutableStateOf(firstViewModel)
        val hostVisible = mutableStateOf(true)
        showHost(currentViewModel, hostVisible)
        awaitAutomaticCheck(firstViewModel, firstChecks)
        assertNoUpdateDialog()

        compose.runOnIdle { hostVisible.value = false }
        compose.waitForIdle()
        val nextChecks = AtomicInteger()
        val nextViewModel = createViewModel(
            checkUpdate = { nextChecks.incrementAndGet(); newerRelease },
            canDownload = { true }
        )
        compose.runOnIdle {
            currentViewModel.value = nextViewModel
            hostVisible.value = true
        }

        awaitAutomaticCheck(nextViewModel, nextChecks)
        assertEquals(1, firstChecks.get())
        assertDownloadPrompt()
    }

    private fun createViewModel(
        checkUpdate: suspend (String) -> ReleaseUpdate,
        canDownload: suspend (String) -> Boolean
    ): AppUpdateViewModel = compose.runOnUiThread {
        AppUpdateViewModel(
            currentVersion = "2.41.0",
            checkUpdate = checkUpdate,
            dispatcher = Dispatchers.Main.immediate,
            canDownload = canDownload
        ).also { viewModelStore.put("update-${nextViewModelKey++}", it) }
    }

    private fun showHost(viewModel: State<AppUpdateViewModel>, hostVisible: State<Boolean> = mutableStateOf(true)) {
        compose.setContent {
            LolitaTheme(darkTheme = false) {
                Surface {
                    Text("更新提示测试宿主")
                    if (hostVisible.value) {
                        key(viewModel.value) { AppUpdateHost(viewModel.value) }
                    }
                }
            }
        }
    }

    private fun awaitAutomaticCheck(viewModel: AppUpdateViewModel, checks: AtomicInteger) {
        compose.waitUntil(5_000) { checks.get() == 1 && !viewModel.state.value.isChecking }
        compose.waitForIdle()
    }

    private fun assertDownloadPrompt() {
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        compose.onNodeWithText("发现新版本").assertIsDisplayed()
        compose.onNodeWithText("最新发布：v2.42.0").assertIsDisplayed()
        compose.onNodeWithText("下载 APK").assertIsDisplayed().assertHasClickAction()
    }

    private fun assertNoUpdateDialog() {
        compose.onNodeWithText("更新提示测试宿主").assertIsDisplayed()
        compose.onAllNodes(isDialog()).assertCountEquals(0)
    }

    private val newerRelease = ReleaseUpdate(
        AppRelease(
            "2.42.0",
            "https://gitee.com/example/lolita/releases/tag/v2.42.0",
            "https://gitee.com/example/lolita/releases/download/v2.42.0/lolita-2.42.0.apk"
        ),
        versionComparison = 1
    )

    private val currentRelease = ReleaseUpdate(
        AppRelease(
            "2.41.0",
            "https://gitee.com/example/lolita/releases/tag/v2.41.0",
            "https://gitee.com/example/lolita/releases/download/v2.41.0/lolita-2.41.0.apk"
        ),
        versionComparison = 0
    )
}
