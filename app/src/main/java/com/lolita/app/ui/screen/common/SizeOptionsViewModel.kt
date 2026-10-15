package com.lolita.app.ui.screen.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.SizeOptions
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class SizeOptionsViewModel(
    private val preferences: AppPreferences = AppModule.appPreferences(),
    itemRepository: ItemRepository = AppModule.itemRepository()
) : ViewModel() {
    val options = combine(preferences.customSizes, itemRepository.getSizes()) { custom, existing ->
        SizeOptions.merge(custom, existing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SizeOptions.defaults)

    suspend fun addSize(size: String): String {
        val existing = options.value.firstOrNull { it.equals(size.trim(), ignoreCase = true) }
        return preferences.addCustomSize(existing ?: size)
    }
}
