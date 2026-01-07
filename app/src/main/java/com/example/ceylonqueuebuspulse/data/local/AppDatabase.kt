// Edited: 2026-01-07
// Purpose: Room database providing DAOs for local persistence of traffic reports and aggregation data.

package com.example.ceylonqueuebuspulse.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.ceylonqueuebuspulse.data.local.dao.AggregatedTrafficDao
import com.example.ceylonqueuebuspulse.data.local.dao.SyncMetaDao
import com.example.ceylonqueuebuspulse.data.local.entity.AggregatedTrafficEntity
import com.example.ceylonqueuebuspulse.data.local.entity.SyncMetaEntity

/**
 * Application-wide Room database. Holds the schema and serves DAO instances.
 *
 * Versioning: Updated to version=2 to include Phase 3 aggregation entities.
 */
@Database(
    entities = [
        TrafficReportEntity::class,
        AggregatedTrafficEntity::class,
        SyncMetaEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase(){
    /** DAO for traffic report operations. */
    abstract fun trafficReportDao(): TrafficReportDao

    /** DAO for aggregated traffic data (Phase 3). */
    abstract fun aggregatedTrafficDao(): AggregatedTrafficDao

    /** DAO for sync metadata (Phase 3). */
    abstract fun syncMetaDao(): SyncMetaDao

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
