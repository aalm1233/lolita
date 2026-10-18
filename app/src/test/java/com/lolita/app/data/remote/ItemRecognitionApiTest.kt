package com.lolita.app.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lolita.app.domain.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.InetAddress
import java.net.URL
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Real HTTP loopback only. No test can contact an external endpoint or use a user's key. */
class ItemRecognitionApiTest {
    // Real 8x10 RGB JPEG, generated once with JDK ImageIO; Android host compilation has no java.desktop.
    private val jpeg: ByteArray get() = Base64.getDecoder().decode("/9j/4AAQSkZJRgABAgAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAAKAAgDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD5/ooooA//2Q==")
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun request(bytes: ByteArray = jpeg) = RecognitionRequest(draftId = UUID.randomUUID().toString(), targetItemId = 93,
        regions = listOf(RecognitionRegion("region-crop", sha(bytes), 8, 10)), baseline = mapOf(RecognitionField.NAME to "/private/original/full-image.jpg secret-baseline"), family = WardrobeFamily.WIG)
    private fun result() = RecognitionResult(candidates = listOf(RecognitionCandidate("candidate-1", RecognitionField.WIG_COLOR,
        "银灰", RecognitionSource.VISUAL, listOf("region-crop"), "", null, null)))
    private fun response(text: String = result().encode(), status: String = "completed") = JsonObject().apply {
        addProperty("id", "resp-controlled"); addProperty("object", "response"); addProperty("status", status)
        add("output", com.google.gson.JsonArray().apply { add(JsonObject().apply {
            addProperty("id", "msg-controlled"); addProperty("type", "message"); addProperty("role", "assistant"); addProperty("status", "completed")
            add("content", com.google.gson.JsonArray().apply { add(JsonObject().apply {
                addProperty("type", "output_text"); addProperty("text", text); add("annotations", com.google.gson.JsonArray())
            }) })
        }) })
    }.toString()

    private class Loopback(private val code: Int, private val response: String) : AutoCloseable {
        val calls = AtomicInteger()
        @Volatile var payload = ""
        @Volatile var authorization = ""
        val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        private val executor = Executors.newSingleThreadExecutor { Thread(it, "recognition-loopback").apply { isDaemon = true } }
        init {
            executor.submit {
                while (!server.isClosed) {
                    try {
                        server.accept().use { socket ->
                            calls.incrementAndGet()
                            socket.soTimeout = 3000
                            val input = socket.getInputStream().buffered()
                            fun line(): String {
                                val output = ByteArrayOutputStream()
                                while (true) {
                                    val ch = input.read()
                                    check(ch >= 0 && output.size() <= 8192)
                                    if (ch == 10) break
                                    if (ch != 13) output.write(ch)
                                }
                                return output.toString("UTF-8")
                            }
                            check(line().startsWith("POST "))
                            val headers = mutableMapOf<String, String>()
                            while (true) { val row = line(); if (row.isEmpty()) break; headers[row.substringBefore(':').lowercase()] = row.substringAfter(':').trim() }
                            val size = headers.getValue("content-length").toInt()
                            check(size in 1..20 * 1024 * 1024)
                            val body = ByteArray(size)
                            var offset = 0
                            while (offset < size) { val count = input.read(body, offset, size - offset); check(count > 0); offset += count }
                            payload = body.toString(Charsets.UTF_8)
                            authorization = headers["authorization"].orEmpty()
                            val result = response.toByteArray()
                            val redirect = if (code in 300..399) "Location: http://127.0.0.1:${server.localPort}/redirect-must-not-follow\r\n" else ""
                            val header = "HTTP/1.1 $code Controlled\r\nContent-Length: ${result.size}\r\nConnection: close\r\n$redirect\r\n"
                            socket.getOutputStream().use { it.write(header.toByteArray()); it.write(result); it.flush() }
                        }
                    } catch (_: Exception) { /* A client may deliberately disconnect an oversized response. */ }
                }
            }
        }
        fun connection(official: URL): HttpURLConnection {
            assertEquals("https://api.openai.com/v1/responses", official.toString())
            return URL("http://127.0.0.1:${server.localPort}/v1/responses").openConnection() as HttpURLConnection
        }
        override fun close() { server.close(); executor.shutdownNow() }
    }

