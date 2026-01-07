// Edited: 2026-01-07
// Purpose: Deprecated manual ViewModel factory (pre-DI). Kept as reference only.
//
// NOTE:
// - The app now uses Koin to create TrafficViewModel.
// - If you ever remove Koin, you can re-enable a factory like this.

package com.example.ceylonqueuebuspulse.di

// Deprecated: keep imports commented out to avoid unused warnings and accidental usage.
// import android.app.Application
// import androidx.lifecycle.ViewModel
// import androidx.lifecycle.ViewModelProvider
// import com.example.ceylonqueuebuspulse.data.local.AppDatabase
// import com.example.ceylonqueuebuspulse.data.repository.TrafficRepository
// import com.example.ceylonqueuebuspulse.ui.TrafficViewModel

// class TrafficViewModelFactory(app: Application) : ViewModelProvider.Factory {
//     private val repo: TrafficRepository by lazy {
//         val db = AppDatabase.get(app)
//         TrafficRepository(db.trafficReportDao())
//     }
//
//     override fun <T : ViewModel> create(modelClass: Class<T>): T {
//         if (modelClass.isAssignableFrom(TrafficViewModel::class.java)) {
//             @Suppress("UNCHECKED_CAST")
//             return TrafficViewModel(repo) as T
//         } else {
//             throw IllegalArgumentException("Unknown ViewModel class")
//         }
//     }
// }
