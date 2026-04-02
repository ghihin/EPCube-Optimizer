package com.ghihin.epcubeoptimizer.domain.repository

import com.ghihin.epcubeoptimizer.domain.model.WeatherForecast
import java.time.LocalDate

interface WeatherRepository {
    suspend fun getWeatherForecast(lat: Double, lon: Double): Result<WeatherForecast>
    suspend fun getWeatherForecast(date: LocalDate): WeatherForecast
}