    @Test fun actualHttpPayloadContainsOnlyCropPixelsRegionLabelsAndStrictSchema() = runBlocking {
        val bytes = jpeg
        val request = request(bytes)
        Loopback(200, response()).use { server ->
            assertEquals(result(), ItemRecognitionApi(server::connection).recognize("dummy-recognition-key", request, listOf(bytes)))
            val root = JsonParser.parseString(server.payload).asJsonObject
            assertEquals("gpt-4.1-mini", root["model"].asString)
            assertFalse(root["store"].asBoolean)
            assertEquals(6000, root["max_output_tokens"].asInt)
            assertFalse(root.has("tools")); assertFalse(root.has("previous_response_id")); assertFalse(root.has("conversation"))
            assertFalse(server.payload.contains("dummy-recognition-key"))
            assertFalse(server.payload.contains("secret-baseline")); assertFalse(server.payload.contains("/private"))
            assertFalse(server.payload.contains(request.draftId)); assertFalse(server.payload.contains("targetItemId"))
            val format = root["text"].asJsonObject["format"].asJsonObject
            assertEquals("json_schema", format["type"].asString); assertTrue(format["strict"].asBoolean)
            assertEquals(RecognitionResult.schema(), format["schema"])
            val content = root["input"].asJsonArray.single().asJsonObject["content"].asJsonArray
            val inputs = content.filter { it.asJsonObject["type"].asString == "input_image" }
            assertEquals(1, inputs.size)
            val url = inputs.single().asJsonObject["image_url"].asString
            assertTrue(url.startsWith("data:image/jpeg;base64,"))
            assertArrayEquals(bytes, Base64.getDecoder().decode(url.substringAfter(',')))
            assertTrue(server.payload.contains("region-crop")); assertTrue(server.payload.contains("appearanceOnly"))
            assertEquals("Bearer dummy-recognition-key", server.authorization)
            assertEquals(1, server.calls.get())
        }
    }

    @Test fun redirectsAuthErrorsAndRateLimitsNeverRetryOrLeakBodies() = runBlocking {
        val bytes = jpeg
        for (code in listOf(302, 307, 401, 403, 402, 429, 500)) Loopback(code, "Bearer dummy-recognition-key /private/source.jpg sensitive-server-body").use { server ->
            val failure = runCatching { ItemRecognitionApi(server::connection).recognize("dummy-recognition-key", request(bytes), listOf(bytes)) }.exceptionOrNull()
            assertTrue(failure is RecognitionApiFailure)
            assertNull(failure!!.cause)
            assertFalse(failure.toString().contains("dummy-recognition-key")); assertFalse(failure.toString().contains("sensitive-server-body")); assertFalse(failure.toString().contains("/private"))
            assertEquals(1, server.calls.get())
        }
    }

    @Test fun incompleteRefusalMultipleMessagesUnknownFieldsAndBadJsonAreRejected() = runBlocking {
        val bytes = jpeg
        val valid = JsonParser.parseString(response()).asJsonObject
        val refusal = JsonParser.parseString(response()).asJsonObject.also {
            it["output"].asJsonArray[0].asJsonObject["content"].asJsonArray[0].asJsonObject.apply { addProperty("type", "refusal"); addProperty("refusal", "sensitive-refusal") }
        }.toString()
        val multiple = valid.deepCopy().also { it["output"].asJsonArray.add(it["output"].asJsonArray[0].deepCopy()) }.toString()
        val unknownCandidate = JsonParser.parseString(result().encode()).asJsonObject.also { it["candidates"].asJsonArray[0].asJsonObject.addProperty("payment", "sensitive-value") }.toString()
        for (body in listOf("not-json-sensitive", "{}", response(status = "incomplete"), refusal, multiple,
            response(text = unknownCandidate), response(text = result().encode().dropLast(1) + ",\"version\":1}"),
            valid.deepCopy().also { it.addProperty("unknownEnvelope", "sensitive-value") }.toString())) {
            Loopback(200, body).use { server ->
                val failure = runCatching { ItemRecognitionApi(server::connection).recognize("dummy-key", request(bytes), listOf(bytes)) }.exceptionOrNull()
                assertTrue(body.take(40), failure is RecognitionApiFailure)
                assertNull(failure!!.cause)
                assertFalse(failure.toString().contains("sensitive"))
                assertEquals(1, server.calls.get())
            }
        }
    }

    @Test fun malformedOversizeUnmatchedAndMetadataImagesNeverOpenTransport() = runBlocking {
        val bytes = jpeg
        val exif = bytes.take(2).toByteArray() + byteArrayOf(0xff.toByte(), 0xe1.toByte(), 0, 8) + "Exif\u0000\u0000".toByteArray() + bytes.drop(2).toByteArray()
        val wrongShape = request(bytes).copy(regions = listOf(request(bytes).regions.single().copy(width = 9)))
        val wrongSha = request(bytes).copy(regions = listOf(request(bytes).regions.single().copy(sha256 = "0".repeat(64))))
        var opened = 0
        val api = ItemRecognitionApi({ opened++; error("transport-must-not-open") })
        val cases = listOf(request(bytes) to emptyList(), request(bytes) to listOf("not-jpeg".toByteArray()),
            request(exif) to listOf(exif), wrongShape to listOf(bytes), wrongSha to listOf(bytes),
            request(bytes) to listOf(ByteArray(3 * 1024 * 1024 + 1)))
        for ((request, regions) in cases) {
            val failure = runCatching { api.recognize("dummy-key", request, regions) }.exceptionOrNull()
            assertTrue(failure is RecognitionApiFailure)
            assertNull(failure!!.cause)
        }
        assertEquals(0, opened)
    }

