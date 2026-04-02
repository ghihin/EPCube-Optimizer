package com.ghihin.epcubeoptimizer.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ghihin.epcubeoptimizer.calendar.DailySchedule
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ScheduleViewerScreen(
    viewModel: ScheduleViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onTestMacroClicked: () -> Unit
) {
    val schedules by viewModel.schedules.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onBack) {
                Text("戻る")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = "1週間先の予測スケジュール", style = MaterialTheme.typography.titleMedium)
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(schedules) { schedule ->
                    ScheduleItemCard(schedule)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onTestMacroClicked,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("【デバッグ】今すぐ深夜マクロ実行を手動テスト")
        }
    }
}

@Composable
fun ScheduleItemCard(schedule: DailySchedule) {
    val dateFormatter = DateTimeFormatter.ofPattern("MM/dd (E)", Locale.JAPANESE)
    val dateStr = schedule.date.format(dateFormatter)

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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dateStr, 
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                
                val mode = if (schedule.isCommute) "外出モード【出社】" else "在宅モード"
                val modeColor = if (schedule.isCommute) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(modeColor.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = mode, 
                        style = MaterialTheme.typography.bodyMedium,
                        color = modeColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                schedule.weatherForecast?.let { weather ->
                    val weatherIcon = when {
                        weather.weatherDescription.contains("晴") -> "☀️"
                        weather.weatherDescription.contains("雨") -> "☔"
                        weather.weatherDescription.contains("雪") -> "⛄"
                        weather.weatherDescription.contains("曇") -> "☁️"
                        else -> "🌤️"
                    }
                    
                    Text(
                        text = "$weatherIcon ${weather.weatherDescription}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "雲量: ${weather.cloudiness}% / 降水確率: ${(weather.probabilityOfPrecipitation * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "予測SOC",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${schedule.predictedSoc ?: "-"}%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
