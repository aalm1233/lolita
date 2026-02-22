package com.lolita.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.data.repository.CoordinateRepository
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.PriceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val nickname: String = "",
    val avatarPath: String = "",
    val totalItems: Int = 0,
    val totalCoordinates: Int = 0,
    val totalSpent: Double = 0.0
)

class SettingsViewModel(
    private val appPreferences: AppPreferences = com.lolita.app.di.AppModule.appPreferences(),
    private val itemRepository: ItemRepository = com.lolita.app.di.AppModule.itemRepository(),
    private val coordinateRepository: CoordinateRepository = com.lolita.app.di.AppModule.coordinateRepository(),
    private val priceRepository: PriceRepository = com.lolita.app.di.AppModule.priceRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                appPreferences.nickname,
                appPreferences.avatarPath,
                itemRepository.getAllItems().map { it.size },
                coordinateRepository.getCoordinateCount(),
                priceRepository.getTotalSpending()
            ) { nickname, avatarPath, itemCount, coordCount, totalSpent ->
                SettingsUiState(
                    nickname = nickname,
                    avatarPath = avatarPath,
                    totalItems = itemCount,
                    totalCoordinates = coordCount,
                    totalSpent = totalSpent
                )
            }.collect { _uiState.value = it }
        }
    }

    fun setNickname(name: String) {
        viewModelScope.launch { appPreferences.setNickname(name) }
    }

    fun setAvatarPath(path: String) {
        viewModelScope.launch { appPreferences.setAvatarPath(path) }
    }
}
