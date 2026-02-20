package com.lolita.app.ui.screen.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemPriority
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.PriceRepository
import com.lolita.app.di.AppModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FilteredItemListUiState(
    val items: List<Item> = emptyList(),
    val isLoading: Boolean = true
)

class FilteredItemListViewModel(
    private val filterType: String,
    private val filterValue: String,
    private val itemRepository: ItemRepository = AppModule.itemRepository(),
    private val priceRepository: PriceRepository = AppModule.priceRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(FilteredItemListUiState())
    val uiState: StateFlow<FilteredItemListUiState> = _uiState.asStateFlow()

    init {
        loadItems()
    }

    private fun loadItems() {
        viewModelScope.launch {
            val flow = when (filterType) {
                "status_owned" -> itemRepository.getItemsByStatus(ItemStatus.OWNED)
                "status_wished" -> itemRepository.getItemsByStatus(ItemStatus.WISHED)
                "brand" -> itemRepository.getItemsByBrandName(filterValue)
                "category" -> itemRepository.getItemsByCategoryName(filterValue)
                "style" -> itemRepository.getItemsByStyle(filterValue)
                "season" -> itemRepository.getItemsBySeason(filterValue)
                "month" -> priceRepository.getItemsByPurchaseMonth(filterValue)
                "priority" -> itemRepository.getWishlistByPriorityFilter(
                    ItemPriority.valueOf(filterValue)
                )
                else -> itemRepository.getAllItems()
            }
            flow.collect { items ->
                _uiState.value = FilteredItemListUiState(items = items, isLoading = false)
            }
        }
    }
}

class FilteredItemListViewModelFactory(
    private val filterType: String,
    private val filterValue: String
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return FilteredItemListViewModel(filterType, filterValue) as T
    }
}
