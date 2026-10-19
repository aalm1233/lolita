package com.lolita.app.ui.screen.tryon

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.tryon.ImageServiceStatus
import com.lolita.app.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompleteWardrobeTryOnViewModelTest {
    private class Fixture {
        val category = Category(id = 1, name = "改名仍是裙", categorySemantic = CategorySemantic.JSK)
        val categories = MutableStateFlow(listOf(category, Category(id = 2, name = "鞋", categorySemantic = CategorySemantic.MARY_JANE)))
        val items = MutableStateFlow((1L..11).map { item(it) })
        var submitted = emptyList<TryOnSelection>()
        val serviceChanges = MutableStateFlow(0)
        val store = ViewModelStore()
        fun item(id: Long, category: Long = 1) = Item(id = id, name = "物品$id", description = "", brandId = 1, categoryId = category,
            imageUrls = listOf("/images/$id.jpg", "/images/$id-other.jpg"), status = ItemStatus.OWNED, coordinateId = 7)
        fun model(handle: SavedStateHandle): TryOnViewModel {
            lateinit var model: TryOnViewModel
            main {
                model = TryOnViewModel(handle, items = items, categories = categories, results = MutableStateFlow(emptyList()),
                    readService = { ImageServiceStatus(ImageGenerationProvider.OPENAI, true) }, selectService = {}, serviceChanges = serviceChanges,
                    saveApiKey = { _, _ -> }, clearApiKey = {}, tasks = MutableStateFlow(emptyList()), readRetryTask = { null },
                    submitTask = { selected, _, _ -> submitted = selected; "controlled-task" }, renameResult = { _, _ -> }, deleteResult = {})
                store.put("model", model)
            }
            return model
        }
        fun close() = main { store.clear() }
    }

    @Test fun elevenItemEntryNeverTruncatesAndRequiresAnExplicitEightItemSubset() = runBlocking {
        val f = Fixture()
        try {
            val vm = f.model(SavedStateHandle(mapOf("coordinateId" to 7L)))
            ready(vm)
            assertEquals(11, vm.uiState.value.selected.size)
            assertTrue(vm.uiState.value.generationSelected.isEmpty())
            main { vm.generate() }
            assertTrue(f.submitted.isEmpty())
            main { (1L..8).forEach(vm::toggleGenerationItem); vm.toggleGenerationItem(9) }
            assertEquals(8, vm.uiState.value.generationSelected.size)
            assertEquals(11, vm.uiState.value.selected.size)
            main { vm.generate() }
            withTimeout(5000) { vm.uiState.first { it.latestTaskId != null } }
            assertEquals((1L..8).toList(), f.submitted.map { it.itemId })
        } finally { f.close() }
    }

    @Test fun reducingPlanToEightDoesNotSilentlyEnrollAnExcludedItem() = runBlocking {
        val f = Fixture()
        try {
            val vm = f.model(SavedStateHandle(mapOf("coordinateId" to 7L)))
            ready(vm)
            main { (1L..7).forEach(vm::toggleGenerationItem); (9L..11).forEach(vm::toggleItem) }
            assertEquals(8, vm.uiState.value.selected.size)
            assertEquals((1L..7).toList(), vm.uiState.value.generationSelected.map { it.itemId })
            assertTrue(vm.uiState.value.requiresExplicitSubset)
        } finally { f.close() }
    }

    @Test fun secondShoeAsksWhichToReplaceAndFilteringKeepsTheOtherSelections() = runBlocking {
        val f = Fixture()
        f.items.value = listOf(f.item(1), f.item(2, 2), f.item(3, 2))
        try {
            val vm = f.model(SavedStateHandle())
            ready(vm)
            main { vm.toggleItem(1); vm.toggleItem(2); vm.toggleItem(3) }
            assertEquals(listOf(1L, 2L), vm.uiState.value.selected.map { it.itemId })
            assertTrue(vm.uiState.value.pendingConflict!!.conflict.requiresReplacement)
            main { vm.cancelConflict() }
            assertEquals(listOf(1L, 2L), vm.uiState.value.selected.map { it.itemId })
            main { vm.toggleItem(3); vm.resolveConflict(2); vm.updateSearchQuery("没有匹配"); vm.selectCategory(1) }
            assertEquals(listOf(1L, 3L), vm.uiState.value.selected.map { it.itemId })
            assertTrue(vm.uiState.value.visibleItems.isEmpty())
        } finally { f.close() }
    }

    @Test fun changingAnImageAfterPaidConfirmationCannotSubmitDifferentContent() = runBlocking {
        val f = Fixture()
        try {
            val vm = f.model(SavedStateHandle())
            ready(vm)
            main { vm.toggleItem(1) }
            val confirmed = vm.uiState.value.generationSelected.toList()
            val revision = vm.uiState.value.keyRevision
            main { vm.setImage(1, "/images/1-other.jpg"); vm.generate(confirmedSelection = confirmed, confirmedKeyRevision = revision) }
            assertTrue(f.submitted.isEmpty())
            assertNotNull(vm.uiState.value.error)
        } finally { f.close() }
    }

    @Test fun externalKeyRevisionInvalidatesAnOpenPaidConfirmation() = runBlocking {
        val f = Fixture()
        try {
            val vm = f.model(SavedStateHandle())
            ready(vm)
            main { vm.toggleItem(1) }
            val confirmed = vm.uiState.value.generationSelected.toList()
            val revision = vm.uiState.value.keyRevision
            f.serviceChanges.value += 1
            withTimeout(5000) { vm.uiState.first { it.keyLoaded && it.keyRevision != revision } }
            main { vm.generate(confirmedSelection = confirmed, confirmedKeyRevision = revision) }
            assertTrue(f.submitted.isEmpty())
            assertNotNull(vm.uiState.value.error)
        } finally { f.close() }
    }

    private suspend fun ready(vm: TryOnViewModel) = withTimeout(5000) { vm.uiState.first { !it.isLoading && it.keyLoaded && it.modelsLoaded } }
    companion object { private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block) }
}
