package com.example.waytoearthwatch.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    var isRunning by remember { mutableStateOf(false) }
    var distance by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(0) }
    var heartRate by remember { mutableStateOf<Int?>(null) }
    var pace by remember { mutableStateOf<Int?>(null) }

    val scope = rememberCoroutineScope()

    // 1초마다 UI 업데이트
    LaunchedEffect(isRunning) {
        if (isRunning) {
            while (true) {
                delay(1000)
                runningManager.getCurrentSession()?.let { session ->
                    distance = session.totalDistanceMeters
                    duration = session.durationSeconds
                    heartRate = session.routePoints.lastOrNull()?.heartRate
                    pace = session.routePoints.lastOrNull()?.paceSeconds
                }
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
                // 시작 화면
                Button(
                    onClick = {
                        scope.launch {
                            runningManager.startRunning()
                            isRunning = true
                        }
                    }
                ) {
                    Text("러닝 시작")
                }
            } else {
                // 러닝 중 화면
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 거리 표시
                    Text(
                        text = String.format("%.2f km", distance / 1000.0),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 시간 표시
                    Text(
                        text = formatDuration(duration),
                        fontSize = 18.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 심박수 표시
                    heartRate?.let {
                        Text(text = "$it BPM", fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 페이스 표시
                    pace?.let {
                        Text(text = formatPace(it), fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 종료 버튼
                    Button(
                        onClick = {
                            scope.launch {
                                isRunning = false
                                onStop()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = androidx.compose.ui.graphics.Color.Red
                        )
                    ) {
                        Text("종료")
                    }
                }
            }
        }
    }
}

// 시간 포맷팅 (초 → HH:MM:SS)
private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, secs)
}

// 페이스 포맷팅 (초/km → MM:SS)
private fun formatPace(paceSeconds: Int): String {
    val minutes = paceSeconds / 60
    val seconds = paceSeconds % 60
    return String.format("%d'%02d\"", minutes, seconds)
}
