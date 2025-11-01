package cloud.waytoearth.watch.manager

import android.content.Context
import android.location.Location
import android.util.Log
import cloud.waytoearth.watch.data.RoutePoint
import cloud.waytoearth.watch.data.RunningSession
import cloud.waytoearth.watch.service.HeartRateService
import cloud.waytoearth.watch.service.HealthMetricsService
import cloud.waytoearth.watch.service.LocationService
import cloud.waytoearth.watch.service.PhoneCommunicationService
import cloud.waytoearth.watch.utils.DistanceCalculator
import cloud.waytoearth.watch.utils.HeartRateCalculator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

data class RunningState(
    val session: RunningSession? = null,
    val isPaused: Boolean = false,
    val isRunning: Boolean = false
)

class RunningManager(private val context: Context) {

    private val TAG = "RunningManager"
    private val locationService = LocationService(context)
    private val heartRateService = HeartRateService(context)

    private var currentSession: RunningSession? = null
    private var lastLocation: Location? = null
    private var currentHeartRate: Int? = null
    private var hsDistanceMeters: Int? = null
    private var hsPaceSeconds: Int? = null
    private var hsSpeedMps: Double? = null
    private var useHsDistance: Boolean = false
    private var paused: Boolean = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var realtimeJob: Job? = null

    // StateFlow로 상태 관리 - UI가 구독할 수 있도록
    private val _runningState = MutableStateFlow(RunningState())
    val runningState: StateFlow<RunningState> = _runningState.asStateFlow()

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

        // StateFlow 업데이트
        updateRunningState()

        // Health Services 시작
        heartRateService.startExercise()
        Log.d(TAG, "Exercise start requested (HS)")

        // HS 메트릭 수집 시작
        val metricsService = HealthMetricsService(context)
        scope.launch {
            metricsService.metricsFlow().collect { m ->
                m.heartRate?.let {
                    currentHeartRate = it
                    Log.v(TAG, "HS HR bpm=$it")
                }
                m.distanceMeters?.let {
                    hsDistanceMeters = it
                    useHsDistance = true
                    currentSession?.totalDistanceMeters = it
                    Log.v(TAG, "HS Distance=${it}m")
                }
                hsPaceSeconds = m.paceSecondsPerKm
                m.paceSecondsPerKm?.let { Log.v(TAG, "HS Pace=${it}s/km") }
                m.speedMps?.let { hsSpeedMps = it; Log.v(TAG, "HS Speed=${String.format("%.2f", it)} m/s") }
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
     * 외부 세션 ID로 러닝 시작 (폰 명령 시)
     */
    suspend fun startRunning(sessionId: String, runningType: String? = null) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "startRunning(external) sessionId=$sessionId type=${runningType ?: "N/A"}")

        currentSession = RunningSession(
            sessionId = sessionId,
            startTime = startTime,
            routePoints = mutableListOf()
        )

        // StateFlow 업데이트
        updateRunningState()

        heartRateService.startExercise()
        Log.d(TAG, "Exercise start requested (HS)")

        scope.launch {
            heartRateService.getHeartRateUpdates().collect { hr ->
                currentHeartRate = hr
                if (hr != null) Log.v(TAG, "HR update bpm=$hr")
            }
        }
        // HS 메트릭 수집 시작 (심박/거리/페이스/속도)
        val metricsService2 = HealthMetricsService(context)
        scope.launch {
            metricsService2.metricsFlow().collect { m ->
                m.heartRate?.let { currentHeartRate = it; Log.v(TAG, "HS HR bpm=$it") }
                m.distanceMeters?.let {
                    hsDistanceMeters = it
                    useHsDistance = true
                    currentSession?.totalDistanceMeters = it
                    Log.v(TAG, "HS Distance=${it}m")
                }
                hsPaceSeconds = m.paceSecondsPerKm
                m.paceSecondsPerKm?.let { Log.v(TAG, "HS Pace=${it}s/km") }
                m.speedMps?.let { hsSpeedMps = it; Log.v(TAG, "HS Speed=${String.format("%.2f", it)} m/s") }
            }
        }

        scope.launch {
            locationService.getLocationUpdates().collect { location ->
                onLocationUpdate(location)
            }
        }
    }

