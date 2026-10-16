package com.lolita.app.data.file

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32

/** Fixtures are generated nonpersonal patterns; existing real JPEG assets add independent inputs. */
class PersonalImageIntegrityTest {
    private fun fixture(name: String): ByteArray = javaClass.getResourceAsStream("/personal-image-integrity/$name")!!.use { it.readBytes() }
    private fun jpeg(progressive: Boolean = false, gray: Boolean = false): ByteArray =
        fixture((if (progressive) "progressive" else "baseline") + (if (gray) "-gray" else "-rgb") + ".jpg")
    private fun png(interlaced: Boolean = false, type: String = "rgba"): ByteArray =
        fixture(type + (if (interlaced) "-adam7" else "") + ".png")

    private fun scans(bytes: ByteArray): List<IntRange> {
        val result = mutableListOf<IntRange>()
        var at = 2
        while (at < bytes.size) {
            require(bytes[at++].toInt() and 255 == 255)
            var marker = bytes[at++].toInt() and 255
            while (marker == 255) marker = bytes[at++].toInt() and 255
            if (marker == 0xd9) break
            val length = (bytes[at].toInt() and 255 shl 8) or (bytes[at + 1].toInt() and 255)
            at += length
            if (marker == 0xda) {
                val start = at
                while (at < bytes.size - 1) {
                    if (bytes[at].toInt() and 255 == 255) {
                        val next = bytes[at + 1].toInt() and 255
                        if (next != 0 && next !in 0xd0..0xd7) break
                        at++
                    }
                    at++
                }
                result += start until at
            }
        }
        return result
    }

    @Test fun acceptsBaselineProgressiveAndGrayJpegWithRealEntropyData() {
        listOf(jpeg(), jpeg(true), jpeg(gray = true), jpeg(true, true)).forEach {
            assertTrue(PersonalImageIntegrity.isComplete(it, "image/jpeg"))
        }
    }

    @Test fun rejectsPartialScanEvenWhenOriginalHeadersAndLegalEoiSuffixRemain() {
        listOf(jpeg(), jpeg(true), jpeg(gray = true), jpeg(true, true)).forEach { original ->
            scans(original).forEach { scan ->
                val middle = scan.first + (scan.last - scan.first) / 2
                val truncated = original.copyOf(middle) + byteArrayOf(0xff.toByte(), 0xd9.toByte())
                assertFalse("accepted truncated scan $scan", PersonalImageIntegrity.isComplete(truncated, "image/jpeg"))
            }
        }
    }

    private fun syntheticContainerTails(): List<ByteArray> {
        // Structural container fixtures only: no camera capture, real video track, or claim of HDR rendering.
        // Motion Photo permits primary-image padding followed by an ISO-BMFF video container.
        val ftyp = java.nio.ByteBuffer.allocate(24).putInt(24).put("ftyp".toByteArray())
            .put("isom".toByteArray()).putInt(0).put("isom".toByteArray()).put("mp42".toByteArray()).array()
        val mdat = java.nio.ByteBuffer.allocate(12).putInt(12).put("mdat".toByteArray()).putInt(0).array()
        // Ultra HDR's primary-JPEG reader must also permit a second JPEG used for gain-map data.
        return listOf(ByteArray(16) + ftyp + mdat, jpeg(gray=true), jpeg(gray=true) + ByteArray(8) + ftyp + mdat)
    }

    @Test fun acceptsCompleteRealPrimaryJpegsWithMotionPhotoAndUltraHdrStyleTails() {
        val real = File("src/main/assets/builtin/brands").listFiles()!!.filter { it.extension == "jpg" }.map { it.readBytes() }
        (real + listOf(jpeg(true))).forEach { primary ->
            syntheticContainerTails().forEach { tail ->
                assertTrue(PersonalImageIntegrity.isComplete(primary + tail,"image/jpeg"))
            }
        }
    }

