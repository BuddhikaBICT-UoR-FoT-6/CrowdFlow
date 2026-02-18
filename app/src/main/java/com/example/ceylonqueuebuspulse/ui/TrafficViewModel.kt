// Kotlin
// Edited on 2026-01-08
package com.example.ceylonqueuebuspulse.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ceylonqueuebuspulse.data.repository.AppResult
import com.example.ceylonqueuebuspulse.data.repository.TrafficRepository
import com.example.ceylonqueuebuspulse.data.repository.TrafficAggregationRepository
import com.example.ceylonqueuebuspulse.data.TrafficReport
import com.example.ceylonqueuebuspulse.data.UserLocationUpdate
import com.example.ceylonqueuebuspulse.util.NetworkException
import com.example.ceylonqueuebuspulse.util.RetryUtil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/**
 * TrafficViewModel coordinates UI state for bus traffic updates.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrafficViewModel(
    private val repository: TrafficRepository,
    private val aggregationRepository: TrafficAggregationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    private val _selectedRouteId = MutableStateFlow("138")

    init {
        viewModelScope.launch {
            repository.reports.collectLatest { list ->
                _uiState.value = _uiState.value.copy(
                    reports = list,
                    isLoading = false,
                    errorMessage = null
                )
            }
        }

        viewModelScope.launch {
            _selectedRouteId.flatMapLatest { routeId ->
                aggregationRepository.observeAggregatedTraffic(routeId)
            }.collectLatest { aggregates ->
                _uiState.value = _uiState.value.copy(
                    aggregatedData = aggregates,
                    selectedRouteId = _selectedRouteId.value
                )
            }
        }
    }

    fun selectRoute(routeId: String) {
        _selectedRouteId.value = routeId
    }

    fun seedHistoricalData(sample: List<TrafficReport>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val result = repository.seedHistoricalData(sample)) {
                is AppResult.Ok -> _uiState.value = _uiState.value.copy(isLoading = false)
                is AppResult.Err -> _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = result.error.userMessage)
            }
        }
    }

    fun submitUserLocation(lat: Double, lng: Double, routeId: String) {
        viewModelScope.launch {
            if (routeId.isBlank()) {
                _uiState.value = _uiState.value.copy(errorMessage = "Route ID is required")
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val update = UserLocationUpdate(
                id = System.currentTimeMillis().toString(),
                userId = "anonymous",
                lat = lat,
                lng = lng,
                timestamp = System.currentTimeMillis(),
                routeId = routeId
            )

            when (val result = repository.submitUserUpdate(update)) {
                is AppResult.Ok -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = null)
                }

                is AppResult.Err -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = result.error.userMessage)
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true, errorMessage = null)
            try {
                val routeId = _selectedRouteId.value
                val nowMs = System.currentTimeMillis()
                val windowSizeMs = 15 * 60 * 1000L
                val windowStartMs = (nowMs / windowSizeMs) * windowSizeMs

                // We keep existing method call to avoid touching Worker callers.
                aggregationRepository.aggregateAndSyncWindow(
                    routeId = routeId,
                    windowStartMs = windowStartMs,
                    segmentId = null,
                    nowMs = nowMs
                )

                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    lastUpdatedMs = System.currentTimeMillis(),
                    errorMessage = null
                )
            } catch (t: Throwable) {
                val friendlyMessage = when (val mapped = RetryUtil.mapException(t)) {
                    is NetworkException -> mapped.message ?: "Failed to sync data"
                }

                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    errorMessage = friendlyMessage
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
