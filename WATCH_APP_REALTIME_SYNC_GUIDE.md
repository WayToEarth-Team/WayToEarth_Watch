# 🔄 워치-앱 실시간 연동 가이드

> 앱에서 시작하고, 워치는 센서 담당 + 실시간 통계 표시

---

## 📋 목차

1. [전체 아키텍처](#1-전체-아키텍처)
2. [데이터 흐름](#2-데이터-흐름)
3. [워치 명령 시스템](#3-워치-명령-시스템)
4. [실시간 동기화](#4-실시간-동기화)
5. [워치 UI 구현](#5-워치-ui-구현)
6. [앱 UI 연동](#6-앱-ui-연동)
7. [에러 처리](#7-에러-처리)
8. [테스트 가이드](#8-테스트-가이드)

---

## 1. 전체 아키텍처

### 1.1 역할 분담

```
[React Native 앱]
- 러닝 시작/종료 제어
- 타입 선택 (SINGLE/CREW/JOURNEY)
- 실시간 지도 + 통계 표시
- 백엔드 API 호출

[갤럭시 워치]
- 센서 데이터 수집 (GPS/만보기/심박수)
- 실시간 통계 표시만
- 앱의 명령을 받아서 동작
```

### 1.2 통신 구조

```
┌─────────────────────┐
│   React Native 앱   │
│                     │
│  [시작 버튼]         │ ──START──→  ┌──────────────┐
│  [지도 + 통계]       │             │  갤럭시 워치  │
│  [종료 버튼]         │ ←─UPDATE─── │             │
│                     │             │ [센서 수집]   │
│  [백엔드 전송]       │ ──STOP───→  │ [통계 표시]   │
└─────────────────────┘             └──────────────┘
         ↓
    백엔드 API
```

---

## 2. 데이터 흐름

### 2.1 전체 플로우

```
1. [앱] 사용자가 러닝 타입 선택 (SINGLE/CREW/JOURNEY)
2. [앱] "시작" 버튼 클릭
3. [앱] → [워치] START 명령 전송
4. [워치] 센서 시작 (GPS or 만보기 + 심박수)
5. [워치] → [앱] "시작됨" 응답
6. [앱] 러닝 화면으로 전환

--- 러닝 중 (10초마다 반복) ---

7. [워치] 센서 데이터 수집 (1초마다)
8. [워치] → [앱] 실시간 UPDATE 전송 (10초마다)
9. [앱] 지도 + 통계 업데이트
10. [워치] 통계 화면 업데이트

--- 종료 시 ---

11. [앱] "종료" 버튼 클릭
12. [앱] → [워치] STOP 명령 전송
13. [워치] 센서 중지 + 최종 데이터 생성
14. [워치] → [앱] COMPLETE 데이터 전송
15. [앱] → [백엔드] API 호출
16. [앱] 결과 화면 표시
```

### 2.2 메시지 타입

```kotlin
// 명령 메시지 (앱 → 워치)
/waytoearth/command/start   // 러닝 시작
/waytoearth/command/stop    // 러닝 종료

// 응답 메시지 (워치 → 앱)
/waytoearth/response/started    // 시작 완료
/waytoearth/response/stopped    // 종료 완료

// 실시간 데이터 (워치 → 앱, 10초마다)
/waytoearth/realtime/update

// 최종 데이터 (워치 → 앱, 종료 시)
/waytoearth/running/complete
```

---

## 3. 워치 명령 시스템

### 3.1 WatchCommandReceiver (워치)

**파일:** `app/src/main/java/com/waytoearth/watch/service/WatchCommandReceiver.kt`

```kotlin
package com.waytoearth.watch.service

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.*
import com.waytoearth.watch.data.RunningType
import com.waytoearth.watch.manager.RunningManager
import kotlinx.coroutines.*
import org.json.JSONObject

class WatchCommandReceiver(private val context: Context) :
    WearableListenerService() {

    private val TAG = "WatchCommandReceiver"
    private lateinit var runningManager: RunningManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        private const val PATH_COMMAND_START = "/waytoearth/command/start"
        private const val PATH_COMMAND_STOP = "/waytoearth/command/stop"
        private const val PATH_RESPONSE_STARTED = "/waytoearth/response/started"
        private const val PATH_RESPONSE_STOPPED = "/waytoearth/response/stopped"
    }

    override fun onCreate() {
        super.onCreate()
        runningManager = RunningManager(context)
    }

    @Override
    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "Message received: ${messageEvent.path}")

        when (messageEvent.path) {
            PATH_COMMAND_START -> {
                handleStartCommand(messageEvent)
            }
            PATH_COMMAND_STOP -> {
                handleStopCommand(messageEvent)
            }
        }
    }

    /**
     * 시작 명령 처리
     */
    private fun handleStartCommand(messageEvent: MessageEvent) {
        scope.launch {
            try {
                val json = JSONObject(String(messageEvent.data))
                val runningType = RunningType.valueOf(json.getString("runningType"))
                val sessionId = json.getString("sessionId")

                Log.d(TAG, "Starting running: type=$runningType, session=$sessionId")

                // 러닝 시작
                runningManager.startRunning(runningType, sessionId)

                // 실시간 동기화 시작
                runningManager.startRealtimeSync()

                // 성공 응답 전송
                sendResponse(PATH_RESPONSE_STARTED, mapOf(
                    "success" to true,
                    "sessionId" to sessionId,
                    "timestamp" to System.currentTimeMillis()
                ))

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start running", e)
                sendResponse(PATH_RESPONSE_STARTED, mapOf(
                    "success" to false,
                    "error" to e.message
                ))
            }
        }
    }

    /**
     * 종료 명령 처리
     */
    private fun handleStopCommand(messageEvent: MessageEvent) {
        scope.launch {
            try {
                val json = JSONObject(String(messageEvent.data))
                val sessionId = json.getString("sessionId")

                Log.d(TAG, "Stopping running: session=$sessionId")

                // 러닝 종료
                val session = runningManager.stopRunning()

                // 성공 응답 전송
                sendResponse(PATH_RESPONSE_STOPPED, mapOf(
                    "success" to true,
                    "sessionId" to sessionId
                ))

            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop running", e)
                sendResponse(PATH_RESPONSE_STOPPED, mapOf(
                    "success" to false,
                    "error" to e.message
                ))
            }
        }
    }

    /**
     * 응답 메시지 전송
     */
    private suspend fun sendResponse(path: String, data: Map<String, Any?>) {
        try {
            val json = JSONObject(data).toString()
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()

            nodes.forEach { node ->
                Wearable.getMessageClient(context)
                    .sendMessage(node.id, path, json.toByteArray())
                    .await()
            }

            Log.d(TAG, "Response sent: $path")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send response", e)
        }
    }
}
```

### 3.2 AndroidManifest.xml에 등록

```xml
<service
    android:name=".service.WatchCommandReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="com.google.android.gms.wearable.MESSAGE_RECEIVED" />
        <data android:scheme="wear" android:host="*" android:pathPrefix="/waytoearth" />
    </intent-filter>
</service>
```

---

## 4. 실시간 동기화

### 4.1 RunningManager 수정 (워치)

**파일:** `app/src/main/java/com/waytoearth/watch/manager/RunningManager.kt`

```kotlin
class RunningManager(private val context: Context) {

    // ... 기존 코드 ...

    private var realtimeSyncJob: Job? = null

    /**
     * 러닝 시작 (외부 명령으로 시작)
     */
    suspend fun startRunning(runningType: RunningType, sessionId: String): String {
        val startTime = System.currentTimeMillis()

        currentSession = RunningSession(
            sessionId = sessionId,  // 앱에서 받은 세션 ID 사용
            startTime = startTime,
            runningType = runningType,
            routePoints = mutableListOf()
        )

        // Health Services 시작
        heartRateService.startExercise()

        // 심박수 수집
        scope.launch {
            heartRateService.getHeartRateUpdates().collect { hr ->
                currentHeartRate = hr
            }
        }

        // 센서 시작
        when (runningType) {
            RunningType.SINGLE, RunningType.CREW -> startGpsTracking()
            RunningType.JOURNEY -> startStepCountTracking()
        }

        return sessionId
    }

    /**
     * 실시간 동기화 시작 (10초마다 앱으로 전송)
     */
    fun startRealtimeSync() {
        realtimeSyncJob?.cancel()

        realtimeSyncJob = scope.launch {
            while (isActive) {
                delay(10_000)  // 10초마다

                val session = currentSession ?: continue

                val realtimeData = JSONObject().apply {
                    put("sessionId", session.sessionId)
                    put("distanceMeters", session.totalDistanceMeters)
                    put("durationSeconds", session.durationSeconds)
                    put("heartRate", currentHeartRate)
                    put("calories", session.calories)
                    put("timestamp", System.currentTimeMillis())

                    // GPS 모드인 경우에만 위치 포함
                    if (session.runningType != RunningType.JOURNEY) {
                        lastLocation?.let {
                            put("latitude", it.latitude)
                            put("longitude", it.longitude)
                        }

                        // 최근 경로 포인트 (최근 10개만)
                        val recentPoints = session.routePoints.takeLast(10)
                        put("recentPoints", JSONArray(recentPoints.map { point ->
                            JSONObject().apply {
                                put("latitude", point.latitude)
                                put("longitude", point.longitude)
                                put("sequence", point.sequence)
                            }
                        }))
                    }

                    // 여정 모드인 경우 걸음 수 포함
                    if (session.runningType == RunningType.JOURNEY) {
                        put("steps", session.totalSteps)
                    }
                }

                // 앱으로 전송
                sendRealtimeUpdate(realtimeData.toString())
            }
        }
    }

    /**
     * 실시간 업데이트 전송
     */
    private suspend fun sendRealtimeUpdate(data: String) {
        try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()

            nodes.forEach { node ->
                Wearable.getMessageClient(context)
                    .sendMessage(
                        node.id,
                        "/waytoearth/realtime/update",
                        data.toByteArray()
                    )
                    .await()
            }
        } catch (e: Exception) {
            Log.e("RunningManager", "Failed to send realtime update", e)
        }
    }

    /**
     * 러닝 종료
     */
    override suspend fun stopRunning(): RunningSession? {
        // 실시간 동기화 중지
        realtimeSyncJob?.cancel()
        realtimeSyncJob = null

        // ... 기존 종료 로직 ...
    }
}
```

---

## 5. 워치 UI 구현

### 5.1 통계 전용 화면

**파일:** `app/src/main/java/com/waytoearth/watch/presentation/RunningStatsScreen.kt`

```kotlin
package com.waytoearth.watch.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.waytoearth.watch.data.RunningType
import com.waytoearth.watch.manager.RunningManager
import kotlinx.coroutines.delay

@Composable
fun RunningStatsScreen(
    runningManager: RunningManager,
    runningType: RunningType
) {
    var distance by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(0) }
    var heartRate by remember { mutableStateOf<Int?>(null) }
    var pace by remember { mutableStateOf<Int?>(null) }
    var steps by remember { mutableStateOf(0) }

    // 1초마다 UI 업데이트
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            runningManager.getCurrentSession()?.let { session ->
                distance = session.totalDistanceMeters
                duration = session.durationSeconds
                heartRate = currentHeartRate
                steps = session.totalSteps

                // 페이스 계산
                pace = if (distance > 0) {
                    (duration * 1000 / distance)
                } else null
            }
        }
    }

    Scaffold(
        timeText = { TimeText() }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 러닝 타입 표시
            item {
                Text(
                    text = when(runningType) {
                        RunningType.SINGLE -> "개인 러닝"
                        RunningType.CREW -> "크루 러닝"
                        RunningType.JOURNEY -> "여정 러닝"
                    },
                    style = MaterialTheme.typography.caption1,
                    color = androidx.compose.ui.graphics.Color.Gray
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // 거리
            item {
                Text(
                    text = "%.2f km".format(distance / 1000.0),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }

            // 시간
            item {
                Text(
                    text = formatDuration(duration),
                    fontSize = 20.sp
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // 심박수
            heartRate?.let {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "❤️", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "$it BPM", fontSize = 16.sp)
                    }
                }
                item { Spacer(modifier = Modifier.height(4.dp)) }
            }

            // 페이스
            pace?.let {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "⏱️", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = formatPace(it), fontSize = 16.sp)
                    }
                }
                item { Spacer(modifier = Modifier.height(4.dp)) }
            }

            // 걸음 수 (여정 러닝만)
            if (runningType == RunningType.JOURNEY && steps > 0) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🚶", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "$steps 걸음", fontSize = 16.sp)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // 안내 메시지
            item {
                Text(
                    text = "앱에서 종료하세요",
                    style = MaterialTheme.typography.caption2,
                    color = androidx.compose.ui.graphics.Color.Gray
                )
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, secs)
}

private fun formatPace(paceSeconds: Int): String {
    val minutes = paceSeconds / 60
    val seconds = paceSeconds % 60
    return String.format("%d'%02d\"", minutes, seconds)
}
```

### 5.2 MainActivity 수정

**파일:** `app/src/main/java/com/waytoearth/watch/presentation/MainActivity.kt`

```kotlin
class MainActivity : ComponentActivity() {

    private lateinit var runningManager: RunningManager
    private var currentRunningType: RunningType? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runningManager = RunningManager(this)

        setContent {
            var isRunning by remember { mutableStateOf(false) }

            if (isRunning && currentRunningType != null) {
                // 러닝 중 - 통계 화면
                RunningStatsScreen(
                    runningManager = runningManager,
                    runningType = currentRunningType!!
                )
            } else {
                // 대기 화면
                WaitingScreen()
            }
        }
    }

    /**
     * 외부에서 러닝 시작 (앱으로부터 명령 받음)
     */
    fun startRunningFromApp(runningType: RunningType) {
        currentRunningType = runningType
        // UI 업데이트는 Compose State로 자동 처리
    }

    /**
     * 외부에서 러닝 종료
     */
    fun stopRunningFromApp() {
        currentRunningType = null
    }
}

@Composable
fun WaitingScreen() {
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
            Text(
                text = "WayToEarth",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "앱에서 러닝을\n시작하세요",
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = androidx.compose.ui.graphics.Color.Gray
            )
        }
    }
}
```

---

## 6. 앱 UI 연동

### 6.1 WatchService 확장 (React Native)

**파일:** `src/services/WatchService.ts`

```typescript
class WatchService {
  // ... 기존 코드 ...

  /**
   * 워치에 러닝 시작 명령 전송
   */
  async startRunningOnWatch(
    runningType: 'SINGLE' | 'CREW' | 'JOURNEY',
    sessionId: string
  ): Promise<boolean> {
    try {
      const command = {
        action: 'START',
        runningType,
        sessionId,
        timestamp: Date.now()
      };

      return await WatchModule.sendMessageToWatch(
        '/waytoearth/command/start',
        JSON.stringify(command)
      );
    } catch (error) {
      console.error('Failed to start running on watch:', error);
      return false;
    }
  }

  /**
   * 워치에 러닝 종료 명령 전송
   */
  async stopRunningOnWatch(sessionId: string): Promise<boolean> {
    try {
      const command = {
        action: 'STOP',
        sessionId,
        timestamp: Date.now()
      };

      return await WatchModule.sendMessageToWatch(
        '/waytoearth/command/stop',
        JSON.stringify(command)
      );
    } catch (error) {
      console.error('Failed to stop running on watch:', error);
      return false;
    }
  }

  /**
   * 워치 시작 응답 리스너
   */
  onRunningStartedResponse(callback: (data: string) => void) {
    return this.eventEmitter.addListener('onRunningStartedResponse', (event: any) => {
      callback(event.data);
    });
  }

  /**
   * 실시간 업데이트 리스너
   */
  onRealtimeUpdate(callback: (data: string) => void) {
    return this.eventEmitter.addListener('onRealtimeUpdate', (event: any) => {
      callback(event.data);
    });
  }

  /**
   * 러닝 완료 데이터 리스너
   */
  onRunningComplete(callback: (data: string) => void) {
    return this.eventEmitter.addListener('onRunningComplete', (event: any) => {
      callback(event.data);
    });
  }
}

export default new WatchService();
```

### 6.2 러닝 화면 (React Native)

**파일:** `src/screens/RunningScreen.tsx`

```typescript
import React, { useEffect, useState } from 'react';
import { View, Text, Button, Alert } from 'react-native';
import WatchService from '../services/WatchService';
import MapView, { Polyline } from 'react-native-maps';

interface Props {
  route: {
    params: {
      runningType: 'SINGLE' | 'CREW' | 'JOURNEY';
    };
  };
  navigation: any;
}

const RunningScreen: React.FC<Props> = ({ route, navigation }) => {
  const { runningType } = route.params;
  const [sessionId] = useState(`app-${Date.now()}`);
  const [isStarted, setIsStarted] = useState(false);

  // 실시간 통계
  const [distance, setDistance] = useState(0);
  const [duration, setDuration] = useState(0);
  const [heartRate, setHeartRate] = useState<number | null>(null);
  const [calories, setCalories] = useState(0);

  // 지도 (GPS 모드만)
  const [routeCoordinates, setRouteCoordinates] = useState<any[]>([]);

  useEffect(() => {
    // 워치 시작 응답 리스너
    const startedSub = WatchService.onRunningStartedResponse((data) => {
      const response = JSON.parse(data);
      if (response.success) {
        setIsStarted(true);
        Alert.alert('러닝 시작', '워치에서 데이터 수집이 시작되었습니다.');
      } else {
        Alert.alert('오류', '워치 시작에 실패했습니다.');
      }
    });

    // 실시간 업데이트 리스너
    const updateSub = WatchService.onRealtimeUpdate((data) => {
      const update = JSON.parse(data);

      setDistance(update.distanceMeters);
      setDuration(update.durationSeconds);
      setHeartRate(update.heartRate);
      setCalories(update.calories);

      // GPS 모드: 지도 업데이트
      if (runningType !== 'JOURNEY' && update.latitude && update.longitude) {
        setRouteCoordinates(prev => [
          ...prev,
          { latitude: update.latitude, longitude: update.longitude }
        ]);
      }
    });

    // 러닝 완료 리스너
    const completeSub = WatchService.onRunningComplete(async (data) => {
      const completeData = JSON.parse(data);

      // 백엔드 API 호출
      await sendToBackend(completeData);

      // 결과 화면으로 이동
      navigation.replace('RunningResult', { data: completeData });
    });

    return () => {
      WatchService.removeListener(startedSub);
      WatchService.removeListener(updateSub);
      WatchService.removeListener(completeSub);
    };
  }, []);

  /**
   * 러닝 시작
   */
  const handleStart = async () => {
    const success = await WatchService.startRunningOnWatch(runningType, sessionId);

    if (!success) {
      Alert.alert('오류', '워치 연결을 확인해주세요.');
    }
  };

  /**
   * 러닝 종료
   */
  const handleStop = async () => {
    Alert.alert(
      '러닝 종료',
      '러닝을 종료하시겠습니까?',
      [
        { text: '취소', style: 'cancel' },
        {
          text: '종료',
          onPress: async () => {
            await WatchService.stopRunningOnWatch(sessionId);
          }
        }
      ]
    );
  };

  /**
   * 백엔드로 전송
   */
  const sendToBackend = async (data: any) => {
    // 기존 API 호출 로직
  };

  return (
    <View style={{ flex: 1 }}>
      {/* 지도 (GPS 모드만) */}
      {runningType !== 'JOURNEY' && (
        <MapView style={{ flex: 1 }}>
          {routeCoordinates.length > 0 && (
            <Polyline
              coordinates={routeCoordinates}
              strokeColor="#0000FF"
              strokeWidth={3}
            />
          )}
        </MapView>
      )}

      {/* 통계 */}
      <View style={{ padding: 16, backgroundColor: 'white' }}>
        <Text style={{ fontSize: 32, fontWeight: 'bold' }}>
          {(distance / 1000).toFixed(2)} km
        </Text>
        <Text style={{ fontSize: 20 }}>
          {formatDuration(duration)}
        </Text>
        {heartRate && <Text>❤️ {heartRate} BPM</Text>}
        <Text>🔥 {calories} kcal</Text>

        {!isStarted ? (
          <Button title="시작" onPress={handleStart} />
        ) : (
          <Button title="종료" onPress={handleStop} color="red" />
        )}
      </View>
    </View>
  );
};

const formatDuration = (seconds: number): string => {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  return `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
};

export default RunningScreen;
```

---

## 7. 에러 처리

### 7.1 연결 끊김 처리

**워치:**
```kotlin
// 연결 상태 모니터링
fun monitorConnection() {
    scope.launch {
        while (isActive) {
            delay(5000)  // 5초마다 체크

            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            if (nodes.isEmpty()) {
                // 연결 끊김 - 데이터 로컬 저장
                saveDataLocally()
            }
        }
    }
}
```

**앱:**
```typescript
// 타임아웃 처리
const startWithTimeout = async () => {
  const timeout = new Promise((_, reject) =>
    setTimeout(() => reject(new Error('Timeout')), 10000)
  );

  try {
    await Promise.race([
      WatchService.startRunningOnWatch(runningType, sessionId),
      timeout
    ]);
  } catch (error) {
    Alert.alert('오류', '워치가 응답하지 않습니다.');
  }
};
```

### 7.2 재연결 시 복구

```kotlin
// 워치에서 저장된 데이터 확인
fun recoverSession(): RunningSession? {
    val savedSession = localStorage.getSession()
    if (savedSession != null && savedSession.isRecent()) {
        return savedSession
    }
    return null
}
```

---

## 8. 테스트 가이드

### 8.1 테스트 시나리오

**시나리오 1: 정상 플로우**
```
1. 앱에서 "SINGLE" 선택
2. "시작" 버튼 클릭
3. 워치 화면 확인 (통계 표시)
4. 10초 대기 → 앱 통계 업데이트 확인
5. 지도에 경로 표시 확인
6. "종료" 버튼 클릭
7. 백엔드 전송 확인
```

**시나리오 2: 연결 끊김**
```
1. 러닝 시작
2. 워치 Bluetooth 끄기
3. 워치에서 로컬 저장 확인
4. Bluetooth 다시 켜기
5. 데이터 전송 확인
```

### 8.2 로그 확인

```bash
# 워치 로그
adb -s <watch-id> logcat | grep -E "WatchCommandReceiver|RunningManager"

# 앱 로그
adb logcat | grep -E "WatchService|WearableListener"
```

---

## 9. 요약

### 역할 분담
```
앱:  시작/종료 제어 + 지도 + 백엔드
워치: 센서 수집 + 통계 표시
```

### 통신 구조
```
START 명령 → 워치 센서 시작 → 10초마다 UPDATE → STOP 명령 → COMPLETE 데이터 → 백엔드
```

### 장점
- 간단한 UX (앱에서 모든 제어)
- 명확한 역할 분담
- 워치 배터리 절약 (UI 최소화)

---

**이제 앱-워치 실시간 연동 준비 완료! 🎉**
