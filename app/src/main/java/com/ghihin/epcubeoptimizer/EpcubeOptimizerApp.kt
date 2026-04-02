package com.ghihin.epcubeoptimizer

import android.app.Application
import com.ghihin.epcubeoptimizer.automation.AlarmScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** Hilt のエントリーポイントとなる Application クラス。 */
@HiltAndroidApp
class EpcubeOptimizerApp : Application() {
    
    @Inject
    lateinit var alarmScheduler: AlarmScheduler

    override fun onCreate() {
        super.onCreate()
        // アプリ起動時にアラームをスケジュールする
        alarmScheduler.scheduleNightlyExecution()
    }
}
