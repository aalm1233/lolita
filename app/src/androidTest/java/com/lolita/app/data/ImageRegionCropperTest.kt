package com.lolita.app.data

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.file.ImageRegionCropper
import com.lolita.app.data.file.NormalizedCropRect
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class ImageRegionCropperTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var directory: File
    private lateinit var context: ContextWrapper
    @Before fun setup() {
        directory = File(target.cacheDir, "crop-pixels-${UUID.randomUUID()}").apply { mkdirs() }
        context = object : ContextWrapper(target) {
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getCacheDir() = File(directory, "cache").apply { mkdirs() }
        }
    }
    @After fun cleanup() { directory.deleteRecursively() }
    private fun sha(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList()
    private fun source(width: Int = 300, height: Int = 200, jpeg: Boolean = false): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.RED)
        canvas.drawRect(width * 2f / 3f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = Color.BLUE })
        canvas.drawRect(width / 3f, height / 4f, width * 2f / 3f, height * 3f / 4f, Paint().apply { color = Color.GREEN })
        val file = File(directory, "source-${UUID.randomUUID()}.${if (jpeg) "jpg" else "png"}")
        file.outputStream().use { bitmap.compress(if (jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    @Test fun uploadFileContainsOnlySelectedGreenPixelsAndOriginalHashIsUnchanged() = runBlocking {
        val original = source()
        val before = sha(original)
        val result = ImageRegionCropper.crop(context, original.path, NormalizedCropRect(1f / 3f, 0.25f, 2f / 3f, 0.75f))
        assertEquals(100, result.width); assertEquals(100, result.height)
        val output = File(result.path)
        assertEquals(File(context.filesDir, "images").canonicalFile, requireNotNull(output.parentFile).canonicalFile)
        UUID.fromString(output.nameWithoutExtension)
        assertEquals("jpg", output.extension)
        val bitmap = BitmapFactory.decodeFile(output.path)
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            assertTrue("不得夹带框外红蓝像素：$x,$y", Color.green(pixel) > 200 && Color.red(pixel) < 40 && Color.blue(pixel) < 40)
        }
        bitmap.recycle()
        assertEquals(before, sha(original))
    }

    @Test fun reencodingStripsGpsCameraCommentAndOrientationMetadata() = runBlocking {
        val original = source(jpeg = true)
        ExifInterface(original.path).apply {
            setAttribute(ExifInterface.TAG_MAKE, "PRIVATE CAMERA")
            setAttribute(ExifInterface.TAG_USER_COMMENT, "PRIVATE NOTE")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE, "31/1,13/1,0/1")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            saveAttributes()
        }
        val before = sha(original)
        val result = ImageRegionCropper.crop(context, original.path, NormalizedCropRect.FULL)
        val exif = ExifInterface(result.path)
        listOf(ExifInterface.TAG_MAKE, ExifInterface.TAG_USER_COMMENT, ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF).forEach { assertNull(exif.getAttribute(it)) }
        // Platform ExifInterface supplies an UNDEFINED compatibility value even for bare JPEGs.
        assertTrue(exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) in
            setOf(ExifInterface.ORIENTATION_UNDEFINED, ExifInterface.ORIENTATION_NORMAL))
        val bytes = File(result.path).readBytes()
        val marker = byteArrayOf(69, 120, 105, 102, 0, 0)
        assertFalse("实际 JPEG 不得保留 EXIF 数据段", (0..bytes.size - marker.size).any { start ->
            marker.indices.all { offset -> bytes[start + offset] == marker[offset] }
        })
        assertEquals(before, sha(original))
    }

    @Test fun clockwiseRotationUsesTheVisibleRotatedCoordinates() = runBlocking {
        val original = source()
        val before = sha(original)
        val full = ImageRegionCropper.crop(context, original.path, NormalizedCropRect.FULL, 90f)
        assertEquals(200, full.width); assertEquals(300, full.height)
        val bitmap = BitmapFactory.decodeFile(full.path)
        assertTrue(Color.blue(bitmap.getPixel(100, 275)) > 200)
        assertTrue(Color.red(bitmap.getPixel(100, 25)) > 200)
        bitmap.recycle()
        val center = ImageRegionCropper.crop(context, original.path, NormalizedCropRect(0.25f, 1f / 3f, 0.75f, 2f / 3f), 90f)
        assertEquals(100, center.width); assertEquals(100, center.height)
        val selected = BitmapFactory.decodeFile(center.path)
        assertTrue(Color.green(selected.getPixel(50, 50)) > 200)
        selected.recycle()
        assertEquals(before, sha(original))
    }

    @Test fun exifRotationIsAppliedOnceAndLongImagePreviewDoesNotInflateResolution() = runBlocking {
        val rotated = source(jpeg = true)
        ExifInterface(rotated.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes()
        }
        val output = ImageRegionCropper.crop(context, rotated.path, NormalizedCropRect.FULL)
        assertEquals(200, output.width); assertEquals(300, output.height)
        val long = source(256, 16_000)
        val before = sha(long)
        val preview = ImageRegionCropper.preview(long.path)
        assertEquals(256, preview.sourceWidth); assertEquals(16_000, preview.sourceHeight)
        assertTrue(preview.bitmap.width.toLong() * preview.bitmap.height <= 1_600_000)
        preview.bitmap.recycle()
        val region = ImageRegionCropper.crop(context, long.path, NormalizedCropRect(0f, 0.5f, 1f, 0.5625f))
        assertEquals(256, region.width); assertEquals(1_000, region.height)
        assertEquals(before, sha(long))
    }

    @Test fun fineRotationAndExifMirrorChangePixelsWithoutCopyingSourceMetadata() = runBlocking {
        val original = source()
        val before = sha(original)
        val corrected = ImageRegionCropper.crop(context, original.path, NormalizedCropRect.FULL, 10f)
        assertEquals(331, corrected.width); assertEquals(250, corrected.height)
        val fine = BitmapFactory.decodeFile(corrected.path)
        assertTrue(Color.green(fine.getPixel(165, 125)) > 200)
        fine.recycle()
        assertEquals(before, sha(original))
        val mirror = source(jpeg = true)
        ExifInterface(mirror.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_FLIP_HORIZONTAL.toString()); saveAttributes()
        }
        val mirrorBefore = sha(mirror)
        val mirrored = ImageRegionCropper.crop(context, mirror.path, NormalizedCropRect.FULL)
        val flipped = BitmapFactory.decodeFile(mirrored.path)
        assertTrue(Color.blue(flipped.getPixel(30, 100)) > 200)
        assertTrue(Color.red(flipped.getPixel(270, 100)) > 200)
        flipped.recycle()
        assertEquals(mirrorBefore, sha(mirror))
    }
}
