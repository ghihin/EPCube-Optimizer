package com.ghihin.epcubeoptimizer.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghihin.epcubeoptimizer.calendar.CalendarRepository
import com.ghihin.epcubeoptimizer.calendar.DailySchedule
import com.ghihin.epcubeoptimizer.calendar.ScheduleAnalyzer
import com.ghihin.epcubeoptimizer.domain.usecase.CalculateTargetSocUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val scheduleAnalyzer: ScheduleAnalyzer,
    private val calculateTargetSocUseCase: CalculateTargetSocUseCase
) : ViewModel() {

    private val _schedules = MutableStateFlow<List<DailySchedule>>(emptyList())
    val schedules: StateFlow<List<DailySchedule>> = _schedules.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadSchedules()
    }

    fun loadSchedules() {
        viewModelScope.launch {
            _isLoading.value = true
            val events = calendarRepository.getEventsForNextNDays(7)
            val analyzed = scheduleAnalyzer.analyzeSchedules(events, 7)
            
            val updated = analyzed.map { schedule ->
                // 天気予報とスケジュールを考慮してSOCを計算
                val targetSocResult = calculateTargetSocUseCase(schedule)
                schedule.copy(
                    predictedSoc = targetSocResult.targetSoc,
                    weatherForecast = targetSocResult.weatherForecast,
                    isSunnyTomorrow = targetSocResult.isSunnyTomorrow
                )
            }
            _schedules.value = updated
            _isLoading.value = false
        }
    }
}
