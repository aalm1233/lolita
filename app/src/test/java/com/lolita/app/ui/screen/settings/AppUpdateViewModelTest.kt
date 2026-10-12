package com.lolita.app.ui.screen.settings

import com.lolita.app.domain.model.AppRelease
import com.lolita.app.domain.model.ReleaseUpdate
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class AppUpdateViewModelTest {
    @Test fun automaticCheckStaysHiddenUntilTheNewApkIsReachable() {
        val response = CompletableDeferred<ReleaseUpdate>()
        val link = CompletableDeferred<Boolean>()
        val viewModel = AppUpdateViewModel("2.41.0", { response.await() }, Dispatchers.Unconfined,
            canDownload = { link.await() })

        viewModel.checkAutomaticallyOnce()
        assertFalse(viewModel.state.value.showDialog)
        response.complete(downloadableUpdate)
        assertFalse(viewModel.state.value.showDialog)
        link.complete(true)

        assertTrue(viewModel.state.value.showDialog)
        assertEquals(downloadableUpdate, viewModel.state.value.update)
        assertNull(viewModel.state.value.error)
    }

    @Test fun automaticCheckIsSilentForEqualOrOlderVersionsAndMissingApks() {
        val results = listOf(
            downloadableUpdate.copy(versionComparison = 0),
            downloadableUpdate.copy(versionComparison = -1),
            newVersion
        )
        for (result in results) {
            val viewModel = AppUpdateViewModel("2.41.0", { result }, Dispatchers.Unconfined,
                canDownload = { error("These results must not probe an APK") })
            viewModel.checkAutomaticallyOnce()
            assertFalse(viewModel.state.value.showDialog)
            assertNull(viewModel.state.value.error)
            assertFalse(viewModel.state.value.isChecking)
        }
    }

    @Test fun automaticCheckIsSilentForNetworkAndParsingFailures() {
        for (failure in listOf(IOException("offline"), IllegalArgumentException("bad release"))) {
            val viewModel = AppUpdateViewModel("2.41.0", { throw failure }, Dispatchers.Unconfined,
                canDownload = { true })
            viewModel.checkAutomaticallyOnce()
            assertFalse(viewModel.state.value.showDialog)
            assertNull(viewModel.state.value.error)
            assertNull(viewModel.state.value.update)
            assertFalse(viewModel.state.value.isChecking)
        }
    }

    @Test fun automaticCheckIsSilentForUnavailableOrFailingDownloadLinks() {
        for (throws in listOf(false, true)) {
            val viewModel = AppUpdateViewModel("2.41.0", { downloadableUpdate }, Dispatchers.Unconfined,
                canDownload = { if (throws) throw IOException("broken link") else false })
            viewModel.checkAutomaticallyOnce()
            assertFalse(viewModel.state.value.showDialog)
            assertNull(viewModel.state.value.error)
            assertFalse(viewModel.state.value.isChecking)
        }
    }

    @Test fun repeatedStartupDoesNotRecheckOrReopenADismissedPrompt() {
        var calls = 0
        val viewModel = AppUpdateViewModel("2.41.0", { calls++; downloadableUpdate }, Dispatchers.Unconfined,
            canDownload = { true })
        viewModel.checkAutomaticallyOnce()
        viewModel.dismissDialog()
        viewModel.checkAutomaticallyOnce()
        assertEquals(1, calls)
        assertFalse(viewModel.state.value.showDialog)

        viewModel.checkForUpdate()
        assertEquals(2, calls)
        assertTrue(viewModel.state.value.showDialog)
    }

    @Test fun manualTapDuringAutomaticCheckShowsErrorsWithoutAnotherRequest() {
        val response = CompletableDeferred<ReleaseUpdate>()
        var calls = 0
        val viewModel = AppUpdateViewModel("2.41.0", { calls++; response.await() }, Dispatchers.Unconfined,
            canDownload = { true })
        viewModel.checkAutomaticallyOnce()
        viewModel.checkForUpdate()
        response.completeExceptionally(IOException("offline"))
        assertEquals(1, calls)
        assertTrue(viewModel.state.value.showDialog)
        assertNotNull(viewModel.state.value.error)
    }

    @Test fun manualTapDuringProbeImmediatelyShowsTheFetchedReleaseAndIgnoresProbeFailure() {
        val link = CompletableDeferred<Boolean>()
        var calls = 0
        val viewModel = AppUpdateViewModel("2.41.0", { calls++; downloadableUpdate }, Dispatchers.Unconfined,
            canDownload = { link.await() })
        viewModel.checkAutomaticallyOnce()
        assertTrue(viewModel.state.value.isChecking)

        viewModel.checkForUpdate()
        assertTrue(viewModel.state.value.showDialog)
        assertFalse(viewModel.state.value.isChecking)
        assertEquals(downloadableUpdate, viewModel.state.value.update)
        assertEquals(1, calls)

        link.completeExceptionally(IOException("probe timeout"))
        assertNull(viewModel.state.value.error)
        assertEquals(downloadableUpdate, viewModel.state.value.update)
    }

    @Test fun dismissingAManualCheckDuringTheAutomaticProbeDoesNotReopenIt() {
        val link = CompletableDeferred<Boolean>()
        val viewModel = AppUpdateViewModel("2.41.0", { downloadableUpdate }, Dispatchers.Unconfined,
            canDownload = { link.await() })
        viewModel.checkAutomaticallyOnce()
        viewModel.checkForUpdate()
        viewModel.dismissDialog()
        link.complete(true)
        assertFalse(viewModel.state.value.showDialog)
        assertEquals(downloadableUpdate, viewModel.state.value.update)
    }

    @Test fun repeatedTapsShareTheRunningCheckAndClosingDoesNotReopenTheDialog() {
        val response = CompletableDeferred<ReleaseUpdate>()
        var calls = 0
        val viewModel = AppUpdateViewModel("2.39.5", {
            calls++
            response.await()
        }, Dispatchers.Unconfined)

        viewModel.checkForUpdate()
        viewModel.checkForUpdate()
        assertEquals(1, calls)
        assertTrue(viewModel.state.value.isChecking)
        viewModel.dismissDialog()
        response.complete(newVersion)

        assertFalse(viewModel.state.value.showDialog)
        assertFalse(viewModel.state.value.isChecking)
        assertEquals(newVersion, viewModel.state.value.update)
    }

    @Test fun networkFailureDoesNotReportLatestAndCanBeRetried() {
        var calls = 0
        val viewModel = AppUpdateViewModel("2.39.5", {
            if (calls++ == 0) throw IOException("offline")
            newVersion
        }, Dispatchers.Unconfined)

        viewModel.checkForUpdate()
        assertNotNull(viewModel.state.value.error)
        assertNull(viewModel.state.value.update)
        assertFalse(viewModel.state.value.isChecking)
        viewModel.checkForUpdate()
        assertNull(viewModel.state.value.error)
        assertEquals(newVersion, viewModel.state.value.update)
    }

    private val newVersion = ReleaseUpdate(
        AppRelease("2.40.0", "https://github.com/aalm1233/lolita/releases/tag/v2.40.0", null), 1
    )
    private val downloadableUpdate = ReleaseUpdate(
        AppRelease("2.42.0", "https://github.com/aalm1233/lolita/releases/tag/v2.42.0",
            "https://github.com/aalm1233/lolita/releases/download/v2.42.0/lolita-2.42.0.apk"), 1
    )
}
