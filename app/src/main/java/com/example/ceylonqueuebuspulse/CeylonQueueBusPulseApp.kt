package com.example.ceylonqueuebuspulse

// Edited: 2026-01-07
// Purpose: Application entry point for Hilt initialization and WorkManager integration.
// This wires Hilt's WorkerFactory into WorkManager so @HiltWorker Workers can be constructed with DI.

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CeylonQueueBusPulseApp : Application(), Configuration.Provider {
    // Injected factory that allows WorkManager to create Workers using Hilt (constructor injection).
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    // WorkManager 2.11+ API: Configuration.Provider exposes a property.
    // WorkManager will call this to obtain a Configuration, so it can create Workers via Hilt.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
