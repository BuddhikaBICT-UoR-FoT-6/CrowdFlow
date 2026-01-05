// Kotlin
// Edited on 2026-01-05
package com.example.ceylonqueuebuspulse.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ceylonqueuebuspulse.data.repository.TrafficRepository
import com.example.ceylonqueuebuspulse.data.TrafficReport
import com.example.ceylonqueuebuspulse.data.UserLocationUpdate
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
    // Repository dependency. In production, inject via DI; default provided by caller.
    private val repository: TrafficRepository
) : ViewModel() {

    /** Backing mutable state; internal only. */
    private val _uiState = MutableStateFlow(UiState())
    /** Public immutable state observed by Compose. */
    val uiState: StateFlow<UiState> = _uiState

    init {
        // Start observing repository reports; update UI model on each emission.
        viewModelScope.launch {
            repository.reports.collectLatest { list ->
                _uiState.value = _uiState.value.copy(
                    reports = list,
                    isLoading = false,
                    errorMessage = null
                )
            }
        }
    }

    /**
     * Intent: seed historical data into the repository.
     *
     * Sets loading, clears previous errors, then attempts seeding with provided sample reports.
     * On failure, updates errorMessage and clears loading; on success, clears loading.
     */
    fun seedHistoricalData(sample: List<TrafficReport>) {
        viewModelScope.launch {
            // Enter loading state and clear any prior error.
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                // Delegate to repository to replace current data with samples.
                repository.seedHistoricalData(sample)
                // Exit loading state after successful seeding.
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (t: Throwable) {
                // Surface a friendly error and exit loading.
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
     * into a UserLocationUpdate data object. On failure, surfaces a user-friendly errorMessage.
     */
    fun submitUserLocation(lat: Double, lng: Double, routeId: String) {
        viewModelScope.launch {
            // Simple validation to avoid bad inputs
            if (routeId.isBlank()) {
                _uiState.value = _uiState.value.copy(errorMessage = "Route id is required")
                return@launch
            }
            // Enter loading state and clear any prior error.
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                // Wrap parameters into a domain update model.
                val update = UserLocationUpdate(
                    id = System.currentTimeMillis().toString(),
                    userId = "anonymous", // Replace with real user id when available
                    lat = lat,
                    lng = lng,
                    timestamp = System.currentTimeMillis(),
                    routeId = routeId
                )
                // Delegate persistence to repository.
                repository.submitUserUpdate(update)
                // Exit loading state after successful submission.
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (t: Throwable) {
                // Surface a friendly error and exit loading.
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = t.message ?: "Failed to submit update"
                )
            }
        }
    }

    /** Trigger remote sync (Phase 2) and persist results through repository. */
    fun refresh(city: String? = null) {
        viewModelScope.launch { repository.sync(city) }
    }

    /** Clears the current error message from the UI state. */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
