package com.lolita.app.data.diagnostics

import java.io.InputStream

/** AOSP debuggerd/proto/tombstone.proto allowlist. Never render the raw protobuf. */
internal object NativeExitTrace {
    const val MAX_INPUT_BYTES = 4 * 1024 * 1024
    private const val MAX_FRAMES = 64
    private val moduleName = Regex("(?:lib[A-Za-z0-9_.+-]+\\.so|[A-Za-z0-9_.+-]+\\.(?:apk|oat|odex|art|vdex))")
    private val codeSymbol = Regex("[A-Za-z0-9_:.<>~(), *&+\\[\\]$=!?@%-]{1,240}")
    private val buildId = Regex("[a-fA-F0-9]{1,128}")

    fun render(input: InputStream): String = try {
        // Fixed allocation prevents unbounded growth, even for an OEM's malformed trace.
        val bytes = ByteArray(MAX_INPUT_BYTES + 1)
        var size = 0
        while (size < bytes.size) {
            val count = input.read(bytes, size, bytes.size - size)
            if (count < 0) break
            if (count == 0) {
                val single = input.read()
                if (single < 0) break
                bytes[size++] = single.toByte()
            } else size += count
        }
        if (size > MAX_INPUT_BYTES) "trace=too-large\n" else parse(bytes, size)
    } catch (_: Exception) { "trace=malformed-or-unreadable\n" }

    private fun parse(bytes: ByteArray, size: Int): String {
        val budget = Budget()
        val root = Wire(bytes, 0, size, budget)
        var tid: Long? = null
        var arch = 0L // proto3 ARM32 default
        var signal: Wire? = null
        val threads = mutableListOf<Wire>()
        while (root.next()) when {
            root.number == 1 && root.type == 0 -> arch = root.integer()
            root.number == 6 && root.type == 0 -> tid = root.integer()
            root.number == 10 && root.type == 2 -> signal = root.message()
            root.number == 16 && root.type == 2 -> {
                val thread = root.message()
                require(threads.size < 512)
                threads += thread
            }
            else -> root.skip()
        }
        var signalNumber = 0L
        var signalCode = 0
        signal?.let { fields ->
            while (fields.next()) when {
                fields.number == 1 && fields.type == 0 -> signalNumber = fields.integer()
                fields.number == 3 && fields.type == 0 -> signalCode = fields.integer().toInt()
                else -> fields.skip()
            }
        }
        // Map entries and top-level fields can appear in any protobuf order.
        var crashingThread: Wire? = null
        for (entry in threads) {
            var key = 0L
            var value: Wire? = null
            while (entry.next()) when {
                entry.number == 1 && entry.type == 0 -> key = entry.integer()
                entry.number == 2 && entry.type == 2 -> value = entry.message()
                else -> entry.skip()
            }
            if (tid != null && tid == key) crashingThread = value
        }
        val output = StringBuilder("trace=available\narchitecture=$arch\nsignal=$signalNumber\nsignalCode=$signalCode\n")
        val thread = crashingThread ?: return output.append("frames=no-crash-thread\n").toString()
        var frames = 0
        var limited = false
        while (thread.next()) {
            if (thread.number == 4 && thread.type == 2) {
                val frame = thread.message()
                if (frames < MAX_FRAMES && !limited) {
                    val line = "frame$frames=${frame(frame)}\n"
                    if (output.length + line.length + 64 <= DiagnosticRecord.MAX_BYTES) {
                        output.append(line)
                        frames++
                    } else limited = true
                } else limited = true
            } else thread.skip()
        }
        output.append("frameCount=$frames\n")
        if (limited) output.append("framesLimited=true\n")
        check(output.length <= DiagnosticRecord.MAX_BYTES)
        return output.toString()
    }

    private fun frame(fields: Wire): String {
        var pc = 0L
        var offset = 0L
        var symbol = "?"
        var module = "?"
        var id = "?"
        while (fields.next()) when {
            fields.number == 1 && fields.type == 0 -> pc = fields.integer()
            fields.number == 5 && fields.type == 0 -> offset = fields.integer()
            fields.number == 4 && fields.type == 2 -> symbol = fields.text(240)?.takeIf { it.matches(codeSymbol) } ?: "?"
            fields.number == 6 && fields.type == 2 -> module = fields.text(2048)?.substringAfterLast('/')
                ?.takeIf { it.matches(moduleName) && it.length <= 120 } ?: "?"
            fields.number == 8 && fields.type == 2 -> id = fields.text(128)?.takeIf { it.matches(buildId) } ?: "?"
            else -> fields.skip()
        }
        return "$module pc=${java.lang.Long.toUnsignedString(pc, 16)} $symbol+$offset buildId=$id"
    }

    private class Budget(var fields: Int = 100_000)

    /** Views into one bounded buffer; unknown fields are skipped without decoding/copying. */
    private class Wire(val bytes: ByteArray, var position: Int, val end: Int, val budget: Budget) {
        var number = 0
        var type = 0
        fun next(): Boolean {
            if (position == end) return false
            require(budget.fields-- > 0)
            val tag = integer()
            require(tag in 1..0xffffffffL)
            number = (tag ushr 3).toInt()
            type = (tag and 7).toInt()
            require(number > 0 && type in setOf(0, 1, 2, 5))
            return true
        }
        fun integer(): Long {
            var result = 0L
            for (shift in 0..63 step 7) {
                require(position < end)
                val byte = bytes[position++].toInt() and 255
                if (shift == 63) require(byte <= 1)
                result = result or ((byte and 127).toLong() shl shift)
                if (byte < 128) return result
            }
            error("invalid varint")
        }
        private fun length(): Int {
            val length = integer()
            require(length >= 0 && length <= end - position)
            return length.toInt()
        }
        fun message(): Wire {
            val length = length()
            return Wire(bytes, position, position + length, budget).also { position += length }
        }
        fun text(max: Int): String? {
            val length = length()
            val start = position
            position += length
            if (length > max) return null
            return String(bytes, start, length, Charsets.UTF_8)
        }
        fun skip() {
            when (type) {
                0 -> { integer(); return }
                1 -> { require(end - position >= 8); position += 8 }
                2 -> { val length = length(); position += length }
                5 -> { require(end - position >= 4); position += 4 }
            }
        }
    }
}
