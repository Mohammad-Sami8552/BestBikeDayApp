package com.sami.bestbikeday

import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.Call

interface WeatherApiService {
    @GET("forecast.json")
    fun get7DayForecast(
        @Query("key") apiKey: String,
        @Query("q") location: String,
        @Query("days") days: Int = 7
    ): Call<WeatherResponse>
}
