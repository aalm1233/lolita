package com.lolita.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.Season
import com.lolita.app.data.repository.SeasonRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SeasonManageUiState(
    val seasons: List<Season> = emptyList(),
    val showAddDialog: Boolean = false,
    val showDeleteConfirm: Season? = null,
    val editingSeason: Season? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class SeasonManageViewModel(
    private val seasonRepository: SeasonRepository = com.lolita.app.di.AppModule.seasonRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SeasonManageUiState())
    val uiState: StateFlow<SeasonManageUiState> = _uiState.asStateFlow()

    init {
        loadSeasons()
    }

    fun loadSeasons() {
        viewModelScope.launch {
            seasonRepository.getAllSeasons().collect { seasons ->
                _uiState.value = _uiState.value.copy(seasons = seasons, isLoading = false)
            }
        }
    }

    fun showAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = true)
    }

    fun hideAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false)
    }

    fun addSeason(name: String) {
        viewModelScope.launch {
            try {
                seasonRepository.insertSeason(Season(name = name.trim(), isPreset = false))
                hideAddDialog()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "添加失败：季节名称已存在")
            }
        }
    }

    fun showDeleteConfirm(season: Season) {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = season)
    }

    fun hideDeleteConfirm() {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = null)
    }

    fun deleteSeason(season: Season) {
        viewModelScope.launch {
            try {
                seasonRepository.deleteSeason(season)
                hideDeleteConfirm()
            } catch (e: Exception) {
                hideDeleteConfirm()
                _uiState.value = _uiState.value.copy(errorMessage = e.message ?: "删除失败")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun showEditDialog(season: Season) {
        _uiState.value = _uiState.value.copy(editingSeason = season)
    }

    fun hideEditDialog() {
        _uiState.value = _uiState.value.copy(editingSeason = null)
    }

    fun updateSeason(season: Season, newName: String) {
        viewModelScope.launch {
            try {
                seasonRepository.updateSeason(season.copy(name = newName.trim()), oldName = season.name)
                hideEditDialog()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "重命名失败：季节名称已存在"
                )
            }
        }
    }
}
