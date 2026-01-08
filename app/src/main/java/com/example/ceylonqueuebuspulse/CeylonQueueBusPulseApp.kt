package com.example.ceylonqueuebuspulse

// Edited: 2026-01-08
// Purpose: Application entry point for Koin initialization, WorkManager integration, and Firebase Auth.
// Firebase Auth is initialized here so both UI and background workers have authenticated context.

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

        // Initialize Koin for dependency injection
        startKoin {
            androidLogger()
            androidContext(this@CeylonQueueBusPulseApp)
            // Let WorkManager create Koin-provided workers.
            workManagerFactory()
            modules(appModule)
        }

        // Initialize Firebase Auth anonymously so background workers have auth context
        Firebase.auth.signInAnonymously()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("CeylonQueueBusPulseApp", "Firebase anonymous sign-in successful")
                } else {
                    Log.e("CeylonQueueBusPulseApp", "Firebase anonymous sign-in failed", task.exception)
                }
            }
    }
}
