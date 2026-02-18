package com.lolita.app.data.file

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
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

    suspend fun deleteImage(path: String) = withContext(Dispatchers.IO) {
        val file = File(path)
        // Only delete files within our images directory to prevent path traversal
        if (file.exists() && file.canonicalPath.contains("${File.separator}$IMAGE_DIR${File.separator}")) {
            file.delete()
        }
    }

    suspend fun downloadFromUrl(context: Context, imageUrl: String): String =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, IMAGE_DIR)
            if (!dir.exists()) dir.mkdirs()

            val extension = imageUrl.substringAfterLast(".", "jpg")
                .substringBefore("?")
                .filter { it.isLetterOrDigit() }
                .take(4).ifBlank { "jpg" }
            val fileName = "${UUID.randomUUID()}.$extension"
            val destFile = File(dir, fileName)

            val connection = URL(imageUrl).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw Exception("HTTP $responseCode")
                }

                connection.inputStream.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                // Clean up incomplete file on failure
                if (destFile.exists()) destFile.delete()
                throw e
            } finally {
                connection.disconnect()
            }
            destFile.absolutePath
        }
}
