package com.lolita.app.data.remote

import com.google.gson.JsonParser
import com.lolita.app.domain.model.ImageGenerationCredentials
import com.lolita.app.domain.model.ImageGenerationProvider
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SeedreamImageApiTest {
    private open class Transport(val code: Int = 200, val body: String = """{"model":"doubao-seedream-5-0-pro-260628","data":[{"b64_json":"aW1hZ2U="}],"usage":{"generated_images":1}}""") : HttpURLConnection(URL("https://ark.cn-beijing.volces.com/api/v3/images/generations")) {
        val uploaded = ByteArrayOutputStream()
        @Volatile var closed = false
        override fun connect() = Unit
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getOutputStream() = uploaded
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray())
        override fun getErrorStream() = ByteArrayInputStream(body.toByteArray())
        override fun getResponseCode() = code
    }

    @Test fun sendsOrderedLocalReferencesAndOfficialParametersWithoutOpenAiFields() = runBlocking {
        val transport = Transport()
        var endpoint = ""
        val api = SeedreamImageApi { endpoint = it.toString(); transport }
        assertArrayEquals("image".toByteArray(), api.generate("test-personal-key", listOf("TEMPLATE".toByteArray(), "DRESS".toByteArray(), "CUFFS".toByteArray()), "原样保留\n\"花纹\""))
        assertEquals("https://ark.cn-beijing.volces.com/api/v3/images/generations", endpoint)
        assertEquals("Bearer test-personal-key", transport.getRequestProperty("Authorization"))
        assertFalse(transport.instanceFollowRedirects)
        val body = JsonParser.parseString(transport.uploaded.toString("UTF-8")).asJsonObject
        assertEquals("doubao-seedream-5-0-pro-260628", body["model"].asString)
        assertEquals("1024x1536", body["size"].asString)
        assertEquals("b64_json", body["response_format"].asString)
        assertEquals("png", body["output_format"].asString)
        assertFalse(body["stream"].asBoolean); assertTrue(body["watermark"].asBoolean)
        assertEquals("原样保留\n\"花纹\"", body["prompt"].asString)
        assertFalse(body.has("n")); assertFalse(body.has("quality"))
        val refs = body["image"].asJsonArray
        assertEquals(listOf("TEMPLATE", "DRESS", "CUFFS"), refs.map { String(Base64.getDecoder().decode(it.asString.substringAfter(','))) })
        assertTrue(refs.all { it.asString.startsWith("data:image/jpeg;base64,") })
        assertTrue(transport.closed)
    }

    @Test fun neverRetriesAndReportsAccountUsageLimitWithoutEchoingServerSecrets() = runBlocking {
        var calls = 0
        val raw = """{"error":{"code":"SetLimitExceeded","message":"secret-key private-account"}}"""
        val api = SeedreamImageApi { calls++; Transport(429, raw) }
        val error = runCatching { api.generate("test-key", listOf(byteArrayOf(1), byteArrayOf(2)), "prompt") }.exceptionOrNull()!!
        assertTrue(error.message!!.contains("安全体验模式"))
        assertFalse(error.message!!.contains("secret-key")); assertFalse(error.message!!.contains("private-account"))
        assertEquals(1, calls)
    }

    @Test fun rejectsRedirectUrlOnlyEmptyPartialMalformedAndOversizedResultsWithoutFetchingUrls() = runBlocking {
        for ((code, body) in listOf(302 to "{}", 200 to "{}", 200 to """{"data":[]}""", 200 to """{"data":[{"url":"https://untrusted.example/image.png"}]}""", 200 to """{"data":[{"error":{"code":"ContentPolicyViolation"}}]}""", 200 to """{"data":[{"b64_json":"%%%"}]}""", 200 to """{"data":[{"b64_json":"YQ=="},{"b64_json":"Yg=="}]}""")) {
            var calls = 0
            assertTrue(runCatching { SeedreamImageApi { calls++; Transport(code, body) }.generate("test-key", listOf(byteArrayOf(1), byteArrayOf(2)), "prompt") }.isFailure)
            assertEquals(1, calls)
        }
        val large = object : Transport() { override fun getContentLengthLong() = 49L * 1024 * 1024 }
        assertTrue(runCatching { SeedreamImageApi { large }.generate("test-key", listOf(byteArrayOf(1), byteArrayOf(2)), "prompt") }.isFailure)
    }

    @Test fun invalidKeyOrReferenceCountNeverCreatesPaidConnection() = runBlocking {
        var calls = 0
        val api = SeedreamImageApi { calls++; Transport() }
        for (key in listOf("", "key\nheader", "x".repeat(513))) assertTrue(runCatching { api.generate(key, listOf(byteArrayOf(1), byteArrayOf(2)), "p") }.isFailure)
        for (count in listOf(0, 1, 10)) assertTrue(runCatching { api.generate("test-key", List(count) { byteArrayOf(1) }, "p") }.isFailure)
        assertEquals(0, calls)
    }

    @Test fun routerKeepsProviderCredentialsAndCancellationBoundToChosenTransport() = runBlocking {
        val entered = CountDownLatch(1)
        val transport = object : Transport() {
            override fun getResponseCode(): Int { entered.countDown(); while (!closed) Thread.sleep(10); throw java.io.IOException("closed") }
        }
        var openAiCalls = 0
        val api = ImageGenerationApi(TryOnImageApi { openAiCalls++; Transport() }, SeedreamImageApi { transport })
        val credentials = ImageGenerationCredentials(ImageGenerationProvider.SEEDREAM, "private-value")
        assertFalse(credentials.toString().contains("private-value"))
        val job = async(Dispatchers.Default) { api.generate(credentials, listOf(byteArrayOf(1), byteArrayOf(2)), "p") }
        assertTrue(entered.await(3, TimeUnit.SECONDS))
        withTimeout(1000) { job.cancelAndJoin() }
        assertTrue(transport.closed); assertEquals(0, openAiCalls)
    }
}
