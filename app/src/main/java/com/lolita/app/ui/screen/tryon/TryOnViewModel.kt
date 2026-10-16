package com.lolita.app.ui.screen.tryon

import com.lolita.app.data.tryon.ImageServiceStatus
import com.lolita.app.domain.model.ImageGenerationProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.local.entity.TryOnResult
import com.lolita.app.data.local.entity.ImageTask
import com.lolita.app.data.local.entity.PersonalModel
import java.io.File
import kotlinx.coroutines.flow.flowOf
import com.lolita.app.domain.model.ImageTaskRequest
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.TryOnRequest
import com.lolita.app.domain.model.TryOnRole
import com.lolita.app.domain.model.TryOnSelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TryOnUiState(
    val items: List<Item> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selected: List<TryOnSelection> = emptyList(),
    val results: List<TryOnResult> = emptyList(),
    val searchQuery: String = "",
    val selectedCategoryId: Long? = null,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val historyFailed: Boolean = false,
    val provider: ImageGenerationProvider = ImageGenerationProvider.SEEDREAM,
    val hasKey: Boolean = false,
    val keyLoaded: Boolean = false,
    val keyRevision: Int = 0,
    val isConfiguring: Boolean = false,
    val isSubmitting: Boolean = false,
    val latestTaskId: String? = null,
    val latestTask: ImageTask? = null,
    val error: String? = null,
    val personalModels: List<PersonalModel> = emptyList(),
    val selectedPersonalModelId: Long? = null,
    val modelsLoaded: Boolean = false
) {
    val busy: Boolean get() = isSubmitting || isConfiguring
    val visibleItems: List<Item> get() = items.filter { item ->
        (selectedCategoryId == null || item.categoryId == selectedCategoryId) &&
            (searchQuery.isBlank() || item.name.contains(searchQuery.trim(), ignoreCase = true))
    }
    val availableCategories: List<Category> get() = categories.filter { category -> items.any { it.categoryId == category.id } }
    val selectionIssue: String? get() {
        if (selected.isEmpty()) return "请选择衣橱里的服饰与小物"
        if (selected.any { row -> items.none { it.id == row.itemId } }) return "已选服饰已不在衣橱，请移除后重新选择"
        if (selected.any { row -> row.imagePath.isBlank() || items.firstOrNull { it.id == row.itemId }?.imageUrls?.contains(row.imagePath) != true }) {
            return "请为每件服饰选择已有参考图片"
        }
        return runCatching { TryOnRequest(selected) }.exceptionOrNull()?.message
    }
    val selectedPersonalModel: PersonalModel? get() = personalModels.firstOrNull { it.id == selectedPersonalModelId }
    val modelName: String get() = selectedPersonalModel?.name ?: if (selectedPersonalModelId == null) "内置虚拟模特" else "已删除的个人模特"
    val modelIssue: String? get() = when {
        !modelsLoaded -> "正在读取模特资料…"
        selectedPersonalModelId != null && selectedPersonalModel == null -> "原个人模特已删除，请重新选择人物后再生成"
        selectedPersonalModel != null && !File(selectedPersonalModel!!.imageUrl).isFile -> "个人模特底图已丢失，请重新选择或调整人物"
        else -> null
    }
    val canGenerate: Boolean get() = modelIssue == null && !busy && !isLoading && !loadFailed && keyLoaded && hasKey && selectionIssue == null
}

