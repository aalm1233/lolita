package com.lolita.app.data.tryon

import com.lolita.app.domain.model.ImageGenerationCredentials
import com.lolita.app.domain.model.ImageGenerationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Personal credentials never enter DataStore, Room, Android backup, logs or the APK. */
data class ImageServiceStatus(val provider: ImageGenerationProvider, val hasKey: Boolean)

class TryOnSettings(context: Context) {
    private val directory = context.noBackupFilesDir
    private val selection = AtomicFile(File(directory, "image-generation-provider-v1"))
    private val revision = MutableStateFlow(0)
    val changes = revision.asStateFlow()
    private fun credentialFile(provider: ImageGenerationProvider) = AtomicFile(File(directory,
        if (provider == ImageGenerationProvider.OPENAI) "tryon-credential-v1" else "seedream-credential-v1"))

    @Synchronized fun readProvider(): ImageGenerationProvider {
        if (!selection.baseFile.exists()) return if (credentialFile(ImageGenerationProvider.OPENAI).baseFile.exists())
            ImageGenerationProvider.OPENAI else ImageGenerationProvider.SEEDREAM
        return try { ImageGenerationProvider.valueOf(selection.openRead().use { it.readBytes().toString(Charsets.UTF_8) }) }
        catch (_: Exception) { error("图像服务配置无法读取，请重新选择服务") }
    }

    @Synchronized fun selectProvider(provider: ImageGenerationProvider) {
        val output = selection.startWrite()
        try { output.write(provider.name.toByteArray(Charsets.UTF_8)); selection.finishWrite(output) }
        catch (failure: Exception) { selection.failWrite(output); throw failure }
        revision.value += 1
    }

    @Synchronized fun readCredentials(): ImageGenerationCredentials {
        val provider = readProvider()
        return ImageGenerationCredentials(provider, readKey(provider))
    }

    @Synchronized fun readStatus(): ImageServiceStatus {
        val provider = readProvider()
        return ImageServiceStatus(provider, hasKey(provider))
    }

    @Synchronized fun hasKey(provider: ImageGenerationProvider = readProvider()): Boolean = runCatching { readKey(provider).isNotBlank() }.getOrDefault(false)

    @Synchronized fun readKey(provider: ImageGenerationProvider = readProvider()): String {
        val file = credentialFile(provider)
        if (!file.baseFile.exists()) return ""
        return try {
            val parts = file.openRead().use { it.readBytes().toString(Charsets.UTF_8) }.split(':')
            check(parts.size == 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128,Base64.getDecoder().decode(parts[0])))
            cipher.doFinal(Base64.getDecoder().decode(parts[1])).toString(Charsets.UTF_8)
        } catch (_: Exception) { throw IllegalStateException("本机密钥无法读取，请重新配置 API Key") }
    }

    @Synchronized fun saveKey(value: String, provider: ImageGenerationProvider = readProvider()) {
        val file = credentialFile(provider)
        val value = value.trim()
        require(value.isNotBlank() && value.length <= 512 && value.all { it.code in 33..126 }) { "请填写有效的 API Key" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key())
        val encoded = Base64.getEncoder().encodeToString(cipher.iv) + ":" +
            Base64.getEncoder().encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)))
        val output = file.startWrite()
        try { output.write(encoded.toByteArray(Charsets.UTF_8)); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
        revision.value += 1
    }

    @Synchronized fun clearKey(provider: ImageGenerationProvider = readProvider()) {
        // Legacy installs infer OpenAI from the old credential. Persist that choice
        // before deletion so clearing a key cannot silently switch its recipient.
        if (!selection.baseFile.exists()) selectProvider(readProvider())
        credentialFile(provider).delete()
        revision.value += 1
    }

    private fun key(): SecretKey = synchronized(keyLock) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS,null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    companion object { private const val ALIAS = "lolita.tryon.personal-key.v1"; private val keyLock = Any() }
}
