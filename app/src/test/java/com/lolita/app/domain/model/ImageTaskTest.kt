package com.lolita.app.domain.model

import com.lolita.app.data.local.entity.ImageTask
import org.junit.Assert.*
import org.junit.Test

class ImageTaskTest {
    private val accessoryV3 = """{"version":3,"garments":[{"itemId":9,"name":"红色KC","role":"MAIN"}],"instructions":"只保留红色发饰","personalModel":null,"tryOnModel":null,"mannequinSubject":{"categoryId":4,"categoryName":"KC","presentation":"ACCESSORY"}}"""

    @Test fun standaloneAccessorySnapshotSurvivesStrictTaskRoundTrip() {
        val accepted = runCatching { ImageTaskRequest.decode(accessoryV3).also { it.validate(ImageTaskKind.MANNEQUIN) } }
        assertTrue("a valid standalone accessory task must decode", accepted.isSuccess)
        val decoded = accepted.getOrThrow()
        assertEquals(com.google.gson.JsonParser.parseString(accessoryV3), com.google.gson.JsonParser.parseString(decoded.encode()))
        assertEquals(listOf(9L), decoded.selections(listOf("/kc.jpg")).map { it.itemId })
        assertThrows(IllegalArgumentException::class.java) { decoded.validate(ImageTaskKind.TRY_ON) }
        assertThrows(IllegalArgumentException::class.java) { decoded.validate(ImageTaskKind.PERSONAL_MODEL) }
    }

    @Test fun malformedAccessorySnapshotsAreRejectedRatherThanBecomingDressTasks() {
        listOf(
            accessoryV3.replace("\"presentation\":\"ACCESSORY\"", "\"presentation\":\"FUTURE\""),
            accessoryV3.replace("\"categoryId\":4", "\"categoryId\":0"),
            accessoryV3.replace("\"categoryId\":4", "\"categoryId\":4.5"),
            accessoryV3.replace("\"categoryName\":\"KC\"", "\"categoryName\":false"),
            accessoryV3.replace("\"categoryName\":\"KC\"", "\"categoryName\":\" \""),
            accessoryV3.replace("\"categoryId\":4", "\"unexpected\":true,\"categoryId\":4"),
            accessoryV3.replace("\"version\":3", "\"version\":2"),
            accessoryV3.replace("\"version\":3", "\"version\":4")
        ).forEach { assertThrows(IllegalArgumentException::class.java) { ImageTaskRequest.decode(it) } }
    }

    @Test fun v2EncodingKeepsExactlyTheLegacyFieldSet() {
        val request = ImageTaskRequest(listOf(garment()), version = 2)
        val objectValue = com.google.gson.JsonParser.parseString(request.encode()).asJsonObject
        assertEquals(setOf("version", "garments", "instructions", "personalModel", "tryOnModel"), objectValue.keySet())
        assertEquals(request, ImageTaskRequest.decode(objectValue.toString()))
    }

    private fun garment(id: Long = 1, role: TryOnRole = TryOnRole.MAIN) = ImageTaskGarment(id, "裙子$id", role)
    private fun request() = ImageTaskRequest(listOf(garment()))
    private fun task(status: ImageTaskStatus) = ImageTask(
        id = "task-1", kind = ImageTaskKind.MANNEQUIN, provider = ImageGenerationProvider.SEEDREAM,
        title = "正面人台图", requestJson = request().encode(), sourceImagePaths = listOf("/images/source.jpg"),
        status = status, resultImagePath = "/images/result.png", createdAt = 10, updatedAt = 20
    )

    // A ninth garment or duplicate identity must never reach a paid call.
    @Test fun acceptsEightGarmentsButRejectsNineAndDuplicateIds() {
        assertEquals(8, ImageTaskRequest((1L..8).map { garment(it) }).selections((1..8).map { "/images/$it.jpg" }).size)
        assertThrows(IllegalArgumentException::class.java) { ImageTaskRequest((1L..9).map { garment(it) }) }
        assertThrows(IllegalArgumentException::class.java) { ImageTaskRequest(listOf(garment(), garment())) }
    }

