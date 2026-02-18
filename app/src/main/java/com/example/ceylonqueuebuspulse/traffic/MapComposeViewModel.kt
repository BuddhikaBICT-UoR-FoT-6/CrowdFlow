package com.example.ceylonqueuebuspulse.traffic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ceylonqueuebuspulse.data.network.TomTomSearchApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class PlaceResult(val label: String, val lat: Double, val lon: Double)

class MapComposeViewModel(
    private val tomTomSearchApi: TomTomSearchApi,
    private val locVm: LocationTrafficViewModel

) : ViewModel(){

    private val _places = MutableStateFlow<List<PlaceResult>>(emptyList())
    val places: StateFlow<List<PlaceResult>> = _places

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    /**
     * Search with retry + basic backoff. Updates [places] on success and [status] on progress/errors.
     */
    fun search(query: String, apiKey: String, maxAttempts: Int = 3) {
        if(query.isBlank()){
            _status.value = "Query is blank"
            return
        }

        viewModelScope.launch {
            _status.value = "Searching..."
            var attempt = 0
            var lastError : Throwable? = null
            while (attempt < maxAttempts) {

                try {
                    val res = tomTomSearchApi.search(query, apiKey, limit = 10)
                    val converted = res.results.mapNotNull { r ->
                        val pos = r.position
                        val lat = pos?.lat
                        val lon = pos?.lon
                        if (lat != null && lon != null) {
                            val label = r.address?.freeformAddress ?: "${lat}, ${lon}"
                            PlaceResult(label = label, lat = lat, lon = lon)
                        } else null
                    }
                    _places.value = converted
                    _status.value = if (converted.isEmpty()) "No results" else null
                    return@launch
                } catch (t: Throwable) {
                    lastError = t
                    attempt++
                    if (attempt < maxAttempts) {
                        delay(400L * attempt) // simple backoff
                    }
                }
            }
            _status.value = "Search failed: ${lastError?.message ?: "Unknown error"}"
        }
    }

    fun selectPlace(place: PlaceResult) {
        // Move the unified pipeline: call provider lookup and update UI via LocationTrafficViewModel
        locVm.selectLocation(place.lat, place.lon)
    }

    fun submitSampleForPlace(place: PlaceResult, severity: Int) {
        locVm.submitSample(null, severity, place.lat, place.lon) { ok, err ->
            _status.value = if (ok) "Sample submitted" else "Submit failed: $err"
        }
    }
}
