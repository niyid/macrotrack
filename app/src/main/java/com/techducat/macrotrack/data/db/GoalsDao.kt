package com.techducat.macrotrack.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goals: UserGoalsEntity)

    @Query("SELECT * FROM user_goals WHERE id = ${UserGoalsEntity.SINGLETON_ID}")
    fun observeGoals(): Flow<UserGoalsEntity?>

    @Query("SELECT * FROM user_goals WHERE id = ${UserGoalsEntity.SINGLETON_ID}")
    suspend fun getGoalsOnce(): UserGoalsEntity?
}