    @Test fun kindValidationRejectsAccessoryOnlyTryOnAndMultipleMannequinGarments() {
        assertThrows(IllegalArgumentException::class.java) { ImageTaskRequest(listOf(garment(role = TryOnRole.HEAD))).validate(ImageTaskKind.TRY_ON) }
        assertThrows(IllegalArgumentException::class.java) { ImageTaskRequest(listOf(garment(), garment(2))).validate(ImageTaskKind.MANNEQUIN) }
        assertThrows(IllegalArgumentException::class.java) { ImageTaskRequest(listOf(garment()), "长".repeat(201)) }
    }

    @Test fun rejectsEmptyOrMalformedGarmentSnapshots() {
        listOf(emptyList(), listOf(ImageTaskGarment(0, "裙子", TryOnRole.MAIN)), listOf(ImageTaskGarment(1, " ", TryOnRole.MAIN)))
            .forEach { rows -> assertThrows(IllegalArgumentException::class.java) { ImageTaskRequest(rows) } }
    }

    // Paths remain separately ordered, so source release cannot corrupt metadata/history.
    @Test fun codecPreservesSnapshotsAndInstructionsWithoutImageOrCredentialFields() {
        val original = ImageTaskRequest(listOf(garment(9), garment(4, TryOnRole.HAND)), "保留袖口")
        val json = original.encode()
        val decoded = ImageTaskRequest.decode(json)
        assertEquals(original, decoded)
        assertEquals(1, decoded.version)
        assertFalse(json.contains("imagePath")); assertFalse(json.contains("provider")); assertFalse(json.contains("apiKey"))
        val selections = decoded.selections(listOf("/images/nine.jpg", "/images/four.jpg"))
        assertEquals(listOf(9L, 4L), selections.map { it.itemId })
        assertEquals(listOf("/images/nine.jpg", "/images/four.jpg"), selections.map { it.imagePath })
        assertEquals(TryOnRole.HAND, selections[1].role)
    }

    @Test fun v1StillDecodesExactly() {
        val json = """{"garments":[{"itemId":1,"name":"裙子1","role":"MAIN"}],"instructions":"","version":1}"""
        assertEquals(json, request().encode())
        assertEquals(request(), ImageTaskRequest.decode(json))
    }

    @Test fun v2TryOnSeparatesModelFromGarments() {
        val json = """{"version":2,"garments":[{"itemId":9,"name":"裙子","role":"MAIN"}],"instructions":"","personalModel":null,"tryOnModel":{"personalModelId":7,"name":"本人"}}"""
        val request = ImageTaskRequest.decode(json)
        request.validate(ImageTaskKind.TRY_ON)
        val rows = request.selections(listOf("/model.jpg", "/dress.jpg"))
        assertEquals(listOf(9L), rows.map { it.itemId })
        assertEquals(listOf("/dress.jpg"), rows.map { it.imagePath })
    }

