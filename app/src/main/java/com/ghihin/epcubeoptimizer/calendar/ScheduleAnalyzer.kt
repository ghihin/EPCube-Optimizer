package com.ghihin.epcubeoptimizer.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleAnalyzer @Inject constructor() {

    fun analyzeSchedules(events: List<CalendarEvent>, days: Int): List<DailySchedule> {
        val today = LocalDate.now()
        val schedules = mutableListOf<DailySchedule>()

        for (i in 0 until days) {
            val targetDate = today.plusDays(i.toLong())
            val eventsForDay = events.filter {
                val eventDate = Instant.ofEpochMilli(it.startTimeMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                eventDate == targetDate
            }

            // "出社"が含まれていれば外出モード（true）
            val isCommute = eventsForDay.any { it.title.contains("出社") }

            schedules.add(DailySchedule(date = targetDate, isCommute = isCommute))
        }

        return schedules
    }
}
