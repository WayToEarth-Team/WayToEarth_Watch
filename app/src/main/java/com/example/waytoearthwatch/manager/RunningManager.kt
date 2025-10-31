package com.example.waytoearthwatch.manager

import android.content.Context
import android.location.Location
import android.util.Log
import com.example.waytoearthwatch.data.RoutePoint
import com.example.waytoearthwatch.data.RunningSession
import com.example.waytoearthwatch.service.HeartRateService
import com.example.waytoearthwatch.service.HealthMetricsService
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
    private var hsDistanceMeters: Int? = null
    private var hsPaceSeconds: Int? = null
    private var hsSpeedMps: Double? = null
    private var useHsDistance: Boolean = false
    private var paused: Boolean = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var realtimeJob: Job? = null

    /**
     * ?щ떇 ?쒖옉
     * @return ?몄뀡 ID
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

        // Health Services ?쒖옉
        heartRateService.startExercise()
        Log.d(TAG, "Exercise start requested (HS)")

        // HS 硫뷀듃由??섏쭛 ?쒖옉
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

        // ?꾩튂 ?섏쭛 ?쒖옉 (1珥덈쭏??
        scope.launch {
            locationService.getLocationUpdates().collect { location ->
                onLocationUpdate(location)
            }
        }

        return sessionId
    }

    /**
     * ?몃? ?몄뀡 ID濡??щ떇 ?쒖옉 (??紐낅졊 ???
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
        // HS 硫뷀듃由??섏쭛 ?쒖옉 (?щ컯/嫄곕━/?띾룄/?섏씠??
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
     * ?꾩튂 ?낅뜲?댄듃 泥섎━ (1珥덈쭏???몄텧??
     */
    private fun onLocationUpdate(location: Location) {
        val session = currentSession ?: return
        val prevSize = session.routePoints.size

        
        if (paused) {
            lastLocation = location
            return
        }
// 嫄곕━ 怨꾩궛: HS 嫄곕━ ?곗꽑, ?꾨땲硫?GPS 利앸텇
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

        // 寃쎄낵 ?쒓컙 怨꾩궛 (珥?
        val elapsedSeconds = ((System.currentTimeMillis() - session.startTime) / 1000).toInt()
        session.durationSeconds = elapsedSeconds

        // 利됱떆 ?섏씠??怨꾩궛 (理쒓렐 100m 湲곗?)
        val instantPace = when {
            hsPaceSeconds != null -> hsPaceSeconds
            hsSpeedMps != null && hsSpeedMps!! > 0.0 -> (1000.0 / hsSpeedMps!!).toInt()
            else -> calculateInstantPace(session.routePoints)
        }

        // RoutePoint ?앹꽦
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
    }

    /**
     * 利됱떆 ?섏씠??怨꾩궛 (理쒓렐 100m 湲곗?)
     */
    private fun calculateInstantPace(points: List<RoutePoint>): Int? {
        if (points.size < 2) return null

        // 理쒓렐 100m 援ш컙 李얘린
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
     * ?щ떇 醫낅즺 諛??곗씠??諛섑솚
     * @return RunningSession
     */
    suspend fun stopRunning(): RunningSession? {
        val session = currentSession ?: return null

        // Health Services 醫낅즺
        heartRateService.endExercise()
        Log.d(TAG, "Exercise end requested (HS)")

        // ?щ컯???듦퀎 怨꾩궛
        val heartRates = session.routePoints.mapNotNull { it.heartRate }
        session.averageHeartRate = HeartRateCalculator.calculateAverage(heartRates)
        session.maxHeartRate = HeartRateCalculator.calculateMax(heartRates)

        // ?됯퇏 ?섏씠??怨꾩궛
        val averagePaceSeconds = DistanceCalculator.calculatePace(
            session.totalDistanceMeters,
            session.durationSeconds
        )

        // 移쇰줈由?怨꾩궛 (媛꾨떒??怨듭떇: 1km??60kcal)
        session.calories = (session.totalDistanceMeters / 1000.0 * 60).toInt()

        currentSession = null
        lastLocation = null
        currentHeartRate = null
        paused = false

        return session
    }

    /**
     * ?ㅼ떆媛??숆린???쒖옉 (10珥?二쇨린)
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
     * ?꾩옱 ?몄뀡 ?뺣낫 媛?몄삤湲?(?ㅼ떆媛?UI ?낅뜲?댄듃??
     */
    fun getCurrentSession(): RunningSession? = currentSession
    fun pause() { paused = true; Log.d(TAG, "Paused") }

    fun resume() { paused = false; Log.d(TAG, "Resumed") }
}


