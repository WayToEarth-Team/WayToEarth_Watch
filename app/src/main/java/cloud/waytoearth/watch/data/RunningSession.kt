package cloud.waytoearth.watch.data

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

data class RunningCompleteRequest(
    val sessionId: String,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val averagePaceSeconds: Int?,
    val calories: Int,
    val averageHeartRate: Int?,
    val maxHeartRate: Int?,
    val routePoints: List<RoutePoint>
)

// RunningSession을 백엔드 요청 형식으로 변환
fun RunningSession.toBackendRequest(): RunningCompleteRequest {
    val averagePaceSeconds = if (totalDistanceMeters > 0) {
        (durationSeconds.toDouble() / (totalDistanceMeters / 1000.0)).toInt()
    } else null

    return RunningCompleteRequest(
        sessionId = sessionId,
        distanceMeters = totalDistanceMeters,
        durationSeconds = durationSeconds,
        averagePaceSeconds = averagePaceSeconds,
        calories = calories,
        averageHeartRate = averageHeartRate,
        maxHeartRate = maxHeartRate,
        routePoints = routePoints
    )
}
