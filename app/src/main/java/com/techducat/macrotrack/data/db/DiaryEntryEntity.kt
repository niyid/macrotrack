package com.techducat.macrotrack.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * DiaryEntryEntity — a single logged food item on a single day.
 *
 * IMPORTANT: macro values here are SNAPSHOTS at the quantity logged, not a
 * foreign-key computation off FoodEntity. This mirrors how Cronometer/MFP
 * diaries behave: if you later edit a food's nutrition facts (or Open Food
 * Facts data corrects itself), your past diary entries do not silently change.
 */
@Entity(
    tableName = "diary_entries",
    indices = [Index(value = ["date"]), Index(value = ["date", "meal_type"])]
)
data class DiaryEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "food_id")
    val foodId: String,

    @ColumnInfo(name = "food_name")
    val foodName: String,

    @ColumnInfo(name = "brand")
    val brand: String = "",

    /** ISO date "yyyy-MM-dd", local device time — used to group the diary by day. */
    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    /** BREAKFAST | LUNCH | DINNER | SNACK */
    @ColumnInfo(name = "meal_type")
    val mealType: String,

    @ColumnInfo(name = "quantity_grams")
    val quantityGrams: Double,

    @ColumnInfo(name = "calories")
    val calories: Double,

    @ColumnInfo(name = "protein_grams")
    val proteinGrams: Double,

    @ColumnInfo(name = "carbs_grams")
    val carbsGrams: Double,

    @ColumnInfo(name = "fat_grams")
    val fatGrams: Double
)
