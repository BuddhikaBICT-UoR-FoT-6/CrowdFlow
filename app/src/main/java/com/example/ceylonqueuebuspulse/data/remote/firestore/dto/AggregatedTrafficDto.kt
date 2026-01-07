// Edited: 2026-01-07
// Purpose: Firestore DTO for aggregated traffic statistics per route/window/segment.

package com.example.ceylonqueuebuspulse.data.remote.firestore.dto

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

/**
 * Aggregated traffic result stored in Firestore.
 *
 * Document location (by convention in this project):
 * routes/{routeId}/windows/{windowStartMs}/segments/{segmentKey}/stats/aggregate
 */
@IgnoreExtraProperties
data class AggregatedTrafficDto(
    // --- Identity ---
    @get:PropertyName("routeId") @set:PropertyName("routeId")
    var routeId: String = "",

    @get:PropertyName("windowStartMs") @set:PropertyName("windowStartMs")
    var windowStartMs: Long = 0L,

    // Optional segment bucket id (null means "_all" segment bucket)
    @get:PropertyName("segmentId") @set:PropertyName("segmentId")
    var segmentId: String? = null,

    // --- Aggregated outputs ---
    @get:PropertyName("severityAvg") @set:PropertyName("severityAvg")
    var severityAvg: Double = 0.0,

    // Percentiles can be missing when sampleCount == 0 or sample size too small.
    @get:PropertyName("severityP50") @set:PropertyName("severityP50")
    var severityP50: Double? = null,

    @get:PropertyName("severityP90") @set:PropertyName("severityP90")
    var severityP90: Double? = null,

    @get:PropertyName("sampleCount") @set:PropertyName("sampleCount")
    var sampleCount: Long = 0L,

    @get:PropertyName("lastAggregatedAtMs") @set:PropertyName("lastAggregatedAtMs")
    var lastAggregatedAtMs: Long = 0L
)
