package com.techducat.macrotrack.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FoodDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(food: FoodEntity)

    @Query("SELECT * FROM foods WHERE id = :id")
    suspend fun getById(id: String): FoodEntity?

    @Query("SELECT * FROM foods WHERE barcode = :barcode LIMIT 1")
    suspend fun getByBarcode(barcode: String): FoodEntity?

    /** Local text search across cached foods — used to show cached results instantly
     *  while a network search (if any) is still in flight. */
    @Query("SELECT * FROM foods WHERE name LIKE '%' || :query || '%' OR brand LIKE '%' || :query || '%' ORDER BY lastUpdated DESC LIMIT 50")
    suspend fun search(query: String): List<FoodEntity>

    @Query("SELECT * FROM foods ORDER BY lastUpdated DESC LIMIT :limit")
    suspend fun getRecentlyUsed(limit: Int = 20): List<FoodEntity>

    @Query("DELETE FROM foods WHERE source = 'off' AND lastUpdated < :olderThan")
    suspend fun pruneStaleCache(olderThan: Long)
}
