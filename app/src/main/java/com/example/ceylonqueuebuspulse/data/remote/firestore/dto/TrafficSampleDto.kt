package com.example.ceylonqueuebuspulse.data.remote.firestore.dto

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

@IgnoreExtraProperties
data class TrafficSampleDto(
    @get:PropertyName("routeId") @set:PropertyName("routeId")
    var routeId: String = "",

    @get:PropertyName("windowStartMs") @set:PropertyName("windowStartMs")
    var windowStartMs: Long = 0L,

    // Optional segment bucket (e.g., geohash or custom segment id)
    @get:PropertyName("segmentId") @set:PropertyName("segmentId")
    var segmentId: String? = null,

    // Severity sample \[0..1\] or \[0..10\] (pick one and keep consistent)
    @get:PropertyName("severity") @set:PropertyName("severity")
    var severity: Double = 0.0,

    // Optional confidence/weight inputs
    @get:PropertyName("accuracyM") @set:PropertyName("accuracyM")
    var accuracyM: Double? = null,

    @get:PropertyName("userIdHash") @set:PropertyName("userIdHash")
    var userIdHash: String? = null,

    @get:PropertyName("reportedAtMs") @set:PropertyName("reportedAtMs")
    var reportedAtMs: Long = 0L


)
