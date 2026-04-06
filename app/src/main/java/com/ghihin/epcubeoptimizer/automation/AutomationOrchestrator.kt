package com.ghihin.epcubeoptimizer.automation

import android.content.Context
import android.content.Intent
import android.util.Log
import com.ghihin.epcubeoptimizer.calendar.CalendarRepository
import com.ghihin.epcubeoptimizer.calendar.ScheduleAnalyzer
import com.ghihin.epcubeoptimizer.core.accessibility.EpCubeAccessibilityService
import com.ghihin.epcubeoptimizer.domain.usecase.CalculateTargetSocUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationOrchestrator @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val scheduleAnalyzer: ScheduleAnalyzer,
    private val calculateTargetSocUseCase: CalculateTargetSocUseCase
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun executeNightlyRoutine(context: Context) {
        Log.d(Config.LOG_TAG, "Executing nightly orchestrator routine")
        
        scope.launch {
            try {
                // 1. カレンダー情報取得
                val events = calendarRepository.getEventsForNextNDays(2) // 念のため2日分
                
                // 2. 出社判定（明日の予定をみる）
                val schedules = scheduleAnalyzer.analyzeSchedules(events, 2)
                
                // 明日の予定は index 1
                val tomorrowSchedule = if (schedules.size > 1) schedules[1] else schedules[0]
                
                // 3. 天気予報とスケジュールを考慮してSOCを計算
                val targetSocResult = calculateTargetSocUseCase(tomorrowSchedule)
                val targetSoc = targetSocResult.targetSoc
                
                Log.d(Config.LOG_TAG, "Tomorrow isCommute: ${tomorrowSchedule.isCommute}, Target SOC: $targetSoc")
                
                // 4. マクロサービスにインテントを送出
                val intent = Intent(context, EpCubeAccessibilityService::class.java).apply {
                    action = EpCubeAccessibilityService.ACTION_START_MACRO
                    putExtra(EpCubeAccessibilityService.EXTRA_TARGET_SOC, targetSoc)
                    putExtra(EpCubeAccessibilityService.EXTRA_IS_SUNNY_TOMORROW, targetSocResult.isSunnyTomorrow)
                }
                context.startService(intent)
                
            } catch (e: Exception) {
                Log.e(Config.LOG_TAG, "Error in execution routine, applying SAFE soc", e)
                val intent = Intent(context, EpCubeAccessibilityService::class.java).apply {
                    action = EpCubeAccessibilityService.ACTION_START_MACRO
                    putExtra(EpCubeAccessibilityService.EXTRA_TARGET_SOC, Config.FALLBACK_SAFE_SOC)
                }
                context.startService(intent)
            }
        }
    }
}
