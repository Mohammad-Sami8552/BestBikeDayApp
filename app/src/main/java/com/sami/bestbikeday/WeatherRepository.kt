package com.sami.bestbikeday

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.awaitResponse

object WeatherRepository {
    private const val BASE_URL = "https://api.weatherapi.com/v1/"
    // TODO: Insert your API key here
    private const val API_KEY = BuildConfig.API_KEY

    private val api: WeatherApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApiService::class.java)
    }

    suspend fun get7DayForecast(location: String): WeatherResponse? {
        return try {
            val response = api.get7DayForecast(API_KEY, location).awaitResponse()
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            null
        }
    }
}
