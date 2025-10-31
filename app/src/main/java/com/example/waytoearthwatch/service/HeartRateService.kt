package com.example.waytoearthwatch.service

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

    // 심박수(BPM) 업데이트 스트림: Health Services 우선, 실패 시 센서 폴백
    fun getHeartRateUpdates(): Flow<Int?> = callbackFlow {
        val exerciseClient = try {
            val c = HealthServices.getClient(context).exerciseClient
            Log.d(TAG, "Health Services exerciseClient acquired")
            c
        } catch (t: Throwable) {
            Log.w(TAG, "Health Services unavailable, fallback to sensor: ${t.message}")
            null
        }
        if (exerciseClient != null) {
            try {
                val executor = ContextCompat.getMainExecutor(context)
                val callbackInterface = Class.forName("androidx.health.services.client.ExerciseUpdateCallback")
                val proxy = Proxy.newProxyInstance(
                    callbackInterface.classLoader,
                    arrayOf(callbackInterface)
                ) { _, method, args ->
                    // Object 기본 메서드 처리
                    when (method.name) {
                        "hashCode" -> return@newProxyInstance System.identityHashCode(this)
                        "equals" -> return@newProxyInstance (args?.getOrNull(0) === this)
                        "toString" -> return@newProxyInstance "ExerciseUpdateCallbackProxy@" + Integer.toHexString(System.identityHashCode(this))
                    }
                    if (method.name.contains("onExerciseUpdateReceived") && args != null && args.isNotEmpty()) {
                        val update = args[0]
                        try {
                            // update.latestMetrics?.getData(DataType.HEART_RATE_BPM)?.lastOrNull()?.value
                            val latestMetrics = update.javaClass.methods.firstOrNull { it.name == "getLatestMetrics" }?.invoke(update)
                            val dataTypeClazz = Class.forName("androidx.health.services.client.data.DataType")
                            val hrField = dataTypeClazz.getField("HEART_RATE_BPM")
                            val hrType = hrField.get(null)
                            val getDataMethod = latestMetrics?.javaClass?.methods?.firstOrNull { it.name == "getData" && it.parameterTypes.size == 1 }
                            val list = getDataMethod?.invoke(latestMetrics, hrType) as? List<*>
                            val last = list?.lastOrNull()
                            val value = last?.javaClass?.methods?.firstOrNull { it.name == "getValue" }?.invoke(last)
                            val bpm = when (value) {
                                is Number -> value.toInt()
                                else -> null
                            }
                            if (bpm != null && bpm > 0) {
                                Log.v(TAG, "HS HR update bpm=$bpm")
                                trySend(bpm)
                            }
                        } catch (_: Throwable) { /* ignore single update parse error */ }
                    }
                    null
                }

                // setUpdateCallback(executor, callback)
                val setCb = exerciseClient.javaClass.methods.firstOrNull { it.name == "setUpdateCallback" && it.parameterTypes.size == 2 }
                if (setCb != null) {
                    setCb.invoke(exerciseClient, executor, proxy)
                    Log.d(TAG, "Health Services update callback registered")
                    awaitClose {
                        try {
                            val clear = exerciseClient.javaClass.methods.firstOrNull { it.name.contains("clear") && it.parameterTypes.isEmpty() }
                            clear?.invoke(exerciseClient)
                            Log.d(TAG, "Health Services update callback cleared")
                        } catch (_: Throwable) {}
                    }
                    return@callbackFlow
                }
                Log.w(TAG, "Health Services setUpdateCallback not found, sensor fallback")
            } catch (_: Throwable) { /* fallback to sensor */ }
        }

        // 센서 폴백
        val sensor = heartRateSensor
        if (sensor == null) {
            Log.e(TAG, "No heart rate sensor available")
            close(); return@callbackFlow
        }
        Log.d(TAG, "Using Sensor fallback for heart rate")
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
        Log.d(TAG, "Sensor listener registered")
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    // Health Services로 운동 세션 켜서 HR/배터리 최적화, 측정은 센서 사용
    suspend fun startExercise() {
        try {
            val config = ExerciseConfig(
                exerciseType = ExerciseType.RUNNING,
                dataTypes = setOf(DataType.HEART_RATE_BPM),
                isAutoPauseAndResumeEnabled = false,
                isGpsEnabled = true
            )
            // 일부 버전에서 메서드 시그니처 차이를 회피하기 위해 리플렉션 사용
            val method = exerciseClient.javaClass.methods.firstOrNull { it.name == "startExercise" }
            method?.invoke(exerciseClient, config)
            Log.d(TAG, "Health Services exercise START requested")
        } catch (_: Throwable) { /* 폴백 유지 */ }
    }

    suspend fun endExercise() {
        try {

            val method = exerciseClient.javaClass.methods.firstOrNull { it.name == "endExercise" }
            method?.invoke(exerciseClient)
            Log.d(TAG, "Health Services exercise END requested")
        } catch (_: Throwable) { /* 폴백 유지 */ }
    }
}
