package cloud.waytoearth.watch.utils

object HeartRateCalculator {

    /**
     * 평균 심박수 계산
     * @param heartRates 심박수 리스트 (null 제외)
     * @return 평균 심박수 (null이면 null)
     */
    fun calculateAverage(heartRates: List<Int>): Int? {
        if (heartRates.isEmpty()) return null
        return heartRates.average().toInt()
    }

    /**
     * 최대 심박수 계산
     * @param heartRates 심박수 리스트 (null 제외)
     * @return 최대 심박수 (null이면 null)
     */
    fun calculateMax(heartRates: List<Int>): Int? {
        return heartRates.maxOrNull()
    }
}
