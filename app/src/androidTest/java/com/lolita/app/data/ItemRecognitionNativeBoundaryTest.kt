package com.lolita.app.data

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lolita.app.data.file.ImageRegionCropper
import com.lolita.app.data.file.NormalizedCropRect
import com.lolita.app.data.remote.ItemRecognitionApi
import com.lolita.app.data.remote.RecognitionApiFailure
import com.lolita.app.domain.model.RecognitionRegion
import com.lolita.app.domain.model.RecognitionRequest
import com.lolita.app.domain.model.WardrobeFamily
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

/** Android pixel encoder -> real cropper -> production API. No sockets and no user credentials. */
class ItemRecognitionNativeBoundaryTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun responseProbeKeepsOnlyShapeAndFixedEnumValues() {
        val response = """{"id":"PRIVATE_SERVER_ID","object":"response","status":"completed","model":"doubao-seed-2-1-pro-260915","private_field":"PRIVATE_KEY_AND_PATH","output":[{"id":"PRIVATE_MESSAGE_ID","type":"message","role":"assistant","status":"completed","content":[{"type":"output_text","text":"{\"version\":1,\"candidates\":[{\"id\":\"PRIVATE_CANDIDATE\",\"field\":\"WIG_COLOR\",\"source\":\"VISUAL\",\"value\":\"PRIVATE_VALUE\",\"quote\":\"PRIVATE_QUOTE\",\"regionIds\":[\"PRIVATE_REGION\"],\"variant\":null,\"sizeRow\":null,\"measurement\":null}]}"}]}]}"""
        val shape = RecognitionSafeResponseProbe.shape(response.toByteArray()).toString()
        assertFalse(shape.contains("PRIVATE_"))
        assertTrue(shape.contains("candidateCount") && shape.contains("WIG_COLOR"))
        assertTrue(shape.contains("missingMeasurementCount"))
        val injected = JsonParser.parseString(response).asJsonObject.apply {
            addProperty("private_value_encoded_as_lowercase_key", true)
            addProperty("model", "PRIVATE_MODEL_VALUE")
            this["output"].asJsonArray[0].asJsonObject.apply {
                addProperty("private_output_key", "PRIVATE_OUTPUT_VALUE")
                addProperty("type", "PRIVATE_TYPE"); addProperty("role", "PRIVATE_ROLE"); addProperty("status", "PRIVATE_STATUS")
            }
        }
        val safe = RecognitionSafeResponseProbe.shape(injected.toString().toByteArray()).toString()
        assertFalse(safe.contains("PRIVATE_"))
        assertFalse(safe.contains("private_value_encoded_as_lowercase_key"))
        assertFalse(safe.contains("private_output_key"))
        assertTrue(safe.contains("unknownFieldCount") && safe.contains("OTHER"))
    }

    @Test fun nativeBitmapJpegCropReachesRecordingTransportWithoutSourceMetadata() = runBlocking {
        val directory = File(target.cacheDir, "recognition-native-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getCacheDir() = File(directory, "cache").apply { mkdirs() }
        }
        try {
            val source = File(directory, "private-source.png")
            val bitmap = Bitmap.createBitmap(80, 100, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(Color.GREEN)
                source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle() }
            val sourceHash = sha(source.readBytes())
            val crop = ImageRegionCropper.crop(context, source.path, NormalizedCropRect(.1f, .2f, .9f, .8f))
            val bytes = File(crop.path).readBytes()
            val markers = jpegMarkers(bytes)
            // This synthetic all-green image contains no user data and can reproduce encoder bytes on host.
            File(target.filesDir, "recognition-native-synthetic.jpg").writeBytes(bytes)
            // Only structural marker numbers and pixel dimensions are persisted for diagnostics.
            val evidence = File(target.filesDir, "recognition-native-boundary.json")
            evidence.writeText(JsonObject().apply {
                addProperty("width", crop.width); addProperty("height", crop.height)
                addProperty("bytes", bytes.size); addProperty("sha256", sha(bytes))
                add("markers", com.google.gson.Gson().toJsonTree(markers))
            }.toString())
            val request = RecognitionRequest(draftId = UUID.randomUUID().toString(), targetItemId = null,
                regions = listOf(RecognitionRegion("native-crop", sha(bytes), crop.width, crop.height)),
                baseline = emptyMap(), family = WardrobeFamily.WIG)
            val transport = RecordingConnection()
            var opened = 0
            val result = runCatching { ItemRecognitionApi({ url ->
                assertEquals(ItemRecognitionApi.ENDPOINT, url.toString()); opened++; transport
            }).recognize("controlled-native-dummy-key", request, listOf(bytes)) }
            val diagnostics = JsonParser.parseString(evidence.readText()).asJsonObject.apply {
                addProperty("opened", opened)
                (result.exceptionOrNull() as? RecognitionApiFailure)?.let {
                    addProperty("failureKind", it.kind.name); it.stage?.let { stage -> addProperty("failureStage", stage.name) }
                }
            }
            evidence.writeText(diagnostics.toString())
            assertEquals("Android JPEG marker list: $markers; failure type: ${result.exceptionOrNull()?.javaClass?.simpleName}", 1, opened)
            assertTrue("Production API must accept its own cropper output", result.getOrThrow().candidates.isEmpty())
            val payload = JsonParser.parseString(transport.sent.toString("UTF-8")).asJsonObject
            val images = payload["input"].asJsonArray.single().asJsonObject["content"].asJsonArray
                .filter { it.asJsonObject["type"].asString == "input_image" }
            assertEquals(1, images.size)
            val uploaded = Base64.getDecoder().decode(images.single().asJsonObject["image_url"].asString.substringAfter(','))
            assertArrayEquals(bytes, uploaded)
            assertFalse(transport.sent.toString("UTF-8").contains(source.path))
            assertEquals(sourceHash, sha(source.readBytes()))
            assertFalse("No APP1..APP15 or comment markers", markers.any { it in (225..239).map { marker -> "%02X".format(marker) } + "FE" })
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            try {
                assertTrue(decoded.colorSpace!!.isSrgb)
                val pixel = decoded.getPixel(decoded.width / 2, decoded.height / 2)
                assertTrue(Color.green(pixel) > 230 && Color.red(pixel) < 15 && Color.blue(pixel) < 15)
            } finally { decoded.recycle() }
            assertTrue(transport.closed)
        } finally { directory.deleteRecursively() }
    }

    @Test fun wideGamutSourceIsConvertedToSrgbBeforeItsEncoderProfileIsRemoved() = runBlocking {
        val directory = File(target.cacheDir, "recognition-p3-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getCacheDir() = File(directory, "cache").apply { mkdirs() }
        }
        try {
            val source = File(directory, "display-p3.png")
            val bitmap = Bitmap.createBitmap(80, 100, Bitmap.Config.ARGB_8888, true, ColorSpace.get(ColorSpace.Named.DISPLAY_P3))
            try {
                assertFalse(bitmap.colorSpace!!.isSrgb)
                Canvas(bitmap).drawColor(Color.rgb(80, 150, 200))
                source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle() }
            val sourceBytes = source.readBytes()
            val roundtrip = BitmapFactory.decodeFile(source.path)
            try { assertFalse("Test fixture must actually retain its wide-gamut profile", roundtrip.colorSpace!!.isSrgb) }
            finally { roundtrip.recycle() }
            val crop = ImageRegionCropper.crop(context, source.path, NormalizedCropRect.FULL)
            val bytes = File(crop.path).readBytes()
            assertFalse(jpegMarkers(bytes).contains("E2"))
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            try {
                assertTrue(decoded.colorSpace!!.isSrgb)
                val pixel = decoded.getPixel(40, 50)
                assertEquals(80.0, Color.red(pixel).toDouble(), 5.0)
                assertEquals(150.0, Color.green(pixel).toDouble(), 5.0)
                assertEquals(200.0, Color.blue(pixel).toDouble(), 5.0)
            } finally { decoded.recycle() }
            assertArrayEquals(sourceBytes, source.readBytes())
        } finally { directory.deleteRecursively() }
    }

    private class RecordingConnection : HttpURLConnection(URL(ItemRecognitionApi.ENDPOINT)) {
        val sent = ByteArrayOutputStream()
        var closed = false
        override fun connect() = Unit
        override fun usingProxy() = false
        override fun disconnect() { closed = true }
        override fun getOutputStream() = sent
        override fun getResponseCode() = 200
        override fun getInputStream() = ByteArrayInputStream("""{"object":"response","status":"completed","model":"doubao-seed-2-1-pro-260915","store":false,"output":[{"type":"message","role":"assistant","status":"completed","content":[{"type":"output_text","text":"{\"version\":1,\"candidates\":[]}"}]}]}""".toByteArray())
    }

    companion object {
        internal fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        internal fun jpegMarkers(bytes: ByteArray): List<String> {
            fun byte(i: Int) = bytes[i].toInt() and 255
            val markers = mutableListOf<String>()
            var offset = 2
            while (offset + 1 < bytes.size) {
                require(byte(offset++) == 255)
                while (offset < bytes.size && byte(offset) == 255) offset++
                val marker = byte(offset++)
                markers += "%02X".format(marker)
                if (marker == 218 || marker == 217) break
                val length = byte(offset) * 256 + byte(offset + 1)
                require(length >= 2 && offset + length <= bytes.size)
                offset += length
            }
            return markers
        }
    }
}
