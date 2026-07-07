package com.techducat.macrotrack.data.repository

import com.techducat.macrotrack.data.db.FoodDao
import com.techducat.macrotrack.data.db.FoodEntity
import com.techducat.macrotrack.data.remote.OffProduct
import com.techducat.macrotrack.data.remote.OpenFoodFactsApi
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FoodRepository — single entry point for "what is this food's nutrition".
 *
 * Lookup order for barcodes: local cache first (instant, works offline for
 * anything scanned before), then Open Food Facts if not cached. Search always
 * shows cached matches immediately and merges in network results as they
 * arrive (caller decides how to combine — see SearchViewModel).
 */
@Singleton
class FoodRepository @Inject constructor(
    private val foodDao: FoodDao,
    private val api: OpenFoodFactsApi
) {

    suspend fun getCachedRecent(limit: Int = 20): List<FoodEntity> =
        foodDao.getRecentlyUsed(limit)

    suspend fun getById(id: String): FoodEntity? = foodDao.getById(id)

    suspend fun searchLocal(query: String): List<FoodEntity> = foodDao.search(query)

    /** Barcode scan result → FoodEntity, or null if not found locally or on Open Food Facts. */
    suspend fun lookupBarcode(barcode: String): FoodEntity? {
        foodDao.getByBarcode(barcode)?.let { return it }

        return try {
            val response = api.getProduct(barcode)
            val product = response.product ?: return null
            if (response.status != 1) return null
            mapToEntity(product, barcode).also { foodDao.upsert(it) }
        } catch (e: Exception) {
            Timber.w(e, "Open Food Facts lookup failed for barcode=%s", barcode)
            null
        }
    }

    /** Free-text search against Open Food Facts; results are cached as a side effect. */
    suspend fun searchRemote(query: String): List<FoodEntity> {
        return try {
            val response = api.search(query)
            response.products
                .filter { !it.productName.isNullOrBlank() && it.nutriments?.energyKcal100g != null }
                .map { mapToEntity(it, it.code) }
                .also { foods -> foods.forEach { foodDao.upsert(it) } }
        } catch (e: Exception) {
            Timber.w(e, "Open Food Facts search failed for query=%s", query)
            emptyList()
        }
    }

    suspend fun saveManualFood(
        name: String,
        brand: String,
        caloriesPer100g: Double,
        proteinPer100g: Double,
        carbsPer100g: Double,
        fatPer100g: Double
    ): FoodEntity {
        val entity = FoodEntity(
            id = "manual:${UUID.randomUUID()}",
            name = name,
            brand = brand,
            caloriesPer100g = caloriesPer100g,
            proteinPer100gGrams = proteinPer100g,
            carbsPer100gGrams = carbsPer100g,
            fatPer100gGrams = fatPer100g,
            source = "manual"
        )
        foodDao.upsert(entity)
        return entity
    }

    private fun mapToEntity(product: OffProduct, barcode: String): FoodEntity {
        val n = product.nutriments
        return FoodEntity(
            id = barcode.ifBlank { "off:${UUID.randomUUID()}" },
            barcode = barcode,
            name = product.productName?.takeIf { it.isNotBlank() } ?: "Unknown product",
            brand = product.brands.orEmpty(),
            caloriesPer100g = n?.energyKcal100g ?: 0.0,
            proteinPer100gGrams = n?.proteins100g ?: 0.0,
            carbsPer100gGrams = n?.carbohydrates100g ?: 0.0,
            fatPer100gGrams = n?.fat100g ?: 0.0,
            fiberPer100gGrams = n?.fiber100g ?: 0.0,
            sugarPer100gGrams = n?.sugars100g ?: 0.0,
            sodiumPer100gMilligrams = (n?.sodium100g ?: 0.0) * 1000.0,
            servingSizeLabel = product.servingSize.orEmpty(),
            imageUrl = product.imageUrl.orEmpty(),
            source = "off"
        )
    }
}
