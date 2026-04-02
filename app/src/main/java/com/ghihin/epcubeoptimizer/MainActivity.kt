package com.ghihin.epcubeoptimizer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ghihin.epcubeoptimizer.core.accessibility.EpCubeAccessibilityService
import com.ghihin.epcubeoptimizer.presentation.main.MainScreen
import com.ghihin.epcubeoptimizer.ui.schedule.ScheduleViewerScreen
import dagger.hilt.android.AndroidEntryPoint

/** アプリのエントリーポイントとなる Activity。Hilt による DI を受け取る。 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == EpCubeAccessibilityService.ACTION_MACRO_RESULT) {
            val isSuccess = intent.getBooleanExtra(EpCubeAccessibilityService.EXTRA_IS_SUCCESS, false)
            val targetSoc = intent.getIntExtra(EpCubeAccessibilityService.EXTRA_TARGET_SOC_RESULT, -1)
            val errorMessage = intent.getStringExtra(EpCubeAccessibilityService.EXTRA_ERROR_MESSAGE)
            Log.d("MainActivity", "Macro Result: success=$isSuccess, targetSoc=$targetSoc, error=$errorMessage")
            // TODO: Pass result to ViewModel
        }
    }
}
