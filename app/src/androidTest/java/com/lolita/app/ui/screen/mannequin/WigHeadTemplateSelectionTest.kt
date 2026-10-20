package com.lolita.app.ui.screen.mannequin

import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.mannequin.WigHeadTemplateCatalog
import com.lolita.app.data.tryon.ImageServiceStatus
import com.lolita.app.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class WigHeadTemplateSelectionTest {
    @Test fun sevenTemplatesCanBePreviewedSelectedAndRestoredWithoutPaidCalls() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val root = File(app.cacheDir, "head-selection-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(root, "no-backup").apply { mkdirs() }
        }
        val catalog = WigHeadTemplateCatalog(context)
        val source = File(root, "product.jpg").apply { writeText("controlled product image") }
        val handle = SavedStateHandle(mapOf("itemId" to 1L))
        val stores = mutableListOf<ViewModelStore>()
        var paid = 0
        fun create(): MannequinViewModel {
            lateinit var vm: MannequinViewModel
            main {
                vm = MannequinViewModel(handle,
                    item = MutableStateFlow(Item(id = 1, brandId = 1, categoryId = 2, name = "长卷发", description = "", status = com.lolita.app.data.local.entity.ItemStatus.OWNED, imageUrls = listOf(source.path))),
                    categories = MutableStateFlow(listOf(Category(id = 2, name = "改过名", categorySemantic = CategorySemantic.FULL_WIG))),
                    readService = { ImageServiceStatus(ImageGenerationProvider.OPENAI, true) }, selectService = {}, serviceChanges = MutableStateFlow(0),
                    saveApiKey = { _, _ -> }, clearApiKey = {}, tasks = MutableStateFlow(emptyList()), readRetryTask = { null },
                    validateLegacy = { false }, appendLegacy = { _, _ -> }, discardLegacy = {}, submitTask = { _, _ -> paid++; "controlled-task" },
                    readHeadTemplates = { catalog.templates }, snapshotHead = catalog::snapshot,
                    readHeadPreference = { WigHeadTemplateCatalog.readPreference(context) }, writeHeadPreference = { WigHeadTemplateCatalog.writePreference(context, it) }, pinCropReferences = { { } })
                stores += ViewModelStore().also { it.put("vm", vm) }
            }
            return vm
        }
        try {
            assertEquals(7, catalog.templates.size)
            assertEquals(listOf("original-blueviolet", "rose-grayblue", "peach-violet", "cerulean-rose", "sage-softpink", "slate-rose", "faceless-ivory"), catalog.templates.map { it.id })
            catalog.templates.forEach { candidate ->
                val snapshot = catalog.snapshot(candidate.id)
                snapshot.validateImage()
                assertEquals(candidate.sha256, snapshot.sha256)
            }
            assertEquals("蓝紫妆", catalog.templates.first().displayName)
            assertTrue(catalog.templates.none { it.displayName.contains("候选") || it.displayName.contains("评审") })
            val first = create()
            withTimeout(5000) { first.uiState.first { it.selectedHeadTemplate != null && !it.isLoadingHead && it.keyLoaded } }
            val selectedId = catalog.templates.last().id
            main { first.selectHeadTemplate(selectedId) }
            val selected = withTimeout(5000) { first.uiState.first { it.selectedHeadTemplate?.id == selectedId && !it.isLoadingHead } }.selectedHeadTemplate!!
            selected.validateImage()
            assertEquals(0, paid)
            assertFalse(first.uiState.value.referenceViewConfirmed)
            main { first.confirmReferenceView(true) }
            val request = first.uiState.value.generationRequest!!
            assertEquals(selected.imagePath, request.wigHeadTemplate!!.imagePath)
            assertEquals(source.path, request.imagePath)
            assertEquals("preference-only", WigHeadTemplateCatalog.readPreference(context)!!.imagePath)
            main { stores.first().clear() }
            val restored = create()
            val restoredState = withTimeout(5000) { restored.uiState.first { it.selectedHeadTemplate != null && !it.isLoadingHead && it.keyLoaded } }
            assertEquals(selected, restoredState.selectedHeadTemplate)
            assertEquals(0, paid)
            val firstId = catalog.templates.first().id
            main { restored.selectHeadTemplate(firstId) }
            withTimeout(5000) { restored.uiState.first { it.selectedHeadTemplate?.id == firstId && !it.isLoadingHead } }
            main { restored.confirmReferenceView(true); restored.generate(confirmedRequest = request) }
            assertEquals(0, paid)
            assertNotNull(restored.uiState.value.error)
            val cropped = File(root, "derived-crop.jpg").apply { writeText("controlled cropped pixels") }
            main { restored.acceptCroppedImage(source.path, cropped.path); restored.confirmReferenceView(true) }
            assertEquals(source.path, restored.uiState.value.generationRequest!!.originalImagePath)
            assertEquals(cropped.path, restored.uiState.value.generationRequest!!.imagePath)
            assertEquals(listOf(source.path), restored.uiState.value.item!!.imageUrls)
            main { stores.last().clear() }
            val cropRestored = create()
            withTimeout(5000) { cropRestored.uiState.first { !it.isLoading && !it.isLoadingHead && it.selectedHeadTemplate != null } }
            assertEquals(cropped.path, cropRestored.uiState.value.selectedImage)
            assertEquals(source.path, cropRestored.uiState.value.originalImagePath)
            assertEquals(0, paid)
            main { cropRestored.useOriginalImage() }
            assertEquals(source.path, cropRestored.uiState.value.selectedImage)
            assertNull(cropRestored.uiState.value.originalImagePath)
        } finally {
            main { stores.forEach { it.clear() } }
            root.deleteRecursively()
        }
    }
    private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
}
