// Edited: 2026-01-05
// Purpose: Retrofit service interface defining endpoints for traffic reports.

package com.example.ceylonqueuebuspulse.data.network

import com.example.ceylonqueuebuspulse.data.network.model.TrafficReportDto
import retrofit2.http.GET
import retrofit2.http.Query

// Retrofit API describing endpoints to fetch aggregated traffic reports.
interface TrafficApi {
    // Example: GET /traffic/reports?city=Colombo
    @GET("traffic/reports")
    suspend fun getReports(
        @Query("city") city: String? = null
    ): List<TrafficReportDto>
}
