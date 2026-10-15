package com.lolita.app.data

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.file.ImageFileHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/** Run after MannequinPersistenceTest to catch leaked process-global fixture configuration. */
@RunWith(AndroidJUnit4::class)
class MannequinApplicationCleanupTest {
    @Test fun applicationContextImageCanBeDiscardedAfterPersistenceFixturesClose()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val bytes=ByteArrayOutputStream().use { out ->
            val bitmap=Bitmap.createBitmap(32,48,Bitmap.Config.ARGB_8888)
            bitmap.compress(Bitmap.CompressFormat.PNG,100,out); bitmap.recycle(); out.toByteArray()
        }
        val path=ImageFileHelper.saveGeneratedImage(context,bytes)
        try {
            ImageFileHelper.deleteImage(path)
            assertFalse("fixtures must restore the app image root and open database",File(path).exists())
        } finally {
            // Only the new test file; leave all application rows and existing images untouched.
            File(path).delete()
        }
    }
}
