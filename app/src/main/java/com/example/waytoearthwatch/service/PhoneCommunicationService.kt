package com.example.waytoearthwatch.service

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.*
import com.google.gson.Gson
import com.example.waytoearthwatch.data.RunningSession
import kotlinx.coroutines.tasks.await

class PhoneCommunicationService(private val context: Context) {

    private val dataClient: DataClient = Wearable.getDataClient(context)
    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val gson = Gson()
    private val TAG = "PhoneCommService"

    companion object {
        const val PATH_COMMAND_START = "/waytoearth/command/start"
        const val PATH_COMMAND_STOP = "/waytoearth/command/stop"
        const val PATH_COMMAND_PAUSE = "/waytoearth/command/pause"
        const val PATH_COMMAND_RESUME = "/waytoearth/command/resume"

        const val PATH_RESPONSE_STARTED = "/waytoearth/response/started"
        const val PATH_RESPONSE_STOPPED = "/waytoearth/response/stopped"
        const val PATH_RESPONSE_PAUSED = "/waytoearth/response/paused"
        const val PATH_RESPONSE_RESUMED = "/waytoearth/response/resumed"

        const val PATH_REALTIME_UPDATE = "/waytoearth/realtime/update"
        const val PATH_RUNNING_COMPLETE = "/waytoearth/running/complete"
    }

    private suspend fun sendMessageAll(path: String, payloadBytes: ByteArray): Boolean {
        return try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            Log.d(TAG, "sendMessageAll path=$path nodes=${nodes.size}")
            if (nodes.isEmpty()) return false
            nodes.forEach { node ->
                messageClient.sendMessage(node.id, path, payloadBytes).await()
            }
            Log.d(TAG, "sendMessageAll OK path=$path")
            true
        } catch (e: Exception) {
            Log.e(TAG, "sendMessageAll failed path=$path: ${e.message}", e)
            false
        }
    }

    // 최종 세션 데이터 전송 (Data Layer)
    suspend fun sendRunningDataToPhone(session: RunningSession): Boolean {
        return try {
            val json = gson.toJson(session)
            val putDataReq = PutDataMapRequest.create(PATH_RUNNING_COMPLETE).apply {
                dataMap.putString("session_data", json)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            dataClient.putDataItem(putDataReq).await()
            Log.d(TAG, "sendRunningDataToPhone OK (DataLayer)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "sendRunningDataToPhone failed: ${e.message}", e)
            false
        }
    }

    // 최종 세션 데이터 전송 (MessageClient)
    suspend fun sendRunningCompleteViaMessage(session: RunningSession): Boolean {
        val json = gson.toJson(session)
        Log.d(TAG, "sendRunningCompleteViaMessage size=${json.length}")
        return sendMessageAll(PATH_RUNNING_COMPLETE, json.toByteArray())
    }

    // 최종 세션 데이터 전송 (서버 스펙 변환 버전, MessageClient)
    suspend fun sendRunningCompleteTransformed(
        sessionId: String,
        distanceMeters: Int,
        durationSeconds: Int,
        averagePaceSeconds: Int?,
        calories: Int,
        routePoints: List<Map<String, Any?>>
    ): Boolean {
        val payload = mapOf(
            "sessionId" to sessionId,
            "distanceMeters" to distanceMeters,
            "durationSeconds" to durationSeconds,
            "averagePaceSeconds" to averagePaceSeconds,
            "calories" to calories,
            "routePoints" to routePoints,
            "endedAt" to System.currentTimeMillis()
        )
        val json = gson.toJson(payload)
        Log.d(TAG, "sendRunningCompleteTransformed size=${json.length}")
        return sendMessageAll(PATH_RUNNING_COMPLETE, json.toByteArray())
    }

    // 실시간 업데이트 전송 (10초마다)
    suspend fun sendRealtimeUpdate(data: Map<String, Any?>): Boolean {
        val json = gson.toJson(data)
        Log.v(TAG, "sendRealtimeUpdate payload=$json")
        return sendMessageAll(PATH_REALTIME_UPDATE, json.toByteArray())
    }

    // 시작/종료 응답 전송
    suspend fun sendResponseStarted(data: Map<String, Any?>): Boolean {
        val json = gson.toJson(data)
        Log.d(TAG, "sendResponseStarted payload=$json")
        return sendMessageAll(PATH_RESPONSE_STARTED, json.toByteArray())
    }

    suspend fun sendResponseStopped(data: Map<String, Any?>): Boolean {
        val json = gson.toJson(data)
        Log.d(TAG, "sendResponseStopped payload=$json")
        return sendMessageAll(PATH_RESPONSE_STOPPED, json.toByteArray())
    }

    suspend fun sendResponsePaused(data: Map<String, Any?>): Boolean {
        val json = gson.toJson(data)
        Log.d(TAG, "sendResponsePaused payload=$json")
        return sendMessageAll(PATH_RESPONSE_PAUSED, json.toByteArray())
    }

    suspend fun sendResponseResumed(data: Map<String, Any?>): Boolean {
        val json = gson.toJson(data)
        Log.d(TAG, "sendResponseResumed payload=$json")
        return sendMessageAll(PATH_RESPONSE_RESUMED, json.toByteArray())
    }
}
