package com.example.waytoearthwatch.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.example.waytoearthwatch.manager.RunningManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RunningScreen(
    runningManager: RunningManager,
    onStop: () -> Unit
) {
    var distance by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(0) }
    var heartRate by remember { mutableStateOf<Int?>(null) }
    var pace by remember { mutableStateOf<Int?>(null) }
    var calories by remember { mutableStateOf(0) }
    var isRunning by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // 1초마다 UI 업데이트
    LaunchedEffect(true) {
        while (true) {
            delay(1000)
            val session = runningManager.getCurrentSession()
            isRunning = session != null
            paused = runningManager.isPaused()
            session?.let {
                distance = it.totalDistanceMeters
                duration = it.durationSeconds
                heartRate = it.routePoints.lastOrNull()?.heartRate
                pace = it.routePoints.lastOrNull()?.paceSeconds
                calories = it.calories
            }
        }
    }

    Scaffold(
        timeText = { TimeText() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isRunning) {
                // 대기 화면 - 폰에서만 시작 가능
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "📱",
                        fontSize = 40.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "폰 앱에서",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.primary
                    )
                    Text(
                        text = "러닝을 시작하세요",
                        fontSize = 14.sp,
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f)
                    )
                }
            } else {
                // 러닝 진행 화면 - 가독성 향상
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (paused) {
                        Text(
                            text = "일시정지 중",
                            color = Color(0xFFEF6C00),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    // 거리 크게 + 단위 작게
                    val distanceKm = distance / 1000.0
                    val distanceText = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colors.primary
                            )
                        ) {
                            append(String.format("%.2f", distanceKm))
                        }
                        append(" ")
                        withStyle(
                            SpanStyle(
                                fontSize = 14.sp,
                                color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f)
                            )
                        ) {
                            append("km")
                        }
                    }
                    Text(distanceText)

                    Spacer(modifier = Modifier.height(8.dp))

                    // 2x2 메트릭: 시간 / 심박 / 페이스 / 칼로리
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Metric(label = "시간", value = formatDuration(duration))
                        Metric(label = "심박", value = heartRate?.let { "$it BPM" } ?: "-")
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Metric(label = "페이스", value = pace?.let { formatPace(it) } ?: "-")
                        Metric(label = "칼로리", value = String.format("%d kcal", calories))
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 컨트롤: 일시정지/재개 + 종료
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    if (paused) runningManager.resume() else runningManager.pause()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(0.4f),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = if (paused) Color(0xFF43A047) else Color(0xFFFB8C00),
                                contentColor = Color.White
                            )
                        ) {
                            Text(if (paused) "재개" else "일시정지", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { scope.launch { onStop() } },
                            modifier = Modifier.fillMaxWidth(0.85f),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color(0xFFE53935),
                                contentColor = Color.White
                            )
                        ) {
                            Text("종료", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption2,
            color = Color.Black.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black
        )
    }
}

// 시간 포맷팅(HH:MM:SS)
private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, secs)
}

// 페이스 포맷팅(분'초")
private fun formatPace(paceSeconds: Int): String {
    val minutes = paceSeconds / 60
    val seconds = paceSeconds % 60
    return String.format("%d'%02d\"", minutes, seconds)
}
