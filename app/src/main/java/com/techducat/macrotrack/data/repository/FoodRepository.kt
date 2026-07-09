package com.techducat.macrotrack.data.repository

import android.content.Context
import com.techducat.macrotrack.R
import com.techducat.macrotrack.data.db.FoodDao
import com.techducat.macrotrack.data.db.FoodEntity
import com.techducat.macrotrack.data.remote.OffProduct
import com.techducat.macrotrack.data.remote.OpenFoodFactsApi
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val api: OpenFoodFactsApi,
    @ApplicationContext private val context: Context
) {

    suspend fun getCachedRecent(limit: Int = 20): List<FoodEntity> =
        foodDao.getRecentlyUsed(limit)

    suspend fun getById(id: String): FoodEntity? = foodDao.getById(id)

    suspend fun searchLocal(query: String): List<FoodEntity> = foodDao.search(query)

    /**
     * Barcode scan result. Split out from a plain nullable [FoodEntity] because
     * "the product genuinely isn't in Open Food Facts" and "the lookup itself
     * failed" are different situations the UI should say different things
     * about — this matters a lot more now that OFF calls go through
     * [com.techducat.macrotrack.network.I2POutproxyTunnel]: a cold I2P router
     * can take anywhere up to several minutes to bootstrap on first run (see
     * EmbeddedI2PRouter.SAM_WAIT_TIMEOUT_MS), and every lookup during that
     * window used to come back indistinguishable from "not found".
     */
    sealed interface BarcodeLookup {
        data class Found(val food: FoodEntity) : BarcodeLookup
        data object NotFound : BarcodeLookup
        data class Unavailable(val cause: Throwable) : BarcodeLookup
    }

    /** Barcode scan result → cached hit, Open Food Facts hit, genuine miss, or lookup failure. */
    suspend fun lookupBarcode(barcode: String): BarcodeLookup {
        foodDao.getByBarcode(barcode)?.let { return BarcodeLookup.Found(it) }

        return try {
            val response = api.getProduct(barcode)
            val product = response.product
            if (response.status != 1 || product == null) {
                BarcodeLookup.NotFound
            } else {
                BarcodeLookup.Found(mapToEntity(product, barcode).also { foodDao.upsert(it) })
            }
        } catch (e: Exception) {
            Timber.w(e, "Open Food Facts lookup failed for barcode=%s", barcode)
            BarcodeLookup.Unavailable(e)
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

    /**
     * Drops cached Open Food Facts rows ([FoodEntity.source] == "off") that haven't been
     * touched in over [maxAgeMs]. Manually-entered foods are never pruned since they have
     * no network source to re-fetch from. Safe to call opportunistically (e.g. on app
     * start) since `foods` is a rebuildable cache — see FoodEntity's kdoc.
     */
    suspend fun pruneStaleCache(maxAgeMs: Long) {
        foodDao.pruneStaleCache(System.currentTimeMillis() - maxAgeMs)
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
        val servingSizeLabel = product.servingSize.orEmpty()
        return FoodEntity(
            id = barcode.ifBlank { "off:${UUID.randomUUID()}" },
            barcode = barcode,
            name = product.productName?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.food_unknown_product),
            brand = product.brands.orEmpty(),
            caloriesPer100g = n?.energyKcal100g ?: 0.0,
            proteinPer100gGrams = n?.proteins100g ?: 0.0,
            carbsPer100gGrams = n?.carbohydrates100g ?: 0.0,
            fatPer100gGrams = n?.fat100g ?: 0.0,
            fiberPer100gGrams = n?.fiber100g ?: 0.0,
            sugarPer100gGrams = n?.sugars100g ?: 0.0,
            sodiumPer100gMilligrams = (n?.sodium100g ?: 0.0) * 1000.0,
            servingSizeGrams = parseServingSizeGrams(servingSizeLabel),
            servingSizeLabel = servingSizeLabel,
            imageUrl = product.imageUrl.orEmpty(),
            source = "off"
        )
    }

    /**
     * Open Food Facts' `serving_size` is a free-text label like "45 g", "1 bar (40g)",
     * or "250ml" — never a bare number. AddEntryViewModel pre-fills the quantity field
     * from [FoodEntity.servingSizeGrams], so without this parse step that field was
     * always null for every scanned/searched food and the app silently fell back to a
     * hardcoded "100" regardless of the product's actual serving size.
     */
    private fun parseServingSizeGrams(label: String): Double? {
        if (label.isBlank()) return null
        val match = Regex("""(\d+(?:[.,]\d+)?)\s*g\b""", RegexOption.IGNORE_CASE).find(label)
        return match?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
    }
}
