package com.lolita.app.data.file

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
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

    suspend fun saveGeneratedImage(context: Context, bytes: ByteArray): String {
        require(bytes.isNotEmpty() && bytes.size <= 32*1024*1024) { "生成图片无效或过大" }
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        require(bounds.outWidth in 1..4096 && bounds.outHeight in 1..6144 && bounds.outMimeType == "image/png") { "生成服务未返回有效的 PNG 图片" }
        val directory = File(context.filesDir, IMAGE_DIR)
        val destination = File(directory,"${UUID.randomUUID()}.png")
        return try {
            withContext(Dispatchers.IO) {
                currentCoroutineContext().ensureActive()
                check(directory.isDirectory || directory.mkdirs()) { "无法创建图片目录" }
                destination.outputStream().use { it.write(bytes) }
                currentCoroutineContext().ensureActive()
                destination.absolutePath
            }
        } catch (failure: Throwable) { destination.delete(); throw failure }
    }

    private const val IMAGE_DIR = "images"
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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

    /** Copies packaged assets to the same persistent image store used by user photographs. */
    suspend fun copyAssetToInternalStorage(context: Context, assetPath: String): String {
        require(assetPath.isNotBlank() && !assetPath.startsWith("/") &&
            assetPath.split('/').none { it == ".." || it == "." || it.isBlank() }) { "非法内置图片路径" }
        val extension = assetPath.substringAfterLast('.', "png").lowercase(java.util.Locale.ROOT)
            .filter { it.isLetterOrDigit() }.take(8).ifBlank { "png" }
        val directory = File(context.filesDir, IMAGE_DIR)
        val destination = File(directory, "${UUID.randomUUID()}.$extension")
        // Keep ownership outside withContext: prompt cancellation while returning also cleans up.
        return try {
            withContext(Dispatchers.IO) {
                currentCoroutineContext().ensureActive()
                check(directory.isDirectory || directory.mkdirs()) { "无法创建图片目录" }
                context.assets.open(assetPath).use { input ->
                    destination.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                destination.absolutePath
            }
        } catch (failure: Throwable) {
            destination.delete()
            throw failure
        }
    }

    // This scope survives editor disposal so a committed row can protect its images.
    fun cleanupUnreferencedImages(paths: Collection<String>) = cleanupScope.launch {
        paths.distinct().forEach { path ->
            runCatching { deleteImage(path) }
        }
    }

    suspend fun deleteImage(path: String) = withContext(Dispatchers.IO) {
        val root = ownedImagesDir ?: return@withContext
        val db = imageDatabase ?: return@withContext
        val file = File(path).canonicalFile
        if (file.parentFile != root || !file.isFile) return@withContext
        // Imported historical backups may share one file between multiple records.
        db.withTransaction {
            val localReference = db.brandDao().getAllBrandsList().any { it.logoUrl == path } ||
                db.itemDao().getAllItemsList().any { path in it.imageUrls || it.sizeChartImageUrl == path || path in it.sizeChartImageUrls } ||
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

            try {
                sourceFile.inputStream().use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
                destFile.absolutePath
            } catch (failure: Throwable) {
                destFile.delete()
                throw failure
            }
        }

    suspend fun downloadFromUrl(context: Context, imageUrl: String, maxBytes: Long = Long.MAX_VALUE): String =
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

                require(connection.contentLengthLong <= maxBytes) { "图片文件过大" }
                connection.inputStream.use { input ->
                    destFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= maxBytes) { "图片文件过大" }
                            output.write(buffer, 0, count)
                        }
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
