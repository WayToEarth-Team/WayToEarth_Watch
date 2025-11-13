package cloud.waytoearth.watch.utils

import android.location.Location
import kotlin.math.*

/**
 * GPS 위치 필터링 유틸리티
 * 프론트엔드와 동일한 필터링 로직 적용
 */
object LocationFilter {

    // 프론트엔드와 동기화된 상수들
    private const val MAX_ACCURACY_METERS = 65f           // 정확도 임계값
    private const val MAX_SPEED_MPS = 6.5                 // 최대 속도 (m/s)
    private const val MIN_ACCUMULATE_DISTANCE = 0.5       // 최소 누적 거리 (m)
    private const val STATIONARY_SPEED_THRESHOLD = 0.5    // 정지 속도 기준 (m/s)
    private const val STATIONARY_DISTANCE_THRESHOLD = 3.0 // 정지 변위 기준 (m)
    private const val RESUME_SPEED_THRESHOLD = 0.7        // 재개 속도 기준 (m/s)
    private const val RESUME_DISTANCE_THRESHOLD = 6.0     // 재개 변위 기준 (m)

    /**
     * 좌표 유효성 검증
     * 프론트엔드: isValidLatLng
     */
    fun isValidLocation(location: Location): Boolean {
        val lat = location.latitude
        val lon = location.longitude

        return lat.isFinite() &&
               lon.isFinite() &&
               abs(lat) <= 90 &&
               abs(lon) <= 180 &&
               !(lat == 0.0 && lon == 0.0)
    }

    /**
     * 정확도 기반 필터링
     * 프론트엔드: acc > 65m 제외
     */
    fun isAccuracyAcceptable(location: Location): Boolean {
        return location.accuracy <= MAX_ACCURACY_METERS
    }

    /**
     * 최소 이동 거리 체크
     * 프론트엔드: max(1.5, min(acc × 0.3, 3))
     */
    fun calculateMinMoveThreshold(accuracy: Float): Double {
        return max(1.5, min(accuracy * 0.3, 3.0))
    }

    /**
     * 이동 거리가 최소 임계값 미만인지 체크
     */
    fun isBelowMinMove(
        lastLoc: Location,
        currentLoc: Location
    ): Boolean {
        val distance = DistanceCalculator.calculateDistance(
            lastLoc.latitude, lastLoc.longitude,
            currentLoc.latitude, currentLoc.longitude
        )
        val minMove = calculateMinMoveThreshold(currentLoc.accuracy)
        return distance < minMove
    }

    /**
     * 속도 스파이크 필터
     * 프론트엔드: 6.5 m/s 초과는 노이즈
     */
    fun isSpeedSpike(
        lastLoc: Location,
        currentLoc: Location,
        lastTimestamp: Long,
        currentTimestamp: Long
    ): Boolean {
        val timeDeltaSec = (currentTimestamp - lastTimestamp) / 1000.0
        if (timeDeltaSec <= 0) return true

        val distance = DistanceCalculator.calculateDistance(
            lastLoc.latitude, lastLoc.longitude,
            currentLoc.latitude, currentLoc.longitude
        )
        val speed = distance / timeDeltaSec // m/s

        return speed > MAX_SPEED_MPS
    }

    /**
     * 정지 상태에서 미세 흔들림 체크
     * 프론트엔드: spd < 0.6 m/s && seg < max(2, min(acc × 0.5, 4))
     */
    fun isStationaryNoise(
        lastLoc: Location,
        currentLoc: Location,
        currentSpeed: Float?
    ): Boolean {
        // 속도가 0.6 m/s 미만이면 정지 상태로 간주
        if (currentSpeed != null && currentSpeed >= 0 && currentSpeed < 0.6) {
            val distance = DistanceCalculator.calculateDistance(
                lastLoc.latitude, lastLoc.longitude,
                currentLoc.latitude, currentLoc.longitude
            )
            val threshold = max(2.0, min(currentLoc.accuracy * 0.5, 4.0))
            return distance < threshold
        }
        return false
    }

