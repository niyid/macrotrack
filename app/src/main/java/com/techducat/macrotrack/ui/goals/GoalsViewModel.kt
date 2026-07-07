package com.techducat.macrotrack.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.techducat.macrotrack.data.repository.GoalsRepository
import com.techducat.macrotrack.model.MacroTotals
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val goalsRepository: GoalsRepository
) : ViewModel() {

    val goals = goalsRepository.observeGoals().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MacroTotals(calories = 2000.0, proteinGrams = 100.0, carbsGrams = 250.0, fatGrams = 65.0)
    )

    fun save(goals: MacroTotals) {
        viewModelScope.launch { goalsRepository.updateGoals(goals) }
    }
}