    @Test fun rejectsTruncatedPrimaryEvenWhenValidEoiAndCompleteGainmapOrVideoTailFollow() {
        val real = File("src/main/assets/builtin/brands/with_puji.jpg").readBytes()
        listOf(real,jpeg(true)).forEach { primary ->
            val scan=scans(primary).first()
            val broken=primary.copyOf(scan.first+(scan.last-scan.first)/2)+byteArrayOf(255.toByte(),0xd9.toByte())
            syntheticContainerTails().forEach { tail ->
                assertFalse(PersonalImageIntegrity.isComplete(broken+tail,"image/jpeg"))
            }
        }
    }

    @Test fun allPackagedRealJpegAssetsPassButEntropyTruncationDoesNot() {
        val files = File("src/main/assets/builtin/brands").listFiles()!!.filter { it.extension == "jpg" }
        assertEquals(13, files.size)
        files.forEach { file ->
            val bytes = file.readBytes()
            assertTrue(file.name, PersonalImageIntegrity.isComplete(bytes, "image/jpeg"))
            val scan = scans(bytes).first()
            assertFalse(file.name, PersonalImageIntegrity.isComplete(bytes.copyOf(scan.first + (scan.last - scan.first) / 3) +
                byteArrayOf(0xff.toByte(), 0xd9.toByte()), "image/jpeg"))
        }
    }

    private fun segment(marker: Int, data: ByteArray): ByteArray =
        byteArrayOf(255.toByte(), marker.toByte(), ((data.size + 2) shr 8).toByte(), (data.size + 2).toByte()) + data

    private fun syntheticJpeg(progressive: Boolean): ByteArray {
        val dc = byteArrayOf(0,1) + ByteArray(15) + byteArrayOf(0)
        val ac = if (progressive) byteArrayOf(0x10,1,1,1,1) + ByteArray(12) + byteArrayOf(0xf0.toByte(),1,0x21,0)
            else byteArrayOf(0x10,1) + ByteArray(15) + byteArrayOf(0)
        val start = byteArrayOf(255.toByte(),0xd8.toByte()) + segment(0xdb,byteArrayOf(0)+ByteArray(64) { 1 }) +
            segment(0xc4,dc+ac) + segment(if (progressive) 0xc2 else 0xc0,
                byteArrayOf(8,0,8,0,if (progressive) 8 else 16,1,1,0x11,0))
        fun scan(first: Int, last: Int, approximation: Int) = segment(0xda,byteArrayOf(1,1,0,first.toByte(),last.toByte(),approximation.toByte()))
        val data = if (progressive) {
            // Initial nonzero at coefficient17; refinement ZRL ends immediately before that old coefficient.
            scan(0,0,0) + byteArrayOf(0x7f) + scan(1,63,1) + byteArrayOf(0x5e) +
                scan(1,63,0x10) + byteArrayOf(0x6f,0xbf.toByte())
        } else segment(0xdd,byteArrayOf(0,1)) + scan(0,63,0) + byteArrayOf(0x3f,255.toByte(),0xd0.toByte(),0x3f)
        return start + data + byteArrayOf(255.toByte(),0xd9.toByte())
    }

    @Test fun restartIntervalsAndProgressiveZeroRunsConsumeTheirExactEntropy() {
        listOf(false,true).forEach {
            val bytes = syntheticJpeg(it)
            assertTrue("progressive=$it",PersonalImageIntegrity.isComplete(bytes,"image/jpeg"))
        }
        val wrongRestart = syntheticJpeg(false).also { bytes ->
            val at = bytes.indices.first { it + 1 < bytes.size && bytes[it] == 255.toByte() && bytes[it+1] == 0xd0.toByte() }
            bytes[at+1] = 0xd1.toByte()
        }
        assertFalse(PersonalImageIntegrity.isComplete(wrongRestart,"image/jpeg"))
    }

    @Test fun rejectsCompleteImagesAbovePersonalPhotoPixelBudget() {
        assertFalse(PersonalImageIntegrity.isComplete(fixture("over-36mp.jpg"),"image/jpeg"))
        assertFalse(PersonalImageIntegrity.isComplete(fixture("over-36mp.png"),"image/png"))
    }

