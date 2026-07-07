package com.techducat.macrotrack.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * UserGoalsEntity — singleton row (id is always 1) holding the user's daily
 * calorie and macro targets. Kept in its own table rather than SharedPreferences
 * so the Diary screen can observe it reactively via Room's Flow support.
 */
@Entity(tableName = "user_goals")
data class UserGoalsEntity(
    @PrimaryKey
    val id: Int = SINGLETON_ID,
    val dailyCalories: Double = 2000.0,
    val proteinGrams: Double = 100.0,
    val carbsGrams: Double = 250.0,
    val fatGrams: Double = 65.0
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
