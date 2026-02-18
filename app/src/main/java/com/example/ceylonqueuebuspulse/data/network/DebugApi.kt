package com.example.ceylonqueuebuspulse.data.network

import com.example.ceylonqueuebuspulse.data.network.model.ApiResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface DebugApi {
    // GET /api/v1/debug/provider/point?lat=...&lon=...
    @GET("api/v1/debug/provider/point")
    suspend fun providerPoint(@Query("lat") lat: Double, @Query("lon") lon: Double): ApiResponse<Map<String, Any>>
}
