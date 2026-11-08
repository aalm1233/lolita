package com.lolita.app.data.diagnostics

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.file.ImageFileHelper
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Temporary audit probe; no application source or user data is changed. */
class NativePathAuditProbeTest {
    @Test fun capturePreviousExit() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir,"native-path-previous-exit.txt").writeText(LocalDiagnostics.report(context))
    }

    @Test fun explicitRenderScriptConcurrentReleaseProbe() = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("raceProbe") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.cacheDir,"rs-race-probe.txt")
        val guarded=InstrumentationRegistry.getArguments().getString("guardedRelease") == "true"
        output.writeText("buffer-initialized isolated library race probe guarded=$guarded\n")
        val rs = android.renderscript.RenderScript.create(context)
        val type = Class.forName("dev.chrisbanes.haze.RenderScriptContext")
        val ctor = type.declaredConstructors.single { it.parameterTypes.size == 2 }.apply { isAccessible=true }
        val apply = type.getMethod("applyBlur",Float::class.javaPrimitiveType)
        val release = type.getMethod("release")
        val surface = type.getMethod("getInputSurface")
        val await = type.getMethod("awaitSurfaceWritten",kotlin.coroutines.Continuation::class.java)
        suspend fun prepared(): Any {
            val instance=ctor.newInstance(rs,(512L shl 32) or 512L)
            val target=surface.invoke(instance) as android.view.Surface
            val canvas=target.lockHardwareCanvas()
            canvas.drawColor(Color.BLUE)
            target.unlockCanvasAndPost(canvas)
            kotlinx.coroutines.withTimeout(5000) {
                kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn<Unit> { continuation ->
                    await.invoke(instance,continuation)
                }
            }
            return instance
        }
        val failures = java.util.concurrent.ConcurrentLinkedQueue<String>()
        try {
            val baseline=prepared()
            output.appendText("serial-complete-input start\n")
            repeat(5) { apply.invoke(baseline,16f) }
            val bitmap=type.getMethod("getOutputBitmap").invoke(baseline) as Bitmap
            assertEquals(Color.BLUE,bitmap.getPixel(256,256))
            release.invoke(baseline)
            output.appendText("serial-complete-input passed with expected blue pixel\n")
            repeat(60) { index ->
                val instance=prepared()
                if (guarded) {
                    val allocation=type.getDeclaredField("inputAlloc").apply {isAccessible=true}.get(instance) as android.renderscript.Allocation
                    val callback=type.getDeclaredMethod("_init_\$lambda\$0",type,android.renderscript.Allocation::class.java).apply {isAccessible=true}
                    allocation.setOnBufferAvailableListener { current -> synchronized(instance) { callback.invoke(null,instance,current) } }
                }
                val start=java.util.concurrent.CountDownLatch(1)
                val worker=Thread {
                    start.await()
                    try { repeat(5) { apply.invoke(instance,16f) } }
                    catch(e:java.lang.reflect.InvocationTargetException) { failures.add(e.targetException.javaClass.name) }
                }
                output.appendText("iteration=$index start\n")
                worker.start();start.countDown()
                val queuedSurface=surface.invoke(instance) as android.view.Surface
                val queuedCanvas=queuedSurface.lockHardwareCanvas()
                queuedCanvas.drawColor(Color.RED)
                queuedSurface.unlockCanvasAndPost(queuedCanvas)
                java.util.concurrent.locks.LockSupport.parkNanos((index % 5) * 100_000L)
                if(guarded) synchronized(instance) { release.invoke(instance) } else release.invoke(instance)
                worker.join(5000)
                check(!worker.isAlive)
                output.appendText("iteration=$index done\n")
            }
        } finally { rs.destroy() }
        output.appendText("failures="+failures.groupingBy { it }.eachCount().toString()+"\n")
    }

    @Test fun imageAllocatorAndNativeLibraryAudit() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.filesDir, "images").apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.png")
        val original = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        original.eraseColor(Color.BLUE)
        file.outputStream().use { original.compress(Bitmap.CompressFormat.PNG, 100, it) }
        original.recycle()
        try {
            val configs = JSONArray()
            for (software in listOf(false, true)) {
                var selected = -1
                val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                    decoder.setTargetSampleSize(maxOf(1, maxOf(info.size.width, info.size.height) / 1024))
                    decoder.setOnPartialImageListener { false }
                    if (software) decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    selected = decoder.allocator
                }
                configs.put(JSONObject().put("explicitSoftware",software).put("allocator",selected)
                    .put("config",bitmap.config.toString()).put("width",bitmap.width).put("height",bitmap.height))
                if (software) assertNotEquals(Bitmap.Config.HARDWARE, bitmap.config)
                bitmap.recycle()
            }
            assertTrue(ImageFileHelper.isOwnedReadableImage(context,file.absolutePath))
            assertTrue(ImageFileHelper.isOwnedReadableProductImage(context,file.absolutePath))
            val mapText = File("/proc/self/maps").readText()
            val loaded = JSONArray(listOf("libandroidx.graphics.path.so", "libdatastore_shared_counter.so", "libhwui.so", "libRS.so", "libart.so", "libsqlite.so").filter { mapText.contains(it) })
            val report = JSONObject().put("androidApi",android.os.Build.VERSION.SDK_INT).put("decoders",configs)
                .put("bothCurrentValidatorsAccepted",true).put("loadedSelectedLibraries",loaded)
            File(context.cacheDir, "native-path-audit.json").writeText(report.toString(2))
        } finally { file.delete() }
    }
}
