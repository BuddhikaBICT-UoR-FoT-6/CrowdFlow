package com.example.ceylonqueuebuspulse.traffic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ceylonqueuebuspulse.data.network.DebugApi
import com.example.ceylonqueuebuspulse.data.network.model.ApiResponse
import com.example.ceylonqueuebuspulse.data.repository.AppResult
import com.example.ceylonqueuebuspulse.data.repository.TrafficAggregationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Simple holder mapping for provider response; keep generic to match server payload shape
data class ProviderResult(val raw: Map<String, Any>?, val mapped: Map<String, Any>?)

class LocationTrafficViewModel(
    private val debugApi: DebugApi,
    private val aggregationRepo: TrafficAggregationRepository
) : ViewModel() {

    private val _provider = MutableStateFlow<ProviderResult?>(null)
    val provider: StateFlow<ProviderResult?> = _provider.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    fun selectLocation(lat: Double, lon: Double) {
        _status.value = "Looking up provider data..."
        viewModelScope.launch {
            try {
                val resp: ApiResponse<Map<String, Any>> = debugApi.providerPoint(lat, lon)
                if (resp.ok && resp.data != null) {
                    _provider.value = ProviderResult(raw = resp.data, mapped = resp.data)
                    _status.value = "Provider lookup complete"
                } else {
                    _status.value = "Provider lookup returned error: ${resp.error ?: resp.message}"
                }
            } catch (e: Exception) {
                _status.value = "Provider lookup failed: ${e.message}"
            }
        }
    }

    /**
     * Submit a user sample. routeId may be null - defaults to "unknown".
     * callback receives (ok, errorMessage)
     */
    fun submitSample(routeId: String?, severity: Int, lat: Double, lon: Double, callback: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val windowSizeMs = 15 * 60 * 1000L
            val windowStartMs = (now / windowSizeMs) * windowSizeMs

            when (
                val result = aggregationRepo.submitUserSample(
                    routeId = routeId ?: "unknown",
                    windowStartMs = windowStartMs,
                    segmentId = "_all",
                    severity = severity.toDouble(),
                    reportedAtMs = now,
                    userIdHash = null
                )
            ) {
                is AppResult.Ok -> {
                    _status.value = "Sample submitted"
                    callback(true, null)
                }

                is AppResult.Err -> {
                    val msg = result.error.userMessage
                    _status.value = "Submit failed: $msg"
                    callback(false, msg)
                }
            }
        }
    }
}
