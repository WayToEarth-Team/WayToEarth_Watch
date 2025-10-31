package cloud.waytoearth.watch.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.google.accompanist.permissions.*
import android.os.Build

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionScreen(onPermissionsGranted: () -> Unit) {
    val permissionList = remember {
        buildList {
            add(android.Manifest.permission.ACCESS_FINE_LOCATION)
            add(android.Manifest.permission.ACTIVITY_RECOGNITION)
            // COARSE는 선택적으로 유지(일부 기기 호환)
            add(android.Manifest.permission.ACCESS_COARSE_LOCATION)

            if (Build.VERSION.SDK_INT >= 36) {
                // Wear OS 6(API 36+) 신권한
                add("android.permission.health.READ_HEART_RATE")
            } else {
                // Wear OS 5.1(API 35) 이하
                add(android.Manifest.permission.BODY_SENSORS)
                add("android.permission.BODY_SENSORS_BACKGROUND")
            }
        }
    }

    val permissionsState = rememberMultiplePermissionsState(permissions = permissionList)

    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            onPermissionsGranted()
        }
    }

    Scaffold(
        timeText = { TimeText() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "위치 및 센서 권한이 필요합니다",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.body2
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { permissionsState.launchMultiplePermissionRequest() }
            ) {
                Text("권한 허용")
            }
        }
    }
}
