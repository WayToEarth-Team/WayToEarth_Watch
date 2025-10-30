package com.example.waytoearthwatch.service

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.example.waytoearthwatch.manager.RunningManager
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
        runningManager = RunningManager(this)
        comm = PhoneCommunicationService(this)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            PhoneCommunicationService.PATH_COMMAND_START -> handleStart(messageEvent)
            PhoneCommunicationService.PATH_COMMAND_STOP -> handleStop(messageEvent)
        }
    }

    private fun handleStart(messageEvent: MessageEvent) {
        scope.launch {
            try {
                val json = JSONObject(String(messageEvent.data))
                val sessionId = json.optString("sessionId")
                val runningType = json.optString("runningType", null)

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

                // 최종 세션 전송
                if (session != null) {
                    comm.sendRunningCompleteViaMessage(session)
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
}

