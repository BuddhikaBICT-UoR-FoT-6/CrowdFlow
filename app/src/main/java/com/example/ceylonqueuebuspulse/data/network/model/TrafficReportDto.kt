// Kotlin
package com.example.ceylonqueuebuspulse.data.network.model

// DTO for network payloads. Keep minimal and map to Room entity in the repository.
data class TrafficReportDto(
    val id: String,
    val route: String,
    val severity: Int,
    val points: Int,
    val updatedAt: Long
)