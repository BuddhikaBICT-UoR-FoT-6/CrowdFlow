// Edited: 2025-12-27
// Purpose: Immutable UI state model for the traffic screen; holds reports list, loading flag, and an optional error message.

package com.example.ceylonqueuebuspulse.ui

import com.example.ceylonqueuebuspulse.data.TrafficReport

/**
 * Immutable UI model for the traffic screen.
 *
 * Holds a snapshot of the traffic-related data and UI flags that Compose observes.
 * Use with StateFlow in ViewModel and collect in the UI via collectAsState().
 *
 * @property reports current list of traffic reports rendered by the UI
 * @property isLoading indicates when an operation is in progress
 * @property errorMessage optional human-readable error to show in the UI
 */
data class UiState(
    // List of traffic reports emitted from the repository and displayed on the screen
    val reports: List<TrafficReport> = emptyList(),
    // True while seeding or submitting updates; allows showing a progress indicator
    val isLoading: Boolean = false,
    // Optional error message to surface to the user when operations fail
    val errorMessage: String? = null,
    // NEW (Phase 3): indicates if a background/foreground sync is in progress
    val isSyncing: Boolean = false,
    // NEW (Phase 3): last successful data refresh time (epoch millis) for UI display
    val lastUpdatedMs: Long? = null
)

data class TrafficReporterUi(
    val routeId: String,
    val severity: Int,
    val segment: List<Pair<Double, Double>>

)