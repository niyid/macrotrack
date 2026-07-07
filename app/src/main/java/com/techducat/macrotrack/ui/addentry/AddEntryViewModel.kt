package com.techducat.macrotrack.ui.addentry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.techducat.macrotrack.data.db.FoodEntity
import com.techducat.macrotrack.data.repository.DiaryRepository
import com.techducat.macrotrack.data.repository.FoodRepository
import com.techducat.macrotrack.model.MealType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddEntryUiState(
    val food: FoodEntity? = null,
    val quantityGrams: String = "100",
    val meal: MealType = MealType.SNACK,
    val isLoading: Boolean = true,
    val logged: Boolean = false
)

@HiltViewModel
class AddEntryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val foodRepository: FoodRepository,
    private val diaryRepository: DiaryRepository
) : ViewModel() {

    private val foodId: String = checkNotNull(savedStateHandle["foodId"])

    private val _uiState = MutableStateFlow(AddEntryUiState())
    val uiState: StateFlow<AddEntryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val food = foodRepository.getById(foodId)
            _uiState.value = _uiState.value.copy(
                food = food,
                quantityGrams = food?.servingSizeGrams?.toString() ?: "100",
                isLoading = false
            )
        }
    }

    fun onQuantityChange(value: String) {
        _uiState.value = _uiState.value.copy(quantityGrams = value)
    }

    fun onMealChange(meal: MealType) {
        _uiState.value = _uiState.value.copy(meal = meal)
    }

    fun logEntry() {
        val state = _uiState.value
        val food = state.food ?: return
        val quantity = state.quantityGrams.toDoubleOrNull() ?: return
        viewModelScope.launch {
            diaryRepository.logFood(food, quantity, state.meal)
            _uiState.value = state.copy(logged = true)
        }
    }
}
