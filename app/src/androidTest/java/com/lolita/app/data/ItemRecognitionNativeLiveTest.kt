package com.lolita.app.data

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lolita.app.data.file.ImageRegionCropper
import com.lolita.app.data.file.NormalizedCropRect
import com.lolita.app.data.remote.ItemRecognitionApi
import com.lolita.app.data.remote.RecognitionApiFailure
import com.lolita.app.domain.model.RecognitionProvider
import com.lolita.app.domain.model.RecognitionRegion
import com.lolita.app.domain.model.RecognitionRequest
import com.lolita.app.domain.model.WardrobeFamily
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.UUID

/**
 * Explicit opt-in only. Exactly two authorized Ark requests, one per original image crop, no retries.
 * Key is provisioned separately through stdin/run-as into no_backup and deleted in every exit path.
 * Neither source images nor credentials are packaged in either APK or inserted into a database.
 */
class ItemRecognitionNativeLiveTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target = instrumentation.targetContext
    private val round = "recognition-native-20261018"

    @Test fun twoExplicitlyAuthorizedArkCallsUseProductionCropperAndProductionApi() = runBlocking {
        assumeTrue("Live paid calls require explicit per-round opt-in",
            InstrumentationRegistry.getArguments().getString("recognitionLiveOptIn") == "ark-native-two-20261018")
        val privateDirectory = File(target.noBackupFilesDir, round).apply { check(isDirectory || mkdirs()) }
        val keyFile = File(privateDirectory, "ark-key")
        val inputDirectory = File(target.filesDir, round)
        val workDirectory = File(target.cacheDir, "$round-${UUID.randomUUID()}").apply { check(mkdirs()) }
        val context = object : ContextWrapper(target) {
            override fun getFilesDir() = File(workDirectory, "files").apply { mkdirs() }
            override fun getCacheDir() = File(workDirectory, "cache").apply { mkdirs() }
        }
        val ledger = File(privateDirectory, "call-ledger.json")
        val report = JsonObject().apply {
            addProperty("round", round); addProperty("runtime", "Android production API + ImageRegionCropper")
            addProperty("provider", RecognitionProvider.VOLCENGINE_ARK.name)
            addProperty("model", RecognitionProvider.VOLCENGINE_ARK.model)
            add("calls", JsonArray())
        }
        try {
            require(keyFile.isFile && keyFile.length() in 1..512)
            val key = keyFile.readText(Charsets.UTF_8).trim()
            require(key.isNotBlank() && key.all { it.code in 33..126 })
            // Validate BOTH originals and generate BOTH real production crops before any paid request.
            val samples = listOf(
                Sample("wig-short-ring-curl", "1000033944.jpg", "a31f8e9701cd5564f308d5183cf8ef72b8b05178e6255cdfbd157f3ede020268",
                    NormalizedCropRect(0f, 140f / 1200, 1f, 1f), WardrobeFamily.WIG, 900, 1060),
                Sample("table-sk", "1000033954.jpg", "7a4faea7651c59f6ac0a555bb61ada5ec0c8792f926b0d1481a74d2e5d0bdf85",
                    NormalizedCropRect(20f / 1440, 1520f / 3168, 1420f / 1440, 2210f / 3168), WardrobeFamily.CLOTHING, 1400, 690)
            )
            val prepared = samples.map { sample ->
                val source = File(inputDirectory, sample.fileName)
                require(source.isFile && ItemRecognitionNativeBoundaryTest.sha(source.readBytes()) == sample.sourceSha256)
                val crop = ImageRegionCropper.crop(context, source.path, sample.rect)
                require(crop.width == sample.width && crop.height == sample.height)
                val bytes = File(crop.path).readBytes()
                val request = RecognitionRequest(draftId = UUID.randomUUID().toString(), targetItemId = null,
                    regions = listOf(RecognitionRegion(sample.id, ItemRecognitionNativeBoundaryTest.sha(bytes), crop.width, crop.height)),
                    baseline = emptyMap(), family = sample.family)
                Prepared(sample, request, bytes)
            }
            RandomAccessFile(File(privateDirectory, "call-ledger.lock"), "rw").use { lockFile ->
                lockFile.channel.lock().use {
                    require(!ledger.exists()) { "This authorization is already consumed; never retry automatically" }
                    val calls = JsonArray()
                    for (preparedSample in prepared) {
                        val sample = preparedSample.sample
                        val entry = JsonObject().apply {
                            addProperty("sample", sample.id); addProperty("state", "started")
                            addProperty("cropSha256", ItemRecognitionNativeBoundaryTest.sha(preparedSample.bytes))
                            addProperty("width", sample.width); addProperty("height", sample.height)
                            addProperty("bytes", preparedSample.bytes.size)
                            add("markers", Gson().toJsonTree(ItemRecognitionNativeBoundaryTest.jpegMarkers(preparedSample.bytes)))
                        }
                        calls.add(entry)
                        // Durable reservation BEFORE entering production API; failures still consume the slot.
                        writeSynced(ledger, JsonObject().apply { addProperty("limit", 2); add("calls", calls) }.toString())
                        val row = entry.deepCopy()
                        val responseDiagnostic = JsonObject()
                        row.add("responseDiagnostic", responseDiagnostic)
                        try {
                            val recognized = ItemRecognitionApi({ url ->
                                RecognitionSafeResponseProbe(url.openConnection() as java.net.HttpURLConnection, responseDiagnostic)
                            }).recognizeWithMetadata(key, preparedSample.request, listOf(preparedSample.bytes))
                            row.addProperty("state", "completed"); row.addProperty("actualModel", recognized.actualModel)
                            row.add("result", JsonParser.parseString(recognized.result.encode()))
                            entry.addProperty("state", "completed")
                        } catch (failure: RecognitionApiFailure) {
                            row.addProperty("state", "failed"); row.addProperty("failureKind", failure.kind.name)
                            failure.stage?.let { row.addProperty("failureStage", it.name) }
                            entry.addProperty("state", "failed"); entry.addProperty("failureKind", failure.kind.name)
                            failure.stage?.let { entry.addProperty("failureStage", it.name) }
                        } catch (_: Exception) {
                            row.addProperty("state", "failed"); row.addProperty("failureKind", "LOCAL_FAILURE")
                            entry.addProperty("state", "failed"); entry.addProperty("failureKind", "LOCAL_FAILURE")
                        }
                        report["calls"].asJsonArray.add(row)
                        writeSynced(ledger, JsonObject().apply { addProperty("limit", 2); add("calls", calls) }.toString())
                        writeSynced(File(target.filesDir, "$round-results.json"), report.toString())
                    }
                }
            }
            assertEquals(2, report["calls"].asJsonArray.size())
            assertTrue("Inspect sanitized per-call failureKind in private results", report["calls"].asJsonArray.all {
                it.asJsonObject["state"].asString == "completed"
            })
        } finally {
            keyFile.delete()
            workDirectory.deleteRecursively()
        }
    }

    private fun writeSynced(file: File, value: String) {
        val temporary = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(temporary).use { output -> output.write(value.toByteArray(Charsets.UTF_8)); output.fd.sync() }
        check(temporary.renameTo(file))
    }
    private data class Sample(val id: String, val fileName: String, val sourceSha256: String,
        val rect: NormalizedCropRect, val family: WardrobeFamily, val width: Int, val height: Int)
    private data class Prepared(val sample: Sample, val request: RecognitionRequest, val bytes: ByteArray)
}
