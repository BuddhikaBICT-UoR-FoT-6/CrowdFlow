package com.example.ceylonqueuebuspulse.data.network

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// Partial response model for TomTom Search API (forward geocoding)

data class TomTomSearchResponse(
    val results: List<TomTomSearchResult> = emptyList()
)

data class TomTomSearchResult(
    val address: TomTomAddress? = null,
    val position: TomTomPosition? = null
)

data class TomTomAddress(
    val freeformAddress: String? = null,
    val municipality: String? = null
)

data class TomTomPosition(
    val lat: Double?,
    val lon: Double?
)

interface TomTomSearchApi {
    // Example: https://api.tomtom.com/search/2/search/{query}.json?key=YOUR_KEY
    @GET("search/2/search/{query}.json")
    suspend fun search(
        @Path("query") query: String,
        @Query("key") apiKey: String,
        @Query("limit") limit: Int = 10
    ): TomTomSearchResponse
}
