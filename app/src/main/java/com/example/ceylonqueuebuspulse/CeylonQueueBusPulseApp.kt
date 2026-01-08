package com.example.ceylonqueuebuspulse

// Edited: 2026-01-08
// Purpose: Application entry point for Koin initialization, WorkManager integration, and Firebase Auth.
// This wires Koin's WorkerFactory into WorkManager so workers can be created via Koin.

import android.app.Application
import android.util.Log
import com.example.ceylonqueuebuspulse.di.appModule
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

class CeylonQueueBusPulseApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase Auth with anonymous sign-in
        // This prevents PERMISSION_DENIED errors when accessing Firestore
        Firebase.auth.signInAnonymously()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "✅ Firebase Auth anonymous sign-in successful")
                } else {
                    Log.e(TAG, "❌ Firebase Auth sign-in failed", task.exception)
                }
            }

        startKoin {
            androidLogger()
            androidContext(this@CeylonQueueBusPulseApp)
            // Let WorkManager create Koin-provided workers.
            workManagerFactory()
            modules(appModule)
        }
    }

    companion object {
        private const val TAG = "CeylonQueueBusPulseApp"
    }
}
