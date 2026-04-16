package com.ghihin.epcubeoptimizer.domain.model

import java.time.LocalDate

data class WeatherForecast(
    val date: LocalDate,
    val shortwaveRadiationSum: Double, // W/m²・h相当
    val weatherDescription: String = "" // 天気の説明（例: "晴れ", "雨"）
) {
    init {
        require(shortwaveRadiationSum >= 0.0) { "Shortwave radiation sum must be non-negative" }
    }
}
