package com.ghihin.epcubeoptimizer.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NightlyAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(Config.LOG_TAG, "NightlyAlarmReceiver check action: $action")
        
        if (action == ACTION_EXECUTE_NIGHTLY || action == Intent.ACTION_BOOT_COMPLETED) {
            // 次回のスケジュールを再登録
            alarmScheduler.scheduleNightlyExecution()
        }
        
        if (action == ACTION_EXECUTE_NIGHTLY) {
            Log.d(Config.LOG_TAG, "Starting wake activity for nightly execution")
            // 画面を点灯し、キーガードを突破するためのActivityを起動する
            val wakeIntent = WakeActivity.createIntent(context)
            context.startActivity(wakeIntent)
        }
    }

    companion object {
        const val ACTION_EXECUTE_NIGHTLY = "com.ghihin.epcubeoptimizer.ACTION_EXECUTE_NIGHTLY"
        const val REQUEST_CODE = 1001
    }
}
