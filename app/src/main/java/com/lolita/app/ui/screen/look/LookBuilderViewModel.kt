package com.lolita.app.ui.screen.look

import com.lolita.app.ui.screen.common.uiErrorText

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.repository.CategoryRepository
import com.lolita.app.data.repository.LookRepository
import com.lolita.app.data.repository.RecommendationRepository
import com.lolita.app.di.AppModule
import com.lolita.app.domain.usecase.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class LookBuilderState(
    val target: Item? = null,
    val groups: Map<String, List<LookOption>> = emptyMap(),
    val selection: LookSelection = LookSelection(emptyMap()),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val savedId: Long? = null
) {
    val items: List<Item> get() = listOfNotNull(target) + groups.flatMap { (slot, options) ->
        options.filter { it.item.id == selection.selected[slot] }.map { it.item }
    }
}

class LookBuilderViewModel(
    private val recommendations: RecommendationRepository = AppModule.recommendationRepository(),
    private val categories: CategoryRepository = AppModule.categoryRepository(),
    private val looks: LookRepository = AppModule.lookRepository()
) : ViewModel() {
    private val _state = MutableStateFlow(LookBuilderState())
    val state = _state.asStateFlow()
    private var requestedId: Long? = null
    private var loadJob: Job? = null
    fun load(id: Long, force: Boolean = false) {
        if (_state.value.saving || requestedId == id && !force) return
        requestedId = id
        loadJob?.cancel()
        _state.value = LookBuilderState()
        loadJob = viewModelScope.launch {
        try {
            val target = recommendations.getItemById(id) ?: error("物品不存在")
            require(target.status == com.lolita.app.data.local.entity.ItemStatus.OWNED) { "请选择已拥有的服饰作为主件" }
            val groups = FullLookEngine.build(target, recommendations.getOwnedItemsExcluding(id), categories.getAllCategories().first(), recommendations.getCoOccurringItemIds(id))
            _state.value = LookBuilderState(target, groups, LookSelection.initial(groups), loading = false)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _state.update { it.copy(loading = false, error = uiErrorText(e, "加载失败")) }
        }
    }
    }
    fun choose(slot: String, id: Long?) {
        val state = _state.value
        if (state.loading || state.saving || state.savedId != null) return
        if (id != null && state.groups[slot].orEmpty().none { it.item.id == id }) return
        _state.update { it.copy(selection = it.selection.copy(selected = it.selection.selected + (slot to id))) }
    }
    fun lock(slot: String) {
        if (_state.value.saving) return
        _state.update { it.copy(selection = it.selection.copy(locked = if (slot in it.selection.locked) it.selection.locked - slot else it.selection.locked + slot)) }
    }
    fun shuffle() {
        if (_state.value.saving) return
        _state.update { it.copy(selection = it.selection.next(it.groups)) }
    }
    fun save(name: String) {
        val snapshot = _state.value
        if (snapshot.loading || snapshot.saving || snapshot.target == null || snapshot.savedId != null) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val id = looks.save(name, snapshot.items)
                _state.update { it.copy(saving = false, savedId = id) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update { it.copy(saving = false, error = uiErrorText(e, "保存失败")) }
            }
        }
    }
}
