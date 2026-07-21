package com.pianokids.audio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 一次识别到的音符事件。
 *
 * @property midiNote MIDI 音符号（A4=69）
 * @property noteName 音名（如 "C4"）
 * @property startTime 起始时间戳（System.currentTimeMillis）
 * @property duration 持续时间（毫秒），0 表示瞬时事件
 * @property velocity 力度（0~127），由 onset 能量粗略映射
 */
data class NoteEvent(
    val midiNote: Int,
    val noteName: String,
    val startTime: Long,
    val duration: Long,
    val velocity: Int,
)

/**
 * 音符识别器：监听 [AudioCapture.frames]，对每一帧做 onset + 基频检测，
 * 在 onset 触发时输出一个 [NoteEvent]。
 *
 * - onset 阈值随背景噪声动态调整：阈值 = max(固定下限, 背景能量 * 倍数)。
 * - 同一音符在 200ms 内不重复触发，避免连击。
 */
@Singleton
class NoteRecognizer @Inject constructor(
    private val audioCapture: AudioCapture,
) {
    companion object {
        /** onset 触发后到下一次可触发的间隔（毫秒） */
        private const val DEBOUNCE_MS = 200L
        /** 静音 RMS 阈值，低于此值不检测 onset */
        private const val SILENCE_RMS = 0.01f
        /** onset 触发所需的最低能量 */
        private const val MIN_ONSET_THRESHOLD = 0.015f
        /** onset 阈值 = 背景能量 * 此倍数 */
        private const val ONSET_NOISE_RATIO = 2.5f
        /** 用于估算背景噪声的滑动平均系数（越小越平滑） */
        private const val NOISE_SMOOTH = 0.05f
        /** 同一音符若频率稳定时长超过此值，则输出一个有 duration 的事件 */
        private const val NOTE_HOLD_THRESHOLD_FRAMES = 8
    }

    /**
     * 音符事件流。每当识别到新音符即发射一个 [NoteEvent]。
     */
    val noteEvents: Flow<NoteEvent> = audioCapture.frames
        .map { buffer -> recognize(buffer) }
        .filterNotNull()

    /** 当前动态背景噪声估计（RMS 域） */
    private var noiseFloor = MIN_ONSET_THRESHOLD
    /** 上一次触发的时间戳 */
    private var lastOnsetTime = 0L
    /** 上一次触发的音符 */
    private var lastNote: Int = -1
    /** 当前正在持续的音符起始信息 */
    private var currentNote: Int = -1
    private var currentStartTime = 0L
    private var currentStableFrames = 0

    /**
     * 对一帧进行 onset 检测与音符识别。
     */
    private fun recognize(buffer: FloatArray): NoteEvent? {
        val now = System.currentTimeMillis()
        val rms = computeRms(buffer)

        // 更新背景噪声：仅在安静时累积，避免被音符本身抬高
        if (rms < noiseFloor * 1.5f) {
            noiseFloor = noiseFloor * (1f - NOISE_SMOOTH) + rms * NOISE_SMOOTH
            if (noiseFloor < MIN_ONSET_THRESHOLD) noiseFloor = MIN_ONSET_THRESHOLD
        }

        val threshold = maxOf(MIN_ONSET_THRESHOLD, noiseFloor * ONSET_NOISE_RATIO)
        val onsetEnergy = NativeAudioEngine.detectOnset(buffer)

        // onset 触发条件：能量超过动态阈值、且不在去抖期
        if (onsetEnergy > threshold && rms > SILENCE_RMS &&
            (lastOnsetTime == 0L || now - lastOnsetTime > DEBOUNCE_MS)
        ) {
            // 先尝试用 onset 帧的频率直接判断音符
            val freq = NativeAudioEngine.detectPitch(buffer, AudioCapture.SAMPLE_RATE)
            if (freq > 0f) {
                val note = NativeAudioEngine.freqToNote(freq)
                if (note.midiNote >= 0) {
                    lastOnsetTime = now
                    lastNote = note.midiNote
                    currentNote = note.midiNote
                    currentStartTime = now
                    currentStableFrames = 1
                    return NoteEvent(
                        midiNote = note.midiNote,
                        noteName = note.noteName,
                        startTime = now,
                        duration = 0L,
                        velocity = mapRmsToVelocity(rms),
                    )
                }
            }
        }

        // 维持音符连续性：若本次基频与当前持续音符相同，累计稳定帧
        if (currentNote >= 0) {
            val freq = NativeAudioEngine.detectPitch(buffer, AudioCapture.SAMPLE_RATE)
            if (freq > 0f) {
                val note = NativeAudioEngine.freqToNote(freq)
                if (note.midiNote == currentNote) {
                    currentStableFrames++
                    // 持续一定帧数后输出一次带 duration 的事件
                    if (currentStableFrames == NOTE_HOLD_THRESHOLD_FRAMES) {
                        val dur = now - currentStartTime
                        return NoteEvent(
                            midiNote = currentNote,
                            noteName = note.noteName,
                            startTime = currentStartTime,
                            duration = dur,
                            velocity = mapRmsToVelocity(rms),
                        )
                    }
                } else {
                    // 音符已切换：清空当前音符
                    currentNote = -1
                    currentStableFrames = 0
                }
            }
        }

        return null
    }

    /** RMS -> MIDI velocity 的简易映射（0..127） */
    private fun mapRmsToVelocity(rms: Float): Int {
        val v = ((rms - SILENCE_RMS) / (0.2f - SILENCE_RMS)) * 127f
        return v.toInt().coerceIn(1, 127)
    }

    private fun computeRms(buf: FloatArray): Float {
        if (buf.isEmpty()) return 0f
        var sum = 0.0
        for (s in buf) {
            sum += (s * s).toDouble()
        }
        return Math.sqrt(sum / buf.size).toFloat()
    }
}
