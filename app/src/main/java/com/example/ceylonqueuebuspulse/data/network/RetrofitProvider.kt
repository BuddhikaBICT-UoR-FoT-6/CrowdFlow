// Edited: 2026-01-05
// Purpose: Provide a Retrofit singleton configured with Moshi for JSON parsing.

package com.example.ceylonqueuebuspulse.data.network

import com.example.ceylonqueuebuspulse.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

// Simple Retrofit provider. Replace BASE_URL with your backend URL.
object RetrofitProvider {
    private val mongoApiBaseUrl: String get() = BuildConfig.MONGO_API_BASE_URL

    // Build a Moshi instance for Kotlin JSON serialization/deserialization.
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    fun mongoRetrofit(): Retrofit = Retrofit.Builder()
        .baseUrl(mongoApiBaseUrl)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .client(client)
        .build()
}
