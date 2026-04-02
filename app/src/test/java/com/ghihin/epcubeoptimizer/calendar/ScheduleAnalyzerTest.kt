package com.ghihin.epcubeoptimizer.calendar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ScheduleAnalyzerTest {

    @Test
    fun testAnalyzeSchedules_commuteDetection() {
        val analyzer = ScheduleAnalyzer()
        val todayMillis = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
            
        val tomorrowMillis = LocalDate.now().plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val events = listOf(
            CalendarEvent("ミーティング", todayMillis), // 在宅
            CalendarEvent("【出社】テスト", tomorrowMillis) // 出社
        )

        val schedules = analyzer.analyzeSchedules(events, 2)

        assertEquals(2, schedules.size)
        // Today: no commute
        assertEquals(false, schedules[0].isCommute)
        // Tomorrow: commute
        assertEquals(true, schedules[1].isCommute)
    }
}
