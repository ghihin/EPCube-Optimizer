package com.ghihin.epcubeoptimizer.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.ghihin.epcubeoptimizer.calendar.CalendarLogWriter
import com.ghihin.epcubeoptimizer.calendar.CalendarRepository
import com.ghihin.epcubeoptimizer.calendar.ExecutionCalendarEvent
import com.ghihin.epcubeoptimizer.calendar.ScheduleAnalyzer
import com.ghihin.epcubeoptimizer.core.accessibility.EpCubeAccessibilityService
import com.ghihin.epcubeoptimizer.domain.usecase.CalculateTargetSocUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class AutomationOrchestrator @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val scheduleAnalyzer: ScheduleAnalyzer,
    private val calculateTargetSocUseCase: CalculateTargetSocUseCase,
    private val calendarLogWriter: CalendarLogWriter
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun executeNightlyRoutine(context: Context) {
        Log.d(Config.LOG_TAG, "Executing nightly orchestrator routine")
        
        scope.launch {
            var isReceiverRegistered = false
            var receiver: BroadcastReceiver? = null

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
                val forecast = targetSocResult.weatherForecast
                val isSunnyTomorrow = targetSocResult.isSunnyTomorrow
                
                val settingMode = if (isSunnyTomorrow && targetSoc >= 60) "グリーンモード" else "スマートモード"
                
                Log.d(Config.LOG_TAG, "Tomorrow isCommute: ${tomorrowSchedule.isCommute}, Target SOC: $targetSoc")
                
                // 4. マクロの実行結果待機のための Receiver 登録
                val resultEvent = withTimeoutOrNull(180_000L) {
                    suspendCancellableCoroutine<ExecutionCalendarEvent> { continuation ->
                        receiver = object : BroadcastReceiver() {
                            override fun onReceive(c: Context?, intent: Intent?) {
                                if (intent?.action == EpCubeAccessibilityService.ACTION_MACRO_RESULT) {
                                    val isSuccess = intent.getBooleanExtra(EpCubeAccessibilityService.EXTRA_IS_SUCCESS, false)
                                    val errorMsg = intent.getStringExtra(EpCubeAccessibilityService.EXTRA_ERROR_MESSAGE)
                                    val preExecSoc = intent.getIntExtra(EpCubeAccessibilityService.EXTRA_PRE_EXEC_SOC, -1)
                                    val preExecMode = intent.getStringExtra(EpCubeAccessibilityService.EXTRA_PRE_EXEC_MODE)
                                    
                                    val event = ExecutionCalendarEvent(
                                        isSuccess = isSuccess,
                                        executionTimeMillis = System.currentTimeMillis(),
                                        settingMode = settingMode,
                                        targetSoc = if (settingMode == "グリーンモード") null else targetSoc,
                                        errorMessage = errorMsg,
                                        preExecSoc = preExecSoc,
                                        preExecMode = preExecMode,
                                        weatherDescription = forecast?.weatherDescription,
                                        cloudiness = forecast?.cloudiness,
                                        precipitationProbability = forecast?.probabilityOfPrecipitation
                                    )
                                    
                                    // 解除 (T010 リソースリーク対策)
                                    if (isReceiverRegistered && receiver != null) {
                                        try {
                                            context.applicationContext.unregisterReceiver(receiver)
                                            isReceiverRegistered = false
                                        } catch (e: Exception) {
                                            Log.e(Config.LOG_TAG, "Error unregistering receiver", e)
                                        }
                                    }
                                    
                                    if (continuation.isActive) {
                                        continuation.resume(event)
                                    }
                                }
                            }
                        }
                        
                        val filter = IntentFilter(EpCubeAccessibilityService.ACTION_MACRO_RESULT)
                        ContextCompat.registerReceiver(
                            context.applicationContext,
                            receiver,
                            filter,
                            ContextCompat.RECEIVER_NOT_EXPORTED
                        )
                        isReceiverRegistered = true
                        
                        // 5. マクロサービスにインテントを送出 (T008)
                        val launchIntent = Intent(context, EpCubeAccessibilityService::class.java).apply {
                            action = EpCubeAccessibilityService.ACTION_START_MACRO
                            putExtra(EpCubeAccessibilityService.EXTRA_TARGET_SOC, targetSoc)
                            putExtra(EpCubeAccessibilityService.EXTRA_IS_SUNNY_TOMORROW, isSunnyTomorrow)
                            putExtra(EpCubeAccessibilityService.EXTRA_FROM_ORCHESTRATOR, true)
                        }
                        context.startService(launchIntent)
                        
                        continuation.invokeOnCancellation {
                            if (isReceiverRegistered && receiver != null) {
                                try {
                                    context.applicationContext.unregisterReceiver(receiver)
                                    isReceiverRegistered = false
                                } catch (e: Exception) {
                                    Log.e(Config.LOG_TAG, "Error unregistering receiver on cancel", e)
                                }
                            }
                        }
                    }
                } // 3分のタイムアウト (T011)

                if (resultEvent != null) {
                    val result = calendarLogWriter.writeExecutionResult(resultEvent)
                    if (resultEvent.isSuccess) {
                        Log.i(Config.LOG_TAG, "Calendar Write Successful (SUCCESS event): $result")
                    } else {
                        Log.e(Config.LOG_TAG, "Calendar Write Successful (FAIL event). Error was: ${resultEvent.errorMessage}")
                    }
                } else {
                    // T014 タイムアウトのログ出力とフェイルセーフイベント書き込み
                    Log.w(Config.LOG_TAG, "マクロからの応答がありません。フェイルセーフとしてタイムアウトイベントをカレンダーに書き込みます。")
                    val timeoutEvent = ExecutionCalendarEvent(
                        isSuccess = false,
                        executionTimeMillis = System.currentTimeMillis(),
                        settingMode = settingMode,
                        targetSoc = null, // 未確定のためnull
                        errorMessage = "マクロ応答タイムアウト (3分)",
                        preExecSoc = null,
                        preExecMode = null,
                        weatherDescription = forecast?.weatherDescription,
                        cloudiness = forecast?.cloudiness,
                        precipitationProbability = forecast?.probabilityOfPrecipitation
                    )
                    calendarLogWriter.writeExecutionResult(timeoutEvent)
                }

            } catch (e: Exception) {
                // T012 および T013 例外時のカレンダーエラー書き込み
                Log.e(Config.LOG_TAG, "Error in execution routine, applying SAFE soc and writing failure log", e)
                
                val fallbackEvent = ExecutionCalendarEvent(
                    isSuccess = false,
                    executionTimeMillis = System.currentTimeMillis(),
                    settingMode = "スマートモード",
                    targetSoc = null,
                    errorMessage = "準備エラー: ${e.message}",
                    preExecSoc = null,
                    preExecMode = null,
                    weatherDescription = null,
                    cloudiness = null,
                    precipitationProbability = null
                )
                calendarLogWriter.writeExecutionResult(fallbackEvent)
                
                val fallbackIntent = Intent(context, EpCubeAccessibilityService::class.java).apply {
                    action = EpCubeAccessibilityService.ACTION_START_MACRO
                    putExtra(EpCubeAccessibilityService.EXTRA_TARGET_SOC, Config.FALLBACK_SAFE_SOC)
                    putExtra(EpCubeAccessibilityService.EXTRA_FROM_ORCHESTRATOR, true)
                }
                context.startService(fallbackIntent)
                
            } finally {
                // T010 確実な receiver 解除
                if (isReceiverRegistered && receiver != null) {
                    try {
                        context.applicationContext.unregisterReceiver(receiver)
                        isReceiverRegistered = false
                    } catch (e: Exception) {
                        Log.e(Config.LOG_TAG, "Error unregistering receiver in finally block", e)
                    }
                }
            }
        }
    }
}
