package com.lolita.app.data.recognition

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.lolita.app.domain.model.RecognitionProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Each recognition provider owns its purpose/file; no legacy OpenAI or image-generation fallback. */
class RecognitionSettings(context: Context) {
    private val directory = context.noBackupFilesDir
    private val selection = AtomicFile(File(directory, "recognition-provider-v1"))
    private val revision = MutableStateFlow(0)
    val changes = revision.asStateFlow()

    private fun purpose(provider: RecognitionProvider): String = when (provider) {
        RecognitionProvider.VOLCENGINE_ARK -> "ark"
        RecognitionProvider.DEEPSEEK -> "deepseek"
    }
    private fun credentialFile(provider: RecognitionProvider) = AtomicFile(File(directory, "recognition-${purpose(provider)}-credential-v1"))

    @Synchronized fun readProvider(): RecognitionProvider {
        if (!selection.baseFile.exists()) return RecognitionProvider.VOLCENGINE_ARK
        return try {
            require(selection.baseFile.length() in 1..64)
            RecognitionProvider.valueOf(selection.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
        } catch (_: Exception) { throw IllegalStateException("识图服务选择无法读取，请明确重新选择服务") }
    }

    @Synchronized fun selectProvider(provider: RecognitionProvider) {
        try {
            val output = selection.startWrite()
            try { output.write(provider.name.toByteArray(Charsets.UTF_8)); selection.finishWrite(output) }
            catch (failure: Exception) { runCatching { selection.failWrite(output) }; throw failure }
        } catch (_: Exception) { throw IllegalStateException("识图服务选择保存失败，请重新选择") }
        revision.value += 1
    }

    @Synchronized fun readKey(provider: RecognitionProvider = readProvider()): String {
        val file = credentialFile(provider)
        if (!file.baseFile.exists()) return ""
        return try {
            require(file.baseFile.length() in 1..16384)
            val parts = file.openRead().use { it.readBytes().toString(Charsets.UTF_8) }.split(':')
            require(parts.size == 2)
            val iv = Base64.getDecoder().decode(parts[0]); require(iv.size == 12)
            val ciphertext = Base64.getDecoder().decode(parts[1]); require(ciphertext.size in 17..528)
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key(provider), GCMParameterSpec(128, iv))
                doFinal(ciphertext).toString(Charsets.UTF_8).also { validate(it) }
            }
        } catch (_: Exception) { throw IllegalStateException("本机识图密钥无法读取，请重新配置当前服务独立的 API Key") }
    }

    @Synchronized fun hasKey(provider: RecognitionProvider = readProvider()): Boolean = runCatching { readKey(provider).isNotBlank() }.getOrDefault(false)

    @Synchronized fun saveKey(value: String, provider: RecognitionProvider = readProvider()) {
        val value = value.trim()
        validate(value)
        val file = credentialFile(provider)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(provider)) }
            val encrypted = Base64.getEncoder().encodeToString(cipher.iv) + ":" +
                Base64.getEncoder().encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)))
            val output = file.startWrite()
            try { output.write(encrypted.toByteArray(Charsets.UTF_8)); file.finishWrite(output) }
            catch (failure: Exception) { runCatching { file.failWrite(output) }; throw failure }
        } catch (_: Exception) { throw IllegalStateException("识图密钥保存失败，请重试") }
        revision.value += 1
    }

    @Synchronized fun clearKey(provider: RecognitionProvider = readProvider()) {
        val file = credentialFile(provider)
        try { file.delete(); check(!file.baseFile.exists()) }
        catch (_: Exception) { throw IllegalStateException("识图密钥清除失败，请重试") }
        revision.value += 1
    }

    private fun validate(value: String) = require(value.isNotBlank() && value.length <= 512 && value.all { it.code in 33..126 }) { "请填写有效的识图 API Key" }
    private fun key(provider: RecognitionProvider): SecretKey = synchronized(keyLock) {
        val alias = "lolita.recognition.${purpose(provider)}.personal-key.v1"
        KeyStore.getInstance("AndroidKeyStore").run {
            load(null)
            (getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
                generateKey()
            }
        }
    }
    companion object { private val keyLock = Any() }
}
