package com.example.waytoearthwatch.utils

import kotlin.math.*

object DistanceCalculator {

    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Haversine 공식을 사용하여 두 GPS 좌표 간 거리 계산
     * @param lat1 첫 번째 위도
     * @param lon1 첫 번째 경도
     * @param lat2 두 번째 위도
     * @param lon2 두 번째 경도
     * @return 거리 (미터)
     */
    fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_METERS * c
    }

    /**
     * 페이스 계산 (초/km)
     * @param distanceMeters 거리 (미터)
     * @param durationSeconds 시간 (초)
     * @return 페이스 (초/km), 거리가 0이면 null
     */
    fun calculatePace(distanceMeters: Int, durationSeconds: Int): Int? {
        if (distanceMeters == 0) return null
        val distanceKm = distanceMeters / 1000.0
        return (durationSeconds / distanceKm).toInt()
    }

    /**
     * 즉시 페이스 계산 (최근 100m 기준)
     * @param recentDistanceMeters 최근 거리 (미터, 권장: 100m)
     * @param recentDurationSeconds 최근 시간 (초)
     * @return 즉시 페이스 (초/km)
     */
    fun calculateInstantPace(
        recentDistanceMeters: Double,
        recentDurationSeconds: Int
    ): Int? {
        if (recentDistanceMeters < 10) return null  // 너무 짧은 거리는 제외
        val distanceKm = recentDistanceMeters / 1000.0
        return (recentDurationSeconds / distanceKm).toInt()
    }
}
