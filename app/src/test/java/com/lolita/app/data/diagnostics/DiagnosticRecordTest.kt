package com.lolita.app.data.diagnostics

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticRecordTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun messagesPathsAndRequestDetailsAreNotReadOrExported() {
        val secret = "https://private.example/token?password=secret 衣橱照片 SQL /data/user/0/private"
        val failure = object : IllegalStateException(secret) {
            override val message: String? get() = error("Must not inspect the original message")
            override fun toString(): String = error("Must not stringify the exception")
        }
        failure.stackTrace = arrayOf(StackTraceElement("com.lolita.Export", "run", "/private/customer-photo.jpg", 12))
        failure.initCause(IllegalArgumentException(secret))
        failure.addSuppressed(java.io.IOException(secret))
        val report = DiagnosticRecord.render(failure, mapOf("versionCode" to "158"))
        assertTrue(report.contains("com.lolita.Export.run(?:12)"))
        assertTrue(report.contains("cause=java.lang.IllegalArgumentException"))
        assertTrue(report.contains("suppressed=java.io.IOException"))
        listOf("private", "password", "照片", "SQL", "secret", "https:").forEach { assertFalse(it, report.contains(it)) }
    }
    @Test fun cyclicAndLargeExceptionGraphsAreBounded() {
        val a = IllegalStateException(); val b = IllegalArgumentException()
        a.initCause(b); b.initCause(a)
        a.stackTrace = Array(10_000) { StackTraceElement("com.example." + "A".repeat(500), "method", "Example.kt", it) }
        a.addSuppressed(b)
        val report = DiagnosticRecord.render(a, mapOf("model" to "MIX Flip 2"))
        assertTrue(report.contains("[limited]"))
        assertTrue(report.toByteArray(Charsets.UTF_8).size <= DiagnosticRecord.MAX_BYTES)
        assertTrue(report.contains("model=MIX Flip 2"))
    }
    @Test fun branchingSuppressedGraphHasGlobalNodeBudget() {
        fun tree(depth: Int): Throwable = IllegalStateException().apply {
            stackTrace = emptyArray()
            if (depth > 0) { initCause(tree(depth - 1)); addSuppressed(tree(depth - 1)); addSuppressed(tree(depth - 1)) }
        }
        val report = DiagnosticRecord.render(tree(5), emptyMap())
        assertEquals(16, Regex("(?:exception|cause|suppressed)=java.lang.IllegalStateException").findAll(report).count())
        assertTrue(report.contains("[limited]"))
    }
    @Test fun storeKeepsLatestOfEachKindAndRejectsOversizeWithoutLosingOldRecord() {
        val store = DiagnosticStore(temporary.newFolder())
        store.write("fatal", "first"); store.write("backup-export", "handled"); store.write("fatal", "second")
        assertEquals("second", store.read("fatal")); assertEquals("handled", store.read("backup-export"))
        assertTrue(runCatching { store.write("fatal", "x".repeat(DiagnosticRecord.MAX_BYTES + 1)) }.isFailure)
        assertEquals("second", store.read("fatal"))
        assertTrue(runCatching { store.write("../escape", "bad") }.isFailure)
    }
    @Test fun diskFailureStillDelegatesSameThreadAndOriginalErrorExactlyOnce() {
        val expected = OutOfMemoryError("private message")
        val thread = Thread.currentThread(); var calls = 0
        val handler = DiagnosticCrashHandler(Thread.UncaughtExceptionHandler { actualThread, actualError ->
            calls++; assertSame(thread, actualThread); assertSame(expected, actualError)
        }) { throw java.io.IOException("full disk") }
        handler.uncaughtException(thread, expected)
        assertEquals(1, calls)
    }
    @Test fun successfulRecordPrecedesOriginalHandlerAndDoesNotSwallowItsFailure() {
        val events = mutableListOf<String>()
        val sentinel = IllegalStateException()
        val handler = DiagnosticCrashHandler(Thread.UncaughtExceptionHandler { _, _ -> events += "delegate"; throw sentinel }) { events += "record" }
        assertSame(sentinel, runCatching { handler.uncaughtException(Thread.currentThread(), RuntimeException()) }.exceptionOrNull())
        assertEquals(listOf("record", "delegate"), events)
    }
    @Test fun oversizedExistingFileIsNotLoadedAndEmptyStoreHasNoFabricatedException() {
        val directory = temporary.newFolder()
        java.io.File(directory, "fatal.txt").writeBytes(ByteArray(DiagnosticRecord.MAX_BYTES + 1))
        assertNull(DiagnosticStore(directory).read("fatal"))
        assertEquals("format=test\n", DiagnosticRecord.render(null, mapOf("format" to "test")))
    }
}