    @Test fun v2PersonalGenerationAcceptsOnlyItsOwnMetadataAndPaths() {
        val personal = PersonalModelRequest("本人", PersonalModelMode.PHOTO, BodyMeasurements(168.0, 65.0), listOf(PersonalModelPhotoRole.FACE))
        val request = ImageTaskRequest(emptyList(), version = 2, personalModel = personal)
        request.validate(ImageTaskKind.PERSONAL_MODEL)
        assertEquals(request, ImageTaskRequest.decode(request.encode()))
        assertTrue(request.selections(listOf("/face.jpg")).isEmpty())
        assertNull(request.modelImagePath(listOf("/face.jpg")))
        assertThrows(IllegalArgumentException::class.java) { request.selections(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { request.validate(ImageTaskKind.TRY_ON) }
        assertThrows(IllegalArgumentException::class.java) { request.copy(garments = listOf(garment())).validate(ImageTaskKind.PERSONAL_MODEL) }
        assertThrows(IllegalArgumentException::class.java) { request.copy(tryOnModel = TryOnModelSnapshot(1, "其他")).validate(ImageTaskKind.PERSONAL_MODEL) }
        assertThrows(IllegalArgumentException::class.java) { ImageTaskRequest(listOf(garment()), personalModel = personal) }
    }

    @Test fun v2OptionalFieldsAreExplicitAndStrict() {
        val request = ImageTaskRequest(listOf(garment()), version = 2)
        assertEquals(request, ImageTaskRequest.decode(request.encode()))
        assertTrue(request.encode().contains("\"personalModel\":null"))
        assertTrue(request.encode().contains("\"tryOnModel\":null"))
        assertNull(request.modelImagePath(listOf("/dress.jpg")))
        val selected = request.copy(tryOnModel = TryOnModelSnapshot(2, "本人"))
        assertEquals("/model.jpg", selected.modelImagePath(listOf("/model.jpg", "/dress.jpg")))
        assertThrows(IllegalArgumentException::class.java) { selected.selections(listOf("/dress.jpg")) }
        assertThrows(IllegalArgumentException::class.java) { selected.selections(listOf("", "/dress.jpg")) }
        assertThrows(IllegalArgumentException::class.java) { selected.validate(ImageTaskKind.MANNEQUIN) }
        listOf(
            selected.encode().replace("\"personalModel\":null,", ""),
            selected.encode().replace("\"personalModelId\":2", "\"personalModelId\":0"),
            selected.encode().replace("\"personalModelId\":2", "\"personalModelId\":2.5"),
            selected.encode().replace("\"name\":\"本人\"", "\"name\":false"),
            selected.encode().replace("\"name\":\"本人\"", "\"name\":\" \""),
            selected.encode().replace("\"tryOnModel\":", "\"unknown\":null,\"tryOnModel\":")
        ).forEach { json -> assertThrows(IllegalArgumentException::class.java) { ImageTaskRequest.decode(json) } }
        assertThrows(IllegalArgumentException::class.java) { TryOnModelSnapshot(-1, "本人") }
    }

    @Test fun rejectsUnknownVersionRoleAndUnexpectedRequestFields() {
        listOf(
            """{"version":2,"garments":[{"itemId":1,"name":"裙子","role":"MAIN"}],"instructions":""}""",
            """{"version":1,"garments":[{"itemId":1,"name":"裙子","role":"FUTURE"}],"instructions":""}""",
            """{"version":1,"garments":[{"itemId":1,"name":"裙子","role":"MAIN","imagePath":"/private.jpg"}],"instructions":""}""",
            """{"version":1,"garments":null,"instructions":""}"""
        ).forEach { json -> assertThrows(IllegalArgumentException::class.java) { ImageTaskRequest.decode(json) } }
    }

    @Test fun sourceSelectionRequiresExactCountAndNonblankPaths() {
        listOf(emptyList(), listOf(""), listOf("/one.jpg", "/two.jpg"))
            .forEach { paths -> assertThrows(IllegalArgumentException::class.java) { request().selections(paths) } }
    }

    @Test fun archivedTaskDoesNotRequireOldSourceImagePaths() {
        val archived = task(ImageTaskStatus.COMPLETED).copy(sourceImagePaths = emptyList())
        archived.validate()
        assertEquals(listOf(1L), ImageTaskRequest.decode(archived.requestJson).garments.map { it.itemId })
        assertThrows(IllegalArgumentException::class.java) { archived.copy(status = ImageTaskStatus.QUEUED).validate() }
    }

    @Test fun restartInterruptsOnlyQueuedAndExecutingStates() {
        val interrupted = setOf(ImageTaskStatus.QUEUED, ImageTaskStatus.PREPARING, ImageTaskStatus.GENERATING, ImageTaskStatus.SAVING)
        ImageTaskStatus.entries.forEach { status ->
            assertEquals(if (status in interrupted) ImageTaskStatus.INTERRUPTED else status, status.interruptedOnRestart())
        }
        assertTrue(ImageTaskStatus.FAILED.isTerminal)
        assertFalse(ImageTaskStatus.AWAITING_CONFIRMATION.isTerminal)
    }

    @Test fun protectsOnlyNeededSourcesAndUnconfirmedOutput() {
        assertEquals(setOf("/images/source.jpg"), task(ImageTaskStatus.QUEUED).protectedImagePaths())
        assertEquals(setOf("/images/source.jpg"), task(ImageTaskStatus.GENERATING).protectedImagePaths())
        assertEquals(setOf("/images/result.png"), task(ImageTaskStatus.AWAITING_CONFIRMATION).protectedImagePaths())
        listOf(ImageTaskStatus.COMPLETED, ImageTaskStatus.FAILED, ImageTaskStatus.INTERRUPTED, ImageTaskStatus.CANCELLED, ImageTaskStatus.DISCARDED)
            .forEach { status ->
                assertTrue(task(status).protectedImagePaths().isEmpty())
                assertTrue(task(status).releaseUnusedSourceImages().sourceImagePaths.isEmpty())
            }
    }
}
