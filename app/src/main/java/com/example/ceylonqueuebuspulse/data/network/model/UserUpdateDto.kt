// Edited: 2026-01-06
// Purpose: Network DTO for posting user-submitted location updates to the backend.

package com.example.ceylonqueuebuspulse.data.network

/** Minimal payload describing a user's location/congestion update. */
data class UserUpdateDto(
    val userId: String,
    val routeId: String?,
    val lat: Double,
    val lng: Double,
    val timestampMs: Long
)
