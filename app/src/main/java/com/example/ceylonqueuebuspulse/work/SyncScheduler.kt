// Edited: 2026-01-06
// Purpose: Helper to schedule periodic SyncWorker with WorkManager (network required + backoff).

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
    private const val UNIQUE_AGG_WORK_NAME = "traffic_aggregate_sync_periodic"

    /**
     * Schedule periodic background sync.
     * Note: WorkManager enforces a minimum periodic interval of 15 minutes.
     */
    fun schedule(context: Context) {
        // Only run when the device has an active network connection.
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

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

        // Aggregate + sync (remote aggregated truth -> local overwrite)
        val aggRequest = PeriodicWorkRequestBuilder<FirestoreAggregationSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.LINEAR,
                30, TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                UNIQUE_AGG_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                aggRequest
            )
    }
}
