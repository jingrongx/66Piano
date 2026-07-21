package com.pianokids.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pianokids.data.prefs.UserPreferences
import com.pianokids.data.repo.ProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 最近练习曲目展示信息。
 */
data class RecentSong(
    val title: String,
    val subtitle: String,
)

/**
 * 首页 UI 状态。
 */
data class HomeUiState(
    val greeting: String = "嗨，欢迎回来！",
    val petEmoji: String = "🐣",
    val petLevel: Int = 1,
    val totalStars: Int = 0,
    val streakDays: Int = 0,
    val recentSongs: List<RecentSong> = listOf(
        RecentSong("小星星", "点击开始练习"),
    ),
)

/**
 * 首页 ViewModel。
 *
 * 汇总展示：问候语、豆豆头像、星星数、连续打卡天数、最近练习曲目。
 * 启动时自动打卡。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val progressRepository: ProgressRepository,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    /** 刷新触发器：变更后重新读取 DataStore 中的偏好值 */
    private val prefsRefresh = MutableStateFlow(0)

    /** 累计星星数：来自课程进度表汇总 */
    val totalStars: StateFlow<Int> = progressRepository.observeProgress()
        .map { list -> list.sumOf { it.stars } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )

    /** 连续打卡天数：随 [prefsRefresh] 变化重新读取 */
    val streakDays: StateFlow<Int> = prefsRefresh
        .map { userPreferences.streakDays() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )

    /** 豆豆等级：随 [prefsRefresh] 变化重新读取 */
    val petLevel: StateFlow<Int> = prefsRefresh
        .map { userPreferences.petLevel() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 1,
        )

    init {
        // 启动时打卡
        viewModelScope.launch {
            userPreferences.checkInToday()
            prefsRefresh.value = prefsRefresh.value + 1
        }
    }

    /** 根据当前时间生成问候语 */
    fun greeting(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..10 -> "早上好！新的一天开始啦"
            in 11..13 -> "中午好！记得休息一下哦"
            in 14..17 -> "下午好！一起弹琴吧"
            in 18..21 -> "晚上好！今天练琴了吗？"
            else -> "夜深啦，早点休息哦~"
        }
    }

    /** 根据豆豆等级返回对应 emoji */
    fun petEmoji(level: Int): String = when (level) {
        1 -> "🐣"
        2 -> "🐤"
        3 -> "🐥"
        else -> "🐔"
    }
}
