# 🎯 갤럭시 워치 앱 완전 구현 가이드

> WayToEarth 러닝 추적 앱 - 워치 측 전체 구현 문서

---

## 📋 목차

1. [전체 아키텍처](#1-전체-아키텍처)
2. [데이터 구조 및 흐름](#2-데이터-구조-및-흐름)
3. [Phase 1: GPS 추적 구현](#phase-1-gps-추적-구현)
4. [Phase 2: 심박수 센서 구현](#phase-2-심박수-센서-구현)
5. [Phase 3: 데이터 수집 및 저장](#phase-3-데이터-수집-및-저장)
6. [Phase 4: 폰 전송 구현](#phase-4-폰-전송-구현)
7. [Phase 5: UI/UX 구현](#phase-5-uiux-구현)
8. [계산 로직 상세](#계산-로직-상세)
9. [테스트 가이드](#테스트-가이드)

---

## 1. 전체 아키텍처

### 시스템 구성도

```
[갤럭시 워치 (Wear OS)]
    ↓ GPS 센서 (1초마다)
    ↓ 심박수 센서 (1초마다)
    ↓ 고도계/만보기
    ↓
[데이터 수집 레이어]
    ↓ RoutePoint 객체 생성
    ↓
[메모리 저장 (List<RoutePoint>)]
    ↓ 러닝 종료 시
    ↓
[JSON 직렬화]
    ↓
[Wearable Data Layer API]
    ↓
[연결된 안드로이드 폰]
    ↓
[백엔드 API 전송]
    ↓
[Spring Boot 서버]
```

### 데이터 흐름

```
1초마다 반복:
  GPS → 위도/경도/고도/정확도
  심박수 센서 → 현재 심박수
  계산 → 누적거리, 페이스
  저장 → RoutePoint 리스트에 추가

러닝 종료 시:
  List<RoutePoint> → JSON 변환
  JSON → Wearable Data Layer로 전송
  폰 → 백엔드 API 호출
  백엔드 → DB 저장
```

---

## 2. 데이터 구조 및 흐름

### RoutePoint 데이터 클래스

**파일:** `app/src/main/java/com/waytoearth/watch/data/RoutePoint.kt`

```kotlin
package com.waytoearth.watch.data

data class RoutePoint(
    val latitude: Double,              // 위도
    val longitude: Double,             // 경도
    val sequence: Int,                 // 순서 (0부터 시작)
    val timestampSeconds: Int,         // 시작 시점부터 경과 시간 (초)
    val heartRate: Int?,               // 심박수 (BPM) - nullable
    val paceSeconds: Int?,             // 현재 페이스 (초/km) - nullable
    val altitude: Double?,             // 고도 (m) - nullable
    val accuracy: Double?,             // GPS 정확도 (m) - nullable
    val cumulativeDistanceMeters: Int  // 누적 거리 (m)
)
```

### RunningSession 데이터 클래스

**파일:** `app/src/main/java/com/waytoearth/watch/data/RunningSession.kt`

```kotlin
package com.waytoearth.watch.data

data class RunningSession(
    val sessionId: String,                   // 세션 ID (UUID)
    val startTime: Long,                     // 시작 시각 (epoch millis)
    val routePoints: MutableList<RoutePoint>, // 경로 포인트 리스트
    var totalDistanceMeters: Int = 0,        // 총 거리 (m)
    var durationSeconds: Int = 0,            // 총 시간 (초)
    var averageHeartRate: Int? = null,       // 평균 심박수
    var maxHeartRate: Int? = null,           // 최대 심박수
    var calories: Int = 0                    // 칼로리
)
```

### 백엔드 전송 JSON 구조

```json
{
  "sessionId": "watch-uuid-12345",
  "distanceMeters": 5200,
  "durationSeconds": 1800,
  "averagePaceSeconds": 346,
  "calories": 350,
  "averageHeartRate": 145,
  "maxHeartRate": 178,
  "routePoints": [
    {
      "latitude": 37.5665,
      "longitude": 126.9780,
      "sequence": 0,
      "timestampSeconds": 0,
      "heartRate": 120,
      "paceSeconds": 0,
      "altitude": 45.2,
      "accuracy": 5.0,
      "cumulativeDistanceMeters": 0
    },
    {
      "latitude": 37.56655,
      "longitude": 126.97805,
      "sequence": 1,
      "timestampSeconds": 1,
      "heartRate": 122,
      "paceSeconds": 330,
      "altitude": 45.5,
      "accuracy": 4.8,
      "cumulativeDistanceMeters": 5
    }
    // ... 1초마다 계속
  ]
}
```

---

## Phase 1: GPS 추적 구현

### 1.1 권한 요청 화면 구현

**파일:** `app/src/main/java/com/waytoearth/watch/presentation/PermissionScreen.kt`

```kotlin
package com.waytoearth.watch.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.google.accompanist.permissions.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionScreen(onPermissionsGranted: () -> Unit) {
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.BODY_SENSORS,
            android.Manifest.permission.ACTIVITY_RECOGNITION
        )
    )

    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            onPermissionsGranted()
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
            Text(
                text = "위치 및 센서 권한이 필요합니다",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.body2
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { permissionsState.launchMultiplePermissionRequest() }
            ) {
                Text("권한 허용")
            }
        }
    }
}
```

### 1.2 LocationService 구현

**파일:** `app/src/main/java/com/waytoearth/watch/service/LocationService.kt`

```kotlin
package com.waytoearth.watch.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class LocationService(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun getLocationUpdates(): Flow<Location> = callbackFlow {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L  // 1초마다 업데이트
        ).apply {
            setMinUpdateIntervalMillis(1000L)
            setMaxUpdateDelayMillis(1000L)
        }.build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    trySend(location)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        awaitClose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun getLastKnownLocation(): Location? {
        return try {
            fusedLocationClient.lastLocation.await()
        } catch (e: Exception) {
            null
        }
    }
}
```

### 1.3 거리 계산 유틸리티 (Haversine 공식)

**파일:** `app/src/main/java/com/waytoearth/watch/utils/DistanceCalculator.kt`

```kotlin
package com.waytoearth.watch.utils

import kotlin.math.*

object DistanceCalculator {

    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Haversine 공식을 사용하여 두 GPS 좌표 간 거리 계산
     * @param lat1 첫 번째 위도
     * @param lon1 첫 번째 경도
     * @param lat2 두 번째 위도
     * @param lon2 두 번째 경도
     * @return 거리 (미터)
     */
    fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_METERS * c
    }

    /**
     * 페이스 계산 (초/km)
     * @param distanceMeters 거리 (미터)
     * @param durationSeconds 시간 (초)
     * @return 페이스 (초/km), 거리가 0이면 null
     */
    fun calculatePace(distanceMeters: Int, durationSeconds: Int): Int? {
        if (distanceMeters == 0) return null
        val distanceKm = distanceMeters / 1000.0
        return (durationSeconds / distanceKm).toInt()
    }

    /**
     * 즉시 페이스 계산 (최근 100m 기준)
     * @param recentDistanceMeters 최근 거리 (미터, 권장: 100m)
     * @param recentDurationSeconds 최근 시간 (초)
     * @return 즉시 페이스 (초/km)
     */
    fun calculateInstantPace(
        recentDistanceMeters: Double,
        recentDurationSeconds: Int
    ): Int? {
        if (recentDistanceMeters < 10) return null  // 너무 짧은 거리는 제외
        val distanceKm = recentDistanceMeters / 1000.0
        return (recentDurationSeconds / distanceKm).toInt()
    }
}
```

---

## Phase 2: 심박수 센서 구현

### 2.1 HeartRateService 구현

**파일:** `app/src/main/java/com/waytoearth/watch/service/HeartRateService.kt`

```kotlin
package com.waytoearth.watch.service

import android.content.Context
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HeartRateService(private val context: Context) {

    private val healthServicesClient = HealthServices.getClient(context)
    private val exerciseClient = healthServicesClient.exerciseClient

    /**
     * 심박수 실시간 스트림
     * @return Flow<Int?> - 심박수 BPM (null 가능)
     */
    fun getHeartRateUpdates(): Flow<Int?> {
        return exerciseClient.exerciseUpdateFlow
            .map { update: ExerciseUpdate ->
                val heartRateDataPoint = update.latestMetrics[DataType.HEART_RATE_BPM]
                heartRateDataPoint?.last()?.value?.toInt()
            }
    }

    /**
     * 운동 세션 시작
     */
    suspend fun startExercise() {
        val config = androidx.health.services.client.data.ExerciseConfig(
            exerciseType = ExerciseType.RUNNING,
            dataTypes = setOf(DataType.HEART_RATE_BPM),
            isAutoPauseAndResumeEnabled = false,
            isGpsEnabled = false  // GPS는 별도로 관리
        )
        exerciseClient.startExercise(config)
    }

    /**
     * 운동 세션 종료
     */
    suspend fun endExercise() {
        exerciseClient.endExercise()
    }
}
```

### 2.2 심박수 통계 계산

**파일:** `app/src/main/java/com/waytoearth/watch/utils/HeartRateCalculator.kt`

```kotlin
package com.waytoearth.watch.utils

object HeartRateCalculator {

    /**
     * 평균 심박수 계산
     * @param heartRates 심박수 리스트 (null 제외)
     * @return 평균 심박수 (null이면 null)
     */
    fun calculateAverage(heartRates: List<Int>): Int? {
        if (heartRates.isEmpty()) return null
        return heartRates.average().toInt()
    }

    /**
     * 최대 심박수 계산
     * @param heartRates 심박수 리스트 (null 제외)
     * @return 최대 심박수 (null이면 null)
     */
    fun calculateMax(heartRates: List<Int>): Int? {
        return heartRates.maxOrNull()
    }
}
```

---

## Phase 3: 데이터 수집 및 저장

### 3.1 RunningManager (핵심 로직)

**파일:** `app/src/main/java/com/waytoearth/watch/manager/RunningManager.kt`

```kotlin
package com.waytoearth.watch.manager

import android.content.Context
import android.location.Location
import com.waytoearth.watch.data.RoutePoint
import com.waytoearth.watch.data.RunningSession
import com.waytoearth.watch.service.HeartRateService
import com.waytoearth.watch.service.LocationService
import com.waytoearth.watch.utils.DistanceCalculator
import com.waytoearth.watch.utils.HeartRateCalculator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

class RunningManager(private val context: Context) {

    private val locationService = LocationService(context)
    private val heartRateService = HeartRateService(context)

    private var currentSession: RunningSession? = null
    private var lastLocation: Location? = null
    private var currentHeartRate: Int? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * 러닝 시작
     * @return 세션 ID
     */
    suspend fun startRunning(): String {
        val sessionId = "watch-${UUID.randomUUID()}"
        val startTime = System.currentTimeMillis()

        currentSession = RunningSession(
            sessionId = sessionId,
            startTime = startTime,
            routePoints = mutableListOf()
        )

        // Health Services 시작
        heartRateService.startExercise()

        // 심박수 수집 시작
        scope.launch {
            heartRateService.getHeartRateUpdates().collect { hr ->
                currentHeartRate = hr
            }
        }

        // 위치 수집 시작 (1초마다)
        scope.launch {
            locationService.getLocationUpdates().collect { location ->
                onLocationUpdate(location)
            }
        }

        return sessionId
    }

    /**
     * 위치 업데이트 처리 (1초마다 호출됨)
     */
    private fun onLocationUpdate(location: Location) {
        val session = currentSession ?: return

        // 거리 계산
        val distanceIncrement = if (lastLocation != null) {
            DistanceCalculator.calculateDistance(
                lastLocation!!.latitude,
                lastLocation!!.longitude,
                location.latitude,
                location.longitude
            )
        } else {
            0.0
        }

        session.totalDistanceMeters += distanceIncrement.toInt()

        // 경과 시간 계산 (초)
        val elapsedSeconds = ((System.currentTimeMillis() - session.startTime) / 1000).toInt()
        session.durationSeconds = elapsedSeconds

        // 즉시 페이스 계산 (최근 100m 기준)
        val instantPace = calculateInstantPace(session.routePoints)

        // RoutePoint 생성
        val routePoint = RoutePoint(
            latitude = location.latitude,
            longitude = location.longitude,
            sequence = session.routePoints.size,
            timestampSeconds = elapsedSeconds,
            heartRate = currentHeartRate,
            paceSeconds = instantPace,
            altitude = location.altitude,
            accuracy = location.accuracy.toDouble(),
            cumulativeDistanceMeters = session.totalDistanceMeters
        )

        session.routePoints.add(routePoint)
        lastLocation = location
    }

    /**
     * 즉시 페이스 계산 (최근 100m 기준)
     */
    private fun calculateInstantPace(points: List<RoutePoint>): Int? {
        if (points.size < 2) return null

        // 최근 100m 구간 찾기
        val currentDistance = points.last().cumulativeDistanceMeters
        val targetDistance = currentDistance - 100

        val startPoint = points.findLast {
            it.cumulativeDistanceMeters <= targetDistance
        } ?: return null

        val recentDistance = (currentDistance - startPoint.cumulativeDistanceMeters).toDouble()
        val recentDuration = points.last().timestampSeconds - startPoint.timestampSeconds

        return DistanceCalculator.calculateInstantPace(recentDistance, recentDuration)
    }

    /**
     * 러닝 종료 및 데이터 반환
     * @return RunningSession
     */
    suspend fun stopRunning(): RunningSession? {
        val session = currentSession ?: return null

        // Health Services 종료
        heartRateService.endExercise()

        // 심박수 통계 계산
        val heartRates = session.routePoints.mapNotNull { it.heartRate }
        session.averageHeartRate = HeartRateCalculator.calculateAverage(heartRates)
        session.maxHeartRate = HeartRateCalculator.calculateMax(heartRates)

        // 평균 페이스 계산
        val averagePaceSeconds = DistanceCalculator.calculatePace(
            session.totalDistanceMeters,
            session.durationSeconds
        )

        // 칼로리 계산 (간단한 공식: 1km당 60kcal)
        session.calories = (session.totalDistanceMeters / 1000.0 * 60).toInt()

        currentSession = null
        lastLocation = null
        currentHeartRate = null

        return session
    }

    /**
     * 현재 세션 정보 가져오기 (실시간 UI 업데이트용)
     */
    fun getCurrentSession(): RunningSession? = currentSession
}
```

---

## Phase 4: 폰 전송 구현

### 4.1 Wearable Data Layer API 설정

**build.gradle.kts에 추가:**

```kotlin
dependencies {
    // Wearable Data Layer
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
}
```

### 4.2 PhoneCommunicationService 구현

**파일:** `app/src/main/java/com/waytoearth/watch/service/PhoneCommunicationService.kt`

```kotlin
package com.waytoearth.watch.service

import android.content.Context
import com.google.android.gms.wearable.*
import com.google.gson.Gson
import com.waytoearth.watch.data.RunningSession
import kotlinx.coroutines.tasks.await

class PhoneCommunicationService(private val context: Context) {

    private val dataClient: DataClient = Wearable.getDataClient(context)
    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val gson = Gson()

    companion object {
        private const val RUNNING_DATA_PATH = "/waytoearth/running/complete"
        private const val MESSAGE_PATH = "/waytoearth/message"
    }

    /**
     * 러닝 데이터를 폰으로 전송
     * @param session 러닝 세션 데이터
     * @return 성공 여부
     */
    suspend fun sendRunningDataToPhone(session: RunningSession): Boolean {
        return try {
            // JSON 변환
            val json = gson.toJson(session)

            // PutDataRequest 생성
            val putDataReq = PutDataMapRequest.create(RUNNING_DATA_PATH).apply {
                dataMap.putString("session_data", json)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()

            // 전송
            dataClient.putDataItem(putDataReq).await()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 메시지로 즉시 전송 (데이터 레이어보다 빠름)
     * @param session 러닝 세션 데이터
     * @return 성공 여부
     */
    suspend fun sendRunningDataViaMessage(session: RunningSession): Boolean {
        return try {
            val json = gson.toJson(session)
            val nodes = getConnectedNodes()

            if (nodes.isEmpty()) {
                return false
            }

            // 연결된 모든 노드(폰)에 메시지 전송
            nodes.forEach { nodeId ->
                messageClient.sendMessage(
                    nodeId,
                    MESSAGE_PATH,
                    json.toByteArray()
                ).await()
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 연결된 폰 노드 가져오기
     */
    private suspend fun getConnectedNodes(): List<String> {
        return try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            nodes.map { it.id }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
```

### 4.3 전송 데이터 DTO (폰에서 백엔드로 전송할 형식)

```kotlin
package com.waytoearth.watch.data

data class RunningCompleteRequest(
    val sessionId: String,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val averagePaceSeconds: Int?,
    val calories: Int,
    val averageHeartRate: Int?,
    val maxHeartRate: Int?,
    val routePoints: List<RoutePoint>
)

// RunningSession을 백엔드 요청 형식으로 변환
fun RunningSession.toBackendRequest(): RunningCompleteRequest {
    val averagePaceSeconds = if (totalDistanceMeters > 0) {
        (durationSeconds.toDouble() / (totalDistanceMeters / 1000.0)).toInt()
    } else null

    return RunningCompleteRequest(
        sessionId = sessionId,
        distanceMeters = totalDistanceMeters,
        durationSeconds = durationSeconds,
        averagePaceSeconds = averagePaceSeconds,
        calories = calories,
        averageHeartRate = averageHeartRate,
        maxHeartRate = maxHeartRate,
        routePoints = routePoints
    )
}
```

---

## Phase 5: UI/UX 구현

### 5.1 메인 러닝 화면

**파일:** `app/src/main/java/com/waytoearth/watch/presentation/RunningScreen.kt`

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
import com.waytoearth.watch.manager.RunningManager

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

    // 1초마다 UI 업데이트
    LaunchedEffect(isRunning) {
        if (isRunning) {
            while (true) {
                kotlinx.coroutines.delay(1000)
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
                        kotlinx.coroutines.GlobalScope.launch {
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
                        text = "${distance / 1000.0} km",
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
                        Text(text = "❤️ $it BPM", fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 페이스 표시
                    pace?.let {
                        Text(text = "⏱️ ${formatPace(it)}", fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 종료 버튼
                    Button(
                        onClick = {
                            kotlinx.coroutines.GlobalScope.launch {
                                runningManager.stopRunning()
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
```

### 5.2 MainActivity 통합

**파일:** `app/src/main/java/com/waytoearth/watch/presentation/MainActivity.kt`

```kotlin
package com.waytoearth.watch.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.waytoearth.watch.manager.RunningManager
import com.waytoearth.watch.service.PhoneCommunicationService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var runningManager: RunningManager
    private lateinit var phoneCommunication: PhoneCommunicationService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runningManager = RunningManager(this)
        phoneCommunication = PhoneCommunicationService(this)

        setContent {
            var hasPermissions by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            if (!hasPermissions) {
                PermissionScreen(
                    onPermissionsGranted = { hasPermissions = true }
                )
            } else {
                RunningScreen(
                    runningManager = runningManager,
                    onStop = {
                        scope.launch {
                            // 러닝 종료 후 폰으로 전송
                            val session = runningManager.stopRunning()
                            session?.let {
                                val success = phoneCommunication.sendRunningDataViaMessage(it)
                                if (success) {
                                    // 전송 성공 처리
                                } else {
                                    // 전송 실패 처리
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}
```

---

## 계산 로직 상세

### Haversine 공식 (거리 계산)

```
a = sin²(Δφ/2) + cos(φ1) * cos(φ2) * sin²(Δλ/2)
c = 2 * atan2(√a, √(1−a))
d = R * c

여기서:
- φ = 위도 (라디안)
- λ = 경도 (라디안)
- R = 지구 반지름 (6,371km = 6,371,000m)
- d = 거리 (미터)
```

### 페이스 계산

```
페이스 (초/km) = 총 시간 (초) ÷ 총 거리 (km)

예시:
- 1800초 (30분) 동안 5km 달렸다면
- 페이스 = 1800 ÷ 5 = 360초/km = 6분/km
```

### 즉시 페이스 계산 (최근 100m 기준)

```
최근 페이스 (초/km) = 최근 시간 (초) ÷ 최근 거리 (km)

예시:
- 최근 100m를 30초에 달렸다면
- 페이스 = 30 ÷ 0.1 = 300초/km = 5분/km
```

### 칼로리 계산 (간단 공식)

```
칼로리 (kcal) = 거리 (km) × 60

더 정확한 공식 (체중 포함):
칼로리 (kcal) = 거리 (km) × 체중 (kg) × 1.036

예시:
- 5km 달렸고 체중이 70kg이면
- 칼로리 = 5 × 70 × 1.036 = 362.6 kcal
```

---

## 테스트 가이드

### 단계별 테스트

#### 1. 권한 테스트
- 앱 실행 → 권한 요청 화면 확인
- "권한 허용" 버튼 클릭
- 시스템 권한 팝업에서 모두 허용
- 메인 화면으로 이동 확인

#### 2. GPS 테스트
- 에뮬레이터: Tools → Device File Explorer → GPS 시뮬레이션
- 실제 워치: 야외에서 GPS 신호 잡힐 때까지 대기
- 위치 업데이트 로그 확인

#### 3. 심박수 테스트
- 실제 워치 착용 후 테스트
- 에뮬레이터는 심박수 시뮬레이션 불가능
- Logcat에서 심박수 데이터 수신 확인

#### 4. 통합 러닝 테스트
```
1. 앱 실행 → "러닝 시작" 버튼 클릭
2. 10분 이상 러닝
3. 실시간으로 거리/시간/심박수 업데이트 확인
4. "종료" 버튼 클릭
5. 폰으로 데이터 전송 확인
6. 백엔드 API 호출 확인
```

#### 5. 로그 확인
```kotlin
// Logcat 필터
tag:RunningManager OR tag:LocationService OR tag:HeartRateService

// 확인할 로그
- "Location update: lat=37.xxx, lng=126.xxx, distance=123m"
- "Heart rate: 145 BPM"
- "RoutePoint added: sequence=10, cumulative=456m"
- "Running completed: 123 points, 5.2km, 1800s"
- "Data sent to phone successfully"
```

### 데이터 검증

#### RoutePoint 검증
```kotlin
// 각 RoutePoint 확인 사항:
- latitude, longitude: 유효한 GPS 좌표
- sequence: 0부터 순차적 증가
- timestampSeconds: 0부터 1씩 증가
- heartRate: 40~220 범위 (또는 null)
- paceSeconds: 180~900 범위 (3분~15분/km, 또는 null)
- altitude: 실제 고도 범위 (또는 null)
- accuracy: GPS 정확도 (보통 5~50m)
- cumulativeDistanceMeters: 계속 증가
```

#### JSON 검증
```bash
# 전송된 JSON 예시
{
  "sessionId": "watch-uuid-xxx",
  "distanceMeters": 5200,           # 총 5.2km
  "durationSeconds": 1800,          # 30분
  "averagePaceSeconds": 346,        # 5분 46초/km
  "calories": 350,
  "averageHeartRate": 145,
  "maxHeartRate": 178,
  "routePoints": [...1800개...]     # 1초마다 1개 = 1800개
}
```

---

## 배터리 최적화

### 주의사항

1. **GPS 정확도 조정**
   - 초기 5분: HIGH_ACCURACY
   - 이후: BALANCED_POWER_ACCURACY

2. **화면 밝기**
   - 러닝 중 자동 어두움 모드

3. **센서 샘플링**
   - GPS: 1초마다 (변경 불가)
   - 심박수: Health Services 기본값 사용

4. **메모리 관리**
   - RoutePoint 리스트가 크면 주기적으로 폰에 전송
   - 1시간 이상 러닝 시: 1000개마다 부분 전송

---

## 트러블슈팅

### GPS 신호 안 잡힘
- 워치를 야외로 이동
- 에뮬레이터: GPS 시뮬레이션 활성화
- `ACCESS_FINE_LOCATION` 권한 확인

### 심박수 데이터 안 나옴
- 워치를 손목에 꼭 착용
- Health Services 권한 (`BODY_SENSORS`) 확인
- 실제 워치에서만 테스트 가능

### 폰 연결 안 됨
- Bluetooth 연결 확인
- Galaxy Wearable 앱에서 워치 연결 상태 확인
- 폰과 워치 모두 앱 설치 필요

### 데이터 전송 실패
- 네트워크 연결 확인 (폰)
- 백엔드 서버 상태 확인
- JWT 토큰 유효성 확인

---

## 다음 단계

1. ✅ 기본 설정 완료
2. ✅ Hello World 실행
3. 🔄 Phase 1: GPS 구현
4. ⏳ Phase 2: 심박수 구현
5. ⏳ Phase 3: 데이터 수집
6. ⏳ Phase 4: 폰 전송
7. ⏳ Phase 5: UI/UX

---

## 참고 자료

- [Wear OS Developer Guide](https://developer.android.com/training/wearables)
- [Health Services API](https://developer.android.com/training/wearables/health-services)
- [Wearable Data Layer](https://developer.android.com/training/wearables/data-layer)
- [FusedLocationProvider](https://developers.google.com/location-context/fused-location-provider)

---

**준비 완료! Phase 1부터 시작합시다! 💪**