    @Test fun invalidKeysFiveRegionLimitAndCumulativeBudgetNeverConnect() = runBlocking {
        var opened = 0
        val api = ItemRecognitionApi({ opened++; error("must not open") })
        val bytes = jpeg
        for (key in listOf("", " ", "secret\nheader", "x".repeat(513))) {
            assertTrue(runCatching { api.recognize(key, request(bytes), listOf(bytes)) }.exceptionOrNull() is RecognitionApiFailure)
        }
        val region = request(bytes).regions.single()
        val six = request(bytes).copy(regions = (1..6).map { region.copy(id = "region-$it") })
        assertTrue(runCatching { api.recognize("dummy-key", six, List(6) { bytes }) }.exceptionOrNull() is RecognitionApiFailure)
        val five = request(bytes).copy(regions = (1..5).map { region.copy(id = "region-$it") })
        assertTrue(runCatching { api.recognize("dummy-key", five, List(5) { ByteArray(3 * 1024 * 1024) }) }.exceptionOrNull() is RecognitionApiFailure)
        assertEquals(0, opened)
    }

    @Test fun appearanceOnlyRegionsCannotBecomeTextSpecificationEvidence() = runBlocking {
        val bytes = jpeg
        val request = request(bytes).let { it.copy(regions = listOf(it.regions.single().copy(appearanceOnly = true))) }
        val forbidden = RecognitionResult(candidates = listOf(RecognitionCandidate("c", RecognitionField.WIG_LENGTH, "60 cm", RecognitionSource.TEXT,
            listOf("region-crop"), "60 cm")))
        Loopback(200, response(forbidden.encode())).use { server ->
            assertTrue(runCatching { ItemRecognitionApi(server::connection).recognize("dummy-key", request, listOf(bytes)) }.exceptionOrNull() is RecognitionApiFailure)
            assertEquals(1, server.calls.get())
        }
    }

    @Test fun oversizeResponsesAreBoundedAndSanitized() = runBlocking {
        val bytes = jpeg
        Loopback(200, "sensitive-" + "x".repeat(1024 * 1024)).use { server ->
            val failure = runCatching { ItemRecognitionApi(server::connection).recognize("dummy-key", request(bytes), listOf(bytes)) }.exceptionOrNull()
            assertTrue(failure is RecognitionApiFailure)
            assertNull(failure!!.cause)
            assertFalse(failure.toString().contains("sensitive"))
        }
    }

    private open class BlockingTransport : HttpURLConnection(URL("https://api.openai.com/v1/responses")) {
        val entered = CompletableDeferred<Unit>()
        val released = CountDownLatch(1)
        val sent = ByteArrayOutputStream()
        @Volatile var closed = false
        override fun connect() = Unit
        override fun usingProxy() = false
        override fun getOutputStream() = sent
        override fun getResponseCode(): Int { entered.complete(Unit); released.await(5, TimeUnit.SECONDS); return 200 }
        override fun getInputStream() = ByteArrayInputStream(ByteArray(0))
        override fun disconnect() { closed = true; released.countDown() }
    }

    @Test fun coroutineCancellationDisconnectsRequestWithoutRetry() = runBlocking {
        val bytes = jpeg
        val transport = BlockingTransport()
        var opened = 0
        val job = async { ItemRecognitionApi({ opened++; transport }).recognize("dummy-key", request(bytes), listOf(bytes)) }
        withTimeout(2000) { transport.entered.await() }
        job.cancelAndJoin()
        assertTrue(transport.closed); assertEquals(1, opened)
    }

    @Test fun totalTimeoutDisconnectsAndReturnsOnlyFixedChineseError() = runBlocking {
        val bytes = jpeg
        val transport = BlockingTransport()
        val failure = runCatching { ItemRecognitionApi({ transport }, timeoutMillis = 100).recognize("dummy-key", request(bytes), listOf(bytes)) }.exceptionOrNull()
        assertTrue(failure is RecognitionApiFailure)
        assertEquals(RecognitionApiFailureKind.TIMEOUT, (failure as RecognitionApiFailure).kind)
        assertNull(failure.cause); assertTrue(transport.closed)
    }
}
