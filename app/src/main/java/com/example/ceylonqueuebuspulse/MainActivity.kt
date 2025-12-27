// Edited: 2025-12-27
// Purpose: Main activity sets up Jetpack Compose UI and binds to TrafficViewModel to display bus traffic updates.

// Kotlin
package com.example.ceylonqueuebuspulse

// Android framework imports for Activity and lifecycle
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

// Compose UI building blocks and runtime state helpers
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// App theme import to style the UI consistently
import com.example.ceylonqueuebuspulse.ui.theme.CeylonQueueBusPulseTheme

// ViewModelProvider to obtain a ViewModel tied to Activity lifecycle
import androidx.lifecycle.ViewModelProvider
import com.example.ceylonqueuebuspulse.ui.TrafficViewModel

// Entry point Activity. Hosts the Compose UI and connects it to the ViewModel.
class MainActivity : ComponentActivity() {
    // onCreate: Activity lifecycle callback where we initialize UI content
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enables drawing under system bars and handles insets for modern UI
        enableEdgeToEdge()

        // Obtain ViewModel at the Activity scope to avoid Compose-specific import issues
        // This ensures the ViewModel survives configuration changes
        val vm: TrafficViewModel = ViewModelProvider(this)[TrafficViewModel::class.java]

        // setContent: Start Compose rendering and declare the UI tree
        setContent {
            // Apply app theme to Material components
            CeylonQueueBusPulseTheme {
                // Collect the UI state Flow from the ViewModel into Compose state
                // 'by' delegates to recompose when the Flow emits updates
                val state by vm.uiState.collectAsState()

                // Scaffold provides basic Material layout structure (top bars, content, etc.)
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Column arranges children vertically
                    Column(
                        modifier = Modifier
                            .padding(innerPadding) // Respect system bars and Scaffold paddings
                            .fillMaxSize()
                    ) {
                        // Title text for the screen
                        Text(
                            text = "Bus Traffic Updates",
                            style = MaterialTheme.typography.titleLarge
                        )

                        // Small vertical space between header and content
                        Spacer(Modifier.height(8.dp))

                        // Render each traffic report as a simple text line
                        state.reports.forEach { report ->
                            Text(
                                text = "Route: ${report.routeId} | Severity: ${report.severity} | Points: ${report.segment.size}"
                            )
                        }

                        // Extra spacing before the action button
                        Spacer(Modifier.height(16.dp))

                        // Button to submit a sample user location update (Colombo coordinates)
                        Button(onClick = { vm.submitUserLocation(6.9271, 79.8612, "route-1") }) {
                            Text("Submit Sample User Update (Colombo)")
                        }
                    }
                }
            }
        }
    }
}

// Preview composable for Android Studio to render a design-time snapshot of the UI
@Preview(showBackground = true)
@Composable
fun AppPreview() {
    CeylonQueueBusPulseTheme {
        Text("Bus Traffic Updates")
    }
}
