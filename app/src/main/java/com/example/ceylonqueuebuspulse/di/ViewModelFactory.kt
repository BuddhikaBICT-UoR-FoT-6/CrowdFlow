// Edited: 2026-01-05
// Purpose: Provide a ViewModelProvider.Factory that injects a Room-backed repository into TrafficViewModel.

package com.example.ceylonqueuebuspulse.di

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.ceylonqueuebuspulse.data.repository.TrafficRepository
import com.example.ceylonqueuebuspulse.data.local.AppDatabase
import com.example.ceylonqueuebuspulse.ui.TrafficViewModel

/**
 * Factory for creating [TrafficViewModel] with a Room-backed [TrafficRepository].
 *
 * Usage:
 * val viewModel by viewModels<TrafficViewModel> { TrafficViewModelFactory(application) }
 */
class TrafficViewModelFactory(app: Application) : ViewModelProvider.Factory {
    // Lazily build repository from the singleton Room database
    private val repo: TrafficRepository by lazy {
        val db = AppDatabase.get(app)
        TrafficRepository(db.trafficReportDao())
    }

    override fun <T : ViewModel> create(modelClass: Class<T>) : T {
        if(modelClass.isAssignableFrom(TrafficViewModel::class.java)){
            @Suppress("UNCHECKED_CAST")
            return TrafficViewModel(repo) as T
        } else {
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

}