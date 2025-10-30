package com.example.waytoearthwatch.service

import android.content.Context
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.data.ExerciseConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HeartRateService(private val context: Context) {

    private val healthServicesClient = HealthServices.getClient(context)
    private val exerciseClient = healthServicesClient.exerciseClient

    /**
     * 심박수 실시간 스트림
     * @return Flow<Int?> - 심박수 BPM (null 가능)
     */
    fun getHeartRateUpdates(): Flow<Int?> {
        return exerciseClient.exerciseUpdateFlow
            .map { update: ExerciseUpdate ->
                val heartRateDataPoint = update.latestMetrics[DataType.HEART_RATE_BPM]
                heartRateDataPoint?.last()?.value?.toInt()
            }
    }

    /**
     * 운동 세션 시작
     */
    suspend fun startExercise() {
        val config = ExerciseConfig(
            exerciseType = ExerciseType.RUNNING,
            dataTypes = setOf(DataType.HEART_RATE_BPM),
            isAutoPauseAndResumeEnabled = false,
            isGpsEnabled = false  // GPS는 별도로 관리
        )
        exerciseClient.startExercise(config)
    }

    /**
     * 운동 세션 종료
     */
    suspend fun endExercise() {
        exerciseClient.endExercise()
    }
}
