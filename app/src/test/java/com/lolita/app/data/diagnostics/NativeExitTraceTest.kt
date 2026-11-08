package com.lolita.app.data.diagnostics

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

class NativeExitTraceTest {
    private fun varint(value: Long): ByteArray {
        val output = ByteArrayOutputStream()
        var n = value
        while (n and -128L != 0L) { output.write(((n and 127) or 128).toInt()); n = n ushr 7 }
        output.write(n.toInt())
        return output.toByteArray()
    }
    private fun int(field: Int, n: Long) = varint((field shl 3).toLong()) + varint(n)
    private fun data(field: Int, bytes: ByteArray) = varint(((field shl 3) or 2).toLong()) + varint(bytes.size.toLong()) + bytes
    private fun text(field: Int, text: String) = data(field, text.toByteArray())
    private fun frame(symbol: String = "art::Runtime::Abort(char const*)", module: String = "/apex/private/lib64/libart.so") =
        int(1, 0x1234) + text(4, symbol) + int(5, 12) + text(6, module) + text(8, "aabbcc1234")
    private fun thread(tid: Long, frames: List<ByteArray>) = data(16,
        data(2, text(2, "PRIVATE_THREAD") + text(5, "PRIVATE_MEMORY") + frames.fold(byteArrayOf()) { a, f -> a + data(4, f) }) + int(1, tid))
    private fun trace(frames: List<ByteArray> = listOf(frame())) =
        thread(90, listOf(frame("PRIVATE_OTHER_THREAD"))) + thread(91, frames) +
            text(14, "PRIVATE_ABORT /data/user/images/private.jpg") + text(9, "PRIVATE_COMMAND") +
            text(17, "PRIVATE_MEMORY_MAP") + text(18, "PRIVATE_LOGS") + text(19, "PRIVATE_FDS") +
            data(10, int(1, 6) + int(3, -6) + text(2, "PRIVATE_SIGNAL_NAME") + text(10, "PRIVATE_MEMORY")) +
            int(6, 91) + int(1, 1) // Fatal tid deliberately follows all map entries.
    private fun render(bytes: ByteArray) = NativeExitTrace.render(ByteArrayInputStream(bytes))

    @Test fun unorderedAospTraceRetainsOnlyCrashingThreadCodeLocations() {
        val report = render(trace())
        assertTrue(report.contains("trace=available\narchitecture=1\nsignal=6\nsignalCode=-6\n"))
        assertTrue(report.contains("frame0=libart.so pc=1234 art::Runtime::Abort(char const*)+12 buildId=aabbcc1234"))
        assertFalse(report.contains("PRIVATE")); assertFalse(report.contains("/"))
        assertTrue(report.contains("frameCount=1\n"))
    }

    @Test fun framesAndOutputAreBoundedEvenWithMaximumSymbols() {
        val frame = frame("a".repeat(240), "/data/app/" + "lib" + "a".repeat(110) + ".so") + text(8, "a".repeat(128))
        val report = render(trace(List(100) { frame }))
        assertTrue(report.contains("trace=available")); assertTrue(report.contains("framesLimited=true"))
        assertFalse(report.contains("frame64=")); assertTrue(report.toByteArray().size <= DiagnosticRecord.MAX_BYTES)
    }

    @Test fun malformedAndUnsupportedWireDataCannotLeakRawBytes() {
        val invalid = listOf(byteArrayOf(0), byteArrayOf(11), byteArrayOf(8, -128),
            byteArrayOf(18, 127, 0), byteArrayOf(9, 1), byteArrayOf(13, 1),
            byteArrayOf(8) + ByteArray(10) { -1 }, int(6, 91) + data(16, byteArrayOf(0)))
        for (bytes in invalid) assertEquals("trace=malformed-or-unreadable\n", render(bytes))
    }

    @Test fun oversizeAndThrowingInputsGiveOnlyFixedStatus() {
        assertEquals("trace=too-large\n", render(ByteArray(NativeExitTrace.MAX_INPUT_BYTES + 1)))
        val input = object : InputStream() { override fun read(): Int = throw IOException("PRIVATE_EXCEPTION") }
        assertEquals("trace=malformed-or-unreadable\n", NativeExitTrace.render(input))
    }

    @Test fun unsafePathsAndFunctionLiteralsAreRedacted() {
        val report = render(trace(listOf(frame("abort(\"PRIVATE_SQL\")", "/private/images/PRIVATE_IMAGE.jpg") + text(8, "PRIVATE_ID"))))
        assertTrue(report.contains("frame0=? pc=1234 ?+12 buildId=?"))
        assertFalse(report.contains("PRIVATE"))
    }

    @Test fun missingFatalThreadAndProto3DefaultsAreExplicit() {
        val missing = render(int(6, 1) + thread(2, listOf(frame())))
        assertTrue(missing.contains("architecture=0")); assertTrue(missing.contains("frames=no-crash-thread"))
        assertFalse(missing.contains("frame0="))
        val empty = render(int(6, 1) + thread(1, emptyList()))
        assertTrue(empty.contains("frameCount=0"))
    }

    @Test fun excessiveFieldsAndThreadEntriesCannotExhaustParser() {
        assertEquals("trace=malformed-or-unreadable\n", render(ByteArray(200_002) { if (it % 2 == 0) 8 else 1 }))
        val threads = (1..513).fold(byteArrayOf()) { a, id -> a + thread(id.toLong(), emptyList()) }
        assertEquals("trace=malformed-or-unreadable\n", render(threads))
    }
}
