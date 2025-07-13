package com.sami.bestbikeday

import com.google.gson.annotations.SerializedName

// Root response
data class WeatherResponse(
    @SerializedName("location") val location: Location,
    @SerializedName("forecast") val forecast: Forecast
)

data class Location(
    @SerializedName("name") val name: String,
    @SerializedName("region") val region: String,
    @SerializedName("country") val country: String
)

data class Forecast(
    @SerializedName("forecastday") val forecastday: List<ForecastDay>
)

data class ForecastDay(
    @SerializedName("date") val date: String,
    @SerializedName("day") val day: Day
)

data class Day(
    @SerializedName("maxtemp_c") val maxTempC: Float,
    @SerializedName("mintemp_c") val minTempC: Float,
    @SerializedName("avgtemp_c") val avgTempC: Float,
    @SerializedName("maxwind_kph") val maxWindKph: Float,
    @SerializedName("daily_chance_of_rain") val dailyChanceOfRain: Int,
    @SerializedName("condition") val condition: Condition
)

data class Condition(
    @SerializedName("text") val text: String,
    @SerializedName("icon") val icon: String
)