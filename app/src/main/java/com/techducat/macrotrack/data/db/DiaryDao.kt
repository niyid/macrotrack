package com.techducat.macrotrack.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {

    @Insert
    suspend fun insert(entry: DiaryEntryEntity): Long

    @Delete
    suspend fun delete(entry: DiaryEntryEntity)

    @Query("SELECT * FROM diary_entries WHERE date = :date ORDER BY timestamp ASC")
    fun observeEntriesForDate(date: String): Flow<List<DiaryEntryEntity>>

    @Query("SELECT * FROM diary_entries WHERE date = :date AND meal_type = :mealType ORDER BY timestamp ASC")
    fun observeEntriesForMeal(date: String, mealType: String): Flow<List<DiaryEntryEntity>>

    @Query(
        """
        SELECT
            COALESCE(SUM(calories), 0)      AS totalCalories,
            COALESCE(SUM(protein_grams), 0) AS totalProtein,
            COALESCE(SUM(carbs_grams), 0)   AS totalCarbs,
            COALESCE(SUM(fat_grams), 0)     AS totalFat
        FROM diary_entries
        WHERE date = :date
        """
    )
    fun observeDailyTotals(date: String): Flow<DailyTotals>

    @Query("SELECT DISTINCT date FROM diary_entries ORDER BY date DESC LIMIT :limit")
    suspend fun getLoggedDates(limit: Int = 60): List<String>

    data class DailyTotals(
        val totalCalories: Double,
        val totalProtein: Double,
        val totalCarbs: Double,
        val totalFat: Double
    )
}
