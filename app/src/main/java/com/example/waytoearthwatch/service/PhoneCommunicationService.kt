package com.example.waytoearthwatch.service

import android.content.Context
import com.google.android.gms.wearable.*
import com.google.gson.Gson
import com.example.waytoearthwatch.data.RunningSession
import kotlinx.coroutines.tasks.await

class PhoneCommunicationService(private val context: Context) {

    private val dataClient: DataClient = Wearable.getDataClient(context)
    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val gson = Gson()

    companion object {
        const val PATH_COMMAND_START = "/waytoearth/command/start"
        const val PATH_COMMAND_STOP = "/waytoearth/command/stop"

        const val PATH_RESPONSE_STARTED = "/waytoearth/response/started"
        const val PATH_RESPONSE_STOPPED = "/waytoearth/response/stopped"

        const val PATH_REALTIME_UPDATE = "/waytoearth/realtime/update"
        const val PATH_RUNNING_COMPLETE = "/waytoearth/running/complete"
    }

    private suspend fun sendMessageAll(path: String, payloadBytes: ByteArray): Boolean {
        return try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            if (nodes.isEmpty()) return false
            nodes.forEach { node ->
                messageClient.sendMessage(node.id, path, payloadBytes).await()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
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
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // 최종 세션 데이터 전송 (MessageClient)
    suspend fun sendRunningCompleteViaMessage(session: RunningSession): Boolean {
        val json = gson.toJson(session)
        return sendMessageAll(PATH_RUNNING_COMPLETE, json.toByteArray())
    }

    // 실시간 업데이트 전송 (10초마다)
    suspend fun sendRealtimeUpdate(data: Map<String, Any?>): Boolean {
        val json = gson.toJson(data)
        return sendMessageAll(PATH_REALTIME_UPDATE, json.toByteArray())
    }

    // 시작/종료 응답 전송
    suspend fun sendResponseStarted(data: Map<String, Any?>): Boolean {
        val json = gson.toJson(data)
        return sendMessageAll(PATH_RESPONSE_STARTED, json.toByteArray())
    }

    suspend fun sendResponseStopped(data: Map<String, Any?>): Boolean {
        val json = gson.toJson(data)
        return sendMessageAll(PATH_RESPONSE_STOPPED, json.toByteArray())
    }
}

