package com.lolita.app.ui.screen.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemPriority
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.local.entity.ItemPurchaseReview
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.PriceRepository
import com.lolita.app.di.AppModule
import kotlinx.coroutines.flow.combine
import com.lolita.app.domain.model.WardrobeFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FilteredItemListUiState(
    val items: List<Item> = emptyList(),
    val isLoading: Boolean = true,
    val purchaseReviews: List<ItemPurchaseReview> = emptyList()
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
            if (filterType == "status_review") {
                itemRepository.getPurchaseReviews().collect { reviews ->
                    _uiState.value = FilteredItemListUiState(
                        items = reviews.map { it.item }, isLoading = false, purchaseReviews = reviews
                    )
                }
                return@launch
            }
            val flow = when (filterType) {
                "family" -> combine(itemRepository.getAllItems(), itemRepository.getCategories()) { items, categories ->
                    val family = WardrobeFamily.valueOf(filterValue)
                    val semantics = categories.associate { it.id to it.categorySemantic.family }
                    items.distinctBy { it.id }.filter { (semantics[it.categoryId] ?: WardrobeFamily.UNCONFIRMED) == family }
                }
                "status_owned" -> itemRepository.getItemsByStatus(ItemStatus.OWNED)
                "status_wished" -> itemRepository.getItemsByStatus(ItemStatus.WISHED)
                "brand" -> itemRepository.getItemsByBrandName(filterValue)
                "category" -> itemRepository.getItemsByCategoryName(filterValue)
                "style" -> itemRepository.getItemsByStyle(filterValue)
                "season" -> itemRepository.getItemsBySeason(filterValue)
                "month" -> priceRepository.getItemsByPurchaseMonth(filterValue)
                "priority" -> {
                    val priority = try { ItemPriority.valueOf(filterValue) } catch (_: Exception) { ItemPriority.MEDIUM }
                    itemRepository.getWishlistByPriorityFilter(priority)
                }
                "location" -> {
                    val locationId = try { filterValue.toLong() } catch (_: Exception) { -1L }
                    if (locationId >= 0) itemRepository.getItemsByLocationId(locationId) else itemRepository.getAllItems()
                }
                "location_unassigned" -> itemRepository.getItemsWithNoLocation()
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
