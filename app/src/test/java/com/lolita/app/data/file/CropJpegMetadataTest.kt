package com.lolita.app.data.file

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class CropJpegMetadataTest {
    private fun marker(code: Int, payload: ByteArray) = byteArrayOf(255.toByte(), code.toByte(),
        ((payload.size + 2) ushr 8).toByte(), (payload.size + 2).toByte()) + payload
    private val soi = byteArrayOf(255.toByte(), 216.toByte())
    private val eoi = byteArrayOf(255.toByte(), 217.toByte())
    private val jfif = marker(224, "JFIF\u0000".toByteArray() + ByteArray(9))
    private val scanHeader = marker(218, byteArrayOf(1, 1, 0, 0, 63, 0))
    private val scan = byteArrayOf(4, 22, 255.toByte(), 0, 13, 255.toByte(), 208.toByte(), 27,
        255.toByte(), 255.toByte(), 0, 92)
    private fun strip(bytes: ByteArray) = ByteArrayOutputStream().also {
        CropJpegMetadata.copyWithoutMetadata(ByteArrayInputStream(bytes), it)
    }.toByteArray()

    @Test fun stripsAllPrivateAppAndCommentSegmentsButPreservesJfifAndEveryScanByte() {
        val quantisation = marker(219, byteArrayOf(1, 2, 3, 4))
        val privateMetadata = (225..239).fold(ByteArray(0)) { bytes, code ->
            bytes + marker(code, "PRIVATE PROFILE EXIF GPS XMP".toByteArray())
        } + marker(254, "PRIVATE COMMENT".toByteArray()) + marker(224, "PRIVATE OTHER APP0".toByteArray())
        val expected = soi + jfif + quantisation + scanHeader + scan + scanHeader + scan + eoi
        val encoded = soi + jfif + privateMetadata + quantisation + scanHeader + scan + privateMetadata + scanHeader + scan + eoi
        assertArrayEquals(expected, strip(encoded))
        assertArrayEquals(expected, strip(expected))
    }

    @Test fun rejectsTruncationInvalidMarkerLengthsAndTrailingData() {
        for (bytes in listOf(byteArrayOf(), soi, soi + byteArrayOf(255.toByte(), 225.toByte(), 0, 1),
            soi + marker(225, ByteArray(8)).dropLast(1).toByteArray(),
            soi + scanHeader + scan, soi + eoi + byteArrayOf(1))) {
            assertTrue(runCatching { strip(bytes) }.exceptionOrNull() is IllegalArgumentException)
        }
    }
}
