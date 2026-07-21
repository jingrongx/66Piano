package com.pianokids.ui.parent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pianokids.data.db.PracticeDao
import com.pianokids.data.db.PracticeSessionEntity
import com.pianokids.data.prefs.UserPreferences
import com.pianokids.data.repo.ProgressRepository
import com.pianokids.tts.TtsHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 家长区 UI 状态。
 *
 * @property unlocked 是否已通过 PIN 解锁
 * @property totalStars 累计星星
 * @property petExp 宠物经验
 * @property petLevel 宠物等级
 * @property streakDays 连续打卡
 * @property dailyGoalMinutes 每日目标
 * @property ttsEnabled TTS 开关
 * @property recentSessions 最近 10 次练习
 * @property totalPracticeMs 累计练习时长
 * @property averageAccuracy 平均准确度
 * @property loading 是否加载中
 * @property message 一次性消息
 */
data class ParentUiState(
    val unlocked: Boolean = false,
    val totalStars: Int = 0,
    val petExp: Int = 0,
    val petLevel: Int = 1,
    val streakDays: Int = 0,
    val dailyGoalMinutes: Int = 15,
    val ttsEnabled: Boolean = true,
    val recentSessions: List<PracticeSessionEntity> = emptyList(),
    val totalPracticeMs: Long = 0L,
    val averageAccuracy: Float = 0f,
    val loading: Boolean = true,
    val message: String? = null,
)

/**
 * 家长区 ViewModel。
 *
 * 进入时需要先输入 PIN；解锁后才能查看孩子练琴数据、修改设置。
 */
@HiltViewModel
class ParentViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val progressRepository: ProgressRepository,
    private val practiceDao: PracticeDao,
    private val ttsHelper: TtsHelper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ParentUiState())
    val uiState: StateFlow<ParentUiState> = _uiState.asStateFlow()

    /**
     * 校验 PIN。
     */
    fun verifyPin(input: String) {
        viewModelScope.launch {
            val ok = userPreferences.verifyParentPin(input)
            if (ok) {
                _uiState.value = _uiState.value.copy(unlocked = true)
                loadStats()
            } else {
                _uiState.value = _uiState.value.copy(message = "PIN 错误")
            }
        }
    }

    /**
     * 加载家长区统计。
     */
    private fun loadStats() {
        viewModelScope.launch {
            val totalStars = userPreferences.totalStars()
            val petExp = userPreferences.petExp()
            val petLevel = userPreferences.petLevel()
            val streakDays = userPreferences.streakDays()
            val dailyGoalMinutes = userPreferences.dailyGoalMinutes()
            val ttsEnabled = userPreferences.ttsEnabled()
            val recentSessions = practiceDao.getRecent(10)
            val totalPracticeMs = progressRepository.getTotalPracticeMs()
            val averageAccuracy = progressRepository.getAverageAccuracy()
            _uiState.value = _uiState.value.copy(
                totalStars = totalStars,
                petExp = petExp,
                petLevel = petLevel,
                streakDays = streakDays,
                dailyGoalMinutes = dailyGoalMinutes,
                ttsEnabled = ttsEnabled,
                recentSessions = recentSessions,
                totalPracticeMs = totalPracticeMs,
                averageAccuracy = averageAccuracy,
                loading = false,
            )
        }
    }

    /**
     * 设置每日目标练习分钟数。
     */
    fun setDailyGoal(minutes: Int) {
        viewModelScope.launch {
            userPreferences.setDailyGoalMinutes(minutes)
            _uiState.update { it.copy(dailyGoalMinutes = minutes, message = "每日目标已设为 $minutes 分钟") }
        }
    }

    /**
     * 切换 TTS 开关。
     */
    fun toggleTts() {
        viewModelScope.launch {
            val newEnabled = !_uiState.value.ttsEnabled
            userPreferences.setTtsEnabled(newEnabled)
            ttsHelper.setEnabled(newEnabled)
            _uiState.update { it.copy(ttsEnabled = newEnabled) }
        }
    }

    /**
     * 修改 PIN。
     */
    fun setPin(newPin: String) {
        if (newPin.length != 4 || !newPin.all { it.isDigit() }) {
            _uiState.update { it.copy(message = "PIN 必须为 4 位数字") }
            return
        }
        viewModelScope.launch {
            userPreferences.setParentPin(newPin)
            _uiState.update { it.copy(message = "PIN 已更新") }
        }
    }

    /**
     * 消费一次性消息。
     */
    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
