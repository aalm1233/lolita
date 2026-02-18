package com.lolita.app.ui.screen.outfit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.local.entity.OutfitLog
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.OutfitLogRepository
import com.lolita.app.di.AppModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuickOutfitLogUiState(
    val items: List<Item> = emptyList(),
    val selectedItemIds: Set<Long> = emptySet(),
    val note: String = "",
    val showNote: Boolean = false,
    val existingLogId: Long? = null,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
)

class QuickOutfitLogViewModel(
    private val outfitLogRepository: OutfitLogRepository = AppModule.outfitLogRepository(),
    private val itemRepository: ItemRepository = AppModule.itemRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuickOutfitLogUiState())
    val uiState: StateFlow<QuickOutfitLogUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            itemRepository.getItemsByStatus(ItemStatus.OWNED).collect { items ->
                _uiState.update { it.copy(items = items) }
            }
        }
        viewModelScope.launch {
            val todayLog = outfitLogRepository.getTodayOutfitLog()
            if (todayLog != null) {
                _uiState.update {
                    it.copy(
                        existingLogId = todayLog.outfitLog.id,
                        selectedItemIds = todayLog.items.map { item -> item.id }.toSet(),
                        note = todayLog.outfitLog.note
                    )
                }
            }
        }
    }

    fun toggleItem(itemId: Long) {
        _uiState.update {
            val newSet = it.selectedItemIds.toMutableSet()
            if (itemId in newSet) newSet.remove(itemId) else newSet.add(itemId)
            it.copy(selectedItemIds = newSet)
        }
    }

    fun updateNote(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun toggleShowNote() {
        _uiState.update { it.copy(showNote = !it.showNote) }
    }

    fun save() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val state = _uiState.value
                if (state.selectedItemIds.isEmpty()) {
                    _uiState.update { it.copy(isSaving = false, error = "请至少选择一件物品") }
                    return@launch
                }
                val today = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 12)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis

                if (state.existingLogId != null) {
                    val log = OutfitLog(
                        id = state.existingLogId,
                        date = today,
                        note = state.note
                    )
                    outfitLogRepository.updateOutfitLogWithItems(
                        log, state.selectedItemIds.toList()
                    )
                } else {
                    val log = OutfitLog(date = today, note = state.note)
                    outfitLogRepository.insertOutfitLogWithItems(
                        log, state.selectedItemIds.toList()
                    )
                }
                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }
}
