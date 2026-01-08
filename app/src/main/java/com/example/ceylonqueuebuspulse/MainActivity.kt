// Edited: 2026-01-06
// Purpose: Main activity hosts Compose UI, binds to TrafficViewModel, schedules background sync, and streams location updates.

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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

// Compose UI
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// Theme + ViewModel
import com.example.ceylonqueuebuspulse.ui.theme.CeylonQueueBusPulseTheme
import com.example.ceylonqueuebuspulse.ui.TrafficViewModel
import com.example.ceylonqueuebuspulse.util.FirebaseTestUtil
import com.example.ceylonqueuebuspulse.work.SyncScheduler

// Google Play Services Location APIs
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

import java.text.DateFormat
import java.util.Date

// Entry point Activity. Hosts the Compose UI and connects it to the ViewModel.
class MainActivity : ComponentActivity() {

    // ViewModel scoped to the Activity lifecycle.
    private val viewModel: TrafficViewModel by viewModel()

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
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw content edge-to-edge under system bars
        enableEdgeToEdge()

        // Initialize Firebase Auth anonymously (redundant with Application-level but harmless)
        com.google.firebase.ktx.Firebase.auth.signInAnonymously()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    android.util.Log.d("MainActivity", "Firebase anonymous sign-in successful")
                } else {
                    android.util.Log.e("MainActivity", "Firebase anonymous sign-in failed", task.exception)
                }
            }

        // Schedule periodic background sync (Phase 3)
        SyncScheduler.schedule(applicationContext)

        // Initialize Firebase with test data (one-time setup)
        // TODO: Remove after initial testing - this populates Firestore with sample data
        // Delay slightly to avoid blocking app startup
        lifecycleScope.launch {
            try {
                kotlinx.coroutines.delay(2000) // Wait 2 seconds after app starts
                android.util.Log.d("MainActivity", "🔥 Starting Firestore initialization...")
                FirebaseTestUtil.initializeFirestoreWithTestData()
                android.util.Log.d("MainActivity", "✅ Firestore initialized with test data")
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "❌ Failed to initialize Firestore: ${e.message}", e)
                // Don't crash the app - Firebase initialization is optional
                e.printStackTrace()
            }
        }

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
                val snackbarHostState = remember { SnackbarHostState() }

                // Show error as snackbar when errorMessage changes
                LaunchedEffect(state.errorMessage) {
                    state.errorMessage?.let {
                        snackbarHostState.showSnackbar(it)
                        viewModel.clearError()
                    }
                }

                // Convert epoch millis -> human-readable local date/time.
                // Uses java.text.DateFormat for API 24+ compatibility.
                val formattedLastUpdate = remember(state.lastUpdatedMs) {
                    state.lastUpdatedMs?.let { ms ->
                        DateFormat.getDateTimeInstance(
                            DateFormat.MEDIUM,
                            DateFormat.SHORT
                        ).format(Date(ms))
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text("Bus Traffic Updates") },
                            actions = {
                                IconButton(onClick = { viewModel.refresh() }) {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = "Refresh"
                                    )
                                }
                            }
                        )
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Spacer(Modifier.height(4.dp))

                        // Show sync/refresh status and last updated timestamp when available
                        if (state.isSyncing) {
                            Text("Syncing…", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            formattedLastUpdate?.let { pretty ->
                                Text("Last updated: $pretty", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Route Selector
                        Text("Select Route:", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        val routes = listOf("138", "174", "177", "120")
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            routes.forEach { routeId ->
                                androidx.compose.material3.FilterChip(
                                    selected = state.selectedRouteId == routeId,
                                    onClick = { viewModel.selectRoute(routeId) },
                                    label = { Text(routeId) }
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Current Traffic Status Card
                        if (state.aggregatedData.isNotEmpty()) {
                            val latest = state.aggregatedData.first()
                            androidx.compose.material3.Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                    containerColor = when {
                                        latest.severityAvg >= 4.0 -> androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.2f)
                                        latest.severityAvg >= 2.5 -> androidx.compose.ui.graphics.Color.Yellow.copy(alpha = 0.3f)
                                        else -> androidx.compose.ui.graphics.Color.Green.copy(alpha = 0.2f)
                                    }
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Current Traffic: Route ${state.selectedRouteId}",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "Severity: %.1f / 5.0".format(latest.severityAvg),
                                        style = MaterialTheme.typography.headlineMedium
                                    )
                                    Text(
                                        text = "Based on ${latest.sampleCount} reports",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    latest.severityP50?.let { p50 ->
                                        Text(
                                            text = "Median (P50): %.1f".format(p50),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    latest.severityP90?.let { p90 ->
                                        Text(
                                            text = "P90: %.1f".format(p90),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    val minutesAgo = (System.currentTimeMillis() - latest.lastAggregatedAtMs) / 60000
                                    Text(
                                        text = "Updated $minutesAgo min ago",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = androidx.compose.ui.graphics.Color.Gray
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "No aggregated data yet for Route ${state.selectedRouteId}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = androidx.compose.ui.graphics.Color.Gray
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        // Historical Trends
                        Text("Recent History:", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))

                        if (state.aggregatedData.size > 1) {
                            androidx.compose.foundation.lazy.LazyColumn(
                                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                            ) {
                                items(state.aggregatedData.size) { index ->
                                    val agg = state.aggregatedData[index]
                                    androidx.compose.material3.Card(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            val timeStr = java.text.DateFormat.getTimeInstance(
                                                java.text.DateFormat.SHORT
                                            ).format(java.util.Date(agg.windowStartMs))
                                            Text(
                                                text = "Window: $timeStr",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = "Avg: %.1f | P50: %.1f | P90: %.1f".format(
                                                    agg.severityAvg,
                                                    agg.severityP50 ?: 0.0,
                                                    agg.severityP90 ?: 0.0
                                                ),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(
                                                text = "${agg.sampleCount} samples",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = androidx.compose.ui.graphics.Color.Gray
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Sample action to submit a fixed location (Colombo)
                        Button(onClick = { viewModel.submitUserLocation(6.9271, 79.8612, state.selectedRouteId) }) {
                            Text("Submit Sample Traffic Report")
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
                // Use the currently selected route from the ViewModel
                val currentRouteId = viewModel.uiState.value.selectedRouteId
                viewModel.submitUserLocation(loc.latitude, loc.longitude, currentRouteId)
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
