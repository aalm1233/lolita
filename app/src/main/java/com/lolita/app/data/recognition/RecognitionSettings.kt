package com.lolita.app.data.recognition

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Recognition has its own key purpose and file. It never reads or falls back to image-generation credentials. */
class RecognitionSettings(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "recognition-credential-v1"))
    private val revision = MutableStateFlow(0)
    val changes = revision.asStateFlow()

    @Synchronized fun readKey(): String {
        if (!file.baseFile.exists()) return ""
        return try {
            require(file.baseFile.length() in 1..16384)
            val parts = file.openRead().use { it.readBytes().toString(Charsets.UTF_8) }.split(':')
            require(parts.size == 2)
            val iv = Base64.getDecoder().decode(parts[0]); require(iv.size == 12)
            val ciphertext = Base64.getDecoder().decode(parts[1]); require(ciphertext.size in 17..528)
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
                doFinal(ciphertext).toString(Charsets.UTF_8).also { validate(it) }
            }
        } catch (_: Exception) { throw IllegalStateException("本机识图密钥无法读取，请重新配置识图 API Key") }
    }

    @Synchronized fun hasKey(): Boolean = runCatching { readKey().isNotBlank() }.getOrDefault(false)

    @Synchronized fun saveKey(value: String) {
        val value = value.trim()
        validate(value)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            val encrypted = Base64.getEncoder().encodeToString(cipher.iv) + ":" +
                Base64.getEncoder().encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)))
            val output = file.startWrite()
            try { output.write(encrypted.toByteArray(Charsets.UTF_8)); file.finishWrite(output) }
            catch (failure: Exception) { runCatching { file.failWrite(output) }; throw failure }
        } catch (_: Exception) { throw IllegalStateException("识图密钥保存失败，请重试") }
        revision.value += 1
    }

    @Synchronized fun clearKey() {
        try { file.delete(); check(!file.baseFile.exists()) }
        catch (_: Exception) { throw IllegalStateException("识图密钥清除失败，请重试") }
        revision.value += 1
    }

    private fun validate(value: String) = require(value.isNotBlank() && value.length <= 512 && value.all { it.code in 33..126 }) { "请填写有效的识图 API Key" }
    private fun key(): SecretKey = synchronized(keyLock) {
        KeyStore.getInstance("AndroidKeyStore").run {
            load(null)
            (getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
                generateKey()
            }
        }
    }
    companion object { private const val ALIAS = "lolita.recognition.personal-key.v1"; private val keyLock = Any() }
}
