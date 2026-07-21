package com.pianokids.music

/**
 * 统一的音乐序列数据模型。
 *
 * 三种来源都归一化到本类型，供练琴/学习/比对无差别消费：
 * - MIDI 文件（[MidiParser.toNoteSequence]）
 * - 自定义乐谱编辑器（手动构建）
 * - 拍照识谱/导入（[com.pianokids.scan.SheetMusicRecognizer] 输出）
 *
 * @property tempo 速度（BPM）
 * @property timeSignatureNumerator 分子（默认 4）
 * @property timeSignatureDenominator 分母（默认 4）
 * @property notes 按起始时间升序的音符列表
 * @property durationMs 整曲时长（毫秒）
 * @property title 曲名
 * @property source 来源（midi / custom / scan）
 */
data class NoteSequence(
    val title: String = "",
    val tempo: Int = 120,
    val timeSignatureNumerator: Int = 4,
    val timeSignatureDenominator: Int = 4,
    val notes: List<Note>,
    val durationMs: Long,
    val source: Source = Source.CUSTOM,
) {
    enum class Source { MIDI, CUSTOM, SCAN }

    /**
     * 仅取音符号序列（用于 DTW 比对）。
     */
    fun midiNotes(): List<Int> = notes.map { it.midi }

    /**
     * 一个小节的毫秒数。
     */
    fun measureMs(): Long {
        val beatMs = 60_000.0 / tempo.coerceAtLeast(1)
        return (beatMs * timeSignatureNumerator * (4.0 / timeSignatureDenominator)).toLong()
    }

    companion object {
        /**
         * 默认速度（120 BPM）下，一个四分音符的毫秒数。
         */
        const val QUARTER_MS_AT_120 = 500L
    }
}

/**
 * 序列中的一个音符。
 *
 * @property midi MIDI 音符号（A4=69）。休息用 [REST_MIDI] 表示。
 * @property startMs 起始时间（相对序列起点，毫秒）
 * @property durationMs 持续时长（毫秒）
 * @property velocity 力度 1..127，默认 90
 */
data class Note(
    val midi: Int,
    val startMs: Long,
    val durationMs: Long,
    val velocity: Int = 90,
) {
    companion object {
        /** 休止符的 MIDI 占位（不会发声，仅供数据建模） */
        const val REST_MIDI = -1
    }
}

/**
 * 节拍换算工具：根据 BPM 与音符时值，计算毫秒时长。
 */
object NoteDuration {
    /** 四分音符 */
    const val QUARTER = 1.0
    /** 八分音符 */
    const val EIGHTH = 0.5
    /** 十六分音符 */
    const val SIXTEENTH = 0.25
    /** 全音符 */
    const val WHOLE = 4.0
    /** 二分音符 */
    const val HALF = 2.0

    /**
     * 计算给定 [beats]（以四分音符为单位）在 [tempo] BPM 下的毫秒数。
     */
    fun ms(beats: Double, tempo: Int): Long {
        val beatMs = 60_000.0 / tempo.coerceAtLeast(1)
        return (beatMs * beats).toLong()
    }
}
