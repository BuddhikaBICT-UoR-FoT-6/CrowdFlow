// Edited: 2026-01-07
// Purpose: WorkManager worker that aggregates a time-window of traffic samples and syncs the aggregated result to Firestore (single source of truth).

package com.example.ceylonqueuebuspulse.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ceylonqueuebuspulse.data.repository.TrafficAggregationRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Background worker that performs "window aggregation" for a specific route.
 *
 * It fetches recent user-submitted samples for a given time window, computes a statistical
 * aggregate, writes that aggregate to Firestore as the source of truth, and then updates the
 * local Room cache from that remote truth.
 */
class FirestoreAggregationSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val aggregationRepo: TrafficAggregationRepository by inject()

    override suspend fun doWork(): Result {
        // Validate inputs so WorkManager chains can still treat this as a real unit of work.
        val routeId = inputData.getString("routeId") ?: return Result.failure()
        val windowStartMs = inputData.getLong("windowStartMs", -1L)
        if (windowStartMs <= 0L) return Result.failure()

        return try {
            aggregationRepo.aggregateAndSyncWindow(
                routeId = routeId,
                windowStartMs = windowStartMs,
                segmentId = null, // Aggregate all segments for now
                nowMs = System.currentTimeMillis()
            )
            Result.success()
        } catch (e: Exception) {
            // TODO: add logging
            Result.retry()
        }
    }
}