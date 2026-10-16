package com.lolita.app.data.remote

import com.google.gson.JsonParser
import com.lolita.app.domain.model.ImageGenerationCredentials
import com.lolita.app.domain.model.ImageGenerationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MannequinApiTest {
    private class Transport : HttpURLConnection(URL("https://example.invalid")) {
        val uploaded = ByteArrayOutputStream()
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = 200
        override fun getOutputStream() = uploaded
        override fun getInputStream() = ByteArrayInputStream("""{"data":[{"b64_json":"aW1hZ2U="}]}""".toByteArray())
    }

    @Test fun standaloneOnePhotoAndDressTwoPhotosReachEachProviderWithoutExtraTemplate() = runBlocking {
        for (provider in ImageGenerationProvider.entries) for (count in listOf(1, 2)) {
            val transport = Transport()
            var calls = 0
            var endpoint = ""
            val open: (URL) -> HttpURLConnection = { calls++; endpoint = it.toString(); transport }
            val api = ImageGenerationApi(TryOnImageApi(open), SeedreamImageApi(open))
            val result = runCatching { api.generateMannequin(ImageGenerationCredentials(provider, "controlled-key"),
                List(count) { "PHOTO$it".toByteArray() }, "selected product") }
            assertTrue("$provider must accept $count selected display inputs", result.isSuccess)
            assertArrayEquals("image".toByteArray(), result.getOrThrow())
            assertEquals(1, calls)
            val body = transport.uploaded.toString("UTF-8")
            assertFalse(body.contains("controlled-key"))
            if (provider == ImageGenerationProvider.SEEDREAM) {
                assertEquals("https://ark.cn-beijing.volces.com/api/v3/images/generations", endpoint)
                assertEquals(count, JsonParser.parseString(body).asJsonObject.getAsJsonArray("image").size())
            } else {
                assertEquals("https://api.openai.com/v1/images/edits", endpoint)
                assertEquals(count, Regex("name=\"image\\[\\]\"").findAll(body).count())
            }
        }
    }

    @Test fun displayBoundaryRejectsZeroAndThreeInputsBeforeConnectionAndKeepsOldSingleInputBlocked() = runBlocking {
        var calls = 0
        val open: (URL) -> HttpURLConnection = { calls++; Transport() }
        val api = ImageGenerationApi(TryOnImageApi(open), SeedreamImageApi(open))
        for (provider in ImageGenerationProvider.entries) {
            val credentials = ImageGenerationCredentials(provider, "controlled-key")
            for (count in listOf(0, 3)) assertTrue(runCatching {
                api.generateMannequin(credentials, List(count) { byteArrayOf(1) }, "display")
            }.isFailure)
            assertTrue(runCatching { api.generate(credentials, listOf(byteArrayOf(1)), "try on") }.isFailure)
        }
        assertEquals(0, calls)
    }
}
