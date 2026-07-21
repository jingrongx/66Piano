package com.pianokids.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
 * - [parentPin]：家长锁 PIN（默认 0000）
 * - [ownedCosmetics]：已拥有的装扮 id 集合
 * - [equippedCosmetics]：当前装备的装扮 id 集合
 * - [ttsEnabled]：TTS 语音引导开关
 * - [currentLessonId]：当前所学课程 id（用于解锁逻辑）
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
    private val keyParentPin = stringPreferencesKey("parent_pin")
    private val keyOwnedCosmetics = stringSetPreferencesKey("owned_cosmetics")
    private val keyEquippedCosmetics = stringSetPreferencesKey("equipped_cosmetics")
    private val keyTtsEnabled = intPreferencesKey("tts_enabled")
    private val keyCurrentLessonId = stringPreferencesKey("current_lesson_id")
    private val keyChallengeHighScore = intPreferencesKey("challenge_high_score")

    // ============== 读 ==============

    suspend fun totalStars(): Int = read(keyTotalStars, 0)

    suspend fun petExp(): Int = read(keyPetExp, 0)

    suspend fun petLevel(): Int = read(keyPetLevel, 1)

    suspend fun streakDays(): Int = read(keyStreakDays, 0)

    suspend fun lastPlayDate(): Long = read(keyLastPlayDate, 0L)

    suspend fun dailyGoalMinutes(): Int = read(keyDailyGoalMinutes, DEFAULT_DAILY_GOAL_MIN)

    /**
     * 家长锁 PIN。未设置时返回默认 "0000"。
     */
    suspend fun parentPin(): String = read(keyParentPin, DEFAULT_PARENT_PIN)

    suspend fun ownedCosmetics(): Set<String> = read(keyOwnedCosmetics, emptySet())

    suspend fun equippedCosmetics(): Set<String> = read(keyEquippedCosmetics, emptySet())

    suspend fun ttsEnabled(): Boolean = read(keyTtsEnabled, 1) == 1

    suspend fun currentLessonId(): String = read(keyCurrentLessonId, "lesson_1")

    suspend fun challengeHighScore(): Int = read(keyChallengeHighScore, 0)

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

    // ============== 家长锁 ==============

    /**
     * 设置家长锁 PIN。PIN 必须为 4 位数字字符串。
     */
    suspend fun setParentPin(pin: String) {
        require(pin.length == 4 && pin.all { it.isDigit() }) {
            "PIN 必须为 4 位数字"
        }
        write(keyParentPin, pin)
    }

    /**
     * 校验 PIN 是否正确。
     */
    suspend fun verifyParentPin(input: String): Boolean = input == parentPin()

    // ============== 装扮商店 ==============

    /**
     * 添加一个已拥有的装扮 id。
     */
    suspend fun addOwnedCosmetic(id: String) {
        val current = ownedCosmetics().toMutableSet()
        if (current.add(id)) {
            write(keyOwnedCosmetics, current)
        }
    }

    /**
     * 装备/卸下某装扮。
     */
    suspend fun toggleEquippedCosmetic(id: String, equip: Boolean) {
        val current = equippedCosmetics().toMutableSet()
        if (equip) current.add(id) else current.remove(id)
        write(keyEquippedCosmetics, current)
    }

    /**
     * 花费 [cost] 颗星购买装扮。
     *
     * 单次事务原子操作：扣星 + 写入已拥有。返回值：
     * - true：购买成功
     * - false：星星不足，或已经拥有该装扮
     */
    suspend fun purchaseCosmetic(id: String, cost: Int): Boolean {
        var success = false
        store.edit { prefs ->
            val stars = prefs[keyTotalStars] ?: 0
            val owned = prefs[keyOwnedCosmetics] ?: emptySet()
            if (stars < cost || id in owned) {
                return@edit
            }
            prefs[keyTotalStars] = stars - cost
            prefs[keyOwnedCosmetics] = owned + id
            success = true
        }
        return success
    }

    // ============== TTS ==============

    suspend fun setTtsEnabled(enabled: Boolean) {
        write(keyTtsEnabled, if (enabled) 1 else 0)
    }

    // ============== 学习路径 ==============

    suspend fun setCurrentLessonId(lessonId: String) {
        write(keyCurrentLessonId, lessonId)
    }

    // ============== 闯关 ==============

    suspend fun setChallengeHighScore(score: Int) {
        if (score > challengeHighScore()) {
            write(keyChallengeHighScore, score)
        }
    }

    /**
     * 通关一关，奖励 [rewardStars] 颗星。
     */
    suspend fun completeChallenge(score: Int, rewardStars: Int) {
        setChallengeHighScore(score)
        if (rewardStars > 0) {
            val stars = totalStars()
            setTotalStars(stars + rewardStars)
            addPetExp(rewardStars * 5)
        }
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
        const val DEFAULT_PARENT_PIN = "0000"
    }
}
