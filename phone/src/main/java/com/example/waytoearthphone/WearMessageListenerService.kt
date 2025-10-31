package com.example.waytoearthphone

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.DataEvent

class WearMessageListenerService : WearableListenerService() {

    private val TAG = "PhoneWearListener"

    companion object {
        private const val PATH_RESPONSE_STARTED = "/waytoearth/response/started"
        private const val PATH_RESPONSE_STOPPED = "/waytoearth/response/stopped"
        private const val PATH_REALTIME_UPDATE = "/waytoearth/realtime/update"
        private const val PATH_RUNNING_COMPLETE = "/waytoearth/running/complete"
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            PATH_RESPONSE_STARTED -> Log.d(TAG, "Started response: ${String(messageEvent.data)}")
            PATH_RESPONSE_STOPPED -> Log.d(TAG, "Stopped response: ${String(messageEvent.data)}")
            PATH_REALTIME_UPDATE -> Log.d(TAG, "Realtime update: ${String(messageEvent.data)}")
            PATH_RUNNING_COMPLETE -> Log.d(TAG, "Running complete (message): size=${messageEvent.data?.size}")
            else -> Log.w(TAG, "Unknown message path: ${messageEvent.path}")
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.use { buffer ->
            for (event in buffer) {
                if (event.type == DataEvent.TYPE_CHANGED) {
                    val item = event.dataItem
                    if (item.uri.path == PATH_RUNNING_COMPLETE) {
                        val dataMap = DataMapItem.fromDataItem(item).dataMap
                        val json = dataMap.getString("session_data")
                        Log.d(TAG, "Running complete (data): length=${json?.length}")
                    }
                }
            }
        }
    }
}

