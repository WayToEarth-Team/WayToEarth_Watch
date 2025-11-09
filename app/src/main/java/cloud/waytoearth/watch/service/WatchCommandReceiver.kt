package cloud.waytoearth.watch.service

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import cloud.waytoearth.watch.manager.RunningManager
import cloud.waytoearth.watch.utils.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

class WatchCommandReceiver : WearableListenerService() {

    private val tag = "WatchCommandReceiver"
    private lateinit var runningManager: RunningManager
    private lateinit var comm: PhoneCommunicationService
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        runningManager = RunningManager.getInstance(this)
        comm = PhoneCommunicationService(this)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(tag, "onMessageReceived path=${messageEvent.path}")
        when (messageEvent.path) {
            PhoneCommunicationService.PATH_COMMAND_START -> handleStart(messageEvent)
            PhoneCommunicationService.PATH_COMMAND_STOP -> handleStop(messageEvent)
            PhoneCommunicationService.PATH_COMMAND_PAUSE -> handlePause(messageEvent)
            PhoneCommunicationService.PATH_COMMAND_RESUME -> handleResume(messageEvent)
            PhoneCommunicationService.PATH_COMMAND_SYNC_PROFILE -> handleSyncProfile(messageEvent)
            else -> Log.w(tag, "Unknown path: ${messageEvent.path}")
        }
    }

    private fun handleStart(messageEvent: MessageEvent) {
        scope.launch {
            try {
                val json = JSONObject(String(messageEvent.data))
                val sessionId = json.optString("sessionId")
                val runningTypeStr = json.optString("runningType", "")
                val runningType = if (runningTypeStr.isBlank()) null else runningTypeStr

                Log.d(tag, "START command: sessionId=$sessionId, type=$runningType")

                runningManager.startRunning(sessionId, runningType)
                runningManager.startRealtimeSync(comm)

                comm.sendResponseStarted(
                    mapOf(
                        "success" to true,
                        "sessionId" to sessionId,
                        "timestamp" to System.currentTimeMillis()
                    )
                )
                Log.d(tag, "START handled session=$sessionId")
            } catch (e: Exception) {
                Log.e(tag, "Failed to handle START", e)
                comm.sendResponseStarted(
                    mapOf(
                        "success" to false,
                        "error" to (e.message ?: "unknown")
                    )
                )
            }
        }
    }

    private fun handleStop(messageEvent: MessageEvent) {
        scope.launch {
            try {
                val json = JSONObject(String(messageEvent.data))
                val sessionId = json.optString("sessionId")

                Log.d(tag, "STOP command: sessionId=$sessionId")

                val session = runningManager.stopRunning()
                runningManager.stopRealtimeSync()

                comm.sendResponseStopped(
                    mapOf(
                        "success" to true,
                        "sessionId" to sessionId
                    )
                )
                Log.d(tag, "STOP handled session=$sessionId")

                // 최종 세션 전송 (DataClient 사용)
                if (session != null) {
                    val avgPace = if (session.totalDistanceMeters > 0) {
                        (session.durationSeconds / (session.totalDistanceMeters / 1000.0)).toInt()
                    } else null
                    val points = session.routePoints.map { p ->
                        mapOf(
                            "latitude" to p.latitude,
                            "longitude" to p.longitude,
                            "sequence" to (p.sequence + 1),
                            "timestampSeconds" to p.timestampSeconds,
                            "heartRate" to p.heartRate,
                            "paceSeconds" to p.paceSeconds,
                            "altitude" to p.altitude,
                            "accuracy" to p.accuracy,
                            "cumulativeDistanceMeters" to p.cumulativeDistanceMeters
                        )
                    }
                    val ok = comm.sendRunningCompleteDataLayer(
                        sessionId = session.sessionId,
                        distanceMeters = session.totalDistanceMeters,
                        durationSeconds = session.durationSeconds,
                        averagePaceSeconds = avgPace,
                        calories = session.calories,
                        averageHeartRate = session.averageHeartRate,
                        maxHeartRate = session.maxHeartRate,
                        routePoints = points
                    )
                    Log.d(tag, "Send complete (DataClient): $ok, points=${points.size}, size≈${session.routePoints.size * 150}bytes")
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to handle STOP", e)
                comm.sendResponseStopped(
                    mapOf(
                        "success" to false,
                        "error" to (e.message ?: "unknown")
                    )
                )
            }
        }
    }

    private fun handlePause(messageEvent: MessageEvent) {
        scope.launch {
            try {
                val json = JSONObject(String(messageEvent.data))
                val sessionId = json.optString("sessionId")
                runningManager.pause()
                comm.sendResponsePaused(mapOf("success" to true, "sessionId" to sessionId))
                Log.d(tag, "PAUSE handled session=$sessionId")
            } catch (e: Exception) {
                Log.e(tag, "Failed to handle PAUSE", e)
                comm.sendResponsePaused(mapOf("success" to false, "error" to (e.message ?: "unknown")))
            }
        }
    }

    private fun handleResume(messageEvent: MessageEvent) {
        scope.launch {
            try {
                val json = JSONObject(String(messageEvent.data))
                val sessionId = json.optString("sessionId")
                runningManager.resume()
                comm.sendResponseResumed(mapOf("success" to true, "sessionId" to sessionId))
                Log.d(tag, "RESUME handled session=$sessionId")
            } catch (e: Exception) {
                Log.e(tag, "Failed to handle RESUME", e)
                comm.sendResponseResumed(mapOf("success" to false, "error" to (e.message ?: "unknown")))
            }
        }
    }

    private fun handleSyncProfile(messageEvent: MessageEvent) {
        scope.launch {
            try {
                val json = JSONObject(String(messageEvent.data))
                val weight = json.optInt("weight", 0)
                val height = json.optInt("height", 0)

                Log.d(tag, "SYNC_PROFILE command: weight=$weight, height=$height")

                // 체중과 키 저장 (0이 아닌 값만)
                if (weight > 0) {
                    UserPreferences.saveWeight(applicationContext, weight)
                    Log.d(tag, "Weight saved: $weight kg")
                }
                if (height > 0) {
                    UserPreferences.saveHeight(applicationContext, height)
                    Log.d(tag, "Height saved: $height cm")
                }

                Log.d(tag, "SYNC_PROFILE handled successfully")
            } catch (e: Exception) {
                Log.e(tag, "Failed to handle SYNC_PROFILE", e)
            }
        }
    }
}
