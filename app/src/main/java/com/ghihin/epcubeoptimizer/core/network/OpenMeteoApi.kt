package com.ghihin.epcubeoptimizer.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String = "shortwave_radiation",
        @Query("timezone") timezone: String = "Asia/Tokyo"
    ): OpenMeteoResponse
}

@Serializable
data class OpenMeteoResponse(
    val hourly: HourlyData
)

@Serializable
data class HourlyData(
    val time: List<String>,
    @SerialName("shortwave_radiation")
    val shortwaveRadiation: List<Double>
)
