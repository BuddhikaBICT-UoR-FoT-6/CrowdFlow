// Edited: 2026-01-05
// Purpose: Room database providing DAOs for local persistence of traffic reports.

package com.example.ceylonqueuebuspulse.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Application-wide Room database. Holds the schema and serves DAO instances.
 *
 * Versioning: Start at version=1. Use migrations on schema changes to avoid destructive re-creates.
 */
@Database(entities = [TrafficReportEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase(){
    /** DAO for traffic report operations. */
    abstract fun trafficReportDao(): TrafficReportDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /** Returns a singleton database instance scoped to the application context. */
        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this){
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ceylon_queue_bus_pulse.db"
                )
                    // During active development, drop/recreate on schema mismatch to avoid crashes.
                    // Replace with proper migrations before release.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }

    }
}
