package com.lolita.app.data.diagnostics

import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.IdentityHashMap

/** Fixed allowlist: never call Throwable.message/toString or printStackTrace. */
internal object DiagnosticRecord {
    const val MAX_BYTES = 32 * 1024
    private val sourceBasename = Regex("[A-Za-z0-9_.-]{1,120}")
    fun render(error: Throwable?, metadata: Map<String, String>): String {
        val output = StringBuilder()
        fun append(line: String) {
            if (output.length + line.length + 1 <= MAX_BYTES) output.append(line).append('\n')
        }
        metadata.forEach { (key, value) -> append("${safe(key)}=${safe(value)}") }
        val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        var remainingNodes = 16
        fun walk(current: Throwable, relation: String, depth: Int) {
            if (depth >= 8 || remainingNodes-- <= 0 || !visited.add(current)) { append("$relation=[limited]"); return }
            append("$relation=${safe(current.javaClass.name)}")
            current.stackTrace.take(40).forEach { frame ->
                // File names are source basenames only, never exception-provided paths.
                val file = frame.fileName?.takeIf { it.matches(sourceBasename) } ?: "?"
                append("at ${safe(frame.className)}.${safe(frame.methodName)}($file:${frame.lineNumber})")
            }
            current.suppressed.take(2).forEach { walk(it, "suppressed", depth + 1) }
            current.cause?.let { walk(it, "cause", depth + 1) }
        }
        error?.let { walk(it, "exception", 0) }
        return output.toString()
    }

    private fun safe(value: String) = buildString(minOf(value.length, 180)) {
        for (index in 0 until minOf(value.length, 180)) {
            val char = value[index]
            append(if (char.code in 32..126 && char != '\\' && char != '/' && char != '=') char else '_')
        }
    }
}

/** Two fixed-size local records; a failed write leaves the previous record intact. */
internal class DiagnosticStore(private val directory: File) {
    @Synchronized fun write(kind: String, contents: String) {
        require(kind in setOf("fatal", "backup-export"))
        val bytes = contents.toByteArray(Charsets.UTF_8)
        require(bytes.size <= DiagnosticRecord.MAX_BYTES)
        check(directory.isDirectory || directory.mkdirs())
        val temporary = File(directory, "$kind.tmp")
        try {
            FileOutputStream(temporary).use { it.write(bytes); it.fd.sync() }
            check(temporary.renameTo(File(directory, "$kind.txt")))
        } finally { temporary.delete() }
    }

    @Synchronized fun read(kind: String): String? {
        require(kind in setOf("fatal", "backup-export"))
        val file = File(directory, "$kind.txt")
        if (!file.isFile || file.length() > DiagnosticRecord.MAX_BYTES) return null
        return file.inputStream().use { input ->
            val bytes = ByteArray(DiagnosticRecord.MAX_BYTES + 1)
            var count = 0
            while (count < bytes.size) {
                val n = input.read(bytes, count, bytes.size - count)
                if (n < 0) break
                count += n
            }
            if (count > DiagnosticRecord.MAX_BYTES) null else String(bytes, 0, count, Charsets.UTF_8)
        }
    }
}

internal class DiagnosticCrashHandler(
    private val previous: Thread.UncaughtExceptionHandler,
    private val record: (Throwable) -> Unit
) : Thread.UncaughtExceptionHandler {
    // Best effort reserve for writing a small report after an allocation failure.
    private var reserve: ByteArray? = ByteArray(64 * 1024)
    override fun uncaughtException(thread: Thread, error: Throwable) {
        reserve = null
        try { record(error) } catch (_: Throwable) { /* Original crash handling is mandatory. */ }
        finally { previous.uncaughtException(thread, error) }
    }
}
