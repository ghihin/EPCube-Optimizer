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
        useCase = CalculateTargetSocUseCase()
    }

    // --- 晴れ (Sunny) のテスト ---
    // 条件: cloudiness <= 30 かつ pop < 0.2 -> 基本SOC 30%

    @Test
    fun `晴れ かつ 在宅日 の場合、SOCは30%になること`() {
        val forecast = WeatherForecast(targetDate, cloudiness = 20, probabilityOfPrecipitation = 0.1f)
        val schedule = UserSchedule(defaultWeeklySchedule = mapOf(DayOfWeek.THURSDAY to false)) // 在宅

        val result = useCase(forecast, schedule, targetDate)

        assertEquals(30, result.value)
        assertEquals(20, result.factors.cloudiness)
        assertEquals(0.1f, result.factors.pop)
        assertEquals(false, result.factors.isCommuteDay)
    }

    @Test
    fun `晴れ かつ 出社日 の場合、SOCは20%になること (30% - 10%)`() {
        val forecast = WeatherForecast(targetDate, cloudiness = 30, probabilityOfPrecipitation = 0.19f)
        val schedule = UserSchedule(defaultWeeklySchedule = mapOf(DayOfWeek.THURSDAY to true)) // 出社

        val result = useCase(forecast, schedule, targetDate)

        assertEquals(20, result.value)
        assertEquals(true, result.factors.isCommuteDay)
    }

    // --- 雨 (Rainy) のテスト ---
    // 条件: cloudiness > 75 または pop >= 0.5 -> 基本SOC 80%

    @Test
    fun `雨(cloudiness大) かつ 在宅日 の場合、SOCは80%になること`() {
        val forecast = WeatherForecast(targetDate, cloudiness = 76, probabilityOfPrecipitation = 0.1f)
        val schedule = UserSchedule(defaultWeeklySchedule = mapOf(DayOfWeek.THURSDAY to false)) // 在宅

        val result = useCase(forecast, schedule, targetDate)

        assertEquals(80, result.value)
    }

    @Test
    fun `雨(pop大) かつ 在宅日 の場合、SOCは80%になること`() {
        val forecast = WeatherForecast(targetDate, cloudiness = 20, probabilityOfPrecipitation = 0.5f)
        val schedule = UserSchedule(defaultWeeklySchedule = mapOf(DayOfWeek.THURSDAY to false)) // 在宅

        val result = useCase(forecast, schedule, targetDate)

        assertEquals(80, result.value)
    }

    @Test
    fun `雨 かつ 出社日 の場合、SOCは70%になること (80% - 10%)`() {
        val forecast = WeatherForecast(targetDate, cloudiness = 80, probabilityOfPrecipitation = 0.6f)
        val schedule = UserSchedule(defaultWeeklySchedule = mapOf(DayOfWeek.THURSDAY to true)) // 出社

        val result = useCase(forecast, schedule, targetDate)

        assertEquals(70, result.value)
    }

    // --- 曇り (Cloudy) のテスト ---
    // 条件: 上記以外 (30 < cloudiness <= 75) -> 基本SOC 50%

    @Test
    fun `曇り かつ 在宅日 の場合、SOCは50%になること`() {
        val forecast = WeatherForecast(targetDate, cloudiness = 50, probabilityOfPrecipitation = 0.3f)
        val schedule = UserSchedule(defaultWeeklySchedule = mapOf(DayOfWeek.THURSDAY to false)) // 在宅

        val result = useCase(forecast, schedule, targetDate)

        assertEquals(50, result.value)
    }

    @Test
    fun `曇り かつ 出社日 の場合、SOCは40%になること (50% - 10%)`() {
        val forecast = WeatherForecast(targetDate, cloudiness = 75, probabilityOfPrecipitation = 0.49f)
        val schedule = UserSchedule(defaultWeeklySchedule = mapOf(DayOfWeek.THURSDAY to true)) // 出社

        val result = useCase(forecast, schedule, targetDate)

        assertEquals(40, result.value)
    }

    // --- 下限値のテスト ---

    @Test
    fun `計算結果が20%を下回る場合、下限値の20%に補正されること`() {
        // 晴れ(30%) かつ 出社(-10%) で 20% になるケースは既にテスト済み。
        // もし将来的に基本SOCが20%になる条件が追加され、かつ出社(-10%)が適用された場合を想定したテスト。
        // 現在のロジックでは最小が 30 - 10 = 20 なので、このテストは念のための確認。
        val forecast = WeatherForecast(targetDate, cloudiness = 0, probabilityOfPrecipitation = 0.0f)
        val schedule = UserSchedule(defaultWeeklySchedule = mapOf(DayOfWeek.THURSDAY to true))

        val result = useCase(forecast, schedule, targetDate)

        assertEquals(20, result.value)
    }
}
