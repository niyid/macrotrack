package com.techducat.macrotrack.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.techducat.macrotrack.data.db.FoodEntity
import com.techducat.macrotrack.data.repository.FoodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<FoodEntity> = emptyList(),
    val isSearching: Boolean = false
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val foodRepository: FoodRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val recent = foodRepository.getCachedRecent()
            _uiState.value = _uiState.value.copy(results = recent)
        }
    }

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        if (query.isBlank()) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(results = foodRepository.getCachedRecent())
            }
            return
        }
        search(query)
    }

    private fun search(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true)

            // Show cached matches immediately (works offline, feels instant)...
            val local = foodRepository.searchLocal(query)
            _uiState.value = _uiState.value.copy(results = local)

            // ...then merge in Open Food Facts results once they arrive.
            val remote = foodRepository.searchRemote(query)
            val merged = (local + remote).distinctBy { it.id }
            if (_uiState.value.query == query) {
                _uiState.value = _uiState.value.copy(results = merged, isSearching = false)
            }
        }
    }
}
