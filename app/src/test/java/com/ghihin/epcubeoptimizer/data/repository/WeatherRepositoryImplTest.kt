package com.ghihin.epcubeoptimizer.data.repository

import com.ghihin.epcubeoptimizer.core.network.Clouds
import com.ghihin.epcubeoptimizer.core.network.ForecastItem
import com.ghihin.epcubeoptimizer.core.network.OpenWeatherMapApi
import com.ghihin.epcubeoptimizer.core.network.WeatherCondition as ApiWeatherCondition
import com.ghihin.epcubeoptimizer.core.network.WeatherResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * [WeatherRepositoryImpl] の単体テスト。
 * OpenWeatherMapApi をスタブで差し替えてロジックを検証する。
 */
class WeatherRepositoryImplTest {

    private val fixedZone: ZoneId = ZoneOffset.UTC

    /**
     * 翌日のUNIXタイムスタンプを生成するヘルパー。
     */
    private fun tomorrowEpoch(): Long {
        val tomorrow = LocalDate.now(fixedZone).plusDays(1)
        return tomorrow.atStartOfDay(fixedZone).toEpochSecond()
    }

    @Test
    fun `getWeatherForecast - 翌日データが存在する場合、最大雲量と最大降水確率を返す`() = runBlocking {
        val dt = tomorrowEpoch()
        val mockApi = object : OpenWeatherMapApi {
            override suspend fun getForecast(
                lat: Double, lon: Double, apiKey: String, units: String
            ): WeatherResponse = WeatherResponse(
                list = listOf(
                    ForecastItem(
                        dt = dt,
                        weather = listOf(ApiWeatherCondition(800, "Clear", "clear sky", "01d")),
                        clouds = Clouds(all = 10),
                        pop = 0.0f
                    ),
                    ForecastItem(
                        dt = dt + 10800, // +3時間
                        weather = listOf(ApiWeatherCondition(500, "Rain", "light rain", "10d")),
                        clouds = Clouds(all = 80),
                        pop = 0.6f
                    )
                )
            )
        }

        val repository = WeatherRepositoryImpl(mockApi, "dummy_key", fixedZone)
        val result = repository.getWeatherForecast(0.0, 0.0)

        assertTrue("成功でなければならない", result.isSuccess)
        val forecast = result.getOrNull()!!
        assertEquals("最大雲量は80", 80, forecast.cloudiness)
        assertEquals("最大降水確率は0.6f", 0.6f, forecast.probabilityOfPrecipitation)
        assertEquals("日付は翌日", LocalDate.now(fixedZone).plusDays(1), forecast.date)
    }

    @Test
    fun `getWeatherForecast - 翌日データが存在しない場合、先頭データにフォールバックする`() = runBlocking {
        // 今日または過去のタイムスタンプだけを返す（翌日データなし）
        val today = LocalDate.now(fixedZone).atStartOfDay(fixedZone).toEpochSecond()
        val mockApi = object : OpenWeatherMapApi {
            override suspend fun getForecast(
                lat: Double, lon: Double, apiKey: String, units: String
            ): WeatherResponse = WeatherResponse(
                list = listOf(
                    ForecastItem(
                        dt = today,
                        weather = listOf(ApiWeatherCondition(801, "Clouds", "few clouds", "02d")),
                        clouds = Clouds(all = 25),
                        pop = 0.1f
                    )
                )
            )
        }

        val repository = WeatherRepositoryImpl(mockApi, "dummy_key", fixedZone)
        val result = repository.getWeatherForecast(0.0, 0.0)

        assertTrue("成功でなければならない", result.isSuccess)
        val forecast = result.getOrNull()!!
        assertEquals("フォールバック時の雲量は25", 25, forecast.cloudiness)
        assertEquals("フォールバック時の降水確率は0.1f", 0.1f, forecast.probabilityOfPrecipitation)
    }

    @Test
    fun `getWeatherForecast - APIリストが空の場合、失敗を返す`() = runBlocking {
        val mockApi = object : OpenWeatherMapApi {
            override suspend fun getForecast(
                lat: Double, lon: Double, apiKey: String, units: String
            ): WeatherResponse = WeatherResponse(list = emptyList())
        }

        val repository = WeatherRepositoryImpl(mockApi, "dummy_key", fixedZone)
        val result = repository.getWeatherForecast(0.0, 0.0)

        assertTrue("失敗でなければならない", result.isFailure)
    }

    @Test
    fun `getWeatherForecast - API例外発生時、失敗を返す`() = runBlocking {
        val mockApi = object : OpenWeatherMapApi {
            override suspend fun getForecast(
                lat: Double, lon: Double, apiKey: String, units: String
            ): WeatherResponse = throw RuntimeException("ネットワークエラー")
        }

        val repository = WeatherRepositoryImpl(mockApi, "dummy_key", fixedZone)
        val result = repository.getWeatherForecast(0.0, 0.0)

        assertTrue("失敗でなければならない", result.isFailure)
    }
}
