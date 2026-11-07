package com.lolita.app.data.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import com.lolita.app.BuildConfig
import java.io.File

/** Local only. No business database, image, address, request or system trace access. */
object LocalDiagnostics {
    private fun store(context: Context) = DiagnosticStore(File(context.noBackupFilesDir, "diagnostics"))
    private fun metadata() = linkedMapOf(
        "format" to "lolita.diagnostics.v1",
        "timestampMs" to System.currentTimeMillis().toString(),
        "appVersion" to BuildConfig.VERSION_NAME,
        "versionCode" to BuildConfig.VERSION_CODE.toString(),
        "manufacturer" to Build.MANUFACTURER,
        "model" to Build.MODEL,
        "androidApi" to Build.VERSION.SDK_INT.toString(),
        "javaHeapMaxBytes" to Runtime.getRuntime().maxMemory().toString(),
        "javaHeapTotalBytes" to Runtime.getRuntime().totalMemory().toString(),
        "javaHeapFreeBytes" to Runtime.getRuntime().freeMemory().toString()
    )

    fun install(context: Context) {
        try {
            val previous = Thread.getDefaultUncaughtExceptionHandler() ?: return
            if (previous is DiagnosticCrashHandler) return
            // Resolve path and initialize the formatter before any allocation failure.
            val target = store(context)
            DiagnosticRecord.render(null, emptyMap())
            Thread.setDefaultUncaughtExceptionHandler(DiagnosticCrashHandler(previous) { error ->
                target.write("fatal", DiagnosticRecord.render(error, metadata()))
            })
        } catch (_: Throwable) { /* Optional diagnosis must never prevent application startup. */ }
    }

    fun recordBackupExportFailure(context: Context, error: Throwable) {
        try { store(context).write("backup-export", DiagnosticRecord.render(error, metadata())) }
        catch (_: Throwable) { /* Diagnosis cannot turn a handled export failure into a crash. */ }
    }

    internal fun report(context: Context): String = buildString {
        append(DiagnosticRecord.render(null, metadata()))
        for (kind in listOf("fatal", "backup-export")) {
            append("\n[$kind]\n")
            append(runCatching { store(context).read(kind) }.getOrNull() ?: "no-local-record\n")
        }
        append("\n[system-exit]\n")
        if (Build.VERSION.SDK_INT >= 30) {
            val exits = runCatching {
                context.getSystemService(ActivityManager::class.java)
                    .getHistoricalProcessExitReasons(context.packageName, 0, 5)
                    .filter { it.processName == context.packageName }
            }.getOrNull()
            // Do not read description, traceInputStream, processName or processStateSummary.
            val exit = exits?.firstOrNull()
            if (exit == null) append("unavailable\n") else {
                append("timestampMs=${exit.timestamp}\nreason=${exit.reason}\nstatus=${exit.status}\n")
                append("pssKb=${exit.pss}\nrssKb=${exit.rss}\nimportance=${exit.importance}\n")
            }
        } else append("unsupported-before-api30\n")
    }

    /** The caller supplies a URI explicitly chosen by the user in the system picker. */
    fun export(context: Context, uri: Uri) {
        val contents = report(context)
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use { it.write(contents) }
    }
}
