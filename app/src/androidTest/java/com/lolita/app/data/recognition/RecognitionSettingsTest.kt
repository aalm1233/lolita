package com.lolita.app.data.recognition

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lolita.app.data.tryon.TryOnSettings
import com.lolita.app.domain.model.ImageGenerationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Real Android Keystore, isolated credential files. Never reads an installed user's settings. */
@RunWith(AndroidJUnit4::class)
class RecognitionSettingsTest {
    private fun isolated(block: (Context) -> Unit) {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(app.cacheDir, "recognition-settings-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) {
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(directory, "no-backup").apply { mkdirs() }
        }
        try { block(context) } finally { directory.deleteRecursively() }
    }

    @Test fun recognitionNeverInfersOrChangesGenerationCredentials() = isolated { context ->
        val generation = TryOnSettings(context)
        generation.saveKey("generation-only-secret", ImageGenerationProvider.OPENAI)
        val recognition = RecognitionSettings(context)
        assertEquals("", recognition.readKey()); assertFalse(recognition.hasKey())
        recognition.saveKey("recognition-only-secret")
        assertEquals("recognition-only-secret", RecognitionSettings(context).readKey())
        assertEquals("generation-only-secret", generation.readKey(ImageGenerationProvider.OPENAI))
        val bytes = File(context.noBackupFilesDir, "recognition-credential-v1").readText()
        assertFalse(bytes.contains("recognition-only-secret")); assertFalse(bytes.contains("generation-only-secret"))
        assertEquals(0, context.filesDir.listFiles().orEmpty().size)
        recognition.clearKey()
        assertEquals("", recognition.readKey()); assertFalse(recognition.hasKey())
        assertEquals("generation-only-secret", generation.readKey(ImageGenerationProvider.OPENAI))
    }

    @Test fun credentialsUseFreshIvAndChangesOnlyAfterSuccessfulMutations() = isolated { context ->
        val settings = RecognitionSettings(context)
        val initial = settings.changes.value
        for (invalid in listOf("", " ", "sk key", "secret\nheader", "x".repeat(513))) {
            assertThrows(IllegalArgumentException::class.java) { settings.saveKey(invalid) }
        }
        assertEquals(initial, settings.changes.value)
        settings.saveKey("dummy-recognition-secret")
        val file = File(context.noBackupFilesDir, "recognition-credential-v1")
        val first = file.readText()
        settings.saveKey("dummy-recognition-secret")
        assertNotEquals(first, file.readText())
        assertEquals(initial + 2, settings.changes.value)
        settings.clearKey()
        assertEquals(initial + 3, settings.changes.value)
    }

    @Test fun aGenerationCiphertextCannotBeDecryptedWithTheRecognitionKeyPurpose() = isolated { context ->
        val generation = TryOnSettings(context)
        generation.saveKey("generation-only-secret", ImageGenerationProvider.OPENAI)
        val recognition = RecognitionSettings(context)
        recognition.saveKey("recognition-only-secret")
        File(context.noBackupFilesDir, "recognition-credential-v1").writeBytes(File(context.noBackupFilesDir, "tryon-credential-v1").readBytes())
        assertNotNull(runCatching { recognition.readKey() }.exceptionOrNull())
        assertFalse(recognition.hasKey())
        assertEquals("generation-only-secret", generation.readKey(ImageGenerationProvider.OPENAI))
    }

    @Test fun malformedCiphertextNeverExposesRawDataOrCauses() = isolated { context ->
        val settings = RecognitionSettings(context)
        settings.saveKey("dummy-recognition-secret")
        File(context.noBackupFilesDir, "recognition-credential-v1").writeText("Bearer sensitive-key https://private.invalid")
        val failure = runCatching { settings.readKey() }.exceptionOrNull()
        assertNotNull(failure); assertNull(failure!!.cause)
        assertFalse(failure.toString().contains("sensitive-key")); assertFalse(failure.toString().contains("private.invalid"))
        assertFalse(settings.hasKey())
    }
}
