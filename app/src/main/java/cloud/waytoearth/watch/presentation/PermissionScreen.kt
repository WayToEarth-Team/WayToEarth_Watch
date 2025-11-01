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
            add(android.Manifest.permission.ACCESS_COARSE_LOCATION)

            // BODY_SENSORS (모든 버전)
            add(android.Manifest.permission.BODY_SENSORS)
            if (Build.VERSION.SDK_INT >= 29) {
                add("android.permission.BODY_SENSORS_BACKGROUND")
            }

            // READ_HEART_RATE (API 30+에서 사용 가능, API 36+에서 필수)
            if (Build.VERSION.SDK_INT >= 30) {
                add("android.permission.health.READ_HEART_RATE")
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
