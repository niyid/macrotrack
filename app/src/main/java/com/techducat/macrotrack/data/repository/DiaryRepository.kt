package com.techducat.macrotrack.data.repository

import com.techducat.macrotrack.data.db.DiaryDao
import com.techducat.macrotrack.data.db.DiaryEntryEntity
import com.techducat.macrotrack.data.db.FoodEntity
import com.techducat.macrotrack.model.MacroTotals
import com.techducat.macrotrack.model.MealType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiaryRepository @Inject constructor(
    private val diaryDao: DiaryDao
) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun todayKey(): String = dateFormat.format(Date())

    fun observeEntries(date: String): Flow<List<DiaryEntryEntity>> =
        diaryDao.observeEntriesForDate(date)

    fun observeEntriesForMeal(date: String, meal: MealType): Flow<List<DiaryEntryEntity>> =
        diaryDao.observeEntriesForMeal(date, meal.name)

    fun observeDailyTotals(date: String): Flow<MacroTotals> =
        diaryDao.observeDailyTotals(date).map {
            MacroTotals(
                calories = it.totalCalories,
                proteinGrams = it.totalProtein,
                carbsGrams = it.totalCarbs,
                fatGrams = it.totalFat
            )
        }

    /**
     * Log [food] at [quantityGrams] grams. Macro snapshot is computed here (per-100g
     * values scaled by quantity) and frozen into the entry — see DiaryEntryEntity's
     * doc comment for why.
     */
    suspend fun logFood(
        food: FoodEntity,
        quantityGrams: Double,
        meal: MealType,
        date: String = todayKey()
    ): Long {
        val scale = quantityGrams / 100.0
        val entry = DiaryEntryEntity(
            foodId = food.id,
            foodName = food.name,
            brand = food.brand,
            date = date,
            mealType = meal.name,
            quantityGrams = quantityGrams,
            calories = food.caloriesPer100g * scale,
            proteinGrams = food.proteinPer100gGrams * scale,
            carbsGrams = food.carbsPer100gGrams * scale,
            fatGrams = food.fatPer100gGrams * scale
        )
        return diaryDao.insert(entry)
    }

    suspend fun deleteEntry(entry: DiaryEntryEntity) = diaryDao.delete(entry)
}
