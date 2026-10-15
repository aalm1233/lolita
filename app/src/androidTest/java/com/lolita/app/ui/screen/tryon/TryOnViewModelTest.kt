package com.lolita.app.ui.screen.tryon

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.repository.TryOnRepository
import com.lolita.app.data.tryon.TryOnGenerationPhase
import com.lolita.app.domain.model.TryOnRole
import com.lolita.app.domain.model.TryOnSelection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated Room wardrobe/history; only the paid external operation and credential boundary are stubbed. */
@RunWith(AndroidJUnit4::class)
class TryOnViewModelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var database: LolitaDatabase
    private lateinit var store: ViewModelStore
    private lateinit var repository: TryOnRepository
    private var configured = true
    private val globalBusy = MutableStateFlow(false)
    private var requests = 0
    private var operation: suspend (List<TryOnSelection>, (TryOnGenerationPhase) -> Unit) -> Long = { _, _ -> 91L }

    @Before fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, LolitaDatabase::class.java).build()
        store = ViewModelStore()
        repository = TryOnRepository(instrumentation.targetContext, database)
        database.brandDao().insertBrand(Brand(id = 1, name = "测试品牌"))
        database.categoryDao().insertCategory(Category(id = 1, name = "JSK"))
        database.categoryDao().insertCategory(Category(id = 2, name = "头饰", group = CategoryGroup.ACCESSORY))
        database.itemDao().insertItem(item(11, "月光裙", 1, listOf("/first.png", "/second.png")))
        database.itemDao().insertItem(item(12, "蝴蝶结", 2, listOf("/head.png")))
        database.itemDao().insertItem(item(13, "无图裙", 1, emptyList()))
        database.itemDao().insertItem(item(14, "心愿裙", 1, listOf("/wish.png")).copy(status = ItemStatus.WISHED))
        Unit
    }

    @After fun teardown() {
        instrumentation.runOnMainSync { store.clear() }
        instrumentation.waitForIdleSync()
        database.close()
    }

    private fun item(id: Long, name: String, category: Long, images: List<String>) = Item(
        id = id, name = name, brandId = 1, categoryId = category, description = "不上传的备注",
        imageUrls = images, status = ItemStatus.OWNED
    )

    private suspend fun model(handle: SavedStateHandle = SavedStateHandle()): TryOnViewModel {
        val vm = withContext(Dispatchers.Main) {
            TryOnViewModel(
                savedStateHandle = handle,
                items = database.itemDao().getAllItems(),
                categories = database.categoryDao().getAllCategories(),
                results = repository.results,
                readService = { com.lolita.app.data.tryon.ImageServiceStatus(com.lolita.app.domain.model.ImageGenerationProvider.OPENAI, configured) }, selectService = {}, serviceChanges = kotlinx.coroutines.flow.flowOf(0),
                saveApiKey = { _, value -> configured = true },
                clearApiKey = { _ -> configured = false },
                globalGenerating = globalBusy,
                generateImage = { rows, phase, _ -> requests++; operation(rows, phase) },
                renameResult = repository::rename,
                deleteResult = repository::delete
            ).also { store.put("vm-${System.nanoTime()}", it) }
        }
        withTimeout(10_000) { vm.uiState.first { !it.isLoading && it.keyLoaded } }
        return vm
    }

    private suspend fun main(action: () -> Unit) = withContext(Dispatchers.Main) { action() }

    @Test fun filtersKeepHiddenSelectionAndImageRoleCorrectionsSurviveRecreation() = runBlocking {
        val handle = SavedStateHandle()
        val vm = model(handle)
        main {
            vm.toggleItem(11)
            vm.setImage(11, "/second.png")
            vm.setRole(11, TryOnRole.OUTER)
            vm.updateSearchQuery("蝴蝶")
            vm.selectCategory(2)
            vm.toggleItem(14)
        }
        assertEquals(listOf(12L), vm.uiState.value.visibleItems.map { it.id })
        assertEquals(listOf(11L), vm.uiState.value.selected.map { it.itemId })
        val recreated = model(handle)
        assertEquals("/second.png", recreated.uiState.value.selected.single().imagePath)
        assertEquals(TryOnRole.OUTER, recreated.uiState.value.selected.single().role)
        assertFalse(recreated.uiState.value.items.any { it.id == 14L })
    }

    @Test fun noKeyNoGarmentAndMissingPictureNeverCallPaidOperation() = runBlocking {
        configured = false
        val handle = SavedStateHandle()
        val vm = model(handle)
        main { vm.toggleItem(11); vm.generate() }
        assertEquals(0, requests)
        assertTrue(vm.uiState.value.error!!.contains("API Key"))
        main { vm.saveKey("a-private-key") }
        withTimeout(10_000) { vm.uiState.first { it.hasKey && !it.isConfiguring } }
        main { vm.toggleItem(11); vm.toggleItem(12); vm.generate() }
        assertEquals(0, requests)
        main { vm.toggleItem(12); vm.toggleItem(13); vm.generate() }
        assertEquals(0, requests)
        assertTrue(vm.uiState.value.error!!.contains("图片"))
        assertFalse(handle.keys().any { key -> handle.get<Any>(key).toString().contains("a-private-key") })
    }

    @Test fun duplicateClicksAndGlobalBusyAreBlockedAndFailureRetainsSelection() = runBlocking {
        val pending = CompletableDeferred<Long>()
        operation = { _, phase -> phase(TryOnGenerationPhase.GENERATING); pending.await() }
        val vm = model()
        main { vm.toggleItem(11); vm.generate(); vm.generate(); vm.toggleItem(12) }
        withTimeout(10_000) { vm.uiState.first { it.phase == TryOnGenerationPhase.GENERATING } }
        assertEquals(1, requests)
        assertEquals(listOf(11L), vm.uiState.value.selected.map { it.itemId })
        pending.completeExceptionally(IllegalStateException("受控服务失败"))
        withTimeout(10_000) { vm.uiState.first { !it.isGenerating && it.error != null } }
        assertEquals(listOf(11L), vm.uiState.value.selected.map { it.itemId })
        globalBusy.value = true
        withTimeout(10_000) { vm.uiState.first { it.globalGenerating } }
        main { vm.generate() }
        assertEquals(1, requests)
    }

    @Test fun successPublishesSavedHistoryAndRenameDeleteDoNotChangeWardrobe() = runBlocking {
        operation = { selected, _ ->
            database.tryOnDao().insert(TryOnResult(name = "试衣结果", imageUrl = "/result.png",
                itemIds = selected.map { it.itemId.toString() }, itemNames = selected.map { it.name }, model = "test-model"))
        }
        val vm = model()
        main { vm.toggleItem(11); vm.generate() }
        val completed = withTimeout(10_000) { vm.uiState.first { it.latestResultId != null && it.results.isNotEmpty() } }
        val resultId = completed.latestResultId!!
        main { vm.rename(resultId, "月光搭配") }
        withTimeout(10_000) { vm.uiState.first { it.results.single().name == "月光搭配" } }
        main { vm.delete(resultId) }
        withTimeout(10_000) { vm.uiState.first { it.results.isEmpty() } }
        assertEquals(4, database.itemDao().getAllItemsList().size)
        assertEquals(listOf(11L), vm.uiState.value.selected.map { it.itemId })
    }

    @Test fun genericAccessoryCategoriesSuggestRolesFromNamesAndSpecificGarmentCategoriesRemainAuthoritative() = runBlocking {
        database.categoryDao().insertCategory(Category(id = 3, name = "其他配饰", group = CategoryGroup.ACCESSORY))
        database.itemDao().insertItem(item(21, "蕾丝手袖", 3, listOf("/hand.png")))
        database.itemDao().insertItem(item(22, "链条腰带", 3, listOf("/waist.png")))
        database.itemDao().insertItem(item(23, "手绘花鸟裙", 1, listOf("/dress.png")))
        val vm = model()
        main { vm.toggleItem(21); vm.toggleItem(22); vm.toggleItem(23) }
        assertEquals(listOf(TryOnRole.HAND, TryOnRole.WAIST, TryOnRole.MAIN), vm.uiState.value.selected.map { it.role })
    }

    @Test fun cancellationKeepsSelectionAndRequiresExplicitRetry() = runBlocking {
        operation = { _, _ -> CompletableDeferred<Long>().await() }
        val vm = model()
        main { vm.toggleItem(11); vm.generate() }
        withTimeout(10_000) { vm.uiState.first { it.isGenerating } }
        main { vm.cancelGeneration() }
        withTimeout(10_000) { vm.uiState.first { !it.isGenerating && it.error != null } }
        assertEquals(1, requests)
        assertTrue(vm.uiState.value.error!!.contains("计费"))
        assertEquals(listOf(11L), vm.uiState.value.selected.map { it.itemId })
    }
}
