package com.ghihin.epcubeoptimizer.domain.repository

import com.ghihin.epcubeoptimizer.domain.model.WeatherForecast

interface WeatherRepository {
    suspend fun getWeatherForecast(lat: Double, lon: Double): Result<WeatherForecast>
}
