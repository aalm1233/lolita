package com.lolita.app.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class TryOnImageApiTest {
    private class Transport(val code: Int = 200, val body: String = """{"created":1,"data":[{"b64_json":"aW1hZ2U="}]}""") : HttpURLConnection(URL("https://api.openai.com/v1/images/edits")) {
        val uploaded = ByteArrayOutputStream()
        var closed = false
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
        assertTrue(body.contains("gpt-image-2"))
        assertTrue(body.contains("name=\"n\"\r\n\r\n1\r\n"))
        assertFalse(body.contains("private-test-key"))
        assertFalse(transport.instanceFollowRedirects)
        assertArrayEquals("image".toByteArray(), result)
        assertTrue(transport.closed)
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
        for (body in listOf("", "not-json", "{}", "{\"data\":[]}", "{\"data\":[{\"b64_json\":\"\"}]}", "{\"data\":[{\"url\":\"https://bad.example/a.png\"}]}")) {
            try {
                TryOnImageApi { Transport(body = body) }.generate("test-key",listOf(byteArrayOf(1),byteArrayOf(2)),"prompt")
                fail("Invalid response must fail")
            } catch (expected: IllegalStateException) { assertTrue(expected.message.orEmpty().isNotEmpty()) }
        }
    }
}
