package com.lolita.app.ui.screen.settings

import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.repository.*
import com.lolita.app.data.tryon.TryOnSettings
import com.lolita.app.data.recognition.RecognitionSettings
import com.lolita.app.ui.theme.LolitaTheme
import com.lolita.app.ui.theme.SkinType
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AiConfigurationScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allFourConfigurationsSaveMaskNeverRefillAndRemainUsableAcrossSkinsAndLargeText() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val directory = File(app.cacheDir, "api-ui-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getNoBackupFilesDir() = directory }
        val repository = AiConfigurationRepository(TryOnSettings(context), RecognitionSettings(context))
        val store = ViewModelStore()
        lateinit var vm: AiConfigurationViewModel
        val skin = mutableStateOf(SkinType.DEFAULT)
        val dark = mutableStateOf(false)
        val scale = mutableFloatStateOf(1f)
        instrumentation.runOnMainSync { vm = AiConfigurationViewModel(repository); store.put("vm", vm) }
        compose.waitUntil(10_000) { !vm.uiState.value.isLoading }
        try {
            compose.setContent {
                val config = Configuration(LocalConfiguration.current).apply {
                    screenWidthDp = 320
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }
                CompositionLocalProvider(LocalConfiguration provides config, LocalDensity provides Density(LocalDensity.current.density, scale.floatValue)) {
                    LolitaTheme(skinType = skin.value, darkTheme = dark.value) {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            Box(Modifier.size(320.dp, 700.dp)) { AiConfigurationScreen({}, vm) }
                        }
                    }
                }
            }
            fun reveal(slot: AiKeySlot) {
                compose.onNodeWithTag("aiConfiguration").performScrollToNode(hasContentDescription("配置${slot.purpose}密钥：${slot.label}"))
            }
            fun edit(slot: AiKeySlot) {
                reveal(slot)
                compose.onNodeWithContentDescription("配置${slot.purpose}密钥：${slot.label}").performScrollTo().performClick()
            }
            val output = File(app.filesDir, "api-configuration-visuals").apply { mkdirs() }
            for (slot in AiKeySlot.entries) {
                edit(slot)
                compose.onNodeWithText("API Key", substring = false).performScrollTo().assert(
                    SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
                compose.onNodeWithText("API Key", substring = false).performTextInput("controlled-ui-${slot.name}")
                compose.onNodeWithText("API Key", substring = false).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
                compose.onNodeWithText("保存密钥").performClick()
                compose.waitUntil(10_000) { !vm.uiState.value.busy && vm.uiState.value.editing == null && vm.uiState.value.configuration.keys[slot] == AiKeyStatus.CONFIGURED }
            }
            assertEquals(4, repository.read().keys.values.count { it == AiKeyStatus.CONFIGURED })
            assertFalse(vm.uiState.value.toString().contains("controlled-ui-"))
            edit(AiKeySlot.OPENAI)
            compose.onNodeWithText("API Key", substring = false).performScrollTo().performTextInput("controlled-discarded-input")
            repository.select(AiKeySlot.SEEDREAM)
            compose.waitUntil(10_000) { vm.uiState.value.editing == null }
            edit(AiKeySlot.OPENAI)
            compose.onNodeWithText("API Key", substring = false).performScrollTo().assert(
                SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
            compose.onNodeWithText("删除本机密钥").performScrollTo().performClick()
            compose.waitUntil(10_000) { !vm.uiState.value.busy && vm.uiState.value.editing == null }
            assertEquals(AiKeyStatus.EMPTY, repository.read().keys[AiKeySlot.OPENAI])
            assertEquals(3, repository.read().keys.values.count { it == AiKeyStatus.CONFIGURED })
            fun capture(name: String) {
                compose.onRoot().captureToImage().asAndroidBitmap().also { bitmap ->
                    File(output, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
            for (currentSkin in SkinType.entries) for (night in listOf(false, true)) {
                compose.runOnIdle { skin.value = currentSkin; dark.value = night }
                compose.onNodeWithTag("aiConfiguration").performScrollToIndex(0)
                compose.onNodeWithText("生图服务").assertIsDisplayed()
                capture("${currentSkin.name.lowercase()}-${if (night) "dark" else "light"}.png")
                reveal(AiKeySlot.DEEPSEEK)
                compose.onNodeWithContentDescription("配置识图密钥：DeepSeek").assertIsDisplayed().assertIsEnabled()
            }
            for (night in listOf(false, true)) {
                compose.runOnIdle { skin.value = SkinType.DEFAULT; dark.value = night; scale.floatValue = 2f }
                reveal(AiKeySlot.DEEPSEEK)
                capture("large-${if (night) "dark" else "light"}.png")
                edit(AiKeySlot.DEEPSEEK)
                compose.onNodeWithText("API Key", substring = false).performScrollTo().performTextInput("controlled-unsaved")
                compose.onNodeWithText("保存替换").assertIsDisplayed().assertIsEnabled()
                compose.onNodeWithText("返回", substring = false).performClick()
            }
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            directory.deleteRecursively()
        }
    }
}
