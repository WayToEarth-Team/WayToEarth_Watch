# WayToEarth Watch

Wear OS 기반 러닝 트래킹 워치 애플리케이션으로, 실시간 GPS, 심박수, 페이스 데이터를 수집하고 페어링된 스마트폰으로 전송합니다.

## 개요

이 프로젝트는 Wear OS 스마트워치에서 러닝 세션 데이터를 수집하여 페어링된 스마트폰 앱으로 전송하는 기능을 제공합니다. Google Wearable API를 사용하여 워치와 폰 간의 실시간 데이터 동기화를 지원합니다.

## 주요 기능

### 1. 러닝 데이터 수집
- **GPS 위치 추적**: 1초마다 위치 데이터 수집
- **심박수 모니터링**: 워치 하드웨어 센서를 직접 사용한 실시간 심박수 측정 (EMA 필터링 적용)
- **거리 계산**: Health Services 거리 또는 GPS 기반 거리 계산
- **페이스 계산**: 최근 100m 기준 즉시 페이스 계산
- **칼로리 계산**: 거리 기반 칼로리 소모량 추정 (1km당 60kcal)
- **일시정지/재개**: 러닝 중 일시정지 및 재개 기능
- **실시간 UI 업데이트**: StateFlow 기반 반응형 UI로 데이터 자동 반영

### 2. 폰 앱과의 실시간 통신
- **명령 수신**: 폰에서 시작/정지/일시정지/재개 명령 수신
- **실시간 업데이트**: 10초마다 현재 러닝 상태 전송
- **최종 세션 전송**: 러닝 종료 시 전체 세션 데이터 전송
- **상태 응답**: 각 명령에 대한 성공/실패 응답 전송
- **워치 주도 상태 알림**: 워치에서 직접 일시정지/재개 시 폰으로 상태 알림

### 3. 데이터 동기화
- **Message API**: 실시간 명령 및 업데이트 전송
- **Data Layer API**: 최종 세션 데이터 영구 저장 및 전송

## 아키텍처

```
┌─────────────────────────────────────────────────┐
│              Wear OS Watch App                   │
├─────────────────────────────────────────────────┤
│  Presentation Layer                              │
│  ├─ MainActivity (Compose)                       │
│  ├─ RunningScreen (UI - StateFlow 구독)          │
│  └─ PermissionScreen (권한 요청)                  │
├─────────────────────────────────────────────────┤
│  Manager Layer                                   │
│  └─ RunningManager (세션 관리, StateFlow 상태)    │
├─────────────────────────────────────────────────┤
│  Service Layer                                   │
│  ├─ PhoneCommunicationService (폰 통신)          │
│  ├─ WatchCommandReceiver (명령 수신)             │
│  ├─ LocationService (GPS)                        │
│  ├─ HeartRateService (심박수)                    │
│  └─ HealthMetricsService (통합 메트릭)           │
├─────────────────────────────────────────────────┤
│  Data Layer                                      │
│  ├─ RunningSession (세션 데이터 모델)             │
│  └─ RoutePoint (경로 포인트 데이터 모델)          │
└─────────────────────────────────────────────────┘
                    ↕️
        (Google Wearable API)
                    ↕️
┌─────────────────────────────────────────────────┐
│            Paired Phone App                      │
└─────────────────────────────────────────────────┘
```

## 데이터 전송 프로토콜

### 1. 워치 → 폰 통신

#### 명령 수신 (폰 → 워치)
| Path | 설명 | Payload |
|------|------|---------|
| `/waytoearth/command/start` | 러닝 시작 | `{sessionId, runningType}` |
| `/waytoearth/command/stop` | 러닝 종료 | `{sessionId}` |
| `/waytoearth/command/pause` | 러닝 일시정지 | `{sessionId}` |
| `/waytoearth/command/resume` | 러닝 재개 | `{sessionId}` |

#### 상태 응답 (워치 → 폰)
| Path | 설명 | Payload |
|------|------|---------|
| `/waytoearth/response/started` | 시작 완료 | `{success, sessionId, timestamp}` |
| `/waytoearth/response/stopped` | 종료 완료 | `{success, sessionId}` |
| `/waytoearth/response/paused` | 일시정지 완료 (명령 응답 또는 워치 주도) | `{success, sessionId, fromWatch?}` |
| `/waytoearth/response/resumed` | 재개 완료 (명령 응답 또는 워치 주도) | `{success, sessionId, fromWatch?}` |

**참고**: `fromWatch: true`가 포함된 경우 워치에서 사용자가 직접 일시정지/재개 버튼을 눌렀음을 의미합니다.

