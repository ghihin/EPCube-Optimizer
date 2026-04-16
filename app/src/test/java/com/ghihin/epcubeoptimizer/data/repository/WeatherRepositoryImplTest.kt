package com.ghihin.epcubeoptimizer.data.repository

import com.ghihin.epcubeoptimizer.core.network.HourlyData
import com.ghihin.epcubeoptimizer.core.network.OpenMeteoApi
import com.ghihin.epcubeoptimizer.core.network.OpenMeteoResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class WeatherRepositoryImplTest {

    private val fixedZone: ZoneId = ZoneOffset.UTC

    @Test
    fun `getWeatherForecast - 翌日08時から15時の短波放射量を合算して返す`() = runBlocking {
        val tomorrow = LocalDate.now(fixedZone).plusDays(1)
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        
        val timeList = mutableListOf<String>()
        val radList = mutableListOf<Double>()
        
        // 0時から23時までのデータを作る
        for (i in 0..23) {
            val ldt = tomorrow.atTime(i, 0)
            timeList.add(ldt.format(formatter))
            
            // 8時〜15時までは 500.0, それ以外は 0.0 とする
            if (i in 8..15) {
                radList.add(500.0)
            } else {
                radList.add(0.0)
            }
        }
        
        val mockApi = object : OpenMeteoApi {
            override suspend fun getForecast(
                latitude: Double, longitude: Double, hourly: String, timezone: String
            ): OpenMeteoResponse = OpenMeteoResponse(
                hourly = HourlyData(time = timeList, shortwaveRadiation = radList)
            )
        }

        val repository = WeatherRepositoryImpl(mockApi, fixedZone)
        val result = repository.getWeatherForecast(0.0, 0.0)

        assertTrue("成功でなければならない", result.isSuccess)
        val forecast = result.getOrNull()!!
        
        // 500.0 が 8時間分 で 4000.0
        assertEquals(4000.0, forecast.shortwaveRadiationSum, 0.1)
        assertEquals("日付は翌日", tomorrow, forecast.date)
    }

    @Test
    fun `getWeatherForecast - データがない場合は失敗を返す`() = runBlocking {
        val mockApi = object : OpenMeteoApi {
            override suspend fun getForecast(
                latitude: Double, longitude: Double, hourly: String, timezone: String
            ): OpenMeteoResponse = OpenMeteoResponse(
                hourly = HourlyData(time = emptyList(), shortwaveRadiation = emptyList())
            )
        }

        val repository = WeatherRepositoryImpl(mockApi, fixedZone)
        val result = repository.getWeatherForecast(0.0, 0.0)

        assertTrue("失敗でなければならない", result.isFailure)
    }

    @Test
    fun `getWeatherForecast - API例外発生時、失敗を返す`() = runBlocking {
        val mockApi = object : OpenMeteoApi {
            override suspend fun getForecast(
                latitude: Double, longitude: Double, hourly: String, timezone: String
            ): OpenMeteoResponse = throw RuntimeException("ネットワークエラー")
        }

        val repository = WeatherRepositoryImpl(mockApi, fixedZone)
        val result = repository.getWeatherForecast(0.0, 0.0)

        assertTrue("失敗でなければならない", result.isFailure)
    }
}
