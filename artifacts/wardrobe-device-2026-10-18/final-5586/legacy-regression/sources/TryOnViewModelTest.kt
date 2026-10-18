package com.lolita.app.ui.screen.tryon

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.domain.model.CategorySemantic
import com.lolita.app.data.repository.TryOnRepository
import com.lolita.app.data.tryon.TryOnGenerationPhase
import com.lolita.app.domain.model.TryOnRole
import com.lolita.app.domain.model.TryOnSelection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.io.File
import java.util.UUID
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
    private val personalModels = MutableStateFlow<List<PersonalModel>>(emptyList())
    private val defaultModel = MutableStateFlow<Long?>(null)
    private var personalRequests = 0
    private var submittedModelId: Long? = null
    private lateinit var directory: File
    private var configured = true
    private val tasks = MutableStateFlow<List<ImageTask>>(emptyList())
    private var requests = 0
    private var retryReader: suspend (String) -> ImageTask? = { database.imageTaskDao().get(it) }
    private var operation: suspend (List<TryOnSelection>) -> String = { "task-91" }

    @Before fun setup() = runBlocking {
        directory = File(instrumentation.targetContext.cacheDir, "tryon-personal-${UUID.randomUUID()}").apply { mkdirs() }
        database = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, LolitaDatabase::class.java).build()
        store = ViewModelStore()
        repository = TryOnRepository(instrumentation.targetContext, database)
        database.brandDao().insertBrand(Brand(id = 1, name = "测试品牌"))
        database.categoryDao().insertCategory(Category(id = 1, name = "JSK", categorySemantic = CategorySemantic.JSK))
        database.categoryDao().insertCategory(Category(id = 2, name = "头饰", group = CategoryGroup.ACCESSORY, categorySemantic = CategorySemantic.KC))
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
        directory.deleteRecursively()
    }

    private fun item(id: Long, name: String, category: Long, images: List<String>) = Item(
        id = id, name = name, brandId = 1, categoryId = category, description = "不上传的备注",
        imageUrls = images, status = ItemStatus.OWNED
    )

    private suspend fun model(handle: SavedStateHandle = SavedStateHandle(), models: Flow<List<PersonalModel>> = personalModels, defaults: Flow<Long?> = defaultModel, waitModels: Boolean = true): TryOnViewModel {
        val vm = withContext(Dispatchers.Main) {
            TryOnViewModel(
                savedStateHandle = handle,
                items = database.itemDao().getAllItems(),
                categories = database.categoryDao().getAllCategories(),
                results = repository.results,
                readService = { com.lolita.app.data.tryon.ImageServiceStatus(com.lolita.app.domain.model.ImageGenerationProvider.OPENAI, configured) }, selectService = {}, serviceChanges = kotlinx.coroutines.flow.flowOf(0),
                saveApiKey = { _, value -> configured = true },
                clearApiKey = { _ -> configured = false },
                tasks = tasks, readRetryTask = { retryReader(it) },
                submitTask = { rows, _ -> requests++; operation(rows) },
                renameResult = repository::rename,
                deleteResult = repository::delete,
                personalModels = models, defaultPersonalModelId = defaults,
                submitPersonalTask = { _, _, id, _ -> personalRequests++; submittedModelId = id; "personal-task" }
            ).also { store.put("vm-${System.nanoTime()}", it) }
        }
        withTimeout(10_000) { vm.uiState.first { !it.isLoading && it.keyLoaded && (!waitModels || it.modelsLoaded) } }
        return vm
    }

    private suspend fun main(action: () -> Unit) = withContext(Dispatchers.Main) { action() }

    @Test fun backgroundGenerationDoesNotBlockEditingOrAnotherExplicitSubmission() = runBlocking {
        val vm = model()
        main { vm.toggleItem(11) }
        tasks.value = listOf(backgroundTask())
        instrumentation.waitForIdleSync()
        assertTrue("后台任务不能锁住生成页草稿", vm.uiState.value.canGenerate)
        main { vm.toggleItem(12) }
        assertEquals(listOf(11L, 12L), vm.uiState.value.selected.map { it.itemId })
    }

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

    @Test fun duplicateSubmitIsBrieflyBlockedAndAcceptedTaskDoesNotLockDraft() = runBlocking {
        val pending = CompletableDeferred<String>()
        operation = { pending.await() }
        val vm = model()
        main { vm.toggleItem(11); vm.generate(); vm.generate(); vm.toggleItem(12) }
        assertTrue(vm.uiState.value.isSubmitting)
        assertEquals(1, requests)
        pending.complete("task-91")
        withTimeout(10_000) { vm.uiState.first { !it.isSubmitting && it.latestTaskId == "task-91" } }
        main { vm.toggleItem(12) }
        assertEquals(listOf(11L, 12L), vm.uiState.value.selected.map { it.itemId })
    }

    @Test fun historyRenameDeleteDoNotChangeWardrobeOrSelection() = runBlocking {
        val resultId = database.tryOnDao().insert(TryOnResult(name = "试衣结果", imageUrl = "/result.png", itemIds = listOf("11"), itemNames = listOf("月光裙"), model = "test-model"))
        val vm = model()
        withTimeout(10_000) { vm.uiState.first { it.results.isNotEmpty() } }
        main { vm.toggleItem(11); vm.rename(resultId, "月光搭配") }
        withTimeout(10_000) { vm.uiState.first { it.results.single().name == "月光搭配" } }
        main { vm.delete(resultId) }
        withTimeout(10_000) { vm.uiState.first { it.results.isEmpty() } }
        assertEquals(4, database.itemDao().getAllItemsList().size)
        assertEquals(listOf(11L), vm.uiState.value.selected.map { it.itemId })
    }

    @Test fun providerChangedAfterDialogCannotSubmitToAnotherProvider() = runBlocking {
        val vm = model()
        main { vm.toggleItem(11); vm.generate(com.lolita.app.domain.model.ImageGenerationProvider.SEEDREAM) }
        assertEquals(0, requests)
        assertTrue(vm.uiState.value.error!!.contains("服务已改变"))
    }

    @Test fun retryLoadingBlocksEditingUntilCurrentWardrobeSelectionIsResolved() = runBlocking {
        val task = backgroundTask().copy(id = "retry-loading", status = com.lolita.app.domain.model.ImageTaskStatus.FAILED)
        val pending = CompletableDeferred<ImageTask?>()
        retryReader = { pending.await() }
        val handle = SavedStateHandle(mapOf("retryTaskId" to "retry-loading"))
        val vm = withContext(Dispatchers.Main) {
            TryOnViewModel(handle, items = database.itemDao().getAllItems(), categories = database.categoryDao().getAllCategories(),
                results = repository.results, readService = { com.lolita.app.data.tryon.ImageServiceStatus(com.lolita.app.domain.model.ImageGenerationProvider.OPENAI, true) },
                selectService = {}, serviceChanges = kotlinx.coroutines.flow.flowOf(0), saveApiKey = { _, _ -> }, clearApiKey = {},
                tasks = tasks, readRetryTask = { retryReader(it) }, submitTask = { _, _ -> error("no paid work") }, renameResult = { _, _ -> }, deleteResult = {})
                .also { store.put("loading", it) }
        }
        withTimeout(10_000) { vm.uiState.first { it.items.isNotEmpty() && it.keyLoaded } }
        assertTrue("重试参考资料尚未读取完成时应保持加载态", vm.uiState.value.isLoading)
        main { vm.toggleItem(12) }
        assertTrue(vm.uiState.value.selected.isEmpty())
        pending.complete(task)
        withTimeout(10_000) { vm.uiState.first { !it.isLoading } }
        assertEquals(listOf(11L), vm.uiState.value.selected.map { it.itemId })
    }

    @Test fun retryLoadsCurrentImagesAndRolesButNeverAutomaticallySubmits() = runBlocking {
        val original = backgroundTask().copy(id = "retry-task", sourceImagePaths = emptyList(), status = com.lolita.app.domain.model.ImageTaskStatus.FAILED)
        database.imageTaskDao().insert(original)
        database.itemDao().updateItem(item(11, "改名后的月光裙", 1, listOf("/current.png")))
        val vm = model(SavedStateHandle(mapOf("retryTaskId" to "retry-task")))
        assertEquals("/current.png", vm.uiState.value.selected.single().imagePath)
        assertEquals("改名后的月光裙", vm.uiState.value.selected.single().name)
        assertEquals(TryOnRole.MAIN, vm.uiState.value.selected.single().role)
        assertEquals(0, requests)
        assertEquals(com.lolita.app.domain.model.ImageTaskStatus.FAILED, database.imageTaskDao().get("retry-task")!!.status)
    }

    @Test fun stableAccessorySemanticsDoNotGuessRolesFromNamesAndSpecificGarmentCategoriesRemainAuthoritative() = runBlocking {
        database.categoryDao().insertCategory(Category(id = 3, name = "其他配饰", group = CategoryGroup.ACCESSORY, categorySemantic = CategorySemantic.OTHER_ACCESSORY))
        database.itemDao().insertItem(item(21, "蕾丝手袖", 3, listOf("/hand.png")))
        database.itemDao().insertItem(item(22, "链条腰带", 3, listOf("/waist.png")))
        database.itemDao().insertItem(item(23, "手绘花鸟裙", 1, listOf("/dress.png")))
        val vm = model()
        main { vm.toggleItem(21); vm.toggleItem(22); vm.toggleItem(23) }
        assertEquals(listOf(TryOnRole.OTHER, TryOnRole.OTHER, TryOnRole.MAIN), vm.uiState.value.selected.map { it.role })
    }

    @Test fun failedSubmissionKeepsDraftAndNeverAutomaticallyRetries() = runBlocking {
        operation = { error("受控服务失败") }
        val vm = model()
        main { vm.toggleItem(11); vm.generate() }
        withTimeout(10_000) { vm.uiState.first { !it.isSubmitting && it.error != null } }
        assertEquals(1, requests)
        assertEquals(listOf(11L), vm.uiState.value.selected.map { it.itemId })
    }

    @Test fun personalChoiceSubmitsCapturedModelAndKeepsGarmentsWhenSwitching() = runBlocking {
        val file = File(directory, "accepted.png").also { image ->
            android.graphics.Bitmap.createBitmap(32, 48, android.graphics.Bitmap.Config.ARGB_8888).also { bitmap ->
                image.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            }
        }
        val request = com.lolita.app.domain.model.PersonalModelRequest("小月", com.lolita.app.domain.model.PersonalModelMode.PARAMETERS,
            com.lolita.app.domain.model.BodyMeasurements(165.0, 52.0))
        val accepted = PersonalModel(id = 91, name = "小月", requestJson = request.encode(), sourceImagePaths = emptyList(),
            imageUrl = file.absolutePath, provider = com.lolita.app.domain.model.ImageGenerationProvider.OPENAI, model = "test")
        personalModels.value = listOf(accepted)
        defaultModel.value = 91
        val handle = SavedStateHandle()
        val vm = model(handle)
        assertEquals(91L, vm.uiState.value.selectedPersonalModelId)
        main { vm.toggleItem(11); vm.selectPersonalModel(null); vm.selectPersonalModel(91) }
        assertEquals(listOf(11L), vm.uiState.value.selected.map { it.itemId })
        defaultModel.value = null
        val restored = model(handle)
        assertEquals(91L, restored.uiState.value.selectedPersonalModelId)
        main { restored.generate() }
        withTimeout(10_000) { restored.uiState.first { it.latestTaskId != null } }
        assertEquals(91L, submittedModelId)
        assertEquals(1, personalRequests)
        assertEquals(0, requests)
        file.delete()
        assertFalse(restored.uiState.value.canGenerate)
        main { restored.generate() }
        assertEquals(1, personalRequests)
    }

    @Test fun sameIdRestoredWithDifferentImageRejectsStaleConfirmation() = runBlocking {
        val originalPath = File(directory, "original.png").apply { writeBytes(byteArrayOf(1)) }.absolutePath
        val replacedPath = File(directory, "restored.png").apply { writeBytes(byteArrayOf(2)) }.absolutePath
        val request = com.lolita.app.domain.model.PersonalModelRequest("旧人物", com.lolita.app.domain.model.PersonalModelMode.PARAMETERS,
            com.lolita.app.domain.model.BodyMeasurements(165.0, 52.0))
        val original = PersonalModel(id = 91, name = "旧人物", requestJson = request.encode(), sourceImagePaths = emptyList(),
            imageUrl = originalPath, provider = com.lolita.app.domain.model.ImageGenerationProvider.OPENAI, model = "test")
        personalModels.value = listOf(original); defaultModel.value = 91
        val vm = model()
        main { vm.toggleItem(11) }
        personalModels.value = listOf(original.copy(name = "恢复后其他人物", imageUrl = replacedPath))
        withTimeout(10_000) { vm.uiState.first { it.selectedPersonalModel?.imageUrl == replacedPath } }
        main { vm.generate(com.lolita.app.domain.model.ImageGenerationProvider.OPENAI, 91, originalPath) }
        assertEquals(0, personalRequests + requests)
        assertTrue(vm.uiState.value.error!!.contains("底图已改变"))
    }

    @Test fun modelAndDefaultMustBothLoadBeforeGenerationCanStart() = runBlocking {
        val models = MutableSharedFlow<List<PersonalModel>>(replay = 1)
        val defaults = MutableSharedFlow<Long?>(replay = 1)
        val vm = model(models = models, defaults = defaults, waitModels = false)
        main { vm.toggleItem(11); vm.generate() }
        assertFalse(vm.uiState.value.canGenerate)
        assertEquals(0, requests)
        models.emit(emptyList())
        instrumentation.waitForIdleSync()
        assertFalse(vm.uiState.value.canGenerate)
        defaults.emit(null)
        withTimeout(10_000) { vm.uiState.first { it.modelsLoaded } }
        assertTrue(vm.uiState.value.canGenerate)
    }

    @Test fun deletedCapturedPersonalRetryRequiresExplicitReselectionAndNeverFallsBack() = runBlocking {
        val original = backgroundTask().copy(id = "personal-retry", status = com.lolita.app.domain.model.ImageTaskStatus.FAILED,
            requestJson = com.lolita.app.domain.model.ImageTaskRequest(
                listOf(com.lolita.app.domain.model.ImageTaskGarment(11, "裙子", TryOnRole.MAIN)), version = 2,
                tryOnModel = com.lolita.app.domain.model.TryOnModelSnapshot(91, "原人物")).encode())
        database.imageTaskDao().insert(original)
        val vm = model(SavedStateHandle(mapOf("retryTaskId" to original.id)))
        assertEquals(91L, vm.uiState.value.selectedPersonalModelId)
        assertFalse(vm.uiState.value.canGenerate)
        main { vm.generate() }
        assertEquals(0, requests + personalRequests)
        assertTrue(vm.uiState.value.modelIssue!!.contains("重新选择"))
        main { vm.selectPersonalModel(null) }
        assertTrue(vm.uiState.value.canGenerate)
        assertEquals(listOf(11L), vm.uiState.value.selected.map { it.itemId })
    }

    @Test fun explicitBuiltinChoiceSurvivesRecreationAndDefaultChanges() = runBlocking {
        defaultModel.value = 404
        val handle = SavedStateHandle()
        val vm = model(handle)
        assertNull(vm.uiState.value.selectedPersonalModelId)
        main { vm.toggleItem(11); vm.selectPersonalModel(null) }
        defaultModel.value = 405
        val restored = model(handle)
        assertNull(restored.uiState.value.selectedPersonalModelId)
        assertEquals(listOf(11L), restored.uiState.value.selected.map { it.itemId })
        main { restored.generate() }
        withTimeout(10_000) { restored.uiState.first { it.latestTaskId != null } }
        assertEquals(1, requests)
        assertEquals(0, personalRequests)
    }

    private fun backgroundTask() = ImageTask("running", com.lolita.app.domain.model.ImageTaskKind.TRY_ON,
        com.lolita.app.domain.model.ImageGenerationProvider.OPENAI, "后台任务",
        com.lolita.app.domain.model.ImageTaskRequest(listOf(com.lolita.app.domain.model.ImageTaskGarment(11, "裙子", TryOnRole.MAIN))).encode(),
        listOf("/first.png"), status = com.lolita.app.domain.model.ImageTaskStatus.GENERATING)
}
