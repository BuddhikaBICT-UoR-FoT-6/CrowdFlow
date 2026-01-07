// Edited: 2026-01-06
// Purpose: Periodic background worker to sync remote traffic data and upsert into Room for offline-first UI.

package com.example.ceylonqueuebuspulse.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ceylonqueuebuspulse.data.repository.TrafficRepository

/**
 * WorkManager worker that runs periodically in the background to pull latest traffic reports
 * from the backend and store them in Room.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
    // Provided by Koin
    private val repository: TrafficRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        // Pull remote data and upsert into Room.
        repository.syncRemoteToLocal()
        Result.success()
    } catch (t: Throwable) {
        // Let WorkManager retry with backoff.
        Result.retry()
    }
}
