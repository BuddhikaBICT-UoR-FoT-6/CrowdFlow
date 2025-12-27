// Kotlin
// Edited on 2025-12-27
package com.example.ceylonqueuebuspulse.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ceylonqueuebuspulse.data.TrafficRepository
import com.example.ceylonqueuebuspulse.data.TrafficReport
import com.example.ceylonqueuebuspulse.data.TrafficSource
import com.example.ceylonqueuebuspulse.data.UserLocationUpdate
import com.example.ceylonqueuebuspulse.data.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * TrafficViewModel coordinates UI state for bus traffic updates.
 *
 * Responsibilities:
 * - Observe repository's Flow of reports and expose a UI-friendly immutable StateFlow.
 * - Provide intents to seed sample historical data and submit user location updates.
 * - Maintain loading and error states around repository operations.
 */
class TrafficViewModel(
    // Repository dependency. In production, inject via DI; default to in-memory implementation.
    private val repository: TrafficRepository = TrafficRepository()
) : ViewModel() {

    /** UI state observed by Compose. */
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    init {
        // Start observing repository reports; update UI model on each emission.
        viewModelScope.launch {
            repository.reports.collectLatest { reports ->
                _uiState.value = _uiState.value.copy(
                    reports = reports,
                    isLoading = false,
                    errorMessage = null
                )
            }
        }
    }

    /**
     * Intent: seed historical data into the repository.
     *
     * Sets loading, clears previous errors, then attempts seeding with generated sample reports.
     * On failure, updates errorMessage and clears loading.
     */
    fun seedHistoricalData(seedCount: Int = 5) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                // Generate simple sample reports for demonstration
                val now = System.currentTimeMillis()
                val samples = (1..seedCount).map { idx ->
                    TrafficReport(
                        id = "hist-$idx",
                        routeId = "route-${(idx % 3) + 1}",
                        severity = (idx % 5),
                        segment = listOf(LatLng(6.9 + idx * 0.001, 79.86 + idx * 0.001)),
                        timestamp = now - idx * 60_000L,
                        source = TrafficSource.HISTORICAL
                    )
                }
                repository.seedHistoricalData(samples)
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = t.message ?: "Failed to seed data"
                )
            }
        }
    }

    /**
     * Intent: submit a user location update for a bus route.
     *
     * Validates inputs, sets loading, and delegates to repository by wrapping parameters
     * into a UserLocationUpdate data object.
     * On failure, surfaces a user-friendly errorMessage.
     */
    fun submitUserLocation(lat: Double, lng: Double, routeId: String) {
        viewModelScope.launch {
            // Simple validation to avoid bad inputs
            if (routeId.isBlank()) {
                _uiState.value = _uiState.value.copy(errorMessage = "Route id is required")
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val update = UserLocationUpdate(
                    id = System.currentTimeMillis().toString(),
                    userId = "anonymous", // Replace with real user id when available
                    lat = lat,
                    lng = lng,
                    timestamp = System.currentTimeMillis(),
                    routeId = routeId
                )
                repository.submitUserUpdate(update)
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = t.message ?: "Failed to submit update"
                )
            }
        }
    }

    /** Clears the current error message from the UI state. */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
