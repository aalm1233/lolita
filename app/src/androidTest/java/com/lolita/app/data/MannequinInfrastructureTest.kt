package com.lolita.app.data

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.tryon.TryOnImages
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MannequinInfrastructureTest {
    @Test fun accessoryPreparationReturnsOnlyTheNormalizedSourceAndPreservesOriginal() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(target.cacheDir, "accessory-images-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val source = File(root, "accessory.png")
            val bitmap = Bitmap.createBitmap(2000, 1000, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(bitmap).drawColor(android.graphics.Color.RED)
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            val original = source.readBytes()
            val images = TryOnImages(target).prepareAccessory(listOf(source.absolutePath))
            assertEquals(1, images.size)
            val result = BitmapFactory.decodeByteArray(images.single(), 0, images.single().size)
            try {
                assertEquals(1536, result.width); assertEquals(768, result.height)
                val center = result.getPixel(result.width / 2, result.height / 2)
                assertTrue(android.graphics.Color.red(center) > 245)
                assertTrue(android.graphics.Color.green(center) < 10)
            } finally { result.recycle() }
            assertArrayEquals(original, source.readBytes())
            for (paths in listOf(emptyList(), listOf(source.absolutePath, source.absolutePath), listOf("/missing.png"))) {
                assertTrue(runCatching { TryOnImages(target).prepareAccessory(paths) }.isFailure)
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun frontTemplateIsFirstAndExactlyOneNormalizedGarmentFollowsWithoutChangingSource()=runBlocking {
        val target=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(target.cacheDir,"mannequin-images-${UUID.randomUUID()}").apply { mkdirs() }
        val context=object:ContextWrapper(target) { override fun getFilesDir()=root }
        try {
            val source=File(root,"transparent-dress.png")
            val bitmap=Bitmap.createBitmap(2000,1000,Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(bitmap).drawRect(800f,200f,1200f,800f,android.graphics.Paint().apply { color=android.graphics.Color.RED })
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
            val original=source.readBytes()
            val images=TryOnImages(context).prepareMannequin(listOf(source.absolutePath))
            assertEquals(2,images.size)
            val template=BitmapFactory.decodeByteArray(images[0],0,images[0].size)
            assertEquals(1024,template.width); assertEquals(1536,template.height); template.recycle()
            val garment=BitmapFactory.decodeByteArray(images[1],0,images[1].size)
            try {
                assertEquals(1536,garment.width); assertEquals(768,garment.height)
                assertTrue(android.graphics.Color.red(garment.getPixel(0,0))>=245)
                assertTrue(android.graphics.Color.green(garment.getPixel(0,0))>=245)
                assertTrue(android.graphics.Color.blue(garment.getPixel(0,0))>=245)
            } finally { garment.recycle() }
            assertArrayEquals(original,source.readBytes())
            assertFalse(images[0].contentEquals(TryOnImages(context).prepare(listOf(source.absolutePath))[0]))
            for (paths in listOf(emptyList(),listOf(source.absolutePath,source.absolutePath),listOf("/missing.png"))) {
                try { TryOnImages(context).prepareMannequin(paths); fail("invalid source count or file accepted") }
                catch (_:IllegalArgumentException) { }
            }
        } finally { root.deleteRecursively() }
    }
}
