package com.ghihin.epcubeoptimizer.domain.model

import java.time.LocalDate

data class WeatherForecast(
    val date: LocalDate,
    val cloudiness: Int, // 0-100%
    val probabilityOfPrecipitation: Float, // 0.0-1.0
    val weatherDescription: String = "" // 天気の説明（例: "晴れ", "雨"）
) {
    init {
        require(cloudiness in 0..100) { "Cloudiness must be between 0 and 100" }
        require(probabilityOfPrecipitation in 0.0f..1.0f) { "Probability of precipitation must be between 0.0 and 1.0" }
    }
}
