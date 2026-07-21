package com.pianokids.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用级 DataStore 实例（每个进程一份，由 Kotlin 顶层属性惰性创建）。
 */
private val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pianokids_prefs",
)

/**
 * 用户偏好封装：基于 DataStore Preferences。
 *
 * 存储字段：
 * - [totalStars]：累计星星
 * - [petExp]：宠物经验
 * - [petLevel]：宠物等级
 * - [streakDays]：连续打卡天数
 * - [lastPlayDate]：上次打卡日期戳（yyyyMMdd 解析为 long）
 * - [dailyGoalMinutes]：每日目标练习分钟数
 */
@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store: DataStore<Preferences> get() = context.userDataStore

    private val keyTotalStars = intPreferencesKey("total_stars")
    private val keyPetExp = intPreferencesKey("pet_exp")
    private val keyPetLevel = intPreferencesKey("pet_level")
    private val keyStreakDays = intPreferencesKey("streak_days")
    private val keyLastPlayDate = longPreferencesKey("last_play_date")
    private val keyDailyGoalMinutes = intPreferencesKey("daily_goal_minutes")

    // ============== 读 ==============

    suspend fun totalStars(): Int = read(keyTotalStars, 0)

    suspend fun petExp(): Int = read(keyPetExp, 0)

    suspend fun petLevel(): Int = read(keyPetLevel, 1)

    suspend fun streakDays(): Int = read(keyStreakDays, 0)

    suspend fun lastPlayDate(): Long = read(keyLastPlayDate, 0L)

    suspend fun dailyGoalMinutes(): Int = read(keyDailyGoalMinutes, DEFAULT_DAILY_GOAL_MIN)

    /**
     * 增加 [n] 颗星，自动顺带处理宠物升级。
     */
    suspend fun setTotalStars(value: Int) {
        write(keyTotalStars, value.coerceAtLeast(0))
    }

    /**
     * 增加宠物经验，并自动升级。
     */
    suspend fun addPetExp(delta: Int) {
        if (delta == 0) return
        val newExp = (petExp() + delta).coerceAtLeast(0)
        val newLevel = computeLevel(newExp)
        write(keyPetExp, newExp)
        write(keyPetLevel, newLevel)
    }

    suspend fun setPetLevel(level: Int) {
        write(keyPetLevel, level.coerceAtLeast(1))
    }

    /**
     * 打卡：根据上次打卡日期与今天的关系，更新连续天数。
     * - 同一天重复调用：幂等。
     * - 跨过 1 天：streakDays + 1。
     * - 跨过 2 天以上：重置为 1。
     */
    suspend fun checkInToday(): Int {
        val today = todayDateInt()
        val last = lastPlayDate()
        val newStreak = when {
            last == 0L -> 1
            last == today.toLong() -> streakDays() // 已打过卡
            today - last == 1L -> streakDays() + 1
            else -> 1
        }
        write(keyStreakDays, newStreak)
        write(keyLastPlayDate, today.toLong())
        return newStreak
    }

    suspend fun setDailyGoalMinutes(minutes: Int) {
        write(keyDailyGoalMinutes, minutes.coerceAtLeast(1))
    }

    // ============== 内部工具 ==============

    private suspend fun <T> read(key: Preferences.Key<T>, default: T): T =
        try {
            store.data.first()[key] ?: default
        } catch (_: Throwable) {
            // DataStore 读取异常时返回默认值，避免崩溃
            default
        }

    private suspend fun <T> write(key: Preferences.Key<T>, value: T) {
        store.edit { it[key] = value }
    }

    /**
     * 由总经验推算宠物等级：每 100 经验一级。
     */
    private fun computeLevel(totalExp: Int): Int = (totalExp / 100) + 1

    /**
     * 返回今日 yyyyMMdd 形式的整数（用 [SimpleDateFormat] 控制时区）。
     */
    private fun todayDateInt(): Int {
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
        return fmt.format(Date()).toIntOrNull() ?: 0
    }

    companion object {
        private const val DEFAULT_DAILY_GOAL_MIN = 15
    }
}
