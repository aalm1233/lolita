package com.lolita.app.data.file

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lolita.app.data.local.entity.ImageTask
import com.lolita.app.data.local.entity.TryOnResult
import com.lolita.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class PersonalModelBackupFormatTest {
    private val gson = Gson()
    private fun root(version: Int = 9) = gson.toJsonTree(BackupData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())).asJsonObject.apply {
        addProperty("formatVersion", version)
        addProperty("schemaVersion", if (version == 9) 29 else 28)
        if (version == 9) { add("personalModels", JsonArray()); add("personalModelDrafts", JsonArray()) }
    }
    private val request = """{"name":"我的模特","mode":"PHOTO","measurements":{"heightCm":162.0,"weightKg":55.0,"bustCm":null,"waistCm":null,"hipCm":null,"shoulderCm":null,"inseamCm":null},"photoRoles":["FACE"],"appearance":"FEMALE","instructions":"保留自然比例"}"""
    private fun profile() = JsonObject().apply {
        addProperty("id", 12); addProperty("name", "我的模特"); addProperty("requestJson", request)
        add("sourceImagePaths", gson.toJsonTree(listOf("/images/face.png")))
        addProperty("imageUrl", "/images/model.png"); addProperty("provider", "SEEDREAM")
        addProperty("model", "api-model"); addProperty("createdAt", 123); addProperty("updatedAt", 124)
    }
    private fun withProfile() = root().apply { this["personalModels"].asJsonArray.add(profile()) }

    // Missing migration arrays would silently lose personal model history on older restores.
    @Test fun formatsOneThroughEightSupplyEmptyProfilesAndDrafts() {
        for (version in 1..8) {
            val old = root(version).apply { remove("personalModels"); remove("personalModelDrafts") }
            val exported = gson.toJsonTree(BackupFormat.parse(old.toString())).asJsonObject
            assertEquals(JsonArray(), exported["personalModels"])
            assertEquals(JsonArray(), exported["personalModelDrafts"])
        }
    }

    @Test fun formatNineRoundTripsProfileRequestAndOriginalPhotos() {
        val input = withProfile()
        val output = gson.toJsonTree(BackupFormat.parse(input.toString())).asJsonObject
        assertEquals(input["personalModels"], output["personalModels"])
    }

    @Test fun formatNineRequiresBothNewTables() {
        for (field in listOf("personalModels", "personalModelDrafts")) {
            assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(root().apply { remove(field) }.toString()) }
        }
    }

    @Test fun unknownProviderAndInvalidStrictRequestCannotBeImported() {
        val badProvider = withProfile().apply { this["personalModels"].asJsonArray[0].asJsonObject.addProperty("provider", "UNKNOWN") }
        val unknownMode = withProfile().apply { this["personalModels"].asJsonArray[0].asJsonObject.addProperty("requestJson", request.replace("PHOTO", "UNKNOWN")) }
        val nonfinite = withProfile().apply { this["personalModels"].asJsonArray[0].asJsonObject.addProperty("requestJson", request.replace("162.0", "1e999")) }
        val hiddenPath = withProfile().apply {
            val body = JsonParser.parseString(request).asJsonObject.apply { addProperty("path", "/hidden") }
            this["personalModels"].asJsonArray[0].asJsonObject.addProperty("requestJson", body.toString())
        }
        listOf(badProvider, unknownMode, nonfinite, hiddenPath).forEach { input ->
            assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(input.toString()) }
        }
    }

    private fun task(status: ImageTaskStatus = ImageTaskStatus.AWAITING_CONFIRMATION) = ImageTask(
        "9145fafd-e0b9-4a85-9e20-87bea5e3e279", ImageTaskKind.PERSONAL_MODEL,
        ImageGenerationProvider.SEEDREAM, "我的模特",
        ImageTaskRequest(emptyList(), version = 2, personalModel = PersonalModelRequest.decode(request)).encode(),
        listOf("/images/original.png"), status, true,
        if (status == ImageTaskStatus.AWAITING_CONFIRMATION) "/images/preview.png" else null,
        createdAt = 123, updatedAt = 124)

    @Test fun pendingAndRetryPersonalRequestsKeepSourcePhotosButVersionOneMannequinStaysCompatible() {
        for (status in listOf(ImageTaskStatus.AWAITING_CONFIRMATION, ImageTaskStatus.FAILED, ImageTaskStatus.INTERRUPTED, ImageTaskStatus.CANCELLED)) {
            val input = root().apply { add("imageTasks", gson.toJsonTree(listOf(task(status)))) }
            val actual = BackupFormat.parse(input.toString()).imageTasks.single()
            assertEquals(listOf("/images/original.png"), actual.sourceImagePaths)
            assertEquals(status, actual.status)
        }
        val legacy = task().copy(kind = ImageTaskKind.MANNEQUIN, sourceImagePaths = emptyList(),
            requestJson = """{"version":1,"garments":[{"itemId":41,"name":"原裙子","role":"MAIN"}],"instructions":"保持腰线"}""")
        assertEquals(legacy, BackupFormat.parse(root(8).apply { add("imageTasks", gson.toJsonTree(listOf(legacy))) }.toString()).imageTasks.single())
    }

    @Test fun strictV2RejectsMissingRequestFieldsAndMismatchedRolePaths() {
        val cases = listOf(task().copy(sourceImagePaths = emptyList()),
            task().copy(requestJson = task().requestJson.replace("\"tryOnModel\":null,", "").replace(",\"tryOnModel\":null", "")))
        cases.forEach { task ->
            assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(root().apply { add("imageTasks", gson.toJsonTree(listOf(task))) }.toString()) }
        }
    }

    @Test fun draftRawInputsRoundTripAndUnknownDraftFieldsAreRejected() {
        val draftData = PersonalModelDraftData(name = "待填写", heightCm = "16.", weightKg = "", photoRoles = listOf(PersonalModelPhotoRole.FACE))
        val draft = JsonObject().apply {
            addProperty("id", "67ac1c13-cdb9-41c6-b07c-15d91ac916c1"); addProperty("dataJson", draftData.encode())
            add("sourceImagePaths", gson.toJsonTree(listOf("/images/draft.jpg"))); addProperty("updatedAt", 123)
        }
        val input = root().apply { this["personalModelDrafts"].asJsonArray.add(draft) }
        assertEquals(input["personalModelDrafts"], gson.toJsonTree(BackupFormat.parse(input.toString())).asJsonObject["personalModelDrafts"])
        val bad = JsonParser.parseString(draftData.encode()).asJsonObject.apply { addProperty("apiKey", "forbidden") }
        draft.addProperty("dataJson", bad.toString())
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(input.toString()) }
    }

    @Test fun oldTryOnHistoryDefaultsToBuiltInModelAndDeletedPersonalModelSnapshotSurvives() {
        val result = TryOnResult(7, "搭配", "/images/result.png", listOf("41"), listOf("原裙子"), "api-model", 123)
        val old = BackupFormat.parse(root(8).apply { add("tryOnResults", gson.toJsonTree(listOf(result))) }.toString()).tryOnResults.single()
        assertNull(old.personalModelId); assertNull(old.personalModelName)
        val snapshot = result.copy(personalModelId = 12, personalModelName = "历史模特名称")
        assertEquals(snapshot, BackupFormat.parse(root().apply { add("tryOnResults", gson.toJsonTree(listOf(snapshot))) }.toString()).tryOnResults.single())
    }

    @Test fun taskCannotClaimUnrelatedAcceptedModelAndSnapshotMustBeComplete() {
        val wrongOwner = task(ImageTaskStatus.COMPLETED).copy(sourceImagePaths = emptyList(), personalModelId = 12, resultImagePath = "/images/wrong.png")
        val input = withProfile().apply { add("imageTasks", gson.toJsonTree(listOf(wrongOwner))) }
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(input.toString()) }
        val broken = TryOnResult(7, "搭配", "/images/result.png", listOf("41"), listOf("原裙子"), "api-model", 123, personalModelId = 12)
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(root().apply { add("tryOnResults", gson.toJsonTree(listOf(broken))) }.toString()) }
    }

    @Test fun pendingPreviewCannotAlsoBeAnOwnedInputPhoto() {
        val aliased = task().copy(resultImagePath = "/images/original.png")
        assertThrows(IllegalArgumentException::class.java) {
            BackupFormat.parse(root().apply { add("imageTasks", gson.toJsonTree(listOf(aliased))) }.toString())
        }
    }

    @Test fun completedPersonalTaskNeedsAcceptedIdentityEvenAfterTargetIsDeleted() {
        val completed = task(ImageTaskStatus.COMPLETED).copy(sourceImagePaths = emptyList())
        assertThrows(IllegalArgumentException::class.java) {
            BackupFormat.parse(root().apply { add("imageTasks", gson.toJsonTree(listOf(completed))) }.toString())
        }
        val deletedTarget = completed.copy(personalModelId = 12, resultImagePath = null)
        assertEquals(deletedTarget, BackupFormat.parse(root().apply { add("imageTasks", gson.toJsonTree(listOf(deletedTarget))) }.toString()).imageTasks.single())
    }
}
