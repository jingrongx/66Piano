package com.pianokids.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// ============== Entities ==============

/**
 * 课程进度实体。
 *
 * @property lessonId 课程唯一标识（主键）
 * @property completed 是否完成
 * @property stars 获得星数（0~3）
 * @property lastPlayedAt 上次练习时间戳（毫秒）
 */
@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val lessonId: String,
    val completed: Boolean,
    val stars: Int,
    val lastPlayedAt: Long,
)

/**
 * 自定义/导入乐谱实体。
 *
 * 三种来源共用同一张表：CUSTOM（手动编辑）/ SCAN（拍照识谱）/ MIDI（导入 MIDI）。
 *
 * @property id 主键（自动生成）
 * @property title 乐谱标题
 * @property source 来源（CUSTOM / SCAN / MIDI）
 * @property tempo BPM
 * @property timeSigNumerator 拍号分子
 * @property timeSigDenominator 拍号分母
 * @property notesJson 序列化的音符列表（JSON）
 * @property durationMs 整曲时长（毫秒）
 * @property coverColor 封面色（用于列表展示）
 * @property createdAt 创建时间戳
 * @property updatedAt 更新时间戳
 */
@Entity(tableName = "pieces")
data class PieceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val source: String,
    val tempo: Int,
    val timeSigNumerator: Int,
    val timeSigDenominator: Int,
    val notesJson: String,
    val durationMs: Long,
    val coverColor: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * 成就实体。
 *
 * @property id 成就唯一标识
 * @property unlocked 是否已解锁
 * @property unlockedAt 解锁时间戳（毫秒），未解锁为 0
 */
@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val id: String,
    val unlocked: Boolean,
    val unlockedAt: Long,
)

/**
 * 一次练习会话实体。
 *
 * @property id 自增主键
 * @property lessonId 关联课程
 * @property durationMs 持续时长（毫秒）
 * @property accuracy 准确度 [0,1]
 * @property timestamp 会话发生时间戳（毫秒）
 */
@Entity(tableName = "practice_sessions")
data class PracticeSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val lessonId: String,
    val durationMs: Long,
    val accuracy: Float,
    val timestamp: Long,
)

// ============== DAOs ==============

/**
 * 课程进度数据访问。
 */
@Dao
interface ProgressDao {
    @Upsert
    suspend fun upsert(entity: ProgressEntity)

    @Query("SELECT * FROM progress WHERE lessonId = :lessonId")
    suspend fun get(lessonId: String): ProgressEntity?

    @Query("SELECT * FROM progress")
    fun observeAll(): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM progress WHERE completed = 1")
    suspend fun getCompleted(): List<ProgressEntity>

    @Query("SELECT COALESCE(SUM(stars), 0) FROM progress")
    suspend fun getTotalStars(): Int

    @Query("SELECT COUNT(*) FROM progress WHERE completed = 1")
    suspend fun getCompletedCount(): Int
}

/**
 * 成就数据访问。
 */
@Dao
interface AchievementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AchievementEntity)

    @Update
    suspend fun update(entity: AchievementEntity)

    @Query("SELECT * FROM achievements WHERE id = :id")
    suspend fun get(id: String): AchievementEntity?

    @Query("SELECT * FROM achievements")
    fun observeAll(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements WHERE unlocked = 1")
    suspend fun getUnlocked(): List<AchievementEntity>

    @Query("UPDATE achievements SET unlocked = 1, unlockedAt = :timestamp WHERE id = :id")
    suspend fun unlock(id: String, timestamp: Long)
}

/**
 * 练习会话数据访问。
 */
@Dao
interface PracticeDao {
    @Insert
    suspend fun insert(entity: PracticeSessionEntity): Long

    @Query("SELECT * FROM practice_sessions WHERE lessonId = :lessonId ORDER BY timestamp DESC")
    fun observeSessions(lessonId: String): Flow<List<PracticeSessionEntity>>

    @Query("SELECT * FROM practice_sessions ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<PracticeSessionEntity>

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM practice_sessions")
    suspend fun getTotalDurationMs(): Long

    @Query("SELECT COALESCE(AVG(accuracy), 0) FROM practice_sessions")
    suspend fun getAverageAccuracy(): Float
}

/**
 * 自定义/导入乐谱数据访问。
 */
@Dao
interface PieceDao {
    @Insert
    suspend fun insert(entity: PieceEntity): Long

    @Update
    suspend fun update(entity: PieceEntity)

    @Query("DELETE FROM pieces WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM pieces WHERE id = :id")
    suspend fun get(id: Long): PieceEntity?

    @Query("SELECT * FROM pieces ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PieceEntity>>

    @Query("SELECT COUNT(*) FROM pieces")
    suspend fun count(): Int
}

// ============== Database ==============

/**
 * 应用主数据库。
 *
 * 包含三张表：[ProgressEntity]、[AchievementEntity]、[PracticeSessionEntity]。
 */
@Database(
    entities = [
        ProgressEntity::class,
        AchievementEntity::class,
        PracticeSessionEntity::class,
        PieceEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class PianoDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
    abstract fun achievementDao(): AchievementDao
    abstract fun practiceDao(): PracticeDao
    abstract fun pieceDao(): PieceDao

    companion object {
        const val DB_NAME = "pianokids.db"
    }
}
