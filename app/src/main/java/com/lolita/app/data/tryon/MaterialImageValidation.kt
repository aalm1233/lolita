package com.lolita.app.data.tryon

import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Alpha is a file capability check, not proof of one correct item or faithful geometry. */
object MaterialImageValidation {
    suspend fun hasTransparentForeground(path: String): Boolean = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outMimeType != "image/png" || bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext false
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
        val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@withContext false
        try {
            if (!bitmap.hasAlpha()) return@withContext false
            var transparent = false
            var foreground = false
            val line = IntArray(bitmap.width)
            for (y in 0 until bitmap.height) {
                bitmap.getPixels(line, 0, bitmap.width, 0, y, bitmap.width, 1)
                for (pixel in line) {
                    val alpha = pixel ushr 24
                    if (alpha < 8) transparent = true
                    if (alpha > 200) foreground = true
                }
                if (transparent && foreground) return@withContext true
            }
            false
        } finally { bitmap.recycle() }
    }
}
