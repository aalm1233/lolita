package com.lolita.app.data.file

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import androidx.room.withTransaction
import com.lolita.app.data.local.LolitaDatabase
import com.google.gson.JsonParser

/**
 * Copies a picked image URI to app-internal storage so the path persists across reboots.
 * Returns the internal file path as a string.
 */
object ImageFileHelper {

    private const val IMAGE_DIR = "images"
    @Volatile private var ownedImagesDir: File? = null
    @Volatile private var imageDatabase: LolitaDatabase? = null

    fun configure(context: Context, database: LolitaDatabase) {
        ownedImagesDir = File(context.filesDir, IMAGE_DIR).canonicalFile
        imageDatabase = database
    }

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
        val root = ownedImagesDir ?: return@withContext
        val db = imageDatabase ?: return@withContext
        val file = File(path).canonicalFile
        if (file.parentFile != root || !file.isFile) return@withContext
        // Imported historical backups may share one file between multiple records.
        db.withTransaction {
            val localReference = db.brandDao().getAllBrandsList().any { it.logoUrl == path } ||
                db.itemDao().getAllItemsList().any { path in it.imageUrls || it.sizeChartImageUrl == path } ||
                db.coordinateDao().getAllCoordinatesList().any { path in it.imageUrls } ||
                db.outfitLogDao().getAllOutfitLogsList().any { path in it.imageUrls } ||
                db.locationDao().getAllLocationsList().any { it.imageUrl == path }
            val sync = db.sharedLibrarySyncDao()
            val remoteReference = sync.getAllBrandsList().any { it.logoUrl == path } ||
                sync.getAllCoordinatesList().any { path in it.imageUrls } ||
                sync.getAllItemsList().any { path in it.imageUrls || it.sizeChartImageUrl == path }
            val archiveReference = db.legacyCatalogArchiveDao().getAll().any { archive ->
                val row = JsonParser.parseString(archive.payload).asJsonObject
                row["image_urls"]?.takeUnless { it.isJsonNull }?.let { value ->
                    val images = if (value.isJsonArray) value.asJsonArray else JsonParser.parseString(value.asString).asJsonArray
                    images.any { it.asString == path }
                } ?: false
            }
            if (!localReference && !remoteReference && !archiveReference) file.delete()
        }
    }

    suspend fun copyExistingImageToInternalStorage(context: Context, sourcePath: String): String =
        withContext(Dispatchers.IO) {
            val sourceFile = File(sourcePath)
            require(sourceFile.exists()) { "Image file does not exist: $sourcePath" }

            val dir = File(context.filesDir, IMAGE_DIR)
            if (!dir.exists()) dir.mkdirs()

            val extension = sourceFile.extension
                .lowercase()
                .filter { it.isLetterOrDigit() }
                .ifBlank { "jpg" }
            val fileName = "${UUID.randomUUID()}.$extension"
            val destFile = File(dir, fileName)

            sourceFile.inputStream().use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            destFile.absolutePath
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