#### 실시간 업데이트 (워치 → 폰, 10초마다)
Path: `/waytoearth/realtime/update`

```json
{
  "sessionId": "watch-uuid",
  "distanceMeters": 1234,
  "durationSeconds": 180,
  "heartRate": 145,
  "paceSeconds": 320,
  "averagePaceSeconds": 310,
  "calories": 74,
  "currentPoint": {
    "latitude": 37.5665,
    "longitude": 126.9780,
    "sequence": 181,
    "t": 180,
    "acc": 5.2
  },
  "timestamp": 1234567890
}
```

#### 최종 세션 데이터 (워치 → 폰, 러닝 종료 시)
Path: `/waytoearth/running/complete`

```json
{
  "sessionId": "watch-uuid",
  "distanceMeters": 5000,
  "durationSeconds": 1800,
  "averagePaceSeconds": 360,
  "calories": 300,
  "averageHeartRate": 150,
  "maxHeartRate": 175,
  "routePoints": [
    {
      "latitude": 37.5665,
      "longitude": 126.9780,
      "sequence": 1,
      "timestampSeconds": 0,
      "heartRate": 145,
      "paceSeconds": 320,
      "altitude": 50.5,
      "accuracy": 5.2,
      "cumulativeDistanceMeters": 0
    }
  ],
  "endedAt": 1234567890
}
```

## 주요 컴포넌트

### PhoneCommunicationService
워치와 폰 간의 양방향 통신을 담당하는 핵심 서비스입니다.

**위치**: `app/src/main/java/cloud/waytoearth/watch/service/PhoneCommunicationService.kt`

**주요 기능**:
- `sendRealtimeUpdate()`: 10초마다 실시간 러닝 데이터 전송
- `sendRunningCompleteTransformed()`: 최종 세션 데이터 전송
- `sendResponseStarted/Stopped/Paused/Resumed()`: 명령 응답 전송

**사용 API**:
- `MessageClient`: 실시간 메시지 전송 (양방향)
- `DataClient`: 영구 데이터 저장 및 동기화

### WatchCommandReceiver
폰에서 전송된 명령을 수신하고 처리하는 서비스입니다.

**위치**: `app/src/main/java/cloud/waytoearth/watch/service/WatchCommandReceiver.kt`

**주요 기능**:
- `WearableListenerService` 상속
- 폰의 명령 메시지 수신 및 파싱
- `RunningManager`를 통한 세션 제어
- 명령 처리 결과 응답

### RunningManager
러닝 세션의 전체 생명주기를 관리하는 핵심 매니저입니다.

**위치**: `app/src/main/java/cloud/waytoearth/watch/manager/RunningManager.kt`

**주요 기능**:
- `startRunning()`: 러닝 세션 시작, GPS/심박수 추적 시작
- `stopRunning()`: 세션 종료, 통계 계산
- `pause()/resume()`: 일시정지/재개
- `startRealtimeSync()`: 10초마다 폰으로 데이터 전송
- `onLocationUpdate()`: GPS 위치 업데이트 처리 (1초마다)

**상태 관리 (StateFlow)**:
- `runningState`: UI가 구독하는 실시간 상태 스트림
- `RunningState`: 현재 세션, 일시정지 여부, 실행 여부를 담는 데이터 클래스
- `updateRunningState()`: 상태 변경 시 자동으로 UI 업데이트 트리거

**데이터 수집**:
- GPS 위치: 1초마다 수집
- 심박수: 워치 하드웨어 센서(TYPE_HEART_RATE)를 직접 사용, EMA 필터링으로 노이즈 제거
- 거리: Health Services 거리 우선, GPS 증분 계산
- 페이스: 최근 100m 기준 즉시 페이스 계산
- 일시정지 시 위치 업데이트는 수신하지만 거리 계산 중지

### LocationService
GPS 위치 추적을 담당하는 서비스입니다.

**위치**: `app/src/main/java/cloud/waytoearth/watch/service/LocationService.kt`

**주요 기능**:
- `FusedLocationProviderClient` 사용
- 1초 간격 위치 업데이트
- `Flow` 기반 위치 스트림 제공

### HeartRateService
심박수 모니터링을 담당하는 서비스입니다.

**위치**: `app/src/main/java/cloud/waytoearth/watch/service/HeartRateService.kt`

**주요 기능**:
- **워치 하드웨어 센서 직접 사용**: `SensorManager`와 `Sensor.TYPE_HEART_RATE`로 센서 직접 제어
- **EMA 필터링**: Exponential Moving Average (alpha=0.3)로 센서 노이즈 제거
- **1초 간격 업데이트**: `SensorEventListener`를 통해 실시간 심박수 수집
- **Health Services 운동 세션**: 배터리 최적화를 위해 ExerciseClient로 RUNNING 모드 활성화
- **Flow 기반 스트림**: 심박수 데이터를 Flow로 제공하여 RunningManager와 연동

