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
    // Non-null when an error occurred; render and provide a way to dismiss/reset
    val errorMessage: String? = null
)