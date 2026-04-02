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

    // 既存のメソッド（後方互換性のため残すか、テスト修正が必要）
    operator fun invoke(
        forecast: WeatherForecast,
        schedule: UserSchedule,
        targetDate: LocalDate
    ): TargetSOC {
        val isCommuteDay = schedule.isCommuteDay(targetDate)
        return calculateInternal(forecast, isCommuteDay, targetDate)
    }

    // DailyScheduleを受け取る新しいメソッド
    suspend operator fun invoke(schedule: DailySchedule): TargetSocResult {
        val forecast = try {
            weatherRepository.getWeatherForecast(schedule.date)
        } catch (e: Exception) {
            null
        }

        val targetSoc = if (forecast != null) {
            calculateInternal(forecast, schedule.isCommute, schedule.date).value
        } else {
            // 天気取得失敗時のフォールバック
            if (schedule.isCommute) 100 else 60
        }

        return TargetSocResult(targetSoc, forecast)
    }

    private fun calculateInternal(forecast: WeatherForecast, isCommuteDay: Boolean, targetDate: LocalDate): TargetSOC {
        val cloudiness = forecast.cloudiness
        val pop = forecast.probabilityOfPrecipitation
        
        // 晴れの判定（雲量30%以下かつ降水確率20%未満）
        val isSunny = cloudiness <= 30 && pop < 0.2f

        // 季節の判定
        val month = targetDate.month
        val isWinter = month == Month.DECEMBER || month == Month.JANUARY || month == Month.FEBRUARY || month == Month.MARCH
        val isSummer = month == Month.JULY || month == Month.AUGUST || month == Month.SEPTEMBER

        val baseSoc = when {
            isWinter -> {
                // 冬場（エアコン利用頻度多め、日照量少な目）
                when {
                    !isCommuteDay && isSunny -> 100
                    isCommuteDay && isSunny -> 70
                    !isCommuteDay && !isSunny -> 100
                    isCommuteDay && !isSunny -> 90
                    else -> 100
                }
            }
            isSummer -> {
                // 夏場（エアコン利用頻度多め、深夜の電気料金が安い時間もエアコンつけっぱなし、日照量多め）
                when {
                    !isCommuteDay && isSunny -> 80
                    isCommuteDay && isSunny -> 60
                    !isCommuteDay && !isSunny -> 100
                    isCommuteDay && !isSunny -> 90
                    else -> 80
                }
            }
            else -> {
                // 春・秋（エアコン利用頻度減、充電量増）
                when {
                    !isCommuteDay && isSunny -> 70
                    isCommuteDay && isSunny -> 60
                    !isCommuteDay && !isSunny -> 80
                    isCommuteDay && !isSunny -> 70
                    else -> 70
                }
            }
        }

        // 下限値の適用 (EP CUBEの仕様により最低60%)
        val finalSoc = maxOf(60, baseSoc)

        return TargetSOC(
            value = finalSoc,
            factors = CalculationFactors(
                cloudiness = cloudiness,
                pop = pop,
                isCommuteDay = isCommuteDay
            )
        )
    }
}

data class TargetSocResult(
    val targetSoc: Int,
    val weatherForecast: WeatherForecast?
)
