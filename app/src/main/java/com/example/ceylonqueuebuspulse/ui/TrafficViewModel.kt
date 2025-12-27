package com.example.ceylonqueuebuspulse.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ceylonqueuebuspulse.data.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrafficViewModel(
    private val repo: TrafficRepository = TrafficRepository()
) : ViewModel() {

    init {
        // Seed with a simple Colombo sample segment
        val sample = listOf(
            TrafficReport(
                id = "hist-1",
                routeId = "route-1",
                severity = 2,
                segment = listOf(
                    LatLng(6.9271, 79.8612), // Colombo
                    LatLng(6.9100, 79.8650)
                ),
                timestamp = System.currentTimeMillis() - 15 * 60_000,
                source = TrafficSource.HISTORICAL
            )
        )
        repo.seedHistoricalData(sample)
    }

    val uiState: StateFlow<TrafficUiState> = repo.reports
        .map { TrafficUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TrafficUiState(emptyList()))

    fun submitUserLocation(lat: Double, lng: Double, routeId: String? = null) {
        viewModelScope.launch {
            repo.submitUserUpdate(
                UserLocationUpdate(
                    id = System.currentTimeMillis().toString(),
                    userId = "local",
                    lat = lat,
                    lng = lng,
                    timestamp = System.currentTimeMillis(),
                    routeId = routeId
                )
            )
        }
    }
}

data class TrafficUiState(val reports: List<TrafficReport>)