/** The function boundaries isolate personal credentials and the paid request from UI/Room tests. */
class TryOnViewModel @JvmOverloads constructor(
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    items: Flow<List<Item>> = AppModule.itemRepository().getItemsByStatus(ItemStatus.OWNED),
    categories: Flow<List<Category>> = AppModule.categoryRepository().getAllCategories(),
    results: Flow<List<TryOnResult>> = AppModule.tryOnRepository().results,
    private val readService: suspend () -> ImageServiceStatus = { AppModule.tryOnSettings().readStatus() },
    private val selectService: suspend (ImageGenerationProvider) -> Unit = { AppModule.tryOnSettings().selectProvider(it) },
    serviceChanges: Flow<Int> = AppModule.tryOnSettings().changes,
    private val saveApiKey: suspend (ImageGenerationProvider, String) -> Unit = { provider, value -> AppModule.tryOnSettings().saveKey(value, provider) },
    private val clearApiKey: suspend (ImageGenerationProvider) -> Unit = { AppModule.tryOnSettings().clearKey(it) },
    tasks: Flow<List<ImageTask>> = AppModule.imageTaskRepository().tasks,
    private val readRetryTask: suspend (String) -> ImageTask? = { AppModule.imageTaskRepository().get(it) },
    private val submitTask: suspend (List<TryOnSelection>, ImageGenerationProvider) -> String = { rows, provider ->
        AppModule.imageTaskCoordinator().submitTryOn(rows, provider)
    },
    private val renameResult: suspend (Long, String) -> Unit = { id, name -> AppModule.tryOnRepository().rename(id, name) },
    private val deleteResult: suspend (Long) -> Unit = { AppModule.tryOnRepository().delete(it) },
    personalModels: Flow<List<PersonalModel>> = flowOf(emptyList()),
    defaultPersonalModelId: Flow<Long?> = flowOf(null),
    private val submitPersonalTask: suspend (List<TryOnSelection>, ImageGenerationProvider, Long, String) -> String = { rows, provider, id, path ->
        AppModule.imageTaskCoordinator().submitTryOn(rows, provider, id, path)
    }

) : ViewModel() {
    private val gson = Gson()
    private val _uiState = MutableStateFlow(TryOnUiState(selected = restoreSelection(), latestTaskId = savedStateHandle[LAST_TASK], selectedPersonalModelId = savedStateHandle[MODEL_ID]))
    val uiState: StateFlow<TryOnUiState> = _uiState.asStateFlow()
    private var observedTasks = emptyList<ImageTask>()
    private var entryConsumed = savedStateHandle.get<Boolean>(ENTRY_CONSUMED) == true || savedStateHandle.contains(SELECTION)

    init {
        viewModelScope.launch {
            combine(personalModels, defaultPersonalModelId) { models, defaultId -> models to defaultId }
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                    _uiState.update { it.copy(modelsLoaded = false, error = "模特资料读取失败，请返回后重试") }
                }.collect { (models, defaultId) ->
                    val chosen = savedStateHandle.get<Boolean>(MODEL_CHOSEN) == true
                    val id = if (chosen) _uiState.value.selectedPersonalModelId else defaultId?.takeIf { candidate -> models.any { it.id == candidate } }
                    _uiState.update { it.copy(personalModels = models, selectedPersonalModelId = id, modelsLoaded = true) }
                }
        }
        viewModelScope.launch {
            combine(items, categories) { rows, groups -> rows.filter { it.status == ItemStatus.OWNED } to groups }
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                    _uiState.update { it.copy(isLoading = false, loadFailed = true, error = "衣橱读取失败，请返回后重试") }
                }.collect { (rows, groups) ->
                    _uiState.update { it.copy(items = rows, categories = groups, isLoading = !entryConsumed) }
                    if (!entryConsumed) {
                        val itemId = savedStateHandle.get<Long>("itemId")?.takeIf { it > 0 }
                        val coordinateId = savedStateHandle.get<Long>("coordinateId")?.takeIf { it > 0 }
                        val parents = rows.filter { it.coordinateId == coordinateId && coordinateId != null }.map { it.id }.toSet()
                        val retry = savedStateHandle.get<String>("retryTaskId")?.takeIf { it.isNotBlank() }?.let { id ->
                            runCatching { readRetryTask(id)?.let { ImageTaskRequest.decode(it.requestJson) } }.getOrNull()
                        }
                        if (retry != null && savedStateHandle.get<Boolean>(MODEL_CHOSEN) != true) {
                            savedStateHandle[MODEL_CHOSEN] = true
                            savedStateHandle[MODEL_ID] = retry.tryOnModel?.personalModelId
                            _uiState.update { it.copy(selectedPersonalModelId = retry.tryOnModel?.personalModelId) }
                        }
                        entryConsumed = true
                        savedStateHandle[ENTRY_CONSUMED] = true
                        val initial = when {
                            retry != null -> retry.garments.mapNotNull { garment -> rows.firstOrNull { it.id == garment.itemId } }
                            itemId != null -> rows.filter { it.id == itemId }
                            coordinateId != null -> rows.filter { it.id in parents || it.parentItemId in parents }
                            else -> emptyList()
                        }
                        setSelection(initial.take(8).map { item -> selection(item).let { selected ->
                            retry?.garments?.firstOrNull { it.itemId == item.id }?.let { selected.copy(role = it.role) } ?: selected
                        } })
                        if (retry != null) _uiState.update { it.copy(error = if (initial.size != retry.garments.size) "部分原服饰已删除或不在已拥有衣橱，请重新选择并核对；原任务保留" else "已载入当前服饰图片，请核对后再次确认生成；原任务保留") }
                        if (initial.size > 8) _uiState.update { it.copy(error = "此套装超过8件，已预选前8件，请核对后生成") }
                        if (itemId != null && initial.isEmpty()) _uiState.update { it.copy(error = "这件服饰已不在已拥有衣橱，请重新选择") }
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }
        }
        viewModelScope.launch {
            results.catch { failure ->
                if (failure is CancellationException) throw failure
                _uiState.update { it.copy(historyFailed = true, error = "试衣历史读取失败，请返回后重试") }
            }.collect { rows -> _uiState.update { it.copy(results = rows) } }
        }
        viewModelScope.launch { tasks.collect { rows -> observedTasks = rows; _uiState.update { it.copy(latestTask = rows.firstOrNull { task -> task.id == it.latestTaskId }) } } }
        viewModelScope.launch {
            serviceChanges.collect {
                _uiState.update { it.copy(keyLoaded = false) }
                try {
                    val status = withContext(Dispatchers.IO) { readService() }
                    _uiState.update { it.copy(provider = status.provider, hasKey = status.hasKey, keyLoaded = true) }
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    _uiState.update { it.copy(hasKey = false, keyLoaded = true, error = "图像服务配置读取失败，请重新选择服务") }
                }
            }
        }
    }

    private fun restoreSelection(): List<TryOnSelection> = runCatching {
        val json = savedStateHandle.get<String>(SELECTION) ?: return emptyList()
        val type = object : TypeToken<List<TryOnSelection>>() {}.type
        gson.fromJson<List<TryOnSelection>>(json, type).orEmpty().filter {
            it.itemId > 0 && it.name.isNotBlank()
        }.distinctBy { it.itemId }.take(8)
    }.getOrDefault(emptyList())

    private fun selection(item: Item): TryOnSelection {
        val category = _uiState.value.categories.firstOrNull { it.id == item.categoryId }?.name.orEmpty()
        val categoryRole = TryOnRole.suggest(category)
        val role = if (categoryRole == TryOnRole.OTHER) TryOnRole.suggest(item.name) else categoryRole
        return TryOnSelection(item.id, item.name, item.imageUrls.firstOrNull { it.isNotBlank() }.orEmpty(), role)
    }

    private fun setSelection(rows: List<TryOnSelection>) {
        savedStateHandle[SELECTION] = gson.toJson(rows)
        _uiState.update { it.copy(selected = rows, error = null) }
    }

    fun selectPersonalModel(id: Long?) {
        val state = _uiState.value
        if (state.busy || state.isLoading || !state.modelsLoaded) return
        if (id != null && state.personalModels.none { it.id == id }) return
        savedStateHandle[MODEL_CHOSEN] = true
        savedStateHandle[MODEL_ID] = id
        _uiState.update { it.copy(selectedPersonalModelId = id, error = null) }
    }

    fun updateSearchQuery(value: String) { _uiState.update { if (it.busy) it else it.copy(searchQuery = value) } }
    fun selectCategory(value: Long?) { _uiState.update { if (it.busy) it else it.copy(selectedCategoryId = value) } }
    fun toggleItem(itemId: Long) {
        val state = _uiState.value
        if (state.busy || state.isLoading || state.loadFailed) return
        if (state.selected.any { it.itemId == itemId }) return setSelection(state.selected.filterNot { it.itemId == itemId })
        val item = state.items.firstOrNull { it.id == itemId } ?: return
        if (state.selected.size >= 8) { _uiState.update { it.copy(error = "每次最多选择8件服饰与小物") }; return }
        setSelection(state.selected + selection(item))
    }

    fun setImage(itemId: Long, path: String) {
        val state = _uiState.value
        if (state.busy || state.items.firstOrNull { it.id == itemId }?.imageUrls?.contains(path) != true || path.isBlank()) return
        setSelection(state.selected.map { if (it.itemId == itemId) it.copy(imagePath = path) else it })
    }

    fun setRole(itemId: Long, role: TryOnRole) {
        if (_uiState.value.busy) return
        setSelection(_uiState.value.selected.map { if (it.itemId == itemId) it.copy(role = role) else it })
    }

    fun selectProvider(provider: ImageGenerationProvider) {
        if (_uiState.value.busy || !_uiState.value.keyLoaded || provider == _uiState.value.provider) return
        configureService { selectService(provider) }
    }
    fun saveKey(value: String) {
        if (_uiState.value.busy || !_uiState.value.keyLoaded) return
        if (value.isBlank()) { _uiState.update { it.copy(error = "请填写 API Key") }; return }
        val provider = _uiState.value.provider
        configureService { saveApiKey(provider, value) }
    }
    fun clearKey() {
        if (_uiState.value.busy || !_uiState.value.keyLoaded) return
        val provider = _uiState.value.provider
        configureService { clearApiKey(provider) }
    }
    private fun configureService(operation: suspend () -> Unit) {
        _uiState.update { it.copy(isConfiguring = true, error = null) }
        viewModelScope.launch {
            try {
                val status = withContext(Dispatchers.IO) { operation(); readService() }
                _uiState.update { it.copy(provider = status.provider, hasKey = status.hasKey, keyLoaded = true, keyRevision = it.keyRevision + 1) }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                _uiState.update { it.copy(error = "图像服务配置失败，请重新输入后重试") }
            } finally { _uiState.update { it.copy(isConfiguring = false) } }
        }
    }
    fun generate(confirmedProvider: ImageGenerationProvider = _uiState.value.provider,
        confirmedPersonalModelId: Long? = _uiState.value.selectedPersonalModelId,
        confirmedModelImagePath: String? = _uiState.value.selectedPersonalModel?.imageUrl) {
        val state = _uiState.value
        if (state.busy || state.isLoading || state.loadFailed || !state.keyLoaded) return
        if (confirmedProvider != state.provider) {
            _uiState.update { it.copy(error = "图像服务已改变，请核对后重新确认") }; return
        }
        if (state.selectedPersonalModelId != confirmedPersonalModelId ||
            state.selectedPersonalModel?.imageUrl != confirmedModelImagePath) {
            _uiState.update { it.copy(error = "试衣人物或底图已改变，请重新选择并核对后生成") }; return
        }
        if (!state.hasKey) { _uiState.update { it.copy(error = "请先配置个人 API Key") }; return }
        state.modelIssue?.let { issue -> _uiState.update { it.copy(error = issue) }; return }
        state.selectionIssue?.let { issue -> _uiState.update { it.copy(error = issue) }; return }
        val snapshot = state.selected.toList()
        val selectedModelId = state.selectedPersonalModelId
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            try {
                // Coordinator accepts in application scope before this screen's waiter can disappear.
                val id = if (selectedModelId == null) submitTask(snapshot, confirmedProvider)
                    else submitPersonalTask(snapshot, confirmedProvider, selectedModelId, requireNotNull(confirmedModelImagePath))
                savedStateHandle[LAST_TASK] = id
                _uiState.update { it.copy(latestTaskId = id, latestTask = observedTasks.firstOrNull { row -> row.id == id }) }
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { _uiState.update { it.copy(error = "任务提交失败，请检查服饰与服务配置后重试") } }
            finally { _uiState.update { it.copy(isSubmitting = false) } }
        }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }
    fun rename(id: Long, name: String) {
        if (name.isBlank() || name.trim().length > 120) { _uiState.update { it.copy(error = "请填写120字以内的名称") }; return }
        viewModelScope.launch {
            try { renameResult(id, name.trim()) }
            catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                _uiState.update { it.copy(error = "重命名失败，请重试") }
            }
        }
    }
    fun delete(id: Long) {
        viewModelScope.launch {
            try { deleteResult(id) }
            catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                _uiState.update { it.copy(error = "删除试衣记录失败，请重试") }
            }
        }
    }

    companion object {
        private const val MODEL_ID = "tryOnPersonalModelId"
        private const val MODEL_CHOSEN = "tryOnPersonalModelChosen"
        private const val LAST_TASK = "tryOnLatestTaskId"
        private const val SELECTION = "tryOnSelections"
        private const val ENTRY_CONSUMED = "tryOnEntryConsumed"
    }
}
