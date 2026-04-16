package com.ghihin.epcubeoptimizer.domain.usecase

import com.ghihin.epcubeoptimizer.domain.model.CalculationFactors
import com.ghihin.epcubeoptimizer.domain.model.TargetSOC
import com.ghihin.epcubeoptimizer.domain.model.UserSchedule
import com.ghihin.epcubeoptimizer.domain.model.WeatherForecast
import com.ghihin.epcubeoptimizer.calendar.DailySchedule
import com.ghihin.epcubeoptimizer.domain.repository.WeatherRepository
import java.time.LocalDate
import java.time.Month
import javax.inject.Inject

class CalculateTargetSocUseCase @Inject constructor(
    private val weatherRepository: WeatherRepository
) {
    companion object {
        // 積算日射量(W/m²・h)の判定閾値定数
        const val THRESHOLD_SUNNY = 4000.0
        const val THRESHOLD_CLOUDY = 2000.0

        // 中間期のSOC (春〜初夏・秋: 4〜6月, 10〜11月等)
        const val SOC_SPRING_SUNNY_ALL = 60
        const val SOC_SPRING_CLOUDY_HOME = 70
        const val SOC_SPRING_CLOUDY_COMMUTE = 60
        const val SOC_SPRING_RAIN_HOME = 100
        const val SOC_SPRING_RAIN_COMMUTE = 80

        // 夏季のSOC (7-9月)
        const val SOC_SUMMER_SUNNY_HOME = 80
        const val SOC_SUMMER_SUNNY_COMMUTE = 60
        const val SOC_SUMMER_RAIN_HOME = 100
        const val SOC_SUMMER_RAIN_COMMUTE = 90

        // 冬季のSOC (12-3月)
        const val SOC_WINTER_SUNNY_HOME = 100
        const val SOC_WINTER_SUNNY_COMMUTE = 70
        const val SOC_WINTER_RAIN_HOME = 100
        const val SOC_WINTER_RAIN_COMMUTE = 90
    }

    // 既存のメソッド（後方互換性のため残すが、基本はDailySchedule版を使用）
    operator fun invoke(
        forecast: WeatherForecast,
        schedule: UserSchedule,
        targetDate: LocalDate
    ): TargetSOC {
        val isCommuteDay = schedule.isCommuteDay(targetDate)
        return calculateInternal(forecast, isCommuteDay, targetDate)
    }

    suspend operator fun invoke(schedule: DailySchedule): TargetSocResult {
        val forecastResult = try {
            weatherRepository.getWeatherForecast(schedule.date)
        } catch (e: Exception) {
            null
        }

        // 1) APIエラー時のフェイルセーフ: 100%固定
        if (forecastResult == null) {
            return TargetSocResult(
                targetSoc = 100,
                weatherForecast = null,
                isSunnyTomorrow = false // エラー時はスマートモードへのフォールバックとして安全運用
            )
        }

        // 2) 対象日のTarget SOCを算出
        val targetSoc = calculateInternal(forecastResult, schedule.isCommute, schedule.date)

        // 3) isSunnyTomorrowの算出 (マクロ用) => THRESHOLD_SUNNY 以上なら true にマッピングして互換性を維持
        val isSunny = forecastResult.shortwaveRadiationSum >= THRESHOLD_SUNNY

        return TargetSocResult(targetSoc.value, forecastResult, isSunny)
    }

    private fun calculateInternal(forecast: WeatherForecast, isCommuteDay: Boolean, targetDate: LocalDate): TargetSOC {
        val radiationSum = forecast.shortwaveRadiationSum

        val month = targetDate.month
        val isWinter = month == Month.DECEMBER || month == Month.JANUARY || month == Month.FEBRUARY || month == Month.MARCH
        val isSummer = month == Month.JULY || month == Month.AUGUST || month == Month.SEPTEMBER

        val baseSoc = when {
            isWinter -> {
                when {
                    radiationSum >= THRESHOLD_SUNNY -> if (isCommuteDay) SOC_WINTER_SUNNY_COMMUTE else SOC_WINTER_SUNNY_HOME
                    else -> if (isCommuteDay) SOC_WINTER_RAIN_COMMUTE else SOC_WINTER_RAIN_HOME
                }
            }
            isSummer -> {
                when {
                    radiationSum >= THRESHOLD_SUNNY -> if (isCommuteDay) SOC_SUMMER_SUNNY_COMMUTE else SOC_SUMMER_SUNNY_HOME
                    else -> if (isCommuteDay) SOC_SUMMER_RAIN_COMMUTE else SOC_SUMMER_RAIN_HOME
                }
            }
            else -> {
                // 中間期
                when {
                    radiationSum >= THRESHOLD_SUNNY -> SOC_SPRING_SUNNY_ALL
                    radiationSum >= THRESHOLD_CLOUDY -> if (isCommuteDay) SOC_SPRING_CLOUDY_COMMUTE else SOC_SPRING_CLOUDY_HOME
                    else -> if (isCommuteDay) SOC_SPRING_RAIN_COMMUTE else SOC_SPRING_RAIN_HOME
                }
            }
        }

        val finalSoc = maxOf(60, baseSoc)

        return TargetSOC(
            value = finalSoc,
            factors = CalculationFactors(
                shortwaveRadiationSum = radiationSum,
                isCommuteDay = isCommuteDay
            )
        )
    }
}

data class TargetSocResult(
    val targetSoc: Int,
    val weatherForecast: WeatherForecast?,
    val isSunnyTomorrow: Boolean = false
)
