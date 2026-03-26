package com.ghihin.epcubeoptimizer.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * OpenWeatherMap Forecast API のRetrofitインターフェース。
 * `/data/2.5/forecast` エンドポイントを使用して3時間ごとの天気予報を取得する。
 */
interface OpenWeatherMapApi {
    @GET("data/2.5/forecast")
    suspend fun getForecast(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): WeatherResponse
}

/** APIレスポンスのルートオブジェクト */
@Serializable
data class WeatherResponse(
    val list: List<ForecastItem>
)

/** 3時間ごとの予報アイテム */
@Serializable
data class ForecastItem(
    /** UNIXタイムスタンプ（秒） */
    val dt: Long,
    /** 天気コンディションのリスト */
    val weather: List<WeatherCondition>,
    /** 雲量情報 */
    val clouds: Clouds,
    /** 降水確率 (0.0〜1.0) */
    val pop: Float
)

/** 天気コンディション */
@Serializable
data class WeatherCondition(
    val id: Int,
    val main: String,
    val description: String,
    val icon: String
)

/** 雲量情報 */
@Serializable
data class Clouds(
    /** 雲量 (0〜100%) */
    val all: Int
)
