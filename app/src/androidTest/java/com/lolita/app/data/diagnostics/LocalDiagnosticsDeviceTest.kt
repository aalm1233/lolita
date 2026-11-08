package com.lolita.app.data.diagnostics

import android.content.ContentValues
import android.content.ContextWrapper
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class LocalDiagnosticsDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val target = inst.targetContext
    @Test fun reportAndRealResolverExportContainOnlySanitizedDiagnosticsAndRetainRecordsOnFailure() {
        val root = File(target.cacheDir, "diagnostics-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) { override fun getNoBackupFilesDir() = root }
        var uri: android.net.Uri? = null
        try {
            val error = IllegalStateException("私人照片 /private/wardrobe.jpg https://private.example?token=secret")
            LocalDiagnostics.recordBackupExportFailure(context, error)
            val report = LocalDiagnostics.report(context)
            assertTrue(report.contains("format=lolita.diagnostics.v2"))
            assertTrue(report.contains("[system-exit]"))
            assertTrue(report.contains("[native-exit]"))
            assertTrue(report.contains("exception=java.lang.IllegalStateException"))
            assertTrue(report.contains("LocalDiagnosticsDeviceTest.kt:"))
            assertFalse(report.contains("wardrobe.jpg")); assertFalse(report.contains("secret")); assertFalse(report.contains("私人照片"))
            val store = DiagnosticStore(File(root, "diagnostics"))
            val before = store.read("backup-export")
            assertTrue(runCatching { LocalDiagnostics.export(context, android.net.Uri.parse("content://not.a.real.provider/report")) }.isFailure)
            assertEquals(before, store.read("backup-export"))
            uri = requireNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "diagnostic-test-${UUID.randomUUID()}.txt")
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            }))
            LocalDiagnostics.export(context, uri)
            val exported = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            assertTrue(exported.contains(before!!)); assertFalse(exported.contains("secret"))
            assertEquals(before, store.read("backup-export"))
        } finally { uri?.let { context.contentResolver.delete(it, null, null) }; root.deleteRecursively() }
    }

    /** Explicit host-driven fatal probe only; never run by an ordinary test selection. */
    @Test fun fatalProbeOnlyWhenExplicitlyRequested() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runFatalProbe") == "true")
        LocalDiagnostics.install(target)
        Thread { throw IllegalStateException("diagnostic-probe-private-message-must-not-export") }.start()
        Thread.sleep(15_000)
        fail("The original uncaught-exception handler must terminate this disposable test process")
    }

    @Test fun nativeProbeOnlyWhenExplicitlyRequested() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runNativeProbe") == "true")
        android.system.Os.kill(android.os.Process.myPid(), android.system.OsConstants.SIGABRT)
        Thread.sleep(15_000)
        fail("SIGABRT must terminate this disposable test process")
    }

    @Test fun nativeTraceAfterExplicitHostProbe() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyNativeProbe") == "true")
        val report = LocalDiagnostics.report(target)
        assertTrue(report.contains("[native-exit]"))
        assertTrue(report.contains("trace=available"))
        assertTrue(report.contains("signal=6\n"))
        assertTrue(report.contains("frame0="))
        assertFalse(report.contains("/apex/"))
        assertFalse(report.contains("/data/"))
        File(target.cacheDir, "native-probe-report.txt").writeText(report)
        val uri = requireNotNull(target.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "native-diagnostic-test-${UUID.randomUUID()}.txt")
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
        }))
        try {
            LocalDiagnostics.export(target, uri)
            val saved = target.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            assertTrue(saved.contains("signal=6\n")); assertTrue(saved.contains("frame0="))
            assertFalse(saved.contains("/data/")); assertFalse(saved.contains("/apex/"))
        } finally { target.contentResolver.delete(uri, null, null) }
    }
}
