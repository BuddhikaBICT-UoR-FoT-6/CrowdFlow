// Edited: 2026-01-08
// Purpose: Helper to schedule periodic SyncWorker and AggregationPlannerWorker with WorkManager (network required + backoff).

package com.example.ceylonqueuebuspulse.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncScheduler {
    private const val UNIQUE_WORK_NAME = "traffic_sync_periodic"
    private const val UNIQUE_PLANNER_WORK_NAME = "traffic_aggregation_planner_periodic"

    /**
     * Schedule periodic background sync and aggregation.
     * Note: WorkManager enforces a minimum periodic interval of 15 minutes.
     */
    fun schedule(context: Context) {
        // Only run when the device has an active network connection.
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Legacy sync worker (syncs raw reports from backend)
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            // Backoff applies to retries when the worker returns Result.retry().
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.LINEAR,
                30, TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )

        // Planner worker that determines active routes/windows and enqueues one-time aggregation workers
        val plannerRequest = PeriodicWorkRequestBuilder<AggregationPlannerWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.LINEAR,
                30, TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                UNIQUE_PLANNER_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                plannerRequest
            )
    }
}
