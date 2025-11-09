package cloud.waytoearth.watch.utils

/**
 * 칼로리 계산 유틸리티 (METs 기반)
 *
 * ⚠️ 백엔드/프론트엔드와 동일한 계산 공식 사용
 *
 * 📐 칼로리 계산 공식 (METs 방식)
 *
 * 칼로리(kcal) = 체중(kg) × METs × 시간(h) × 1.05
 *
 * METs 값 (속도 기준):
 * - 걷기 (< 6 km/h):      METs 3.5
 * - 조깅 (6 ~ 8 km/h):    METs 7.0
 * - 러닝 (8 ~ 10 km/h):   METs 9.0
 * - 빠른 러닝 (≥ 10 km/h): METs 11.0
 *
 * 🔍 계산 예시
 *
 * 예1) 70kg, 5km, 30분 (10 km/h) 러닝
 *   → 속도 = 5km / 0.5h = 10 km/h → METs 9.0
 *   → 칼로리 = 70 × 9.0 × 0.5 × 1.05 = 330.75 kcal
 *
 * 예2) 60kg, 1km, 14분 (4.3 km/h) 걷기
 *   → 속도 = 1km / 0.233h = 4.3 km/h → METs 3.5
 *   → 칼로리 = 60 × 3.5 × 0.233 × 1.05 = 51.4 kcal
 *
 * @author WayToEarth Team
 * @since 2025-01-09
 */
object CalorieCalculator {

    /**
     * 칼로리 계산 (METs 기반)
     *
     * @param distanceKm 거리(km)
     * @param durationSeconds 시간(초)
     * @param weightKg 체중(kg), 기본값 65kg
     * @return 소모 칼로리(kcal), 반올림된 정수
     */
    fun calculate(distanceKm: Double, durationSeconds: Int, weightKg: Int = 65): Int {
        // 유효성 검증
        if (distanceKm <= 0 || durationSeconds <= 0 || weightKg <= 0) {
            return 0
        }

        // 시간을 시간(hour) 단위로 변환
        val durationHours = durationSeconds / 3600.0

        // 속도(km/h) 계산
        val speedKmh = distanceKm / durationHours

        // 속도에 따른 METs 값 결정 (백엔드와 동일)
        val mets = when {
            speedKmh < 6.0 -> 3.5  // 걷기
            speedKmh < 8.0 -> 7.0  // 조깅
            speedKmh < 10.0 -> 9.0  // 러닝
            else -> 11.0           // 빠른 러닝
        }

        // 칼로리 계산: 체중(kg) × METs × 시간(h) × 1.05
        val calories = weightKg * mets * durationHours * 1.05

        // 반올림하여 정수로 반환
        return calories.toInt()
    }

    /**
     * 미터 단위 거리로 칼로리 계산
     *
     * @param distanceMeters 거리(m)
     * @param durationSeconds 시간(초)
     * @param weightKg 체중(kg), 기본값 65kg
     * @return 소모 칼로리(kcal)
     */
    fun calculateFromMeters(distanceMeters: Int, durationSeconds: Int, weightKg: Int = 65): Int {
        return calculate(distanceMeters / 1000.0, durationSeconds, weightKg)
    }

    /**
     * 속도(km/h) 계산 헬퍼 메서드
     *
     * @param distanceKm 거리(km)
     * @param durationSeconds 시간(초)
     * @return 속도(km/h)
     */
    fun calculateSpeed(distanceKm: Double, durationSeconds: Int): Double {
        if (distanceKm <= 0 || durationSeconds <= 0) {
            return 0.0
        }
        val durationHours = durationSeconds / 3600.0
        return distanceKm / durationHours
    }

    /**
     * METs 값 계산 헬퍼 메서드
     *
     * @param speedKmh 속도(km/h)
     * @return METs 값
     */
    fun getMets(speedKmh: Double): Double {
        return when {
            speedKmh < 6.0 -> 3.5  // 걷기
            speedKmh < 8.0 -> 7.0  // 조깅
            speedKmh < 10.0 -> 9.0  // 러닝
            else -> 11.0           // 빠른 러닝
        }
    }
}
