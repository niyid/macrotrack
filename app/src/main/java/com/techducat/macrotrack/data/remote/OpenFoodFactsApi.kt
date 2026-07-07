package com.techducat.macrotrack.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface OpenFoodFactsApi {

    /** Barcode lookup — hit when the user scans a product. */
    @GET("api/v2/product/{barcode}.json?fields=code,product_name,brands,image_url,serving_size,nutriments")
    suspend fun getProduct(@Path("barcode") barcode: String): OffProductResponse

    /** Free-text search — hit when the user types a food name instead of scanning. */
    @GET("cgi/search.pl")
    suspend fun search(
        @Query("search_terms") query: String,
        @Query("search_simple") searchSimple: Int = 1,
        @Query("action") action: String = "process",
        @Query("json") json: Int = 1,
        @Query("page_size") pageSize: Int = 25
    ): OffSearchResponse
}
