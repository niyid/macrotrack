package com.techducat.macrotrack.ui.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.techducat.macrotrack.data.db.DiaryEntryEntity
import com.techducat.macrotrack.data.repository.DiaryRepository
import com.techducat.macrotrack.data.repository.GoalsRepository
import com.techducat.macrotrack.model.MacroTotals
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiaryUiState(
    val date: String = "",
    val entries: List<DiaryEntryEntity> = emptyList(),
    val totals: MacroTotals = MacroTotals(),
    val goals: MacroTotals = MacroTotals()
)

@HiltViewModel
class DiaryViewModel @Inject constructor(
    private val diaryRepository: DiaryRepository,
    private val goalsRepository: GoalsRepository
) : ViewModel() {

    private val date = diaryRepository.todayKey()

    val uiState = combine(
        diaryRepository.observeEntries(date),
        diaryRepository.observeDailyTotals(date),
        goalsRepository.observeGoals()
    ) { entries, totals, goals ->
        DiaryUiState(date = date, entries = entries, totals = totals, goals = goals)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DiaryUiState(date = date)
    )

    fun deleteEntry(entry: DiaryEntryEntity) {
        viewModelScope.launch { diaryRepository.deleteEntry(entry) }
    }
}
