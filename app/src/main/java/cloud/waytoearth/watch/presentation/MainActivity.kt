package cloud.waytoearth.watch.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import cloud.waytoearth.watch.manager.RunningManager
import cloud.waytoearth.watch.service.PhoneCommunicationService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var runningManager: RunningManager
    private lateinit var phoneCommunication: PhoneCommunicationService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runningManager = RunningManager(this)
        phoneCommunication = PhoneCommunicationService(this)

        setContent {
            var hasPermissions by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            if (!hasPermissions) {
                PermissionScreen(
                    onPermissionsGranted = { hasPermissions = true }
                )
            } else {
                RunningScreen(
                    runningManager = runningManager,
                    onStop = {
                        scope.launch {
                            // 러닝 종료 후 폰으로 전송
                            val session = runningManager.stopRunning()
                            session?.let {
                                val success = phoneCommunication.sendRunningCompleteViaMessage(it)
                                if (success) {
                                    // 전송 성공 처리
                                } else {
                                    // 전송 실패 처리
                                }
                            }
                        }
                    },
                    onPauseToggle = { isPaused ->
                        scope.launch {
                            // 워치에서 일시정지/재개 버튼을 누르면 폰에 알림
                            val sessionId = runningManager.getCurrentSession()?.sessionId ?: return@launch
                            if (isPaused) {
                                phoneCommunication.sendResponsePaused(
                                    mapOf("sessionId" to sessionId, "fromWatch" to true)
                                )
                            } else {
                                phoneCommunication.sendResponseResumed(
                                    mapOf("sessionId" to sessionId, "fromWatch" to true)
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}
