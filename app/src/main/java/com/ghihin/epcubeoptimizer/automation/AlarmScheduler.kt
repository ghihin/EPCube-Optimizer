package com.ghihin.epcubeoptimizer.automation

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @SuppressLint("ScheduleExactAlarm")
    fun scheduleNightlyExecution() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val intent = Intent(context, NightlyAlarmReceiver::class.java).apply {
            action = NightlyAlarmReceiver.ACTION_EXECUTE_NIGHTLY
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            NightlyAlarmReceiver.REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, Config.AUTO_EXECUTION_HOUR)
            set(Calendar.MINUTE, Config.AUTO_EXECUTION_MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        // もし現在時刻より前なら翌日に設定
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        
        Log.d(Config.LOG_TAG, "Scheduling nightly execution for: ${calendar.time}")
        
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } catch (e: SecurityException) {
            Log.e(Config.LOG_TAG, "Exact alarm permission not granted", e)
        }
    }
}
