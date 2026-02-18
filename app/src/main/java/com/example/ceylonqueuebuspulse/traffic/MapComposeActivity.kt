package com.example.ceylonqueuebuspulse.traffic

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ceylonqueuebuspulse.MainActivity
import com.example.ceylonqueuebuspulse.R
import com.example.ceylonqueuebuspulse.data.auth.PendingDeepLinkStore
import com.example.ceylonqueuebuspulse.ui.auth.AuthViewModel
import com.example.ceylonqueuebuspulse.util.HeadingProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.json.JSONArray
import org.json.JSONObject
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import androidx.lifecycle.lifecycleScope

class MapComposeActivity : ComponentActivity() {
    private val vm: MapComposeViewModel by viewModel()
    private val locVm: LocationTrafficViewModel by viewModel()

    // Auth for logout
    private val authVm: AuthViewModel by viewModel()
    private val pendingDeepLinkStore: PendingDeepLinkStore by inject()

    private lateinit var fusedClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* handled in compose via state */ }

    private var headingJob: Job? = null
    private var latestHeadingDeg: Float = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        // Start heading updates (best-effort; may be unavailable on some devices).
        try {
            val headingProvider = HeadingProvider(applicationContext)
            headingJob = headingProvider.headings()
                .distinctUntilChanged()
                .onEach { latestHeadingDeg = it }
                .launchIn(lifecycleScope)
        } catch (_: Throwable) {
            // ignore
        }

        val initialRouteId = intent.getStringExtra(EXTRA_ROUTE_ID)?.trim().takeUnless { it.isNullOrEmpty() } ?: "138"

        setContent {
            MapComposeScreen(
                vm = vm,
                locVm = locVm,
                initialRouteId = initialRouteId,
                onRequestLocationPermission = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                onStartLocation = { webView -> startLocationUpdates(webView) },
                onStopLocation = { stopLocationUpdates() },
                onMapClick = { lat, lon ->
                    locVm.selectLocation(lat, lon)
                },
                onPlaceSelected = { place ->
                    vm.selectPlace(place)
                },
                onLogout = {
                    // Clear any pending deep links to avoid stale navigation
                    pendingDeepLinkStore.clear()

                    // Clear the session and navigate to the login/register screen
                    authVm.logout()
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                }
            )
        }
    }

    companion object {
        const val EXTRA_ROUTE_ID = "extra_route_id"
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(webView: WebView) {
        if (locationCallback != null) return

        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3_000L)
            .setMinUpdateIntervalMillis(2_000L)
            .setMaxUpdateDelayMillis(6_000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val heading = latestHeadingDeg
                webView.evaluateJavascript("setUserLocation(${loc.latitude}, ${loc.longitude}, ${heading})", null)
            }
        }

        fusedClient.requestLocationUpdates(request, locationCallback as LocationCallback, mainLooper)
    }

    private fun stopLocationUpdates() {
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        locationCallback = null
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        headingJob?.cancel()
        headingJob = null
    }
}