    /**
     * 위치 업데이트 처리 (1초마다 수신됨)
     */
    private fun onLocationUpdate(location: Location) {
        val session = currentSession ?: return
        val prevSize = session.routePoints.size


        if (paused) {
            lastLocation = location
            return
        }
        // 거리 계산: HS 거리 우선, 아니면 GPS 증분
        if (!useHsDistance) {
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
        }

        // 경과 시간 계산 (초)
        val elapsedSeconds = ((System.currentTimeMillis() - session.startTime) / 1000).toInt()
        session.durationSeconds = elapsedSeconds

        // 즉시 페이스 계산 (최근 100m 기준)
        val instantPace = when {
            hsPaceSeconds != null -> hsPaceSeconds
            hsSpeedMps != null && hsSpeedMps!! > 0.0 -> (1000.0 / hsSpeedMps!!).toInt()
            else -> calculateInstantPace(session.routePoints)
        }

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
        session.calories = (session.totalDistanceMeters / 1000.0 * 60).toInt()
        if ((prevSize + 1) % 10 == 0) {
            Log.d(TAG, "RoutePoint added count=${prevSize + 1} dist=${session.totalDistanceMeters}m dur=${session.durationSeconds}s")
        }
        lastLocation = location

        // StateFlow 업데이트
        updateRunningState()
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
        paused = false

        // StateFlow 업데이트
        updateRunningState()

        return session
    }

    /**
     * 실시간 동기화 시작 (10초 주기)
     */
    fun startRealtimeSync(comm: PhoneCommunicationService) {
        realtimeJob?.cancel()
        realtimeJob = scope.launch {
            Log.d(TAG, "Realtime sync started (10s interval)")
            while (isActive) {
                val snapshot = currentSession
                if (snapshot != null) {
                    val lastPoint = snapshot.routePoints.lastOrNull()
                    val avgPace = if (snapshot.totalDistanceMeters > 0) {
                        (snapshot.durationSeconds / (snapshot.totalDistanceMeters / 1000.0)).toInt()
                    } else null

                    val currentPoint = lastPoint?.let {
                        mapOf(
                            "latitude" to it.latitude,
                            "longitude" to it.longitude,
                            // API 호환: sequence는 1부터로 전달
                            "sequence" to (it.sequence + 1),
                            "t" to it.timestampSeconds,
                            "acc" to it.accuracy
                        )
                    }

                    val data = mapOf(
                        "sessionId" to snapshot.sessionId,
                        "distanceMeters" to snapshot.totalDistanceMeters,
                        "durationSeconds" to snapshot.durationSeconds,
                        "heartRate" to lastPoint?.heartRate,
                        "paceSeconds" to lastPoint?.paceSeconds,
                        "averagePaceSeconds" to avgPace,
                        "calories" to snapshot.calories,
                        "currentPoint" to currentPoint,
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
     * 현재 세션 스냅샷 가져오기 (실시간 UI 업데이트용)
     */
    fun getCurrentSession(): RunningSession? = currentSession
    fun pause() {
        paused = true
        Log.d(TAG, "Paused")
        updateRunningState()
    }

    fun resume() {
        paused = false
        Log.d(TAG, "Resumed")
        updateRunningState()
    }

    fun isPaused(): Boolean = paused
    fun isRunning(): Boolean = currentSession != null

    /**
     * StateFlow 업데이트 헬퍼 함수
     */
    private fun updateRunningState() {
        _runningState.value = RunningState(
            session = currentSession,
            isPaused = paused,
            isRunning = currentSession != null
        )
    }
}

