package com.lolita.app.ui.screen.settings

import com.lolita.app.domain.model.AppRelease
import com.lolita.app.domain.model.ReleaseUpdate
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class AppUpdateViewModelTest {
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
}