### HealthMetricsService
Health Services의 통합 메트릭을 수집하는 서비스입니다.

**위치**: `app/src/main/java/cloud/waytoearth/watch/service/HealthMetricsService.kt`

**주요 기능**:
- 심박수, 거리, 페이스, 속도 통합 수집
- Health Services의 정확한 거리 및 페이스 데이터 활용

## 데이터 모델

### RunningSession
러닝 세션의 전체 데이터를 담는 모델입니다.

**위치**: `app/src/main/java/cloud/waytoearth/watch/data/RunningSession.kt`

```kotlin
data class RunningSession(
    val sessionId: String,                   // 세션 ID (UUID)
    val startTime: Long,                     // 시작 시각 (epoch millis)
    val routePoints: MutableList<RoutePoint>, // 경로 포인트 리스트
    var totalDistanceMeters: Int = 0,        // 총 거리 (m)
    var durationSeconds: Int = 0,            // 총 시간 (초)
    var averageHeartRate: Int? = null,       // 평균 심박수
    var maxHeartRate: Int? = null,           // 최대 심박수
    var calories: Int = 0                    // 칼로리
)
```

### RoutePoint
1초마다 수집되는 경로 포인트 데이터입니다.

**위치**: `app/src/main/java/cloud/waytoearth/watch/data/RoutePoint.kt`

```kotlin
data class RoutePoint(
    val latitude: Double,              // 위도
    val longitude: Double,             // 경도
    val sequence: Int,                 // 순서 (0부터 시작)
    val timestampSeconds: Int,         // 시작 시점부터 경과 시간 (초)
    val heartRate: Int?,               // 심박수 (BPM) - nullable
    val paceSeconds: Int?,             // 현재 페이스 (초/km) - nullable
    val altitude: Double?,             // 고도 (m) - nullable
    val accuracy: Double?,             // GPS 정확도 (m) - nullable
    val cumulativeDistanceMeters: Int  // 누적 거리 (m)
)
```

### RunningState
UI 업데이트를 위한 실시간 상태 모델입니다.

**위치**: `app/src/main/java/cloud/waytoearth/watch/manager/RunningManager.kt`

```kotlin
data class RunningState(
    val session: RunningSession? = null,  // 현재 러닝 세션 (null이면 미실행)
    val isPaused: Boolean = false,        // 일시정지 여부
    val isRunning: Boolean = false        // 러닝 실행 여부
)
```

이 모델은 `StateFlow<RunningState>`로 UI에 제공되어 Compose가 자동으로 리컴포지션을 수행합니다.

## 기술 스택

### Core
- **Kotlin**: 1.9+
- **Wear OS**: minSdk 30 (Android 11)
- **Compose for Wear OS**: UI 구현

### Google Play Services
- `play-services-wearable`: 워치-폰 통신
- `play-services-location`: GPS 위치 추적

### Health Services
- `health-services-client`: 운동 세션 관리 및 배터리 최적화 (심박수는 센서 직접 사용)

### Libraries
- `kotlinx-coroutines-android`: 비동기 처리
- `kotlinx-coroutines-flow`: StateFlow/Flow 기반 반응형 상태 관리
- `gson`: JSON 직렬화/역직렬화
- `accompanist-permissions`: 런타임 권한 관리

## 필수 권한

### AndroidManifest.xml
```xml
<uses-permission android:name="android.permission.BODY_SENSORS" />
<uses-permission android:name="android.permission.BODY_SENSORS_BACKGROUND" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />
<uses-permission android:name="android.permission.health.READ_HEART_RATE" />
```

## 설치 및 실행

### 요구사항
- Android Studio Hedgehog 이상
- Wear OS 에뮬레이터 또는 실제 Wear OS 디바이스 (Android 11 이상)
- 페어링된 안드로이드 폰 (데이터 전송 테스트용)

### 빌드
```bash
./gradlew build
```

### 설치
```bash
./gradlew installDebug
```

## 폰 앱 연동

### 폰 앱에서 구현해야 할 사항

1. **WearableListenerService 구현**
   - 워치에서 전송되는 메시지 수신

