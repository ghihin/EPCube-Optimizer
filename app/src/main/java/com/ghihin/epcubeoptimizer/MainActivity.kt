package com.ghihin.epcubeoptimizer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.ghihin.epcubeoptimizer.calendar.CalendarLogWriter
import com.ghihin.epcubeoptimizer.calendar.ExecutionCalendarEvent
import com.ghihin.epcubeoptimizer.core.accessibility.EpCubeAccessibilityService
import com.ghihin.epcubeoptimizer.core.permission.PermissionHelper
import com.ghihin.epcubeoptimizer.presentation.main.MainScreen
import com.ghihin.epcubeoptimizer.ui.schedule.ScheduleViewerScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** アプリのエントリーポイントとなる Activity。Hilt による DI を受け取る。 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var calendarLogWriter: CalendarLogWriter

    private var onPermissionDenied: (() -> Unit)? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            onPermissionDenied?.invoke()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (!PermissionHelper.hasCalendarPermissions(this)) {
            requestPermissionLauncher.launch(PermissionHelper.CALENDAR_PERMISSIONS)
        }
        
        handleIntent(intent)
        setContent {
            MaterialTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                
                onPermissionDenied = {
                    scope.launch {
                        snackbarHostState.showSnackbar("カレンダー権限が必要です。設定から許可してください。")
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { paddingValues ->
                    Surface(
                        modifier = Modifier.fillMaxSize().padding(paddingValues),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        var showViewer by remember { mutableStateOf(false) }
                        
                        if (showViewer) {
                            ScheduleViewerScreen(
                                onBack = { showViewer = false },
                                onTestMacroClicked = {
                                    val intent = Intent(this@MainActivity, com.ghihin.epcubeoptimizer.automation.WakeActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    }
                                    startActivity(intent)
                                },
                                onTestGreenModeClicked = {
                                    val intent = Intent(this@MainActivity, EpCubeAccessibilityService::class.java).apply {
                                        action = EpCubeAccessibilityService.ACTION_START_MACRO
                                        putExtra(EpCubeAccessibilityService.EXTRA_TARGET_SOC, 100) // グリーンモード時は使用されないのでダミーの100を渡す
                                        putExtra(EpCubeAccessibilityService.EXTRA_IS_SUNNY_TOMORROW, true) // グリーンモードを強制
                                    }
                                    startService(intent)
                                },
                                onTestSmartModeClicked = {
                                    val intent = Intent(this@MainActivity, EpCubeAccessibilityService::class.java).apply {
                                        action = EpCubeAccessibilityService.ACTION_START_MACRO
                                        putExtra(EpCubeAccessibilityService.EXTRA_TARGET_SOC, 99) // テスト用に99%を指定
                                        putExtra(EpCubeAccessibilityService.EXTRA_IS_SUNNY_TOMORROW, false) // スマートモードを強制
                                    }
                                    startService(intent)
                                }
                            )
                        } else {
                            Column {
                                Box(modifier = Modifier.weight(1f)) {
                                    MainScreen()
                                }
                                Button(
                                    onClick = { showViewer = true },
                                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                                ) {
                                    Text("📅 1週間先のスケジュール・予測SOCを確認")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == EpCubeAccessibilityService.ACTION_MACRO_RESULT) {
            val isSuccess = intent.getBooleanExtra(EpCubeAccessibilityService.EXTRA_IS_SUCCESS, false)
            val targetSoc = intent.getIntExtra(EpCubeAccessibilityService.EXTRA_TARGET_SOC_RESULT, -1)
            val errorMessage = intent.getStringExtra(EpCubeAccessibilityService.EXTRA_ERROR_MESSAGE)
            val preExecSoc = intent.getIntExtra(EpCubeAccessibilityService.EXTRA_PRE_EXEC_SOC, -1)
            val preExecMode = intent.getStringExtra(EpCubeAccessibilityService.EXTRA_PRE_EXEC_MODE)
            val isFromOrchestrator = intent.getBooleanExtra(EpCubeAccessibilityService.EXTRA_FROM_ORCHESTRATOR, false)
            
            Log.d("MainActivity", "Macro Result: success=$isSuccess, targetSoc=$targetSoc, isFromOrchestrator=$isFromOrchestrator")
            
            // オーケストレータ経由「以外」（手動ボタン等）の場合のみダミーイベントをカレンダーに書き込む
            if (!isFromOrchestrator) {
                val dummyEvent = ExecutionCalendarEvent(
                    isSuccess = isSuccess,
                    executionTimeMillis = System.currentTimeMillis(),
                    settingMode = if (targetSoc == 100) "グリーンモード" else "スマートモード",
                    targetSoc = if (targetSoc == 100) null else targetSoc,
                    errorMessage = errorMessage,
                    preExecSoc = preExecSoc,
                    preExecMode = preExecMode,
                    weatherDescription = "(手動テスト実行)",
                    cloudiness = null,
                    precipitationProbability = null
                )
                
                lifecycleScope.launch {
                    calendarLogWriter.writeExecutionResult(dummyEvent, ignoreDuplicate = true)
                }
            }
        }
    }
}
