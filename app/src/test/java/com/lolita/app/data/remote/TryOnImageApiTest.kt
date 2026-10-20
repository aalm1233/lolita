package com.lolita.app.data.remote

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TryOnImageApiTest {
    private open class Transport(val code: Int = 200, val body: String = """{"created":1,"data":[{"b64_json":"aW1hZ2U="}]}""") : HttpURLConnection(URL("https://api.openai.com/v1/images/edits")) {
        val uploaded = ByteArrayOutputStream()
        @Volatile var closed = false
        override fun connect() = Unit
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getOutputStream() = uploaded
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray())
        override fun getResponseCode() = code
    }

    @Test fun uploadsEveryReferenceOnceToOfficialEndpointAndDecodesResult() = runBlocking {
        val transport = Transport()
        var url = ""
        val api = TryOnImageApi { requested -> url = requested.toString(); transport }
        val result = api.generate("private-test-key", listOf("MODEL".toByteArray(), "DRESS".toByteArray(), "CUFFS".toByteArray()), "preserve model")
        assertEquals("https://api.openai.com/v1/images/edits", url)
        val body = transport.uploaded.toString("UTF-8")
        assertTrue(body.indexOf("MODEL") < body.indexOf("DRESS"))
        assertTrue(body.indexOf("DRESS") < body.indexOf("CUFFS"))
        assertEquals(3, Regex("name=\"image\\[\\]\"").findAll(body).count())
        assertTrue(body.contains("name=\"model\"\r\n\r\ngpt-image-2.5-sunburst\r\n"))
        assertTrue(body.contains("name=\"n\"\r\n\r\n1\r\n"))
        assertTrue(body.contains("name=\"quality\"\r\n\r\nmedium\r\n"))
        assertTrue(body.contains("name=\"size\"\r\n\r\n1024x1536\r\n"))
        assertTrue(body.contains("name=\"output_format\"\r\n\r\npng\r\n"))
        assertFalse(body.contains("private-test-key"))
        assertFalse(transport.instanceFollowRedirects)
        assertArrayEquals("image".toByteArray(), result)
        assertTrue(transport.closed)
    }
    @Test fun mixedPngAndJpegReferencesKeepTheirBytesAndMatchingMultipartTypes() = runBlocking {
        val png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jgXcAAAAASUVORK5CYII=")
        val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte())
        val transport = Transport()
        TryOnImageApi { transport }.generate("test-key", listOf(png, jpeg), "keep details")
        val body = transport.uploaded.toByteArray().toString(Charsets.ISO_8859_1)
        assertTrue(body.contains("filename=\"reference-0.png\"\r\nContent-Type: image/png\r\n\r\n${png.toString(Charsets.ISO_8859_1)}\r\n"))
        assertTrue(body.contains("filename=\"reference-1.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n${jpeg.toString(Charsets.ISO_8859_1)}\r\n"))
        assertEquals(2, Regex("name=\"image\\[\\]\"").findAll(body).count())
    }

    @Test fun accountModelAndMalformedFailuresAreTypedWithoutRawResponseText() = runBlocking {
        for ((status, kind) in listOf(401 to "AUTH", 403 to "AUTH", 402 to "QUOTA", 429 to "RATE_LIMIT", 500 to "MODEL", 302 to "MODEL", 200 to "INVALID_IMAGE")) {
            val failure = runCatching { TryOnImageApi { Transport(status, "Bearer private https://secret.invalid") }.generate("controlled-key", listOf(byteArrayOf(1), byteArrayOf(2)), "p") }.exceptionOrNull()
            assertTrue(failure is com.lolita.app.domain.model.ImageGenerationFailure)
            assertEquals(kind, (failure as com.lolita.app.domain.model.ImageGenerationFailure).kind.name)
            assertFalse(failure.toString().contains("Bearer")); assertFalse(failure.toString().contains("secret.invalid"))
        }
    }
    @Test fun authAndRedirectErrorsNeverExposeServerBodyOrRetry() = runBlocking {
        for (code in listOf(401,402,429,302,500)) {
            var calls = 0
            val transport = Transport(code, "private-test-key secret server detail")
            val api = TryOnImageApi { calls++; transport }
            try {
                api.generate("private-test-key", listOf(byteArrayOf(1),byteArrayOf(2)), "prompt")
                fail("HTTP $code must fail")
            } catch (expected: IllegalStateException) {
                assertFalse(expected.message.orEmpty().contains("private-test-key"))
                assertFalse(expected.message.orEmpty().contains("secret server detail"))
            }
            assertEquals(1,calls)
            assertTrue(transport.closed)
        }
    }
    @Test fun rejectsEmptyMalformedAndRemoteUrlOnlyResponses() = runBlocking {
        for (body in listOf("", "not-json", "{}", "{\"data\":[]}", "{\"data\":[{\"b64_json\":\"\"}]}", "{\"data\":[{\"b64_json\":\"%%%%\"}]}", "{\"data\":[{\"url\":\"https://bad.example/a.png\"}]}")) {
            try {
                TryOnImageApi { Transport(body = body) }.generate("test-key",listOf(byteArrayOf(1),byteArrayOf(2)),"prompt")
                fail("Invalid response must fail")
            } catch (expected: IllegalStateException) { assertTrue(expected.message.orEmpty().isNotEmpty()) }
        }
    }

    @Test fun invalidKeyNeverOpensTransport() = runBlocking {
        var connections = 0
        val api = TryOnImageApi { connections++; Transport() }
        for (key in listOf("", " ", "sk key", "sk-key\nheader", "x".repeat(513))) {
            try {
                api.generate(key, listOf(byteArrayOf(1), byteArrayOf(2)), "prompt")
                fail("invalid key accepted")
            } catch (_: IllegalArgumentException) { }
        }
        assertEquals(0, connections)
    }

    @Test fun rejectsDeclaredOversizeResponseBeforeReadingIt() = runBlocking {
        var read = false
        val transport = object : Transport() {
            override fun getContentLengthLong() = 49L * 1024 * 1024
            override fun getInputStream(): ByteArrayInputStream {
                read = true
                return super.getInputStream()
            }
        }
        try {
            TryOnImageApi { transport }.generate("test-key", listOf(byteArrayOf(1), byteArrayOf(2)), "prompt")
            fail("oversized result accepted")
        } catch (_: IllegalStateException) { }
        assertFalse(read)
        assertTrue(transport.closed)
    }

    @Test fun cancellingWaitingRequestDisconnectsTransportAndDoesNotRetry() = runBlocking {
        val waiting = CompletableDeferred<Unit>()
        val disconnected = CountDownLatch(1)
        var connections = 0
        val transport = object : Transport() {
            override fun getResponseCode(): Int {
                waiting.complete(Unit)
                check(disconnected.await(2, TimeUnit.SECONDS)) { "cancellation did not disconnect" }
                return 200
            }
            override fun disconnect() { super.disconnect(); disconnected.countDown() }
        }
        val running = async { TryOnImageApi { connections++; transport }.generate("test-key", listOf(byteArrayOf(1), byteArrayOf(2)), "prompt") }
        withTimeout(2_000) { waiting.await() }
        running.cancelAndJoin()
        assertTrue(transport.closed)
        assertEquals(1, connections)
        assertTrue(running.isCancelled)
    }

    @Test fun cancellationBeforeConnectionCreationFinishesNeverUploadsImages() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val disconnected = CountDownLatch(1)
        val transport = object : Transport() {
            override fun disconnect() { super.disconnect(); disconnected.countDown() }
        }
        val api = TryOnImageApi {
            entered.complete(Unit)
            // A connection factory may finish even after its worker is interrupted.
            while (true) {
                try { check(release.await(2, TimeUnit.SECONDS)); break }
                catch (_: InterruptedException) { }
            }
            transport
        }
        val running = async { api.generate("test-key", listOf(byteArrayOf(1), byteArrayOf(2)), "prompt") }
        withTimeout(2_000) { entered.await() }
        running.cancelAndJoin()
        release.countDown()
        assertTrue(disconnected.await(2, TimeUnit.SECONDS))
        assertEquals(0, transport.uploaded.size())
    }
}
