package com.lolita.app.data.tryon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

class TryOnImages(private val context: Context) {
    suspend fun prepare(paths: List<String>): List<ByteArray> {
        require(paths.size in 1..8) { "请选择1至8张服饰参考图" }
        return prepareWithTemplate(paths, MODEL_ASSET)
    }

    suspend fun prepareMannequin(paths: List<String>): List<ByteArray> {
        require(paths.size == 1) { "请选择一张裙子参考图" }
        return prepareWithTemplate(paths, MANNEQUIN_ASSET)
    }

    private suspend fun prepareWithTemplate(paths: List<String>, template: String): List<ByteArray> = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val result = mutableListOf(normalize { context.assets.open(template) })
        for (path in paths) {
            currentCoroutineContext().ensureActive()
            val file = File(path)
            require(file.isFile && file.length() in 1..80L * 1024 * 1024) { "参考图片已丢失或过大，请重新选择图片" }
            result.add(normalize { file.inputStream() })
            require(result.sumOf { it.size.toLong() } <= 40L * 1024 * 1024) { "参考图片总大小超出限制" }
        }
        result
    }

    private fun normalize(open: () -> InputStream): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it,null,bounds) }
        require(bounds.outWidth in 1..30000 && bounds.outHeight in 1..30000) { "无法读取参考图片" }
        val orientation = open().use { runCatching { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION,1) }.getOrDefault(1) }
        var sample = 1
        while (maxOf(bounds.outWidth,bounds.outHeight) / sample > 3072) sample *= 2
        var bitmap = open().use { BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: throw IllegalArgumentException("无法读取参考图片")
        try {
            val matrix = Matrix().apply {
                when (orientation) {
                    2 -> setScale(-1f,1f)
                    3 -> setRotate(180f)
                    4 -> { setRotate(180f); postScale(-1f,1f) }
                    5 -> { setRotate(90f); postScale(-1f,1f) }
                    6 -> setRotate(90f)
                    7 -> { setRotate(-90f); postScale(-1f,1f) }
                    8 -> setRotate(-90f)
                }
            }
            if (!matrix.isIdentity) {
                val upright = Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
                if (upright !== bitmap) { bitmap.recycle(); bitmap = upright }
            }
            val ratio = minOf(1f,1536f / maxOf(bitmap.width,bitmap.height))
            if (ratio < 1f) {
                val scaled = Bitmap.createScaledBitmap(bitmap,maxOf(1,(bitmap.width*ratio).toInt()),maxOf(1,(bitmap.height*ratio).toInt()),true)
                if (scaled !== bitmap) { bitmap.recycle(); bitmap = scaled }
            }
            if (bitmap.hasAlpha()) {
                val opaque = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                android.graphics.Canvas(opaque).apply {
                    drawColor(android.graphics.Color.WHITE)
                    drawBitmap(bitmap, 0f, 0f, null)
                }
                bitmap.recycle()
                bitmap = opaque
            }
            return ByteArrayOutputStream().use { out ->
                require(bitmap.compress(Bitmap.CompressFormat.JPEG,90,out)) { "无法准备参考图片" }
                out.toByteArray().also { require(it.size <= 10*1024*1024) { "参考图片过大" } }
            }
        } finally { bitmap.recycle() }
    }

    companion object {
        const val MODEL_ASSET = "tryon/model.png"
        const val MANNEQUIN_ASSET = "mannequin/front-template.png"
    }
}
