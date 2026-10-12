package com.lolita.app.ui.screen.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.data.repository.BudgetRepository
import com.lolita.app.di.AppModule
import com.lolita.app.domain.usecase.BudgetSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth

data class BudgetUiState(val month: YearMonth = YearMonth.now(), val limitCents: Long? = null,
    val summary: BudgetSummary? = null, val showMoney: Boolean = false, val loading: Boolean = true,
    val saving: Boolean = false, val error: String? = null, val saved: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModel(private val repository: BudgetRepository = AppModule.budgetRepository(),
    private val preferences: AppPreferences = AppModule.appPreferences()) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val refresh = MutableStateFlow(0)
    private val _state = MutableStateFlow(BudgetUiState())
    val state = _state.asStateFlow()
    init {
        viewModelScope.launch {
            combine(month, refresh) { selected, _ -> selected }.flatMapLatest { selected ->
                combine(repository.observe(selected), preferences.showTotalPrice) { (budget, summary), show ->
                    BudgetUiState(selected, budget?.amountCents, summary, show, loading = false)
                }.onStart { emit(BudgetUiState(month = selected, showMoney = _state.value.showMoney)) }
                    .catch { e -> emit(BudgetUiState(month = selected, loading = false,
                        showMoney = preferences.showTotalPrice.first(), error = e.message ?: "预算加载失败")) }
            }.collect { loaded -> _state.update {
                it.copy(month = loaded.month, limitCents = loaded.limitCents, summary = loaded.summary,
                    showMoney = loaded.showMoney, loading = loaded.loading, error = loaded.error ?: it.error)
            } }
        }
    }
    fun changeMonth(delta: Long) {
        if (_state.value.saving) return
        val next = month.value.plusMonths(delta)
        if (next.year !in 1..9999) return
        _state.value = BudgetUiState(month = next, showMoney = _state.value.showMoney)
        month.value = next
    }
    fun retry() {
        if (_state.value.saving) return
        _state.update { it.copy(error = null, saved = false) }
        refresh.update { it + 1 }
    }
    fun resetEditor() { _state.update { it.copy(error = null, saved = false) } }
    fun save(input: String) = mutate { repository.save(month.value, input) }
    fun remove() = mutate { repository.delete(month.value) }
    private fun mutate(block: suspend () -> Unit) {
        if (_state.value.saving || _state.value.loading || _state.value.summary == null) return
        _state.update { it.copy(saving = true, error = null, saved = false) }
        viewModelScope.launch {
            try {
                block()
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update { it.copy(saving = false, error = e.message ?: "保存失败") }
            }
        }
    }
}
