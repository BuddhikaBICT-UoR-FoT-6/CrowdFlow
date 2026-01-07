// Edited: 2026-01-06
// Purpose: Periodic background worker to sync remote traffic data and upsert into Room for offline-first UI.

package com.example.ceylonqueuebuspulse.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ceylonqueuebuspulse.data.repository.TrafficRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager worker that runs periodically in the background to pull latest traffic reports
 * from the backend and store them in Room.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    // Repository is provided by Hilt (and internally uses Retrofit + Room DAO)
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
