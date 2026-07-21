package com.pianokids.audio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 一次音高检测结果。
 *
 * @property freq 频率（Hz）。静音或无法判定时为 -1f
 * @property note 对应的 [NoteInfo]；静音时为 null
 * @property timestamp 检测时刻的系统时间戳（System.currentTimeMillis）
 * @property confidence 置信度 [0,1]；静音为 0
 */
data class PitchResult(
    val freq: Float,
    val note: NoteInfo?,
    val timestamp: Long,
    val confidence: Float,
)

/**
 * 音高检测器：监听 [AudioCapture.frames]，逐帧调用 [NativeAudioEngine]
 * 提取基频，并做中位数平滑与静音过滤。
 *
 * - 平滑：保留最近 3 个有效频率，输出中位数，过滤瞬时跳变。
 * - 静音：当帧 RMS 能量低于阈值时输出 freq=-1，避免误判噪声。
 */
@Singleton
class PitchDetector @Inject constructor(
    private val audioCapture: AudioCapture,
) {
    companion object {
        /** 平滑窗口大小（最近 N 个有效频率取中位数） */
        private const val SMOOTH_WINDOW = 3
        /** 静音 RMS 阈值（归一化能量） */
        private const val SILENCE_RMS_THRESHOLD = 0.012f
        /** 跳变过滤：相邻两次频率差超过该比例视为异常 */
        private const val JUMP_RATIO = 0.35f
        /** 单帧 RMS 影响置信度的上限 */
        private const val CONFIDENCE_RMS_FULL = 0.06f
    }

    /**
     * 音高结果流。
     *
     * 每个上游音频帧对应一个 [PitchResult]。
     */
    val pitches: Flow<PitchResult> = audioCapture.frames
        .map { buffer -> processFrame(buffer) }

    /** 最近 N 个有效频率的滑动窗口 */
    private val recentFreqs = ArrayDeque<Float>(SMOOTH_WINDOW)
    /** 上一次输出的有效频率，用于跳变过滤 */
    private var lastOutputFreq = -1f

    /**
     * 处理一帧：
     * 1. 计算 RMS，过低则输出静音结果；
     * 2. 调 native 检测基频；
     * 3. 通过跳变过滤后入滑动窗口；
     * 4. 输出窗口内中位数。
     */
    private fun processFrame(buffer: FloatArray): PitchResult {
        val now = System.currentTimeMillis()
        val rms = computeRms(buffer)

        // 静音判定
        if (rms < SILENCE_RMS_THRESHOLD) {
            recentFreqs.clear()
            lastOutputFreq = -1f
            return PitchResult(
                freq = -1f,
                note = null,
                timestamp = now,
                confidence = 0f,
            )
        }

        val rawFreq = NativeAudioEngine.detectPitch(buffer, AudioCapture.SAMPLE_RATE)
        if (rawFreq <= 0f) {
            // native 未识别出基频：不更新窗口，但保留之前的平滑结果
            return PitchResult(
                freq = lastOutputFreq,
                note = lastOutputFreq.takeIf { it > 0f }?.let { NativeAudioEngine.freqToNote(it) },
                timestamp = now,
                confidence = 0f,
            )
        }

        // 跳变过滤：若与上次输出差距过大，丢弃本次（防瞬时毛刺）
        val accepted = if (lastOutputFreq > 0f && isJump(rawFreq, lastOutputFreq)) {
            lastOutputFreq
        } else {
            rawFreq
        }

        // 入滑动窗口
        if (recentFreqs.size >= SMOOTH_WINDOW) {
            recentFreqs.removeFirst()
        }
        recentFreqs.addLast(accepted)

        val smoothed = median(recentFreqs)
        lastOutputFreq = smoothed

        val note = NativeAudioEngine.freqToNote(smoothed)
        val confidence = (rms / CONFIDENCE_RMS_FULL).coerceIn(0f, 1f)

        return PitchResult(
            freq = smoothed,
            note = note,
            timestamp = now,
            confidence = confidence,
        )
    }

    /** 计算归一化浮点数组的 RMS 能量。 */
    private fun computeRms(buf: FloatArray): Float {
        if (buf.isEmpty()) return 0f
        var sum = 0.0
        for (s in buf) {
            sum += (s * s).toDouble()
        }
        return Math.sqrt(sum / buf.size).toFloat()
    }

    /**
     * 判断两次频率是否发生跳变（音高跃迁或异常）。
     * 用比例而非绝对差，兼容低音区与高音区。
     */
    private fun isJump(a: Float, b: Float): Boolean {
        if (b <= 0f) return false
        val diff = kotlin.math.abs(a - b)
        // 高于 1.35 倍或低于 0.65 倍视为跳变
        return a > b * (1f + JUMP_RATIO) || a < b * (1f - JUMP_RATIO)
    }

    /** 计算小集合的中位数（不分配新数组）。 */
    private fun median(values: ArrayDeque<Float>): Float {
        if (values.isEmpty()) return -1f
        val sorted = values.toFloatArray().also { it.sort() }
        val n = sorted.size
        return if (n % 2 == 1) {
            sorted[n / 2]
        } else {
            (sorted[n / 2 - 1] + sorted[n / 2]) / 2f
        }
    }

    private fun ArrayDeque<Float>.toFloatArray(): FloatArray = FloatArray(size) { i -> this[i] }
}
