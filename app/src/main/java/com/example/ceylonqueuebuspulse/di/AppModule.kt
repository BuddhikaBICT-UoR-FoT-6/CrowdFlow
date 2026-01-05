// Edited: 2026-01-05
// Purpose: Lightweight manual DI wiring to provide repository and expose Retrofit API.

package com.example.ceylonqueuebuspulse.di

import android.content.Context
import com.example.ceylonqueuebuspulse.data.network.RetrofitProvider
import com.example.ceylonqueuebuspulse.data.repository.TrafficRepository
import com.example.ceylonqueuebuspulse.data.local.AppDatabase

/**
 * Simple DI module. Prefer replacing with Hilt in production for lifecycle-aware injection.
 */
object AppModule {
    /**
     * Provide a [TrafficRepository] backed by the singleton Room database instance.
     *
     * @param context Application or Activity context used to obtain the DB singleton.
     */
    fun repository(context: Context): TrafficRepository {
        val db = AppDatabase.get(context)
        return TrafficRepository(dao = db.trafficReportDao())
    }

    /** Retrofit API singleton, useful for manual wiring or testing without DI framework. */
    val api = RetrofitProvider.api
}