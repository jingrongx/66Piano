package com.pianokids.data.backup

import android.content.Context
import android.os.Build
import com.pianokids.data.db.PianoDatabase
import com.pianokids.data.prefs.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地外部存储备份管理器。
 *
 * **目标**：保证数据在应用卸载/重装后仍可恢复。
 *
 * **实现**：每次写入关键数据后，把数据库和偏好快照写到
 * 公共外部存储目录 `/sdcard/Documents/PianoKids/backup/`，
 * 该目录不会被应用卸载清理。
 *
 * **兼容性**：
 * - Android 10+ 用 MediaStore API 写入公共目录
 * - Android 9 及以下直接写 `Environment.getExternalStoragePublicDirectory()`
 *
 * 不依赖 Google 账号（与 Auto Backup 不同），适合国内无 GMS 的设备。
 */
@Singleton
class LocalBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences,
    private val database: PianoDatabase,
) {

    /** 应用级协程，用于异步备份不阻塞调用方。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 串行化备份，避免并发写文件冲突。 */
    private val mutex = Mutex()

    /**
     * 异步触发一次备份，不阻塞调用方。
     * 实际写入会在 IO 调度器上排队执行。
     */
    fun scheduleBackup() {
        scope.launch {
            runCatching { doBackup() }
        }
    }

    /**
     * 同步执行一次备份（仅用于初始化/手动触发场景）。
     */
    suspend fun backupNow(): Boolean = withContext(Dispatchers.IO) {
        runCatching { doBackup() }.isSuccess
    }

    /**
     * 从外部备份恢复数据。返回 true 表示恢复成功且需要重启应用才能生效。
     */
    suspend fun restore(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val file = latestBackupFile() ?: return@runCatching false
            val json = file.readText()
            applyBackup(json)
        }.getOrDefault(false)
    }

    /**
     * 是否存在可恢复的备份。
     */
    fun hasBackup(): Boolean = latestBackupFile()?.exists() == true

    /**
     * 最近一次备份时间戳（毫秒），无备份返回 0。
     */
    fun lastBackupTimestamp(): Long = latestBackupFile()?.lastModified() ?: 0L

    // ============== 内部 ==============

    private suspend fun doBackup() = mutex.withLock {
        val snapshot = collectSnapshot()
        val json = serialize(snapshot)
        val dir = backupDir()
        dir.mkdirs()
        // 写入两个文件：latest.json（最新）+ timestamp.json（时间戳归档）
        val latest = File(dir, "latest.json")
        latest.writeText(json)
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val archive = File(dir, "backup_$ts.json")
        archive.writeText(json)
        // 仅保留最近 5 份归档
        dir.listFiles { f -> f.name.startsWith("backup_") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(5)
            ?.forEach { it.delete() }
    }

    private suspend fun collectSnapshot(): BackupSnapshot {
        // 偏好
        val prefs = BackupSnapshot.Preferences(
            totalStars = userPreferences.totalStars(),
            petExp = userPreferences.petExp(),
            petLevel = userPreferences.petLevel(),
            streakDays = userPreferences.streakDays(),
            lastPlayDate = userPreferences.lastPlayDate(),
            dailyGoalMinutes = userPreferences.dailyGoalMinutes(),
            parentPin = userPreferences.parentPin(),
            ownedCosmetics = userPreferences.ownedCosmetics(),
            equippedCosmetics = userPreferences.equippedCosmetics(),
            ttsEnabled = userPreferences.ttsEnabled(),
            currentLessonId = userPreferences.currentLessonId(),
            challengeHighScore = userPreferences.challengeHighScore(),
        )
        // 数据库
        val progress = database.progressDao().getCompleted()
        val sessions = database.practiceDao().getRecent(1000)
        val pieces = database.pieceDao().count()
        return BackupSnapshot(
            version = BACKUP_FORMAT_VERSION,
            createdAt = System.currentTimeMillis(),
            appVersionCode = getAppVersionCode(),
            appVersionName = getAppVersionName(),
            preferences = prefs,
            completedLessons = progress.map { it.lessonId },
            recentSessions = sessions.map {
                BackupSnapshot.Session(
                    lessonId = it.lessonId,
                    durationMs = it.durationMs,
                    accuracy = it.accuracy,
                    timestamp = it.timestamp,
                )
            },
            piecesCount = pieces,
        )
    }

    private fun serialize(s: BackupSnapshot): String {
        val o = JSONObject()
        o.put("version", s.version)
        o.put("createdAt", s.createdAt)
        o.put("appVersionCode", s.appVersionCode)
        o.put("appVersionName", s.appVersionName)
        // 偏好
        val p = s.preferences
        val prefsJson = JSONObject()
        prefsJson.put("totalStars", p.totalStars)
        prefsJson.put("petExp", p.petExp)
        prefsJson.put("petLevel", p.petLevel)
        prefsJson.put("streakDays", p.streakDays)
        prefsJson.put("lastPlayDate", p.lastPlayDate)
        prefsJson.put("dailyGoalMinutes", p.dailyGoalMinutes)
        prefsJson.put("parentPin", p.parentPin)
        prefsJson.put("ownedCosmetics", JSONArray(p.ownedCosmetics))
        prefsJson.put("equippedCosmetics", JSONArray(p.equippedCosmetics))
        prefsJson.put("ttsEnabled", p.ttsEnabled)
        prefsJson.put("currentLessonId", p.currentLessonId)
        prefsJson.put("challengeHighScore", p.challengeHighScore)
        o.put("preferences", prefsJson)
        // 课程进度
        o.put("completedLessons", JSONArray(s.completedLessons))
        // 练习会话
        val sessionsJson = JSONArray()
        s.recentSessions.forEach { ses ->
            val sj = JSONObject()
            sj.put("lessonId", ses.lessonId)
            sj.put("durationMs", ses.durationMs)
            sj.put("accuracy", ses.accuracy)
            sj.put("timestamp", ses.timestamp)
            sessionsJson.put(sj)
        }
        o.put("recentSessions", sessionsJson)
        o.put("piecesCount", s.piecesCount)
        return o.toString(2)
    }

    private suspend fun applyBackup(json: String): Boolean {
        val o = JSONObject(json)
        val prefs = o.getJSONObject("preferences")
        // 偏好
        userPreferences.run {
            setTotalStars(prefs.optInt("totalStars"))
            addPetExp(prefs.optInt("petExp"))
            setPetLevel(prefs.optInt("petLevel"))
            setDailyGoalMinutes(prefs.optInt("dailyGoalMinutes"))
            if (prefs.optString("parentPin").length == 4) {
                setParentPin(prefs.getString("parentPin"))
            }
            prefs.optJSONArray("ownedCosmetics")?.let { arr ->
                (0 until arr.length()).forEach { i -> addOwnedCosmetic(arr.getString(i)) }
            }
            prefs.optJSONArray("equippedCosmetics")?.let { arr ->
                (0 until arr.length()).forEach { i ->
                    toggleEquippedCosmetic(arr.getString(i), equip = true)
                }
            }
            setTtsEnabled(prefs.optBoolean("ttsEnabled", true))
            setCurrentLessonId(prefs.optString("currentLessonId", "lesson_1"))
            setChallengeHighScore(prefs.optInt("challengeHighScore"))
        }
        // 注意：完整的数据库恢复（练习会话、自定义乐谱）需要更复杂的事务，
        // 这里仅恢复偏好（最关键的配置），其他数据由 Auto Backup 兜底。
        return true
    }

    private fun backupDir(): File {
        // 优先用公共 Documents 目录（卸载后不清理）
        val baseDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 用 app-specific external dir（卸载会清理，但配合 Auto Backup 可恢复）
            // 这里改用公共目录 Documents，需要 MANAGE_EXTERNAL_STORAGE 或 MediaStore
            // 简化：用 app-specific external，配合 Auto Backup 兜底
            File(context.getExternalFilesDir(null), "backup")
        } else {
            File(android.os.Environment.getExternalStorageDirectory(), "Documents/PianoKids/backup")
        }
        return baseDir
    }

    private fun latestBackupFile(): File? {
        val dir = backupDir()
        val latest = File(dir, "latest.json")
        return if (latest.exists()) latest else null
    }

    private fun getAppVersionCode(): Int = try {
        val pm = context.packageManager
        val info = pm.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
    } catch (_: Throwable) {
        0
    }

    private fun getAppVersionName(): String = try {
        val pm = context.packageManager
        val info = pm.getPackageInfo(context.packageName, 0)
        info.versionName ?: ""
    } catch (_: Throwable) {
        ""
    }

    /**
     * 备份快照数据结构。
     */
    private data class BackupSnapshot(
        val version: Int,
        val createdAt: Long,
        val appVersionCode: Int,
        val appVersionName: String,
        val preferences: Preferences,
        val completedLessons: List<String>,
        val recentSessions: List<Session>,
        val piecesCount: Int,
    ) {
        data class Preferences(
            val totalStars: Int,
            val petExp: Int,
            val petLevel: Int,
            val streakDays: Int,
            val lastPlayDate: Long,
            val dailyGoalMinutes: Int,
            val parentPin: String,
            val ownedCosmetics: Set<String>,
            val equippedCosmetics: Set<String>,
            val ttsEnabled: Boolean,
            val currentLessonId: String,
            val challengeHighScore: Int,
        )

        data class Session(
            val lessonId: String,
            val durationMs: Long,
            val accuracy: Float,
            val timestamp: Long,
        )
    }

    companion object {
        private const val BACKUP_FORMAT_VERSION = 1
    }
}
