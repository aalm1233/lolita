package com.lolita.app.data.remote

import com.google.gson.JsonParser
import com.lolita.app.domain.model.ImageGenerationFailure
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

class SeedreamLayersApiTest {
    private val png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jgXcAAAAASUVORK5CYII=")
    private fun response() = """{"data":[{"z_index":0,"output_format":"png"},{"z_index":1,"output_format":"png","b64_json":"${Base64.getEncoder().encodeToString(png)}","name":"腰饰","description":"含细链","bounding_box":{"normalized":[10,20,900,950]}}],"usage":{"generated_images":2}}"""
    private class Transport(val body: String, val status: Int = 200) : HttpURLConnection(URL(SeedreamImageApi.ENDPOINT)) {
        val sent = ByteArrayOutputStream()
        var closed = false
        override fun connect() = Unit
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getOutputStream() = sent
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray())
    }
    @Test fun decompositionIsOneExplicitRequestAndAccountsForBackgroundToo() = runBlocking {
        val conn = Transport(response()); var calls = 0
        val result = SeedreamLayersApi { calls++; conn }.decompose("controlled-key",png,"保留所有附属部件")
        assertEquals(1,calls); assertTrue(conn.closed); assertFalse(conn.instanceFollowRedirects)
        val request = JsonParser.parseString(conn.sent.toString("UTF-8")).asJsonObject
        assertTrue(request["layer_decomposition"].asBoolean)
        assertEquals("b64_json",request["response_format"].asString)
        assertFalse(request["watermark"].asBoolean)
        assertEquals("png",request["output_format"].asString)
        assertTrue(request["image"].asString.startsWith("data:image/png;base64,"))
        assertFalse(request.has("background")); assertFalse(request.has("sequential_image_generation"))
        assertEquals(2,result.generatedImages); assertEquals(1,result.layers.size)
        assertEquals(1,result.layers.single().zIndex); assertArrayEquals(png,result.layers.single().bytes)
    }
    @Test fun malformedOrPartiallyFailedLayersNeverBecomeASuccessfulSubset() = runBlocking {
        val root = JsonParser.parseString(response()).asJsonObject
        val variants = listOf(
            root.deepCopy().apply { getAsJsonArray("data")[1].asJsonObject.addProperty("z_index",0) },
            root.deepCopy().apply { getAsJsonArray("data")[1].asJsonObject.remove("bounding_box") },
            root.deepCopy().apply { getAsJsonArray("data")[1].asJsonObject.addProperty("b64_json","broken") },
            root.deepCopy().apply { getAsJsonArray("data")[1].asJsonObject.add("error",JsonParser.parseString("""{"message":"private-server-text"}""")) },
            root.deepCopy().apply { getAsJsonObject("usage").addProperty("generated_images",18) }
        )
        for (bad in variants) {
            var calls=0
            val error=runCatching { SeedreamLayersApi { calls++; Transport(bad.toString()) }.decompose("controlled-key",png,"拆层") }.exceptionOrNull()
            assertTrue(error is ImageGenerationFailure); assertFalse(error.toString().contains("private-server-text")); assertEquals(1,calls)
        }
    }
    @Test fun failuresAreTrustedLocalCategoriesAndNeverRetryOrFollowRedirects() = runBlocking {
        for ((status,kind) in listOf(401 to "AUTH",402 to "QUOTA",429 to "RATE_LIMIT",302 to "MODEL",500 to "MODEL")) {
            var calls=0;val connection=Transport("secret-server-message",status)
            val error=runCatching { SeedreamLayersApi { calls++; connection }.decompose("controlled-key",png,"拆层") }.exceptionOrNull()
            assertEquals(kind,(error as ImageGenerationFailure).kind.name); assertEquals(1,calls); assertTrue(connection.closed)
            assertFalse(error.toString().contains("secret-server-message"))
        }
    }
}
