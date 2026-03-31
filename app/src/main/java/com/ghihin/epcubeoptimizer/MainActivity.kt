package com.ghihin.epcubeoptimizer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.ghihin.epcubeoptimizer.core.accessibility.EpCubeAccessibilityService
import com.ghihin.epcubeoptimizer.presentation.main.MainScreen
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
                    MainScreen()
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
