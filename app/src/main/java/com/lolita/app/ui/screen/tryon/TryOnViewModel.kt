package com.lolita.app.ui.screen.tryon

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.local.entity.TryOnResult
import com.lolita.app.data.tryon.TryOnGenerationPhase
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.TryOnRequest
import com.lolita.app.domain.model.TryOnRole
import com.lolita.app.domain.model.TryOnSelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    val hasKey: Boolean = false,
    val keyLoaded: Boolean = false,
    val keyRevision: Int = 0,
    val isConfiguring: Boolean = false,
    val isGenerating: Boolean = false,
    val globalGenerating: Boolean = false,
    val phase: TryOnGenerationPhase? = null,
    val latestResultId: Long? = null,
    val error: String? = null
) {
    val busy: Boolean get() = isGenerating || globalGenerating || isConfiguring
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
    val canGenerate: Boolean get() = !busy && !isLoading && !loadFailed && keyLoaded && hasKey && selectionIssue == null
}

/** The function boundaries isolate personal credentials and the paid request from UI/Room tests. */
class TryOnViewModel @JvmOverloads constructor(
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    items: Flow<List<Item>> = AppModule.itemRepository().getItemsByStatus(ItemStatus.OWNED),
    categories: Flow<List<Category>> = AppModule.categoryRepository().getAllCategories(),
    results: Flow<List<TryOnResult>> = AppModule.tryOnRepository().results,
    private val hasApiKey: suspend () -> Boolean = { AppModule.tryOnSettings().hasKey() },
    private val saveApiKey: suspend (String) -> Unit = { AppModule.tryOnSettings().saveKey(it) },
    private val clearApiKey: suspend () -> Unit = { AppModule.tryOnSettings().clearKey() },
    globalGenerating: StateFlow<Boolean> = AppModule.tryOnGenerator().isGenerating,
    private val generateImage: suspend (List<TryOnSelection>, (TryOnGenerationPhase) -> Unit) -> Long = { rows, phase ->
        AppModule.tryOnGenerator().generate(rows, phase)
    },
    private val renameResult: suspend (Long, String) -> Unit = { id, name -> AppModule.tryOnRepository().rename(id, name) },
    private val deleteResult: suspend (Long) -> Unit = { AppModule.tryOnRepository().delete(it) }
) : ViewModel() {
    private val gson = Gson()
    private val _uiState = MutableStateFlow(TryOnUiState(selected = restoreSelection()))
    val uiState: StateFlow<TryOnUiState> = _uiState.asStateFlow()
    private var generationJob: Job? = null
    private var entryConsumed = savedStateHandle.get<Boolean>(ENTRY_CONSUMED) == true || savedStateHandle.contains(SELECTION)

    init {
        viewModelScope.launch {
            combine(items, categories) { rows, groups -> rows.filter { it.status == ItemStatus.OWNED } to groups }
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                    _uiState.update { it.copy(isLoading = false, loadFailed = true, error = "衣橱读取失败，请返回后重试") }
                }.collect { (rows, groups) ->
                    _uiState.update { it.copy(items = rows, categories = groups, isLoading = false) }
                    if (!entryConsumed) {
                        entryConsumed = true
                        savedStateHandle[ENTRY_CONSUMED] = true
                        val itemId = savedStateHandle.get<Long>("itemId")?.takeIf { it > 0 }
                        val coordinateId = savedStateHandle.get<Long>("coordinateId")?.takeIf { it > 0 }
                        val parents = rows.filter { it.coordinateId == coordinateId && coordinateId != null }.map { it.id }.toSet()
                        val initial = when {
                            itemId != null -> rows.filter { it.id == itemId }
                            coordinateId != null -> rows.filter { it.id in parents || it.parentItemId in parents }
                            else -> emptyList()
                        }
                        setSelection(initial.take(8).map { selection(it) })
                        if (initial.size > 8) _uiState.update { it.copy(error = "此套装超过8件，已预选前8件，请核对后生成") }
                        if (itemId != null && initial.isEmpty()) _uiState.update { it.copy(error = "这件服饰已不在已拥有衣橱，请重新选择") }
                    }
                }
        }
        viewModelScope.launch {
            results.catch { failure ->
                if (failure is CancellationException) throw failure
                _uiState.update { it.copy(historyFailed = true, error = "试衣历史读取失败，请返回后重试") }
            }.collect { rows -> _uiState.update { it.copy(results = rows) } }
        }
        viewModelScope.launch { globalGenerating.collect { value -> _uiState.update { it.copy(globalGenerating = value) } } }
        viewModelScope.launch {
            val configured = runCatching { withContext(Dispatchers.IO) { hasApiKey() } }.getOrDefault(false)
            _uiState.update { it.copy(hasKey = configured, keyLoaded = true) }
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

    fun saveKey(value: String) {
        if (_uiState.value.busy) return
        if (value.isBlank()) { _uiState.update { it.copy(error = "请填写 API Key") }; return }
        _uiState.update { it.copy(isConfiguring = true, error = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { saveApiKey(value) }
                _uiState.update { it.copy(hasKey = true, keyLoaded = true, keyRevision = it.keyRevision + 1) }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                _uiState.update { it.copy(error = "API Key 保存失败，请核对后重新输入") }
            } finally { _uiState.update { it.copy(isConfiguring = false) } }
        }
    }

    fun clearKey() {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(isConfiguring = true, error = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { clearApiKey() }
                _uiState.update { it.copy(hasKey = false, keyRevision = it.keyRevision + 1) }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                _uiState.update { it.copy(error = "密钥删除失败，请重试") }
            } finally { _uiState.update { it.copy(isConfiguring = false) } }
        }
    }

    fun generate() {
        val state = _uiState.value
        if (state.busy || state.isLoading || state.loadFailed || !state.keyLoaded) return
        if (!state.hasKey) { _uiState.update { it.copy(error = "请先配置个人 API Key") }; return }
        state.selectionIssue?.let { issue -> _uiState.update { it.copy(error = issue) }; return }
        val snapshot = state.selected.toList()
        _uiState.update { it.copy(isGenerating = true, phase = TryOnGenerationPhase.PREPARING, error = null) }
        generationJob = viewModelScope.launch {
            try {
                val id = generateImage(snapshot) { phase -> _uiState.update { it.copy(phase = phase) } }
                _uiState.update { it.copy(latestResultId = id) }
            } catch (failure: CancellationException) {
                _uiState.update { it.copy(error = "已取消等待；服务端可能仍在处理并计费，重试需主动点击") }
                throw failure
            } catch (failure: Exception) {
                // The transport provides bounded Chinese diagnostics; never show raw server bodies.
                val message = failure.message?.takeIf { text -> text.length <= 180 && text.any { it in '\u4e00'..'\u9fff' } && !text.contains("sk-") }
                _uiState.update { it.copy(error = message ?: "试衣生成失败，请检查图片与服务配置后主动重试") }
            } finally { _uiState.update { it.copy(isGenerating = false, phase = null) } }
        }
    }

    fun cancelGeneration() { generationJob?.cancel() }
    fun clearError() { _uiState.update { it.copy(error = null) } }
    fun consumeLatestResult() { _uiState.update { it.copy(latestResultId = null) } }
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
        private const val SELECTION = "tryOnSelections"
        private const val ENTRY_CONSUMED = "tryOnEntryConsumed"
    }
}
