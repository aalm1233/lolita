package com.lolita.app.ui.screen.outfit

import com.lolita.app.ui.screen.common.uiErrorText

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.local.entity.OutfitLog
import com.lolita.app.data.repository.BrandRepository
import com.lolita.app.data.repository.CategoryRepository
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.OutfitLogRepository
import com.lolita.app.di.AppModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuickOutfitLogUiState(
    val items: List<Item> = emptyList(),
    val brandNames: Map<Long, String> = emptyMap(),
    val categories: List<Category> = emptyList(),
    val searchQuery: String = "",
    val selectedCategoryId: Long? = null,
    val selectedItemIds: Set<Long> = emptySet(),
    val existingLogItems: List<Item> = emptyList(),
    val note: String = "",
    val showNote: Boolean = false,
    val existingLogId: Long? = null,
    val isLoadingItems: Boolean = true,
    val isLoadingTodayLog: Boolean = true,
    val loadFailed: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
) {
    val isLoading: Boolean get() = isLoadingItems || isLoadingTodayLog

    val availableCategories: List<Category>
        get() {
            val usedIds = items.map { it.categoryId }.toSet()
            return categories.filter { it.id in usedIds }
        }

    val visibleItems: List<Item>
        get() {
            val query = searchQuery.trim()
            return items.filter { item ->
                (selectedCategoryId == null || item.categoryId == selectedCategoryId) &&
                    (query.isEmpty() || item.name.contains(query, ignoreCase = true) ||
                        brandNames[item.brandId]?.contains(query, ignoreCase = true) == true)
            }
        }

    // Selection stays independent of the filtered grid. Existing log items can
    // still be removed from the preview even when their status is no longer OWNED.
    val selectedItems: List<Item>
        get() {
            val byId = (existingLogItems + items).associateBy { it.id }
            return selectedItemIds.mapNotNull { byId[it] }
        }
}

class QuickOutfitLogViewModel(
    private val outfitLogRepository: OutfitLogRepository = AppModule.outfitLogRepository(),
    private val itemRepository: ItemRepository = AppModule.itemRepository(),
    private val brandRepository: BrandRepository = AppModule.brandRepository(),
    private val categoryRepository: CategoryRepository = AppModule.categoryRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuickOutfitLogUiState())
    val uiState: StateFlow<QuickOutfitLogUiState> = _uiState.asStateFlow()
    private var existingLog: OutfitLog? = null

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                itemRepository.getItemsByStatus(ItemStatus.OWNED),
                brandRepository.getAllBrands(),
                categoryRepository.getAllCategories()
            ) { items, brands, categories ->
                Triple(items, brands.associate { it.id to it.name }, categories)
            }.catch { error ->
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(isLoadingItems = false, loadFailed = true, error = "衣橱读取失败，请返回后重试")
                }
            }.collect { (items, brandNames, categories) ->
                _uiState.update { state ->
                    state.copy(
                        items = items,
                        brandNames = brandNames,
                        categories = categories,
                        selectedCategoryId = state.selectedCategoryId?.takeIf { id ->
                            categories.any { it.id == id } && items.any { it.categoryId == id }
                        },
                        isLoadingItems = false
                    )
                }
            }
        }
        viewModelScope.launch {
            try {
                val todayLog = outfitLogRepository.getTodayOutfitLog()
                existingLog = todayLog?.outfitLog
                if (todayLog != null) {
                    _uiState.update {
                        it.copy(
                            existingLogId = todayLog.outfitLog.id,
                            existingLogItems = todayLog.items,
                            selectedItemIds = todayLog.items.map { item -> item.id }.toSet(),
                            note = todayLog.outfitLog.note,
                            showNote = todayLog.outfitLog.note.isNotBlank()
                        )
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                android.util.Log.w("QuickOutfitLogVM", "Failed to load today's outfit log", error)
                _uiState.update {
                    it.copy(loadFailed = true, error = "今日记录读取失败，请返回后重试")
                }
            } finally {
                _uiState.update { it.copy(isLoadingTodayLog = false) }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { if (it.isSaving) it else it.copy(searchQuery = query) }
    }

    fun selectCategory(categoryId: Long?) {
        _uiState.update { if (it.isSaving) it else it.copy(selectedCategoryId = categoryId) }
    }

    fun clearFilters() {
        _uiState.update {
            if (it.isSaving) it else it.copy(searchQuery = "", selectedCategoryId = null)
        }
    }

    fun toggleItem(itemId: Long) {
        _uiState.update {
            if (it.isSaving || it.isLoading || it.loadFailed) return@update it
            if (itemId !in it.selectedItemIds && it.items.none { item -> item.id == itemId }) return@update it
            val newSet = it.selectedItemIds.toMutableSet()
            if (itemId in newSet) newSet.remove(itemId) else newSet.add(itemId)
            it.copy(selectedItemIds = newSet, error = null)
        }
    }

    fun updateNote(note: String) {
        _uiState.update { if (it.isSaving) it else it.copy(note = note) }
    }

    fun toggleShowNote() {
        _uiState.update { if (it.isSaving) it else it.copy(showNote = !it.showNote) }
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading || state.loadFailed || state.saveSuccess) return
        if (state.selectedItemIds.isEmpty()) {
            _uiState.update { it.copy(error = "请至少选择一件物品") }
            return
        }
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val today = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 12)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis

                val log = existingLog
                if (log != null) {
                    // This shortcut only changes clothing and the note. Keep
                    // existing photos, date and creation time intact.
                    outfitLogRepository.updateOutfitLogWithItems(
                        log.copy(note = state.note), state.selectedItemIds.toList()
                    )
                } else {
                    outfitLogRepository.insertOutfitLogWithItems(
                        OutfitLog(date = today, note = state.note), state.selectedItemIds.toList()
                    )
                }
                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(isSaving = false, error = uiErrorText(error, "保存失败，请重试")) }
            }
        }
    }
}
