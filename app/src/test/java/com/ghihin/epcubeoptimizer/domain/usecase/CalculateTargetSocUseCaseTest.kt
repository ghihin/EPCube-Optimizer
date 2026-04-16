package com.ghihin.epcubeoptimizer.domain.usecase

import com.ghihin.epcubeoptimizer.calendar.DailySchedule
import com.ghihin.epcubeoptimizer.domain.model.WeatherForecast
import com.ghihin.epcubeoptimizer.domain.repository.WeatherRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CalculateTargetSocUseCaseTest {

    private fun setupUseCase(mockForecast: Result<WeatherForecast>?): CalculateTargetSocUseCase {
        val fakeRepo = object : WeatherRepository {
            override suspend fun getWeatherForecast(lat: Double, lon: Double): Result<WeatherForecast> = mockForecast ?: Result.failure(Exception())
            override suspend fun getWeatherForecast(date: LocalDate): WeatherForecast = mockForecast?.getOrNull() ?: throw Exception()
        }
        return CalculateTargetSocUseCase(fakeRepo)
    }

    @Test
    fun `APIエラー時のフォールバック - 目標SOC100パーセントのスマートモード`() = runBlocking {
        val useCase = setupUseCase(Result.failure(Exception("API Error")))
        val schedule = DailySchedule(date = LocalDate.of(2026, 5, 20), isCommute = true)
        
        val result = useCase(schedule)
        assertEquals(100, result.targetSoc)
        assertFalse(result.isSunnyTomorrow)
    }

    @Test
    fun `中間期・晴れの場合 - グリーンモード(60パーセント)・スケジュール問わず`() = runBlocking {
        val date = LocalDate.of(2026, 5, 20) // 中間期
        val forecast = Result.success(WeatherForecast(date, shortwaveRadiationSum = 4500.0))
        val useCase = setupUseCase(forecast)
        
        // 出社
        val resultCommute = useCase(DailySchedule(date, isCommute = true))
        assertEquals(60, resultCommute.targetSoc)
        assertTrue(resultCommute.isSunnyTomorrow)
        
        // 在宅
        val resultHome = useCase(DailySchedule(date, isCommute = false))
        assertEquals(60, resultHome.targetSoc)
        assertTrue(resultHome.isSunnyTomorrow)
    }

    @Test
    fun `中間期・くもりの場合 - 在宅70パーセント、出社60パーセント`() = runBlocking {
        val date = LocalDate.of(2026, 5, 20) // 中間期
        val forecast = Result.success(WeatherForecast(date, shortwaveRadiationSum = 2500.0)) // THRESHOLD_CLOUDY と THRESHOLD_SUNNY の間
        val useCase = setupUseCase(forecast)
        
        val resultHome = useCase(DailySchedule(date, isCommute = false))
        assertEquals(70, resultHome.targetSoc)
        assertFalse(resultHome.isSunnyTomorrow)

        val resultCommute = useCase(DailySchedule(date, isCommute = true))
        assertEquals(60, resultCommute.targetSoc)
    }

    @Test
    fun `冬季・悪天候の場合 - 在宅100パーセント、出社90パーセント`() = runBlocking {
        val date = LocalDate.of(2026, 1, 15) // 冬季
        val forecast = Result.success(WeatherForecast(date, shortwaveRadiationSum = 1000.0)) // 悪天候
        val useCase = setupUseCase(forecast)
        
        val resultHome = useCase(DailySchedule(date, isCommute = false))
        assertEquals(100, resultHome.targetSoc)

        val resultCommute = useCase(DailySchedule(date, isCommute = true))
        assertEquals(90, resultCommute.targetSoc)
    }

    @Test
    fun `夏季・晴れの場合 - 在宅80パーセント、出社60パーセント`() = runBlocking {
        val date = LocalDate.of(2026, 8, 10) // 夏季
        val forecast = Result.success(WeatherForecast(date, shortwaveRadiationSum = 5000.0)) // 晴れ
        val useCase = setupUseCase(forecast)
        
        val resultHome = useCase(DailySchedule(date, isCommute = false))
        assertEquals(80, resultHome.targetSoc)
        assertTrue(resultHome.isSunnyTomorrow)

        val resultCommute = useCase(DailySchedule(date, isCommute = true))
        assertEquals(60, resultCommute.targetSoc)
    }
}
