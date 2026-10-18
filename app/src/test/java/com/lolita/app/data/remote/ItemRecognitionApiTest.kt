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
    private fun resultJson() = """{"version":1,"candidates":[{"id":"candidate-1","field":"WIG_COLOR","value":"银灰","source":"VISUAL","regionIds":["region-crop"],"quote":"","variant":null,"sizeRow":null,"measurement":null}]}"""
    private fun response(text: String = resultJson(), status: String = "completed") = JsonObject().apply {
        addProperty("id", "resp-controlled"); addProperty("object", "response"); addProperty("status", status)
        addProperty("model", "doubao-seed-2-1-pro-260915"); addProperty("store", false)
        add("thinking", JsonObject().apply { addProperty("type", "disabled") })
        add("caching", JsonObject().apply { addProperty("type", "disabled") })
        addProperty("expire_at", 1792272000)
        add("tools", com.google.gson.JsonNull.INSTANCE)
        add("output", com.google.gson.JsonArray().apply { add(JsonObject().apply {
            addProperty("id", "msg-controlled"); addProperty("type", "message"); addProperty("role", "assistant"); addProperty("status", "completed")
            addProperty("phase", "final_answer"); addProperty("partial", false)
            add("content", com.google.gson.JsonArray().apply { add(JsonObject().apply {
                addProperty("type", "output_text"); addProperty("text", text); add("annotations", com.google.gson.JsonNull.INSTANCE)
            }) })
        }) })
    }.toString()

    private class Loopback(private val code: Int, private val response: String,
        private val endpoint: String = "https://ark.cn-beijing.volces.com/api/v3/responses") : AutoCloseable {
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
            assertEquals(endpoint, official.toString())
            return URL("http://127.0.0.1:${server.localPort}/api/v3/responses").openConnection() as HttpURLConnection
        }
        override fun close() { server.close(); executor.shutdownNow() }
    }

    private fun deepSeekRequest() = request().copy(provider = RecognitionProvider.valueOf("DEEPSEEK"), model = "deepseek-flash")
    private fun deepSeekResponse(actualModel: String = "DeepSeek-V4.1-Flash-1017") = JsonObject().apply {
        addProperty("id", "ds-response"); addProperty("object", "response"); addProperty("created_at", 1792272000)
        addProperty("status", "completed"); addProperty("model", actualModel); addProperty("store", false)
        addProperty("parallel_tool_calls", true); add("previous_response_id", com.google.gson.JsonNull.INSTANCE)
        add("error", com.google.gson.JsonNull.INSTANCE); add("incomplete_details", com.google.gson.JsonNull.INSTANCE)
        add("output", com.google.gson.JsonArray().apply { add(JsonObject().apply {
            addProperty("type", "message"); addProperty("id", "ds-message"); addProperty("status", "completed"); addProperty("role", "assistant")
            add("content", com.google.gson.JsonArray().apply { add(JsonObject().apply {
                addProperty("type", "output_text"); addProperty("text", resultJson()); add("annotations", com.google.gson.JsonArray())
            }) })
        }) })
    }.toString()

    // Handwritten envelope shape from the 2026-10-18 real 35-field response. No service values or user data.
    private fun deepSeekFullEnvelope() = JsonParser.parseString(deepSeekResponse()).asJsonObject.apply {
        addProperty("background", false); addProperty("completed_at", 1792272001)
        addProperty("frequency_penalty", 0); addProperty("presence_penalty", 0)
        addProperty("instructions", "controlled instructions"); addProperty("max_output_tokens", 6000)
        addProperty("service_tier", "default"); addProperty("temperature", 1); addProperty("top_p", 1)
        addProperty("tool_choice", "auto"); addProperty("top_logprobs", 0); addProperty("truncation", "disabled")
        for (key in listOf("content_filters", "max_tool_calls", "moderation", "prompt_cache_key", "prompt_cache_retention", "safety_identifier", "user"))
            add(key, com.google.gson.JsonNull.INSTANCE)
        add("tools", com.google.gson.JsonArray()); add("metadata", JsonObject()); add("usage", JsonObject())
        add("reasoning", JsonObject().apply { addProperty("effort", "none"); add("summary", com.google.gson.JsonNull.INSTANCE) })
        add("text", JsonObject().apply { add("format", JsonObject().apply { addProperty("type", "json_schema") }) })
    }

    @Test fun deepSeekAcceptsObservedMetadataEnvelopeWithoutRelaxingStructuredOutput() = runBlocking {
        val envelope = deepSeekFullEnvelope()
        assertEquals(35, envelope.size())
        Loopback(200, envelope.toString(), "https://api.deepseek.com/responses").use { server ->
            assertEquals(result(), ItemRecognitionApi(server::connection).recognize("dummy-key", deepSeekRequest(), listOf(jpeg)))
            assertEquals(1, server.calls.get())
        }
    }

    @Test fun deepSeekAcceptsOnlyFinalAnswerPhaseAndEmptyLogprobMetadata() = runBlocking {
        val envelope = deepSeekFullEnvelope().apply {
            this["output"].asJsonArray[0].asJsonObject.apply {
                addProperty("phase", "final_answer")
                this["content"].asJsonArray[0].asJsonObject.add("logprobs", com.google.gson.JsonArray())
            }
        }
        Loopback(200, envelope.toString(), "https://api.deepseek.com/responses").use { server ->
            assertEquals(result(), ItemRecognitionApi(server::connection).recognize("dummy-key", deepSeekRequest(), listOf(jpeg)))
        }
        val invalid = listOf(
            envelope.deepCopy().apply { this["output"].asJsonArray[0].asJsonObject.addProperty("phase", "commentary") },
            envelope.deepCopy().apply { this["output"].asJsonArray[0].asJsonObject["content"].asJsonArray[0].asJsonObject.addProperty("logprobs", "[]") },
            envelope.deepCopy().apply { this["output"].asJsonArray[0].asJsonObject["content"].asJsonArray[0].asJsonObject.add("logprobs", com.google.gson.JsonArray().apply { add(JsonObject()) }) })
        for (body in invalid) Loopback(200, body.toString(), "https://api.deepseek.com/responses").use { server ->
            val failure = runCatching { ItemRecognitionApi(server::connection).recognize("dummy-key", deepSeekRequest(), listOf(jpeg)) }.exceptionOrNull()
            assertEquals(RecognitionApiFailureKind.INVALID_RESPONSE, (failure as RecognitionApiFailure).kind)
            assertNull(failure.cause); assertEquals(1, server.calls.get())
        }
        val ark = JsonParser.parseString(response()).asJsonObject.apply {
            this["output"].asJsonArray[0].asJsonObject["content"].asJsonArray[0].asJsonObject.add("logprobs", com.google.gson.JsonArray())
        }
        Loopback(200, ark.toString()).use { server ->
            assertTrue(runCatching { ItemRecognitionApi(server::connection).recognize("dummy-key", request(), listOf(jpeg)) }.exceptionOrNull() is RecognitionApiFailure)
        }
    }

    @Test fun deepSeekMetadataTypesConstantsAndUnknownOutputRemainStrict() = runBlocking {
        val valid = deepSeekFullEnvelope()
        val invalid = listOf(
            valid.deepCopy().apply { addProperty("background", true) },
            valid.deepCopy().apply { addProperty("parallel_tool_calls", false) },
            valid.deepCopy().apply { addProperty("frequency_penalty", "0") },
            valid.deepCopy().apply { addProperty("presence_penalty", 1) },
            valid.deepCopy().apply { addProperty("content_filters", "disabled") },
            valid.deepCopy().apply { addProperty("moderation", false) },
            valid.deepCopy().apply { addProperty("prompt_cache_key", "private-cache") },
            valid.deepCopy().apply { addProperty("prompt_cache_retention", "24h") },
            valid.deepCopy().apply { addProperty("safety_identifier", "user") },
            valid.deepCopy().apply { add("metadata", JsonObject().apply { addProperty("private", "value") }) },
            valid.deepCopy().apply { addProperty("max_output_tokens", "6000") },
            valid.deepCopy().apply { addProperty("completed_at", "1792272001") },
            valid.deepCopy().apply { addProperty("service_tier", "unknown-tier") },
            valid.deepCopy().apply { addProperty("truncation", "auto") },
            valid.deepCopy().apply { addProperty("unknown_vendor_field", false) },
            valid.deepCopy().apply { this["output"].asJsonArray[0].asJsonObject.addProperty("unknown_message_field", "x") },
            valid.deepCopy().apply {
                val structured = JsonParser.parseString(resultJson()).asJsonObject
                structured["candidates"].asJsonArray[0].asJsonObject.addProperty("unknown_candidate_field", "x")
                this["output"].asJsonArray[0].asJsonObject["content"].asJsonArray[0].asJsonObject.addProperty("text", structured.toString())
            })
        for (body in invalid) Loopback(200, body.toString(), "https://api.deepseek.com/responses").use { server ->
            val failure = runCatching { ItemRecognitionApi(server::connection).recognize("dummy-key", deepSeekRequest(), listOf(jpeg)) }.exceptionOrNull()
            assertEquals(RecognitionApiFailureKind.INVALID_RESPONSE, (failure as RecognitionApiFailure).kind)
            assertNull(failure.cause); assertEquals(1, server.calls.get())
        }
    }

    @Test fun productionPromptRequiresChineseSuggestionsVisibleOriginalUnitsAndNoHiddenTightsInference() = runBlocking {
        Loopback(200, response()).use { server ->
            ItemRecognitionApi(server::connection).recognize("dummy-key", request(), listOf(jpeg))
            val prompt = JsonParser.parseString(server.payload).asJsonObject["instructions"].asString
            assertTrue(prompt.contains("简体中文"))
            assertTrue(prompt.contains("原语种"))
            assertTrue(prompt.contains("腰部或裆部"))
            assertTrue(prompt.contains("不得推断为连裤袜"))
            assertTrue(prompt.contains("value 必须包含图上可见的原单位"))
            assertTrue(prompt.contains("quote 必须忠实包含对应表头和原数值"))
        }
    }

    @Test fun deepSeekUsesItsOwnPayloadAndReturnsTheActualModelWithoutInventingASnapshot() = runBlocking {
        Loopback(200, deepSeekResponse(), "https://api.deepseek.com/responses").use { server ->
            val result = ItemRecognitionApi(server::connection).recognizeWithMetadata("dummy-deepseek-key", deepSeekRequest(), listOf(jpeg))
            assertEquals("DEEPSEEK", result.provider.name)
            assertEquals("DeepSeek-V4.1-Flash-1017", result.actualModel)
            assertEquals(this@ItemRecognitionApiTest.result(), result.result)
            val root = JsonParser.parseString(server.payload).asJsonObject
            assertEquals(setOf("model", "stream", "max_output_tokens", "instructions", "input", "text", "reasoning"), root.keySet())
            assertEquals("deepseek-flash", root["model"].asString)
            assertEquals("none", root["reasoning"].asJsonObject["effort"].asString)
            assertEquals(setOf("effort"), root["reasoning"].asJsonObject.keySet())
            assertEquals(6000, root["max_output_tokens"].asInt); assertFalse(root["stream"].asBoolean)
            assertFalse(root.has("store")); assertFalse(root.has("thinking")); assertFalse(root.has("tools"))
            val format = root["text"].asJsonObject["format"].asJsonObject
            assertEquals(setOf("type", "name", "schema"), format.keySet())
            assertEquals("json_schema", format["type"].asString)
            val image = root["input"].asJsonArray.single().asJsonObject["content"].asJsonArray.single { it.asJsonObject["type"].asString == "input_image" }.asJsonObject
            assertEquals("high", image["detail"].asString)
            assertArrayEquals(jpeg, Base64.getDecoder().decode(image["image_url"].asString.substringAfter(',')))
            assertFalse(server.payload.contains("/private")); assertFalse(server.payload.contains("secret-baseline"))
            assertEquals("Bearer dummy-deepseek-key", server.authorization); assertEquals(1, server.calls.get())
        }
        Loopback(200, deepSeekResponse(actualModel = "deepseek-flash"), "https://api.deepseek.com/responses").use { server ->
            assertEquals("deepseek-flash", ItemRecognitionApi(server::connection).recognizeWithMetadata("dummy-key", deepSeekRequest(), listOf(jpeg)).actualModel)
        }
    }

    @Test fun deepSeekRejectsArkFieldsToolOutputThinkingAndUnknownResponseFields() = runBlocking {
        val valid = JsonParser.parseString(deepSeekResponse()).asJsonObject
        val invalid = listOf(
            valid.deepCopy().apply { add("thinking", JsonObject().apply { addProperty("type", "disabled") }) },
            valid.deepCopy().apply { add("caching", JsonObject().apply { addProperty("type", "disabled") }) },
            valid.deepCopy().apply { addProperty("model", "") },
            valid.deepCopy().apply { addProperty("model", "x".repeat(161)) },
            valid.deepCopy().apply { addProperty("store", true) },
            valid.deepCopy().apply { addProperty("previous_response_id", "previous-private-response") },
            valid.deepCopy().apply { this["output"].asJsonArray[0].asJsonObject.addProperty("type", "function_call") },
            valid.deepCopy().apply { this["output"].asJsonArray.add(JsonObject().apply { addProperty("type", "reasoning") }) },
            valid.deepCopy().apply { add("tools", com.google.gson.JsonArray().apply { add(JsonObject().apply { addProperty("type", "web_search") }) }) })
        for (body in invalid) Loopback(200, body.toString(), "https://api.deepseek.com/responses").use { server ->
            val failure = runCatching { ItemRecognitionApi(server::connection).recognize("dummy-key", deepSeekRequest(), listOf(jpeg)) }.exceptionOrNull()
            assertTrue(failure is RecognitionApiFailure)
            assertEquals(RecognitionApiFailureKind.INVALID_RESPONSE, (failure as RecognitionApiFailure).kind)
            assertNull(failure.cause); assertEquals(1, server.calls.get())
        }
    }

    @Test fun deepSeekErrorsNeverUseArkActivationCodesAndNeverRetry() = runBlocking {
        for ((code, expected) in listOf(302 to RecognitionApiFailureKind.INVALID_RESPONSE, 307 to RecognitionApiFailureKind.INVALID_RESPONSE,
            401 to RecognitionApiFailureKind.AUTH, 402 to RecognitionApiFailureKind.QUOTA, 403 to RecognitionApiFailureKind.AUTH,
            404 to RecognitionApiFailureKind.INVALID_RESPONSE, 429 to RecognitionApiFailureKind.RATE_LIMIT)) {
            Loopback(code, """{"error":{"code":"ModelNotOpen","message":"sensitive dummy-key"}}""", "https://api.deepseek.com/responses").use { server ->
                val failure = runCatching { ItemRecognitionApi(server::connection).recognize("dummy-key", deepSeekRequest(), listOf(jpeg)) }.exceptionOrNull()
                assertEquals(expected, (failure as RecognitionApiFailure).kind)
                assertNull(failure.cause); assertFalse(failure.toString().contains("sensitive")); assertEquals(1, server.calls.get())
            }
        }
    }

    @Test fun deepSeekCancellationDisconnectsItsCapturedProviderWithoutRetry() = runBlocking {
        val transport = BlockingTransport()
        var opened = 0
        val job = async { ItemRecognitionApi({ url ->
            assertEquals("https://api.deepseek.com/responses", url.toString()); opened++; transport
        }).recognize("dummy-deepseek-key", deepSeekRequest(), listOf(jpeg)) }
        withTimeout(2000) { transport.entered.await() }
        job.cancelAndJoin()
        assertTrue(transport.closed); assertEquals(1, opened)
    }

    @Test fun freshOutputRejectsStringVersionAndMissingMeasurementForBothProviders() = runBlocking {
        for (provider in listOf(RecognitionProvider.VOLCENGINE_ARK, RecognitionProvider.DEEPSEEK)) {
            val invalidResults = listOf(
                JsonParser.parseString(resultJson()).asJsonObject.apply { addProperty("version", "1") },
                JsonParser.parseString(resultJson()).asJsonObject.apply { this["candidates"].asJsonArray[0].asJsonObject.remove("measurement") })
            for (invalid in invalidResults) {
                val envelope = JsonParser.parseString(if (provider == RecognitionProvider.VOLCENGINE_ARK) response() else deepSeekResponse()).asJsonObject
                envelope["output"].asJsonArray[0].asJsonObject["content"].asJsonArray[0].asJsonObject.addProperty("text", invalid.toString())
                val endpoint = if (provider == RecognitionProvider.VOLCENGINE_ARK) "https://ark.cn-beijing.volces.com/api/v3/responses" else "https://api.deepseek.com/responses"
                Loopback(200, envelope.toString(), endpoint).use { server ->
                    val request = if (provider == RecognitionProvider.VOLCENGINE_ARK) request() else deepSeekRequest()
                    val failure = runCatching { ItemRecognitionApi(server::connection).recognize("dummy-key", request, listOf(jpeg)) }.exceptionOrNull()
                    assertTrue("$provider must reject fresh output: $invalid", failure is RecognitionApiFailure)
                    assertEquals(RecognitionApiFailureKind.INVALID_RESPONSE, (failure as RecognitionApiFailure).kind)
                    assertNull(failure.cause); assertEquals(1, server.calls.get())
                }
            }
        }
    }

    @Test fun actualHttpPayloadContainsOnlyCropPixelsRegionLabelsAndStrictSchema() = runBlocking {
        val bytes = jpeg
        val request = request(bytes)
        Loopback(200, response()).use { server ->
            assertEquals(result(), ItemRecognitionApi(server::connection).recognize("dummy-recognition-key", request, listOf(bytes)))
            val root = JsonParser.parseString(server.payload).asJsonObject
            assertEquals("doubao-seed-2-1-pro-260915", root["model"].asString)
            assertFalse(root["store"].asBoolean)
            assertFalse(root["stream"].asBoolean)
            assertEquals(setOf("type"), root["thinking"].asJsonObject.keySet())
            assertEquals("disabled", root["thinking"].asJsonObject["type"].asString)
            assertEquals(6000, root["max_output_tokens"].asInt)
            assertEquals(setOf("model", "store", "stream", "max_output_tokens", "instructions", "input", "text", "thinking"), root.keySet())
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

    @Test fun modelNotOpenedAndPermissionErrorsUseOnlyAFixedArkActivationMessage() = runBlocking {
        val bytes = jpeg
        for ((http, code) in listOf(404 to "ModelNotOpen", 403 to "ModelNotOpen", 404 to "InvalidEndpointOrModel.NotFound",
            404 to "InvalidEndpointOrModel.ModelIDAccessDisabled", 403 to "OperationDenied.ServiceNotOpen")) {
            val body = JsonObject().apply { add("error", JsonObject().apply {
                addProperty("code", code); addProperty("message", "dummy-recognition-key sensitive-account /private/original.jpg")
            }) }.toString()
            Loopback(http, body).use { server ->
                val failure = runCatching { ItemRecognitionApi(server::connection).recognize("dummy-recognition-key", request(bytes), listOf(bytes)) }.exceptionOrNull()
                assertTrue(failure is RecognitionApiFailure)
                assertEquals("MODEL_UNAVAILABLE", (failure as RecognitionApiFailure).kind.name)
                assertTrue(failure.message!!.contains("火山方舟") && failure.message!!.contains("开通"))
                assertNull(failure.cause)
                assertFalse(failure.toString().contains("dummy-recognition-key")); assertFalse(failure.toString().contains("sensitive-account")); assertFalse(failure.toString().contains("/private"))
                assertEquals(1, server.calls.get())
            }
        }
    }

    @Test fun arkBillingAndQuotaErrorsAreSanitizedWithoutRetrying() = runBlocking {
        for ((http, code) in listOf(403 to "AccountOverdueError", 403 to "OperationDenied.ServiceOverdue",
            429 to "QuotaExceeded", 429 to "AccountQuotaExceeded", 429 to "SetLimitExceeded")) {
            val body = JsonObject().apply { add("error", JsonObject().apply {
                addProperty("code", code); addProperty("message", "sensitive-account dummy-key")
            }) }.toString()
            Loopback(http, body).use { server ->
                val failure = runCatching { ItemRecognitionApi(server::connection).recognize("dummy-key", request(), listOf(jpeg)) }.exceptionOrNull()
                assertTrue(failure is RecognitionApiFailure)
                assertEquals(RecognitionApiFailureKind.QUOTA, (failure as RecognitionApiFailure).kind)
                assertNull(failure.cause); assertFalse(failure.toString().contains("sensitive-account"))
                assertEquals(1, server.calls.get())
            }
        }
    }

    @Test fun unknownErrorCodesCannotSmuggleAnActivationMessageOrServerDetails() = runBlocking {
        val body = """{"error":{"code":"Sensitive.ModelNotOpen.injected","message":"sensitive-account dummy-key"}}"""
        Loopback(403, body).use { server ->
            val failure = runCatching { ItemRecognitionApi(server::connection).recognize("dummy-key", request(), listOf(jpeg)) }.exceptionOrNull()
            assertTrue(failure is RecognitionApiFailure)
            assertEquals(RecognitionApiFailureKind.AUTH, (failure as RecognitionApiFailure).kind)
            assertNull(failure.cause); assertFalse(failure.toString().contains("sensitive"))
            assertEquals(1, server.calls.get())
        }
    }

    @Test fun arkNullOptionalFieldsAreAcceptedButThinkingToolsCacheAndPartialOutputsAreRejected() = runBlocking {
        val valid = JsonParser.parseString(response()).asJsonObject
        val nullable = valid.deepCopy().apply {
            add("thinking", com.google.gson.JsonNull.INSTANCE); add("caching", com.google.gson.JsonNull.INSTANCE)
            add("parallel_tool_calls", com.google.gson.JsonNull.INSTANCE); add("tool_choice", com.google.gson.JsonNull.INSTANCE)
        }
        Loopback(200, nullable.toString()).use { server ->
            assertEquals(result(), ItemRecognitionApi(server::connection).recognize("dummy-key", request(), listOf(jpeg)))
        }
        val invalid = listOf(
            valid.deepCopy().apply { addProperty("model", "gpt-4.1-mini") },
            valid.deepCopy().apply { this["thinking"].asJsonObject.addProperty("type", "enabled") },
            valid.deepCopy().apply { this["thinking"].asJsonObject.addProperty("unknown", true) },
            valid.deepCopy().apply { this["caching"].asJsonObject.addProperty("type", "enabled") },
            valid.deepCopy().apply { add("tools", com.google.gson.JsonArray().apply { add(JsonObject().apply { addProperty("type", "web_search") }) }) },
            valid.deepCopy().apply { this["output"].asJsonArray[0].asJsonObject.addProperty("phase", "commentary") },
            valid.deepCopy().apply { this["output"].asJsonArray[0].asJsonObject.addProperty("partial", true) },
            valid.deepCopy().apply { addProperty("store", true) },
            valid.deepCopy().apply { addProperty("prompt_cache_key", "openai-only-field") })
        for (body in invalid) Loopback(200, body.toString()).use { server ->
            val failure = runCatching { ItemRecognitionApi(server::connection).recognize("dummy-key", request(), listOf(jpeg)) }.exceptionOrNull()
            assertTrue(failure is RecognitionApiFailure)
            assertEquals(RecognitionApiFailureKind.INVALID_RESPONSE, (failure as RecognitionApiFailure).kind)
            assertNull(failure.cause); assertEquals(1, server.calls.get())
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

    private open class BlockingTransport : HttpURLConnection(URL("https://ark.cn-beijing.volces.com/api/v3/responses")) {
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
