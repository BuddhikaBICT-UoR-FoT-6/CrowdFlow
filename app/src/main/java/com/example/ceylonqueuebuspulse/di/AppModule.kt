// Edited: 2026-01-07
// Purpose: Hilt DI module that provides singletons for Room, Retrofit API, and TrafficRepository.

package com.example.ceylonqueuebuspulse.di

import android.content.Context
import com.example.ceylonqueuebuspulse.data.local.AppDatabase
import com.example.ceylonqueuebuspulse.data.local.TrafficReportDao
import com.example.ceylonqueuebuspulse.data.network.RetrofitProvider
import com.example.ceylonqueuebuspulse.data.network.TrafficApi
import com.example.ceylonqueuebuspulse.data.repository.TrafficRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * Hilt module providing app-wide singletons.
 *
 * Provided dependencies:
 * - Retrofit + [TrafficApi] for remote sync
 * - Room [AppDatabase] + [TrafficReportDao] for offline persistence
 * - [TrafficRepository] as the single source of truth
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // --- Networking ---

    /** Provide configured Retrofit singleton. */
    @Provides
    @Singleton
    fun provideRetrofit(): Retrofit {
        // Centralized creation (BASE_URL + Moshi converter + OkHttp timeouts) lives in RetrofitProvider.
        return RetrofitProvider.retrofit()
    }

    /** Provide the Retrofit API interface implementation. */
    @Provides
    @Singleton
    fun provideTrafficApi(retrofit: Retrofit): TrafficApi =
        retrofit.create(TrafficApi::class.java)

    // --- Local persistence (Room) ---

    /** Provide the Room database singleton. */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.get(context)

    /** Provide the DAO used to read/write traffic reports. */
    @Provides
    @Singleton
    fun provideTrafficReportDao(db: AppDatabase): TrafficReportDao =
        db.trafficReportDao()

    // --- Repository ---

    /** Provide the repository used by ViewModels/Workers. */
    @Provides
    @Singleton
    fun provideTrafficRepository(
        dao: TrafficReportDao,
        api: TrafficApi,
        @ApplicationContext context: Context
    ): TrafficRepository = TrafficRepository(
        dao = dao,
        api = api,
        appContext = context
    )
}