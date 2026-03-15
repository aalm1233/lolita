package com.lolita.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.data.repository.CatalogRepository
import com.lolita.app.data.repository.CoordinateRepository
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.PriceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val nickname: String = "",
    val avatarPath: String = "",
    val totalItems: Int = 0,
    val totalCatalogEntries: Int = 0,
    val totalCoordinates: Int = 0,
    val totalSpent: Double = 0.0
)

private data class SettingsStats(
    val itemCount: Int,
    val catalogCount: Int,
    val coordinateCount: Int,
    val totalSpent: Double
)

class SettingsViewModel(
    private val appPreferences: AppPreferences = com.lolita.app.di.AppModule.appPreferences(),
    private val itemRepository: ItemRepository = com.lolita.app.di.AppModule.itemRepository(),
    private val catalogRepository: CatalogRepository = com.lolita.app.di.AppModule.catalogRepository(),
    private val coordinateRepository: CoordinateRepository = com.lolita.app.di.AppModule.coordinateRepository(),
    private val priceRepository: PriceRepository = com.lolita.app.di.AppModule.priceRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profileFlow = combine(
                appPreferences.nickname,
                appPreferences.avatarPath
            ) { nickname, avatarPath ->
                nickname to avatarPath
            }

            val statsFlow = combine(
                itemRepository.getAllItems().map { it.size },
                catalogRepository.getCatalogEntryCount(),
                coordinateRepository.getCoordinateCount(),
                priceRepository.getTotalSpending()
            ) { itemCount, catalogCount, coordCount, totalSpent ->
                SettingsStats(
                    itemCount = itemCount,
                    catalogCount = catalogCount,
                    coordinateCount = coordCount,
                    totalSpent = totalSpent
                )
            }

            combine(profileFlow, statsFlow) { profile, stats ->
                SettingsUiState(
                    nickname = profile.first,
                    avatarPath = profile.second,
                    totalItems = stats.itemCount,
                    totalCatalogEntries = stats.catalogCount,
                    totalCoordinates = stats.coordinateCount,
                    totalSpent = stats.totalSpent
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
