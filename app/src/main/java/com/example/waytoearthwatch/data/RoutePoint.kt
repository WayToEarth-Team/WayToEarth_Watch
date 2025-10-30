package com.example.waytoearthwatch.data

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
