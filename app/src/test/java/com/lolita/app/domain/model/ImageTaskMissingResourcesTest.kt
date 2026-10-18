package com.lolita.app.domain.model

import com.lolita.app.data.local.entity.ImageTask
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.ui.screen.tasks.ImageTaskUiState
import com.lolita.app.ui.screen.tasks.canRetry
import org.junit.Assert.*
import org.junit.Test

class ImageTaskMissingResourcesTest {
    private fun task(): ImageTask {
        val head = WigHeadTemplateSnapshot("original-head", "原选头模候选", 1, "a".repeat(64), "/missing/original-head.png", 1024, 1536)
        val request = ImageTaskRequest(listOf(ImageTaskGarment(1, "原假发", TryOnRole.MAIN, CategorySemantic.FULL_WIG)), version = 4,
            mannequinSubject = MannequinSubject(7, "假发", MannequinPresentation.WIG_HEAD, CategorySemantic.FULL_WIG),
            promptVersion = 1, promptSnapshot = "original fixed prompt", serviceModel = ImageGenerationProvider.SEEDREAM.model,
            inputSha256s = listOf(head.sha256, "b".repeat(64)), wigHeadTemplate = head)
        return ImageTask("missing", ImageTaskKind.MANNEQUIN, ImageGenerationProvider.SEEDREAM, "原任务", request.encode(),
            listOf(head.imagePath, "/missing/wig.jpg"), status = ImageTaskStatus.INTERRUPTED, keepSourceImages = true, sourceResourcesMissing = true)
    }

    @Test fun missingV4InputsCannotOfferPaidRetryAndKeepTheOriginalSnapshot() {
        val task = task(); task.validate()
        assertFalse(task.canRetry)
        assertEquals(task.requestJson, task.releaseUnusedSourceImages().requestJson)
        assertEquals(task.sourceImagePaths, task.releaseUnusedSourceImages().sourceImagePaths)
        assertEquals("original-head", ImageTaskRequest.decode(task.requestJson).wigHeadTemplate!!.id)
        assertEquals("original fixed prompt", ImageTaskRequest.decode(task.requestJson).promptSnapshot)
    }

    @Test fun completedResultsRemainViewableWhenOnlyTheirInputsAreMissing() {
        val task = task().copy(status = ImageTaskStatus.COMPLETED, resultImagePath = "/saved/result.png")
        task.validate()
        val item = Item(id = 1, brandId = 1, categoryId = 7, name = "假发", description = "", imageUrls = listOf("/saved/result.png"), status = ItemStatus.OWNED)
        assertEquals("/saved/result.png", ImageTaskUiState(items = listOf(item)).resultPath(task))
        assertEquals(task.sourceImagePaths.toSet(), task.protectedImagePaths())
    }

    @Test fun missingMarkerCannotBypassLegacyOrV4StructuralValidation() {
        val legacy = ImageTaskRequest(listOf(ImageTaskGarment(1, "裙", TryOnRole.MAIN)))
        assertThrows(IllegalArgumentException::class.java) { task().copy(requestJson = legacy.encode(), sourceImagePaths = listOf("/legacy"), keepSourceImages = false).validate() }
        assertThrows(IllegalArgumentException::class.java) { task().copy(sourceImagePaths = emptyList()).validate() }
        val intact = task().copy(sourceResourcesMissing = false)
        intact.validate(); assertTrue(intact.canRetry)
    }
}
