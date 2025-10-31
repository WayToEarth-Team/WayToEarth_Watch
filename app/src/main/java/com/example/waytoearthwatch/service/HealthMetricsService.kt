package cloud.waytoearth.watch.service

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.health.services.client.HealthServices
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.lang.reflect.Proxy

data class HealthMetrics(
    val heartRate: Int? = null,
    val distanceMeters: Int? = null,
    val speedMps: Double? = null,
    val paceSecondsPerKm: Int? = null
)

class HealthMetricsService(private val context: Context) {
    private val TAG = "HealthMetricsService"

    fun metricsFlow(): Flow<HealthMetrics> = callbackFlow {
        val exerciseClient = try {
            val c = HealthServices.getClient(context).exerciseClient
            Log.d(TAG, "HS exerciseClient acquired")
            c
        } catch (t: Throwable) {
            Log.w(TAG, "HS unavailable: ${t.message}")
            null
        }
        if (exerciseClient == null) {
            close(); return@callbackFlow
        }

        try {
            val executor = ContextCompat.getMainExecutor(context)
            val callbackInterface = Class.forName("androidx.health.services.client.ExerciseUpdateCallback")
            Log.d(TAG, "Callback interface loaded: ${callbackInterface.name}")
            val proxy = Proxy.newProxyInstance(
                callbackInterface.classLoader,
                arrayOf(callbackInterface)
            ) { _, method, args ->
                // Object 기본 메서드 처리: hashCode/equals/toString
                when (method.name) {
                    "hashCode" -> return@newProxyInstance System.identityHashCode(this)
                    "equals" -> return@newProxyInstance (args?.getOrNull(0) === this)
                    "toString" -> return@newProxyInstance "ExerciseUpdateCallbackProxy@" + Integer.toHexString(System.identityHashCode(this))
                }
                if (method.name.contains("onExerciseUpdateReceived") && args != null && args.isNotEmpty()) {
                    val update = args[0]
                    try {
                        val latestMetrics = update.javaClass.methods.firstOrNull { it.name == "getLatestMetrics" }?.invoke(update)
                        if (latestMetrics == null) Log.w(TAG, "latestMetrics is null")
                        val dataTypeClazz = Class.forName("androidx.health.services.client.data.DataType")
                        val hrType = dataTypeClazz.getField("HEART_RATE_BPM").get(null)
                        val distType = dataTypeClazz.getField("DISTANCE").get(null)
                        val speedType = dataTypeClazz.getField("SPEED").get(null)
                        val paceType = dataTypeClazz.getField("PACE").get(null)

                        val getDataMethod = latestMetrics?.javaClass?.methods?.firstOrNull { it.name == "getData" && it.parameterTypes.size == 1 }
                        if (getDataMethod == null) Log.w(TAG, "getData method not found on metrics")

                        fun lastValue(list: Any?): Any? {
                            val l = list as? List<*> ?: return null
                            val last = l.lastOrNull() ?: return null
                            return last.javaClass.methods.firstOrNull { it.name == "getValue" }?.invoke(last)
                        }

                        val hr = (lastValue(getDataMethod?.invoke(latestMetrics, hrType)) as? Number)?.toInt()
                        val dist = (lastValue(getDataMethod?.invoke(latestMetrics, distType)) as? Number)?.toDouble()?.toInt()
                        val speed = (lastValue(getDataMethod?.invoke(latestMetrics, speedType)) as? Number)?.toDouble()
                        val pace = (lastValue(getDataMethod?.invoke(latestMetrics, paceType)) as? Number)?.toInt()

                        val m = HealthMetrics(
                            heartRate = hr?.takeIf { it > 0 },
                            distanceMeters = dist?.takeIf { it >= 0 },
                            speedMps = speed?.takeIf { it >= 0.0 },
                            paceSecondsPerKm = pace?.takeIf { it >= 0 }
                        )
                        Log.v(TAG, "HS metrics hr=${m.heartRate} dist=${m.distanceMeters} speed=${m.speedMps} pace=${m.paceSecondsPerKm}")
                        trySend(m)
                    } catch (e: Throwable) {
                        Log.w(TAG, "Parse metrics failed: ${e.message}")
                    }
                }
                null
            }

            // 다양한 메서드 명 시도 (버전에 따라 다를 수 있음)
            val methods = exerciseClient.javaClass.methods
            val candidate = methods.firstOrNull { it.name == "setUpdateCallback" && it.parameterTypes.size == 2 }
                ?: methods.firstOrNull { it.name.contains("setUpdate") && it.parameterTypes.size == 2 }
                ?: methods.firstOrNull { it.name.contains("add") && it.parameterTypes.size == 2 && it.parameterTypes[1].name.contains("Exercise") }
            if (candidate != null) {
                Log.d(TAG, "Registering callback via method=${candidate.name}")
                candidate.invoke(exerciseClient, executor, proxy)
                Log.d(TAG, "HS metrics callback registered")
                awaitClose {
                    try {
                        val clear = methods.firstOrNull { it.name.startsWith("clear") && it.parameterTypes.isEmpty() }
                            ?: methods.firstOrNull { it.name.startsWith("remove") && it.parameterTypes.isEmpty() }
                        clear?.let { Log.d(TAG, "Clearing callback via ${it.name}"); it.invoke(exerciseClient) }
                        Log.d(TAG, "HS metrics callback cleared")
                    } catch (_: Throwable) {}
                }
                return@callbackFlow
            } else {
                Log.w(TAG, "No suitable HS callback registration method found")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "HS metrics callback error: ${t.message}", t)
        }

        close()
    }
}
