package com.lolita.app.data.file

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Copies a picked image URI to app-internal storage so the path persists across reboots.
 * Returns the internal file path as a string.
 */
object ImageFileHelper {

    private const val IMAGE_DIR = "images"

    suspend fun copyToInternalStorage(context: Context, sourceUri: Uri): String =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, IMAGE_DIR)
            if (!dir.exists()) dir.mkdirs()

            val extension = context.contentResolver.getType(sourceUri)
                ?.substringAfter("/")
                ?.let { if (it == "jpeg") "jpg" else it }
                ?: "jpg"

            val fileName = "${UUID.randomUUID()}.$extension"
            val destFile = File(dir, fileName)

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Cannot open input stream for $sourceUri")

            destFile.absolutePath
        }

    fun deleteImage(path: String) {
        val file = File(path)
        if (file.exists()) file.delete()
    }
}