    @Test fun rejectsLegalLargeProgressiveImageWhoseRepeatedRefinementExceedsWorkBudget() {
        // Independent JDK ImageIO generation script verifies this DC-only 36MP progressive fixture.
        assertFalse(PersonalImageIntegrity.isComplete(fixture("over-work-budget.jpg"),"image/jpeg"))
    }

    @Test fun malformedShortRandomInputsNeverEscapeTheIntegrityGate() {
        val random = java.util.Random(1)
        repeat(512) {
            val bytes = ByteArray(random.nextInt(128)).also { random.nextBytes(it) }
            assertFalse(PersonalImageIntegrity.isComplete(bytes,"image/jpeg"))
            assertFalse(PersonalImageIntegrity.isComplete(bytes,"image/png"))
        }
    }

    @Test fun acceptsValidPngIncludingAdam7IndexedGrayscaleAndAlpha() {
        listOf("rgba", "indexed", "gray", "binary").forEach { type ->
            listOf(false, true).forEach { interlaced ->
                val bytes = png(interlaced, type)
                assertTrue("type=$type Adam7=$interlaced", PersonalImageIntegrity.isComplete(bytes, "image/png"))
            }
        }
    }

    private fun shortenedIdat(bytes: ByteArray): ByteArray {
        var at = 8
        val out = ByteArrayOutputStream().apply { write(bytes, 0, 8) }
        while (at < bytes.size) {
            val size = java.nio.ByteBuffer.wrap(bytes, at, 4).int
            val type = bytes.copyOfRange(at + 4, at + 8)
            if (String(type, Charsets.US_ASCII) == "IDAT") {
                val data = bytes.copyOfRange(at + 8, at + 8 + size / 2)
                out.write(java.nio.ByteBuffer.allocate(4).putInt(data.size).array())
                out.write(type); out.write(data)
                val crc = CRC32().apply { update(type); update(data) }
                out.write(java.nio.ByteBuffer.allocate(4).putInt(crc.value.toInt()).array())
            } else out.write(bytes, at, size + 12)
            at += size + 12
        }
        return out.toByteArray()
    }

    @Test fun acceptsPngWhenZlibHeaderAndBodySpanVerySmallIdatChunks() {
        val bytes = png()
        val out = ByteArrayOutputStream().apply { write(bytes,0,8) }
        var at = 8
        while (at < bytes.size) {
            val size = java.nio.ByteBuffer.wrap(bytes,at,4).int
            val type = bytes.copyOfRange(at+4,at+8)
            if (String(type,Charsets.US_ASCII)=="IDAT") {
                bytes.copyOfRange(at+8,at+8+size).forEach { value ->
                    out.write(byteArrayOf(0,0,0,1)); out.write(type); out.write(value.toInt())
                    val crc=CRC32().apply { update(type); update(value.toInt()) }
                    out.write(java.nio.ByteBuffer.allocate(4).putInt(crc.value.toInt()).array())
                }
            } else out.write(bytes,at,size+12)
            at+=size+12
        }
        val split=out.toByteArray()
        assertTrue(PersonalImageIntegrity.isComplete(split,"image/png"))
    }

    @Test fun rejectsTruncatedIdatWithRecomputedChunkCrcAndValidIend() {
        listOf(png(), png(true)).forEach {
            val broken = shortenedIdat(it)
            assertTrue(broken.takeLast(12) == it.takeLast(12))
            assertFalse(PersonalImageIntegrity.isComplete(broken, "image/png"))
        }
    }

    @Test fun rejectsBadCrcMissingTrailerUnknownLegacyFormatsAndMalformedHeaders() {
        val valid = png()
        val corrupt = valid.copyOf().also { it[20] = (it[20].toInt() xor 1).toByte() }
        assertFalse(PersonalImageIntegrity.isComplete(corrupt, "image/png"))
        assertFalse(PersonalImageIntegrity.isComplete(valid.copyOf(valid.size - 4), "image/png"))
        listOf("image/webp", "image/gif", "image/heif", null).forEach {
            assertFalse(PersonalImageIntegrity.isComplete(valid, it))
        }
        assertFalse(PersonalImageIntegrity.isComplete(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte()), "image/jpeg"))
    }
}
