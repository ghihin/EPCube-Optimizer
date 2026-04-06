package com.ghihin.epcubeoptimizer.domain.usecase

import com.ghihin.epcubeoptimizer.domain.model.UserSchedule
import com.ghihin.epcubeoptimizer.domain.model.WeatherForecast
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class CalculateTargetSocUseCaseTest {

    private lateinit var useCase: CalculateTargetSocUseCase
    private val targetDate = LocalDate.of(2026, 3, 26) // Thursday

    @Before
    fun setup() {
        val fakeRepo = object : com.ghihin.epcubeoptimizer.domain.repository.WeatherRepository {
            override suspend fun getWeatherForecast(lat: Double, lon: Double): Result<WeatherForecast> = Result.failure(Exception())
            override suspend fun getWeatherForecast(date: LocalDate): WeatherForecast = throw Exception()
        }
        useCase = CalculateTargetSocUseCase(fakeRepo)
    }

    // --- 晴れ (Sunny) のテスト ---
    // 条件: cloudiness <= 30 かつ pop < 0.2
    // 冬(3月) の場合: 在宅100%, 出社70%

    @Test
    fun `晴れ かつ 在宅日 の場合、SOCは100%になること`() {
        val forecast = WeatherForecast(targetDate, cloudiness = 20, probabilityOfPrecipitation = 0.1f)
        val schedule = UserSchedule(defaultWeeklySchedule = mapOf(DayOfWeek.THURSDAY to false)) // 在宅

        val result = useCase(forecast, schedule, targetDate)

        assertEquals(100, result.value)
        assertEquals(20, result.factors.cloudiness)
        assertEquals(0.1f, result.factors.pop)
        assertEquals(false, result.factors.isCommuteDay)
    }

    @Test
    fun `晴れ かつ 出社日 の場合、SOCは70%になること`() {
        val forecast = WeatherForecast(targetDate, cloudiness = 30, probabilityOfPrecipitation = 0.19f)
        val schedule = UserSchedule(defaultWeeklySchedule = mapOf(DayOfWeek.THURSDAY to true)) // 出社

        val result = useCase(forecast, schedule, targetDate)

        assertEquals(70, result.value)
        assertEquals(true, result.factors.isCommuteDay)
    }

    // --- 雨・曇り (Not Sunny) のテスト ---
    // 冬の場合: 在宅100%, 出社90%

    @Test
    fun `雨(cloudiness大) かつ 在宅日 の場合、SOCは100%になること`() {
        val forecast = WeatherForecast(targetDate, cloudiness = 76, probabilityOfPrecipitation = 0.1f)
        val schedule = UserSchedule(defaultWeeklySchedule = mapOf(DayOfWeek.THURSDAY to false)) // 在宅

        val result = useCase(forecast, schedule, targetDate)

        assertEquals(100, result.value)
    }

    @Test
    fun `雨(pop大) かつ 在宅日 の場合、SOCは100%になること`() {
        val forecast = WeatherForecast(targetDate, cloudiness = 20, probabilityOfPrecipitation = 0.5f)
        val schedule = UserSchedule(defaultWeeklySchedule = mapOf(DayOfWeek.THURSDAY to false)) // 在宅

        val result = useCase(forecast, schedule, targetDate)

        assertEquals(100, result.value)
    }

    @Test
    fun `雨 かつ 出社日 の場合、SOCは90%になること`() {
        val forecast = WeatherForecast(targetDate, cloudiness = 80, probabilityOfPrecipitation = 0.6f)
        val schedule = UserSchedule(defaultWeeklySchedule = mapOf(DayOfWeek.THURSDAY to true)) // 出社

        val result = useCase(forecast, schedule, targetDate)

        assertEquals(90, result.value)
    }

    @Test
    fun `曇り かつ 在宅日 の場合、SOCは100%になること`() {
        val forecast = WeatherForecast(targetDate, cloudiness = 50, probabilityOfPrecipitation = 0.3f)
        val schedule = UserSchedule(defaultWeeklySchedule = mapOf(DayOfWeek.THURSDAY to false)) // 在宅

        val result = useCase(forecast, schedule, targetDate)

        assertEquals(100, result.value)
    }

    @Test
    fun `曇り かつ 出社日 の場合、SOCは90%になること`() {
        val forecast = WeatherForecast(targetDate, cloudiness = 75, probabilityOfPrecipitation = 0.49f)
        val schedule = UserSchedule(defaultWeeklySchedule = mapOf(DayOfWeek.THURSDAY to true)) // 出社

        val result = useCase(forecast, schedule, targetDate)

        assertEquals(90, result.value)
    }

    // --- 下限値のテスト ---

    @Test
    fun `計算結果が60%を下回る場合、下限値の60%に補正されること`() {
        // 例: 秋の晴れ出社で60%
        val autumnDate = LocalDate.of(2026, 11, 26)
        val forecast = WeatherForecast(autumnDate, cloudiness = 0, probabilityOfPrecipitation = 0.0f)
        val schedule = UserSchedule(defaultWeeklySchedule = mapOf(DayOfWeek.THURSDAY to true))

        val result = useCase(forecast, schedule, autumnDate)

        assertEquals(60, result.value)
    }
}
