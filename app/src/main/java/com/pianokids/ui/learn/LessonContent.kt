package com.pianokids.ui.learn

/**
 * 单节课的配置数据。
 *
 * 让 LessonViewModel 可以根据 lessonId 加载不同课程内容，
 * 而不是把所有内容硬编码在 ViewModel 里。
 *
 * @property id 课程 ID（与 LearnViewModel.LESSON_X 对应）
 * @property title 标题（例如「认识钢琴」）
 * @property level 等级（1..6）
 * @property icon emoji 图标
 * @property introText 「看动画」步骤展示的知识文本
 * @property demoMidi 「看示范」步骤播放的示范音
 * @property demoName 示范音名（例如 "中央 C"）
 * @property demoDesc 示范步骤的提示语
 * @property followTargets 「跟我弹」步骤应弹的音（默认只有一个，完成即下一步）
 * @property soloTargets 「自己弹」步骤应依次弹奏的音
 * @property soloHint 自己弹步骤的提示
 * @property rewardStars 通关奖励星数
 * @property rewardExp 通关奖励经验
 * @property ttsLines TTS 引导文本（按步骤顺序）
 */
data class LessonContent(
    val id: String,
    val title: String,
    val level: Int,
    val icon: String,
    val introText: String,
    val demoMidi: Int,
    val demoName: String,
    val demoDesc: String,
    val followTargets: List<Int>,
    val soloTargets: List<Int>,
    val soloHint: String,
    val rewardStars: Int = 3,
    val rewardExp: Int = 30,
    val ttsLines: List<String> = emptyList(),
)

/**
 * 6 级学习路径的全部 12 节课配置。
 *
 * 课程难度递增：
 * - 第 1 课 认识中央 C
 * - 第 2 课 认识白键 C-D-E
 * - 第 3 课 认识黑键（升号）
 * - 第 4 课 Do Re Mi
 * - 第 5 课 五指练习 C-D-E-F-G
 * - 第 6 课 双手 C + G
 * - 第 7 课 节奏入门
 * - 第 8 课 C 大三和弦
 * - 第 9 课 C 大调音阶上行
 * - 第 10 课 小星星片段
 * - 第 11 课 表现力（强弱）
 * - 第 12 课 综合演奏
 */
object LessonCatalog {

