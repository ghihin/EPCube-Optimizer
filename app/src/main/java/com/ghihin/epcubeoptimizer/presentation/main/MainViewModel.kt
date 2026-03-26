package com.ghihin.epcubeoptimizer.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghihin.epcubeoptimizer.domain.model.TargetSOC
import com.ghihin.epcubeoptimizer.domain.model.UserSchedule
import com.ghihin.epcubeoptimizer.domain.model.WeatherForecast
import com.ghihin.epcubeoptimizer.domain.repository.ScheduleRepository
import com.ghihin.epcubeoptimizer.domain.repository.WeatherRepository
import com.ghihin.epcubeoptimizer.domain.usecase.CalculateTargetSocUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

/** メイン画面の UI 状態 */
sealed class MainUiState {
    object Loading : MainUiState()
    data class Success(
        val forecast: WeatherForecast,
        val schedule: UserSchedule,
        val targetSoc: TargetSOC,
        /** 明日が出社日かどうか（トグル表示用） */
        val isTomorrowCommute: Boolean
    ) : MainUiState()
    data class Error(val message: String) : MainUiState()
}

/**
 * メイン画面の ViewModel。
 * Hilt によって依存関係を注入し、天気・スケジュールを取得して SOC を計算する。
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val weatherRepository: WeatherRepository,
    private val scheduleRepository: ScheduleRepository,
    private val calculateTargetSocUseCase: CalculateTargetSocUseCase
) : ViewModel() {

    // 鶴ヶ島市 (埼玉県) の緯度・経度
    private val lat = 35.9356
    private val lon = 139.3950

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** 天気とスケジュールを取得して SOC を計算する */
    fun load() {
        viewModelScope.launch {
            _uiState.value = MainUiState.Loading
            try {
                // 天気とスケジュールを並列取得
                val forecastResult = weatherRepository.getWeatherForecast(lat, lon)
                val schedule = scheduleRepository.getSchedule().first()

                forecastResult.fold(
                    onSuccess = { forecast ->
                        val tomorrow = LocalDate.now().plusDays(1)
                        val targetSoc = calculateTargetSocUseCase(forecast, schedule, tomorrow)
                        _uiState.value = MainUiState.Success(
                            forecast = forecast,
                            schedule = schedule,
                            targetSoc = targetSoc,
                            isTomorrowCommute = schedule.isCommuteDay(tomorrow)
                        )
                    },
                    onFailure = { e ->
                        _uiState.value = MainUiState.Error(
                            e.message ?: "天気情報の取得に失敗しました"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = MainUiState.Error(e.message ?: "予期しないエラーが発生しました")
            }
        }
    }

    /**
     * 明日の出社/在宅を切り替えて SOC を即座に再計算する。
     * DataStore への保存後に [load] で状態を更新する。
     */
    fun toggleTomorrowCommute() {
        val current = _uiState.value as? MainUiState.Success ?: return
        viewModelScope.launch {
            val tomorrow = LocalDate.now().plusDays(1)
            val newIsCommute = !current.isTomorrowCommute

            // 明日の日付を上書きとして保存
            val newOverrides = current.schedule.dateOverrides.toMutableMap()
            newOverrides[tomorrow] = newIsCommute
            val newSchedule = current.schedule.copy(dateOverrides = newOverrides)

            scheduleRepository.saveSchedule(newSchedule)

            // SOC を即座に再計算して画面を更新
            val targetSoc = calculateTargetSocUseCase(current.forecast, newSchedule, tomorrow)
            _uiState.value = current.copy(
                schedule = newSchedule,
                targetSoc = targetSoc,
                isTomorrowCommute = newIsCommute
            )
        }
    }
}
