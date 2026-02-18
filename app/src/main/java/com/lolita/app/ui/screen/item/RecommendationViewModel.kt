package com.lolita.app.ui.screen.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.repository.CategoryRepository
import com.lolita.app.data.repository.RecommendationRepository
import com.lolita.app.di.AppModule
import com.lolita.app.domain.usecase.MatchingEngine
import com.lolita.app.domain.usecase.MatchScore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecommendationUiState(
    val targetItem: Item? = null,
    val recommendations: Map<String, List<MatchScore>> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class RecommendationViewModel(
    private val recommendationRepository: RecommendationRepository = AppModule.recommendationRepository(),
    private val categoryRepository: CategoryRepository = AppModule.categoryRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecommendationUiState())
    val uiState: StateFlow<RecommendationUiState> = _uiState.asStateFlow()

    private val matchingEngine = MatchingEngine()

    fun loadRecommendations(itemId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val target = recommendationRepository.getItemById(itemId)
                    ?: run {
                        _uiState.update { it.copy(isLoading = false, error = "物品不存在") }
                        return@launch
                    }
                val candidates = recommendationRepository.getOwnedItemsExcluding(itemId)
                val coOccurring = recommendationRepository.getCoOccurringItemIds(itemId)

                val targetCategory = categoryRepository.getCategoryById(target.categoryId)
                val targetGroup = targetCategory?.group

                // Filter out same category group items
                val filtered = if (targetGroup != null) {
                    val sameCategoryIds = candidates.filter { candidate ->
                        val cat = categoryRepository.getCategoryById(candidate.categoryId)
                        cat?.group == targetGroup
                    }.map { it.id }.toSet()
                    candidates.filter { it.id !in sameCategoryIds }
                } else candidates

                val scores = matchingEngine.recommend(target, filtered, coOccurring)

                // Group by category name
                val grouped = scores.groupBy { score ->
                    val cat = categoryRepository.getCategoryById(score.item.categoryId)
                    cat?.name ?: "其他"
                }

                _uiState.update {
                    it.copy(
                        targetItem = target,
                        recommendations = grouped,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
