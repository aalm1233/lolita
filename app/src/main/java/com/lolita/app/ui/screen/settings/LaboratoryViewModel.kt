package com.lolita.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.Item
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.MannequinPresentation
import com.lolita.app.domain.model.mannequinSubject
import com.lolita.app.domain.model.productReferenceImages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*

data class LaboratoryState(val wigs: List<Item> = emptyList(), val loading: Boolean = true, val error: String? = null)

class LaboratoryViewModel(
    items: Flow<List<Item>> = AppModule.itemRepository().getAllItems(),
    categories: Flow<List<Category>> = AppModule.categoryRepository().getAllCategories()
) : ViewModel() {
    val uiState = combine(items, categories) { rows, groups ->
        val heads = groups.filter { mannequinSubject(it, confirmedSemantics = true)?.presentation == MannequinPresentation.WIG_HEAD }.map { it.id }.toSet()
        LaboratoryState(rows.filter { it.categoryId in heads && it.productReferenceImages.isNotEmpty() }, loading = false)
    }.catch { failure ->
        if (failure is CancellationException) throw failure
        emit(LaboratoryState(loading = false, error = "假发资料读取失败，请返回后重试"))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LaboratoryState())
}
