package com.lolita.app.data

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.tryon.TryOnImages
import com.lolita.app.data.tryon.TryOnSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TryOnInfrastructureTest {
    @Test fun clearingLegacyGptCredentialKeepsTheSelectedService() {
        val context = fixture()
        try {
            val settings = TryOnSettings(context)
            settings.saveKey("legacy-gpt-only", com.lolita.app.domain.model.ImageGenerationProvider.OPENAI)
            assertEquals(com.lolita.app.domain.model.ImageGenerationProvider.OPENAI, settings.readProvider())
            settings.clearKey()
            assertFalse(settings.hasKey())
            assertEquals(com.lolita.app.domain.model.ImageGenerationProvider.OPENAI, TryOnSettings(context).readProvider())
        } finally { context.filesDir.parentFile!!.deleteRecursively() }
    }
    @Test fun legacyKeySurvivesServiceSwitchAndOtherCredentialDeletion() {
        val context = fixture()
        try {
            val settings = TryOnSettings(context)
            val gpt = com.lolita.app.domain.model.ImageGenerationProvider.OPENAI
            val seed = com.lolita.app.domain.model.ImageGenerationProvider.SEEDREAM
            assertEquals(seed, settings.readProvider())
            settings.saveKey("legacy-private-gpt", gpt)
            assertEquals(gpt, TryOnSettings(context).readProvider())
            settings.selectProvider(seed)
            assertFalse(settings.hasKey())
            settings.saveKey("private-seedream")
            assertEquals(seed, TryOnSettings(context).readCredentials().provider)
            assertEquals("private-seedream", TryOnSettings(context).readCredentials().apiKey)
            settings.clearKey()
            assertFalse(settings.hasKey())
            settings.selectProvider(gpt)
            assertEquals("legacy-private-gpt", settings.readCredentials().apiKey)
            val raw = context.noBackupFilesDir.walkTopDown().filter { it.isFile }.joinToString { it.readText() }
            assertFalse(raw.contains("legacy-private-gpt")); assertFalse(raw.contains("private-seedream"))
        } finally { context.filesDir.parentFile!!.deleteRecursively() }
    }
    private fun fixture(): ContextWrapper = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        val root = File(baseContext.cacheDir,"tryon-${UUID.randomUUID()}").apply { mkdirs() }
        override fun getFilesDir() = File(root,"files").apply { mkdirs() }
        override fun getNoBackupFilesDir() = File(root,"no-backup").apply { mkdirs() }
    }
    @Test fun personalKeyIsEncryptedOnDiskAndCanBeReplacedAndCleared() {
        val context = fixture()
        try {
            val settings = TryOnSettings(context)
            assertFalse(settings.hasKey())
            settings.saveKey("sk-private-test-very-sensitive")
            assertEquals("sk-private-test-very-sensitive",TryOnSettings(context).readKey())
            assertTrue(settings.hasKey())
            val raw = context.noBackupFilesDir.walkTopDown().filter { it.isFile }.joinToString { it.readText() }
            assertFalse(raw.contains("sk-private-test-very-sensitive"))
            settings.saveKey("sk-replacement")
            assertEquals("sk-replacement",settings.readKey())
            settings.clearKey()
            assertFalse(settings.hasKey())
            assertEquals("",settings.readKey())
        } finally { context.filesDir.parentFile!!.deleteRecursively() }
    }
    @Test fun sourceImagesAreNormalizedWithoutChangingOriginalAndInvalidOutputLeavesNoFile() = runBlocking {
        val context = fixture()
        try {
            val source = File(context.filesDir,"source.png")
            Bitmap.createBitmap(2000,1000,Bitmap.Config.ARGB_8888).also { bitmap ->
                source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
            }
            val original = source.readBytes()
            val images = TryOnImages(context).prepare(listOf(source.absolutePath))
            assertEquals(2,images.size)
            val resized = BitmapFactory.decodeByteArray(images[1],0,images[1].size)
            assertEquals(1536,resized.width); assertEquals(768,resized.height); resized.recycle()
            assertArrayEquals(original,source.readBytes())
            val before = File(context.filesDir,"images").listFiles().orEmpty().size
            try { ImageFileHelper.saveGeneratedImage(context,byteArrayOf(1,2,3)); fail("invalid image accepted") }
            catch (_: IllegalArgumentException) { }
            assertEquals(before,File(context.filesDir,"images").listFiles().orEmpty().size)
            val result = ImageFileHelper.saveGeneratedImage(context,original)
            assertTrue(File(result).isFile)
            assertArrayEquals(original,File(result).readBytes())
            try { TryOnImages(context).prepare(listOf("/missing.jpg")); fail("missing source accepted") }
            catch (_: IllegalArgumentException) { }
        } finally { context.filesDir.parentFile!!.deleteRecursively() }
    }

    @Test fun pngHeaderWithoutCompletePixelsIsRejectedBeforeSaving() = runBlocking {
        val context = fixture()
        try {
            val complete = java.io.ByteArrayOutputStream().use { output ->
                val bitmap = Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                bitmap.recycle()
                output.toByteArray()
            }
            val idat = (8 until complete.size - 8).first { offset ->
                complete.copyOfRange(offset, offset + 4).contentEquals("IDAT".toByteArray())
            }
            val truncated = complete.copyOf(idat + 8)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(truncated, 0, truncated.size, bounds)
            assertEquals("必须覆盖有合法尺寸但像素流截断的情况", 32, bounds.outWidth)
            try {
                ImageFileHelper.saveGeneratedImage(context, truncated)
                fail("不能将只有头部的PNG作为成功的生成结果")
            } catch (_: IllegalArgumentException) { }
            assertTrue(File(context.filesDir, "images").listFiles().orEmpty().isEmpty())
        } finally { context.filesDir.parentFile!!.deleteRecursively() }
    }

    @Test fun transparentProductPhotosUseNeutralWhiteInsteadOfBlackWhenNormalized() = runBlocking {
        val context = fixture()
        try {
            val source = File(context.filesDir, "transparent-product.png")
            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(bitmap).drawRect(20f, 20f, 44f, 44f,
                android.graphics.Paint().apply { color = android.graphics.Color.RED })
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            val normalized = TryOnImages(context).prepare(listOf(source.absolutePath))[1]
            val photo = BitmapFactory.decodeByteArray(normalized, 0, normalized.size)
            try {
                val corner = photo.getPixel(0, 0)
                assertTrue("透明背景转JPEG不能变黑", android.graphics.Color.red(corner) >= 245 &&
                    android.graphics.Color.green(corner) >= 245 && android.graphics.Color.blue(corner) >= 245)
                val garment = photo.getPixel(32, 32)
                assertTrue(android.graphics.Color.red(garment) >= 235 && android.graphics.Color.green(garment) <= 20)
            } finally { photo.recycle() }
        } finally { context.filesDir.parentFile!!.deleteRecursively() }
    }
}