2. **메시지 수신 처리**
   ```kotlin
   override fun onMessageReceived(messageEvent: MessageEvent) {
       when (messageEvent.path) {
           "/waytoearth/response/started" -> handleStarted()
           "/waytoearth/response/stopped" -> handleStopped()
           "/waytoearth/response/paused" -> handlePaused()
           "/waytoearth/response/resumed" -> handleResumed()
           "/waytoearth/realtime/update" -> handleRealtimeUpdate()
           "/waytoearth/running/complete" -> handleRunningComplete()
       }
   }
   ```

3. **명령 전송**
   ```kotlin
   val messageClient = Wearable.getMessageClient(context)
   val nodes = Wearable.getNodeClient(context).connectedNodes.await()
   nodes.forEach { node ->
       val payload = JSONObject().apply {
           put("sessionId", sessionId)
       }.toString().toByteArray()

       messageClient.sendMessage(
           node.id,
           "/waytoearth/command/start",
           payload
       ).await()
   }
   ```

## 데이터 흐름

### 러닝 시작
```
폰 앱 → [START 명령] → 워치
워치 → [RunningManager.startRunning()] → GPS/심박수 추적 시작
워치 → [STARTED 응답] → 폰 앱
워치 → [10초마다 실시간 업데이트] → 폰 앱
```

### 러닝 중
```
워치 (GPS 1초마다) → RoutePoint 생성 → RunningSession 업데이트
워치 (심박수 실시간) → currentHeartRate 업데이트
워치 (10초마다) → PhoneCommunicationService.sendRealtimeUpdate() → 폰 앱
```

### 러닝 종료
```
폰 앱 → [STOP 명령] → 워치
워치 → [RunningManager.stopRunning()] → 통계 계산
워치 → [STOPPED 응답] → 폰 앱
워치 → [최종 세션 데이터] → 폰 앱
워치 → GPS/심박수 추적 중지
```

### 일시정지/재개 (폰 주도)
```
폰 앱 → [PAUSE 명령] → 워치
워치 → [RunningManager.pause()] → 거리 계산 중지, StateFlow 업데이트
워치 → [PAUSED 응답] → 폰 앱
워치 UI → 자동 업데이트 (일시정지 상태 표시)

폰 앱 → [RESUME 명령] → 워치
워치 → [RunningManager.resume()] → 거리 계산 재개, StateFlow 업데이트
워치 → [RESUMED 응답] → 폰 앱
워치 UI → 자동 업데이트 (러닝 중 상태 표시)
```

### 일시정지/재개 (워치 주도)
```
워치 UI → [일시정지 버튼 클릭]
워치 → [RunningManager.pause()] → StateFlow 업데이트
워치 → [PAUSED 응답 with fromWatch=true] → 폰 앱
워치 UI → 자동 업데이트 (일시정지 상태 표시)

워치 UI → [재개 버튼 클릭]
워치 → [RunningManager.resume()] → StateFlow 업데이트
워치 → [RESUMED 응답 with fromWatch=true] → 폰 앱
워치 UI → 자동 업데이트 (러닝 중 상태 표시)
```

## 테스팅

자세한 테스트 가이드는 [TESTING_GUIDE.md](./TESTING_GUIDE.md)를 참조하세요.

## 주의사항

1. **배터리 소모**: GPS와 심박수 센서를 동시에 사용하므로 배터리 소모가 큽니다.
2. **권한 요청**: 첫 실행 시 위치, 센서, 활동 인식 권한이 필요합니다.
3. **폰 연결**: 워치와 폰이 페어링되어 있어야 데이터 전송이 가능합니다.
4. **데이터 크기**: 장시간 러닝 시 RoutePoint 데이터가 매우 커질 수 있습니다 (1시간 = 3600개 포인트).

## 문제 해결

### 워치에서 폰으로 데이터가 전송되지 않는 경우
1. 워치와 폰이 페어링되어 있는지 확인
2. 폰에서 WearableListenerService가 등록되어 있는지 확인
3. AndroidManifest.xml에 서비스가 등록되어 있는지 확인

### GPS 위치가 수집되지 않는 경우
1. 위치 권한이 승인되었는지 확인
2. 워치의 위치 서비스가 활성화되어 있는지 확인
3. 실외에서 테스트 (GPS 신호 수신 필요)

### 심박수가 수집되지 않는 경우
1. BODY_SENSORS 및 BODY_SENSORS_BACKGROUND 권한이 승인되었는지 확인
2. 워치를 손목에 착용했는지 확인 (센서가 피부에 밀착되어야 함)
3. 워치에 TYPE_HEART_RATE 센서가 있는지 확인
4. HeartRateService가 제대로 시작되었는지 로그 확인

## 라이선스

이 프로젝트는 WayToEarth의 일부입니다.

## 문의

프로젝트 관련 문의사항은 이슈를 등록해 주세요.