@Composable
fun MapComposeScreen(
    vm: MapComposeViewModel,
    locVm: LocationTrafficViewModel,
    initialRouteId: String,
    onRequestLocationPermission: () -> Unit,
    onStartLocation: (WebView) -> Unit,
    onStopLocation: () -> Unit,
    onMapClick: (Double, Double) -> Unit,
    onPlaceSelected: (PlaceResult) -> Unit,
    onLogout: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val places by vm.places.collectAsState(initial = emptyList())
    val status by vm.status.collectAsState(initial = null)
    val provider by locVm.provider.collectAsState(initial = null)
    val routePoints by locVm.routePoints.collectAsState(initial = emptyList())

    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val fineGranted = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    val coarseGranted = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    fun refreshPermissionState() {
        fineGranted.value = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        coarseGranted.value = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    val locationEnabled = remember {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        mutableStateOf(lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true || lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true)
    }

    var selectedRouteId by remember { mutableStateOf(initialRouteId) }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = stringResource(id = R.string.app_name))
            OutlinedButton(onClick = onLogout) { Text(stringResource(id = R.string.action_logout)) }
        }

        Spacer(Modifier.height(8.dp))

        // Search
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f))
            Button(onClick = { vm.search(query, "") }) { Text("Search") }
        }

        Spacer(Modifier.height(8.dp))

        // Route selector (drives which points are displayed on the map)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("138", "174", "177", "120").forEach { routeId ->
                OutlinedButton(
                    onClick = { selectedRouteId = routeId },
                    enabled = selectedRouteId != routeId
                ) { Text(routeId) }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Location controls
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                refreshPermissionState()
                if (!(fineGranted.value || coarseGranted.value)) {
                    onRequestLocationPermission()
                } else {
                    webViewRef?.let { onStartLocation(it) }
                }
            }) {
                Text(stringResource(id = R.string.action_enable))
            }

            OutlinedButton(onClick = {
                webViewRef?.evaluateJavascript("centerOnUser()", null)
            }) {
                Text(stringResource(id = R.string.action_center_on_me))
            }
        }

        if (!(fineGranted.value || coarseGranted.value)) {
            Text(
                text = stringResource(id = R.string.location_permission_required),
                style = MaterialTheme.typography.bodySmall
            )
        } else if (!locationEnabled.value) {
            Text(
                text = stringResource(id = R.string.location_services_off),
                style = MaterialTheme.typography.bodySmall
            )
        }

        status?.let { Text(it) }

        // WebView hosting a Leaflet map loaded from assets/leaflet_map.html
        AndroidView(
            factory = { ctx ->
                val wv = WebView(ctx)
                webViewRef = wv
                setupWebViewForLeaflet(wv, onMapClick = { lat, lon -> onMapClick(lat, lon) })

                refreshPermissionState()
                if (fineGranted.value || coarseGranted.value) {
                    onStartLocation(wv)
                }
                wv
            },
            update = { wv ->
                // keep reference fresh for LaunchedEffects
                webViewRef = wv
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
        )

        // Show a simple example of provider data after tap (keeps unified pipeline visible)
        provider?.let {
            Spacer(Modifier.height(8.dp))
            Text(text = "Provider data received for selected point", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(places) { p ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = p.label)
                        Spacer(Modifier.height(4.dp))
                        Text(text = "${p.lat}, ${p.lon}")
                    }
                    Button(onClick = {
                        webViewRef?.evaluateJavascript("setLocation(${p.lat}, ${p.lon}, ${escapeJsString(p.label)})", null)
                        onPlaceSelected(p)
                    }) { Text("Select") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { vm.submitSampleForPlace(p, 3) }) { Text("Submit") }
                }
            }
        }
    }

    // Load route points whenever selected route changes.
    LaunchedEffect(selectedRouteId) {
        locVm.loadRoutePoints(routeId = selectedRouteId, maxPoints = 12)
        webViewRef?.evaluateJavascript("setRoute('${selectedRouteId}')", null)
    }

    // When points change, push them into the map.
    LaunchedEffect(routePoints, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (routePoints.isEmpty()) return@LaunchedEffect

        val arr = JSONArray()
        for (p in routePoints) {
            val obj = JSONObject()
            obj.put("lat", p.lat)
            obj.put("lon", p.lon)
            obj.put("label", "Route ${selectedRouteId}")
            obj.put("severity", p.severity ?: 2.0)
            arr.put(obj)
        }

        // Avoid quoting/escaping issues by passing JSON as a JS string literal via JSON.stringify
        val json = arr.toString()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "")
            .replace("\r", "")

        wv.evaluateJavascript("setTrafficPoints(\"$json\")", null)
    }

    DisposableEffect(Unit) {
        onDispose {
            onStopLocation()
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun setupWebViewForLeaflet(wv: WebView, onMapClick: (Double, Double) -> Unit) {
    val settings: WebSettings = wv.settings
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.loadWithOverviewMode = true
    settings.useWideViewPort = true

    wv.webChromeClient = WebChromeClient()
    wv.webViewClient = WebViewClient()

    @Suppress("unused")
    wv.addJavascriptInterface(object {
        @JavascriptInterface
        fun onMapTap(lat: Double, lon: Double) {
            onMapClick(lat, lon)
        }
    }, "AndroidBridge")

    wv.loadUrl("file:///android_asset/leaflet_map.html")
}

private fun escapeJsString(s: String): String {
    return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'"
}
