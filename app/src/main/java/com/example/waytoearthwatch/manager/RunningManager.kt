package com.example.waytoearthwatch.manager

import android.content.Context
import android.location.Location
import android.util.Log
import com.example.waytoearthwatch.data.RoutePoint
import com.example.waytoearthwatch.data.RunningSession
import com.example.waytoearthwatch.service.HeartRateService
import com.example.waytoearthwatch.service.LocationService
import com.example.waytoearthwatch.utils.DistanceCalculator
import com.example.waytoearthwatch.utils.HeartRateCalculator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

class RunningManager(private val context: Context) {

    private val TAG = "RunningManager"
    private val locationService = LocationService(context)
    private val heartRateService = HeartRateService(context)

    private var currentSession: RunningSession? = null
    private var lastLocation: Location? = null
    private var currentHeartRate: Int? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var realtimeJob: Job? = null

    /**
     * 러닝 시작
     * @return 세션 ID
     */
    suspend fun startRunning(): String {
        val sessionId = "watch-${UUID.randomUUID()}"
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "startRunning(generated) sessionId=$sessionId")

        currentSession = RunningSession(
            sessionId = sessionId,
            startTime = startTime,
            routePoints = mutableListOf()
        )

        // Health Services 시작
        heartRateService.startExercise()
        Log.d(TAG, "Exercise start requested (HS)")

        // 심박수 수집 시작
        scope.launch {
            heartRateService.getHeartRateUpdates().collect { hr ->
                currentHeartRate = hr
                if (hr != null) Log.v(TAG, "HR update bpm=$hr")
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
     * 외부 세션 ID로 러닝 시작 (폰 명령 대응)
     */
    suspend fun startRunning(sessionId: String, runningType: String? = null) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "startRunning(external) sessionId=$sessionId type=${runningType ?: "N/A"}")

        currentSession = RunningSession(
            sessionId = sessionId,
            startTime = startTime,
            routePoints = mutableListOf()
        )

        heartRateService.startExercise()
        Log.d(TAG, "Exercise start requested (HS)")

        scope.launch {
            heartRateService.getHeartRateUpdates().collect { hr ->
                currentHeartRate = hr
                if (hr != null) Log.v(TAG, "HR update bpm=$hr")
            }
        }

        scope.launch {
            locationService.getLocationUpdates().collect { location ->
                onLocationUpdate(location)
            }
        }
    }

    /**
     * 위치 업데이트 처리 (1초마다 호출됨)
     */
    private fun onLocationUpdate(location: Location) {
        val session = currentSession ?: return
        val prevSize = session.routePoints.size

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
        if ((prevSize + 1) % 10 == 0) {
            Log.d(TAG, "RoutePoint added count=${prevSize + 1} dist=${session.totalDistanceMeters}m dur=${session.durationSeconds}s")
        }
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
        Log.d(TAG, "Exercise end requested (HS)")

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
     * 실시간 동기화 시작 (10초 주기)
     */
    fun startRealtimeSync(comm: com.example.waytoearthwatch.service.PhoneCommunicationService) {
        realtimeJob?.cancel()
        realtimeJob = scope.launch {
            Log.d(TAG, "Realtime sync started (10s interval)")
            while (isActive) {
                val snapshot = currentSession
                if (snapshot != null) {
                    val lastPoint = snapshot.routePoints.lastOrNull()
                    val data = mapOf(
                        "sessionId" to snapshot.sessionId,
                        "distanceMeters" to snapshot.totalDistanceMeters,
                        "durationSeconds" to snapshot.durationSeconds,
                        "heartRate" to lastPoint?.heartRate,
                        "paceSeconds" to lastPoint?.paceSeconds,
                        "timestamp" to System.currentTimeMillis()
                    )
                    try {
                        Log.v(TAG, "Realtime tick send distance=${snapshot.totalDistanceMeters} dur=${snapshot.durationSeconds}")
                        comm.sendRealtimeUpdate(data)
                    } catch (e: Exception) {
                        Log.w(TAG, "Realtime send failed: ${e.message}")
                    }
                }
                delay(10_000)
            }
        }
    }

    fun stopRealtimeSync() {
        realtimeJob?.cancel()
        Log.d(TAG, "Realtime sync stopped")
        realtimeJob = null
    }

    /**
     * 현재 세션 정보 가져오기 (실시간 UI 업데이트용)
     */
    fun getCurrentSession(): RunningSession? = currentSession
}
