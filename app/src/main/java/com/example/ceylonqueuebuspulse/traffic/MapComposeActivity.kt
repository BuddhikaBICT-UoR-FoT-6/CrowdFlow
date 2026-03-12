package com.example.ceylonqueuebuspulse.traffic

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ceylonqueuebuspulse.BuildConfig
import com.example.ceylonqueuebuspulse.MainActivity
import com.example.ceylonqueuebuspulse.R
import com.example.ceylonqueuebuspulse.data.auth.PendingDeepLinkStore
import com.example.ceylonqueuebuspulse.settings.SettingsViewModel
import com.example.ceylonqueuebuspulse.settings.ThemeMode
import com.example.ceylonqueuebuspulse.ui.PrivacyPolicyActivity
import com.example.ceylonqueuebuspulse.ui.auth.AuthViewModel
import com.example.ceylonqueuebuspulse.ui.theme.CeylonQueueBusPulseTheme
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
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.lifecycleScope

class MapComposeActivity : ComponentActivity() {
    private val vm: MapComposeViewModel by viewModel()
    private val locVm: LocationTrafficViewModel by viewModel()

    // Auth for logout
    private val authVm: AuthViewModel by viewModel()
    private val settingsVm: SettingsViewModel by viewModel()
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
            val settings by settingsVm.settings.collectAsState()
            val isDark = when (settings.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            CeylonQueueBusPulseTheme(darkTheme = isDark) {
                MapComposeScreen(
                    vm = vm,
                    locVm = locVm,
                    settingsVm = settingsVm,
                    isDarkMode = isDark,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapComposeScreen(
    vm: MapComposeViewModel,
    locVm: LocationTrafficViewModel,
    settingsVm: SettingsViewModel,
    isDarkMode: Boolean,
    initialRouteId: String,
    onRequestLocationPermission: () -> Unit,
    onStartLocation: (WebView) -> Unit,
    onStopLocation: () -> Unit,
    onMapClick: (Double, Double) -> Unit,
    onPlaceSelected: (PlaceResult) -> Unit,
    onLogout: () -> Unit,
) {
    val routeVm: RouteCatalogViewModel = koinViewModel()
    val nearbyRoutes by routeVm.routes.collectAsState(initial = emptyList())
    val appSettings by settingsVm.settings.collectAsState()

    var query by remember { mutableStateOf("") }
    val places by vm.places.collectAsState(initial = emptyList())
    val status by vm.status.collectAsState(initial = null)
    val routePoints by locVm.routePoints.collectAsState(initial = emptyList())

    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Track when the HTML+Leaflet is fully loaded so we don't call JS too early.
    var isMapReady by remember { mutableStateOf(false) }

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

    // When nearby routes change, auto-select first if current selection isn't in the list.
    LaunchedEffect(nearbyRoutes) {
        if (nearbyRoutes.isNotEmpty()) {
            val contains = nearbyRoutes.any { it.ref == selectedRouteId }
            if (!contains) {
                selectedRouteId = nearbyRoutes.first().ref
            }
        }
    }

    // Capture user's current location from the WebView updates so the "Center on me" button can fetch traffic.
    var lastUserLatLon by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var didLoadNearbyForUserLocation by remember { mutableStateOf(false) }

    // Current selected point (from map tap or search select)
    var selectedPoint by remember { mutableStateOf<PlaceResult?>(null) }

    // User-report UI
    var showReportDialog by remember { mutableStateOf(false) }
    var reportSeverity by remember { mutableStateOf(3f) }

    var webViewLoadError by remember { mutableStateOf<String?>(null) }

    // Hamburger menu state
    var menuExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // --- MAP (dominant, full-screen) ---
        AndroidView(
            factory = { ctx ->
                val wv = WebView(ctx)
                WebView.setWebContentsDebuggingEnabled(true)
                webViewRef = wv
                setupWebViewForLeaflet(
                    wv,
                    onPageReady = {
                        isMapReady = true
                    },
                    onMapClick = { lat, lon ->
                        // Update nearby route chips for this tapped location
                        routeVm.loadNearby(lat, lon)
                        didLoadNearbyForUserLocation = true

                        val p = PlaceResult(label = "Dropped pin", lat = lat, lon = lon)
                        selectedPoint = p
                        onMapClick(lat, lon)
                        showReportDialog = true
                    },
                    onUserLocation = { lat, lon ->
                        lastUserLatLon = lat to lon
                        // Load nearby routes once on first location fix.
                        if (!didLoadNearbyForUserLocation) {
                            didLoadNearbyForUserLocation = true
                            routeVm.loadNearby(lat, lon)
                        }
                    },
                    onError = { msg -> webViewLoadError = msg }
                )

                refreshPermissionState()
                if (fineGranted.value || coarseGranted.value) {
                    onStartLocation(wv)
                }
                wv
            },
            update = { wv -> webViewRef = wv },
            modifier = Modifier.fillMaxSize()
        )

        // If WebView can't load Leaflet/tiles, show a helpful overlay instead of a silent blank screen.
        if (webViewLoadError != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Map failed to load", style = MaterialTheme.typography.titleMedium)
                    Text(webViewLoadError ?: "Unknown error")
                    Text("Common fixes: ensure emulator has internet access; disable proxy/VPN; try another network.")
                }
            }
        }

        // ═══════════════════════════════════════════════════════════
        // FLOATING SEARCH BAR (top center)
        // ═══════════════════════════════════════════════════════════
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 64.dp, top = 48.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                )
                IconButton(onClick = { vm.search(query, BuildConfig.TOMTOM_API_KEY) }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // ═══════════════════════════════════════════════════════════
        // FLOATING ROUTE CHIPS (below search bar)
        // Uses nearby routes when available, otherwise the user's
        // preferred (most-used) routes from settings.
        // ═══════════════════════════════════════════════════════════
        val preferredChips = appSettings.preferredRoutes.sorted().take(4).map { RouteChip(it) }
        val chips = when {
            nearbyRoutes.isNotEmpty() -> nearbyRoutes.take(4)
            preferredChips.isNotEmpty() -> preferredChips
            else -> emptyList()
        }
        if (chips.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 108.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                chips.forEach { chip ->
                    FilterChip(
                        selected = selectedRouteId == chip.ref,
                        onClick = {
                            selectedRouteId = chip.ref
                            // Auto-save to preferred routes so chips reflect most-used routes
                            val updated = (appSettings.preferredRoutes + chip.ref).take(8).toSet()
                            settingsVm.setPreferredRoutes(updated)
                        },
                        label = { Text(chip.ref, style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }

        // ═══════════════════════════════════════════════════════════
        // HAMBURGER FAB (top-right corner)
        // ═══════════════════════════════════════════════════════════
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 12.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { menuExpanded = true },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Menu, contentDescription = "Menu")
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                // User Management (Logout)
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_user_management)) },
                    onClick = {
                        menuExpanded = false
                        onLogout()
                    }
                )
                // Privacy Policy
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_privacy)) },
                    onClick = {
                        menuExpanded = false
                        context.startActivity(Intent(context, PrivacyPolicyActivity::class.java))
                    }
                )
                // Location Settings
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_location_settings)) },
                    onClick = {
                        menuExpanded = false
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                )
                HorizontalDivider()
                // Dark / Light mode toggle
                DropdownMenuItem(
                    text = {
                        Text(
                            if (isDarkMode) stringResource(R.string.menu_light_mode)
                            else stringResource(R.string.menu_dark_mode)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        settingsVm.setThemeMode(if (isDarkMode) ThemeMode.LIGHT else ThemeMode.DARK)
                    }
                )
            }
        }

        // ═══════════════════════════════════════════════════════════
        // STATUS & PERMISSION MESSAGES (below route chips)
        // ═══════════════════════════════════════════════════════════
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 148.dp, end = 16.dp)
        ) {
            status?.let {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                    )
                ) {
                    Text(it, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!(fineGranted.value || coarseGranted.value)) {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                    )
                ) {
                    Text(
                        stringResource(R.string.location_permission_required),
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            } else if (!locationEnabled.value) {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                    )
                ) {
                    Text(
                        stringResource(R.string.location_services_off),
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // ═══════════════════════════════════════════════════════════
        // CENTER ON ME FAB (bottom-left corner)
        // ═══════════════════════════════════════════════════════════
        FloatingActionButton(
            onClick = {
                refreshPermissionState()
                if (!(fineGranted.value || coarseGranted.value)) {
                    onRequestLocationPermission()
                    return@FloatingActionButton
                }

                webViewRef?.let { onStartLocation(it) }

                val coords = lastUserLatLon
                if (coords != null) {
                    // Update nearby route chips for this location
                    routeVm.loadNearby(coords.first, coords.second)

                    webViewRef?.evaluateJavascript("centerOnUser()", null)
                    val p = PlaceResult(label = "My location", lat = coords.first, lon = coords.second)
                    selectedPoint = p
                    locVm.selectLocation(coords.first, coords.second)
                    showReportDialog = true
                } else {
                    webViewRef?.evaluateJavascript("centerOnUser()", null)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 32.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = stringResource(R.string.action_center_on_me))
        }

        // ═══════════════════════════════════════════════════════════
        // SEARCH RESULTS PANEL (bottom card, only when we have results)
        // ═══════════════════════════════════════════════════════════
        if (places.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .padding(8.dp)
                ) {
                    items(places) { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = p.label)
                                Spacer(Modifier.height(2.dp))
                                Text(text = "${p.lat}, ${p.lon}", style = MaterialTheme.typography.bodySmall)
                            }
                            Button(onClick = {
                                selectedPoint = p
                                // Update nearby route chips for this chosen location
                                routeVm.loadNearby(p.lat, p.lon)

                                webViewRef?.evaluateJavascript("setLocation(${p.lat}, ${p.lon}, ${escapeJsString(p.label)})", null)
                                onPlaceSelected(p)
                                showReportDialog = true
                            }) { Text("Select") }
                        }
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════════
        // TRAFFIC REPORT DIALOG (on map tap / location select)
        // ═══════════════════════════════════════════════════════════
        if (showReportDialog) {
            val current = selectedPoint
            AlertDialog(
                onDismissRequest = { showReportDialog = false },
                title = { Text("Report traffic") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (current != null) {
                            Text("Location: ${current.label}")
                            Text("${current.lat}, ${current.lon}")
                        }

                        Text("Severity: ${reportSeverity.toInt()} (1=Low, 3=Medium, 5=High)")
                        Slider(
                            value = reportSeverity,
                            onValueChange = { reportSeverity = it },
                            valueRange = 1f..5f,
                            steps = 3
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val point = current
                            if (point != null) {
                                locVm.submitSample(
                                    routeId = selectedRouteId,
                                    severity = reportSeverity.toInt(),
                                    lat = point.lat,
                                    lon = point.lon
                                ) { _, _ ->
                                    // status already updated in locVm; keep UI simple
                                }
                            }
                            showReportDialog = false
                        }
                    ) {
                        Text("Submit")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showReportDialog = false }) { Text("Cancel") }
                }
            )
        }
    }

    // Load route points whenever selected route changes.
    LaunchedEffect(selectedRouteId, webViewRef, isMapReady) {
        locVm.loadRoutePoints(routeId = selectedRouteId, maxPoints = 12)
        val wv = webViewRef
        if (wv != null && isMapReady) {
            // Route IDs are strings ("138" etc.). Always quote them for JS.
            wv.evaluateJavascript("window.setRoute && window.setRoute('${selectedRouteId}')", null)
        }
    }

    // When points change, push them into the map.
    LaunchedEffect(routePoints, webViewRef, isMapReady) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (!isMapReady) return@LaunchedEffect
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

        wv.evaluateJavascript("window.setTrafficPoints && window.setTrafficPoints(\"$json\")", null)
    }

    // When map becomes ready, push the latest route/points (handles cold start reliably).
    LaunchedEffect(isMapReady) {
        if (!isMapReady) return@LaunchedEffect
        val wv = webViewRef ?: return@LaunchedEffect
        // Ensure current route gets drawn even if routePoints hasn't emitted yet.
        wv.evaluateJavascript("window.setRoute && window.setRoute('${selectedRouteId}')", null)
    }

    DisposableEffect(Unit) {
        onDispose { onStopLocation() }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun setupWebViewForLeaflet(
    wv: WebView,
    onPageReady: () -> Unit,
    onMapClick: (Double, Double) -> Unit,
    onUserLocation: (Double, Double) -> Unit,
    onError: (String) -> Unit
) {
    val settings: WebSettings = wv.settings
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.loadWithOverviewMode = true
    settings.useWideViewPort = true
    settings.cacheMode = WebSettings.LOAD_NO_CACHE

    // Helpful for debugging blank maps caused by blocked CDN assets.
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

    // Allow asset:// and file:// fetches (GeoJSON route assets, etc.)
    settings.allowFileAccess = true
    settings.allowContentAccess = true

    wv.webChromeClient = object : WebChromeClient() {
        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            Log.d(
                "LeafletWebView",
                "${consoleMessage.message()} (line ${consoleMessage.lineNumber()} @ ${consoleMessage.sourceId()})"
            )
            return super.onConsoleMessage(consoleMessage)
        }
    }

    wv.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            // The HTML is loaded; Leaflet script has run and window.* functions should exist now.
            onPageReady()
        }

        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            val msg = "WebView load error url=${request?.url} error=${error?.description}"
            Log.e("LeafletWebView", msg)
            onError(msg)
            super.onReceivedError(view, request, error)
        }

        override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
            val msg = "WebView HTTP error url=${request?.url} status=${errorResponse?.statusCode}"
            Log.e("LeafletWebView", msg)
            onError(msg)
            super.onReceivedHttpError(view, request, errorResponse)
        }
    }

    @Suppress("unused")
    wv.addJavascriptInterface(object {
        @JavascriptInterface
        fun onMapTap(lat: Double, lon: Double) {
            onMapClick(lat, lon)
        }

        @JavascriptInterface
        fun onUserLocation(lat: Double, lon: Double) {
            onUserLocation(lat, lon)
        }
    }, "AndroidBridge")

    wv.loadUrl("file:///android_asset/leaflet_map.html")
}

private fun escapeJsString(s: String): String {
    return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'"
}
