package com.lolita.app.ui.screen.mannequin

import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.tryon.ImageServiceStatus
import com.lolita.app.domain.model.*
import com.lolita.app.ui.theme.LolitaTheme
import com.lolita.app.ui.theme.SkinType
import com.lolita.app.ui.theme.skin.component.SkinArtBackground
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import org.junit.runners.model.MultipleFailureException
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Native 320dp/2x Compose pages with an isolated Room item and a drawn, non-user blouse. No API calls. */
class BlouseMannequinScreenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target = instrumentation.targetContext
    private val compose = createComposeRule()
    private val nativeViewport = TestRule { base, description -> object : Statement() {
        override fun evaluate() {
            val resources = target.resources
            val oldDensity = resources.displayMetrics.densityDpi
            val oldFont = resources.configuration.fontScale
            val densityOverride = Regex("Override density: (\\d+)").find(shell("wm density"))?.groupValues?.get(1)
            val fontSetting = shell("settings get system font_scale")
            val nightSetting = requireNotNull(Regex("Night mode: (auto|no|yes|custom)").find(shell("cmd uimode night"))).groupValues[1]
            val dpi = (resources.displayMetrics.widthPixels / 320f * 160).toInt()
            val night = description.methodName.contains("Dark")
            try {
                shell("wm density $dpi")
                shell("settings put system font_scale 2.0")
                shell("cmd uimode night ${if (night) "yes" else "no"}")
                awaitConfiguration(dpi, 2f, if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
                dark.value = night
                base.evaluate()
            } finally {
                if (fontSetting == "null") shell("settings delete system font_scale") else {
                    require(fontSetting.matches(Regex("[0-9]+(?:\\.[0-9]+)?")))
                    shell("settings put system font_scale $fontSetting")
                }
                shell(densityOverride?.let { "wm density $it" } ?: "wm density reset")
                shell("cmd uimode night $nightSetting")
                // Auto/custom may legitimately change its resolved day/night while the test runs.
                // Restore the setting, then wait for density/font; explicit yes/no must resolve exactly.
                val restoredNight = when (nightSetting) {
                    "yes" -> Configuration.UI_MODE_NIGHT_YES
                    "no" -> Configuration.UI_MODE_NIGHT_NO
                    else -> null
                }
                awaitConfiguration(oldDensity, oldFont, restoredNight)
                assertTrue(shell("cmd uimode night").contains("Night mode: $nightSetting"))
            }
        }
    } }
    @get:Rule val rules: TestRule = RuleChain.outerRule(nativeViewport).around(compose)
    private val skin = mutableStateOf(SkinType.DEFAULT)
    private val dark = mutableStateOf(false)
    private lateinit var root: File
    private lateinit var db: LolitaDatabase
    private lateinit var store: ViewModelStore
    private lateinit var vm: MannequinViewModel
    private var viewModelJob: Job? = null
    private var submissions = 0

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }

    private fun awaitConfiguration(dpi: Int, scale: Float, night: Int?) {
        val deadline = android.os.SystemClock.uptimeMillis() + 5_000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            val resources = target.resources
            if (resources.displayMetrics.densityDpi == dpi && kotlin.math.abs(resources.configuration.fontScale - scale) < .01f &&
                (night == null || resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == night)) return
            Thread.sleep(25)
        }
        error("Native blouse viewport did not become $dpi dpi / $scale / $night")
    }

    @Before fun setup() = runBlocking {
        root = File(target.cacheDir, "blouse-ui-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
        }
        db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        val photo = File(context.filesDir, "${UUID.randomUUID()}.png")
        drawBlouse(photo)
        db.brandDao().insertBrand(Brand(id = 1, name = "测试品牌"))
        db.categoryDao().insertCategory(Category(id = 1, name = "测试衬衫", categorySemantic = CategorySemantic.BLOUSE))
        db.itemDao().insertItem(Item(id = 11, brandId = 1, categoryId = 1, name = "衬衫测试夹具", description = "",
            status = ItemStatus.OWNED, imageUrls = listOf(photo.path)))
        withContext(Dispatchers.Main) {
            store = ViewModelStore()
            vm = MannequinViewModel(SavedStateHandle(mapOf("itemId" to 11L)),
                item = db.itemDao().observeItem(11), categories = db.categoryDao().getAllCategories(),
                readService = { ImageServiceStatus(ImageGenerationProvider.SEEDREAM, true) }, selectService = {},
                serviceChanges = flowOf(0), saveApiKey = { _, _ -> }, clearApiKey = {}, tasks = flowOf(emptyList()),
                submitTask = { _, _ -> submissions++; error("Screenshot inspection must never submit a task") })
            store.put("blouse-screen", vm)
            viewModelJob = vm.viewModelScope.coroutineContext[Job]
        }
        compose.setContent {
            LolitaTheme(skinType = skin.value, darkTheme = dark.value) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize().navigationBarsPadding()) {
                        SkinArtBackground()
                        MannequinScreen(onNavigateBack = {}, viewModel = vm)
                    }
                }
            }
        }
        compose.waitUntil(10_000) { !vm.uiState.value.isLoading && vm.uiState.value.keyLoaded }
        assertNull(vm.uiState.value.selectionIssue)
    }

    @After fun close() {
        val failures = mutableListOf<Throwable>()
        fun cleanup(block: () -> Unit) { runCatching(block).exceptionOrNull()?.let(failures::add) }
        cleanup { if (::store.isInitialized) compose.runOnIdle { store.clear() } }
        cleanup { runBlocking { viewModelJob?.cancelAndJoin() } }
        cleanup { compose.waitForIdle() }
        cleanup { if (::db.isInitialized) db.close() }
        cleanup { if (::root.isInitialized) root.deleteRecursively() }
        MultipleFailureException.assertEmpty(failures)
    }

    @Test fun sevenLightSkinsShowBlouseAndExplicitTwoImageConfirmationAt320dpAndDoubleText() = inspectSkins()
    @Test fun sevenDarkSkinsShowBlouseAndExplicitTwoImageConfirmationAt320dpAndDoubleText() = inspectSkins()

    private fun inspectSkins() {
        val output = File(target.filesDir, "blouse-mannequin-screenshots/${UUID.randomUUID()}").apply { mkdirs() }
        val manifest = mutableListOf<Map<String, Any>>()
        for (candidate in SkinType.entries) {
            compose.runOnIdle { skin.value = candidate }
            compose.onNodeWithTag("mannequinWorkspace").performScrollToNode(hasText("衬衫测试夹具"))
            capture(output, candidate, "workspace", manifest)
            val title = compose.onNodeWithTag("blouseMannequinTitle").assertIsDisplayed()
            title.assertTextContains("衬衫", substring = true).assertTextContains("展示", substring = true)
            assertTextNotClipped(title, expectedText = "衬衫展示")
            val send = compose.onNodeWithText("生成衬衫人台图").assertIsDisplayed().assertIsEnabled()
            assertTextNotClipped(send)
            compose.onNodeWithTag("mannequinUploadSummary").assertTextContains("共2张图片", substring = true)
            compose.onNodeWithTag("mannequinWorkspace").performScrollToNode(hasText("领口、双袖、袖口和下摆完整", substring = true))
            compose.onNodeWithText("领口、双袖、袖口和下摆完整", substring = true).assertIsDisplayed()
            send.performClick()
            capture(output, candidate, "confirmation-open", manifest)
            compose.onNodeWithText("确认生成衬衫人台图？").assertIsDisplayed()
            compose.onNode(hasText("共2张图片；API 单独计费", substring = true) and hasAnyAncestor(isDialog()))
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("确认并加入任务").assertIsDisplayed().assertIsEnabled()
            assertEquals(0, submissions)
            capture(output, candidate, "confirmation", manifest)
            compose.onNodeWithText("再核对一下").performClick()
            compose.onNodeWithText("确认生成衬衫人台图？").assertDoesNotExist()
            assertEquals(0, submissions)
        }
    }

    private fun assertTextNotClipped(node: SemanticsNodeInteraction, expectedText: String? = null) {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
        assertTrue(layouts.isNotEmpty())
        if (expectedText != null) {
            assertEquals(expectedText, layouts.single().layoutInput.text.text.replace("\n", ""))
        }
        assertTrue("Blouse text must remain complete: " + layouts.map {
            "${it.layoutInput.text.text}: ${it.size}, lines=${it.lineCount}, width=${it.didOverflowWidth}, height=${it.didOverflowHeight}, " +
                "constraints=${it.layoutInput.constraints}, style=${it.layoutInput.style}, " +
                "density=${it.layoutInput.density.density}, fontScale=${it.layoutInput.density.fontScale}, " +
                "paragraphWidth=${it.multiParagraph.width}, maxIntrinsicWidth=${it.multiParagraph.maxIntrinsicWidth}, " +
                "lineBounds=${(0 until it.lineCount).map { line -> "$line:[${it.getLineLeft(line)}, ${it.getLineRight(line)}]" }}"
        }, layouts.all { layout ->
            // Compose may retain the paragraph's loose maxWidth while measuring a start-aligned
            // Text to its intrinsic width. Check actual line ink: the former centered title
            // exceeded its 398px box (right=470px), whereas button ink ends at 613px in 615px.
            !layout.didOverflowHeight && (0 until layout.lineCount).all { line ->
                !layout.isLineEllipsized(line) && layout.getLineLeft(line) >= -.5f &&
                    layout.getLineRight(line) <= layout.size.width + .5f
            }
        })
    }

    private fun capture(output: File, candidate: SkinType, state: String, manifest: MutableList<Map<String, Any>>) {
        compose.waitForIdle()
        // Compose idle can precede the platform Dialog window's compositor animation.
        // Let the native window finish drawing before recording review evidence.
        instrumentation.waitForIdleSync()
        Thread.sleep(300)
        instrumentation.waitForIdleSync()
        val file = File(output, "${candidate.name.lowercase()}-${if (dark.value) "dark" else "light"}-$state.png")
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try { file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
        manifest += mapOf("file" to file.name, "skin" to candidate.name, "dark" to dark.value, "state" to state,
            "widthDp" to 320, "fontScale" to 2, "syntheticFixture" to true, "submittedTasks" to submissions,
            "sha256" to MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) })
        File(output, "manifest.json").writeText(Gson().toJson(manifest))
    }

    private fun drawBlouse(file: File) {
        val bitmap = Bitmap.createBitmap(320, 400, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(248, 244, 238))
            val shirt = Path().apply {
                moveTo(125f, 70f); lineTo(88f, 90f); lineTo(45f, 190f); lineTo(83f, 208f)
                lineTo(111f, 155f); lineTo(111f, 320f); lineTo(209f, 320f); lineTo(209f, 155f)
                lineTo(237f, 208f); lineTo(275f, 190f); lineTo(232f, 90f); lineTo(195f, 70f); close()
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(220, 194, 207) }
            canvas.drawPath(shirt, paint)
            paint.color = Color.rgb(117, 73, 101); paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
            canvas.drawPath(shirt, paint); canvas.drawLine(160f, 100f, 160f, 320f, paint)
            paint.style = Paint.Style.FILL
            for (y in 130..285 step 35) canvas.drawCircle(171f, y.toFloat(), 3f, paint)
            paint.textSize = 18f; canvas.drawText("SYNTHETIC BLOUSE", 68f, 370f, paint)
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
    }
}
