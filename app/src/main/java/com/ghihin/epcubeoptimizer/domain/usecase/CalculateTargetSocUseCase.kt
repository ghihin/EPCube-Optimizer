package com.ghihin.epcubeoptimizer.domain.usecase

import com.ghihin.epcubeoptimizer.domain.model.CalculationFactors
import com.ghihin.epcubeoptimizer.domain.model.TargetSOC
import com.ghihin.epcubeoptimizer.domain.model.UserSchedule
import com.ghihin.epcubeoptimizer.domain.model.WeatherForecast
import java.time.LocalDate
import javax.inject.Inject

class CalculateTargetSocUseCase @Inject constructor() {

    operator fun invoke(
        forecast: WeatherForecast,
        schedule: UserSchedule,
        targetDate: LocalDate
    ): TargetSOC {
        val isCommuteDay = schedule.isCommuteDay(targetDate)
        val cloudiness = forecast.cloudiness
        val pop = forecast.probabilityOfPrecipitation

        var baseSoc = when {
            // 晴れ (Sunny): clouds.all <= 30 かつ pop < 0.2 -> SOC 30%
            cloudiness <= 30 && pop < 0.2f -> 30
            // 雨 (Rainy): clouds.all > 75 または pop >= 0.5 -> SOC 80%
            cloudiness > 75 || pop >= 0.5f -> 80
            // 曇り (Cloudy): 30 < clouds.all <= 75 -> SOC 50%
            else -> 50
        }

        // 出社日の場合、上記結果から -10% する（下限値は 20%）
        if (isCommuteDay) {
            baseSoc -= 10
        }

        // 下限値の適用
        val finalSoc = maxOf(20, baseSoc)

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
