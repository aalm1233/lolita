package com.lolita.app.domain.model

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class RecognitionProviderRequestTest {
    private fun request() = RecognitionRequest(draftId = UUID.randomUUID().toString(), targetItemId = null,
        regions = listOf(RecognitionRegion("crop", "a".repeat(64), 64, 80)), baseline = emptyMap())

    @Test fun requestPersistsTheArkProviderAndExactSeedModel() {
        val request = request()
        val json = JsonParser.parseString(request.encode()).asJsonObject
        assertEquals("VOLCENGINE_ARK", json["provider"]?.asString)
        assertEquals("doubao-seed-2-1-pro-260915", json["model"].asString)
        assertEquals(request, RecognitionRequest.decode(json.toString()))
    }

    @Test fun unpublishedOpenAiOrMissingProviderRequestsAreRejectedRatherThanMigrated() {
        val request = request()
        assertThrows(IllegalArgumentException::class.java) { request.copy(model = "gpt-4.1-mini").validate() }
        val row = JsonParser.parseString(request.encode()).asJsonObject
        row.remove("provider")
        assertThrows(IllegalArgumentException::class.java) { RecognitionRequest.decode(row.toString()) }
        for (provider in listOf("OPENAI", "SEEDREAM", "volcengine_ark", "UNKNOWN")) {
            val invalid = JsonParser.parseString(request.encode()).asJsonObject
            invalid.addProperty("provider", provider)
            assertThrows(IllegalArgumentException::class.java) { RecognitionRequest.decode(invalid.toString()) }
        }
        val numeric = JsonParser.parseString(request.encode()).asJsonObject
        numeric.addProperty("provider", 1)
        assertThrows(IllegalArgumentException::class.java) { RecognitionRequest.decode(numeric.toString()) }
    }

    @Test fun deepSeekAndArkRequestsCannotExchangeModels() {
        val ds = request().copy(provider = RecognitionProvider.valueOf("DEEPSEEK"), model = "deepseek-flash")
        assertEquals(ds, RecognitionRequest.decode(ds.encode()))
        assertEquals("DEEPSEEK", JsonParser.parseString(ds.encode()).asJsonObject["provider"].asString)
        assertThrows(IllegalArgumentException::class.java) { ds.copy(model = "doubao-seed-2-1-pro-260915").validate() }
        assertThrows(IllegalArgumentException::class.java) { request().copy(model = "deepseek-flash").validate() }
        assertThrows(IllegalArgumentException::class.java) { ds.copy(model = "deepseek-v4-pro").validate() }
    }

    @Test fun requestDoesNotCoerceStringOrFractionalNumbersAndRegionLabels() {
        val base = JsonParser.parseString(request().encode()).asJsonObject
        val invalid = listOf(
            base.deepCopy().apply { addProperty("version", "1") },
            base.deepCopy().apply { addProperty("version", 1.5) },
            base.deepCopy().apply { addProperty("targetItemId", "42") },
            base.deepCopy().apply { addProperty("targetItemId", 42.5) },
            base.deepCopy().apply { this["regions"].asJsonArray[0].asJsonObject.addProperty("id", 123) },
            base.deepCopy().apply { this["regions"].asJsonArray[0].asJsonObject.addProperty("width", "64") })
        for (row in invalid) assertThrows("Must reject snapshot coercion: $row", IllegalArgumentException::class.java) { RecognitionRequest.decode(row.toString()) }
    }
}
