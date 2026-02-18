package com.example.ceylonqueuebuspulse.traffic

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.koin.androidx.viewmodel.ext.android.viewModel

class MapComposeActivity : ComponentActivity() {
    private val vm: MapComposeViewModel by viewModel()
    private val locVm: LocationTrafficViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MapComposeScreen(vm = vm,
                onMapClick = { lat, lon ->
                    locVm.selectLocation(lat, lon)
                },
                onPlaceSelected = { place ->
                    // forward to viewmodel which triggers provider lookup
                    vm.selectPlace(place)
                },
                onRequestMove = { lat, lon, label ->
                    // no-op here; MapComposeScreen's internal WebView will handle move via callback
                }
            )
        }
    }
}

@Composable
fun MapComposeScreen(
    vm: MapComposeViewModel,
    onMapClick: (Double, Double) -> Unit,
    onPlaceSelected: (PlaceResult) -> Unit,
    onRequestMove: (Double, Double, String?) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val places by vm.places.collectAsState(initial = emptyList())
    val status by vm.status.collectAsState(initial = null)
    val context = LocalContext.current
    var webViewRef: WebView? = null

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f))
            Button(onClick = { vm.search(query, "") }) { Text("Search") }
        }

        status?.let { Text(it) }

        // WebView hosting a Leaflet map loaded from assets/leaflet_map.html
        AndroidView(factory = { ctx ->
            val wv = WebView(ctx)
            webViewRef = wv
            setupWebViewForLeaflet(wv, ctx) { lat, lon ->
                onMapClick(lat, lon)
            }
            wv
        }, modifier = Modifier
            .fillMaxWidth()
            .height(360.dp))

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(places) { p ->
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = p.label)
                        Spacer(Modifier.height(4.dp))
                        Text(text = "${p.lat}, ${p.lon}")
                    }
                    Button(onClick = {
                        // move map to place and notify VM
                        webViewRef?.evaluateJavascript("setLocation(${p.lat}, ${p.lon}, ${escapeJsString(p.label)})", null)
                        onPlaceSelected(p)
                    }) { Text("Select") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { vm.submitSampleForPlace(p, 3) }) { Text("Submit") }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun setupWebViewForLeaflet(wv: WebView, ctx: Context, onMapClick: (Double, Double) -> Unit) {
    val settings: WebSettings = wv.settings
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.loadWithOverviewMode = true
    settings.useWideViewPort = true

    wv.webChromeClient = WebChromeClient()
    wv.webViewClient = WebViewClient()
    wv.addJavascriptInterface(object {
        @JavascriptInterface
        fun onMapTap(lat: Double, lon: Double) {
            // Bridge called from JS when user taps the map
            onMapClick(lat, lon)
        }
    }, "AndroidBridge")

    // Load the asset
    wv.loadUrl("file:///android_asset/leaflet_map.html")
}

private fun escapeJsString(s: String): String {
    // Basic escaping for single-quoted JS string
    return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'"
}
