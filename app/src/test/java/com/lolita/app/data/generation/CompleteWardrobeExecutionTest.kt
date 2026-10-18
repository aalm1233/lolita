package com.lolita.app.data.generation

import com.lolita.app.data.mannequin.MannequinGenerator
import com.lolita.app.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CompleteWardrobeExecutionTest {
    @Test fun acceptedHeadRequestUsesItsTwoInputsAfterCategoryOrCatalogChanges() = runBlocking {
        val directory = kotlin.io.path.createTempDirectory("head-execution-").toFile()
        try {
            val head = File(directory, "head.png").apply { writeText("original-head") }
            val product = File(directory, "wig.jpg").apply { writeText("original-product") }
            val preview = File(directory, "preview.png")
            val template = WigHeadTemplateSnapshot("original", "原候选", 1, imageSha256(head.path), head.path, 1024, 1536)
            val subject = MannequinSubject(2, "原假发分类", MannequinPresentation.WIG_HEAD, CategorySemantic.FULL_WIG)
            val request = MannequinRequest(1, product.path, subject = subject, wigHeadTemplate = template, referenceViewConfirmed = true)
            val snapshot = ImageTaskRequest(listOf(ImageTaskGarment(1, "原商品名", TryOnRole.MAIN, CategorySemantic.FULL_WIG)),
                version = 4, mannequinSubject = subject, promptVersion = 1, promptSnapshot = request.prompt(),
                serviceModel = ImageGenerationProvider.OPENAI.model, inputSha256s = listOf(imageSha256(head.path), imageSha256(product.path)), wigHeadTemplate = template)
            var sent = 0
            val generator = MannequinGenerator(findItem = { error("queued snapshots do not read mutable items") }, findCategory = { error("queued snapshots do not read mutable categories") },
                readCredentials = { ImageGenerationCredentials(ImageGenerationProvider.OPENAI, "controlled-key") }, prepareImages = { error("must not read current catalog") },
                generateImage = { _, images, prompt ->
                    sent++
                    assertEquals(2, images.size)
                    assertArrayEquals("original-head".toByteArray(), images[0])
                    assertArrayEquals("original-product".toByteArray(), images[1])
                    assertEquals(snapshot.promptSnapshot, prompt)
                    "generated".toByteArray()
                }, savePreview = { preview.writeBytes(it); preview.path }, discardPreview = {})
            val paths = listOf(head.path, product.path)
            assertEquals(preview.path, generator.generate(request, immutableRequest = snapshot, immutablePaths = paths,
                prepareSnapshotImages = { it.map { path -> File(path).readBytes() } }))
            assertEquals(1, sent)
            head.writeText("replacement-head")
            assertTrue(runCatching { generator.generate(request, immutableRequest = snapshot, immutablePaths = paths,
                prepareSnapshotImages = { it.map { path -> File(path).readBytes() } }) }.isFailure)
            assertEquals(1, sent)
        } finally { directory.deleteRecursively() }
    }
}
