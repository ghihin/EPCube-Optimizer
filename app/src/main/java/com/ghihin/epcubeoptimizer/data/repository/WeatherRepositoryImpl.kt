package com.ghihin.epcubeoptimizer.data.repository

import com.ghihin.epcubeoptimizer.core.network.OpenMeteoApi
import com.ghihin.epcubeoptimizer.domain.model.WeatherForecast
import com.ghihin.epcubeoptimizer.domain.repository.WeatherRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * [WeatherRepository] の実装。
 * Open-Meteo API から翌日の天気予報（短波放射量）を取得し、ドメインモデルに変換する。
 */
class WeatherRepositoryImpl @Inject constructor(
    private val api: OpenMeteoApi,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : WeatherRepository {

    // デフォルトの緯度経度（鶴ヶ島市付近）
    private val defaultLat = 35.93
    private val defaultLon = 139.40

    override suspend fun getWeatherForecast(lat: Double, lon: Double): Result<WeatherForecast> {
        return try {
            val response = api.getForecast(lat, lon)

            // 翌日の日付を取得
            val tomorrow = LocalDate.now(zoneId).plusDays(1)
            val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

            var sumRadiation = 0.0
            var count = 0

            for (i in response.hourly.time.indices) {
                val timeStr = response.hourly.time[i]
                val radiation = response.hourly.shortwaveRadiation[i]

                val localDateTime = LocalDateTime.parse(timeStr, formatter)
                if (localDateTime.toLocalDate() == tomorrow) {
                    // 08:00 〜 15:59 までのデータを合算 (8〜15の8時間)
                    if (localDateTime.hour in 8..15) {
                        sumRadiation += radiation
                        count++
                    }
                }
            }

            if (count == 0) {
                return Result.failure(IllegalStateException("該当時間帯の天気予報データが見つかりませんでした"))
            }

            Result.success(
                WeatherForecast(
                    date = tomorrow,
                    shortwaveRadiationSum = sumRadiation,
                    weatherDescription = "日射量合計: ${sumRadiation.toInt()} W/m²・h"
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getWeatherForecast(date: LocalDate): WeatherForecast {
        val response = api.getForecast(defaultLat, defaultLon)

        val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        var sumRadiation = 0.0
        var count = 0

        for (i in response.hourly.time.indices) {
            val timeStr = response.hourly.time[i]
            val radiation = response.hourly.shortwaveRadiation[i]

            val localDateTime = LocalDateTime.parse(timeStr, formatter)
            if (localDateTime.toLocalDate() == date) {
                if (localDateTime.hour in 8..15) {
                    sumRadiation += radiation
                    count++
                }
            }
        }

        if (count == 0) {
            throw IllegalStateException("該当時間帯の天気予報データが見つかりませんでした")
        }

        return WeatherForecast(
            date = date,
            shortwaveRadiationSum = sumRadiation,
            weatherDescription = "日射量合計: ${sumRadiation.toInt()} W/m²・h"
        )
    }
}
