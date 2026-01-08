package com.example.ceylonqueuebuspulse.di

import com.example.ceylonqueuebuspulse.data.local.AppDatabase
import com.example.ceylonqueuebuspulse.data.network.RetrofitProvider
import com.example.ceylonqueuebuspulse.data.network.model.MongoApi
import com.example.ceylonqueuebuspulse.data.repository.TrafficRepository
import com.example.ceylonqueuebuspulse.data.repository.TrafficAggregationRepository
import com.example.ceylonqueuebuspulse.ui.TrafficViewModel
import com.example.ceylonqueuebuspulse.util.ConnectivityMonitor
import com.example.ceylonqueuebuspulse.util.RetryPolicy
import com.example.ceylonqueuebuspulse.work.AggregationPlannerWorker
import com.example.ceylonqueuebuspulse.work.MongoAggregationSyncWorker
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.androidx.workmanager.dsl.worker
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Central Koin module definitions with error handling and retry logic support.
 */
val appModule = module {
    // --- Networking & Connectivity ---
    single(named("mongo")) { RetrofitProvider.mongoRetrofit() }
    single<MongoApi> { get<retrofit2.Retrofit>(named("mongo")).create(MongoApi::class.java) }
    single { ConnectivityMonitor(androidContext()) }
    single { RetryPolicy.DEFAULT }

    // --- Local persistence (Room) ---
    single { AppDatabase.get(androidContext()) }
    single { get<AppDatabase>().trafficReportDao() }
    single { get<AppDatabase>().aggregatedTrafficDao() }
    single { get<AppDatabase>().syncMetaDao() }

    // --- Repositories ---
    single {
        TrafficAggregationRepository(
            mongoApi = get(),
            aggregatedTrafficDao = get(),
            syncMetaDao = get()
        )
    }

    single {
        TrafficRepository(
            dao = get(),
            appContext = androidContext(),
            aggregationRepository = get(),
            mongoApi = get()
        )
    }

    // --- ViewModels ---
    viewModel { TrafficViewModel(repository = get(), aggregationRepository = get()) }

    // --- WorkManager workers ---
    worker { AggregationPlannerWorker(appContext = get(), params = get()) }
    worker { MongoAggregationSyncWorker(appContext = get(), params = get()) }
}
