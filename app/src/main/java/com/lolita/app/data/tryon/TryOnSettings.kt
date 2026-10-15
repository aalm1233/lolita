package com.lolita.app.data.tryon

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
class TryOnSettings(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "tryon-credential-v1"))

    @Synchronized fun hasKey(): Boolean = runCatching { readKey().isNotBlank() }.getOrDefault(false)

    @Synchronized fun readKey(): String {
        if (!file.baseFile.exists()) return ""
        return try {
            val parts = file.openRead().use { it.readBytes().toString(Charsets.UTF_8) }.split(':')
            check(parts.size == 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128,Base64.getDecoder().decode(parts[0])))
            cipher.doFinal(Base64.getDecoder().decode(parts[1])).toString(Charsets.UTF_8)
        } catch (_: Exception) { throw IllegalStateException("本机密钥无法读取，请重新配置 API Key") }
    }

    @Synchronized fun saveKey(value: String) {
        val value = value.trim()
        require(value.isNotBlank() && value.length <= 512 && value.all { it.code in 33..126 }) { "请填写有效的 API Key" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key())
        val encoded = Base64.getEncoder().encodeToString(cipher.iv) + ":" +
            Base64.getEncoder().encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)))
        val output = file.startWrite()
        try { output.write(encoded.toByteArray(Charsets.UTF_8)); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
    }

    @Synchronized fun clearKey() { file.delete() }

    private fun key(): SecretKey = synchronized(keyLock) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS,null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    companion object { private const val ALIAS = "lolita.tryon.personal-key.v1"; private val keyLock = Any() }
}
