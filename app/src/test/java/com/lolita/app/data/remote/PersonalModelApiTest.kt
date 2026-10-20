package com.lolita.app.data.remote

import com.lolita.app.domain.model.ImageGenerationCredentials
import com.lolita.app.domain.model.ImageGenerationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class PersonalModelApiTest {
    private class Transport : HttpURLConnection(URL("https://example.invalid")) {
        val uploaded = ByteArrayOutputStream()
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = 200
        override fun getOutputStream() = uploaded
        override fun getInputStream() = ByteArrayInputStream("""{"data":[{"b64_json":"aW1hZ2U="}]}""".toByteArray())
    }

    // The new one-template operation must not weaken the old paid request boundary.
    @Test fun oneTemplateIsAcceptedOnlyForPersonalModelGenerationOnBothProviders() = runBlocking {
        for (provider in ImageGenerationProvider.entries) {
            val transport = Transport()
            var calls = 0
            val api = ImageGenerationApi(TryOnImageApi { calls++; transport }, SeedreamImageApi { calls++; transport })
            val credentials = ImageGenerationCredentials(provider, "controlled-key")
            val template = listOf("TEMPLATE".toByteArray())
            assertTrue(runCatching { api.generate(credentials, template, "old") }.isFailure)
            assertEquals(0, calls)
            assertArrayEquals("image".toByteArray(), api.generatePersonalModel(credentials, template, "personal"))
            assertEquals(1, calls)
            assertTrue(transport.uploaded.toString("UTF-8").contains("personal"))
            if (provider == ImageGenerationProvider.SEEDREAM) {
                assertFalse(com.google.gson.JsonParser.parseString(transport.uploaded.toString("UTF-8"))
                    .asJsonObject["watermark"].asBoolean)
            }
        }
    }

    @Test fun emptyOrTooManyPersonalReferencesFailBeforeOpeningConnection() = runBlocking {
        var calls = 0
        val api = ImageGenerationApi(TryOnImageApi { calls++; Transport() }, SeedreamImageApi { calls++; Transport() })
        for (provider in ImageGenerationProvider.entries) for (count in listOf(0, 6)) {
            assertTrue(runCatching { api.generatePersonalModel(ImageGenerationCredentials(provider, "controlled-key"),
                List(count) { byteArrayOf(1) }, "personal") }.isFailure)
        }
        assertEquals(0, calls)
    }
}
