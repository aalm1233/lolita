package com.lolita.app.data.recognition

import android.content.Context
import android.content.ContextWrapper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lolita.app.data.tryon.TryOnSettings
import com.lolita.app.domain.model.ImageGenerationProvider
import com.lolita.app.domain.model.RecognitionProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

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

    @Test fun arkRecognitionNeverInfersOrChangesSeedreamOrOpenAiCredentials() = isolated { context ->
        val generation = TryOnSettings(context)
        generation.saveKey("openai-generation-only-secret", ImageGenerationProvider.OPENAI)
        generation.saveKey("seedream-generation-only-secret", ImageGenerationProvider.SEEDREAM)
        val legacy = File(context.noBackupFilesDir, "recognition-credential-v1").apply { writeText(legacyRecognitionCiphertext()) }
        val originalLegacy = legacy.readBytes()
        val recognition = RecognitionSettings(context)
        assertEquals("", recognition.readKey()); assertFalse(recognition.hasKey())
        recognition.saveKey("recognition-only-secret")
        assertEquals("recognition-only-secret", RecognitionSettings(context).readKey())
        assertEquals("openai-generation-only-secret", generation.readKey(ImageGenerationProvider.OPENAI))
        assertEquals("seedream-generation-only-secret", generation.readKey(ImageGenerationProvider.SEEDREAM))
        val bytes = File(context.noBackupFilesDir, "recognition-ark-credential-v1").readText()
        assertFalse(bytes.contains("recognition-only-secret")); assertFalse(bytes.contains("generation-only-secret"))
        assertArrayEquals(originalLegacy, legacy.readBytes())
        assertEquals(0, context.filesDir.listFiles().orEmpty().size)
        recognition.clearKey()
        assertEquals("", recognition.readKey()); assertFalse(recognition.hasKey())
        assertEquals("openai-generation-only-secret", generation.readKey(ImageGenerationProvider.OPENAI))
        assertEquals("seedream-generation-only-secret", generation.readKey(ImageGenerationProvider.SEEDREAM))
        assertArrayEquals(originalLegacy, legacy.readBytes())
    }

    @Test fun credentialsUseFreshIvAndChangesOnlyAfterSuccessfulMutations() = isolated { context ->
        val settings = RecognitionSettings(context)
        val initial = settings.changes.value
        for (invalid in listOf("", " ", "sk key", "secret\nheader", "x".repeat(513))) {
            assertThrows(IllegalArgumentException::class.java) { settings.saveKey(invalid) }
        }
        assertEquals(initial, settings.changes.value)
        settings.saveKey("dummy-recognition-secret")
        val file = File(context.noBackupFilesDir, "recognition-ark-credential-v1")
        val first = file.readText()
        settings.saveKey("dummy-recognition-secret")
        assertNotEquals(first, file.readText())
        assertEquals(initial + 2, settings.changes.value)
        settings.clearKey()
        assertEquals(initial + 3, settings.changes.value)
    }

    @Test fun aSeedreamCiphertextCannotBeDecryptedWithTheArkRecognitionPurpose() = isolated { context ->
        val generation = TryOnSettings(context)
        generation.saveKey("generation-only-secret", ImageGenerationProvider.SEEDREAM)
        val recognition = RecognitionSettings(context)
        recognition.saveKey("recognition-only-secret")
        File(context.noBackupFilesDir, "recognition-ark-credential-v1").writeBytes(File(context.noBackupFilesDir, "seedream-credential-v1").readBytes())
        assertNotNull(runCatching { recognition.readKey() }.exceptionOrNull())
        assertFalse(recognition.hasKey())
        assertEquals("generation-only-secret", generation.readKey(ImageGenerationProvider.SEEDREAM))
    }

    @Test fun malformedCiphertextNeverExposesRawDataOrCauses() = isolated { context ->
        val settings = RecognitionSettings(context)
        settings.saveKey("dummy-recognition-secret")
        File(context.noBackupFilesDir, "recognition-ark-credential-v1").writeText("Bearer sensitive-key https://private.invalid")
        val failure = runCatching { settings.readKey() }.exceptionOrNull()
        assertNotNull(failure); assertNull(failure!!.cause)
        assertFalse(failure.toString().contains("sensitive-key")); assertFalse(failure.toString().contains("private.invalid"))
        assertFalse(settings.hasKey())
    }

    @Test fun legacyOpenAiRecognitionCiphertextCannotBeDecryptedWithTheNewArkPurpose() = isolated { context ->
        val legacy = legacyRecognitionCiphertext()
        File(context.noBackupFilesDir, "recognition-credential-v1").writeText(legacy)
        val settings = RecognitionSettings(context)
        settings.saveKey("ark-recognition-secret")
        File(context.noBackupFilesDir, "recognition-ark-credential-v1").writeText(legacy)
        val failure = runCatching { settings.readKey() }.exceptionOrNull()
        assertNotNull("The Ark purpose must not decrypt the previous recognition purpose", failure)
        assertNull(failure!!.cause)
        assertFalse(failure.toString().contains("legacy-openai-secret"))
        assertFalse(settings.hasKey())
    }

    @Test fun userCanExplicitlyEnterTheSameArkKeyWithoutSharingCredentialFiles() = isolated { context ->
        val generation = TryOnSettings(context)
        generation.saveKey("same-ark-project-key", ImageGenerationProvider.SEEDREAM)
        val recognition = RecognitionSettings(context)
        assertFalse(recognition.hasKey())
        recognition.saveKey("same-ark-project-key")
        assertEquals("same-ark-project-key", recognition.readKey())
        assertNotEquals(File(context.noBackupFilesDir, "seedream-credential-v1").readText(),
            File(context.noBackupFilesDir, "recognition-ark-credential-v1").readText())
        recognition.clearKey()
        assertEquals("same-ark-project-key", generation.readKey(ImageGenerationProvider.SEEDREAM))
    }

    @Test fun providerSelectionPersistsAndEachRecognitionProviderOwnsItsKeyAndRevision() = isolated { context ->
        val ark = RecognitionProvider.VOLCENGINE_ARK
        val deepSeek = RecognitionProvider.valueOf("DEEPSEEK")
        val settings = RecognitionSettings(context)
        assertEquals(ark, settings.readProvider())
        settings.saveKey("ark-only-key", ark)
        val revision = settings.changes.value
        settings.selectProvider(deepSeek)
        assertEquals(revision + 1, settings.changes.value)
        assertEquals(deepSeek, settings.readProvider()); assertFalse(settings.hasKey())
        assertEquals("ark-only-key", settings.readKey(ark))
        settings.saveKey("deepseek-only-key")
        assertEquals("deepseek-only-key", RecognitionSettings(context).readKey())
        assertEquals(deepSeek, RecognitionSettings(context).readProvider())
        assertEquals("ark-only-key", settings.readKey(ark))
        settings.selectProvider(ark)
        settings.clearKey(deepSeek)
        assertEquals("ark-only-key", settings.readKey())
        assertEquals("", settings.readKey(deepSeek))
        assertTrue(File(context.noBackupFilesDir, "recognition-ark-credential-v1").isFile)
        assertFalse(File(context.noBackupFilesDir, "recognition-deepseek-credential-v1").exists())
        assertEquals(0, context.filesDir.listFiles().orEmpty().size)
    }

    @Test fun arkCiphertextCannotBeDecryptedByTheDeepSeekRecognitionPurpose() = isolated { context ->
        val deepSeek = RecognitionProvider.valueOf("DEEPSEEK")
        val settings = RecognitionSettings(context)
        settings.saveKey("ark-secret", RecognitionProvider.VOLCENGINE_ARK)
        settings.saveKey("deepseek-secret", deepSeek)
        File(context.noBackupFilesDir, "recognition-deepseek-credential-v1").writeBytes(File(context.noBackupFilesDir, "recognition-ark-credential-v1").readBytes())
        val failure = runCatching { settings.readKey(deepSeek) }.exceptionOrNull()
        assertNotNull(failure); assertNull(failure!!.cause)
        assertFalse(failure.toString().contains("ark-secret")); assertFalse(settings.hasKey(deepSeek))
        assertEquals("ark-secret", settings.readKey(RecognitionProvider.VOLCENGINE_ARK))
    }

    @Test fun corruptProviderSelectionRequiresExplicitSelectionInsteadOfFallingBack() = isolated { context ->
        val settings = RecognitionSettings(context)
        File(context.noBackupFilesDir, "recognition-provider-v1").writeText("OPENAI sensitive-private-key")
        val failure = runCatching { settings.readProvider() }.exceptionOrNull()
        assertNotNull(failure); assertNull(failure!!.cause)
        assertFalse(failure.toString().contains("sensitive-private-key"))
        settings.selectProvider(RecognitionProvider.VOLCENGINE_ARK)
        assertEquals(RecognitionProvider.VOLCENGINE_ARK, settings.readProvider())
    }

    private fun legacyRecognitionCiphertext(): String {
        val alias = "lolita.recognition.personal-key.v1"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = store.getKey(alias, null) as? SecretKey ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        return Base64.getEncoder().encodeToString(cipher.iv) + ":" + Base64.getEncoder().encodeToString(cipher.doFinal("legacy-openai-secret".toByteArray()))
    }
}
