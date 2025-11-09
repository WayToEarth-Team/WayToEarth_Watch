package cloud.waytoearth.watch.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * 사용자 프로필 정보 저장/로드 (SharedPreferences)
 */
object UserPreferences {

    private const val PREF_NAME = "waytoearth_user_prefs"
    private const val KEY_WEIGHT = "weight_kg"
    private const val KEY_HEIGHT = "height_cm"
    private const val DEFAULT_WEIGHT = 65

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 체중 저장 (kg)
     */
    fun saveWeight(context: Context, weightKg: Int) {
        getPrefs(context).edit().putInt(KEY_WEIGHT, weightKg).apply()
    }

    /**
     * 체중 로드 (kg), 기본값 65kg
     */
    fun getWeight(context: Context): Int {
        return getPrefs(context).getInt(KEY_WEIGHT, DEFAULT_WEIGHT)
    }

    /**
     * 키 저장 (cm)
     */
    fun saveHeight(context: Context, heightCm: Int) {
        getPrefs(context).edit().putInt(KEY_HEIGHT, heightCm).apply()
    }

    /**
     * 키 로드 (cm), 기본값 0 (설정 안 됨)
     */
    fun getHeight(context: Context): Int {
        return getPrefs(context).getInt(KEY_HEIGHT, 0)
    }

    /**
     * 프로필 정보 한번에 저장
     */
    fun saveProfile(context: Context, weightKg: Int?, heightCm: Int?) {
        val editor = getPrefs(context).edit()
        weightKg?.let { editor.putInt(KEY_WEIGHT, it) }
        heightCm?.let { editor.putInt(KEY_HEIGHT, it) }
        editor.apply()
    }

    /**
     * 프로필 정보 초기화
     */
    fun clearProfile(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
