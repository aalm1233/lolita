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

    /** Personal references must be complete readable files with exact internal UUID ownership. */
    suspend fun isOwnedReadableImage(context: Context, path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(path).canonicalFile
            val root = File(context.filesDir, IMAGE_DIR).canonicalFile
            if (path != File(File(context.filesDir, IMAGE_DIR), file.name).absolutePath || file.parentFile != root ||
                !file.isFile || !file.canRead() || file.length() !in 1..80L * 1024 * 1024) return@withContext false
            if (UUID.fromString(file.nameWithoutExtension).toString() != file.nameWithoutExtension) return@withContext false
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth !in 1..16384 || bounds.outHeight !in 1..16384) return@withContext false
            // ImageDecoder rejects truncated data by default, unlike BitmapFactory's tolerant JPEG decoder.
            val decoded = if (android.os.Build.VERSION.SDK_INT >= 28) {
                android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(file)) { decoder, info, _ ->
                    decoder.setTargetSampleSize(maxOf(1, maxOf(info.size.width, info.size.height) / 1024))
                    decoder.setOnPartialImageListener { false }
                }
            } else {
                // API 26/27: verify format terminators before decoding the complete bounded bitmap.
                val bytes = file.readBytes()
                if (!hasCompleteImageTerminator(bytes, bounds.outMimeType)) return@withContext false
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                    android.graphics.BitmapFactory.Options().apply {
                        var sample = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
                        inSampleSize = sample
                    }) ?: return@withContext false
            }
            decoded.recycle()
            true
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            false
        }
    }

    private fun hasCompleteImageTerminator(bytes: ByteArray, mimeType: String?): Boolean = when (mimeType) {
        "image/jpeg" -> bytes.size >= 2 && bytes[bytes.size - 2] == 0xff.toByte() && bytes.last() == 0xd9.toByte()
        "image/png" -> bytes.takeLast(12).toByteArray().contentEquals(byteArrayOf(0,0,0,0,73,69,78,68,-82,66,96,-126))
        "image/webp" -> bytes.size >= 12 && java.nio.ByteBuffer.wrap(bytes,4,4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int.toLong() + 8 == bytes.size.toLong()
        "image/gif" -> bytes.lastOrNull() == 0x3b.toByte()
        else -> false
    }

    /** Restore only a complete generated PNG inside this application's image store. No mutation. */
    suspend fun isOwnedGeneratedImage(context: Context, path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(path).canonicalFile
            val root = File(context.filesDir, IMAGE_DIR).canonicalFile
            if (path != File(File(context.filesDir, IMAGE_DIR), file.name).absolutePath ||
                file.parentFile != root || !file.isFile || !file.canRead() || file.length() !in 1..32L*1024*1024) return@withContext false
            validateGeneratedPng(file.readBytes())
            true
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            false
        }
    }

    suspend fun saveGeneratedImage(context: Context, bytes: ByteArray): String {
        val directory = File(context.filesDir, IMAGE_DIR)
        val destination = File(directory,"${UUID.randomUUID()}.png")
        return try {
            withContext(Dispatchers.IO) {
                try { validateGeneratedPng(bytes) } catch (_: IllegalArgumentException) {
                    throw com.lolita.app.domain.model.ImageGenerationFailure(com.lolita.app.domain.model.ImageGenerationFailureKind.INVALID_IMAGE)
                }
                currentCoroutineContext().ensureActive()
                check(directory.isDirectory || directory.mkdirs()) { "无法创建图片目录" }
                destination.outputStream().use { it.write(bytes) }
                currentCoroutineContext().ensureActive()
                destination.absolutePath
            }
        } catch (failure: Throwable) {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { destination.delete() }
            throw failure
        }
    }

    private fun validateGeneratedPng(bytes: ByteArray) {
        require(bytes.isNotEmpty() && bytes.size <= 32*1024*1024) { "生成图片无效或过大" }
        val pngEnd = byteArrayOf(0, 0, 0, 0, 73, 69, 78, 68, -82, 66, 96, -126)
        require(bytes.size >= pngEnd.size && bytes.copyOfRange(bytes.size - pngEnd.size, bytes.size).contentEquals(pngEnd)) {
            "生成服务未返回完整的 PNG 图片"
        }
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        require(bounds.outWidth in 1..4096 && bounds.outHeight in 1..6144 && bounds.outMimeType == "image/png") { "生成服务未返回有效的 PNG 图片" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
        val decoded = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
        requireNotNull(decoded) { "生成服务未返回可读取的 PNG 图片" }
        decoded.recycle()
    }

    private const val IMAGE_DIR = "images"
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var ownedImagesDir: File? = null
    @Volatile private var imageDatabase: LolitaDatabase? = null
    private val referencePinLock = Any()
    private val referencePins = mutableMapOf<String, Int>()
    private val deferredDeletes = mutableMapOf<String, String>()

    /** Call while holding the same Room transaction that captures the references. */
    fun pinImageReferences(context: Context, paths: Collection<String>): ImageReferenceLease {
        val directory = File(context.filesDir, IMAGE_DIR).canonicalFile
        val owned = paths.filterNot { it.startsWith("https://") || it.startsWith("http://") }
            .map { File(it).canonicalFile }.filter { it.parentFile == directory }.map { it.path }.toSet()
        synchronized(referencePinLock) {
            owned.forEach { referencePins[it] = (referencePins[it] ?: 0) + 1 }
        }
        return ImageReferenceLease(owned)
    }

    class ImageReferenceLease internal constructor(private val paths: Set<String>) {
        private val released = java.util.concurrent.atomic.AtomicBoolean(false)
        suspend fun release() {
            if (!released.compareAndSet(false, true)) return
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                val retry = synchronized(referencePinLock) {
                    buildList {
                        paths.forEach { path ->
                            val remaining = (referencePins[path] ?: 1) - 1
                            if (remaining > 0) referencePins[path] = remaining
                            else {
                                referencePins.remove(path)
                                deferredDeletes.remove(path)?.let(::add)
                            }
                        }
                    }
                }
                retry.forEach { deleteImage(it) }
            }
        }
    }

    fun configure(context: Context, database: LolitaDatabase) {
        ownedImagesDir = File(context.filesDir, IMAGE_DIR).canonicalFile
        imageDatabase = database
    }

    suspend fun copyToInternalStorage(context: Context, sourceUri: Uri): String {
        // Own the destination outside withContext so cancellation during its return
        // cannot strand a completed copy before the editor receives the path.
        var destination: File? = null
        return try {
            withContext(Dispatchers.IO) {
                currentCoroutineContext().ensureActive()
                val dir = File(context.filesDir, IMAGE_DIR)
                check(dir.isDirectory || dir.mkdirs()) { "无法创建图片目录" }
                val extension = context.contentResolver.getType(sourceUri)
                    ?.substringAfter("/")
                    ?.let { if (it == "jpeg") "jpg" else it }
                    ?: "jpg"
                val destFile = File(dir, "${UUID.randomUUID()}.$extension")
                destination = destFile
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    destFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                    }
                } ?: throw IllegalStateException("Cannot open input stream for $sourceUri")
                currentCoroutineContext().ensureActive()
                destFile.absolutePath
            }
        } catch (failure: Throwable) {
            destination?.delete()
            throw failure
        }
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
            val localReference = db.personalModelDao().getAll().any { path in it.protectedImagePaths() } ||
                db.personalModelDao().getDrafts().any { path in it.sourceImagePaths } ||
                db.imageTaskDao().getAll().any { path in it.protectedImagePaths() } ||
                db.tryOnDao().getAll().any { it.imageUrl == path } ||
                db.brandDao().getAllBrandsList().any { it.logoUrl == path } ||
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
            if (!localReference && !remoteReference && !archiveReference) {
                val canDelete = synchronized(referencePinLock) {
                    if ((referencePins[file.path] ?: 0) > 0) {
                        deferredDeletes[file.path] = path
                        false
                    } else true
                }
                if (canDelete) file.delete()
            }
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
