package com.lolita.app.ui.screen.item

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.di.AppModule
import com.lolita.app.ui.screen.common.ImageRegionCropDialog
import com.lolita.app.ui.theme.LolitaTheme
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.min

class ImageRegionCropDialogTest {
    @get:Rule val compose = createComposeRule()
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var directory: File
    private lateinit var context: ContextWrapper
    private lateinit var db: LolitaDatabase
    private lateinit var original: File
    private lateinit var originalHash: List<Byte>
    private var open by mutableStateOf(true)
    private var result: String? = null
    @Before fun setup() {
        directory = File(target.cacheDir, "crop-dialog-${UUID.randomUUID()}").apply { mkdirs() }
        context = object : ContextWrapper(target) {
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getCacheDir() = File(directory, "cache").apply { mkdirs() }
        }
        db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        ImageFileHelper.configure(context, db)
        original = File(directory, "original.png")
        val bitmap = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.RED)
            drawRect(200f, 0f, 300f, 200f, Paint().apply { color = Color.BLUE })
            drawRect(100f, 50f, 200f, 150f, Paint().apply { color = Color.GREEN })
        }
        original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        originalHash = MessageDigest.getInstance("SHA-256").digest(original.readBytes()).toList()
        compose.setContent { LolitaTheme {
            CompositionLocalProvider(LocalContext provides context) {
                if (open) ImageRegionCropDialog(original.path, onDismiss = { open = false }, onCropped = { result = it; open = false })
            }
        } }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("矩形选区，可拖动四角与双指缩放").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun cleanup() {
        compose.runOnIdle { open = false }
        ImageFileHelper.configure(AppModule.context(), AppModule.database())
        db.close(); directory.deleteRecursively()
    }
    private fun images() = File(context.filesDir, "images").listFiles().orEmpty().toList()

    @Test fun cancellationDeletesTheActualPreviewAndKeepsSourceBytesUntouched() = runBlocking {
        compose.onNodeWithText("确认使用选区").assertIsNotEnabled()
        compose.onNodeWithText("查看实际裁切").performScrollTo().performClick()
        compose.waitUntil(10_000) { images().size == 1 && compose.onAllNodesWithText("实际裁切 300 × 200 px", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("取消", substring = false).performClick()
        withTimeout(5_000) { while (images().isNotEmpty()) delay(25) }
        assertNull(result)
        assertEquals(originalHash, MessageDigest.getInstance("SHA-256").digest(original.readBytes()).toList())
    }

    @Test fun draggingBothCornersReturnsOnlyTheActualGreenSelectionAfterPreviewConfirmation() {
        val canvas = compose.onNodeWithContentDescription("矩形选区，可拖动四角与双指缩放")
        canvas.performScrollTo().performTouchInput {
            val scale = min(width / 300f, height / 200f) * 0.9f
            val w = 300f * scale; val h = 200f * scale
            val left = (width - w) / 2f; val top = (height - h) / 2f
            swipe(Offset(left, top), Offset(left + w / 3f, top + h / 4f), 300)
        }
        canvas.performTouchInput {
            val scale = min(width / 300f, height / 200f) * 0.9f
            val w = 300f * scale; val h = 200f * scale
            val left = (width - w) / 2f; val top = (height - h) / 2f
            swipe(Offset(left + w, top + h), Offset(left + w * 2f / 3f, top + h * 3f / 4f), 300)
        }
        compose.onNodeWithText("查看实际裁切").performScrollTo().performClick()
        compose.waitUntil(10_000) { images().size == 1 && compose.onAllNodesWithText("实际裁切 100 × 100 px", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("确认使用选区").assertIsEnabled().performClick()
        compose.waitUntil(5_000) { result != null }
        val output = BitmapFactory.decodeFile(result)
        assertEquals(100, output.width); assertEquals(100, output.height)
        listOf(Offset(5f, 5f), Offset(50f, 50f), Offset(95f, 95f)).forEach {
            val pixel = output.getPixel(it.x.toInt(), it.y.toInt())
            assertTrue(Color.green(pixel) > 200 && Color.red(pixel) < 40 && Color.blue(pixel) < 40)
        }
        output.recycle()
        assertEquals(originalHash, MessageDigest.getInstance("SHA-256").digest(original.readBytes()).toList())
    }
}
