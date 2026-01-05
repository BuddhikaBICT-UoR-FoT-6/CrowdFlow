// Edited: 2026-01-05
// Purpose: Provide a Retrofit singleton configured with Moshi for JSON parsing.

package com.example.ceylonqueuebuspulse.data.network

import com.squareup.moshi.Moshi
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

// Simple Retrofit provider. Replace BASE_URL with your backend URL.
object RetrofitProvider {
    private const val BASE_URL = "https://api.example.com/" // TODO: set real backend URL

    // Build a Moshi instance for Kotlin JSON serialization/deserialization.
    private val moshi: Moshi = Moshi.Builder().build()

    // Lazy Retrofit API client to avoid unnecessary initialization cost.
    val api: TrafficApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TrafficApi::class.java)
    }
}
