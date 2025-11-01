package cloud.waytoearth.watch.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.roundToInt
import java.lang.reflect.Proxy

class HeartRateService(private val context: Context) {

    private val TAG = "HeartRateService"
    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val heartRateSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

    private val exerciseClient by lazy { HealthServices.getClient(context).exerciseClient }

    // 심박수(BPM) 업데이트 스트림: 워치 센서 직접 사용
    fun getHeartRateUpdates(): Flow<Int?> = callbackFlow {
        val sensor = heartRateSensor
        if (sensor == null) {
            Log.e(TAG, "No heart rate sensor available")
            close(); return@callbackFlow
        }

        Log.d(TAG, "Using watch heart rate sensor directly")
        var ema: Double? = null
        val alpha = 0.3
        var lastEmit = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_HEART_RATE && event.values.isNotEmpty()) {
                    val raw = event.values[0]
                    val now = System.currentTimeMillis()
                    if (raw > 0f) {
                        ema = if (ema == null) raw.toDouble() else (alpha * raw + (1 - alpha) * ema!!)
                        if (now - lastEmit >= 1000) {
                            lastEmit = now
                            val bpm = ema!!.roundToInt()
                            Log.v(TAG, "Sensor HR update bpm=$bpm")
                            trySend(bpm)
                        }
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) { }
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        Log.d(TAG, "Heart rate sensor listener registered")
        awaitClose {
            sensorManager.unregisterListener(listener)
            Log.d(TAG, "Heart rate sensor listener unregistered")
        }
    }

    // Health Services로 운동 세션 켜서 HR/배터리 최적화, 측정은 센서 사용
    suspend fun startExercise() {
        try {
            val config = ExerciseConfig(
                exerciseType = ExerciseType.RUNNING,
                dataTypes = setOf(
                    DataType.HEART_RATE_BPM,
                    DataType.DISTANCE,
                    DataType.SPEED,
                    DataType.PACE,
                    DataType.LOCATION
                ),
                isAutoPauseAndResumeEnabled = false,
                isGpsEnabled = true
            )

            // prepareExercise 먼저 호출 (일부 기기에서 필수)
            try {
                val prepareMethod = exerciseClient.javaClass.methods.firstOrNull { it.name == "prepareExercise" }
                if (prepareMethod != null) {
                    prepareMethod.invoke(exerciseClient, config)
                    Log.d(TAG, "Health Services exercise PREPARE requested")
                    // prepare 후 2초 대기 (센서 초기화 시간)
                    kotlinx.coroutines.delay(2000)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "prepareExercise failed: ${e.message}")
            }

            // startExercise 호출
            val method = exerciseClient.javaClass.methods.firstOrNull { it.name == "startExercise" }
            method?.invoke(exerciseClient, config)
            Log.d(TAG, "Health Services exercise START requested")
        } catch (e: Throwable) {
            Log.w(TAG, "startExercise failed: ${e.message}")
        }
    }

    suspend fun endExercise() {
        try {

            val method = exerciseClient.javaClass.methods.firstOrNull { it.name == "endExercise" }
            method?.invoke(exerciseClient)
            Log.d(TAG, "Health Services exercise END requested")
        } catch (_: Throwable) { /* 폴백 유지 */ }
    }
}
