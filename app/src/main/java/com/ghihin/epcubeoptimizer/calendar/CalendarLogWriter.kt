package com.ghihin.epcubeoptimizer.calendar

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.util.Log
import com.ghihin.epcubeoptimizer.automation.Config
import com.ghihin.epcubeoptimizer.core.permission.PermissionHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarLogWriter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val contentResolver: ContentResolver = context.contentResolver

    suspend fun writeExecutionResult(event: ExecutionCalendarEvent, ignoreDuplicate: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!PermissionHelper.hasCalendarPermissions(context)) {
            Log.w(Config.LOG_TAG, "WRITE_CALENDAR permission not granted. Skipping calendar logging.")
            return@withContext false
        }

        try {
            val calendarId = getDefaultCalendarId()
            if (calendarId == null) {
                Log.e(Config.LOG_TAG, "Calendar ID not found. Skipping calendar logging.")
                return@withContext false
            }

            if (!ignoreDuplicate && isDuplicateEvent(event.executionTimeMillis)) {
                Log.i(Config.LOG_TAG, "重複イベント検出: INSERT をスキップします")
                return@withContext false
            }

            val values = buildEventValues(calendarId, event)
            val uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            
            if (uri != null) {
                Log.i(Config.LOG_TAG, "Calendar event inserted successfully: $uri")
                true
            } else {
                Log.e(Config.LOG_TAG, "Failed to insert calendar event")
                false
            }
        } catch (e: Exception) {
            Log.e(Config.LOG_TAG, "Error writing execution result to calendar", e)
            false
        }
    }

    private suspend fun getDefaultCalendarId(): Long? = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.ACCOUNT_NAME
        )
        // 表示中のカレンダーのみに絞る
        val selection = "${CalendarContract.Calendars.VISIBLE} = 1"
        
        var bestId: Long? = null
        var fallbackGoogleId: Long? = null
        var fallbackId: Long? = null
        
        try {
            contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val accountType = cursor.getString(1)
                    val isPrimary = cursor.getInt(2) == 1
                    val accountName = cursor.getString(3)
                    
                    if (fallbackId == null) fallbackId = id
                    
                    if (accountType == "com.google") {
                        if (fallbackGoogleId == null) fallbackGoogleId = id
                        
                        // メイン設定に一致するプライマリカレンダーを最優先
                        if (accountName == Config.TARGET_CALENDAR_ACCOUNT_NAME && isPrimary) {
                            bestId = id
                            break // 最適なGoogleメインカレンダーを見つけた
                        } else if (isPrimary && bestId == null) {
                            // サブアカウントのPrimary用
                            bestId = id
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(Config.LOG_TAG, "Failed to query calendar ID", e)
        }
        
        // 優先度: ghihin(Primary) > サブGoogle(Primary) > 何らかのGoogle > 何らかの見えてるカレンダー
        return@withContext bestId ?: fallbackGoogleId ?: fallbackId
    }

    private suspend fun isDuplicateEvent(timeMillis: Long): Boolean = withContext(Dispatchers.IO) {
        val timeLimitMin = timeMillis - 30 * 60 * 1000L
        val timeLimitMax = timeMillis + 30 * 60 * 1000L
        
        val projection = arrayOf(CalendarContract.Events._ID)
        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ? AND ${CalendarContract.Events.TITLE} LIKE ?"
        val selectionArgs = arrayOf(timeLimitMin.toString(), timeLimitMax.toString(), "%EPCUBE設定%")
        
        try {
            contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                return@withContext cursor.count > 0
            }
        } catch (e: Exception) {
            Log.e(Config.LOG_TAG, "Failed to check duplicate events", e)
        }
        false
    }

    private fun buildEventValues(calendarId: Long, event: ExecutionCalendarEvent): ContentValues {
        val title = if (event.isSuccess) "✅ EPCUBE設定完了" else "❌ EPCUBE設定失敗"
        
        return ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, event.executionTimeMillis)
            put(CalendarContract.Events.DTEND, event.executionTimeMillis + 15 * 60 * 1000L) // 15分後
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.DESCRIPTION, buildDescription(event))
            put(CalendarContract.Events.HAS_ALARM, 0)
        }
    }

    private fun buildDescription(event: ExecutionCalendarEvent): String {
        val targetSocStr = event.targetSoc?.toString()?.let { "$it%" } ?: "(深夜充電なし)"
        val preExecSocStr = event.preExecSoc?.takeIf { it != -1 }?.toString()?.let { "$it%" } ?: "取得不可"
        val preExecModeStr = event.preExecMode ?: "取得不可"
        val weatherDescStr = event.weatherDescription ?: "取得不可"
        val cloudinessStr = event.cloudiness?.toString()?.let { "$it%" } ?: "取得不可"
        val precipStr = event.precipitationProbability?.let { "${(it * 100).toInt()}%" } ?: "取得不可"

        val sb = StringBuilder()
        sb.append("【実行結果】\n")
        sb.append("設定モード: ${event.settingMode}\n")
        sb.append("目標SOC: $targetSocStr\n")
        if (!event.isSuccess && !event.errorMessage.isNullOrEmpty()) {
            sb.append("※エラー: ${event.errorMessage}\n")
        }
        sb.append("\n【変更前の状態】\n")
        sb.append("取得SOC: $preExecSocStr\n")
        sb.append("運転モード: $preExecModeStr\n")
        sb.append("\n【気象条件 (明日)】\n")
        sb.append("天気: $weatherDescStr\n")
        sb.append("雲量: $cloudinessStr\n")
        sb.append("降水確率: $precipStr")

        val desc = sb.toString()
        // Ensure description does not exceed 400 characters (SC-004)
        return if (desc.length > 400) {
            desc.take(397) + "..."
        } else {
            desc
        }
    }
}
