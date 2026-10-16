package com.lolita.app.data.personalmodel

import com.lolita.app.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PersonalModelGeneratorTest {
    private fun parameters() = PersonalModelRequest("我的模特", PersonalModelMode.PARAMETERS, BodyMeasurements(163.0, 52.0))

    @Test fun parametersGenerateWithTemplateOnlyAndPublishExactlyOnePreview() = runBlocking {
        var paid = 0
        var saved = 0
        var markedBeforeSend = false
        val generator = PersonalModelGenerator(
            readCredentials = { ImageGenerationCredentials(ImageGenerationProvider.SEEDREAM, "controlled-key") },
            validateSourceImage = { true },
            prepareImages = { paths -> assertTrue(paths.isEmpty()); listOf("TEMPLATE".toByteArray()) },
            generateImage = { credentials, images, prompt ->
                paid++
                assertTrue(markedBeforeSend)
                assertEquals(ImageGenerationProvider.SEEDREAM, credentials.provider)
                assertEquals(1, images.size)
                assertTrue(prompt.contains("fictional"))
                byteArrayOf(9)
            }, savePreview = { saved++; "/generated/preview.png" }, discardPreview = {})
        assertEquals("/generated/preview.png", generator.generate(parameters(), emptyList(),
            expectedProvider = ImageGenerationProvider.SEEDREAM, beforeRequest = { markedBeforeSend = true }))
        assertEquals(1, paid)
        assertEquals(1, saved)
    }

    @Test fun staleProviderOrMissingPhotoNeverMakesPaidRequest() = runBlocking {
        var calls = 0
        val generator = PersonalModelGenerator(
            readCredentials = { ImageGenerationCredentials(ImageGenerationProvider.OPENAI, "controlled-key") },
            validateSourceImage = { false }, prepareImages = { listOf(byteArrayOf(1), byteArrayOf(2)) },
            generateImage = { _, _, _ -> calls++; byteArrayOf(9) }, savePreview = { "/preview.png" }, discardPreview = {})
        assertTrue(runCatching { generator.generate(parameters(), emptyList(), expectedProvider = ImageGenerationProvider.SEEDREAM) }.isFailure)
        val photo = PersonalModelRequest("我的模特", PersonalModelMode.PHOTO, BodyMeasurements(163.0, 52.0), listOf(PersonalModelPhotoRole.FACE))
        assertTrue(runCatching { generator.generate(photo, listOf("/missing.jpg")) }.isFailure)
        assertEquals(0, calls)
    }

    @Test fun serviceFailureIsNotRetriedAndDoesNotSaveAnImage() = runBlocking {
        var calls = 0
        var saved = 0
        val generator = PersonalModelGenerator(
            readCredentials = { ImageGenerationCredentials(ImageGenerationProvider.SEEDREAM, "controlled-key") },
            validateSourceImage = { true }, prepareImages = { listOf(byteArrayOf(1)) },
            generateImage = { _, _, _ -> calls++; throw ImageGenerationFailure(ImageGenerationFailureKind.NETWORK) },
            savePreview = { saved++; "/preview.png" }, discardPreview = {})
        assertTrue(runCatching { generator.generate(parameters(), emptyList()) }.isFailure)
        assertEquals(1, calls)
        assertEquals(0, saved)
    }
}
