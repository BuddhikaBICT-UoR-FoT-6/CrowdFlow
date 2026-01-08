package com.example.ceylonqueuebuspulse.data.network.model

import retrofit2.http.*

// DTOs matching the Node server responses

data class SubmitSampleRequest(
    val routeId: String,
    val windowStartMs: Long,
    val segmentId: String = "_all",
    val severity: Double,
    val reportedAtMs: Long,
    val userIdHash: String? = null
)

data class AggregateRequest(
    val routeId: String,
    val windowStartMs: Long,
    val segmentId: String = "_all"
)

data class AggregateDto(
    val routeId: String,
    val windowStartMs: Long,
    val segmentId: String,
    val severityAvg: Double,
    val severityP50: Double?,
    val severityP90: Double?,
    val sampleCount: Int,
    val lastAggregatedAtMs: Long
)

data class ApiResponse<T>(
    val ok: Boolean,
    val data: T? = null,
    val message: String? = null,
    val error: String? = null
)

interface MongoApi {
    @POST("api/v1/samples")
    suspend fun submitSample(@Body body: SubmitSampleRequest): ApiResponse<Map<String, Any>>

    @POST("api/v1/aggregate")
    suspend fun aggregateWindow(@Body body: AggregateRequest): ApiResponse<AggregateDto>

    @GET("api/v1/aggregates")
    suspend fun getAggregates(
        @Query("routeId") routeId: String,
        @Query("windowStartMs") windowStartMs: Long
    ): ApiResponse<List<AggregateDto>>
}

