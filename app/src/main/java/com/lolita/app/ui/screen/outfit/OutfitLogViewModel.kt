package com.lolita.app.ui.screen.outfit

import com.lolita.app.ui.screen.common.uiErrorText

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import com.lolita.app.data.file.ImageFileHelper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.OutfitItemCrossRef
import com.lolita.app.data.local.entity.OutfitLog
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.OutfitLogRepository
import com.lolita.app.data.local.dao.OutfitLogWithItems
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class OutfitLogListUiState(
    val allLogs: List<OutfitLogListItem> = emptyList(),
    val logs: List<OutfitLogListItem> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val error: String? = null
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
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleted: Boolean = false,
    val loadError: String? = null,
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

    private var searchJob: Job? = null

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
                    val query = _uiState.value.searchQuery
                    _uiState.update {
                        it.copy(
                            allLogs = listItems,
                            logs = applySearch(listItems, query),
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun search(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            val allLogs = _uiState.value.allLogs
            _uiState.update { it.copy(logs = applySearch(allLogs, query)) }
        }
    }

    private fun applySearch(logs: List<OutfitLogListItem>, query: String): List<OutfitLogListItem> {
        if (query.isBlank()) return logs
        return logs.filter {
            it.dateString.contains(query, ignoreCase = true) ||
                it.previewNote.contains(query, ignoreCase = true)
        }
    }

    fun deleteOutfitLog(logId: Long) {
        viewModelScope.launch {
            try {
                outfitLogRepository.deleteOutfitLogById(logId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = uiErrorText(e, "删除失败")) }
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
                _uiState.update {
                    it.copy(
                        log = result?.outfitLog,
                        items = result?.items ?: emptyList(),
                        isLoading = false
                    )
                }
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
    private var hasRequestedLog = false
    private var editorLoaded = false
    private var loadJob: Job? = null
    private val newImages = mutableSetOf<String>()
    private var originalLog: OutfitLog? = null
    private var originalItemIds: Set<Long> = emptySet()
    var hasUnsavedChanges: Boolean = false
        private set

    init {
        loadAvailableItems()
    }

    private fun loadAvailableItems() {
        viewModelScope.launch {
            itemRepository.getAllItems().collect { items ->
                _uiState.update { it.copy(availableItems = items) }
            }
        }
    }

    fun loadOutfitLog(logId: Long?) {
        if (_uiState.value.isSaving) return
        if (hasRequestedLog && editingLogId == logId && (editorLoaded || loadJob?.isActive == true)) return
        loadJob?.cancel()
        if (hasRequestedLog && editingLogId != logId) cleanupNewImages()
        hasRequestedLog = true
        editingLogId = logId
        editorLoaded = false
        originalLog = null
        originalItemIds = emptySet()
        hasUnsavedChanges = false
        _uiState.update { OutfitLogEditUiState(availableItems = it.availableItems, isLoading = logId != null,
            date = if (logId == null) System.currentTimeMillis() else null) }
        if (logId == null) {
            editorLoaded = true
            return
        }
        loadJob = viewModelScope.launch {
            try {
                val result = outfitLogRepository.getOutfitLogWithItems(logId).first()
                if (result == null) {
                    _uiState.update { it.copy(isDeleted = true, isLoading = false, loadError = "原记录不存在，请重试") }
                } else {
                    originalLog = result.outfitLog
                    val itemIds = result.items.map { it.id }.toSet()
                    originalItemIds = itemIds
                    editorLoaded = true
                    _uiState.update { it.copy(date = result.outfitLog.date, note = result.outfitLog.note,
                        imageUrls = result.outfitLog.imageUrls, selectedItemIds = itemIds, isLoading = false) }
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                _uiState.update { it.copy(isLoading = false, loadError = uiErrorText(failure, "读取穿搭失败")) }
            }
        }
    }

    fun cleanupNewImages() {
        ImageFileHelper.cleanupUnreferencedImages(newImages.toList())
        newImages.clear()
    }

    override fun onCleared() {
        cleanupNewImages()
        super.onCleared()
    }

    fun updateDate(date: Long) {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        hasUnsavedChanges = true
        _uiState.update { it.copy(date = date) }
    }

    fun updateNote(note: String) {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        hasUnsavedChanges = true
        _uiState.update { it.copy(note = note) }
    }

    fun addImage(imageUrl: String) {
        if (_uiState.value.isLoading || _uiState.value.isSaving) {
            if (imageUrl !in _uiState.value.imageUrls) ImageFileHelper.cleanupUnreferencedImages(listOf(imageUrl))
            return
        }
        newImages.add(imageUrl)
        hasUnsavedChanges = true
        _uiState.update { it.copy(imageUrls = it.imageUrls + imageUrl) }
    }

    fun removeImage(imageUrl: String) {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        hasUnsavedChanges = true
        _uiState.update { it.copy(imageUrls = it.imageUrls - imageUrl) }
        if (imageUrl !in _uiState.value.imageUrls && newImages.remove(imageUrl)) {
            ImageFileHelper.cleanupUnreferencedImages(listOf(imageUrl))
        }
    }

    fun toggleItemSelection(itemId: Long) {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        hasUnsavedChanges = true
        _uiState.update {
            val newSelection = if (itemId in it.selectedItemIds) it.selectedItemIds - itemId else it.selectedItemIds + itemId
            it.copy(selectedItemIds = newSelection)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // The VM owns persistence; a recreated screen may cancel its await without cancelling the save.
    private var saveTask: Deferred<Result<Long>>? = null

    suspend fun save(): Result<Long> {
        val task = saveTask?.takeIf { it.isActive } ?: viewModelScope.async { saveDraft() }
            .also { saveTask = it }
        return task.await()
    }

    private suspend fun saveDraft(): Result<Long> {
        if (_uiState.value.isSaving) return Result.failure(Exception("正在保存中"))
        if (_uiState.value.isDeleted) {
            return Result.failure(Exception("原记录已被删除，无法保存"))
        }
        val original = originalLog
        if (editingLogId != null && (_uiState.value.isLoading || original == null)) {
            return Result.failure(Exception("原记录尚未读取完成，暂时无法保存"))
        }
        _uiState.update { it.copy(isSaving = true) }
        return try {
            val date = _uiState.value.date ?: System.currentTimeMillis()
            val outfitLog = original?.copy(
                date = date,
                note = _uiState.value.note,
                imageUrls = _uiState.value.imageUrls
            ) ?: OutfitLog(
                date = date,
                note = _uiState.value.note,
                imageUrls = _uiState.value.imageUrls
            )
            val isNew = original == null
            val currentItems = _uiState.value.selectedItemIds
            val removedItems = if (isNew) emptySet() else originalItemIds - currentItems
            val addedItems = if (isNew) currentItems else currentItems - originalItemIds

            val logId = outfitLogRepository.saveOutfitLogWithItems(
                outfitLog, isNew, addedItems, removedItems
            )
            originalLog = outfitLog.copy(id = logId)
            originalItemIds = currentItems
            cleanupNewImages()
            hasUnsavedChanges = false
            _uiState.update { it.copy(isSaving = false) }
            Result.success(logId)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _uiState.update { it.copy(isSaving = false, error = uiErrorText(e, "保存失败")) }
            Result.failure(e)
        }
    }

    fun isValid(): Boolean {
        return _uiState.value.date != null
    }
}
