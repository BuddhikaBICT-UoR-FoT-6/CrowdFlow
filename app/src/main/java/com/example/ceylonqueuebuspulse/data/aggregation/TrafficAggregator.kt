// Edited: 2026-01-07
// Purpose: Compute statistically stable traffic severity from many user samples using time-decayed weighting and percentiles.

package com.example.ceylonqueuebuspulse.data.aggregation

import com.example.ceylonqueuebuspulse.data.network.model.SubmitSampleRequest
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Aggregation utilities for traffic severity.
 *
 * Strategy:
 * - Time-decayed weighted average emphasizing recent samples.
 * - Optional GPS accuracy down-weighting.
 * - Simple nearest-rank percentiles (P50/P90) from raw severities.
 */
object TrafficAggregator {

    /** Aggregation result for one (routeId, windowStartMs, segmentId) bucket. */
    data class Result(
        val severityAvg: Double,
        val severityP50: Double?,
        val severityP90: Double?,
        val sampleCount: Long
    )

    /**
     * Aggregates user samples into a stable severity estimate.
     *
     * @param samples samples in the same (route, window, segment) bucket.
     * @param nowMs timestamp used as "current time" for computing sample age.
     * @param halfLifeMs half-life for exponential decay: at halfLife, weight halves.
     * @param baseWeight small constant so we never divide by zero.
     */
    fun aggregate(
        samples: List<SubmitSampleRequest>,
        nowMs: Long,
        halfLifeMs: Long = 15 * 60 * 1000L,
        baseWeight: Double = 1e-6
    ): Result {
        // --- Guard: no samples -> return neutral result. ---
        if (samples.isEmpty()) {
            return Result(
                severityAvg = 0.0,
                severityP50 = null,
                severityP90 = null,
                sampleCount = 0
            )
        }

        // --- Convert half-life to exponential decay coefficient. ---
        val lambda = ln(2.0) / max(1L, halfLifeMs).toDouble()

        var weightSum = 0.0
        var weightedSeveritySum = 0.0
        val severities = ArrayList<Double>(samples.size)

        // --- Weighted average computation + capture severities for percentile calculation. ---
        for (s in samples) {
            // Sample age in milliseconds (0 if sample is in the future due to clock skew).
            val ageMs = max(0L, nowMs - s.reportedAtMs)

            // Time-decay weights, so older samples influence the result less.
            val decay = exp(-lambda * ageMs.toDouble())

            // No accuracy field in SubmitSampleRequest, weight = decay.
            val w = max(baseWeight, decay)

            weightSum += w
            weightedSeveritySum += w * s.severity
            severities.add(s.severity)
        }

        // --- Percentiles (nearest-rank from sorted list). ---
        severities.sort()
        val p50 = percentile(sorted = severities, p = 50.0)
        val p90 = percentile(sorted = severities, p = 90.0)

        // --- Final weighted average. ---
        val avg = if (weightSum <= 0.0) 0.0 else (weightedSeveritySum / weightSum)

        return Result(
            severityAvg = avg,
            severityP50 = p50,
            severityP90 = p90,
            sampleCount = samples.size.toLong()
        )
    }

    /**
     * Nearest-rank percentile from an already-sorted list.
     *
     * Definition (nearest-rank):
     *  - rank = ceil(p/100 * N)
     *  - index = rank - 1
     *  - clamp to [0, N-1]
     */
    private fun percentile(sorted: List<Double>, p: Double): Double? {
        if (sorted.isEmpty()) return null

        val pp = min(100.0, max(0.0, p))
        val n = sorted.size
        val rank = kotlin.math.ceil((pp / 100.0) * n.toDouble()).toInt()
        val idx = min(n - 1, max(0, rank - 1))
        return sorted[idx]
    }
}
