package com.ghihin.epcubeoptimizer.presentation.main

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ghihin.epcubeoptimizer.BuildConfig
import com.ghihin.epcubeoptimizer.core.accessibility.EpCubeAccessibilityService

/**
 * メイン画面の Composable。
 * [MainViewModel] から UI 状態を受け取り、Loading / Error / Success を切り替えて表示する。
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        when (val state = uiState) {
            is MainUiState.Loading -> LoadingContent()
            is MainUiState.Error -> ErrorContent(
                message = state.message,
                onRetry = { viewModel.load() }
            )
            is MainUiState.Success -> SuccessContent(
                state = state,
                onToggleCommute = { viewModel.toggleTomorrowCommute() }
            )
        }
        
        // バージョン情報を画面下部に表示
        Text(
            text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ---------------------------------------------------------------------------
// ローディング
// ---------------------------------------------------------------------------

@Composable
private fun LoadingContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(modifier = Modifier.size(56.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "天気情報を取得中…", style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------------------------------------------------------------------
// エラー
// ---------------------------------------------------------------------------

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "⚠️ エラー",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onRetry) {
            Text("再試行")
        }
    }
}

// ---------------------------------------------------------------------------
// 成功
// ---------------------------------------------------------------------------

@Composable
private fun SuccessContent(
    state: MainUiState.Success,
    onToggleCommute: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // タイトル
        Text(
            text = "EPCube 目標 SOC",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        // 目標 SOC — 大きく表示
        Text(
            text = "${state.targetSoc.value}%",
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Divider()

        // 天気情報カード
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "☁️ 明日の天気（鶴ヶ島市）",
                    style = MaterialTheme.typography.labelLarge
                )
                InfoRow(label = "雲量", value = "${state.forecast.cloudiness}%")
                InfoRow(
                    label = "降水確率",
                    value = "${"%.0f".format(state.forecast.probabilityOfPrecipitation * 100)}%"
                )
            }
        }

        Divider()

        // スケジュール切り替えカード
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "📅 明日のスケジュール",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = if (state.isTomorrowCommute) "出社" else "在宅",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (state.isTomorrowCommute)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.secondary
                    )
                }
                Switch(
                    checked = state.isTomorrowCommute,
                    onCheckedChange = { onToggleCommute() }
                )
            }
        }

        // 計算根拠
        Text(
            text = "計算根拠: 雲量 ${state.targetSoc.factors.cloudiness}%・" +
                "降水確率 ${"%.0f".format(state.targetSoc.factors.pop * 100)}%・" +
                "スケジュール ${if (state.targetSoc.factors.isCommuteDay) "出社" else "在宅"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // マクロ実行ボタン
        Button(
            onClick = {
                val intent = Intent(context, EpCubeAccessibilityService::class.java).apply {
                    action = EpCubeAccessibilityService.ACTION_START_MACRO
                    putExtra(EpCubeAccessibilityService.EXTRA_TARGET_SOC, state.targetSoc.value)
                }
                context.startService(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("EP CUBEに反映")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
