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
    @Test fun explicitServiceErrorsAlongsideValidPngNeverCreatePreviewOrRetry() = runBlocking {
        // A real 32x48 PNG; failure must come from the error marker, not broken pixels.
        val png = "iVBORw0KGgoAAAANSUhEUgAAACAAAAAwCAIAAAD/zu84AAAAQ0lEQVR4nO3RsQ3AMAwEMcX77+yMcG7UkQO8gNM3c2fTWV0fBx74QZIoSZQkShIliZJESaIkUZIoSZQkShIlidJ6oh+XPgFfIViZugAAAABJRU5ErkJggg=="
        val rows = listOf(
            """{"error":{"code":"ServiceFailure"},"data":[{"b64_json":"$png"}]}""",
            """{"data":[{"error":{"code":"ContentPolicyViolation"},"b64_json":"$png"}]}"""
        )
        for (body in rows) {
            val root = kotlin.io.path.createTempDirectory("seedream-error-").toFile()
            try {
                val source = java.io.File(root, "source.jpg").apply { writeBytes(byteArrayOf(1)) }
                var requests = 0
                var saves = 0
                val api = SeedreamImageApi { requests++; Transport(body = body) }
                val generator = com.lolita.app.data.mannequin.MannequinGenerator(
                    findItem = { com.lolita.app.data.local.entity.Item(id = 11, name = "裙子", description = "", brandId = 1, categoryId = 1, status = com.lolita.app.data.local.entity.ItemStatus.WISHED, imageUrls = listOf(source.absolutePath)) },
                    findCategory = { com.lolita.app.data.local.entity.Category(id = 1, name = "OP") },
                    readCredentials = { ImageGenerationCredentials(ImageGenerationProvider.SEEDREAM, "test-key") },
                    prepareImages = { listOf(byteArrayOf(1), byteArrayOf(2)) },
                    generateImage = { credentials, images, prompt -> api.generate(credentials.apiKey, images, prompt) },
                    savePreview = { saves++; java.io.File(root, "preview.png").apply { writeBytes(it) }.absolutePath }, discardPreview = {})
                val failure = runCatching { generator.generate(com.lolita.app.domain.model.MannequinRequest(11, source.absolutePath)) }.exceptionOrNull()
                assertNotNull("HTTP200中显式错误必须优先于PNG结果", failure)
                assertEquals(1, requests); assertEquals(0, saves)
                assertFalse(java.io.File(root, "preview.png").exists())
            } finally { root.deleteRecursively() }
        }
    }
    @Test fun trustedAccountAndModelFailuresCarryCategoriesInsteadOfRawServerText() = runBlocking {
        val cases = listOf(
            Triple(401, "{}", "AUTH"), Triple(402, "{}", "QUOTA"),
            Triple(429, "{}", "RATE_LIMIT"), Triple(429, """{"error":{"code":"SetLimitExceeded","message":"Bearer secret"}}""", "SEEDREAM_LIMIT"),
            Triple(500, "{}", "MODEL"), Triple(200, """{"error":{"code":"ModelError","message":"Bearer secret"}}""", "MODEL"),
            Triple(200, """{"data":[{"error":{"code":"ModelError"}}]}""", "MODEL"), Triple(200, "{}", "INVALID_IMAGE"))
        for ((status, body, kind) in cases) {
            val failure = runCatching { SeedreamImageApi { Transport(status, body) }.generate("test-key", listOf(byteArrayOf(1), byteArrayOf(2)), "p") }.exceptionOrNull()
            assertTrue(failure is com.lolita.app.domain.model.ImageGenerationFailure)
            assertEquals(kind, (failure as com.lolita.app.domain.model.ImageGenerationFailure).kind.name)
            assertFalse(failure.toString().contains("Bearer secret"))
        }
    }
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
        assertFalse(body["stream"].asBoolean); assertFalse(body["watermark"].asBoolean)
        assertEquals("原样保留\n\"花纹\"", body["prompt"].asString)
        assertFalse(body.has("n")); assertFalse(body.has("quality"))
        val refs = body["image"].asJsonArray
        assertEquals(listOf("TEMPLATE", "DRESS", "CUFFS"), refs.map { String(Base64.getDecoder().decode(it.asString.substringAfter(','))) })
        assertTrue(refs.all { it.asString.startsWith("data:image/jpeg;base64,") })
        assertTrue(transport.closed)
    }

    @Test fun mixedLosslessAndFallbackReferencesUseMatchingDataUrlsWithoutReencoding() = runBlocking {
        val png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jgXcAAAAASUVORK5CYII=")
        val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte())
        val transport = Transport()
        SeedreamImageApi { transport }.generate("test-key", listOf(png, jpeg), "keep details")
        val body = JsonParser.parseString(transport.uploaded.toString("UTF-8")).asJsonObject
        val refs = body["image"].asJsonArray
        assertEquals(2, refs.size())
        for ((index, bytes) in listOf(png, jpeg).withIndex()) {
            val mime = if (index == 0) "image/png" else "image/jpeg"
            assertTrue(refs[index].asString.startsWith("data:$mime;base64,"))
            assertArrayEquals(bytes, Base64.getDecoder().decode(refs[index].asString.substringAfter(',')))
        }
        assertFalse(body["watermark"].asBoolean)
        assertEquals("1024x1536", body["size"].asString)
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
