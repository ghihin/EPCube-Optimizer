package com.ghihin.epcubeoptimizer.automation

import java.time.LocalDateTime
import java.util.UUID

/**
 * マクロ自動実行のステータス情報
 */
enum class ExecutionStatus {
    STARTED,
    CALLED_CALENDAR_API,
    CALCULATED_SOC,
    MACRO_RUNNING,
    SUCCESS,
    VALIDATION_FAILED,     // SOCの範囲異常など
    CALENDAR_ERROR,        // カレンダーアクセス失敗
    WEATHER_ERROR,         // 天候取得エラー
    MACRO_FAILED           // マクロ失敗（20秒待機後の値不一致など）
}

/**
 * 毎晩行われる全自動処理（天候取得〜マクロ設定）の実行結果や履歴ログを保持するモデル
 */
data class AutomationExecutionLog(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val status: ExecutionStatus,
    val socTarget: Int? = null,
    val errorDetail: String? = null,
    // カレンダーロギング用に追加（in-memoryのみ、DB永続化なし）
    val preExecSoc: Int? = null,
    val preExecMode: String? = null,
    val weatherDescription: String? = null,
    val cloudiness: Int? = null,
    val precipitationProbability: Float? = null
)
