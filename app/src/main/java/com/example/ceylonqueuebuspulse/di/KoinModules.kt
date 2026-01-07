package com.example.ceylonqueuebuspulse.di

import com.example.ceylonqueuebuspulse.data.local.AppDatabase
import com.example.ceylonqueuebuspulse.data.network.RetrofitProvider
import com.example.ceylonqueuebuspulse.data.network.TrafficApi
import com.example.ceylonqueuebuspulse.data.remote.firestore.FirestoreTrafficDataSource
import com.example.ceylonqueuebuspulse.data.repository.TrafficRepository
import com.example.ceylonqueuebuspulse.data.repository.TrafficAggregationRepository
import com.example.ceylonqueuebuspulse.ui.TrafficViewModel
import com.example.ceylonqueuebuspulse.work.FirestoreAggregationSyncWorker
import com.example.ceylonqueuebuspulse.work.SyncWorker
import com.google.firebase.firestore.FirebaseFirestore
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.worker
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Central Koin module definitions replacing the previous Hilt modules.
 */
val appModule = module {
    // --- Firebase ---
    single { FirebaseFirestore.getInstance() }

    // --- Networking ---
    single { RetrofitProvider.retrofit() }
    single<TrafficApi> { get<retrofit2.Retrofit>().create(TrafficApi::class.java) }

    // --- Local persistence (Room) ---
    single { AppDatabase.get(androidContext()) }
    single { get<AppDatabase>().trafficReportDao() }
    single { get<AppDatabase>().aggregatedTrafficDao() }
    single { get<AppDatabase>().syncMetaDao() }

    // --- Data sources / repositories ---
    single { FirestoreTrafficDataSource(get()) }
    single {
        TrafficRepository(
            dao = get(),
            api = get(),
            appContext = androidContext(),
            aggregationRepository = get()
        )
    }
    single {
        TrafficAggregationRepository(
            remote = get(),
            aggregatedTrafficDao = get(),
            syncMetaDao = get()
        )
    }

    // NOTE: Aggregation repositories/DAOs exist in source, but AppDatabase isn’t yet configured
    // with their entities/DAOs. Don’t bind them here until Room schema is updated.

    // --- ViewModels ---
    viewModel { TrafficViewModel(get()) }

    // --- WorkManager workers ---
    worker { SyncWorker(appContext = get(), params = get(), repository = get()) }
    worker { FirestoreAggregationSyncWorker(appContext = get(), params = get()) }
}
