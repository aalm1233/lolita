package com.lolita.app.data.file

import android.content.ContextWrapper
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class ImageCopyCancellationTest {
    @Test fun cancellationWhileReturningCopiedImageDeletesUnclaimedFile() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = File(context.cacheDir, "copy-cancel-${UUID.randomUUID()}").apply { mkdirs() }
        val source = File(directory, "source.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val wrapped = object : ContextWrapper(context) {
            override fun getFilesDir(): File = directory
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        lateinit var copy: Job
        try {
            // Hold the caller dispatcher until the IO copy completes. Cancelling before the
            // queued return resumes reproduces withContext's prompt-cancellation boundary.
            instrumentation.runOnMainSync {
                copy = scope.launch { ImageFileHelper.copyToInternalStorage(wrapped, Uri.fromFile(source)) }
                val until = System.nanoTime() + 5_000_000_000
                while (File(directory, "images").listFiles()?.none { it.length() == 3L } != false && System.nanoTime() < until) Thread.sleep(10)
                assertTrue("IO did not produce the test copy", File(directory, "images").listFiles()?.any { it.length() == 3L } == true)
                copy.cancel()
            }
            copy.join()
            assertTrue("cancelled copy left an unowned image", File(directory, "images").listFiles().orEmpty().isEmpty())
            assertTrue(source.exists())
        } finally {
            scope.cancel()
            directory.deleteRecursively()
        }
    }
}
