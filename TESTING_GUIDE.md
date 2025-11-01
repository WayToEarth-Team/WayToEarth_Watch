# 🧪 갤럭시 워치 앱 테스트 가이드

## 📋 목차
1. [준비 사항](#준비-사항)
2. [워치 앱 설치](#워치-앱-설치)
3. [폰 앱 설치](#폰-앱-설치)
4. [연결 확인](#연결-확인)
5. [테스트 시나리오](#테스트-시나리오)
6. [문제 해결](#문제-해결)

---

## 준비 사항

### 필요한 장비
- ✅ 갤럭시 워치 (Wear OS 3.0 이상)
- ✅ 안드로이드 폰 (Android 8.0 이상)
- ✅ USB 케이블 (워치용, 폰용)
- ✅ Windows PC (Android Studio 설치)

### 필요한 설정

#### 1. 워치 개발자 옵션 활성화
```
워치에서:
1. 설정 → 시스템 → 정보
2. "빌드 번호"를 7번 연속 탭
3. "개발자 모드가 활성화되었습니다" 메시지 확인
4. 설정 → 개발자 옵션
   - ADB 디버깅: ON
   - Wi-Fi를 통한 디버깅: ON
   - 절전 모드 해제 유지: ON
```

#### 2. 폰 개발자 옵션 활성화
```
폰에서:
1. 설정 → 휴대전화 정보 → 소프트웨어 정보
2. "빌드 번호"를 7번 연속 탭
3. 개발자 옵션 활성화 확인
4. 설정 → 개발자 옵션
   - USB 디버깅: ON
```

---

## 워치 앱 설치

### 방법 1: USB 케이블 연결 (권장)

#### Step 1: 워치를 PC에 연결
```bash
# 워치를 USB 케이블로 PC에 연결
# 워치에서 "USB 디버깅 허용" 팝업이 뜨면 "허용" 클릭
```

#### Step 2: 연결 확인
```bash
# CMD 또는 PowerShell에서
cd C:\Users\leepg\AndroidStudioProjects\WayToEarthWatch

# ADB 기기 확인
adb devices

# 출력 예시:
# List of devices attached
# 1234567890ABCDEF    device    ← 이게 보이면 성공!
```

#### Step 3: Android Studio로 빌드 및 설치
```
1. Android Studio에서 WayToEarthWatch 프로젝트 열기
2. 상단 Run 버튼 옆 디바이스 선택에서 워치 선택
3. Run (Shift+F10) 또는 초록색 ▶️ 버튼 클릭
4. 워치에 앱이 자동으로 설치됨
```

#### Step 4: Gradle 명령어로 설치 (대안)
```bash
cd C:\Users\leepg\AndroidStudioProjects\WayToEarthWatch

# Debug APK 빌드
gradlew assembleDebug

# 워치에 설치
adb install -r app\build\outputs\apk\debug\app-debug.apk

# 설치 확인
adb shell pm list packages | grep waytoearth
```

### 방법 2: Wi-Fi 디버깅 (무선)

#### Step 1: 워치 IP 주소 확인
```
워치에서:
1. 설정 → 연결 → Wi-Fi
2. 연결된 네트워크 이름 탭
3. IP 주소 확인 (예: 192.168.0.50)
```

#### Step 2: PC에서 워치에 연결
```bash
# 워치 IP로 연결 (포트 5555)
adb connect 192.168.0.50:5555

# 연결 확인
adb devices
```

#### Step 3: 앱 설치 (USB와 동일)
```bash
gradlew assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

---

## 폰 앱 설치

### React Native 앱 설치

#### Step 1: 폰을 PC에 연결
```bash
# USB 케이블로 폰 연결
# 폰에서 "USB 디버깅 허용" 팝업이 뜨면 "허용"
```

#### Step 2: 연결 확인
```bash
cd C:\Users\leepg\FRONT\waytoearth

# 폰 연결 확인
adb devices
# 폰과 워치 둘 다 보여야 함
```

#### Step 3: React Native 앱 실행
```bash
# 의존성 설치 (처음만)
npm install

# Android 앱 실행
npm run android
# 또는
npx expo run:android
```

---

## 연결 확인

### 1. 워치-폰 페어링 확인

#### Galaxy Wearable 앱 사용
```
폰에서:
1. Galaxy Wearable 앱 설치 (Play Store)
2. 앱 실행 → 워치 연결
3. "연결됨" 상태 확인
```

#### ADB로 확인
```bash
# 폰에서 Wear 앱 상태 확인
adb -s [폰디바이스ID] shell dumpsys wear

# 워치에서 연결 상태 확인
adb -s [워치디바이스ID] shell dumpsys connectivity
```

### 2. Wearable Data Layer 확인

#### 폰 앱에서 확인
```bash
# Logcat 필터링
adb -s [폰디바이스ID] logcat | grep -E "WayToEarthWear|WearListener"

# 예상 로그:
# WayToEarthWear: Broadcast receiver registered
# WearListenerService: Service started
```

#### 워치 앱에서 확인
```bash
# Logcat 필터링
adb -s [워치디바이스ID] logcat | grep -E "PhoneCommService|WatchCommandReceiver"

# 예상 로그:
# PhoneCommService: sendMessageAll nodes=1
# WatchCommandReceiver: onMessageReceived path=/waytoearth/command/start
```

---

## 테스트 시나리오

### 시나리오 1: 기본 러닝 시작/종료

#### 1단계: 앱 실행 확인
```
워치:
- WayToEarth Watch 앱 실행
- "폰 앱에서 러닝을 시작하세요" 메시지 확인 ✅

폰:
- WayToEarth 앱 실행
- 러닝 화면으로 이동
```

#### 2단계: 러닝 시작
```javascript
// 폰 앱에서 시작 버튼 클릭
// 예상 동작:
1. 폰: startRunOrchestrated() 호출
2. 폰: 백엔드 /v1/running/start API 호출 ✅
3. 폰 → 워치: START 명령 전송 ✅
4. 워치: GPS + 심박수 센서 활성화 ✅
5. 워치: 실시간 데이터 수집 시작 ✅

// Logcat 확인:
// 폰: [API OK] running/start -> sessionId=phone-1234567890
// 폰: [WEAR] start command sent ok=true
// 워치: [WatchCommandReceiver] START command: sessionId=phone-1234567890
// 워치: [RunningManager] startRunning(external) sessionId=phone-1234567890
```

#### 3단계: 실시간 업데이트 확인 (10초마다)
```javascript
// 예상 로그:
// 워치: [PhoneCommService] sendRealtimeUpdate payload=...
// 폰: [WearListenerService] onMessageReceived: path=/waytoearth/realtime/update
// 폰: [WayToEarthWear] Broadcast received: wearRealtimeUpdate
// 폰: [API] running/update payload: {...}

// 폰 UI 확인:
- 거리 실시간 업데이트 ✅
- 시간 실시간 업데이트 ✅
- 심박수 실시간 업데이트 ✅
- 페이스 실시간 업데이트 ✅
```

#### 4단계: 러닝 종료
```javascript
// 폰 앱에서 종료 버튼 클릭
// 예상 동작:
1. 폰: stopRun() 호출
2. 폰 → 워치: STOP 명령 전송 ✅
3. 워치: RunningManager.stopRunning() ✅
4. 워치: 최종 데이터 수집 및 JSON 생성 ✅
5. 워치 → 폰: COMPLETE 데이터 전송 (모든 필드 포함) ✅
6. 폰: apiComplete() → 백엔드 전송 ✅

// Logcat 확인:
// 폰: [WEAR] stop command sent ok=true
// 워치: [WatchCommandReceiver] STOP command: sessionId=...
// 워치: [PhoneCommService] sendRunningCompleteTransformed size=150000
// 폰: [WearListenerService] Running complete (message): size=150000
// 폰: [API] running/complete payload: {sessionId, distanceMeters, ..., count: 1800}
```

### 시나리오 2: 일시정지/재개

#### 일시정지
```
폰에서 "일시정지" 버튼 클릭

예상 동작:
1. 폰: pauseRun() 호출
2. 폰 → 워치: PAUSE 명령 전송
3. 워치: RunningManager.pause()
4. 워치: 데이터 수집 멈춤 (paused = true)
5. 워치 → 폰: PAUSED 응답
6. 폰: apiPause() → 백엔드 전송

확인:
- 워치 UI: "일시정지 중" 표시 ✅
- 폰 UI: 일시정지 상태 표시 ✅
```

#### 재개
```
폰에서 "재개" 버튼 클릭

예상 동작:
1. 폰: resumeRun() 호출
2. 폰 → 워치: RESUME 명령 전송
3. 워치: RunningManager.resume()
4. 워치: 데이터 수집 재개 (paused = false)
5. 워치 → 폰: RESUMED 응답
6. 폰: apiResume() → 백엔드 전송

확인:
- 워치 UI: 일시정지 표시 사라짐 ✅
- 폰 UI: 재개 상태 표시 ✅
```

### 시나리오 3: 백엔드 데이터 검증

#### API 응답 확인
```bash
# 러닝 완료 후 백엔드 확인
curl -X GET "https://your-api.com/api/v1/running/{runningRecordId}/detail" \
  -H "Authorization: Bearer {token}"

# 확인 사항:
{
  "routePoints": [
    {
      "latitude": 37.5665,      ✅
      "longitude": 126.9780,     ✅
      "sequence": 1,             ✅
      "timestampSeconds": 0,     ✅
      "heartRate": 120,          ✅ 중요!
      "paceSeconds": 330,        ✅ 중요!
      "altitude": 45.2,          ✅ 중요!
      "accuracy": 5.0,           ✅ 중요!
      "cumulativeDistanceMeters": 0  ✅ 중요!
    }
  ],
  "averageHeartRate": 145,     ✅
  "maxHeartRate": 178          ✅
}
```

---

## 문제 해결

### 문제 1: 워치에서 앱이 실행되지 않음

**증상:**
- 앱 아이콘은 보이지만 실행 안 됨
- "앱이 응답하지 않습니다" 메시지

**해결:**
```bash
# 1. 앱 완전 삭제
adb -s [워치디바이스ID] uninstall com.example.waytoearthwatch

# 2. 캐시 정리
adb -s [워치디바이스ID] shell pm clear com.google.android.gms

# 3. 워치 재시작
adb -s [워치디바이스ID] reboot

# 4. 재설치
gradlew assembleDebug
adb -s [워치디바이스ID] install -r app/build/outputs/apk/debug/app-debug.apk
```

### 문제 2: 워치-폰 연결 안 됨

**증상:**
- 폰에서 시작해도 워치가 반응 없음
- Logcat에 "nodes=0" 표시

**해결:**
```
1. Galaxy Wearable 앱에서 연결 재설정
2. 블루투스 껐다 켜기
3. 둘 다 같은 Wi-Fi에 연결
4. Wear 앱 권한 확인:
   - 폰 설정 → 앱 → WayToEarth → 권한
   - 위치, 블루투스 권한 허용
```

### 문제 3: 실시간 업데이트 안 됨

**증상:**
- 워치에서 데이터 수집은 되는데 폰 UI 업데이트 안 됨
- Logcat에 "wearRealtimeUpdate" 없음

**해결:**
```bash
# 1. WearMessageListenerService 확인
adb -s [폰디바이스ID] shell dumpsys activity services | grep WearMessageListenerService

# 2. 서비스 재시작
adb -s [폰디바이스ID] shell am stopservice cloud.waytoearth/.wear.WearMessageListenerService
adb -s [폰디바이스ID] shell am startservice cloud.waytoearth/.wear.WearMessageListenerService

# 3. Logcat으로 메시지 수신 확인
adb -s [폰디바이스ID] logcat | grep "WearListenerService"
```

### 문제 4: 백엔드에 데이터 일부만 전송됨

**증상:**
- routePoints에 heartRate, altitude 등이 null

**원인:**
- WatchCommandReceiver.kt에서 필드 누락

**확인:**
```bash
# 워치 로그 확인
adb -s [워치디바이스ID] logcat | grep "sendRunningCompleteTransformed"

# 예상 로그:
# sendRunningCompleteTransformed size=150000
#
# size가 너무 작으면 (< 50000) 필드가 빠진 것
```

**해결:**
- WatchCommandReceiver.kt의 routePoints 매핑 확인
- 모든 필드 (heartRate, paceSeconds, altitude 등) 포함되었는지 확인

### 문제 5: GPS 위치 잡히지 않음

**증상:**
- 워치에서 거리가 0으로 표시
- Accuracy가 계속 높음 (> 50m)

**해결:**
```
1. 워치를 야외로 나가서 테스트 (실내는 GPS 약함)
2. 워치 설정 → 위치 → 정확도: 높음
3. 위치 권한 확인:
   - 설정 → 앱 → WayToEarth Watch → 권한
   - 위치: "항상 허용"
4. A-GPS 데이터 리셋:
   adb -s [워치디바이스ID] shell settings put secure assisted_gps_enabled 1
```

---

## 체크리스트

### 설치 전
- [ ] 워치 개발자 옵션 활성화
- [ ] 폰 개발자 옵션 활성화
- [ ] USB 디버깅 활성화 (둘 다)
- [ ] Galaxy Wearable 앱 설치 및 페어링

### 설치 후
- [ ] 워치 앱 실행 확인
- [ ] 폰 앱 실행 확인
- [ ] Logcat 연결 (워치, 폰 각각)
- [ ] 권한 모두 허용 (위치, 센서, 블루투스)

### 테스트 중
- [ ] 러닝 시작: 워치가 데이터 수집 시작
- [ ] 10초마다 실시간 업데이트 확인
- [ ] 폰 UI 실시간 변경 확인
- [ ] 러닝 종료: 백엔드로 전송 확인
- [ ] 백엔드 데이터: 모든 필드 확인

---


### 테스트 필요 사항
- [ ] 심박수 체크
- [ ] gps 신호 
- [ ] 거리 계산 로직
- [ ] 페이스 체크
- ---

## 빠른 테스트 명령어 모음

```bash
# === 기기 연결 ===
adb devices

# === 앱 설치 ===
# 워치
cd C:\Users\leepg\AndroidStudioProjects\WayToEarthWatch
gradlew assembleDebug
adb -s [워치ID] install -r app\build\outputs\apk\debug\app-debug.apk

# 폰
cd C:\Users\leepg\FRONT\waytoearth
npm run android

# === 로그 모니터링 ===
# 워치 로그
adb -s [워치ID] logcat | grep -E "PhoneCommService|WatchCommandReceiver|RunningManager"

# 폰 로그
adb -s [폰ID] logcat | grep -E "WayToEarthWear|WearListenerService|WATCH|API"

# === 앱 재시작 ===
# 워치 앱 강제 종료
adb -s [워치ID] shell am force-stop com.example.waytoearthwatch

# 폰 앱 강제 종료
adb -s [폰ID] shell am force-stop cloud.waytoearth

# === 디버깅 ===
# 워치 앱 실행 로그
adb -s [워치ID] shell am start -n com.example.waytoearthwatch/.presentation.MainActivity

# 폰 서비스 상태 확인
adb -s [폰ID] shell dumpsys activity services cloud.waytoearth
```

---

**테스트 준비 완료! 실제 기기로 테스트를 시작하세요!** 🚀
