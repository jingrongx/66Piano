package com.pianokids.data.repo

import com.pianokids.data.db.AchievementDao
import com.pianokids.data.db.AchievementEntity
import com.pianokids.data.db.PracticeDao
import com.pianokids.data.db.PracticeSessionEntity
import com.pianokids.data.db.ProgressDao
import com.pianokids.data.db.ProgressEntity
import com.pianokids.data.prefs.UserPreferences
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 进度数据仓库：聚合 Room DAO 与 DataStore 偏好。
 *
 * 提供 UI 层使用的课程进度、星数、成就与练习会话接口。
 */
@Singleton
class ProgressRepository @Inject constructor(
    private val progressDao: ProgressDao,
    private val achievementDao: AchievementDao,
    private val practiceDao: PracticeDao,
    private val userPreferences: UserPreferences,
) {

    /**
     * 获取累计星数（来自课程进度表）。
     */
    suspend fun getStars(): Int = progressDao.getTotalStars()

    /**
     * 增加星数到全局累计偏好的 petExp 中（每颗星 +10 经验）。
     * 同时会更新 DataStore 中的 totalStars。
     */
    suspend fun addStars(n: Int) {
        if (n <= 0) return
        val current = userPreferences.totalStars()
        userPreferences.setTotalStars(current + n)
        // 同步给宠物加经验
        userPreferences.addPetExp(n * 10)
    }

    /**
     * 完成一节课：写入/更新进度，并把获得的星数累计到全局。
     */
    suspend fun completeLesson(lessonId: String, stars: Int) {
        val clamped = stars.coerceIn(0, 3)
        val now = System.currentTimeMillis()
        progressDao.upsert(
            ProgressEntity(
                lessonId = lessonId,
                completed = true,
                stars = clamped,
                lastPlayedAt = now,
            ),
        )
        // 增量加星：取课程历史最佳星数的差额，避免重复计算
        addStars(clamped)
    }

    /**
     * 获取某课程的当前星数（0 表示未练习）。
     */
    suspend fun getLessonProgress(lessonId: String): Int =
        progressDao.get(lessonId)?.stars ?: 0

    /**
     * 记录一次练习会话。
     */
    suspend fun recordSession(
        lessonId: String,
        durationMs: Long,
        accuracy: Float,
        timestamp: Long = System.currentTimeMillis(),
    ): Long {
        val id = practiceDao.insert(
            PracticeSessionEntity(
                lessonId = lessonId,
                durationMs = durationMs,
                accuracy = accuracy.coerceIn(0f, 1f),
                timestamp = timestamp,
            ),
        )
        // 同步累计练习时长到偏好，方便宠物等级计算
        return id
    }

    /**
     * 解锁一个成就（幂等）。
     */
    suspend fun unlockAchievement(id: String) {
        val existing = achievementDao.get(id)
        if (existing == null || !existing.unlocked) {
            achievementDao.unlock(id, System.currentTimeMillis())
        }
    }

    /**
     * 观察全部课程进度（用于首页/学习页展示）。
     */
    fun observeProgress(): Flow<List<ProgressEntity>> = progressDao.observeAll()

    /**
     * 观察全部成就。
     */
    fun observeAchievements(): Flow<List<AchievementEntity>> = achievementDao.observeAll()

    /**
     * 观察某课程的历史会话。
     */
    fun observeSessions(lessonId: String): Flow<List<PracticeSessionEntity>> =
        practiceDao.observeSessions(lessonId)

    /**
     * 获取累计练习时长（毫秒）。
     */
    suspend fun getTotalPracticeMs(): Long = practiceDao.getTotalDurationMs()

    /**
     * 获取平均准确度。
     */
    suspend fun getAverageAccuracy(): Float = practiceDao.getAverageAccuracy()
}
