package com.example.ceylonqueuebuspulse.traffic

import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import com.google.gson.annotations.SerializedName

data class ProviderResponse(val ok: Boolean, val mapped: Map<String, Any>?, val provider: Map<String, Any>?)
data class PostResult(val ok: Boolean, val inserted: Int)

interface BackendApi {
    @GET("api/v1/debug/provider/point")
    suspend fun getProviderPoint(@Query("lat") lat: Double, @Query("lon") lon: Double): ProviderResponse

    @POST("traffic/samples")
    suspend fun postSamples(@Body body: List<Map<String, Any>>) : PostResult

}

object ApiClient {
    private val retrofit = Retrofit.Builder()
        .baseUrl("http://10.0.2.2:3000/") // emulator -> host
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: BackendApi = retrofit.create(BackendApi::class.java)

}

