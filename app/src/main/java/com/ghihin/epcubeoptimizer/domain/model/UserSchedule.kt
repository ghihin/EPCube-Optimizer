package com.ghihin.epcubeoptimizer.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

data class UserSchedule(
    val defaultWeeklySchedule: Map<DayOfWeek, Boolean>, // true = 出社, false = 在宅
    val dateOverrides: Map<LocalDate, Boolean> = emptyMap()
) {
    fun isCommuteDay(date: LocalDate): Boolean {
        return dateOverrides[date] ?: defaultWeeklySchedule[date.dayOfWeek] ?: false
    }
}