    /**
     * 거리 보정 (노이즈 차감)
     * 프론트엔드: min(0.8, avgAcc × 0.05, seg × 0.1)
     */
    fun calculateEffectiveDistance(
        rawDistance: Double,
        prevAccuracy: Float,
        currentAccuracy: Float
    ): Double {
        val avgAccuracy = (prevAccuracy + currentAccuracy) / 2

        // 노이즈 차감: min(0.8m, avgAccuracy × 0.05, rawDistance × 0.1)
        val noiseAllowance = min(0.8, min(avgAccuracy * 0.05, rawDistance * 0.1))
        val effectiveDistance = max(0.0, rawDistance - noiseAllowance)

        // 0.5m 미만은 미세 떨림으로 간주, 거리에 포함하지 않음
        return if (effectiveDistance >= MIN_ACCUMULATE_DISTANCE) {
            effectiveDistance
        } else {
            0.0
        }
    }

    /**
     * 위치 샘플 무시 여부 판단 (통합 필터)
     * 프론트엔드: shouldIgnoreSample
     */
    fun shouldIgnoreSample(
        prevLoc: Location?,
        currentLoc: Location,
        prevTimestamp: Long,
        currentTimestamp: Long
    ): Boolean {
        // 1. 좌표 유효성 검증
        if (!isValidLocation(currentLoc)) return true

        // 2. 정확도 필터: 65m 이상은 제외
        if (!isAccuracyAcceptable(currentLoc)) return true

        // 첫 포인트는 수락
        if (prevLoc == null) return false

        // 3. 최소 이동 임계값 체크
        if (isBelowMinMove(prevLoc, currentLoc)) return true

        // 4. 정지 상태 미세 흔들림 제거
        if (isStationaryNoise(prevLoc, currentLoc, currentLoc.speed)) return true

        // 5. 속도 스파이크 필터
        if (isSpeedSpike(prevLoc, currentLoc, prevTimestamp, currentTimestamp)) return true

        return false
    }

    /**
     * 10초 윈도우 기반 정지 감지
     */
    data class StationaryState(
        val isStationary: Boolean,
        val windowSpeed: Double,
        val windowDistance: Double
    )

    fun detectStationary(
        locationWindow: List<Pair<Location, Long>>
    ): StationaryState {
        if (locationWindow.size < 2) {
            return StationaryState(false, 0.0, 0.0)
        }

        val first = locationWindow.first()
        val last = locationWindow.last()
        val windowDurationSec = (last.second - first.second) / 1000.0

        val windowDistance = DistanceCalculator.calculateDistance(
            first.first.latitude, first.first.longitude,
            last.first.latitude, last.first.longitude
        )

        val windowSpeed = if (windowDurationSec > 0) {
            windowDistance / windowDurationSec
        } else {
            0.0
        }

        // 정지 판정: 평균 속도 < 0.5 m/s AND 총 변위 < 3m
        val isStationary = windowDurationSec >= 4 &&
                          windowSpeed < STATIONARY_SPEED_THRESHOLD &&
                          windowDistance < STATIONARY_DISTANCE_THRESHOLD

        return StationaryState(isStationary, windowSpeed, windowDistance)
    }

    /**
     * 정지 상태에서 재개 판단
     */
    fun shouldResumeFromStationary(
        windowSpeed: Double,
        windowDistance: Double,
        currentSpeed: Float?
    ): Boolean {
        // 속도 > 0.7 m/s OR 변위 > 6m
        val speedResume = windowSpeed > RESUME_SPEED_THRESHOLD
        val distanceResume = windowDistance > RESUME_DISTANCE_THRESHOLD
        val currentSpeedResume = currentSpeed != null && currentSpeed > RESUME_SPEED_THRESHOLD

        return speedResume || distanceResume || currentSpeedResume
    }
}
