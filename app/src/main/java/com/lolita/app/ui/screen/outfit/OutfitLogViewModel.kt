package com.lolita.app.ui.screen.outfit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.OutfitItemCrossRef
import com.lolita.app.data.local.entity.OutfitLog
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.OutfitLogRepository
import com.lolita.app.data.local.dao.OutfitLogWithItems
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class OutfitLogListUiState(
    val logs: List<OutfitLogListItem> = emptyList(),
    val isLoading: Boolean = true
)

data class OutfitLogListItem(
    val id: Long,
    val date: Long,
    val dateString: String,
    val previewNote: String,
    val imageCount: Int,
    val itemCount: Int,
    val firstImageUrl: String? = null
)

data class OutfitLogDetailUiState(
    val log: OutfitLog? = null,
    val items: List<Item> = emptyList(),
    val isLoading: Boolean = true
)

data class OutfitLogEditUiState(
    val date: Long? = null,
    val note: String = "",
    val imageUrls: List<String> = emptyList(),
    val selectedItemIds: Set<Long> = emptySet(),
    val availableItems: List<Item> = emptyList(),
    val isSaving: Boolean = false,
    val error: String? = null
)

/**
 * ViewModel for outfit log list screen
 */
class OutfitLogListViewModel(
    private val outfitLogRepository: OutfitLogRepository = com.lolita.app.di.AppModule.outfitLogRepository(),
    private val itemRepository: ItemRepository = com.lolita.app.di.AppModule.itemRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(OutfitLogListUiState())
    val uiState: StateFlow<OutfitLogListUiState> = _uiState.asStateFlow()

    init {
        loadOutfitLogs()
    }

    private fun loadOutfitLogs() {
        viewModelScope.launch {
            outfitLogRepository.getAllOutfitLogs()
                .combine(outfitLogRepository.getItemCountsByOutfitLog()) { logs, itemCounts ->
                    val countMap = itemCounts.associate { it.outfitLogId to it.itemCount }
                    val dateFormat = SimpleDateFormat("MM月dd日 EEEE", Locale.CHINA)
                    logs.map { log ->
                        OutfitLogListItem(
                            id = log.id,
                            date = log.date,
                            dateString = dateFormat.format(Date(log.date)),
                            previewNote = log.note.take(50) + if (log.note.length > 50) "..." else "",
                            imageCount = log.imageUrls.size,
                            itemCount = countMap[log.id] ?: 0,
                            firstImageUrl = log.imageUrls.firstOrNull()
                        )
                    }
                }
                .collect { listItems ->
                    _uiState.value = _uiState.value.copy(
                        logs = listItems,
                        isLoading = false
                    )
                }
        }
    }

    fun deleteOutfitLog(logId: Long) {
        viewModelScope.launch {
            val log = outfitLogRepository.getOutfitLogById(logId)
            log?.let {
                outfitLogRepository.deleteOutfitLog(it)
            }
        }
    }
}

/**
 * ViewModel for outfit log detail screen
 */
class OutfitLogDetailViewModel(
    private val outfitLogRepository: OutfitLogRepository = com.lolita.app.di.AppModule.outfitLogRepository(),
    private val logId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(OutfitLogDetailUiState())
    val uiState: StateFlow<OutfitLogDetailUiState> = _uiState.asStateFlow()

    init {
        loadOutfitLogDetail()
    }

    private fun loadOutfitLogDetail() {
        viewModelScope.launch {
            outfitLogRepository.getOutfitLogWithItems(logId).collect { result ->
                _uiState.value = _uiState.value.copy(
                    log = result?.outfitLog,
                    items = result?.items ?: emptyList(),
                    isLoading = false
                )
            }
        }
    }

    fun removeItem(itemId: Long) {
        viewModelScope.launch {
            outfitLogRepository.unlinkItemFromOutfitLog(logId, itemId)
        }
    }

    fun deleteOutfitLog() {
        viewModelScope.launch {
            val log = _uiState.value.log ?: return@launch
            outfitLogRepository.deleteOutfitLog(log)
        }
    }
}

/**
 * ViewModel for outfit log edit screen
 */
class OutfitLogEditViewModel(
    private val outfitLogRepository: OutfitLogRepository = com.lolita.app.di.AppModule.outfitLogRepository(),
    private val itemRepository: ItemRepository = com.lolita.app.di.AppModule.itemRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(OutfitLogEditUiState())
    val uiState: StateFlow<OutfitLogEditUiState> = _uiState.asStateFlow()

    private var editingLogId: Long? = null
    private var originalItemIds: Set<Long> = emptySet()

    init {
        loadAvailableItems()
    }

    private fun loadAvailableItems() {
        viewModelScope.launch {
            itemRepository.getAllItems().collect { items ->
                _uiState.value = _uiState.value.copy(
                    availableItems = items
                )
            }
        }
    }

    fun loadOutfitLog(logId: Long?) {
        editingLogId = logId
        if (logId == null) {
            // New outfit log - set today's date
            _uiState.value = _uiState.value.copy(
                date = System.currentTimeMillis()
            )
            return
        }

        viewModelScope.launch {
            val result = outfitLogRepository.getOutfitLogWithItems(logId).first()
            result?.let { data ->
                val itemIds = data.items.map { it.id }.toSet()
                originalItemIds = itemIds
                _uiState.value = OutfitLogEditUiState(
                    date = data.outfitLog.date,
                    note = data.outfitLog.note,
                    imageUrls = data.outfitLog.imageUrls,
                    selectedItemIds = itemIds,
                    availableItems = _uiState.value.availableItems
                )
            }
        }
    }

    fun updateDate(date: Long) {
        _uiState.value = _uiState.value.copy(date = date)
    }

    fun updateNote(note: String) {
        _uiState.value = _uiState.value.copy(note = note)
    }

    fun addImage(imageUrl: String) {
        _uiState.value = _uiState.value.copy(
            imageUrls = _uiState.value.imageUrls + imageUrl
        )
    }

    fun removeImage(imageUrl: String) {
        _uiState.value = _uiState.value.copy(
            imageUrls = _uiState.value.imageUrls - imageUrl
        )
    }

    fun toggleItemSelection(itemId: Long) {
        val currentSelection = _uiState.value.selectedItemIds
        val newSelection = if (currentSelection.contains(itemId)) {
            currentSelection - itemId
        } else {
            currentSelection + itemId
        }
        _uiState.value = _uiState.value.copy(selectedItemIds = newSelection)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    suspend fun save(): Result<Long> {
        _uiState.value = _uiState.value.copy(isSaving = true)
        return try {
            val date = _uiState.value.date ?: System.currentTimeMillis()
            val outfitLog = OutfitLog(
                id = editingLogId ?: 0,
                date = date,
                note = _uiState.value.note,
                imageUrls = _uiState.value.imageUrls
            )
            val isNew = editingLogId == null
            val currentItems = _uiState.value.selectedItemIds
            val removedItems = if (isNew) emptySet() else originalItemIds - currentItems
            val addedItems = if (isNew) currentItems else currentItems - originalItemIds

            val logId = outfitLogRepository.saveOutfitLogWithItems(
                outfitLog, isNew, addedItems, removedItems
            )
            _uiState.value = _uiState.value.copy(isSaving = false)
            Result.success(logId)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isSaving = false, error = e.message ?: "保存失败")
            Result.failure(e)
        }
    }

    fun isValid(): Boolean {
        return _uiState.value.date != null
    }
}
