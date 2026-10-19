package com.lolita.app.ui.screen.tryon

import com.lolita.app.domain.model.CategorySemantic

import android.content.ContextWrapper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.tryon.TryOnSettings
import com.lolita.app.domain.model.ImageGenerationProvider
import com.lolita.app.ui.screen.mannequin.MannequinViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ImageServiceConfigurationTest {
    @Test fun bothRetainedScreensFollowServiceSwitchWithoutMixingOrDeletingKeys() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = File(instrumentation.targetContext.cacheDir, "service-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(instrumentation.targetContext) {
            override fun getNoBackupFilesDir() = File(root, "credentials").apply { mkdirs() }
        }
        val settings = TryOnSettings(context)
        val store = ViewModelStore()
        val busy = MutableStateFlow(false)
        val item = Item(id = 11, brandId = 1, categoryId = 1, name = "测试裙", description = "", status = ItemStatus.OWNED, imageUrls = listOf("/reference.png"))
        try {
            settings.saveKey("legacy-gpt-key", ImageGenerationProvider.OPENAI)
            val tryOn = withContext(Dispatchers.Main) {
                TryOnViewModel(items = flowOf(listOf(item)), categories = flowOf(listOf(Category(id = 1, name = "OP", categorySemantic = CategorySemantic.OP))), results = flowOf(emptyList()),
                    readService = settings::readStatus, selectService = settings::selectProvider, serviceChanges = settings.changes,
                    saveApiKey = { provider, value -> settings.saveKey(value, provider) }, clearApiKey = settings::clearKey,
                    tasks = flowOf(emptyList()), submitTask = { _, _, _ -> error("no paid calls") }, renameResult = { _, _ -> }, deleteResult = {})
                    .also { store.put("tryOn", it) }
            }
            val mannequin = withContext(Dispatchers.Main) {
                MannequinViewModel(savedStateHandle = SavedStateHandle(mapOf("itemId" to 11L)), item = flowOf(item), categories = flowOf(listOf(Category(id = 1, name = "OP", categorySemantic = CategorySemantic.OP))),
                    readService = settings::readStatus, selectService = settings::selectProvider, serviceChanges = settings.changes,
                    saveApiKey = { provider, value -> settings.saveKey(value, provider) }, clearApiKey = settings::clearKey,
                    tasks = flowOf(emptyList()), submitTask = { _, _ -> "task-id" })
                    .also { store.put("mannequin", it) }
            }
            suspend fun await(provider: ImageGenerationProvider, hasKey: Boolean) = withTimeout(10_000) {
                tryOn.uiState.first { it.keyLoaded && !it.busy && it.provider == provider && it.hasKey == hasKey }
                mannequin.uiState.first { it.keyLoaded && !it.busy && it.provider == provider && it.hasKey == hasKey }
            }
            await(ImageGenerationProvider.OPENAI, true)
            withContext(Dispatchers.Main) { mannequin.selectProvider(ImageGenerationProvider.SEEDREAM) }
            await(ImageGenerationProvider.SEEDREAM, false)
            withContext(Dispatchers.Main) { tryOn.saveKey("seed-private-key") }
            await(ImageGenerationProvider.SEEDREAM, true)
            assertEquals("legacy-gpt-key", settings.readKey(ImageGenerationProvider.OPENAI))
            // Background tasks no longer freeze service configuration in retained pages.
            withContext(Dispatchers.Main) { mannequin.generate() }
            withTimeout(10_000) { mannequin.uiState.first { it.latestTaskId != null && !it.busy } }
            withContext(Dispatchers.Main) { tryOn.clearKey() }
            await(ImageGenerationProvider.SEEDREAM, false)
            withContext(Dispatchers.Main) { tryOn.selectProvider(ImageGenerationProvider.OPENAI) }
            await(ImageGenerationProvider.OPENAI, true)
            assertEquals("legacy-gpt-key", settings.readKey())
        } finally { withContext(Dispatchers.Main) { store.clear() }; root.deleteRecursively() }
    }
}
