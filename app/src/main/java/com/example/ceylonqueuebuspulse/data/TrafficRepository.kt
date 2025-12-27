// Edited: 2025-12-27
// Purpose: Repository managing traffic reports; exposes a Flow of reports and handles seeding historical data and user updates.

package com.example.ceylonqueuebuspulse.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// Central data source for traffic information.
// In a full app, this could fetch from network/local DB; here we manage in-memory state.
class TrafficRepository {
    // Backing state for the list of traffic reports.
    // Mutable internally, exposed as an immutable Flow to consumers.
    private val _reports = MutableStateFlow<List<TrafficReport>>(emptyList())

    // Public stream of reports that UI/ViewModel can observe.
    // Using Flow allows Compose to collect and update UI reactively.
    val reports: Flow<List<TrafficReport>> = _reports.asStateFlow()

    // Replace current reports with provided historical sample data.
    // Useful during app bootstrap or periodic aggregation updates.
    fun seedHistoricalData(sample: List<TrafficReport>) {
        _reports.value = sample
    }

    // Convert a user location update to a lightweight TrafficReport and append it.
    // This simulates crowdsourced congestion input.
    fun submitUserUpdate(update: UserLocationUpdate) {
        // Create a report from the user's current location.
        val report = TrafficReport(
            // Prefix with "user-" to distinguish reports generated from user updates
            id = "user-${update.id}",
            // Associate to a route if provided; otherwise mark as unknown
            routeId = update.routeId ?: "unknown",
            // Basic heuristic: set a medium severity for user-submitted points
            severity = 3,
            // Represent the affected segment as a single LatLng point for now
            segment = listOf(LatLng(update.lat, update.lng)),
            // Timestamp propagated from the user's update
            timestamp = update.timestamp,
            // Mark the origin as USER for downstream filtering/aggregation
            source = TrafficSource.USER
        )
        // Append the new report to the current list in an immutable fashion
        _reports.value = _reports.value + report
    }
}