    /** 全部课程，按 id 索引。 */
    val all: Map<String, LessonContent> = listOf(
        LessonContent(
            id = "lesson_1",
            title = "认识钢琴",
            level = 1,
            icon = "🎹",
            introText = "钢琴一共有 88 个键\n52 个白键，36 个黑键\n最中间的 C 叫做「中央 C」",
            demoMidi = 60,
            demoName = "中央 C",
            demoDesc = "高亮的键就是中央 C\n点击下方按钮听听它的声音",
            followTargets = listOf(60),
            soloTargets = listOf(60, 62, 64),
            soloHint = "依次弹奏 C、D、E",
            ttsLines = listOf(
                "欢迎来到第 1 课，认识钢琴",
                "这是中央 C 的声音",
                "请弹奏中央 C",
                "依次弹奏 C D E",
                "恭喜完成第 1 课",
            ),
        ),
        LessonContent(
            id = "lesson_2",
            title = "认识白键",
            level = 1,
            icon = "🎵",
            introText = "白键按 C D E F G A B 七个音名循环\n找到中央 C，右边的依次是 D E F G A B",
            demoMidi = 62,
            demoName = "D4",
            demoDesc = "中央 C 右边第一个白键是 D",
            followTargets = listOf(62),
            soloTargets = listOf(60, 62, 64, 65),
            soloHint = "依次弹奏 C D E F",
            rewardStars = 3,
            rewardExp = 40,
            ttsLines = listOf(
                "第 2 课，认识白键",
                "这是 D 的声音",
                "请弹奏 D",
                "依次弹奏 C D E F",
                "太棒了，完成了第 2 课",
            ),
        ),
        LessonContent(
            id = "lesson_3",
            title = "认识黑键",
            level = 1,
            icon = "🎶",
            introText = "黑键是半音\nC 右边的黑键叫 C#\nD 右边的黑键叫 D#",
            demoMidi = 61,
            demoName = "C#4",
            demoDesc = "C 右边第一个黑键是 C#",
            followTargets = listOf(61),
            soloTargets = listOf(61, 63),
            soloHint = "依次弹奏 C# 和 D#",
            rewardExp = 50,
            ttsLines = listOf(
                "第 3 课，认识黑键",
                "这是 C# 的声音",
                "请弹奏 C#",
                "依次弹奏 C# 和 D#",
                "黑键也难不倒你，完成第 3 课",
            ),
        ),
        LessonContent(
            id = "lesson_4",
            title = "Do Re Mi",
            level = 2,
            icon = "🎤",
            introText = "Do Re Mi 是唱名\n对应 C D E 三个音\n是许多歌曲的基础",
            demoMidi = 64,
            demoName = "E4 (Mi)",
            demoDesc = "Mi 是 Do Re Mi 的第三个音",
            followTargets = listOf(64),
            soloTargets = listOf(60, 62, 64, 62, 60),
            soloHint = "依次弹奏 Do Re Mi Re Do",
            rewardExp = 60,
            ttsLines = listOf(
                "第 4 课，Do Re Mi",
                "这是 Mi 的声音",
                "请弹奏 Mi",
                "依次弹奏 Do Re Mi Re Do",
                "你已掌握 Do Re Mi，完成第 4 课",
            ),
        ),
        LessonContent(
            id = "lesson_5",
            title = "五指练习",
            level = 2,
            icon = "✋",
            introText = "五指练习是钢琴基本功\n让每根手指都灵活起来\nC D E F G 对应 1 2 3 4 5 指",
            demoMidi = 67,
            demoName = "G4",
            demoDesc = "G 是五指练习的最高音",
            followTargets = listOf(67),
            soloTargets = listOf(60, 62, 64, 65, 67),
            soloHint = "依次弹奏 C D E F G",
            rewardExp = 70,
            ttsLines = listOf(
                "第 5 课，五指练习",
                "这是 G 的声音",
                "请弹奏 G",
                "依次弹奏 C D E F G",
                "手指很灵活，完成第 5 课",
            ),
        ),
        LessonContent(
            id = "lesson_6",
            title = "双手配合",
            level = 3,
            icon = "🤲",
            introText = "双手配合是钢琴进阶的关键\n先弹低音 C 作为基础\n再用右手弹旋律",
            demoMidi = 48,
            demoName = "C3 (低音 C)",
            demoDesc = "低音 C 是左手伴奏的基础",
            followTargets = listOf(48),
            soloTargets = listOf(48, 60, 62, 64),
            soloHint = "先弹低音 C，再依次弹中音 C D E",
            rewardExp = 80,
            ttsLines = listOf(
                "第 6 课，双手配合",
                "这是低音 C 的声音",
                "请弹奏低音 C",
                "先弹低音 C，再弹 C D E",
                "双手配合很棒，完成第 6 课",
            ),
        ),
        LessonContent(
            id = "lesson_7",
            title = "节奏入门",
            level = 3,
            icon = "🥁",
            introText = "节奏是音乐的灵魂\n四分音符是一拍\n两个八分音符等于一个四分音符",
            demoMidi = 60,
            demoName = "C4 (节拍)",
            demoDesc = "跟着节拍弹 C",
            followTargets = listOf(60),
            soloTargets = listOf(60, 60, 60, 60),
            soloHint = "按节奏弹 4 个 C",
            rewardExp = 80,
            ttsLines = listOf(
                "第 7 课，节奏入门",
                "感受四分音符的节拍",
                "请弹奏 C",
                "按节奏弹 4 个 C",
                "节奏感很好，完成第 7 课",
            ),
        ),
        LessonContent(
            id = "lesson_8",
            title = "和弦基础",
            level = 4,
            icon = "🎼",
            introText = "和弦是三个或更多音同时弹响\nC 大三和弦 = C E G\n是最基础的和弦",
            demoMidi = 60,
            demoName = "C 大三和弦",
            demoDesc = "C E G 同时弹就是 C 大三和弦",
            followTargets = listOf(60),
            soloTargets = listOf(60, 64, 67),
            soloHint = "依次弹 C E G (C 大三和弦)",
            rewardExp = 90,
            ttsLines = listOf(
                "第 8 课，和弦基础",
                "C 大三和弦由 C E G 组成",
                "请弹奏 C",
                "依次弹 C E G",
                "你已经会弹和弦了，完成第 8 课",
            ),
        ),
        LessonContent(
            id = "lesson_9",
            title = "音阶练习",
            level = 4,
            icon = "📈",
            introText = "C 大调音阶 = C D E F G A B C\n上行从低到高\n下行从高到低",
            demoMidi = 72,
            demoName = "C5",
            demoDesc = "C5 是 C 大调音阶的上行终点",
            followTargets = listOf(72),
            soloTargets = listOf(60, 62, 64, 65, 67, 69, 71, 72),
            soloHint = "上行弹奏 C 大调音阶",
            rewardExp = 100,
            ttsLines = listOf(
                "第 9 课，音阶练习",
                "这是 C5 的声音",
                "请弹奏 C5",
                "上行弹奏 C 大调音阶",
                "音阶很流畅，完成第 9 课",
            ),
        ),
        LessonContent(
            id = "lesson_10",
            title = "小星星片段",
            level = 5,
            icon = "🎹",
            introText = "小星星开头：C C G G A A G\n是很多孩子学会的第一首曲子",
            demoMidi = 67,
            demoName = "G4",
            demoDesc = "G 是小星星的第三个音",
            followTargets = listOf(67),
            soloTargets = listOf(60, 60, 67, 67, 69, 69, 67),
            soloHint = "弹奏小星星开头",
            rewardExp = 120,
            ttsLines = listOf(
                "第 10 课，小星星片段",
                "这是 G 的声音",
                "请弹奏 G",
                "弹奏小星星开头",
                "你已经会弹小星星了，完成第 10 课",
            ),
        ),
        LessonContent(
            id = "lesson_11",
            title = "表现力",
            level = 5,
            icon = "💫",
            introText = "音乐不只是弹对音\n还有强弱和快慢\n轻轻弹 → 弱，用力弹 → 强",
            demoMidi = 60,
            demoName = "C4 (强弱)",
            demoDesc = "同样的 C，力度不同效果不同",
            followTargets = listOf(60),
            soloTargets = listOf(60, 60, 60),
            soloHint = "弹 3 次 C，由弱到强",
            rewardExp = 120,
            ttsLines = listOf(
                "第 11 课，表现力",
                "感受强弱的区别",
                "请弹奏 C",
                "弹 3 次 C，由弱到强",
                "表现力很丰富，完成第 11 课",
            ),
        ),
        LessonContent(
            id = "lesson_12",
            title = "综合演奏",
            level = 6,
            icon = "🎉",
            introText = "把学过的节奏、和弦、音阶结合起来\n完成一首小作品",
            demoMidi = 60,
            demoName = "综合演奏",
            demoDesc = "听完整的示范",
            followTargets = listOf(60),
            soloTargets = listOf(60, 62, 64, 65, 67, 65, 64, 62, 60),
            soloHint = "弹奏综合乐句",
            rewardExp = 150,
            ttsLines = listOf(
                "第 12 课，综合演奏",
                "听完整的示范",
                "请弹奏 C",
                "弹奏综合乐句",
                "恭喜你成为小钢琴家，完成第 12 课",
            ),
        ),
    ).associateBy { it.id }

    /**
     * 获取某课配置，未找到时回退到 lesson_1。
     */
    fun get(lessonId: String): LessonContent = all[lessonId] ?: all.getValue("lesson_1")

    /**
     * 解锁规则：完成上一课才能解锁下一课。
     * lesson_1 默认解锁。
     */
    fun unlockedLessonIds(completed: Set<String>): Set<String> {
        // 按 level 排序得到学习路径顺序
        val order = all.values.sortedBy { it.level }.map { it.id }
        val unlocked = mutableSetOf<String>()
        unlocked.add(order.first())
        for (i in 0 until order.size - 1) {
            if (order[i] in completed) {
                unlocked.add(order[i + 1])
            }
        }
        return unlocked
    }
}
