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
