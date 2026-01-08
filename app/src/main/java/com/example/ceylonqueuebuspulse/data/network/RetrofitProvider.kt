// Edited: 2026-01-05
// Purpose: Provide a Retrofit singleton configured with Moshi for JSON parsing.

package com.example.ceylonqueuebuspulse.data.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

// Simple Retrofit provider. Replace BASE_URL with your backend URL.
object RetrofitProvider {
    const val BASE_URL = "https://api.example.com/" // TODO: set real backend URL

    // Build a Moshi instance for Kotlin JSON serialization/deserialization.
    // KotlinJsonAdapterFactory is required for proper Kotlin data class support.
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    // Lazy Retrofit API client to avoid unnecessary initialization cost.
    val api: TrafficApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TrafficApi::class.java)
    }

    fun retrofit(): Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .client(client)
        .build()
}
