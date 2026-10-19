package com.lolita.app.data

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.repository.*
import com.lolita.app.data.tryon.TryOnSettings
import com.lolita.app.data.recognition.RecognitionSettings
import com.lolita.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Real Keystore, isolated credential files. Never reads or changes the app's own credentials. */
@RunWith(AndroidJUnit4::class)
class AiConfigurationRepositoryTest {
    private class Fixture {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "api-config-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getNoBackupFilesDir() = root
        }
        val images = TryOnSettings(context)
        val recognition = RecognitionSettings(context)
        val repository = AiConfigurationRepository(images, recognition)
        fun bytes() = root.listFiles()!!.associate { it.name to it.readBytes().toList() }
    }

    @Test fun oldFourKeysAndSelectionsSurviveReadsAndEditsOnlyAffectTheExplicitSlot() {
        val f = Fixture()
        try {
            f.images.saveKey("controlled-openai", ImageGenerationProvider.OPENAI)
            f.images.saveKey("controlled-seedream", ImageGenerationProvider.SEEDREAM)
            f.recognition.saveKey("controlled-doubao", RecognitionProvider.VOLCENGINE_ARK)
            f.recognition.saveKey("controlled-deepseek", RecognitionProvider.DEEPSEEK)
            f.images.selectProvider(ImageGenerationProvider.OPENAI)
            f.recognition.selectProvider(RecognitionProvider.DEEPSEEK)
            val original = f.bytes()
            val status = f.repository.read()
            assertEquals(AiKeySlot.entries.toSet(), status.keys.keys)
            assertTrue(status.keys.values.all { it == AiKeyStatus.CONFIGURED })
            assertEquals(ImageGenerationProvider.OPENAI, status.generation)
            assertEquals(RecognitionProvider.DEEPSEEK, status.recognition)
            assertEquals(original, f.bytes())
            assertFalse(status.toString().contains("controlled-"))
            f.repository.save(AiKeySlot.DOUBAO, "controlled-new-doubao")
            val changed = f.bytes()
            assertEquals(listOf("recognition-ark-credential-v1"), original.keys.filter { original[it] != changed[it] })
            assertEquals("controlled-new-doubao", RecognitionSettings(f.context).readKey(RecognitionProvider.VOLCENGINE_ARK))
            assertEquals("controlled-openai", TryOnSettings(f.context).readKey(ImageGenerationProvider.OPENAI))
            assertEquals("controlled-seedream", f.images.readKey(ImageGenerationProvider.SEEDREAM))
            assertEquals("controlled-deepseek", f.recognition.readKey(RecognitionProvider.DEEPSEEK))
            f.repository.select(AiKeySlot.SEEDREAM)
            assertEquals(ImageGenerationProvider.SEEDREAM, f.images.readProvider())
            assertEquals(RecognitionProvider.DEEPSEEK, f.recognition.readProvider())
            f.repository.clear(AiKeySlot.OPENAI)
            assertEquals("", f.images.readKey(ImageGenerationProvider.OPENAI))
            assertEquals(ImageGenerationProvider.SEEDREAM, f.images.readProvider())
            assertEquals("controlled-new-doubao", f.recognition.readKey(RecognitionProvider.VOLCENGINE_ARK))
        } finally { f.root.deleteRecursively() }
    }

    @Test fun removingLegacyInferredOpenAiKeyDoesNotSilentlyChangeDefaultService() {
        val f = Fixture()
        try {
            f.images.saveKey("controlled-legacy", ImageGenerationProvider.OPENAI)
            assertFalse(File(f.root, "image-generation-provider-v1").exists())
            assertEquals(ImageGenerationProvider.OPENAI, f.repository.read().generation)
            f.repository.clear(AiKeySlot.OPENAI)
            assertEquals(ImageGenerationProvider.OPENAI, TryOnSettings(f.context).readProvider())
            assertEquals(AiKeyStatus.EMPTY, f.repository.read().keys[AiKeySlot.OPENAI])
        } finally { f.root.deleteRecursively() }
    }

    @Test fun unreadableKeyOrSelectionCanBeRepairedWithoutChangingOtherCredentials() {
        val f = Fixture()
        try {
            File(f.root, "recognition-ark-credential-v1").writeText("controlled-corrupt")
            File(f.root, "recognition-provider-v1").writeText("invalid-selection")
            f.images.saveKey("controlled-other", ImageGenerationProvider.SEEDREAM)
            val original = File(f.root, "seedream-credential-v1").readBytes()
            val status = f.repository.read()
            assertNull(status.recognition)
            assertEquals(AiKeyStatus.UNREADABLE, status.keys[AiKeySlot.DOUBAO])
            assertFalse(status.toString().contains("controlled-"))
            f.repository.select(AiKeySlot.DOUBAO)
            f.repository.save(AiKeySlot.DOUBAO, "controlled-repaired")
            assertEquals(AiKeyStatus.CONFIGURED, f.repository.read().keys[AiKeySlot.DOUBAO])
            assertArrayEquals(original, File(f.root, "seedream-credential-v1").readBytes())
        } finally { f.root.deleteRecursively() }
    }
}
