package com.example.ceylonqueuebuspulse

// Edited: 2026-01-07
// Purpose: Application entry point for Koin initialization and WorkManager integration.
// This wires Koin's WorkerFactory into WorkManager so workers can be created via Koin.

import android.app.Application
import com.example.ceylonqueuebuspulse.di.appModule
import com.example.ceylonqueuebuspulse.util.StrictModeConfig
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

class CeylonQueueBusPulseApp : Application() {

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.STRICT_MODE_ENABLED) {
            StrictModeConfig.enableForDebug()
        }

        // Crashlytics: add a couple of low-risk keys for easier triage (no-op if not present).
        try {
            val clazz = Class.forName("com.google.firebase.crashlytics.FirebaseCrashlytics")
            val instance = clazz.getMethod("getInstance").invoke(null)
            clazz.getMethod("setCustomKey", String::class.java, String::class.java)
                .invoke(instance, "buildType", BuildConfig.BUILD_TYPE)
            clazz.getMethod("setCustomKey", String::class.java, String::class.java)
                .invoke(instance, "versionName", BuildConfig.VERSION_NAME)
        } catch (_: Throwable) {
            // ignore
        }

        startKoin {
            androidLogger()
            androidContext(this@CeylonQueueBusPulseApp)
            // Let WorkManager create Koin-provided workers.
            workManagerFactory()
            modules(appModule)
        }
    }
}
