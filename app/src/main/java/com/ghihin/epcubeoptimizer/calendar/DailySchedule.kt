package com.ghihin.epcubeoptimizer.calendar

import com.ghihin.epcubeoptimizer.domain.model.WeatherForecast
import java.time.LocalDate

/**
 * 対象日のスケジュール（カレンダーの予定に基づく）と予測されるSOCを表すデータモデル
 *
 * @param date 対象日
 * @param isCommute 翌日の予定で外出（出社等）があるかどうか（true: 外出モード、false: 在宅モード）
 * @param predictedSoc 算出された0〜100のSOC目標値（未計算・未判定の場合はnull）
 * @param weatherForecast 取得した天気予報情報（未取得の場合はnull）
 */
data class DailySchedule(
    val date: LocalDate,
    val isCommute: Boolean,
    val predictedSoc: Int? = null,
    val weatherForecast: WeatherForecast? = null,
    val isSunnyTomorrow: Boolean = false
) {
    init {
        predictedSoc?.let {
            require(it in 0..100) { "SOCは0〜100の間で設定する必要があります: $it" }
        }
    }
}
