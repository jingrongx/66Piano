package com.pianokids.ui.pet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pianokids.data.prefs.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 豆豆宠物 UI 状态。
 */
data class PetUiState(
    val petLevel: Int = 1,
    val petExp: Int = 0,
    val petEmoji: String = "🐣",
    val expInLevel: Int = 0,
    val expForNextLevel: Int = 100,
    val expProgress: Float = 0f,
)

/**
 * 豆豆宠物 ViewModel。
 *
 * 展示豆豆形象、等级与经验条。
 */
@HiltViewModel
class PetViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
) : ViewModel() {

    /** 刷新触发器 */
    private val refresh = MutableStateFlow(0)

    /** 豆豆等级 */
    val petLevel: StateFlow<Int> = refresh
        .map { userPreferences.petLevel() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 1,
        )

    /** 豆豆经验 */
    val petExp: StateFlow<Int> = refresh
        .map { userPreferences.petExp() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )

    /**
     * 根据等级返回豆豆 emoji。
     */
    fun petEmoji(level: Int): String = when (level) {
        1 -> "🐣"
        2 -> "🐤"
        3 -> "🐥"
        else -> "🐔"
    }

    companion object {
        /** 每级所需经验 */
        const val EXP_PER_LEVEL = 100
    }
}
