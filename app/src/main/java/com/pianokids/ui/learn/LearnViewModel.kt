package com.pianokids.ui.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pianokids.data.repo.ProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 一节课的元信息。
 */
data class LessonItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val level: Int,
    val icon: String,
)

/**
 * 一个等级的学习路径段。
 */
data class LevelSection(
    val level: Int,
    val title: String,
    val emoji: String,
    val lessons: List<LessonItem>,
)

/**
 * 学习页 UI 状态。
 */
data class LearnUiState(
    val levels: List<LevelSection> = emptyList(),
    val progress: Map<String, Int> = emptyMap(),
)

/**
 * 学习页 ViewModel。
 *
 * 展示 6 个等级的学习路径。P1 仅第 1 课"认识钢琴"可学，其余锁定。
 */
@HiltViewModel
class LearnViewModel @Inject constructor(
    private val progressRepository: ProgressRepository,
) : ViewModel() {

    /** 学习路径静态数据：6 个等级 */
    val levels: List<LevelSection> = buildLearningPath()

    /** 各课程已获星数：lessonId -> stars */
    val progress: StateFlow<Map<String, Int>> = progressRepository.observeProgress()
        .map { list -> list.associate { it.lessonId to it.stars } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap(),
        )

    /** 已完成课程 id 集合（stars > 0 视为完成） */
    val completedIds: StateFlow<Set<String>> = progressRepository.observeProgress()
        .map { list -> list.filter { it.stars > 0 }.map { it.lessonId }.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptySet(),
        )

    /**
     * 判断某课是否解锁。
     * 规则：lesson_1 默认解锁；后续需上一课完成。
     */
    fun isUnlocked(lessonId: String): Boolean {
        val unlocked = LessonCatalog.unlockedLessonIds(completedIds.value)
        return lessonId in unlocked
    }

    companion object {
        const val LESSON_1 = "lesson_1"
        const val LESSON_2 = "lesson_2"
        const val LESSON_3 = "lesson_3"
        const val LESSON_4 = "lesson_4"
        const val LESSON_5 = "lesson_5"
        const val LESSON_6 = "lesson_6"
        const val LESSON_7 = "lesson_7"
        const val LESSON_8 = "lesson_8"
        const val LESSON_9 = "lesson_9"
        const val LESSON_10 = "lesson_10"
        const val LESSON_11 = "lesson_11"
        const val LESSON_12 = "lesson_12"

        private fun buildLearningPath(): List<LevelSection> = listOf(
            LevelSection(
                level = 1,
                title = "启蒙",
                emoji = "🌱",
                lessons = listOf(
                    LessonItem(LESSON_1, "认识钢琴", "了解钢琴的奥秘", 1, "🎹"),
                    LessonItem(LESSON_2, "认识白键", "找找 C 在哪里", 1, "🎵"),
                    LessonItem(LESSON_3, "认识黑键", "黑白交替的秘密", 1, "🎶"),
                ),
            ),
            LevelSection(
                level = 2,
                title = "基础",
                emoji = "🌿",
                lessons = listOf(
                    LessonItem(LESSON_4, "Do Re Mi", "三个音的魔法", 2, "🎤"),
                    LessonItem(LESSON_5, "五指练习", "让手指灵活起来", 2, "✋"),
                ),
            ),
            LevelSection(
                level = 3,
                title = "进阶",
                emoji = "⭐",
                lessons = listOf(
                    LessonItem(LESSON_6, "双手配合", "左右手一起弹", 3, "🤲"),
                    LessonItem(LESSON_7, "节奏入门", "感受节拍", 3, "🥁"),
                ),
            ),
            LevelSection(
                level = 4,
                title = "提升",
                emoji = "🌟",
                lessons = listOf(
                    LessonItem(LESSON_8, "和弦基础", "三个音一起响", 4, "🎼"),
                    LessonItem(LESSON_9, "音阶练习", "上下行的乐趣", 4, "📈"),
                ),
            ),
            LevelSection(
                level = 5,
                title = "挑战",
                emoji = "🏆",
                lessons = listOf(
                    LessonItem(LESSON_10, "简单乐曲", "弹一首小曲", 5, "🎹"),
                    LessonItem(LESSON_11, "表现力", "让音乐动起来", 5, "💫"),
                ),
            ),
            LevelSection(
                level = 6,
                title = "大师",
                emoji = "👑",
                lessons = listOf(
                    LessonItem(LESSON_12, "综合演奏", "成为小钢琴家", 6, "🎉"),
                ),
            ),
        )
    }
}
