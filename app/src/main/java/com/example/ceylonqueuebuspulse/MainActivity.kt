// Edited: 2025-12-27
// Purpose: Main activity sets up Jetpack Compose UI and binds to TrafficViewModel to display bus traffic updates.

// Kotlin
package com.example.ceylonqueuebuspulse

// Location handling - 2025-12-28
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat

// Compose UI
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// Theme + ViewModel
import com.example.ceylonqueuebuspulse.ui.theme.CeylonQueueBusPulseTheme
import com.example.ceylonqueuebuspulse.ui.TrafficViewModel

// Google Play Services Location APIs
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.example.ceylonqueuebuspulse.work.SyncScheduler

// Entry point Activity. Hosts the Compose UI and connects it to the ViewModel.
class MainActivity : ComponentActivity() {

    // ViewModel scoped to the Activity lifecycle
    // Kotlin
    private val viewModel: TrafficViewModel by viewModels {
        com.example.ceylonqueuebuspulse.di.TrafficViewModelFactory(application)
    }


    // Fused Location state
    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private var locationCallback: LocationCallback? = null

    // Runtime permission launcher for fine/coarse location (multiple permissions)
    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val fineGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarseGranted = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (fineGranted || coarseGranted) {
                startLocationUpdates()
            } else {
                // Clear any previous error; you might also surface a snack/toast
                viewModel.clearError()
            }
        }

    // Lifecycle: initialize location and compose UI
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw content edge-to-edge under system bars
        enableEdgeToEdge()

        // Schedule periodic background sync (Phase 3)
        SyncScheduler.schedule(applicationContext)

        // Initialize fused location client and request configuration
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(2000L)
            .setMaxUpdateDelayMillis(10000L)
            .build()

        // Check/ask permissions and start streaming if allowed
        ensureLocationPermissionAndStart()

        // Compose UI content
        setContent {
            CeylonQueueBusPulseTheme {
                val state by viewModel.uiState.collectAsState()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    ) {
                        Text(
                            text = "Bus Traffic Updates",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(4.dp))
                        // Show sync/refresh status and last updated timestamp when available
                        if (state.isSyncing) {
                            Text("Syncing…", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            state.lastUpdatedMs?.let { ts ->
                                Text("Last updated: ${ts}", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        // Spacing
                        Spacer(Modifier.height(8.dp))

                        // List current traffic reports
                        state.reports.forEach { report ->
                            Text(
                                text = "Route: ${report.routeId} | Severity: ${report.severity} | Points: ${report.segment.size}"
                            )
                        }

                        // More spacing
                        Spacer(Modifier.height(16.dp))

                        // Sample action to submit a fixed location (Colombo)
                        Button(onClick = { viewModel.submitUserLocation(6.9271, 79.8612, "route-1") }) {
                            Text("Submit Sample User Update (Colombo)")
                        }
                    }
                }
            }
        }
    }

    // Ensure permissions are granted; if not, request them
    private fun ensureLocationPermissionAndStart() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            startLocationUpdates()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Begin receiving fused location updates and forward to ViewModel
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        // Avoid duplicate registration
        if (locationCallback != null) return

        // Extra guard: ensure we still have permission at call time to avoid SecurityException
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) return

        // Handle incoming location batches
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val routeId = "route-1" // TODO: map to real route selection
                viewModel.submitUserLocation(loc.latitude, loc.longitude, routeId)
            }
        }

        fusedClient.requestLocationUpdates(
            locationRequest,
            locationCallback as LocationCallback,
            mainLooper
        )
    }

    // Stop receiving updates to conserve battery/resources
    private fun stopLocationUpdates() {
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        locationCallback = null
    }

    // Resume: re-verify permission and restart updates
    override fun onResume() {
        super.onResume()
        ensureLocationPermissionAndStart()
    }

    // Pause: stop updates while in background
    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }
}

// Preview composable for design-time rendering in Android Studio
@Preview(showBackground = true)
@Composable
fun AppPreview() {
    CeylonQueueBusPulseTheme {
        Text("Bus Traffic Updates")
    }
}
