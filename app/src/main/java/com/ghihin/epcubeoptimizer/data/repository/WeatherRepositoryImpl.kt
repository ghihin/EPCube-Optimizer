package com.ghihin.epcubeoptimizer.data.repository

import com.ghihin.epcubeoptimizer.core.network.OpenWeatherMapApi
import com.ghihin.epcubeoptimizer.domain.model.WeatherForecast
import com.ghihin.epcubeoptimizer.domain.repository.WeatherRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Named

/**
 * [WeatherRepository] の実装。
 * OpenWeatherMap Forecast API から翌日の天気予報を取得し、ドメインモデルに変換する。
 */
class WeatherRepositoryImpl @Inject constructor(
    private val api: OpenWeatherMapApi,
    @Named("apiKey") private val apiKey: String,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : WeatherRepository {

    // デフォルトの緯度経度（鶴ヶ島市付近）
    private val defaultLat = 35.93
    private val defaultLon = 139.40

    override suspend fun getWeatherForecast(lat: Double, lon: Double): Result<WeatherForecast> {
        return try {
            val response = api.getForecast(lat, lon, apiKey)

            // 翌日の日付を取得
            val tomorrow = LocalDate.now(zoneId).plusDays(1)

            // 翌日に該当する予報アイテムを絞り込む
            val tomorrowItems = response.list.filter { item ->
                val itemDate = Instant.ofEpochSecond(item.dt)
                    .atZone(zoneId)
                    .toLocalDate()
                itemDate == tomorrow
            }

            // 翌日データが存在しない場合はリスト先頭のデータにフォールバック
            val targetItems = tomorrowItems.ifEmpty { response.list.take(1) }

            if (targetItems.isEmpty()) {
                return Result.failure(IllegalStateException("天気予報データが取得できませんでした"))
            }

            // 翌日全体の最大雲量と最大降水確率をそれぞれ代表値として使用
            val maxCloudiness = targetItems.maxOf { it.clouds.all }
            val maxPop = targetItems.maxOf { it.pop }
            
            // 天気の説明を取得（最も頻出するもの、または最初のもの）
            val weatherDescription = targetItems.firstOrNull()?.weather?.firstOrNull()?.description ?: ""

            Result.success(
                WeatherForecast(
                    date = tomorrow,
                    cloudiness = maxCloudiness,
                    probabilityOfPrecipitation = maxPop,
                    weatherDescription = weatherDescription
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getWeatherForecast(date: LocalDate): WeatherForecast {
        val response = api.getForecast(defaultLat, defaultLon, apiKey)

        val targetItems = response.list.filter { item ->
            val itemDate = Instant.ofEpochSecond(item.dt)
                .atZone(zoneId)
                .toLocalDate()
            itemDate == date
        }

        val itemsToUse = targetItems.ifEmpty { response.list.take(1) }

        if (itemsToUse.isEmpty()) {
            throw IllegalStateException("天気予報データが取得できませんでした")
        }

        val maxCloudiness = itemsToUse.maxOf { it.clouds.all }
        val maxPop = itemsToUse.maxOf { it.pop }
        val weatherDescription = itemsToUse.firstOrNull()?.weather?.firstOrNull()?.description ?: ""

        return WeatherForecast(
            date = date,
            cloudiness = maxCloudiness,
            probabilityOfPrecipitation = maxPop,
            weatherDescription = weatherDescription
        )
    }
}
