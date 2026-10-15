package com.lolita.app.domain.model

import com.lolita.app.data.local.entity.ImageTask
import org.junit.Assert.*
import org.junit.Test

class ImageTaskTest {
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
