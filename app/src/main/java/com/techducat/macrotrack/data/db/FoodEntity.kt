package com.techducat.macrotrack.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * FoodEntity — local cache of nutrition facts, keyed by barcode when the food
 * came from a scan/Open Off Facts lookup, or by a generated id ("manual:<uuid>")
 * for foods the user typed in by hand.
 *
 * All macro fields are per 100g, matching Open Food Facts' convention, so the
 * diary math (see DiaryRepository) can scale by whatever quantity the user logs.
 *
 * This table is a CACHE, not a ledger: it can be safely wiped/rebuilt from
 * network lookups without losing diary history, because DiaryEntryEntity
 * stores its own snapshot of the macros at the time of logging.
 */
@Entity(
    tableName = "foods",
    indices = [Index(value = ["name"]), Index(value = ["barcode"], unique = false)]
)
data class FoodEntity(
    @PrimaryKey
    val id: String,

    /** Raw scanned barcode (EAN-13/UPC-A/etc.), or empty for manually-entered foods. */
    val barcode: String = "",

    val name: String,
    val brand: String = "",

    val caloriesPer100g: Double,
    val proteinPer100gGrams: Double,
    val carbsPer100gGrams: Double,
    val fatPer100gGrams: Double,
    val fiberPer100gGrams: Double = 0.0,
    val sugarPer100gGrams: Double = 0.0,
    val sodiumPer100gMilligrams: Double = 0.0,

    /** Typical single-serving size in grams, if known (e.g. "1 bar = 45g"). */
    val servingSizeGrams: Double? = null,
    val servingSizeLabel: String = "",

    val imageUrl: String = "",

    /** "off" (Open Food Facts), "manual" (user-entered), or "recent" (reused entry). */
    val source: String = "manual",

    val lastUpdated: Long = System.currentTimeMillis()
)
