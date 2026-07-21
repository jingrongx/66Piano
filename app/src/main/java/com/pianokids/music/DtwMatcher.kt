package com.pianokids.music

/**
 * 单个音符匹配结果。
 *
 * @property referenceIndex 标准谱中的音符序号（-1 表示未匹配）
 * @property playedIndex 孩子弹奏中的音符序号（-1 表示未匹配）
 * @property correct 是否音高匹配（同一 MIDI 音符号）
 */
data class NoteMatch(
    val referenceIndex: Int,
    val playedIndex: Int,
    val correct: Boolean,
)

/**
 * DTW 匹配总体结果。
 *
 * @property score 总体相似度 [0,1]；1 表示完全匹配
 * @property matches 每个参考音符的匹配详情
 * @property missingNotes 标准谱中未被弹奏到的 MIDI 音符列表
 * @property extraNotes 孩子弹奏中多余的 MIDI 音符列表
 */
data class MatchResult(
    val score: Float,
    val matches: List<NoteMatch>,
    val missingNotes: List<Int>,
    val extraNotes: List<Int>,
)

/**
 * 动态时间规整（DTW）匹配器。
 *
 * 把孩子弹奏的 MIDI 音符序列 [played] 与标准 [reference] 谱对齐，
 * 容忍速度起伏与少量错音，输出每个参考音符的匹配状态及总体得分。
 */
class DtwMatcher {

    /**
     * 执行 DTW 对齐。
     *
     * @param played 孩子弹奏的 MIDI 音符号序列（按时间顺序）
     * @param reference 标准谱的 MIDI 音符号序列（按时间顺序）
     */
    fun match(played: List<Int>, reference: List<Int>): MatchResult {
        if (reference.isEmpty() && played.isEmpty()) {
            return MatchResult(score = 1f, matches = emptyList(), missingNotes = emptyList(), extraNotes = emptyList())
        }
        if (reference.isEmpty()) {
            return MatchResult(
                score = 0f,
                matches = emptyList(),
                missingNotes = emptyList(),
                extraNotes = played,
            )
        }
        if (played.isEmpty()) {
            return MatchResult(
                score = 0f,
                matches = reference.mapIndexed { i, _ -> NoteMatch(i, -1, false) },
                missingNotes = reference,
                extraNotes = emptyList(),
            )
        }

        val n = reference.size
        val m = played.size

        // 1. 累积代价矩阵 dp[i][j] = 把 reference[0..i) 与 played[0..j) 对齐的最小总代价
        //    用 FloatArray 数组节省内存
        val dp = Array(n + 1) { FloatArray(m + 1) }
        for (i in 0..n) dp[i][0] = i * MISS_PENALTY
        for (j in 0..m) dp[0][j] = j * EXTRA_PENALTY
        for (i in 1..n) {
            for (j in 1..m) {
                val cost = localCost(reference[i - 1], played[j - 1])
                dp[i][j] = minOf(
                    dp[i - 1][j - 1] + cost,
                    dp[i - 1][j] + MISS_PENALTY,
                    dp[i][j - 1] + EXTRA_PENALTY,
                )
            }
        }

        // 2. 回溯路径
        val path = ArrayDeque<Pair<Int, Int>>() // (refIndex+1, playedIndex+1)
        var i = n
        var j = m
        while (i > 0 || j > 0) {
            path.addFirst(i to j)
            if (i > 0 && j > 0) {
                val cost = localCost(reference[i - 1], played[j - 1])
                val diag = dp[i - 1][j - 1] + cost
                val up = dp[i - 1][j] + MISS_PENALTY
                val left = dp[i][j - 1] + EXTRA_PENALTY
                when {
                    diag <= up && diag <= left -> { i--; j-- }
                    up <= left -> { i-- }
                    else -> { j-- }
                }
            } else if (i > 0) {
                i--
            } else {
                j--
            }
        }

        // 3. 由路径构造每个参考音符的匹配
        val matches = mutableListOf<NoteMatch>()
        val matchedRefIndices = HashSet<Int>()
        val matchedPlayedIndices = HashSet<Int>()
        for ((ri, pj) in path) {
            val refIdx = ri - 1
            val playedIdx = pj - 1
            if (refIdx >= 0 && playedIdx >= 0) {
                val refNote = reference[refIdx]
                val playedNote = played[playedIdx]
                val correct = refNote == playedNote
                matches.add(NoteMatch(refIdx, playedIdx, correct))
                matchedRefIndices.add(refIdx)
                matchedPlayedIndices.add(playedIdx)
            } else if (refIdx >= 0) {
                // 参考音符存在但弹奏缺失
                matches.add(NoteMatch(refIdx, -1, false))
                matchedRefIndices.add(refIdx)
            }
            // playedIdx >= 0 但 refIdx < 0 的情况：多余音符，放到 extraNotes
        }

        // 4. 缺失 / 多余音符列表
        val missingNotes = reference.mapIndexedNotNull { idx, note ->
            if (idx !in matchedRefIndices) note else null
        }
        val extraNotes = played.mapIndexedNotNull { idx, note ->
            if (idx !in matchedPlayedIndices) note else null
        }

        // 5. 评分：用最终累积代价的归一化形式
        //    完美匹配代价为 0；最坏代价为 n*MISS_PENALTY
        val worstCost = (n * MISS_PENALTY).coerceAtLeast(1f)
        val rawScore = 1f - (dp[n][m] / worstCost).coerceIn(0f, 1f)
        // 综合正确率：已匹配且 correct 的占比
        val correctCount = matches.count { it.correct }
        val correctRate = if (n > 0) correctCount.toFloat() / n else 0f
        // 取两者较高，但权重偏向 correctRate
        val score = (correctRate * 0.7f + rawScore * 0.3f).coerceIn(0f, 1f)

        return MatchResult(
            score = score,
            matches = matches.sortedBy { it.referenceIndex },
            missingNotes = missingNotes,
            extraNotes = extraNotes,
        )
    }

    companion object {
        /** 缺失一个参考音符的代价（孩子漏弹） */
        private const val MISS_PENALTY = 4f
        /** 多出一个弹奏音符的代价（孩子多弹/错弹） */
        private const val EXTRA_PENALTY = 4f
        /** 音高不等的局部代价（按半音差距递增，但封顶） */
        private const val WRONG_NOTE_PENALTY = 6f

        /**
         * 两个 MIDI 音符号的局部代价：
         * - 相同 -> 0
         * - 相差 12 的倍数（同音名不同八度）-> 1（视作"接近"）
         * - 其他 -> WRONG_NOTE_PENALTY * (1 - 1/(diff+1))
         */
        private fun localCost(ref: Int, played: Int): Float {
            if (ref == played) return 0f
            val diff = kotlin.math.abs(ref - played)
            return if (diff % 12 == 0) 1f else {
                WRONG_NOTE_PENALTY * (1f - 1f / (diff + 1f))
            }
        }
    }
}
