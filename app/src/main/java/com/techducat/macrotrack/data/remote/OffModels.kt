package com.techducat.macrotrack.data.remote

import com.squareup.moshi.Json

/**
 * Response models for the Open Food Facts public API (world.openfoodfacts.org).
 * Only the fields MacroTrack actually uses are declared — the real payload has
 * far more, Moshi just ignores anything not listed here.
 *
 * No API key, no account, no auth — the only thing sent over the wire is the
 * barcode or search text itself.
 */

data class OffProductResponse(
    val status: Int = 0,
    val product: OffProduct? = null
)

data class OffSearchResponse(
    val count: Int = 0,
    val products: List<OffProduct> = emptyList()
)

data class OffProduct(
    @Json(name = "code") val code: String = "",
    @Json(name = "product_name") val productName: String? = null,
    @Json(name = "brands") val brands: String? = null,
    @Json(name = "image_url") val imageUrl: String? = null,
    @Json(name = "serving_size") val servingSize: String? = null,
    @Json(name = "nutriments") val nutriments: OffNutriments? = null
)

data class OffNutriments(
    @Json(name = "energy-kcal_100g") val energyKcal100g: Double? = null,
    @Json(name = "proteins_100g") val proteins100g: Double? = null,
    @Json(name = "carbohydrates_100g") val carbohydrates100g: Double? = null,
    @Json(name = "fat_100g") val fat100g: Double? = null,
    @Json(name = "fiber_100g") val fiber100g: Double? = null,
    @Json(name = "sugars_100g") val sugars100g: Double? = null,
    @Json(name = "sodium_100g") val sodium100g: Double? = null
)
