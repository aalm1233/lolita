package com.lolita.app.data.file

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.lolita.app.data.local.entity.ImageTask
import com.lolita.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ImageTaskBackupFormatTest {
    @Test fun pendingStandaloneAccessoryTaskKeepsItsPresentationThroughBackup() {
        val request = """{"version":3,"garments":[{"itemId":41,"name":"袜子","role":"MAIN"}],"instructions":"保留原来的一双","personalModel":null,"tryOnModel":null,"mannequinSubject":{"categoryId":7,"categoryName":"袜子","presentation":"ACCESSORY"}}"""
        val input = modern(task().copy(requestJson = request))
        val accepted = runCatching { BackupFormat.parse(input.toString()) }
        assertTrue("pending accessory task must remain restorable", accepted.isSuccess)
        val result = gson.toJsonTree(accepted.getOrThrow()).asJsonObject
        assertEquals(input["imageTasks"], result["imageTasks"])
    }

    private val gson = Gson()
    private fun root() = gson.toJsonTree(BackupData(emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList())).asJsonObject
    private fun task(id: String = "9145fafd-e0b9-4a85-9e20-87bea5e3e279") = ImageTask(
        id, ImageTaskKind.MANNEQUIN, ImageGenerationProvider.SEEDREAM, "花鸟龙月人台图",
        ImageTaskRequest(listOf(ImageTaskGarment(41,"花鸟龙月",TryOnRole.MAIN)),"保持腰线").encode(),
        emptyList(), ImageTaskStatus.AWAITING_CONFIRMATION, true, "/images/pending.png", null, 123, 124)
    private fun modern(vararg tasks: ImageTask): JsonObject = root().apply {
        addProperty("formatVersion",8); addProperty("schemaVersion",28)
        add("imageTasks",gson.toJsonTree(tasks))
    }

    // Removing the tasks field from export or failing old-format migration loses history.
    @Test fun oldFormatsOneThroughSevenSupplyEmptyTaskHistory() {
        for (version in 1..7) {
            val old = root().apply { addProperty("formatVersion",version); addProperty("schemaVersion",27); remove("imageTasks") }
            val exported = gson.toJsonTree(BackupFormat.parse(old.toString())).asJsonObject
            assertTrue("old format $version", exported.has("imageTasks"))
            assertEquals(0,exported["imageTasks"].asJsonArray.size())
        }
    }

    @Test fun completeTaskRecordRoundTripsWithoutLosingPendingResultOrRequest() {
        val input = modern(task())
        val result = gson.toJsonTree(BackupFormat.parse(input.toString())).asJsonObject
        assertEquals(input["imageTasks"],result["imageTasks"])
    }

    @Test fun activeTasksRemainSnapshotEvidenceUntilExplicitImportNormalization() {
        val active = task().copy(status=ImageTaskStatus.GENERATING,sourceImagePaths=listOf("/images/source.jpg"),resultImagePath=null)
        val parsed = gson.toJsonTree(BackupFormat.parse(modern(active).toString())).asJsonObject["imageTasks"].asJsonArray.single().asJsonObject
        assertEquals("GENERATING",parsed["status"].asString)
        assertTrue(parsed["requestSent"].asBoolean)
        assertEquals("/images/source.jpg",parsed["sourceImagePaths"].asJsonArray.single().asString)
    }

    @Test fun incompleteNewBackupUnknownFieldsAndInconsistentTaskTypesAreRejected() {
        val missing = modern().apply { remove("imageTasks") }
        val unknown = modern(task()).apply { this["imageTasks"].asJsonArray[0].asJsonObject.addProperty("apiKey","must never import") }
        val cases = listOf(missing,unknown,modern(task().copy(kind=ImageTaskKind.TRY_ON)),
            modern(task().copy(requestJson=task().requestJson.replace("\"version\":1","\"version\":2"))),
            modern(task(),task()))
        cases.forEach { assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(it.toString()) } }
    }

    @Test fun nullMetadataUnknownEnumsAndTwoOwnersOfPendingPreviewAreRejected() {
        val missing = modern(task()).apply { this["imageTasks"].asJsonArray[0].asJsonObject.remove("requestSent") }
        val invalid = modern(task()).apply { this["imageTasks"].asJsonArray[0].asJsonObject.addProperty("status","NEW_UNKNOWN") }
        val sharedPending = modern(task(),task("67ac1c13-cdb9-41c6-b07c-15d91ac916c1"))
        listOf(missing,invalid,sharedPending).forEach {
            assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(it.toString()) }
        }
    }
}
