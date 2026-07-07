package com.techducat.macrotrack.data.repository

import com.techducat.macrotrack.data.db.GoalsDao
import com.techducat.macrotrack.data.db.UserGoalsEntity
import com.techducat.macrotrack.model.MacroTotals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalsRepository @Inject constructor(
    private val goalsDao: GoalsDao
) {

    fun observeGoals(): Flow<MacroTotals> =
        goalsDao.observeGoals().map { it.toMacroTotals() }

    suspend fun updateGoals(goals: MacroTotals) {
        goalsDao.upsert(
            UserGoalsEntity(
                dailyCalories = goals.calories,
                proteinGrams = goals.proteinGrams,
                carbsGrams = goals.carbsGrams,
                fatGrams = goals.fatGrams
            )
        )
    }

    private fun UserGoalsEntity?.toMacroTotals(): MacroTotals {
        val g = this ?: UserGoalsEntity()
        return MacroTotals(
            calories = g.dailyCalories,
            proteinGrams = g.proteinGrams,
            carbsGrams = g.carbsGrams,
            fatGrams = g.fatGrams
        )
    }
}
