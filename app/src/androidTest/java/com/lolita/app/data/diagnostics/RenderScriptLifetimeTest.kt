package com.lolita.app.data.diagnostics

import android.graphics.Bitmap
import android.graphics.Color
import android.renderscript.RenderScript
import android.view.Surface
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.locks.LockSupport
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/** Uses the production backport with no test-side resource locks. No wardrobe data. */
@Suppress("DEPRECATION")
class RenderScriptLifetimeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val type = Class.forName("dev.chrisbanes.haze.RenderScriptContext")
    private val constructor = type.declaredConstructors.single { it.parameterTypes.size == 2 }.apply { isAccessible = true }
    private val applyBlur = type.getMethod("applyBlur", Float::class.javaPrimitiveType)
    private val release = type.getMethod("release")
    private val surface = type.getMethod("getInputSurface")
    private val await = type.getMethod("awaitSurfaceWritten", kotlin.coroutines.Continuation::class.java)

    private suspend fun prepared(rs: RenderScript): Any {
        val instance = constructor.newInstance(rs, (512L shl 32) or 512L)
        draw(instance, Color.BLUE)
        withTimeout(5000) {
            suspendCoroutineUninterceptedOrReturn<Unit> { continuation -> await.invoke(instance, continuation) }
        }
        return instance
    }

    private fun draw(instance: Any, color: Int) {
        val target = surface.invoke(instance) as Surface
        val canvas = target.lockHardwareCanvas()
        try { canvas.drawColor(color) } finally { target.unlockCanvasAndPost(canvas) }
    }

    @Test fun repeatedReleaseAndLateComputationAreSafeAfterCompleteInput() = runBlocking {
        val rs = RenderScript.create(context)
        try {
            val instance = prepared(rs)
            repeat(5) { applyBlur.invoke(instance, 16f) }
            val bitmap = type.getMethod("getOutputBitmap").invoke(instance) as Bitmap
            assertEquals(Color.BLUE, bitmap.getPixel(256, 256))
            repeat(3) { release.invoke(instance) }
            repeat(5) { applyBlur.invoke(instance, 16f) }
        } finally { rs.destroy() }
    }

    @Test fun pendingBuffersAndConcurrentComputationSurviveTeardown() = runBlocking {
        val rs = RenderScript.create(context)
        val failures = ConcurrentLinkedQueue<Throwable>()
        val report = File(context.cacheDir, "rs-lifetime-regression.txt")
        report.writeText("production code; no test-side locks\n")
        try {
            repeat(60) { index ->
                val instance = prepared(rs)
                val start = CountDownLatch(1)
                val worker = Thread {
                    try {
                        start.await()
                        repeat(5) { applyBlur.invoke(instance, 16f) }
                    } catch (failure: Throwable) {
                        failures.add(if (failure is InvocationTargetException) failure.targetException else failure)
                    }
                }
                report.appendText("iteration=$index start\n")
                worker.start()
                start.countDown()
                try {
                    draw(instance, Color.RED)
                    LockSupport.parkNanos((index % 5) * 100_000L)
                    release.invoke(instance)
                } finally {
                    worker.join(5000)
                }
                assertFalse("Blur worker must finish", worker.isAlive)
                report.appendText("iteration=$index done\n")
                assertTrue("Concurrent resource access failed: ${failures.map { it.javaClass.name }}", failures.isEmpty())
            }
        } finally { rs.destroy() }
        report.appendText("failures=0\n")
    }
}
