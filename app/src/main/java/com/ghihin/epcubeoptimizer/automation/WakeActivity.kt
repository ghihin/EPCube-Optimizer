package com.ghihin.epcubeoptimizer.automation

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 画面点灯・ロック解除を行うための透明なActivity
 * 起動後、直ちに AutomationOrchestrator を呼び出して役割を終え終了する。
 */
@AndroidEntryPoint
class WakeActivity : ComponentActivity() {

    @Inject
    lateinit var orchestrator: AutomationOrchestrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(Config.LOG_TAG, "WakeActivity started")
        
        wakeUpScreen()
        
        // 処理をオーケストレーターに委譲
        orchestrator.executeNightlyRoutine(this)
        
        // UIを持たないので直ちに終了する（マクロはバックグラウンドのAccessibilityServiceで動く）
        finish()
    }
    
    @Suppress("DEPRECATION")
    private fun wakeUpScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, WakeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
    }
}
