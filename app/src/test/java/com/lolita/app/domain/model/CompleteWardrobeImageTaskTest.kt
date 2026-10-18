package com.lolita.app.domain.model

import com.lolita.app.data.local.entity.ImageTask
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CompleteWardrobeImageTaskTest {
    @Test fun v4FreezesHeadInputsAndPromptAndMapsOnlyTheActualPath() {
        val folder = kotlin.io.path.createTempDirectory("immutable-head-").toFile()
        try {
            val head = File(folder, "head.png").apply { writeText("chosen candidate") }
            val wig = File(folder, "wig.jpg").apply { writeText("chosen wig") }
            val snapshot = WigHeadTemplateSnapshot("chosen", "候选甲", 1, imageSha256(head.path), head.path, 1024, 1536)
            val request = ImageTaskRequest(listOf(ImageTaskGarment(11, "假发", TryOnRole.MAIN, CategorySemantic.FULL_WIG, ItemComposition.SINGLE)),
                version = 4, mannequinSubject = MannequinSubject(2, "改过名的分类", MannequinPresentation.WIG_HEAD, CategorySemantic.FULL_WIG),
                promptVersion = 1, promptSnapshot = "frozen prompt", serviceModel = "frozen-model", inputSha256s = listOf(imageSha256(head.path), imageSha256(wig.path)), wigHeadTemplate = snapshot)
            val paths = listOf(head.path, wig.path)
            val decoded = ImageTaskRequest.decode(request.encode())
            assertEquals(request, decoded)
            decoded.validate(ImageTaskKind.MANNEQUIN)
            decoded.validateInputImages(paths)
            assertEquals(wig.path, decoded.selections(paths).single().imagePath)
            assertEquals("frozen prompt", decoded.promptSnapshot)
            assertEquals("zip/head.png", decoded.mapImagePaths { "zip/head.png" }.wigHeadTemplate!!.imagePath)
            assertEquals(snapshot.sha256, decoded.mapImagePaths { "zip/head.png" }.wigHeadTemplate!!.sha256)
            head.writeText("silently replaced asset")
            assertThrows(IllegalArgumentException::class.java) { decoded.validateInputImages(paths) }
            assertEquals("候选甲", decoded.wigHeadTemplate!!.name)
        } finally { folder.deleteRecursively() }
    }

    @Test fun v4SourcesAreProtectedAfterCompletionFailureCancellationAndRestart() {
        val request = ImageTaskRequest(listOf(ImageTaskGarment(1, "裙", TryOnRole.MAIN, CategorySemantic.JSK, ItemComposition.SINGLE)),
            version = 4, promptVersion = 1, promptSnapshot = "fixed", serviceModel = "model", inputSha256s = listOf("a".repeat(64), "b".repeat(64)))
        for (status in ImageTaskStatus.entries) {
            val task = ImageTask("id", ImageTaskKind.TRY_ON, ImageGenerationProvider.SEEDREAM, "试衣", request.encode(), listOf("/model", "/dress"), status = status, keepSourceImages = true)
            assertTrue(status.name, task.protectedImagePaths().containsAll(listOf("/model", "/dress")))
            assertEquals(task.sourceImagePaths, task.releaseUnusedSourceImages().sourceImagePaths)
        }
    }

    @Test fun croppedProductPreservesAndMapsItsOriginalWithoutUploadingTheOriginal() {
        val head = WigHeadTemplateSnapshot("head", "候选", 1, "a".repeat(64), "/head", 1024, 1536)
        val request = ImageTaskRequest(listOf(ImageTaskGarment(1, "假发", TryOnRole.MAIN, CategorySemantic.FULL_WIG)), version = 4,
            mannequinSubject = MannequinSubject(2, "假发", MannequinPresentation.WIG_HEAD, CategorySemantic.FULL_WIG),
            promptVersion = 1, promptSnapshot = "fixed", serviceModel = "model", inputSha256s = listOf(head.sha256, "b".repeat(64)),
            wigHeadTemplate = head, productOriginalImagePath = "/private-original")
        val inputs = listOf("/head", "/crop")
        assertEquals("/crop", request.selections(inputs).single().imagePath)
        assertEquals("zip/private-original", request.mapImagePaths { "zip" + it }.productOriginalImagePath)
        val task = ImageTask("id", ImageTaskKind.MANNEQUIN, ImageGenerationProvider.OPENAI, "假发", request.encode(), inputs,
            status = ImageTaskStatus.FAILED, keepSourceImages = true)
        assertEquals(setOf("/head", "/crop", "/private-original"), task.protectedImagePaths())
        val earlyV4 = com.google.gson.JsonParser.parseString(request.copy(productOriginalImagePath = null).encode()).asJsonObject.apply { remove("productOriginalImagePath") }
        assertNull(ImageTaskRequest.decode(earlyV4.toString()).productOriginalImagePath)
    }

    @Test fun oldRequestsKeepTheirExactFieldsAndLegacyHairRule() {
        val legacy = """{"garments":[{"itemId":1,"name":"原裙","role":"MAIN"}],"instructions":"","version":1}"""
        val decoded = ImageTaskRequest.decode(legacy)
        assertEquals(com.google.gson.JsonParser.parseString(legacy), com.google.gson.JsonParser.parseString(decoded.encode()))
        val prompt = TryOnRequest(decoded.selections(listOf("/dress")), protocolVersion = decoded.version).prompt()
        assertTrue(prompt.contains("hair, neutral standing pose"))
        assertFalse(prompt.contains("replace the original hairstyle"))
        assertFalse(prompt.contains("Do not shorten"))
        assertThrows(IllegalArgumentException::class.java) { ImageTaskRequest.decode(legacy.replace("MAIN", "WIG")) }
    }
}
