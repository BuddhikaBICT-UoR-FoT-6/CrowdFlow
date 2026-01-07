package com.example.ceylonqueuebuspulse

// Edited: 2026-01-07
// Purpose: Application entry point for Koin initialization and WorkManager integration.
// This wires Koin's WorkerFactory into WorkManager so workers can be created via Koin.

import android.app.Application
import com.example.ceylonqueuebuspulse.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

class CeylonQueueBusPulseApp : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@CeylonQueueBusPulseApp)
            // Let WorkManager create Koin-provided workers.
            workManagerFactory()
            modules(appModule)
        }
    }
}
